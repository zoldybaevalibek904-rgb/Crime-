package crimedistrict;

/** Ordered jobs with world targets and conditions independent of rendering. */
final class Story {
    static final int LOCKED = 0, AVAILABLE = 1, ACTIVE = 2, COMPLETED = 3, FAILED = 4;
    private static final int ANY = 0, FOOT = 1, DRIVE = 2, CARGO = 3, HIDE = 4, LOSE_GANG = 5;
    private static final int[][] JOBS = {
        {274,257,FOOT,435,534,DRIVE},
        {1100,302,FOOT},
        {Street.CARGO_X,Street.CARGO_Y,CARGO,438,534,CARGO},
        {436,1178,ANY,446,953,HIDE},
        {Street.RED_X,Street.RED_Y,ANY,1024,507,LOSE_GANG},
        {Street.GUARD_X,Street.GUARD_Y,FOOT,1026,1283,FOOT,1415,1281,FOOT,681,530,ANY}
    };
    private static final String[] TITLES = {
        "FIRST DELIVERY","NEW WORK","URGENT CARGO","UNWANTED HEAT","HOSTILE TURF","ROAD TO THE COAST"
    };
    private static final String[][] OBJECTIVES = {
        {"Walk to pickup","Drive to drop-off"},
        {"Meet at Residential"},
        {"Take the depot van","Bring van Downtown"},
        {"Check the depot","Leave and lose police"},
        {"Check Red Jackets","Leave their territory"},
        {"Meet the lifeguard","Visit west boardwalk","Visit east boardwalk","Return Downtown"}
    };

    int chapter, stage, state, delay, revision;

    void reset() {
        chapter = stage = delay = revision = 0;
        state = AVAILABLE;
    }

    int status(int job) {
        return job < chapter ? COMPLETED : job > chapter ? LOCKED : state;
    }

    boolean finished() { return chapter == JOBS.length; }
    boolean marker() { return !finished() && state != COMPLETED && state != FAILED; }
    boolean gangAlert() { return chapter == 4 && stage == 1 && state == ACTIVE; }

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
        if (chapter == 3 && stage == 1 && state == ACTIVE) {
            stage = 0;
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
        if (chapter == 3 && stage == 0 && city.wanted == 0) city.offence();
        stage++;
        revision++;
        if (stage*3 == JOBS[chapter].length) {
            chapter++;
            stage = 0;
            state = COMPLETED;
            delay = 105;
        }
    }

    private boolean satisfied(District city, int condition) {
        int x = city.focusX()/District.UNIT, y = city.focusY()/District.UNIT;
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
    }
}
