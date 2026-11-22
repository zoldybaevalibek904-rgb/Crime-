package crimedistrict;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Downtown geometry and fixed-point simulation, independent of the display. */
final class District {
    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    static final int[] DX = {0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0};
    static final int UNIT = 256;
    static final int HERO_SPEED = 360;
    static final int CAR_SPEED = 920;
    static final int PEDESTRIAN_MODELS = 26;
    static final int ROAD = 0, SIDEWALK = 1, BUILDING = 2, SAND = 3, WATER = 4, PARKING = 5, DECOR = 6, BLOCKED = 7;
    private static final int[] LIMIT = {920, 860, 790, 690, 920, 800};
    private static final int[] ACCEL = {64, 79, 52, 42, 64, 61};
    private static final int[] BRAKE = {106, 128, 90, 73, 106, 96};

    static final class Actor {
        final int model, role, startX, startY, startDir, startWaypoint;
        final boolean vehicle;
        final int[] route;
        int x, y, dir, waypoint, wait, stride, speed, blocked, routeStep, duty;
        int hp, hit, threat, panic, strike, hitPose, fallen;
        boolean moving, claimed, deferred, removed;

        Actor(int model, boolean vehicle, int role, int x, int y, int dir, int waypoint, int[] route) {
            this.model = model;
            this.vehicle = vehicle;
            this.role = role;
            this.route = route;
            startX = x*UNIT;
            startY = y*UNIT;
            startDir = dir;
            startWaypoint = waypoint;
        }

        void reset() {
            x = startX;
            y = startY;
            dir = startDir;
            waypoint = startWaypoint;
            wait = stride = speed = blocked = 0;
            routeStep = 2;
            moving = claimed = removed = false;
            deferred = role==Street.EMS || role==Street.MEDIC || role==Street.GARAGE;
            hp = !vehicle && (role==Street.RED || role==Street.IRON) ? 74 : 100;
            hit = threat = panic = strike = hitPose = fallen = 0;
            duty = route.length > 2 ? Street.PATROL : Street.IDLE;
        }
    }

    static final class Prop {
        int art, x, y, left, top, width, height, depth;
    }

    final int columns, rows, width, height;
    private final Tiles map;
    final Prop[] props;
    private final Prop[][] solidBands;
    final Actor[] street = Street.populate();
    final Story story = new Story();
    final Combat combat = new Combat();
    final Trade trade = new Trade();
    final EMS ems = new EMS();
    int lifeRevision;
    Actor car;
    int heroX, heroY, heroDir;
    private int velocityX, velocityY;
    int ticks, wanted, unseen, notice;
    private int offenceCooldown, caught;
    int cameraX, cameraY, stride;
    boolean driving, walking;

    District(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing city map");
        DataInputStream in = new DataInputStream(stream);
        try {
            map = new Tiles(in);
            columns = map.columns;
            rows = map.rows;
            width = columns*16;
            height = rows*16;
            props = new Prop[in.readUnsignedShort()];
            for (int i = 0; i < props.length; i++) {
                Prop p = new Prop();
                p.art = in.readUnsignedByte();
                p.x = in.readShort();
                p.y = in.readShort();
                p.left = p.x + in.readByte();
                p.top = p.y + in.readByte();
                p.width = in.readUnsignedByte();
                p.height = in.readUnsignedByte();
                p.depth = p.top + p.height;
                props[i] = p;
            }
        } finally {
            in.close();
        }
        solidBands = new Prop[(height+63)/64][];
        for (int band = 0; band < solidBands.length; band++) {
            int count = 0;
            for (int i = 0; i < props.length; i++) if (inBand(props[i],band)) count++;
            Prop[] bucket = new Prop[count];
            count = 0;
            for (int i = 0; i < props.length; i++) if (inBand(props[i],band)) bucket[count++] = props[i];
            solidBands[band] = bucket;
        }
        reset();
    }

