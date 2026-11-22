package crimedistrict;

import java.io.IOException;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.midlet.MIDlet;

public final class CrimeDistrict extends MIDlet {
    private Screen screen;

    protected void startApp() {
        if (screen == null) screen = new Screen();
        Display.getDisplay(this).setCurrent(screen);
        if (screen.isShown()) screen.resume();
    }

    protected void pauseApp() { if (screen != null) screen.suspend(); }
    protected void destroyApp(boolean unconditional) { if (screen != null) screen.stop(); }

    private final class Screen extends GameCanvas implements Runnable {
        private static final int TITLE = 0, PLAY = 1, PAUSE = 2;
        private static final int MENU = 256, BACK = 512;
        private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8, FIRE = 16;
        private static final int TOP_LEFT = Graphics.TOP | Graphics.LEFT;
        private final Font label = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        private final Font headline = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        private final String[] SECTORS = {"DOWNTOWN","RESIDENTIAL","INDUSTRIAL","BEACH"};
        private final Image[] props = new Image[25];
        private final SaveGame saves = new SaveGame();
        private District city;
        private Image tiles, sedan, traffic, cruiser;
        private final Image[] walkers = new Image[District.PEDESTRIAN_MODELS + 1];
        private final int[] touched = new int[walkers.length];
        private int savedRevision;
        private boolean saveError, artFailure;
        private int[] drawOrder;
        private int[] drawDepth;
        private static final int HERO = -2;
        private volatile boolean active, closed;
        private Thread loop;
        private boolean bufferDirty = true;
        private int state, selection, held, pressed;

        Screen() {
            super(false);
            setFullScreenMode(true);
            try {
                city = new District(getClass().getResourceAsStream("/city.bin"));
                drawOrder = new int[city.street.length + 2];
                drawDepth = new int[drawOrder.length];
                tiles = Image.createImage("/tiles.png");
                walkers[0] = Image.createImage("/walker.png");
                cruiser = Image.createImage("/cruiser.png");
                traffic = Image.createImage("/traffic.png");
                sedan = Image.createImage("/sedan.png");
                for (int i = 0; i < props.length; i++) props[i] = Image.createImage("/prop" + i + ".png");
            } catch (IOException failure) {
                city = null;
            }
        }

        synchronized void resume() {
            if (closed) return;
            bufferDirty = true;
            active = true;
            if (loop == null) {
                loop = new Thread(this);
                loop.start();
            }
            notifyAll();
        }

        synchronized void suspend() {
            active = false;
            held = pressed = 0;
        }

        synchronized void stop() {
            if (!closed && state == PLAY && !artFailure) checkpoint();
            closed = true;
            notifyAll();
        }

        protected void hideNotify() { suspend(); }
        protected void showNotify() { resume(); }

        protected synchronized void sizeChanged(int width, int height) {
            bufferDirty = true;
        }

        private int keyBit(int code) {
            switch (code) {
                case KEY_NUM2: return UP;
                case KEY_NUM8: return DOWN;
                case KEY_NUM4: return LEFT;
                case KEY_NUM6: return RIGHT;
                case KEY_NUM5: return FIRE;
                case -6: return MENU;
                case -7: return BACK;
                default:
                    try {
                        switch (getGameAction(code)) {
                            case CanvasActions.UP: return UP;
                            case CanvasActions.DOWN: return DOWN;
                            case CanvasActions.LEFT: return LEFT;
                            case CanvasActions.RIGHT: return RIGHT;
                            case CanvasActions.FIRE: return FIRE;
                            default: return 0;
                        }
                    } catch (IllegalArgumentException unknownKey) {
                        return 0;
                    }
            }
        }

        protected synchronized void keyPressed(int code) {
            int bit = keyBit(code);
            pressed |= bit & ~held;
            held |= bit;
        }

        protected synchronized void keyReleased(int code) { held &= ~keyBit(code); }

        protected synchronized void keyRepeated(int code) {
            if (active) held |= keyBit(code) & (UP | DOWN | LEFT | RIGHT);
        }

