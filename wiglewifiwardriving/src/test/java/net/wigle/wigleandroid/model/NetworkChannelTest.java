package net.wigle.wigleandroid.model;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for frequency/channel conversion
 * @author rksh
 */
public class NetworkChannelTest {

    //Test Data
    static final List<Integer> wiFi24GHzCenterFrequencies = Arrays.asList(2412, 2417, 2422, 2427, 2432,
            2437, 2442, 2447, 2452, 2457, 2462, 2467, 2472, 2484);
    static final List<Integer> wiFi24GHzChannelNumbers = Arrays.asList(1, 2, 3, 4, 5,
            6, 7, 8, 9, 10, 11, 12, 13, 14);

    //ALIBI: https://en.wikipedia.org/wiki/List_of_WLAN_channels#5_GHz_(802.11a/h/j/n/ac/ax) - 5910-5980 not currently supported/registered
    static final List<Integer> wiFi5GHzCenterFrequencies = Arrays.asList(5035, 5040, 5045, 5055, 5060,
            5080, 5160, 5170, 5180, 5190, 5200, 5210, 5220, 5230, 5240,
            5250, 5260, 5270, 5280, 5290, 5300, 5310, 5320, 5340, 5480,
            5500, 5510, 5520, 5530, 5540, 5550, 5560, 5570, 5580, 5590,
            5600, 5610, 5620, 5630, 5640, 5660, 5670, 5680, 5690, 5700,
            5710, 5720, 5745, 5755, 5765, 5775, 5785, 5795, 5805, 5815,
            5825, 5835, 5845, 5855, 5865, 5875, 5885/*UNUSED: , 5900, 5910, 5915,
            5920, 5935, 5940, 5945, 5960, 5980*/);

    static final List<Integer> wiFi5GHzChannelNumbers = Arrays.asList(7, 8, 9, 11, 12,
            16, 32, 34, 36, 38, 40, 42, 44, 46, 48,
            50, 52, 54, 56, 58, 60, 62, 64, 68, 96,
            100, 102, 104, 106, 108, 110, 112, 114, 116, 118,
            120, 122, 124, 126, 128, 132, 134, 136, 138, 140,
            142, 144, 149, 151, 153, 155, 157, 159, 161, 163,
            165, 167, 169, 171, 173, 175, 177/*UNUSED: , 180, 182, 183,
            184, 187, 188, 189, 192, 196*/);

    static final List<Integer> wiFi6GHzCenterFrequencies = Arrays.asList(5955, 5975, 5995, 6015, 6035,
            6055, 6075, 6095, 6115, 6135, 6155, 6175, 6195, 6215, 6235,
            6255, 6275, 6295, 6315, 6335, 6355, 6375, 6395, 6415);
    static final List<Integer> wiFi6GHzChannelNumbers = Arrays.asList(1, 5, 9, 13, 17,
            21, 25, 29, 33, 37, 41, 45, 49, 53, 57, 61,
            65, 69, 73, 77, 81, 85, 89, 93);

    static final List<Integer> wiFi60GHzCenterFrequencies = Arrays.asList(58320, 60480, 62640, 64800, 66960,
            69120);
    static final List<Integer> wiFi60GHzChannelNumbers = Arrays.asList(1, 2, 3, 4, 5,
            6);

    static final List<Integer> cellChannels = Arrays.asList(237, 238, 239, 240, 241,
            242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252,
            253, 254, 255);

    static final List<Integer> cellFrequencies = Arrays.asList(2312, 2317, 2322, 2327, 2332,
            2337, 2342, 2347, 2352, 2357, 2362, 2367, 2372, 2377, 2382, 2387,
            2392, 2397, 2402);

    //ALIBI: full spec ad/ay: the linux kernel impl doesn't support these yet. https://en.wikipedia.org/wiki/List_of_WLAN_channels#60_GHz_(802.11ad/ay)
    //static final List<Integer> wiFi60GHzCenterFrequencies = Arrays.asList(new Integer[] {58320, 60480, 62640, 64800, 66960,69120, 59400, 61560, 63720, 65880, 68040, 60480, 62640, 64800, 66960, 61560, 63720, 65880});
    //static final List<Integer> wiFi60GHzChannelNumbers = Arrays.asList(new Integer[] {1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 17, 18, 19, 20, 25, 26, 27});

