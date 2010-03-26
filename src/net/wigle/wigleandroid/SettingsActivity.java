package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class SettingsActivity extends Activity {
  /** convenience, just get the darn new string */
  private static abstract class SetWatcher implements TextWatcher {
    public void afterTextChanged(Editable s) {}
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    public void onTextChanged(CharSequence s, int start, int before, int count) {
      onTextChanged( s.toString() ); 
    }
    public abstract void onTextChanged( String s );
  }
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.settings);
      
      SharedPreferences prefs = this.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
      final Editor editor = prefs.edit();
      
      final CheckBox beAnonymous = (CheckBox) findViewById(R.id.be_anonymous);
      final EditText user = (EditText) findViewById(R.id.edit_username);
      final EditText pass = (EditText) findViewById(R.id.edit_password);
      boolean isAnonymous = prefs.getBoolean( WigleAndroid.PREF_BE_ANONYMOUS, false);
      if ( isAnonymous ) {
        user.setEnabled( false );
        pass.setEnabled( false );
      }
      
      beAnonymous.setChecked( isAnonymous );
      beAnonymous.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {             
              editor.putBoolean( WigleAndroid.PREF_BE_ANONYMOUS, isChecked );
              editor.commit();
              
              if ( isChecked ) {
                // turn anonymous
                user.setEnabled( false );
                pass.setEnabled( false );
                user.setText( WigleAndroid.ANONYMOUS );
                pass.setText( "" );
                editor.putString( WigleAndroid.PREF_USERNAME, WigleAndroid.ANONYMOUS );
                editor.putString( WigleAndroid.PREF_PASSWORD, "" );
              }
              else {
                // unset anonymous
                user.setEnabled( true );
                pass.setEnabled( true );
                user.setText( "" );
                pass.setText( "" );
                editor.putString( WigleAndroid.PREF_USERNAME, "" );
                editor.putString( WigleAndroid.PREF_PASSWORD, "" );
              }
          }
      });
      
      user.setText( prefs.getString( WigleAndroid.PREF_USERNAME, "") );
      user.addTextChangedListener( new SetWatcher() {
        public void onTextChanged(String s) {
          // WigleAndroid.debug("user: " + s);
          editor.putString( WigleAndroid.PREF_USERNAME, s.trim() );
          editor.commit();
        } 
      });
      
      pass.setText( prefs.getString( WigleAndroid.PREF_PASSWORD, "") );
      pass.addTextChangedListener( new SetWatcher() {
        public void onTextChanged(String s) {
          // WigleAndroid.debug("pass: " + s);
          editor.putString( WigleAndroid.PREF_PASSWORD, s.trim() );
          editor.commit();
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