    private static boolean inBand(Prop p, int band) {
        return p.width > 0 && p.top < (band+1)*64 && p.depth > band*64;
    }

    void reset() {
        heroX = 226 * UNIT;
        heroY = 218 * UNIT;
        heroDir = DOWN;
        walking = driving = false;
        velocityX = velocityY = stride = ticks = wanted = unseen = offenceCooldown = caught = notice = 0;
        story.reset();
        combat.reset();
        trade.reset();
        lifeRevision=0;
        for (int i = 0; i < street.length; i++) street[i].reset();
        ems.reset(this);
        car = street[0];
        cameraX = heroX;
        cameraY = heroY;
    }

    int focusX() { return driving ? car.x : heroX; }
    int focusY() { return driving ? car.y : heroY; }
    int sector() { return (focusX()/UNIT >= 768 ? 1 : 0) + (focusY()/UNIT >= 704 ? 2 : 0); }

    int tileAt(int x, int y) {
        return map.get(x,y)&63;
    }

    int zoneAt(int col, int row) { return map.get(col,row)>>6; }

    int neighborhood() {
        int x = focusX()/UNIT/768, y = focusY()/UNIT/704;
        if (x < 2 && y < 2) return sector();
        if (y == 0 && x == 2) return 4;
        if (y == 1 && x >= 2) return 8;
        if (y == 3 && x < 2 || y == 2 && x == 2) return 7;
        if (x == 3 && y >= 2 || x == 1 && y == 2 || x == 3 && y == 0) return 6;
        return 5;
    }

    void tick(boolean up, boolean down, boolean left, boolean right, boolean action) {
        tick(up,down,left,right,action,0);
    }

    void tick(boolean up, boolean down, boolean left, boolean right, boolean action, int controls) {
        boolean normal=combat.life==Combat.NORMAL;
        if (normal && action) {
            if (driving) {
                if (canLeaveCar()) leaveCar();
            } else {
                Actor next = nearbyCar();
                if (next != null) {
                    car = next;
                    if (!car.claimed && car.role == Street.CIVILIAN) reportCrime(1,true);
                    car.claimed = true;
                    car.speed = velocityX = velocityY = 0;
                    driving = true;
                }
            }
        }
        if (normal) {
            if (driving) {
                if (car.hp>0) drive(up, down, left, right);
                else velocityX=velocityY=0;
                if ((controls&1)!=0 && ticks%12==0)
                    startle(car.x/UNIT,car.y/UNIT,62);
            } else if (combat.hurt<7) walk(up, down, left, right);
        }
        ticks++;
        combat.tick(this,controls);
        ems.tick(this);
        if (offenceCooldown > 0) offenceCooldown--;
        if (notice > 0) notice--;
        boolean seen = false;
        for (int i = 0; i < street.length; i++) {
            Actor actor = street[i];
            if (actor.role==Street.EMS || actor.role==Street.MEDIC || actor.role==Street.GARAGE) continue;
            if (actor.hp==0 && !actor.vehicle) {
                if (ticks-actor.fallen>1100 && !onCamera(actor.x,actor.y)) {
                    actor.deferred = true;
                    actor.removed = true;
                }
                if (actor.removed && ticks-actor.fallen>1300 && distance(actor,focusX(),focusY())>290) {
                    actor.reset();
                    if (!spawnClear(actor)) actor.deferred=true;
                }
                continue;
            }
            if (actor.deferred) {
                if (((ticks+i)&31) != 0 || distance(actor,focusX(),focusY()) < 235 || !spawnClear(actor)) continue;
                actor.deferred = false;
            }
            if (actor.claimed || (actor.vehicle && actor.route.length == 0)) continue;
            int distance = distance(actor, focusX(), focusY());
            boolean distant = absolute(actor.x-cameraX) > 176*UNIT || absolute(actor.y-cameraY) > 216*UNIT;
            if (!actor.deferred && actor.hp>0 && actor.role == Street.POLICE && wanted > 0 && distance < sightRange() &&
                canSee(actor.x, actor.y, focusX(), focusY())) seen = true;
            if (distant && (((ticks+i)&3) != 0 || distance > 540)) continue;
            updateActor(actor, distant ? 4 : 1, distance);
        }
        if (wanted > 0) {
            if (seen) {
                unseen = 0;
                if (!driving && policeAtFeet()) {
                    if (++caught >= 46) {
                        wanted = caught = 0;
                        combat.end(this,Combat.BUSTED);
                    }
                } else caught = 0;
            } else {
                caught = 0;
                if (++unseen >= 180) { unseen = 0; wanted--; }
            }
        }
        if (combat.life==Combat.NORMAL) story.tick(this);
        trade.tick(this);
    }

