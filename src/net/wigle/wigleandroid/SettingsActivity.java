package net.wigle.wigleandroid;

import net.wigle.wigleandroid.MainActivity.Doer;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
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
  
  private static final int MENU_EXIT = 11;
  private static final int MENU_LIST = 12;
  private static final int MENU_ERROR_REPORT = 13;
  
  /** convenience, just get the darn new string */
  public static abstract class SetWatcher implements TextWatcher {
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
      final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
      final Editor editor = prefs.edit();
      
      // donate
      final CheckBox donate = (CheckBox) findViewById(R.id.donate);
      final boolean isDonate = prefs.getBoolean( ListActivity.PREF_DONATE, false);
      
      donate.setChecked( isDonate );
      if ( isDonate ) {
        eraseDonate();
      }
      donate.setOnCheckedChangeListener(new OnCheckedChangeListener() {
          public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) { 
            if ( isChecked == prefs.getBoolean( ListActivity.PREF_DONATE, false) ) {
              // this would cause no change, bail
              return;
            }
            
            if ( isChecked ) {
              // turn off until confirmed
              buttonView.setChecked( false );
              // confirm
              MainActivity.createConfirmation( SettingsActivity.this, 
                  "Donate data to WiGLE?\n\n"
                  + "Allow WiGLE to make an anonymized copy of data which WiGLE is authorized to license commercially to other parties.", 
                  new Doer() {
                @Override
                public void execute() {
                  editor.putBoolean( ListActivity.PREF_DONATE, isChecked );
                  editor.commit();
                                    
                  buttonView.setChecked( true );
                  // poof
                  eraseDonate();
                }
              });
            }
            else {
              editor.putBoolean( ListActivity.PREF_DONATE, isChecked );
              editor.commit();
            }
          }
        });
      
      // anonymous
      final CheckBox beAnonymous = (CheckBox) findViewById(R.id.be_anonymous);
      final EditText user = (EditText) findViewById(R.id.edit_username);
      final EditText pass = (EditText) findViewById(R.id.edit_password);
      final boolean isAnonymous = prefs.getBoolean( ListActivity.PREF_BE_ANONYMOUS, false);
      if ( isAnonymous ) {
        user.setEnabled( false );
        pass.setEnabled( false );
      }
      
      beAnonymous.setChecked( isAnonymous );
      beAnonymous.setOnCheckedChangeListener(new OnCheckedChangeListener() {
          public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) { 
            if ( isChecked == prefs.getBoolean( ListActivity.PREF_BE_ANONYMOUS, false) ) {
              // this would cause no change, bail
              return;
            }
            
            if ( isChecked ) {
              // turn off until confirmed
              buttonView.setChecked( false );
              // confirm
              MainActivity.createConfirmation( SettingsActivity.this, "Upload anonymously?", new Doer() {
                @Override
                public void execute() {
                  // turn anonymous
                  user.setEnabled( false );
                  pass.setEnabled( false );
                  
                  editor.putBoolean( ListActivity.PREF_BE_ANONYMOUS, isChecked );
                  editor.commit();
                  
                  buttonView.setChecked( true );
                  
                  // might have to remove or show register link
                  updateRegister();
                }
              });
            }
            else {
              // unset anonymous
              user.setEnabled( true );
              pass.setEnabled( true );
              
              editor.putBoolean( ListActivity.PREF_BE_ANONYMOUS, isChecked );
              editor.commit();
              
              // might have to remove or show register link
              updateRegister();
            }            
          }
        });
      
      // register link
      final TextView register = (TextView) findViewById(R.id.register);
      register.setText(Html.fromHtml("<a href='http://wigle.net/gps/gps/main/register'>Register</a>"
          + " at <a href='http://wigle.net/gps/gps/main/register'>WiGLE.net</a>"));
      register.setMovementMethod(LinkMovementMethod.getInstance());
      updateRegister();
            
      user.setText( prefs.getString( ListActivity.PREF_USERNAME, "" ) );
      user.addTextChangedListener( new SetWatcher() {
        public void onTextChanged( final String s ) {
          // ListActivity.debug("user: " + s);
          editor.putString( ListActivity.PREF_USERNAME, s.trim() );
          editor.commit();
          // might have to remove or show register link
          updateRegister();
        } 
      });
      
      final CheckBox showPassword = (CheckBox) findViewById(R.id.showpassword);
      showPassword.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) { 
          if ( isChecked ) {
            pass.setTransformationMethod(SingleLineTransformationMethod.getInstance());
          }
          else {
            pass.setTransformationMethod(PasswordTransformationMethod.getInstance());
          }
        }
      });
      
      pass.setText( prefs.getString( ListActivity.PREF_PASSWORD, "" ) );
      pass.addTextChangedListener( new SetWatcher() {
        public void onTextChanged( final String s ) {
          // ListActivity.debug("pass: " + s);
          editor.putString( ListActivity.PREF_PASSWORD, s.trim() );
          editor.commit();
        } 
      });
      
      final Button button = (Button) findViewById( R.id.speech_button );
      button.setOnClickListener( new OnClickListener() {
          public void onClick( final View view ) {
            final Intent errorReportIntent = new Intent( SettingsActivity.this, SpeechActivity.class );
            SettingsActivity.this.startActivity( errorReportIntent );
          }
        });
      
      final Button kmlRunExportButton = (Button) findViewById( R.id.kml_run_export_button );
      kmlRunExportButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {  
          MainActivity.createConfirmation( SettingsActivity.this, "Export run to KML?", new Doer() {
            @Override
            public void execute() {
              // actually need this Activity context, for dialogs
              KmlWriter kmlWriter = new KmlWriter( SettingsActivity.this, ListActivity.lameStatic.dbHelper, 
                  ListActivity.lameStatic.runNetworks );
              kmlWriter.start();
            }
          } );
        }
      });
      
      final Button kmlExportButton = (Button) findViewById( R.id.kml_export_button );
      kmlExportButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {  
          MainActivity.createConfirmation( SettingsActivity.this, "Export DB to KML?", new Doer() {
            @Override
            public void execute() {
              // actually need this Activity context, for dialogs
              KmlWriter kmlWriter = new KmlWriter( SettingsActivity.this, ListActivity.lameStatic.dbHelper );
              kmlWriter.start();
            }
          } );
        }
      });
      
      // db marker reset button and text
      final TextView tv = (TextView) findViewById( R.id.reset_maxid_text );
      tv.setText( "Highest uploaded id: " + prefs.getLong( ListActivity.PREF_DB_MARKER, 0L ) );
      
      final Button resetMaxidButton = (Button) findViewById( R.id.reset_maxid_button );
      resetMaxidButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {    
          MainActivity.createConfirmation( SettingsActivity.this, "Zero out DB marker?", new Doer() {
            @Override
            public void execute() {          
              editor.putLong( ListActivity.PREF_DB_MARKER, 0L );
              editor.commit();
              tv.setText( "Max upload id: 0" );
            }
          } );
        }
      });
      
      // db marker maxout button and text
      final TextView maxtv = (TextView) findViewById( R.id.maxout_maxid_text );
      final long maxDB = prefs.getLong( ListActivity.PREF_MAX_DB, 0L );
      maxtv.setText( "Max id at startup: " + maxDB );
      
      final Button maxoutMaxidButton = (Button) findViewById( R.id.maxout_maxid_button );
      maxoutMaxidButton.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) { 
          MainActivity.createConfirmation( SettingsActivity.this, "Max out DB marker?", new Doer() {
            @Override
            public void execute() {
              editor.putLong( ListActivity.PREF_DB_MARKER, maxDB );
              editor.commit();
              // set the text on the other button
              tv.setText( "Max upload id: " + maxDB );
            } 
          } );
        }
      } );
      
      // period spinners
      doScanSpinner( R.id.periodstill_spinner, 
          ListActivity.PREF_SCAN_PERIOD_STILL, ListActivity.SCAN_STILL_DEFAULT, "Nonstop" );
      doScanSpinner( R.id.period_spinner, 
          ListActivity.PREF_SCAN_PERIOD, ListActivity.SCAN_DEFAULT, "Nonstop" );
      doScanSpinner( R.id.periodfast_spinner, 
          ListActivity.PREF_SCAN_PERIOD_FAST, ListActivity.SCAN_FAST_DEFAULT, "Nonstop" );
      doScanSpinner( R.id.gps_spinner, 
          ListActivity.GPS_SCAN_PERIOD, ListActivity.LOCATION_UPDATE_INTERVAL, "Tie to Wifi Scan Period" );
      
      MainActivity.prefBackedCheckBox(this, R.id.edit_showcurrent, ListActivity.PREF_SHOW_CURRENT, true);
      MainActivity.prefBackedCheckBox(this, R.id.use_metric, ListActivity.PREF_METRIC, false);
      MainActivity.prefBackedCheckBox(this, R.id.found_sound, ListActivity.PREF_FOUND_SOUND, true);
      MainActivity.prefBackedCheckBox(this, R.id.found_new_sound, ListActivity.PREF_FOUND_NEW_SOUND, true);
      MainActivity.prefBackedCheckBox(this, R.id.speech_gps, ListActivity.PREF_SPEECH_GPS, true);
      
      // speech spinner
      Spinner spinner = (Spinner) findViewById( R.id.speak_spinner );
      if ( ! TTS.hasTTS() ) {
        // no text to speech :(
        spinner.setEnabled( false );
        final TextView speakText = (TextView) findViewById( R.id.speak_text );
        speakText.setText("No Text-to-Speech engine");
      }
      final long[] speechPeriods = new long[]{ 10,15,30,60,120,300,600,900,1800,0 };
      final String[] speechName = new String[]{ "10 sec","15 sec","30 sec",
          "1 min","2 min","5 min","10 min","15 min","30 min","Off" };
      doSpinner( R.id.speak_spinner, 
          ListActivity.PREF_SPEECH_PERIOD, ListActivity.DEFAULT_SPEECH_PERIOD, speechPeriods, speechName );      
            
      // battery kill spinner
      final long[] batteryPeriods = new long[]{ 1,2,3,4,5,10,15,20,0 };
      final String[] batteryName = new String[]{ "1 %","2 %","3 %","4 %","5 %","10 %","15 %","20 %","Off" };
      doSpinner( R.id.battery_kill_spinner, 
          ListActivity.PREF_BATTERY_KILL_PERCENT, ListActivity.DEFAULT_BATTERY_KILL_PERCENT, batteryPeriods, batteryName );   
      
      // reset wifi spinner
      final long[] resetPeriods = new long[]{ 30000,60000,90000,120000,300000,600000,0 };
      final String[] resetName = new String[]{ "30 sec","1 min","1.5 min",
          "2 min","5 min","10 min","Off" };
      doSpinner( R.id.reset_wifi_spinner, 
          ListActivity.PREF_RESET_WIFI_PERIOD, ListActivity.DEFAULT_RESET_WIFI_PERIOD, resetPeriods, resetName );      
  }
  
  @Override
  public void onResume() {
    ListActivity.info( "resume settings." );
    
    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    // donate
    final boolean isDonate = prefs.getBoolean( ListActivity.PREF_DONATE, false);
    if ( isDonate ) {
      eraseDonate();
    }
    
    super.onResume();
  }
  
  private void updateRegister() {
    final TextView register = (TextView) findViewById(R.id.register);
    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final String username = prefs.getString( ListActivity.PREF_USERNAME, "" );
    final boolean isAnonymous = prefs.getBoolean( ListActivity.PREF_BE_ANONYMOUS, false);
    if ( "".equals(username) || isAnonymous ) {
      register.setEnabled( true );
      register.setVisibility( View.VISIBLE );        
    }
    else {
      // poof
      register.setEnabled( false );
      register.setVisibility( View.GONE );
    }    
  }
  
  private void eraseDonate() {
    final CheckBox donate = (CheckBox) findViewById(R.id.donate);
    donate.setEnabled(false);
    donate.setVisibility(View.GONE);
  }
  
  private void doScanSpinner( final int id, final String pref, final long spinDefault, final String zeroName ) {
    final long[] periods = new long[]{ 0,50,250,500,750,1000,1500,2000,3000,4000,5000,10000,30000,60000 };
    final String[] periodName = new String[]{ zeroName,"50ms","250ms","500 ms","750 ms","1 sec","1.5 sec","2 sec",
        "3 sec","4 sec","5 sec","10 sec","30 sec","1 min" };
    doSpinner(id, pref, spinDefault, periods, periodName);
  }
  
  private void doSpinner( final int id, final String pref, final long spinDefault, 
      final long[] periods, final String[] periodName ) {
    
    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final Editor editor = prefs.edit();
    
    Spinner spinner = (Spinner) findViewById( id );
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
        this, android.R.layout.simple_spinner_item);
    
    long period = prefs.getLong( pref, spinDefault );
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
        ListActivity.info( pref + " setting scan period: " + period );
        editor.putLong( pref, period );
        editor.commit();
      }
      public void onNothingSelected( final AdapterView<?> arg0 ) {}
      });
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add( 0, MENU_EXIT, 0, "Exit" );
      item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
        
      item = menu.add( 0, MENU_LIST, 0, "List" );
      item.setIcon( android.R.drawable.ic_menu_sort_by_size );
      
      item = menu.add( 0, MENU_ERROR_REPORT, 0, "Error Report" );
      item.setIcon( android.R.drawable.ic_menu_report_image );
      
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT:
          MainActivity.finishListActivity( this );
          finish();
          return true;
        case MENU_LIST:
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
      ListActivity.info( "onKeyDown: not quitting app on back" );
      MainActivity.switchTab( this, MainActivity.TAB_LIST );
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }
  
}
