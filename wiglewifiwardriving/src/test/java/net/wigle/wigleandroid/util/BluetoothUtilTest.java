package net.wigle.wigleandroid.util;

import static net.wigle.wigleandroid.util.BluetoothUtil.BleRandomSubtype;
import static net.wigle.wigleandroid.util.BluetoothUtil.bleRandomSubtypeFromBssid;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for BLE Random Address subtype derivation per Bluetooth Core Spec Vol 6 Part B Sec
 * 1.3.2 (random_address[47:46] determines the subtype).
 */
public class BluetoothUtilTest {

    @Test
    public void staticRandom_topBits11() {
        // 0xC0 = 0b11000000 -> top 2 bits = 11 -> Static
        Assert.assertEquals(BleRandomSubtype.STATIC, bleRandomSubtypeFromBssid("C0:11:22:33:44:55"));
        Assert.assertEquals(BleRandomSubtype.STATIC, bleRandomSubtypeFromBssid("F0:AA:BB:CC:DD:EE"));
        Assert.assertEquals(BleRandomSubtype.STATIC, bleRandomSubtypeFromBssid("FF:FF:FF:FF:FF:FF"));
    }

    @Test
    public void resolvablePrivate_topBits01() {
        // 0x40 = 0b01000000 -> top 2 bits = 01 -> Resolvable Private
        Assert.assertEquals(BleRandomSubtype.RESOLVABLE_PRIVATE, bleRandomSubtypeFromBssid("40:11:22:33:44:55"));
        Assert.assertEquals(BleRandomSubtype.RESOLVABLE_PRIVATE, bleRandomSubtypeFromBssid("7F:AA:BB:CC:DD:EE"));
    }

    @Test
    public void nonResolvablePrivate_topBits00() {
        // 0x00 = 0b00000000 -> top 2 bits = 00 -> Non-Resolvable Private
        Assert.assertEquals(BleRandomSubtype.NON_RESOLVABLE_PRIVATE, bleRandomSubtypeFromBssid("00:11:22:33:44:55"));
        Assert.assertEquals(BleRandomSubtype.NON_RESOLVABLE_PRIVATE, bleRandomSubtypeFromBssid("3F:AA:BB:CC:DD:EE"));
    }

    @Test
    public void reservedTopBits_returnsNotApplicable() {
        // 0x80 = 0b10000000 -> top 2 bits = 10 -> reserved (not used for random per spec)
        Assert.assertEquals(BleRandomSubtype.NOT_APPLICABLE, bleRandomSubtypeFromBssid("80:11:22:33:44:55"));
        Assert.assertEquals(BleRandomSubtype.NOT_APPLICABLE, bleRandomSubtypeFromBssid("BF:AA:BB:CC:DD:EE"));
    }

    @Test
    public void invalidInputs_returnNotApplicable() {
        Assert.assertEquals(BleRandomSubtype.NOT_APPLICABLE, bleRandomSubtypeFromBssid(null));
        Assert.assertEquals(BleRandomSubtype.NOT_APPLICABLE, bleRandomSubtypeFromBssid(""));
        Assert.assertEquals(BleRandomSubtype.NOT_APPLICABLE, bleRandomSubtypeFromBssid("Z"));
        Assert.assertEquals(BleRandomSubtype.NOT_APPLICABLE, bleRandomSubtypeFromBssid("ZZ:11:22:33:44:55"));
    }

    @Test
    public void lowercaseAndShortInputs_handled() {
        // Only the first byte matters; case-insensitive parsing.
        Assert.assertEquals(BleRandomSubtype.STATIC, bleRandomSubtypeFromBssid("ff"));
        Assert.assertEquals(BleRandomSubtype.NON_RESOLVABLE_PRIVATE, bleRandomSubtypeFromBssid("00"));
    }
}
