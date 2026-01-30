package net.wigle.wigleandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.collections.MarkerManager;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.NetworkIconGenerator;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

/**
 * Custom map rendering: clustering, label decisions, MapUtils functionality
 * [delete this file for FOSS build]
 */
public class MapRender {

    private final ClusterManager<Network> mClusterManager;
    private final NetworkRenderer networkRenderer;
    private final boolean isDbResult;
    private final AtomicInteger networkCount = new AtomicInteger();
    private final SharedPreferences prefs;
    private final GoogleMap map;
    private Matcher ssidMatcher;
    private final Set<Network> labeledNetworks = Collections.newSetFromMap(
            new ConcurrentHashMap<>());

    private static final String MESSAGE_BSSID = "messageBssid";
    private static final String MESSAGE_BSSID_LIST = "messageBssidList";
    //ALIBI: placeholder while we sort out "bubble" icons for BT, BLE, Cell, Cell NR, WiFi encryption types.
    private static final BitmapDescriptor DEFAULT_ICON = BitmapDescriptorFactory.defaultMarker(NetworkHues.DEFAULT);
    private static final BitmapDescriptor DEFAULT_ICON_NEW = BitmapDescriptorFactory.defaultMarker(NetworkHues.NEW);
    private static final BitmapDescriptor CELL_ICON = BitmapDescriptorFactory.defaultMarker(NetworkHues.CELL);
    private static final BitmapDescriptor CELL_ICON_NEW = BitmapDescriptorFactory.defaultMarker(NetworkHues.CELL_NEW);
    private static final BitmapDescriptor BT_ICON = BitmapDescriptorFactory.defaultMarker(NetworkHues.BT);
    private static final BitmapDescriptor BT_ICON_NEW = BitmapDescriptorFactory.defaultMarker(NetworkHues.BT_NEW);
    private static final BitmapDescriptor WIFI_ICON = BitmapDescriptorFactory.defaultMarker(NetworkHues.WIFI);
    private static final BitmapDescriptor WIFI_ICON_NEW = BitmapDescriptorFactory.defaultMarker(NetworkHues.WIFI_NEW);

    private static final float DEFAULT_ICON_ALPHA = 0.7f;
    private static final float CUSTOM_ICON_ALPHA = 0.75f;
    // a % of the cache size can be labels
    private static final int MAX_LABELS = MainActivity.getNetworkCache().maxSize() / 10;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private class NetworkRenderer extends DefaultClusterRenderer<Network> {
        final NetworkIconGenerator iconFactory;

        public NetworkRenderer(Context context, GoogleMap map, ClusterManager<Network> clusterManager) {
            super(context, map, clusterManager);
            iconFactory = new NetworkIconGenerator(context);
        }

        @Override
        protected void onBeforeClusterItemRendered(@NonNull Network network, MarkerOptions markerOptions) {
            // Draw a single network.
            final BitmapDescriptor icon = getIcon(network);
            markerOptions.icon(icon);
            if (icon == DEFAULT_ICON || icon == DEFAULT_ICON_NEW) {
                markerOptions.alpha(DEFAULT_ICON_ALPHA);
            }
            else {
                markerOptions.alpha(CUSTOM_ICON_ALPHA);
            }

            markerOptions.title(network.getSsid());
            final String newString = network.isNew() ? " (new)" : "";
            markerOptions.snippet(network.getBssid() + newString + "\n\t" + network.getChannel() + "\n\t" + network.getCapabilities());
        }

        private BitmapDescriptor getIcon(final Network network) {
            if (showDefaultIcon(network)) {
                MapRender.this.labeledNetworks.remove(network);
                return getPinDescriptor(network);
            }

            MapRender.this.labeledNetworks.add(network);
            return BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(network, network.isNew()));
        }

