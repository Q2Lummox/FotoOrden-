"""
Genera una galería de ejemplo para sacar capturas de la app en un emulador.

  python3 tools/demo_media.py <carpeta_salida>

Crea la misma estructura de carpetas que un móvil (DCIM/Camera, WhatsApp...).
Fotos de Lorem Picsum (libres), caras generadas por IA (no son personas reales),
documentos dibujados aquí, y copias casi iguales / borrosas para probar la limpieza.
"""
import io
import os
import random
import sys
import urllib.request
from datetime import datetime, timedelta

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "media"
random.seed(7)

CAMERA = "DCIM/Camera"
VACACIONES = "Pictures/Vacaciones Asturias"
WHATSAPP = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"

LANDSCAPES = [10, 11, 15, 16, 17, 28, 29, 1015, 1016, 1018, 1036, 1039, 1043, 1044]
FOOD = [1080, 102, 292, 425, 312, 431]
PETS = [237, 1025, 40, 593, 169]
MISC = [0, 1, 20, 26, 48, 60, 119, 180, 201, 250]

UA = {"User-Agent": "Mozilla/5.0 FotoOrden-demo"}


def fetch(url):
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=30) as r:
            return Image.open(io.BytesIO(r.read())).convert("RGB")
    except Exception as e:  # noqa: BLE001
        print("  fallo", url, e)
        return None


def picsum(pid, w=1200, h=900):
    return fetch(f"https://picsum.photos/id/{pid}/{w}/{h}")


def fake_face():
    return fetch("https://thispersondoesnotexist.com/")


def font(size, bold=False):
    names = ["DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"]
    for d in ["/usr/share/fonts/truetype/dejavu", "/usr/share/fonts/dejavu"]:
        for n in names:
            p = os.path.join(d, n)
            if os.path.exists(p):
                return ImageFont.truetype(p, size)
    return ImageFont.load_default(size=size)


def receipt():
    img = Image.new("RGB", (900, 1300), (246, 244, 238))
    d = ImageDraw.Draw(img)
    y = 70
    d.text((450, y), "SUPERMERCADO LA PLAZA", font=font(46, True), fill=(30, 30, 30), anchor="mt"); y += 80
    d.text((450, y), "C/ Mayor 12 · Oviedo · CIF B33123456", font=font(26), fill=(60, 60, 60), anchor="mt"); y += 70
    d.text((80, y), "Fecha: 03/11/2026   Hora: 18:42", font=font(30), fill=(30, 30, 30)); y += 70
    items = [("Leche entera 1L x6", "6,54"), ("Pan de molde", "1,85"), ("Tomates 1 kg", "2,39"),
             ("Aceite de oliva 1L", "8,95"), ("Café molido 250 g", "3,20"), ("Manzanas golden", "2,10"),
             ("Yogur natural x8", "2,45"), ("Detergente", "5,99")]
    for name, price in items:
        d.text((80, y), name, font=font(32), fill=(30, 30, 30))
        d.text((820, y), price, font=font(32), fill=(30, 30, 30), anchor="ra")
        y += 56
    d.line((80, y + 10, 820, y + 10), fill=(80, 80, 80), width=3); y += 40
    d.text((80, y), "TOTAL", font=font(42, True), fill=(20, 20, 20))
    d.text((820, y), "33,47 EUR", font=font(42, True), fill=(20, 20, 20), anchor="ra"); y += 90
    d.text((450, y), "Gracias por su compra", font=font(30), fill=(60, 60, 60), anchor="mt")
    bg = Image.new("RGB", (1200, 1600), (120, 98, 80))
    bg.paste(img.rotate(2, expand=True, fillcolor=(120, 98, 80)), (130, 110))
    return bg


def notes():
    img = Image.new("RGB", (1200, 1600), (252, 252, 248))
    d = ImageDraw.Draw(img)
    for yy in range(160, 1600, 64):
        d.line((0, yy, 1200, yy), fill=(190, 210, 235), width=2)
    d.text((90, 70), "Apuntes - Tema 4: La fotosíntesis", font=font(48, True), fill=(20, 40, 120))
    lines = [
        "1. Fase luminosa: ocurre en los tilacoides.",
        "   La clorofila capta la energía de la luz.",
        "2. Se rompe la molécula de agua y se libera O2.",
        "3. Se produce ATP y NADPH.",
        "4. Fase oscura (ciclo de Calvin) en el estroma.",
        "   El CO2 se fija y se forma glucosa.",
        "Importante para el examen del jueves:",
        "   - Ecuación global de la fotosíntesis",
        "   - Diferencias entre fase luminosa y oscura",
    ]
    y = 175
    for l in lines:
        d.text((90, y), l, font=font(38), fill=(30, 30, 60))
        y += 64
    return img


def ticket_tren():
    img = Image.new("RGB", (1400, 800), (235, 240, 245))
    d = ImageDraw.Draw(img)
    d.rectangle((40, 40, 1360, 760), outline=(40, 90, 160), width=6)
    d.text((80, 80), "BILLETE DE TREN", font=font(56, True), fill=(40, 90, 160))
    d.text((80, 190), "Origen: Oviedo         Destino: Madrid Chamartín", font=font(40), fill=(20, 20, 20))
    d.text((80, 260), "Fecha: 14/12/2026      Salida: 08:15      Llegada: 12:47", font=font(40), fill=(20, 20, 20))
    d.text((80, 330), "Coche 6   Plaza 12A   Tarifa: Básica", font=font(40), fill=(20, 20, 20))
    d.text((80, 400), "Localizador: X7K2PQ   Precio total: 42,60 EUR", font=font(40), fill=(20, 20, 20))
    for i in range(30):
        x = 900 + i * 14
        d.rectangle((x, 520, x + random.choice([4, 7, 9]), 700), fill=(10, 10, 10))
    return img


