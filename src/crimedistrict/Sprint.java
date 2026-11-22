package crimedistrict;

/** Direction edges only. Key-repeat never generates a tap. */
final class Sprint {
    private int direction;
    private long when;
    private boolean sprint;

    void press(int bit,int held,long now) {
        if ((held&bit)!=0) return;
        if (direction==bit && now>=when && now-when<=275) sprint=true;
        direction=bit;
        when=now;
    }

    void release(int directionsStillHeld) {
        if (directionsStillHeld==0) sprint=false;
    }

    boolean active() { return sprint; }
    void clear() { direction=0; sprint=false; }
}
