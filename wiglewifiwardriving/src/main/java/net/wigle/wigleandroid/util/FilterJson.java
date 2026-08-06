package net.wigle.wigleandroid.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.wigle.wigleandroid.model.FilterSet;

/**
 * Gson codec for {@link FilterSet} export / import.
 */
public final class FilterJson {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final Gson PRETTY = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private FilterJson() {
    }

    public static String toJson(final FilterSet filterSet) {
        if (filterSet == null) {
            return "null";
        }
        return GSON.toJson(filterSet);
    }

    public static String toPrettyJson(final FilterSet filterSet) {
        if (filterSet == null) {
            return "null";
        }
        return PRETTY.toJson(filterSet);
    }

    /**
     * Deserialize a filter set. Rejects missing schemaVersion or versions newer than
     * {@link FilterSet#CURRENT_SCHEMA_VERSION}.
     */
    public static FilterSet fromJson(final String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("FilterSet JSON is empty");
        }
        final FilterSet filterSet;
        try {
            filterSet = GSON.fromJson(json, FilterSet.class);
        } catch (final JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid FilterSet JSON", e);
        }
        if (filterSet == null) {
            throw new IllegalArgumentException("FilterSet JSON decoded to null");
        }
        validateSchemaVersion(filterSet);
        filterSet.normalize();
        return filterSet;
    }

    private static void validateSchemaVersion(final FilterSet filterSet) {
        final Integer version = filterSet.getSchemaVersion();
        if (version == null) {
            throw new IllegalArgumentException("FilterSet JSON missing schemaVersion");
        }
        if (version > FilterSet.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported FilterSet schemaVersion " + version
                            + " (current=" + FilterSet.CURRENT_SCHEMA_VERSION + ")");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Invalid FilterSet schemaVersion " + version);
        }
    }
}
