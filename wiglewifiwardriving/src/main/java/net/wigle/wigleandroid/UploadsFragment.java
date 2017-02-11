package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import net.wigle.wigleandroid.background.ApiDownloader;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.DownloadHandler;
import net.wigle.wigleandroid.model.Upload;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class UploadsFragment extends Fragment {
    private static final int MSG_RANKING_DONE = 100;
    private static final int MENU_USER_STATS = 200;
    private static final int MENU_SITE_STATS = 201;

    /*
     {
       "success":true,
       "processingQueueDepth":1,
       "results":[
         {
           "transid":"20170101-00928",
           "username":"arkasha",
           "firstTime":"2017-01-01T23:59:24.000Z",
           "lastupdt":"2017-01-02T00:00:06.000Z",
           "fileName":"1483315164_WigleWifi_20170101155922.csv",
           "fileSize":20846,
           "fileLines":174,
           "status":"D",
           "discoveredGps":0,
           "discovered":0,
           "total":118,
           "totalGps":117,
           "totalLocations":172,
           "percentDone":100.0,
           "timeParsing":5,
           "genDiscovered":0,
           "genDiscoveredGps":0,
           "genTotal":1,
           "genTotalGps":1,
           "genTotalLocations":1,
           "wait":null
         }, ...
       ]
     }
     */
    private static final String RESULT_LIST_KEY = "results";

    private static final String KEY_TOTAL_WIFI_GPS = "discoveredGps";
    private static final String KEY_TOTAL_CELL_GPS = "genDiscoveredGps";
    private static final String KEY_QUEUE_DEPTH = "processingQueueDepth";
    private static final String KEY_TRANSID = "transid";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PERCENT_DONE = "percentDone";
    private static final String KEY_FILE_SIZE = "fileSize";

    private static final int ROW_COUNT = 100;

    private static final String[] ALL_ROW_KEYS = new String[] {
            KEY_TOTAL_WIFI_GPS, KEY_TOTAL_CELL_GPS, KEY_PERCENT_DONE, KEY_FILE_SIZE,
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;
    private UploadsListAdapter listAdapter;
    private RankDownloadHandler handler;

    private static final Map<String, String> uploadStatusMap;
    static {
        Map<String, String> statusMap = new HashMap<String, String>();
        statusMap.put("W", "upload_queued");
        statusMap.put("I", "upload_parsing");
        statusMap.put("T", "upload_trilaterating");
        statusMap.put("S", "upload_stats");
        statusMap.put("D", "upload_success");
        statusMap.put("E", "upload_failed");
        uploadStatusMap = Collections.unmodifiableMap(statusMap);
    }
    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("UPLOADS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        MainActivity.setLocale(getActivity());

        // media volume
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        finishing = new AtomicBoolean(false);
        numberFormat = NumberFormat.getNumberInstance(Locale.US);
        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        MainActivity.info("UPLOADS: onCreateView. orientation: " + orientation);
        final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.uploads, container, false);
        setupSwipeRefresh(rootView);
        setupListView(rootView);

        handler = new RankDownloadHandler(rootView, numberFormat,
                getActivity().getPackageName(), getResources());
        handler.setUploadsListAdapter(listAdapter);
        downloadUploads();

        return rootView;
    }

    private void setupSwipeRefresh(final LinearLayout rootView) {
        // Lookup the swipe container view
        final SwipeRefreshLayout swipeContainer = (SwipeRefreshLayout) rootView.findViewById(R.id.uploads_swipe_container);

        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Your code to refresh the list here.
                // Make sure you call swipeContainer.setRefreshing(false)
                // once the network request has completed successfully.
                downloadUploads();
            }
        });
    }

    private void downloadUploads() {
        if (handler == null) {
            MainActivity.error("downloadUploads handler is null");
            return;
        }
        final String monthUrl = MainActivity.UPLOADS_STATS_URL + "?pageend=" + ROW_COUNT;
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                "uploads-cache.json", monthUrl, false, true, true, ApiDownloader.REQUEST_GET,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json, final boolean isCache) {
                        handleUploads(json, handler);
                    }
                });
        try {
            task.startDownload(this);
        } catch (WiGLEAuthException waex) {
            MainActivity.info("Transactions Download Failed due to failed auth");
        }
    }

    private void setupListView(final View view) {
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        if (listAdapter == null) {
            listAdapter = new UploadsListAdapter(getActivity().getApplicationContext(), R.layout.uploadrow);
        } else if (!listAdapter.isEmpty() && prefs.getString(ListFragment.PREF_TOKEN,"").isEmpty()) {
            listAdapter.clear();
        }
        // always set our current list adapter
        final ListView listView = (ListView) view.findViewById(R.id.uploads_list_view);
        listView.setAdapter(listAdapter);

    }

    private String statusValue(String statusCode) {
        String packageName = "net.wigle.wigleandroid";
        int stringId =  getResources().getIdentifier("upload_unknown", "string", packageName);
        if (uploadStatusMap.containsKey(statusCode)) {
            stringId = getResources().getIdentifier(uploadStatusMap.get(statusCode), "string",
                    packageName);
        }
        return getString(stringId);
    }

    private final static class RankDownloadHandler extends DownloadHandler {
        private UploadsListAdapter uploadsListAdapter;

        private RankDownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                                final Resources resources) {
            super(view, numberFormat, packageName, resources);
        }

        public void setUploadsListAdapter(final UploadsListAdapter uploadsListAdapter) {
            this.uploadsListAdapter = uploadsListAdapter;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            final ArrayList<Parcelable> results = bundle.getParcelableArrayList(RESULT_LIST_KEY);
            // MainActivity.info("handleMessage. results: " + results);
            if (msg.what == MSG_RANKING_DONE && results != null && uploadsListAdapter != null) {
                TextView tv = (TextView) view.findViewById(R.id.queue_depth);
                final String queueDepthTitle = resources.getString(R.string.queue_depth);
                tv.setText(queueDepthTitle + ": " + bundle.getString(KEY_QUEUE_DEPTH));

                uploadsListAdapter.clear();
                for (final Parcelable result : results) {
                    if (result instanceof Bundle) {
                        final Bundle row = (Bundle) result;
                        final Upload upload = new Upload(row.getString(KEY_TRANSID), row.getLong(KEY_TOTAL_WIFI_GPS),
                                row.getLong(KEY_TOTAL_CELL_GPS), (int) row.getLong(KEY_PERCENT_DONE),
                                row.getString(KEY_STATUS), row.getLong(KEY_FILE_SIZE));
                        uploadsListAdapter.add(upload);
                    }
                }

                final SwipeRefreshLayout swipeRefreshLayout =
                        (SwipeRefreshLayout) view.findViewById(R.id.uploads_swipe_container);
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private void handleUploads(final JSONObject json, final Handler handler) {
        MainActivity.info("handleUploads");

        if (json == null) {
            MainActivity.info("handleUploads null json, returning");
            return;
        }

        final Bundle bundle = new Bundle();
        try {
            final JSONArray list = json.getJSONArray(RESULT_LIST_KEY);
            final ArrayList<Parcelable> resultList = new ArrayList<>(list.length());
            for (int i = 0; i < list.length(); i++) {
                final JSONObject row = list.getJSONObject(i);
                final Bundle rowBundle = new Bundle();
                for (final String key : ALL_ROW_KEYS) {
                    rowBundle.putLong(key, row.getLong(key));
                }
                rowBundle.putString(KEY_TRANSID, row.getString(KEY_TRANSID));
                rowBundle.putString(KEY_STATUS, this.statusValue(row.getString(KEY_STATUS)));
                resultList.add(rowBundle);
            }
            bundle.putParcelableArrayList(RESULT_LIST_KEY, resultList);
            bundle.putString(KEY_QUEUE_DEPTH, json.getString(KEY_QUEUE_DEPTH));
        }
        catch (final JSONException ex) {
            MainActivity.error("json error: " + ex, ex);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_RANKING_DONE;
        handler.sendMessage(message);
    }

    @Override
    public void onDestroy() {
        MainActivity.info( "UPLOADS: onDestroy" );
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        MainActivity.info("UPLOADS: onResume");
        super.onResume();
        getActivity().setTitle(R.string.uploads_app_name);
    }

    @Override
    public void onStart() {
        MainActivity.info( "UPLOADS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        MainActivity.info( "UPLOADS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        MainActivity.info( "UPLOADS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
        MainActivity.info("UPLOADS: config changed");
        super.onConfigurationChanged( newConfig );
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
        MenuItem item = menu.add(0, MENU_USER_STATS, 0, getString(R.string.user_stats_app_name));
        item.setIcon( android.R.drawable.ic_menu_myplaces );
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_USER_STATS, 0, getString(R.string.user_stats_app_name));
        item.setIcon(android.R.drawable.ic_menu_myplaces);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon( R.drawable.wiglewifi_small_black_white );
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon(R.drawable.wiglewifi_small_black_white);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity();
        switch ( item.getItemId() ) {
            case MENU_USER_STATS:
                main.selectFragment(MainActivity.USER_STATS_TAB_POS);
                return true;
            case MENU_SITE_STATS:
                main.selectFragment(MainActivity.SITE_STATS_TAB_POS);
                return true;
        }
        return false;
    }

}