        public void run() {
            Graphics graphics = null;
            long deadline = System.currentTimeMillis();
            int fraction = 0;
            try {
                while (!closed) {
                    int keys, edges;
                    boolean refreshBuffer;
                    synchronized (this) {
                        while (!active && !closed) {
                            if (state == PLAY) {
                                state = PAUSE;
                                selection = 0;
                                checkpoint();
                            }
                            wait();
                            deadline = System.currentTimeMillis();
                        }
                        if (closed) return;
                        keys = held;
                        edges = pressed;
                        pressed = 0;
                        refreshBuffer = bufferDirty;
                        bufferDirty = false;
                    }
                    if (refreshBuffer) graphics = getGraphics();
                    synchronized (this) {
                        if (closed) return;
                        if (city != null && !artFailure) advance(keys | edges, edges);
                        else if ((edges & (BACK | FIRE)) != 0) exit();
                    }
                    render(graphics);
                    flushGraphics();
                    deadline += 33;
                    if (++fraction == 3) { deadline++; fraction = 0; }
                    long delay = deadline - System.currentTimeMillis();
                    if (delay > 0) Thread.sleep(delay);
                    else if (delay < -66) deadline = System.currentTimeMillis();
                }
            } catch (InterruptedException interrupted) {
                stop();
            }
        }

        private void exit() {
            stop();
            CrimeDistrict.this.notifyDestroyed();
        }

        private void checkpoint() {
            if (city != null) {
                saveError = !saves.write(city);
                savedRevision = city.story.revision;
            }
        }

        private int menuItems() { return state == TITLE && saves.available() ? 3 : 2; }

        private void advance(int keys, int edges) {
            if (state == PLAY) {
                if ((edges & (MENU | BACK)) != 0) {
                    state = PAUSE;
                    selection = 0;
                    checkpoint();
                } else {
                    city.tick((keys & UP) != 0, (keys & DOWN) != 0, (keys & LEFT) != 0,
                              (keys & RIGHT) != 0, (edges & FIRE) != 0);
                    city.follow(viewWidth(), viewHeight());
                    if (city.story.revision != savedRevision || city.ticks%450 == 0) checkpoint();
                    if (city.ticks%120 == 0) trimWalkers();
                }
                return;
            }
            int choices = menuItems();
            if ((edges & UP) != 0) selection = (selection+choices-1)%choices;
            if ((edges & DOWN) != 0) selection = (selection+1)%choices;
            if ((edges & BACK) != 0) {
                if (state == PAUSE) state = PLAY;
                else exit();
                return;
            }
            if ((edges & (MENU | FIRE)) == 0) return;
            if (state == TITLE) {
                if (selection == choices-1) exit();
                else {
                    boolean resumed = choices == 3 && selection == 0;
                    if (resumed && !saves.load(city)) {
                        saveError = true;
                        return;
                    }
                    if (!resumed) {
                        city.reset();
                        checkpoint();
                    }
                    city.follow(viewWidth(),viewHeight());
                    savedRevision = city.story.revision;
                    for (int i = 1; i < walkers.length; i++) walkers[i] = null;
                    state = PLAY;
                }
            } else {
                state = selection == 0 ? PLAY : TITLE;
                selection = 0;
            }
        }

        private void trimWalkers() {
            for (int i = 1; i < walkers.length; i++) {
                if (city.ticks-touched[i] > 180) walkers[i] = null;
            }
        }

        private int viewWidth() { return Math.min(240, getWidth()); }
        private int viewHeight() { return Math.min(320, getHeight()); }

        private void render(Graphics g) {
            int w = viewWidth(), h = viewHeight();
            int ox = (getWidth() - w) / 2, oy = (getHeight() - h) / 2;
            g.setClip(0, 0, getWidth(), getHeight());
            fill(g, 0x12131a, 0, 0, getWidth(), getHeight());
            g.translate(ox, oy);
            g.setClip(0, 0, w, h);
            g.setFont(label);
            if (city == null || artFailure) {
                text(g, "RESOURCE LOAD ERROR", w / 2, h / 2, true, 0xffbf89);
            } else {
                drawWorld(g, w, h);
                if (state == PLAY) drawHud(g, w, h);
                else drawMenu(g, w, h);
            }
            g.translate(-ox, -oy);
        }

