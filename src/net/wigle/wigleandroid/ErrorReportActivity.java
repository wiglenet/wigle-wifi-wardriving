package net.wigle.wigleandroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * display latest error stack, if any.
 * allow the stack to be emailed to us.
 * @author bobzilla
 *
 */
public class ErrorReportActivity extends Activity {
  private static final int MENU_EXIT = 11;
  private static final int MENU_EMAIL = 12;
  private boolean fromFailure = false;
  private String stack;
  
  @Override
  public void onCreate( final Bundle savedInstanceState) {
    super.onCreate( savedInstanceState );
    // set language
    MainActivity.setLocale( this );
    setContentView( R.layout.error );
    
    // get stack from file
    stack = getLatestStack();
    
    // set on view
    TextView tv = (TextView) findViewById( R.id.errorreport );
    tv.setText( stack );
    
    Intent intent = getIntent();
    boolean doEmail = intent.getBooleanExtra( ListActivity.ERROR_REPORT_DO_EMAIL, false );
    if ( doEmail ) {
      fromFailure = true;
      // setup email sending
      setupEmail( stack );
    }
    
    final String dialogMessage = intent.getStringExtra(ListActivity.ERROR_REPORT_DIALOG );
    if ( dialogMessage != null ) {
      fromFailure = true;
      shutdownRestOfApp();
      
      final Handler handler = new Handler();
      final Runnable dialogTask = new Runnable() {
        public void run() {              
          final AlertDialog.Builder builder = new AlertDialog.Builder( ErrorReportActivity.this );
          builder.setCancelable( false );
          builder.setTitle( getString(R.string.fatal_title) );
          String fatalDbWarn = "";
          if ( dialogMessage.contains("SQL") ) {
            fatalDbWarn = getString(R.string.fatal_db_warn);
          }
          builder.setMessage( fatalDbWarn + "\n\n*** " + getString(R.string.fatal_pre_message) + ": ***\n" + dialogMessage 
              + "\n\n" + getString(R.string.fatal_post_message) );                      

          final AlertDialog ad = builder.create();
          ad.setButton( DialogInterface.BUTTON_POSITIVE, "OK, Shutdown", new DialogInterface.OnClickListener() {
            public void onClick( final DialogInterface dialog, final int which ) {
              try {
                dialog.dismiss();
              }
              catch ( Exception ex ) {
                // guess it wasn't there anyways
                ListActivity.info( "exception dismissing alert dialog: " + ex );
              }
            } }); 
          
          try {
            ad.show();
          }
          catch ( WindowManager.BadTokenException windowEx ) {
            ListActivity.info("window probably gone when trying to display dialog. windowEx: " + windowEx, windowEx );
          }
        }
      };
    
      handler.removeCallbacks( dialogTask );
      handler.postDelayed( dialogTask, 100 );
    }
  }
  
  private void shutdownRestOfApp() {
    ListActivity.info( "ErrorReportActivity: shutting down app" );
    // shut down anything we can get a handle to
    final MainActivity mainActivity = MainActivity.getMainActivity();
    mainActivity.finishListActivity();
    mainActivity.finish();                  
    if ( NetworkActivity.networkActivity != null ) {
      NetworkActivity.networkActivity.finish();
    }
    if ( SpeechActivity.speechActivity != null ) {
      SpeechActivity.speechActivity.finish();
    }
  }
  
  private String getLatestStack() {
    StringBuilder builder = new StringBuilder( "No Error Report found" );
    try {
      File fileDir = new File( MainActivity.safeFilePath( Environment.getExternalStorageDirectory() ) + "/wiglewifi/" );
      if ( ! fileDir.canRead() || ! fileDir.isDirectory() ) {
        ListActivity.error( "file is not readable or not a directory. fileDir: " + fileDir );
      }
      else {
        String[] files = fileDir.list();
        if ( files == null ) {
          ListActivity.error( "no files in dir: " + fileDir );
        }
        else {
          String latestFilename = null;
          for ( String filename : files ) {
            if ( filename.startsWith( ListActivity.ERROR_STACK_FILENAME ) ) {
              if ( latestFilename == null || filename.compareTo( latestFilename ) > 0 ) {
                latestFilename = filename;
              }
            }
          }
          ListActivity.info( "latest filename: " + latestFilename );
          
          String filePath = MainActivity.safeFilePath( fileDir ) + "/" + latestFilename;
          BufferedReader reader = new BufferedReader( new FileReader( filePath ) );
          String line = reader.readLine();
          builder.setLength( 0 );
          while ( line != null ) {
            builder.append( line ).append( "\n" );
            line = reader.readLine();
          }
        }
      }
    }
    catch ( IOException ex ) {
      ListActivity.error( "error reading stack file: " + ex, ex );
    }
    
    return builder.toString();
  }
  
  private void setupEmail( String stack ) {
    ListActivity.info( "ErrorReport onCreate" );
    final Intent emailIntent = new Intent( android.content.Intent.ACTION_SEND );
    emailIntent.setType( "plain/text" );
    emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"bobzilla@wigle.net"} );
    emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "WigleWifi error report" );
    emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, stack );
    final Intent chooserIntent = Intent.createChooser( emailIntent, "Email WigleWifi error report?" );
    startActivity( chooserIntent );
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
    if ( fromFailure ) {
      MenuItem item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_exit));
      item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
    }
    else {
      MenuItem item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_return));
      item.setIcon( android.R.drawable.ic_media_previous );
    }
    
    MenuItem item = menu.add(0, MENU_EMAIL, 0, getString(R.string.menu_error_report));
    item.setIcon( android.R.drawable.ic_menu_send );
    
    return true;
  }
  
  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT:
          finish();
          return true;
        case MENU_EMAIL:
          setupEmail( stack );
          return true;
      }
      return false;
  }
  
}
