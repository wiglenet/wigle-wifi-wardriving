package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
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

import net.wigle.wigleandroid.model.RankUser;
import net.wigle.wigleandroid.model.api.RankResponse;
import net.wigle.wigleandroid.model.api.UserStats;
import net.wigle.wigleandroid.net.AuthenticatedRequestCompletedListener;
import net.wigle.wigleandroid.net.RequestCompletedListener;
import net.wigle.wigleandroid.ui.EndlessScrollListener;
import net.wigle.wigleandroid.ui.ProgressThrobberFragment;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MenuUtil;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User rank fragment. Direct port from old code the OkHttp-based downloader for now.
 * TODO need to actually paginate correctly for continuous scroll
 */
public class RankStatsFragment extends ProgressThrobberFragment {
    private static final int MENU_USER_STATS = 200;
    private static final int MENU_SITE_STATS = 201;
    private static final int MENU_RANK_SWAP = 202;
    private static final int MENU_USER_CENTRIC_SWAP = 203;

    private static final int DEFAULT_PAGE_ROW_COUNT = 100;

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;
    private RankListAdapter listAdapter;
    private AtomicBoolean monthRankingMode;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private AtomicBoolean userCentric;
    private RankResponse rankResponse;
    private long myPage;
    private long myRank;
    private long currentPage = -1;
    private boolean userDownloadFailed = false;
    private LinearLayout rootView;
    private ListView listView;
    SwipeRefreshLayout swipeContainer;
    TextView typeView;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("RANKSTATS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language, volume, number format
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
            numberFormat = NumberFormat.getNumberInstance(MainActivity.getLocale(a, a.getResources().getConfiguration()));
        } else {
            numberFormat = NumberFormat.getNumberInstance(Locale.US);
        }

