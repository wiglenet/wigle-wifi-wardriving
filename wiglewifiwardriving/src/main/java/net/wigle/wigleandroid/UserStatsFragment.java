package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.caverock.androidsvg.SVG;
import com.google.android.material.navigation.NavigationView;

import net.wigle.wigleandroid.model.api.UserStats;
import net.wigle.wigleandroid.net.AuthenticatedRequestCompletedListener;
import net.wigle.wigleandroid.ui.AuthenticatedFragment;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MenuUtil;
import net.wigle.wigleandroid.util.UrlConfig;

import org.json.JSONObject;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserStatsFragment extends AuthenticatedFragment {
    private static final int MENU_SITE_STATS = 201;
    private static final int MENU_RANK_STATS = 202;

    public static final String KEY_RANK = "rank";
    private static final String KEY_PREV_RANK = "prevRank";
    public static final String KEY_MONTH_RANK = "monthRank";
    private static final String KEY_PREV_MONTH_RANK = "prevMonthRank";
    private static final String KEY_DISCOVERED = "discoveredWiFiGPS";
    private static final String KEY_TOTAL = "discoveredWiFi";
    private static final String KEY_TOTAL_LOCS = "totalWiFiLocations";
    private static final String KEY_BT_DISC = "discoveredBtGPS";
    private static final String KEY_BT_TOTAL = "discoveredBt";
    private static final String KEY_GEN_DISC = "discoveredCellGPS";
    private static final String KEY_GEN_TOTAL = "discoveredCell";
    private static final String KEY_MONTH_COUNT = "eventMonthCount";
    private static final String KEY_PREV_MONTH = "eventPrevMonthCount";
    private static final String KEY_FIRST_TRANS = "first";
    private static final String KEY_LAST_TRANS = "last";
    private static final String[] ALL_USER_KEYS = new String[] {
            KEY_RANK, KEY_PREV_RANK, KEY_MONTH_RANK, KEY_PREV_MONTH_RANK, KEY_DISCOVERED, KEY_TOTAL, KEY_TOTAL_LOCS,
            KEY_BT_DISC, KEY_BT_TOTAL, KEY_GEN_DISC, KEY_GEN_TOTAL, KEY_MONTH_COUNT, KEY_PREV_MONTH, KEY_FIRST_TRANS,
            KEY_LAST_TRANS,
    };

    private static final int COLOR_UP = Color.rgb(30, 200, 30);
    private static final int COLOR_DOWN = Color.rgb(200, 30, 30);
    private static final int COLOR_BLANK = Color.rgb(80, 80, 80);

    private UserStats stats;
    private AtomicBoolean finishing;
    private NumberFormat numberFormat;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("USERSTATS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
            numberFormat = NumberFormat.getNumberInstance(MainActivity.getLocale(a, a.getResources().getConfiguration()));
        }
        finishing = new AtomicBoolean(false);
        if (null != numberFormat && numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        } else {
            numberFormat = NumberFormat.getNumberInstance(Locale.US);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        Logging.info("USERSTATS: onCreateView. orientation: " + orientation);
        final ScrollView scrollView = (ScrollView) inflater.inflate(R.layout.userstats, container, false);
        MainActivity.State s = MainActivity.getStaticState();
        if (s != null) {
            s.apiManager.getUserStats(new AuthenticatedRequestCompletedListener<UserStats, JSONObject>() {
                @Override
                public void onAuthenticationRequired() {
                    final FragmentActivity fa = getActivity();
                    if (null != fa) {
                        showAuthDialog();
                    }
                }

                @Override
                public void onTaskCompleted() {
                    final Activity a = getActivity();
                    if (null != a) {
                        handleUserStats(scrollView, getResources(), getActivity().getPackageName());
                    }
                }

                @Override
                public void onTaskSucceeded(UserStats response) {
                    stats = response;
                }

                @Override
                public void onTaskFailed(int status, JSONObject error) {
                    Logging.error("SITESTATS: failed: " + status);
                    //no-op for now. maybe show an error toast?
                }
            });
        }
        return scrollView;
    }

    public void handleUserStats(final View view, final Resources resources, final String packageName) {
        TextView tv;
        if (null == this.stats) {
            Logging.error("Error trying to populate user stats: null results.");
            return;
        }
        for (final String key : ALL_USER_KEYS) {
            int id = resources.getIdentifier(key, "id", packageName);
            tv = view.findViewById(id);
            switch (key) {
                case KEY_FIRST_TRANS:
                    tv.setText(stats.getStatistics().getFirst());
                    break;
                case KEY_LAST_TRANS:
                    tv.setText(stats.getStatistics().getLast());
                    break;
                case KEY_PREV_RANK: {
                    final long diff = stats.getStatistics().getPrevRank() - stats.getRank();
                    diffToString(diff, tv);

                    tv = view.findViewById(R.id.actual_prevrank);
                    tv.setText(numberFormat.format(stats.getStatistics().getPrevRank()));
                    break;
                }
                case KEY_PREV_MONTH_RANK: {
                    final long diff = stats.getStatistics().getPrevMonthRank() - stats.getMonthRank();
                    diffToString(diff, tv);

                    tv = view.findViewById(R.id.actual_prevmonthrank);
                    tv.setText(numberFormat.format(stats.getStatistics().getPrevMonthRank()));
                    break;
                }

                default: {
                    long val = -1L;
                    switch (key) {
                        case KEY_RANK: {
                            val = stats.getRank();
                            break;
                        }
                        case KEY_MONTH_RANK: {
                            val = stats.getMonthRank();
                            break;
                        }
                        case KEY_DISCOVERED: {
                            val = stats.getStatistics().getDiscoveredWiFiGPS();
                            break;
                        }
                        case KEY_TOTAL: {
                            val = stats.getStatistics().getDiscoveredWiFi();
                            break;
                        }
                        case KEY_TOTAL_LOCS: {
                            val = stats.getStatistics().getTotalWiFiLocations();
                            break;
                        }
                        case KEY_BT_DISC: {
                            val = stats.getStatistics().getDiscoveredBtGPS();
                            break;
                        }
                        case KEY_BT_TOTAL: {
                            val = stats.getStatistics().getDiscoveredBt();
                            break;
                        }
                        case KEY_GEN_DISC: {
                            val = stats.getStatistics().getDiscoveredCellGPS();
                            break;
                        }
                        case KEY_GEN_TOTAL: {
                            val = stats.getStatistics().getDiscoveredCell();
                            break;
                        }
                        case KEY_MONTH_COUNT: {
                            val = stats.getStatistics().getEventMonthCount();
                            break;
                        }
                        case KEY_PREV_MONTH: {
                            val = stats.getStatistics().getEventPrevMonthCount();
                            break;
                        }
                    }
                    tv.setText(numberFormat.format(val));
                }
            }
        }
        if (null != stats.getImageBadgeUrl() && !stats.getImageBadgeUrl().isEmpty()) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.submit(new BadgeImageRunnable(handler, view.findViewById(R.id.badgeImage)));
        }
    }

    @SuppressLint("SetTextI18n")
    public static void diffToString(final long diff, final TextView tv) {
        if (diff == 0) {
            tv.setText("");
            tv.setTextColor(COLOR_BLANK);
            return;
        }

        String plus = "   ";
        if (diff > 0) {
            plus = "  ↑";
            tv.setTextColor(COLOR_UP);
        }
        else {
            plus = "  ↓";
            tv.setTextColor(COLOR_DOWN);
        }
        tv.setText(plus + diff);
    }

    @Override
    public void onDestroy() {
        Logging.info( "STATS: onDestroy" );
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("STATS: onResume");
        super.onResume();
        Activity a = getActivity();
        if (null != a) {
            a.setTitle(R.string.user_stats_app_name);
        }
    }

    @Override
    public void onStart() {
        Logging.info( "STATS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        Logging.info( "STATS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        Logging.info( "STATS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig ) {
        Logging.info("STATS: config changed");
        super.onConfigurationChanged( newConfig );
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, @NonNull final MenuInflater inflater) {
        MenuItem item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon( R.drawable.ic_planet_small_white );
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon(R.drawable.ic_planet_small_white);

        item = menu.add(0, MENU_RANK_STATS, 0, getString(R.string.rank_stats_app_name));
        item.setIcon(android.R.drawable.ic_menu_sort_by_size);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_RANK_STATS, 0, getString(R.string.rank_stats_app_name));
        item.setIcon(android.R.drawable.ic_menu_sort_by_size);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity();
        final Activity currentActivity = getActivity();
        if (null != currentActivity) {
            NavigationView navigationView = currentActivity.findViewById(R.id.left_drawer);
            switch (item.getItemId()) {
                case MENU_SITE_STATS:
                    MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_site_stats);
                    return true;
                case MENU_RANK_STATS:
                    MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_rank);
                    return true;
            }
        }
        return false;
    }

    private class BadgeImageRunnable implements Runnable {
        final Handler handler;
        final ImageView badgeImageView;

        public BadgeImageRunnable(final Handler handler, final ImageView badgeImageView) {
            this.handler = handler;
            this.badgeImageView = badgeImageView;
        }

        @Override
        public void run() {
            try (InputStream in = new java.net.URL(UrlConfig.WIGLE_BASE_URL+
                        stats.getImageBadgeUrl().replace(".png",".svg")).openStream()) {
                SVG svg = SVG.getFromInputStream(in);
                Drawable drawable = new PictureDrawable(svg.renderToPicture());
                handler.post(() -> {
                    badgeImageView.setImageDrawable(drawable);
                });
            } catch (Exception e) {
                Logging.error("Failed to download badge image ", e);
            }
        }
    }
}
