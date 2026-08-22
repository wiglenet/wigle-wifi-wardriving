package net.wigle.wigleandroid.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Top-level document for filter export / import: a named, ordered set of {@link Filter}s.
 * The schema version covers the whole document, so it lives here rather than on each filter.
 * Serialize/deserialize via {@link net.wigle.wigleandroid.util.FilterJson}.
 *
 * <p>Sample JSON payload:
 * <pre>{@code
 *
 * {
 *   "schemaVersion": 1,
 *   "name": "a composable, ordered set of filters",
 *   "filters": [
 *     {
 *       "description": "exclude AirTags",
 *       "exclude": true,
 *       "macAddresses": ["AA:BB:CC:DD:EE:FF"],
 *       "ouis": ["AA:BB:CC"],
 *       "ble": {
 *         "mfgrIds": ["0x004C"],
 *          "service": {
 *            "any": [
 *             { "description": "Find My", "uuid": "FD6F" }
 *            ],
 *            "all": {
 *              "groupKey": ["uuid-a", "uuid-b"]
 *            }
 *          }
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * v0.0.1
 * @author rksh
 */
public class FilterSet {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @SerializedName("schemaVersion")
    private Integer schemaVersion;

    private String name; // text name of this set of filters

    private List<Filter> filters;

    /** No-arg for Gson / deserialization. Prefer {@link #builder()}. */
    public FilterSet() {
    }

    private FilterSet(final Builder builder) {
        this.schemaVersion = builder.schemaVersion != null
                ? builder.schemaVersion : CURRENT_SCHEMA_VERSION;
        this.name = builder.name;
        this.filters = builder.filters == null
                ? null : Collections.unmodifiableList(new ArrayList<>(builder.filters));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public String getName() {
        return name;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    /** Light post-deserialize cleanup; leaves a null filter list as null. */
    public void normalize() {
        if (schemaVersion == null) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        if (filters != null) {
            for (final Filter filter : filters) {
                if (filter != null) {
                    filter.normalize();
                }
            }
        }
    }

    /**
     * Fluent builder for {@link FilterSet}.
     */
    public static final class Builder {
        private Integer schemaVersion = CURRENT_SCHEMA_VERSION;
        private String name;
        private List<Filter> filters;

        public Builder schemaVersion(final Integer schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder filters(final Collection<Filter> filters) {
            this.filters = filters == null ? null : new ArrayList<>(filters);
            return this;
        }

        public Builder filters(final Filter... filters) {
            if (filters == null) {
                this.filters = null;
                return this;
            }
            return filters(Arrays.asList(filters));
        }

        public Builder addFilter(final Filter filter) {
            if (filter == null) {
                return this;
            }
            if (this.filters == null) {
                this.filters = new ArrayList<>();
            }
            this.filters.add(filter);
            return this;
        }

        public Builder addFilters(final Collection<Filter> filters) {
            if (filters == null) {
                return this;
            }
            for (final Filter filter : filters) {
                addFilter(filter);
            }
            return this;
        }

        public Builder addFilters(final Filter... filters) {
            if (filters == null) {
                return this;
            }
            return addFilters(Arrays.asList(filters));
        }

        public FilterSet build() {
            return new FilterSet(this);
        }
    }
}
