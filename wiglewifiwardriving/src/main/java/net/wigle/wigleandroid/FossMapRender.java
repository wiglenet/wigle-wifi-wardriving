package net.wigle.wigleandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.NetworkIconGenerator;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

/**
 * port of MapRender for FOSS builds w/ MapLibre.
 * Implements spatial clustering via grid-based grouping - not as polished as gmaps.
 */
@SuppressWarnings("deprecation")
public class FossMapRender {

    private static final int CLUSTER_THRESHOLD = 4;   // match MapRender: cluster when size > 4
    private static final int CLUSTER_ALWAYS_AT = 30; // difference from MapRender: always cluster at >= 30
    private static final int GRID_MIN_CELLS = 4;
    private static final int GRID_MAX_CELLS = 12;
    private static final float CLUSTER_ICON_DP = 48f;
    private static final long INITIAL_RECLUSTER_DELAY_MS = 1000; // hack - initial cache load misses recluster
    private final Context context;
    private final boolean isDbResult;
    private final AtomicInteger networkCount = new AtomicInteger();
    private final SharedPreferences prefs;
    private final MapLibreMap map;
    private Matcher ssidMatcher;

    // track markers by BSSID so we can update/remove efficiently
    private final Map<String, Marker> markersByBssid = new ConcurrentHashMap<>();
    // cluster markers (one per cluster when clustering is on)
    private final List<Marker> clusterMarkers = Collections.synchronizedList(new ArrayList<>());

    // networks that currently have "bubble" labels
    private final Set<Network> labeledNetworks = Collections.newSetFromMap(
            new ConcurrentHashMap<>());

    private final NetworkIconGenerator iconFactory;
    private final IconFactory mapIconFactory;

    // a % of the cache size can be labels
    private static final int MAX_LABELS = MainActivity.getNetworkCache().maxSize() / 10;

    private volatile LatLngBounds cachedVisibleBounds;

    private volatile boolean destroyed;

    private static final String MESSAGE_BSSID = "messageBssid";
    private static final String MESSAGE_BSSID_LIST = "messageBssidList";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FossMapRender(@NonNull final Context context,
                         @NonNull final MapLibreMap map,
                         final boolean isDbResult) {
        this.context = context.getApplicationContext();
        this.map = map;
        this.isDbResult = isDbResult;
        this.prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        this.ssidMatcher = FilterMatcher.getSsidFilterMatcher(
                prefs, AbstractMappingFragment.MAP_DIALOG_PREFIX);
        this.iconFactory = new NetworkIconGenerator(context);
        this.mapIconFactory = IconFactory.getInstance(context);

        if (!isDbResult) {
            setupRelabelingTask();
        }

        reCluster();
        //recluster on a delay to accommodate cache load
        if (!isDbResult) {
            updateMarkersHandler.postDelayed(() -> {
                if (!destroyed) {
                    reCluster();
                }
            }, INITIAL_RECLUSTER_DELAY_MS);
        }
    }

