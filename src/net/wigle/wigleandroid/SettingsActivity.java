package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class SettingsActivity extends Activity {
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.settings);
      
      SharedPreferences prefs = this.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
      final Editor editor = prefs.edit();
      
      final EditText user = (EditText) findViewById(R.id.edit_username);
      user.setText( prefs.getString( WigleAndroid.PREF_USERNAME, "") );
      user.setOnKeyListener(new OnKeyListener() {
          public boolean onKey(View v, int keyCode, KeyEvent event) {
              String value = ((EditText)v).getText().toString();
              // WigleAndroid.info( "user value: '" + value + "'" );
              editor.putString( WigleAndroid.PREF_USERNAME, value );
              editor.commit();
              return false;
          }
      });
      
      final EditText pass = (EditText) findViewById(R.id.edit_password);
      pass.setText( prefs.getString( WigleAndroid.PREF_PASSWORD, "") );
      pass.setOnKeyListener(new OnKeyListener() {
          public boolean onKey(View v, int keyCode, KeyEvent event) {
              String value = ((EditText)v).getText().toString();
              // WigleAndroid.info( "pass value: '" + value + "'" );
              editor.putString( WigleAndroid.PREF_PASSWORD, value );
              editor.commit();
              return false;
          }
      });
      
      final CheckBox showCurrent = (CheckBox) findViewById(R.id.edit_showcurrent);
      showCurrent.setChecked( prefs.getBoolean( WigleAndroid.PREF_SHOW_CURRENT, true) );
      showCurrent.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {             
              editor.putBoolean( WigleAndroid.PREF_SHOW_CURRENT, isChecked );
              editor.commit();
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
