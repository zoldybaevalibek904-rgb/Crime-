"""Architectural materials and street detail on the original 16px tile grid."""
from PIL import Image, ImageDraw
from art import ROOT, scenery
from world_art import new_art, add_tiles

BUILDING_ROOFS = {
    0:(126,86,"brick"),1:(137,85,"store"),2:(113,78,"bar"),
    3:(141,99,"glass"),4:(91,67,"metal"),5:(72,57,"brick"),
    16:(91,71,"house"),17:(157,102,"industrial"),
    18:(105,68,"coast"),19:(102,71,"store"),
}


def architecture(kind, asset):
    w,h,style=BUILDING_ROOFS[kind]
    d=asset.d
    r=h-35
    d.line([(3,5),(w-3,5)],fill="#c0a299")
    d.line([(4,r-3),(w-4,r-3)],fill="#8b7179")
    d.line([(4,r+1),(w-5,r+1)],fill="#574351")
    if style=="house":
        d.polygon([(8,12),(w//2,8),(w-9,12),(w-10,r-7),(9,r-7)],fill="#785460")
        d.line([(w//2,9),(w//2,r-8)],fill="#af7474")
        for y in range(14,r-7,4):
            d.line([(10,y),(w//2-1,y-2)],fill="#624351")
            d.line([(w//2+1,y-2),(w-10,y)],fill="#684650")
        d.rectangle((62,14,70,23),fill="#513b48")
        d.line([(62,14),(70,14)],fill="#c5917e")
    elif style in ("industrial","metal"):
        for x in range(10,w-11,6):
            d.line([(x,11),(x,r-8)],fill="#4f5160")
            d.line([(x+1,12),(x+1,r-8)],fill="#66717a")
        for x in range(18,w-20,39):
            top=15
            d.rectangle((x,top,x+15,top+10),fill="#333643")
            d.rectangle((x+2,top+1,x+13,top+8),fill="#73858b")
            d.line([(x+3,top+3),(x+11,top+3)],fill="#8f9b96")
        if style=="industrial":
            for x in (27,64,101,138):
                d.line([(x,r+5),(x,r+28)],fill="#8e8186")
    elif style=="glass":
        for i in range(3):
            x=22+i*27
            d.rectangle((x,25,x+21,45),fill="#453d4b")
            d.polygon([(x+2,26),(x+18,26),(x+18,42),(x+2,42)],fill="#5a727d")
            d.line([(x+3,27),(x+17,27)],fill="#8b999b")
            d.line([(x+6,28),(x+6,40),(x+13,40)],fill="#6d828b")
        d.line([(8,r+16),(w-8,r+16)],fill="#a07a76")
    else:
        for x in range(9,w-10,19):
            d.line([(x,11),(x,13)],fill="#ad8e87")
        if style in ("store","coast","bar"):
            d.line([(8,r+6),(w-9,r+6)],fill="#cb9480" if style!="coast" else "#8ca69d")
            for x in range(11,w-13,18):
                d.line([(x,r+15),(x+2,r+15)],fill="#483644")
        for x in range(7,w-12,21):
            d.line([(x,r+5),(x,r+9)],fill="#a4796b")


def pavement():
    path=ROOT/"tiles.png"
    atlas=Image.open(path).convert("RGBA")
    for tile in range(56):
        x,y=tile%8*16,tile//8*16
        cell=atlas.crop((x,y,x+16,y+16))
        d=ImageDraw.Draw(cell)
        if tile in (0,1,2,3):
            offset=(tile*5)%11
            d.line([(offset,11),(offset+3,11),(offset+4,12),(offset+5,12)],fill="#4b4249")
            if tile in (2,3): d.line([(8,3),(10,3),(11,4),(13,4)],fill="#352e37")
        elif tile in (4,5,6,7):
            for p in (2,8):
                d.line([(p,3+(tile%3)),(p+3,3+(tile%3))],fill="#ad8f80")
            d.line([(2,15),(13,15)],fill="#80665f")
        elif 24<tile<40:
            flags=tile-24
            for b in range(4):
                if not flags&(1<<b): continue
                for p in (3,10):
                    if b<2: d.line([(p,b*14),(p,b*14+1)],fill="#9d7e74")
                    else: d.line([((b-2)*14,p),((b-2)*14+1,p)],fill="#9d7e74")
        elif 40<=tile<=43:
            for p,q in ((3,7),(11,11),(9,3)):
                d.line([(p,q),(p+2,q),(p+3,q+1)],fill="#d5b691")
                d.point((p+1,q+1),fill="#ac866e")
        elif 44<=tile<=47:
            d.line([(7,tile-41),(12,tile-41),(14,tile-40)],fill="#638e93")
        elif 48<=tile<=51:
            d.line([(1,3),(3,3),(4,4)],fill="#bb9c84")
            d.line([(6,13),(10,13),(11,14)],fill="#69959c")
        atlas.paste(cell,(x,y))
    atlas.save(path,optimize=True)


if __name__=="__main__":
    original=scenery()+new_art()
    for kind in BUILDING_ROOFS:
        asset=original[kind]
        architecture(kind,asset)
        asset.save(f"prop{kind}")
    add_tiles()
    pavement()
