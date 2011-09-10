package net.wigle.wigleandroid;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TabHost;

public final class MainActivity extends TabActivity {
  static final String TAB_LIST = "list";
  static final String TAB_MAP = "map";
  static final String TAB_DASH = "dash";
  static final String TAB_DATA = "data";
  
  private static MainActivity mainActivity;
  private ListActivity listActivity;
  private PowerManager.WakeLock wakeLock;
  
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
    
    mainActivity = this;

    TabHost tabHost = getTabHost();  // The activity TabHost
    TabHost.TabSpec spec;  // Reusable TabSpec for each tab
    Intent intent;  // Reusable Intent for each tab

    // Create an Intent to launch an Activity for the tab (to be reused)
    intent = new Intent().setClass(this, ListActivity.class);
    spec = tabHost.newTabSpec( TAB_LIST ).setIndicator("List")
                  .setContent(intent);
    tabHost.addTab(spec);
    
    intent = new Intent().setClass(this, MappingActivity.class);
    spec = tabHost.newTabSpec( TAB_MAP ).setIndicator("Map")
                  .setContent(intent);
    tabHost.addTab(spec);

    intent = new Intent().setClass(this, DashboardActivity.class);
    spec = tabHost.newTabSpec( TAB_DASH ).setIndicator("Dashboard")
                  .setContent(intent);
    tabHost.addTab(spec);
    
    intent = new Intent().setClass(this, DataActivity.class);
    spec = tabHost.newTabSpec( TAB_DATA ).setIndicator("Data")
                  .setContent(intent);
    tabHost.addTab(spec);
    
    // force shrink the tabs
    for ( int i = 0; i < tabHost.getTabWidget().getChildCount(); i++ ) {
      View view = tabHost.getTabWidget().getChildAt( i );
      int height = 60;
      ViewGroup.LayoutParams param = view.getLayoutParams();
      param.height = height;
    }

