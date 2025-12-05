package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.ui.PrefsBackedCheckbox.BT_SUB_BOX_IDS;
import static net.wigle.wigleandroid.ui.PrefsBackedCheckbox.WIFI_SUB_BOX_IDS;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.wigle.wigleandroid.ui.PrefsBackedCheckbox;
import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.util.FilterUtil;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Building a filter activity for the network list
 * Created by arkasha on 20170801.
 */
public class FilterActivity extends ScreenChildActivity {

    public static final String ADDR_FILTER_MESSAGE = "net.wigle.wigleandroid.filter.MESSAGE";
    public static final String INTENT_DISPLAY_FILTER = "displayFilter";
    public static final String INTENT_LOG_FILTER = "logFilter";
    public static final String INTENT_ALERT_FILTER = "alertFilter";

    public static final String INTENT_BLE_MFGR_ID_ALERT = "bleMfgrAlertFilter";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        setContentView(R.layout.listfiltersettings);

        final androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        View view = findViewById(android.R.id.content);
        EdgeToEdge.enable(this);
        View titleLayout = findViewById(R.id.filter_settings_title);

        if (null != titleLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(titleLayout, new OnApplyWindowInsetsListener() {
                        @Override
                        public @NonNull WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                            final Insets innerPadding = insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars());
                            v.setPadding(
                                    innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                            );
                            return insets;
                        }
                    }
            );
        }

        View bottomToolsLayout = findViewById(R.id.filter_settings_ok);
        if (null != bottomToolsLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomToolsLayout, new OnApplyWindowInsetsListener() {
                @Override
                public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                    final Insets innerPadding = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars() /*TODO:  | cutouts?*/);
                    v.setPadding(
                            innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                    );
                    return insets;
                }
            });
        }

        Logging.info("Filter Fragment Selected");
        final EditText regex = findViewById( R.id.edit_regex );
        final String regexKey = PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_REGEX;
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

        PrefsBackedCheckbox.prefBackedCheckBox(this , view, R.id.showinvert,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_INVERT, false );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showopen,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_OPEN, true, value -> FilterUtil.updateWifiGroupCheckbox(view));
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showwep,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_WEP, true, value -> FilterUtil.updateWifiGroupCheckbox(view));
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showwpa,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_WPA, true, value -> FilterUtil.updateWifiGroupCheckbox(view));
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showcell,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_CELL, true );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.enabled,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_ENABLED, true );
        PrefsBackedCheckbox.prefBackedCheckBox(this, view, R.id.showbtc,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_BT, true, value -> FilterUtil.updateBluetoothGroupCheckbox(view));
        PrefsBackedCheckbox.prefBackedCheckBox(this, view, R.id.showbtle,
                PreferenceKeys.FILTER_PREF_PREFIX + PreferenceKeys.PREF_MAPF_BTLE, true, value -> FilterUtil.updateBluetoothGroupCheckbox(view));

        FilterUtil.updateWifiGroupCheckbox(view);
        FilterUtil.updateBluetoothGroupCheckbox(view);

        final Button filterDisplayButton = view.findViewById(R.id.display_filter_button);
        filterDisplayButton.setOnClickListener(view1 -> {
            final Intent macFilterIntent = new Intent(getApplicationContext(), MacFilterActivity.class );
            macFilterIntent.putExtra(ADDR_FILTER_MESSAGE, INTENT_DISPLAY_FILTER);
            startActivity( macFilterIntent );
        });

        final Button filterLogButton = view.findViewById(R.id.log_filter_button);
        filterLogButton.setOnClickListener(view12 -> {
            final Intent macFilterIntent = new Intent(getApplicationContext(), MacFilterActivity.class );
            macFilterIntent.putExtra(ADDR_FILTER_MESSAGE, INTENT_LOG_FILTER);
            startActivity( macFilterIntent );
        });

        final Button filterAlertButton = view.findViewById(R.id.alert_filter_button);
        filterAlertButton.setOnClickListener(view12 -> {
            final Intent macFilterIntent = new Intent(getApplicationContext(), MacFilterActivity.class );
            macFilterIntent.putExtra(ADDR_FILTER_MESSAGE, INTENT_ALERT_FILTER);
            startActivity( macFilterIntent );
        });

        final Button bleMfgrFilterAlertButton = view.findViewById(R.id.alert_ble_mfgr_filter_button);
        bleMfgrFilterAlertButton.setOnClickListener(view12 -> {
            final Intent macFilterIntent = new Intent(getApplicationContext(), MacFilterActivity.class );
            macFilterIntent.putExtra(ADDR_FILTER_MESSAGE, INTENT_BLE_MFGR_ID_ALERT);
            startActivity( macFilterIntent );
        });

        final Button finishButton = view.findViewById(R.id.finish_filter);
        if (null != finishButton) {
            finishButton.setOnClickListener(v -> finish());
        }
    }
}
