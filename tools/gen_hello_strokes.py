#!/usr/bin/env python3
"""
Generates the writing lines (pen strokes) for the "Hello" screen from free Android fonts:
render word → skeleton (centre line) → graph → pen strokes in writing order → smooth → Kotlin file.

Fonts (from the emulator's /system/fonts, not in the repo):
  Latin: DancingScript-Regular.ttf (OFL) · Cyrillic: Roboto-Regular.ttf (Apache 2.0)
  Chinese/Japanese/Korean: NotoSansCJK-Regular.ttc (OFL)

Usage: gen_hello_strokes.py <font-dir> <output.kt>
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from skimage.morphology import skeletonize

FONT_DIR, OUT = sys.argv[1], sys.argv[2]
SIZE = 900  # render size in px (high → hardly any pixel steps in the centre line)

# word → (font, ttc index or None, script font?)
WORDS = [
    ("你好", "NotoSansCJK-Regular.ttc", 2),       # SC
    ("hola", "DancingScript-Regular.ttf", None),
    ("hallo", "DancingScript-Regular.ttf", None),
    ("bonjour", "DancingScript-Regular.ttf", None),
    ("こんにちは", "NotoSansCJK-Regular.ttc", 0),   # JP
    ("ciao", "DancingScript-Regular.ttf", None),
    ("안녕하세요", "NotoSansCJK-Regular.ttc", 1),   # KR
    ("olá", "DancingScript-Regular.ttf", None),
    ("привет", "Roboto-Regular.ttf", None),
    ("merhaba", "DancingScript-Regular.ttf", None),
    ("hej", "DancingScript-Regular.ttf", None),
]

N8 = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]


def render(word, font_file, index):
    font = ImageFont.truetype(f"{FONT_DIR}/{font_file}", SIZE, index=index or 0)
    l, t, r, b = font.getbbox(word)
    pad = 20
    img = Image.new("L", (r - l + 2 * pad, b - t + 2 * pad), 0)
    ImageDraw.Draw(img).text((pad - l, pad - t), word, font=font, fill=255)
    a = np.array(img) > 127
    # baseline (for vertical alignment): height from the top to the baseline
    asc, _ = font.getmetrics()
    baseline = pad - t + asc
    return a, baseline


def neighbours(sk, y, x):
    h, w = sk.shape
    for dy, dx in N8:
        yy, xx = y + dy, x + dx
        if 0 <= yy < h and 0 <= xx < w and sk[yy, xx]:
            yield yy, xx


def prune(sk, min_len):
    """Remove short spurs (end point → branch, shorter than min_len)."""
    sk = sk.copy()
    for _ in range(3):
        ends = [(y, x) for y, x in zip(*np.nonzero(sk)) if len(list(neighbours(sk, y, x))) == 1]
        for e in ends:
            path = [e]; prev = None; cur = e
            while True:
                nb = [p for p in neighbours(sk, *cur) if p != prev and p not in path]
                if len(nb) != 1 or len(path) > min_len:
                    break
                prev, cur = cur, nb[0]
                if len(list(neighbours(sk, *cur))) > 2:
                    break
                path.append(cur)
            if len(path) <= min_len and len(list(neighbours(sk, *cur))) > 2:
                for p in path:
                    sk[p] = False
    return sk


def edges_of(sk):
    """Skeleton → edges (pixel chains between nodes = degree ≠ 2), incl. pure loops."""
    deg = {}
    for y, x in zip(*np.nonzero(sk)):
        deg[(y, x)] = len(list(neighbours(sk, y, x)))
    nodes = {p for p, d in deg.items() if d != 2}
    used = set()
    edges = []

    def walk(start, first):
        chain = [start, first]
        prev, cur = start, first
        while cur not in nodes:
            nb = [p for p in neighbours(sk, *cur) if p != prev and (min(p, cur), max(p, cur)) not in used]
            nb = [p for p in nb if p != chain[-2]]
            if not nb:
                break
            # prefer non-diagonal (avoids shortcuts across corner pixels)
            nb.sort(key=lambda p: abs(p[0] - cur[0]) + abs(p[1] - cur[1]))
            nxt = nb[0]
            used.add((min(cur, nxt), max(cur, nxt)))
            prev, cur = cur, nxt
            chain.append(cur)
            if cur == start:
                break
        return chain

    for n in nodes:
        for nb in neighbours(sk, *n):
            key = (min(n, nb), max(n, nb))
            if key in used:
                continue
            used.add(key)
            edges.append(walk(n, nb))
    # loops without nodes (e.g. "o")
    seen = {p for e in edges for p in e}
    for p in deg:
        if p not in seen and deg[p] == 2:
            nb = next(neighbours(sk, *p))
            used.add((min(p, nb), max(p, nb)))
            loop = walk(p, nb)
            edges.append(loop)
            seen.update(loop)
    return [e for e in edges if len(e) >= 3]


def direction(chain, at_end, k=8):
    a = chain[-1] if at_end else chain[0]
    b = chain[-min(k, len(chain))] if at_end else chain[min(k, len(chain) - 1)]
    return (a[1] - b[1], a[0] - b[0])  # (dx, dy) in travel direction


def order_strokes(edges, cjk):
    """Join edges into pen strokes: at a node the straightest continuation, otherwise a new stroke (left → right)."""
    remaining = list(range(len(edges)))
    strokes = []

    def start_key(i):
        e = edges[i]
        x = min(e[0][1], e[-1][1])
        y = min(e[0][0], e[-1][0])
        if cjk:  # roughly per character: column block, inside it top → bottom
            return (x // (SIZE * 0.8), y, x)
        return (x, y)

    while remaining:
        remaining.sort(key=start_key)
        i = remaining.pop(0)
        e = edges[i]
        # in writing direction: start on the left (CJK: top/left)
        if (e[-1][1], e[-1][0]) < (e[0][1], e[0][0]) if not cjk else (e[-1][0] + e[-1][1] * 0.3) < (e[0][0] + e[0][1] * 0.3):
            e = e[::-1]
        stroke = list(e)
        while True:
            end = stroke[-1]
            d0 = direction(stroke, True)
            best, best_cos, best_rev = None, -0.2, False
            for j in remaining:
                f = edges[j]
                for rev in (False, True):
                    g = f[::-1] if rev else f
                    if max(abs(g[0][0] - end[0]), abs(g[0][1] - end[1])) > 2:
                        continue
                    d1 = direction(g, False)
                    d1 = (-d1[0], -d1[1])
                    n0 = math.hypot(*d0) or 1; n1 = math.hypot(*d1) or 1
                    c = (d0[0] * d1[0] + d0[1] * d1[1]) / (n0 * n1)
                    if c > best_cos:
                        best, best_cos, best_rev = j, c, rev
            if best is None:
                break
            remaining.remove(best)
            g = edges[best][::-1] if best_rev else edges[best]
            stroke.extend(g[1:])
        strokes.append(stroke)
    return strokes


def smooth(points, step):
    """Pixel chain → evenly sampled, smoothed line (moving average + sampling by arc length)."""
    p = np.array([(x, y) for y, x in points], dtype=float)
    if len(p) > 5:
        k = max(5, int(SIZE * 0.025))  # smoothing ≈ 2.5 % of the font height: soft and round like a pen stroke
        pad = np.vstack([np.repeat(p[:1], k, 0), p, np.repeat(p[-1:], k, 0)])
        ker = np.ones(2 * k + 1) / (2 * k + 1)
        p = np.stack([np.convolve(pad[:, 0], ker, "valid"), np.convolve(pad[:, 1], ker, "valid")], 1)
    seg = np.hypot(*np.diff(p, axis=0).T)
    s = np.concatenate([[0], np.cumsum(seg)])
    if s[-1] < step:
        return p[[0, -1]]
    n = max(2, int(s[-1] / step) + 1)
    t = np.linspace(0, s[-1], n)
    q = np.stack([np.interp(t, s, p[:, 0]), np.interp(t, s, p[:, 1])], 1)
    # second, small smoothing pass on the evenly spaced points (end points stay fixed)
    if len(q) > 6:
        r = q.copy()
        for _ in range(6):
            r[1:-1] = (r[:-2] + 2 * r[1:-1] + r[2:]) / 4
        q = r
    return q


def main():
    out = []
    global previews
    previews = []
    for word, font, idx in WORDS:
        cjk = font.startswith("NotoSansCJK")
        a, baseline = render(word, font, idx)
        sk = skeletonize(a)
        sk = prune(sk, int(SIZE * 0.05))
        strokes = order_strokes(edges_of(sk), cjk)
        step = SIZE * 0.012
        lines = [smooth(s, step) for s in strokes]
        # discard tiny parts (skeleton leftovers): arc length < 4 % of the word width
        width = max(max(l[:, 0]) for l in lines) - min(min(l[:, 0]) for l in lines)
        lines = [l for l in lines if np.hypot(*np.diff(l, axis=0).T).sum() > width * 0.04]
        xs = np.concatenate([l[:, 0] for l in lines]); ys = np.concatenate([l[:, 1] for l in lines])
        x0, x1 = xs.min(), xs.max(); cy = (ys.min() + ys.max()) / 2
        # normalised: width 1, centre (0,0)
        norm = [((l[:, 0] - x0) / (x1 - x0) - 0.5, (l[:, 1] - cy) / (x1 - x0)) for l in lines]
        total = sum(len(l) for l in lines)
        print(f"{word}: {len(lines)} strokes, {total} points, aspect ratio {(ys.max() - ys.min()) / (x1 - x0):.2f}")
        if len(sys.argv) > 3:  # preview: strokes numbered in colour
            pv = Image.new("RGB", (420, 180), "white"); d = ImageDraw.Draw(pv)
            for k, (nx, ny) in enumerate(norm):
                pts = [(210 + x * 380, 90 + y * 380) for x, y in zip(nx, ny)]
                col = (40 + (k * 70) % 200, 80, 255 - (k * 50) % 200)
                d.line(pts, fill=col, width=3); d.ellipse([pts[0][0] - 4, pts[0][1] - 4, pts[0][0] + 4, pts[0][1] + 4], fill=(255, 0, 0))
                d.text(pts[0], str(k), fill=(0, 0, 0))
            previews.append(pv)
        # compact as text (strokes separated by "|") — large floatArrayOf lists exceed the 64 KB limit of a JVM method
        enc = "|".join(",".join(f"{x:.4f},{y:.4f}" for x, y in zip(nx, ny)) for nx, ny in norm)
        out.append(f'    "{word}" to "{enc}",')
    kt = (
        "package io.github.janhurn9400.liquidy.ui.welcome\n\n"
        "// GENERATED by tools/gen_hello_strokes.py — do not edit by hand.\n"
        "// Pen strokes per greeting: centre lines of free fonts (Dancing Script OFL, Roboto Apache 2.0, Noto Sans CJK OFL),\n"
        "// per stroke x0, y0, x1, y1, …; word width = 1, centre (0, 0).\n\n"
        "private val ENCODED: Map<String, String> = mapOf(\n" + "\n".join(out) + "\n)\n\n"
        "/** Strokes per greeting, parsed from the text only on first access. */\n"
        "internal object HELLO_STROKES {\n"
        "    private val cache = HashMap<String, Array<FloatArray>>()\n"
        "    fun getValue(word: String): Array<FloatArray> = cache.getOrPut(word) {\n"
        "        ENCODED.getValue(word).split('|').map { z -> z.split(',').map { it.toFloat() }.toFloatArray() }.toTypedArray()\n"
        "    }\n"
        "}\n"
    )
    open(OUT, "w").write(kt)
    if len(sys.argv) > 3:
        sheet = Image.new("RGB", (420 * 4, 180 * ((len(previews) + 3) // 4)), "white")
        for i, pv in enumerate(previews):
            sheet.paste(pv, ((i % 4) * 420, (i // 4) * 180))
        sheet.save(sys.argv[3])


main()
