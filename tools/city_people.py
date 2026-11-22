"""District uniforms and silhouettes, painted on the native Crime District sprite grid."""
from pathlib import Path
from PIL import Image, ImageDraw
import actor_art as studio

ROOT = Path(__file__).resolve().parents[1] / "res"
# Base cut, garment and trouser folds, skin ramp, hair, and hand-placed details.
CAST = (
    (4,"29303a 3d4854 55636f 727d88","292b31 363945 4e5261 666977","815247 a66d58 d29977","32282b 694d43","clerk"),
    (6,"5b454f 825c66 a47b7d c39792","272b3a 3b4356 475771 647084","925a43 c58b61 e4af83","362a2f 744e45","sweater"),
    (1,"47403e 665045 99745b b68b6b","302e32 404047 59555a 776768","885643 b97c54 e2a677","35292c 614840","hardhat"),
    (5,"32404b 445b69 62818a 8b9e9e","30353c 424651 565f6b 74737c","784937 a8694c cf9368","32242a 5c3e36","runner"),
    (4,"55454b 766068 988077 b29a88","322e34 443a42 5b4c54 74606b","8a6156 b6836a d5a588","554a50 9c8d84","walker"),
    (2,"4e4148 775b61 966e6e be9690","342e3b 4e4451 6a5862 827478","905d4c c28760 e7ab7d","35252b 62463d","vendor"),

    (2,"704c40 9b684d b67c59 d1996b","2e4655 3d6973 518389 7aa6a5","965b42 c48c63 e6b382","312a31 6b4d41","swim"),
    (3,"625044 9b7363 c59980 e3b896","5c454d 91565f b77c7d d69c92","8c493d b6755f d99d79","543238 936554","summer"),
    (1,"554a56 7a7179 a89589 c9ae91","514649 775b56 987359 b18c6c","7e5549 b77d62 d9a37d","37292c 65504b","holiday"),
    (5,"563742 85464c ad6158 ce8971","3b2f3a 5a4149 795859 976f69","8e5846 bc815b d6a47b","302631 5b3e3b","guard"),
    (6,"543e43 7b5756 a97464 c89478","34303c 494352 61576a 837380","875947 b88166 d9a388","322830 765446","ice"),
    (2,"3c4e53 5a7070 7c9183 a0aa90","2b3c49 405663 60727d 788f90","784735 a77750 c9976a","332931 624937","coach"),

    (3,"4b2836 753744 a24e54 ba675e","2d2938 403645 554c5c 746576","855344 b67756 d79b70","2b2329 674442","bandana"),
    (5,"402832 682c3e 8e4552 b26762","262939 3b3c4d 555464 6f6c80","724839 a06b4f c89168","31252c 634337","bomber"),
    (6,"522b36 753843 9e5152 c07264","282b38 403e50 5b5363 776b7c","926252 bd8061 dfa67c","402a32 7d5651","chain"),
    (1,"34363b 515354 757269 969082","2d3338 45484f 656673 837c7b","835440 af785b d3a47c","34292b 654b42","metal"),
    (2,"39363d 54474f 746273 8e7f88","272e37 394555 526074 6d798e","7d5245 a76f56 d29870","382b2e 745347","weld"),
    (5,"31313b 4e4d53 706b72 8d8588","2c3038 414750 5e606b 7b7585","8a5b4b b67a5e d4a080","333139 715852","iron"),

    (1,"263048 354665 526381 7985a0","252a3a 353e56 51596e 6f7485","835540 b47c58 d8a478","312832 61433c","police"),
)


def arrow_mark(d,x,y,color):
    d.line([(x-1,y),(x+1,y),(x,y+2),(x-1,y)],fill=color)


