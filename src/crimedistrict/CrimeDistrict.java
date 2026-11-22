package crimedistrict;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.midlet.MIDlet;

public final class CrimeDistrict extends MIDlet {
    protected void startApp() {
        Display.getDisplay(this).setCurrent(new GameCanvas(false) {
            public void paint(Graphics g) {
                g.setColor(0x171b24);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(0xe9c799);
                g.drawString("CRIME DISTRICT", getWidth() / 2, getHeight() / 2,
                             Graphics.TOP | Graphics.HCENTER);
            }
        });
    }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
