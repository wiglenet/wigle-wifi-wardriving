package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.SettingsFragment;
import net.wigle.wigleandroid.background.AbstractBackgroundTask.ProgressDialogFragment;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.Toast;

public class BackgroundGuiHandler extends Handler {
    public static final int WRITING_PERCENT_START = 100000;
    public static final int AUTHENTICATION_ERROR = 1;
    public static final String ERROR = "error";
    public static final String FILENAME = "filename";
    public static final String FILEPATH = "filepath";

    private FragmentActivity context;
    private final Object lock;
    private final ProgressDialogFragment pd;
    private final AlertSettable alertSettable;

    private String msg_text = "";

    public BackgroundGuiHandler(final FragmentActivity context, final Object lock, final ProgressDialogFragment pd,
                                final AlertSettable alertSettable) {

        this.context = context;
        this.lock = lock;
        this.pd = pd;
        this.alertSettable = alertSettable;
    }

    public void setContext(final FragmentActivity context) {
        synchronized(lock) {
            this.context = context;
        }
    }

    @Override
    public void handleMessage( final Message msg ) {
        synchronized ( lock ) {
            if (msg.what == AUTHENTICATION_ERROR) {
                Toast.makeText(this.context, R.string.status_login_fail
                        , Toast.LENGTH_LONG).show();
            }
            if (pd == null) {
                // no dialog box, just return
                return;
            }

            if ( msg.what >= WRITING_PERCENT_START ) {
                final int percentTimesTen = msg.what - WRITING_PERCENT_START;
                pd.setMessage( context.getSupportFragmentManager(), msg_text + " " + (percentTimesTen/10f) + "%" );
                // "The progress range is 0..10000."
                pd.setProgress( context.getSupportFragmentManager(), percentTimesTen * 10 );
                return;
            }

            if ( msg.what >= Status.values().length || msg.what < 0 ) {
                MainActivity.error( "msg.what: " + msg.what + " out of bounds on Status values");
                return;
            }
            final Status status = Status.values()[ msg.what ];
            if ( Status.UPLOADING.equals( status ) ) {
                //          pd.setMessage( status.getMessage() );
                msg_text = context.getString( status.getMessage() );
                pd.setProgress(context.getSupportFragmentManager(), 0);
                return;
            }
            if ( Status.WRITING.equals( status ) ) {
                msg_text = context.getString( status.getMessage() );
                pd.setProgress(context.getSupportFragmentManager(), 0);
                return;
            }

            // If we got this far then the task is done

            // make sure we didn't lose this dialog this somewhere
            if ( pd != null ) {
                try {
                    MainActivity.info("fragment from pd: " + pd);
                    pd.dismiss();
                    alertSettable.clearProgressDialog();
                }
                catch ( Exception ex ) {
                    // guess it wasn't there anyways
                    MainActivity.info( "exception dismissing dialog: " + ex );
                }
            }
            // Activity context
            final FragmentManager fm = context.getSupportFragmentManager();
            final DialogFragment dialog = (DialogFragment) fm.findFragmentByTag(AbstractBackgroundTask.PROGRESS_TAG);
            if (dialog != null) {
                try {
                    MainActivity.info("fragment from dialog: " + dialog);
                    dialog.dismiss();
                }
                catch ( Exception ex ) {
                    // guess it wasn't there anyways
                    MainActivity.info( "exception dismissing fm dialog: " + ex );
                }
            }

            final BackgroundAlertDialog alertDialog = BackgroundAlertDialog.newInstance(msg, status);
            try {
                alertDialog.show(fm, "background-dialog");
            }
            catch (IllegalStateException ex) {
                MainActivity.warn("illegal state in background gui handler: " + ex, ex);
            }
        }
    }

    public static class BackgroundAlertDialog extends DialogFragment {
        public static BackgroundAlertDialog newInstance( final Message msg, final Status status ) {
            final BackgroundAlertDialog frag = new BackgroundAlertDialog();
            Bundle args = msg.peekData();
            args.putInt("title", status.getTitle());
            args.putInt("message", status.getMessage());
            args.putInt("status", status.ordinal());
            frag.setArguments(args);
            return frag;
        }

        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final FragmentActivity activity = getActivity();
            final AlertDialog.Builder builder = new AlertDialog.Builder( activity );
            builder.setCancelable( false );
            final Bundle bundle = getArguments();
            builder.setTitle( bundle.getInt("title") );
            final int message = bundle.getInt("message");
            final int status = bundle.getInt("status");

            String filename;

            String filepath = bundle.getString( FILEPATH );
            filepath = filepath == null ? "" : filepath + "\n";
            filename = bundle.getString( FILENAME );
            if ( filename != null ) {
                // just don't show the gz
                int index = filename.indexOf( ".gz" );
                if ( index > 0 ) {
                    filename = filename.substring( 0, index );
                }
                index = filename.indexOf( ".kml" );
                if ( index > 0 ) {
                    filename = filename.substring( 0, index );
                }
            }
            if ( filename == null ) {
                filename = "";
            }
            else {
                filename = "\n\nFile location:\n" + filepath + filename;
            }

            String error = bundle.getString( ERROR );
            error = error == null ? "" : " Error: " + error;
            builder.setMessage( activity.getString( message ) + error + filename );

            AlertDialog ad = builder.create();
            ad.setButton( DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick( final DialogInterface dialog, final int which ) {
                    try {
                        dialog.dismiss();
                    }
                    catch ( Exception ex ) {
                        // guess it wasn't there anyways
                        MainActivity.info( "exception dismissing alert dialog: " + ex );
                    }

                    if (status == Status.BAD_USERNAME.ordinal() || status == Status.BAD_PASSWORD.ordinal()
                            || status == Status.BAD_LOGIN.ordinal()) {
                        MainActivity.info("dialog: start settings fragment");
                        try {
                            MainActivity.getMainActivity().selectFragment(MainActivity.SETTINGS_TAB_POS);
                        }
                        catch (Exception ex) {
                            MainActivity.info("failed to start settings fragment: " + ex, ex);
                        }
                    }
                } });

            return ad;
        }
    }

}
