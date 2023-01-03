package net.wigle.wigleandroid;

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
import androidx.fragment.app.FragmentActivity;
import androidx.core.view.MenuItemCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

import net.wigle.wigleandroid.background.ApiDownloader;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.DownloadHandler;
import net.wigle.wigleandroid.model.RankUser;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MenuUtil;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.UrlConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class RankStatsFragment extends Fragment {
    private static final int MSG_RANKING_DONE = 100;
    private static final int MENU_USER_STATS = 200;
    private static final int MENU_SITE_STATS = 201;
    private static final int MENU_RANK_SWAP = 202;
    private static final int MENU_USER_CENTRIC_SWAP = 203;

    private static final String RESULT_LIST_KEY = "results";
    /*
    {
      eventView: false,
      myUsername: "arkasha",
      pageEnd: 100,
      pageStart: 0,
      results:[
        {
          discoveredCell: 999911,
          discoveredCellGPS: 718583,
          discoveredWiFi: 41781793,
          discoveredWiFiGPS: 26209080,
          discoveredWiFiGPSPercent: 8.64536,
          eventMonthCount: 20526,
          eventPrevMonthCount: 431245,
          first: "20011003-00001",
          last: "20170103-00583",
          monthRank: 1,
          prevMonthRank: 1,
          prevRank: 1,
          rank: 1,
          self: false,
          totalWiFiLocations: 185993637,
          userName: "anonymous"
        },
        ...]}
     */
    private static final String KEY_MONTH_WIFI_GPS = "eventMonthCount";
    private static final String KEY_TOTAL_WIFI_GPS = "discoveredWiFiGPS";
    private static final String KEY_TOTAL_BT_GPS = "discoveredBtGPS";
    private static final String KEY_TOTAL_CELL_GPS = "discoveredCellGPS";
    private static final String KEY_RANK = "rank";
    private static final String KEY_USERNAME = "userName";
    private static final String KEY_PREV_RANK = "prevRank";
    private static final String KEY_PREV_MONTH_RANK = "prevMonthRank";
    private static final String KEY_SELECTED = "selected";

    private static final int ROW_COUNT = 100;

    private static final String[] ALL_ROW_KEYS = new String[] {
            KEY_MONTH_WIFI_GPS, KEY_TOTAL_WIFI_GPS, KEY_TOTAL_BT_GPS, KEY_TOTAL_CELL_GPS, KEY_RANK, KEY_PREV_RANK,
            KEY_PREV_MONTH_RANK,
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;
    private RankListAdapter listAdapter;
    private AtomicBoolean monthRanking;
    private AtomicBoolean userCentric;
    private RankDownloadHandler handler;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("RANKSTATS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
        }
        // media volume
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        finishing = new AtomicBoolean(false);
        monthRanking = new AtomicBoolean(false);
        userCentric = new AtomicBoolean(true);
        numberFormat = NumberFormat.getNumberInstance(Locale.US);
        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        Logging.info("RANKSTATS: onCreateView.a orientation: " + orientation);
        final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.rankstats, container, false);
        setupSwipeRefresh(rootView);
        setupListView(rootView);

        final Activity a = getActivity();
        if (null != a) {
            handler = new RankDownloadHandler(rootView, numberFormat,
                    getActivity().getPackageName(), getResources(), monthRanking);
            handler.setRankListAdapter(listAdapter);
            final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);

            //TODO: we should only perform user DL if there's a user set
            UserStatsFragment.executeUserDownload(this, new UserStatsFragment.UserDownloadApiListener(new Handler() {
                @Override
                public void handleMessage(final Message msg) {
                    final Bundle bundle = msg.getData();
                    final boolean isCache = bundle.getBoolean(UserStatsFragment.KEY_IS_CACHE);
                    Logging.info("got user message, isCache: " + isCache);

                    final SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(ListFragment.PREF_RANK, bundle.getLong(UserStatsFragment.KEY_RANK));
                    editor.putLong(ListFragment.PREF_MONTH_RANK, bundle.getLong(UserStatsFragment.KEY_MONTH_RANK));
                    editor.apply();
                    downloadRanks(isCache);
                }
            }));
        }

        return rootView;
    }

    private void setupSwipeRefresh(final LinearLayout rootView) {
        // Lookup the swipe container view
        final SwipeRefreshLayout swipeContainer = rootView.findViewById(R.id.rank_swipe_container);

        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(() -> {
            // Your code to refresh the list here.
            // Make sure you call swipeContainer.setRefreshing(false)
            // once the network request has completed successfully.
            downloadRanks(false);
        });
    }

    private void downloadRanks(final boolean isCache) {
        if (handler == null) {
            Logging.error("downloadRanks: handler is null");
            return;
        }
        final FragmentActivity fragmentActivity = getActivity();
        if (fragmentActivity == null) {
            Logging.error("downloadRanks: fragmentActivity is null");
            return;
        }
        final boolean doMonthRanking = monthRanking.get();
        final String sort = doMonthRanking ? "monthcount" : "discovered";
        String top = "top";
        long pageStart = 0;
        long selected = 0;
        if (userCentric.get()) {
            top = "";
            final String userRankKey = doMonthRanking ? ListFragment.PREF_MONTH_RANK : ListFragment.PREF_RANK;
            final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            final long userRank = prefs.getLong(userRankKey, 0);
            final long startRank = userRank - 50;
            pageStart = startRank > 0 ? startRank : 0;
            selected = startRank < 0 ? userRank : 50;
            selected -= 5;
            if (selected < 0) selected = 0;
        }
        final long finalSelected = selected;

        final String cacheName = (doMonthRanking ? "month" : "all") + top;

        final String monthUrl = UrlConfig.RANK_STATS_URL + "?pagestart=" + pageStart
                + "&pageend=" + (pageStart + ROW_COUNT) + "&sort=" + sort;
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                "rank-stats-" + cacheName + "-cache.json", monthUrl, false, false, false,
                ApiDownloader.REQUEST_GET,
                (json, isCache1) -> handleRankStats(json, handler, finalSelected));
        task.setCacheOnly(isCache);
        try {
            task.startDownload(this);
        } catch (WiGLEAuthException waex) {
            Logging.info("Rank Stats Download Failed due to failed auth");
            WiGLEToast.showOverFragment(getActivity(), R.string.error_general, getString(R.string.error_general));
        }
    }

    private void setupListView(final View view) {
        final Activity a = getActivity();
        if (null != a) {
            final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            if (listAdapter == null) {
                listAdapter = new RankListAdapter(getActivity(), R.layout.rankrow);
            } else if (!listAdapter.isEmpty() && !TokenAccess.hasApiToken(prefs)) {
                listAdapter.clear();
            }
            // always set our current list adapter
            final ListView listView = view.findViewById(R.id.rank_list_view);
            listView.setAdapter(listAdapter);
        }
    }

    private final static class RankDownloadHandler extends DownloadHandler {
        private RankListAdapter rankListAdapter;
        final AtomicBoolean monthRanking;

        private RankDownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                                final Resources resources, final AtomicBoolean monthRanking) {
            super(view, numberFormat, packageName, resources);
            this.monthRanking = monthRanking;
        }

        public void setRankListAdapter(final RankListAdapter rankListAdapter) {
            this.rankListAdapter = rankListAdapter;
        }

        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            final ArrayList<Parcelable> results = bundle.getParcelableArrayList(RESULT_LIST_KEY);
            // MainActivity.info("handleMessage. results: " + results);
            if (msg.what == MSG_RANKING_DONE && results != null && rankListAdapter != null) {
                TextView tv = view.findViewById(R.id.rankstats_type);
                final boolean doMonthRanking = monthRanking.get();
                tv.setText(doMonthRanking ? R.string.monthcount_title : R.string.all_time_title);
                final String rankDiffKey = doMonthRanking ? KEY_PREV_MONTH_RANK : KEY_PREV_RANK;
                final long selected = bundle.getLong(KEY_SELECTED);

                rankListAdapter.clear();
                rankListAdapter.setMonthRanking(monthRanking.get());
                for (final Parcelable result : results) {
                    if (result instanceof Bundle) {
                        final Bundle row = (Bundle) result;
                        final long rankDiff = row.getLong(rankDiffKey) - row.getLong(KEY_RANK);
                        final RankUser rankUser = new RankUser(row.getLong(KEY_RANK), rankDiff,
                                row.getString(KEY_USERNAME), row.getLong(KEY_MONTH_WIFI_GPS),
                                row.getLong(KEY_TOTAL_WIFI_GPS), row.getLong(KEY_TOTAL_BT_GPS),
                                row.getLong(KEY_TOTAL_CELL_GPS));
                        rankListAdapter.add(rankUser);
                    }
                }
                final ListView listView = view.findViewById(R.id.rank_list_view);
                listView.setSelectionFromTop((int)selected, 20);

                final SwipeRefreshLayout swipeRefreshLayout =
                        view.findViewById(R.id.rank_swipe_container);
                swipeRefreshLayout.setRefreshing(false);
            }
            //TODO: swipeRefreshLayout.setRefreshing(false); anyway if request is done?
        }
    }

    private void handleRankStats(final JSONObject json, final Handler handler, final long selected) {
        Logging.info("handleRankStats");
        if (json == null) {
            Logging.info("handleRankStats null json, returning");
            return;
        }

        final Bundle bundle = new Bundle();
        bundle.putLong(KEY_SELECTED, selected);
        try {
            final JSONArray list = json.getJSONArray(RESULT_LIST_KEY);
            final ArrayList<Parcelable> resultList = new ArrayList<>(list.length());
            for (int i = 0; i < list.length(); i++) {
                final JSONObject row = list.getJSONObject(i);
                final Bundle rowBundle = new Bundle();
                for (final String key : ALL_ROW_KEYS) {
                    if (row.has(key)) {
                        rowBundle.putLong(key, row.getLong(key));
                    }
                }
                rowBundle.putString(KEY_USERNAME, row.getString(KEY_USERNAME));
                resultList.add(rowBundle);
            }
            bundle.putParcelableArrayList(RESULT_LIST_KEY, resultList);
        } catch (final JSONException ex) {
            //TODO: better error for bundle
            Logging.error("json error: " + ex, ex);
        } catch (final Exception e) {
            //TODO: better error for bundle
            Logging.error("rank error: " + e, e);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_RANKING_DONE;
        handler.sendMessage(message);
    }

    @Override
    public void onDestroy() {
        Logging.info( "RANKSTATS: onDestroy" );
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("RANKSTATS: onResume");
        super.onResume();
        final Activity a = getActivity();
        if (null != a) {
            a.setTitle(R.string.rank_stats_app_name);
        }
    }

    @Override
    public void onStart() {
        Logging.info( "RANKSTATS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        Logging.info( "RANKSTATS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        Logging.info( "RANKSTATS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig ) {
        Logging.info("RANKSTATS: config changed");
        super.onConfigurationChanged( newConfig );
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, @NonNull final MenuInflater inflater) {
        MenuItem item = menu.add(0, MENU_USER_STATS, 0, getString(R.string.user_stats_app_name));
        item.setIcon( android.R.drawable.ic_menu_myplaces );
        MenuItemCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_USER_STATS, 0, getString(R.string.user_stats_app_name));
        item.setIcon(android.R.drawable.ic_menu_myplaces);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon( R.drawable.wiglewifi_small_black_white );
        MenuItemCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon(R.drawable.wiglewifi_small_black_white);

        item = menu.add(0, MENU_RANK_SWAP, 0, getRankSwapString());
        item.setIcon(android.R.drawable.ic_menu_sort_alphabetically);

        item = menu.add(0, MENU_USER_CENTRIC_SWAP, 0, getUserCentricSwapString());
        item.setIcon(android.R.drawable.picture_frame);

        super.onCreateOptionsMenu(menu, inflater);
    }

    private String getRankSwapString() {
        return getString(monthRanking.get() ? R.string.rank_all_time : R.string.rank_month);
    }

    private String getUserCentricSwapString() {
        return getString(userCentric.get() ? R.string.not_user_centric : R.string.user_centric);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity();
        final Activity a = getActivity();
        if (null != a) {
            NavigationView navigationView = a.findViewById(R.id.left_drawer);
            switch ( item.getItemId() ) {
                case MENU_USER_STATS:
                    MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_user_stats);
                    return true;
                case MENU_SITE_STATS:
                    MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_site_stats);
                    return true;
                case MENU_RANK_SWAP:
                    monthRanking.set(!monthRanking.get());
                    item.setTitle(getRankSwapString());
                    downloadRanks(false);
                    return true;
                case MENU_USER_CENTRIC_SWAP:
                    userCentric.set(!userCentric.get());
                    item.setTitle(getUserCentricSwapString());
                    downloadRanks(false);
                    return true;
            }
        }
        return false;
    }

}
