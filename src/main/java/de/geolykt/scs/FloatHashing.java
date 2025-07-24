package de.geolykt.scs;

final class FloatHashing {
    static final long positionalRawHash(float x, float y) {
        return (((long) Float.floatToRawIntBits(x)) << 32) | ((long) Float.floatToRawIntBits(y)) & 0xFFFF_FFFFL;
    }
}
