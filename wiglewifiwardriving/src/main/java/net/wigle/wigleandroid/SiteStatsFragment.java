package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.view.MenuItemCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

import net.wigle.wigleandroid.net.RequestCompletedListener;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MenuUtil;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SiteStatsFragment extends Fragment {
    private static final int MENU_USER_STATS = 200;
    private static final int MENU_RANK_STATS = 202;

    private static final String KEY_NETLOC = "netloc";
    private static final String KEY_LOCTOTAL = "loctotal";
    private static final String KEY_BTLOC = "btloc";
    private static final String KEY_GENLOC = "genloc";
    private static final String KEY_USERSTOT = "userstot";
    private static final String KEY_TRANSTOT = "transtot";
    private static final String KEY_NETWPA3 = "netwpa3";
    private static final String KEY_NETWPA2 = "netwpa2";
    private static final String KEY_NETWPA = "netwpa";
    private static final String KEY_NETWEP = "netwep";
    private static final String KEY_NETNOWEP = "netnowep";
    private static final String KEY_NETWEP_UNKNOWN = "netwepunknown";
    private static final String API_NETWEP_UNKNOWN = "netwep?";

    private static final String[] ALL_SITE_KEYS = new String[] {
        KEY_NETLOC, KEY_LOCTOTAL, KEY_BTLOC, KEY_GENLOC, KEY_USERSTOT, KEY_TRANSTOT,
        KEY_NETWPA3, KEY_NETWPA2, KEY_NETWPA, KEY_NETWEP, KEY_NETNOWEP, KEY_NETWEP_UNKNOWN,
        };

    private ScrollView scrollView;
    private View landscape;
    private View portrait;

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;
    private Map<String,Long> siteStats;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("SITESTATS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            numberFormat = NumberFormat.getNumberInstance(MainActivity.getLocale(a, a.getResources().getConfiguration()));
            // media volume
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }

        finishing = new AtomicBoolean(false);
        if (null != numberFormat && numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        Logging.info("SITESTATS: onCreateView. orientation: " + orientation);
        scrollView = (ScrollView) inflater.inflate(R.layout.sitestats, container, false);
        landscape = inflater.inflate(R.layout.sitestatslandscape, container, false);
        portrait = inflater.inflate(R.layout.sitestatsportrait, container, false);
        switchView();

        return scrollView;
    }

    private void switchView() {
        if (scrollView != null) {
            final int orientation = getResources().getConfiguration().orientation;
            View component = portrait;
            if (orientation == 2) {
                component = landscape;
            }
            scrollView.removeAllViews();
            scrollView.addView(component);
            downloadLatestSiteStats(scrollView);

        }
    }

    public void downloadLatestSiteStats(final View view) {
        MainActivity.State s = MainActivity.getStaticState();
        if (s != null) {
            s.apiManager.getSiteStats(new RequestCompletedListener<Map<String,Long>, JSONObject>() {
                @Override
                public void onTaskCompleted() {
                    TextView tv;
                    final Activity a = getActivity();
                    if (null != a) {
                        for (final String key : ALL_SITE_KEYS) {
                            int id = getResources().getIdentifier(key, "id", getActivity().getPackageName());
                            tv = view.findViewById(id);
                            try {
                                if (tv != null) {
                                    if (KEY_NETWEP_UNKNOWN.equals(key)) {
                                        tv.setText(numberFormat.format(siteStats.get(API_NETWEP_UNKNOWN))); //ALIBI: Android doesn't like question marks in resource IDs.
                                    } else {
                                        tv.setText(numberFormat.format(siteStats.get(key)));
                                    }
                                }
                            } catch (Exception e) {
                                Logging.error("failed to format: "+key);
                            }
                        }
                        Logging.info("SITESTATS: load completed.");
                    }
                }

                @Override
                public void onTaskSucceeded(Map<String,Long> response) {
                    handleSiteStats(response);
                }

                @Override
                public void onTaskFailed(int status, JSONObject error) {
                    Logging.error("SITESTATS: failed: " + status);
                    //no-op for now. maybe show an error toast?
                }
            });
        }
    }

    private void handleSiteStats(final Map<String,Long> stats) {
        Logging.info("handleSiteStats");
        if (stats == null) {
            Logging.info("handleSiteStats null json, returning");
            return;
        }
        this.siteStats = stats;
    }

    @Override
    public void onDestroy() {
        Logging.info( "SITESTATS: onDestroy" );
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("SITESTATS: onResume");
        super.onResume();
        final Activity a = getActivity();
        if (null != a) {
            a.setTitle(R.string.site_stats_app_name);
        }
    }

    @Override
    public void onStart() {
        Logging.info( "SITESTATS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        Logging.info( "SITESTATS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        Logging.info( "SITESTATS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig ) {
        Logging.info("SITESTATS: config changed");
        switchView();
        super.onConfigurationChanged( newConfig );
    }


    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, @NonNull final MenuInflater inflater) {
        MenuItem item = menu.add(0, MENU_USER_STATS, 0, getString(R.string.user_stats_app_name));
        item.setIcon( R.drawable.user_star );
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_USER_STATS, 0, getString(R.string.user_stats_app_name));
        item.setIcon(R.drawable.user_star);

        item = menu.add(0, MENU_RANK_STATS, 0, getString(R.string.rank_stats_app_name));
        item.setIcon(R.drawable.rankings);
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_RANK_STATS, 0, getString(R.string.rank_stats_app_name));
        item.setIcon(R.drawable.rankings);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity();
        NavigationView navigationView = getActivity().findViewById(R.id.left_drawer);
        switch ( item.getItemId() ) {
            case MENU_USER_STATS:
                MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_user_stats);
                return true;
            case MENU_RANK_STATS:
                MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_rank);
                return true;
        }
        return false;
    }

}
