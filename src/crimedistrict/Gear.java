package crimedistrict;

final class Gear {
    static final int CASH=1,BAT=2,PISTOL=3,SMG=4,AMMO=5,MEDKIT=6;
    private static final String[] LABELS={"","CASH","BAT","PISTOL","SMG","AMMO","MEDKIT"};
    final int[] x=new int[18], y=new int[18], type=new int[18], amount=new int[18], until=new int[18];
    int medkits, token, red, iron, revision;
    private int cursor;

    void reset() {
        medkits=1;
        token=red=iron=revision=cursor=0;
        for(int i=0;i<type.length;i++) type[i]=0;
    }

    void drop(District d,District.Actor a) {
        int x=a.x/District.UNIT,y=a.y/District.UNIT;
        int weapon=a.arms(d.wanted);
        spawn(d,x,y,CASH,a.role==Street.POLICE?16:10+(a.model*3)%27);
        if(weapon>0) {
            int xx=x+9;
            if(!d.freeStatic(xx-2,y-2,4,4) || !d.canHit(x*District.UNIT,y*District.UNIT,xx*District.UNIT,y*District.UNIT)) xx=x;
            spawn(d,xx,y,weapon+1,weapon==1?0:Combat.CAPACITY[weapon]);
            if(weapon>=2) {
                int yy=d.freeStatic(x-2,y+7,4,4) && d.canHit(x*District.UNIT,y*District.UNIT,
                         x*District.UNIT,(y+9)*District.UNIT)?y+9:y;
                spawn(d,x,yy,AMMO,Combat.CAPACITY[weapon]);
            }
        }
        if(a.role==Street.RED) red=Math.max(-8,red-2);
        if(a.role==Street.IRON) iron=Math.max(-8,iron-2);
        revision++;
    }

    void spawn(District d,int wx,int wy,int item,int n) {
        for(int j=0;j<type.length;j++) {
            int i=(cursor+j)%type.length;
            if(type[i]!=0 && d.ticks<until[i]) continue;
            cursor=(i+1)%type.length;
            x[i]=wx;
            y[i]=wy;
            type[i]=item;
            amount[i]=n;
            until[i]=d.ticks+1800;
            return;
        }
    }

    boolean visible(District d,int i) { return type[i]!=0 && d.ticks<until[i]; }

    int nearby(District d) {
        int px=d.heroX/District.UNIT,py=d.heroY/District.UNIT,target=-1,best=20*20;
        for(int i=0;i<type.length;i++) {
            if(!visible(d,i)) continue;
            int dx=x[i]-px,dy=y[i]-py,score=dx*dx+dy*dy;
            if(score<best && d.canHit(d.heroX,d.heroY,x[i]*District.UNIT,y[i]*District.UNIT)) {
                target=i;
                best=score;
            }
        }
        return target;
    }

    String label(int i) { return LABELS[type[i]]; }

    boolean take(District d) {
        int i=nearby(d);
        if(i<0) return false;
        int item=type[i];
        if(item==CASH) d.trade.award(amount[i]);
        else if(item==MEDKIT) medkits=Math.min(9,medkits+1);
        else if(item==AMMO) {
            for(int w=2;w<4;w++)
                if((d.combat.owned&(1<<w))!=0) d.combat.reserve[w]=Math.min(240,d.combat.reserve[w]+amount[i]);
        } else {
            int w=item-1;
            if((d.combat.owned&(1<<w))==0) d.combat.magazine[w]=amount[i];
            else d.combat.reserve[w]=Math.min(240,d.combat.reserve[w]+amount[i]);
            d.combat.owned|=1<<w;
        }
        type[i]=0;
        revision++;
        d.trade.tell(LABELS[item]+" collected");
        return true;
    }

    void heal(District d) {
        if(medkits==0) { d.trade.tell("No medkits"); return; }
        if(d.combat.hp==100) { d.trade.tell("Health already full"); return; }
        medkits--;
        d.combat.hp=Math.min(100,d.combat.hp+45);
        revision++;
        d.trade.tell("Medkit used");
    }
}