    void startle(int x,int y,int radius) {
        for (int i=0;i<street.length;i++) {
            Actor a=street[i];
            if (!a.vehicle && !a.deferred && a.hp>0 && a.role==Street.CIVILIAN &&
                    absolute(a.x/UNIT-x)+absolute(a.y/UNIT-y)<radius)
                a.panic=Math.max(a.panic,84);
        }
    }

    Actor targetAt(int x,int y,int tolerance,int fromX,int fromY,int dx,int dy) {
        Actor best=null;
        int nearest=999;
        for (int i=0;i<street.length;i++) {
            Actor a=street[i];
            if (a.deferred || a.vehicle || a.hp<=0) continue;
            int ax=a.x/UNIT,ay=a.y/UNIT;
            int deltaX=absolute(ax-x),deltaY=absolute(ay-y);
            if ((ax-fromX)*dx+(ay-fromY)*dy>0 && deltaX<=tolerance && deltaY<=tolerance &&
                deltaX+deltaY<nearest) {
                best=a; nearest=deltaX+deltaY;
            }
        }
        return best;
    }

    void reportCrime(int level,boolean direct) {
        if (!direct) {
            for (int i=0;i<street.length;i++) {
                Actor a=street[i];
                if (a.role==Street.POLICE && !a.deferred && a.hp>0 &&
                        distance(a,focusX(),focusY())<=150 &&
                        canSee(a.x,a.y,focusX(),focusY())) { direct=true; break; }
            }
        }
        if (direct) { wanted=Math.max(wanted,Math.min(3,level)); unseen=0; }
    }

    boolean onCamera(int x,int y) {
        return absolute(x-cameraX)<147*UNIT && absolute(y-cameraY)<195*UNIT;
    }

    void respawn() {
        driving=walking=false;
        heroX=Street.HOSPITAL_X*UNIT;
        heroY=Street.HOSPITAL_Y*UNIT;
        heroDir=DOWN;
        cameraX=heroX; cameraY=heroY;
        velocityX=velocityY=wanted=unseen=caught=0;
        lifeRevision++;
        trade.job=-1; trade.jobTime=0;
        trade.tell("Hospital recovery");
    }

    Actor garageVehicle(int model) {
        for (int i=0;i<street.length;i++) {
            Actor a=street[i];
            if (a.role==Street.GARAGE && a.model==model) return a;
        }
        throw new IllegalStateException("Garage vehicle missing");
    }

    private int garageX() { return (Trade.GARAGE_X+65)*UNIT; }
    private int garageY() { return (Trade.GARAGE_Y+25)*UNIT; }

    boolean garageSpace(Actor a) {
        return freeVehicle(a,garageX(),garageY(),RIGHT);
    }

    void deliverGarageVehicle(Actor a) {
        a.claimed=true;
        a.deferred=false;
        a.x=garageX();
        a.y=garageY();
        a.dir=RIGHT;
        velocityX=velocityY=0;
    }

    boolean vehicleSpace(Actor a,int x,int y,int dir) { return freeVehicle(a,x,y,dir); }
    boolean pedestrianSpace(Actor a,int x,int y) { return freePedestrian(a,x,y); }
    void emsWalk(Actor a,int x,int y) {
        a.moving=false;
        manoeuvre(a,x,y,1,345);
    }

