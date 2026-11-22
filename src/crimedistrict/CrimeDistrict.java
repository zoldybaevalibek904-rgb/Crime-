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
        private static final int TITLE = 0, PLAY = 1, PAUSE = 2, STORE = 3, INVENTORY=4;
        private static final int MENU = 256, BACK = 512, HORN=1024, SWITCH=2048, RELOAD=4096, PACK=8192, MAP=16384;
        private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8, FIRE = 16;
        private static final int TOP_LEFT = Graphics.TOP | Graphics.LEFT;
        private final Font label = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        private final Font headline = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        private final String[] SECTORS = {"DOWNTOWN","RESIDENTIAL","INDUSTRIAL","BEACH","HOSPITAL","COMMERCIAL","SUBURBS","GANG TURF","COASTAL ROAD"};
        private final Image[] props = new Image[29];
        private final SaveGame saves = new SaveGame();
        private District city;
        private Image tiles, sedan, traffic, cruiser, ambulance, motors, tank, heli;
        private Image walk, run, hit, fall, actionImage, roomImage;
        private int actionBank=-1, roomBank=-1;
        private final Sprint sprint=new Sprint();
        private boolean mapOpen;
        private final Image[] walkers = new Image[District.PEDESTRIAN_MODELS + 1];
        private final int[] touched = new int[walkers.length];
        private int savedRevision, savedTrade, savedLife, savedInventory, savedRoom, store;
        private boolean saveError, artFailure;
        private int[] drawOrder;
        private int[] drawDepth;
        private static final int HERO = -2;
        private volatile boolean active, closed;
        private Thread loop;
        private boolean bufferDirty = true;
        private int state, selection, held, pressed;
        private int walletValue=-1, toolKey=-1;
        private String walletLabel="", toolLabel="";

        Screen() {
            super(false);
            setFullScreenMode(true);
            try {
                city = new District(getClass().getResourceAsStream("/city04.bin"));
                drawOrder = new int[city.street.length + 2];
                drawDepth = new int[drawOrder.length];
                tiles = Image.createImage("/tiles.png");
                cruiser = Image.createImage("/cruiser.png");
                ambulance = Image.createImage("/ambulance.png");
                walk=Image.createImage("/walk05.png");
                run=Image.createImage("/run05.png");
                hit=Image.createImage("/hurt05.png");
                fall=Image.createImage("/fall05.png");
                motors=Image.createImage("/motors05.png");
                tank=Image.createImage("/tank05.png");
                heli=Image.createImage("/heli05.png");
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
            sprint.clear();
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
                case KEY_NUM1: return HORN;
                case KEY_NUM3: return 0;
                case KEY_STAR: return SWITCH;
                case KEY_NUM0: return PACK;
                case KEY_POUND: return MAP;
                case KEY_NUM9: return RELOAD;
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
            if((bit&15)!=0 && state==PLAY && city!=null && !city.driving)
                sprint.press(bit,held,System.currentTimeMillis());
            pressed |= bit & ~held;
            held |= bit;
        }

        protected synchronized void keyReleased(int code) {
            held &= ~keyBit(code);
            sprint.release(held&15);
        }

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
                savedTrade = city.trade.revision;
                savedLife = city.lifeRevision;
                savedInventory=city.gear.revision;
                savedRoom=city.rooms.revision;
            }
        }

        private int menuItems() { return state == TITLE && saves.available() ? 3 : 2; }

        private void advance(int keys, int edges) {
            if(state==INVENTORY) {
                if((edges&(BACK|PACK))!=0) state=PLAY;
                else if((edges&FIRE)!=0) { city.gear.heal(city); checkpoint(); }
                return;
            }
            if (state == STORE) {
                int n=city.trade.options(store);
                if ((edges&UP)!=0) selection=(selection+n-1)%n;
                if ((edges&DOWN)!=0) selection=(selection+1)%n;
                if ((edges&BACK)!=0 || ((edges&FIRE)!=0 && selection==n-1)) {
                    state=PLAY;
                    selection=0;
                    return;
                }
                if ((edges&FIRE)!=0) { city.trade.choose(city,store,selection); checkpoint(); }
                return;
            }
            if (state==PLAY) {
                if ((edges & (MENU | BACK)) != 0) {
                    state=PAUSE;
                    sprint.clear();
                    selection=0;
                    checkpoint();
                    return;
                }
                if((edges&MAP)!=0) mapOpen=!mapOpen;
                if((edges&PACK)!=0 && !city.driving && city.combat.life==Combat.NORMAL) {
                    state=INVENTORY;
                    sprint.clear();
                    return;
                }
                boolean action=(edges&FIRE)!=0;
                if(city.rooms.active()) {
                    city.rooms.move(inputDirection(keys),sprint.active());
                    city.trade.advanceTime();
                    if(action) {
                        store=city.rooms.action(city);
                        if(store!=0) { state=STORE; selection=0; }
                        checkpoint();
                    } else if(city.trade.revision!=savedTrade) checkpoint();
                    return;
                }
                if(action && !city.driving && city.combat.life==Combat.NORMAL &&
                   city.gear.nearby(city)<0 && city.nearbyCar()==null &&
                   city.rooms.door(city)<0) {
                    District.Actor contact=city.conversational();
                    store=contact==null?city.trade.place(city):
                        contact.model==28 && city.story.finished()?Trade.WORK:0;
                    if(store!=0) { state=STORE; selection=0; return; }
                }
                int actions=(keys&HORN)!=0?16:0;
                if((edges&SWITCH)!=0) actions|=2;
                if((edges&RELOAD)!=0) actions|=4;
                if(sprint.active() && (keys&15)!=0 && !city.driving) actions|=8;
                city.tick((keys&UP)!=0,(keys&DOWN)!=0,(keys&LEFT)!=0,(keys&RIGHT)!=0,action,actions);
                if(city.driving) sprint.clear();
                city.follow(viewWidth(),viewHeight());
                if(city.story.revision!=savedRevision || city.trade.revision!=savedTrade ||
                    city.lifeRevision!=savedLife || city.gear.revision!=savedInventory ||
                    city.rooms.revision!=savedRoom || city.ticks%450==0) checkpoint();
                if(city.ticks%120==0) trimWalkers();
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
                    savedTrade = city.trade.revision;
                    savedLife = city.lifeRevision;
                savedInventory=city.gear.revision;
                savedRoom=city.rooms.revision;
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

        private int inputDirection(int keys) {
            if((keys&UP)!=0) return District.UP;
            if((keys&DOWN)!=0) return District.DOWN;
            if((keys&LEFT)!=0) return District.LEFT;
            if((keys&RIGHT)!=0) return District.RIGHT;
            return -1;
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
                if(city.rooms.active() && state!=TITLE) {
                    drawRoom(g,w,h);
                    if(state==STORE) drawStore(g,w,h);
                    else if(state==INVENTORY) drawInventory(g,w,h);
                    else if(state==PAUSE) drawMenu(g,w,h);
                } else {
                    drawWorld(g,w,h);
                    if(state==PLAY) {
                        drawHud(g,w,h);
                        if(mapOpen) drawMap(g,w);
                    } else if(state==STORE) drawStore(g,w,h);
                    else if(state==INVENTORY) drawInventory(g,w,h);
                    else drawMenu(g,w,h);
                }
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
            for(int i=0;i<city.gear.type.length;i++) {
                if(!city.gear.visible(city,i)) continue;
                int x=city.gear.x[i]-sx,y=city.gear.y[i]-sy;
                if(x<0 || y<0 || x>=w || y>=h) continue;
                g.setColor(0x2e2631);
                g.fillArc(x-5,y-3,11,6,0,360);
                fill(g,city.gear.type[i]==Gear.CASH?0xd7b276:0xa9b6b5,x-3,y-2,7,3);
                fill(g,0xffdc9a,x-1,y-3,3,1);
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
            if (city.combat.flash>0) {
                g.setColor(0xfac07d);
                g.drawLine(city.combat.lineX-sx,city.combat.lineY-sy-10,city.combat.hitX-sx,city.combat.hitY-sy-10);
            }
            if (state==PLAY && city.combat.life==Combat.NORMAL) {
                if (city.trade.job>=0) drawTarget(g,city.trade.x()-sx,city.trade.y()-sy,w,h);
                else if (city.story.marker())
                    drawTarget(g, city.story.targetX(city) - sx, city.story.targetY(city) - sy, w, h);
            }
            if(city.police.airborne) {
                int x=city.police.airX/District.UNIT-sx,y=city.police.airY/District.UNIT-sy;
                if(x>-28 && x<w+28 && y>-28 && y<h+28) {
                    g.setColor(0x302b3a);
                    g.fillArc(x-12,y+10,27,10,0,360);
                    region(g,heli,city.police.airHeading*48,0,48,48,x-24,y-28);
                    g.setColor(0xc6b5aa);
                    int rotor=city.ticks%4;
                    if(rotor==0) g.drawLine(x-20,y-7,x+20,y-7);
                    else if(rotor==1) g.drawLine(x-14,y-19,x+14,y+5);
                    else if(rotor==2) g.drawLine(x,y-25,x,y+11);
                    else g.drawLine(x-15,y+5,x+15,y-19);
                }
            }
            if(state==PLAY && !city.driving && city.combat.life==Combat.NORMAL) {
                int pickup=city.gear.nearby(city);
                District.Actor door=pickup<0?city.nearbyCar():null;
                int room=door==null && pickup<0?city.rooms.door(city):-1;
                int service=room<0 && door==null && pickup<0?city.trade.place(city):0;
                String prompt=pickup>=0?city.gear.label(pickup):door!=null?"ENTER VEHICLE":
                    room>=0?Rooms.NAMES[room]:city.conversational()!=null?"TALK":service!=0?city.trade.name(service):null;
                if(prompt!=null) {
                    panel(g,w/2-72,h-48,144,18,0x25232b);
                    text(g,"5  "+prompt,w/2,h-47,true,0xffe0aa);
                }
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
                int x=city.heroX/District.UNIT-sx,y=city.heroY/District.UNIT-sy;
                int life=city.combat.life;
                if(life==Combat.WASTED) drawHeroPose(g,fall,Math.min(2,(75-city.combat.recovery)/5),city.heroDir,x,y);
                else if(city.combat.hurt>0) drawHeroPose(g,hit,Math.min(2,(9-city.combat.hurt)/3),city.heroDir,x,y);
                else if(city.combat.attackPose>0) {
                    int bank=Math.min(2,city.combat.attackWeapon);
                    int phase=Math.min(5,(city.combat.duration-city.combat.attackPose)*6/city.combat.duration);
                    Image image=action(bank);
                    if(image!=null) drawHeroPose(g,image,phase,city.combat.aim,x,y);
                } else if(city.walking) {
                    drawHeroPose(g,city.running?run:walk,city.stride/(city.running?4:6),city.heroDir,x,y);
                } else drawHeroPose(g,walk,4+city.ticks/21%2,city.heroDir,x,y);
            } else {
                District.Actor actor = city.street[id];
                int x = actor.x / District.UNIT - sx, y = actor.y / District.UNIT - sy;
                if (actor.vehicle) {
                    if(actor.model==10) {
                        region(g,tank,actor.dir*48,0,48,48,x-24,y-24);
                    } else if(actor.model>=6) {
                        region(g,motors,actor.dir*48,(actor.model-6)*48,48,48,x-24,y-24);
                    } else if (actor.model==5) {
                        region(g,ambulance,actor.dir*48,0,48,48,x-24,y-24);
                        if (city.ticks/5%2==0) fill(g,0xd9cedf,x-3,y-11,5,1);
                    } else if (actor.model == 0 || actor.model == 4) {
                        region(g, actor.model == 0 ? sedan : cruiser, actor.dir * 44, 0, 44, 44, x - 22,y - 22);
                        if (actor.model == 4 && actor.duty == Street.CHASE) {
                            fill(g, (city.ticks/5)%2 == 0 ? 0xe38b7e : 0x94b8d7, x-2, y-8, 4, 1);
                        }
                    } else {
                        region(g, traffic, actor.dir * 48, (actor.model - 1) * 48, 48, 48, x - 24, y - 24);
                    }
                } else {
                    if (actor.hp==0 && city.ticks-actor.fallen>=9) {
                        Image body=walker(actor.model);
                        int height=modernWalker(actor.model)?38:36;
                        int width=modernWalker(actor.model)?28:24;
                        if (body!=null) g.drawRegion(body,0,actor.dir*height,width,height,
                            actor.dir==District.LEFT?Sprite.TRANS_ROT270:Sprite.TRANS_ROT90,x-height/2,y-15,TOP_LEFT);
                    } else {
                        int phase=modernWalker(actor.model)?
                            (actor.hitPose>0?(actor.officer() && city.wanted<3?6:5):actor.moving?actor.stride/8:4):
                            actor.moving?walkFrame(actor.stride/8):actor.hitPose>0?1:0;
                        drawWalker(g,actor.model,actor.dir,phase,x,y);
                        if (actor.hit>5) fill(g,0xba7869,x-3,y-13,5,1);
                    }
                }
            }
        }

        private Image action(int bank) {
            if(actionBank==bank) return actionImage;
            try {
                String name=bank==0?"/fist05.png":bank==1?"/bat05.png":"/shoot05.png";
                actionImage=Image.createImage(name);
                actionBank=bank;
                return actionImage;
            } catch(IOException missing) { artFailure=true; return null; }
        }

        private void drawHeroPose(Graphics g,Image image,int phase,int dir,int x,int y) {
            region(g,image,phase*40,dir*42,40,42,x-20,y-36);
        }

        private int walkFrame(int phase) {
            // The authored passing pose B connects both contact poses.
            return (phase+1)%4;
        }

        private boolean modernWalker(int model) {
            return model==9 || model==19 || model>=23 && model<=28;
        }

        private Image walker(int model) {
            touched[model] = city.ticks;
            if (walkers[model] == null) {
                try {
                    walkers[model] = Image.createImage((modernWalker(model)?"/person05-":"/pedestrian")+model+".png");
                } catch (IOException missing) {
                    artFailure = true;
                }
            }
            return walkers[model];
        }

        private void drawWalker(Graphics g, int model, int direction, int frame, int x, int y) {
            Image image=walker(model);
            if(image==null) return;
            if(modernWalker(model)) region(g,image,frame*28,direction*38,28,38,x-14,y-35);
            else region(g,image,frame*24,direction*36,24,36,x-12,y-31);
        }

        private void drawTarget(Graphics g, int x, int y, int w, int h) {
            int margin = 12, top = w >= 236 ? 70 : 82, bottom = h - 35;
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
            text(g, SECTORS[city.neighborhood()], 17, 10, false, 0xffe0aa);
            panel(g, 8, h - 24, 49, 17, 0x25232b);
            text(g, "MENU", 32, h - 24, true, 0xe8d3b8);
            if (city.trade.job>=0 || !city.story.finished() || city.story.delay>0) {
                String title = city.trade.job>=0 ? city.trade.jobTitle() : city.story.title();
                String task = city.trade.job>=0 ? city.trade.task(city) : city.story.objective(city);
                int boardWidth = Math.min(123, w - 16);
                int bx = w - boardWidth - 8;
                int by = w >= 236 ? 8 : 31;
                panel(g, bx, by, boardWidth, 36, 0x25232b);
                text(g, title, bx + boardWidth / 2, by + 3, true, 0xffe0aa);
                text(g, task, bx + boardWidth / 2, by + 18, true, 0xcdb3a3);
            }
            panel(g,8,31,99,29,0x25232b);
            if (walletValue!=city.trade.money) {
                walletValue=city.trade.money;
                walletLabel="$"+walletValue;
            }
            text(g,walletLabel,86,33,true,0xe8c899);
            fill(g,0x5c3842,13,39,47,4);
            fill(g,0xc7776f,13,39,city.combat.hp*47/100,4);
            int gun=city.combat.weapon;
            int key=city.driving ? 65536+city.car.hp : gun*8192+
                    city.combat.magazine[gun]*256+city.combat.reserve[gun]+(city.combat.reload>0?32768:0);
            if (key!=toolKey) {
                toolKey=key;
                toolLabel=city.driving?"CAR  "+city.car.hp+"%":Combat.LABELS[gun];
                if (!city.driving && gun>=2)
                    toolLabel+=city.combat.reload>0?"  LOAD":"  "+city.combat.magazine[gun]+"/"+city.combat.reserve[gun];
            }
            text(g,toolLabel,14,45,false,0xffe1ae);
            if (city.wanted > 0) {
                panel(g, 8, 63, 91, 17, 0x342831);
                for (int i = 0; i < 5; i++) drawStar(g, 19+16*i,70,i < city.wanted);
            }
            if (city.trade.note>0 && city.combat.life==Combat.NORMAL) {
                panel(g,w/2-75,h-76,150,17,0x25232b);
                text(g,city.trade.notice,w/2,h-76,true,0xffe0aa);
            }
            if (city.combat.life!=Combat.NORMAL) {
                panel(g,w/2-85,h/2-23,170,46,0x302631);
                g.setFont(headline);
                text(g,city.combat.life==Combat.WASTED?"WASTED":"BUSTED",w/2,h/2-14,true,0xdd8076);
                g.setFont(label);
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

        private void drawStore(Graphics g,int w,int h) {
            int mid=w/2,y=h/2-86;
            panel(g,mid-95,y,190,173,0x25232b);
            text(g,city.trade.name(store),mid,y+10,true,0xffe0aa);
            int choices=city.trade.options(store);
            int first=Math.max(0,Math.min(selection-2,choices-6));
            for (int i=first;i<Math.min(choices,first+6);i++) {
                int line=y+31+(i-first)*19;
                if (selection==i) fill(g,0x594042,mid-85,line,170,17);
                text(g,city.trade.row(city,store,i),mid,line,true,0xe8d3b8);
            }
            int model=Trade.modelAtRow(selection);
            if(store==Trade.GARAGE && model>=0) {
                if(model==0) region(g,sedan,District.RIGHT*44,0,44,44,mid+46,y+3);
                else if(model>=6) region(g,motors,District.RIGHT*48,(model-6)*48,48,48,mid+42,y+1);
                else region(g,traffic,District.RIGHT*48,(model-1)*48,48,48,mid+42,y+1);
            }
            text(g,city.trade.note>0?city.trade.notice:"BALANCE  $"+city.trade.money,mid,y+150,true,0xebc38f);
        }

        private void drawRoom(Graphics g,int w,int h) {
            Rooms room=city.rooms;
            if(roomBank!=room.id) {
                try {
                    roomImage=Image.createImage("/room05-"+room.id+".png");
                    roomBank=room.id;
                } catch(IOException missing) { artFailure=true; return; }
            }
            int x=(w-192)/2,y=(h-160)/2;
            g.drawImage(roomImage,x,y,TOP_LEFT);
            if(room.id<4) drawWalker(g,room.id==1?9:room.id==2?27:12,District.DOWN,
                                     room.id==1||room.id==2?4:0,x+(room.id==0?163:96),y+40);
            drawHeroPose(g,room.phase==0?walk:sprint.active()?run:walk,
                         room.phase==0?4:room.phase/5%4,room.dir,x+room.x,y+room.y);
            panel(g,10,9,w-20,24,0x25232b);
            text(g,Rooms.NAMES[room.id],w/2,12,true,0xffe0aa);
            String prompt=room.atExit()?"5  LEAVE":room.atCounter()?(room.id==4?"5  REST AND SAVE":"5  INTERACT"):null;
            if(prompt!=null) text(g,prompt,w/2,h-42,true,0xffe0aa);
            if(city.trade.note>0) text(g,city.trade.notice,w/2,h-23,true,0xe8c899);
        }

        private void drawInventory(Graphics g,int w,int h) {
            int x=w/2,y=h/2-64;
            panel(g,x-93,y,186,128,0x302530);
            text(g,"INVENTORY",x,y+10,true,0xffe0aa);
            text(g,"MEDKITS  "+city.gear.medkits,x,y+37,true,0xe8c899);
            text(g,"KEYS  "+keyCount(city.trade.cars)+"   HP  "+city.combat.hp,x,y+58,true,0xe8c899);
            text(g,Combat.LABELS[city.combat.weapon]+(city.gear.token!=0?"   PARCEL":""),x,y+78,true,0xe8c899);
            text(g,"5  USE MEDKIT       0  BACK",x,y+102,true,0xffe0aa);
        }

        private int keyCount(int bits) {
            int n=0;
            while(bits!=0) { bits&=bits-1; n++; }
            return n;
        }

        private void drawMap(Graphics g,int w) {
            int left=w-92,top=54;
            panel(g,left-3,top-3,87,66,0x25232b);
            int cx=city.focusX()/District.UNIT/16,cy=city.focusY()/District.UNIT/16;
            for(int dy=-7;dy<=7;dy++) for(int dx=-10;dx<=9;dx++) {
                int col=cx+dx,row=cy+dy;
                if(col<0 || row<0 || col>=city.columns || row>=city.rows) continue;
                int zone=city.zoneAt(col,row);
                if(zone==District.ROAD || zone==District.WATER)
                    fill(g,zone==District.ROAD?0x876e71:0x456e79,left+(dx+10)*4,top+(dy+7)*4,4,4);
            }
            mapPin(g,left,top,cx,cy,Street.HOSPITAL_X,Street.HOSPITAL_Y,0xdd7b75);
            mapPin(g,left,top,cx,cy,Trade.GARAGE_X,Trade.GARAGE_Y,
                (city.trade.businesses&2)!=0?0xb8d295:0x82a299);
            if((city.trade.businesses&1)!=0) mapPin(g,left,top,cx,cy,Rooms.X[0],Rooms.Y[0],0xb8d295);
            mapPin(g,left,top,cx,cy,Rooms.X[4],Rooms.Y[4],0xc59caf);
            if(city.trade.job>=0) mapPin(g,left,top,cx,cy,city.trade.x(),city.trade.y(),0xf0b870);
            else if(city.story.marker())
                mapPin(g,left,top,cx,cy,city.story.targetX(city),city.story.targetY(city),0xf0b870);
            int dir=city.driving?city.car.dir:city.heroDir;
            arrow(g,left+42+District.DX[dir]*4,top+30+District.DY[dir]*4,
                        District.DX[dir],District.DY[dir],6,3,0xffe0aa);
        }

        private void mapPin(Graphics g,int left,int top,int cx,int cy,int x,int y,int color) {
            int dx=Math.max(-10,Math.min(9,x/16-cx));
            int dy=Math.max(-7,Math.min(7,y/16-cy));
            fill(g,color,left+(dx+10)*4,top+(dy+7)*4,3,3);
        }

        private void drawMenu(Graphics g, int w, int h) {
            int center = w / 2, y = h / 2 - 65;
            panel(g, center - 105, y, 210, menuItems() == 3 ? 155 : 130, 0x25232b);
            fill(g, 0xe2a76b, center - 105, y, 210, 3);
            g.setFont(headline);
            text(g, state == TITLE ? "CRIME DISTRICT" : "PAUSED", center, y + 12, true, 0xffe0aa);
            g.setFont(label);
            text(g, saveError ? "SAVE UNAVAILABLE" : state == TITLE ? "CITY  /  ALPHA 0.5.0" : SECTORS[city.neighborhood()],
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
