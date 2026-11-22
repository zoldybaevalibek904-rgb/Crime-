package crimedistrict;

/** Ordered jobs with world targets and conditions independent of rendering. */
final class Story {
    static final int LOCKED = 0, AVAILABLE = 1, ACTIVE = 2, COMPLETED = 3, FAILED = 4;
    private static final int ANY = 0, FOOT = 1, DRIVE = 2, CARGO = 3, HIDE = 4, LOSE_GANG = 5, RED_FIGHT=6,IRON_FIGHT=7,ESCAPE=8,GARAGE=9,TALK=10,BUSINESS=11;
    private static final int[][] JOBS = {
        {274,257,FOOT,435,534,DRIVE},
        {1100,302,FOOT},
        {Street.CARGO_X,Street.CARGO_Y,CARGO,438,534,CARGO},
        {436,1178,ANY,446,953,HIDE},
        {Street.RED_X,Street.RED_Y,ANY,1024,507,LOSE_GANG},
        {Street.GUARD_X,Street.GUARD_Y,FOOT,1026,1283,FOOT,1415,1281,FOOT,681,530,ANY},
        {Street.RED_X,Street.RED_Y,FOOT,Street.RED_X,Street.RED_Y,RED_FIGHT},
        {1890,231,ANY,1096,270,ANY},
        {Street.IRON_X,Street.IRON_Y,FOOT,Street.IRON_X,Street.IRON_Y,IRON_FIGHT},
        {1323,229,ANY,1803,277,ESCAPE},
        {Trade.GARAGE_X,Trade.GARAGE_Y,GARAGE},
        {Street.BROKER_X,Street.BROKER_Y,TALK},
        {Street.IRON_X,Street.IRON_Y,FOOT,473,1219,ANY},
        {554,1199,DRIVE,970,1218,DRIVE,1719,1220,DRIVE},
        {Street.BROKER_X,Street.BROKER_Y,TALK,Street.RED_X,Street.RED_Y,RED_FIGHT},
        {325,168,BUSINESS},
        {Street.BROKER_X,Street.BROKER_Y,TALK,978,914,DRIVE,1450,1231,ESCAPE,842,144,FOOT}
    };
    private static final String[] TITLES = {
        "FIRST DELIVERY","NEW WORK","URGENT CARGO","UNWANTED HEAT","HOSTILE TURF","ROAD TO THE COAST",
        "BAD COMPANY","THE DEBT","DEPOT TROUBLE","THE GETAWAY","NEW WHEELS",
        "NEW CONTACTS","FOREIGN TURF","THE DELIVERY","BAD DEAL","YOUR OWN SHOP","CITY NEVER SLEEPS"
    };
    private static final String[][] OBJECTIVES = {
        {"Walk to pickup","Drive to drop-off"},
        {"Meet at Residential"},
        {"Take the depot van","Bring van Downtown"},
        {"Check the depot","Leave and lose police"},
        {"Check Red Jackets","Leave their territory"},
        {"Meet the lifeguard","Visit west boardwalk","Visit east boardwalk","Return Downtown"},
        {"Meet Red Jackets","Win the fight"},
        {"Collect at hospital","Bring payment back"},
        {"Go to the depot","Defeat Iron Crew"},
        {"Reach the junction","Lose police and escape"},
        {"Use the garage"},
        {"Talk to the broker"},
        {"Enter Iron Crew turf","Get back to the road"},
        {"Collect the shipment","Cross the coast road","Deliver to the docks"},
        {"Talk to the broker","Win the confrontation"},
        {"Buy a business"},
        {"Talk to the broker","Reach the coast road","Lose the patrol","Return to safehouse"}
    };

    private static final int[] REWARDS={60,50,90,75,65,100,110,150,150,135,180,90,110,170,150,220,240};
    int chapter, stage, state, delay, revision;
    boolean won;
    private int talked;

    void reset() {
        chapter = stage = delay = revision = 0;
        state = AVAILABLE;
        won=false;
        talked=0;
    }

    int status(int job) {
        return job < chapter ? COMPLETED : job > chapter ? LOCKED : state;
    }

    boolean finished() { return chapter == JOBS.length; }
    boolean marker() { return !finished() && state != COMPLETED && state != FAILED; }
    boolean gangAlert() { return (chapter==4 || chapter==12) && stage==1 && state==ACTIVE; }

