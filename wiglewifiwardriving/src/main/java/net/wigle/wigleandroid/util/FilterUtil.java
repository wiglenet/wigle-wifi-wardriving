package net.wigle.wigleandroid.util;

import static net.wigle.wigleandroid.ui.PrefsBackedCheckbox.BT_SUB_BOX_IDS;
import static net.wigle.wigleandroid.ui.PrefsBackedCheckbox.WIFI_SUB_BOX_IDS;

import android.view.View;
import android.widget.CheckBox;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.ui.PrefsBackedCheckbox;

/**
 * Filter utilities
 */
public class FilterUtil {
    public static void updateWifiGroupCheckbox(final View view) {
        PrefsBackedCheckbox.checkBoxGroupControl(view, R.id.showwifi,
                WIFI_SUB_BOX_IDS,
                (compoundButton, checked) -> {
                    for (int subBoxId: WIFI_SUB_BOX_IDS) {
                        final CheckBox checkSubItem = view.findViewById(subBoxId);
                        if (null != checkSubItem) {
                            checkSubItem.setChecked(checked);
                        }
                    }
                });
    }

    public static void updateBluetoothGroupCheckbox(final View view) {
        PrefsBackedCheckbox.checkBoxGroupControl(view, R.id.showbt,
                BT_SUB_BOX_IDS,
                (compoundButton, checked) -> {
                    for (int subBoxId: BT_SUB_BOX_IDS) {
                        final CheckBox checkSubItem = view.findViewById(subBoxId);
                        if (null != checkSubItem) {
                            checkSubItem.setChecked(checked);
                        }
                    }
                });
    }
}
