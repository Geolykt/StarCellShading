package de.geolykt.scs;

final class Hashing {
    static final long positionalHashRawFloat(float x, float y) {
        return (((long) Float.floatToRawIntBits(x)) << 32) | ((long) Float.floatToRawIntBits(y)) & 0xFFFF_FFFFL;
    }

    static final long positionalHashInt(int a, int b) {
        return (((long) a) << 32) | ((long) b) & 0xFFFF_FFFFL;
    }
}
