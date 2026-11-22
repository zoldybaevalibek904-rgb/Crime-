package crimedistrict;

import java.io.DataInputStream;
import java.io.IOException;

/** Packed 9-bit tiles arranged in chunks, with a small reusable decoded cache. */
final class Tiles {
    final int columns, rows;
    private final byte[] packed;
    private final short[][] cache = new short[12][256];
    private final int[] keys = new int[12];
    private final int[] used = new int[12];
    private int age, last = -1;

    Tiles(DataInputStream in) throws IOException {
        columns = in.readUnsignedByte();
        rows = in.readUnsignedByte();
        if (columns == 0 || rows == 0 || columns%16 != 0 || rows%16 != 0)
            throw new IOException("Invalid chunk geometry");
        packed = new byte[columns*rows*9/8];
        in.readFully(packed);
        for (int i = 0; i < keys.length; i++) keys[i] = -1;
    }

    int get(int x, int y) {
        int id = (y>>4)*(columns>>4)+(x>>4);
        int slot = last;
        if (slot < 0 || keys[slot] != id) slot = acquire(id);
        return cache[slot][(y&15)*16+(x&15)];
    }

    private int acquire(int id) {
        int slot = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == id) {
                used[i] = ++age;
                return last = i;
            }
            if (used[i] < used[slot]) slot = i;
        }
        int offset = id*288;
        short[] target = cache[slot];
        for (int i = 0; i < target.length; i++) {
            int bit = i*9, p = offset+(bit>>3);
            target[i] = (short)((((packed[p]&255)|((packed[p+1]&255)<<8)) >> (bit&7))&511);
        }
        keys[slot] = id;
        used[slot] = ++age;
        return last = slot;
    }
}