    void offence() {
        wanted = Math.min(3, wanted+1);
        unseen = 0;
    }

    private int sightRange() { return 90 + wanted*34; }

    private boolean policeAtFeet() {
        for (int i = 0; i < street.length; i++) {
            Actor a = street[i];
            if (!a.deferred && a.hp>0 && a.role == Street.POLICE && !a.vehicle && distance(a,heroX,heroY) <= 13 &&
                canSee(a.x,a.y,heroX,heroY)) return true;
        }
        return false;
    }

    private static int distance(Actor a, int x, int y) {
        return (absolute(a.x-x)+absolute(a.y-y))/UNIT;
    }

    boolean canHit(int x,int y,int targetX,int targetY) {
        return pathClear(x,y,targetX,targetY,2,0);
    }

    boolean canSee(int x, int y, int targetX, int targetY) {
        return pathClear(x,y,targetX,targetY,8,0);
    }

    private boolean pathClear(int x, int y, int targetX, int targetY, int spacing, int padding) {
        int length = (absolute(targetX-x)+absolute(targetY-y))/UNIT;
        int steps = (length+spacing-1)/spacing;
        for (int i = 1; i < steps; i++) {
            int xx = (x+(targetX-x)*i/steps)/UNIT;
            int yy = (y+(targetY-y)*i/steps)/UNIT;
            if (!clear(xx-padding,yy-padding,1+2*padding,1+2*padding)) return false;
        }
        return true;
    }

    private void walk(boolean up, boolean down, boolean left, boolean right) {
        int direction = -1;
        if (up != down) direction = up ? UP : DOWN;
        else if (left != right) direction = left ? LEFT : RIGHT;
        walking = false;
        if (direction >= 0) {
            heroDir = direction;
            int x = heroX + DX[direction] * HERO_SPEED;
            int y = heroY + DY[direction] * HERO_SPEED;
            if (freeHero(x, y)) {
                heroX = x;
                heroY = y;
                walking = true;
                stride = (stride + 1) % 24;
            }
        }
        if (!walking) stride = 0;
    }

    private void drive(boolean up, boolean down, boolean left, boolean right) {
        int dx = (right ? 1 : 0) - (left ? 1 : 0);
        int dy = (down ? 1 : 0) - (up ? 1 : 0);
        int maximum = LIMIT[car.model];
        int limit = dx != 0 && dy != 0 ? maximum * 181 / UNIT : maximum;
        velocityX = changeVelocity(velocityX, dx * limit, car.model);
        velocityY = changeVelocity(velocityY, dy * limit, car.model);
        // Turning and braking can temporarily mix old and new velocity components.
        int squared = velocityX * velocityX + velocityY * velocityY;
        if (squared > maximum * maximum) {
            int length = absolute(velocityX) + absolute(velocityY);
            int estimate = (length + squared / length) / 2;
            while (estimate < length) {
                length = estimate;
                estimate = (length + squared / length) / 2;
            }
            if (length * length < squared) length++;
            velocityX = velocityX * maximum / length;
            velocityY = velocityY * maximum / length;
        }
        int next = direction(velocityX, velocityY, car.dir);
        if (next != car.dir) {
            if (freeVehicle(car, car.x, car.y, next)) car.dir = next;
            else if ((next < LEFT) != (car.dir < LEFT)) {
                if (car.dir < LEFT) velocityX = 0;
                else velocityY = 0;
            }
        }
        if (velocityX != 0) {
            if (freeVehicle(car, car.x + velocityX, car.y, car.dir)) car.x += velocityX;
            else impact(velocityX, 0);
        }
        if (velocityY != 0) {
            if (freeVehicle(car, car.x, car.y + velocityY, car.dir)) car.y += velocityY;
            else impact(0, velocityY);
        }
        next = direction(velocityX, velocityY, car.dir);
        if (next != car.dir && freeVehicle(car, car.x, car.y, next)) car.dir = next;
    }

