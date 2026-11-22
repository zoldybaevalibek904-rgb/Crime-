package crimedistrict;

public class ModelQA {
    private static final int U = District.UNIT;
    private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8, FIRE = 16;

    static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    static void frame(District d, int bits) {
        d.tick((bits & UP) != 0,(bits & DOWN) != 0,(bits & LEFT) != 0,(bits & RIGHT) != 0,(bits & FIRE) != 0);
        d.follow(240,320);
    }

    static void steps(District d, int bits, int count) {
        for (int i = 0; i < count; i++) frame(d,bits);
    }

    static void foot(District d, int x, int y) {
        d.driving = false;
        d.heroX = d.cameraX = x*U;
        d.heroY = d.cameraY = y*U;
    }

    static void car(District d, int x, int y) {
        d.driving = true;
        d.car.claimed = true;
        d.car.x = d.cameraX = x*U;
        d.car.y = d.cameraY = y*U;
    }

    static void checkStreet(District d) {
        for (int i = 0; i < d.street.length; i++) {
            District.Actor a = d.street[i];
            if (a.deferred) continue;
            int w = a.vehicle ? District.halfWidth(a.dir,a.model) : 4;
            int h = a.vehicle ? District.halfHeight(a.dir,a.model) : 3;
            int x = a.x/U, y = a.y/U;
            check(d.freeStatic(x-w,y-h,w*2,h*2),"actor " + i + " enters wall/water at "+x+","+y);
            for (int j = 0; j < i; j++) {
                District.Actor b = d.street[j];
                if (b.deferred) continue;
                int ow = b.vehicle ? District.halfWidth(b.dir,b.model) : 4;
                int oh = b.vehicle ? District.halfHeight(b.dir,b.model) : 3;
                check(Math.abs(x-b.x/U) >= w+ow || Math.abs(y-b.y/U) >= h+oh,
                      "actors overlap: "+i+"/"+j);
            }
        }
    }

    private static int accessible(District d) {
        boolean[] seen=new boolean[d.columns*d.rows];
        int[] queue=new int[seen.length];
        int lo=0,hi=1;
        queue[0]=13*d.columns+14;
        seen[queue[0]]=true;
        while (lo<hi) {
            int cell=queue[lo++],x=cell%d.columns,y=cell/d.columns;
            for (int dir=0;dir<4;dir++) {
                int xx=x+District.DX[dir],yy=y+District.DY[dir];
                if(xx<0 || yy<0 || xx>=d.columns || yy>=d.rows) continue;
                int next=yy*d.columns+xx;
                if(seen[next]) continue;
                if(d.freeStatic(Math.min(x,xx)*16+4,Math.min(y,yy)*16+5,
                        8+Math.abs(xx-x)*16,6+Math.abs(yy-y)*16)) {
                    seen[next]=true;
                    queue[hi++]=next;
                }
            }
        }
        return hi;
    }

    static void cityRoutes(District d) {
        int[][] areas = {{385,330},{1144,343},{384,1047},{1156,1100},{1350,1200}};
        int[] facings = new int[d.street.length];
        for (int[] zone : areas) {
            foot(d,zone[0],zone[1]);
            for (int tick = 0; tick < 2900; tick++) {
                frame(d,0);
                if (tick%11 == 0) checkStreet(d);
                for (int i = 0; i < d.street.length; i++) {
                    District.Actor a = d.street[i];
                    if (a.moving) facings[i] |= 1 << a.dir;
                }
            }
        }
        for (int i = 0; i < Street.LEGACY_POPULATION; i++) {
            District.Actor a = d.street[i];
            if (a.route.length > 2)
                check(Integer.bitCount(facings[i]) >= (a.vehicle ? 3 : 2),"stationary route "+i);
        }
        for (int by=0;by<4;by++) for (int bx=0;bx<4;bx++) {
            if (bx<2 && by<2) continue;
            foot(d,bx*768+323,by*704+350);
            for (int tick=0;tick<1500;tick++) {
                frame(d,0);
                if (tick%37==0) checkStreet(d);
                for (int i=Street.LEGACY_POPULATION;i<d.street.length;i++)
                    if (d.street[i].moving) facings[i] |= 1<<d.street[i].dir;
            }
        }
        for (int i=Street.LEGACY_POPULATION;i<d.street.length;i++) {
            District.Actor a=d.street[i];
            if (a.route.length>2) check(Integer.bitCount(facings[i])>=2,"new actor route "+i);
        }
        d.reset();
        car(d,716,216);
        steps(d,RIGHT,25); check(d.sector() == 1,"Downtown to Residential");
        d.reset();
        car(d,216,663);
        steps(d,DOWN,25); check(d.sector() == 2,"Downtown to Industrial");
        d.reset();
        car(d,717,1230);
        steps(d,RIGHT,25); check(d.sector() == 3,"Industrial to Beach");
        d.reset();
        car(d,985,663);
        steps(d,DOWN,25); check(d.sector() == 3,"Residential to Beach");
        d.reset();
        car(d,1500,216);
        steps(d,RIGHT,25); check(d.neighborhood()==4,"Residential to Hospital District");
        d.reset();
        car(d,1500,1229);
        steps(d,RIGHT,25); check(d.neighborhood()==8,"Beach to expanded Coastal Road");
        foot(d,1008,1320);
        steps(d,DOWN,24); check(d.heroY/U < 1328,"deep water blocks walking");
    }

