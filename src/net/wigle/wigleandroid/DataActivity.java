package net.wigle.wigleandroid;

import net.wigle.wigleandroid.MainActivity.Doer;
import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * configure settings
 */
public final class DataActivity extends Activity {
  
  private static final int MENU_EXIT = 11;
  private static final int MENU_SETTINGS = 12;
  private static final int MENU_ERROR_REPORT = 13;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState) {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.data );
      
      // force media volume controls
      this.setVolumeControlStream( AudioManager.STREAM_MUSIC );

      // get prefs
//      final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
//      final Editor editor = prefs.edit();      
      
      final Button kmlRunExportButton = (Button) findViewById( R.id.kml_run_export_button );
      kmlRunExportButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {  
          MainActivity.createConfirmation( DataActivity.this, "Export run to KML file?", new Doer() {
            @Override
            public void execute() {
              // actually need this Activity context, for dialogs
              KmlWriter kmlWriter = new KmlWriter( DataActivity.this, ListActivity.lameStatic.dbHelper, 
                  ListActivity.lameStatic.runNetworks );
              kmlWriter.start();
            }
          } );
        }
      });
      
      final Button kmlExportButton = (Button) findViewById( R.id.kml_export_button );
      kmlExportButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {  
          MainActivity.createConfirmation( DataActivity.this, "Export DB to KML file?", new Doer() {
            @Override
            public void execute() {
              // actually need this Activity context, for dialogs
              KmlWriter kmlWriter = new KmlWriter( DataActivity.this, ListActivity.lameStatic.dbHelper );
              kmlWriter.start();
            }
          } );
        }
      });
      
  }  
  
  @Override
  public void onResume() {
    ListActivity.info( "resume data." );    
    super.onResume();
  }
   
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add( 0, MENU_EXIT, 0, "Exit" );
      item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
              
      item = menu.add( 0, MENU_ERROR_REPORT, 0, "Error Report" );
      item.setIcon( android.R.drawable.ic_menu_report_image );
      
      item = menu.add( 0, MENU_SETTINGS, 0, "Settings" );
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
        case MENU_ERROR_REPORT:
          final Intent errorReportIntent = new Intent( this, ErrorReportActivity.class );
          startActivity( errorReportIntent );
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
