package crimedistrict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Versioned save payload. Validate fully before applying it to the live city. */
final class Snapshot {
    private static final int MAGIC = 0x4344524d;
    private static final int VERSION = 1;

    static byte[] encode(District d) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        out.writeShort(d.heroX/District.UNIT);
        out.writeShort(d.heroY/District.UNIT);
        out.writeByte(d.heroDir);
        out.writeByte(d.story.chapter);
        out.writeByte(d.story.stage);
        out.writeByte(d.wanted);
        out.writeBoolean(d.driving);
        int selected = 0;
        for (int i = 0; i < d.street.length; i++) if (d.street[i] == d.car) selected = i;
        out.writeByte(selected);
        out.writeByte(d.street.length);
        for (int i = 0; i < d.street.length; i++) {
            District.Actor a = d.street[i];
            boolean store = a.vehicle && a.claimed;
            out.writeBoolean(store);
            if (store) {
                out.writeShort(a.x/District.UNIT);
                out.writeShort(a.y/District.UNIT);
                out.writeByte(a.dir);
            }
        }
        out.close();
        return buffer.toByteArray();
    }

    static boolean decode(District d, byte[] data) {
        if (data == null) return false;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        try {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) return false;
            int x = in.readUnsignedShort(), y = in.readUnsignedShort();
            int direction = in.readUnsignedByte(), job = in.readUnsignedByte();
            int stage = in.readUnsignedByte(), wanted = in.readUnsignedByte();
            boolean driving = in.readBoolean();
            int selected = in.readUnsignedByte(), count = in.readUnsignedByte();
            if (count != d.street.length || selected >= count || !d.street[selected].vehicle ||
                direction > 3 || wanted > 3 || !Story.valid(job,stage)) return false;
            if (!d.freeStatic(x-4,y-3,8,6)) return false;
            int[] cars = new int[count*3];
            boolean[] claimed = new boolean[count];
            for (int i = 0; i < count; i++) {
                if (!in.readBoolean()) continue;
                int cx = in.readUnsignedShort(), cy = in.readUnsignedShort(), heading = in.readUnsignedByte();
                District.Actor a = d.street[i];
                if (!a.vehicle || a.role == Street.POLICE || heading > 3) return false;
                int w = District.halfWidth(heading,a.model), h = District.halfHeight(heading,a.model);
                if (!d.freeStatic(cx-w,cy-h,w*2,h*2)) return false;
                claimed[i] = true;
                cars[i*3] = cx*District.UNIT;
                cars[i*3+1] = cy*District.UNIT;
                cars[i*3+2] = heading;
            }
            if (in.available() != 0 || driving && !claimed[selected]) return false;
            d.reset();
            d.heroX = x*District.UNIT;
            d.heroY = y*District.UNIT;
            d.heroDir = direction;
            d.car = d.street[selected];
            d.driving = driving;
            for (int i = 0; i < count; i++) {
                if (!claimed[i]) continue;
                District.Actor a = d.street[i];
                a.claimed = true;
                a.x = cars[i*3];
                a.y = cars[i*3+1];
                a.dir = cars[i*3+2];
            }
            d.deferObstructedSpawns();
            d.wanted = wanted;
            d.story.restore(job,stage);
            d.cameraX = d.focusX();
            d.cameraY = d.focusY();
            return true;
        } catch (IOException invalid) {
            return false;
        }
    }
}
