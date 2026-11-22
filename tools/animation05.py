"""Native pixel keyframes. Feet share a single scene anchor across every action."""
from PIL import Image, ImageDraw
from actor_art import ROOT, save_indexed

W,H=40,42
INK="#211923"
JACKET=("#302936","#514457","#766270")
DENIM=("#29384a","#455975","#697b86")
FLESH=("#ad715a","#e4a578","#f4bc8c")

# Contact, compression, opposite contact, and passing positions, measured in pixels.
WALK=(((-5,0),(5,-5)),((-2,-2),(3,0)),((5,-5),(-5,0)),((3,0),(-2,-2)))
RUN=(((-9,0),(7,-8)),((-3,-3),(4,-2)),((7,-8),(-9,0)),((4,-2),(-3,-3)))
# shoulder yaw, body translation, lead-foot placement, wrist reach.
STRIKE=((0,-1,0,0),(1,-3,-2,-3),(2,-1,-2,0),(3,3,5,14),(2,2,4,10),(1,0,1,3))
SWING=((1,0,0,1),(2,-3,-2,-8),(3,-2,-3,-5),(4,3,7,14),(3,3,6,10),(1,0,1,3))
FIRE=((0,0,-1,8),(1,0,0,11),(2,-2,0,9),(1,-1,-1,10),(0,0,0,9),(0,0,0,6))


def limb(d, points, thick, ramp):
    d.line(points,fill=INK,width=thick+2,joint="curve")
    for x,y in points:
        r=(thick+1)//2
        d.ellipse((x-r,y-r,x+r,y+r),fill=INK)
    d.line(points,fill=ramp[0],width=thick,joint="curve")
    left=[(x-1,y-1) for x,y in points]
    d.line(left,fill=ramp[1],width=max(1,thick-2),joint="curve")
    x,y=points[0]
    d.point((x-1,y-1),fill=ramp[2])


def head(source,direction):
    image=source.crop((6,direction*36,19,direction*36+12))
    original=image.copy()
    d=ImageDraw.Draw(image)
    for x,y,delta in ((4,2,17),(7,2,15),(3,3,10),(8,4,16),(5,10,8)):
        r,g,b,a=original.getpixel((x,y))
        if a==255:
            d.point((x,y),fill=(min(255,r+delta),min(255,g+delta),min(255,b+delta),255))
    return image


