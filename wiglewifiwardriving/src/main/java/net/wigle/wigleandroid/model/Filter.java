package net.wigle.wigleandroid.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Export-/Import-able network matching filter, carried in a {@link FilterSet}.
 * Provides description and a list of criteria to use in matching / exclusion from matching.
 * Serialize/deserialize via {@link net.wigle.wigleandroid.util.FilterJson}.
 *
 * <p>Sample JSON payload:
 * <pre>{@code
 *
 * {
 *   "description": "exclude AirTags",
 *   "exclude": true,
 *   "macAddresses": ["AA:BB:CC:DD:EE:FF"],
 *   "ouis": ["AA:BB:CC"],
 *   "ble": {
 *     "mfgrIds": ["0x004C"],
 *     "service": {
 *       "any": [
 *         { "description": "Find My", "uuid": "FD6F" }
 *       ],
 *       "all": {
 *         "groupKey": ["uuid-a", "uuid-b"]
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * v0.0.1
 * @author rksh
 */
public class Filter
{
    private String description; // text description of this filter.
    private Boolean exclude; // whether this filter should be treated as positive matching criteria or exclusion criteria from matches

    @SerializedName("macAddresses")
    private List<String> macAddress; // full addresses to match

    @SerializedName("ouis")
    private List<String> oui; // OUIs to match

    private BleFilter ble;

    /** No-arg for Gson / deserialization. Prefer {@link #builder()}. */
    public Filter() {
    }

    private Filter(final Builder builder) {
        this.description = builder.description;
        this.exclude = builder.exclude;
        this.macAddress = builder.macAddress == null
                ? null : Collections.unmodifiableList(new ArrayList<>(builder.macAddress));
        this.oui = builder.oui == null
                ? null : Collections.unmodifiableList(new ArrayList<>(builder.oui));
        this.ble = builder.ble;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDescription() {
        return description;
    }

    public Boolean getExclude() {
        return exclude;
    }

    public List<String> getMacAddress() {
        return macAddress;
    }

    public List<String> getOui() {
        return oui;
    }

    public BleFilter getBle() {
        return ble;
    }

    /** Light post-deserialize cleanup; leaves null lists as null. */
    public void normalize() {
        if (ble != null) {
            ble.normalize();
        }
    }

    /**
     * Fluent builder for {@link Filter}.
     */
    public static final class Builder {
        private String description;
        private Boolean exclude;
        private List<String> macAddress;
        private List<String> oui;
        private BleFilter ble;

        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        public Builder exclude(final Boolean exclude) {
            this.exclude = exclude;
            return this;
        }

        public Builder macAddress(final List<String> macAddress) {
            return macAddresses(macAddress);
        }

        public Builder macAddresses(final Collection<String> macAddresses) {
            this.macAddress = macAddresses == null ? null : new ArrayList<>(macAddresses);
            return this;
        }

        public Builder macAddresses(final String... macAddresses) {
            if (macAddresses == null) {
                this.macAddress = null;
                return this;
            }
            return macAddresses(Arrays.asList(macAddresses));
        }

        public Builder addMacAddress(final String mac) {
            if (mac == null) {
                return this;
            }
            if (this.macAddress == null) {
                this.macAddress = new ArrayList<>();
            }
            this.macAddress.add(mac);
            return this;
        }

        public Builder addMacAddresses(final Collection<String> macAddresses) {
            if (macAddresses == null) {
                return this;
            }
            for (final String mac : macAddresses) {
                addMacAddress(mac);
            }
            return this;
        }

        public Builder addMacAddresses(final String... macAddresses) {
            if (macAddresses == null) {
                return this;
            }
            return addMacAddresses(Arrays.asList(macAddresses));
        }

        public Builder oui(final List<String> oui) {
            return ouis(oui);
        }

        public Builder ouis(final Collection<String> ouis) {
            this.oui = ouis == null ? null : new ArrayList<>(ouis);
            return this;
        }

        public Builder ouis(final String... ouis) {
            if (ouis == null) {
                this.oui = null;
                return this;
            }
            return ouis(Arrays.asList(ouis));
        }

        public Builder addOui(final String ouiPrefix) {
            if (ouiPrefix == null) {
                return this;
            }
            if (this.oui == null) {
                this.oui = new ArrayList<>();
            }
            this.oui.add(ouiPrefix);
            return this;
        }

        public Builder addOuis(final Collection<String> ouis) {
            if (ouis == null) {
                return this;
            }
            for (final String ouiPrefix : ouis) {
                addOui(ouiPrefix);
            }
            return this;
        }

        public Builder addOuis(final String... ouis) {
            if (ouis == null) {
                return this;
            }
            return addOuis(Arrays.asList(ouis));
        }

        public Builder ble(final BleFilter ble) {
            this.ble = ble;
            return this;
        }

        public Filter build() {
            return new Filter(this);
        }
    }

    /**
     * BLE-specific matching: Manufacturer IDs and service UUIDs
     */
    public static class BleFilter {
        @SerializedName("mfgrIds")
        List<String> bleMfgrIds;   // list of BLE manufacturer IDs

        @SerializedName("service")
        BleServiceFilter bleService;  // service UUID matchers

        /** No-arg for Gson / deserialization. Prefer {@link #builder()}. */
        public BleFilter() {
        }

        public BleFilter(List<String> bleMfgrIds, BleServiceFilter bleService) {
            this.bleMfgrIds = bleMfgrIds;
            this.bleService = bleService;
        }

        private BleFilter(final Builder builder) {
            this.bleMfgrIds = builder.bleMfgrIds == null
                    ? null : Collections.unmodifiableList(new ArrayList<>(builder.bleMfgrIds));
            this.bleService = builder.bleService;
        }

        public static Builder builder() {
            return new Builder();
        }

        public List<String> getBleMfgrIds() {
            return bleMfgrIds;
        }

        public BleServiceFilter getBleService() {
            return bleService;
        }

        void normalize() {
            if (bleService != null) {
                bleService.normalize();
            }
        }

        public static final class Builder {
            private List<String> bleMfgrIds;
            private BleServiceFilter bleService;

            public Builder bleMfgrIds(final Collection<String> bleMfgrIds) {
                this.bleMfgrIds = bleMfgrIds == null ? null : new ArrayList<>(bleMfgrIds);
                return this;
            }

            public Builder bleMfgrIds(final String... bleMfgrIds) {
                if (bleMfgrIds == null) {
                    this.bleMfgrIds = null;
                    return this;
                }
                return bleMfgrIds(Arrays.asList(bleMfgrIds));
            }

            public Builder addBleMfgrId(final String mfgrId) {
                if (mfgrId == null) {
                    return this;
                }
                if (this.bleMfgrIds == null) {
                    this.bleMfgrIds = new ArrayList<>();
                }
                this.bleMfgrIds.add(mfgrId);
                return this;
            }

            public Builder addBleMfgrIds(final Collection<String> bleMfgrIds) {
                if (bleMfgrIds == null) {
                    return this;
                }
                for (final String mfgrId : bleMfgrIds) {
                    addBleMfgrId(mfgrId);
                }
                return this;
            }

            public Builder addBleMfgrIds(final String... bleMfgrIds) {
                if (bleMfgrIds == null) {
                    return this;
                }
                return addBleMfgrIds(Arrays.asList(bleMfgrIds));
            }

            public Builder bleService(final BleServiceFilter bleService) {
                this.bleService = bleService;
                return this;
            }

            public BleFilter build() {
                return new BleFilter(this);
            }
        }
    }

    /**
     * match-all/match-any service UUID criteria
     */
    public static class BleServiceFilter {
        List<BleSingleFilter> any;
        Map<String, List<String>> all;

        /** No-arg for Gson / deserialization. Prefer {@link #builder()}. */
        public BleServiceFilter() {
        }

        public BleServiceFilter(List<BleSingleFilter> any, Map<String, List<String>> all) {
            this.any = any;
            this.all = all;
        }

        private BleServiceFilter(final Builder builder) {
            this.any = builder.any == null
                    ? null : Collections.unmodifiableList(new ArrayList<>(builder.any));
            if (builder.all == null) {
                this.all = null;
            } else {
                final Map<String, List<String>> copy = new LinkedHashMap<>();
                for (final Map.Entry<String, List<String>> e : builder.all.entrySet()) {
                    copy.put(e.getKey(), e.getValue() == null
                            ? null : Collections.unmodifiableList(new ArrayList<>(e.getValue())));
                }
                this.all = Collections.unmodifiableMap(copy);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public List<BleSingleFilter> getAny() {
            return any;
        }

        public Map<String, List<String>> getAll() {
            return all;
        }

        void normalize() {
            // reserved for future cleanup; null lists stay null
        }

        public static final class Builder {
            private List<BleSingleFilter> any;
            private Map<String, List<String>> all;

            public Builder any(final List<BleSingleFilter> any) {
                return anys(any);
            }

            public Builder anys(final Collection<BleSingleFilter> anys) {
                this.any = anys == null ? null : new ArrayList<>(anys);
                return this;
            }

            public Builder anys(final BleSingleFilter... anys) {
                if (anys == null) {
                    this.any = null;
                    return this;
                }
                return anys(Arrays.asList(anys));
            }

            public Builder addAny(final BleSingleFilter filter) {
                if (filter == null) {
                    return this;
                }
                if (this.any == null) {
                    this.any = new ArrayList<>();
                }
                this.any.add(filter);
                return this;
            }

            public Builder addAny(final String description, final String uuid) {
                return addAny(BleSingleFilter.builder()
                        .description(description)
                        .uuid(uuid)
                        .build());
            }

            public Builder addAnys(final Collection<BleSingleFilter> anys) {
                if (anys == null) {
                    return this;
                }
                for (final BleSingleFilter filter : anys) {
                    addAny(filter);
                }
                return this;
            }

            public Builder addAnys(final BleSingleFilter... anys) {
                if (anys == null) {
                    return this;
                }
                return addAnys(Arrays.asList(anys));
            }

            public Builder all(final Map<String, List<String>> all) {
                return alls(all);
            }

            public Builder alls(final Map<String, List<String>> alls) {
                this.all = alls == null ? null : new LinkedHashMap<>(alls);
                return this;
            }

            @SafeVarargs
            public final Builder alls(final Map<String, List<String>>... alls) {
                if (alls == null) {
                    this.all = null;
                    return this;
                }
                final Map<String, List<String>> merged = new LinkedHashMap<>();
                for (final Map<String, List<String>> map : alls) {
                    if (map != null) {
                        merged.putAll(map);
                    }
                }
                this.all = merged;
                return this;
            }

            public Builder putAll(final String key, final List<String> values) {
                if (key == null) {
                    return this;
                }
                if (this.all == null) {
                    this.all = new LinkedHashMap<>();
                }
                this.all.put(key, values);
                return this;
            }

            public Builder addAlls(final Map<String, List<String>> alls) {
                if (alls == null) {
                    return this;
                }
                if (this.all == null) {
                    this.all = new LinkedHashMap<>();
                }
                this.all.putAll(alls);
                return this;
            }

            @SafeVarargs
            public final Builder addAlls(final Map<String, List<String>>... alls) {
                if (alls == null) {
                    return this;
                }
                for (final Map<String, List<String>> map : alls) {
                    addAlls(map);
                }
                return this;
            }

            public BleServiceFilter build() {
                return new BleServiceFilter(this);
            }
        }
    }

    public static class BleSingleFilter {
        private String description;
        private String uuid;

        /** No-arg for Gson / deserialization. Prefer {@link #builder()}. */
        public BleSingleFilter() {
        }

        public BleSingleFilter(String description, String uuid) {
            this.description = description;
            this.uuid = uuid;
        }

        private BleSingleFilter(final Builder builder) {
            this.description = builder.description;
            this.uuid = builder.uuid;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getDescription() {
            return description;
        }

        public String getUuid() {
            return uuid;
        }

        public static final class Builder {
            private String description;
            private String uuid;

            public Builder description(final String description) {
                this.description = description;
                return this;
            }

            public Builder uuid(final String uuid) {
                this.uuid = uuid;
                return this;
            }

            public BleSingleFilter build() {
                return new BleSingleFilter(this);
            }
        }
    }
}
