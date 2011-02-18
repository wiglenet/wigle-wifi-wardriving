package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class NetworkActivity extends Activity {
  private static final int MENU_EXIT = 11;
  private Network network;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.network);
    
    final Intent intent = getIntent();
    final String bssid = intent.getStringExtra( ListActivity.NETWORK_EXTRA_BSSID );
    ListActivity.info( "bssid: " + bssid );
    
    network = ListActivity.getNetworkCache().get(bssid);
    
    TextView tv = (TextView) findViewById( R.id.bssid );
    tv.setText( bssid );
    
    if ( network != null ) {
      tv = (TextView) findViewById( R.id.ssid );
      tv.setText( network.getSsid() );
    }
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add(0, MENU_EXIT, 0, "Return");
      item.setIcon( android.R.drawable.ic_menu_revert );
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT:
          // call over to finish
          finish();
          return true;
      }
      return false;
  }
}
