package net.wigle.wigleandroid;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.SettingsUtil;

import java.util.Objects;

public class SpeechActivity extends ScreenChildActivity {
    private static final int MENU_RETURN = 12;

    // used for shutting extraneous activities down on an error
    public static SpeechActivity speechActivity;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // set language
        MainActivity.setLocale( this );
        setContentView( R.layout.speech );
        speechActivity = this;
        EdgeToEdge.enable(this);
        View wrapperLayout = findViewById(R.id.speech_content_wrapper);
        if (null != wrapperLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(wrapperLayout, new OnApplyWindowInsetsListener() {
                        @Override
                        public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                            final Insets innerPadding = insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars() |
                                            WindowInsetsCompat.Type.displayCutout());
                            v.setPadding(
                                    innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                            );
                            return insets;
                        }
                    }
            );
        }

        // force media volume controls
        this.setVolumeControlStream( AudioManager.STREAM_MUSIC );

        final SharedPreferences prefs = this.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0);
        doTtsCheckbox( prefs, R.id.speech_gps, PreferenceKeys.PREF_SPEECH_GPS );
        doTtsCheckbox( prefs, R.id.speech_run, PreferenceKeys.PREF_SPEAK_RUN );
        doTtsCheckbox( prefs, R.id.speech_new_wifi, PreferenceKeys.PREF_SPEAK_NEW_WIFI );
        doTtsCheckbox( prefs, R.id.speech_new_cell, PreferenceKeys.PREF_SPEAK_NEW_CELL );
        doTtsCheckbox( prefs, R.id.speech_new_bt, PreferenceKeys.PREF_SPEAK_NEW_BT );
        doTtsCheckbox( prefs, R.id.speech_queue, PreferenceKeys.PREF_SPEAK_QUEUE );
        doTtsCheckbox( prefs, R.id.speech_miles, PreferenceKeys.PREF_SPEAK_MILES );
        doTtsCheckbox( prefs, R.id.speech_time, PreferenceKeys.PREF_SPEAK_TIME );
        doTtsCheckbox( prefs, R.id.speech_battery, PreferenceKeys.PREF_SPEAK_BATTERY );
        doTtsCheckbox( prefs, R.id.speech_ssid, PreferenceKeys.PREF_SPEAK_SSID, false );
        doTtsCheckbox( prefs, R.id.speech_wifi_restart, PreferenceKeys.PREF_SPEAK_WIFI_RESTART );

        // speech spinner
        Spinner spinner = findViewById(R.id.speak_spinner );
        //TODO: this may no longer be necessary
        if (MainActivity.getMainActivity() == null || Objects.requireNonNull(MainActivity.getStaticState()).tts == null) {
            // no text to speech :(
            spinner.setEnabled( false );
            final TextView speakText = findViewById(R.id.speak_text );
            speakText.setText(getString(R.string.no_tts));
        }

        final String off = getString(R.string.off);
        final String sec = " " + getString(R.string.sec);
        final String min = " " + getString(R.string.min);
        final Long[] speechPeriods = new Long[]{ 10L,15L,30L,60L,120L,300L,600L,900L,1800L,0L };
        final String[] speechName = new String[]{ "10" + sec,"15" + sec,"30" + sec,
                "1" + min,"2" + min,"5" + min,"10" + min,"15" + min,"30" + min, off };
        SettingsUtil.doSpinner(findViewById(R.id.speak_spinner), PreferenceKeys.PREF_SPEECH_PERIOD,
                MainActivity.DEFAULT_SPEECH_PERIOD, speechPeriods, speechName, this);

        Button speechSettingsFinished = findViewById(R.id.finish_speech_settings);
        if (null != speechSettingsFinished) {
            speechSettingsFinished.setOnClickListener(v-> finish());
       }
    }

    @Override
    public void onDestroy() {
        speechActivity = null;
        super.onDestroy();
    }

    private void doTtsCheckbox(final SharedPreferences prefs, final int id, final String pref ) {
        doTtsCheckbox( prefs, id, pref, true );
    }

    private void doTtsCheckbox(final SharedPreferences prefs, final int id, final String pref, final boolean defaultVal ) {
        final CheckBox box = findViewById( id );
        box.setChecked( prefs.getBoolean( pref, defaultVal ) );
        box.setOnCheckedChangeListener((buttonView, isChecked) -> {
            final Editor editor = prefs.edit();
            editor.putBoolean( pref, isChecked );
            editor.apply();
            if (!isChecked) {
                MainActivity m = MainActivity.getMainActivity();
                if (null != m && !m.isFinishing()) {
                    m.interruptSpeak();
                }
            }
        });
    }

    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
        MenuItem item = menu.add(0, MENU_RETURN, 0, getString(R.string.menu_return));
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