    //ALIBI: this will be both unit tests for the new code, and edge case detection for the old.
    //START: bad old code
    private static final Map<Integer, Integer> freqToChan;
    static {
        Map<Integer, Integer> freqToChanTemp = new HashMap<>();
        for (int i = 237; i <= 255; i++) {
            //I can find no documentation regarding channels 237-255
            freqToChanTemp.put(2312 + 5 * (i - 237), i);
        }

        for (int i = 0; i <= 13; i++) {
            freqToChanTemp.put(2407 + (5 * i), i);
        }
        freqToChanTemp.put(2484, 14);

        freqToChanTemp.put(5170, 34);
        freqToChanTemp.put(5180, 36);
        freqToChanTemp.put(5190, 38);
        freqToChanTemp.put(5200, 40);
        freqToChanTemp.put(5210, 42);
        freqToChanTemp.put(5220, 44);
        freqToChanTemp.put(5230, 46);
        freqToChanTemp.put(5240, 48);
        freqToChanTemp.put(5260, 52);
        freqToChanTemp.put(5280, 56);
        freqToChanTemp.put(5300, 60);
        freqToChanTemp.put(5320, 64);

        freqToChanTemp.put(5500, 100);
        freqToChanTemp.put(5520, 104);
        freqToChanTemp.put(5540, 108);
        freqToChanTemp.put(5560, 112);
        freqToChanTemp.put(5580, 116);
        freqToChanTemp.put(5600, 120);
        freqToChanTemp.put(5620, 124);
        freqToChanTemp.put(5640, 128);
        freqToChanTemp.put(5660, 132);
        freqToChanTemp.put(5680, 136);
        freqToChanTemp.put(5700, 140);

        freqToChanTemp.put(5745, 149);
        freqToChanTemp.put(5765, 153);
        freqToChanTemp.put(5785, 157);
        freqToChanTemp.put(5805, 161);
        freqToChanTemp.put(5825, 165);

        freqToChan = Collections.unmodifiableMap(freqToChanTemp);
    }
    public static Integer frequencyForWiFiChannel(final int channel) {
        for (Map.Entry<Integer, Integer> entry : freqToChan.entrySet()) {
            if (entry.getValue().equals(channel)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @BeforeClass
    public static void setupTestSets() {
        System.out.println("Checking WiFi Channel test data...");
        Assert.assertEquals(wiFi24GHzCenterFrequencies.size(), wiFi24GHzChannelNumbers.size());
        Assert.assertEquals(wiFi5GHzCenterFrequencies.size(), wiFi5GHzChannelNumbers.size());
        Assert.assertEquals(wiFi6GHzCenterFrequencies.size(), wiFi6GHzChannelNumbers.size());
        Assert.assertEquals(wiFi60GHzCenterFrequencies.size(), wiFi60GHzChannelNumbers.size());
        System.out.println("...ready to test.");
    }
    //END bad old code.

    @Test
    public void test24GHzChannels() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi24GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.channelForWiFiFrequencyMhz(wiFi24GHzCenterFrequencies.get(i)), wiFi24GHzChannelNumbers.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 2.4GHz freq->chan in "+totalMillis+"ms");
    }

    @Test
    public void test5GHzChannels() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi5GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.channelForWiFiFrequencyMhz(wiFi5GHzCenterFrequencies.get(i)), wiFi5GHzChannelNumbers.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 5GHz freq->chan in "+totalMillis+"ms");
    }

    @Test
    public void test6GHzChannels() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi6GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.channelForWiFiFrequencyMhz(wiFi6GHzCenterFrequencies.get(i)), wiFi6GHzChannelNumbers.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 6GHz freq->chan in "+totalMillis+"ms");
    }

