package net.wigle.wigleandroid.util;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by bobzilla on 12/20/15
 * Adapted from: http://stackoverflow.com/questions/26290640/android-bluetoothdevice-getname-return-null
 */
public class BluetoothUtil {

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
                        try {
                            name = new String(nameBytes, "utf-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
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
}