    static District.Actor vehicle(District d, int type, int role) {
        for (District.Actor a : d.street) if (a.vehicle && a.model == type && a.role == role) return a;
        throw new AssertionError("vehicle missing");
    }

    static void driving(District d) {
        int[] roles = {Street.PARKED,Street.PARKED,Street.PARKED,Street.CARGO};
        int[] speeds = new int[4];
        for (int type = 0; type < 4; type++) {
            d.reset();
            District.Actor actor = vehicle(d,type,roles[type]);
            foot(d,actor.x/U-28,actor.y/U);
            frame(d,FIRE);
            check(d.driving && d.car == actor,"enter model "+type);
            car(d,210,217);
            for (int dir = 0; dir < 4; dir++) {
                int x = actor.x, y = actor.y;
                steps(d,1<<dir,7);
                check(actor.dir == dir && (actor.x-x)*District.DX[dir]+(actor.y-y)*District.DY[dir] > 0,
                      "four-way drive for "+type);
                steps(d,0,17);
            }
            car(d,310,228);
            int oldX = actor.x;
            steps(d,RIGHT,20);
            speeds[type] = actor.x-oldX;
            steps(d,0,20);
            int stopped = actor.x;
            steps(d,0,3);
            check(actor.x==stopped,"release stops motion");
            frame(d,FIRE);
            check(!d.driving,"safe exit");
        }
        check(speeds[0] > speeds[2] && speeds[1] > speeds[2] && speeds[2] > speeds[3],"different handling");

        d.reset();
        car(d,210,220);
        for (int mask : new int[]{8,9,10,6,5,1,2,4}) for (int i=0;i<20;i++) {
            int x = d.car.x, y = d.car.y;
            frame(d,mask);
            int dx = d.car.x-x, dy = d.car.y-y;
            check(dx*dx+dy*dy <= District.CAR_SPEED*District.CAR_SPEED,"normalized diagonal");
        }
        d.reset();
        foot(d,236,229);
        steps(d,RIGHT,75);
        check(d.heroX/U==245,"sedan blocks walking");
        d.reset();
        car(d,316,229);
        steps(d,UP,95);
        check(d.car.y/U==180 && d.car.hp<100,"Market blocks sedan and strong impact damages it");
    }

    static void police(District d) {
        d.reset();
        District.Actor taxi = vehicle(d,1,Street.CIVILIAN);
        foot(d,taxi.x/U-29,taxi.y/U);
        frame(d,FIRE);
        check(d.driving && d.car == taxi && d.wanted==1,"traffic theft adds star");
        d.offence();
        d.offence();
        d.offence();
        check(d.wanted==3,"wanted caps at three");
        boolean pursuit = false;
        for (int i=0;i<50;i++) {
            frame(d,0);
            for (District.Actor a : d.street)
                if (a.role == Street.POLICE && a.duty == Street.CHASE) pursuit = true;
        }
        check(pursuit,"police react to wanted player");
        foot(d,1060,1305);
        steps(d,0,550);
        check(d.wanted==0,"all three stars decay after losing sight");

        d.reset();
        District.Actor police = vehicle(d,4,Street.POLICE);
        police.claimed = true; // Hold the patrol still to isolate the collision check.
        car(d,police.x/U,police.y/U+49);
        steps(d,UP,25);
        check(d.wanted>=1,"striking a marked cruiser raises wanted");
    }

