"""One-off: turn the brand app-icon PNG into an adaptive-icon foreground layer.

Source: DALUR_film_brand_assets/DALUR.film_app_icon_512.png (navy bg + cream
"D. film" mark). Chroma-keys out the flat navy background (with a distance-based
alpha falloff so anti-aliased text edges blend cleanly) and writes the result as
the app's adaptive-icon foreground drawable. The background stays a plain
color (@color/ic_launcher_background) since the source has no gradient.
"""
from PIL import Image

ROOT = r"D:\## APP\DALUR film"
SRC = ROOT + r"\DALUR_film_brand_assets\DALUR.film_app_icon_512.png"
DST = ROOT + r"\app\src\main\res\drawable-nodpi\ic_launcher_foreground.png"
BG = (0x0B, 0x10, 0x26)  # brand navy, matches the SVG's <rect fill="#0B1026">
TOLERANCE = 40  # euclidean RGB distance treated as "fully background"


def main() -> None:
    img = Image.open(SRC).convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            dist = ((r - BG[0]) ** 2 + (g - BG[1]) ** 2 + (b - BG[2]) ** 2) ** 0.5
            if dist <= TOLERANCE:
                px[x, y] = (r, g, b, 0)
            elif dist <= TOLERANCE * 3:
                # anti-aliased edge pixel: scale alpha by how far it is from bg
                fade = (dist - TOLERANCE) / (TOLERANCE * 2)
                px[x, y] = (r, g, b, int(a * min(1.0, fade)))
    img.save(DST)
    print(f"wrote {DST} ({w}x{h})")


if __name__ == "__main__":
    main()
