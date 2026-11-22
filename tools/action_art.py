"""Action cells and emergency-service sprites on the existing native pixel grid."""
from pathlib import Path
from PIL import Image, ImageDraw
from actor_art import save_indexed

ROOT=Path(__file__).resolve().parents[1]/"res"


def actor_frame(atlas, direction, phase):
    return atlas.crop((phase*24,direction*36,phase*24+24,direction*36+36))


def actions():
    hero=Image.open(ROOT/"walker.png").convert("RGBA")
    atlas=Image.new("RGBA",(180,176))
    for direction in range(4):
        idle=actor_frame(hero,direction,0)
        for column in range(5):
            frame=Image.new("RGBA",(36,44))
            if column==4:
                frame.alpha_composite(idle,(6,5))
                for y in range(17,27):
                    for x in range(10,27):
                        r,g,b,a=frame.getpixel((x,y))
                        if a==255 and 43<r<79 and abs(r-b)<22:
                            if (x+y)%5==0:
                                frame.putpixel((x,y),(r+6,g+6,b+7,255))
                d=ImageDraw.Draw(frame)
                if direction==1:
                    d.line([(17,19),(18,20),(18,25)],fill="#938378")
                elif direction==0:
                    d.line([(11,19),(14,18),(20,18),(23,19)],fill="#605360")
            elif column>=2:
                lean=idle.transpose(Image.Transpose.ROTATE_270 if direction in (0,3) else Image.Transpose.ROTATE_90)
                frame.alpha_composite(lean,(0,14 if column==2 else 21))
            elif column==1:
                frame.alpha_composite(actor_frame(hero,direction,2),(7,7))
                d=ImageDraw.Draw(frame)
                d.line([(14,19),(16,20),(19,20)],fill="#ac7771")
                d.point((20,20),fill="#dcab92")
            else:
                frame.alpha_composite(idle,(6,5))
                d=ImageDraw.Draw(frame)
                if direction>=2:
                    sign=-1 if direction==2 else 1
                    x=15 if sign<0 else 21
                    p=[(x,21),(x+4*sign,22),(x+10*sign,21),(x+11*sign,23),(x+8*sign,26),(x+4*sign,25),(x,25)]
                    d.polygon(p,fill="#211b24")
                    d.line([(x,22),(x+4*sign,23),(x+8*sign,22)],fill="#60515b",width=2)
                    d.line([(x+8*sign,22),(x+10*sign,22),(x+10*sign,24),(x+8*sign,24)],fill="#c9916d")
                    d.point((x+10*sign,22),fill="#e5ac81")
                else:
                    x=22 if direction==0 else 24
                    y=16 if direction==0 else 25
                    d.polygon([(x-5,18),(x-1,18),(x+3,y),(x+5,y+2),(x+1,24),(x-3,23)],fill="#251f28")
                    d.line([(x-3,19),(x,21),(x+2,y+2)],fill="#60535d",width=2)
                    d.rectangle((x+1,y,x+3,y+2),fill="#d09b76")
            atlas.paste(frame,(column*36,direction*44))
    save_indexed(atlas,ROOT/"actions.png")


