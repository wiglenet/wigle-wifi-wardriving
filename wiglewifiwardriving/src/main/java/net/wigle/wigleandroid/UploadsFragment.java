package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.model.Upload.Status.IN_PROGRESS;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.view.MenuItemCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
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

import net.wigle.wigleandroid.model.Upload;
import net.wigle.wigleandroid.model.api.UploadsResponse;
import net.wigle.wigleandroid.net.AuthenticatedRequestCompletedListener;
import net.wigle.wigleandroid.ui.EndlessScrollListener;
import net.wigle.wigleandroid.ui.ProgressThrobberFragment;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MenuUtil;
import net.wigle.wigleandroid.util.PreferenceKeys;
import org.json.JSONObject;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class UploadsFragment extends ProgressThrobberFragment {
    private static final int MENU_USER_STATS = 200;
    private static final int MENU_SITE_STATS = 201;

    public static boolean disableListButtons = false;

    private static final int ROW_COUNT = 100;

    private int currentPage = 0;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private AtomicBoolean finishing;
    private UploadsListAdapter listAdapter;
    private final AtomicBoolean lockListAdapter = new AtomicBoolean(false);

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView queueDepth;
    private UploadsResponse latestResponse;
    private static final long REFRESH_IN_PROGRESS_DELAY_MS = 30000; //30s
    private static final Map<Upload.Status, String> uploadStatusMap;

    private Timer refreshTimer;

    static {
        Map<Upload.Status, String> statusMap = new HashMap<>();
        statusMap.put(Upload.Status.QUEUED, "upload_queued");
        statusMap.put(Upload.Status.PARSING, "upload_parsing");
        statusMap.put(Upload.Status.TRILATERATING, "upload_trilaterating");
        statusMap.put(Upload.Status.STATS, "upload_stats");
        statusMap.put(Upload.Status.SUCCESS, "upload_success");
        statusMap.put(Upload.Status.FAILED, "upload_failed");
        statusMap.put(Upload.Status.ARCHIVE, "upload_archive");
        statusMap.put(Upload.Status.CATALOG, "upload_catalog");
        statusMap.put(Upload.Status.GEOINDEX, "upload_geoindex");

        uploadStatusMap = Collections.unmodifiableMap(statusMap);
    }
    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("UPLOADS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language, media volume, and number format
        Activity a = getActivity();
        NumberFormat numberFormat;
        if (null != a) {
            MainActivity.setLocale(a);
            // media volume
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
            numberFormat = NumberFormat.getNumberInstance(MainActivity.getLocale(a, a.getResources().getConfiguration()));
        } else {
            numberFormat = NumberFormat.getNumberInstance(Locale.US);
        }

        finishing = new AtomicBoolean(false);
        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        Logging.info("UPLOADS: onCreateView. orientation: " + orientation);
        final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.uploads, container, false);
        loadingImage = rootView.findViewById(R.id.uploads_throbber);
        errorImage = rootView.findViewById(R.id.uploads_error);

        setupSwipeRefresh(rootView);
        setupListView(rootView);

        final Activity a = getActivity();
        if (null != a) {
            busy.set(false);
            startAnimation(); //animation only applies for first page.
            downloadUploads(0, false);
        }
        return rootView;
    }

    private void setupSwipeRefresh(final LinearLayout rootView) {
        // Lookup the swipe container view
        final SwipeRefreshLayout swipeContainer = rootView.findViewById(R.id.uploads_swipe_container);
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(() -> {
            hideError();
            if (null != listAdapter) {
                listAdapter.clear();
            }
            downloadUploads(0, false);
        });
    }

    private void downloadUploads(final int page, final boolean update) {
        if (busy.compareAndSet(false, true)) {
            final int pageStart = page * ROW_COUNT;
            final MainActivity.State s = MainActivity.getStaticState();
            if (null != s) {
                s.apiManager.getUploads(pageStart, (pageStart + ROW_COUNT), new AuthenticatedRequestCompletedListener<UploadsResponse, JSONObject>() {
                        @Override
                        public void onTaskCompleted() {
                            busy.set(false);
                            stopAnimation();
                            if (latestResponse != null) {
                                if (update) {
                                    listAdapter.clear();
                                }
                                handleUploads(latestResponse);
                            } else {
                                swipeRefreshLayout.setRefreshing(false);
                                if (page == 0) {
                                    showError();
                                }
                                Logging.error("empty response - unable to update list view.");
                            }
                        }

                        @Override
                        public void onTaskSucceeded(UploadsResponse response) {
                            try {
                                List<File> filesOnDevice = FileUtility.getCsvUploadsAndDownloads(getContext());
                                for (Upload u: response.getResults()) {
                                    boolean onDevice = false;
                                    final String withExt = u.getFileName() + FileUtility.GZ_EXT;
                                    for (int j = 0; j < filesOnDevice.size(); j++) {
                                        final File f = filesOnDevice.get(j);
                                        if (withExt.contains(f.getName())) {
                                            //DEBUG: Logging.info("matched (uploaded): "+withExt+" v. "+f.getName());
                                            onDevice = true;
                                            u.setUploadedFromLocal(true);
                                            u.setDownloadedToLocal(false);
                                            filesOnDevice.remove(j);
                                            break;
                                        } else if (f.getName().startsWith(u.getTransid())) {
                                            //DEBUG: Logging.info("matched (downloaded): "+withExt+" v. "+f.getName());
                                            onDevice = true;
                                            u.setUploadedFromLocal(false);
                                            u.setDownloadedToLocal(true);
                                            filesOnDevice.remove(j);
                                            break;
                                        }
                                    }
                                    if (!onDevice) {
                                        u.setUploadedFromLocal(false);
                                        u.setDownloadedToLocal(false);
                                    }
                                    u.setHumanReadableStatus(statusValue(u.getStatus()));
                                }
                            } catch (Exception e) {
                                Logging.error("Uploads download error: ", e);
                            }
                            latestResponse = response;
                        }

                        @Override
                        public void onTaskFailed(int status, JSONObject error) {
                            Logging.error("Failed to update Uploads list.");
                            latestResponse = null;
                        }

                        @Override
                        public void onAuthenticationRequired() {
                            showAuthDialog();
                        }
                    }
                );
            }
        } else {
            Logging.error("preventing download because previous is still in progress.");
        }
    }

    private void setupListView(final View view) {
        final Activity a = getActivity();
        SharedPreferences prefs;
        if (null != a) {
            prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            if (listAdapter == null) {
                listAdapter = new UploadsListAdapter(getActivity(), R.layout.uploadrow, prefs, this);
            } else if (!listAdapter.isEmpty() && !TokenAccess.hasApiToken(prefs)) {
                listAdapter.clear();
            }
        } else {
            Logging.error("No activity - cannot instantiate listAdapter");
        }
        swipeRefreshLayout = view.findViewById(R.id.uploads_swipe_container);
        queueDepth = view.findViewById(R.id.queue_depth);

        // always set our current list adapter
        final AbsListView listView = view.findViewById(R.id.uploads_list_view);
        listView.setAdapter(listAdapter);
        listView.setOnScrollListener(new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {
                currentPage++;
                downloadUploads(currentPage, false);
                return true;
            }
        });

    }