    static void missions(District d) throws Exception {
        d.reset();
        check(d.story.status(0)==Story.AVAILABLE && d.story.status(1)==Story.LOCKED,"job availability");
        steps(d,DOWN,27);
        steps(d,RIGHT,34);
        check(d.story.chapter==0 && d.story.stage==1,"walk to pickup");
        frame(d,FIRE);
        check(d.driving,"enter car for First Delivery");
        for (int i=0;i<170 && d.car.x/U<532;i++) frame(d,RIGHT);
        check(d.car.x/U>=532,"east boulevard");
        for (int i=0;i<190 && d.car.y/U<519;i++) frame(d,DOWN);
        check(d.car.y/U>=519,"south junction");
        for (int i=0;i<140 && d.story.chapter==0;i++) frame(d,LEFT);
        check(d.story.chapter==1 && d.story.status(0)==Story.COMPLETED,"First Delivery completed by input");
        steps(d,0,110);

        visit(d,1100,302);
        check(d.story.chapter==2,"New Work");
        steps(d,0,110);
        District.Actor van = vehicle(d,3,Street.CARGO);
        foot(d,van.x/U-28,van.y/U);
        frame(d,FIRE);
        check(d.car==van && d.story.stage==1 && d.driving,"Urgent Cargo requires designated van");
        car(d,438,534);
        frame(d,0);
        check(d.story.chapter==3,"Urgent Cargo completed");
        steps(d,0,110);

        visit(d,436,1178);
        check(d.story.stage==1 && d.wanted==1,"Unwanted Heat begins");
        d.story.caught();
        check(d.story.status(3)==Story.FAILED,"police capture fails the job");
        foot(d,431,950);
        steps(d,0,101);
        d.wanted = 0;
        visit(d,436,1178);
        check(d.story.stage==1 && d.wanted==1,"failed job can restart");
        foot(d,1060,1305);
        steps(d,0,185);
        check(d.story.chapter==4,"Unwanted Heat completes after hiding");

        steps(d,0,110);
        visit(d,Street.RED_X,Street.RED_Y);
        check(d.story.gangAlert(),"Hostile Turf alerts Red Jackets");
        steps(d,0,12);
        boolean chasing = false;
        for (District.Actor a : d.street)
            if (a.role == Street.RED && a.duty == Street.CHASE) chasing = true;
        check(chasing,"gang responds during story");
        visit(d,1024,507);
        check(d.story.chapter==5,"Hostile Turf");
        steps(d,0,110);
        visit(d,Street.GUARD_X,Street.GUARD_Y);
        check(d.story.stage==1,"beach contact");
        visit(d,1026,1283);
        visit(d,1415,1281);

        byte[] record = Snapshot.encode(d);
        d.reset();
        check(Snapshot.decode(d,record) && d.story.chapter==5 && d.story.stage==3 &&
              d.sector()==3 && d.heroX/U==1415,"save restores active job, vehicle and district");
        check(van.claimed && van.x/U==438,"save restores parked vehicle position");
        record[4] = 99;
        check(!Snapshot.decode(d,record) && d.story.stage==3,"unknown save version preserves live state");
        visit(d,681,530);
        check(d.story.chapter==6,"Road to the Coast and all original missions completed");
        int moving = d.heroX;
        steps(d,RIGHT,4);
        check(d.heroX>moving,"free roam after storyline");
    }

    private static void visit(District d, int x, int y) {
        check(d.freeStatic(x-4,y-3,8,6),"mission goal is clear "+x+","+y);
        foot(d,x,y);
        frame(d,0);
    }

    private static void aim(District d,int direction,int controls) {
        d.heroDir=direction;
        d.tick(false,false,false,false,false,controls);
        d.follow(240,320);
    }

    private static District.Actor pose(District.Actor a,int x,int y) {
        a.x=x*U;
        a.y=y*U;
        a.claimed=true;
        return a;
    }