    /**
     * avoid race conditions on destruction. Still seeing delayed calls (TODO: improve)
     */
    public void destroy() {
        destroyed = true;
        updateMarkersHandler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    /**
     * determine whether a network should be visible on the map
     */
    public boolean okForMapTab(@NonNull final Network network) {
        final boolean hideNets = prefs.getBoolean(PreferenceKeys.PREF_MAP_HIDE_NETS, false);
        final boolean showNewDBOnly = prefs.getBoolean(PreferenceKeys.PREF_MAP_ONLY_NEWDB, false)
                && !isDbResult;
        if (network.getPosition() != null && !hideNets) {
            if (!showNewDBOnly || network.isNew()) {
                return FilterMatcher.isOk(
                        ssidMatcher,
                        null,
                        prefs,
                        AbstractMappingFragment.MAP_DIALOG_PREFIX,
                        network);
            }
        }
        return false;
    }

    /**
     * add a network.
     */
    public void addItem(@NonNull final Network network) {
        if (network.getPosition() == null) {
            return;
        }
        if (!okForMapTab(network)) {
            return;
        }

        final int count = networkCount.incrementAndGet();
        if (count > MainActivity.getNetworkCache().size() * 1.3) {
            updateMarkersHandler.post(this::reCluster);
            return;
        }
        updateMarkersHandler.post(() -> addOrUpdateMarker(network));
    }

    /**
     * Clears all network and cluster markers we manage.
     */
    public void clear() {
        if (destroyed) {
            return;
        }
        Logging.info("FossMapRender: clear");
        labeledNetworks.clear();
        networkCount.set(0);

        for (Marker marker : markersByBssid.values()) {
            try {
                marker.remove();
            } catch (Exception ignore) {
                // annotation may already be gone
            }
        }
        markersByBssid.clear();

        synchronized (clusterMarkers) {
            for (Marker marker : clusterMarkers) {
                try {
                    marker.remove();
                } catch (Exception ignore) {
                    // annotation may already be gone
                }
            }
            clusterMarkers.clear();
        }
    }

    /**
     * Rebuild markers from the in-memory cache, with spatial clustering when enabled.
     */
    public void reCluster() {
        if (destroyed) {
            return;
        }
        Logging.info("FossMapRender: reCluster");
        clear();
        if (!isDbResult) {
            addClusteredNetworks();
        }
    }

    /**
     * refresh the filter and fully rebuild marker set.
     */
    public void onResume() {
        ssidMatcher = FilterMatcher.getSsidFilterMatcher(
                prefs, AbstractMappingFragment.MAP_DIALOG_PREFIX);
        reCluster();
    }

    /**
     * update a network marker
     */
    public void updateNetwork(@NonNull final Network network) {
        if (okForMapTab(network)) {
            sendUpdateNetwork(network.getBssid());
        }
    }

    // ---------- clustering - WiP ----------

    /**
     * show this group as a single cluster marker?
     */
    private boolean shouldRenderAsCluster(final int size) {
        final boolean clusterPref = prefs.getBoolean(PreferenceKeys.PREF_MAP_CLUSTER, true);
        return (clusterPref && size > CLUSTER_THRESHOLD) || size >= CLUSTER_ALWAYS_AT;
    }

    /**
     * Grid-based clustering: assign networks to cells, return list of clusters (cell center + networks).
     * zoom in splits clusters, zoom out combines. This doesn't look as good as the google analogue,
     * but at least it's deterministic
     */
    private List<MapCluster> computeClusters(@NonNull final LatLngBounds bounds, final double zoom) {
        final double north = bounds.getLatNorth();
        final double south = bounds.getLatSouth();
        final double west = bounds.getLonWest();
        final double east = bounds.getLonEast();
        final double latSpan = north - south;
        final double lonSpan = east - west;
        if (latSpan <= 0 || lonSpan <= 0) {
            return Collections.emptyList();
        }

        final int numCells = (int) Math.max(GRID_MIN_CELLS, Math.min(GRID_MAX_CELLS, 4 + (zoom - 10) / 2));
        final double cellLat = latSpan / numCells;
        final double cellLon = lonSpan / numCells;

        final Map<String, List<Network>> cells = new HashMap<>();
        final Collection<Network> nets = MainActivity.getNetworkCache().values();

        for (final Network network : nets) {
            if (!okForMapTab(network)) {
                continue;
            }
            final LatLng pos = getMarkerPosition(network);
            if (pos == null) {
                continue;
            }
            final double lat = pos.getLatitude();
            final double lon = pos.getLongitude();
            if (!bounds.contains(pos)) {
                continue;
            }
            final int j = (int) Math.min(numCells - 1, Math.max(0, (north - lat) / cellLat));
            final int i = (int) Math.min(numCells - 1, Math.max(0, (lon - west) / cellLon));
            final String key = i + "," + j;
            cells.computeIfAbsent(key, k -> new ArrayList<>()).add(network);
        }

        final List<MapCluster> clusters = new ArrayList<>();
        for (final List<Network> list : cells.values()) {
            if (list.isEmpty()) {
                continue;
            }
            double sumLat = 0;
            double sumLon = 0;
            for (final Network n : list) {
                final LatLng p = getMarkerPosition(n);
                if (p != null) {
                    sumLat += p.getLatitude();
                    sumLon += p.getLongitude();
                }
            }
            final int size = list.size();
            final LatLng center = new LatLng(sumLat / size, sumLon / size);
            clusters.add(new MapCluster(center, list));
        }
        return clusters;
    }

    /**
     * add the markers
     */
    private void addClusteredNetworks() {
        LatLngBounds bounds;
        double zoom;
        try {
            bounds = map.getProjection().getVisibleRegion().latLngBounds;
            zoom = map.getCameraPosition().zoom;
        } catch (Exception e) {
            Logging.info("FossMapRender: cannot get bounds/zoom, adding all as individual: " + e.getMessage());
            addLatestNetworks();
            return;
        }
        if (bounds == null) {
            addLatestNetworks();
            return;
        }

        final boolean clusterOn = prefs.getBoolean(PreferenceKeys.PREF_MAP_CLUSTER, true);
        if (!clusterOn) {
            addLatestNetworks();
            return;
        }

        final List<MapCluster> clusters = computeClusters(bounds, zoom);
        int addedCount = 0;

        for (final MapCluster cluster : clusters) {
            final int size = cluster.networks.size();
            if (shouldRenderAsCluster(size)) {
                final MarkerOptions options = new MarkerOptions()
                        .position(cluster.center)
                        .title(size + " Networks")
                        .snippet(buildClusterSnippet(cluster));

                final Icon icon = makeClusterIcon(size);
                if (icon != null) {
                    options.icon(icon);
                }
                final Marker marker = map.addMarker(options);
                if (marker != null) {
                    clusterMarkers.add(marker);
                    addedCount += 1;
                }
            } else {
                for (final Network network : cluster.networks) {
                    addOrUpdateMarker(network);
                    addedCount++;
                }
            }
        }
        networkCount.getAndAdd(addedCount);
    }

    private String buildClusterSnippet(@NonNull final MapCluster cluster) {
        final StringBuilder sb = new StringBuilder();
        final int max = Math.min(5, cluster.networks.size());
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(cluster.networks.get(i).getSsid());
        }
        if (cluster.networks.size() > max) {
            sb.append("\n...");
        }
        return sb.toString();
    }

