package net.wigle.wigleandroid.util;

import static net.wigle.wigleandroid.ui.PrefsBackedCheckbox.BT_SUB_BOX_IDS;
import static net.wigle.wigleandroid.ui.PrefsBackedCheckbox.WIFI_SUB_BOX_IDS;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.CheckBox;

import com.google.gson.Gson;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Filter;
import net.wigle.wigleandroid.model.FilterSet;
import net.wigle.wigleandroid.ui.PrefsBackedCheckbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter utilities
 */
public class FilterUtil {
    /** Stored full MAC form, e.g. {@code AA:BB:CC:DD:EE:FF}. Shorter alert-addr entries are OUIs. */
    private static final int FULL_MAC_LENGTH = 17;

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

    /**
     * Build an exportable {@link FilterSet} from alert address / BLE manufacturer-ID prefs.
     * The alerts describe a single way of matching, so the set holds one filter for now, and
     * the caller's name serves as both the set name and that filter's description.
     *
     * @return the filter set, or {@code null} if there are no alert entries to export
     */
    public static FilterSet buildAlertFilterSetFromPrefs(final SharedPreferences prefs,
                                                         final String name) {
        final Filter filter = buildAlertFilterFromPrefs(prefs, name);
        if (filter == null) {
            return null;
        }
        return FilterSet.builder()
                .name(name)
                .addFilter(filter)
                .build();
    }

    /**
     * Build an exportable {@link Filter} from alert address / BLE manufacturer-ID prefs.
     * Full MACs go to {@code macAddresses}, shorter address entries to {@code ouis},
     * and BLE manufacturer IDs to {@code ble.mfgrIds}.
     *
     * @return the filter, or {@code null} if there are no alert entries to export
     */
    public static Filter buildAlertFilterFromPrefs(final SharedPreferences prefs,
                                                   final String description) {
        if (prefs == null) {
            return null;
        }
        final List<String> macAddresses = new ArrayList<>();
        final List<String> ouis = new ArrayList<>();
        for (final String entry : prefStringList(prefs, PreferenceKeys.PREF_ALERT_ADDRS)) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            if (entry.length() == FULL_MAC_LENGTH) {
                macAddresses.add(entry);
            } else {
                ouis.add(entry);
            }
        }
        final List<String> bleMfgrIds = prefStringList(prefs, PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS);

        if (macAddresses.isEmpty() && ouis.isEmpty() && bleMfgrIds.isEmpty()) {
            return null;
        }

        final Filter.Builder builder = Filter.builder()
                .exclude(false);
        if (description != null && !description.isEmpty()) {
            builder.description(description);
        }
        if (!macAddresses.isEmpty()) {
            builder.macAddresses(macAddresses);
        }
        if (!ouis.isEmpty()) {
            builder.ouis(ouis);
        }
        if (!bleMfgrIds.isEmpty()) {
            builder.ble(Filter.BleFilter.builder()
                    .bleMfgrIds(bleMfgrIds)
                    .build());
        }
        return builder.build();
    }

    private static List<String> prefStringList(final SharedPreferences prefs, final String key) {
        final String[] values = new Gson().fromJson(prefs.getString(key, "[]"), String[].class);
        if (values == null || values.length == 0) {
            return new ArrayList<>();
        }
        final List<String> list = new ArrayList<>(values.length);
        for (final String value : values) {
            if (value != null && !value.isEmpty()) {
                list.add(value);
            }
        }
        return list;
    }
}
