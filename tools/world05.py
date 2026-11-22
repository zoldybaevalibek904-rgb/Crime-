"""Pixel artwork for the interiors and the new transport families."""
from PIL import Image, ImageDraw
from actor_art import ROOT, save_indexed
from art import Art, INK

# Bed length, roof rise, cabin position and body palettes differ for each chassis.
MOTORS=(
    (43,26,13,("#565158","#8c7774","#b09988","#4b3e46")),
    (42,23,8,("#405a65","#62878c","#91a9a3","#30414c")),
    (46,27,10,("#673b45","#a55c58","#cb846e","#56303d")),
    (46,28,12,("#454356","#777186","#a29a9c","#343244")),
)


def tire(d,x,y,r):
    d.ellipse((x-r,y-r,x+r,y+r),fill="#231d27")
    d.ellipse((x-r+2,y-r+2,x+r-2,y+r-2),fill="#4e4b55")
    d.line([(x-2,y),(x,y-2),(x+2,y),(x,y+2),(x-2,y)],fill="#9a8884")
    d.point((x,y),fill="#d0b4a0")


def side(spec,kind):
    length,height,rise,(shade,body,light,dark)=spec
    im=Image.new("RGBA",(48,48))
    d=ImageDraw.Draw(im)
    a=(48-length)//2
    b=47-a
    roof=23-rise
    d.ellipse((a,30,b,38),fill=(33,26,34,105))
    tail=10 if kind==0 else 8
    d.polygon([(a,22),(a+4,19),(a+tail,19),(a+17,roof+1),(a+22,roof),
               (b-15,roof),(b-9,18),(b-3,19),(b,23),(b,29),(b-3,33),
               (a+3,33),(a,30)],fill=INK)
    d.polygon([(a+2,23),(a+6,20),(a+tail+1,20),(a+18,roof+2),(a+23,roof+1),
               (b-16,roof+1),(b-9,19),(b-3,21),(b-2,25),(b-3,30),(a+3,31)],fill=body)
    # Silhouette-specific lower shoulder and trim.
    d.line([(a+3,25),(a+16,24),(b-11,24),(b-3,23)],fill=light)
    d.polygon([(a+3,27),(b-2,26),(b-4,31),(a+4,31)],fill=shade)
    d.line([(a+5,29),(b-6,29)],fill=dark)
    if kind==0:
        d.polygon([(a+3,20),(a+15,20),(a+15,24),(a+4,23)],fill="#3d3540")
        d.line([(a+4,20),(a+15,20)],fill=light)
        d.line([(a+14,21),(a+14,26)],fill=dark)
        glass=[(a+16,21),(a+21,roof+3),(b-16,roof+3),(b-10,19),(b-11,22)]
    elif kind==1:
        glass=[(a+11,22),(a+19,roof+3),(b-17,roof+2),(b-9,19),(b-10,22)]
        d.line([(a+4,25),(a+8,24)],fill="#b5a99a")
    elif kind==2:
        glass=[(a+12,20),(a+19,roof+3),(b-16,roof+3),(b-11,19),(b-15,21)]
        d.line([(b-13,22),(b-5,22)],fill="#e0a68c")
        d.line([(a+8,26),(a+13,26)],fill="#c88d77")
    else:
        glass=[(a+10,20),(a+19,roof+3),(b-16,roof+3),(b-9,19),(b-11,22)]
        d.line([(a+7,27),(b-6,27)],fill="#baa79d")
        d.line([(a+24,23),(a+24,29)],fill=dark)
    d.polygon(glass,fill="#2b2d3d")
    d.line([glass[0],glass[1],glass[2]],fill="#7d9199")
    d.line([(a+24,roof+4),(a+24,22)],fill="#494756")
    d.line([(b-17,roof+4),(b-12,20)],fill="#566877")
    d.line([(a+25,25),(a+28,25)],fill=light)
    d.rectangle((a+1,23,a+3,25),fill="#bd6268")
    d.point((a+2,23),fill="#e98879")
    d.rectangle((b-3,22,b-1,24),fill="#edc38f")
    d.line([(b-4,28),(b-1,28)],fill="#777780")
    for x in (a+9,b-9): tire(d,x,31,5 if kind==0 else 4)
    return im


