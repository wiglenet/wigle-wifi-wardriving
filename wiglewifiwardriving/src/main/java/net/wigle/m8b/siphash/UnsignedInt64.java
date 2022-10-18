package net.wigle.m8b.siphash;

/**
 * from https://github.com/emboss/siphash-java
 * @author <a href="mailto:Martin.Bosslet@googlemail.com">Martin Bosslet</a>
 */
class UnsignedInt64 {
    private UnsignedInt64() {}
    
    public static long binToInt(byte[] b) {
        return  binToIntOffset(b, 0);
    }
    
    public static long binToIntOffset(byte[] b, int off) {
        return (((long) b[off    ]) & 0xff)     |
               (((long) b[off + 1]) & 0xff) << 8  |
               (((long) b[off + 2]) & 0xff) << 16 |
               (((long) b[off + 3]) & 0xff) << 24 |
               (((long) b[off + 4]) & 0xff) << 32 |
               (((long) b[off + 5]) & 0xff) << 40 |
               (((long) b[off + 6]) & 0xff) << 48 |
               (((long) b[off + 7]) & 0xff) << 56;
    }
    
    public static void intToBin(long l, byte[] b) {
        b[0] = (byte) ( l         & 0xff);
        b[1] = (byte) ((l >>> 8 ) & 0xff);
        b[2] = (byte) ((l >>> 16) & 0xff);
        b[3] = (byte) ((l >>> 24) & 0xff);
        b[4] = (byte) ((l >>> 32) & 0xff);
        b[5] = (byte) ((l >>> 40) & 0xff);
        b[6] = (byte) ((l >>> 48) & 0xff);
        b[7] = (byte) ((l >>> 56) & 0xff);
    }
    
    public static long rotateLeft(long l, int shift) {
        return (l << shift) | l >>> (64 - shift);
    }
}