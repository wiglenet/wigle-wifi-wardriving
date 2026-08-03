package net.wigle.wigleandroid.model;

import net.wigle.wigleandroid.util.FilterJson;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Round-trip and schema tests for Filter JSON codec.
 */
public class FilterJsonTest {

    @Test
    public void roundTripWithMacOuiAndBle() {
        final Filter original = Filter.builder()
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
                .build();

        final String json = FilterJson.toJson(original);
        Assert.assertTrue(json.contains("\"macAddresses\""));
        Assert.assertTrue(json.contains("\"ouis\""));
        Assert.assertTrue(json.contains("\"mfgrIds\""));
        Assert.assertTrue(json.contains("\"service\""));
        Assert.assertTrue(json.contains("\"schemaVersion\":1"));

        final Filter decoded = FilterJson.fromJson(json);
        Assert.assertEquals(Integer.valueOf(Filter.CURRENT_SCHEMA_VERSION), decoded.getSchemaVersion());
        Assert.assertEquals("exclude AirTags", decoded.getDescription());
        Assert.assertEquals(Boolean.TRUE, decoded.getExclude());
        Assert.assertEquals(Collections.singletonList("AA:BB:CC:DD:EE:FF"), decoded.getMacAddress());
        Assert.assertEquals(Collections.singletonList("AA:BB:CC"), decoded.getOui());

        Assert.assertNotNull(decoded.getBle());
        Assert.assertEquals(Collections.singletonList("0x004C"), decoded.getBle().getBleMfgrIds());
        Assert.assertNotNull(decoded.getBle().getBleService());

        final List<Filter.BleSingleFilter> any = decoded.getBle().getBleService().getAny();
        Assert.assertNotNull(any);
        Assert.assertEquals(1, any.size());
        Assert.assertEquals("Find My", any.get(0).getDescription());
        Assert.assertEquals("FD6F", any.get(0).getUuid());

        final Map<String, List<String>> all = decoded.getBle().getBleService().getAll();
        Assert.assertNotNull(all);
        Assert.assertEquals(Arrays.asList("uuid-a", "uuid-b"), all.get("groupKey"));
    }

    @Test
    public void omitsNullBle() {
        final Filter filter = Filter.builder()
                .description("mac only")
                .macAddresses("11:22:33:44:55:66")
                .build();

        final String json = FilterJson.toJson(filter);
        Assert.assertFalse(json.contains("\"ble\""));
        Assert.assertNull(FilterJson.fromJson(json).getBle());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingSchemaVersion() {
        FilterJson.fromJson("{\"description\":\"no version\",\"macAddresses\":[\"AA:BB:CC:DD:EE:FF\"]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFutureSchemaVersion() {
        FilterJson.fromJson("{\"schemaVersion\":999,\"description\":\"future\"}");
    }

    @Test
    public void listRoundTrip() {
        final List<Filter> filters = Arrays.asList(
                Filter.builder().description("a").ouis("AA:BB:CC").build(),
                Filter.builder().description("b").exclude(false).build()
        );
        final String json = FilterJson.toJsonList(filters);
        final List<Filter> decoded = FilterJson.fromJsonList(json);
        Assert.assertEquals(2, decoded.size());
        Assert.assertEquals("a", decoded.get(0).getDescription());
        Assert.assertEquals("b", decoded.get(1).getDescription());
    }
}