def axial(spec,kind,direction):
    _,_,rise,(shade,body,light,dark)=spec
    im=Image.new("RGBA",(48,48))
    d=ImageDraw.Draw(im)
    length=(43,41,44,45)[kind]
    half=(12,10,11,11)[kind]
    top=(48-length)//2
    end=47-top
    l,r=24-half,23+half
    nose=direction==1
    d.ellipse((l-2,top+2,r+3,end+2),fill=(30,25,34,103))
    for y in (top+8,end-12):
        for x in (l-1,r-1):
            d.rounded_rectangle((x,y,x+3,y+7),1,fill="#211e28")
    d.polygon([(l+3,top),(r-3,top),(r,top+5),(r,end-4),(r-3,end),
               (l+3,end),(l,end-4),(l,top+5)],fill=INK)
    d.polygon([(l+4,top+2),(r-4,top+2),(r-2,top+6),(r-2,end-4),
               (r-5,end-2),(l+5,end-2),(l+2,end-5),(l+2,top+6)],fill=body)
    d.line([(l+2,top+8),(l+2,end-7)],fill=light)
    d.line([(r-2,top+8),(r-2,end-7)],fill=dark)
    rear=top+5 if nose else end-12
    if kind==0:
        d.rectangle((l+4,rear,r-4,rear+7),fill="#433943")
        for y in (rear+2,rear+4,rear+6):
            d.line([(l+5,y),(r-5,y)],fill="#77646a")
    roof=16 if nose else 20
    d.polygon([(l+3,roof),(r-3,roof),(r-2,roof+4),(r-3,roof+12),
               (l+3,roof+12),(l+2,roof+4)],fill="#282c3a")
    d.rectangle((l+4,roof+2,r-4,roof+8),fill=shade)
    d.line([(l+5,roof+2),(r-5,roof+2)],fill=light)
    wind=roof+9 if nose else roof-4
    d.line([(l+4,wind),(r-4,wind)],fill="#7b8c96")
    for x in (l+3,r-5):
        d.rectangle((x,top+3,x+2,top+5),fill="#af545c" if nose else "#e9c58f")
        d.rectangle((x,end-5,x+2,end-3),fill="#e9c58f" if nose else "#af545c")
    d.line([(l+6,end-3),(r-6,end-3)],fill="#77717c")
    return im


def motor_atlas():
    atlas=Image.new("RGBA",(192,192))
    for i,spec in enumerate(MOTORS):
        right=side(spec,i)
        frames=(axial(spec,i,0),axial(spec,i,1),right.transpose(Image.Transpose.FLIP_LEFT_RIGHT),right)
        for d,im in enumerate(frames): atlas.paste(im,(d*48,i*48))
    save_indexed(atlas,ROOT/"motors05.png")


def rotate_atlas(image,name):
    atlas=Image.new("RGBA",(192,48))
    for direction,angle in enumerate((0,180,90,270)):
        atlas.paste(image.rotate(angle),(direction*48,0))
    save_indexed(atlas,ROOT/name)


