package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;

import net.wigle.wigleandroid.background.ApiDownloader;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.DownloadHandler;
import net.wigle.wigleandroid.model.NewsItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class NewsFragment extends Fragment {
    private static final int MSG_NEWS_DONE = 100;

    // {"success": true,"results": [
    // {"link":"http://wigle.net/phpbb/viewtopic.php?p=8783",
    // "subject":"250 Million Wifi Networks",
    // "postDate":"Sat Apr 30 15:31:41 2016",
    // "story":"Major congrats to user 'redlukas' can say &quot;quarter billion&quot;",
    // "storyId":"8783",
    // "more":false,
    // "userName":"bobzilla"}
    private static final String RESULT_LIST_KEY = "results";

    private static final String KEY_SUBJECT = "subject";
    private static final String KEY_POST = "story";
    private static final String KEY_DATE_TIME = "postDate";
    private static final String KEY_POSTER = "userName";
    private static final String KEY_LINK = "link";

    private static final String[] ALL_ROW_KEYS = new String[] {
            KEY_SUBJECT, KEY_POST, KEY_DATE_TIME, KEY_POSTER, KEY_LINK,
        };

    private NumberFormat numberFormat;
    private NewsListAdapter listAdapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("NEWS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        MainActivity.setLocale(getActivity());

        // media volume
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        numberFormat = NumberFormat.getNumberInstance(Locale.US);
        if (numberFormat instanceof DecimalFormat) {
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        MainActivity.info("NEWS: onCreateView. orientation: " + orientation);
        final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.news, container, false);
        setupListView(rootView);

        final NewsDownloadHandler handler = new NewsDownloadHandler(rootView, numberFormat,
                getActivity().getPackageName(), getResources());
        handler.setNewsListAdapter(listAdapter);
        final ApiDownloader task = new ApiDownloader(getActivity(), ListFragment.lameStatic.dbHelper,
                "news-cache.json", MainActivity.NEWS_URL, false, false, false,
                ApiDownloader.REQUEST_GET,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json, final boolean isCache) {
                        handleNews(json, handler);
                    }
                });
        try {
            task.startDownload(this);
        } catch (WiGLEAuthException waex) {
            //unauthenticated call - should never trip
            MainActivity.warn("Authentication error on news load (should not happen)", waex);
        }

        return rootView;
    }

    private void setupListView(final View view) {
        if (listAdapter == null) {
            listAdapter = new NewsListAdapter(getActivity(), R.layout.uploadrow);
        }
        // always set our current list adapter
        final ListView listView = (ListView) view.findViewById(R.id.news_list_view);
        listView.setAdapter(listAdapter);

    }

    private final static class NewsDownloadHandler extends DownloadHandler {
        private NewsListAdapter newsListAdapter;

        private NewsDownloadHandler(final View view, final NumberFormat numberFormat, final String packageName,
                                    final Resources resources) {
            super(view, numberFormat, packageName, resources);
        }

        public void setNewsListAdapter(final NewsListAdapter newsListAdapter) {
            this.newsListAdapter = newsListAdapter;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(final Message msg) {
            final Bundle bundle = msg.getData();

            final ArrayList<Parcelable> results = bundle.getParcelableArrayList(RESULT_LIST_KEY);
            // MainActivity.info("handleMessage. results: " + results);
            if (msg.what == MSG_NEWS_DONE && results != null && newsListAdapter != null) {
                newsListAdapter.clear();
                for (final Parcelable result : results) {
                    if (result instanceof Bundle) {
                        final Bundle row = (Bundle) result;
                        final NewsItem upload = new NewsItem(row.getString(KEY_SUBJECT), row.getString(KEY_POST),
                                row.getString(KEY_POSTER), row.getString(KEY_DATE_TIME), row.getString(KEY_LINK));
                        newsListAdapter.add(upload);
                    }
                }
            }
        }
    }

    private void handleNews(final JSONObject json, final Handler handler) {
        MainActivity.info("handleNews");

        if (json == null) {
            MainActivity.info("handleNews null json, returning");
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
                    String value = row.getString(key);
                    if (KEY_POST.equals(key)) {
                        value = value.replace("\\n", "\n");
                        value = value.replace("&quot;", "\"");
                        value = value.replace("&amp;", "&");
                        value = value.replaceAll("<.*?>", "");
                    }
                    rowBundle.putString(key, value);
                }
                resultList.add(rowBundle);
            }
            bundle.putParcelableArrayList(RESULT_LIST_KEY, resultList);
        }
        catch (final JSONException ex) {
            MainActivity.error("json error: " + ex, ex);
        }

        final Message message = new Message();
        message.setData(bundle);
        message.what = MSG_NEWS_DONE;
        handler.sendMessage(message);
    }

    @Override
    public void onDestroy() {
        MainActivity.info( "NEWS: onDestroy" );
        super.onDestroy();
    }

    @Override
    public void onResume() {
        MainActivity.info("NEWS: onResume");
        super.onResume();
        getActivity().setTitle(R.string.news_app_name);
    }

    @Override
    public void onStart() {
        MainActivity.info( "NEWS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        MainActivity.info( "NEWS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        MainActivity.info( "NEWS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
        MainActivity.info("NEWS: config changed");
        super.onConfigurationChanged( newConfig );
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        return false;
    }

}
