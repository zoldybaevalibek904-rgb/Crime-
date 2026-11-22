package crimedistrict;

/** A single dispatch unit; road search and the incident queue have fixed storage. */
final class EMS {
    private static final int SIZE=43, CENTER=21, LIMIT=39;
    private static final int WAIT=1, APPROACH=2, SERVICE=3, LEAVE=4, FALLBACK=5;
    private final short[] parent=new short[SIZE*SIZE];
    private final byte[] distance=new byte[SIZE*SIZE];
    private final short[] queue=new short[SIZE*SIZE];
    private final int[] route=new int[LIMIT+1];
    private final District.Actor[] pending=new District.Actor[3];
    private final int[] deadline=new int[3];
    private int head,count,length,index,originX,originY,state,arrival,begin,limit,lastRoll;
    private int sequence=(int)(System.currentTimeMillis()&65535);
    private District.Actor patient,truck,medic;
    int arrivals, fallback, lastDelay;

    void reset(District d) {
        head=count=state=arrivals=fallback=lastDelay=0;
        patient=null;
        for (int i=0;i<pending.length;i++) pending[i]=null;
        for (int i=0;i<d.street.length;i++) {
            District.Actor a=d.street[i];
            if (a.role==Street.EMS) truck=a;
            if (a.role==Street.MEDIC) medic=a;
        }
    }

    void request(District d,District.Actor victim) {
        if (count==pending.length) return;
        sequence=(sequence*25173+13849)&65535;
        int slot=(head+count)%pending.length;
        pending[slot]=victim;
        int delay=300+sequence%301;
        if (delay==lastRoll) delay=300+(delay-299)%301;
        lastRoll=delay;
        deadline[slot]=d.ticks+delay;
        count++;
    }

    void tick(District d) {
        if (state==0 && count>0) {
            patient=pending[head];
            pending[head]=null;
            arrival=deadline[head];
            head=(head+1)%pending.length;
            count--;
            if (patient.hp>0 || patient.removed) return;
            lastDelay=arrival-patient.fallen;
            if (arrival-d.ticks<30 || !plan(d)) {
                state=FALLBACK;
                limit=Math.max(arrival,d.ticks+120);
            } else {
                state=WAIT;
                begin=arrival-(length-1)*6;
            }
        }
        if (state==WAIT && d.ticks>=begin) {
            int position=route[0];
            int x=px(position)*District.UNIT,y=py(position)*District.UNIT;
            int heading=heading(px(route[1])-px(position),py(route[1])-py(position));
            if (d.onCamera(x,y) || !d.vehicleSpace(truck,x,y,heading)) {
                if (d.ticks>=arrival) { state=FALLBACK; limit=d.ticks+100; }
                return;
            }
            truck.x=x; truck.y=y; truck.dir=heading;
            truck.deferred=false;
            index=1;
            state=APPROACH;
        }
        if (state==APPROACH) {
            if (d.ticks>Math.min(arrival+90,patient.fallen+600)) {
                state=FALLBACK; limit=d.ticks+100;
                return;
            }
            if (index<length) {
                if (drive(d,route[index])) index++;
            } else if (d.ticks>=arrival) {
                arrivals++;
                state=SERVICE;
                limit=d.ticks+100;
                for (int i=0;i<4;i++) {
                    int x=truck.x+District.DX[i]*13*District.UNIT;
                    int y=truck.y+District.DY[i]*13*District.UNIT;
                    if (!d.pedestrianSpace(medic,x,y)) continue;
                    medic.x=x; medic.y=y; medic.dir=i;
                    medic.deferred=false; break;
                }
            }
        } else if (state==SERVICE) {
            if (!medic.deferred && d.ticks<limit-28) d.emsWalk(medic,patient.x,patient.y);
            if (d.ticks==limit-28) removePatient();
            if (!medic.deferred && d.ticks>=limit-28) d.emsWalk(medic,truck.x,truck.y);
            if (d.ticks>=limit) {
                medic.deferred=true;
                state=LEAVE;
                index=length-2;
                limit=d.ticks+420;
            }
        } else if (state==LEAVE) {
            if (!d.onCamera(truck.x,truck.y)) {
                truck.deferred=true;
                state=0;
            } else if (index>=0 && drive(d,route[index])) index--;
            else if (d.ticks>=limit && index<0) truck.speed=0;
        } else if (state==FALLBACK && d.ticks>=limit) {
            if (!d.onCamera(patient.x,patient.y)) removePatient();
            fallback++;
            if (truck.deferred) state=0;
            else {
                index=Math.max(0,index-1); state=LEAVE; limit=d.ticks+420;
            }
        }
    }