        private BitmapDescriptor getPinDescriptor(final Network network) {
            switch (network.getType()) {
                case CDMA:
                case GSM:
                case WCDMA:
                case LTE:
                case NR:
                    if (network.isNew()) {
                        return CELL_ICON_NEW;
                    }
                    return CELL_ICON;
                case BT:
                case BLE:
                    if (network.isNew()) {
                        return BT_ICON_NEW;
                    }
                    return BT_ICON;
                case WIFI:
                    if (network.isNew()) {
                        return WIFI_ICON_NEW;
                    }
                    return WIFI_ICON;
                default:
                    if (network.isNew()) {
                        return WIFI_ICON_NEW;
                    }
                    return DEFAULT_ICON;
            }
        }
        private boolean showDefaultIcon(final Network network) {
            final boolean showLabel = prefs.getBoolean( PreferenceKeys.PREF_MAP_LABEL, true );
            if (showLabel) {
                final LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
                if (bounds == null || network.getLatLng() == null) return false;
                // if on screen, and room in labeled networks, we can show the label
                return !bounds.contains(network.getPosition()) || MapRender.this.labeledNetworks.size() > MAX_LABELS;
            }
            return true;
        }

        @Override
        protected void onBeforeClusterRendered(@NonNull Cluster<Network> cluster, @NonNull MarkerOptions markerOptions) {
            // Draw a cluster
            super.onBeforeClusterRendered(cluster, markerOptions);
            markerOptions.title(cluster.getSize() + " Networks");
            StringBuilder builder = new StringBuilder();
            int count = 0;
            for (final Network network : cluster.getItems()) {
                final String ssid = network.getSsid();
                if (!ssid.equals("")) {
                    if (count > 0) {
                        builder.append(", ");
                    }
                    count++;
                    builder.append(ssid);
                }
                if (count > 20) {
                    break;
                }
            }
            markerOptions.snippet(builder.toString());
        }

        @Override
        protected boolean shouldRenderAsCluster(@NonNull Cluster<Network> cluster) {
            return (prefs.getBoolean( PreferenceKeys.PREF_MAP_CLUSTER, true ) && cluster.getSize() > 4)
                    || cluster.getSize() >= 100;
        }

        protected void updateItem(final Network network) {
            final Marker marker = this.getMarker(network);
            if (marker != null) {
                if (showDefaultIcon(network)) {
                    if (marker.getAlpha() != DEFAULT_ICON_ALPHA) {
                        marker.setIcon(getIcon(network));
                        marker.setAlpha(DEFAULT_ICON_ALPHA);
                    }
                }
                else {
                    if (marker.getAlpha() == DEFAULT_ICON_ALPHA) {
                        marker.setIcon(getIcon(network));
                        marker.setAlpha(CUSTOM_ICON_ALPHA);
                    }
                }
            }
            else if (network.isNew()) {
                // handle case where network was not added before because it is not new
                final boolean showNewDBOnly = prefs.getBoolean( PreferenceKeys.PREF_MAP_ONLY_NEWDB, false );
                if (showNewDBOnly) {
                    mClusterManager.addItem(network);
                    mClusterManager.cluster();
                }
            }
        }