    private void impact(int dx, int dy) {
        if (offenceCooldown == 0) {
            int x = (car.x+dx)/UNIT, y = (car.y+dy)/UNIT;
            for (int i = 0; i < street.length; i++) {
                Actor other = street[i];
                if (!other.deferred && other.role == Street.POLICE && other.vehicle &&
                    touch(x,y,halfWidth(car.dir,car.model),halfHeight(car.dir,car.model),
                          other.x,other.y,halfWidth(other.dir,other.model),halfHeight(other.dir,other.model))) {
                    reportCrime(1,true);
                    offenceCooldown = 90;
                    break;
                }
            }
        }
        int shock = absolute(dx)+absolute(dy);
        if (shock>290) {
            car.hp=Math.max(0,car.hp-shock/126);
            if (car.hp==0) combat.damage(this,35);
        }
        if (dx != 0) velocityX = 0;
        if (dy != 0) velocityY = 0;
    }

    private static int changeVelocity(int current, int target, int model) {
        if (target != 0 && current * target < 0) current = 0;
        int step = absolute(target) < absolute(current) ? BRAKE[model] : ACCEL[model];
        return current < target ? Math.min(current + step, target) : Math.max(current - step, target);
    }

    private static int direction(int dx, int dy, int previous) {
        if (dx == 0 && dy == 0) return previous;
        int lateral = absolute(dx), vertical = absolute(dy);
        if (lateral > vertical || (lateral == vertical && previous >= LEFT)) return dx < 0 ? LEFT : RIGHT;
        return dy < 0 ? UP : DOWN;
    }

    boolean canLeaveCar() {
        return Math.max(absolute(velocityX), absolute(velocityY)) <= UNIT;
    }

    private void updateActor(Actor actor, int elapsed, int distance) {
        actor.moving = false;
        if (actor.vehicle) {
            if (actor.role == Street.POLICE) {
                actor.duty = wanted > 0 && distance < 145+wanted*45 ? Street.CHASE : Street.PATROL;
            }
            patrol(actor, elapsed);
            return;
        }
        if (actor.strike>0) actor.strike=Math.max(0,actor.strike-elapsed);
        if (actor.hitPose>0) actor.hitPose=Math.max(0,actor.hitPose-elapsed);
        if (actor.hit>0) {
            actor.hit=Math.max(0,actor.hit-elapsed);
            if (actor.hit>5) return;
        }
        if (actor.threat>0) actor.threat=Math.max(0,actor.threat-elapsed);
        if (actor.panic>0) {
            actor.panic=Math.max(0,actor.panic-elapsed);
            int x=actor.x+(actor.x>focusX()?28:-28)*UNIT;
            int y=actor.y+(actor.y>focusY()?28:-28)*UNIT;
            manoeuvre(actor,x,y,elapsed,402);
            actor.duty=Street.RETURN;
            return;
        }
        boolean police = actor.role == Street.POLICE;
        boolean gang = actor.role == Street.RED || actor.role == Street.IRON;
        boolean hostile = police ? wanted > 0 : gang && (story.gangAlert() || actor.threat>0 || story.fight(actor.role));
        int homeDistance = (absolute(actor.x-actor.startX)+absolute(actor.y-actor.startY))/UNIT;
        if ((police || gang) && hostile && homeDistance < (police ? 205 : 88) &&
            distance < (police ? sightRange() : 70) &&
            (actor.duty == Street.CHASE || canSee(actor.x, actor.y, focusX(), focusY()))) {
            actor.duty = Street.CHASE;
            if (gang && (actor.threat>0 || story.fight(actor.role))) combat.enemyAttack(this,actor,distance);
            if (actor.hitPose<6) manoeuvre(actor, focusX(), focusY(), elapsed, police ? (323+wanted*42) : 300);
            return;
        }
        if (actor.duty == Street.CHASE || actor.duty == Street.RETURN) {
            actor.duty = Street.RETURN;
            if (homeDistance > 2) {
                manoeuvre(actor, actor.startX, actor.startY, elapsed, 270);
                return;
            }
            // Recover the authored lane when the actor has returned to its post.
            if (!freePedestrian(actor, actor.startX, actor.startY)) return;
            actor.x = actor.startX;
            actor.y = actor.startY;
            actor.waypoint = actor.startWaypoint;
            actor.routeStep = 2;
            actor.duty = Street.PATROL;
        }
        if (gang && distance < 39) {
            actor.duty = Street.ALERT;
            actor.dir = direction(focusX()-actor.x,focusY()-actor.y,actor.dir);
            return;
        }
        actor.duty = actor.route.length > 2 ? Street.PATROL : Street.IDLE;
        if (nearMovingVehicle(actor)) {
            actor.wait = 12;
            return;
        }
        if (actor.duty == Street.PATROL) patrol(actor, elapsed);
    }

