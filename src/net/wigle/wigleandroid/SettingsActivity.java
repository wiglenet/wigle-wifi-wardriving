package net.wigle.wigleandroid;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.settings);
      
      final EditText edittext = (EditText) findViewById(R.id.edit_username);
      edittext.setOnKeyListener(new OnKeyListener() {
          public boolean onKey(View v, int keyCode, KeyEvent event) {
              // If the event is a key-down event on the "enter" button
              if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                  (keyCode == KeyEvent.KEYCODE_ENTER)) {
                // Perform action on key press
                Toast.makeText( SettingsActivity.this, edittext.getText(), Toast.LENGTH_SHORT).show();
                return true;
              }
              return false;
          }
      });
  }
  
  private static final int MENU_RETURN = 12;
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      MenuItem item = menu.add(0, MENU_RETURN, 0, "Return");
      item.setIcon( android.R.drawable.ic_media_previous );
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      switch ( item.getItemId() ) {
        case MENU_RETURN:
          finish();
          return true;
      }
      return false;
  }
}