    @Test
    public void test60GHzChannels() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi60GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.channelForWiFiFrequencyMhz(wiFi60GHzCenterFrequencies.get(i)), wiFi60GHzChannelNumbers.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 60GHz freq->chan in "+totalMillis+"ms");
    }

    @Test
    public void test24GHzFreqs() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi24GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.frequencyMHzForWiFiChannel(wiFi24GHzChannelNumbers.get(i), Network.NetworkBand.WIFI_2_4_GHZ), wiFi24GHzCenterFrequencies.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 2.4GHz chan->freq in "+totalMillis+"ms");
    }

    @Test
    public void test5GHzFreqs() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi5GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.frequencyMHzForWiFiChannel(wiFi5GHzChannelNumbers.get(i), Network.NetworkBand.WIFI_5_GHZ), wiFi5GHzCenterFrequencies.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 5GHz chan->freq in "+totalMillis+"ms");
    }

    @Test
    public void test6GHzFreqs() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi6GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.frequencyMHzForWiFiChannel(wiFi6GHzChannelNumbers.get(i), Network.NetworkBand.WIFI_6_GHZ), wiFi6GHzCenterFrequencies.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 6GHz chan->freq in "+totalMillis+"ms");
    }

    @Test
    public void test60GHzFreqs() {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi60GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(Network.frequencyMHzForWiFiChannel(wiFi60GHzChannelNumbers.get(i), Network.NetworkBand.WIFI_60_GHZ), wiFi60GHzCenterFrequencies.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("executed 60GHz chan->freq in "+totalMillis+"ms");
    }

    // apparently this is worth saving from the old frequencies setup, for certain cell frequencies.
    @Test
    public void testCellChannelBackwardsCompat() {
        for (int i = 0; i < cellChannels.size(); i++) {
            Assert.assertEquals(cellChannels.get(i), Network.channelForWiFiFrequencyMhz(cellFrequencies.get(i)));
        }
    }

    @Test
    public void cellFreqBackwardsCompat() {
        for (int i = 0; i < cellFrequencies.size(); i++) {
            Assert.assertEquals(cellFrequencies.get(i),Network.frequencyMHzForWiFiChannel(cellChannels.get(i), Network.NetworkBand.CELL_2_3_GHZ));
        }
    }

    @Test
    public void testOld24GHzFreqs() {
        //ALIBI: this worked
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi24GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(frequencyForWiFiChannel(wiFi24GHzChannelNumbers.get(i)), wiFi24GHzCenterFrequencies.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("[OLD] executed 2.4GHz chan->freq in "+totalMillis+"ms");
    }

    /*@Test
    public void testOld5GHzFreqs() {
        for (int i = 0; i < wiFi5GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(frequencyForWiFiChannel(wiFi5GHzChannelNumbers.get(i)), wiFi5GHzCenterFrequencies.get(i));
        }
        // Channels 7,8,9,11,12 alias
    }

    6GHz and 60GHz will both fail spectacularly due to aliasing.
    */

    @Test
    public void testOld24GHzChannels() {
        //ALIBI: this worked
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < wiFi24GHzChannelNumbers.size(); i++) {
            Assert.assertEquals(freqToChan.get(wiFi24GHzCenterFrequencies.get(i)), wiFi24GHzChannelNumbers.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("[OLD] executed 2.4GHz freq->chan in "+totalMillis+"ms");
    }

    @Test
    public void testOld5GHzChannels() {
        //ALIBI: channels 7, 8, 9, 11, 12, 16, 32, 50, 54, 58, 62, 68, 96, 102, 106, 110, 114, 118, 122, 126, 134, 138, 142, 144, 151, 155, 159, 163, 167, 169-196 were unsupported in the old code
        long startMillis = System.currentTimeMillis();
        for (int i = 7; i < 14; i++) {
            Assert.assertEquals(freqToChan.get(wiFi5GHzCenterFrequencies.get(i)), wiFi5GHzChannelNumbers.get(i));
        }

        for (int i = 16; i <= 22; i+=2) {
            Assert.assertEquals(freqToChan.get(wiFi5GHzCenterFrequencies.get(i)), wiFi5GHzChannelNumbers.get(i));
        }

        for (int i = 25; i <= 39; i+=2) {
            Assert.assertEquals(freqToChan.get(wiFi5GHzCenterFrequencies.get(i)), wiFi5GHzChannelNumbers.get(i));
        }

        for (int i = 40; i <= 44; i+=2) {
            Assert.assertEquals(freqToChan.get(wiFi5GHzCenterFrequencies.get(i)), wiFi5GHzChannelNumbers.get(i));
        }

        for (int i = 47; i <= 55; i+=2) {
            Assert.assertEquals(freqToChan.get(wiFi5GHzCenterFrequencies.get(i)), wiFi5GHzChannelNumbers.get(i));
        }
        long totalMillis = System.currentTimeMillis()-startMillis;
        System.out.println("[OLD] executed 5GHz freq->chan in "+totalMillis+"ms");
    }

    @Test
    public void testOldCellChannelBackwardsCompat() {
        for (int i = 0; i < cellChannels.size(); i++) {
            Assert.assertEquals(cellChannels.get(i),freqToChan.get(cellFrequencies.get(i)));
        }
    }

}
