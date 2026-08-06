package net.wigle.wigleandroid.model;

import net.wigle.wigleandroid.util.FilterJson;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Round-trip and schema tests for FilterSet JSON codec.
 */
public class FilterJsonTest {

    @Test
    public void roundTripWithMacOuiAndBle() {
        final FilterSet original = FilterSet.builder()
                .name("alerts")
                .addFilter(Filter.builder()
                        .description("exclude AirTags")
                        .exclude(true)
                        .macAddresses("AA:BB:CC:DD:EE:FF")
                        .ouis("AA:BB:CC")
                        .ble(Filter.BleFilter.builder()
                                .bleMfgrIds("0x004C")
                                .bleService(Filter.BleServiceFilter.builder()
                                        .addAny("Find My", "FD6F")
                                        .putAll("groupKey", Arrays.asList("uuid-a", "uuid-b"))
                                        .build())
                                .build())
                        .build())
                .build();

        final String json = FilterJson.toJson(original);
        Assert.assertTrue(json.contains("\"filters\""));
        Assert.assertTrue(json.contains("\"macAddresses\""));
        Assert.assertTrue(json.contains("\"ouis\""));
        Assert.assertTrue(json.contains("\"mfgrIds\""));
        Assert.assertTrue(json.contains("\"service\""));
        Assert.assertTrue(json.contains("\"schemaVersion\":1"));

        final FilterSet decoded = FilterJson.fromJson(json);
        Assert.assertEquals(Integer.valueOf(FilterSet.CURRENT_SCHEMA_VERSION),
                decoded.getSchemaVersion());
        Assert.assertEquals("alerts", decoded.getName());
        Assert.assertNotNull(decoded.getFilters());
        Assert.assertEquals(1, decoded.getFilters().size());

        final Filter filter = decoded.getFilters().get(0);
        Assert.assertEquals("exclude AirTags", filter.getDescription());
        Assert.assertEquals(Boolean.TRUE, filter.getExclude());
        Assert.assertEquals(Collections.singletonList("AA:BB:CC:DD:EE:FF"), filter.getMacAddress());
        Assert.assertEquals(Collections.singletonList("AA:BB:CC"), filter.getOui());

        Assert.assertNotNull(filter.getBle());
        Assert.assertEquals(Collections.singletonList("0x004C"), filter.getBle().getBleMfgrIds());
        Assert.assertNotNull(filter.getBle().getBleService());

        final List<Filter.BleSingleFilter> any = filter.getBle().getBleService().getAny();
        Assert.assertNotNull(any);
        Assert.assertEquals(1, any.size());
        Assert.assertEquals("Find My", any.get(0).getDescription());
        Assert.assertEquals("FD6F", any.get(0).getUuid());

        final Map<String, List<String>> all = filter.getBle().getBleService().getAll();
        Assert.assertNotNull(all);
        Assert.assertEquals(Arrays.asList("uuid-a", "uuid-b"), all.get("groupKey"));
    }

    @Test
    public void versionsTheSetRatherThanEachFilter() {
        final String json = FilterJson.toJson(FilterSet.builder()
                .name("two")
                .addFilter(Filter.builder().description("a").build())
                .addFilter(Filter.builder().description("b").build())
                .build());

        Assert.assertEquals(json.indexOf("schemaVersion"), json.lastIndexOf("schemaVersion"));
    }

    @Test
    public void omitsNullBle() {
        final FilterSet filterSet = FilterSet.builder()
                .name("mac only")
                .addFilter(Filter.builder()
                        .description("mac only")
                        .macAddresses("11:22:33:44:55:66")
                        .build())
                .build();

        final String json = FilterJson.toJson(filterSet);
        Assert.assertFalse(json.contains("\"ble\""));
        Assert.assertNull(FilterJson.fromJson(json).getFilters().get(0).getBle());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingSchemaVersion() {
        FilterJson.fromJson("{\"name\":\"no version\",\"filters\":[{\"description\":\"a\"}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFutureSchemaVersion() {
        FilterJson.fromJson("{\"schemaVersion\":999,\"name\":\"future\",\"filters\":[]}");
    }

    @Test
    public void listRoundTrip() {
        final FilterSet filterSet = FilterSet.builder()
                .name("ordered")
                .filters(Filter.builder().description("a").ouis("AA:BB:CC").build(),
                        Filter.builder().description("b").exclude(false).build())
                .build();

        final List<Filter> decoded = FilterJson.fromJson(FilterJson.toJson(filterSet)).getFilters();
        Assert.assertEquals(2, decoded.size());
        Assert.assertEquals("a", decoded.get(0).getDescription());
        Assert.assertEquals("b", decoded.get(1).getDescription());
    }
}
