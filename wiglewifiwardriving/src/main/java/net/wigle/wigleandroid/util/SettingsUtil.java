package net.wigle.wigleandroid.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.ui.ThemeUtil;

import java.util.Arrays;

/**
 * User settings manipulation utilities
 * Created by arkasha on 8/7/17.
 */

public class SettingsUtil {
    public static String[] getMapModes(final boolean isAuthed) {
        if (isAuthed) {
            return new String[]{PreferenceKeys.PREF_MAP_NO_TILE,
                    PreferenceKeys.PREF_MAP_ONLYMINE_TILE, PreferenceKeys.PREF_MAP_NOTMINE_TILE,
                    PreferenceKeys.PREF_MAP_ALL_TILE};

        }
        return new String[]{PreferenceKeys.PREF_MAP_NO_TILE,
                PreferenceKeys.PREF_MAP_ALL_TILE};
    }

    public static String[] getMapModeNames(final boolean isAuthed, final Context context) {
        if (isAuthed) {
            return new String[]{ context.getString(R.string.map_none),
                    context.getString(R.string.map_mine), context.getString(R.string.map_not_mine),
                    context.getString(R.string.map_all)};

        }
        return new String[]{ context.getString(R.string.map_none),
                context.getString(R.string.map_all)};
    }

    public static void doScanSpinner( final int id, final String pref, final long spinDefault,
                                final String zeroName, final View view, final Context context ) {
        final String ms = " " + context.getString(R.string.ms_short);
        final String sec = " " + context.getString(R.string.sec);
        final String min = " " + context.getString(R.string.min);

        final Long[] periods = new Long[]{ 0L,50L,250L,500L,750L,1000L,1500L,2000L,3000L,4000L,5000L,10000L,30000L,60000L };
        final String[] periodName = new String[]{ zeroName,"50" + ms,"250" + ms,"500" + ms,"750" + ms,
                "1" + sec,"1.5" + sec,"2" + sec,
                "3" + sec,"4" + sec,"5" + sec,"10" + sec,"30" + sec,"1" + min };
        SettingsUtil.doSpinner(id, view, pref, spinDefault, periods, periodName, context);
    }

    public static <V> void doSpinner(final int id, final View view, final String pref, final V spinDefault,
                               final V[] periods, final String[] periodName, final Context context) {
        doSpinner(view.findViewById(id), pref, spinDefault, periods, periodName, context);
    }

    public static <V> void doSpinner( final Spinner spinner, final String pref, final V spinDefault, final V[] periods,
                                      final String[] periodName, final Context context ) {

        if ( periods.length != periodName.length ) {
            throw new IllegalArgumentException("lengths don't match, periods: " + Arrays.toString(periods)
                    + " periodName: " + Arrays.toString(periodName));
        }

        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item);

        Object period = null;
        if ( periods instanceof Long[] ) {
            period = prefs.getLong( pref, (Long) spinDefault );
        } else if ( periods instanceof String[] ) {
            period = prefs.getString( pref, (String) spinDefault );
        } else if (periods instanceof Integer[] ) {
            period = prefs.getInt( pref, (Integer) spinDefault);
        } else {
            Logging.error("unhandled object type array: " + Arrays.toString(periods) + " class: " + periods.getClass());
        }

        if (period == null) {
            period = periods[0];
        }

        int periodIndex = 0;
        for ( int i = 0; i < periods.length; i++ ) {
            adapter.add( periodName[i] );
            if ( period.equals(periods[i]) ) {
                periodIndex = i;
            }
        }
        adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
        spinner.setAdapter( adapter );
        spinner.setSelection( periodIndex );
        spinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected( final AdapterView<?> parent, final View v, final int position, final long id ) {
                // set pref
                final V period = periods[position];
                // MainActivity.info( pref + " setting period: " + period );
                if ( period instanceof Long ) {
                    editor.putLong( pref, (Long) period );
                } else if ( period instanceof String ) {
                    editor.putString( pref, (String) period );
                } else if (period instanceof Integer) {
                    editor.putInt(pref, (Integer)period);
                } else {
                    Logging.error("unhandled object type: " + period + " class: " + period.getClass());
                }
                editor.apply();

                if ( pref.equals(PreferenceKeys.PREF_LANGUAGE) ) {
                    MainActivity.setLocale( context, context.getResources().getConfiguration() );
                }
                if ( pref.equals(PreferenceKeys.PREF_DAYNIGHT_MODE) ) {
                    ThemeUtil.setTheme(prefs);
                    try {
                        ThemeUtil.setNavTheme(((Activity) v.getContext()).getWindow(), context, prefs);
                    } catch (NullPointerException npe) {
                        //ALIBI: ignorable here.
                    }
                }
            }
            @Override
            public void onNothingSelected( final AdapterView<?> arg0 ) {}
        });
    }

    public static void doMapSpinner( final int spinnerId, final String pref, final String spinDefault, final String[] terms,
                                     final String[] termNames, final Context context, final View view ) {

        if ( terms.length != termNames.length ) {
            throw new IllegalArgumentException("lists don't match: " + Arrays.toString(terms)
                    + " periodName: " + Arrays.toString(termNames));
        }

        Spinner spinner = view.findViewById(spinnerId);
        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item);

        final String term = prefs.getString(pref, spinDefault );

        int termIndex = 0;
        for ( int i = 0; i < terms.length; i++ ) {
            adapter.add( termNames[i] );
            if ( term.equals(terms[i]) ) {
                termIndex = i;
            }
        }
        Logging.info("current selection: "+term +": ("+termIndex+")");
        adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
        spinner.setAdapter( adapter );
        spinner.setSelection( termIndex );
        spinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected( final AdapterView<?> parent, final View v, final int position, final long id ) {
                // set pref
                final String period = terms[position];
                Logging.info( pref + " setting map data: " + period );
                editor.putString( pref, period );
                editor.apply();
                LinearLayout mainLayout = view.findViewById(R.id.show_map_discovered_since);

                if (PreferenceKeys.PREF_MAP_NO_TILE.equals(period)) {
                    mainLayout.setVisibility(View.GONE);
                } else {
                    mainLayout.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onNothingSelected( final AdapterView<?> arg0 ) {}
        });
    }
}
