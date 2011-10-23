package net.wigle.wigleandroid;

import java.util.List;

import net.wigle.wigleandroid.MainActivity.Doer;
import net.wigle.wigleandroid.background.FileUploaderListener;
import net.wigle.wigleandroid.background.FileUploaderTask;
import net.wigle.wigleandroid.background.KmlWriter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * configure settings
 */
public final class DataActivity extends Activity implements FileUploaderListener {
  
  private static final int MENU_EXIT = 11;
  private static final int MENU_SETTINGS = 12;
  private static final int MENU_ERROR_REPORT = 13;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState) {
      super.onCreate( savedInstanceState );
      // set language
      MainActivity.setLocale( this );
      setContentView( R.layout.data );
      
      // force media volume controls
      this.setVolumeControlStream( AudioManager.STREAM_MUSIC );
      
      setupQueryButtons();
      setupCsvButtons();
      setupKmlButtons();
      setupBackupDbButton();
  }  
  
  private void setupQueryButtons() {
    Button button = (Button) findViewById( R.id.search_button );
    button.setOnClickListener(new OnClickListener() {
      public void onClick( final View view ) {
        final QueryArgs queryArgs = new QueryArgs();
        String fail = null;
        String field = null;
        boolean okValue = false;
        
        for ( final int id : new int[]{ R.id.query_address, R.id.query_ssid, R.id.query_bssid } ) {
          if ( fail != null ) {
            break;
          }
          
          final EditText editText = (EditText) findViewById( id );
          final String text = editText.getText().toString().trim();
          if ( "".equals(text) ) {
            continue;
          }
          
          try {
            switch( id ) {
              case R.id.query_address:
                field = getString(R.string.address);
                Geocoder gc = new Geocoder(DataActivity.this);
                List<Address> addresses = gc.getFromLocationName(text, 1);
                if ( addresses.size() < 1 ) {
                  fail = getString(R.string.no_address_found);
                  break;
                }
                queryArgs.setAddress(addresses.get(0));
                okValue = true;
                break;
              case R.id.query_ssid:
                field = getString(R.string.ssid);
                queryArgs.setSSID(text);
                okValue = true;
                break;
              case R.id.query_bssid:
                field = getString(R.string.bssid);
                queryArgs.setBSSID(text);
                okValue = true;
                break;
              default:
                ListActivity.error("setupButtons: bad id: " + id);
            }
          }
          catch( Exception ex ) {
            fail = getString(R.string.problem_with_field) + " '" + field + "': " + ex.getMessage();
            break;
          }          
        }
        
        if ( fail == null && ! okValue ) {
          fail = "No query fields specified";
        }
        
        if ( fail != null ) {
          // toast!
          Toast.makeText( DataActivity.this, fail, Toast.LENGTH_SHORT ).show();
        }
        else {
          ListActivity.lameStatic.queryArgs = queryArgs;
          // start db result activity
          final Intent settingsIntent = new Intent( DataActivity.this, DBResultActivity.class );
          startActivity( settingsIntent );
        }
      }
    });
    
    button = (Button) findViewById( R.id.reset_button );
    button.setOnClickListener(new OnClickListener() {
      public void onClick( final View view ) {
        for ( final int id : new int[]{ R.id.query_address, R.id.query_ssid } ) {        
          final EditText editText = (EditText) findViewById( id );
          editText.setText("");
        }
      }
    });

  }
  
  /**
   * FileUploaderListener interface
   */
  public void uploadComplete() {
    // nothing
  }
  
  private void setupCsvButtons() {
    // actually need this Activity context, for dialogs
    
    final Button csvRunExportButton = (Button) findViewById( R.id.csv_run_export_button );
    csvRunExportButton.setOnClickListener( new OnClickListener() {
      public void onClick( final View buttonView ) {  
        MainActivity.createConfirmation( DataActivity.this, 
            DataActivity.this.getString(R.string.data_export_csv), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            FileUploaderTask fileUploaderTask = new FileUploaderTask( DataActivity.this, 
                ListActivity.lameStatic.dbHelper, DataActivity.this, true );
            fileUploaderTask.start();
          }
        } );
      }
    });
    
    final Button csvExportButton = (Button) findViewById( R.id.csv_export_button );
    csvExportButton.setOnClickListener( new OnClickListener() {
      public void onClick( final View buttonView ) {  
        MainActivity.createConfirmation( DataActivity.this, 
            DataActivity.this.getString(R.string.data_export_csv_db), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            FileUploaderTask fileUploaderTask = new FileUploaderTask( DataActivity.this, 
                ListActivity.lameStatic.dbHelper, DataActivity.this, true );
            fileUploaderTask.setWriteWholeDb();
            fileUploaderTask.start();
          }
        } );
      }
    });
  }
  
  private void setupKmlButtons() {
    final Button kmlRunExportButton = (Button) findViewById( R.id.kml_run_export_button );
    kmlRunExportButton.setOnClickListener( new OnClickListener() {
      public void onClick( final View buttonView ) {  
        MainActivity.createConfirmation( DataActivity.this, 
            DataActivity.this.getString(R.string.data_export_kml_run), new Doer() {
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
        MainActivity.createConfirmation( DataActivity.this, 
            DataActivity.this.getString(R.string.data_export_kml_db), new Doer() {
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
  
  private void setupBackupDbButton() {
    final Button kmlExportButton = (Button) findViewById( R.id.backup_db_button );
    if ( ! ListActivity.hasSD() ) {
      kmlExportButton.setEnabled(false);
    }
    
    kmlExportButton.setOnClickListener( new OnClickListener() {
      public void onClick( final View buttonView ) {  
        MainActivity.createConfirmation( DataActivity.this, 
            DataActivity.this.getString(R.string.data_backup_db), new Doer() {
          @Override
          public void execute() {
            // actually need this Activity context, for dialogs
            BackupTask task = new BackupTask(DataActivity.this, MainActivity.getListActivity(DataActivity.this));
            task.execute();
          }
        } );
      }
    });  
  }
  
  /**
   * way to background load the data and show progress on the gui thread
   */
  public static class BackupTask extends AsyncTask<Object, Integer, Integer> {
    private final Activity activity;
    private final ListActivity listActivity;
    private Pair<Boolean,String> dbResult;
    
    public BackupTask ( final Activity activity, final ListActivity listActivity ) {
      this.activity = activity;
      this.listActivity = listActivity;
      listActivity.setUploading();
    }
    
    @Override
    protected Integer doInBackground( Object... obj ) {
      dbResult = ListActivity.lameStatic.dbHelper.copyDatabase(this);
      // dbResult = new Pair<Boolean,String>(Boolean.TRUE, "meh");
      return 0;
    }
    
    @Override
    protected void onProgressUpdate( Integer... progress ) {      
      final TextView tv = (TextView) activity.findViewById( R.id.backup_db_text );
      tv.setText( activity.getString(R.string.backup_db_text) + "\n" + progress[0] + "%" );
    }
    
    @Override
    protected void onPostExecute( Integer result ) {       
      listActivity.uploadComplete();
      
      final TextView tv = (TextView) activity.findViewById( R.id.backup_db_text );
      tv.setText( activity.getString(R.string.backup_db_text) );
      
      final AlertDialog.Builder builder = new AlertDialog.Builder( activity );
      builder.setCancelable( true );
      builder.setTitle( activity.getString( dbResult.getFirst() ? R.string.status_success : R.string.status_fail ));
      builder.setMessage( dbResult.getSecond() );
      final AlertDialog ad = builder.create();
      // ok
      ad.setButton( DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
        public void onClick( final DialogInterface dialog, final int which ) {
          try {
            dialog.dismiss();
          }
          catch ( Exception ex ) {
            // guess it wasn't there anyways
            ListActivity.info( "exception dismissing alert dialog: " + ex );
          }
          return;
        } }); 
      ad.show();
    }
    
    public void progress( int progress ) {
      publishProgress(progress);
    }        
  }
  
  @Override
  public void onResume() {
    ListActivity.info( "resume data." );    
    super.onResume();
  }
   
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add( 0, MENU_EXIT, 0, getString(R.string.menu_exit) );
      item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
              
      item = menu.add( 0, MENU_ERROR_REPORT, 0, getString(R.string.menu_error_report) );
      item.setIcon( android.R.drawable.ic_menu_report_image );
      
      item = menu.add( 0, MENU_SETTINGS, 0, getString(R.string.menu_settings) );
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
