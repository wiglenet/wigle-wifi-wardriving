package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.ProgressPanel;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.WiGLEAuthException;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractBackgroundTask extends Thread implements AlertSettable {
    private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;

    protected FragmentActivity context;
    protected final DatabaseHelper dbHelper;

    private final BackgroundGuiHandler handler;
    private final AtomicBoolean interrupt = new AtomicBoolean( false );
    private final Object lock = new Object();
    private final String name;
    private ProgressPanel pp;
    private int lastSentPercent = -1;

    private static AbstractBackgroundTask latestTask = null;
    static final String PROGRESS_TAG = "background-task-progress";

    public AbstractBackgroundTask(final FragmentActivity context, final DatabaseHelper dbHelper, final String name,
                                  final boolean showProgress) {
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

        if (showProgress) activateProgressPanel( context );
        //TODO: make this a placeholder?
        //pp.setMessage(name);

        this.handler = new BackgroundGuiHandler(context, lock, pp, this);
        latestTask = this;
    }

    @Override
    public final void clearProgressDialog() {
        if (null != pp) {
            pp.hide();
        }
        pp = null;
    }

    @Override
    public final void run() {
        // set thread name
        setName( name + "-" + getName() );

        try {
            MainActivity.info( "setting background thread priority (-20 highest, 19 lowest) to: " + THREAD_PRIORITY );
            Process.setThreadPriority( THREAD_PRIORITY );

            subRun();
        } catch ( InterruptedException ex ) {
            MainActivity.info( name + " interrupted: " + ex );
        } catch ( final WiGLEAuthException waex) {
            //DEBUG: MainActivity.error("auth error", waex);
            Bundle errorBundle = new Bundle();
            errorBundle.putCharSequence("AUTH_ERROR", waex.getMessage());
            sendBundledMessage(BackgroundGuiHandler.AUTHENTICATION_ERROR, errorBundle);
        } catch ( final IOException ioex) {
            MainActivity.error("connection error", ioex);
            Bundle errorBundle = new Bundle();
            errorBundle.putString(BackgroundGuiHandler.ERROR, "IOException");
            errorBundle.putCharSequence("CONN_ERROR", ioex.getMessage());
            sendBundledMessage(BackgroundGuiHandler.AUTHENTICATION_ERROR, errorBundle);
        } catch ( final Exception ex ) {
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

    public static void updateTransferringState(final boolean transferring, final FragmentActivity context) {
        Button uploadButton = (Button) context.findViewById(R.id.upload_button);
        if (null != uploadButton) uploadButton.setEnabled(!transferring);
        Button importObservedButton = (Button) context.findViewById(R.id.import_observed_button);
        if (null != importObservedButton) importObservedButton.setEnabled(!transferring);
        if (transferring) {
            MainActivity.getMainActivity().setTransferring();
        } else {
            MainActivity.getMainActivity().transferComplete();
        }
    }

    private void activateProgressPanel(final FragmentActivity context) {
        final LinearLayout progressLayout = (LinearLayout) context.findViewById(R.id.inline_status_bar);
        final TextView progressLabel = (TextView) context.findViewById(R.id.inline_progress_status);
        final ProgressBar progressBar = (ProgressBar) context.findViewById(R.id.inline_status_progress);
        final Button taskCancelButton = (Button) context.findViewById(R.id.inline_status_cancel);

        if ((null != progressLayout) && (null != progressLabel) && (null != progressBar)) {
            pp = new ProgressPanel(progressLayout, progressLabel, progressBar);
            pp.show();
            taskCancelButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    latestTask.interrupt();
                    clearProgressDialog();
                    updateTransferringState(false, context);
                }
            });
            //ALIBI: this will get replaced as soon as the progress is set for the first time
            progressBar.setIndeterminate(true);

            //ALIBI: prevent multiple simultaneous large transfers by disabling visible buttons,
            // setting global state to make sure they get set on show
            updateTransferringState(true, context);
            pp.setMessage(context.getString(R.string.status_working));
            pp.setIndeterminate();
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
