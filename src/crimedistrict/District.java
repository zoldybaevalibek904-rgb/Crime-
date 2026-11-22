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
    private static final int ACCELERATION = 64;
    private static final int BRAKE = 106;
    private static final int[] PICKUP = {274, 257};
    private static final int[] DELIVERY = {435, 523};
    static final int TO_PICKUP = 0, TO_DELIVERY = 1, COMPLETE = 2;

    // The same Downtown streets and sidewalks serve both the player and these routes.
    private static final int[][] ROUTES = {
        {306, 170, 487, 170},
        {306, 259, 480, 259},
        {315, 451, 482, 451},
        {199, 194, 561, 194, 561, 490, 199, 490},
    };

    static final class Actor {
        final int model;
        final boolean vehicle;
        final int[] route;
        int x, y, dir, waypoint, wait, stride, speed;
        boolean moving;

        Actor(int model, boolean vehicle, int[] route) {
            this.model = model;
            this.vehicle = vehicle;
            this.route = route;
        }
    }

    static final class Prop {
        int art, x, y, left, top, width, height, depth;
    }

    final int columns, rows, width, height;
    final byte[] tiles;
    final Prop[] props;
    final Actor[] street = {
        new Actor(1, false, ROUTES[0]), new Actor(2, false, ROUTES[1]),
        new Actor(3, false, ROUTES[2]), new Actor(1, true, ROUTES[3]),
        new Actor(2, true, ROUTES[3]), new Actor(3, true, ROUTES[3])
    };
    int heroX, heroY, carX, carY, heroDir, carDir;
    private int velocityX, velocityY, ticks;
    int mission, celebration;
    int cameraX, cameraY, stride;
    boolean driving, walking;

    District(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing Downtown map");
        DataInputStream in = new DataInputStream(stream);
        try {
            columns = in.readUnsignedByte();
            rows = in.readUnsignedByte();
            width = columns * 16;
            height = rows * 16;
            tiles = new byte[columns * rows];
            in.readFully(tiles);
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
        reset();
    }

    void reset() {
        heroX = 226 * UNIT;
        heroY = 218 * UNIT;
        carX = 267 * UNIT;
        carY = 229 * UNIT;
        heroDir = DOWN;
        carDir = RIGHT;
        walking = driving = false;
        velocityX = velocityY = stride = ticks = celebration = 0;
        mission = TO_PICKUP;
        place(street[0], 323, 170, RIGHT, 2);
        place(street[1], 347, 259, RIGHT, 2);
        place(street[2], 451, 451, LEFT, 0);
        place(street[3], 336, 194, RIGHT, 2);
        place(street[4], 561, 320, DOWN, 4);
        place(street[5], 404, 490, LEFT, 6);
        cameraX = heroX;
        cameraY = heroY;
    }

    private void place(Actor actor, int x, int y, int dir, int waypoint) {
        actor.x = x * UNIT;
        actor.y = y * UNIT;
        actor.dir = dir;
        actor.waypoint = waypoint;
        actor.wait = actor.stride = actor.speed = 0;
        actor.moving = false;
    }

    void tick(boolean up, boolean down, boolean left, boolean right, boolean action) {
        if (action) {
            if (driving) {
                if (canLeaveCar()) leaveCar();
            } else if (nearCar()) {
                driving = true;
                velocityX = velocityY = 0;
            }
        }
        if (driving) drive(up, down, left, right);
        else walk(up, down, left, right);

        ticks++;
        for (int i = 0; i < street.length; i++) {
            Actor actor = street[i];
            boolean distant = absolute(actor.x - cameraX) > 180 * UNIT
                           || absolute(actor.y - cameraY) > 220 * UNIT;
            if (!distant || ((ticks + i) & 1) == 0) patrol(actor, distant ? 2 : 1);
        }
        updateMission();
    }

    int targetX() { return (mission == TO_PICKUP ? PICKUP : DELIVERY)[0]; }
    int targetY() { return (mission == TO_PICKUP ? PICKUP : DELIVERY)[1]; }

    private void updateMission() {
        if (mission == COMPLETE) {
            if (celebration > 0) celebration--;
            return;
        }
        if (driving != (mission == TO_DELIVERY)) return;
        int x = (driving ? carX : heroX) / UNIT - targetX();
        int y = (driving ? carY : heroY) / UNIT - targetY();
        int radius = driving ? 15 : 12;
        if (x*x + y*y <= radius*radius) {
            mission++;
            if (mission == COMPLETE) celebration = 105;
        }
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
        int limit = dx != 0 && dy != 0 ? CAR_SPEED * 181 / UNIT : CAR_SPEED;
        velocityX = changeVelocity(velocityX, dx * limit);
        velocityY = changeVelocity(velocityY, dy * limit);
        // Turning and braking can temporarily mix old and new velocity components.
        int squared = velocityX * velocityX + velocityY * velocityY;
        if (squared > CAR_SPEED * CAR_SPEED) {
            int length = absolute(velocityX) + absolute(velocityY);
            int estimate = (length + squared / length) / 2;
            while (estimate < length) {
                length = estimate;
                estimate = (length + squared / length) / 2;
            }
            if (length * length < squared) length++;
            velocityX = velocityX * CAR_SPEED / length;
            velocityY = velocityY * CAR_SPEED / length;
        }
        int next = direction(velocityX, velocityY, carDir);
        if (next != carDir) {
            if (freeVehicle(null, carX, carY, next)) carDir = next;
            else if ((next < LEFT) != (carDir < LEFT)) {
                if (carDir < LEFT) velocityX = 0;
                else velocityY = 0;
            }
        }
        if (freeVehicle(null, carX + velocityX, carY, carDir)) carX += velocityX;
        else velocityX = 0;
        if (freeVehicle(null, carX, carY + velocityY, carDir)) carY += velocityY;
        else velocityY = 0;
        next = direction(velocityX, velocityY, carDir);
        if (next != carDir && freeVehicle(null, carX, carY, next)) carDir = next;
    }

    private static int changeVelocity(int current, int target) {
        if (target != 0 && current * target < 0) current = 0;
        int step = absolute(target) < absolute(current) ? BRAKE : ACCELERATION;
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

    private void patrol(Actor actor, int elapsed) {
        actor.moving = false;
        if (actor.wait > 0) {
            actor.wait -= elapsed;
            return;
        }
        int dx = actor.route[actor.waypoint] * UNIT - actor.x;
        int dy = actor.route[actor.waypoint + 1] * UNIT - actor.y;
        if (dx == 0 && dy == 0) {
            actor.waypoint = (actor.waypoint + 2) % actor.route.length;
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
            actor.speed = changeVelocity(actor.speed, clearAhead ? 410 : 86);
            step = actor.speed * elapsed;
        } else {
            step = (195 + actor.model * 18) * elapsed;
        }
        step = Math.min(step, remaining);
        int x = actor.x + DX[dir] * step, y = actor.y + DY[dir] * step;
        if (actor.vehicle ? freeVehicle(actor, x, y, dir) : freePedestrian(actor, x, y)) {
            actor.dir = dir;
            actor.x = x;
            actor.y = y;
            actor.moving = true;
            actor.stride = (actor.stride + elapsed) % 32;
        } else if (actor.vehicle) {
            actor.speed = 0;
        }
    }

    boolean nearCar() {
        return absolute(heroX - carX) <= (carHalfWidth(carDir) + 23) * UNIT
            && absolute(heroY - carY) <= (carHalfHeight(carDir) + 19) * UNIT
            && clearDoorPath(heroX, heroY);
    }

    private void leaveCar() {
        int first = carDir < LEFT ? LEFT : UP;
        int bestDistance = Integer.MAX_VALUE;
        int bestX = heroX, bestY = heroY, bestDirection = heroDir;
        for (int i = 0; i < 4; i++) {
            int dir = (first + i) % 4;
            int edge = dir < LEFT ? carHalfHeight(carDir) + 5 : carHalfWidth(carDir) + 7;
            for (int offset = -8; offset <= 8; offset += 8) {
                int x = carX + (DX[dir] * edge + (dir < LEFT ? offset : 0)) * UNIT;
                int y = carY + (DY[dir] * edge + (dir >= LEFT ? offset : 0)) * UNIT;
                int distance = edge + absolute(offset);
                if (distance < bestDistance && freeHero(x, y) && clearDoorPath(x, y)) {
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

    private boolean clearDoorPath(int x, int y) {
        for (int part = 1; part <= 5; part++) {
            int sampleX = (carX + (x - carX) * part / 6) / UNIT;
            int sampleY = (carY + (y - carY) * part / 6) / UNIT;
            if (!clear(sampleX - 3, sampleY - 2, 6, 4)) return false;
        }
        return true;
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
        return clearActors(actor, x / UNIT, y / UNIT, 4, 3, false);
    }

    private boolean freeVehicle(Actor actor, int x, int y, int dir) {
        int model = actor == null ? 0 : actor.model;
        return clearActors(actor, x / UNIT, y / UNIT, halfWidth(dir, model), halfHeight(dir, model), true);
    }

    private boolean clearActors(Actor self, int x, int y, int w, int h, boolean vehicle) {
        if (!clear(x - w, y - h, w * 2, h * 2)) return false;
        if (!vehicle || self != null) {
            if (touch(x, y, w, h, carX, carY, carHalfWidth(carDir), carHalfHeight(carDir))) return false;
        }
        if (self != null && !driving && touch(x, y, w, h, heroX, heroY, 4, 3)) return false;
        for (int i = 0; i < street.length; i++) {
            Actor other = street[i];
            if (other == self) continue;
            int ow = other.vehicle ? halfWidth(other.dir, other.model) : 4;
            int oh = other.vehicle ? halfHeight(other.dir, other.model) : 3;
            if (touch(x, y, w, h, other.x, other.y, ow, oh)) return false;
        }
        return true;
    }

    private static boolean touch(int x, int y, int w, int h, int fx, int fy, int ow, int oh) {
        return absolute(x - fx / UNIT) < w + ow && absolute(y - fy / UNIT) < h + oh;
    }

    private boolean clear(int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x + w > width || y + h > height) return false;
        for (int i = 0; i < props.length; i++) {
            Prop p = props[i];
            if (p.width > 0 && overlaps(x, y, w, h, p.left, p.top, p.width, p.height)) return false;
        }
        return true;
    }

    private static boolean overlaps(int x, int y, int w, int h, int bx, int by, int bw, int bh) {
        return x < bx + bw && x + w > bx && y < by + bh && y + h > by;
    }

    void follow(int viewportWidth, int viewportHeight) {
        int targetX = driving ? carX : heroX;
        int targetY = driving ? carY : heroY;
        cameraX += (targetX - cameraX) / 5;
        cameraY += (targetY - cameraY) / 5;
        cameraX = clamp(cameraX, viewportWidth * UNIT / 2, (width - viewportWidth / 2) * UNIT);
        cameraY = clamp(cameraY, viewportHeight * UNIT / 2, (height - viewportHeight / 2) * UNIT);
    }

    private static int clamp(int n, int low, int high) {
        return Math.max(low, Math.min(high, n));
    }
}
