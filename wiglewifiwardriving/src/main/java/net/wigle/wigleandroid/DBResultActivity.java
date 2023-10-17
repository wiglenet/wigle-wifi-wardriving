package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.model.Network.RSN_CAP;
import static net.wigle.wigleandroid.model.Network.SAE_CAP;
import static net.wigle.wigleandroid.model.Network.SUITE_B_192_CAP;
import static net.wigle.wigleandroid.model.Network.WEP_CAP;
import static net.wigle.wigleandroid.model.Network.WPA2_CAP;
import static net.wigle.wigleandroid.model.Network.WPA3_CAP;
import static net.wigle.wigleandroid.model.Network.WPA_CAP;
import static net.wigle.wigleandroid.model.NetworkFilterType.BT;
import static net.wigle.wigleandroid.model.NetworkFilterType.CELL;
import static net.wigle.wigleandroid.model.NetworkFilterType.WIFI;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentActivity;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.RelativeLayout;
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
import net.wigle.wigleandroid.model.NetworkFilterType;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.model.WiFiSecurityType;
import net.wigle.wigleandroid.model.api.BtSearchResponse;
import net.wigle.wigleandroid.model.api.CellSearchResponse;
import net.wigle.wigleandroid.model.api.WiFiSearchResponse;
import net.wigle.wigleandroid.net.AuthenticatedRequestCompletedListener;
import net.wigle.wigleandroid.ui.ProgressThrobberActivity;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.WiGLEAuthDialog;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.json.JSONObject;

public class DBResultActivity extends ProgressThrobberActivity {
    private static final int MENU_RETURN = 12;
    private static final int LIMIT = 50;

    private static final int DEFAULT_ZOOM = 18;

    private static final String API_LAT1_PARAM = "latrange1";
    private static final String API_LAT2_PARAM = "latrange2";
    private static final String API_LON1_PARAM = "longrange1";
    private static final String API_LON2_PARAM = "longrange2";
    private static final String API_BSSID_PARAM = "netid";
    private static final String API_CELL_OP_PARAM = "cell_op";
    private static final String API_CELL_NET_PARAM = "cell_net";
    private static final String API_CELL_ID_PARAM = "cell_id";
    private static final String API_BT_NAME_PARAM = "name";
    private static final String API_BT_NAMELIKE_PARAM = "namelike";
    private static final String API_SSIDLIKE_PARAM = "ssidlike";
    private static final String API_SSID_PARAM = "ssid";
    private static final String API_ENCRYPTION_PARAM = "encryption";

    private SetNetworkListAdapter listAdapter;
    private MapView mapView;
    private MapRender mapRender;
    private final List<Network> resultList = new ArrayList<>();
    private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>();
    private WiFiSearchResponse searchResponse;
    private BtSearchResponse btSearchResponse;
    private CellSearchResponse cellSearchResponse;

    private boolean queryFailed;

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
        loadingImage = findViewById(R.id.search_throbber);
        errorImage = findViewById(R.id.search_error);