//TODO: apply to JSON object
    private String statusValue(Upload.Status statusCode) {
        String packageName = "net.wigle.wigleandroid";
        int stringId =  getResources().getIdentifier("upload_unknown", "string", packageName);
        if (uploadStatusMap.containsKey(statusCode)) {
            stringId = getResources().getIdentifier(uploadStatusMap.get(statusCode), "string",
                    packageName);
        }
        return getString(stringId);
    }


    private void handleUploads(final UploadsResponse response) {
        if (response == null) {
            Logging.info("handleUploads null response, returning");
            return;
        }

        if (response != null && listAdapter != null && lockListAdapter.compareAndSet(false, true)) {
            try {
                final Activity a = getActivity();
                if (null != response && null != a) {
                    final String queueDepthTitle = a.getResources().getString(R.string.queue_depth,
                            "" + response.getProcessingQueueDepth(),
                            "" + response.getTrilaterationQueueDepth(), "" + response.getGeoQueueDepth());
                    queueDepth.setText(queueDepthTitle);
                }
                //listAdapter.clear(); //TODO: should we clear on update and scroll up to keep this from getting crazy?
                boolean refresh = false;
                for (final Upload result : response.getResults()) {
                    if (IN_PROGRESS.contains(result.getStatus())) {
                        if ( !refresh) {
                            refresh = true;
                        }
                    }
                    listAdapter.add(result);
                }
                if (refresh) {
                    final Handler handler = new Handler(Looper.getMainLooper());
                    if (null == refreshTimer) {
                        refreshTimer = new Timer();
                        refreshTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                downloadUploads(0, true);
                                handler.post(() -> listAdapter.notifyDataSetChanged());
                            }
                        }, REFRESH_IN_PROGRESS_DELAY_MS, REFRESH_IN_PROGRESS_DELAY_MS);
                    }
                } else {
                    if (refreshTimer != null) {
                        refreshTimer.cancel();
                        refreshTimer = null;
                    }
                }
            } finally {
                lockListAdapter.set(false);
                swipeRefreshLayout.setRefreshing(false);
            }
        } else {
            Logging.error("Failed to update - response: "+response+" listAdapter"+listAdapter);
        }
        busy.set(false);
    }

    @Override
    public void onDestroy() {
        Logging.info( "UPLOADS: onDestroy" );
        if (null != refreshTimer) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("UPLOADS: onResume");
        super.onResume();
        busy.set(false);
        final Activity a = getActivity();
        if (null != a) {
            getActivity().setTitle(R.string.uploads_app_name);
        }
    }

    @Override
    public void onStart() {
        Logging.info( "UPLOADS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        Logging.info( "UPLOADS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        Logging.info( "UPLOADS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig ) {
        Logging.info("UPLOADS: config changed");
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
