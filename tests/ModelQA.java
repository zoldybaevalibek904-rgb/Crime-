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
        for (int i = 0; i < d.street.length; i++) {
            District.Actor a = d.street[i];
            if (a.route.length > 2)
                check(Integer.bitCount(facings[i]) >= (a.vehicle ? 3 : 2),"stationary route "+i);
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
        check(d.car.y/U==180,"Market blocks sedan at full speed");
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
        check(d.story.finished(),"Road to the Coast and all six missions completed");
        int moving = d.heroX;
        steps(d,RIGHT,4);
        check(d.heroX>moving,"free roam after storyline");
    }

    private static void visit(District d, int x, int y) {
        check(d.freeStatic(x-4,y-3,8,6),"mission goal is clear "+x+","+y);
        foot(d,x,y);
        frame(d,0);
    }

    public static void main(String[] args) throws Exception {
        District d = new District(ModelQA.class.getResourceAsStream("/city.bin"));
        checkStreet(d);
        cityRoutes(d);
        driving(d);
        police(d);
        missions(d);
        System.out.println("PASS 14,500 route ticks, four connected districts, cars, police, six jobs, and save validation");
    }
}
