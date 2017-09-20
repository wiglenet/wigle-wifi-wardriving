package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.ProgressPanel;
import net.wigle.wigleandroid.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class BackgroundGuiHandler extends Handler {
    public static final int WRITING_PERCENT_START = 100000;
    public static final int AUTHENTICATION_ERROR = 1;
    public static final String ERROR = "error";
    public static final String FILENAME = "filename";
    public static final String FILEPATH = "filepath";

    private FragmentActivity context;
    private final Object lock;
    private final ProgressPanel pp;
    private final AlertSettable alertSettable;

    private String msg_text = "";

    public BackgroundGuiHandler(final FragmentActivity context, final Object lock, final ProgressPanel pp,
                                final AlertSettable alertSettable) {

        this.context = context;
        this.lock = lock;
        this.pp = pp;
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
                if (pp != null) {
                    pp.hide();
                }
            }
            if (pp == null) {
                // no dialog box, just return
                return;
            }

            if ( msg.what >= WRITING_PERCENT_START ) {
                final int percentTimesTen = msg.what - WRITING_PERCENT_START;
                pp.setMessage( msg_text + " " + (percentTimesTen/10f) + "%" );
                pp.setProgress( percentTimesTen / 10 );
                return;
            }

            if ( msg.what >= Status.values().length || msg.what < 0 ) {
                MainActivity.error( "msg.what: " + msg.what + " out of bounds on Status values");
                return;
            }
            final Status status = Status.values()[ msg.what ];
            if ( Status.UPLOADING.equals( status ) ) {
                msg_text = context.getString( status.getMessage() );
                pp.setProgress(0);
                return;
            } else if ( Status.WRITING.equals( status ) ) {
                msg_text = context.getString( status.getMessage() );
                pp.setProgress(0);
                return;
            } else if ( Status.DOWNLOADING.equals( status ) ) {
                msg_text = context.getString( status.getMessage() );
                //pp.setProgress(0);
                return;
            } else if ( Status.PARSING.equals( status ) ) {
                msg_text = context.getString( status.getMessage() );
                pp.setProgress(0);
                return;
            }

            // If we got this far then the task is done

            // make sure we didn't lose this dialog this somewhere
            if ( pp != null ) {
                try {
                    MainActivity.info("fragment from pp: " + pp);
                    AbstractBackgroundTask.updateTransferringState(false, context);
                    pp.hide();
                    alertSettable.clearProgressDialog();
                }
                catch ( Exception ex ) {
                    // guess it wasn't there anyway
                    MainActivity.info( "exception dismissing dialog/hiding progress: " + ex );
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
                    // you can't dismiss what isn't there
                    MainActivity.info( "exception dismissing fm dialog: " + ex );
                }
            }

            if (Status.SUCCESS.equals(status)) {
                //ALIBI: for now, success gets a long custom toast, other messages get dialogs
                //TODO: if we decide to use custom toast elsewhere, move this to its own class?
                LayoutInflater inflater = context.getLayoutInflater();
                View layout = inflater.inflate(R.layout.wigle_detail_toast,
                        (ViewGroup) context.findViewById(R.id.custom_toast_container));

                TextView title = (TextView) layout.findViewById(R.id.toast_title_text);
                title.setText(status.getTitle());

                TextView text = (TextView) layout.findViewById(R.id.toast_message_text);
                text.setText(composeDisplayMessage(context,  msg.peekData().getString( ERROR ),
                        msg.peekData().getString( FILEPATH ), msg.peekData().getString( FILENAME ),
                        status.getMessage() ));

                Toast toast = new Toast(context.getApplicationContext());
                toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(layout);
                toast.show();
            } else {
                final BackgroundAlertDialog alertDialog = BackgroundAlertDialog.newInstance(msg, status);
                try {
                    alertDialog.show(fm, "background-dialog");
                } catch (IllegalStateException ex) {
                    MainActivity.warn("illegal state in background gui handler: " + ex, ex);
                }
            }
        }
    }

    public static String composeDisplayMessage(Context context, String error, String filepath,
                                        String filename, final int messageId) {
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
        } else {
            filename = "\n\nFile location:\n" + filepath + filename;
        }
        error = error == null ? "" : " Error: " + error;

        return context.getString(messageId) + error + filename;
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

            builder.setMessage( composeDisplayMessage(activity,  bundle.getString( ERROR ),
                    bundle.getString( FILEPATH ), bundle.getString( FILENAME ),
                    message ));

            AlertDialog ad = builder.create();
            ad.setButton( DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick( final DialogInterface dialog, final int which ) {
                    try {
                        dialog.dismiss();
                    } catch ( Exception ex ) {
                        // guess it wasn't there anyways
                        MainActivity.info( "exception dismissing alert dialog: " + ex );
                    }

                    if (status == Status.BAD_USERNAME.ordinal() || status == Status.BAD_PASSWORD.ordinal()
                            || status == Status.BAD_LOGIN.ordinal()) {
                        MainActivity.info("dialog: start settings fragment");
                        try {
                            MainActivity.getMainActivity().selectFragment(MainActivity.SETTINGS_TAB_POS);
                        } catch (Exception ex) {
                            MainActivity.info("failed to start settings fragment: " + ex, ex);
                        }
                    }
                } });

            return ad;
        }
    }
}
