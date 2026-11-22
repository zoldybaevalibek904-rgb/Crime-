package crimedistrict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Versioned save payload. Validate fully before applying it to the live city. */
final class Snapshot {
    private static final int MAGIC = 0x4344524d;
    private static final int VERSION = 2;

    static byte[] encode(District d) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        boolean recovery=d.combat.life!=Combat.NORMAL;
        out.writeShort(recovery?Street.HOSPITAL_X:d.heroX/District.UNIT);
        out.writeShort(recovery?Street.HOSPITAL_Y:d.heroY/District.UNIT);
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
                out.writeByte(a.hp);
            }
        }
        out.writeByte(recovery?100:d.combat.hp);
        out.writeInt(d.trade.money);
        out.writeByte(d.combat.owned);
        out.writeByte(d.combat.weapon);
        for (int i=2;i<4;i++) {
            out.writeByte(d.combat.magazine[i]);
            out.writeByte(d.combat.reserve[i]);
        }
        out.writeByte(d.trade.cars);
        out.writeByte(d.trade.visits);
        out.writeByte(recovery?-1:d.trade.job);
        out.writeByte(recovery?0:d.trade.progress);
        out.writeShort(recovery?0:d.trade.jobTime);
        out.close();
        return buffer.toByteArray();
    }

    static boolean decode(District d, byte[] data) {
        if (data == null) return false;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        try {
            if (in.readInt()!=MAGIC) return false;
            int version=in.readUnsignedByte();
            if (version<1 || version>VERSION) return false;
            int x = in.readUnsignedShort(), y = in.readUnsignedShort();
            int direction = in.readUnsignedByte(), job = in.readUnsignedByte();
            int stage = in.readUnsignedByte(), wanted = in.readUnsignedByte();
            boolean driving = in.readBoolean();
            int selected = in.readUnsignedByte(), count = in.readUnsignedByte();
            if (count != (version==1?Street.LEGACY_POPULATION:d.street.length) || selected >= count || !d.street[selected].vehicle ||
                direction > 3 || wanted > 3 || !Story.valid(job,stage)) return false;
            if (!d.freeStatic(x-4,y-3,8,6)) return false;
            int[] cars = new int[count*3];
            boolean[] claimed = new boolean[count];
            int[] vehicleHealth = new int[count];
            for (int i = 0; i < count; i++) {
                if (!in.readBoolean()) continue;
                int cx = in.readUnsignedShort(), cy = in.readUnsignedShort(), heading = in.readUnsignedByte();
                District.Actor a = d.street[i];
                if (!a.vehicle || a.role == Street.POLICE || heading > 3) return false;
                int w = District.halfWidth(heading,a.model), h = District.halfHeight(heading,a.model);
                if (!d.freeStatic(cx-w,cy-h,w*2,h*2)) return false;
                int health=version>=2?in.readUnsignedByte():100;
                if (health>100) return false;
                vehicleHealth[i]=health;
                claimed[i] = true;
                cars[i*3] = cx*District.UNIT;
                cars[i*3+1] = cy*District.UNIT;
                cars[i*3+2] = heading;
            }
            int hp=100,money=220,owned=1,weapon=0,pistol=0,pistolAmmo=0,smg=0,smgAmmo=0;
            int garage=1,visits=0,work=-1,progress=0,time=0;
            if (version>=2) {
                hp=in.readUnsignedByte();
                money=in.readInt();
                owned=in.readUnsignedByte();
                weapon=in.readUnsignedByte();
                pistol=in.readUnsignedByte();
                pistolAmmo=in.readUnsignedByte();
                smg=in.readUnsignedByte();
                smgAmmo=in.readUnsignedByte();
                garage=in.readUnsignedByte();
                visits=in.readUnsignedByte();
                work=in.readByte();
                progress=in.readUnsignedByte();
                time=in.readUnsignedShort();
                if (hp<1 || hp>100 || money<0 || money>99999 || (owned&1)==0 || owned>15 ||
                    weapon>3 || (owned&(1<<weapon))==0 || pistol>8 || smg>18 ||
                    pistolAmmo>240 || smgAmmo>240 || (garage&1)==0 || garage>15 ||
                    visits>1 || work<-1 || work>2 || (work>=0 && progress>=(work==2?4:2)) || time>1050) return false;
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
                a.hp = vehicleHealth[i];
                a.deferred=false;
            }
            d.combat.hp=hp;
            d.combat.owned=owned;
            d.combat.weapon=weapon;
            d.combat.magazine[2]=pistol; d.combat.reserve[2]=pistolAmmo;
            d.combat.magazine[3]=smg; d.combat.reserve[3]=smgAmmo;
            d.trade.money=money; d.trade.cars=garage; d.trade.visits=visits;
            d.trade.job=work; d.trade.progress=progress; d.trade.jobTime=time;
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
