package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class SpeechActivity extends Activity {
  private static final int MENU_RETURN = 12;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.speech );
    
    // force media volume controls
    this.setVolumeControlStream( AudioManager.STREAM_MUSIC );
    
    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    doButton( prefs, R.id.speech_run, ListActivity.PREF_SPEAK_RUN );
    doButton( prefs, R.id.speech_new, ListActivity.PREF_SPEAK_NEW );
    doButton( prefs, R.id.speech_queue, ListActivity.PREF_SPEAK_QUEUE );
    doButton( prefs, R.id.speech_miles, ListActivity.PREF_SPEAK_MILES );
    doButton( prefs, R.id.speech_time, ListActivity.PREF_SPEAK_TIME );
    doButton( prefs, R.id.speech_battery, ListActivity.PREF_SPEAK_BATTERY );
  }
  
  private void doButton( final SharedPreferences prefs, final int id, final String pref ) {
    final CheckBox box = (CheckBox) findViewById( id );
    box.setChecked( prefs.getBoolean( pref, true) );
    box.setOnCheckedChangeListener( new OnCheckedChangeListener() {
      public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {
        final Editor editor = prefs.edit();
        editor.putBoolean( pref, isChecked );
        editor.commit();
      }
    });
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add(0, MENU_RETURN, 0, "Return");
      item.setIcon( android.R.drawable.ic_media_previous );
      
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_RETURN:
          finish();
          return true;
      }
      return false;
  }
}