        private void drawWorld(Graphics g, int w, int h) {
            int sx = city.cameraX / District.UNIT - w / 2;
            int sy = city.cameraY / District.UNIT - h / 2;
            int lastCol = Math.min(city.columns - 1, (sx + w - 1) / 16);
            int lastRow = Math.min(city.rows - 1, (sy + h - 1) / 16);
            for (int row = sy / 16; row <= lastRow; row++) {
                for (int col = sx / 16; col <= lastCol; col++) {
                    int tile = city.tileAt(col,row);
                    if (tile == 44 || tile == 48) tile += (city.ticks/12 + (col+row)%2) % 4;
                    region(g, tiles, tile % 8 * 16, tile / 8 * 16, 16, 16, col * 16 - sx, row * 16 - sy);
                }
            }
            for (int i = 0; i < city.props.length; i++) {
                District.Prop prop = city.props[i];
                if (prop.width == 0) drawProp(g, prop, sx, sy, w, h);
            }
            int visible = 0;
            if (!city.driving && visibleActor(city.heroX, city.heroY, sx, sy, w, h)) {
                insertActor(visible++, HERO, city.heroY / District.UNIT + 3);
            }
            for (int i = 0; i < city.street.length; i++) {
                District.Actor actor = city.street[i];
                if (!actor.deferred && visibleActor(actor.x, actor.y, sx, sy, w, h)) {
                    int foot = actor.vehicle ? District.halfHeight(actor.dir, actor.model) : 3;
                    insertActor(visible++, i, actor.y / District.UNIT + foot);
                }
            }
            int actor = 0;
            for (int i = 0; i < city.props.length; i++) {
                District.Prop prop = city.props[i];
                if (prop.width == 0) continue;
                while (actor < visible && drawDepth[actor] <= prop.depth) {
                    drawActor(g, drawOrder[actor++], sx, sy);
                }
                drawProp(g, prop, sx, sy, w, h);
            }
            while (actor < visible) drawActor(g, drawOrder[actor++], sx, sy);
            if (state == PLAY && city.story.marker()) {
                drawTarget(g, city.story.targetX(city) - sx, city.story.targetY(city) - sy, w, h);
            }
            District.Actor door = state == PLAY && !city.driving ? city.nearbyCar() : null;
            if (door != null) {
                int x = door.x / District.UNIT - sx;
                int y = door.y / District.UNIT - sy - 61;
                x = Math.max(58, Math.min(w - 58, x));
                y = Math.max(56, y);
                panel(g, x - 57, y, 114, 20, 0x25232b);
                text(g, door.role == Street.CIVILIAN && !door.claimed ? "5  TAKE VEHICLE" : "5  ENTER VEHICLE",
                     x, y + 2, true, 0xffe0aa);
                g.fillTriangle(x - 4, y + 21, x + 4, y + 21, x, y + 25);
            }
        }

        private void drawProp(Graphics g, District.Prop prop, int sx, int sy, int w, int h) {
            Image art = props[prop.art];
            int x = prop.x - sx, y = prop.y - sy;
            if (x < w && y < h && x + art.getWidth() > 0 && y + art.getHeight() > 0) {
                g.drawImage(art, x, y, TOP_LEFT);
            }
        }

        private boolean visibleActor(int fx, int fy, int sx, int sy, int w, int h) {
            int x = fx / District.UNIT - sx, y = fy / District.UNIT - sy;
            return x >= -24 && y >= -24 && x < w + 24 && y < h + 30;
        }

        private void insertActor(int slot, int actor, int depth) {
            while (slot > 0 && drawDepth[slot - 1] > depth) {
                drawOrder[slot] = drawOrder[slot - 1];
                drawDepth[slot] = drawDepth[slot - 1];
                slot--;
            }
            drawOrder[slot] = actor;
            drawDepth[slot] = depth;
        }

