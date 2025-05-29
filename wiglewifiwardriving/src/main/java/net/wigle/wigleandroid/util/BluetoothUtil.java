package net.wigle.wigleandroid.util;

import net.wigle.wigleandroid.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by bobzilla on 12/20/15
 * Adapted from: <a href="http://stackoverflow.com/questions/26290640/android-bluetoothdevice-getname-return-null">...</a>
 */
public class BluetoothUtil {

    public static final Map<UUID, Integer> BLE_STRING_CHARACTERISTIC_UUIDS = new HashMap<>();
    static {
        BLE_STRING_CHARACTERISTIC_UUIDS.put(UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"), R.string.ble_model_title);
        BLE_STRING_CHARACTERISTIC_UUIDS.put(UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb"), R.string.ble_serial_title);
        BLE_STRING_CHARACTERISTIC_UUIDS.put(UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb"), R.string.ble_firmware_title);
        BLE_STRING_CHARACTERISTIC_UUIDS.put(UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb"), R.string.ble_hw_title);
        BLE_STRING_CHARACTERISTIC_UUIDS.put(UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb"), R.string.ble_sw_title);
        BLE_STRING_CHARACTERISTIC_UUIDS.put(UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb"), R.string.ble_mfgr_title);
    }

    public static final Map<UUID, Map<UUID, String>> BLE_SERVICE_CHARACTERISTIC_MAP = new HashMap<>();
    static {

        final Map<UUID, String> gapServiceMap = new HashMap<>();
        gapServiceMap.put(UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"),"GAP: Device name");
        gapServiceMap.put(UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb"),"GAP: Appearance");
        gapServiceMap.put(UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb"),"GAP: System ID");
        BLE_SERVICE_CHARACTERISTIC_MAP.put(UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"), gapServiceMap);

        //Disabled for now
        //final Map<UUID, String> timeServiceMap = new HashMap<>();
        //timeServiceMap.put(UUID.fromString("00002a08-0000-1000-8000-00805f9b34fb"),"TIME: Date Time");
        //timeServiceMap.put(UUID.fromString("00002a09-0000-1000-8000-00805f9b34fb"),"TIME: Day of week");
        //timeServiceMap.put(UUID.fromString("00002a0a-0000-1000-8000-00805f9b34fb"),"TIME: Day date time");
        //timeServiceMap.put(UUID.fromString("00002a0c-0000-1000-8000-00805f9b34fb"),"TIME: Exact time");
        // loads more here
        //BLE_SERVICE_CHARACTERISTIC_MAP.put(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"), timeServiceMap);

        //1808 GLUCOSE
        //   0x2a18 Glucose Measurement
        //1809 THERMOMETER
        //   0x2a1D Temperature Measurement / 0x2A1E Temperature Type...

        final Map<UUID, String> infoServiceMap = new HashMap<>();
        infoServiceMap.put(UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"),"INFO: Model number");
        infoServiceMap.put(UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb"),"INFO: Serial number");
        infoServiceMap.put(UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb"),"INFO: Firmware rev.");
        infoServiceMap.put(UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb"),"INFO: Hardware rev.");
        infoServiceMap.put(UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb"),"INFO: Software rev.");
        infoServiceMap.put(UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb"),"INFO: Mfgr name");
        infoServiceMap.put(UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb"),"INFO: PnP ID");
        BLE_SERVICE_CHARACTERISTIC_MAP.put(UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), infoServiceMap);

        //Disabled for now
        //final Map<UUID, String> heartServiceMap = new HashMap<>();
        //heartServiceMap.put(UUID.fromString("00002aa4-0000-1000-8000-00805f9b34fb"),"HR: Heart rate");
        //BLE_SERVICE_CHARACTERISTIC_MAP.put(UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb"), heartServiceMap);

        final Map<UUID, String> batteryServiceMap = new HashMap<>();
        batteryServiceMap.put(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"),"BAT: Battery level");
        BLE_SERVICE_CHARACTERISTIC_MAP.put(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"), batteryServiceMap);

        //Disabled for now
        //final Map<UUID, String> bpServiceMap = new HashMap<>();
        //bpServiceMap.put(UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb"),"BP: Blood Pressure");
        //bpServiceMap.put(UUID.fromString("00002a36-0000-1000-8000-00805f9b34fb"),"BP: Intermediate cuff pressure");
        //bpServiceMap.put(UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"),"BP: Heart rate measurement");
        //bpServiceMap.put(UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb"),"BP: Body sensor location");
        //BLE_SERVICE_CHARACTERISTIC_MAP.put(UUID.fromString("00001810-0000-1000-8000-00805f9b34fb"), bpServiceMap);

        //1812 HID
        //1814 RUNNING
        //1815 Automation IO
        //1816 Cycling Speed and Cadence
        //1818 Cycling Power
        //1819 Location and Navigation
        //181A Environmental Sensing
        //181B Body Composition
        //181C User Data
        //181D Weight Scale
        //181F Continuous Glucose Monitoring
        //1821 Indoor Positioning
        //1822 Pulse Oximeter
    }
    private static final int DATA_TYPE_FLAGS = 0x01;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
    private static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    private static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
    private static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
    private static final int DATA_TYPE_SERVICE_DATA_16_BIT = 0x16;
    private static final int DATA_TYPE_SERVICE_DATA_32_BIT = 0x20;
    private static final int DATA_TYPE_SERVICE_DATA_128_BIT = 0x21;
    private static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;

    public static final class BleAdvertisedData {
        private List<UUID> mUuids;
        private String mName;
        public BleAdvertisedData(List<UUID> uuids, String name){
            mUuids = uuids;
            mName = name;
        }

        public List<UUID> getUuids(){
            return mUuids;
        }

        public String getName(){
            return mName;
        }
    }

    private final static String TAG=BluetoothUtil.class.getSimpleName();
    public static BleAdvertisedData parseAdvertisedData(byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();
        String name = null;
        if( advertisedData == null ){
            return new BleAdvertisedData(uuids, name);
        }

        ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            byte length = buffer.get();
            if (length == 0) break;
            try {
                byte type = buffer.get();
                switch (type) {
                    case DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL: // Partial list of 16-bit UUIDs
                    case DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE: // Complete list of 16-bit UUIDs
                        //MainActivity.info("16-bit uuid");
                        while (length >= 2) {
                            //TODO: java.nio.BufferUnderflowException
                            short devType = buffer.getShort();
                            uuids.add(UUID.fromString(String.format(
                                    "%08x-0000-1000-8000-00805f9b34fb", devType)));
                            length -= 2;
                        }
                        break;
                    case DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL: // Partial list of 16-bit UUIDs
                    case DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE: // Complete list of 16-bit UUIDs
                        //MainActivity.info("32-bit uuid");
                        while (length >= 4) {
                            //TODO: java.nio.BufferUnderflowException
                            int devType = buffer.getShort();
                            uuids.add(UUID.fromString(String.format(
                                    "%16x-0000-1000-8000-00805f9b34fb", devType)));
                            length -= 4;
                        }
                        break;
                    case DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL: // Partial list of 128-bit UUIDs
                    case DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE: // Complete list of 128-bit UUIDs
                        //MainActivity.info("128-bit uuid");
                        while (length >= 16) {
                            long lsb = buffer.getLong();
                            long msb = buffer.getLong();
                            uuids.add(new UUID(msb, lsb));
                            length -= 16;
                        }
                        break;
                    case DATA_TYPE_LOCAL_NAME_SHORT:
                    case DATA_TYPE_LOCAL_NAME_COMPLETE:
                        //MainActivity.info("Name");
                        byte[] nameBytes = new byte[length-1];
                        buffer.get(nameBytes);
                        name = new String(nameBytes, StandardCharsets.UTF_8);
                        break;
                    default:
                            //TODO: Bad position -40/23
                            buffer.position(buffer.position() + length - 1);
                        break;
                }
            } catch (Exception ex) {
                Logging.error("Error parsing advertised BLE data: ", ex);
            }

        }
        return new BleAdvertisedData(uuids, name);
    }

    /**
     * Model class for GATT Characteristic Appearance Category
     */
    public static class AppearanceCategory {
        String name;
        Map<Integer, String> subcategories;

        public AppearanceCategory(String name, Map<Integer, String> subcategories) {
            this.name = name;
            this.subcategories = subcategories;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<Integer, String> getSubcategories() {
            return subcategories;
        }

        public void setSubcategory(Map<Integer, String> subcategories) {
            this.subcategories = subcategories;
        }
    }

    /**
     * Utility method to get an int for a GATT 16 bit Uint
     * @param bytes the two byte value
     * @return the integer value
     */
    public static int getGattUint16(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb = bb.order(ByteOrder.LITTLE_ENDIAN); //ALIBI: GATTributes can be presumed little-endian
        short s = bb.getShort(); //signed short
        return 0xFFFF & s;
    }

    /**
     * Utility method to get an int for a GATT 16 bit Uint
     * @param intByte the byte value
     * @return the integer value
     */
    public static int getGattUint8(byte intByte) {
        return 0xFF & intByte;
    }

}