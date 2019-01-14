package net.wigle.wigleandroid.util;

import net.wigle.m8b.siphash.SipHash;
import net.wigle.m8b.siphash.SipKey;

public class MagicEightUtil {

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
