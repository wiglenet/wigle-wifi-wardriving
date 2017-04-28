package net.wigle.wigleandroid.background;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.WiGLEAuthException;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

public abstract class AbstractBackgroundTask extends Thread implements AlertSettable {
    private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;

    protected FragmentActivity context;
    protected final DatabaseHelper dbHelper;

    private final BackgroundGuiHandler handler;
    private final AtomicBoolean interrupt = new AtomicBoolean( false );
    private final Object lock = new Object();
    private final String name;
    private ProgressDialogFragment pd;
    private int lastSentPercent = -1;

    private static AbstractBackgroundTask latestTask = null;
    static final String PROGRESS_TAG = "background-task-progress";

    public AbstractBackgroundTask(final FragmentActivity context, final DatabaseHelper dbHelper, final String name,
                                  final boolean createDialog) {
        if ( context == null ) {
            throw new IllegalArgumentException( "context is null" );
        }
        if ( dbHelper == null ) {
            throw new IllegalArgumentException( "dbHelper is null" );
        }
        if ( name == null ) {
            throw new IllegalArgumentException( "name is null" );
        }

        this.context = context;
        this.dbHelper = dbHelper;
        this.name = name;

        if (createDialog) createProgressDialog( context );

        this.handler = new BackgroundGuiHandler(context, lock, pd, this);
        latestTask = this;
    }

    @Override
    public final void clearProgressDialog() {
        pd = null;
    }

    @Override
    public final void run() {
        // set thread name
        setName( name + "-" + getName() );

        try {
            MainActivity.info( "setting background thread priority (-20 highest, 19 lowest) to: " + THREAD_PRIORITY );
            Process.setThreadPriority( THREAD_PRIORITY );

            subRun();
        }
        catch ( InterruptedException ex ) {
            MainActivity.info( name + " interrupted: " + ex );
        }
        catch ( final WiGLEAuthException waex) {
            //DEBUG: MainActivity.info("auth error", waex);
            Bundle errorBundle = new Bundle();
            errorBundle.putCharSequence("AUTH_ERROR", waex.getMessage());
            sendBundledMessage(BackgroundGuiHandler.AUTHENTICATION_ERROR, errorBundle);
        }
        catch ( final Exception ex ) {
            dbHelper.deathDialog(name, ex);
        }
    }

    protected final void sendPercentTimesTen(final int percentDone, final Bundle bundle) {
        // only send up to 1000 times
        if ( percentDone > lastSentPercent && percentDone >= 0 ) {
            sendBundledMessage( BackgroundGuiHandler.WRITING_PERCENT_START + percentDone, bundle );
            lastSentPercent = percentDone;
        }
    }

    protected final void sendBundledMessage(final int what, final Bundle bundle) {
        final Message msg = new Message();
        msg.what = what;
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    protected abstract void subRun() throws IOException, InterruptedException, WiGLEAuthException;

    /** interrupt this task */
    public final void setInterrupted() {
        interrupt.set( true );
    }

    protected final boolean wasInterrupted() {
        return interrupt.get();
    }

    public final Handler getHandler() {
        return handler;
    }

    private void createProgressDialog(final FragmentActivity context) {
        // make an interruptable progress dialog
        pd = ProgressDialogFragment.newInstance();
        pd.show(context.getSupportFragmentManager(), PROGRESS_TAG);
    }

    public static class ProgressDialogFragment extends DialogFragment {
        public static ProgressDialogFragment newInstance() {
            return new ProgressDialogFragment ();
        }

        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setTitle(getString(Status.WRITING.getTitle()));
            dialog.setMessage(getString(Status.WRITING.getMessage()));
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            MainActivity.info("Cancelling dialog for task: " + latestTask);
            if (latestTask != null) {
                latestTask.setInterrupted();
            }
        }

        private ProgressDialog getDialog(final FragmentManager manager) {
            final ProgressDialogFragment dialog = (ProgressDialogFragment) manager.findFragmentByTag(PROGRESS_TAG);
            if (dialog != null) {
                return (ProgressDialog) dialog.getDialog();
            }
            MainActivity.info("No progress dialog");
            return null;
        }

        public void setMessage(final FragmentManager manager, final String message) {
            final ProgressDialog dialog = getDialog(manager);
            if (dialog != null)
            {
                dialog.setMessage(message);
            }
        }

        /**
         * Sets the progress of the dialog, we need to make sure we get the right dialog reference here
         * which is why we obtain the dialog fragment manually from the fragment manager
         * @param manager fragment manager
         * @param progress how much progress has been made
         */
        public void setProgress(final FragmentManager manager, final int progress)
        {
            final ProgressDialog dialog = getDialog(manager);
            if (dialog != null)
            {
                dialog.setProgress(progress);
            }
        }
    }

    public final void setContext( final FragmentActivity context ) {
        synchronized ( lock ) {
            this.context = context;
        }
        handler.setContext(context);
    }

    protected final boolean validAuth() {
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0);
        if ( (!prefs.getString(ListFragment.PREF_AUTHNAME,"").isEmpty()) && (TokenAccess.hasApiToken(prefs))) {
            return true;
        }
        return false;

    }


    protected final String getUsername() {
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0);
        String username = prefs.getString( ListFragment.PREF_USERNAME, "" );
        if ( prefs.getBoolean( ListFragment.PREF_BE_ANONYMOUS, false) ) {
            username = ListFragment.ANONYMOUS;
        }
        return username;
    }

    protected final String getPassword() {
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0);
        String password = prefs.getString( ListFragment.PREF_PASSWORD, "" );

        if ( prefs.getBoolean( ListFragment.PREF_BE_ANONYMOUS, false) ) {
            password = "";
        }
        return password;
    }

    protected final String getToken() {
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0);
        String token = TokenAccess.getApiToken(prefs);

        if ( prefs.getBoolean( ListFragment.PREF_BE_ANONYMOUS, false) ) {
            token = "";
        }
        return token;
    }

    /**
     * @return null if ok, else an error status
     */
    protected final Status validateUserPass(final String username, final String password) {
        Status status = null;
        if ( "".equals( username ) ) {
            MainActivity.error( "username not defined" );
            status = Status.BAD_USERNAME;
        }
        else if ( "".equals( password ) && ! ListFragment.ANONYMOUS.equals( username.toLowerCase(Locale.US) ) ) {
            MainActivity.error( "password not defined and username isn't 'anonymous'" );
            status = Status.BAD_PASSWORD;
        }

        return status;
    }

}
