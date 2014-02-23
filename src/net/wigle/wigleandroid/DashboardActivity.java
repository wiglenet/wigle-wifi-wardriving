package net.wigle.wigleandroid;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class DashboardActivity extends ActionBarActivity {
  private Handler timer;
  private AtomicBoolean finishing;
  private NumberFormat numberFormat;
  
  private static final int MENU_EXIT = 11;
  private static final int MENU_SETTINGS = 12;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    // set language
    MainActivity.setLocale( this );
    setContentView( R.layout.dash );
    
    // media volume
    this.setVolumeControlStream( AudioManager.STREAM_MUSIC );  
    
    finishing = new AtomicBoolean( false );
    numberFormat = NumberFormat.getNumberInstance( Locale.US );
    if ( numberFormat instanceof DecimalFormat ) {
      ((DecimalFormat) numberFormat).setMaximumFractionDigits( 2 );
    }
    
    setupTimer();
  }
  
  private void setupTimer() {
    if ( timer == null ) {
      timer = new Handler();
      final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {              
            // make sure the app isn't trying to finish
            if ( ! finishing.get() ) {
              updateUI();
              
              final long period = 1000L;
              // info("wifitimer: " + period );
              timer.postDelayed( this, period );
            }
            else {
              ListActivity.info( "finishing mapping timer" );
            }
        }
      };
      timer.removeCallbacks( mUpdateTimeTask );
      timer.postDelayed( mUpdateTimeTask, 100 );
    }
  }
  
  private void updateUI() {
    TextView tv = (TextView) findViewById( R.id.runnets );
    tv.setText( ListActivity.lameStatic.runNets + " " + getString(R.string.run));
    
    tv = (TextView) findViewById( R.id.newwifi );
    final String scanning = ListActivity.isScanning(this) ? "" : getString(R.string.dash_scan_off) + "\n"; 
    tv.setText( scanning + ListActivity.lameStatic.newWifi + " " + getString(R.string.dash_new_wifi) );
    
    tv = (TextView) findViewById( R.id.currnets );
    tv.setText( getString(R.string.dash_vis_nets) + " " + ListActivity.lameStatic.currNets );
    
    tv = (TextView) findViewById( R.id.newNetsSinceUpload );
    tv.setText( getString(R.string.dash_new_upload) + " " + newNetsSinceUpload() );  
    
    tv = (TextView) findViewById( R.id.newcells );
    tv.setText( getString(R.string.dash_new_cells) + " " + ListActivity.lameStatic.newCells );    
    
    updateDist( R.id.rundist, ListActivity.PREF_DISTANCE_RUN, getString(R.string.dash_dist_run) );
    updateDist( R.id.totaldist, ListActivity.PREF_DISTANCE_TOTAL, getString(R.string.dash_dist_total) );
    updateDist( R.id.prevrundist, ListActivity.PREF_DISTANCE_PREV_RUN, getString(R.string.dash_dist_prev) );
    
    tv = (TextView) findViewById( R.id.queuesize );
    tv.setText( getString(R.string.dash_db_queue) + " " + ListActivity.lameStatic.preQueueSize );
    
    tv = (TextView) findViewById( R.id.dbNets );
    tv.setText( getString(R.string.dash_db_nets) + " " + ListActivity.lameStatic.dbNets );
    
    tv = (TextView) findViewById( R.id.dbLocs );
    tv.setText( getString(R.string.dash_db_locs) + " " + ListActivity.lameStatic.dbLocs );
        
    tv = (TextView) findViewById( R.id.gpsstatus );
    Location location = ListActivity.lameStatic.location;
    String gpsStatus = getString(R.string.dash_no_loc);
    if ( location != null ) {
      gpsStatus = location.getProvider();
    }
    tv.setText( getString(R.string.dash_short_loc) + " " + gpsStatus );
  }
  
  private long newNetsSinceUpload() {
    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final long marker = prefs.getLong( ListActivity.PREF_DB_MARKER, 0L );
    final long uploaded = prefs.getLong( ListActivity.PREF_NETS_UPLOADED, 0L );
    long newSinceUpload = 0;
    if ( marker != 0 && uploaded == 0 ) {
      // marker is set but no uploaded, a migration situation, so return zero      
    }
    else {
      newSinceUpload = ListActivity.lameStatic.dbNets - uploaded;
      if ( newSinceUpload < 0 ) {
        newSinceUpload = 0;
      }
    }
    return newSinceUpload;
  }
  
  private void updateDist( final int id, final String pref, final String title ) {
    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    
    float dist = prefs.getFloat( pref, 0f );
    final String distString = metersToString( numberFormat, this, dist, false );
    final TextView tv = (TextView) findViewById( id );
    tv.setText( title + " " + distString );    
  }
  
  public static String metersToString(final NumberFormat numberFormat, final Context context, final float meters,
      final boolean useShort ) {
    final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final boolean metric = prefs.getBoolean( ListActivity.PREF_METRIC, false );
    
    String retval = null;
    if ( meters > 1000f ) {
      if ( metric ) {
        retval = numberFormat.format( meters / 1000f ) + " " + context.getString(R.string.km_short);
      }
      else {
        retval = numberFormat.format( meters / 1609.344f ) + " " + 
            (useShort ? context.getString(R.string.mi_short) : context.getString(R.string.miles));
      }
    }
    else if ( metric ){
      retval = numberFormat.format( meters ) + " " + 
          (useShort ? context.getString(R.string.m_short) : context.getString(R.string.meters));
    }
    else {
      retval = numberFormat.format( meters * 3.2808399f  ) + " " + 
          (useShort ? context.getString(R.string.ft_short) : context.getString(R.string.feet));
    }
    return retval;
  }
  
  @Override
  public void finish() {
    ListActivity.info( "finish dash." );
    finishing.set( true );
    
    super.finish();
  }
  
  @Override
  public void onDestroy() {
    ListActivity.info( "destroy dash." );
    finishing.set( true );
    
    super.onDestroy();
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
    MenuItem item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_exit));
    item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
        
    item = menu.add(0, MENU_SETTINGS, 0, getString(R.string.menu_settings));
    item.setIcon( android.R.drawable.ic_menu_preferences );
    
    return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT:
          MainActivity.finishListActivity( this );
          finish();
          return true;
        case MENU_SETTINGS:
          final Intent settingsIntent = new Intent( this, SettingsActivity.class );
          startActivity( settingsIntent );
          break;
      }
      return false;
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      ListActivity.info( "onKeyDown: not quitting app on back" );
      MainActivity.switchTab( this, MainActivity.TAB_LIST );
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }
}