def special():
    im=Image.new("RGBA",(48,48)); d=ImageDraw.Draw(im)
    d.ellipse((7,11,42,46),fill=(33,26,35,110))
    for x in (9,32):
        d.rounded_rectangle((x,8,x+7,40),3,fill="#292731")
        d.rounded_rectangle((x+1,10,x+6,38),2,fill="#4f4e52")
        for y in range(11,39,4): d.line((x+1,y,x+6,y),fill="#82776e")
    d.polygon([(15,12),(18,9),(31,9),(34,14),(34,34),(30,39),(18,39),(14,34)],fill="#302d36")
    d.polygon([(17,14),(21,11),(30,12),(32,16),(32,32),(28,36),(19,36),(16,32)],fill="#69716a")
    d.line([(17,16),(17,30),(20,34)],fill="#9d9b7d")
    d.polygon([(18,20),(22,17),(29,17),(32,22),(30,30),(26,32),(19,29),(17,25)],fill="#494f4d")
    d.line([(19,24),(22,20),(29,20)],fill="#98a086")
    d.rounded_rectangle((22,3,27,22),2,fill="#30363b")
    d.line([(23,5),(23,18)],fill="#888e79")
    d.rectangle((22,14,27,17),fill="#707a72")
    d.ellipse((25,22,29,26),fill="#a3a08a")
    rotate_atlas(im,"tank05.png")
    im=Image.new("RGBA",(48,48)); d=ImageDraw.Draw(im)
    d.polygon([(20,6),(23,3),(27,4),(30,8),(33,17),(31,24),(27,28),(26,40),
               (23,44),(20,42),(22,29),(18,25),(15,19),(16,12)],fill="#251f2b")
    d.polygon([(21,7),(25,5),(29,9),(32,16),(30,22),(26,26),(25,40),
               (23,41),(23,28),(19,24),(17,19),(18,12)],fill="#627787")
    d.polygon([(20,8),(25,7),(28,10),(29,16),(26,19),(20,18),(18,14)],fill="#354858")
    d.line([(21,9),(25,8),(28,12)],fill="#9cb2ad")
    d.line([(18,19),(22,21),(28,20)],fill="#d5c0a6")
    d.line([(25,28),(24,39)],fill="#a7a29a")
    d.polygon([(19,36),(28,36),(28,39),(18,39)],fill="#4e5666")
    d.line([(13,14),(12,22),(14,27),(18,27)],fill="#88888f",width=2)
    d.line([(33,14),(35,22),(33,27),(29,27)],fill="#88888f",width=2)
    d.ellipse((22,20,27,24),fill="#b69d89")
    d.point((29,20),fill="#bf6060")
    rotate_atlas(im,"heli05.png")


ROOMS=(
    ((18,25,44,53,"stock"),(70,25,18,62,"stock"),(104,25,18,62,"stock"),(151,22,27,83,"counter")),
    ((18,26,46,27,"tools"),(114,28,58,50,"bench"),(17,93,40,21,"tools")),
    ((19,25,31,78,"tools"),(65,24,106,25,"counter"),(138,78,36,30,"bench")),
    ((19,23,148,24,"counter"),(24,68,33,24,"table"),(116,68,33,24,"table"),(116,113,33,16,"bench")),
    ((18,27,53,38,"bed"),(111,24,56,35,"bench"),(21,93,52,27,"tools"),(125,88,47,32,"bed")),
)


