package net.wigle.wigleandroid;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.ui.NetworkListAdapter;
import net.wigle.wigleandroid.ui.NetworkListSorter;
import net.wigle.wigleandroid.ui.SetNetworkListAdapter;
import net.wigle.wigleandroid.util.Logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class NetworkListTest {

    private Context context;
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

    //ALIBI: dup from unit tests because of no-sharing in tests
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

    @Before
    public void setContext() {
        context = getInstrumentation().getTargetContext();
    }

    @Test
    public void testSetBackedList() {
        Assert.assertTrue(null != context);
        SetNetworkListAdapter setAdapter = new SetNetworkListAdapter( context, R.layout.row );
        long start = System.currentTimeMillis();
        for (Network net: btleLarge) {
            setAdapter.addBluetoothLe(net);
        }
        long end = System.currentTimeMillis();
        Logging.info(" Added to Set-backed in ("+(end-start)+"ms)");
        start = System.currentTimeMillis();
        setAdapter.sort(NetworkListSorter.signalCompare);
        end = System.currentTimeMillis();
        Logging.info(" Sorted set-backed in ("+(end-start)+"ms)");
        start = System.currentTimeMillis();
        for (Network net: btcLarge) {
            setAdapter.enqueueBluetooth(net);
        }
        setAdapter.batchUpdateBt(false, false, true);
        end = System.currentTimeMillis();
        Logging.info(" Batch-added BTC to set-backed in ("+(end-start)+"ms)");

        start = System.currentTimeMillis();
        setAdapter.sort(NetworkListSorter.signalCompare);
        end = System.currentTimeMillis();
        Logging.info(" Re-sorted set-backed in ("+(end-start)+"ms)");

        start = System.currentTimeMillis();
        for (Network net: btleSmall) {
            setAdapter.addBluetoothLe(net);
        }
        setAdapter.batchUpdateBt(true, true, false);
        end = System.currentTimeMillis();
        Logging.info(" Destructively batch-added BTLE small to set-backed in ("+(end-start)+"ms)");

        start = System.currentTimeMillis();
        setAdapter.sort(NetworkListSorter.signalCompare);
        end = System.currentTimeMillis();
        Logging.info(" Re-sorted set-backed in ("+(end-start)+"ms)");
    }

    @Test
    public void testOldList() {
        Assert.assertTrue(null != context);
        NetworkListAdapter adapter = new NetworkListAdapter( context, R.layout.row );
        long start = System.currentTimeMillis();
        for (Network net: btleLarge) {
            adapter.addBluetoothLe(net);
        }
        long end = System.currentTimeMillis();
        Logging.info(" Added to old in ("+(end-start)+"ms)");
        start = System.currentTimeMillis();
        adapter.sort(NetworkListSorter.signalCompare);
        end = System.currentTimeMillis();
        Logging.info(" Sorted old in ("+(end-start)+"ms)");
        start = System.currentTimeMillis();
        for (Network net: btcLarge) {
            adapter.enqueueBluetooth(net);
        }
        adapter.batchUpdateBt(false, false, true);
        end = System.currentTimeMillis();
        Logging.info(" Batch-added BTC to old in ("+(end-start)+"ms)");

        start = System.currentTimeMillis();
        adapter.sort(NetworkListSorter.signalCompare);
        end = System.currentTimeMillis();
        Logging.info(" Re-sorted old in ("+(end-start)+"ms)");

        start = System.currentTimeMillis();
        for (Network net: btleSmall) {
            adapter.addBluetoothLe(net);
        }
        adapter.batchUpdateBt(true, true, false);
        end = System.currentTimeMillis();
        Logging.info(" Destructively batch-added BTLE small to old in ("+(end-start)+"ms)");

        start = System.currentTimeMillis();
        adapter.sort(NetworkListSorter.signalCompare);
        end = System.currentTimeMillis();
        Logging.info(" Re-sorted old in ("+(end-start)+"ms)");
    }

    //ALIBI: dup from unit tests because of no-sharing in tests
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