package net.wigle.wigleandroid;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TextView;

public class MainActivity extends TabActivity {
  static final String TAB_LIST = "list";
  static final String TAB_MAP = "map";
  static final String TAB_DASH = "dash";
  static final String TAB_SETTINGS = "setings";
  
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    TabHost tabHost = getTabHost();  // The activity TabHost
    TabHost.TabSpec spec;  // Reusable TabSpec for each tab
    Intent intent;  // Reusable Intent for each tab

    // Create an Intent to launch an Activity for the tab (to be reused)
    intent = new Intent().setClass(this, WigleAndroid.class);
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
    
    intent = new Intent().setClass(this, SettingsActivity.class);
    spec = tabHost.newTabSpec( TAB_SETTINGS ).setIndicator("Settings")
                  .setContent(intent);
    tabHost.addTab(spec);
    
    // force shrink the tabs
    for ( int i = 0; i < tabHost.getTabWidget().getChildCount(); i++ ) {
      View view = tabHost.getTabWidget().getChildAt( i );
      int height = 50;
//      if ( view instanceof ViewGroup ) {
//        ViewGroup vg = (ViewGroup) view;
//        if ( vg.getChildCount() > 1 ) {
//          View child = vg.getChildAt( 1 );
//          WigleAndroid.info( "child: " + child );
//          if ( child instanceof TextView ) {
//            height = child.getMeasuredHeight();
//            WigleAndroid.info( "height: " + height );
//          }
//        }
//      }
      ViewGroup.LayoutParams param = view.getLayoutParams();
      param.height = height;
    }

    tabHost.setCurrentTabByTag( TAB_LIST );
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
  
  @Override
  public void onDestroy() {
    WigleAndroid.info( "MAIN: destroy." );
    super.onDestroy();
  }
  
  @Override
  public void onPause() {
    WigleAndroid.info( "MAIN: pause." );
    super.onPause();
  }
  
  @Override
  public void onResume() {
    WigleAndroid.info( "MAIN: resume." );
    super.onResume();
  }
  
  @Override
  public void onStart() {
    WigleAndroid.info( "MAIN: start." );
    super.onStart();
  }
  
  @Override
  public void onStop() {
    WigleAndroid.info( "MAIN: stop." );
    super.onStop();
  }
  
  @Override
  public void onRestart() {
    WigleAndroid.info( "MAIN: restart." );
    super.onRestart();
  }
  
  @Override
  public void finish() {
    WigleAndroid.info( "MAIN: finish." );
    super.finish();
  }
  
}