def furniture(a,x,y,w,h,kind):
    d=a.d
    d.polygon([(x,y+5),(x+w+4,y+5),(x+w+4,y+h+4),(x+1,y+h+4)],fill="#492f3a")
    d.rectangle((x,y,x+w-1,y+h-1),fill="#38313b")
    d.rectangle((x+2,y+2,x+w-3,y+h-4),fill="#866d60")
    d.line([(x+3,y+2),(x+w-4,y+2)],fill="#ae8973")
    if kind=="stock":
        for row in range(y+7,y+h-5,12):
            d.line((x+3,row+5,x+w-4,row+5),fill="#5f4651")
            for col in range(x+4,x+w-7,7):
                color=("#a56c62","#bfa68c","#607f78")[(col+row)%3]
                d.rectangle((col,row,col+4,row+4),fill=color)
                d.line((col,row,col+3,row),fill="#c3a38b")
    elif kind in ("tools","counter"):
        d.rectangle((x+3,y+5,x+w-4,y+h-7),fill="#514953")
        for col in range(x+7,x+w-7,11):
            d.line([(col,y+6),(col+3,y+9),(col+3,y+13)],fill="#b69c8a")
        for col in range(x+6,x+w-7,13):
            d.rectangle((col,y+h-10,col+6,y+h-7),fill="#a08470")
        if kind=="counter":
            d.rectangle((x+7,y+5,x+14,y+12),fill="#749492")
            d.line((x+8,y+6,x+12,y+6),fill="#b6b7a4")
    elif kind=="bed":
        d.rectangle((x+4,y+5,x+w-5,y+h-6),fill="#74576b")
        d.rounded_rectangle((x+5,y+5,x+w-7,y+13),2,fill="#c4a88f")
        for row in range(y+15,y+h-6,4):
            d.line((x+6,row,x+w-8,row),fill="#927084")
    elif kind=="table":
        d.rounded_rectangle((x+4,y+3,x+w-4,y+h-4),5,fill="#b18b71")
        d.ellipse((x+7,y+8,x+14,y+14),fill="#d3bb9a")
        d.ellipse((x+w-15,y+8,x+w-8,y+14),fill="#d3bb9a")
        d.rectangle((x+w//2,y+6,x+w//2+3,y+11),fill="#7f5965")
    else:
        d.rectangle((x+5,y+4,x+w-6,y+10),fill="#b1907d")
        d.rectangle((x+5,y+12,x+w-6,max(y+12,y+h-7)),fill="#6d5961")
        d.line((x+7,y+13,x+w-8,y+13),fill="#947772")


def rooms():
    for i,layout in enumerate(ROOMS):
        a=Art(192,160,("#68586a","#5d6066","#665665","#84646b","#6d5c69")[i]); d=a.d
        for y in range(11,154,9):
            d.line((10,y,181,y),fill="#584a58")
            for x in range(13+(y%2)*6,180,16):
                d.line((x,y+1,x,y+7),fill="#594e5a")
                d.line((x+2,y+2,x+9,y+2),fill="#92777a")
        for x in (1,181):
            d.rectangle((x,1,x+9,153),fill="#413846")
            d.line((x+3,5,x+3,149),fill="#8b737e")
        d.rectangle((4,2,187,16),fill="#484154")
        for x in (18,60,107,147):
            d.rectangle((x,5,x+21,12),fill="#526a75")
            d.line((x+1,6,x+18,6),fill="#819299")
            d.line((x+9,6,x+9,11),fill="#b6a2a1")
        d.rectangle((10,153,181,158),fill="#403747")
        d.rectangle((81,150,111,158),fill="#a28376")
        d.rectangle((84,145,109,151),fill="#836171")
        d.line((86,147,107,147),fill="#a78881")
        for rect in layout: furniture(a,*rect)
        a.save("room05-"+str(i))


def exteriors():
    from polish04 import architecture
    from art import scenery
    from world_art import new_art
    from expand04 import assets
    assets()
    originals=scenery()+new_art()
    for kind in (1,2,3,17,18):
        a=originals[kind]
        architecture(kind,a)
        d=a.d
        if kind==1:
            # Painted awning scallops and independent shop window displays.
            for x in range(13,124,12):
                d.line([(x,57),(x+5,57),(x+6,58)],fill="#c78878")
            for x in (17,41,100,115):
                d.rectangle((x,67,x+6,71),fill="#b48964")
                d.line((x+1,67,x+4,67),fill="#ecd19a")
        elif kind==3:
            # Wintergarden glass follows each roof bay with a distinct frame.
            for x in (28,56,83):
                d.polygon([(x,19),(x+14,19),(x+18,24),(x+14,27),(x,27)],fill="#3d4659")
                d.line((x+1,19,x+14,19),fill="#8b9a9e")
                d.line((x+2,22,x+14,22),fill="#647a85")
        elif kind==17:
            for x in (14,113):
                d.rectangle((x,81,x+26,91),fill="#514650")
                for y in range(82,91,2):
                    d.line((x+3,y,x+23,y),fill="#7a7682")
                d.line((x,81,x+24,81),fill="#aaa092")
                d.rectangle((x+16,85,x+21,87),fill="#bd9769")
        elif kind==18:
            for x in (13,88):
                d.rectangle((x,55,x+5,58),fill="#7b6b61")
                d.line((x,55,x+5,55),fill="#c5a689")
                d.line((x+1,57,x+1,61),fill="#815151")
        else:
            for x in (18,83):
                d.line((x,55,x+11,55),fill="#9e8baf")
                d.line((x,56,x+10,56),fill="#554658")
        a.save(f"prop{kind}")
    for k,x0,y0,label in ((27,19,29,"SUPPLY"),(28,22,39,"GARAGE")):
        path=ROOT/f"prop{k}.png"
        out=Image.open(path).convert("RGBA")
        d=ImageDraw.Draw(out)
        d.line([(x0-5,y0-2),(x0+26,y0-2)],fill="#cca98d")
        d.line([(x0-4,y0+10),(x0+27,y0+10)],fill="#6b4c59")
        d.ellipse((x0-8,y0+3,x0-5,y0+6),fill="#e9bd81")
        d.ellipse((x0+28,y0+3,x0+31,y0+6),fill="#e9bd81")
        out.save(path,optimize=True)


if __name__=="__main__":
    motor_atlas()
    special()
    rooms()
    exteriors()
