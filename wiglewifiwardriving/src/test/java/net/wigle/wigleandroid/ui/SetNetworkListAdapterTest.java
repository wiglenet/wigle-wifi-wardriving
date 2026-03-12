package net.wigle.wigleandroid.ui;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * make sure our proxy list from the the WiFi, Cell, BT and BTLE sets works right
 */
public class SetNetworkListAdapterTest {

    final static long wifiLargeSize = 10000L;
    final static long wifiSmallSize = 100L;
    final static long cellLargeSize = 10001L;
    final static long cellSmallSize = 101L;
    final static long btcLargeSize = 10002L;
    final static long btcSmallSize = 102L;
    final static long btleLargeSize = 10003L;
    final static long btleSmallSize = 103L;

    private static List<Network> wifiLarge = new ArrayList<>();
    private static List<Network> wifiSmall = new ArrayList<>();
    private static List<Network> cellLarge = new ArrayList<>();
    private static List<Network> cellSmall = new ArrayList<>();
    private static List<Network> btcLarge = new ArrayList<>();
    private static List<Network> btcSmall = new ArrayList<>();
    private static List<Network> btleLarge = new ArrayList<>();
    private static List<Network> btleSmall = new ArrayList<>();

    @BeforeClass
    public static void setupTestSets() {
        //ALIBI: note: we're inviting collision, but it's unlikely, considering the size of MAC addresses
        System.out.print("Generating sets...");
        for (int i = 0; i < wifiLargeSize; i++) {
            final String nextMac = randomMACAddress();
            wifiLarge.add(new Network(nextMac, "SSID"+nextMac, 1, "test WiFi capabilities",
            -113, NetworkType.WIFI));
        }
        for (int i = 0; i < wifiSmallSize; i++) {
            final String nextMac = randomMACAddress();
            wifiSmall.add(new Network(nextMac, "SSID"+nextMac, 2, "test WiFi capabilities",
                    -101, NetworkType.WIFI));
        }
        for (int i = 0; i < cellLargeSize; i++) {
            final String nextMac = randomMACAddress();
            cellLarge.add(new Network(nextMac, "SSID"+nextMac, 101, "test CDMA capabilities",
                    -90, NetworkType.CDMA));
        }
        for (int i = 0; i < cellSmallSize; i++) {
            final String nextMac = randomMACAddress();
            cellSmall.add(new Network(nextMac, "SSID"+nextMac, 1231, "test LTE capabilities",
                    -88, NetworkType.LTE));
        }
        for (int i = 0; i < btcLargeSize; i++) {
            final String nextMac = randomMACAddress();
            btcLarge.add(new Network(nextMac, "SSID"+nextMac, 4, "test BT capabilities",
                    -112, NetworkType.BT));
        }
        for (int i = 0; i < btcSmallSize; i++) {
            final String nextMac = randomMACAddress();
            btcSmall.add(new Network(nextMac, "SSID"+nextMac, 2, "test BT capabilities",
                    -111, NetworkType.BT));
        }
        for (int i = 0; i < btleLargeSize; i++) {
            final String nextMac = randomMACAddress();
            btleLarge.add(new Network(nextMac, "SSID"+nextMac, 0, "test BLE capabilities",
                    -104, NetworkType.BLE));
        }
        for (int i = 0; i < btleSmallSize; i++) {
            final String nextMac = randomMACAddress();
            btleSmall.add(new Network(nextMac, "SSID"+nextMac, -1, "test BLE capabilities",
                    -103, NetworkType.BLE));
        }
        System.out.println("Generated!");
    }

    /**
     * Just throw all the large sets, sort them, and then add the smaller sets. Smoke and timing
     */
    @Test
    public void testLargeSets() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting Large Sets into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        netlist.addAll(cellLarge);
        netlist.addAll(btcLarge);
        netlist.addAll(btleLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        System.out.print("Sorting SetBackedNetworkList by signal...");
        start = System.currentTimeMillis();
        netlist.sort(NetworkListSorter.signalCompare);
        end = System.currentTimeMillis();
        System.out.println(" Sorted in ("+(end-start)+"ms)");
        System.out.print("Sorting SetBackedNetworkList by channel...");
        start = System.currentTimeMillis();
        netlist.sort(NetworkListSorter.channelCompare);
        end = System.currentTimeMillis();
        System.out.println(" Sorted in ("+(end-start)+"ms)");
        System.out.print("Sorting SetBackedNetworkList by encryption ...");
        start = System.currentTimeMillis();
        netlist.sort(NetworkListSorter.cryptoCompare);
        end = System.currentTimeMillis();
        System.out.println(" Sorted in ("+(end-start)+"ms)");
        System.out.print("Sorting SetBackedNetworkList by time found ...");
        start = System.currentTimeMillis();
        netlist.sort(NetworkListSorter.findTimeCompare);
        end = System.currentTimeMillis();
        System.out.println(" Sorted in ("+(end-start)+"ms)");

        System.out.print("Merging smaller sets...");
        start = System.currentTimeMillis();
        netlist.addAll(wifiSmall);
        netlist.addAll(cellSmall);
        netlist.addAll(btcSmall);
        netlist.addAll(btleSmall);
        end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");
    }