    /**
     * cluster icon - super-simple for first version (fixed text length, no bubble sizing).
     */
    private Icon makeClusterIcon(final int count) {
        try {
            final int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CLUSTER_ICON_DP,
                    context.getResources().getDisplayMetrics());
            final Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);

            final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(0xFF1976D2);
            circlePaint.setStyle(Paint.Style.FILL);
            final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setColor(0xFF0D47A1);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(sizePx * 0.08f);

            final float r = sizePx * 0.4f;
            final float cx = sizePx * 0.5f;
            final float cy = sizePx * 0.5f;
            canvas.drawCircle(cx, cy, r, circlePaint);
            canvas.drawCircle(cx, cy, r, strokePaint);

            final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(sizePx * 0.4f);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            final String label = count > 99 ? "99+" : String.valueOf(count);
            final Rect textBounds = new Rect();
            textPaint.getTextBounds(label, 0, label.length(), textBounds);
            final float baseline = cy + (textBounds.height() * 0.35f);
            canvas.drawText(label, cx, baseline, textPaint);

            return mapIconFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Logging.info("FossMapRender: makeClusterIcon failed: " + e.getMessage());
            return mapIconFactory.fromResource(R.drawable.ic_wifi_sm);
        }
    }

    /** center position and list of networks in that cell. */
    private static final class MapCluster {
        final LatLng center;
        final List<Network> networks;

        MapCluster(final LatLng center, final List<Network> networks) {
            this.center = center;
            this.networks = networks;
        }
    }

    /**
     * Get MapLibre LatLng for marker placement using the network's stored position (model LatLng).
     */
    private LatLng getMarkerPosition(@NonNull final Network network) {
        final net.wigle.wigleandroid.model.LatLng stored = network.getLatLng();
        if (stored == null) {
            return null;
        }
        return new LatLng(stored.latitude, stored.longitude);
    }

    private void addLatestNetworks() {
        int cached = 0;
        int added = 0;
        final Collection<Network> nets = MainActivity.getNetworkCache().values();

        for (final Network network : nets) {
            cached++;
            if (okForMapTab(network)) {
                added++;
                addOrUpdateMarker(network);
            }
        }
        //DEBUG: Logging.info("FossMapRender cached: " + cached + " added: " + added);
        networkCount.getAndAdd(added);
    }

    private void addOrUpdateMarker(@NonNull final Network network) {
        final LatLng latLng = getMarkerPosition(network);
        if (destroyed || latLng == null) {
            return;
        }
        final String bssid = network.getBssid();

        Marker marker = markersByBssid.get(bssid);
        if (marker == null) {
            final MarkerOptions options = new MarkerOptions()
                    .position(latLng)
                    .title(network.getSsid())
                    .snippet(buildSnippet(network));

            Icon icon = null;
            try {
                icon = getIcon(network);
                if (icon == null) {
                    icon = mapIconFactory.fromResource(R.drawable.ic_wifi_sm);
                }
            } catch (Exception ex) {
                Logging.info("FossMapRender: getIcon failed, using default: " + ex);
            }
            options.icon(icon);

            marker = map.addMarker(options);
            if (marker != null) {
                markersByBssid.put(bssid, marker);
            }
        } else {
            try {
                marker.setPosition(latLng);
                marker.setTitle(network.getSsid());
                marker.setSnippet(buildSnippet(network));
                Icon icon = null;
                try {
                    icon = getIcon(network);
                } catch (Exception ignored) {
                    // use fallback below
                }
                if (icon == null) {
                    icon = mapIconFactory.fromResource(R.drawable.ic_wifi_sm);
                }
                marker.setIcon(icon);
            } catch (Exception ex) {
                Logging.info("FossMapRender: marker update failed, recreating. " + ex);
                try {
                    marker.remove();
                } catch (Exception ignore) {
                    // ignore
                }
                markersByBssid.remove(bssid);
                addOrUpdateMarker(network);
            }
        }
    }

    private String buildSnippet(@NonNull final Network network) {
        final String newString = network.isNew() ? " (new)" : "";
        return network.getBssid() + newString
                + "\n\t" + network.getChannel()
                + "\n\t" + network.getCapabilities();
    }

    /**
     * decide how to draw network
     */
    private boolean shouldUseLabeledIcon(@NonNull final Network network) {
        final boolean showLabel = prefs.getBoolean(PreferenceKeys.PREF_MAP_LABEL, true);
        if (!showLabel) {
            return false;
        }

        final LatLngBounds bounds = cachedVisibleBounds;
        final LatLng pos = getMarkerPosition(network);
        if (bounds == null || pos == null) {
            return true;
        }
        return bounds.contains(pos) && labeledNetworks.size() <= MAX_LABELS;
    }

    /**
     * returns the Icon for the network (either a simple pin or a rich label bubble)
     */
    private Icon getIcon(@NonNull final Network network) {
        final boolean labeled = shouldUseLabeledIcon(network);
        if (labeled) {
            labeledNetworks.add(network);
            return mapIconFactory.fromBitmap(iconFactory.makeIcon(network, network.isNew()));
        }

        labeledNetworks.remove(network);

        final int resId;
        switch (network.getType()) {
            case CDMA:
            case GSM:
            case WCDMA:
            case LTE:
            case NR:
                resId = R.drawable.ic_cell;
                break;
            case BT:
                resId = R.drawable.bt_white;
                break;
            case BLE:
                resId = R.drawable.btle_white;
                break;
            case WIFI:
            default:
                resId = R.drawable.ic_wifi_sm;
                break;
        }
        return mapIconFactory.fromResource(resId);
    }

    /**
     * set up a camera idle listener to refresh clustering and labels.
     */
    private void setupRelabelingTask() {
        final Handler handler = new Handler(Looper.getMainLooper());
        map.addOnCameraIdleListener(() -> {
            if (destroyed) {
                return;
            }
            final LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
            cachedVisibleBounds = bounds;

            executor.execute(() -> {
                if (destroyed) {
                    return;
                }
                handler.post(() -> {
                    if (!destroyed) {
                        reCluster();
                    }
                });
            });
        });
    }

    private void sendUpdateNetwork(@NonNull final String bssid) {
        final Bundle data = new Bundle();
        data.putString(MESSAGE_BSSID, bssid);
        final Message message = new Message();
        message.setData(data);
        updateMarkersHandler.sendMessage(message);
    }

    private final Handler updateMarkersHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull final Message message) {
            if (destroyed) {
                return;
            }
            final String bssid = message.getData().getString(MESSAGE_BSSID);
            if (bssid != null) {
                final Network network = MainActivity.getNetworkCache().get(bssid);
                if (network != null) {
                    addOrUpdateMarker(network);
                }
            }

            final ArrayList<String> bssids = message.getData().getStringArrayList(MESSAGE_BSSID_LIST);
            if (bssids != null && !bssids.isEmpty()) {
                //DEBUG: Logging.info("FossMapRender bssids: " + bssids.size());
                for (final String thisBssid : bssids) {
                    final Network network = MainActivity.getNetworkCache().get(thisBssid);
                    if (network != null) {
                        addOrUpdateMarker(network);
                    }
                }
            }
        }
    };
}