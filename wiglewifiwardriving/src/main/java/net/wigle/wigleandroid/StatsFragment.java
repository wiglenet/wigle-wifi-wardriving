package net.wigle.wigleandroid;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class StatsFragment extends Fragment {
    private static final int MSG_SITE_DONE = 100;

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


    private static final String[] ALL_KEYS = new String[] {
        KEY_NETLOC, KEY_LOCTOTAL, KEY_GENLOC, KEY_USERSTOT, KEY_TRANSTOT,
        KEY_NETWPA2, KEY_NETWPA, KEY_NETWEP, KEY_NETNOWEP, KEY_NETWEP_UNKNOWN,
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("STATS: onCreate");
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

        // download token if needed
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final boolean beAnonymous = prefs.getBoolean( ListFragment.PREF_BE_ANONYMOUS, false);
        MainActivity.info("authname: " + prefs.getString(ListFragment.PREF_AUTHNAME, null));
        if (!beAnonymous && prefs.getString(ListFragment.PREF_AUTHNAME, null) == null) {
            MainActivity.info("No authname, going to request token");
            downloadToken(null);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        MainActivity.info("STATS: onCreateView. orientation: " + orientation);
        final ScrollView scrollView = (ScrollView) inflater.inflate(R.layout.stats, container, false);

        // XXX: don't do this if it has been done recently
        downloadLatestSiteStats(scrollView);

        return scrollView;

    }

    private final static class DownloadHandler extends Handler {
        private final View view;
        private final NumberFormat numberFormat;
        private final String packageName;
        private final Resources resources;

        private DownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                                final Resources resources) {
            this.view = view;
            this.numberFormat = numberFormat;
            this.packageName = packageName;
            this.resources = resources;
        }

        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            if (msg.what == MSG_SITE_DONE) {
                TextView tv;

                for (final String key : ALL_KEYS) {
                    int id = resources.getIdentifier(key, "id", packageName);
                    tv = (TextView) view.findViewById(id);
                    tv.setText(numberFormat.format(bundle.getLong(key)));
                }

            }
        }
    }

    public void downloadLatestSiteStats(final View view) {
        // what runs on the gui thread
        final Handler handler = new DownloadHandler(view, numberFormat, getActivity().getPackageName(),
                getResources());

        final String siteStatsCacheFilename = "site-stats-cache.json";
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                siteStatsCacheFilename, MainActivity.SITE_STATS_URL, false,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json) {
                        handleSiteStats(json, handler);
                    }
                });

        handleSiteStats(task.getCached(), handler);

        task.start();
    }

    private void handleSiteStats(final JSONObject json, final Handler handler) {
        MainActivity.info("handleSiteStats");
        if (json == null) {
            MainActivity.info("handleSiteStats null json, returning");
            return;
        }

        final Bundle bundle = new Bundle();
        try {
            for (final String key : ALL_KEYS) {
                String jsonKey = key;
                if (KEY_NETWEP_UNKNOWN.equals(key)) jsonKey = "netwep?";
                bundle.putLong(key, json.getLong(jsonKey));
            }
        }
        catch (final JSONException ex) {
            MainActivity.error("json error: " + ex, ex);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_SITE_DONE;
        handler.sendMessage(message);
    }

    public void downloadToken(final ApiDownloader pendingTask) {
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                null, MainActivity.TOKEN_URL, true,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json) {
                        try {
                            // {"success":true,"result":{"authname":"AID...","token":"..."}}
                            if (json.getBoolean("success")) {
                                final JSONObject result = json.getJSONObject("result");
                                final String authname = result.getString("authname");
                                final String token = result.getString("token");
                                final SharedPreferences prefs =
                                        getContext().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                                final SharedPreferences.Editor edit = prefs.edit();
                                edit.putString(ListFragment.PREF_AUTHNAME, authname);
                                edit.putString(ListFragment.PREF_TOKEN, token);
                                edit.apply();

                                // execute pending task
                                if (pendingTask != null) {
                                    pendingTask.start();
                                }
                            }
                        }
                        catch (final JSONException ex) {
                            MainActivity.warn("json exception: " + ex + " json: " + json, ex);
                        }
                    }
                });

        task.start();
    }

    @Override
    public void onDestroy() {
        MainActivity.info( "STATS: onDestroy" );
        finishing.set( true );

        super.onDestroy();
    }

    @Override
    public void onResume() {
        MainActivity.info("STATS: onResume");
        super.onResume();
        getActivity().setTitle(R.string.stats_app_name);
    }

    @Override
    public void onStart() {
        MainActivity.info( "STATS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        MainActivity.info( "STATS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        MainActivity.info( "STATS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
        MainActivity.info("STATS: config changed");
        super.onConfigurationChanged( newConfig );
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
        // MenuItem item = menu.add(0, MENU_SETTINGS, 0, getString(R.string.menu_settings));
        // tem.setIcon( android.R.drawable.ic_menu_preferences );

        // item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_exit));
        // item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
//      switch ( item.getItemId() ) {
//        case MENU_EXIT:
//          final MainActivity main = MainActivity.getMainActivity();
//          main.finish();
//          return true;
//        case MENU_SETTINGS:
//          final Intent settingsIntent = new Intent( getActivity(), SettingsFragment.class );
//          startActivity( settingsIntent );
//          break;
//      }
        return false;
    }

}