        if ( queryArgs != null ) {
            startAnimation();

            LatLng center = MappingFragment.DEFAULT_POINT;
            LatLngBounds bounds = queryArgs.getLocationBounds();
            if ( bounds != null ) {
                center = new LatLng(bounds.getCenter().latitude, bounds.getCenter().longitude);
            }
            final SharedPreferences prefs = this.getApplicationContext().
                    getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            setupMap( center, savedInstanceState, prefs );
            if (queryArgs.searchWiGLE()) {
                setupWiGLEQuery(queryArgs);
            } else {
                setupQuery(queryArgs);
            }
        }
    }

    private void setupList() {
        // not set by nonconfig retain
        listAdapter = new SetNetworkListAdapter( this, R.layout.row );
        final ListView listView = findViewById( R.id.dblist );
        ListFragment.setupListAdapter( listView, MainActivity.getMainActivity(), listAdapter, true );
    }

    private void setupMap(final LatLng center, final Bundle savedInstanceState, final SharedPreferences prefs) {
        mapView = new MapView( this );
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(), prefs, R.raw.night_style_json));
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

        final LatLngBounds bounds = queryArgs.getLocationBounds();
        String sql = "SELECT bssid,lastlat,lastlon FROM " + DatabaseHelper.NETWORK_TABLE + " WHERE 1=1 ";
        final String ssid = queryArgs.getSSID();
        String bssid = queryArgs.getBSSID();
        boolean limit = false;
        List<String> params = new ArrayList<>();
        if ((queryArgs.getType() != null) && CELL.equals(queryArgs.getType())) {
            boolean hasCellParams = false;
            String cellId = "";
            if (queryArgs.getCellOp() != null && !queryArgs.getCellOp().isEmpty()) {
                cellId += queryArgs.getCellOp()+"_";
                hasCellParams = true;
            } else {
                cellId += "%";
            }
            if (queryArgs.getCellNet() != null && !queryArgs.getCellNet().isEmpty()) {
                cellId += queryArgs.getCellNet()+"_";
                hasCellParams = true;
            } else {
                cellId += "%";
            }
            if (queryArgs.getCellId() != null && !queryArgs.getCellId().isEmpty()) {
                cellId += queryArgs.getCellId();
                hasCellParams = true;
            } else {
                cellId += "%";
            }
            if (hasCellParams) {
                bssid = cellId;
            }
        }

        if ( ssid != null && ! "".equals(ssid) ) {
            sql += " AND ssid like ?"; // + DatabaseUtils.sqlEscapeString(ssid);
            params.add(ssid);
            limit = true;
        }
        if ( bssid != null && ! "".equals(bssid) ) {
            sql += " AND bssid LIKE ?"; // + DatabaseUtils.sqlEscapeString(bssid);
            params.add(bssid);
            limit = true;
        }
        if ( queryArgs.getType() != null && !NetworkFilterType.ALL.equals(queryArgs.getType())) {
            switch (queryArgs.getType()) {
                case BT:
                    sql += " AND type IN ('B','E')";
                    break;
                case CELL:
                    sql += " AND type IN ('G','C','L','D','N')";
                    break;
                case WIFI:
                    sql += " AND type = ?";
                    params.add(NetworkType.WIFI.getCode());
                    break;
                default:
                    break;
            }
        }
        if ( queryArgs.getType() != null && (NetworkFilterType.ALL.equals(queryArgs.getType())|| WIFI.equals(queryArgs.getType()))) {
            if (queryArgs.getCrypto() != null && !WiFiSecurityType.ALL.equals(queryArgs.getCrypto())) {
                switch (queryArgs.getCrypto()) {
                    case WPA3:
                        sql += " AND (capabilities LIKE ? OR capabilities LIKE ? OR capabilities LIKE ?)";
                        params.add("%"+WPA3_CAP+"%");
                        params.add("%"+SUITE_B_192_CAP+"%");
                        params.add("%"+SAE_CAP+"%");
                        break;
                    case WPA2:
                        sql += " AND (capabilities LIKE ? OR capabilities LIKE ?)";
                        params.add("%"+WPA2_CAP+"%");
                        params.add("%"+RSN_CAP+"%");
                        break;
                    case WPA:
                        sql += " AND capabilities LIKE ?";
                        params.add("%"+WPA_CAP+"-%");
                        break;
                    case WEP:
                        sql += " AND capabilities LIKE ?";
                        params.add(WEP_CAP+"%");
                        break;
                    case NONE:
                        sql += " AND capabilities IN ('[]','[ESS]', '')"; //TODO: verify that these cases are complete
                        break;
                    default:
                        break;
                }
            }
        }
        if (bounds  != null ) {
            sql += " AND lastlat > ? AND lastlat < ? AND lastlon > ? AND lastlon < ?";
            params.add((bounds.southwest.latitude)+"");
            params.add((bounds.northeast.latitude)+"");
            params.add((bounds.southwest.longitude)+"");
            params.add((bounds.northeast.longitude)+"");
        }
        if ( limit ) {
            sql += " LIMIT ?"; // + LIMIT;
            params.add(LIMIT+"");
        }
        //DEBUG: Logging.error(sql);
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

                if ( bounds == null ) {
                    top.put( (float) count[0], bssid );
                } else {
                    Location.distanceBetween( lat, lon, bounds.getCenter().latitude, bounds.getCenter().longitude, results );
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
                    if (resultList.size() > 0) {
                        handler.post(() -> {
                            stopAnimation();
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            boolean hasValidPoints = false;
                            for (Network n : resultList) {
                                listAdapter.add(n);
                                mapRender.addItem(n);
                                final LatLng ll = n.getPosition();
                                //noinspection ConstantConditions
                                if (ll != null) {
                                    builder.include(ll);
                                    hasValidPoints = true;
                                }
                            }
                            if (hasValidPoints) {
                                mapView.getMapAsync(googleMap -> googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0)));
                            } else {
                                handler.post(() -> handleEmptyResult());
                            }
                            resultList.clear();
                        });
                    }
                } else {
                    handler.post(() -> handleEmptyResult());
                }
            }
        }, ListFragment.lameStatic.dbHelper);

        PooledQueryExecutor.enqueue( request );
    }

    private void handleResults() {
        stopAnimation();
        listAdapter.clear();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        if (null != searchResponse && null != searchResponse.getResults()) {
            for (WiFiSearchResponse.WiFiNetwork net :searchResponse.getResults()) {
                if (null != net) {
                    final Network n = WiFiSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(n.getPosition());

                    if (n.getLatLng() != null && mapRender != null) {
                        mapRender.addItem(n);
                    }
                }
            }
        } else if (null != btSearchResponse && null != btSearchResponse.getResults()) {
            for (BtSearchResponse.BtNetwork net :btSearchResponse.getResults()) {
                if (null != net) {
                    final Network n = BtSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(n.getPosition());

                    if (n.getLatLng() != null && mapRender != null) {
                        mapRender.addItem(n);
                    }
                }
            }
        } else if (null != cellSearchResponse && null != cellSearchResponse.getResults()) {
            for (CellSearchResponse.CellNetwork net :cellSearchResponse.getResults()) {
                if (null != net) {
                    final Network n = CellSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(n.getPosition());

                    if (n.getLatLng() != null && mapRender != null) {
                        mapRender.addItem(n);
                    }
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

    private void handleEmptyResult() {
        listAdapter.clear();
        WiGLEToast.showOverActivity(this, R.string.app_name,
                getString(R.string.search_empty), Toast.LENGTH_LONG);
        stopAnimation();
    }

    private void handleFailedRequest() {
        listAdapter.clear();
        WiGLEToast.showOverActivity(this, R.string.app_name,
                getString(R.string.search_empty), Toast.LENGTH_LONG);
        stopAnimation();
        showError();
    }

    private void setupWiGLEQuery(final QueryArgs queryArgs) {
        final FragmentActivity fa = this;
        String queryParams = "";
        if (queryArgs.getSSID() != null && !queryArgs.getSSID().isEmpty()) {
            try {
                boolean likeMatch = queryArgs.getSSID().contains("%") || queryArgs.getSSID().contains("_");
                String nameParam = API_SSID_PARAM;
                if (null != queryArgs.getType() && BT.equals(queryArgs.getType())) {
                    if (likeMatch) {
                        nameParam = API_BT_NAMELIKE_PARAM;
                    } else {
                        nameParam = API_BT_NAME_PARAM;
                    }
                } else {
                    if (likeMatch) {
                        nameParam = API_SSIDLIKE_PARAM;
                    }
                }
                queryParams+=nameParam+"="+URLEncoder.encode((queryArgs.getSSID()), java.nio.charset.StandardCharsets.UTF_8.toString() );
            } catch (UnsupportedEncodingException e) {
                Logging.error("parameter encoding error for SSID: ", e);
            }
        }

        if (queryArgs.getBSSID() != null && !queryArgs.getBSSID().isEmpty()) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }

            if (null != queryArgs.getType()) {
                switch (queryArgs.getType()) {
                    case WIFI:
                    case BT:
                        queryParams += API_BSSID_PARAM + "=" + (queryArgs.getBSSID());
                        break;
                    default:
                        break;
                }
            } else {
                queryParams += API_BSSID_PARAM + "=" + (queryArgs.getBSSID());
            }
        }

        if (CELL.equals(queryArgs.getType())) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }
            boolean needSep = false;
            if ((queryArgs.getCellOp() != null) && !queryArgs.getCellOp().isEmpty()) {
                queryParams += API_CELL_OP_PARAM + "=" + queryArgs.getCellOp();
                needSep = true;
            }
            if ((queryArgs.getCellNet() != null) && !queryArgs.getCellNet().isEmpty()) {
                if (needSep) {
                    queryParams += "&";
                }
                queryParams += API_CELL_NET_PARAM + "=" + queryArgs.getCellNet();
                needSep = true;
            }
            if ((queryArgs.getCellId() != null) && !queryArgs.getCellId().isEmpty()) {
                if (needSep) {
                    queryParams += "&";
                }
                queryParams += API_CELL_ID_PARAM + "=" + queryArgs.getCellId();
            }
        }

        final WiFiSecurityType securityType = queryArgs.getCrypto();
        if (null !=  securityType) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }
            final String param = WiFiSecurityType.webParameterValue(securityType);
            if (null != param) {
                queryParams += API_ENCRYPTION_PARAM + "=" +param;
            }
        }

        final LatLngBounds bounds = queryArgs.getLocationBounds();
        if (bounds != null) {
            if (!queryParams.isEmpty()) {
                queryParams+="&";
            }
            queryParams+=API_LAT1_PARAM+"="+bounds.southwest.latitude+"&";
            queryParams+=API_LAT2_PARAM+"="+bounds.northeast.latitude+"&";
            queryParams+=API_LON1_PARAM+"="+bounds.southwest.longitude+"&";
            queryParams+=API_LON2_PARAM+"="+bounds.northeast.longitude;
        }

        final MainActivity.State s = MainActivity.getStaticState();
        //DEBUG: Logging.error(queryParams);

        if (null != s) {
            queryFailed = false;
            if (null == queryArgs.getType() /*ALIBI: default to WiFi, but shouldn't happen*/ || WIFI.equals(queryArgs.getType())) {
                s.apiManager.searchWiFi(queryParams, new AuthenticatedRequestCompletedListener<WiFiSearchResponse, JSONObject>() {
                    @Override
                    public void onAuthenticationRequired() {
                        if (null != fa) {
                            WiGLEAuthDialog.createDialog(fa, getString(R.string.login_title),
                                    getString(R.string.login_required), getString(R.string.login),
                                    getString(R.string.cancel));
                        }
                    }

                    @Override
                    public void onTaskCompleted() {
                        if (null != searchResponse) {
                            handleResults();
                        } else {
                            if (queryFailed) {
                                handleFailedRequest();
                            } else {
                                handleEmptyResult();
                            }
                        }
                    }

                    @Override
                    public void onTaskSucceeded(WiFiSearchResponse response) {
                        searchResponse = response;
                    }

                    @Override
                    public void onTaskFailed(int status, JSONObject error) {
                        searchResponse = null;
                        queryFailed = true;
                    }
                });
            } else if (BT.equals(queryArgs.getType())) {
                s.apiManager.searchBt(queryParams, new AuthenticatedRequestCompletedListener<BtSearchResponse, JSONObject>() {
                    @Override
                    public void onAuthenticationRequired() {
                        if (null != fa) {
                            WiGLEAuthDialog.createDialog(fa, getString(R.string.login_title),
                                    getString(R.string.login_required), getString(R.string.login),
                                    getString(R.string.cancel));
                        }
                    }

                    @Override
                    public void onTaskCompleted() {
                        if (null != btSearchResponse) {
                            handleResults();
                        } else {
                            if (queryFailed) {
                                handleFailedRequest();
                            } else {
                                handleEmptyResult();
                            }
                        }
                    }

                    @Override
                    public void onTaskSucceeded(BtSearchResponse response) {
                        btSearchResponse = response;
                    }

                    @Override
                    public void onTaskFailed(int status, JSONObject error) {
                        btSearchResponse = null;
                        queryFailed = true;
                    }
                });
            } else if (CELL.equals(queryArgs.getType())) { //TODO: failing
                s.apiManager.searchCell(queryParams, new AuthenticatedRequestCompletedListener<CellSearchResponse, JSONObject>() {
                    @Override
                    public void onAuthenticationRequired() {
                        if (null != fa) {
                            WiGLEAuthDialog.createDialog(fa, getString(R.string.login_title),
                                    getString(R.string.login_required), getString(R.string.login),
                                    getString(R.string.cancel));
                        }
                    }

                    @Override
                    public void onTaskCompleted() {
                        if (null != cellSearchResponse) {
                            handleResults();
                        } else {
                            if (queryFailed) {
                                handleFailedRequest();
                            } else {
                                handleEmptyResult();
                            }
                        }
                    }

                    @Override
                    public void onTaskSucceeded(CellSearchResponse response) {
                        cellSearchResponse = response;
                    }

                    @Override
                    public void onTaskFailed(int status, JSONObject error) {
                        cellSearchResponse = null;
                        queryFailed = true;
                    }
                });
            } else {
                Logging.error("Unsupported network type for search: "+queryArgs.getType());
            }
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
