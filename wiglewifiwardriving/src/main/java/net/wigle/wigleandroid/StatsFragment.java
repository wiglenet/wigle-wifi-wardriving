package net.wigle.wigleandroid;

import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class StatsFragment extends Fragment {
    private AtomicBoolean finishing;
    private ScrollView scrollView;
    private NumberFormat numberFormat;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("STATS: onCreate");
        super.onCreate( savedInstanceState );
        setHasOptionsMenu(true);
        // set language
        MainActivity.setLocale( getActivity() );

        // media volume
        getActivity().setVolumeControlStream( AudioManager.STREAM_MUSIC );

        finishing = new AtomicBoolean( false );
        numberFormat = NumberFormat.getNumberInstance( Locale.US );
        if ( numberFormat instanceof DecimalFormat ) {
          numberFormat.setMinimumFractionDigits(2);
          numberFormat.setMaximumFractionDigits(2);
        }
    }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final int orientation = getResources().getConfiguration().orientation;
    MainActivity.info("STATS: onCreateView. orientation: " + orientation);
    scrollView = (ScrollView) inflater.inflate(R.layout.stats, container, false);
    return scrollView;
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
