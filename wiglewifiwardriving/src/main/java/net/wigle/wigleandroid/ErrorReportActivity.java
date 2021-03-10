package net.wigle.wigleandroid;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import net.wigle.wigleandroid.util.FileUtility;

/**
 * display latest error stack, if any.
 * allow the stack to be emailed to us.
 * @author bobzilla
 *
 */
public class ErrorReportActivity extends AppCompatActivity {
    // https://developer.android.com/reference/android/os/TransactionTooLargeException
    // ALIBI: this may still be too big in some cases, but we know we can't be bigger than the max - the size of the other data we package
    public final static int MAX_STACK_TRANSACTION_SIZE = (1024 * (1024-2));

    private static final int MENU_EXIT = 11;
    private static final int MENU_EMAIL = 12;
    private boolean fromFailure = false;
    private String stack;
    private String stackFilePath;

    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        // set language
        MainActivity.setLocale( this );
        setContentView( R.layout.error );

        // get stack from file
        stackFilePath = FileUtility.getLatestStackfilePath(getApplicationContext());
        if (stackFilePath == null || stackFilePath.isEmpty()) {
            //ALIBI: we have no record of what or why - no reason to hassle user
            finish();
        }
        stack = getLatestStack(stackFilePath);

        // set on view
        TextView tv = findViewById( R.id.errorreport );
        tv.setText( stack );

        Intent intent = getIntent();
        boolean doEmail = intent.getBooleanExtra( MainActivity.ERROR_REPORT_DO_EMAIL, false );
        if ( doEmail ) {
            fromFailure = true;
            // setup email sending
            setupEmail( stack, stackFilePath );
        }

        final String dialogMessage = intent.getStringExtra(MainActivity.ERROR_REPORT_DIALOG );
        if ( dialogMessage != null ) {
            fromFailure = true;
            shutdownRestOfApp();

            final Handler handler = new Handler();
            final Runnable dialogTask = new Runnable() {
                @Override
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
                        @Override
                        public void onClick( final DialogInterface dialog, final int which ) {
                            try {
                                dialog.dismiss();
                            }
                            catch ( Exception ex ) {
                                // guess it wasn't there anyways
                                MainActivity.info( "exception dismissing alert dialog: " + ex );
                            }
                        } });

                    try {
                        if (!isFinishing()) {
                            ad.show();
                        }
                    }
                    catch ( WindowManager.BadTokenException windowEx ) {
                        MainActivity.info("window probably gone when trying to display dialog. windowEx: " + windowEx, windowEx );
                    }
                }
            };

            handler.removeCallbacks( dialogTask );
            handler.postDelayed( dialogTask, 100 );
        }
    }

    private void shutdownRestOfApp() {
        MainActivity.info( "ErrorReportActivity: shutting down app" );
        // shut down anything we can get a handle to
        final MainActivity mainActivity = MainActivity.getMainActivity();
        if ( mainActivity != null ) {
            mainActivity.finishSoon();
        }
        if ( NetworkActivity.networkActivity != null ) {
            NetworkActivity.networkActivity.finish();
        }
        if ( SpeechActivity.speechActivity != null ) {
            SpeechActivity.speechActivity.finish();
        }
    }

    private String getLatestStack(final String filePath) {
        StringBuilder builder = new StringBuilder( "No Error Report found" );
        if (filePath == null) return builder.toString();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader( new InputStreamReader( new FileInputStream( filePath ), "UTF-8") );
            String line = reader.readLine();
            builder.setLength( 0 );
            while ( line != null ) {
                builder.append( line ).append( "\n" );
                line = reader.readLine();
            }

            if (stack == null || stack.length() > MAX_STACK_TRANSACTION_SIZE) {
                return builder.toString();
            }
        } catch ( IOException ex ) {
            MainActivity.error( "error reading stack file: " + ex, ex );
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException ex) {
                    MainActivity.error( "error closing stack file: " + ex, ex );
                }
            }
        }

        return builder.toString();
    }

    private void setupEmail(final String stack, final String stackFile ) {
        MainActivity.info( "ErrorReport onCreate" );
        final Intent emailIntent = new Intent( android.content.Intent.ACTION_SEND );
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"wiwiwa@wigle.net"} );
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "WigleWifi error report" );
        emailIntent.setType("text/plain");
        if (stack == null && stackFile != null && ! stackFile.isEmpty()) {
            //ALIBI: if we have a stackfile but no stack string, it means the file's large
            emailIntent.putExtra(Intent.EXTRA_STREAM, stackFile);
        } else {
            emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, stack);
        }
        final Intent chooserIntent = Intent.createChooser( emailIntent, "Email WigleWifi error report?" );
        try {
            startActivity(chooserIntent);
        } catch (final ActivityNotFoundException ex) {
            MainActivity.warn("No email activity found: " + ex.getLocalizedMessage(), ex);
        } catch (final RuntimeException re) {
            MainActivity.warn("Runtime exception trying to send stack intent [stack: "+
                    (null!=stack?stack.length():"(none)")+" file:"+stackFile+"]; ", re);
        }
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
                setupEmail( stack, stackFilePath);
                return true;
        }
        return false;
    }

}
