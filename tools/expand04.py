"""Extend the authored Alpha 0.3 geometry in-place and encode 16×16 map chunks."""
from collections import deque
from struct import iter_unpack, pack
from world_art import new_art, add_tiles, generate, ROAD, SIDEWALK, BUILDING, SAND, WATER, PARKING, DECOR, BLOCKED
from art import ROOT, building

COLS, ROWS = 192, 176
BASE_W, BASE_H = 96, 88
RECORD = ">BhhbbBB"
SIZE = {0:(126,86),1:(137,85),2:(113,78),3:(141,99),4:(91,67),5:(72,57),
        16:(91,71),17:(157,102),18:(105,68),19:(102,71),25:(153,110),
        26:(109,79),27:(72,57),28:(91,67)}
# District blocks in local pixels. Existing city occupies the north-west four blocks.
BLOCKS = {
    (2,0): ((25,270,57),(16,59,74),(27,434,73),(16,635,75),(3,357,340),(16,43,581)),
    (3,0): ((19,29,64),(3,277,54),(19,642,53),(1,288,319),(26,46,318),(3,294,568),(26,617,579)),
    (2,1): ((18,28,58),(18,328,66),(19,629,58),(18,40,322),(18,625,341)),
    (3,1): ((18,42,61),(19,333,68),(18,600,61),(18,308,320),(2,616,344)),
    (0,2): ((3,26,58),(26,288,66),(19,642,64),(3,287,307),(27,435,336),(26,42,570)),
    (1,2): ((16,49,61),(16,282,74),(19,627,76),(16,29,306),(16,319,338),(16,624,298),(16,290,574)),
    (2,2): ((17,8,42),(17,276,51),(4,643,65),(17,287,306),(4,599,326),(17,13,572),(17,280,568)),
    (3,2): ((16,45,62),(1,296,59),(16,620,68),(19,28,315),(16,640,335),(16,295,562)),
    (0,3): ((17,11,60),(17,271,54),(17,597,64),(4,24,330),(17,307,323),(17,605,312),(17,8,569)),
    (1,3): ((4,41,65),(17,273,63),(4,647,67),(26,24,311),(17,284,311),(17,606,320),(4,291,573)),
    (2,3): ((19,33,62),(3,307,57),(26,622,69),(19,42,315),(26,315,340),(3,605,322),(19,293,575)),
    (3,3): ((16,49,55),(19,309,71),(16,615,81),(16,38,325),(16,293,310),(16,620,330),(16,297,560)),
}


def assets():
    warm = ("#a38a79","#685a62","#c1a290")
    cool = ("#697784","#414b5b","#8797a0")
    plum = ("#875b58","#60464e","#ab7366")
    hospital = building(3,153,110,"#5b6870",cool,"MEDICAL",None)
    hospital.rect(56,76,41,29,"#354451")
    hospital.rect(59,79,35,23,"#68868a")
    hospital.rect(60,81,33,2,"#9ba9a0")
    for x in (59,70,82,92):
        hospital.rect(x,79,2,24,"#465863")
    hospital.rect(53,104,47,3,"#a69890")
    hospital.rect(17,55,38,12,"#4d5a64")
    hospital.letters(21,58,"MEDICAL","#e0d3b0")
    hospital.rect(65,35,23,19,"#403b48")
    hospital.rect(67,37,19,15,"#965956")
    hospital.rect(74,39,5,11,"#e9d4b9")
    hospital.rect(71,42,11,5,"#e9d4b9")
    corner = building(2,109,79,"#675d69",warm,"ARCADE",("#4f6870","#6b8990","#a09985"))
    shop = building(5,72,57,"#615461",plum,"SUPPLY",None)
    garage = building(4,91,67,"#57636e",cool,None,None)
    garage.rect(27,36,37,9,"#343640")
    garage.letters(32,38,"GARAGE","#d8b794")
    for ident, art in enumerate((hospital,corner,shop,garage),25):
        art.save(f"prop{ident}")


