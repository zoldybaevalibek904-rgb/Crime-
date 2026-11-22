package crimedistrict;

/** Fixed-tick combat in game coordinates. No simulated projectiles are allocated. */
final class Combat {
    static final String[] LABELS = {"FISTS","BAT","PISTOL","SMG"};
    static final int[] CAPACITY = {0,0,8,18};
    private static final int[] REACH = {15,24,105,81};
    private static final int[] DAMAGE = {22,36,37,23};
    private static final int[] RATE = {15,20,12,5};
    private static final int[] RELOAD = {0,0,34,43};
    static final int NORMAL = 0, WASTED = 1, BUSTED = 2;

    int hp, weapon, owned, cooldown, reload, attackPose, hurt, life, recovery, safety;
    int lineX, lineY, hitX, hitY, flash;
    final int[] magazine = new int[4], reserve = new int[4];

    void reset() {
        hp = 100;
        weapon = 0;
        owned = 1;
        for (int i=0;i<4;i++) magazine[i]=reserve[i]=0;
        cooldown = reload = attackPose = hurt = life = recovery = safety = flash = 0;
    }

    void tick(District d, int controls) {
        if (cooldown>0) cooldown--;
        if (attackPose>0) attackPose--;
        if (hurt>0) hurt--;
        if (safety>0) safety--;
        if (flash>0) flash--;
        if (life != NORMAL) {
            if (--recovery <= 0) {
                d.respawn();
                hp = 100;
                life = hurt = attackPose = cooldown = reload = 0;
                safety = 180;
            }
            return;
        }
        if ((controls & 2) != 0 && !d.driving) {
            do weapon=(weapon+1)%4; while ((owned & (1<<weapon)) == 0);
            reload=0;
        }
        if (reload>0 && --reload==0) {
            int n = Math.min(reserve[weapon],CAPACITY[weapon]-magazine[weapon]);
            magazine[weapon]+=n;
            reserve[weapon]-=n;
        }
        if (d.driving) return;
        if ((controls&4)!=0) reload();
        if ((controls&1)!=0 && cooldown==0 && reload==0) attack(d);
    }

    private void reload() {
        if (weapon>=2 && reload==0 && reserve[weapon]>0 && magazine[weapon]<CAPACITY[weapon])
            reload=RELOAD[weapon];
    }

    private void attack(District d) {
        boolean gun=weapon>=2;
        if (gun && magazine[weapon]==0) {
            reload();
            return;
        }
        if (gun) magazine[weapon]--;
        cooldown=RATE[weapon];
        attackPose=gun?5:8;
        int x=d.heroX/District.UNIT,y=d.heroY/District.UNIT;
        int dx=District.DX[d.heroDir],dy=District.DY[d.heroDir];
        District.Actor victim=null;
        int lastX=x,lastY=y;
        for (int distance=3;distance<=REACH[weapon];distance+=3) {
            int xx=x+dx*distance,yy=y+dy*distance;
            if (!d.freeStatic(xx,yy,1,1) || !d.canHit(lastX*District.UNIT,lastY*District.UNIT,xx*District.UNIT,yy*District.UNIT)) break;
            lastX=xx;
            lastY=yy;
            victim=d.targetAt(xx,yy,gun?4:7,x,y,dx,dy);
            if (victim!=null) {
                if (!d.canHit(d.heroX,d.heroY,victim.x,victim.y)) victim=null;
                else break;
            }
        }
        if (gun) {
            lineX=x; lineY=y; hitX=lastX; hitY=lastY;
            flash=3;
            d.startle(x,y,145);
        }
        int offence=gun?1:0;
        boolean direct=false;
        if (victim!=null) {
            victim.hp=Math.max(0,victim.hp-DAMAGE[weapon]);
            victim.hit=10;
            victim.threat=240;
            direct=victim.role==Street.POLICE;
            offence=Math.max(offence,direct?(victim.hp==0?3:2):(victim.hp==0?2:1));
            d.startle(x,y,gun?145:68);
            if (victim.hp==0) {
                victim.moving=false;
                victim.fallen=d.ticks;
                d.ems.request(d,victim);
                d.story.defeated(victim.role);
            }
        }
        if (offence>0) d.reportCrime(offence,direct);
    }

    void enemyAttack(District d,District.Actor attacker,int distance) {
        if (hp<=0 || life!=NORMAL || safety>0 || attacker.strike>0 ||
                !d.canSee(attacker.x,attacker.y,d.focusX(),d.focusY())) return;
        boolean gun=attacker.model==24 && attacker.threat>0;
        int reach=gun?70:attacker.model==23?23:17;
        if (distance>reach || d.driving) return;
        attacker.strike=gun?36:24;
        attacker.hitPose=8;
        if (gun) {
            lineX=attacker.x/District.UNIT; lineY=attacker.y/District.UNIT;
            hitX=d.heroX/District.UNIT; hitY=d.heroY/District.UNIT;
            flash=3;
            d.startle(lineX,lineY,90);
        }
        damage(d,gun?15:attacker.model==23?13:8);
    }

    void damage(District d,int amount) {
        if (life!=NORMAL || safety>0) return;
        hp=Math.max(0,hp-amount);
        hurt=9;
        if (hp==0) end(d,WASTED);
    }

    void end(District d,int outcome) {
        if (life!=NORMAL) return;
        life=outcome;
        recovery=75;
        d.story.caught();
        d.lifeRevision++;
        d.driving=false;
        d.walking=false;
        d.wanted=0;
        reload=0;
        attackPose=0;
        d.trade.fine(outcome==WASTED?90:60);
    }
}
