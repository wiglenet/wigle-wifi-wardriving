package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.db.DatabaseHelper.SEARCH_NETWORKS;
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

import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.background.PooledQueryExecutor;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.MapBounds;
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
import net.wigle.wigleandroid.ui.WiGLEAuthDialog;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public abstract class AbstractDBResultActivity extends ProgressThrobberActivity {
    private static final int MENU_RETURN = 12;
    private static final int LIMIT = 50;
    protected static final int DEFAULT_ZOOM = 18;
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
    protected final List<Network> resultList = new ArrayList<>();
    private final ConcurrentLinkedHashMap<LatLng, Integer> obsMap = new ConcurrentLinkedHashMap<>();
    protected SetNetworkListAdapter listAdapter;
    protected WiFiSearchResponse searchResponse;
    protected BtSearchResponse btSearchResponse;
    protected CellSearchResponse cellSearchResponse;
    private boolean queryFailed;

    private static void putWithBackoff(TreeMap<Float, String> top, String s, float diff) {
        String old = top.put(diff, s);
        // protect against infinite loops
        int count = 0;
        while (old != null && count < 1000) {
            // ut oh, two at the same difference away. add a slight bit and put it back
            // info( "collision at diff: " + diff + " old: " + old.getCallsign() + " orig: " + s.getCallsign() );
            diff += 0.0001f;
            old = top.put(diff, old);
            count++;
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        // set language
        MainActivity.setLocale(this);
        EdgeToEdge.enable(this);
        // force media volume controls
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(getLayoutResourceId());

        listAdapter = new SetNetworkListAdapter(this, true, R.layout.row);
        
        View wrapperLayout = findViewById(R.id.db_result_wrapper);
        if (wrapperLayout != null) {
            wrapperLayout.post(() -> setupListView());
        } else {
            // Fallback: use handler if wrapper not found
            new Handler(Looper.getMainLooper()).post(() -> setupListView());
        }
        if (null != wrapperLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(wrapperLayout, new OnApplyWindowInsetsListener() {
                        @Override
                        public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                            final Insets innerPadding = insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars() |
                                            WindowInsetsCompat.Type.displayCutout());
                            v.setPadding(
                                    innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                            );
                            return insets;
                        }
                    }
            );
        }

        ImageButton back = findViewById(R.id.result_back_button);
        if (null != back) {
            back.setOnClickListener(v -> {
                finish();
            });
        }

        QueryArgs queryArgs = ListFragment.lameStatic.queryArgs;
        loadingImage = findViewById(R.id.search_throbber);
        errorImage = findViewById(R.id.search_error);

        if (queryArgs != null) {
            startAnimation();

            LatLng center = MappingFragment.DEFAULT_POINT;
            MapBounds bounds = queryArgs.getLocationBounds();
            if (bounds != null) {
                center = new LatLng(bounds.getCenter().latitude, bounds.getCenter().longitude);
            }
            final SharedPreferences prefs = this.getApplicationContext().
                    getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            setupMap(center, savedInstanceState, prefs);
            if (queryArgs.searchWiGLE()) {
                setupWiGLEQuery(queryArgs);
            } else {
                setupQuery(queryArgs);
            }
        }
    }

    /**
     * Setup the ListView connection to the adapter.
     * This is called asynchronously to ensure fragment views are ready.
     */
    private void setupListView() {
        // Get the ListView from the NetListFragment
        final NetListFragment fragment = (NetListFragment) getSupportFragmentManager().findFragmentById(R.id.net_list);
        ListView listView = null;
        if (fragment != null && fragment.getView() != null) {
            listView = fragment.getView().findViewById(R.id.dblist);
        }
        // Fallback to direct findViewById in case fragment view isn't ready yet
        if (listView == null) {
            listView = findViewById(R.id.dblist);
        }
        if (listView != null && listAdapter != null) {
            ListFragment.setupListAdapter(listView, MainActivity.getMainActivity(), listAdapter, true);
        }
    }

    /**
     * Get the layout resource ID for this activity.
     * Subclasses must provide their specific layout.
     */
    protected abstract int getLayoutResourceId();

    protected abstract void setupMap(LatLng center, Bundle savedInstanceState, SharedPreferences prefs);

    public abstract void updateMap(boolean hasValidpoints, Handler handler);

    private void setupQuery(final QueryArgs queryArgs) {

        final MapBounds bounds = queryArgs.getLocationBounds();
        String sql = SEARCH_NETWORKS;
        final String ssid = queryArgs.getSSID();
        String bssid = queryArgs.getBSSID();
        boolean limit = false;
        List<String> params = new ArrayList<>();
        if ((queryArgs.getType() != null) && CELL.equals(queryArgs.getType())) {
            boolean hasCellParams = false;
            String cellId = "";
            if (queryArgs.getCellOp() != null && !queryArgs.getCellOp().isEmpty()) {
                cellId += queryArgs.getCellOp() + "_";
                hasCellParams = true;
            } else {
                cellId += "%";
            }
            if (queryArgs.getCellNet() != null && !queryArgs.getCellNet().isEmpty()) {
                cellId += queryArgs.getCellNet() + "_";
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

        if (ssid != null && !ssid.isEmpty()) {
            sql += " AND ssid like ?"; // + DatabaseUtils.sqlEscapeString(ssid);
            params.add(ssid);
            limit = true;
        }
        if (bssid != null && !bssid.isEmpty()) {
            sql += " AND bssid LIKE ?"; // + DatabaseUtils.sqlEscapeString(bssid);
            params.add(bssid);
            limit = true;
        }
        if (queryArgs.getType() != null && !NetworkFilterType.ALL.equals(queryArgs.getType())) {
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
        if (queryArgs.getType() != null && (NetworkFilterType.ALL.equals(queryArgs.getType()) || WIFI.equals(queryArgs.getType()))) {
            if (queryArgs.getCrypto() != null && !WiFiSecurityType.ALL.equals(queryArgs.getCrypto())) {
                switch (queryArgs.getCrypto()) {
                    case WPA3:
                        sql += " AND (capabilities LIKE ? OR capabilities LIKE ? OR capabilities LIKE ?)";
                        params.add("%" + WPA3_CAP + "%");
                        params.add("%" + SUITE_B_192_CAP + "%");
                        params.add("%" + SAE_CAP + "%");
                        break;
                    case WPA2:
                        sql += " AND (capabilities LIKE ? OR capabilities LIKE ?)";
                        params.add("%" + WPA2_CAP + "%");
                        params.add("%" + RSN_CAP + "%");
                        break;
                    case WPA:
                        sql += " AND capabilities LIKE ?";
                        params.add("%" + WPA_CAP + "-%");
                        break;
                    case WEP:
                        sql += " AND capabilities LIKE ?";
                        params.add(WEP_CAP + "%");
                        break;
                    case NONE:
                        sql += " AND capabilities IN ('[]','[ESS]', '')"; //TODO: verify that these cases are complete
                        break;
                    default:
                        break;
                }
            }
        }
        if (bounds != null) {
            sql += " AND lastlat > ? AND lastlat < ? AND lastlon > ? AND lastlon < ?";
            params.add((bounds.southwest.latitude) + "");
            params.add((bounds.northeast.latitude) + "");
            params.add((bounds.southwest.longitude) + "");
            params.add((bounds.northeast.longitude) + "");
        }
        if (limit) {
            sql += " LIMIT ?"; // + LIMIT;
            params.add(LIMIT + "");
        }
        //DEBUG: Logging.error(sql);
        final TreeMap<Float, String> top = new TreeMap<>();
        final float[] results = new float[1];
        final long[] count = new long[1];
        final Handler handler = new Handler(Looper.getMainLooper());
        final PooledQueryExecutor.Request request = new PooledQueryExecutor.Request(sql, params.toArray(new String[0]),
                new PooledQueryExecutor.ResultHandler() {
                    @Override
                    public boolean handleRow(final Cursor cursor) {
                        final String bssid = cursor.getString(0);
                        final float lat = cursor.getFloat(1);
                        final float lon = cursor.getFloat(2);
                        count[0]++;

                        if (bounds == null) {
                            top.put((float) count[0], bssid);
                        } else {
                            Location.distanceBetween(lat, lon, bounds.getCenter().latitude, bounds.getCenter().longitude, results);
                            final float meters = results[0];

                            if (top.size() <= LIMIT) {
                                AbstractDBResultActivity.putWithBackoff(top, bssid, meters);
                            } else {
                                Float last = top.lastKey();
                                if (meters < last) {
                                    top.remove(last);
                                    AbstractDBResultActivity.putWithBackoff(top, bssid, meters);
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
                                if (null != network.getLatLng()) {
                                    final LatLng point =
                                            new LatLng(network.getLatLng().latitude, network.getLatLng().longitude);
                                        obsMap.put(point, 0);
                                }
                            }
                            if (resultList.size() > 0) {
                                handler.post(() -> {
                                    stopAnimation();
                                    boolean hasValidPoints = false;
                                    updateMap(hasValidPoints, handler);
                                    resultList.clear();
                                });
                            }
                        } else {
                            handler.post(() -> handleEmptyResult());
                        }
                    }
                }, ListFragment.lameStatic.dbHelper);

        PooledQueryExecutor.enqueue(request);
    }

    protected abstract void handleResults();

    protected void handleEmptyResult() {
        if (listAdapter != null) {
            listAdapter.clear();
        }
        WiGLEToast.showOverActivity(this, R.string.app_name,
                getString(R.string.search_empty), Toast.LENGTH_LONG);
        stopAnimation();
    }

    private void handleFailedRequest() {
        if (listAdapter != null) {
            listAdapter.clear();
        }
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
                queryParams += nameParam + "=" + URLEncoder.encode((queryArgs.getSSID()), java.nio.charset.StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                Logging.error("parameter encoding error for SSID: ", e);
            }
        }

        if (queryArgs.getBSSID() != null && !queryArgs.getBSSID().isEmpty()) {
            if (!queryParams.isEmpty()) {
                queryParams += "&";
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
                queryParams += "&";
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
        if (null != securityType) {
            if (!queryParams.isEmpty()) {
                queryParams += "&";
            }
            final String param = WiFiSecurityType.webParameterValue(securityType);
            if (null != param) {
                queryParams += API_ENCRYPTION_PARAM + "=" + param;
            }
        }

        final MapBounds bounds = queryArgs.getLocationBounds();
        if (bounds != null) {
            if (!queryParams.isEmpty()) {
                queryParams += "&";
            }
            queryParams += API_LAT1_PARAM + "=" + bounds.getSouthwest().latitude + "&";
            queryParams += API_LAT2_PARAM + "=" + bounds.getNortheast().latitude + "&";
            queryParams += API_LON1_PARAM + "=" + bounds.getSouthwest().longitude + "&";
            queryParams += API_LON2_PARAM + "=" + bounds.getNortheast().longitude;
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
                Logging.error("Unsupported network type for search: " + queryArgs.getType());
            }
        }
    }

    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuItem item = menu.add(0, MENU_RETURN, 0, getString(R.string.menu_return));
        item.setIcon(android.R.drawable.ic_media_previous);

        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RETURN:
                finish();
                return true;
        }
        return false;
    }
}
