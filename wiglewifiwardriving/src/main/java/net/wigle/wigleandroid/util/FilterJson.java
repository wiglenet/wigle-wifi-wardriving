package net.wigle.wigleandroid.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.wigle.wigleandroid.model.Filter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gson codec for {@link Filter} export / import.
 */
public final class FilterJson {
    private static final Type FILTER_LIST_TYPE = new TypeToken<List<Filter>>() {}.getType();

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final Gson PRETTY = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private FilterJson() {
    }

    public static String toJson(final Filter filter) {
        if (filter == null) {
            return "null";
        }
        return GSON.toJson(filter);
    }

    public static String toPrettyJson(final Filter filter) {
        if (filter == null) {
            return "null";
        }
        return PRETTY.toJson(filter);
    }

    public static String toJsonList(final List<Filter> filters) {
        if (filters == null) {
            return "null";
        }
        return GSON.toJson(filters, FILTER_LIST_TYPE);
    }

    public static String toPrettyJsonList(final List<Filter> filters) {
        if (filters == null) {
            return "null";
        }
        return PRETTY.toJson(filters, FILTER_LIST_TYPE);
    }

    /**
     * Deserialize a single filter. Rejects missing schemaVersion or versions newer than
     * {@link Filter#CURRENT_SCHEMA_VERSION}.
     */
    public static Filter fromJson(final String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Filter JSON is empty");
        }
        final Filter filter;
        try {
            filter = GSON.fromJson(json, Filter.class);
        } catch (final JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid Filter JSON", e);
        }
        if (filter == null) {
            throw new IllegalArgumentException("Filter JSON decoded to null");
        }
        validateSchemaVersion(filter);
        filter.normalize();
        return filter;
    }

    /**
     * Deserialize a JSON array of filters. Each element is validated like {@link #fromJson(String)}.
     */
    public static List<Filter> fromJsonList(final String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Filter JSON list is empty");
        }
        final List<Filter> filters;
        try {
            filters = GSON.fromJson(json, FILTER_LIST_TYPE);
        } catch (final JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid Filter JSON list", e);
        }
        if (filters == null) {
            throw new IllegalArgumentException("Filter JSON list decoded to null");
        }
        final List<Filter> validated = new ArrayList<>(filters.size());
        for (final Filter filter : filters) {
            if (filter == null) {
                throw new IllegalArgumentException("Filter JSON list contains null element");
            }
            validateSchemaVersion(filter);
            filter.normalize();
            validated.add(filter);
        }
        return Collections.unmodifiableList(validated);
    }

    private static void validateSchemaVersion(final Filter filter) {
        final Integer version = filter.getSchemaVersion();
        if (version == null) {
            throw new IllegalArgumentException("Filter JSON missing schemaVersion");
        }
        if (version > Filter.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Filter schemaVersion " + version
                            + " (current=" + Filter.CURRENT_SCHEMA_VERSION + ")");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Invalid Filter schemaVersion " + version);
        }
    }
}