def pose(direction,phase,kind,face):
    im=Image.new("RGBA",(W,H))
    d=ImageDraw.Draw(im)
    side=direction>=2
    flip=-1 if direction==2 else 1
    back=direction==0
    bottom=35
    center=20
    bob=0
    reach=0
    yaw=0
    if kind in ("walk","run"):
        (la,lb)=(RUN if kind=="run" else WALK)[phase]
        bob=(0,1,0,-1)[phase] if kind=="walk" else (-1,1,-1,-2)[phase]
        shift=flip*(2 if kind=="run" and side else 0)
        foot=[(center-3+flip*la[0],bottom+la[1]//2),(center+3+flip*lb[0],bottom+lb[1]//2)] if side else [
            (center-4+la[0]//5,bottom+(la[1] if not back else lb[1])//2),
            (center+4+lb[0]//5,bottom+(lb[1] if not back else la[1])//2)]
    elif kind in ("fist","bat","shoot"):
        yaw,move,step,reach=({"fist":STRIKE,"bat":SWING,"shoot":FIRE}[kind])[phase]
        shift=flip*move if side else move//2
        bob=-(phase==3)-(phase==4)
        foot=[(center-4-step//3,bottom-5),(center+4+step,bottom-1)] if not side else [
              (center-4+flip*(-2-step//2),bottom-3),(center+4+flip*step,bottom)]
    elif kind=="hurt":
        bob=phase%2+1
        shift=-(3-phase)*flip if side else (phase-2)
        foot=[(center-5,bottom-2),(center+6,bottom)]
    else:
        shift=0
        bob=phase%2
        foot=[(center-4,bottom-2),(center+4,bottom)]
    cx=center+shift
    shoulder=15+bob
    hip=25+bob
    d.ellipse((7,32,33,40),fill=(29,25,35,105))
    for i in (0,1):
        fx,fy=foot[i]
        px=cx+(-3 if i==0 else 3)
        knee=((px+fx)//2+(flip if side else 0),(hip+fy)//2-1)
        limb(d,[(px,hip-1),knee,(fx,fy-2)],5,DENIM if i else ("#25303e","#384d61","#526678"))
        toe=flip if side else 0
        d.polygon([(fx-3,fy-3),(fx+2,fy-3),(fx+4+toe,fy-1),(fx+3+toe,fy+1),(fx-3,fy+1),(fx-4,fy)],fill=INK)
        d.line([(fx-2,fy-1),(fx+3+toe,fy-1)],fill="#605259")
        d.line([(fx-2,fy),(fx+2+toe,fy)],fill="#302731")
    span=4 if side else 7
    if kind in ("walk","run"):
        swing=(4,-1,-4,2)[phase]*(2 if kind=="run" else 1)
        hands=[(cx-span+flip*swing,shoulder+9-(kind=="run")*2),
               (cx+span-flip*swing,shoulder+10-(kind=="run")*2)] if side else [
               (cx-span-1,shoulder+8+swing//2),(cx+span+1,shoulder+8-swing//2)]
    elif kind in ("fist","bat","shoot"):
        if side:
            hands=[(cx-4-flip*2,shoulder+6),(min(35,max(4,cx+flip*(4+reach))),shoulder+4-abs(reach)//5)]
        else:
            hands=[(cx-span-1+reach//5,shoulder+7),(cx+span+min(4,reach//5),shoulder+(7 if back else 9)+(-1 if back else 1)*reach//3)]
    elif kind=="hurt":
        hands=[(cx-span-3,shoulder+6),(cx+span+3,shoulder+4)]
    else:
        hands=[(cx-span-1,shoulder+8),(cx+span+1,shoulder+8)]
    for index in (0,1):
        sx=cx-span if index==0 else cx+span
        sy=shoulder+(yaw//2 if index==0 else -yaw//2)
        hx,hy=hands[index]
        elbow=((sx+hx)//2+(flip if side else (-1 if index==0 else 1)),max(sy+3,(hy+sy)//2+1))
        if index==1:
            torso=[(cx-span,shoulder),(cx-2,shoulder-3),(cx+3,shoulder-3),(cx+span,shoulder-1),
                   (cx+span-1,hip),(cx+2,hip+2),(cx-span+1,hip)]
            d.polygon(torso,fill=INK)
            d.polygon([(cx-span+1,shoulder),(cx-2,shoulder-2),(cx+3,shoulder-2),(cx+span-1,shoulder),
                       (cx+span-2,hip-1),(cx+1,hip+1),(cx-span+2,hip-1)],fill=JACKET[1])
            d.line([(cx-span+2,shoulder+1),(cx-2,shoulder),(cx-2,hip-2)],fill=JACKET[2])
            d.line([(cx+span-2,shoulder+2),(cx+span-3,hip-2),(cx+1,hip)],fill=JACKET[0])
            if not back:
                x=cx+(flip*2 if side else 0)
                d.polygon([(x-2,shoulder-1),(x+2,shoulder),(x+1,hip-1),(x-1,hip-1)],fill="#cebaa3")
                d.line([(x-1,shoulder+1),(x-1,hip-3)],fill="#efe0c1")
                d.point((cx-span+2,hip-2),fill="#aa8b81")
        limb(d,[(sx,sy),(elbow[0],elbow[1]),(hx,hy)],4,JACKET)
        d.rounded_rectangle((hx-2,hy-1,hx+2,hy+2),1,fill=INK)
        d.line([(hx-1,hy),(hx+1,hy)],fill=FLESH[1],width=2)
        d.point((hx,hy-1),fill=FLESH[2])
        if index==1 and kind in ("bat","shoot"):
            dx=flip if side else 0
            dy=(-1 if back else 1) if not side else 0
            if kind=="bat":
                # Bat follows the same wrist through wind-up and extension.
                xx=max(2,min(37,hx+dx*(10 if phase>=2 else -6)+(3 if not side else 0)))
                yy=max(2,min(37,hy+dy*(8 if phase>=2 else -8)-(5 if side and phase<=2 else 0)))
                limb(d,[(hx,hy),(xx,yy)],3,("#805c50","#b78b68","#d8a777"))
            else:
                xx=max(2,min(37,hx+dx*6))
                yy=hy+dy*5
                limb(d,[(hx,hy),(xx,yy)],3,("#303142","#6d798a","#9da6aa"))
                if phase==2:
                    d.polygon([(xx,yy-3),(xx+3,yy),(xx,yy+3),(xx-3,yy)],fill="#edac70")
                    d.point((xx,yy),fill="#ffe4b3")
    hx=cx-6+(flip if side and kind=="run" else 0)
    im.alpha_composite(face,(hx,3+bob))
    return im


def atlas(source,kind,count):
    im=Image.new("RGBA",(W*count,H*4))
    for direction in range(4):
        face=head(source,direction)
        for phase in range(count):
            if kind=="fall":
                frame=pose(direction,0,"idle",face)
                if phase>=1:
                    turn=Image.Transpose.ROTATE_90 if direction in (0,2) else Image.Transpose.ROTATE_270
                    frame=frame.crop((4,4,36,38)).transpose(turn)
                    cell=Image.new("RGBA",(W,H))
                    cell.alpha_composite(frame,(3,phase*3+10))
                    frame=cell
            else:
                action="idle" if kind=="walk" and phase>=4 else kind
                frame=pose(direction,phase,action,face)
            im.paste(frame,(W*phase,H*direction))
    save_indexed(im,ROOT/(kind+"05.png"))


if __name__=="__main__":
    source=Image.open(ROOT/"walker.png").convert("RGBA")
    for action,n in (("walk",6),("run",4),("fist",6),("bat",6),("shoot",6),("hurt",3),("fall",3)):
        atlas(source,action,n)

NPCS={
    9:("hardhat",("#44323a","#80624d","#b19370"),("#302e37","#504753","#726372"),"fist"),
    19:("red",("#462632","#874455","#bc6b69"),("#322d40","#503e55","#726077"),"fist"),
    23:("crew",("#31313a","#58505c","#837677"),("#2c3742","#455363","#6c7480"),"bat"),
    24:("crew",("#333244","#545970","#79778d"),("#2f3037","#424952","#666672"),"shoot"),
    25:("law",("#26314a","#465775","#73859c"),("#222a39","#3b485f","#67758d"),"shoot"),
    26:("medic",("#54444d","#9d8e81","#d9bf9f"),("#344154","#526474","#7a85a0"),"fist"),
    28:("broker",("#4b393f","#806468","#ba9284"),("#343546","#514d62","#736c7d"),"fist"),
    27:("guard",("#403738","#6c685c","#a29a78"),("#2c3540","#464f5b","#686e7b"),"bat"),
}


def npc_atlases():
    def rgb(value):
        return tuple(bytes.fromhex(value[1:]))+(255,)

    for model,(role,jacket,pants,attack) in NPCS.items():
        source=Image.open(ROOT/f"pedestrian{25 if model==27 else 7 if model==28 else model}.png").convert("RGBA")
        out=Image.new("RGBA",(196 if model==25 else 168,152))
        jmap={rgb(a):rgb(b) for a,b in zip(JACKET,jacket)}
        jmap.update((rgb(a),rgb(b)) for a,b in zip(DENIM,pants))
        jmap.update((rgb(a),rgb(b)) for a,b in zip(("#25303e","#384d61","#526678"),pants))
        for direction in range(4):
            face=source.crop((5,direction*36,22,direction*36+12))
            for slot in range(7 if model==25 else 6):
                kind="walk" if slot<4 else "idle" if slot==4 else "fist" if slot==6 else attack
                phase=slot if slot<4 else 0 if slot==4 else 3
                im=pose(direction,phase,kind,face)
                im.putdata([jmap.get(p,p) for p in im.get_flattened_data()])
                d=ImageDraw.Draw(im)
                if role=="hardhat":
                    d.polygon([(14,8),(15,5),(18,3),(23,3),(26,6),(27,9),(13,9)],fill="#5a4139")
                    d.polygon([(15,7),(17,4),(23,4),(25,7),(25,8),(15,8)],fill="#cc9c5c")
                    d.line([(17,5),(21,5),(23,6)],fill="#e4bb79")
                    d.line([(15,20),(24,20)],fill="#c1a173")
                elif role=="law":
                    d.line([(15,22),(24,22)],fill="#293142")
                    d.point((22,16),fill="#e6c88b")
                elif role=="medic":
                    d.line([(19,17),(19,21)],fill="#ae5b62")
                    d.line([(17,19),(21,19)],fill="#ae5b62")
                    d.rounded_rectangle((9,23,14,27),1,fill="#51444d")
                    d.rectangle((10,24,13,25),fill="#ba9c89")
                elif role=="red":
                    d.line([(15,17),(18,19),(20,20)],fill="#ce7778")
                    d.line([(24,19),(26,19)],fill="#9f515b")
                elif role=="broker":
                    d.line([(15,18),(22,23)],fill="#a68478")
                    d.line([(21,20),(18,24)],fill="#78545f")
                    d.point((21,20),fill="#dcb088")
                elif role=="guard":
                    d.line([(15,8),(26,8)],fill="#807862")
                    d.rectangle((18,16,22,18),fill="#a89a7d")
                    d.point((19,17),fill="#ded0aa")
                else:
                    d.line([(15,17),(19,17)],fill="#b2a39f")
                    d.line([(23,19),(23,22)],fill="#958a91")
                clip=im.crop((6,1,34,39))
                out.paste(clip,(28*slot,38*direction))
        save_indexed(out,ROOT/f"person05-{model}.png")


if __name__=="__main__":
    source=Image.open(ROOT/"walker.png").convert("RGBA")
    for action,n in (("walk",6),("run",4),("fist",6),("bat",6),("shoot",6),("hurt",3),("fall",3)):
        atlas(source,action,n)
    npc_atlases()