    tabHost.setCurrentTabByTag( TAB_LIST );
  }
  
  // be careful with this
  public static MainActivity getMainActivity() {
    return mainActivity;
  }
  
  /**
   * switch to the specified tab using the activity's parent, if possible
   * @param activity
   * @param tab
   */
  static void switchTab( Activity activity, String tab ) {
    final Activity parent = activity.getParent();
    if ( parent != null && parent instanceof TabActivity ) {
      ((TabActivity) parent).getTabHost().setCurrentTabByTag( tab );
    }
  }
  
  static void setLockScreen( Activity activity, boolean lockScreen ) {
    final Activity parent = activity.getParent();
    if ( parent != null && parent instanceof MainActivity ) {
      MainActivity main = (MainActivity) parent;
      main.setLockScreen( lockScreen );
    }
  }
  
  static boolean isScreenLocked( Activity activity ) {
    final Activity parent = activity.getParent();
    if ( parent != null && parent instanceof MainActivity ) {
      MainActivity main = (MainActivity) parent;
      return main.wakeLock.isHeld();
    }
    return false;
  }
  
  private void setLockScreen( boolean lockScreen ) {
    if ( lockScreen ) {
      if ( ! wakeLock.isHeld() ) {
        ListActivity.info("acquire wake lock");
        wakeLock.acquire();
      }
    }
    else if ( wakeLock.isHeld() ) {
      ListActivity.info("release wake lock");
      wakeLock.release();
    }
  }
  
  public static interface Doer {
    public void execute();
  }
  
  static void createConfirmation( final Activity activity, final String message, final Doer doer ) {
    final AlertDialog.Builder builder = new AlertDialog.Builder( activity );
    builder.setCancelable( true );
    builder.setTitle( "Confirmation" );
    builder.setMessage( message );
    AlertDialog ad = builder.create();
    // ok
    ad.setButton( DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
      public void onClick( final DialogInterface dialog, final int which ) {
        try {
          dialog.dismiss();
          doer.execute();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          ListActivity.info( "exception dismissing alert dialog: " + ex );
        }
        return;
      } }); 
    
    // cancel
    ad.setButton( DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
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
    
    try {
      ad.show();
    }
    catch ( WindowManager.BadTokenException ex ) {
      ListActivity.info( "exception showing dialog, view probably changed: " + ex, ex );
    }
  }
  
  public static CheckBox prefSetCheckBox( final Context context, final Dialog dialog, final int id, 
      final String pref, final boolean def ) {
    
    final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final CheckBox checkbox = (CheckBox) dialog.findViewById( id );
    checkbox.setChecked( prefs.getBoolean( pref, def ) );
    return checkbox;
  }
  
  public static CheckBox prefSetCheckBox( final Activity activity, final int id, final String pref, final boolean def ) {
    final SharedPreferences prefs = activity.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final CheckBox checkbox = (CheckBox) activity.findViewById( id );
    checkbox.setChecked( prefs.getBoolean( pref, def ) );
    return checkbox;
  }
  
  public static CheckBox prefBackedCheckBox( final Activity activity, final int id, final String pref, final boolean def ) {
    final SharedPreferences prefs = activity.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final Editor editor = prefs.edit();
    final CheckBox checkbox = prefSetCheckBox( activity, id, pref, def );
    checkbox.setOnCheckedChangeListener( new OnCheckedChangeListener() {
      public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {             
            editor.putBoolean( pref, isChecked );
            editor.commit();
        }
    });
    
    return checkbox;
  }
  
  static MainActivity getMainActivity( Activity activity ) {
    final Activity parent = activity.getParent();    
    if ( parent != null && parent instanceof MainActivity ) {
      return (MainActivity) parent;
    }
    return null;
  }
  
  /** safely get the canonical path, as this call throws exceptions on some devices */
  public static String safeFilePath( final File file ) {
    String retval = null;
    try {
      retval = file.getCanonicalPath();
    }
    catch ( Exception ex ) {
      // ignore
    }
    
    if ( retval == null ) {
      retval = file.getAbsolutePath();
    }
    return retval;
  }
  
  /**
   * For now, we need a handle to the list activity to FINISH HIM.
   * Eventually the MainActivity might handle all the services and listeners and things
   * @param listActivity
   */
  public void setListActivity( final ListActivity listActivity ) {
    this.listActivity = listActivity;
  }
  
  public static void finishListActivity( final Activity activity ) {
    MainActivity main = getMainActivity( activity );
    if ( main != null ) {
      main.finishListActivity();
    }
  }
  
  public static ListActivity getListActivity( final Activity activity ) {
    MainActivity main = getMainActivity( activity );
    if ( main != null ) {
      return main.listActivity;
    }
    return null;
  }
  
  public void finishListActivity() {
    if ( listActivity != null ) {
      listActivity.finish();
    }
  }
  
  @Override
  public void onDestroy() {
    ListActivity.info( "MAIN: destroy." );
    super.onDestroy();
  }
  
  @Override
  public void onPause() {
    ListActivity.info( "MAIN: pause." );
    super.onPause();
    
    // deal with wake lock
    if ( wakeLock.isHeld() ) {
      ListActivity.info("release wake lock");
      wakeLock.release();
    }
  }
  
  @Override
  public void onResume() {
    ListActivity.info( "MAIN: resume." );
    super.onResume();
    
    // deal with wake lock
    if ( ! wakeLock.isHeld() ) {
      ListActivity.info("acquire wake lock");
      wakeLock.acquire();
    }
  }
  
  @Override
  public void onStart() {
    ListActivity.info( "MAIN: start." );
    super.onStart();
  }
  
  @Override
  public void onStop() {
    ListActivity.info( "MAIN: stop." );
    super.onStop();
  }
  
  @Override
  public void onRestart() {
    ListActivity.info( "MAIN: restart." );
    super.onRestart();
  }
  
  @Override
  public void finish() {
    ListActivity.info( "MAIN: finish." );
    super.finish();
  }
  
  public static Throwable getBaseThrowable(final Throwable throwable) {
    Throwable retval = throwable;
    while ( retval.getCause() != null ) {
      retval = retval.getCause();
    }
    return retval;
  }
  
  public static String getBaseErrorMessage(Throwable throwable, final boolean withNewLine) {
    throwable = MainActivity.getBaseThrowable( throwable );
    final String newline = withNewLine ? "\n" : " ";
    return throwable.getClass().getSimpleName() + ":" + newline + throwable.getMessage();
  }
}
