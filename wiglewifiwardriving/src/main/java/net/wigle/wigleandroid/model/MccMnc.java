package net.wigle.wigleandroid.model;

import java.util.ArrayList;
import java.util.List;

/**
 * PLMN strings stored on a cell. The upload parser reads the digits before the first '_',
 * takes the first three as the MCC, and treats the rest as the literal MNC. It then tries
 * one restored width: a 1-digit MNC as two then three digits, a 2-digit MNC with a leading
 * zero, and a 3-digit MNC that starts with 0 with that zero removed. The literal match wins.
 */
public final class MccMnc {

    private MccMnc() {
    }

    /**
     * Three-digit MCC, or null when the value is unknown or out of range.
     * Integer.MAX_VALUE is the telephony unknown sentinel.
     */
    public static String mccFor(final int mcc) {
        if (mcc <= 0 || mcc >= 1000) {
            return null;
        }
        return pad(mcc, 3);
    }

    /**
     * MNC when the radio only supplied an int, so the width is unknown.
     * 0-99 are sent as two digits ("0" becomes "00", "10" stays "10") and the parser
     * can still try the three-digit form. 100-999 are already three digits.
     * Unknown and out of range return null.
     */
    public static String mncFor(final int mnc) {
        if (mnc < 0 || mnc >= 1000) {
            return null;
        }
        if (mnc > 99) {
            return pad(mnc, 3);
        }
        return pad(mnc, 2);
    }

    /**
     * MCC from the string telephony APIs. Short numeric values are padded to 3 digits.
     */
    public static String normalizeMcc(final String mcc) {
        if (mcc == null) {
            return null;
        }
        final String digits = mcc.trim();
        if (!isDigits(digits) || digits.length() > 3) {
            return null;
        }
        return mccFor(Integer.parseInt(digits));
    }

    /**
     * MNC from the string telephony APIs. Two- and three-digit forms are kept as reported,
     * including leading zeros ("00", "000", "001"). A single digit has no width and is padded to two.
     */
    public static String normalizeMnc(final String mnc) {
        if (mnc == null) {
            return null;
        }
        final String digits = mnc.trim();
        if (!isDigits(digits) || digits.length() > 3) {
            return null;
        }
        if (digits.length() >= 2) {
            return digits;
        }
        return "0" + digits;
    }

    /**
     * Digits before the first '_', when that field is 4 to 6 digits.
     * "310260_253_22671" and "310260" both yield "310260".
     */
    public static String operatorToken(final String operatorId) {
        if (operatorId == null) {
            return null;
        }
        String token = operatorId.trim();
        final int split = token.indexOf('_');
        if (split >= 0) {
            token = token.substring(0, split);
        }
        if (token.length() < 4 || token.length() > 6) {
            return null;
        }
        if (!isDigits(token)) {
            return null;
        }
        return token;
    }

    /**
     * Literal MNC first, then one restored width.
     * 1 digit: "1", "01", "001". 2 digits: "10", "010". 3 digits starting with 0: "001", "01".
     */
    public static List<String> mncCandidates(final String rest) {
        final List<String> candidates = new ArrayList<>();
        candidates.add(rest);
        if (rest.length() == 1) {
            candidates.add("0" + rest);
            candidates.add("00" + rest);
        } else if (rest.length() == 2) {
            candidates.add("0" + rest);
        } else if (rest.charAt(0) == '0') {
            int i = 0;
            while (i < rest.length() - 1 && rest.charAt(i) == '0') {
                i++;
            }
            String stripped = rest.substring(i);
            if (stripped.length() == 1) {
                stripped = "0" + stripped;
            }
            if (!stripped.equals(rest)) {
                candidates.add(stripped);
            }
        }
        return candidates;
    }

    private static String pad(final int value, final int width) {
        final String raw = Integer.toString(value);
        if (raw.length() >= width) {
            return raw;
        }
        final StringBuilder padded = new StringBuilder(width);
        for (int i = raw.length(); i < width; i++) {
            padded.append('0');
        }
        padded.append(raw);
        return padded.toString();
    }

    private static boolean isDigits(final String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
