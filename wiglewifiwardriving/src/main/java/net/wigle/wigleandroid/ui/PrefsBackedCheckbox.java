package net.wigle.wigleandroid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.listener.PrefCheckboxListener;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for SharedPreferences-backed checkboxes.
 */
public class PrefsBackedCheckbox {
    public static final List<Integer> WIFI_SUB_BOX_IDS = Arrays.asList(R.id.showwpa, R.id.showwep, R.id.showopen);

    public static CheckBox prefSetCheckBox(final Context context, final View view, final int id,
                                           final String pref, final boolean def, final SharedPreferences prefs) {
        return prefSetCheckBox(context, view, id, pref, def, null);
    }

    public static CheckBox prefSetCheckBox(final Context context, final View view, final int id,
                                           final String pref, final boolean def, final SharedPreferences prefs, final PrefCheckboxListener listener) {
        final CheckBox checkbox = view.findViewById(id);
        if (checkbox != null) {
            final boolean isChecked = prefs.getBoolean(pref, def);
            checkbox.setChecked(isChecked);
            if (null != listener) {
                listener.preferenceSet(isChecked);
            }
        }
        return checkbox;
    }

    private static CheckBox prefSetCheckBox(final SharedPreferences prefs, final View view, final int id, final String pref,
                                            final boolean def) {
        final CheckBox checkbox = view.findViewById(id);
        if (checkbox == null) {
            Logging.error("No checkbox for id: " + id);
        } else {
            checkbox.setChecked(prefs.getBoolean(pref, def));
        }
        return checkbox;
    }

    public static CheckBox prefBackedCheckBox(final Activity activity, final View view, final int id,
                                              final String pref, final boolean def) {
        return prefBackedCheckBox(activity, view, id, pref, def, null);
    }

    public static CheckBox prefBackedCheckBox(final Activity activity, final View view, final int id,
                                              final String pref, final boolean def, final PrefCheckboxListener listener) {
        final SharedPreferences prefs = activity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        final CheckBox checkbox = prefSetCheckBox(prefs, view, id, pref, def);
        if (null != checkbox) {
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                editor.putBoolean(pref, isChecked);
                editor.apply();
                if (null != listener) {
                    listener.preferenceSet(isChecked);
                }
            });
        } else {
            Logging.error("null checkbox - unable to attach listener: "+id);
        }
        return checkbox;
    }

    public static void checkBoxGroupControl(final View view, final int superId, final List<Integer> subIds,
                                            final CompoundButton.OnCheckedChangeListener mListener) {
        final CheckBox superCheckbox = view.findViewById(superId);
        if (null != superCheckbox) {
            List<CheckBox> subCheckboxes = new ArrayList<>();
            for (Integer subId: subIds) {
                CheckBox subBox = view.findViewById(subId);
                if (null != subBox) {
                    subCheckboxes.add(subBox);
                }
            }
            if (!subCheckboxes.isEmpty()) {
                superCheckbox.setOnCheckedChangeListener(null);
                superCheckbox.setChecked(subCheckboxes.stream().allMatch(CheckBox::isChecked));
                superCheckbox.setOnCheckedChangeListener(mListener);
            }
        }
    }
}
