package net.wigle.wigleandroid;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.location.Address;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.QueryArgs;

public class DBResultActivity extends ActionBarActivity {
    private static final int MENU_RETURN = 12;
    private static final int MSG_QUERY_DONE = 2;
    private static final int LIMIT = 50;

    private static final int DEFAULT_ZOOM = 18;

    private NetworkListAdapter listAdapter;
    private MapView mapView;
    private MapRender mapRender;
    private final List<Network> resultList = new ArrayList<>();
    private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>();

    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // set language
        MainActivity.setLocale( this );
        setContentView( R.layout.dbresult );

        // force media volume controls
        setVolumeControlStream( AudioManager.STREAM_MUSIC );

        setupList();

        QueryArgs queryArgs = ListFragment.lameStatic.queryArgs;
        MainActivity.info("queryArgs: " + queryArgs);
        final TextView tv = (TextView) findViewById( R.id.dbstatus );

        if ( queryArgs != null ) {
            tv.setText( getString(R.string.status_working) + "...");
            Address address = queryArgs.getAddress();
            LatLng center = MappingFragment.DEFAULT_POINT;
            if ( address != null ) {
                center = new LatLng(address.getLatitude(), address.getLongitude());
            }
            setupMap( center, savedInstanceState );
            setupQuery( queryArgs );
        }
        else {
            tv.setText(getString(R.string.status_fail) + "...");
        }
    }

    private void setupList() {
        // not set by nonconfig retain
        listAdapter = new NetworkListAdapter( getApplicationContext(), R.layout.row );
        final ListView listView = (ListView) findViewById( R.id.dblist );
        ListFragment.setupListAdapter( listView, MainActivity.getMainActivity(), listAdapter, true );
    }

    private void setupMap( final LatLng center, final Bundle savedInstanceState ) {
        mapView = new MapView( this );
        mapView.onCreate(savedInstanceState);
        MapsInitializer.initialize(this);

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap googleMap) {
                mapRender = new MapRender(DBResultActivity.this, googleMap, true);

                if (center != null) {
                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(center).zoom(DEFAULT_ZOOM).build();
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }
            }
        });

        final RelativeLayout rlView = (RelativeLayout) findViewById( R.id.db_map_rl );
        rlView.addView( mapView );
    }

    @SuppressLint("HandlerLeak")
    private void setupQuery( final QueryArgs queryArgs ) {
        final Address address = queryArgs.getAddress();

        // what runs on the gui thread
        final Handler handler = new Handler() {
            @Override
            public void handleMessage( final Message msg ) {
                final TextView tv = (TextView) findViewById( R.id.dbstatus );

                if ( msg.what == MSG_QUERY_DONE ) {

                    tv.setText( getString(R.string.status_success)  );

                    listAdapter.clear();
                    boolean first = true;
                    for ( final Network network : resultList ) {
                        listAdapter.add( network );
                        if ( address == null && first ) {
                            final LatLng center = MappingFragment.getCenter( DBResultActivity.this, network.getLatLng(), null );
                            MainActivity.info( "set center: " + center + " network: " + network.getSsid()
                                    + " point: " + network.getLatLng());
                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(final GoogleMap googleMap) {
                                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                                            .target(center).zoom(DEFAULT_ZOOM).build();
                                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                                }
                            });

                            first = false;
                        }

                        if (network.getLatLng() != null && mapRender != null) {
                            mapRender.addItem(network);
                        }
                    }
                    resultList.clear();
                }
            }
        };

        String sql = "SELECT bssid,lastlat,lastlon FROM " + DatabaseHelper.NETWORK_TABLE + " WHERE 1=1 ";
        final String ssid = queryArgs.getSSID();
        final String bssid = queryArgs.getBSSID();
        boolean limit = false;
        if ( ssid != null && ! "".equals(ssid) ) {
            sql += " AND ssid like " + DatabaseUtils.sqlEscapeString(ssid);
            limit = true;
        }
        if ( bssid != null && ! "".equals(bssid) ) {
            sql += " AND bssid like " + DatabaseUtils.sqlEscapeString(bssid);
            limit = true;
        }
        if ( address != null ) {
            final double diff = 0.1d;
            final double lat = address.getLatitude();
            final double lon = address.getLongitude();
            sql += " AND lastlat > '" + (lat - diff) + "' AND lastlat < '" + (lat + diff) + "'";
            sql += " AND lastlon > '" + (lon - diff) + "' AND lastlon < '" + (lon + diff) + "'";
        }
        if ( limit ) {
            sql += " LIMIT " + LIMIT;
        }

        final TreeMap<Float,String> top = new TreeMap<>();
        final float[] results = new float[1];
        final long[] count = new long[1];

        final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
            @Override
            public boolean handleRow( final Cursor cursor ) {
                final String bssid = cursor.getString(0);
                final float lat = cursor.getFloat(1);
                final float lon = cursor.getFloat(2);
                count[0]++;

                if ( address == null ) {
                    top.put( (float) count[0], bssid );
                }
                else {
                    Location.distanceBetween( lat, lon, address.getLatitude(), address.getLongitude(), results );
                    final float meters = results[0];

                    if ( top.size() <= LIMIT ) {
                        putWithBackoff( top, bssid, meters );
                    }
                    else {
                        Float last = top.lastKey();
                        if ( meters < last ) {
                            top.remove( last );
                            putWithBackoff( top, bssid, meters );
                        }
                    }
                }

                return true;
            }

            @Override
            public void complete() {
                for ( final String bssid : top.values() ) {
                    final Network network = ListFragment.lameStatic.dbHelper.getNetwork( bssid );
                    resultList.add( network );
                    final LatLng point = network.getLatLng();
                    if (point != null) {
                        obsMap.put(point, 0);
                    }
                }

                handler.sendEmptyMessage( MSG_QUERY_DONE );
                if ( mapView != null ) {
                    // force a redraw
                    mapView.postInvalidate();
                }
            }
        });

        // queue it up
        ListFragment.lameStatic.dbHelper.addToQueue( request );
    }

    private static void putWithBackoff( TreeMap<Float,String> top, String s, float diff ) {
        String old = top.put( diff, s );
        // protect against infinite loops
        int count = 0;
        while ( old != null && count < 1000 ) {
            // ut oh, two at the same difference away. add a slight bit and put it back
            // info( "collision at diff: " + diff + " old: " + old.getCallsign() + " orig: " + s.getCallsign() );
            diff += 0.0001f;
            old = top.put( diff, old );
            count++;
        }
    }

    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
        MenuItem item = menu.add(0, MENU_RETURN, 0, getString(R.string.menu_return));
        item.setIcon( android.R.drawable.ic_media_previous );

        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        switch ( item.getItemId() ) {
            case MENU_RETURN:
                finish();
                return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        if (mapRender != null) {
            // save memory
            mapRender.clear();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }


}