        private void drawActor(Graphics g, int id, int sx, int sy) {
            if (id == HERO) {
                drawWalker(g, 0, city.heroDir, city.walking ? walkFrame(city.stride / 6) : 0,
                           city.heroX / District.UNIT - sx, city.heroY / District.UNIT - sy);
            } else {
                District.Actor actor = city.street[id];
                int x = actor.x / District.UNIT - sx, y = actor.y / District.UNIT - sy;
                if (actor.vehicle) {
                    if (actor.model == 0 || actor.model == 4) {
                        region(g, actor.model == 0 ? sedan : cruiser, actor.dir * 44, 0, 44, 44, x - 22,y - 22);
                        if (actor.model == 4 && actor.duty == Street.CHASE) {
                            fill(g, (city.ticks/5)%2 == 0 ? 0xe38b7e : 0x94b8d7, x-2, y-8, 4, 1);
                        }
                    } else {
                        region(g, traffic, actor.dir * 48, (actor.model - 1) * 48, 48, 48, x - 24, y - 24);
                    }
                } else {
                    drawWalker(g, actor.model, actor.dir, actor.moving ? walkFrame(actor.stride / 8) : 0, x, y);
                }
            }
        }

        private int walkFrame(int phase) {
            // The authored passing pose B connects both contact poses.
            return phase == 3 ? 2 : 1 + phase;
        }

        private void drawWalker(Graphics g, int model, int direction, int frame, int x, int y) {
            touched[model] = city.ticks;
            if (walkers[model] == null) {
                try {
                    walkers[model] = Image.createImage("/pedestrian" + model + ".png");
                } catch (IOException missing) {
                    artFailure = true;
                    return;
                }
            }
            region(g, walkers[model], frame * 24, direction * 36, 24, 36, x - 12, y - 31);
        }

        private void drawTarget(Graphics g, int x, int y, int w, int h) {
            int margin = 12, top = w >= 236 ? 58 : 74, bottom = h - 35;
            if (x >= margin && x <= w - margin && y >= top && y <= bottom) {
                g.setColor(0x28252e);
                g.drawArc(x - 11, y - 6, 22, 12, 0, 360);
                g.setColor(0xf0b870);
                g.drawArc(x - 10, y - 5, 20, 10, 0, 360);
                g.fillTriangle(x - 5, y - 17, x + 5, y - 17, x, y - 10);
                return;
            }
            int cx = w / 2, cy = (top + bottom) / 2;
            int dx = x - cx, dy = y - cy;
            int sx = (w / 2 - margin) * 1024 / Math.max(1, Math.abs(dx));
            int sy = (bottom - cy) * 1024 / Math.max(1, Math.abs(dy));
            int scale = Math.min(sx, sy);
            int tipX = cx + dx * scale / 1024;
            int tipY = cy + dy * scale / 1024;
            int axisX = sx <= sy ? (dx < 0 ? -1 : 1) : 0;
            int axisY = sx > sy ? (dy < 0 ? -1 : 1) : 0;
            arrow(g, tipX, tipY, axisX, axisY, 10, 7, 0x28252e);
            arrow(g, tipX, tipY, axisX, axisY, 8, 5, 0xf0b870);
        }

        private void arrow(Graphics g, int x, int y, int dx, int dy, int length, int width, int color) {
            g.setColor(color);
            g.fillTriangle(x, y, x - dx*length + dy*width, y - dy*length + dx*width,
                           x - dx*length - dy*width, y - dy*length - dx*width);
        }

