package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RelativeLayout;

import net.wigle.wigleandroid.model.NewsItem;
import net.wigle.wigleandroid.model.api.WiGLENews;
import net.wigle.wigleandroid.net.RequestCompletedListener;
import net.wigle.wigleandroid.util.Logging;

import org.json.JSONObject;

import java.util.List;

/**
 * Show the latest WiGLE news
 */
public class NewsFragment extends Fragment {
    private NewsListAdapter listAdapter;
    private List<NewsItem> currentNews;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("NEWS: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language, media volume
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        Logging.info("NEWS: onCreateView. orientation: " + orientation);
        final RelativeLayout rootView = (RelativeLayout) inflater.inflate(R.layout.news, container, false);
        setupListView(rootView);

        MainActivity.State s = MainActivity.getStaticState();
        if (s != null) {
            s.apiManager.getNews(new RequestCompletedListener<WiGLENews, JSONObject>() {
                @Override
                public void onTaskCompleted() {
                    if (listAdapter == null) {
                        listAdapter = new NewsListAdapter(getActivity(), R.layout.uploadrow);
                    }
                    if (currentNews != null) {
                        listAdapter.clear();
                        for (final NewsItem item : currentNews) {
                            listAdapter.add(item);
                        }
                    }
                    Logging.info("NEWS: load completed.");
                    //no-op
                }

                @Override
                public void onTaskSucceeded(WiGLENews response) {
                    handleNews(response);
                }

                @Override
                public void onTaskFailed(int status, JSONObject error) {
                    Logging.error("NEWS: failed: " + status);
                    //no-op for now. maybe show an error toast?
                }
            });
        }
        return rootView;
    }

    private void setupListView(final View view) {
        if (listAdapter == null) {
            listAdapter = new NewsListAdapter(getActivity(), R.layout.uploadrow);
        }
        // always set our current list adapter
        final ListView listView = view.findViewById(R.id.news_list_view);
        listView.setAdapter(listAdapter);
    }

    private void handleNews(final WiGLENews news) {
        Logging.info("handleNews");

        if (news == null) {
            Logging.info("handleNews null result, returning");
            return;
        }

        try {
            this.currentNews = news.getResults();
        } catch (final Exception e) {
            Logging.error("news error: " + e, e);
        }
    }

    @Override
    public void onDestroy() {
        Logging.info( "NEWS: onDestroy" );
        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("NEWS: onResume");
        super.onResume();
        Activity a = getActivity();
        if (null != a) {
            a.setTitle(R.string.news_app_name);
        }
    }

    @Override
    public void onStart() {
        Logging.info( "NEWS: onStart" );
        super.onStart();
    }

    @Override
    public void onPause() {
        Logging.info( "NEWS: onPause" );
        super.onPause();
    }

    @Override
    public void onStop() {
        Logging.info( "NEWS: onStop" );
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig ) {
        Logging.info("NEWS: config changed");
        super.onConfigurationChanged( newConfig );
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item ) {
        return false;
    }

}
