package net.wigle.wigleandroid;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import net.wigle.wigleandroid.background.ApiDownloader;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.DownloadHandler;

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
    private static final String KEY_GENLOC = "genloc";
    private static final String KEY_USERSTOT = "userstot";
    private static final String KEY_TRANSTOT = "transtot";
    private static final String KEY_NETWPA2 = "netwpa2";
    private static final String KEY_NETWPA = "netwpa";
    private static final String KEY_NETWEP = "netwep";
    private static final String KEY_NETNOWEP = "netnowep";
    private static final String KEY_NETWEP_UNKNOWN = "netwepunknown";


    private static final String[] ALL_SITE_KEYS = new String[] {
        KEY_NETLOC, KEY_LOCTOTAL, KEY_GENLOC, KEY_USERSTOT, KEY_TRANSTOT,
        KEY_NETWPA2, KEY_NETWPA, KEY_NETWEP, KEY_NETNOWEP, KEY_NETWEP_UNKNOWN,
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("SITESTATS: onCreate");
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
        MainActivity.info("SITESTATS: onCreateView. orientation: " + orientation);
        final ScrollView scrollView = (ScrollView) inflater.inflate(R.layout.sitestats, container, false);

        downloadLatestSiteStats(scrollView);
        return scrollView;

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
                "site-stats-cache.json", MainActivity.SITE_STATS_URL, false, false, false,
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
            MainActivity.warn("Authentication error on site stats load (should not happen)", waex);
        }
    }

    private void handleSiteStats(final JSONObject json, final Handler handler) {
        MainActivity.info("handleSiteStats");
        if (json == null) {
            MainActivity.info("handleSiteStats null json, returning");
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
            MainActivity.error("json error: " + ex, ex);
        } catch (final Exception e) {
            MainActivity.error("Statistics error: " + e, e);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_SITE_DONE;
        handler.sendMessage(message);
    }

    @Override
    public void onDestroy() {
        MainActivity.info( "SITESTATS: onDestroy" );
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        MainActivity.info("SITESTATS: onResume");
        super.onResume();
        getActivity().setTitle(R.string.site_stats_app_name);
    }

    @Override
    public void onStart() {
        MainActivity.info( "SITESTATS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        MainActivity.info( "SITESTATS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        MainActivity.info( "SITESTATS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
        MainActivity.info("SITESTATS: config changed");
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
        switch ( item.getItemId() ) {
            case MENU_USER_STATS:
                main.selectFragment(MainActivity.USER_STATS_TAB_POS);
                return true;
            case MENU_RANK_STATS:
                main.selectFragment(MainActivity.RANK_STATS_TAB_POS);
                return true;
        }
        return false;
    }

}
