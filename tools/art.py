"""Original pixel art and Downtown layout. Pillow is only needed to edit/regenerate art."""
from pathlib import Path
from struct import pack
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "res"
ROOT.mkdir(exist_ok=True)
INK = "#211d26"
PAPER = "#edc6a0"
PIXEL_FONT = {
    "A": (2,5,7,5,5), "B": (6,5,6,5,6), "C": (3,4,4,4,3),
    "D": (6,5,5,5,6), "E": (7,4,6,4,7), "F": (7,4,6,4,4),
    "G": (3,4,5,5,3), "H": (5,5,7,5,5), "I": (7,2,2,2,7),
    "K": (5,5,6,5,5), "L": (4,4,4,4,7), "M": (5,7,7,5,5),
    "N": (5,7,7,7,5), "O": (2,5,5,5,2), "P": (6,5,6,4,4),
    "R": (6,5,6,5,5), "S": (3,4,2,1,6), "T": (7,2,2,2,2),
    "U": (5,5,5,5,7), "V": (5,5,5,5,2), "W": (5,5,7,7,5),
    "Y": (5,5,2,2,2), " ": (0,0,0,0,0),
}


class Art:
    def __init__(self, w, h, color=None):
        self.image = Image.new("RGBA", (w, h), color)
        self.d = ImageDraw.Draw(self.image)

    def rect(self, x, y, w, h, color):
        self.d.rectangle((x, y, x+w-1, y+h-1), fill=color)

    def line(self, points, color):
        self.d.line(points, fill=color)

    def poly(self, points, color):
        self.d.polygon(points, fill=color)

    def oval(self, x, y, w, h, color):
        self.d.ellipse((x, y, x+w-1, y+h-1), fill=color)

    def letters(self, x, y, words, color, scale=1):
        for index, letter in enumerate(words):
            for row, mask in enumerate(PIXEL_FONT[letter]):
                for col in range(3):
                    if mask & (4 >> col):
                        self.rect(x+(index*4+col)*scale, y+row*scale, scale, scale, color)

    def save(self, name):
        self.image.save(ROOT / (name + ".png"), optimize=True)


def terrain(tile):
    road = tile < 4 or 8 <= tile <= 13 or tile in (16, 17, 19)
    a = Art(16, 16, "#403941" if road else "#9b8177")
    if road:
        for i, (x, y) in enumerate(((1,2),(9,6),(4,12),(12,15),(14,3),(0,9),(7,0),(10,11))):
            a.rect((x + tile*3) % 15, (y + tile) % 16, 1 + i % 3, 1,
                   "#4a4048" if i % 2 else "#38323c")
    else:
        a.line([(0, 0), (15, 0)], "#694f52")
        a.line([(0, 0), (0, 15)], "#694f52")
        a.line([(1, 1), (15, 1)], "#b59180")
        a.line([(1, 1), (1, 15)], "#ab897b")
        a.line([(5, 10), (7, 10), (8, 11)], "#8b716d")
        a.rect(2, 14, 12, 1, "#95776f")
        if tile in (5, 7):
            a.line([(12,12),(14,12),(15,13)], "#89716e")
        a.rect(9 + tile % 4, 4 + tile % 3, 3, 1, "#a98977")
    if tile == 8:
        a.rect(0, 14, 16, 1, "#c29459")
        a.rect(0, 11, 16, 1, "#a47650")
    if tile == 9:
        a.rect(13, 0, 1, 16, "#c29459")
        a.rect(10, 0, 1, 16, "#a47650")
    if tile == 10:
        for x in (2, 10):
            a.rect(x, 1, 4, 14, "#b6a096")
            a.rect(x, 14, 4, 1, "#87747c")
            a.rect(x + 1, 6 + x % 3, 2, 1, "#a48e85")
    if tile == 11:
        for y in (2, 10):
            a.rect(1, y, 14, 4, "#b6a096")
            a.rect(14, y, 1, 4, "#87747c")
            a.rect(6 + y % 3, y + 1, 1, 2, "#a48e85")
    if tile == 12:
        a.oval(3, 3, 11, 11, "#282530")
        a.oval(4, 4, 8, 8, "#5b5056")
        for x in (6, 8, 10):
            a.rect(x, 6, 1, 4, "#38323a")
    if tile == 13:
        a.rect(3, 2, 10, 8, "#77666b")
        a.rect(4, 3, 8, 6, "#25222b")
        for x in (5, 7, 9, 11):
            a.rect(x, 4, 1, 4, "#71636a")
    if tile == 16:
        a.rect(1, 13, 11, 2, "#b29b88")
    if tile == 17:
        a.rect(13, 1, 2, 11, "#b29b88")
    if tile == 18:
        a.rect(1, 2, 14, 13, "#786a61")
        a.rect(2, 3, 12, 11, "#586049")
        for x, y in ((3,4),(7,8),(9,4),(11,10),(5,11)):
            a.rect(x,y,2,1,"#72774b")
    if tile == 19:
        a.rect(0, 0, 16, 2, "#76646a")
        a.rect(0, 2, 16, 1, "#554851")
    if tile >= 24:
        edges = tile - 24
        for i, (x,y,w,h) in enumerate(((0,0,16,3),(0,13,16,3),(0,0,3,16),(13,0,3,16))):
            if edges & (1 << i):
                a.rect(x,y,w,h,"#c5aa95")
                a.rect(x + (w == 3 and x > 0)*2, y + (h == 3 and y > 0)*2,
                       1 if w == 3 else w, 1 if h == 3 else h,"#58434b")
    return a.image


