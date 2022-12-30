package net.wigle.wigleandroid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.content.SharedPreferences;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

/**
 * filter matchers
 */
public final class FilterMatcher {
    private static boolean isSsidFilterOn( final SharedPreferences prefs, final String prefix ) {
        return prefs.getBoolean( prefix + PreferenceKeys.PREF_MAPF_ENABLED, true );
    }

    private static boolean isBssidFilterOn( final SharedPreferences prefs, final String addressKey) {
        final String f = prefs.getString( addressKey, "");
        if (!"".equals(f) && f.length() > 4 /*ALIBI: json - [''] */) {
            return true;
        }
        return false;
    }

    public static Matcher getSsidFilterMatcher( final SharedPreferences prefs, final String prefix ) {
        final String regex = prefs.getString( prefix + PreferenceKeys.PREF_MAPF_REGEX, "" );
        Matcher matcher = null;
        if ( isSsidFilterOn( prefs, prefix ) && regex != null && ! "".equals(regex) ) {
            try {
                Pattern pattern = Pattern.compile( regex, Pattern.CASE_INSENSITIVE );
                matcher = pattern.matcher( "" );
            }
            catch ( PatternSyntaxException ex ) {
                Logging.error("regex pattern exception: " + ex);
            }
        }

        return matcher;
    }


    public static boolean isOk(final Matcher ssidMatcher, final Matcher bssidMatcher,
                               final SharedPreferences prefs, final String prefix, final Network network ) {

        /*
         * ALIBI: shouldn't be necessary, but seeing null network reports.
         */
        if (network == null) {
            return false;
        }

        if ( isSsidFilterOn( prefs, prefix ) ) {
            if (ssidMatcher != null) {
                try {
                    final String ssid = network.getSsid();
                    ssidMatcher.reset(ssid);
                    final boolean invert = prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_INVERT, false);
                    final boolean matches = ssidMatcher.find();
                    if (!matches && !invert) {
                        return false;
                    } else if (matches && invert) {
                        return false;
                    }
                } catch (IllegalArgumentException iaex) {
                    Logging.warn("Matcher: IllegalArgument: " + network.getSsid() + "pattern: " + ssidMatcher.pattern());
                    final boolean invert = prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_INVERT, false);
                    return !invert;
                }
            }

            if ( NetworkType.WIFI.equals( network.getType() ) ) {
                switch (network.getCrypto()) {
                    case Network.CRYPTO_NONE:
                        if (!prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_OPEN, true)) {
                            return false;
                        }
                        break;
                    case Network.CRYPTO_WEP:
                        if (!prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_WEP, true)) {
                            return false;
                        }
                        break;
                    case Network.CRYPTO_WPA:
                    case Network.CRYPTO_WPA2:
                    case Network.CRYPTO_WPA3:
                        if (!prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_WPA, true)) {
                            return false;
                        }
                        break;
                    default:
                        Logging.error("unhandled crypto: " + network);
                }
            } else if (NetworkType.BT.equals(network.getType())) {
                if (!prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_BT, true)) {
                    return false;
                }
            } else if (NetworkType.BLE.equals(network.getType()) ) {
                if (!prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_BTLE, true)) {
                    return false;
                }
            } else if (!prefs.getBoolean(prefix + PreferenceKeys.PREF_MAPF_CELL, true)) {
                return false;
            }
        }

        if (isBssidFilterOn(prefs, PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS)) {
            if (bssidMatcher != null) { //ALIBI: fallthrough on Map call, since we're not applying this there?
                try {
                    final String bssid = network.getBssid();
                    bssidMatcher.reset(bssid);
                    final boolean matches = bssidMatcher.find();
                    if (matches) {
                        return false;
                    }
                } catch (IllegalArgumentException iaex) {
                    Logging.warn("Matcher: IllegalArgument: " + network.getBssid() + "pattern: " + bssidMatcher.pattern());
                    return true;
                }
            }
        }
        return true;
    }

}
