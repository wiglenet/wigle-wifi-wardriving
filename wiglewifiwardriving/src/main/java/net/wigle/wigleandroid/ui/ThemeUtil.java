package net.wigle.wigleandroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatDelegate;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;

public class ThemeUtil {
    public static void setTheme(final SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT > 28) {
            final int displayMode = prefs.getInt(ListFragment.PREF_DAYNIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES);
            MainActivity.info("set theme called: "+displayMode);
            AppCompatDelegate.setDefaultNightMode(displayMode);
        } else {
            //Force night mode
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    public static void setNavTheme(final Window w, final Context c, final SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final int displayMode = prefs.getInt(ListFragment.PREF_DAYNIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES);
            final int nightModeFlags = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (AppCompatDelegate.MODE_NIGHT_YES == displayMode ||
                    (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM == displayMode &&
                            nightModeFlags == Configuration.UI_MODE_NIGHT_YES)) {
                w.setNavigationBarColor(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            }
        }
    }

}