def roof_unit(a, x, y):
    a.poly([(x+2,y+5),(x+22,y+5),(x+30,y+14),(x+6,y+16)], "#302936")
    a.rect(x, y, 17, 14, "#241e29")
    a.rect(x+1, y+1, 15, 11, "#99858a")
    a.rect(x+1, y+1, 15, 1, "#c6aba3")
    a.oval(x+4, y+2, 9, 9, "#3e3744")
    a.line([(x+5,y+4),(x+11,y+9)], "#6c6573")
    a.line([(x+7,y+9),(x+10,y+3)], "#91848f")
    a.rect(x+7, y+6, 2, 2, "#292535")
    a.rect(x+3, y+12, 11, 1, "#544755")


def glass(a, x, y, w, h, warm):
    a.rect(x-1, y-1, w+2, h+3, INK)
    a.rect(x, y, w, h, "#d18b53" if warm else "#526578")
    a.rect(x+1, y+1, w-2, h//2, "#f2b46d" if warm else "#627286")
    a.rect(x+1, y+h//2, w-2, h-h//2, "#9e5b48" if warm else "#425267")
    a.poly([(x+2,y+2),(x+5,y+2),(x+2,y+h-3)], "#ffce8b" if warm else "#85929c")
    a.rect(x+w//2, y, 1, h, "#3d3239")
    a.rect(x, y+h//2, w, 1, "#523d41")
    a.rect(x+w-2, y+1, 1, h-2, "#b57259" if warm else "#657487")
    a.rect(x-2, y+h+1, w+4, 2, "#ba9181")


def building(index, w, h, roof, brick, sign, canopy):
    a = Art(w+12, h+8)
    a.poly([(5,h-13),(w,h-15),(w+11,h-3),(w+8,h+5),(8,h+5)], (36,27,38,112))
    r = h-35
    a.rect(1, 4, w, h-3, INK)
    a.rect(3, r-1, w-4, 35, brick[0])
    for y in range(r+1, h-1, 4):
        a.rect(3,y,w-4,1,brick[1])
        for x in range(5+(y//4 % 2)*5,w-5,10):
            a.rect(x,y,1,4,brick[1])
            a.rect(x+1,y+1,6,1,brick[2])
    a.rect(3, r, 3, 32, brick[2])
    a.rect(w-5, r, 4, 34, brick[1])
    a.rect(3, 5, w-4, r-3, "#ad8a83")
    a.rect(5, 7, w-8, r-8, "#312a35")
    a.rect(8, 10, w-14, r-12, roof)
    for y in range(13, r-4, 9):
        a.rect(8, y, w-14, 1, "#524854")
        for x in range(14+(y//9 % 2)*8, w-8, 20):
            a.rect(x, y, 1, 9 if y+9 < r-2 else r-2-y, "#524854")
            a.rect(x+2, y+1, 5, 1, "#72606b")
    a.rect(4, r-3, w-5, 3, "#66505b")
    a.rect(3, r-4, w-4, 1, "#d0a595")
    a.rect(3, r-1, w-4, 2, "#ba9182")
    for x in range(10,w-7,11):
        a.rect(x, r-3, 1, 3, "#433641")
    roof_unit(a, 16, 14)
    if w > 110:
        roof_unit(a, w-39, r-23)
    else:
        a.rect(w-24, 15, 8, 8, "#332c39")
        a.rect(w-24, 15, 7, 2, "#97818b")
    if index == 4:
        for x in (14, w-40):
            a.rect(x-2,r+12,30,23,INK)
            a.rect(x,r+14,26,20,"#65636a")
            for y in range(r+15,h-1,3):
                a.rect(x,y,26,1,"#919096")
        return a
    for x in range(11, w-20, 23):
        glass(a,x,r+18,15,11,(x//23+index) % 3 != 2)
    x = w//2-7
    a.rect(x-2,r+16,19,19,INK)
    a.rect(x,r+17,15,17,"#4d4a52")
    a.rect(x+1,r+18,12,10,"#51696c")
    a.rect(x+2,r+19,10,3,"#c59669")
    a.rect(x+7,r+17,1,17,INK)
    a.rect(x+5,r+28,1,2,PAPER)
    a.rect(x+9,r+28,1,2,PAPER)
    a.rect(x-3,h-1,21,2,"#ad8c81")
    if canopy:
        a.rect(7,r+5,w-13,13,"#32222c")
        a.rect(8,r+4,w-15,10,canopy[0])
        for x in range(9,w-8,12):
            a.rect(x,r+5,5,8,canopy[1])
        a.rect(8,r+4,w-15,1,canopy[2])
        a.rect(8,r+14,w-15,2,canopy[2])
    if sign:
        scale = 2 if index in (1,2) else 1
        length = (len(sign)*4-1)*scale
        x = (w-length)//2
        a.rect(x-3,r+4,length+6,5*scale+3,"#392931")
        a.letters(x,r+5,sign,"#f1c99c",scale)
    return a


def scenery():
    brick = ("#865348","#603d3d","#a56a53")
    blue = ("#64616b","#46434f","#807783")
    ochre = ("#986b53","#634743","#b38567")
    arts = [
        building(0,126,86,"#675763",brick,"ROW HOUSE",None),
        building(1,137,85,"#62535c",brick,"MARKET",("#963e41","#b6524c","#d27861")),
        building(2,113,78,"#655261",ochre,"CORNER BAR",("#473f58","#795065","#aa7181")),
        building(3,141,99,"#635e6c",blue,"DOWNTOWN OFFICE",None),
        building(4,91,67,"#5b5563",ochre,None,None),
        building(5,72,57,"#6a5a62",brick,"SERVICE",None),
    ]
    a = Art(39,48)
    a.oval(8,37,30,10,(39,30,43,105))
    a.rect(13,32,16,13,"#3e3339")
    a.rect(14,33,14,11,"#a08072")
    a.rect(16,34,10,8,"#575541")
    a.poly([(18,24),(24,25),(23,37),(26,39),(17,40),(19,36)],"#563b36")
    a.line([(20,25),(20,35),(18,38)],"#af7551")
    a.poly([(3,20),(2,14),(6,12),(5,8),(11,7),(13,3),(19,4),(24,1),(28,5),(32,5),
            (33,10),(37,13),(35,17),(38,20),(33,25),(28,25),(24,30),(14,29),(12,26),(7,26)],
           "#303c35")
    for x,y in ((6,15),(11,9),(17,7),(23,5),(27,9),(26,15),(19,14),(13,18),(30,19),(21,21),(9,21)):
        a.poly([(x-3,y+5),(x-3,y+2),(x,y-1),(x+4,y-2),(x+8,y+1),(x+5,y+3),(x+3,y+6)],
               "#515d39" if y > 16 else "#697741")
        a.poly([(x-1,y+2),(x+2,y-1),(x+5,y),(x+7,y+1),(x+4,y+2),(x+2,y+4)],
               "#9c994c" if y < 16 else "#798649")
        a.rect(x+2,y,2,1,"#b3a755")
    arts.append(a)
    a = Art(22,37)
    a.oval(5,30,17,6,(42,31,42,110))
    a.rect(6,29,8,5,INK)
    a.rect(7,30,6,3,"#5b5257")
    a.rect(8,10,4,20,INK)
    a.rect(9,11,1,19,"#a58a7b")
    a.poly([(5,6),(14,6),(13,13),(11,16),(8,16),(6,13)],INK)
    a.poly([(7,7),(12,7),(12,12),(10,14),(8,12)],"#ed9c52")
    a.rect(8,7,3,5,"#ffe2a2")
    a.oval(4,2,12,6,INK)
    a.rect(8,3,4,2,"#96817e")
    arts.append(a)
    a = Art(15,16)
    a.oval(2,11,13,5,(42,31,42,110))
    a.poly([(2,4),(11,4),(10,13),(4,14),(2,11)],INK)
    a.rect(4,5,6,7,"#59716b")
    a.rect(5,6,1,5,"#91a088")
    a.oval(1,2,11,5,"#333940")
    a.oval(3,2,7,3,"#78847a")
    arts.append(a)
    a = Art(33,22)
    a.oval(7,15,25,7,(42,31,42,115))
    a.rect(2,5,25,15,INK)
    a.rect(4,8,21,10,"#496167")
    a.rect(5,10,18,1,"#72817e")
    a.poly([(1,6),(5,2),(24,2),(28,6),(26,9),(2,9)],"#263541")
    for x in range(5,25,4):
        a.rect(x,4,2,2,"#66787e")
    a.rect(14,12,7,3,"#a29b82")
    arts.append(a)
    a = Art(45,20)
    a.rect(0,14,44,4,(44,32,43,105))
    for x in range(1,43,4):
        a.rect(x,4,1,13,"#4c4651")
        a.rect(x+1,2,1,13,"#b18d7b")
    for y in (5,11):
        a.rect(1,y,43,2,"#6d5c65")
        a.rect(1,y,43,1,"#a58378")
    for x in (0,21,41):
        a.rect(x,1,3,17,"#433742")
        a.rect(x,1,3,2,"#c3a08c")
    arts.append(a)
    a = Art(20,17)
    a.oval(4,11,15,6,(42,31,42,110))
    a.rect(1,2,15,13,INK)
    a.rect(2,3,13,10,"#986344")
    a.rect(2,3,13,2,"#cea16c")
    a.rect(3,7,11,1,"#714735")
    a.line([(3,12),(13,5)],"#c48d59")
    a.rect(2,12,13,1,"#bc875a")
    arts.append(a)
    a = Art(18,20)
    a.oval(4,14,14,6,(42,31,42,100))
    a.poly([(3,11),(14,11),(12,18),(5,18)],"#8f5545")
    a.rect(3,11,11,2,"#c28865")
    for x,y in ((1,5),(5,2),(9,4),(12,6),(5,7)):
        a.poly([(8,13),(x,y+2),(x,y),(x+3,y),(x+4,y+4)],"#536d43")
        a.rect(x,y,2,1,"#a5a25a")
    arts.append(a)
    a = Art(15,26)
    a.oval(5,20,10,5,(42,31,42,110))
    a.rect(6,7,3,17,"#433947")
    a.rect(6,7,1,16,"#a88d83")
    a.poly([(4,0),(10,0),(13,3),(13,9),(10,12),(4,12),(1,9),(1,3)],"#5c353e")
    a.poly([(4,1),(10,1),(12,4),(12,8),(10,10),(4,10),(2,8),(2,4)],"#ab524a")
    a.rect(4,5,6,2,"#f3bc99")
    arts.append(a)
    a = Art(17,21)
    a.rect(2,5,13,14,INK)
    a.rect(3,6,11,11,"#a45046")
    a.rect(4,8,9,5,"#87928c")
    a.rect(5,9,6,2,"#c9baa6")
    a.rect(3,15,11,2,"#cf7560")
    arts.append(a)
    a = Art(56,26)
    for x in (0, 55):
        a.rect(x,0,1,26,"#b59c8f")
        a.rect(0 if x == 0 else 43,0,13,1,"#b59c8f")
        a.rect(0 if x == 0 else 43,25,13,1,"#8f7980")
    arts.append(a)
    return arts


def walker_frame(row, frame):
    a = Art(24,30)
    a.oval(4,22,17,7,(30,22,33,113))
    step = (0, -2, 0, 2, 0)[frame]
    passing = (0, 0, -1, 0, 1)[frame]
    profile = row >= 2
    back = row == 0
    if profile:
        for x, y, color in ((10-step-passing,23,"#344252"),(10+step+passing,25,"#57647a")):
            a.rect(x-1,18,5,y-16,INK)
            a.rect(x,19,3,y-18,color)
            a.rect(x,y,5,2,INK)
            a.rect(x,y,4,1,"#73717b")
        a.poly([(8,11),(14,10),(16,13),(15,20),(8,20),(7,16)],INK)
        a.rect(9,12,6,7,"#434651")
        a.rect(14,13,1,6,"#b6a497")
        arm = 10-step+passing
        a.rect(arm-1,13,4,7,INK)
        a.rect(arm,14,2,4,"#626071")
        a.rect(arm,18,2,2,"#b88060")
        a.poly([(8,4),(13,3),(16,5),(16,7),(17,8),(16,10),(13,12),(9,11),(8,8)],INK)
        a.poly([(11,5),(15,5),(15,7),(16,8),(15,10),(12,10),(10,8)],"#c8916c")
        a.rect(14,7,1,1,"#3b2b36")
        a.poly([(8,5),(10,3),(14,3),(16,5),(14,5),(12,6),(11,9),(9,10),(8,8)],"#292631")
        a.rect(10,4,4,1,"#51434d")
        return a.image if row == 3 else a.image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    for x, shift in ((8,step+passing),(13,-step-passing)):
        y = 25+shift
        a.rect(x-1,18,6,y-17,INK)
        a.rect(x,19,4,y-18,"#44536b")
        a.rect(x,20,1,max(1,y-20),"#667189")
        a.rect(x-1,y,6,2,INK)
        a.rect(x,y,4,1,"#73717b")
    a.poly([(9,10),(15,10),(18,13),(17,19),(7,19),(6,14)],INK)
    a.rect(8,12,8,6,"#42444f")
    a.rect(8,12,2,5,"#5e5b68")
    if not back:
        a.rect(11,11,3,7,"#d9c5ad")
        a.rect(13,15,1,3,"#a99083")
    else:
        a.rect(10,12,5,1,"#64616d")
    for x, swing in ((5,-step),(17,step)):
        y = 15+swing//2
        a.rect(x,y-2,3,7,INK)
        a.rect(x+1,y-1,2,4,"#545460")
        a.rect(x+1,y+3,2,2,"#bd8662")
    a.poly([(7,6),(9,3),(14,2),(17,4),(18,7),(16,10),(14,12),(10,12),(8,10)],INK)
    if not back:
        a.poly([(9,6),(15,5),(16,7),(16,9),(14,11),(10,11),(9,9)],"#cb9168")
        a.rect(10,7,1,1,"#4a3439")
        a.rect(14,7,1,1,"#4a3439")
        a.rect(11,10,3,1,"#9e6559")
        a.poly([(8,6),(10,3),(14,3),(17,5),(17,7),(15,6),(13,5),(10,7)],"#302830")
    else:
        a.poly([(8,6),(10,3),(14,3),(17,5),(17,8),(15,10),(10,10),(8,8)],"#302830")
        a.rect(11,10,4,1,"#aa775e")
    a.line([(10,4),(13,3),(15,4)],"#55434c")
    return a.image


def car_frame(row):
    a = Art(44,44)
    horizontal = row >= 2
    if horizontal:
        a.oval(2,17,41,16,(29,24,33,132))
        a.poly([(2,17),(5,14),(11,13),(16,10),(28,10),(33,14),(39,15),(42,18),
                (42,25),(39,29),(4,29),(2,26)],INK)
        a.poly([(3,18),(6,15),(13,14),(17,11),(27,11),(33,15),(39,16),(41,18),
                (41,25),(38,27),(5,27),(3,25)],"#555c73")
        a.poly([(13,15),(17,11),(27,11),(32,15),(34,20),(10,20)],"#272a3a")
        a.poly([(17,12),(26,12),(29,15),(28,18),(15,18),(14,16)],"#727b96")
        a.line([(15,15),(17,12),(26,12)],"#a2a4b2")
        a.poly([(29,13),(32,16),(33,19),(29,19),(28,17)],"#424b61")
        a.poly([(13,16),(15,19),(11,19)],"#56627b")
        a.rect(4,20,36,1,"#9392a2")
        a.rect(4,25,37,1,"#323343")
        for x in (13,22,31):
            a.rect(x,21,1,4,"#2e3245")
        for x in (15,24):
            a.rect(x,22,3,1,"#a3a0aa")
        a.rect(39,21,2,2,"#f1cf94")
        a.rect(3,22,2,2,"#cb6258")
        for x in (7,30):
            a.oval(x,24,8,8,INK)
            a.oval(x+2,26,4,4,"#6c6571")
            a.rect(x+3,27,2,1,"#a29597")
        return a.image if row == 3 else a.image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    a.oval(13,4,19,39,(29,24,33,132))
    for x in (12,27):
        for y in (8,28):
            a.rect(x,y,5,8,INK)
    a.poly([(15,6),(18,3),(26,3),(30,7),(30,36),(27,41),(17,41),(13,37),(13,9)],INK)
    a.poly([(16,7),(18,5),(26,5),(29,8),(29,36),(26,39),(18,39),(14,36),(14,9)],"#515b73")
    a.rect(15,11,1,23,"#9b9aa9")
    a.rect(28,10,1,23,"#777e96")
    a.poly([(16,12),(27,12),(28,16),(27,30),(16,30),(15,26),(15,16)],"#292d40")
    y = 18 if row == 0 else 15
    a.rect(17,y,10,11,"#747f96")
    a.rect(17,y,10,1,"#a0a4b4")
    a.rect(17,y+1,1,9,"#9197ad")
    a.rect(17,13,10,3,"#465169")
    a.rect(17,31,10,2,"#81849b")
    for x in (15,26):
        a.rect(x,6,3,2,"#ffe0a1" if row == 0 else "#c75f55")
        a.rect(x,36,3,2,"#c75f55" if row == 0 else "#ffe0a1")
    a.rect(20,38,4,1,"#a4a5ad")
    return a.image


def pedestrian_frame(model, row, frame):
    source = walker_frame(row, frame)
    art = Art(24,30)
    profile = row >= 2
    left = row == 2

    def xside(x):
        return 23-x if left else x

    def shape(points, color):
        art.poly([(xside(x),y) for x,y in points],color)

    colors = {
        1: {"#42444f":"#694939","#434651":"#694939","#5e5b68":"#916750",
            "#545460":"#845940","#626071":"#986b51","#44536b":"#413940",
            "#57647a":"#4c4147","#344252":"#342d35","#667189":"#625056",
            "#73717b":"#998378"},
        2: {"#42444f":"#8d8483","#434651":"#8d8483","#5e5b68":"#b5a196",
            "#545460":"#98908c","#626071":"#b4a398","#64616d":"#bca99b",
            "#d9c5ad":"#ae9e97","#b6a497":"#c8b3a1","#a99083":"#776b71"},
        3: {"#42444f":"#873c3e","#434651":"#873c3e","#5e5b68":"#aa574d",
            "#545460":"#934847","#626071":"#aa574d","#64616d":"#a0534c",
            "#d9c5ad":"#9b4543","#a99083":"#6e303b","#b6a497":"#b56155",
            "#44536b":"#bf8667","#57647a":"#c98d70","#344252":"#a2695b",
            "#667189":"#d89e79","#73717b":"#dec4ab"}
    }[model]
    palette = {tuple(bytes.fromhex(k[1:])) + (255,): tuple(bytes.fromhex(v[1:])) + (255,)
               for k,v in colors.items()}
    source.putdata([palette.get(pixel,pixel) for pixel in source.get_flattened_data()])
    art.image.paste(source)

    if model == 1:
        if profile:
            shape([(8,6),(8,4),(10,2),(14,2),(16,4),(16,6),(19,7),(19,8),(13,8),(8,7)],INK)
            shape([(9,4),(11,3),(14,3),(15,5),(11,5),(9,6)],"#70656a")
            shape([(9,6),(14,5),(18,7),(14,7),(10,7)],"#49505d")
        else:
            art.poly([(7,7),(7,5),(9,2),(15,2),(17,4),(18,7),(16,9),(8,9)],INK)
            art.poly([(8,6),(10,3),(14,3),(16,5),(16,6),(9,7)],"#67646b")
            art.rect(10,3,4,1,"#92817a")
            art.rect(8 if row == 1 else 9,7,9 if row == 1 else 7,1,"#46434e")
    elif model == 2:
        if profile:
            shape([(7,10),(9,9),(10,12),(14,12),(13,14),(9,13),(7,12)],"#5b525a")
            shape([(8,10),(10,11),(12,12),(10,12),(8,11)],"#c0ac9d")
        elif row == 0:
            art.poly([(8,10),(10,12),(15,12),(17,10),(16,14),(12,16),(8,13)],"#564f59")
            art.poly([(9,11),(11,13),(15,12),(14,14),(12,15),(10,13)],"#ac9c94")
        else:
            art.rect(9,11,7,1,"#554950")
            for x in (10,14):
                art.rect(x,12,1,4,"#dfc5ad")
    elif profile:
        shape([(8,5),(10,6),(10,10),(12,13),(11,16),(8,15),(6,13),(6,8)],INK)
        shape([(8,7),(9,8),(9,12),(10,15),(8,14),(7,12),(7,9)],"#4e383e")
        shape([(8,18),(15,18),(15,21),(9,21)],"#333543")
        shape([(9,19),(14,19),(14,20),(9,20)],"#525467")
    else:
        art.poly([(8,5),(9,9),(10,13),(9,16),(6,16),(6,12),(7,8)],INK)
        art.poly([(16,5),(17,8),(18,12),(18,16),(15,16),(14,12),(15,9)],INK)
        art.line([(8,8),(7,12),(7,14)],"#513740")
        art.line([(16,8),(17,12),(17,14)],"#513740")
        if row == 0:
            art.poly([(9,4),(14,4),(16,8),(16,13),(14,15),(10,15),(8,12),(8,8)],"#382c35")
            art.line([(10,6),(10,11),(11,13)],"#5e4448")
        art.rect(8,18,9,4,"#323440")
        art.rect(9,19,7,1,"#545569")
    return art.image


def taxi_frame(row):
    art = Art(48,48)
    source = car_frame(row)
    colors = {
        "555c73":"a47343","515b73":"b48045","727b96":"d4a75c","747f96":"d4a75c",
        "a2a4b2":"ebc882","a0a4b4":"ebc882","9197ad":"c89146","9392a2":"efbc68",
        "9b9aa9":"e1ab60","777e96":"a46c38","81849b":"d6a456","a3a0aa":"e1ac64"
    }
    palette = {tuple(bytes.fromhex(k))+(255,): tuple(bytes.fromhex(v))+(255,)
               for k,v in colors.items()}
    source.putdata([palette.get(pixel,pixel) for pixel in source.get_flattened_data()])
    art.image.paste(source,(2,2))
    if row >= 2:
        for x in range(8,40):
            art.rect(x,25+(x%2),1,1,"#3c3034")
        y = 12
    else:
        for i in range(11):
            art.rect(17+i,34+i%2,1,1,"#493536")
        y = 21
    art.rect(15,y,17,8,INK)
    art.rect(16,y+1,15,6,"#e1be81")
    # Four tiny roof letters are fixed to the camera and remain readable from every heading.
    for col, letter in enumerate(((7,2,2,2,2),(2,5,7,5,5),(5,5,2,5,5),(7,2,2,2,7))):
        for dy, mask in enumerate(letter):
            for dx in range(3):
                if mask & (4 >> dx):
                    art.rect(16+col*4+dx,y+1+dy,1,1,"#423034")
    return art.image


def utility_frame(row, van):
    a = Art(48,48)
    body, light, dark = (("#9e938d","#caafa0","#796d6d") if van
                         else ("#59535a","#82747a","#403b46"))
    roof = "#cdc0ad" if van else "#635a62"
    if row >= 2:
        a.oval(1,17,46,17,(28,24,33,130))
        back = 9 if van else 13
        a.poly([(1,back+3),(4,back),(24,back),(30,12),(35,15),(43,16),(46,19),
                (46,27),(43,30),(4,30),(1,27)],INK)
        a.poly([(2,back+4),(5,back+1),(24,back+1),(29,13),(34,16),(43,17),(45,20),
                (45,26),(42,28),(4,28),(2,26)],body)
        a.poly([(5,back+1),(24,back+1),(29,13),(31,16),(5,16)],roof)
        a.line([(5,back+1),(24,back+1),(28,12 if van else 14)],light)
        a.poly([(25,12 if van else 14),(28,13),(34,17),(30,20),(26,17)],"#333642")
        a.line([(28,14),(31,17),(30,18)],"#62707d")
        a.rect(8,18,17,5,dark if van else "#292a36")
        if not van:
            a.rect(15,18,2,5,body)
            a.rect(7,15,14,1,"#736972")
        else:
            a.rect(7,12,15,1,"#b3a297")
            a.rect(7,14,15,1,"#ac9d91")
        a.poly([(26,18),(29,20),(28,23),(25,23),(25,19)],"#2e303d")
        a.line([(4,24),(41,24),(44,22)],light)
        a.line([(5,27),(43,27)],dark)
        for x in (8,25,32):
            a.rect(x,23,1,4,dark)
        a.rect(27,25,3,1,light)
        a.rect(43,20,2,3,"#efd09b")
        a.rect(2,21,2,4,"#a54e48")
        for x in (6,32):
            a.oval(x,24,10,10,INK)
            a.oval(x+2,26,6,6,"#514a58")
            a.oval(x+3,27,4,4,"#82737b")
            a.rect(x+4,28,2,1,"#aa998e")
        return a.image if row == 3 else a.image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    a.oval(11,3,26,44,(28,24,33,130))
    for x in (12,30):
        for y in (7,32):
            a.rect(x,y,6,9,INK)
    a.poly([(12,7),(15,3),(31,3),(35,7),(35,41),(31,45),(16,45),(12,41)],INK)
    a.poly([(13,8),(16,5),(31,5),(34,8),(34,40),(31,43),(16,43),(13,40)],body)
    a.rect(14,9,1,28,light)
    a.rect(32,9,1,28,dark)
    upper = row == 0
    top = 17 if upper else 9
    a.rect(16,top,16,22,roof)
    a.rect(16,top,16,1,light)
    for x in (18,22,27,30):
        a.rect(x,top+3,1,14,light if not van else "#b4a397")
    win = 11 if upper else 32
    a.poly([(16,win),(31,win),(32,win+4),(29,win+6),(18,win+6),(15,win+4)],"#303442")
    a.line([(17,win+1),(29,win+1)],"#65717e")
    if van:
        a.rect(23,39 if upper else 6,1,4,dark)
    elif upper:
        a.oval(20,37,8,6,INK)
        a.oval(22,38,4,4,dark)
    else:
        a.oval(20,4,8,6,INK)
        a.oval(22,5,4,4,dark)
    for x in (15,29):
        a.rect(x,7,3,2,"#f6d7a0" if upper else "#a84d4c")
        a.rect(x,39,3,2,"#a84d4c" if upper else "#f6d7a0")
    a.rect(21,42,6,1,light)
    return a.image


def downtown():
    cols, rows = 48, 44
    vertical = range(11,16), range(32,37)
    horizontal = range(11,16), range(29,34)

    def road(x,y):
        return any(x in strip for strip in vertical) or any(y in strip for strip in horizontal)

    tiles = []
    for y in range(rows):
        for x in range(cols):
            tile = 4 + (x*3 + y*7) % 4
            if road(x,y):
                tile = (x*3+y*7)%4
                vx = next((strip for strip in vertical if x in strip), None)
                hy = next((strip for strip in horizontal if y in strip), None)
                if vx and not hy:
                    if y in (10,16,28,34): tile = 10
                    elif x == vx.start+2: tile = 9 if vx.start == 11 else 17 if y%2 == 0 else tile
                    elif x == vx.start+4 and y%7 == 1: tile = 13
                if hy and not vx:
                    if x in (10,16,31,37): tile = 11
                    elif y == hy.start+2: tile = 8 if hy.start == 11 else 16 if x%2 == 0 else tile
                    elif y == hy.start+4 and x%9 == 1: tile = 12
            else:
                mask = sum(1 << bit for bit,(dx,dy) in enumerate(((0,-1),(0,1),(-1,0),(1,0)))
                           if road(x+dx,y+dy))
                if mask:
                    tile = 24+mask
                elif (x in (18,28) and 18 <= y <= 26) or (38 <= x <= 45 and y in (17,27)):
                    tile = 18
                elif (y == 4 and 16 <= x <= 31) or (26 <= x <= 27 and 5 <= y <= 10) or (y == 27 and 17 <= x <= 30) or (y == 40 and 38 <= x <= 46):
                    tile = 19
            tiles.append(tile)

    props = []
    def add(kind,x,y,left,top,width,height):
        props.append((kind,x,y,left,top,width,height))
    def house(kind,x,y,w,h):
        add(kind,x,y,3,17,w-4,h-16)

    house(0,21,62,126,86)
    house(1,256,76,137,85)
    house(5,425,89,72,57)
    house(3,606,49,141,99)
    house(2,28,270,113,78)
    house(0,28,361,126,86)
    house(3,306,311,141,99)
    house(4,633,288,91,67)
    house(5,625,380,72,57)
    house(4,47,576,91,67)
    house(2,299,565,113,78)
    house(0,610,567,126,86)
    for x,y in ((128,113),(413,114),(595,115),(122,271),(264,271),(463,372),
                (131,520),(276,524),(445,542),(596,528),(704,377),(35,5),
                (475,53),(707,513),(42,411),(360,391),(622,253)):
        add(6,x,y,13,33,16,12)
    for x,y in ((145,140),(277,132),(464,131),(593,131),(146,256),(280,246),
                (472,246),(599,249),(145,492),(279,490),(475,486),(602,492)):
        add(7,x,y,6,29,8,5)
    for x,y in ((266,151),(127,352),(450,389),(692,353),(462,156)):
        add(8,x,y,2,7,9,7)
    for x,y in ((74,3),(420,88),(56,351),(459,336),(705,366),(73,660)):
        add(9,x,y,2,6,25,14)
    for x,y in ((305,271),(347,271),(389,271),(78,553),(635,365),(677,365),(437,627)):
        add(10,x,y,0,9,44,7)
    for x,y in ((409,143),(435,149),(80,352),(451,351),(638,357),(475,612)):
        add(11,x,y,1,4,15,11)
    for x,y in ((111,341),(291,149),(371,148),(309,405),(447,401),(727,142),(427,622)):
        add(12,x,y,3,11,11,7)
    for x,y in ((161,141),(260,139),(495,137),(160,250),(261,247),(494,250),(599,454)):
        add(13,x,y,5,19,5,5)
    for x,y in ((376,150),(97,341),(20,147),(707,143)):
        add(14,x,y,2,7,13,12)
    for x,y in ((239,216),(295,216),(351,216),(608,422),(664,422),(72,500)):
        add(15,x,y,0,0,0,0)
    props.sort(key=lambda prop: prop[2]+prop[4]+prop[6])
    payload = pack(">BB",cols,rows)+bytes(tiles)+pack(">H",len(props))
    payload += b"".join(pack(">BhhbbBB",*prop) for prop in props)
    (ROOT / "downtown.bin").write_bytes(payload)


if __name__ == "__main__":
    tiles = Art(128,80)
    for tile in range(40):
        tiles.image.paste(terrain(tile),(tile%8*16,tile//8*16))
    tiles.save("tiles")
    for index, art in enumerate(scenery()):
        art.save(f"prop{index}")
    walker = Art(120,120)
    sedan = Art(176,44)
    for row in range(4):
        for frame in range(5):
            walker.image.paste(walker_frame(row,frame),(frame*24,row*30))
        sedan.image.paste(car_frame(row),(row*44,0))
    walker.save("walker")
    sedan.save("sedan")
    traffic = Art(192,144)
    for row in range(4):
        for model, frame in enumerate((taxi_frame(row), utility_frame(row, False), utility_frame(row, True))):
            traffic.image.paste(frame,(row*48,model*48))
    traffic.save("traffic")
    for model in range(1,4):
        pedestrian = Art(120,120)
        for row in range(4):
            for frame in range(5):
                pedestrian.image.paste(pedestrian_frame(model,row,frame),(frame*24,row*30))
        pedestrian.save(f"pedestrian{model}")
    downtown()
