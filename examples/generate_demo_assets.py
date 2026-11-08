"""Create the two project-owned PNG files used in README demonstrations."""

from pathlib import Path
import math
import struct
import zlib


WIDTH, HEIGHT = 640, 480
OUTPUT = Path(__file__).parent / "assets"


def chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def scene(x: int, y: int, variant: int) -> tuple[int, int, int]:
    sky = (35, 91, 143) if variant == 1 else (97, 95, 142)
    warm = (248, 190, 122) if variant == 1 else (247, 157, 134)
    horizon = 265 if variant == 1 else 290
    sun_x, sun_y = (470, 120) if variant == 1 else (150, 135)
    if (x - sun_x) ** 2 + (y - sun_y) ** 2 < 52 ** 2:
        return (255, 221, 150)
    if y < horizon:
        t = y / horizon
        return tuple(int(sky[i] * (1 - t) + warm[i] * t) for i in range(3))
    ridge = horizon + 44 * math.sin(x / 60 + variant) + 20 * math.sin(x / 25)
    if y < ridge:
        return (76, 108, 105) if variant == 1 else (96, 91, 110)
    if y < horizon + 105:
        return (48, 86, 91) if variant == 1 else (66, 72, 94)
    return (184, 144, 105) if variant == 1 else (122, 126, 116)


def write_png(path: Path, variant: int) -> None:
    rows = bytearray()
    for y in range(HEIGHT):
        rows.append(0)
        for x in range(WIDTH):
            rows.extend(scene(x, y, variant))
    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_png(OUTPUT / "demo-coast.png", 1)
    write_png(OUTPUT / "demo-sunset.png", 2)
