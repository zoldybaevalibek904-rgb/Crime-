"""Author the four continuous city sectors from the Downtown source."""
from struct import iter_unpack, pack
from art import ROOT, Art, INK, building, scenery, downtown, terrain

ROAD, SIDEWALK, BUILDING, SAND, WATER, PARKING, DECOR, BLOCKED = range(8)
COLS, ROWS = 96, 88
DISTRICT_WIDTH, DISTRICT_HEIGHT = 48, 44
PROP = ">BhhbbBB"


def new_art():
    slate = ("#676570", "#444450", "#88808a")
    cream = ("#a57961", "#705451", "#c39473")
    brick = ("#77514e", "#563b42", "#a56a56")
    items = [
        building(0, 91, 71, "#71505a", cream, "COURT", None),
        building(4, 157, 102, "#586371", slate, None, None),
        building(2, 105, 68, "#59666e", cream, "CAFE", ("#3d686c", "#62938c", "#a8b48b")),
        building(1, 102, 71, "#655764", brick, "DELI", ("#744552", "#a76161", "#c17a69")),
    ]
    warehouse = items[1]
    for x in (24, 58, 92, 126):
        warehouse.rect(x, 29, 16, 21, "#33313d")
        warehouse.rect(x+2, 30, 12, 16, "#7a8190")
        warehouse.rect(x+2, 32, 12, 2, "#89909d")
        warehouse.rect(x+2, 43, 12, 3, "#556477")
    warehouse.letters(60, 76, "DEPOT", "#d6bc9b")
    a = Art(52,26)
    a.oval(3,18,49,8,(44,31,34,99))
    a.poly([(1,5),(7,1),(47,1),(50,4),(50,21),(1,21)],INK)
    a.rect(3,6,45,13,"#734951")
    a.rect(4,3,43,3,"#955c5c")
    for x in range(5,47,4):
        a.rect(x,7,1,11,"#a26b65")
        a.rect(x+1,8,1,10,"#5b3c46")
    a.rect(36,10,9,4,"#c0a383")
    a.line([(3,20),(47,20)],"#ad725f")
    items.append(a)
    a = Art(43,52)
    a.oval(17,42,24,8,(60,43,31,93))
    a.poly([(18,46),(24,46),(26,32),(24,20),(22,13),(18,13),(21,24),(21,34)],"#604537")
    for y in range(22,46,4):
        a.line([(21,y),(24,y-1)],"#b88757")
    for x,y,ex,ey in ((19,13,2,20),(20,12,4,6),(22,12,15,1),(22,12,32,1),
                      (22,14,41,8),(22,15,40,22),(21,16,31,30),(19,15,8,29)):
        mid = ((x+ex)//2,(y+ey)//2-5)
        a.poly([(x,y),mid,(ex,ey),(mid[0]+1,mid[1]+5),(x,y+4)],"#354c39")
        a.line([(x,y),mid,(ex,ey)],"#87934f")
        a.line([(x,y+2),(mid[0],mid[1]+2),(ex,ey)],"#627843")
    a.oval(18,12,7,6,"#98744a")
    items.append(a)
    a = Art(37,31)
    a.oval(9,23,27,7,(57,42,38,85))
    a.rect(17,12,3,15,"#644954")
    a.rect(17,12,1,14,"#d8ad86")
    a.poly([(0,12),(4,7),(10,3),(17,1),(25,3),(32,7),(36,13),(32,15),(5,15)],"#62424e")
    a.poly([(2,11),(12,3),(17,2),(13,12)],"#af625f")
    a.poly([(13,12),(17,2),(23,3),(25,12)],"#e2bd9a")
    a.poly([(25,12),(23,3),(29,6),(35,12)],"#ae6360")
    a.line([(4,13),(32,13)],"#d29480")
    items.append(a)
    a = Art(30,22)
    a.oval(4,14,24,7,(56,42,35,92))
    a.poly([(5,19),(1,7),(7,2),(19,4),(27,13),(24,19)],"#624d46")
    a.poly([(4,7),(8,4),(18,6),(24,13),(22,16),(8,14)],"#547981")
    for x,y in ((5,8),(8,11),(11,13),(15,13),(19,14)):
        a.line([(x,y),(x+8,y-2)],"#b7c2a0")
    a.line([(7,15),(6,19),(3,20)],"#b28460")
    a.line([(21,16),(20,20),(16,21)],"#b28460")
    items.append(a)
    a = Art(61,66)
    a.oval(14,53,43,12,(64,46,35,96))
    for x in (9,44):
        a.rect(x,30,5,30,"#564945")
        a.rect(x,31,2,27,"#b39069")
    for y in range(39,59,5):
        a.line([(28,y),(45,y-6)],"#9b795f")
        a.line([(28,y+1),(45,y-5)],"#5d5150")
    a.line([(26,59),(26,39),(44,31)],"#c6a078")
    a.rect(9,14,42,23,"#705758")
    a.rect(11,17,37,17,"#b88767")
    a.rect(15,18,27,11,"#34444b")
    a.rect(17,20,22,6,"#658385")
    a.rect(27,19,2,10,"#9d775f")
    a.poly([(2,16),(11,3),(45,3),(57,16),(53,19),(4,19)],"#704956")
    a.poly([(5,15),(13,5),(44,5),(54,15)],"#ba6d60")
    for x in range(14,46,6):
        a.line([(x,6),(x+2,14)],"#d08a73")
    a.letters(17,31,"GUARD","#f4cfa1")
    items.append(a)
    return items


def add_tiles():
    atlas = Art(128,112)
    for i in range(40):
        atlas.image.paste(terrain(i),(i%8*16,i//8*16))
    for i in range(40,56):
        if i <= 43:
            a = Art(16,16,("#c5a078","#c7a47d","#c9a67e","#ceae86")[i-40])
            for x,y in ((3,3),(9,8),(2,12),(12,14)):
                a.line([(x,y),(x+2,y),(x+3,y+1)],"#b88f6f")
                a.rect(x+1,y+1,2,1,"#d6b68d")
        elif i <= 47:
            a = Art(16,16,("#456e79","#487581","#46727e","#497682")[i-44])
            for x,y in ((1,3),(8,9),(3,14)):
                a.line([(x,y),(x+4,y)],"#52848a")
                a.rect(x+((i-44)%3),y,2,1,"#669196")
        elif i <= 51:
            a = Art(16,16,"#ad9279")
            wave = (10,9,8,9)[i-48]
            for x in range(16):
                y = wave - (1 if 4 <= x < 12 else 0)
                a.rect(x,y,1,16-y,"#53808a")
                a.rect(x,y,1,1,"#b7c1a2")
                if x % 5 != 3:
                    a.rect(x,y+1,1,1,"#82a6a2")
            a.line([(3,4),(8,4),(10,5)],"#cdb190")
        elif i == 52:
            a = Art(16,16,"#616b50")
            for x,y in ((4,4),(10,12),(13,3),(4,11)):
                a.poly([(x-2,y+1),(x,y-1),(x+2,y),(x+1,y+2)],"#76835a")
                a.rect(x,y,1,1,"#929064")
        elif i == 53:
            a = Art(16,16,"#9f816c")
            for y in (0,4,8,12):
                a.line([(0,y),(15,y)],"#6e594f")
                a.line([(1,y+1),(15,y+1)],"#bb9a7b")
                a.rect(4+(y%3)*4,y+1,1,3,"#806457")
        elif i == 54:
            a = Art(16,16,"#544a50")
            a.line([(0,0),(0,15)],"#665863")
            a.rect(10,1,2,1,"#84736e")
        else:
            a = Art(16,16,"#4b5453")
            a.line([(0,0),(15,0)],"#766e64")
            a.line([(2,12),(6,12),(9,13)],"#5b615c")
        atlas.image.paste(a.image,(i%8*16,i//8*16))
    atlas.save("tiles")


def generate():
    downtown()
    old = (ROOT/"downtown.bin").read_bytes()
    legacy = list(iter_unpack(PROP,old[4+48*44:]))
    tiles, zones = bytearray(COLS*ROWS), bytearray(COLS*ROWS)
    props = []
    road_tiles = {0,1,2,3,8,9,10,11,12,13,16,17}
    special = {16:(91,71),17:(157,102),18:(105,68),19:(102,71)}
    old_buildings = {0:(126,86),1:(137,85),2:(113,78),3:(141,99),4:(91,67),5:(72,57)}
    for y in range(ROWS):
        for x in range(COLS):
            dx, dy = x%48, y%44
            sector = x//48 + 2*(y//44)
            vert = (11 <= dx <= 15 or 32 <= dx <= 36)
            horiz = (11 <= dy <= 15 or 29 <= dy <= 33)
            if sector == 0:
                tile = old[2+dy*48+dx]
                zone = ROAD if tile in road_tiles else SIDEWALK
                # Through lane through the north blocks adds an alley connection.
                if dy == 4 and 16 <= dx <= 30:
                    tile = 19
            elif sector == 3 and dy >= 34:
                if dy >= 39:
                    tile, zone = (48 if dy == 39 else 44), (BLOCKED if dy == 43 else WATER)
                else:
                    tile, zone = (53 if dy == 34 else 40 + (x*3+y)%4), (SIDEWALK if dy == 34 else SAND)
            elif vert or horiz:
                tile, zone = ((x*3+y)%4), ROAD
                if vert and not horiz and dx in (13,34) and y%2:
                    tile = 17
                if horiz and not vert and dy in (13,31) and x%2:
                    tile = 16
                if horiz and not vert and dx in (10,16,31,37):
                    tile = 11
                if vert and not horiz and dy in (10,16,28,34):
                    tile = 10
            else:
                near_road = dx in (10,16,31,37) or dy in (10,16,28,34)
                lawn = sector == 1 and not near_road and (dx+dy)%11 < 7
                tile = 52 if lawn else (55 if sector == 2 and not near_road else 4+(x+y)%4)
                zone = DECOR if lawn else SIDEWALK
                if sector in (1,2,3) and (17 <= dx <= 25 and dy in (17,18)):
                    tile, zone = 54, PARKING
            if (sector == 0 and 16 <= dx <= 31 and dy in (2,3) or
                sector == 1 and 37 <= dx <= 44 and dy in (3,4) or
                sector == 2 and 16 <= dx <= 31 and dy in (27,28)):
                tile, zone = ((x+y)%4), ROAD
            if sector == 3 and 17 <= dx <= 27 and dy in (27,28):
                tile, zone = 54, PARKING
            tiles[y*COLS+x], zones[y*COLS+x] = tile, zone

    def cells(x,y,w,h):
        if x < 0 or y < 0 or x+w > COLS*16 or y+h > ROWS*16:
            return None
        return [row*COLS+col for row in range(y//16,(y+h-1)//16+1)
                for col in range(x//16,(x+w-1)//16+1)]

    def overlap(x,y,w,h,rect):
        k,px,py,dx,dy,pw,ph = rect
        return pw and x < px+dx+pw and x+w > px+dx and y < py+dy+ph and y+h > py+dy

    # Keep the historic walking tracks and the extended street-life spawn lanes clear.
    lanes = [(306,155,455,176),(306,256,485,278),(311,449,487,464),
             (36,151,130,174),(613,155,694,177),(615,443,716,463)]
    def blocked_lane(x,y,w,h):
        return any(x<b and x+w>a and y<d and y+h>c for a,c,b,d in lanes)

    def place(record, relocate=False):
        kind,x,y,dx,dy,w,h = record
        if not w:
            props.append(record)
            return True
        offsets = ((0,0),) if not relocate else [(0,0)] + [
            (ix,iy) for radius in range(8,145,8)
            for ix,iy in ((0,-radius),(0,radius),(-radius,0),(radius,0),
                          (-radius,-radius),(radius,-radius),(-radius,radius),(radius,radius))]
        for ox,oy in offsets:
            xx, yy = x+dx+ox,y+dy+oy
            touched = cells(xx,yy,w,h)
            if not touched or any(zones[i] in (ROAD,WATER,BLOCKED) for i in touched):
                continue
            if (ox or oy) and blocked_lane(xx,yy,w,h):
                continue
            if any(overlap(xx,yy,w,h,other) for other in props):
                continue
            props.append((kind,x+ox,y+oy,dx,dy,w,h))
            if kind in old_buildings or kind in special:
                for idx in touched:
                    zones[idx] = BUILDING
            return True
        return False

    for record in legacy:
        if record[0] in old_buildings:
            assert place(record), record
    moved = 0
    for record in legacy:
        if record[0] not in old_buildings:
            if not place(record):
                moved += 1
                assert place(record, True), record

    for sector, layouts in (
        (1, [(16,27,67),(16,267,57),(19,400,62),(16,630,66),
             (16,30,291),(16,291,330),(16,406,330),(19,632,336),
             (16,41,574),(16,285,585),(16,615,576)]),
        (2, [(17,7,53),(17,271,52),(5,433,103),(17,601,59),
             (17,10,300),(17,279,314),(4,621,315),
             (17,15,560),(17,304,572),(17,602,566)]),
        (3, [(18,22,68),(18,284,68),(18,615,76),
             (18,35,304),(18,291,311),(18,625,314)]),
    ):
        ox,oy=(sector%2)*768,(sector//2)*704
        for kind,x,y in layouts:
            w,h=(special if kind in special else old_buildings)[kind]
            assert place((kind,ox+x,oy+y,3,17,w-4,h-16)), (sector,kind,x,y)
        for kind,x,y,dx,dy,w,h in (
            (6,134,105,13,33,16,12),(7,157,128,6,29,8,5),
            (7,435,127,6,29,8,5),(13,494,151,5,19,5,5),
            (6,474,337,13,33,16,12),(7,278,426,6,29,8,5),
            (7,593,425,6,29,8,5),(8,420,418,2,7,9,7),
            (9,68,376,2,6,25,14),(10,295,383,0,9,44,7),
            (12,127,434,3,11,11,7),(13,152,429,5,19,5,5),
            (15,273,274,0,0,0,0),(15,329,274,0,0,0,0),
        ):
            place((kind,ox+x,oy+y,dx,dy,w,h), True)
    for x in (273,329,385):
        place((15,x+768,704+432,0,0,0,0))
    for x,y in ((18,404),(129,403),(280,398),(404,408),(620,394),(680,417),
                (18,570),(185,573),(334,572),(570,573),(689,574)):
        place((20,x,y+704,3,6,45,15), True)
    for x,y in ((47,548),(166,550),(279,551),(386,556),(499,551),(608,550),(718,555)):
        assert place((21,x+768,y+704,17,43,12,5), True)
    for x,y in ((91,578),(217,579),(343,580),(466,585),(572,572),(661,574)):
        assert place((22,x+768,y+704,15,23,8,5), True)
        assert place((23,x+786,y+709,5,10,21,8), True)
    assert place((24,1163,1254,9,49,43,10), True)

    props.sort(key=lambda p:p[2]+p[4]+p[6])
    (ROOT/"city.bin").write_bytes(pack(">BB",COLS,ROWS)+tiles+zones+pack(">H",len(props))
                                +b"".join(pack(PROP,*p) for p in props))
    print(f"City: {COLS}×{ROWS} tiles, {len(props)} props; {moved} legacy props relocated")


if __name__ == "__main__":
    add_tiles()
    for index,asset in enumerate(new_art(),16):
        asset.save(f"prop{index}")
    generate()
