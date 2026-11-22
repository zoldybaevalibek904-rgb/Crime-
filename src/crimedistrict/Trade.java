package crimedistrict;

final class Trade {
    static final int WEAPONS=1,GARAGE=2,WORK=3,MARKET=4,CAFE=5;
    static final int SHOP_X=462, SHOP_Y=154, GARAGE_X=678,GARAGE_Y=363, WORK_X=460,WORK_Y=552;
    static final String[] MODELS={"Sedan","Taxi","SUV","Van","Pickup","Coupe","Muscle","Luxury"};
    private static final int[] PRICES={240,340,440,380,395,490,520,590};
    private static final int[] MODELS_BY_ROW={0,1,2,3,6,7,8,9};
    private static final int[] WORK_REWARDS={65,105,120,80,115,130};

    static int modelAtRow(int row) { return row>=1 && row<=MODELS.length?MODELS_BY_ROW[row-1]:-1; }
    static boolean validJob(int work,int progress) {
        return work>=-1 && work<WORK_TARGETS.length &&
               (work<0 || progress>=0 && progress<WORK_TARGETS[work].length/2);
    }
    private static final int[] WEAPON_PRICES={0,65,165,270};
    private static final int[][] WORK_TARGETS = {
        {985,234,1110,273},
        {Street.CARGO_X,Street.CARGO_Y,1890,228},
        {555,231,977,238,990,486,558,523},
        {454,550,325,168,842,144},
        {743,388,1890,231,2450,493},
        {978,914,1330,1194,1720,1216,2100,1194}
    };
    private static final String[] WORK_LABELS={"Taxi job","Delivery job","Checkpoint race","Courier request","Vehicle delivery","Coastal sprint"};

    int money, cars, visits, job, progress, jobTime, note, revision;
    int businesses, till, incomeTime;
    String notice = "";

    void reset() {
        money=220; cars=1; visits=0; job=-1; progress=jobTime=note=revision=0;
        businesses=till=incomeTime=0;
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

    String name(int site) {
        return site==WEAPONS?"WEAPON SUPPLY":site==GARAGE?"GARAGE":
               site==MARKET?"MARKET":site==CAFE?"CAFÉ":"JOBS";
    }

    int options(int site) {
        return site==WEAPONS?5:site==GARAGE?11:site==MARKET?3:site==CAFE?2:WORK_LABELS.length+1;
    }

    private String businessLabel(int bit) {
        return (businesses&bit)!=0?"Collect income  $"+till:bit==1?"Buy Market  $450":"Buy workshop  $650";
    }

    private void business(int bit) {
        if((businesses&bit)!=0) {
            if(till==0) { tell("Till is empty"); return; }
            award(till);
            till=0;
            tell("Business income collected");
        } else if(pay(bit==1?450:650)) {
            businesses|=bit;
            tell("Business acquired");
        }
    }

    String row(District d,int site,int row) {
        if (row==options(site)-1) return "Back";
        if(site==MARKET) return row==0?"Medkit  $25":businessLabel(1);
        if(site==CAFE) return "Meal and recovery  $25";
        if (site==WEAPONS) {
            if (row==3) return "Ammo pack  $40";
            int item=row+1;
            return Combat.LABELS[item]+((d.combat.owned&(1<<item))!=0?"  OWNED":"  $"+WEAPON_PRICES[item]);
        }
        if (site==GARAGE) {
            if (row==0) return "Repair vehicle  $45";
            if(row==9) return businessLabel(2);
            int model=modelAtRow(row);
            return MODELS[row-1]+((cars&(1<<model))!=0?"  SELECT":"  $"+PRICES[row-1]);
        }
        return WORK_LABELS[row];
    }

    void choose(District d,int site,int row) {
        if (row<0 || row>=options(site)-1) return;
        if(site==MARKET) {
            if(row==1) business(1);
            else if(d.gear.medkits==9) tell("Inventory full");
            else if(pay(25)) { d.gear.medkits++; d.gear.revision++; tell("Medkit purchased"); }
            return;
        }
        if(site==CAFE) {
            if(pay(25)) { d.combat.hp=Math.min(100,d.combat.hp+40); tell("Meal served"); }
            return;
        }
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
            if(row==9) { business(2); return; }
            if (row==0) {
                District.Actor selected=d.driving?d.car:d.nearbyCar();
                if(selected==null && d.rooms.active() &&
                    Math.abs(d.car.x/District.UNIT-GARAGE_X)+Math.abs(d.car.y/District.UNIT-GARAGE_Y)<94) selected=d.car;
                if (selected==null) { tell("Park a vehicle nearby"); return; }
                if (selected.hp==100) { tell("Vehicle is in good condition"); return; }
                if (pay(45)) {
                    selected.hp=100;
                    selected.claimed=true;
                    tell("Vehicle repaired");
                }
            } else {
                int model=modelAtRow(row);
                District.Actor selected=d.garageVehicle(model);
                if (!d.garageSpace(selected)) { tell("Clear the marked bay"); return; }
                if ((cars&(1<<model))==0 && !pay(PRICES[row-1])) return;
                cars|=1<<model;
                d.deliverGarageVehicle(selected);
                revision++;
                tell("Vehicle in the garage bay");
            }
        } else {
            job=row; progress=0; jobTime=0;
            d.gear.token&=1;
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
        if(job==3) return "Visit the courier marker";
        if(job==4) return d.driving?"Drive to delivery marker":"Take a car";
        if ((job==2 || job==5) && !d.driving) return "Take a car";
        if (job==2 || job==5) return jobTime>0?"Checkpoint  "+((jobTime+29)/30)+"s":"Reach first checkpoint";
        return progress==0?(job==0?"Pick up passenger":"Collect cargo"):"Drive to drop-off";
    }

    void advanceTime() {
        if (note>0) note--;
        if(businesses!=0 && ++incomeTime>=900) {
            incomeTime=0;
            till=Math.min(160,till+((businesses&1)!=0?22:0)+((businesses&2)!=0?32:0));
            revision++;
        }
    }

    void tick(District d) {
        advanceTime();
        if (job<0 || d.combat.life!=Combat.NORMAL) return;
        if (jobTime>0 && --jobTime==0) { job=-1; revision++; tell("Race timed out"); return; }
        if ((job==3?d.driving:!d.driving) || job==0 && d.car.model!=1 || job==1 && d.car.model!=3) return;
        if (!near(d.focusX()/District.UNIT,d.focusY()/District.UNIT,x(),y(),job==2||job==5?31:23)) return;
        if ((job==2 || job==5) && progress==0) jobTime=1050;
        progress++;
        revision++;
        if(progress==1 && job==3) d.gear.token|=2;
        if (progress==1 && job!=2) tell(job==0?"Passenger on board":"Cargo loaded");
        if (progress*2==WORK_TARGETS[job].length) {
            int reward=WORK_REWARDS[job];
            award(reward);
            if(job==3) d.gear.token&=1;
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
