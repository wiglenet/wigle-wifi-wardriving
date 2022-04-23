package net.wigle.wigleandroid.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quick and dirty line-based CSV to string array mapper.
 */
public class CsvUtil {
    public static final char COMMA = ',';
    public static final char QUOTE = '"';
    public static final char CR = '\r';
    public static final char LF = '\n';

    private static final SimpleDateFormat CSV_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static String[] lineToArray(final String line) {
        List<String> a = new ArrayList<>();
        String token = "";
        boolean quoteOpen = false;
        for (char c:line.toCharArray()) {
            switch(c) {
                case LF: case CR:
                    quoteOpen = false;
                    if (!token.isEmpty()) {
                        a.add(token);
                    }
                    token = "";
                    break;
                case QUOTE:
                    quoteOpen = !quoteOpen;
                    break;
                case COMMA:
                    if (!quoteOpen) {
                        a.add(token);
                        token = "";
                    } else {
                        token += c;
                    }
                    break;
                default:
                    token += c;
                    break;
            }
        }
        if (!token.isEmpty()) {
            a.add(token);
        }
        return a.toArray(new String[0]);
    }

    public static long getUnixtimeForString(final String timestamp) {
        try {
            return CSV_TIME_FORMAT.parse(timestamp).getTime();
        } catch (ParseException | NullPointerException e) {
            Logging.error("Failed to parse CSV time: "+timestamp, e);
        }
        return 0;
    }
}
