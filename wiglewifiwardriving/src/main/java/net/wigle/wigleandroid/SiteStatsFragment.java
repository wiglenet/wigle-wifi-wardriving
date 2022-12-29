package net.wigle.wigleandroid;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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

import net.wigle.wigleandroid.background.ApiDownloader;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.DownloadHandler;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MenuUtil;
import net.wigle.wigleandroid.util.UrlConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SiteStatsFragment extends Fragment {
    private static final int MSG_SITE_DONE = 100;
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


    private static final String[] ALL_SITE_KEYS = new String[] {
        KEY_NETLOC, KEY_LOCTOTAL, KEY_BTLOC, KEY_GENLOC, KEY_USERSTOT, KEY_TRANSTOT,
        KEY_NETWPA3, KEY_NETWPA2, KEY_NETWPA, KEY_NETWEP, KEY_NETNOWEP, KEY_NETWEP_UNKNOWN,
        };

    private ScrollView scrollView;
    private View landscape;
    private View portrait;

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("SITESTATS: onCreate");
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

    private final static class SiteDownloadHandler extends DownloadHandler {
        private SiteDownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                                final Resources resources) {
            super(view, numberFormat, packageName, resources);
        }

        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            if (msg.what == MSG_SITE_DONE) {
                TextView tv;

                for (final String key : ALL_SITE_KEYS) {
                    int id = resources.getIdentifier(key, "id", packageName);
                    tv = (TextView) view.findViewById(id);
                    tv.setText(numberFormat.format(bundle.getLong(key)));
                }
            }
        }
    }

    public void downloadLatestSiteStats(final View view) {
        // what runs on the gui thread
        final Handler handler = new SiteDownloadHandler(view, numberFormat, getActivity().getPackageName(),
                getResources());
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                "site-stats-cache.json", UrlConfig.SITE_STATS_URL, false, false, false,
                ApiDownloader.REQUEST_GET,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json, final boolean isCache) {
                        handleSiteStats(json, handler);
                    }
                });
        try {
            task.startDownload(this);
        } catch (WiGLEAuthException waex) {
            //unauthenticated call - should never trip
            Logging.warn("Authentication error on site stats load (should not happen)", waex);
        }
    }

    private void handleSiteStats(final JSONObject json, final Handler handler) {
        Logging.info("handleSiteStats");
        if (json == null) {
            Logging.info("handleSiteStats null json, returning");
            return;
        }

        final Bundle bundle = new Bundle();
        try {
            for (final String key : ALL_SITE_KEYS) {
                String jsonKey = key;
                if (KEY_NETWEP_UNKNOWN.equals(key)) jsonKey = "netwep?";
                bundle.putLong(key, json.getLong(jsonKey));
            }
        } catch (final JSONException ex) {
            Logging.error("json error: " + ex, ex);
        } catch (final Exception e) {
            Logging.error("Statistics error: " + e, e);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_SITE_DONE;
        handler.sendMessage(message);
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
        getActivity().setTitle(R.string.site_stats_app_name);
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
    public void onConfigurationChanged( final Configuration newConfig ) {
        Logging.info("SITESTATS: config changed");
        switchView();
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

        item = menu.add(0, MENU_RANK_STATS, 0, getString(R.string.rank_stats_app_name));
        item.setIcon(android.R.drawable.ic_menu_sort_by_size);
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_RANK_STATS, 0, getString(R.string.rank_stats_app_name));
        item.setIcon(android.R.drawable.ic_menu_sort_by_size);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        final MainActivity main = MainActivity.getMainActivity();
        NavigationView navigationView = (NavigationView) getActivity().findViewById(R.id.left_drawer);
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
