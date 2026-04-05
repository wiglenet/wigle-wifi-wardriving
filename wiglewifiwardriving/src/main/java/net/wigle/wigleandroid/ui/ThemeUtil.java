package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;
import android.view.Window;

import androidx.appcompat.app.AppCompatDelegate;

//import com.google.android.gms.maps.GoogleMap;
//import com.google.android.gms.maps.model.MapStyleOptions;

import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

public class ThemeUtil {

    /**
     * Apply AppCompat day/night on the main thread. Direct UI elements must invoke this synchronously on callback.
     */
    public static void setTheme(final SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT > 28) {
            final int displayMode = prefs.getInt(PreferenceKeys.PREF_DAYNIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES);
            Logging.info("set theme called: " + displayMode);
            AppCompatDelegate.setDefaultNightMode(displayMode);
        } else {
            //Force night mode
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    public static void setNavTheme(final Window w, final Context c, final SharedPreferences prefs) {
        if (w == null || c == null) {
            return;
        }
        final int displayMode = prefs.getInt(PreferenceKeys.PREF_DAYNIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES);
        final int nightModeFlags = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (AppCompatDelegate.MODE_NIGHT_YES == displayMode ||
                (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM == displayMode &&
                        nightModeFlags == Configuration.UI_MODE_NIGHT_YES)) {
            w.setNavigationBarColor(0x80000000);
        } else {
            final TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.navigationBarColor, tv, true)) {
                w.setNavigationBarColor(tv.data);
            }
        }
    }

//    public static void setMapTheme(final GoogleMap googleMap, final Context c, final SharedPreferences prefs, final int mapNightThemeId) {
//        if (shouldUseMapNightMode(c, prefs)) {
//            try {
//                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(c, mapNightThemeId));
//            } catch (Resources.NotFoundException e) {
//                Logging.error("Unable to theme map: ", e);
//            }
//        }
//    }

    public static boolean shouldUseMapNightMode(final Context c, final SharedPreferences prefs) {
        final boolean mapsMatchMode = prefs.getBoolean(PreferenceKeys.PREF_MAPS_FOLLOW_DAYNIGHT, false);
        if (mapsMatchMode) {
            final int displayMode = prefs.getInt(PreferenceKeys.PREF_DAYNIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES);
            final int nightModeFlags = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return AppCompatDelegate.MODE_NIGHT_YES == displayMode ||
                    (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM == displayMode &&
                            nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
        }
        return false;
    }
}
