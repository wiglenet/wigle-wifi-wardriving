package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.ProgressPanel;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.FileUtility;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import org.apache.commons.io.FilenameUtils;

import java.io.File;

import static net.wigle.wigleandroid.util.FileUtility.CSV_EXT;
import static net.wigle.wigleandroid.util.FileUtility.CSV_GZ_EXT;
import static net.wigle.wigleandroid.util.FileUtility.KML_EXT;

public class BackgroundGuiHandler extends Handler {
    public static final int WRITING_PERCENT_START = 100000;
    public static final int AUTHENTICATION_ERROR = 1;
    public static final int CONNECTION_ERROR = -1;
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
                WiGLEToast.showOverActivity(this.context, R.string.error_general, context.getString(R.string.status_login_fail));
                if (pp != null) {
                    pp.hide();
                }
            }
            if (msg.what == CONNECTION_ERROR) {
                WiGLEToast.showOverActivity(this.context, R.string.error_general, context.getString(R.string.no_wigle_conn));
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
                WiGLEToast.showOverFragment(context, status.getTitle(),
                        composeDisplayMessage(context, msg.peekData().getString(ERROR),
                                msg.peekData().getString(FILEPATH), msg.peekData().getString(FILENAME),
                                status.getMessage()));
            } else if (Status.WRITE_SUCCESS.equals(status)) {
                final String fileName = msg.peekData().getString(FILENAME);
                if (null != fileName) {
                    if (fileName.endsWith(KML_EXT)) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE KML Export");
                        try {
                            if (null != this.context) {
                                final String filePath = msg.peekData().getString(FILEPATH);
                                //MainActivity.debug("File: "+filePath+" ("+fileName+")");
                                if (null != filePath) {
                                    File file = FileUtility.getKmlDownloadFile(this.context, FilenameUtils.removeExtension(fileName), filePath);
                                    if (file == null || !file.exists()) {
                                        showError(fm, msg, status);
                                        MainActivity.error("UNABLE to export file - no access to " + filePath + " - " + fileName);
                                        return;
                                    }
                                    Uri fileUri = FileProvider.getUriForFile(context,
                                            MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                                    ".kmlprovider", file);
                                    //DEBUG: MainActivity.info("send action called for file URI: " + fileUri.toString());
                                    intent.setType("application/vnd.google-earth.kml+xml");
                                    intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    context.startActivity(Intent.createChooser(intent, context.getResources().getText(R.string.send_to)));
                                } else {
                                    showError(fm, msg, status);
                                    MainActivity.error("Null filePath - unable to share.");
                                }
                            } else {
                                showError(fm, msg, status);
                                MainActivity.error("null context - cannot generate intent to share KML");
                            }
                        } catch (Exception ex) {
                            showError(fm, msg, status);
                            MainActivity.error("Failed to send file intent: ", ex);
                        }
                    } else if (fileName.endsWith(CSV_GZ_EXT)) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE CSV Export");
                        try {
                            if (null != this.context) {
                                File file = FileUtility.getCsvGzFile(this.context, fileName);
                                if (file == null || !file.exists()) {
                                    showError(fm, msg, status);
                                    MainActivity.error("UNABLE to export CSV file - no access to " + fileName);
                                    return;
                                }
                                Uri fileUri = FileProvider.getUriForFile(context,
                                        MainActivity.getMainActivity().getApplicationContext().getPackageName() +
                                                ".csvgzprovider", file);
                                //DEBUG: MainActivity.info("send action called for file URI: " + fileUri.toString());
                                intent.setType("application/gzip");
                                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                context.startActivity(Intent.createChooser(intent, context.getResources().getText(R.string.send_to)));
                            } else {
                                showError(fm, msg, status);
                                MainActivity.error("null context - cannot generate intent to share CSV");
                            }
                        } catch (Exception ex) {
                            showError(fm, msg, status);
                            MainActivity.error("Failed to send file intent: ", ex);
                        }
                    } else {
                        //Other file types get a default dialog
                        showError(fm, msg, status);
                    }
                } else {
                    //Null filename - weird case
                    showError(fm, msg, status);
                }
            } else {
                showError(fm, msg, status);
            }
        }
    }

    private void showError(final FragmentManager fm, final Message msg, final Status status) {
        final BackgroundAlertDialog alertDialog = BackgroundAlertDialog.newInstance(msg, status);
        try {
            final Activity a = MainActivity.getMainActivity();
            if (null != a && !a.isFinishing()) {
                alertDialog.show(fm, "background-dialog");
            }
        } catch (IllegalStateException ex) {
            MainActivity.warn("illegal state in background gui handler: ", ex);
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
            index = filename.indexOf( KML_EXT );
            if ( index > 0 ) {
                filename = filename.substring( 0, index );
            }
        }
        if ( filename == null ) {
            filename = "";
        } else {
            final String showPath = filepath == null ? "" : filepath;
            filename = "\n\nFile location:\n" + showPath + filename;
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
                            MainActivity.getMainActivity().selectFragment(R.id.nav_settings);
                        } catch (Exception ex) {
                            MainActivity.info("failed to start settings fragment: " + ex, ex);
                        }
                    }
                } });

            return ad;
        }
    }
}
