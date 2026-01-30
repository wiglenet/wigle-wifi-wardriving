package net.wigle.wigleandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

/**
 * port of MapRender for FOSS builds w/ MapLibre.
 */
@SuppressWarnings("deprecation")
public class FossMapRender {

    private final MapLibreMap map;
    private final boolean isDbResult;
    private final AtomicInteger networkCount = new AtomicInteger();
    private final SharedPreferences prefs;
    private Matcher ssidMatcher;

    // track markers by BSSID so we can update/remove efficiently
    private final Map<String, Marker> markersByBssid = new ConcurrentHashMap<>();

    // networks that currently have rich "bubble" labels
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
    }

    /**
     * avoid race conditions on destruction
     */
    public void destroy() {
        destroyed = true;
        updateMarkersHandler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    /**
     * Determines whether a network should be visible on the map, mirroring MapRender logic.
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
     * Clears all network markers we manage.
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
    }

    /**
     * rebuild markers from the in-memory cache
     */
    public void reCluster() {
        if (destroyed) {
            return;
        }
        Logging.info("FossMapRender: reCluster");
        clear();
        if (!isDbResult) {
            addLatestNetworks();
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

    // ---------- internal helpers ----------

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
        if (destroyed || network.getLatLng() == null) {
            return;
        }
        final String bssid = network.getBssid();
        final LatLng latLng = new LatLng(
                network.getLatLng().latitude,
                network.getLatLng().longitude);

        Marker marker = markersByBssid.get(bssid);
        if (marker == null) {
            final MarkerOptions options = new MarkerOptions()
                    .position(latLng)
                    .title(network.getSsid())
                    .snippet(buildSnippet(network));

            Icon icon = null;
            try {
                icon = getIcon(network);
                if (null != icon) {
                    options.icon(icon);
                } else {
                    Logging.error("Failed to get icon for "+network.getBssid()+" ("+network.getType()+")");
                }
            } catch (Exception ex) {
                Logging.info("FossMapRender: getIcon failed, using default: " + ex);
            }

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
     * Decides whether this network should have a rich label bubble, roughly matching MapRender.
     */
    private boolean shouldUseLabeledIcon(@NonNull final Network network) {
        final boolean showLabel = prefs.getBoolean(PreferenceKeys.PREF_MAP_LABEL, true);
        if (!showLabel) {
            return false;
        }

        final LatLngBounds bounds = cachedVisibleBounds;
        if (bounds == null || network.getLatLng() == null) {
            return true;
        }
        final LatLng pos = new LatLng(
                network.getLatLng().latitude,
                network.getLatLng().longitude);

        return bounds.contains(pos) && labeledNetworks.size() <= MAX_LABELS;
    }

    /**
     * Returns an Icon for the network, either a simple pin or a rich label bubble.
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
     * Set up a camera idle listener that walks visible markers and queues refresh work.
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
                final Collection<Network> nets = MainActivity.getNetworkCache().values();
                final ArrayList<String> bssids = new ArrayList<>(nets.size());

                for (final Network network : nets) {
                    if (network.getLatLng() == null) {
                        continue;
                    }
                    if (!okForMapTab(network)) {
                        continue;
                    }
                    if (bounds != null) {
                        final LatLng pos = new LatLng(
                                network.getLatLng().latitude,
                                network.getLatLng().longitude);
                        final boolean inBounds = bounds.contains(pos);
                        if (!inBounds && !labeledNetworks.contains(network)) {
                            continue;
                        }
                    }
                    bssids.add(network.getBssid());
                }

                if (!bssids.isEmpty()) {
                    sendUpdateNetwork(bssids);
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

    private void sendUpdateNetwork(@NonNull final ArrayList<String> bssids) {
        final Bundle data = new Bundle();
        data.putStringArrayList(MESSAGE_BSSID_LIST, bssids);
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