def costume(sprite, role, direction):
    d = ImageDraw.Draw(sprite)
    front, back = direction == 1, direction == 0
    profile = direction >= 2
    face = 8 if direction == 2 else 11 if direction == 3 else 10
    near = 7 if direction == 3 else 12
    if role in ("swim","summer","holiday","guard","coach"):
        shade = "#a76953"
        for y in range(25,30):
            for x in range(4,17):
                r,g,b,a = sprite.getpixel((x,y))
                if a == 255 and max(r,g,b) > 41 and r < 157:
                    d.point((x,y),fill=shade if y > 27 else "#ca9070")
        if role in ("swim","guard"):
            for y in range(13,20):
                for x in range(5,15):
                    r,g,b,a = sprite.getpixel((x,y))
                    if a == 255 and max(r,g,b) > 51 and (role == "swim" or x in (5,6,13,14)):
                        d.point((x,y),fill="#bc825d" if x > 10 else "#d09a72")
            if role == "swim":
                d.line([(8,13),(9,14),(11,14)],fill="#e1a77b")
                d.line([(8,21),(12,21)],fill="#97a6a3")
            elif not back:
                d.line([(near,12),(near,18)],fill="#dfc7a5")
                d.point((near,18),fill="#e3a261")
        if role == "summer":
            d.line([(near,12),(near-1,15),(near,19)],fill="#edb2a0")
            d.line([(near,20),(near+1,23)],fill="#edd0a3")
            d.point((near,9),fill="#d9ba9f")
    if role in ("clerk","police"):
        if front:
            d.line([(9,13),(10,16),(11,13)],fill="#aeb6ae" if role=="police" else "#c9bdac")
            d.line([(10,14),(10,19)],fill="#514653" if role=="police" else "#6e5260")
        if role == "police":
            d.line([(5,5),(14,5)],fill="#384967")
            d.line([(7,3),(12,3)],fill="#71859c")
            d.point((face,4),fill="#d1b18a")
            d.line([(8,20),(13,20)],fill="#292a35")
            if not back:
                arrow_mark(d,near,13,"#c9aa80")
            if back:
                d.line([(7,14),(12,14)],fill="#8697a8")
    if role in ("hardhat","metal","weld"):
        top = {"hardhat":"#b88b48","metal":"#777063","weld":"#5b5969"}[role]
        gleam = {"hardhat":"#d4aa65","metal":"#a2977e","weld":"#9290a1"}[role]
        d.polygon([(5,5),(6,3),(7,1),(12,1),(14,3),(15,5),(15,6),(5,6)],fill="#292631")
        d.polygon([(6,4),(8,2),(12,2),(14,4),(14,5),(6,5)],fill=top)
        d.line([(8,2),(11,2),(13,3)],fill=gleam)
        d.line([(6,5),(14,5)],fill=gleam)
        if role == "weld" and not back:
            d.line([(face-1,6),(face+2,6)],fill="#6f8390")
        if role == "hardhat":
            d.line([(7,13),(12,13)],fill="#c49a69")
            d.line([(7,18),(12,18)],fill="#8e7557")
    if role in ("walker","holiday") and not back:
        d.line([(face-2,7),(face+2,7)],fill="#423038")
        d.point((face+1,8),fill="#817179")
    if role == "walker":
        if front:
            d.point((9,9),fill="#c9ad94")
            d.line([(7,12),(9,13),(11,13)],fill="#a99084")
        x = 15 if direction in (0,2) else 4
        d.line([(x,21),(x,29),(x+1,31)],fill="#986d50")
        d.point((x-1,20),fill="#d5ab7a")
    if role in ("vendor","ice"):
        if not back:
            d.polygon([(8,13),(12,13),(12,19),(13,22),(7,22),(8,18)],fill="#a98c75" if role=="vendor" else "#a7a49a")
            d.line([(8,15),(11,15),(11,17)],fill="#dbbea0")
            d.line([(8,20),(11,20)],fill="#766466")
        else:
            d.line([(8,13),(12,19),(8,19),(12,14)],fill="#ab9682")
    if role in ("runner","coach"):
        d.line([(near,13),(near,18)],fill="#a7b5a8")
        d.point((near-1,20),fill="#d3bd97")
        if role == "coach":
            d.line([(face-2,5),(face+2,5)],fill="#a49e7f")
    if role in ("bandana","bomber","chain","iron","sweater"):
        if role == "bandana":
            d.line([(5,6),(14,6)],fill="#a44c52")
            d.point((13,8),fill="#c16d5c")
        elif role == "chain" and not back:
            d.line([(8,12),(10,13),(12,12)],fill="#d4b482")
            d.point((10,14),fill="#e3bd83")
        elif role == "bomber":
            d.line([(7,19),(13,19)],fill="#b47166")
            if back:
                arrow_mark(d,10,14,"#bb7464")
        elif role == "iron":
            d.line([(6,5),(14,5)],fill="#92918b")
            d.line([(7,17),(13,17)],fill="#898381")
        elif role == "sweater":
            d.line([(7,12),(10,13),(13,12)],fill="#ccaa98")
            d.point((near,11),fill="#e1bc92")


def cast_assets():
    native = Image.open(ROOT/"walker.png").convert("RGBA")
    hero = []
    for direction in range(4):
        row = []
        for frame in range(4):
            pose = native.crop((frame*24+2,direction*36,frame*24+22,direction*36+33))
            pose.putalpha(pose.getchannel("A").point(lambda alpha: 255 if alpha == 255 else 0))
            row.append(pose)
        hero.append(row)
    original_wardrobe, original_hair = studio.WARDROBE, studio.HAIR
    for number,(base,top,pants,skin,hair,role) in enumerate(CAST,7):
        wardrobe = list(original_wardrobe)
        wardrobe[base-1] = (top,pants,skin)
        haircuts = list(original_hair)
        haircuts[base-1] = hair
        studio.WARDROBE, studio.HAIR = wardrobe, haircuts
        poses = studio.pedestrian_rows(hero,base)
        for direction,frames in enumerate(poses):
            for frame in frames:
                costume(frame,role,direction)
        studio.save_indexed(studio.pack_people(poses),ROOT/f"pedestrian{number}.png")
    studio.WARDROBE, studio.HAIR = original_wardrobe, original_hair


def cruiser_asset():
    sedan = Image.open(ROOT/"sedan.png").convert("RGBA")
    atlas = Image.new("RGBA",sedan.size)
    for direction in range(4):
        frame = sedan.crop((direction*44,0,(direction+1)*44,44))
        d = ImageDraw.Draw(frame)
        if direction < 2:
            spots = [(x,y) for x in range(13,31) for y in range(14,27)]
        else:
            spots = [(x,y) for x in range(7,37) for y in range(20,26)]
        for x,y in spots:
            r,g,b,a = frame.getpixel((x,y))
            if a == 255 and 34 < r < 125 and b > r+5:
                d.point((x,y),fill="#c9bbac" if r > 59 else "#8d8f93")
        if direction >= 2:
            d.line([(13,22),(29,22)],fill="#394961")
            d.line([(17,23),(25,23)],fill="#586a80")
        else:
            d.line([(16,19),(27,19)],fill="#495a71")
        y = 14 if direction < 2 else 9
        d.rounded_rectangle((16,y,27,y+4),radius=1,fill="#242735")
        d.rectangle((17,y+1,20,y+2),fill="#b75d60")
        d.rectangle((23,y+1,26,y+2),fill="#758ca9")
        d.point((19,y+1),fill="#e4a08d")
        d.point((25,y+1),fill="#c4c9cb")
        d.rectangle((21,y+1,22,y+2),fill="#b5a999")
        atlas.paste(frame,(direction*44,0))
    studio.save_indexed(atlas,ROOT/"cruiser.png")


if __name__ == "__main__":
    cast_assets()
    cruiser_asset()
