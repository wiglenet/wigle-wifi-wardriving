package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.util.UrlConfig.WIGLE_BASE_URL;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.AsyncTask;
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
import android.widget.ImageView;
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

import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserStatsFragment extends Fragment {
    public static final int MSG_USER_DONE = 101;
    private static final int MENU_SITE_STATS = 201;
    private static final int MENU_RANK_STATS = 202;

    /*
    {
      imageBadgeUrl: "/bi/64f3oBpg41EH9bOo2TXr0w.png",
      monthRank: 128,
      rank: 44,
      statistics : {
        discoveredCell: 6766,
        discoveredCellGPS: 3702,
        discoveredWiFi: 1305884,
        discoveredWiFiGPS: 668555,
        discoveredWiFiGPSPercent: 0,
        eventMonthCount: 743,
        eventPrevMonthCount: 10908,
        first: "20011125-00000",
        last: "20170101-00928",
        monthRank: 128,
        prevMonthRank: 128,
        prevRank: 44,
        rank: 44,
        self : true,
        totalWiFiLocations: 12255273,
        userName: "arkasha"
      }
      success: true,
      user: "arkasha"
     }
     */
    public static final String KEY_BADGE_IMAGE_URL = "imageBadgeUrl";
    public static final String KEY_STATISTICS = "statistics";
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

    public static final String KEY_IS_CACHE = "iscache";

    private static final int COLOR_UP = Color.rgb(30, 200, 30);
    private static final int COLOR_DOWN = Color.rgb(200, 30, 30);
    private static final int COLOR_BLANK = Color.rgb(80, 80, 80);

    private static final String[] ALL_USER_KEYS = new String[] {
            KEY_RANK, KEY_PREV_RANK, KEY_MONTH_RANK, KEY_PREV_MONTH_RANK, KEY_DISCOVERED, KEY_TOTAL, KEY_TOTAL_LOCS,
            KEY_BT_DISC, KEY_BT_TOTAL, KEY_GEN_DISC, KEY_GEN_TOTAL, KEY_MONTH_COUNT, KEY_PREV_MONTH, KEY_FIRST_TRANS,
            KEY_LAST_TRANS,
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("USERSTATS: onCreate");
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
        Logging.info("USERSTATS: onCreateView. orientation: " + orientation);
        final ScrollView scrollView = (ScrollView) inflater.inflate(R.layout.userstats, container, false);

        final Handler handler = new UserDownloadHandler(scrollView, numberFormat, getActivity().getPackageName(),
                getResources());
        executeUserDownload(this, new UserDownloadApiListener(handler));

        return scrollView;
    }

    public static void executeUserDownload(final Fragment fragment, final ApiListener apiListener) {
        final ApiDownloader task = new ApiDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper,
                "user-stats-cache.json", UrlConfig.USER_STATS_URL, false, true, true,
                ApiDownloader.REQUEST_GET,
                apiListener);
        try {
            task.startDownload(fragment);
        } catch (WiGLEAuthException waex) {
            Logging.info("User Stats Download Failed due to failed auth");
        }
    }

    public static class UserDownloadApiListener implements ApiListener {
        final Handler handler;
        public UserDownloadApiListener(final Handler handler) {
            this.handler = handler;
        }

        @Override
        public void requestComplete(final JSONObject json, final boolean isCache) {
            Logging.info("handleUserStats");
            if (json == null) {
                Logging.info("handleUserStats null json, returning");
                return;
            }
            // MainActivity.info("user stats: " + json);

            final Bundle bundle = new Bundle();
            bundle.putBoolean(KEY_IS_CACHE, isCache);
            try {
                if (!json.getBoolean("success")) {
                    Logging.info("handleUserStats json success is false");
                    bundle.putString("error", "Unable to load user statistics.");
                } else {
                    if (json.isNull(KEY_STATISTICS)) {
                        Logging.info("handleUserStats json stats null, returning");
                        bundle.putString("error", "Unable to load user statistics.");
                    } else {
                        final JSONObject stats = json.getJSONObject(KEY_STATISTICS);
                        for (final String key : ALL_USER_KEYS) {
                            final JSONObject lookupJson = (KEY_RANK.equals(key) || KEY_MONTH_RANK.equals(key)) ? json : stats;
                            if (!lookupJson.has(key)) continue;
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
                    if (json.isNull(KEY_BADGE_IMAGE_URL)) {
                        Logging.info("handleUserStats json badge url null;");
                    } else {
                        final String imageBadgeUrl = json.getString(KEY_BADGE_IMAGE_URL);
                        bundle.putString(KEY_BADGE_IMAGE_URL, WIGLE_BASE_URL+imageBadgeUrl);
                    }
                }
            } catch (final JSONException ex) {
                Logging.error("json error: " + ex, ex);
            } catch (final Exception e) {
                Logging.error("user stats error: " + e, e);
            }

            final Message message = new Message();
            message.setData(bundle);
            message.what = MSG_USER_DONE;
            handler.sendMessage(message);
        }
    }

    private final static class UserDownloadHandler extends DownloadHandler {
        private UserDownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                                final Resources resources) {
            super(view, numberFormat, packageName, resources);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();
            if (msg.what == MSG_USER_DONE) {
                if (bundle.containsKey("error")) {
                    Logging.info("handleMessage error loading user stats. TODO: show warning");
                } else {
                    TextView tv;

                    for (final String key : ALL_USER_KEYS) {
                        int id = resources.getIdentifier(key, "id", packageName);
                        tv = (TextView) view.findViewById(id);
                        switch (key) {
                            case KEY_FIRST_TRANS:
                            case KEY_LAST_TRANS:
                                tv.setText(bundle.getString(key));
                                break;
                            case KEY_PREV_RANK: {
                                final long diff = bundle.getLong(key) - bundle.getLong(KEY_RANK);
                                diffToString(diff, tv);

                                tv = (TextView) view.findViewById(R.id.actual_prevrank);
                                tv.setText(numberFormat.format(bundle.getLong(key)));
                                break;
                            }
                            case KEY_PREV_MONTH_RANK: {
                                final long diff = bundle.getLong(key) - bundle.getLong(KEY_MONTH_RANK);
                                diffToString(diff, tv);

                                tv = (TextView) view.findViewById(R.id.actual_prevmonthrank);
                                tv.setText(numberFormat.format(bundle.getLong(key)));
                                break;
                            }
                            default:
                                tv.setText(numberFormat.format(bundle.getLong(key)));
                        }
                    }
                    final String badgeImageURL = bundle.getString(KEY_BADGE_IMAGE_URL);
                    if (null != badgeImageURL && !badgeImageURL.isEmpty()) {
                        new DownloadBadgeImageTask((ImageView) view.findViewById(R.id.badgeImage)).execute(badgeImageURL);
                    }
                }
            }
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
        else if (diff < 0) {
            plus = "  ↓";
            tv.setTextColor(COLOR_DOWN);
        }
        tv.setText(plus + Long.toString(diff));
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
            getActivity().setTitle(R.string.user_stats_app_name);
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
    public void onConfigurationChanged( final Configuration newConfig ) {
        Logging.info("STATS: config changed");
        super.onConfigurationChanged( newConfig );
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
        MenuItem item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon( R.drawable.wiglewifi_small_black_white );
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_SITE_STATS, 0, getString(R.string.site_stats_app_name));
        item.setIcon(R.drawable.wiglewifi_small_black_white);

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
            case MENU_SITE_STATS:
                MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_site_stats);
                return true;
            case MENU_RANK_STATS:
                MenuUtil.selectStatsSubmenuItem(navigationView, main, R.id.nav_rank);
                return true;
        }
        return false;
    }

    private static class DownloadBadgeImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView badgeImage;

        public DownloadBadgeImageTask(ImageView image) {
            this.badgeImage = image;
        }
        protected Bitmap doInBackground(String... urls) {
            String badgeUrl = urls[0];
            Bitmap badge = null;
            try {
                InputStream in = new java.net.URL(badgeUrl).openStream();
                badge = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Logging.error("Failed to download bage image ", e);
            }
            return badge;
        }

        protected void onPostExecute(Bitmap result) {
            badgeImage.setImageBitmap(result);
        }
    }
}
