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
        screen.resume();
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
        private final Image[] props = new Image[16];
        private District city;
        private Image tiles, walker, sedan;
        private volatile boolean active, closed;
        private Thread loop;
        private int state, selection, held, pressed;

        Screen() {
            super(false);
            setFullScreenMode(true);
            try {
                city = new District(getClass().getResourceAsStream("/downtown.bin"));
                tiles = Image.createImage("/tiles.png");
                walker = Image.createImage("/walker.png");
                sedan = Image.createImage("/sedan.png");
                for (int i = 0; i < props.length; i++) props[i] = Image.createImage("/prop" + i + ".png");
            } catch (IOException failure) {
                city = null;
            }
        }

        synchronized void resume() {
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
            closed = true;
            notifyAll();
        }

        protected void hideNotify() { suspend(); }
        protected void showNotify() { if (!closed) resume(); }

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

        public void run() {
            Graphics graphics = getGraphics();
            long deadline = System.currentTimeMillis();
            int fraction = 0;
            try {
                while (!closed) {
                    int keys, edges;
                    synchronized (this) {
                        while (!active && !closed) {
                            if (state == PLAY) { state = PAUSE; selection = 0; }
                            wait();
                            deadline = System.currentTimeMillis();
                        }
                        if (closed) return;
                        keys = held;
                        edges = pressed;
                        pressed = 0;
                    }
                    if (city != null) advance(keys | edges, edges);
                    else if ((edges & (BACK | FIRE)) != 0) exit();
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

        private void advance(int keys, int edges) {
            if (state == PLAY) {
                if ((edges & (MENU | BACK)) != 0) {
                    state = PAUSE;
                    selection = 0;
                } else {
                    city.tick((keys & UP) != 0, (keys & DOWN) != 0, (keys & LEFT) != 0,
                              (keys & RIGHT) != 0, (edges & FIRE) != 0);
                    city.follow(viewWidth(), viewHeight());
                }
                return;
            }
            if ((edges & (UP | DOWN)) != 0) selection ^= 1;
            if ((edges & BACK) != 0) {
                if (state == PAUSE) state = PLAY;
                else exit();
                return;
            }
            if ((edges & (MENU | FIRE)) == 0) return;
            if (state == TITLE) {
                if (selection == 1) exit();
                else {
                    city.reset();
                    state = PLAY;
                }
            } else {
                state = selection == 0 ? PLAY : TITLE;
                selection = 0;
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
            if (city == null) {
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
                    int tile = city.tiles[row * city.columns + col] & 255;
                    region(g, tiles, tile % 8 * 16, tile / 8 * 16, 16, 16, col * 16 - sx, row * 16 - sy);
                }
            }
            int heroY = city.heroY / District.UNIT;
            int carY = city.carY / District.UNIT;
            int carDepth = carY + District.carHalfHeight(city.carDir);
            boolean heroDrawn = city.driving, carDrawn = false;
            for (int i = 0; i <= city.props.length; i++) {
                District.Prop prop = i < city.props.length ? city.props[i] : null;
                int depth = prop == null ? Integer.MAX_VALUE : prop.depth;
                if (!heroDrawn && heroY + 3 <= depth && (carDrawn || heroY + 3 <= carDepth)) {
                    drawHero(g, sx, sy);
                    heroDrawn = true;
                }
                if (!carDrawn && carDepth <= depth) {
                    region(g, sedan, city.carDir * 44, 0, 44, 44,
                           city.carX / District.UNIT - sx - 22, carY - sy - 22);
                    carDrawn = true;
                }
                if (!heroDrawn && heroY + 3 <= depth) {
                    drawHero(g, sx, sy);
                    heroDrawn = true;
                }
                if (prop != null) {
                    Image art = props[prop.art];
                    int x = prop.x - sx, y = prop.y - sy;
                    if (x < w && y < h && x + art.getWidth() > 0 && y + art.getHeight() > 0) {
                        g.drawImage(art, x, y, TOP_LEFT);
                    }
                }
            }
            if (state == PLAY && !city.driving && city.nearCar()) {
                int x = city.carX / District.UNIT - sx;
                int y = carY - sy - 37;
                x = Math.max(58, Math.min(w - 58, x));
                y = Math.max(35, y);
                panel(g, x - 57, y, 114, 20, 0x25232b);
                text(g, "5  ENTER VEHICLE", x, y + 2, true, 0xffe0aa);
                g.fillTriangle(x - 4, y + 21, x + 4, y + 21, x, y + 25);
            }
        }

        private void drawHero(Graphics g, int sx, int sy) {
            int frame = city.walking ? 1 + city.stride / 6 : 0;
            region(g, walker, frame * 24, city.heroDir * 30, 24, 30,
                   city.heroX / District.UNIT - sx - 12, city.heroY / District.UNIT - sy - 25);
        }

        private void drawHud(Graphics g, int w, int h) {
            panel(g, 8, 8, 99, 20, 0x25232b);
            fill(g, 0xd5a367, 8, 8, 3, 20);
            text(g, "DOWNTOWN", 17, 10, false, 0xffe0aa);
            panel(g, 8, h - 24, 49, 17, 0x25232b);
            text(g, "MENU", 32, h - 24, true, 0xe8d3b8);
            if (city.driving) {
                panel(g, w - 105, h - 24, 97, 17, 0x25232b);
                text(g, "5  EXIT VEHICLE", w - 56, h - 24, true, 0xe8d3b8);
            }
        }

        private void drawMenu(Graphics g, int w, int h) {
            int center = w / 2, y = h / 2 - 65;
            panel(g, center - 105, y, 210, 130, 0x25232b);
            fill(g, 0xe2a76b, center - 105, y, 210, 3);
            g.setFont(headline);
            text(g, state == TITLE ? "CRIME DISTRICT" : "PAUSED", center, y + 12, true, 0xffe0aa);
            g.setFont(label);
            text(g, state == TITLE ? "DOWNTOWN  /  ALPHA 0.1" : "DOWNTOWN",
                 center, y + 41, true, 0xc8a58c);
            for (int i = 0; i < 2; i++) {
                int line = y + 65 + i * 25;
                if (selection == i) panel(g, center - 76, line, 152, 21, 0x594042);
                String caption = i == 0 ? (state == TITLE ? "NEW GAME" : "RESUME")
                                        : (state == TITLE ? "EXIT" : "MAIN MENU");
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
