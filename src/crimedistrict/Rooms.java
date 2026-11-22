package crimedistrict;

/** The outdoor avatar remains at its entrance while the interior uses local coordinates. */
final class Rooms {
    static final String[] NAMES={"MARKET","GARAGE","WEAPON SUPPLY","CAFÉ","SAFEHOUSE"};
    static final int[] X={325,Trade.GARAGE_X,Trade.SHOP_X,1112,842};
    static final int[] Y={168,Trade.GARAGE_Y,Trade.SHOP_Y,1090,144};
    private static final int[][] BLOCKS={
        {18,25,44,53, 70,25,18,62, 104,25,18,62, 151,22,27,83},
        {18,26,46,27, 114,28,58,50, 17,93,40,21},
        {19,25,31,78, 65,24,106,25, 138,78,36,30},
        {19,23,148,24, 24,68,33,24, 116,68,33,24, 116,113,33,16},
        {18,27,53,38, 111,24,56,35, 21,93,52,27, 125,88,47,32}
    };
    private static final int[] COUNTER_X={137,85,93,85,97};
    private static final int[] COUNTER_Y={85,59,65,61,89};
    int id=-1,x,y,dir,phase,revision;

    void reset() { id=-1; x=y=phase=revision=0; dir=District.UP; }
    boolean active() { return id>=0; }

    int door(District d) {
        if(d.driving) return -1;
        int x=d.heroX/District.UNIT,y=d.heroY/District.UNIT,closest=-1,score=20*20;
        for(int i=0;i<X.length;i++) {
            int dx=x-X[i],dy=y-Y[i],dist=dx*dx+dy*dy;
            if(dist<score && d.canHit(d.heroX,d.heroY,X[i]*District.UNIT,Y[i]*District.UNIT)) {
                score=dist;
                closest=i;
            }
        }
        return closest;
    }

    boolean enter(District d) {
        int entry=door(d);
        if(entry<0) return false;
        id=entry;
        x=96; y=137;
        dir=District.UP;
        phase=0;
        revision++;
        d.walking=d.running=false;
        d.combat.windup=d.combat.attackPose=0;
        return true;
    }

    void move(int direction,boolean sprint) {
        if(direction<0) { phase=0; return; }
        dir=direction;
        int speed=sprint?3:2;
        int nx=x+District.DX[dir]*speed,ny=y+District.DY[dir]*speed;
        if(clear(id,nx,ny)) {
            x=nx; y=ny;
            phase=1+phase%24;
        } else phase=0;
    }

    int action(District d) {
        if(y>=128 && Math.abs(x-96)<15) {
            id=-1;
            phase=0;
            revision++;
            return 0;
        }
        if(counterDistance()>25) return 0;
        if(id==0) return Trade.MARKET;
        if(id==1) return Trade.GARAGE;
        if(id==2) return Trade.WEAPONS;
        if(id==3) return Trade.CAFE;
        d.combat.hp=100;
        revision++;
        d.trade.tell("Rested. Progress saved.");
        return 0;
    }

    boolean atExit() { return y>=128 && Math.abs(x-96)<15; }
    boolean atCounter() { return counterDistance()<=25; }
    private int counterDistance() { return Math.abs(x-COUNTER_X[id])+Math.abs(y-COUNTER_Y[id]); }

    static boolean clear(int id,int x,int y) {
        if(id<0 || id>=NAMES.length || x<13 || x>179 || y<14 || y>149) return false;
        int[] solids=BLOCKS[id];
        for(int i=0;i<solids.length;i+=4) {
            if(x+4>solids[i] && x-4<solids[i]+solids[i+2] &&
                y+3>solids[i+1] && y-3<solids[i+1]+solids[i+3]) return false;
        }
        return true;
    }
}