        finishing = new AtomicBoolean(false);
        monthRankingMode = new AtomicBoolean(false);
        userCentric = new AtomicBoolean(true);
        if (null != numberFormat && numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        Logging.info("RANKSTATS: onCreateView.a orientation: " + orientation);
        rootView = (LinearLayout) inflater.inflate(R.layout.rankstats, container, false);
        typeView = rootView.findViewById(R.id.rankstats_type);
        loadingImage = rootView.findViewById(R.id.rank_throbber);
        errorImage = rootView.findViewById(R.id.rank_error);

        setupSwipeRefresh(rootView);
        setupListView(rootView);

        final FragmentActivity a = getActivity();
        if (null != a) {
            startAnimation();
            final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);

            final MainActivity.State s = MainActivity.getStaticState();
            if (null != s) {
                s.apiManager.getUserStats(new AuthenticatedRequestCompletedListener<UserStats, JSONObject>() {
                    @Override
                    public void onAuthenticationRequired() {
                        showAuthDialog();
                    }

                    @Override
                    public void onTaskCompleted() {
                        stopAnimation();
                        if (userDownloadFailed) {
                            WiGLEToast.showOverFragment(a, R.string.upload_failed,
                                    a.getResources().getString(R.string.dl_failed));
                        }
                    }

                    @Override
                    public void onTaskSucceeded(UserStats response) {
                          myRank = response.getRank();
                          final SharedPreferences.Editor editor = prefs.edit();
                          editor.putLong(ListFragment.PREF_RANK, response.getRank());
                          editor.putLong(ListFragment.PREF_MONTH_RANK, response.getMonthRank());
                          editor.apply();
                          downloadRanks(true);
                          userDownloadFailed = false;
                    }

                      @Override
                      public void onTaskFailed(int status, JSONObject error) {
                          userDownloadFailed = true;
                      }
                  }
                );
            }
        }
        return rootView;
    }

    private void setupSwipeRefresh(final LinearLayout rootView) {
        // Lookup the swipe container view
        swipeContainer = rootView.findViewById(R.id.rank_swipe_container);
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(() -> {
            hideError();
            if (isRefreshing.compareAndSet(false,true)) {
                if (null != listAdapter) {
                    listAdapter.clear();
                }
                downloadRanks(true);
            }
        });
    }

    private void setSwipeRefreshDone(final LinearLayout rootView) {
        // Lookup the swipe container view
        final SwipeRefreshLayout swipeContainer = rootView.findViewById(R.id.rank_swipe_container);
        swipeContainer.setRefreshing(false);
    }

    private void downloadRanks(final boolean first) {
        if (busy.compareAndSet(false, true)) {
            final FragmentActivity fragmentActivity = getActivity();
            if (fragmentActivity == null) {
                Logging.error("downloadRanks: fragmentActivity is null");
                return;
            }
            final boolean doMonthRanking = monthRankingMode.get();
            final String sort = doMonthRanking ? "monthcount" : "discovered";
            long pageStart = 0;
            long pageEnd = DEFAULT_PAGE_ROW_COUNT;
            long selected = 0;
            Long userRank;
            if (userCentric.get()) {
                final String userRankKey = doMonthRanking ? ListFragment.PREF_MONTH_RANK : ListFragment.PREF_RANK;
                final SharedPreferences prefs = fragmentActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                userRank = prefs.getLong(userRankKey, 0);
                myPage = (userRank / DEFAULT_PAGE_ROW_COUNT);
                if (first) {
                    //ALIBI: load an initial "megapage" from zero to the user's rank (to nearest DEFAULT_PAGE_ROW_COUNT)
                    pageStart = 0;
                    currentPage = myPage;
                    pageEnd = ((currentPage + 1) * DEFAULT_PAGE_ROW_COUNT);
                } else {
                    pageStart = currentPage * DEFAULT_PAGE_ROW_COUNT;
                    pageEnd = pageStart+ DEFAULT_PAGE_ROW_COUNT;
                }
                selected = (userRank != null && userRank > 0) ? Math.max(userRank -5, 5) : 45;
            } else {
                myPage = 0;
            }
            final long finalSelected = selected;

            MainActivity.State s = MainActivity.getStaticState();
            if (s != null) {
                //DEBUG:
                Logging.info("getting ranks: "+pageStart + " end: "+ pageEnd + " usercentric: " + userCentric.get() +", sort:" + sort);

                s.apiManager.getRank(pageStart, pageEnd, sort,
                        finalSelected, new RequestCompletedListener<RankResponse, JSONObject>() {
                    @Override
                    public void onTaskCompleted() {
                        stopAnimation();
                        swipeContainer.setRefreshing(false);
                        if (null != rankResponse) {
                            handleRanks(rankResponse, first);
                        } else {
                            if (first) {
                                showError();
                            }
                            Logging.error("unable to populate ranks - rankResponse is null");
                        }
                        busy.set(false);
                    }

                    @Override
                    public void onTaskSucceeded(RankResponse response) {
                        rankResponse = response;
                    }

                    @Override
                    public void onTaskFailed(int status, JSONObject error) {
                        rankResponse = null;
                    }
                });
            }
        } else {
            Logging.error("preventing download because previous is still in progress.");
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
            listView = view.findViewById(R.id.rank_list_view);
            listView.setAdapter(listAdapter);
            listView.setOnScrollListener(new EndlessScrollListener() {
                @Override
                public boolean onLoadMore(int page, int totalItemsCount) {
                    Logging.info("downloading subsequent ranks...");
                    currentPage++;
                    downloadRanks(false);
                    return true;
                }
            });
        }
    }

    public void handleRanks(final RankResponse ranks, final boolean first) {
        if (ranks != null && listAdapter != null) {
            final boolean doMonthRanking = monthRankingMode.get();
            typeView.setText(doMonthRanking ? R.string.monthcount_title : R.string.all_time_title);
            if (isRefreshing.compareAndSet(true, false) || first) {
                listAdapter.clear();
                swipeContainer = rootView.findViewById(R.id.rank_swipe_container);
                swipeContainer.setRefreshing(false);
            }
            listAdapter.setMonthRanking(monthRankingMode.get());
            for (final RankResponse.RankResponseRow result : ranks.getResults()) {
                final RankUser rankUser = new RankUser(result, doMonthRanking);
                listAdapter.add(rankUser);
            }
            if (first) {
                listView.setSelectionFromTop((int) ranks.getSelected(), 20);
            }
            //setupSwipeRefresh(rootView);
        } else {
            Logging.error("null ranks or list adapter - unable to refresh.");
        }
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
        item.setIcon(R.drawable.ic_planet_small_white);
        MenuItemCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon(R.drawable.ic_planet_small_white);
        item = menu.add(0, MENU_RANK_SWAP, 0, getRankSwapString());
        item.setIcon(R.drawable.sort);

        item = menu.add(0, MENU_USER_CENTRIC_SWAP, 0, getUserCentricSwapString());
        item.setIcon(android.R.drawable.picture_frame);

        super.onCreateOptionsMenu(menu, inflater);
    }

    private String getRankSwapString() {
        return getString(monthRankingMode.get() ? R.string.rank_all_time : R.string.rank_month);
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
                    monthRankingMode.set(!monthRankingMode.get());
                    item.setTitle(getRankSwapString());
                    downloadRanks(true);
                    return true;
                case MENU_USER_CENTRIC_SWAP:
                    userCentric.set(!userCentric.get());
                    item.setTitle(getUserCentricSwapString());
                    downloadRanks(true);
                    return true;
            }
        }
        return false;
    }
}
