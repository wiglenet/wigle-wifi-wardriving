package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.ProgressPanel;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;

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
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.io.File;

import static net.wigle.wigleandroid.util.FileUtility.CSV_GZ_EXT;
import static net.wigle.wigleandroid.util.FileUtility.KML_EXT;

public class BackgroundGuiHandler extends Handler {
    public static final int WRITING_PERCENT_START = 100000;
    public static final String ERROR = "error";
    public static final String FILENAME = "filename";
    public static final String FILEPATH = "filepath";
    public static final String TRANSIDS = "transIds";

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
            if (msg.what == Status.BAD_LOGIN.ordinal()) {
                WiGLEToast.showOverActivity(this.context, R.string.error_general, context.getString(R.string.status_login_fail));
                if (pp != null) {
                    pp.hide();
                }
            }
            if (msg.what == Status.CONNECTION_ERROR.ordinal()) {
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
                Logging.error( "msg.what: " + msg.what + " out of bounds on Status values");
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
            // make sure we didn't lose this dialog somewhere
            try {
                //DEBUG: Logging.info("fragment from pp: " + pp);
                final Button importObservedButton = context.findViewById(R.id.import_observed_button);
                final Button uploadButton = context.findViewById(R.id.upload_button);
                AbstractBackgroundTask.updateTransferringState(false, uploadButton, importObservedButton);
                pp.hide();
                alertSettable.clearProgressDialog();
            } catch ( Exception ex ) {
                // guess it wasn't there anyway
                Logging.error( "exception dismissing dialog/hiding progress: " + ex );
            }
            // Activity context
            final FragmentManager fm = context.getSupportFragmentManager();
            final DialogFragment dialog = (DialogFragment) fm.findFragmentByTag(AbstractBackgroundTask.PROGRESS_TAG);
            if (dialog != null) {
                try {
                    //DEBUG: Logging.info("fragment from dialog: " + dialog);
                    if (null != dialog && dialog.isVisible()) {
                        dialog.dismiss();
                    }
                } catch ( Exception ex ) {
                    // you can't dismiss what isn't there
                    Logging.error( "exception dismissing fm dialog: " + ex );
                }
            }

            if (Status.SUCCESS.equals(status)) {
                //ALIBI: for now, success gets a long custom toast, other messages get dialogs
                WiGLEToast.showOverFragment(context, status.getTitle(),
                        composeDisplayMessage(context, msg.peekData().getString(ERROR),
                                msg.peekData().getString(FILEPATH), msg.peekData().getString(FILENAME),
                                status.getMessage(), msg.peekData().getString(TRANSIDS)));
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
                                    File file = FileUtility.getKmlDownloadFile(this.context, removeFileExtension(fileName), filePath);
                                    if (file == null || !file.exists()) {
                                        showError(fm, msg, status);
                                        Logging.error("UNABLE to export file - no access to " + filePath + " - " + fileName);
                                        return;
                                    }
                                    final MainActivity ma = MainActivity.getMainActivity();
                                    if (null != ma) {
                                        Uri fileUri = FileProvider.getUriForFile(context,
                                                ma.getApplicationContext().getPackageName() +
                                                        ".kmlprovider", file);
                                        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                    }
                                    //DEBUG: MainActivity.info("send action called for file URI: " + fileUri.toString());
                                    intent.setType("application/vnd.google-earth.kml+xml");
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    context.startActivity(Intent.createChooser(intent, context.getResources().getText(R.string.send_to)));
                                } else {
                                    showError(fm, msg, status);
                                    Logging.error("Null filePath - unable to share.");
                                }
                            } else {
                                showError(fm, msg, status);
                                Logging.error("null context - cannot generate intent to share KML");
                            }
                        } catch (Exception ex) {
                            showError(fm, msg, status);
                            Logging.error("Failed to send file intent: ", ex);
                        }
                    } else if (fileName.endsWith(CSV_GZ_EXT)) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "WiGLE CSV Export");
                        try {
                            if (null != this.context) {
                                File file = FileUtility.getCsvGzFile(this.context, fileName);
                                if (file == null || !file.exists()) {
                                    showError(fm, msg, status);
                                    Logging.error("UNABLE to export CSV file - no access to " + fileName);
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
                                Logging.error("null context - cannot generate intent to share CSV");
                            }
                        } catch (Exception ex) {
                            showError(fm, msg, status);
                            Logging.error("Failed to send file intent: ", ex);
                        }
                    } else {
                        //Other file types get a default dialog
                        Logging.error("file ending success error");
                        showError(fm, msg, status);
                    }
                } else {
                    //Null filename - weird case
                    Logging.error("null filename error");
                    showError(fm, msg, status);
                }
            } else {
                boolean settingsForward = false;
                if (msg.what == Status.BAD_USERNAME.ordinal()) {
                    WiGLEToast.showOverActivity(this.context, R.string.error_general, context.getString(R.string.status_no_user));
                    settingsForward = true;
                } else if (msg.what == Status.BAD_PASSWORD.ordinal()) {
                    WiGLEToast.showOverActivity(this.context, R.string.error_general, context.getString(R.string.status_no_pass));
                    settingsForward = true;
                } else {
                    showError(fm, msg, status);
                }
                //ALIBI: on first-launch, this is a little weird, since user just confirmed "anonymous" - but we don't set the pref for them when they click "ok" - we forward them
                if (settingsForward) {
                    try {
                        MainActivity.getMainActivity().selectFragment(R.id.nav_settings);
                    } catch (Exception ex) {
                        Logging.info("failed to start settings fragment: " + ex, ex);
                    }
                }
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
            Logging.warn("illegal state in background gui handler: ", ex);
        }
    }

    /**
     * Compose a file-specific string (for use in a dialog) including optional error, file name/path, and transId list (for uploads)
     * TODO: this does a poor job of i18n, the "File location:"  and "Transaction IDs:" messages should be delegated to local String files
     * @param context the context for string i18n
     * @param error the error string
     * @param filepath the path of the file
     * @param filename the name of the file
     * @param messageId the R.string.id of the message to prepend to error
     * @param transIds the list of transIds for transaction Ids
     * @return a composited string
     */
    public static String composeDisplayMessage(Context context, String error, String filepath,
                                        String filename, final int messageId, final String transIds) {
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
            filename = "File location:\n" + showPath + filename;
        }
        String transIdString = "";
        if (transIds != null && !transIds.isEmpty()) {
            transIdString = String.format("\n\nTransaction ID(s):\n"+transIds);
        }
        error = (error == null || error.isEmpty()) ? "" :  error+"\n\n";

        return error + filename + transIdString;
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
            final int titleId = (null != bundle) ? bundle.getInt("title") : -1;
            if (titleId != -1) {
                builder.setTitle(titleId);
            }
            final int message = (null != bundle) ? bundle.getInt("message"): -1;
            final int status = (null != bundle) ? bundle.getInt("status"): -1;

            builder.setMessage( composeDisplayMessage(activity,  (null != bundle) ? bundle.getString( ERROR ):null,
                    (null != bundle) ? bundle.getString( FILEPATH ):null, (null != bundle) ? bundle.getString( FILENAME ):null,
                    message,  (null != bundle)?bundle.getString( TRANSIDS) : null));

            AlertDialog ad = builder.create();
            ad.setButton( DialogInterface.BUTTON_POSITIVE, "OK", (dialog, which) -> {
                try {
                    if (null != dialog) {
                        dialog.dismiss();
                    }
                } catch ( Exception ex ) {
                    // guess it wasn't there anyways
                    Logging.info( "exception dismissing alert dialog: " + ex );
                }

                if (status == Status.BAD_USERNAME.ordinal() || status == Status.BAD_PASSWORD.ordinal()
                        || status == Status.BAD_LOGIN.ordinal()) {
                    Logging.info("dialog: start settings fragment");
                    try {
                        MainActivity.getMainActivity().selectFragment(R.id.nav_settings);
                    } catch (Exception ex) {
                        Logging.info("failed to start settings fragment: " + ex, ex);
                    }
                }
            });

            return ad;
        }
    }

    /**
     * ALIBI: simple filename ext. remover for Android. commons.io versions without security vulns (2.7 and up) break out backward compat.
     * @param fileName the input filename
     * @return the fileName minus the extension
     * @throws IllegalArgumentException if the filename is null or invalid
     */
    private static String removeFileExtension(final String fileName) throws IllegalArgumentException {
        if (fileName == null || fileName.indexOf(0) >= 0) {
            throw new IllegalArgumentException("Invalid file name: "+fileName);
        }
        final int extensionPos = fileName.lastIndexOf('.');
        final int lastSeparator = fileName.lastIndexOf('/');
        int index =  lastSeparator > extensionPos ? -1 : extensionPos;
        if (index == -1) {
            return fileName;
        }
        return fileName.substring(0, index);
    }
}