    private void removePatient() {
        patient.deferred=true;
        patient.removed=true;
    }

    private boolean drive(District d,int node) {
        int x=px(node)*District.UNIT,y=py(node)*District.UNIT;
        int dx=x-truck.x,dy=y-truck.y;
        if (dx==0 && dy==0) return true;
        int heading=heading(dx,dy);
        int advance=Math.min(768,Math.abs(dx)+Math.abs(dy));
        int nx=truck.x+District.DX[heading]*advance,ny=truck.y+District.DY[heading]*advance;
        if (!d.vehicleSpace(truck,nx,ny,heading)) {
            truck.speed=0;
            return false;
        }
        truck.dir=heading;
        truck.x=nx;
        truck.y=ny;
        truck.speed=advance;
        return nx==x && ny==y;
    }

    private boolean plan(District d) {
        int x=patient.x/District.UNIT/16,y=patient.y/District.UNIT/16;
        originX=x-CENTER; originY=y-CENTER;
        int goal=-1,best=121;
        for (int dy=-6;dy<=6;dy++) for (int dx=-6;dx<=6;dx++) {
            int cost=dx*dx+dy*dy;
            if (cost>=best || !lane(d,x+dx,y+dy)) continue;
            goal=(CENTER+dy)*SIZE+CENTER+dx;
            best=cost;
        }
        if (goal<0) return false;
        for (int i=0;i<parent.length;i++) parent[i]=-2;
        int lo=0,hi=0,spawn=-1;
        queue[hi++]=(short)goal;
        parent[goal]=-1;
        distance[goal]=0;
        while(lo<hi) {
            int node=queue[lo++],steps=distance[node];
            int xx=node%SIZE,yy=node/SIZE;
            if (steps>=13 && !d.onCamera(px(node)*District.UNIT,py(node)*District.UNIT)) {
                spawn=node;
                break;
            }
            if (steps>=LIMIT-1) continue;
            for (int dir=0;dir<4;dir++) {
                int nx=xx+District.DX[dir],ny=yy+District.DY[dir];
                if(nx<0||ny<0||nx>=SIZE||ny>=SIZE) continue;
                int next=ny*SIZE+nx;
                if(parent[next]!=-2 || !lane(d,nx+originX,ny+originY)) continue;
                parent[next]=(short)node;
                distance[next]=(byte)(steps+1);
                queue[hi++]=(short)next;
            }
        }
        if(spawn<0) return false;
        length=0;
        for(int node=spawn;node>=0;node=parent[node]) route[length++]=node;
        return length>1;
    }

    private static boolean lane(District d,int col,int row) {
        if(col<1||row<1||col>=d.columns-1||row>=d.rows-1||d.zoneAt(col,row)!=District.ROAD) return false;
        int x=col*16+8,y=row*16+8;
        return d.freeStatic(x-11,y-22,22,44) && d.freeStatic(x-22,y-11,44,22);
    }

    private int px(int node) { return (originX+node%SIZE)*16+8; }
    private int py(int node) { return (originY+node/SIZE)*16+8; }
    private static int heading(int dx,int dy) {
        return dx==0?(dy<0?District.UP:District.DOWN):(dx<0?District.LEFT:District.RIGHT);
    }
}