    private static void combat(District d) throws Exception {
        d.reset();
        foot(d,300,243);
        District.Actor victim=pose(d.street[1],310,243);
        District.Actor behind=pose(d.street[2],294,243);
        aim(d,District.RIGHT,1);
        check(victim.hp==78 && behind.hp==100 && d.combat.attackPose>0,"directional fist damage");
        aim(d,District.RIGHT,1);
        check(victim.hp==78,"melee cooldown");
        d.trade.choose(d,Trade.WEAPONS,0);
        steps(d,0,20);
        aim(d,District.RIGHT,1);
        check(victim.hp==42,"bat damage");

        d.reset();
        d.trade.money=1000;
        d.trade.choose(d,Trade.WEAPONS,1);
        foot(d,330,175);
        victim=pose(d.street[1],330,85);
        aim(d,District.UP,1);
        check(victim.hp==100 && d.combat.magazine[2]==7,"wall blocks hitscan and shot spends ammo");
        steps(d,0,15);
        foot(d,305,244);
        victim=pose(victim,334,244);
        District.Actor officer=null;
        for(District.Actor a:d.street) if(!a.vehicle && a.role==Street.POLICE) { officer=a; break; }
        check(officer!=null,"police witness can be placed");
        pose(officer,355,239);
        aim(d,District.RIGHT,1);
        check(victim.hp==63 && d.wanted>=1,"witnessed shot raises wanted on the same tick");
        steps(d,0,14);
        aim(d,District.RIGHT,1);
        steps(d,0,14);
        aim(d,District.RIGHT,1);
        check(victim.hp==0 && victim.fallen>0 && !victim.deferred && d.wanted>=2,
              "fatal shot leaves body and raises witnessed wanted");
        d.trade.choose(d,Trade.WEAPONS,2);
        steps(d,0,16);
        aim(d,District.RIGHT,1);
        check(d.combat.magazine[3]==17 && d.combat.cooldown==5,"SMG fire rate and ammo");
        d.trade.choose(d,Trade.WEAPONS,3);
        aim(d,District.RIGHT,4);
        steps(d,0,44);
        check(d.combat.magazine[3]==18 && d.combat.reserve[3]>0,"reload transfers reserve");
        d.combat.damage(d,100);
        check(d.combat.life==Combat.WASTED,"fatal damage enters WASTED");
        steps(d,RIGHT,75);
        check(d.combat.life==Combat.NORMAL && d.combat.hp==100 && d.wanted==0 &&
              d.heroX/U==Street.HOSPITAL_X && d.heroY/U==Street.HOSPITAL_Y,"safe hospital respawn");
        check(d.freeStatic(d.heroX/U-4,d.heroY/U-3,8,6),"hospital door is clear");

        d.reset();
        foot(d,449,246);
        d.wanted=1;
        steps(d,0,68);
        check(d.combat.life==Combat.BUSTED,"nearby police detain player");
        steps(d,0,76);
        check(d.combat.life==Combat.NORMAL && d.trade.money==160,"detention fine and recovery");
    }

    private static void ambulance(District d) {
        int delay=-1;
        for (int attempt=0;attempt<2;attempt++) {
            d.reset();
            foot(d,304,289);
            District.Actor victim=pose(d.street[1],300,244);
            for (District.Actor a:d.street) {
                if(a==victim || a.role==Street.EMS || a.role==Street.MEDIC) continue;
                a.deferred=true;
                a.claimed=true;
            }
            victim.hp=0;
            victim.fallen=d.ticks;
            d.ems.request(d,victim);
            frame(d,0);
            int eta=d.ems.lastDelay;
            check(eta>=300 && eta<=600 && eta!=delay,"variable EMS deadline from death");
            delay=eta;
            District.Actor ems=vehicle(d,5,Street.EMS);
            int arrival=-1;
            while(d.ticks<eta+105) {
                frame(d,0);
                if (d.ems.arrivals!=0 && arrival<0) arrival=d.ticks;
            }
            check(arrival>=300 && arrival<=600,"ambulance reaches roadway in 10–20 seconds ("+arrival+")");
            check(victim.removed && victim.deferred,"medic clears casualty after scene treatment");
            foot(d,500,650);
            steps(d,0,16);
            check(ems.deferred,"service vehicle leaves outside the viewport");
        }
    }