def encode(path, tiles, zones, props):
    data = bytearray(pack(">BB",COLS,ROWS))
    for cy in range(0,ROWS,16):
        for cx in range(0,COLS,16):
            bits = mask = 0
            for y in range(cy,cy+16):
                for x in range(cx,cx+16):
                    n = y*COLS+x
                    bits |= (tiles[n] | (zones[n]<<6)) << mask
                    mask += 9
                    while mask >= 8:
                        data.append(bits&255)
                        bits >>= 8
                        mask -= 8
    assert len(data) == 2 + COLS*ROWS*9//8
    data += pack(">H",len(props))
    for prop in sorted(props,key=lambda p:p[2]+p[4]+p[6]):
        data += pack(RECORD,*prop)
    path.write_bytes(data)


def connected(tiles,zones,props,cols,rows):
    allowed = bytearray(z not in (WATER,BLOCKED) for z in zones)
    walls = [[] for _ in range(rows)]
    for _,x,y,l,t,w,h in props:
        if not w:
            continue
        x,y = x+l,y+t
        for row in range(max(0,(y-16)//16),min(rows,(y+h+16)//16+1)):
            walls[row].append((x,y,w,h))

    def clear(x,y,dx,dy):
        return all(x+dx+4 <= xx or x-4 >= xx+w or y+dy+3 <= yy or y-3 >= yy+h
                   for xx,yy,w,h in walls[y//16])

    for n,z in enumerate(allowed):
        if z and not clear(n%cols*16+8,n//cols*16+8,0,0):
            allowed[n] = 0
    start = 13*cols+14
    queue = deque([start])
    allowed[start] = 0
    count = 1
    while queue:
        n = queue.popleft()
        x,y = n%cols,n//cols
        for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
            if not (0<=x+dx<cols and 0<=y+dy<rows):
                continue
            nn = n + dx+dy*cols
            if allowed[nn] and clear(x*16+8,y*16+8,dx*16 if dx>0 else 0,dy*16 if dy>0 else 0) and (
                    (dx>=0 and dy>=0) or clear((x+dx)*16+8,(y+dy)*16+8,max(0,-dx)*16,max(0,-dy)*16)):
                allowed[nn] = 0
                queue.append(nn)
                count += 1
    return count


def expand():
    add_tiles()
    generate()
    assets()
    base = (ROOT/"city.bin").read_bytes()
    area = BASE_W*BASE_H
    old_tiles,old_zones=base[2:2+area],base[2+area:2+2*area]
    legacy = list(iter_unpack(RECORD,base[4+2*area:]))
    props = [(27 if k==5 and x==425 and y==89 else 28 if k==4 and x==633 and y==288 else k,x,y,l,t,w,h)
             for k,x,y,l,t,w,h in legacy]
    tiles,zones=bytearray(COLS*ROWS),bytearray(COLS*ROWS)
    for row in range(ROWS):
        for col in range(COLS):
            localx,localy=col%48,row%44
            zone_id=col//48,row//44
            if col<BASE_W and row<BASE_H:
                t,z=old_tiles[row*BASE_W+col],old_zones[row*BASE_W+col]
            else:
                vert=11<=localx<=15 or 32<=localx<=36
                horiz=11<=localy<=15 or 29<=localy<=33
                coast=zone_id[1]==1 and zone_id[0]>=2
                leafy=zone_id in ((3,0),(1,2),(3,2),(3,3))
                industrial=zone_id in ((2,2),(0,3),(1,3))
                if zone_id[1]==2 and zone_id[0]>=1 and localy<3:
                    t,z=44,WATER
                elif coast and localy>=34:
                    t,z=(53,SIDEWALK) if localy==34 else (40+(col+row)%4,SAND)
                    if localy>=39:
                        t,z=(48 if localy==39 else 44), (WATER if localy<43 else BLOCKED)
                elif vert or horiz:
                    t,z=(col+row)%4,ROAD
                    if vert and not horiz:
                        if localy in (10,16,28,34): t=10
                        elif localx in (13,34) and localy%2==0: t=17
                    if horiz and not vert:
                        if localx in (10,16,31,37): t=11
                        elif localy in (13,31) and localx%2==1: t=16
                elif 17<=localx<=27 and localy in (17,18,27,28):
                    t,z=54,PARKING
                elif leafy and 2<=localx%10<=7 and localy not in (10,16,28,34):
                    t,z=52,DECOR
                elif industrial and localy not in (10,16,28,34):
                    t,z=55,SIDEWALK
                else:
                    t,z=(4+(col+row)%4),SIDEWALK
            tiles[row*COLS+col],zones[row*COLS+col]=t,z

    def place(k,x,y,left,top,w,h,building=False):
        xx,yy=x+left,y+top
        if not w:
            props.append((k,x,y,left,top,w,h))
            return True
        cells=[r*COLS+c for r in range(yy//16,(yy+h-1)//16+1) for c in range(xx//16,(xx+w-1)//16+1)]
        if any(zones[n] in (ROAD,WATER,BLOCKED) for n in cells):
            return False
        if any(pw and xx<px+pl+pw and xx+w>px+pl and yy<py+pt+ph and yy+h>py+pt
               for pk,px,py,pl,pt,pw,ph in props):
            return False
        props.append((k,x,y,left,top,w,h))
        if building:
            for n in cells: zones[n]=BUILDING
        return True

    assert place(15,715,375,0,0,0,0)
    for row in (23,24):
        for col in (45,46,47):
            n=row*COLS+col
            if zones[n]==SIDEWALK:
                zones[n]=PARKING
                tiles[n]=54
    for (sx,sy), buildings in sorted(BLOCKS.items()):
        ox,oy=sx*768,sy*704
        for kind,x,y in buildings:
            w,h=SIZE[kind]
            assert place(kind,ox+x,oy+y,3,17,w-4,h-16,True),(sx,sy,kind,x,y)
        for k,x,y,l,t,w,h in ((6,137,106,13,33,16,12),(7,158,129,6,29,8,5),
            (13,496,145,5,19,5,5),(6,468,356,13,33,16,12),(7,479,410,6,29,8,5),
            (9,69,397,2,6,25,14),(10,273,390,0,9,44,7),(8,447,137,2,7,9,7),
            (12,629,426,3,11,11,7),(7,600,424,6,29,8,5),(15,274,273,0,0,0,0),
            (15,330,273,0,0,0,0),(6,134,569,13,33,16,12),(9,666,408,2,6,25,14),
            (11,465,652,1,4,15,11),(7,400,630,6,29,8,5),(6,465,63,13,33,16,12),
            (6,704,91,13,33,16,12),(7,265,115,6,29,8,5),(7,58,458,6,29,8,5)):
            place(k,ox+x,oy+y,l,t,w,h)
        if sy==1:
            for x in (45,177,297,438,619,715):
                place(21,ox+x,oy+548,17,43,12,5)
            for x in (94,221,360,477,680):
                place(22,ox+x,oy+576,15,23,8,5)
                place(23,ox+x+18,oy+597,5,10,21,8)
            place(24,ox+523,oy+550,9,49,43,10)
        if (sx,sy) in ((2,2),(0,3),(1,3)):
            for x in (22,120,293,353,429,620,686):
                place(20,ox+x,oy+423,3,6,45,15)
    reached=connected(tiles,zones,props,COLS,ROWS)
    previous=connected(old_tiles,old_zones,legacy,BASE_W,BASE_H)
    assert reached >= max(25344,previous*3),(previous,reached)
    encode(ROOT/"city04.bin",tiles,zones,props)
    print(f"{COLS}x{ROWS} tiles; {reached} connected passable centers, previously {previous}; {len(props)} props")


if __name__=="__main__":
    expand()
