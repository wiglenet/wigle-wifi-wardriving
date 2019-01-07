package net.wigle.m8b.siphash;

/**
 * from https://github.com/emboss/siphash-java
 * @author <a href="mailto:Martin.Bosslet@googlemail.com">Martin Bosslet</a>
 */
public class SipHash {
    public static long digest(SipKey key, byte[] data) {
        long m;
        State s = new State(key);
        int iter = data.length / 8;
        
        for(int i=0; i < iter; i++) {
            m = UnsignedInt64.binToIntOffset(data, i * 8);
            s.processBlock(m);
        }
        
        m = lastBlock(data, iter);
        s.processBlock(m);
        s.finish();
        return s.digest();
    }
    
    private static long lastBlock(byte[] data, int iter) {
        long last = ((long) data.length) << 56;
        int off = iter * 8;

        switch (data.length % 8) {
            case 7: 
                last |= ((long) data[off + 6]) << 48;
            case 6:
                last |= ((long) data[off + 5]) << 40;
            case 5:
                last |= ((long) data[off + 4]) << 32;
            case 4:
                last |= ((long) data[off + 3]) << 24;
            case 3:
                last |= ((long) data[off + 2]) << 16;
            case 2:
                last |= ((long) data[off + 1]) << 8;
            case 1:
                last |= (long) data[off];
                break;
            case 0:
                break;
        }
        return last;
    }
    
    private static class State {
        private long v0;
        private long v1;
        private long v2;
        private long v3;
        
        public State(SipKey key) {
            v0 = 0x736f6d6570736575L;
            v1 = 0x646f72616e646f6dL;
            v2 = 0x6c7967656e657261L;
            v3 = 0x7465646279746573L;
            
            long k0 = key.getLeftHalf();
            long k1 = key.getRightHalf();
            
            v0 ^= k0;
            v1 ^= k1;
            v2 ^= k0;
            v3 ^= k1;
        }

        private void compress() {
            v0 += v1;
            v2 += v3;
            v1 = UnsignedInt64.rotateLeft(v1, 13);
            v3 = UnsignedInt64.rotateLeft(v3, 16);
            v1 ^= v0;
            v3 ^= v2;
            v0 = UnsignedInt64.rotateLeft(v0, 32);
            v2 += v1;
            v0 += v3;
            v1 = UnsignedInt64.rotateLeft(v1, 17);
            v3 = UnsignedInt64.rotateLeft(v3, 21);
            v1 ^= v2;
            v3 ^= v0;
            v2 = UnsignedInt64.rotateLeft(v2, 32);
        }
        
        private void compressTimes(int times) {
            for (int i=0; i < times; i++) {
                compress();
            }
        }
        
        public void processBlock(long m) {
            v3 ^= m;
            compressTimes(2);
            v0 ^= m;
        }
        
        public void finish() {
            v2 ^= 0xff;
            compressTimes(4);
        }
        
        public long digest() {
            return v0 ^ v1 ^ v2 ^ v3;
        }
    }  
}