def meme():
    img = Image.new("RGB", (1080, 1080), (255, 214, 102))
    d = ImageDraw.Draw(img)
    d.text((540, 380), "¡BUENOS DÍAS!", font=font(110, True), fill=(120, 40, 20), anchor="mm")
    d.text((540, 560), "Feliz lunes a todos", font=font(70), fill=(120, 40, 20), anchor="mm")
    d.text((540, 700), "que tengáis un gran día", font=font(54), fill=(120, 40, 20), anchor="mm")
    return img


def synthetic(i):
    """Si no hay internet: un degradado con formas, para que no falte nada."""
    img = Image.new("RGB", (1200, 900))
    d = ImageDraw.Draw(img)
    c1 = [random.randint(40, 200) for _ in range(3)]
    for y in range(900):
        t = y / 900
        d.line((0, y, 1200, y), fill=tuple(int(c * (1 - t) + 230 * t) for c in c1))
    for _ in range(8):
        x, y, r = random.randint(0, 1200), random.randint(0, 900), random.randint(40, 200)
        d.ellipse((x - r, y - r, x + r, y + r), fill=tuple(random.randint(0, 255) for _ in range(3)))
    return img


counter = [0]


def save(img, folder, when, name=None, fmt="JPEG"):
    path = os.path.join(OUT, folder)
    os.makedirs(path, exist_ok=True)
    counter[0] += 1
    if name is None:
        name = when.strftime("IMG_%Y%m%d_%H%M%S") + ".jpg"
    stamp = when.strftime("%Y:%m:%d %H:%M:%S")
    exif = Image.Exif()
    exif[0x0110] = "Pixel 8"
    exif[0x0132] = stamp
    ifd = exif.get_ifd(0x8769)
    ifd[0x9003] = stamp
    ifd[0x9004] = stamp
    full = os.path.join(path, name)
    if fmt == "PNG":
        img.save(full, "PNG")
    else:
        img.save(full, "JPEG", quality=88, exif=exif)
    # El emulador usa la fecha del fichero si no hay EXIF
    ts = when.timestamp()
    os.utime(full, (ts, ts))
    return full


def when(day_offset, hour=None):
    base = datetime(2026, 11, 7, 20, 0, 0)
    h = hour if hour is not None else random.randint(9, 21)
    return (base - timedelta(days=day_offset)).replace(hour=h, minute=random.randint(0, 59), second=random.randint(0, 59))


def main():
    got = {}

    def get(pid):
        if pid not in got:
            got[pid] = picsum(pid) or synthetic(pid)
        return got[pid]

    print("Paisajes, comida, mascotas, varias…")
    days = 1
    for pid in LANDSCAPES[:8] + FOOD + PETS + MISC:
        save(get(pid), CAMERA, when(days)); days += random.randint(1, 4)

    print("Vacaciones…")
    for i, pid in enumerate(LANDSCAPES[8:]):
        save(get(pid), VACACIONES, when(40 - i // 2))

    print("Caras (generadas por IA)…")
    for i in range(7):
        face = fake_face()
        if face is None:
            continue
        face = face.resize((1000, 1000))
        save(face, CAMERA, when(2 + i * 5))

    print("Documentos…")
    save(receipt(), CAMERA, when(4, 18))
    save(notes(), CAMERA, when(9, 22))
    save(ticket_tren(), CAMERA, when(15, 11))

    print("Repetidas (ráfagas)…")
    for pid, d0 in [(1015, 3), (237, 6)]:
        src = get(pid)
        t = when(d0, 13)
        for k in range(4):
            w, h = src.size
            c = 6 * k
            img = src.crop((c, c, w - c, h - c)).resize((w, h))
            img = ImageEnhance.Brightness(img).enhance(1 + 0.02 * k)
            save(img, CAMERA, t + timedelta(seconds=k), name=(t + timedelta(seconds=k)).strftime("IMG_%Y%m%d_%H%M%S") + f"_{k}.jpg")

    print("Borrosas…")
    for pid, d0 in [(16, 5), (60, 12), (20, 19)]:
        save(get(pid).filter(ImageFilter.GaussianBlur(14)), CAMERA, when(d0))
    pocket = Image.new("RGB", (1200, 900), (18, 16, 20))
    save(pocket, CAMERA, when(8))

    print("WhatsApp…")
    for i, pid in enumerate([26, 48, 1043, 431]):
        t = when(3 + i * 6)
        save(get(pid), WHATSAPP, t, name=t.strftime("IMG-%Y%m%d-WA00") + f"{i:02d}.jpg")
    t = when(1, 8)
    save(meme(), WHATSAPP, t, name=t.strftime("IMG-%Y%m%d-WA0099") + ".jpg")
    t = when(7, 8)
    save(meme().transpose(Image.Transpose.FLIP_LEFT_RIGHT).rotate(0), WHATSAPP, t,
         name=t.strftime("IMG-%Y%m%d-WA0098") + ".jpg")

    print("Total:", counter[0], "imágenes en", OUT)


if __name__ == "__main__":
    main()