def coachwork():
    targets=(("sedan.png",44,1),("traffic.png",48,3),("cruiser.png",44,1))
    for name,cell,rows in targets:
        path=ROOT/name
        atlas=Image.open(path).convert("RGBA")
        for model in range(rows):
            for direction in range(4):
                ox,oy=direction*cell,model*cell
                for y in range(10,cell-10):
                    for x in range(4,cell-4):
                        r,g,b,a=atlas.getpixel((ox+x,oy+y))
                        if a!=255: continue
                        blue=b>r+9 and 47<r<132
                        warm=r>g+19 and 76<r<191
                        stone=abs(r-g)<23 and 96<r<190
                        if not (blue or warm or stone): continue
                        if direction>=2:
                            if y==20 and 9<x<cell-8:
                                atlas.putpixel((ox+x,oy+y),(min(255,r+14),min(255,g+13),min(255,b+14),255))
                            elif y==23 and 11<x<cell-9:
                                atlas.putpixel((ox+x,oy+y),(max(0,r-9),max(0,g-8),max(0,b-7),255))
                            elif y==18 and x in (15,23,31):
                                atlas.putpixel((ox+x,oy+y),(min(255,r+21),min(255,g+18),min(255,b+18),255))
                        elif x in (cell//2-6,cell//2+5) and 13<y<cell-13:
                            atlas.putpixel((ox+x,oy+y),(min(255,r+12),min(255,g+13),min(255,b+14),255))
        save_indexed(atlas,path)


def ambulance():
    traffic=Image.open(ROOT/"traffic.png").convert("RGBA")
    result=Image.new("RGBA",(192,48))
    for direction in range(4):
        frame=traffic.crop((direction*48,96,direction*48+48,144))
        d=ImageDraw.Draw(frame)
        for y in range(48):
            for x in range(48):
                r,g,b,a=frame.getpixel((x,y))
                if a==255 and r>=105 and 0<=r-b<34 and abs(r-g)<32:
                    d.point((x,y),fill=(min(r+18,237),min(g+23,229),min(b+24,218)))
        if direction>=2:
            left=3 if direction==3 else 22
            d.polygon([(left,8),(left+20,8),(left+22,12),(left+20,19),(left,19)],fill="#594e54")
            d.polygon([(left+1,9),(left+19,9),(left+20,13),(left+19,18),(left+1,18)],fill="#d8ccc0")
            d.line([(left+2,10),(left+18,10)],fill="#f1e3cb")
            d.line([(left+2,17),(left+19,17)],fill="#ab9390")
            d.line([(left+3,20),(left+19,20)],fill="#a34b50")
            d.line([(left+3,21),(left+19,21)],fill="#be6e69")
            cross=left+10
            d.rectangle((cross-1,11,cross+1,16),fill="#a64d53")
            d.rectangle((cross-3,13,cross+3,14),fill="#a64d53")
            d.rectangle((left+4,7,left+8,9),fill="#54728e")
            d.line([(left+4,7),(left+7,7)],fill="#a6c3cc")
        else:
            d.rounded_rectangle((16,7,32,25),radius=2,fill="#62565d")
            d.rectangle((17,8,31,24),fill="#ded2c4")
            d.line([(18,9),(30,9),(31,10)],fill="#f0e3c9")
            d.rectangle((23,12,25,21),fill="#a24e53")
            d.rectangle((20,15,28,18),fill="#a24e53")
            d.line([(18,22),(30,22)],fill="#9f5a60")
            d.rectangle((19,24,29,26),fill="#323442")
            d.rectangle((20,24,23,25),fill="#67869b")
            d.rectangle((25,24,28,25),fill="#af6264")
        result.paste(frame,(direction*48,0))
    save_indexed(result,ROOT/"ambulance.png")


def medic():
    source=Image.open(ROOT/"pedestrian25.png").convert("RGBA")
    for direction in range(4):
        for phase in range(4):
            frame=actor_frame(source,direction,phase)
            d=ImageDraw.Draw(frame)
            for y in range(11,26):
                for x in range(5,20):
                    r,g,b,a=frame.getpixel((x,y))
                    if a==255 and b>r+10 and 37<=r<=133:
                        if y<21 and 7<x<17: d.point((x,y),fill=(r+57,g+38,b+23))
                        else: d.point((x,y),fill=(r+15,g+18,b+12))
            x=13 if direction in (0,2) else 10
            d.line([(x,14),(x,18)], fill="#ac5b60")
            d.line([(x-2,16),(x+2,16)], fill="#ac5b60")
            d.line([(8,4),(13,4)], fill="#a69a94")
            if direction!=0:
                x=17 if direction in (1,2) else 5
                d.rounded_rectangle((x-1,21,x+3,26),radius=1,fill="#463644")
                d.rectangle((x,22,x+2,24),fill="#99847a")
                d.point((x+1,23),fill="#ccaaa1")
            source.paste(frame,(phase*24,direction*36))
    save_indexed(source,ROOT/"pedestrian26.png")


if __name__=="__main__":
    actions()
    coachwork()
    ambulance()
    medic()