    private static void tradesAndNewStory(District d) throws Exception {
        d.reset();
        d.story.restore(6,0);
        visit(d,Street.RED_X,Street.RED_Y);
        check(d.story.fight(Street.RED),"Red Jackets story encounter activates");
        d.story.defeated(Street.RED);
        frame(d,0);
        check(d.story.chapter==7 && d.trade.money==330,"first new job pays reward");
        steps(d,0,110);
        visit(d,1890,231);
        visit(d,1096,270);
        check(d.story.chapter==8,"The Debt");
        steps(d,0,110);
        visit(d,Street.IRON_X,Street.IRON_Y);
        check(d.story.fight(Street.IRON),"Iron Crew story encounter activates");
        d.story.defeated(Street.IRON);
        frame(d,0); steps(d,0,110);
        check(d.story.chapter==9,"Depot Trouble");
        visit(d,1323,229);
        check(d.wanted>=2,"The Getaway triggers police");
        d.wanted=0;
        visit(d,1803,277);
        check(d.story.chapter==10,"The Getaway");
        steps(d,0,110);
        visit(d,Trade.GARAGE_X,Trade.GARAGE_Y);
        check(d.trade.place(d)==Trade.GARAGE,"garage entrance usable");
        d.trade.choose(d,Trade.GARAGE,2);
        District.Actor taxi=d.garageVehicle(1);
        check(!taxi.deferred && taxi.claimed && (d.trade.cars&2)!=0,"garage buys and delivers taxi");
        frame(d,0);
        check(d.story.finished(),"All eleven story jobs complete");

        d.car=taxi;
        car(d,985,234);
        taxi.hp=51;
        int cash=d.trade.money;
        d.trade.choose(d,Trade.GARAGE,0);
        check(taxi.hp==100 && d.trade.money==cash-45,"garage repair uses balance");
        cash=d.trade.money;
        d.trade.choose(d,Trade.WORK,0);
        frame(d,0); car(d,1110,273); frame(d,0);
        check(d.trade.job==-1 && d.trade.money==cash+65,"repeatable Taxi Job");
        d.car=vehicle(d,3,Street.CARGO);
        d.trade.choose(d,Trade.WORK,1);
        car(d,Street.CARGO_X,Street.CARGO_Y); frame(d,0);
        car(d,1890,228); frame(d,0);
        check(d.trade.job==-1 && d.trade.money==cash+170,"repeatable Delivery Job");
        d.trade.choose(d,Trade.WORK,2);
        int[][] points={{555,231},{977,238},{990,486},{558,523}};
        for(int[] point:points) { car(d,point[0],point[1]); frame(d,0); }
        check(d.trade.job==-1 && d.trade.money==cash+290,"repeatable Checkpoint Race");

        d.car.hp=64;
        d.combat.hp=59;
        d.trade.choose(d,Trade.WEAPONS,1);
        d.combat.magazine[2]=3;
        byte[] save=Snapshot.encode(d);
        d.reset();
        check(Snapshot.decode(d,save) && d.story.finished() && d.combat.hp==59 && d.car.hp==64 &&
              d.combat.magazine[2]==3 && (d.trade.cars&2)!=0,"new health, tools and cars survive reload");
        java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream legacy=new java.io.DataOutputStream(buffer);
        legacy.writeInt(0x4344524d); legacy.writeByte(1);
        legacy.writeShort(1200); legacy.writeShort(1280);
        legacy.writeByte(1); legacy.writeByte(6); legacy.writeByte(0); legacy.writeByte(0);
        legacy.writeBoolean(false); legacy.writeByte(0); legacy.writeByte(Street.LEGACY_POPULATION);
        for(int i=0;i<Street.LEGACY_POPULATION;i++) legacy.writeBoolean(false);
        check(Snapshot.decode(d,buffer.toByteArray()) && d.story.chapter==6 && d.heroX/U==1200 &&
              d.heroY/U==1280 && d.combat.hp==100,"migration from real v1 byte layout");
    }

    public static void main(String[] args) throws Exception {
        District d = new District(ModelQA.class.getResourceAsStream("/city04.bin"));
        checkStreet(d);
        int cells=accessible(d);
        check(cells>=25344,"accessible connected city, tiles="+cells);
        System.out.println("Connected, passable tile centers: "+cells);
        cityRoutes(d);
        driving(d);
        police(d);
        missions(d);
        combat(d);
        ambulance(d);
        tradesAndNewStory(d);
        System.out.println("PASS 32,500 route ticks, old and expanded districts, all weapons, pursuit, EMS, eleven story jobs, three paid jobs and save migration");
    }
}
