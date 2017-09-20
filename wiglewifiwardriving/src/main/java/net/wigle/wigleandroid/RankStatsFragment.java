package net.wigle.wigleandroid;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
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
import net.wigle.wigleandroid.model.RankUser;

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
    private static final String KEY_TOTAL_CELL_GPS = "discoveredCellGPS";
    private static final String KEY_RANK = "rank";
    private static final String KEY_USERNAME = "userName";
    private static final String KEY_PREV_RANK = "prevRank";
    private static final String KEY_PREV_MONTH_RANK = "prevMonthRank";
    private static final String KEY_SELECTED = "selected";

    private static final int ROW_COUNT = 100;

    private static final String[] ALL_ROW_KEYS = new String[] {
            KEY_MONTH_WIFI_GPS, KEY_TOTAL_WIFI_GPS, KEY_TOTAL_CELL_GPS, KEY_RANK, KEY_PREV_RANK, KEY_PREV_MONTH_RANK,
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
        MainActivity.info("RANKSTATS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        MainActivity.setLocale(getActivity());

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
        MainActivity.info("RANKSTATS: onCreateView.a orientation: " + orientation);
        final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.rankstats, container, false);
        setupSwipeRefresh(rootView);
        setupListView(rootView);

        handler = new RankDownloadHandler(rootView, numberFormat,
                getActivity().getPackageName(), getResources(), monthRanking);
        handler.setRankListAdapter(listAdapter);
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);

        //TODO: we should only perform user DL if there's a user set
        UserStatsFragment.executeUserDownload(this, new UserStatsFragment.UserDownloadApiListener(new Handler() {
            @Override
            public void handleMessage(final Message msg) {
                final Bundle bundle = msg.getData();
                final boolean isCache = bundle.getBoolean(UserStatsFragment.KEY_IS_CACHE);
                MainActivity.info("got user message, isCache: " + isCache);

                final SharedPreferences.Editor editor = prefs.edit();
                editor.putLong( ListFragment.PREF_RANK, bundle.getLong(UserStatsFragment.KEY_RANK) );
                editor.putLong( ListFragment.PREF_MONTH_RANK, bundle.getLong(UserStatsFragment.KEY_MONTH_RANK) );
                editor.apply();
                downloadRanks(isCache);
            }
        }));

        return rootView;
    }

    private void setupSwipeRefresh(final LinearLayout rootView) {
        // Lookup the swipe container view
        final SwipeRefreshLayout swipeContainer = (SwipeRefreshLayout) rootView.findViewById(R.id.rank_swipe_container);

        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Your code to refresh the list here.
                // Make sure you call swipeContainer.setRefreshing(false)
                // once the network request has completed successfully.
                downloadRanks(false);
            }
        });
    }

    private void downloadRanks(final boolean isCache) {
        if (handler == null) {
            MainActivity.error("downloadRanks: handler is null");
            return;
        }
        final FragmentActivity fragmentActivity = getActivity();
        if (fragmentActivity == null) {
            MainActivity.error("downloadRanks: fragmentActivity is null");
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
            final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            final long userRank = prefs.getLong(userRankKey, 0);
            final long startRank = userRank - 50;
            pageStart = startRank > 0 ? startRank : 0;
            selected = startRank < 0 ? userRank : 50;
            selected -= 5;
            if (selected < 0) selected = 0;
        }
        final long finalSelected = selected;

        final String cacheName = (doMonthRanking ? "month" : "all") + top;

        final String monthUrl = MainActivity.RANK_STATS_URL + "?pagestart=" + pageStart
                + "&pageend=" + (pageStart + ROW_COUNT) + "&sort=" + sort;
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                "rank-stats-" + cacheName + "-cache.json", monthUrl, false, false, false,
                ApiDownloader.REQUEST_GET,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json, final boolean isCache) {
                        handleRankStats(json, handler, finalSelected);
                    }
                });
        task.setCacheOnly(isCache);
        try {
            task.startDownload(this);
        } catch (WiGLEAuthException waex) {
            //TODO: toast? *shouldn't* be authed, but a UserStats call may have been issued in error
            MainActivity.info("Rank Stats Download Failed due to failed auth");
        }
    }

    private void setupListView(final View view) {
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        if (listAdapter == null) {
            listAdapter = new RankListAdapter(getActivity().getApplicationContext(), R.layout.rankrow);
        } else if (!listAdapter.isEmpty() && !TokenAccess.hasApiToken(prefs)) {
            listAdapter.clear();
        }

        // always set our current list adapter
        final ListView listView = (ListView) view.findViewById(R.id.rank_list_view);
        listView.setAdapter(listAdapter);

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
                TextView tv = (TextView) view.findViewById(R.id.rankstats_type);
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
                                row.getLong(KEY_TOTAL_WIFI_GPS), row.getLong(KEY_TOTAL_CELL_GPS));
                        rankListAdapter.add(rankUser);
                    }
                }
                final ListView listView = (ListView) view.findViewById(R.id.rank_list_view);
                listView.setSelectionFromTop((int)selected, 20);

                final SwipeRefreshLayout swipeRefreshLayout =
                        (SwipeRefreshLayout) view.findViewById(R.id.rank_swipe_container);
                swipeRefreshLayout.setRefreshing(false);
            }
            //TODO: swipeRefreshLayout.setRefreshing(false); anyway if request is done?
        }
    }

    private void handleRankStats(final JSONObject json, final Handler handler, final long selected) {
        MainActivity.info("handleRankStats");
        if (json == null) {
            MainActivity.info("handleRankStats null json, returning");
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
            MainActivity.error("json error: " + ex, ex);
        } catch (final Exception e) {
            //TODO: better error for bundle
            MainActivity.error("rank error: " + e, e);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_RANKING_DONE;
        handler.sendMessage(message);
    }

    @Override
    public void onDestroy() {
        MainActivity.info( "RANKSTATS: onDestroy" );
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        MainActivity.info("RANKSTATS: onResume");
        super.onResume();
        getActivity().setTitle(R.string.rank_stats_app_name);
    }

    @Override
    public void onStart() {
        MainActivity.info( "RANKSTATS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        MainActivity.info( "RANKSTATS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        MainActivity.info( "RANKSTATS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
        MainActivity.info("RANKSTATS: config changed");
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
    public boolean onOptionsItemSelected( final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity();
        switch ( item.getItemId() ) {
            case MENU_USER_STATS:
                main.selectFragment(MainActivity.USER_STATS_TAB_POS);
                return true;
            case MENU_SITE_STATS:
                main.selectFragment(MainActivity.SITE_STATS_TAB_POS);
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
        return false;
    }

}
