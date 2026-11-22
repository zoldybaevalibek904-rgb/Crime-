package crimedistrict;

import java.util.Vector;

/** Fixed population and authored lane graphs. Actors keep their world positions off-screen. */
final class Street {
    static final int CIVILIAN = 0, PARKED = 1, POLICE = 2, RED = 3, IRON = 4, CONTACT = 5, CARGO = 6;
    static final int IDLE = 0, PATROL = 1, ALERT = 2, CHASE = 3, RETURN = 4;
    static final int RED_X = 1120, RED_Y = 443, IRON_X = 641, IRON_Y = 1147;
    static final int GUARD_X = 1224, GUARD_Y = 1314;
    static final int CARGO_X = 291, CARGO_Y = 985;

    static District.Actor[] populate() {
        Vector actors = new Vector();
        car(actors, 0, PARKED, 267,229);
        walk(actors,1,CIVILIAN,323,173,2,new int[]{306,173,452,173,452,158,444,158,444,173});
        walk(actors,2,CIVILIAN,347,259,2,new int[]{306,259,480,259,480,274,306,274});
        walk(actors,3,CIVILIAN,451,460,6,loop(315,451,482,460));
        walk(actors,4,CIVILIAN,122,170,6,loop(40,155,126,170));
        walk(actors,5,CIVILIAN,640,159,2,loop(617,159,689,173));
        walk(actors,6,CIVILIAN,684,459,6,loop(620,447,711,459));

        int[] downtown = lanes(0,0);
        add(actors,1,true,CIVILIAN,336,194,District.RIGHT,2,downtown);
        add(actors,2,true,CIVILIAN,561,320,District.DOWN,4,downtown);
        add(actors,3,true,CIVILIAN,404,490,District.LEFT,6,downtown);
        add(actors,4,true,POLICE,199,338,District.UP,0,downtown);
        add(actors,0,true,CIVILIAN,685,239,District.RIGHT,2,new int[]{628,239,1200,239,1200,212,628,212});
        car(actors,1,PARKED,1114,281);
        car(actors,2,PARKED,460,296);
        car(actors,3,CARGO,CARGO_X,CARGO_Y);
        car(actors,0,PARKED,1105,1146);

        int[] residential = lanes(768,0), industrial = lanes(0,704), coast = lanes(768,704);
        add(actors,1,true,CIVILIAN,1024,194,District.RIGHT,2,residential);
        add(actors,0,true,CIVILIAN,1329,396,District.DOWN,4,residential);
        add(actors,3,true,CIVILIAN,415,898,District.RIGHT,2,industrial);
        add(actors,4,true,POLICE,561,1014,District.DOWN,4,industrial);
        add(actors,2,true,CIVILIAN,1140,1194,District.LEFT,6,coast);
        add(actors,1,true,CIVILIAN,1056,898,District.RIGHT,2,coast);

        walk(actors,7,CONTACT,1100,302,0,new int[]{1100,302});
        walk(actors,8,CIVILIAN,872,162,2,loop(795,153,908,167));
        walk(actors,9,CIVILIAN,351,1127,2,loop(348,1127,413,1131));
        walk(actors,10,CIVILIAN,888,442,2,loop(866,421,920,450));
        walk(actors,11,CIVILIAN,1164,444,2,loop(1156,439,1215,450));
        walk(actors,12,CIVILIAN,656,1127,2,loop(653,1122,725,1138));

        walk(actors,13,CIVILIAN,833,1316,2,loop(807,1316,901,1322));
        walk(actors,14,CIVILIAN,984,1307,2,loop(972,1304,1047,1322));
        walk(actors,15,CIVILIAN,1090,1319,2,loop(1077,1318,1141,1322));
        walk(actors,16,CONTACT,GUARD_X,GUARD_Y,0,new int[]{GUARD_X,GUARD_Y});
        walk(actors,17,CIVILIAN,1255,1257,0,new int[]{1255,1257});
        walk(actors,18,CIVILIAN,1394,1314,2,loop(1376,1314,1473,1322));

        walk(actors,19,RED,1097,443,2,loop(1094,438,1136,450));
        walk(actors,20,RED,1135,426,0,new int[]{1135,426});
        walk(actors,21,RED,1080,429,0,new int[]{1080,429});
        walk(actors,22,IRON,645,1145,2,loop(638,1145,680,1154));
        walk(actors,23,IRON,686,1149,0,new int[]{686,1149});
        walk(actors,24,IRON,635,1127,0,new int[]{635,1127});
        walk(actors,25,POLICE,454,258,2,loop(446,257,480,274));
        walk(actors,25,POLICE,296,1219,2,loop(280,1217,453,1230));
        walk(actors,25,POLICE,1243,265,2,loop(1225,265,1260,278));
        add(actors,4,true,POLICE,1329,1032,District.DOWN,4,coast);

        District.Actor[] result = new District.Actor[actors.size()];
        actors.copyInto(result);
        return result;
    }

    private static int[] loop(int x, int y, int x2, int y2) {
        return new int[]{x,y,x2,y,x2,y2,x,y2};
    }

    private static int[] lanes(int x, int y) {
        return loop(x+199,y+194,x+561,y+490);
    }

    private static void walk(Vector actors,int model,int role,int x,int y,int waypoint,int[] route) {
        add(actors,model,false,role,x,y,waypoint == 6 ? District.LEFT : District.RIGHT,waypoint,route);
    }

    private static void car(Vector actors,int model,int role,int x,int y) {
        add(actors,model,true,role,x,y,District.RIGHT,0,new int[0]);
    }

    private static void add(Vector actors,int model,boolean vehicle,int role,int x,int y,int dir,int waypoint,int[] route) {
        actors.addElement(new District.Actor(model,vehicle,role,x,y,dir,waypoint,route));
    }
}
