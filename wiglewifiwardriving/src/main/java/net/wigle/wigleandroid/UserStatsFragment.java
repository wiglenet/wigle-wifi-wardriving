package net.wigle.wigleandroid;

import android.content.SharedPreferences;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserStatsFragment extends Fragment {
    private static final int MSG_USER_DONE = 101;
    private static final int MENU_SITE_STATS = 201;

    // {"success":true,"statistics":{"visible":"Y","gendisc":"6439","total":"897432","discovered":"498732",
    // "prevmonthcount":"1814","lasttransid":"20151114-00277","monthcount":"34","totallocs":"8615324","gentotal":"9421",
    // "firsttransid":"20010907-01998"},"imageBadgeUrl":"\/bi\/asdf.png","user":"bobzilla","rank":43}

    private static final String KEY_RANK = "rank";
    private static final String KEY_DISCOVERED = "discovered";
    private static final String KEY_TOTAL = "total";
    private static final String KEY_TOTAL_LOCS = "totallocs";
    private static final String KEY_GEN_DISC = "gendisc";
    private static final String KEY_GEN_TOTAL = "gentotal";
    private static final String KEY_MONTH_COUNT = "monthcount";
    private static final String KEY_PREV_MONTH = "prevmonthcount";
    private static final String KEY_FIRST_TRANS = "firsttransid";
    private static final String KEY_LAST_TRANS = "lasttransid";

    private static final String[] ALL_USER_KEYS = new String[] {
            KEY_RANK, KEY_DISCOVERED, KEY_TOTAL, KEY_TOTAL_LOCS, KEY_GEN_DISC,
            KEY_GEN_TOTAL, KEY_MONTH_COUNT, KEY_PREV_MONTH, KEY_FIRST_TRANS, KEY_LAST_TRANS,
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("USERSTATS: onCreate");
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
        MainActivity.info("USERSTATS: onCreateView. orientation: " + orientation);
        final ScrollView scrollView = (ScrollView) inflater.inflate(R.layout.userstats, container, false);

        // download token if needed
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);

        final boolean beAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
        final String authname = prefs.getString(ListFragment.PREF_AUTHNAME, null);
        MainActivity.info("authname: " + authname);
        if (!beAnonymous) {
            if (authname == null) {
                MainActivity.info("No authname, going to request token");
                final Handler handler = new DownloadHandler(scrollView, numberFormat, getActivity().getPackageName(),
                        getResources());
                final ApiDownloader task = getUserStatsTaks(scrollView, handler);
                downloadToken(task);
            }
            else {
                downloadLatestUserStats(scrollView);
            }
        }

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

            if (msg.what == MSG_USER_DONE) {
                TextView tv;

                for (final String key : ALL_USER_KEYS) {
                    int id = resources.getIdentifier(key, "id", packageName);
                    tv = (TextView) view.findViewById(id);
                    switch (key) {
                        case KEY_FIRST_TRANS:
                        case KEY_LAST_TRANS:
                            tv.setText(bundle.getString(key));
                            break;
                        default:
                            tv.setText(numberFormat.format(bundle.getLong(key)));
                    }
                }
            }
        }
    }

    public void downloadToken(final ApiDownloader pendingTask) {
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                null, MainActivity.TOKEN_URL, true, false,
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

    private ApiDownloader getUserStatsTaks(final View view, final Handler handler) {
        final String userStatsCacheFilename = "user-stats-cache.json";
        return new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                userStatsCacheFilename, MainActivity.USER_STATS_URL, false, true,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json) {
                        handleUserStats(json, handler);
                    }
                });
    }

    private void downloadLatestUserStats(final View view) {
        // what runs on the gui thread
        final Handler handler = new DownloadHandler(view, numberFormat, getActivity().getPackageName(),
                getResources());
        final ApiDownloader task = getUserStatsTaks(view, handler);
        handleUserStats(task.getCached(), handler);
        task.start();
    }

    private void handleUserStats(final JSONObject json, final Handler handler) {
        MainActivity.info("handleUserStats");
        if (json == null) {
            MainActivity.info("handleUserStats null json, returning");
            return;
        }
        MainActivity.info("user stats: " + json);

        final Bundle bundle = new Bundle();
        try {
            final JSONObject stats = json.getJSONObject("statistics");
            for (final String key : ALL_USER_KEYS) {
                final JSONObject lookupJson = (KEY_RANK.equals(key)) ? json : stats;
                switch (key) {
                    case KEY_FIRST_TRANS:
                    case KEY_LAST_TRANS:
                        bundle.putString(key, lookupJson.getString(key));
                        break;
                    default:
                        bundle.putLong(key, lookupJson.getLong(key));
                }
            }
        }
        catch (final JSONException ex) {
            MainActivity.error("json error: " + ex, ex);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_USER_DONE;
        handler.sendMessage(message);
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
        getActivity().setTitle(R.string.user_stats_app_name);
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
        MenuItem item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon( R.drawable.wiglewifi );
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon(R.drawable.wiglewifi);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        switch ( item.getItemId() ) {
            case MENU_SITE_STATS:
                final MainActivity main = MainActivity.getMainActivity();
                main.selectFragment(MainActivity.SITE_STATS_TAB_POS);
                return true;
        }
        return false;
    }

}
