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
import com.google.maps.android.ui.IconGenerator;

import net.wigle.wigleandroid.model.Network;
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
    private static final BitmapDescriptor DEFAULT_ICON = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
    private static final BitmapDescriptor DEFAULT_ICON_NEW = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN);
    private static final float DEFAULT_ICON_ALPHA = 0.75f;
    private static final float CUSTOM_ICON_ALPHA = 0.80f;
    // a % of the cache size can be labels
    private static final int MAX_LABELS = MainActivity.getNetworkCache().maxSize() / 15;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private class NetworkRenderer extends DefaultClusterRenderer<Network> {
        final IconGenerator iconFactory;

        public NetworkRenderer(Context context, GoogleMap map, ClusterManager<Network> clusterManager) {
            super(context, map, clusterManager);
            iconFactory = new IconGenerator(context);
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
                return network.isNew() ? DEFAULT_ICON_NEW : DEFAULT_ICON;
            }

            MapRender.this.labeledNetworks.add(network);
            if ( network.isNew() ) {
                iconFactory.setStyle(IconGenerator.STYLE_WHITE);
            }
            else {
                iconFactory.setStyle(IconGenerator.STYLE_BLUE);
            }
            return BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(network.getSsid()));
        }

        private boolean showDefaultIcon(final Network network) {
            final boolean showLabel = prefs.getBoolean( PreferenceKeys.PREF_MAP_LABEL, true );
            if (showLabel) {
                final LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
                if (bounds == null || network.getLatLng() == null) return false;
                // if on screen, and room in labeled networks, we can show the label
                return !bounds.contains(network.getLatLng()) || MapRender.this.labeledNetworks.size() > MAX_LABELS;
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
                        final boolean inBounds = bounds.contains(network.getLatLng());
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

}
