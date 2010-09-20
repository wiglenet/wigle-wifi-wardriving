package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * configure settings
 */
public final class SettingsActivity extends Activity {
  
  private static final int MENU_RETURN = 12;
  private static final int MENU_ERROR_REPORT = 13;
  
  /** convenience, just get the darn new string */
  private static abstract class SetWatcher implements TextWatcher {
    public void afterTextChanged( final Editable s ) {}
    public void beforeTextChanged( final CharSequence s, final int start, final int count, final int after ) {}
    public void onTextChanged( final CharSequence s, final int start, final int before, final int count ) {
      onTextChanged( s.toString() ); 
    }
    public abstract void onTextChanged( String s );
  }
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState) {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.settings );
      
      // force media volume controls
      this.setVolumeControlStream( AudioManager.STREAM_MUSIC );

      // don't let the textbox have focus to start with, so we don't see a keyboard right away
      final LinearLayout linearLayout = (LinearLayout) findViewById( R.id.linearlayout );
      linearLayout.setFocusableInTouchMode(true);
      linearLayout.requestFocus();
      
      // get prefs
      final SharedPreferences prefs = this.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
      final Editor editor = prefs.edit();
      
      final CheckBox beAnonymous = (CheckBox) findViewById(R.id.be_anonymous);
      final EditText user = (EditText) findViewById(R.id.edit_username);
      final EditText pass = (EditText) findViewById(R.id.edit_password);
      final boolean isAnonymous = prefs.getBoolean( WigleAndroid.PREF_BE_ANONYMOUS, false);
      if ( isAnonymous ) {
        user.setEnabled( false );
        pass.setEnabled( false );
      }
      
      beAnonymous.setChecked( isAnonymous );
      beAnonymous.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {             
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
      
      user.setText( prefs.getString( WigleAndroid.PREF_USERNAME, "" ) );
      user.addTextChangedListener( new SetWatcher() {
        public void onTextChanged( final String s ) {
          // WigleAndroid.debug("user: " + s);
          editor.putString( WigleAndroid.PREF_USERNAME, s.trim() );
          editor.commit();
        } 
      });
      
      pass.setText( prefs.getString( WigleAndroid.PREF_PASSWORD, "" ) );
      pass.addTextChangedListener( new SetWatcher() {
        public void onTextChanged( final String s ) {
          // WigleAndroid.debug("pass: " + s);
          editor.putString( WigleAndroid.PREF_PASSWORD, s.trim() );
          editor.commit();
        } 
      });
      
      final CheckBox showCurrent = (CheckBox) findViewById(R.id.edit_showcurrent);
      showCurrent.setChecked( prefs.getBoolean( WigleAndroid.PREF_SHOW_CURRENT, true ) );
      showCurrent.setOnCheckedChangeListener( new OnCheckedChangeListener() {
        public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {             
              editor.putBoolean( WigleAndroid.PREF_SHOW_CURRENT, isChecked );
              editor.commit();
          }
      });
      
      // db marker reset button and text
      final TextView tv = (TextView) findViewById( R.id.reset_maxid_text );
      tv.setText( "Highest uploaded id: " + prefs.getLong( WigleAndroid.PREF_DB_MARKER, 0L ) );
      
      final Button resetMaxidButton = (Button) findViewById( R.id.reset_maxid_button );
      resetMaxidButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {             
              editor.putLong( WigleAndroid.PREF_DB_MARKER, 0L );
              editor.commit();
              tv.setText( "Max upload id: 0" );
          }
      });
      
      // db marker maxout button and text
      final TextView maxtv = (TextView) findViewById( R.id.maxout_maxid_text );
      final long maxDB = prefs.getLong( WigleAndroid.PREF_MAX_DB, 0L );
      maxtv.setText( "Max id at startup: " + maxDB );
      
      final Button maxoutMaxidButton = (Button) findViewById( R.id.maxout_maxid_button );
      maxoutMaxidButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {             
              editor.putLong( WigleAndroid.PREF_DB_MARKER, maxDB );
              editor.commit();
              // set the text on the other button
              tv.setText( "Max upload id: " + maxDB );
          }
      });
      
      // period spinners
      doScanSpinner( R.id.period_spinner, WigleAndroid.PREF_SCAN_PERIOD );
      doScanSpinner( R.id.periodfast_spinner, WigleAndroid.PREF_SCAN_PERIOD_FAST );
      
      final CheckBox foundSound = (CheckBox) findViewById(R.id.found_sound);
      foundSound.setChecked( prefs.getBoolean( WigleAndroid.PREF_FOUND_SOUND, true) );
      foundSound.setOnCheckedChangeListener( new OnCheckedChangeListener() {
        public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {             
              editor.putBoolean( WigleAndroid.PREF_FOUND_SOUND, isChecked );
              editor.commit();
          }
      });
      
      final CheckBox foundNewSound = (CheckBox) findViewById(R.id.found_new_sound);
      foundNewSound.setChecked( prefs.getBoolean( WigleAndroid.PREF_FOUND_NEW_SOUND, true) );
      foundNewSound.setOnCheckedChangeListener( new OnCheckedChangeListener() {
        public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {             
              editor.putBoolean( WigleAndroid.PREF_FOUND_NEW_SOUND, isChecked );
              editor.commit();
          }
      });
      
      final CheckBox speechGPS = (CheckBox) findViewById(R.id.speech_gps);
      speechGPS.setChecked( prefs.getBoolean( WigleAndroid.PREF_SPEECH_GPS, true) );
      speechGPS.setOnCheckedChangeListener( new OnCheckedChangeListener() {
        public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked) {             
              editor.putBoolean( WigleAndroid.PREF_SPEECH_GPS, isChecked );
              editor.commit();
          }
      });
      
      // speach spinner
      Spinner spinner = (Spinner) findViewById( R.id.speak_spinner );
      if ( ! TTS.hasTTS() ) {
        // no text to speech :(
        spinner.setEnabled( false );
        final TextView speakText = (TextView) findViewById( R.id.speak_text );
        speakText.setText("No Text-to-Speech engine");
      }
      ArrayAdapter<String> adapter = new ArrayAdapter<String>(
          this, android.R.layout.simple_spinner_item );
      final long[] speechPeriods = new long[]{ 10,15,30,60,120,300,600,1800,0 };
      final String[] speechName = new String[]{ "10 sec","15 sec","30 sec","1 min","2 min","5 min","10 min","30 min","Off" };
      long period = prefs.getLong( WigleAndroid.PREF_SPEECH_PERIOD, WigleAndroid.DEFAULT_SPEECH_PERIOD );
      int periodIndex = 0;
      for ( int i = 0; i < speechPeriods.length; i++ ) {
        adapter.add( speechName[i] );
        if ( period == speechPeriods[i] ) {
          periodIndex = i;
        }
      }
      adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
      spinner.setAdapter( adapter );
      spinner.setSelection( periodIndex );
      spinner.setOnItemSelectedListener( new OnItemSelectedListener() {
        public void onItemSelected( final AdapterView<?> parent, final View v, final int position, final long id ) {
          // set pref
          final long period = speechPeriods[position];
          WigleAndroid.info("setting period: " + period );
          editor.putLong( WigleAndroid.PREF_SPEECH_PERIOD, period );
          editor.commit();
        }
        public void onNothingSelected( final AdapterView<?> arg0 ) {}
        });   
      
      
  }
  
  private void doScanSpinner( final int id, final String pref ) {
    final SharedPreferences prefs = this.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
    final Editor editor = prefs.edit();
    
    Spinner spinner = (Spinner) findViewById( id );
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
        this, android.R.layout.simple_spinner_item);
    final long[] periods = new long[]{ 0,50,250,500,1000,2000,5000,10000,30000 };
    final String[] periodName = new String[]{ "Nonstop","50 ms","250ms","500 ms","1 sec","2 sec","5 sec","10 sec","30 sec" };
    long period = prefs.getLong( pref, WigleAndroid.SCAN_DEFAULT );
    int periodIndex = 0;
    for ( int i = 0; i < periods.length; i++ ) {
      adapter.add( periodName[i] );
      if ( period == periods[i] ) {
        periodIndex = i;
      }
    }
    adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
    spinner.setAdapter( adapter );
    spinner.setSelection( periodIndex );
    spinner.setOnItemSelectedListener( new OnItemSelectedListener() {
      public void onItemSelected( final AdapterView<?> parent, final View v, final int position, final long id ) {
        // set pref
        final long period = periods[position];
        WigleAndroid.info( pref + " setting period: " + period );
        editor.putLong( pref, period );
        editor.commit();
      }
      public void onNothingSelected( final AdapterView<?> arg0 ) {}
      });
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add( 0, MENU_RETURN, 0, "Return" );
      item.setIcon( android.R.drawable.ic_media_previous );
      
      item = menu.add( 0, MENU_ERROR_REPORT, 0, "Error Report" );
      item.setIcon( android.R.drawable.ic_menu_report_image );
      
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_RETURN:
          MainActivity.switchTab( this, MainActivity.TAB_LIST );
          return true;
        case MENU_ERROR_REPORT:
          final Intent errorReportIntent = new Intent( this, ErrorReportActivity.class );
          this.startActivity( errorReportIntent );
          break;
      }
      return false;
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      WigleAndroid.info( "onKeyDown: treating back like home, not quitting app" );
      moveTaskToBack(true);
      if ( getParent() != null ) {
        getParent().moveTaskToBack( true );
      }
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }
  
}
