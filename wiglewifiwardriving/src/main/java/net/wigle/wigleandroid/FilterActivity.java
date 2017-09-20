package net.wigle.wigleandroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

/**
 * Building a filter activity for the network list
 * Created by arkasha on 20170801.
 */

@SuppressWarnings("deprecation")
public class FilterActivity extends AppCompatActivity {

    public static final String ADDR_FILTER_MESSAGE = "net.wigle.wigleandroid.filter.MESSAGE";
    public static final String INTENT_DISPLAY_FILTER = "displayFilter";
    public static final String INTENT_LOG_FILTER = "logFilter";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = this.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        setContentView(R.layout.filtersettings);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            final android.support.v7.app.ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
        View view = findViewById(android.R.id.content);
        MainActivity.info("Filter Fragment Selected");
        final EditText regex = (EditText) findViewById( R.id.edit_regex );
        final String regexKey = ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_REGEX;
        regex.setText( prefs.getString(regexKey, "") );

        regex.addTextChangedListener( new SettingsFragment.SetWatcher() {
            @Override
            public void onTextChanged( final String s ) {
                //DEBUG: MainActivity.info("regex update: "+s);
                String currentValue = prefs.getString(regexKey, "");
                if (currentValue.equals(s.trim())) {
                    return;
                }
                if (s.trim().isEmpty()) {
                    //ALIBI: empty values should unset
                    editor.remove(regexKey);
                } else {
                    editor.putString(regexKey, s.trim());
                }
                editor.apply();
            }
        });

        MainActivity.prefBackedCheckBox(this , view, R.id.showinvert,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_INVERT, false );
        MainActivity.prefBackedCheckBox( this, view, R.id.showopen,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_OPEN, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showwep,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_WEP, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showwpa,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_WPA, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showcell,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_CELL, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.enabled,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_ENABLED, true );

        final Button filter_display_button = (Button) view.findViewById(R.id.display_filter_button);
        filter_display_button.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( final View view ) {
                final Intent macFilterIntent = new Intent(getApplicationContext(), MacFilterActivity.class );
                macFilterIntent.putExtra(ADDR_FILTER_MESSAGE, INTENT_DISPLAY_FILTER);
                startActivity( macFilterIntent );
            }
        });

        final Button filter_log_button = (Button) view.findViewById(R.id.log_filter_button);
        filter_log_button.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( final View view ) {
                final Intent macFilterIntent = new Intent(getApplicationContext(), MacFilterActivity.class );
                macFilterIntent.putExtra(ADDR_FILTER_MESSAGE, INTENT_LOG_FILTER);
                startActivity( macFilterIntent );
            }
        });

    }
}
