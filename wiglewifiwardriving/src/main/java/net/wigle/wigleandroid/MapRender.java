package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.ui.IconGenerator;

import net.wigle.wigleandroid.model.Network;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

public class MapRender implements ClusterManager.OnClusterClickListener<Network>,
        ClusterManager.OnClusterInfoWindowClickListener<Network>,
        ClusterManager.OnClusterItemClickListener<Network>,
        ClusterManager.OnClusterItemInfoWindowClickListener<Network> {

    private final ClusterManager<Network> mClusterManager;
    private final NetworkRenderer networkRenderer;
    private final boolean isDbResult;
    private final AtomicInteger networkCount = new AtomicInteger();
    private final SharedPreferences prefs;
    private final GoogleMap map;
    private Matcher ssidMatcher;
    private final Set<Network> labeledNetworks = Collections.newSetFromMap(
            new ConcurrentHashMap<Network,Boolean>());

    private static final String MESSAGE_BSSID = "messageBssid";
    private static final BitmapDescriptor DEFAULT_ICON = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
    private static final BitmapDescriptor DEFAULT_ICON_NEW = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN);
    private static final float DEFAULT_ICON_ALPHA = 0.75f;
    private static final float CUSTOM_ICON_ALPHA = 0.80f;
    // a % of the cache size can be labels
    private static final int MAX_LABELS = MainActivity.getNetworkCache().maxSize() / 15;

    private class NetworkRenderer extends DefaultClusterRenderer<Network> {
        final IconGenerator iconFactory;

        public NetworkRenderer(Context context, GoogleMap map, ClusterManager<Network> clusterManager) {
            super(context, map, clusterManager);
            iconFactory = new IconGenerator(context);
        }

        @Override
        protected void onBeforeClusterItemRendered(Network network, MarkerOptions markerOptions) {
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
            markerOptions.snippet(network.getBssid() + newString + " - " + network.getChannel() + " - " + network.getCapabilities());
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
            final boolean showLabel = prefs.getBoolean( ListFragment.PREF_MAP_LABEL, true );
            if (showLabel) {
                final LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
                // if on screen, and room in labeled networks, we can show the label
                if (bounds.contains(network.getLatLng()) && MapRender.this.labeledNetworks.size() <= MAX_LABELS) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected void onBeforeClusterRendered(Cluster<Network> cluster, MarkerOptions markerOptions) {
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
        protected boolean shouldRenderAsCluster(Cluster<Network> cluster) {
            return (prefs.getBoolean( ListFragment.PREF_MAP_CLUSTER, true ) && cluster.getSize() > 4)
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
                final boolean showNewDBOnly = prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false );
                if (showNewDBOnly) {
                    mClusterManager.addItem(network);
                    mClusterManager.cluster();
                }
            }
        }

        private void setupRelabelingTask() {
            // setup camera change listener to fire the asynctask
            map.setOnCameraChangeListener(new OnCameraChangeListener() {
                @Override
                public void onCameraChange(CameraPosition position) {
                    new DynamicallyAddMarkerTask().execute(map.getProjection().getVisibleRegion().latLngBounds);
                }
            });
        }

        private class DynamicallyAddMarkerTask extends AsyncTask<LatLngBounds, Integer, Void> {
            @Override
            protected Void doInBackground(LatLngBounds... bounds) {
                final Collection<Network> nets = MainActivity.getNetworkCache().values();
                for (final Network network : nets) {
                    final Marker marker = NetworkRenderer.this.getMarker(network);
                    if (marker != null && network.getLatLng() != null) {
                        final boolean inBounds = bounds[0].contains(network.getLatLng());
                        if (inBounds || MapRender.this.labeledNetworks.contains(network)) {
                            // MainActivity.info("sendupdate: " + network.getBssid());
                            sendUpdateNetwork(network.getBssid());
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                mClusterManager.cluster();
            }
        }

    }

    public MapRender(final Context context, final GoogleMap map, final boolean isDbResult) {
        this.map = map;
        this.isDbResult = isDbResult;
        prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        ssidMatcher = FilterMatcher.getSsidFilterMatcher( prefs, MappingFragment.MAP_DIALOG_PREFIX );
        mClusterManager = new ClusterManager<>(context, map);
        networkRenderer = new NetworkRenderer(context, map, mClusterManager);
        mClusterManager.setRenderer(networkRenderer);
        map.setOnCameraChangeListener(mClusterManager);
        map.setOnMarkerClickListener(mClusterManager);
        map.setOnInfoWindowClickListener(mClusterManager);
        mClusterManager.setOnClusterClickListener(this);
        mClusterManager.setOnClusterInfoWindowClickListener(this);
        mClusterManager.setOnClusterItemClickListener(this);
        mClusterManager.setOnClusterItemInfoWindowClickListener(this);

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
        MainActivity.info("MapRender cached: " + cached + " added: " + added);
        networkCount.getAndAdd(added);

        mClusterManager.cluster();
    }

    public boolean okForMapTab( final Network network ) {
        final boolean showNewDBOnly = prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false )
                && ! isDbResult;
        if (network.getPosition() != null) {
            if (!showNewDBOnly || network.isNew()) {
                if (FilterMatcher.isOk(ssidMatcher,
                        null /*ALIBI: we *can* use the filter from the list filter view here ...*/,
                        prefs, MappingFragment.MAP_DIALOG_PREFIX, network)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onClusterItemInfoWindowClick(Network item) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onClusterItemClick(Network item) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onClusterInfoWindowClick(Cluster<Network> cluster) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean onClusterClick(Cluster<Network> cluster) {
        // TODO Auto-generated method stub
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
        MainActivity.info("MapRender: clear");
        labeledNetworks.clear();
        networkCount.set(0);
        mClusterManager.clearItems();
        // mClusterManager.setRenderer(networkRenderer);
    }

    public void reCluster() {
        MainActivity.info("MapRender: reCluster");
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

    @SuppressLint("HandlerLeak")
    final Handler updateMarkersHandler = new Handler() {
        @Override
        public void handleMessage(final Message message) {
            final String bssid = message.getData().getString(MESSAGE_BSSID);
            // MainActivity.info("handleMessage: " + bssid);
            final Network network = MainActivity.getNetworkCache().get(bssid);
            if (network != null) {
                networkRenderer.updateItem(network);
            }
        }
    };

}