    /**
     * Add all the large sets, then add them again, make sure the list doesn't get bigger
     */
    @Test
    public void testDupSafe() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting Large Sets into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        netlist.addAll(cellLarge);
        netlist.addAll(btcLarge);
        netlist.addAll(btleLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long size = netlist.size();

        // add all those nets again and make sure the size doesn't increase
        System.out.print("Reinserting Large Sets into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        netlist.addAll(cellLarge);
        netlist.addAll(btcLarge);
        netlist.addAll(btleLarge);
        end = System.currentTimeMillis();
        System.out.println(" Reinserted in ("+(end-start)+"ms)");

        Assert.assertEquals(size, netlist.size());

    }

    /**
     * Add sets, use the removeAlls, make sure the numbers line up
     */
    @Test
    public void testRemoveAllSafe() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting 1/2 of Large Sets into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        netlist.addAll(btcLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long sizeHalf = netlist.size();

        System.out.print("Inserting 2nd 1/2 of Large Sets into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.addAll(cellLarge);
        netlist.addAll(btleLarge);
        end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long sizeFull = netlist.size();

        Assert.assertNotEquals(sizeHalf, sizeFull);

        System.out.print("Removing 1/2 of Large Sets from SetBackedNetworkList...");
        start = System.currentTimeMillis();
        boolean cellSucceeded = netlist.removeAll(cellLarge);
        boolean btleSucceeded = netlist.removeAll(btleLarge);
        end = System.currentTimeMillis();
        System.out.println(" Removed in ("+(end-start)+"ms)");

        Assert.assertTrue(cellSucceeded);
        Assert.assertTrue(btleSucceeded);

        Assert.assertEquals(sizeHalf, netlist.size());

        System.out.print("Removing second 1/2 of Large Sets from SetBackedNetworkList...");
        start = System.currentTimeMillis();
        boolean wifiSucceeded = netlist.removeAll(wifiLarge);
        boolean btSucceeded = netlist.removeAll(btcLarge);
        end = System.currentTimeMillis();
        System.out.println(" Removed in ("+(end-start)+"ms)");

        Assert.assertTrue(cellSucceeded);
        Assert.assertTrue(btleSucceeded);
        Assert.assertEquals(0L, netlist.size());
    }

    /**
     * Test the clears - make sure the numbers line up
     */
    @Test
    public void testClears() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting 1/2 of Large Sets into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        netlist.addAll(btcLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long sizeHalf = netlist.size();
        System.out.print("Inserting 2nd 1/2 of Large Sets into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.addAll(cellLarge);
        netlist.addAll(btleLarge);
        end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long sizeFull = netlist.size();

        Assert.assertNotEquals(sizeHalf, sizeFull);
        System.out.print("Clearing cell and btle Large Sets from SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.clearBluetoothLe();
        netlist.clearCell();
        end = System.currentTimeMillis();
        System.out.println(" Removed in ("+(end-start)+"ms)");
        Assert.assertEquals(sizeHalf, netlist.size());

        System.out.print("Clearing btc and wifi Large Sets from SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.clearBluetooth();
        netlist.clearWifi();
        end = System.currentTimeMillis();
        System.out.println(" Removed in ("+(end-start)+"ms)");
        Assert.assertEquals(0L, netlist.size());
    }

    @Test
    public void testIndividualRemove() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting WiFi Large Set into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long wifiSize = netlist.size();

        System.out.print("Removing 1 WiFi net from SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.remove(wifiLarge.get(50));
        end = System.currentTimeMillis();
        System.out.println(" removed in ("+(end-start)+"ms)");

        Assert.assertEquals(wifiSize-1, netlist.size());

        System.out.print("Inserting Cell Large Set into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.addAll(cellLarge);
        end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long cellSize = netlist.size();

        System.out.print("Removing 1 Cell net from SetBackedNetworkList...");
        start = System.currentTimeMillis();
        // Use a cell from the tail - eldest are evicted when cellLarge exceeds MAX_CELL_SET_SIZE (5000)
        netlist.remove(cellLarge.get(cellLarge.size() - 1));
        end = System.currentTimeMillis();
        System.out.println(" removed in ("+(end-start)+"ms)");

        Assert.assertEquals((cellSize-1), netlist.size());

    }

    @Test
    public void testRetainAll() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting WiFi Large Set into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long wifiSize = netlist.size();

        System.out.print("Inserting BLE Large Set into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.addAll(btleLarge);
        end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long wifiPlusBleSize = netlist.size();

        Assert.assertNotEquals(wifiPlusBleSize, wifiSize);

        System.out.print("Retaining WiFi Large Set in SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.retainAll(wifiLarge);
        end = System.currentTimeMillis();
        System.out.println(" Retained in ("+(end-start)+"ms)");

        Assert.assertEquals(netlist.size(), wifiSize);
    }

    @Test
    public void testAdd() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting WiFi Large Set into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(wifiLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long wifiSize = netlist.size();

        if (netlist.add(wifiSmall.get(66))) {
            //ALIBI: if this randomly happened to be a dup, it would be false.
            Assert.assertEquals(wifiSize+1, netlist.size());
        }

    }

    private static final int TEST_WIFI_CAP = 100;
    private static final int TEST_BT_CAP = 100;
    private static final int TEST_CELL_CAP = 500;

    /**
     * Test that WiFi networks are capped at MAX_WIFI_SET_SIZE.
     * Adding more than the cap should evict eldest entries.
     */
    @Test
    public void testWifiCap() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        netlist.MAX_WIFI_SET_SIZE = TEST_WIFI_CAP;
        List<Network> wifiOverCap = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            wifiOverCap.add(new Network(randomMACAddress(), "SSID" + i, 1, "test",
                    -100, NetworkType.WIFI));
        }
        netlist.addAll(wifiOverCap);
        Assert.assertEquals("WiFi list should be capped at MAX_WIFI_SET_SIZE",
                TEST_WIFI_CAP, netlist.size());
        // First-added networks should have been evicted
        Assert.assertFalse("Eldest WiFi should have been evicted",
                netlist.contains(wifiOverCap.get(0)));
        Assert.assertTrue("Most recent WiFi should still be present",
                netlist.contains(wifiOverCap.get(wifiOverCap.size() - 1)));
    }

    /**
     * Test that BT networks are capped at MAX_BT_SET_SIZE.
     */
    @Test
    public void testBtCap() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        netlist.MAX_BT_SET_SIZE = TEST_BT_CAP;
        List<Network> btOverCap = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            btOverCap.add(new Network(randomMACAddress(), "BT" + i, 1, "test",
                    -100, NetworkType.BT));
        }
        netlist.addAll(btOverCap);
        Assert.assertEquals("BT list should be capped at MAX_BT_SET_SIZE",
                TEST_BT_CAP, netlist.size());
        Assert.assertFalse("Eldest BT should have been evicted",
                netlist.contains(btOverCap.get(0)));
        Assert.assertTrue("Most recent BT should still be present",
                netlist.contains(btOverCap.get(btOverCap.size() - 1)));
    }

    /**
     * Test that BLE networks are capped at MAX_BT_SET_SIZE.
     */
    @Test
    public void testBleCap() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        netlist.MAX_BT_SET_SIZE = TEST_BT_CAP;
        List<Network> bleOverCap = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            bleOverCap.add(new Network(randomMACAddress(), "BLE" + i, 0, "test",
                    -100, NetworkType.BLE));
        }
        netlist.addAll(bleOverCap);
        Assert.assertEquals("BLE list should be capped at MAX_BT_SET_SIZE",
                TEST_BT_CAP, netlist.size());
        Assert.assertFalse("Eldest BLE should have been evicted",
                netlist.contains(bleOverCap.get(0)));
        Assert.assertTrue("Most recent BLE should still be present",
                netlist.contains(bleOverCap.get(bleOverCap.size() - 1)));
    }

    /**
     * Test that cell networks are capped at MAX_CELL_SET_SIZE.
     */
    @Test
    public void testCellCap() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        netlist.MAX_CELL_SET_SIZE = TEST_CELL_CAP;
        List<Network> cellOverCap = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            cellOverCap.add(new Network(randomMACAddress(), "cell" + i, 1, "test",
                    -90, NetworkType.LTE));
        }
        netlist.addAll(cellOverCap);
        Assert.assertEquals("Cell list should be capped at MAX_CELL_SET_SIZE",
                TEST_CELL_CAP, netlist.size());
        Assert.assertFalse("Eldest cell should have been evicted",
                netlist.contains(cellOverCap.get(0)));
        Assert.assertTrue("Most recent cell should still be present",
                netlist.contains(cellOverCap.get(cellOverCap.size() - 1)));
    }

    /**
     * Test that caps are enforced per network type; adding mixed types does not
     * cause cross-type eviction.
     */
    @Test
    public void testCapsPerType() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        netlist.MAX_WIFI_SET_SIZE = TEST_WIFI_CAP;
        netlist.MAX_BT_SET_SIZE = TEST_BT_CAP;
        // Add 100 WiFi (at cap)
        List<Network> wifi100 = new ArrayList<>();
        for (int i = 0; i < TEST_WIFI_CAP; i++) {
            wifi100.add(new Network(randomMACAddress(), "SSID" + i, 1, "test",
                    -100, NetworkType.WIFI));
        }
        netlist.addAll(wifi100);
        Assert.assertEquals(TEST_WIFI_CAP, netlist.size());

        // Add 100 BT (at cap) - total 200
        List<Network> bt100 = new ArrayList<>();
        for (int i = 0; i < TEST_BT_CAP; i++) {
            bt100.add(new Network(randomMACAddress(), "BT" + i, 1, "test",
                    -100, NetworkType.BT));
        }
        netlist.addAll(bt100);
        Assert.assertEquals(TEST_WIFI_CAP + TEST_BT_CAP, netlist.size());

        // Add 100 more WiFi - should evict eldest WiFi, total stays 200
        List<Network> wifi100More = new ArrayList<>();
        for (int i = 0; i < TEST_WIFI_CAP; i++) {
            wifi100More.add(new Network(randomMACAddress(), "SSID2-" + i, 1, "test",
                    -100, NetworkType.WIFI));
        }
        netlist.addAll(wifi100More);
        Assert.assertEquals("Total should remain 200 (100 WiFi + 100 BT)",
                TEST_WIFI_CAP + TEST_BT_CAP, netlist.size());
        Assert.assertFalse("Eldest WiFi should have been evicted",
                netlist.contains(wifi100.get(0)));
        Assert.assertTrue("Original BT should be untouched",
                netlist.contains(bt100.get(0)));
    }

    @Test
    public void testBtBatch() {
        SetBackedNetworkList netlist = new SetBackedNetworkList();
        System.out.print("Inserting BT Large Sets into SetBackedNetworkList...");
        long start = System.currentTimeMillis();
        netlist.addAll(btcLarge);
        netlist.addAll(btleLarge);
        long end = System.currentTimeMillis();
        System.out.println(" Inserted in ("+(end-start)+"ms)");

        long lgBtSize = netlist.size();

        System.out.print("Enqueueing BT small Sets into SetBackedNetworkList...");
        for (Network n: btcSmall) {
            netlist.enqueueBluetooth(n);
        }
        Assert.assertEquals(netlist.size(), lgBtSize);

        for (Network n: btleSmall) {
            netlist.enqueueBluetoothLe(n);
        }
        Assert.assertEquals(netlist.size(), lgBtSize);
        System.out.println(" Enqueued in ("+(end-start)+"ms)");

        System.out.print("Non-destructively processing BTC small set into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.batchUpdateBt(false, false, true);
        end = System.currentTimeMillis();
        long nonDestructiveClassicSize = netlist.size();
        System.out.println(" Processed in ("+(end-start)+"ms)");

        Assert.assertNotEquals(nonDestructiveClassicSize, lgBtSize);

        System.out.print("Destructively processing BTLE small set into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.batchUpdateBt(true, true, false );
        end = System.currentTimeMillis();
        System.out.println(" Processed in ("+(end-start)+"ms)");

        long destructiveLeSize = netlist.size();
        Assert.assertNotEquals(nonDestructiveClassicSize, destructiveLeSize);

        netlist.clear();
        System.out.println("cleared");

        System.out.print("Reinserting BT Large Sets into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.addAll(btcLarge);
        netlist.addAll(btleLarge);
        netlist.addAll(btcSmall);
        end = System.currentTimeMillis();
        System.out.println(" Reinserted in ("+(end-start)+"ms)");

        Assert.assertEquals(nonDestructiveClassicSize, netlist.size());

        netlist.clear();
        System.out.println("cleared");

        System.out.print("Reinserting BT Destructive Case Sets into SetBackedNetworkList...");
        start = System.currentTimeMillis();
        netlist.addAll(btcLarge);
        netlist.addAll(btleSmall);
        netlist.addAll(btcSmall);
        end = System.currentTimeMillis();
        System.out.println(" Reinserted in ("+(end-start)+"ms)");

        Assert.assertEquals(destructiveLeSize, netlist.size());
    }

    // from https://stackoverflow.com/questions/24261027/make-a-random-mac-address-generator-generate-just-unicast-macs
    private static String randomMACAddress(){
        Random rand = new Random();
        byte[] macAddr = new byte[6];
        rand.nextBytes(macAddr);

        macAddr[0] = (byte)(macAddr[0] & (byte)254);  //zeroing last 2 bytes to make it unicast and locally adminstrated

        StringBuilder sb = new StringBuilder(18);
        for(byte b : macAddr){
            if(sb.length() > 0) {
                sb.append(":");
            }
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}
