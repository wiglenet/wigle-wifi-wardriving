package net.wigle.wigleandroid;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import android.database.Cursor;
import android.location.Address;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import net.wigle.wigleandroid.background.PooledQueryExecutor;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.model.api.WiFiSearchResponse;
import net.wigle.wigleandroid.net.RequestCompletedListener;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;

import org.json.JSONObject;

public class DBResultActivity extends AppCompatActivity {
    private static final int MENU_RETURN = 12;
    private static final int LIMIT = 50;

    private static final int DEFAULT_ZOOM = 18;

    private static final String API_LAT1_PARAM = "latrange1";
    private static final String API_LAT2_PARAM = "latrange2";
    private static final String API_LON1_PARAM = "longrange1";
    private static final String API_LON2_PARAM = "longrange2";
    private static final String API_BSSID_PARAM = "netid";
    private static final String API_SSIDLIKE_PARAM = "ssidlike";
    private static final String API_SSID_PARAM = "ssid";

    private static final Double LOCAL_RANGE = 0.1d;
    private static final Double ONLINE_RANGE = 0.001d; //ALIBI: online DB coverage mandates tighter bounds.

    private SetNetworkListAdapter listAdapter;
    private MapView mapView;
    private MapRender mapRender;
    private final List<Network> resultList = new ArrayList<>();
    private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>();
    private WiFiSearchResponse searchResponse;

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
        final TextView tv = findViewById( R.id.dbstatus );

