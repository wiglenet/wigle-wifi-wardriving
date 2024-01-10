package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import net.wigle.wigleandroid.listener.GNSSListener;
import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.StatsUtil;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashboardFragment extends Fragment {
  private final Handler timer = new Handler();
  private AtomicBoolean finishing;
  private NumberFormat numberFormat;
  private NumberFormat integerFormat;
  private ScrollView scrollView;
  private View landscape;
  private View portrait;

  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState ) {
    Logging.info("DASH: onCreate");
    super.onCreate( savedInstanceState );
    setHasOptionsMenu(true);
    Activity activity = getActivity();
    if (null != activity) {
        // locale
        MainActivity.setLocale(activity);
        // media volume
        getActivity().setVolumeControlStream( AudioManager.STREAM_MUSIC );
    } else {
        Logging.error("Failed to set language - null activity in dash onCreate.");
    }
    finishing = new AtomicBoolean( false );
    final Configuration conf = getResources().getConfiguration();
    Locale locale = null;
    if (null != conf) {
        locale = conf.getLocales().get(0);
    }
    if (null == locale) {
        locale = Locale.US;
    }
    numberFormat = NumberFormat.getNumberInstance(locale);
    integerFormat = NumberFormat.getInstance();
    if ( numberFormat instanceof DecimalFormat ) {
      numberFormat.setMinimumFractionDigits(2);
      numberFormat.setMaximumFractionDigits(2);
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final int orientation = getResources().getConfiguration().orientation;
    Logging.info("DASH: onCreateView. orientation: " + orientation);
    scrollView = (ScrollView) inflater.inflate(R.layout.dash, container, false);
    landscape = inflater.inflate(R.layout.dashlandscape, container, false);
    portrait = inflater.inflate(R.layout.dashportrait, container, false);

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
    }
  }

  private final Runnable mUpdateTimeTask = new Runnable() {
    @Override
    public void run() {
        // make sure the app isn't trying to finish
        if ( ! finishing.get() ) {
            try {
                final View view = getView();
                if (view != null) {
                    updateUI(view);
                }
            } catch (Exception e) {
                Logging.error("failed to update dash UI: ",e);
            }
            final long period = 1000L;
            // info("wifitimer: " + period );
            timer.postDelayed( this, period );
        } else {
          Logging.info( "finishing mapping timer" );
        }
    }
  };

  private void setupTimer() {
    timer.removeCallbacks( mUpdateTimeTask );
    timer.postDelayed( mUpdateTimeTask, 250 );
  }

  private void updateUI( final View view ) {

        View topBar =  view.findViewById( R.id.dash_status_bar );
        final Activity currentActivity = getActivity();
        final boolean scanning = currentActivity != null && MainActivity.isScanning(currentActivity);
        if (scanning) {
            topBar.setVisibility(View.GONE);
        } else {
            topBar.setVisibility(View.VISIBLE);
            TextView dashScanstatus = view.findViewById(R.id.dash_scanstatus);
            dashScanstatus.setText(getString(R.string.dash_scan_off));
        }

        TextView tv = view.findViewById( R.id.runnets );
        tv.setText( (integerFormat.format(ListFragment.lameStatic.runNets + ListFragment.lameStatic.runBt )));

        tv = view.findViewById( R.id.runcaption );
        tv.setText( (getString(R.string.run)));

        tv = view.findViewById( R.id.newwifi );
        tv.setText( (integerFormat.format(ListFragment.lameStatic.newWifi)));

        tv = view.findViewById( R.id.newbt );
        tv.setText(integerFormat.format(ListFragment.lameStatic.newBt) );

        tv = view.findViewById( R.id.currnets );
        tv.setText( getString(R.string.dash_vis_nets, ListFragment.lameStatic.currNets));

        tv = view.findViewById( R.id.newcells );
        tv.setText( integerFormat.format(ListFragment.lameStatic.newCells) );

        if (null != currentActivity) {
            final SharedPreferences prefs = currentActivity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            tv = view.findViewById( R.id.newNetsSinceUpload );
            tv.setText( getString(R.string.dash_new_upload, StatsUtil.newNetsSinceUpload(prefs)) );

            updateDist(view, prefs, R.id.rundist, PreferenceKeys.PREF_DISTANCE_RUN, getString(R.string.dash_dist_run));
            updateTime(view, prefs, R.id.run_dur, PreferenceKeys.PREF_STARTTIME_RUN);
            updateTimeTare(view, prefs, R.id.scan_dur, MainActivity.isScanning(getActivity()));
            updateDist(view, prefs, R.id.totaldist, PreferenceKeys.PREF_DISTANCE_TOTAL, getString(R.string.dash_dist_total));
            updateDist(view, prefs, R.id.prevrundist, PreferenceKeys.PREF_DISTANCE_PREV_RUN, getString(R.string.dash_dist_prev));
        }
        tv = view.findViewById( R.id.queuesize );
        tv.setText( getString(R.string.dash_db_queue, integerFormat.format(ListFragment.lameStatic.preQueueSize)));

        tv = view.findViewById( R.id.dbNets );
        tv.setText( getString(R.string.dash_db_nets, integerFormat.format(ListFragment.lameStatic.dbNets)));

        tv = view.findViewById( R.id.dbLocs );
        tv.setText( getString(R.string.dash_db_locs, integerFormat.format(ListFragment.lameStatic.dbLocs)));

        tv = view.findViewById( R.id.scanned_in );
      final String status =
              getString(R.string.scanned_in, ListFragment.lameStatic.currNets, ListFragment.lameStatic.currWifiScanDurMs, getString(R.string.ms_short));

      tv.setText(status);

        tv = view.findViewById( R.id.gpsstatus );
        Location location = ListFragment.lameStatic.location;

        tv.setText( getString(R.string.dash_short_loc, ""));

        TextView fixMeta = view.findViewById(R.id.fixmeta);
        TextView conType = view.findViewById(R.id.contype);
        TextView conCount = view.findViewById(R.id.concount);

        ImageView iv = view.findViewById(R.id.fixtype);
        if (location == null) {
            int colorNone = ResourcesCompat.getColor(getResources(), R.color.gps_none, null);
            tv.setTextColor(colorNone);
            iv.setImageResource(R.drawable.ic_gps_nofix);
            iv.setVisibility(View.VISIBLE);
            iv.setColorFilter(colorNone);
            fixMeta.setVisibility(View.INVISIBLE);
            conType.setVisibility(View.INVISIBLE);
            conCount.setVisibility(View.INVISIBLE);
        } else {
            switch (location.getProvider()) {
                case LocationManager.GPS_PROVIDER:
                    String satString = null;
                    String conKeyString = null;
                    String conValString = null;
                    if (MainActivity.getMainActivity() != null && MainActivity.getMainActivity().getGPSListener() != null) {
                        final GNSSListener listener = MainActivity.getMainActivity().getGPSListener();
                        satString = "("+listener.getSatCount()+")";
                        conKeyString = MainActivity.join("\n", listener.getConstellations().keySet());
                        conValString = MainActivity.join("\n", listener.getConstellations().values());
                    }
                    int colorSat = ResourcesCompat.getColor(getResources(), R.color.gps_sat, null);
                    if (satString == null) {
                        fixMeta.setVisibility(View.INVISIBLE);
                    } else {
                        fixMeta.setTextColor(colorSat);
                        fixMeta.setVisibility(View.VISIBLE);
                        fixMeta.setText(satString);
                    }
                    tv.setTextColor(colorSat);
                    iv.setImageResource(R.drawable.ic_gps);
                    iv.setColorFilter(colorSat);
                    iv.setVisibility(View.VISIBLE);

                    if (conKeyString == null) {
                        conType.setVisibility(View.INVISIBLE);
                        conCount.setVisibility(View.INVISIBLE);
                    } else {
                        conType.setVisibility(View.VISIBLE);
                        conType.setText(conKeyString);

                        conCount.setVisibility(View.VISIBLE);
                        conCount.setText(conValString);
                    }
                    break;
                case LocationManager.NETWORK_PROVIDER:
                    fixMeta.setVisibility(View.INVISIBLE);
                    int colorNet = ResourcesCompat.getColor(getResources(), R.color.gps_network, null);
                    tv.setTextColor(colorNet);
                    iv.setImageResource(R.drawable.ic_wifi);
                    iv.setVisibility(View.VISIBLE);
                    iv.setColorFilter(colorNet);
                    break;
                case LocationManager.PASSIVE_PROVIDER:
                    fixMeta.setVisibility(View.INVISIBLE);
                    int colorPassive = ResourcesCompat.getColor(getResources(), R.color.gps_passive, null);
                    tv.setTextColor(colorPassive);
                    iv.setImageResource(R.drawable.ic_cell);
                    iv.setVisibility(View.VISIBLE);
                    iv.setColorFilter(colorPassive);
                    break;
                default:
                    //ALIBI: fall back on string version
                    fixMeta.setVisibility(View.INVISIBLE);
                    int colorUnknown = ResourcesCompat.getColor(getResources(), R.color.colorNavigationItem, null);
                    tv.setTextColor(colorUnknown);
                    iv.setVisibility(View.GONE);
                    tv.setText( getString(R.string.dash_short_loc, location.getProvider()));
                    iv.setColorFilter(colorUnknown);
            }
        }
  }

  private void updateDist(final View view, final SharedPreferences prefs, final int id, final String pref, final String title ) {
      float dist = prefs.getFloat(pref, 0f);
      final String distString = UINumberFormat.metersToString(prefs, numberFormat, getActivity(), dist, false);
      final TextView tv = view.findViewById(id);
      tv.setText( title + " " + distString );
  }

  private void updateTime( final View view, final SharedPreferences prefs, final int id, final String pref) {
      long millis = System.currentTimeMillis();
      long duration = millis - prefs.getLong(pref, millis);
      final String durString = timeString(duration);
      final TextView tv = view.findViewById(id);
      tv.setText(durString);
  }

  private void updateTimeTare(final View view, final SharedPreferences prefs, final int id,
                              final boolean isScanning) {
      long cumulative = prefs.getLong(PreferenceKeys.PREF_CUMULATIVE_SCANTIME_RUN, 0L);
      if (isScanning) {
        cumulative += System.currentTimeMillis() - prefs.getLong(PreferenceKeys.PREF_STARTTIME_CURRENT_SCAN, System.currentTimeMillis());
      }
      final String durString = timeString(cumulative);
      final TextView tv = view.findViewById( id );
      tv.setText(durString );
  }

  @Override
  public void onDestroy() {
    Logging.info( "DASH: onDestroy" );
    finishing.set( true );

    super.onDestroy();
  }

  @Override
  public void onResume() {
    Logging.info( "DASH: onResume" );
    super.onResume();
    setupTimer();
    final Activity a = getActivity();
    if (null != a) {
        a.setTitle(R.string.dashboard_app_name);
    }
  }

  @Override
  public void onStart() {
    Logging.info( "DASH: onStart" );
    super.onStart();
  }

  @Override
  public void onPause() {
    Logging.info( "DASH: onPause" );
    super.onPause();
  }

  @Override
  public void onStop() {
    Logging.info( "DASH: onStop" );
    super.onStop();
  }

  @Override
  public void onConfigurationChanged(@NonNull final Configuration newConfig ) {
    Logging.info( "DASH: config changed" );
    switchView();
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

  private String timeString(final long duration) {
    final long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
    final long minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60;
    final long hours = TimeUnit.MILLISECONDS.toHours(duration);
    
    Locale defaultLocale = Locale.getDefault();
    
    return hours > 0
      ? String.format(defaultLocale," %d:%02d:%02d", hours, minutes, seconds)
      : String.format(defaultLocale, " %02d:%02d", minutes, seconds);
  }

}