        private void setupRelabelingTask() {
            // setup camera change listener to fire the asynctask
            Handler handler = new Handler(Looper.getMainLooper());
            final LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
            map.setOnCameraIdleListener(() -> executor.execute(() -> {
                final Collection<Network> nets = MainActivity.getNetworkCache().values();
                final ArrayList<String> ssids = new ArrayList<>(nets.size());
                for (final Network network : nets) {
                    final Marker marker = NetworkRenderer.this.getMarker(network);
                    if (marker != null && network.getLatLng() != null) {
                        final boolean inBounds = bounds.contains(network.getPosition());
                        if (inBounds || MapRender.this.labeledNetworks.contains(network)) {
                            // MainActivity.info("sendupdate: " + network.getBssid());
                            ssids.add(network.getBssid());
                        }
                    }
                }
                if (!ssids.isEmpty()) {
                    sendUpdateNetwork(ssids);
                }
                handler.post(mClusterManager::cluster);
            }));
        }
    }

    public MapRender(final Context context, final GoogleMap map, final boolean isDbResult) {
        this.map = map;
        this.isDbResult = isDbResult;
        prefs = context.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
        ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, MappingFragment.MAP_DIALOG_PREFIX );
        mClusterManager = new ClusterManager<>(context, map);
        networkRenderer = new NetworkRenderer(context, map, mClusterManager);
        mClusterManager.setRenderer(networkRenderer);
        map.setOnCameraIdleListener(mClusterManager);

        MarkerManager.Collection markerCollection = mClusterManager.getMarkerCollection();
        markerCollection.setOnInfoWindowClickListener(marker -> {
            return;
        });
        markerCollection.setOnMarkerClickListener(marker -> false);

        mClusterManager.setOnClusterItemClickListener(item -> false);
        mClusterManager.setOnClusterItemInfoWindowClickListener(item -> {
            return;
        });

        if (!isDbResult) {
            // setup the relabeling background task
            networkRenderer.setupRelabelingTask();
        }

        reCluster();
    }

    private void addLatestNetworks() {
        // add items
        int cached = 0;
        int added = 0;
        final Collection<Network> nets = MainActivity.getNetworkCache().values();

        for (final Network network : nets) {
            cached++;
            if (okForMapTab(network)) {
                added++;
                mClusterManager.addItem(network);
            }
        }
        Logging.info("MapRender cached: " + cached + " added: " + added);
        networkCount.getAndAdd(added);

        mClusterManager.cluster();
    }

    public boolean okForMapTab( final Network network ) {
        final boolean hideNets = prefs.getBoolean( PreferenceKeys.PREF_MAP_HIDE_NETS, false );
        final boolean showNewDBOnly = prefs.getBoolean( PreferenceKeys.PREF_MAP_ONLY_NEWDB, false )
                && ! isDbResult;
        if (network.getPosition() != null && !hideNets) {
            if (!showNewDBOnly || network.isNew()) {
                return FilterMatcher.isOk(ssidMatcher,
                        null /*ALIBI: we *can* use the filter from the list filter view here ...*/,
                        prefs, MappingFragment.MAP_DIALOG_PREFIX, network);
            }
        }
        return false;
    }

    public void addItem(final Network network) {
        if (network.getPosition() != null) {
            final int count = networkCount.incrementAndGet();
            if (count > MainActivity.getNetworkCache().size() * 1.3) {
                // getting too large, re-sync with cache
                reCluster();
            }
            else {
                mClusterManager.addItem(network);
                mClusterManager.cluster();
            }
        }
    }

    public void clear() {
        Logging.info("MapRender: clear");
        labeledNetworks.clear();
        networkCount.set(0);
        mClusterManager.clearItems();
        // mClusterManager.setRenderer(networkRenderer);
    }

    public void reCluster() {
        Logging.info("MapRender: reCluster");
        clear();
        if (!isDbResult) {
            addLatestNetworks();
        }
    }

    public void onResume() {
        ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, MappingFragment.MAP_DIALOG_PREFIX );
        reCluster();
    }

    public void updateNetwork(final Network network) {
        if (okForMapTab(network)) {
            sendUpdateNetwork(network.getBssid());
        }
    }

    private void sendUpdateNetwork(final String bssid) {
        final Bundle data = new Bundle();
        data.putString(MESSAGE_BSSID, bssid);
        Message message = new Message();
        message.setData(data);
        updateMarkersHandler.sendMessage(message);
    }

    private void sendUpdateNetwork(final ArrayList<String> bssids) {
        final Bundle data = new Bundle();
        data.putStringArrayList(MESSAGE_BSSID_LIST, bssids);
        Message message = new Message();
        message.setData(data);
        updateMarkersHandler.sendMessage(message);
    }

    final Handler updateMarkersHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(final Message message) {
            final String bssid = message.getData().getString(MESSAGE_BSSID);
            if (bssid != null) {
                // MainActivity.info("handleMessage: " + bssid);
                final Network network = MainActivity.getNetworkCache().get(bssid);
                if (network != null) {
                    networkRenderer.updateItem(network);
                }
            }

            final ArrayList<String> bssids = message.getData().getStringArrayList(MESSAGE_BSSID_LIST);
            if (bssids != null && !bssids.isEmpty()) {
                Logging.info("bssids: " + bssids.size());
                for (final String thisBssid : bssids) {
                    final Network network = MainActivity.getNetworkCache().get(thisBssid);
                    if (network != null) {
                        networkRenderer.updateItem(network);
                    }
                }
            }
        }
    };

    private static final class NetworkHues {
        public static final float WIFI = 92.90f;     // #87A96B
        public static final float WIFI_NEW = 97.67f; // #85BB65
        public static final float BT = 220.9f;        // #4682B
        public static final float BT_NEW = 210.88f;  // #99BADD
        public static final float CELL = 0.0f;       // #B22222
        public static final float CELL_NEW = 4.8f;   // #E34234
        public static final float DEFAULT = 110.0f;  // TODO: establish a better default
        public static final float NEW = 120.0f;      // TODO: ""
    }
}
