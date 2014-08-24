package net.wigle.wigleandroid;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.ui.IconGenerator;

public class MapRender implements ClusterManager.OnClusterClickListener<Network>,
    ClusterManager.OnClusterInfoWindowClickListener<Network>,
    ClusterManager.OnClusterItemClickListener<Network>,
    ClusterManager.OnClusterItemInfoWindowClickListener<Network> {

  private final ClusterManager<Network> mClusterManager;
  private final NetworkRenderer networkRenderer;
  private final boolean isDbResult;
  private final AtomicInteger networkCount = new AtomicInteger();

  private class NetworkRenderer extends DefaultClusterRenderer<Network> {
    final IconGenerator iconFactory;
    final SharedPreferences prefs;

    public NetworkRenderer(Context context, GoogleMap map, ClusterManager<Network> clusterManager) {
      super(context, map, clusterManager);
      iconFactory = new IconGenerator(context);
      prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
    }

    @Override
    protected void onBeforeClusterItemRendered(Network network, MarkerOptions markerOptions) {
      // Draw a single network.
      final BitmapDescriptor icon = getIcon(network);
      if (icon != null) {
        markerOptions.icon(icon);
      }

      markerOptions.title(network.getSsid());
      final String newString = network.isNew() ? " (new)" : "";
      markerOptions.snippet(network.getBssid() + newString + " - " + network.getChannel() + " - " + network.getCapabilities());
    }

    private BitmapDescriptor getIcon(final Network network) {
      final boolean showLabel = prefs.getBoolean( ListFragment.PREF_MAP_LABEL, true );
      if (showLabel) {
        if ( network.isNew() ) {
          iconFactory.setStyle(IconGenerator.STYLE_WHITE);
        }
        else {
          iconFactory.setStyle(IconGenerator.STYLE_BLUE);
        }
        return BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(network.getSsid()));
      }
      return null;
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
      if (network.isNew()) {
        final Marker marker = this.getMarker(network);
        if (marker != null) {
          final BitmapDescriptor icon = getIcon(network);
          marker.setIcon(icon);
        }
        else {
          final boolean showNewDBOnly = prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false );
          if (showNewDBOnly) {
            mClusterManager.addItem(network);
            reCluster();
          }
        }
      }
    }

  }

  public MapRender(final Context context, final GoogleMap map, final boolean isDbResult) {
    this.isDbResult = isDbResult;
    mClusterManager = new ClusterManager<Network>(context, map);
    networkRenderer = new NetworkRenderer(context, map, mClusterManager);
    mClusterManager.setRenderer(networkRenderer);
    map.setOnCameraChangeListener(mClusterManager);
    map.setOnMarkerClickListener(mClusterManager);
    map.setOnInfoWindowClickListener(mClusterManager);
    mClusterManager.setOnClusterClickListener(this);
    mClusterManager.setOnClusterInfoWindowClickListener(this);
    mClusterManager.setOnClusterItemClickListener(this);
    mClusterManager.setOnClusterItemInfoWindowClickListener(this);

    reCluster();
  }

  private void addLatestNetworks() {
    // add items
    int cached = 0;
    int added = 0;
    final Collection<Network> nets = MainActivity.getNetworkCache().values();
    final boolean showNewDBOnly = networkRenderer.prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false )
         && ! isDbResult;

    for (final Network network : nets) {
      cached++;
      if (network.getPosition() != null) {
        if (!showNewDBOnly || network.isNew()) {
          added++;
          mClusterManager.addItem(network);
        }
      }
    }
    MainActivity.info("MapRender cached: " + cached + " added: " + added);
    networkCount.getAndAdd(added);

    mClusterManager.cluster();
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

  public void updateItem(final Network network) {
    networkRenderer.updateItem(network);
  }

  public void clear() {
    MainActivity.info("MapRender: clear");
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

}