    boolean fight(int role) {
        return state==ACTIVE && stage==1 &&
               ((chapter==6 || chapter==14) && role==Street.RED || chapter==8 && role==Street.IRON);
    }

    void talk(int model) { talked=model; }

    void defeated(int role) {
        if (fight(role)) won=true;
    }

    String title() {
        if (state == FAILED) return "JOB FAILED";
        if (state == COMPLETED && delay > 0) return "JOB COMPLETE";
        return finished() ? "FREE ROAM" : TITLES[chapter];
    }

    String objective(District city) {
        if (state == FAILED) return "Retry in a moment";
        if (state == COMPLETED && delay > 0) return finished() ? "All jobs complete" : "Next job incoming";
        if (finished()) return "Explore the city";
        if (chapter == 0 && stage == 1 && !city.driving) return "Take a car";
        return OBJECTIVES[chapter][stage];
    }

    int targetX(District city) {
        if (chapter == 2 && stage == 0) return cargo(city).x/District.UNIT;
        return JOBS[chapter][stage*3];
    }

    int targetY(District city) {
        if (chapter == 2 && stage == 0) return cargo(city).y/District.UNIT;
        return JOBS[chapter][stage*3+1];
    }

    private District.Actor cargo(District city) {
        for (int i = 0; i < city.street.length; i++) {
            if (city.street[i].role == Street.CARGO) return city.street[i];
        }
        throw new IllegalStateException("Missing cargo van");
    }

    void caught() {
        if ((chapter==3 || chapter==6 || chapter==8 || chapter==9 || chapter==14 || chapter==16) && stage==1 && state==ACTIVE) {
            stage = 0;
            won=false;
            state = FAILED;
            delay = 100;
            revision++;
        }
    }

    void tick(District city) {
        if (delay > 0) {
            if (--delay == 0) {
                if (state == FAILED) stage = 0;
                state = finished() ? COMPLETED : AVAILABLE;
            }
            return;
        }
        if (finished()) return;
        if (state == AVAILABLE) state = ACTIVE;
        int condition = JOBS[chapter][stage*3+2];
        if (!satisfied(city,condition)) return;
        if ((chapter==3 || chapter==9) && stage==0) {
            city.wanted=Math.max(city.wanted,chapter==9?2:1);
            city.unseen=0;
        }
        if(chapter==16 && stage==1) city.reportCrime(2,true);
        if(chapter==13) {
            if(stage==0) city.gear.token|=1;
            if(stage==2) city.gear.token&=~1;
        }
        stage++;
        won=false;
        talked=0;
        revision++;
        if (stage*3 == JOBS[chapter].length) {
            city.trade.award(REWARDS[chapter]);
            chapter++;
            stage = 0;
            state = COMPLETED;
            delay = 105;
        }
    }

    private boolean satisfied(District city, int condition) {
        int x = city.focusX()/District.UNIT, y = city.focusY()/District.UNIT;
        if (condition==RED_FIGHT || condition==IRON_FIGHT) return won;
        if(condition==TALK && talked!=28) return false;
        if(condition==BUSINESS) return city.trade.businesses!=0;
        if (condition==ESCAPE && city.wanted!=0 || condition==GARAGE && city.trade.visits==0) return false;
        if (condition == HIDE)
            return city.wanted == 0 && Math.abs(x-436)+Math.abs(y-1178) >= 175;
        if (condition == LOSE_GANG)
            return Math.abs(x-Street.RED_X)+Math.abs(y-Street.RED_Y) >= 145;
        if (condition == FOOT && city.driving || condition == DRIVE && !city.driving) return false;
        if (condition == CARGO && (!city.driving || city.car.role != Street.CARGO)) return false;
        if (condition == CARGO && stage == 0) return true;
        int dx = x-targetX(city), dy = y-targetY(city);
        int radius = city.driving ? 17 : 16;
        return dx*dx+dy*dy <= radius*radius;
    }

    static boolean valid(int chapter, int stage) {
        return chapter >= 0 && chapter <= JOBS.length && stage >= 0 &&
               (chapter == JOBS.length ? stage == 0 : stage*3 < JOBS[chapter].length);
    }

    void restore(int chapter, int stage) {
        this.chapter = chapter;
        this.stage = stage;
        state = finished() ? COMPLETED : AVAILABLE;
        revision = delay = 0;
        won=false;
        talked=0;
    }
}