    private boolean nearMovingVehicle(Actor pedestrian) {
        for (int i = 0; i < street.length; i++) {
            Actor a = street[i];
            if (a.deferred || a.hp==0) continue;
            if (a.vehicle && (a.speed > 150 || (driving && a == car &&
                    absolute(velocityX)+absolute(velocityY) > 200)) &&
                    distance(a,pedestrian.x,pedestrian.y) < 29) return true;
            if (pedestrian.role==Street.CIVILIAN && a.role==Street.POLICE && a.duty==Street.CHASE &&
                    distance(a,pedestrian.x,pedestrian.y)<19) return true;
        }
        return false;
    }

    private void manoeuvre(Actor actor, int x, int y, int elapsed, int speed) {
        int dx = x-actor.x, dy = y-actor.y;
        int first = direction(dx,dy,actor.dir);
        int second = first < LEFT ? (dx < 0 ? LEFT : RIGHT) : (dy < 0 ? UP : DOWN);
        for (int i = 0; i < 2; i++) {
            int dir = i == 0 ? first : second;
            int step = Math.min(elapsed*speed, absolute(dir < LEFT ? dy : dx));
            int nextX = actor.x+DX[dir]*step, nextY = actor.y+DY[dir]*step;
            if (step > 0 && freePedestrian(actor,nextX,nextY)) {
                actor.x = nextX;
                actor.y = nextY;
                actor.dir = dir;
                actor.moving = true;
                actor.stride = (actor.stride+elapsed)%32;
                return;
            }
        }
    }

    private void patrol(Actor actor, int elapsed) {
        actor.moving = false;
        if (actor.wait > 0) {
            actor.wait -= elapsed;
            return;
        }
        int dx = actor.route[actor.waypoint] * UNIT - actor.x;
        int dy = actor.route[actor.waypoint + 1] * UNIT - actor.y;
        if (dx == 0 && dy == 0) {
            if (actor.vehicle && actor.duty == Street.CHASE) turnTowardPlayer(actor);
            nextWaypoint(actor);
            if (!actor.vehicle) {
                actor.wait = 20 + actor.model * 11;
                actor.stride = 0;
            }
            return;
        }
        int dir = direction(dx, dy, actor.dir);
        int remaining = Math.max(absolute(dx), absolute(dy));
        int step;
        if (actor.vehicle) {
            // Sample along the lane before the collision hull reaches an obstacle.
            int ahead = Math.min(remaining, 10 * UNIT);
            boolean clearAhead = freeVehicle(actor, actor.x + DX[dir] * ahead,
                                             actor.y + DY[dir] * ahead, dir);
            int cruise = actor.duty == Street.CHASE ? 510+wanted*85 : (actor.model == 3 ? 350 : 410);
            actor.speed = changeVelocity(actor.speed, clearAhead ? cruise : 86, actor.model);
            step = actor.speed * elapsed;
        } else {
            step = (192 + (actor.model*23)%97) * elapsed;
        }
        step = Math.min(step, remaining);
        int x = actor.x + DX[dir] * step, y = actor.y + DY[dir] * step;
        if (actor.vehicle ? freeVehicle(actor, x, y, dir) : freePedestrian(actor, x, y)) {
            actor.dir = dir;
            actor.blocked = 0;
            actor.x = x;
            actor.y = y;
            actor.moving = true;
            actor.stride = (actor.stride + elapsed) % 32;
        } else if (actor.vehicle) {
            actor.speed = 0;
        } else if ((actor.blocked += elapsed) >= 28) {
            actor.routeStep = -actor.routeStep;
            nextWaypoint(actor);
            actor.wait = 16;
            actor.blocked = actor.stride = 0;
        }
    }

