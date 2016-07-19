package net.wigle.wigleandroid;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
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

    private static final String RESULT_LIST_KEY = "results";

    // {"discoveredCellGPS":"1773",
    // "first":"27-Jan-2003",
    // "last":"13-Jul-2016",
    // "self":false,
    // "eventPrevMonthCount":"284489",
    // "username":"ccie4526",
    // "discoveredWiFiGPSPercent":"3.751",
    // "eventMonthCount":"94942",
    // "discoveredWiFi":"11143854",
    // "discoveredCell":"2799",
    // "rank":2,
    // "discoveredWiFiGPS":"10013307"},
    private static final String KEY_MONTH_WIFI_GPS = "eventMonthCount";
    private static final String KEY_TOTAL_WIFI_GPS = "discoveredWiFiGPS";
    private static final String KEY_TOTAL_CELL_GPS = "discoveredCellGPS";
    private static final String KEY_RANK = "rank";
    private static final String KEY_USERNAME = "username";

    private static final int ROW_COUNT = 100;

    private static final String[] ALL_ROW_KEYS = new String[] {
            KEY_MONTH_WIFI_GPS, KEY_TOTAL_WIFI_GPS, KEY_TOTAL_CELL_GPS, KEY_RANK
        };

    private AtomicBoolean finishing;
    private NumberFormat numberFormat;
    private RankListAdapter listAdapter;

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
        numberFormat = NumberFormat.getNumberInstance(Locale.US);
        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        MainActivity.info("RANKSTATS: onCreateView. orientation: " + orientation);
        final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.rankstats, container, false);
        setupListView(rootView);

        final RankDownloadHandler handler = new RankDownloadHandler(rootView, numberFormat,
                getActivity().getPackageName(), getResources());
        handler.setRankListAdapter(listAdapter);
        final String monthUrl = MainActivity.RANK_STATS_URL + "?pageend=" + ROW_COUNT + "&sort=monthcount";
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                "rank-stats-month-cache.json", monthUrl, false, false, false,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json) {
                        handleRankStats(json, handler);
                    }
                });
        task.startDownload(this);

        return rootView;
    }

    private void setupListView(final View view) {
        if (listAdapter == null) {
            listAdapter = new RankListAdapter(getActivity().getApplicationContext(), R.layout.rankrow);
        }
        // always set our current list adapter
        final ListView listView = (ListView) view.findViewById(R.id.rank_list_view);
        listView.setAdapter(listAdapter);

    }

    private final static class RankDownloadHandler extends DownloadHandler {
        private RankListAdapter rankListAdapter;

        private RankDownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                                final Resources resources) {
            super(view, numberFormat, packageName, resources);
        }

        public void setRankListAdapter(final RankListAdapter rankListAdapter) {
            this.rankListAdapter = rankListAdapter;
        }

        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            final ArrayList<Parcelable> results = bundle.getParcelableArrayList(RESULT_LIST_KEY);
            MainActivity.info("handleMessage. results: " + results);
            if (msg.what == MSG_RANKING_DONE && results != null && rankListAdapter != null) {
                TextView tv = (TextView) view.findViewById(R.id.rankstats_type);
                tv.setText(R.string.monthcount_title);

                rankListAdapter.clear();
                for (final Parcelable result : results) {
                    if (result instanceof Bundle) {
                        final Bundle row = (Bundle) result;
                        final RankUser rankUser = new RankUser(row.getLong(KEY_RANK), row.getString(KEY_USERNAME),
                                row.getLong(KEY_MONTH_WIFI_GPS), row.getLong(KEY_TOTAL_WIFI_GPS),
                                row.getLong(KEY_TOTAL_CELL_GPS));
                        rankListAdapter.add(rankUser);
                    }
                }
            }
        }
    }

    private void handleRankStats(final JSONObject json, final Handler handler) {
        MainActivity.info("handleRankStats");
        if (json == null) {
            MainActivity.info("handleRankStats null json, returning");
            return;
        }

        final Bundle bundle = new Bundle();
        try {
            final JSONArray list = json.getJSONArray(RESULT_LIST_KEY);
            final ArrayList<Parcelable> resultList = new ArrayList<>(list.length());
            for (int i = 0; i < list.length(); i++) {
                final JSONObject row = list.getJSONObject(i);
                final Bundle rowBundle = new Bundle();
                for (final String key : ALL_ROW_KEYS) {
                    rowBundle.putLong(key, row.getLong(key));
                }
                rowBundle.putString(KEY_USERNAME, row.getString(KEY_USERNAME));
                resultList.add(rowBundle);
            }
            bundle.putParcelableArrayList(RESULT_LIST_KEY, resultList);
        }
        catch (final JSONException ex) {
            MainActivity.error("json error: " + ex, ex);
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
            case MENU_SITE_STATS:
                main.selectFragment(MainActivity.SITE_STATS_TAB_POS);
                return true;
        }
        return false;
    }

}
