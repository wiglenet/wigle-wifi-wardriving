package net.wigle.wigleandroid.util;

import net.wigle.m8b.M8bProgress;
import net.wigle.m8b.geodesy.mgrs;
import net.wigle.m8b.geodesy.utm;
import net.wigle.m8b.siphash.SipHash;
import net.wigle.m8b.siphash.SipKey;
import net.wigle.wigleandroid.MainActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MagicEightUtil {

    /**
     * This is horrible but the lack of available 1.8 support on older android versions (WiGLE's
     * only available path due to the Android-Pie-and-up prohibition on WiFi scanning) results in
     * significant incompatibilities attempting to port the https://github.com/wiglenet/m8b project
     * to Android. Since we want users to be able to export, share, and combine positioning
     * artifacts without need for a central database, this is a port of the m8b generation mechanism
     * to Android SDK 14 and up. Thanks for nothing, Android team.
     *
     * Feel free to star the issue: https://issuetracker.google.com/issues/112688545
     *
     * @param linesReaderIn
     * @param out
     * @param slicebits
     * @param tabs
     * @throws IOException
     */
    public static void generate(final BufferedReader linesReaderIn,
                                final FileChannel out, final int slicebits, final boolean tabs,
                                M8bProgress progress)
            throws IOException {
        // just zerokey it. we're not trying to avoid collisions.
        SipKey sipkey = new SipKey(new byte[16]);
        byte[] macbytes = new byte[6];
        Map<Integer,Set<mgrs>> mjg = new TreeMap<Integer,Set<mgrs>>();

        int non_utm=0;

        int records = 0;
        int lines = 0;

        //TODO: bounds checking on slicebits (1-30)

        char sep = tabs ? '\t' : '|';

        String line;
        while((line = linesReaderIn.readLine()) != null){
            //bssid|bestlat|bestlon
            //8e:15:44:60:50:ac|40.00900289|-75.21358834

            lines++;

            int b1 = line.indexOf(sep);
            int b2 = line.indexOf(sep,b1+1);

            String latstr = line.substring(b1+1,b2);
            String lonstr = line.substring(b2+1);

            double lat = Double.parseDouble(latstr);
            double lon = Double.parseDouble(lonstr);

            if (!(-80<=lat && lat<=84)) {
                non_utm++;
                continue;
            }

            mgrs m = mgrs.fromUtm(utm.fromLatLon(lat,lon));

            String slice2 = line.substring(0,17);
            Integer kslice2 = extractKeyFrom(slice2,macbytes,sipkey,slicebits);

            Set<mgrs> locs = mjg.get(kslice2);
            if (locs==null){
                locs = new HashSet<mgrs>();
                mjg.put(kslice2,locs);
            }
            if(locs.add(m)){
                records++;
            }

            if (lines % 100 == 0) {
                progress.handleGenerationProgress(lines);
            }
        }
        // mjg is complete, write out to pairfile

        Charset utf8  = Charset.forName("UTF-8");

        ByteBuffer bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN); // screw you, java

        // write header
        bb.put("MJG\n".getBytes(utf8)); // magic number
        bb.put("2\n".getBytes(utf8)); // version
        bb.put("SIP-2-4\n".getBytes(utf8)); // hash
        bb.put(String.format("%x\n",slicebits).getBytes(utf8)); // slice bits (hex)
        bb.put("MGRS-1000\n".getBytes(utf8)); // coords
        bb.put("4\n".getBytes(utf8)); // id size in bytes (hex)
        bb.put("9\n".getBytes(utf8)); // coords size in bytes (hex)
        bb.put(String.format("%x\n",records).getBytes(utf8)); // record count (hex)

        int recordsize = 4+9;

        bb.flip();
        while (bb.hasRemaining()){
            out.write(bb);
        }

        // back to fill mode
        bb.clear();
        byte[] mstr = new byte[9];
        int outElements = 0;
        for ( Map.Entry<Integer,Set<mgrs>> me : mjg.entrySet()) {
            int key = me.getKey().intValue();
            for ( mgrs m : me.getValue() ) {
                if (bb.remaining() < recordsize ) {
                    bb.flip();
                    while (bb.hasRemaining()){
                        out.write(bb);
                    }
                    bb.clear();
                }
                m.populateBytes(mstr);
                bb.putInt(key).put(mstr);
            }
            outElements++;
            if (outElements % 100 == 0) {
                progress.handleWriteProgress(outElements, records);
                //DEBUG: MainActivity.info("progress: " + outElements + "/" + mjg.size());
            }
        }

        bb.flip();
        while (bb.hasRemaining()) {
            out.write(bb);
        }

        bb.clear();
        out.close();
    }

    // 8e:15:44:60:50:ac -> [0x8e, 0x15, 0x44, 0x60, 0x50, 0xac] -> hash masked off at n-th LS bit
    /**
     * read mac from text string into macbytes. run siphahsh(skipkeky,macbytes) and mask
     * the low-n bits (n=10 would only produce integer less than 1024)
     */
    public static Integer extractKeyFrom(String mac, byte[] macbytes, SipKey sipkey, int n) {

        Integer kslice2 = Integer.valueOf( extractIntKeyFrom(mac,macbytes,sipkey,n) );
        return kslice2;
    }

    /**
     * read mac from text string into macbytes. run siphahsh(skipkeky,macbytes) and mask
     * the low-n bits (n=10 would only produce integer less than 1024)
     */
    public static int extractIntKeyFrom(String mac, byte[] macbytes, SipKey sipkey, int n) {

        for ( int i = 0; i < macbytes.length; i++ ) {
            char hi = mac.charAt(i*3);
            char lo = mac.charAt(i*3+1);
            byte hib = (byte) ((nybbleFrom(hi) << 4) & 0xf0);
            byte lob = nybbleFrom(lo);
            macbytes[i] = (byte)( hib | lob);
        }

        long siph = SipHash.digest(sipkey, macbytes);

        long mask = (1L << n ) - 1;

        return (int)(siph & mask);
    }

    /**
     * return the nybble value of hex char c
     */
    private static byte nybbleFrom(char c){
        switch(c){
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return (byte)(c - '0');
            case 'A':
            case 'a':
                return 10;
            case 'B':
            case 'b':
                return 11;
            case 'C':
            case 'c':
                return 12;
            case 'D':
            case 'd':
                return 13;
            case 'E':
            case 'e':
                return 14;
            case 'F':
            case 'f':
                return 15;
            default:
                throw new IllegalArgumentException("non hex char '"+c+"'");
        }
    }

}
