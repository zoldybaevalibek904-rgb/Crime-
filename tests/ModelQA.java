package crimedistrict;

public class ModelQA {
    static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    static void frame(District d, int bits) {
        d.tick((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);
        d.follow(240, 320);
    }

    static void steps(District d, int bits, int count) {
        for (int i=0;i<count;i++) frame(d,bits);
    }

    static void body(District d, int x, int y, int w, int h) {
        check(x>=w && y>=h && x+w<=d.width && y+h<=d.height, "map perimeter");
        for (District.Prop p : d.props)
            check(p.width==0 || x+w<=p.left || x-w>=p.left+p.width ||
                  y+h<=p.top || y-h>=p.top+p.height, "no static overlap");
    }

    public static void main(String[] args) throws Exception {
        District d = new District(ModelQA.class.getResourceAsStream("/downtown.bin"));
        int[][] turnMasks = new int[6][4];
        for(int t=0;t<14400;t++) {
            frame(d, 0);
            for(int i=0;i<d.street.length;i++) {
                District.Actor actor=d.street[i];
                if(actor.moving) turnMasks[i][actor.dir]++;
                int w=actor.vehicle?District.halfWidth(actor.dir,actor.model):4;
                int h=actor.vehicle?District.halfHeight(actor.dir,actor.model):3;
                body(d,actor.x/256,actor.y/256,w,h);
                for (int j=0;j<i;j++) {
                    District.Actor other=d.street[j];
                    int ow=other.vehicle?District.halfWidth(other.dir,other.model):4;
                    int oh=other.vehicle?District.halfHeight(other.dir,other.model):3;
                    check(Math.abs(actor.x/256-other.x/256)>=w+ow ||
                          Math.abs(actor.y/256-other.y/256)>=h+oh, "no actor overlap");
                }
            }
        }
        for(int i=0;i<turnMasks.length;i++) {
            System.out.println("street actor " + i + " movement " + java.util.Arrays.toString(turnMasks[i]));
            int directions = 0;
            for(int count:turnMasks[i]) if(count>50) directions++;
            check(directions == 4, "all patrol directions remain live");
        }

        for (int dir=0;dir<4;dir++) {
            d.reset();
            frame(d,16);
            check(d.driving,"entry");
            int x=d.carX, y=d.carY;
            steps(d,1<<dir,10);
            check(d.carDir==dir && (d.carX-x)*District.DX[dir]+(d.carY-y)*District.DY[dir]>0,"cardinal driving");
            steps(d,0,16);
            x=d.carX; y=d.carY;
            steps(d,0,3);
            check(d.carX==x && d.carY==y,"release stops");
            frame(d,16);
            check(!d.driving,"exit");
            body(d,d.heroX/256,d.heroY/256,4,3);
        }

        d.reset();
        frame(d,16);
        d.carX=210*256; d.carY=220*256;
        int[] changes={8,9,10,6,5,1,2,4};
        for(int mask:changes) for(int i=0;i<20;i++) {
            int x=d.carX,y=d.carY;
            frame(d,mask);
            int dx=d.carX-x,dy=d.carY-y;
            check(dx*dx+dy*dy<=District.CAR_SPEED*District.CAR_SPEED, "normalized diagonal and turns");
        }
        d.reset();
        d.heroX = 236 * District.UNIT;
        d.heroY = 229 * District.UNIT;
        steps(d, 8, 75);
        check(d.heroX / District.UNIT == 245, "sedan blocks pedestrian");

        d.reset();
        frame(d, 16);
        d.carX = 316 * District.UNIT;
        steps(d, 1, 95);
        check(d.carY / District.UNIT == 180, "Market blocks sedan at full speed");

        d.reset();
        // Complete both legs via the ordinary movement simulation.
        steps(d,2,27);
        steps(d,8,34);
        check(d.mission==District.TO_DELIVERY,"walk to pickup");
        frame(d,16);
        check(d.driving,"pickup-to-car is reachable");
        // Drive east on the lower lane, then south along the eastern boulevard.
        for (int i=0;i<160 && d.carX/256<532;i++) frame(d,8);
        check(d.carX/256>=532,"east boulevard reachable");
        for (int i=0;i<180 && d.carY/256<519;i++) frame(d,2);
        check(d.carY/256>=519,"south junction reachable");
        for(int i=0;i<120 && d.mission!=District.COMPLETE;i++) frame(d,4);
        check(d.mission==District.COMPLETE,"delivery complete in vehicle");
        steps(d,0,120);
        check(d.celebration==0 && d.driving,"free roam after delivery");
        System.out.println("PASS simulation, four-way driving, diagonals, patrols, collision and full mission");
    }
}
