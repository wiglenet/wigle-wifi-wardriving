package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.view.MenuItemCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

import net.wigle.wigleandroid.background.ApiDownloader;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.DownloadHandler;
import net.wigle.wigleandroid.model.Upload;
import net.wigle.wigleandroid.ui.EndlessScrollListener;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.MenuUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class UploadsFragment extends Fragment {
    private static final int MSG_RANKING_DONE = 100;
    private static final int MENU_USER_STATS = 200;
    private static final int MENU_SITE_STATS = 201;

    public static boolean disableListButtons = false;

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
    private static final String KEY_TOTAL_BT_GPS = "btDiscoveredGps";
    private static final String KEY_TOTAL_CELL_GPS = "genDiscoveredGps";
    private static final String KEY_QUEUE_DEPTH = "processingQueueDepth";
    private static final String KEY_TRANSID = "transid";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PERCENT_DONE = "percentDone";
    private static final String KEY_FILE_SIZE = "fileSize";
    private static final String KEY_FILE_NAME = "fileName";
    private static final String KEY_UPLOADED = "uploadedFromLocal";
    private static final String KEY_DOWNLOADED = "downloadedToLocal";

    private static final int ROW_COUNT = 100;

    private int currentPage = 0;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private static final String[] ALL_ROW_KEYS = new String[] {
            KEY_TOTAL_WIFI_GPS, KEY_TOTAL_BT_GPS, KEY_TOTAL_CELL_GPS, KEY_PERCENT_DONE, KEY_FILE_SIZE
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;
    private UploadsListAdapter listAdapter;
    private RankDownloadHandler handler;

    private static final Map<String, String> uploadStatusMap;
    static {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("W", "upload_queued");
        statusMap.put("I", "upload_parsing");
        statusMap.put("T", "upload_trilaterating");
        statusMap.put("S", "upload_stats");
        statusMap.put("D", "upload_success");
        statusMap.put("E", "upload_failed");
        statusMap.put("A", "upload_archive");
        statusMap.put("C", "upload_catalog");
        statusMap.put("G", "upload_geoindex");

        uploadStatusMap = Collections.unmodifiableMap(statusMap);
    }
    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("UPLOADS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            // media volume
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }

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

        final Activity a = getActivity();
        if (null != a) {
            handler = new RankDownloadHandler(rootView, numberFormat,
                    getActivity().getPackageName(), getResources());
            handler.setUploadsListAdapter(listAdapter);
            busy.set(false);
            downloadUploads(0);
        }

        return rootView;
    }

    private void setupSwipeRefresh(final LinearLayout rootView) {
        // Lookup the swipe container view
        final SwipeRefreshLayout swipeContainer = rootView.findViewById(R.id.uploads_swipe_container);

        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (null != listAdapter) {
                    listAdapter.clear();
                }
                downloadUploads(0);
            }
        });
    }

    private void downloadUploads(final int page) {
        if (handler == null) {
            MainActivity.error("downloadUploads handler is null");
        }
        if (!busy.get()) {
            final int pageStart = page * ROW_COUNT;
            final String downloadUrl = MainActivity.UPLOADS_STATS_URL + "?pagestart=" + pageStart + "&pageend=" + (pageStart + ROW_COUNT);
            busy.set(true);
            final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper, null,
                    /*page == 0 ? "uploads-cache.json" : "uploads-cache-p" + page + ".json",*/
                    //ALIBI: cachefiles are too problematic with infinite scroll
                    downloadUrl, false, true, true, ApiDownloader.REQUEST_GET,
                    new ApiListener() {
                        @Override
                        public void requestComplete(final JSONObject json, final boolean isCache) {
                            if (!isCache) {
                                handleUploads(json, handler);
                            } // cache is too big a problem with the infini-scroll.
                            busy.set(false);
                        }
                    });
            try {
                task.startDownload(this);
            } catch (WiGLEAuthException waex) {
                MainActivity.info("Transactions Download Failed due to failed auth");
            }
        } else {
            MainActivity.error("preventing download because previous is still in progress.");
        }
    }

    private void setupListView(final View view) {
        final Activity a = getActivity();
        SharedPreferences prefs;
        if (null != a) {
            prefs = a.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            if (listAdapter == null) {
                listAdapter = new UploadsListAdapter(getActivity(), R.layout.uploadrow, prefs, this);
            } else if (!listAdapter.isEmpty() && !TokenAccess.hasApiToken(prefs)) {
                listAdapter.clear();
            }
        }
        // always set our current list adapter
        final AbsListView listView = view.findViewById(R.id.uploads_list_view);
        listView.setAdapter(listAdapter);
        listView.setOnScrollListener(new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {
                currentPage++;
                downloadUploads(currentPage);
                return true;
            }
        });

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

        private void setUploadsListAdapter(final UploadsListAdapter uploadsListAdapter) {
            this.uploadsListAdapter = uploadsListAdapter;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            final ArrayList<Parcelable> results = bundle.getParcelableArrayList(RESULT_LIST_KEY);

            //DEBUG: MainActivity.info("handleMessage. results: " + results);
            if (msg.what == MSG_RANKING_DONE && results != null && uploadsListAdapter != null) {
                TextView tv = view.findViewById(R.id.queue_depth);
                final String queueDepthTitle = resources.getString(R.string.queue_depth);
                tv.setText(queueDepthTitle + ": " + bundle.getString(KEY_QUEUE_DEPTH));
                uploadsListAdapter.clear();
                for (final Parcelable result : results) {
                    if (result instanceof Bundle) {
                        final Bundle row = (Bundle) result;
                        final Upload upload = new Upload(row.getString(KEY_TRANSID), row.getLong(KEY_TOTAL_WIFI_GPS),
                                row.getLong(KEY_TOTAL_BT_GPS),
                                row.getLong(KEY_TOTAL_CELL_GPS), (int) row.getLong(KEY_PERCENT_DONE),
                                row.getString(KEY_STATUS), row.getLong(KEY_FILE_SIZE), row.getString(KEY_FILE_NAME),
                                row.getBoolean(KEY_UPLOADED), row.getBoolean(KEY_DOWNLOADED));
                        uploadsListAdapter.add(upload);
                    }
                }
            }
            final SwipeRefreshLayout swipeRefreshLayout =
                    view.findViewById(R.id.uploads_swipe_container);
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void handleUploads(final JSONObject json, final Handler handler) {
        //MainActivity.info("handleUploads");

        if (json == null) {
            MainActivity.info("handleUploads null json, returning");
            return;
        }

        final Bundle bundle = new Bundle();
        try {
            final JSONArray list = json.getJSONArray(RESULT_LIST_KEY);
            final ArrayList<Parcelable> resultList = new ArrayList<>(ROW_COUNT);
            List<File> filesOnDevice = FileUtility.getCsvUploadsAndDownloads(getContext());
            for (int i = 0; i < list.length(); i++) {
                final JSONObject row = list.getJSONObject(i);
                final Bundle rowBundle = new Bundle();
                for (final String key : ALL_ROW_KEYS) {
                    rowBundle.putLong(key, row.getLong(key));
                }
                final String transId = row.getString(KEY_TRANSID);
                rowBundle.putString(KEY_TRANSID, transId);
                final String fileName = row.getString(KEY_FILE_NAME);
                rowBundle.putString(KEY_FILE_NAME, fileName);
                //TODO: annotate with file status
                rowBundle.putString(KEY_STATUS, this.statusValue(row.getString(KEY_STATUS)));
                resultList.add(rowBundle);
                boolean onDevice = false;

                final String withExt = fileName+FileUtility.GZ_EXT;
                for (int j = 0; j < filesOnDevice.size(); j++) {
                    final File f = filesOnDevice.get(j);
                    if (withExt.contains(f.getName())) {
                        onDevice = true;
                        rowBundle.putBoolean(KEY_UPLOADED, true);
                        rowBundle.putBoolean(KEY_DOWNLOADED, false);
                        filesOnDevice.remove(j);
                        break;
                    } else if (f.getName().startsWith(transId)) {
                        onDevice = true;
                        rowBundle.putBoolean(KEY_UPLOADED, false);
                        rowBundle.putBoolean(KEY_DOWNLOADED, true);
                        filesOnDevice.remove(j);
                        break;
                    }
                }
                if (! onDevice) {
                    rowBundle.putBoolean(KEY_UPLOADED, false);
                    rowBundle.putBoolean(KEY_DOWNLOADED, false);
                }
            }
            //TODO: remaining elements in filesOnDevice are likely failed uploads - we can offer the option to retry
            bundle.putParcelableArrayList(RESULT_LIST_KEY, resultList);
            bundle.putString(KEY_QUEUE_DEPTH, json.getString(KEY_QUEUE_DEPTH));
        } catch (final JSONException ex) {
            MainActivity.error("json error: " + ex, ex);
        } catch (final Exception e) {
            MainActivity.error("uploads error: " + e, e);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_RANKING_DONE;
        handler.sendMessage(message);
        busy.set(false);
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
        busy.set(false);
        final Activity a = getActivity();
        if (null != a) {
            getActivity().setTitle(R.string.uploads_app_name);
        }
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
    public void onCreateOptionsMenu (final Menu menu, @NonNull final MenuInflater inflater) {
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
    public boolean onOptionsItemSelected( @NonNull final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity();
        final Activity a = getActivity();
        if (null != a) {
            NavigationView navigationView = a.findViewById(R.id.left_drawer);
            switch (item.getItemId()) {
                case MENU_USER_STATS:
                    MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_user_stats);
                    return true;
                case MENU_SITE_STATS:
                    MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_site_stats);
                    return true;
            }
        }
        return false;
    }
}