        if ( queryArgs != null ) {
            tv.setText( getString(R.string.status_working)); //TODO: throbber/overlay?
            Address address = queryArgs.getAddress();
            LatLng center = MappingFragment.DEFAULT_POINT;
            if ( address != null ) {
                center = new LatLng(address.getLatitude(), address.getLongitude());
            }
            setupMap( center, savedInstanceState );
            if (queryArgs.searchWiGLE()) {
                setupWiGLEQuery(queryArgs);
            } else {
                setupQuery(queryArgs);
            }
        }
        else {
            tv.setText(getString(R.string.status_fail) + "...");
        }
    }

    private void setupList() {
        // not set by nonconfig retain
        listAdapter = new SetNetworkListAdapter( this, R.layout.row );
        final ListView listView = findViewById( R.id.dblist );
        ListFragment.setupListAdapter( listView, MainActivity.getMainActivity(), listAdapter, true );
    }

    private void setupMap( final LatLng center, final Bundle savedInstanceState ) {
        mapView = new MapView( this );
        mapView.onCreate(savedInstanceState);
        MapsInitializer.initialize(this);

        mapView.getMapAsync(googleMap -> {
            mapRender = new MapRender(DBResultActivity.this, googleMap, true);

            if (center != null) {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(center).zoom(DEFAULT_ZOOM).build(); //TODO: zoom all the way out instead?
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
        });

        final RelativeLayout rlView = findViewById( R.id.db_map_rl );
        rlView.addView( mapView );
    }

    private void setupQuery( final QueryArgs queryArgs ) {
        final Address address = queryArgs.getAddress();

        String sql = "SELECT bssid,lastlat,lastlon FROM " + DatabaseHelper.NETWORK_TABLE + " WHERE 1=1 ";
        final String ssid = queryArgs.getSSID();
        final String bssid = queryArgs.getBSSID();
        boolean limit = false;
        List<String> params = new ArrayList<>();
        if ( ssid != null && ! "".equals(ssid) ) {
            sql += " AND ssid like ?"; // + DatabaseUtils.sqlEscapeString(ssid);
            params.add(ssid);
            limit = true;
        }
        if ( bssid != null && ! "".equals(bssid) ) {
            sql += " AND bssid like ?"; // + DatabaseUtils.sqlEscapeString(bssid);
            params.add(bssid);
            limit = true;
        }
        if ( address != null ) {
            sql += " AND lastlat > ? AND lastlat < ? AND lastlon > ? AND lastlon < ?";
            final double lat = address.getLatitude();
            final double lon = address.getLongitude();
            params.add((lat - LOCAL_RANGE)+"");
            params.add((lat + LOCAL_RANGE)+"");
            params.add((lon - LOCAL_RANGE)+"");
            params.add((lon + LOCAL_RANGE)+"");
        }
        if ( limit ) {
            sql += " LIMIT ?"; // + LIMIT;
            params.add(LIMIT+"");
        }

        final TreeMap<Float,String> top = new TreeMap<>();
        final float[] results = new float[1];
        final long[] count = new long[1];
        final Handler handler = new Handler(Looper.getMainLooper());
        final PooledQueryExecutor.Request request = new PooledQueryExecutor.Request( sql, params.toArray(new String[0]),
                new PooledQueryExecutor.ResultHandler() {
            @Override
            public boolean handleRow( final Cursor cursor ) {
                final String bssid = cursor.getString(0);
                final float lat = cursor.getFloat(1);
                final float lon = cursor.getFloat(2);
                count[0]++;

                if ( address == null ) {
                    top.put( (float) count[0], bssid );
                } else {
                    Location.distanceBetween( lat, lon, address.getLatitude(), address.getLongitude(), results );
                    final float meters = results[0];

                    if ( top.size() <= LIMIT ) {
                        putWithBackoff( top, bssid, meters );
                    } else {
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
                if (top.values().size() > 0) {
                    resultList.clear();
                    for (final String bssid : top.values()) {
                        final Network network = ListFragment.lameStatic.dbHelper.getNetwork(bssid);
                        resultList.add(network);
                        final LatLng point = network.getLatLng();
                        if (point != null) {
                            obsMap.put(point, 0);
                        }
                    }
                    handler.post(() -> {
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        for (Network n: resultList) {
                            listAdapter.add(n);
                            mapRender.addItem(n);
                            final LatLng ll = n.getPosition();
                            //noinspection ConstantConditions
                            if (ll != null) builder.include(ll);
                        }
                        mapView.getMapAsync(googleMap -> googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0)));
                        resultList.clear();
                    });
                } else {
                    handler.post(() -> handleFailedRequest());
                }
            }
        }, ListFragment.lameStatic.dbHelper);

        PooledQueryExecutor.enqueue( request );
    }

    private void handleResults() {
        final TextView tv = findViewById(R.id.dbstatus);
        tv.setText(getString(R.string.status_success));
        listAdapter.clear();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (WiFiSearchResponse.WiFiNetwork net : searchResponse.getResults()) {
            if (null != net) {
                final Network n = WiFiSearchResponse.asNetwork(net);
                listAdapter.add(n);
                builder.include(n.getPosition());

                if (n.getLatLng() != null && mapRender != null) {
                    mapRender.addItem(n);
                }
            }
        }
        if (!listAdapter.isEmpty()) {
            try {
                mapView.getMapAsync(googleMap -> googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0)));
            } catch (IllegalStateException ise) {
                Logging.error("Illegal state exception on map move: ", ise);
            }
        }
        resultList.clear();
    }

    private void handleFailedRequest() {
        final TextView tv = findViewById( R.id.dbstatus );
        tv.setText( getString(R.string.search_empty)  );
        listAdapter.clear();
        WiGLEToast.showOverActivity(this, R.string.app_name,
                getString(R.string.search_empty), Toast.LENGTH_LONG);
    }

    private void setupWiGLEQuery(final QueryArgs queryArgs) {

        String queryParams = "";
        if (queryArgs.getSSID() != null && !queryArgs.getSSID().isEmpty()) {

            if (queryArgs.getSSID().contains("%") || queryArgs.getSSID().contains("_")) {
                queryParams+=API_SSIDLIKE_PARAM+"="+URLEncoder.encode((queryArgs.getSSID()));
            } else {
                queryParams+=API_SSID_PARAM+"="+URLEncoder.encode((queryArgs.getSSID()));
            }
        }

        if (queryArgs.getBSSID() != null && !queryArgs.getBSSID().isEmpty()) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }

            queryParams+=API_BSSID_PARAM+"="+(queryArgs.getBSSID());
        }

        final Address address = queryArgs.getAddress();
        if (address != null) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }

            final double lat = address.getLatitude();
            final double lon = address.getLongitude();

            queryParams+=API_LAT1_PARAM+"="+(lat - ONLINE_RANGE)+"&";
            queryParams+=API_LAT2_PARAM+"="+(lat + ONLINE_RANGE)+"&";
            queryParams+=API_LON1_PARAM+"="+(lon - ONLINE_RANGE)+"&";
            queryParams+=API_LON2_PARAM+"="+(lon + ONLINE_RANGE);
        }

        final MainActivity.State s = MainActivity.getStaticState();
        if (null != s) {
            s.apiManager.searchWiFi(queryParams, new RequestCompletedListener<WiFiSearchResponse, JSONObject>() {
                @Override
                public void onTaskCompleted() {
                    if (null != searchResponse) {
                        handleResults();
                    } else {
                        handleFailedRequest();
                    }
                }

                @Override
                public void onTaskSucceeded(WiFiSearchResponse response) {
                    searchResponse = response;
                }

                @Override
                public void onTaskFailed(int status, JSONObject error) {
                    searchResponse = null;
                }
            });
        }
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
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (null != mapView) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        if (mapRender != null) {
            // save memory
            mapRender.clear();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}
