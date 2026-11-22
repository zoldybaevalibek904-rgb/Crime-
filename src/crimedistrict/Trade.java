package crimedistrict;

final class Trade {
    static final int WEAPONS=1,GARAGE=2,WORK=3;
    static final int SHOP_X=462, SHOP_Y=154, GARAGE_X=678,GARAGE_Y=363, WORK_X=460,WORK_Y=552;
    static final String[] MODELS={"Sedan","Taxi","SUV","Van"};
    private static final int[] PRICES={240,340,440,380};
    private static final int[] WEAPON_PRICES={0,65,165,270};
    private static final int[][] WORK_TARGETS = {
        {985,234,1110,273},
        {Street.CARGO_X,Street.CARGO_Y,1890,228},
        {555,231,977,238,990,486,558,523}
    };
    private static final String[] WORK_LABELS={"Taxi job","Delivery job","Checkpoint race"};

    int money, cars, visits, job, progress, jobTime, note, revision;
    String notice = "";

    void reset() {
        money=220; cars=1; visits=0; job=-1; progress=jobTime=note=revision=0;
        notice="";
    }

    int place(District d) {
        if (d.combat.life!=Combat.NORMAL) return 0;
        int x=d.focusX()/District.UNIT,y=d.focusY()/District.UNIT;
        if (!d.driving && near(x,y,SHOP_X,SHOP_Y,20)) return WEAPONS;
        if (near(x,y,GARAGE_X,GARAGE_Y,24) && (!d.driving || d.canLeaveCar())) return GARAGE;
        if (!d.driving && near(x,y,WORK_X,WORK_Y,20)) return WORK;
        return 0;
    }

    String name(int site) { return site==WEAPONS?"WEAPON SUPPLY":site==GARAGE?"GARAGE":"JOBS"; }
    int options(int site) { return site==WEAPONS?5:site==GARAGE?6:4; }

    String row(District d,int site,int row) {
        if (row==options(site)-1) return "Back";
        if (site==WEAPONS) {
            if (row==3) return "Ammo pack  $40";
            int item=row+1;
            return Combat.LABELS[item]+((d.combat.owned&(1<<item))!=0?"  OWNED":"  $"+WEAPON_PRICES[item]);
        }
        if (site==GARAGE) {
            if (row==0) return "Repair vehicle  $45";
            int model=row-1;
            return MODELS[model]+((cars&(1<<model))!=0?"  SELECT":"  $"+PRICES[model]);
        }
        return WORK_LABELS[row];
    }

    void choose(District d,int site,int row) {
        if (row<0 || row>=options(site)-1) return;
        if (site==WEAPONS) {
            if (row==3) {
                if ((d.combat.owned&12)==0) { tell("Buy a firearm first"); return; }
                if (!pay(40)) return;
                for (int i=2;i<4;i++) if ((d.combat.owned&(1<<i))!=0)
                    d.combat.reserve[i]=Math.min(240,d.combat.reserve[i]+Combat.CAPACITY[i]*2);
                tell("Ammo stocked");
                return;
            }
            int item=row+1;
            if ((d.combat.owned&(1<<item))==0) {
                if (!pay(WEAPON_PRICES[item])) return;
                d.combat.owned|=1<<item;
                d.combat.magazine[item]=Combat.CAPACITY[item];
                d.combat.reserve[item]=Combat.CAPACITY[item];
            }
            d.combat.weapon=item;
            d.combat.reload=0;
            revision++;
            tell("Equipped");
        } else if (site==GARAGE) {
            if (visits==0) revision++;
            visits=1;
            if (row==0) {
                District.Actor selected=d.driving?d.car:d.nearbyCar();
                if (selected==null) { tell("Park a vehicle nearby"); return; }
                if (selected.hp==100) { tell("Vehicle is in good condition"); return; }
                if (pay(45)) {
                    selected.hp=100;
                    selected.claimed=true;
                    tell("Vehicle repaired");
                }
            } else {
                int model=row-1;
                District.Actor selected=d.garageVehicle(model);
                if (!d.garageSpace(selected)) { tell("Clear the marked bay"); return; }
                if ((cars&(1<<model))==0 && !pay(PRICES[model])) return;
                cars|=1<<model;
                d.deliverGarageVehicle(selected);
                revision++;
                tell("Vehicle in the garage bay");
            }
        } else {
            job=row; progress=0; jobTime=0;
            revision++;
            tell(WORK_LABELS[row]);
        }
    }

    boolean pay(int n) {
        if (money<n) { tell("Not enough money"); return false; }
        money-=n;
        revision++;
        return true;
    }

    void fine(int n) { money=Math.max(0,money-n); revision++; }
    void award(int n) { money=Math.min(99999,money+n); revision++; }
    void tell(String s) { notice=s; note=100; }
    int x() { return WORK_TARGETS[job][progress*2]; }
    int y() { return WORK_TARGETS[job][progress*2+1]; }
    String jobTitle() { return WORK_LABELS[job]; }
    String task(District d) {
        if (job==0 && (!d.driving || d.car.model!=1)) return "Take a taxi";
        if (job==1 && (!d.driving || d.car.model!=3)) return "Take a van";
        if (job==2 && !d.driving) return "Take a car";
        if (job==2) return jobTime>0?"Checkpoint  "+((jobTime+29)/30)+"s":"Reach first checkpoint";
        return progress==0?(job==0?"Pick up passenger":"Collect cargo"):"Drive to drop-off";
    }

    void tick(District d) {
        if (note>0) note--;
        if (job<0 || d.combat.life!=Combat.NORMAL) return;
        if (jobTime>0 && --jobTime==0) { job=-1; revision++; tell("Race timed out"); return; }
        if (!d.driving || job==0 && d.car.model!=1 || job==1 && d.car.model!=3) return;
        if (!near(d.car.x/District.UNIT,d.car.y/District.UNIT,x(),y(),job==2?31:23)) return;
        if (job==2 && progress==0) jobTime=1050;
        progress++;
        revision++;
        if (progress==1 && job!=2) tell(job==0?"Passenger on board":"Cargo loaded");
        if (progress*2==WORK_TARGETS[job].length) {
            int reward=job==0?65:job==1?105:120;
            award(reward);
            job=-1;
            jobTime=0;
            tell("Job complete  +$"+reward);
        }
    }

    private static boolean near(int x,int y,int sx,int sy,int r) {
        int dx=x-sx,dy=y-sy;
        return dx*dx+dy*dy<=r*r;
    }
}