        private void drawHud(Graphics g, int w, int h) {
            panel(g, 8, 8, 99, 20, 0x25232b);
            fill(g, 0xd5a367, 8, 8, 3, 20);
            text(g, SECTORS[city.sector()], 17, 10, false, 0xffe0aa);
            panel(g, 8, h - 24, 49, 17, 0x25232b);
            text(g, "MENU", 32, h - 24, true, 0xe8d3b8);
            if (!city.story.finished() || city.story.delay > 0) {
                String title = city.story.title();
                String task = city.story.objective(city);
                int boardWidth = Math.min(123, w - 16);
                int bx = w - boardWidth - 8;
                int by = w >= 236 ? 8 : 31;
                panel(g, bx, by, boardWidth, 36, 0x25232b);
                text(g, title, bx + boardWidth / 2, by + 3, true, 0xffe0aa);
                text(g, task, bx + boardWidth / 2, by + 18, true, 0xcdb3a3);
            }
            if (city.wanted > 0) {
                panel(g, 8, 31, 59, 17, 0x342831);
                for (int i = 0; i < 3; i++) drawStar(g, 19+16*i,38,i < city.wanted);
            } else if (city.notice > 0) {
                panel(g, 8, 31, 95, 17, 0x342831);
                text(g, "POLICE RELEASE", 55, 31, true, 0xffe0aa);
            }
            if (city.driving) {
                panel(g, w - 113, h - 24, 105, 17, 0x25232b);
                text(g, city.canLeaveCar() ? "5  EXIT VEHICLE" : "STOP TO EXIT",
                     w - 60, h - 24, true, 0xe8d3b8);
            }
        }

        private void drawStar(Graphics g,int x,int y,boolean lit) {
            g.setColor(lit ? 0xf4bc76 : 0x76555a);
            g.fillTriangle(x,y-5,x-4,y+5,x+4,y+5);
            g.fillTriangle(x-5,y-1,x+5,y-1,x,y+4);
        }

        private void drawMenu(Graphics g, int w, int h) {
            int center = w / 2, y = h / 2 - 65;
            panel(g, center - 105, y, 210, menuItems() == 3 ? 155 : 130, 0x25232b);
            fill(g, 0xe2a76b, center - 105, y, 210, 3);
            g.setFont(headline);
            text(g, state == TITLE ? "CRIME DISTRICT" : "PAUSED", center, y + 12, true, 0xffe0aa);
            g.setFont(label);
            text(g, saveError ? "SAVE UNAVAILABLE" : state == TITLE ? "CITY  /  ALPHA 0.3.0" : SECTORS[city.sector()],
                 center, y + 41, true, 0xc8a58c);
            int choices = menuItems();
            for (int i = 0; i < choices; i++) {
                int line = y + 65 + i * 25;
                if (selection == i) panel(g, center - 76, line, 152, 21, 0x594042);
                String caption;
                if (state == PAUSE) caption = i == 0 ? "RESUME" : "MAIN MENU";
                else if (i == choices-1) caption = "EXIT";
                else caption = i == 0 && choices == 3 ? "CONTINUE" : "NEW GAME";
                text(g, caption, center, line + 2, true, selection == i ? 0xffe0aa : 0xbba49b);
            }
        }

        private void panel(Graphics g, int x, int y, int w, int h, int color) {
            fill(g, 0x14141b, x + 2, y + 2, w, h);
            fill(g, color, x, y, w, h);
            g.setColor(0x937779);
            g.drawRect(x, y, w - 1, h - 1);
        }

        private void fill(Graphics g, int color, int x, int y, int w, int h) {
            g.setColor(color);
            g.fillRect(x, y, w, h);
        }

        private void text(Graphics g, String value, int x, int y, boolean centered, int color) {
            g.setColor(color);
            g.drawString(value, x, y, Graphics.TOP | (centered ? Graphics.HCENTER : Graphics.LEFT));
        }

        private void region(Graphics g, Image image, int u, int v, int w, int h, int x, int y) {
            g.drawRegion(image, u, v, w, h, Sprite.TRANS_NONE, x, y, TOP_LEFT);
        }
    }

    private interface CanvasActions {
        int UP = javax.microedition.lcdui.Canvas.UP;
        int DOWN = javax.microedition.lcdui.Canvas.DOWN;
        int LEFT = javax.microedition.lcdui.Canvas.LEFT;
        int RIGHT = javax.microedition.lcdui.Canvas.RIGHT;
        int FIRE = javax.microedition.lcdui.Canvas.FIRE;
    }
}
