package net.wigle.m8b.siphash;

/**
 * from https://github.com/emboss/siphash-java
 * @author <a href="mailto:Martin.Bosslet@googlemail.com">Martin Bosslet</a>
 */
public class SipKey {
    private final byte[] key;
    
    public SipKey(byte[] key) {
        if (key == null || key.length != 16)
            throw new RuntimeException("SipHash key must be 16 bytes");
        this.key = key;
    }
    
    long getLeftHalf() {
       return UnsignedInt64.binToIntOffset(key, 0); 
    }
    
    long getRightHalf() {
        return UnsignedInt64.binToIntOffset(key, 8); 
    }
}