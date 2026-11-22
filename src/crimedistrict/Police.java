package crimedistrict;

/** Dispatch authority. Reserve units are reused across incidents. */
final class Police {
    int lastX,lastY, event, lastEscalation, airX,airY,airHeading,airAge, deployments;
    boolean airborne;

    void reset() {
        lastX=lastY=event=airX=airY=airHeading=airAge=deployments=0;
        lastEscalation=-1000;
        airborne=false;
    }

    void report(District d,int level) {
        int previous=d.wanted;
        int next=Math.max(previous,level);
        if(previous>0 && d.ticks-lastEscalation>=95) next=Math.max(next,previous+1);
        d.wanted=Math.min(5,next);
        if(d.wanted!=previous) lastEscalation=d.ticks;
        d.unseen=0;
        event=180;
        sight(d);
    }

    void sight(District d) { lastX=d.focusX(); lastY=d.focusY(); }

    void tick(District d) {
        if(event>0) event--;
        if(d.wanted>0 && lastX==0 && lastY==0) sight(d);
        tickAir(d);
        int deployed=0;
        District.Actor reserve=null,tank=null;
        for(int i=0;i<d.street.length;i++) {
            District.Actor a=d.street[i];
            if(a.role==Street.ARMOR) tank=a;
            if(a.role!=Street.RESPONSE) continue;
            if(a.deferred) {
                if(reserve==null) reserve=a;
                continue;
            }
            deployed++;
            if(d.wanted==0 && a.transport!=null && a.hp>0 && distance(a.x,a.y,a.transport.x,a.transport.y)<25) {
                a.transport.empty=false;
                a.transport.pullOver=false;
                a.reset();
                deployed--;
            } else if(d.wanted==0 && !d.onCamera(a.x,a.y)) {
                if(a.transport!=null) { a.transport.empty=false; a.transport.pullOver=false; }
                a.reset();
                deployed--;
            }
        }
        if(tank!=null) tickTank(d,tank);
        int max=d.wanted==1?1:d.wanted==2?2:d.wanted>=3?3:0;
        if(deployed<max && reserve!=null && !d.driving && (event>0 || d.ticks%15==0)) {
            District.Actor patrol=null;
            int best=165;
            for(int i=0;i<d.street.length;i++) {
                District.Actor a=d.street[i];
                if(!a.vehicle || a.role!=Street.POLICE || a.deferred || a.empty || a.hp<=0) continue;
                int dist=distance(a.x,a.y,lastX,lastY);
                if(dist<best && (a.pullOver || d.canSee(a.x,a.y,lastX,lastY))) { patrol=a; best=dist; }
            }
            if(patrol!=null) {
                patrol.pullOver=true;
                if(patrol.speed==0 && alight(d,reserve,patrol)) deployments++;
            }
        }
        if(d.wanted==0) {
            for(int i=0;i<d.street.length;i++) {
                District.Actor a=d.street[i];
                if(a.vehicle && a.pullOver && !a.empty) a.pullOver=false;
            }
        }
    }

    private boolean alight(District d,District.Actor officer,District.Actor car) {
        int first=car.dir<District.LEFT?District.LEFT:District.UP;
        for(int dir=first;dir<first+2;dir++) {
            int dx=District.DX[dir]*(District.halfWidth(car.dir,car.model)+7)*District.UNIT;
            int dy=District.DY[dir]*(District.halfHeight(car.dir,car.model)+6)*District.UNIT;
            int x=car.x+dx,y=car.y+dy;
            if(!d.pedestrianSpace(officer,x,y)) continue;
            officer.reset();
            officer.deferred=false;
            officer.x=x;
            officer.y=y;
            officer.dir=dir;
            officer.transport=car;
            officer.duty=Street.CHASE;
            car.empty=true;
            return true;
        }
        return false;
    }

    private void tickAir(District d) {
        if(d.wanted<4) {
            if(airborne) {
                airX-=3*District.UNIT;
                airY-=2*District.UNIT;
                if(!d.onCamera(airX,airY)) { airborne=false; airAge=0; }
            }
            return;
        }
        if(!airborne) {
            airX=d.focusX()-192*District.UNIT;
            airY=d.focusY()-220*District.UNIT;
            airHeading=District.DOWN;
            airborne=true;
            airAge=0;
        }
        airAge++;
        int dx=lastX-airX,dy=lastY-airY;
        if(Math.abs(dx)>8*District.UNIT || Math.abs(dy)>8*District.UNIT) {
            airHeading=Math.abs(dx)>Math.abs(dy)?(dx<0?District.LEFT:District.RIGHT):(dy<0?District.UP:District.DOWN);
            airX+=clamp(dx,630);
            airY+=clamp(dy,630);
        }
    }

    boolean airSight(District d) {
        if(!airborne || d.wanted<4 || airAge>430 || !d.driving) return false;
        if(d.zoneAt(d.focusX()/District.UNIT/16,d.focusY()/District.UNIT/16)!=District.ROAD) return false;
        return distance(airX,airY,d.focusX(),d.focusY())<130;
    }

    private void tickTank(District d,District.Actor tank) {
        if(!tank.deferred) {
            if(d.wanted<5 && !d.onCamera(tank.x,tank.y)) tank.reset();
            return;
        }
        if(d.wanted<5 || d.ticks%32!=0) return;
        int ox=d.focusX()/District.UNIT/768*768,oy=d.focusY()/District.UNIT/704*704;
        int[] route=tank.route;
        route[0]=ox+199; route[1]=oy+194; route[2]=ox+561; route[3]=oy+194;
        route[4]=ox+561; route[5]=oy+490; route[6]=ox+199; route[7]=oy+490;
        for(int n=0;n<4;n++) {
            int x=route[n*2]*District.UNIT,y=route[n*2+1]*District.UNIT;
            int dir=n==0?District.RIGHT:n==1?District.DOWN:n==2?District.LEFT:District.UP;
            if(d.onCamera(x,y) || !d.vehicleSpace(tank,x,y,dir) ||
                d.zoneAt(route[n*2]/16,route[n*2+1]/16)!=District.ROAD) continue;
            tank.x=x; tank.y=y; tank.dir=dir;
            tank.waypoint=(n*2+2)%8;
            tank.deferred=false;
            return;
        }
    }

    private static int clamp(int n,int limit) { return Math.max(-limit,Math.min(limit,n)); }
    private static int distance(int ax,int ay,int bx,int by) {
        return (Math.abs(ax-bx)+Math.abs(ay-by))/District.UNIT;
    }
}
