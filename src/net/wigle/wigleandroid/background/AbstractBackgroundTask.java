package net.wigle.wigleandroid.background;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.background.BackgroundGuiHandler.BackgroundAlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.FragmentActivity;

public abstract class AbstractBackgroundTask extends Thread implements AlertSettable {
  private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;

  protected FragmentActivity context;
  protected final DatabaseHelper dbHelper;

  private final Handler handler;
  private final AtomicBoolean interrupt = new AtomicBoolean( false );
  private final Object lock = new Object();
  private final String name;
  private ProgressDialog pd;
  private BackgroundAlertDialog ad;
  private int lastSentPercent = -1;

  public AbstractBackgroundTask( final FragmentActivity context, final DatabaseHelper dbHelper, final String name ) {
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

    createProgressDialog( context );

    this.handler = new BackgroundGuiHandler(context, lock, pd, this);
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
      MainActivity.info( "setting file export thread priority (-20 highest, 19 lowest) to: " + THREAD_PRIORITY );
      Process.setThreadPriority( THREAD_PRIORITY );

      subRun();
    }
    catch ( InterruptedException ex ) {
      MainActivity.info( name + " interrupted: " + ex );
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

  protected abstract void subRun() throws IOException, InterruptedException;

  /** interrupt this upload */
  public final void setInterrupted() {
    interrupt.set( true );
  }

  protected final boolean wasInterrupted() {
    return interrupt.get();
  }

  public final Handler getHandler() {
    return handler;
  }

  private void createProgressDialog(final Context context) {
    // make an interruptable progress dialog
    pd = ProgressDialog.show( context, context.getString(Status.WRITING.getTitle()),
        context.getString(Status.WRITING.getMessage()), true, true,
      new OnCancelListener(){
        @Override
        public void onCancel( DialogInterface di ) {
          interrupt.set(true);
        }
      });
  }

  @Override
  public final void setAlertDialog(final BackgroundAlertDialog ad) {
    this.ad = ad;
  }

  public final void setContext( final FragmentActivity context ) {
    synchronized ( lock ) {
      this.context = context;

      if ( pd != null && pd.isShowing() ) {
        try {
          pd.dismiss();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          MainActivity.info( "exception dismissing progress dialog: " + ex );
        }
        createProgressDialog( context );
      }

      if ( ad != null && ad.isVisible() ) {
        try {
          ad.dismiss();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          MainActivity.info( "exception dismissing alert dialog: " + ex );
        }
      }
    }
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