    private void turnTowardPlayer(Actor actor) {
        int ahead = (actor.waypoint+2)%actor.route.length;
        int behind = (actor.waypoint+actor.route.length-2)%actor.route.length;
        int x = focusX()/UNIT, y = focusY()/UNIT;
        int costAhead = absolute(actor.route[ahead]-x)+absolute(actor.route[ahead+1]-y);
        int costBehind = absolute(actor.route[behind]-x)+absolute(actor.route[behind+1]-y);
        if (costAhead+36 < costBehind) actor.routeStep = 2;
        else if (costBehind+36 < costAhead) actor.routeStep = -2;
    }

    private void nextWaypoint(Actor actor) {
        actor.waypoint = (actor.waypoint + actor.routeStep + actor.route.length) % actor.route.length;
    }

    Actor nearbyCar() {
        Actor closest = null;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < street.length; i++) {
            Actor a = street[i];
            if (!a.vehicle || a.deferred || a.role == Street.POLICE || a.role==Street.EMS) continue;
            int dx = absolute(a.x-heroX)/UNIT, dy = absolute(a.y-heroY)/UNIT;
            int score = dx*dx+dy*dy;
            if (score >= best || a.speed > 420 || dx > 48 || dy > 48) continue;
            int side = Math.max(0,dx-halfWidth(a.dir,a.model));
            int end = Math.max(0,dy-halfHeight(a.dir,a.model));
            if (side*side+end*end <= 24*24 && pathClear(a.x,a.y,heroX,heroY,3,1)) {
                best = score;
                closest = a;
            }
        }
        return closest;
    }

    private void leaveCar() {
        int first = car.dir < LEFT ? LEFT : UP;
        int bestDistance = Integer.MAX_VALUE;
        int bestX = heroX, bestY = heroY, bestDirection = heroDir;
        for (int i = 0; i < 4; i++) {
            int dir = (first + i) % 4;
            int edge = dir < LEFT ? halfHeight(car.dir,car.model) + 5 : halfWidth(car.dir,car.model) + 7;
            for (int offset = -8; offset <= 8; offset += 8) {
                int x = car.x + (DX[dir] * edge + (dir < LEFT ? offset : 0)) * UNIT;
                int y = car.y + (DY[dir] * edge + (dir >= LEFT ? offset : 0)) * UNIT;
                int distance = edge + absolute(offset);
                if (distance < bestDistance && freeHero(x, y) && pathClear(car.x,car.y,x,y,3,1)) {
                    bestDistance = distance;
                    bestX = x;
                    bestY = y;
                    bestDirection = dir;
                }
            }
        }
        if (bestDistance != Integer.MAX_VALUE) {
            heroX = bestX;
            heroY = bestY;
            heroDir = bestDirection;
            driving = false;
            velocityX = velocityY = stride = 0;
        }
    }

    private static int absolute(int n) { return n < 0 ? -n : n; }
    static int carHalfWidth(int dir) { return dir < LEFT ? 8 : 18; }
    static int carHalfHeight(int dir) { return dir < LEFT ? 18 : 8; }

    static int halfWidth(int dir, int model) {
        return carHalfWidth(dir) + (model >= 2 ? (model == 3 && dir >= LEFT ? 4 : 2) : 0);
    }

    static int halfHeight(int dir, int model) {
        return carHalfHeight(dir) + (model >= 2 ? (model == 3 && dir < LEFT ? 4 : 2) : 0);
    }

    private boolean freeHero(int x, int y) { return freePedestrian(null, x, y); }

    private boolean freePedestrian(Actor actor, int x, int y) {
        return clearActors(actor, x / UNIT, y / UNIT, 4, 3);
    }

    private boolean freeVehicle(Actor actor, int x, int y, int dir) {
        int model = actor.model;
        int xx = x / UNIT, yy = y / UNIT;
        if (xx >= 0 && xx < width && yy >= 0 && yy < height && zoneAt(xx/16,yy/16) == SAND)
            return false;
        return clearActors(actor, xx, yy, halfWidth(dir, model), halfHeight(dir, model));
    }

    private boolean clearActors(Actor self, int x, int y, int w, int h) {
        if (!clear(x - w, y - h, w * 2, h * 2)) return false;
        if (self != null && !driving && touch(x, y, w, h, heroX, heroY, 4, 3)) return false;
        for (int i = 0; i < street.length; i++) {
            Actor other = street[i];
            if (other == self || other.deferred || !other.vehicle && other.hp==0) continue;
            int ow = other.vehicle ? halfWidth(other.dir, other.model) : 4;
            int oh = other.vehicle ? halfHeight(other.dir, other.model) : 3;
            if (touch(x, y, w, h, other.x, other.y, ow, oh)) return false;
        }
        return true;
    }

    private static boolean touch(int x, int y, int w, int h, int fx, int fy, int ow, int oh) {
        return absolute(x - fx / UNIT) < w + ow && absolute(y - fy / UNIT) < h + oh;
    }

    void deferObstructedSpawns() {
        for (int i = 0; i < street.length; i++) {
            Actor a = street[i];
            if (!a.claimed && !a.deferred && !spawnClear(a)) a.deferred = true;
        }
    }

    private boolean spawnClear(Actor actor) {
        if (distance(actor,focusX(),focusY()) < 28) return false;
        return actor.vehicle ? freeVehicle(actor,actor.x,actor.y,actor.dir)
                             : freePedestrian(actor,actor.x,actor.y);
    }

    boolean freeStatic(int x, int y, int w, int h) { return clear(x,y,w,h); }

    private boolean clear(int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x + w > width || y + h > height) return false;
        for (int row = y/16; row <= (y+h-1)/16; row++) {
            for (int col = x/16; col <= (x+w-1)/16; col++) {
                int zone = zoneAt(col,row);
                if (zone == WATER || zone == BLOCKED) return false;
            }
        }
        for (int band = y/64; band <= (y+h-1)/64; band++) {
            Prop[] bucket = solidBands[band];
            for (int i = 0; i < bucket.length; i++) {
                Prop p = bucket[i];
                if (overlaps(x,y,w,h,p.left,p.top,p.width,p.height)) return false;
            }
        }
        return true;
    }

    private static boolean overlaps(int x, int y, int w, int h, int bx, int by, int bw, int bh) {
        return x < bx + bw && x + w > bx && y < by + bh && y + h > by;
    }

    void follow(int viewportWidth, int viewportHeight) {
        int targetX = focusX() + (driving ? velocityX*3 : 0);
        int targetY = focusY() + (driving ? velocityY*3 : 0);
        cameraX += (targetX - cameraX) / 5;
        cameraY += (targetY - cameraY) / 5;
        cameraX = clamp(cameraX, viewportWidth * UNIT / 2, (width - viewportWidth / 2) * UNIT);
        cameraY = clamp(cameraY, viewportHeight * UNIT / 2, (height - viewportHeight / 2) * UNIT);
    }

    private static int clamp(int n, int low, int high) {
        return Math.max(low, Math.min(high, n));
    }
}
