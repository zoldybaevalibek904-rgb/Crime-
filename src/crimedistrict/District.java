package crimedistrict;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Downtown geometry and fixed-point simulation, independent of the display. */
final class District {
    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    static final int[] DX = {0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0};
    static final int[] OPPOSITE = {DOWN, UP, RIGHT, LEFT};
    static final int UNIT = 256;
    static final int HERO_SPEED = 360;
    static final int CAR_SPEED = 920;
    static final int REVERSE_SPEED = 416;

    static final class Prop {
        int art, x, y, left, top, width, height, depth;
    }

    final int columns, rows, width, height;
    final byte[] tiles;
    final Prop[] props;
    int heroX, heroY, carX, carY, heroDir, carDir, speed;
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
        walking = driving = steered = false;
        speed = stride = 0;
        cameraX = heroX;
        cameraY = heroY;
    }

    void tick(boolean up, boolean down, boolean left, boolean right, boolean action) {
        if (action) {
            if (driving) {
                if (absolute(speed) <= UNIT) leaveCar();
            } else if (nearCar()) {
                driving = true;
                speed = 0;
            }
        }
        if (driving) {
            drive(up, down, left, right);
        } else {
            walk(up, down, left, right);
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
        // One quarter-turn per press, as soon as there is room to turn.
        if (left == right) steered = false;
        if (left != right && !steered) {
            int next;
            if (carDir == UP) next = left ? LEFT : RIGHT;
            else if (carDir == DOWN) next = left ? RIGHT : LEFT;
            else if (carDir == LEFT) next = left ? DOWN : UP;
            else next = left ? UP : DOWN;
            if (freeCar(carX, carY, next)) {
                carDir = next;
                steered = true;
            }
        }
        if (up && !down) {
            speed = Math.min(CAR_SPEED, speed + (speed < 0 ? 100 : 36));
        } else if (down && !up) {
            speed = Math.max(speed > 0 ? 0 : -REVERSE_SPEED, speed - (speed > 0 ? 92 : 29));
        } else if (speed > 0) {
            speed = Math.max(0, speed - 26);
        } else {
            speed = Math.min(0, speed + 26);
        }
        int x = carX + DX[carDir] * speed;
        int y = carY + DY[carDir] * speed;
        if (freeCar(x, y, carDir)) {
            carX = x;
            carY = y;
        } else {
            speed = 0;
        }
    }

    private boolean steered;

    boolean nearCar() {
        return absolute(heroX - carX) <= (carHalfWidth(carDir) + 23) * UNIT
            && absolute(heroY - carY) <= (carHalfHeight(carDir) + 19) * UNIT;
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
                if (distance < bestDistance && freeHero(x, y)) {
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
            steered = false;
            speed = stride = 0;
        }
    }

    private static int absolute(int n) { return n < 0 ? -n : n; }
    static int carHalfWidth(int dir) { return dir < LEFT ? 8 : 18; }
    static int carHalfHeight(int dir) { return dir < LEFT ? 18 : 8; }

    private boolean freeHero(int x, int y) {
        int left = x / UNIT - 4, top = y / UNIT - 3;
        int w = carHalfWidth(carDir), h = carHalfHeight(carDir);
        return clear(left, top, 8, 6)
            && !overlaps(left, top, 8, 6, carX / UNIT - w, carY / UNIT - h, w * 2, h * 2);
    }

    private boolean freeCar(int x, int y, int dir) {
        int w = carHalfWidth(dir), h = carHalfHeight(dir);
        return clear(x / UNIT - w, y / UNIT - h, w * 2, h * 2);
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
