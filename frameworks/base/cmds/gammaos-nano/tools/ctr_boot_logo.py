#!/usr/bin/env python3
"""Builds ctr3ds/data/lyt_logo.cpk: the 3DS launch splash (romfs lyt/logo, NintendoLogo_U_00 and
_D_00 with SceneOutA/B/C) with the GammaOS logo in place of the Nintendo 3DS one, for the 3DS
theme's cold boot (NanoMenuCtrBoot.cpp).

The layout, its animations and the red sweeps are the console's (docs/porting.md section 6 of the
3ds web repo). Only the logo textures change, drawn at 8 pixels per layout unit so they stay sharp
on large panels:
  3dsLogo_00 (P_Nin_00, slides in from the right): "GAMMA" where "NINTENDO" was
  3dsLogo_02 (P_3_00, scales in from 1.4):          the GammaOS mark where the red "3" was
  3dsLogo_03 (P_Ds_00, slides in from the left):    "OS" where "DS" was
  3dsLogo_01 (the O of NINTENDO) and Nintendo_128x64 (the bottom badge) are blank.
The lockup is centred like the original (which spans -119..118). P_3_00 gets the mark's size and
position and draws it white.

  python3 tools/ctr_boot_logo.py [--romfs /work/3ds/web/app/public/romfs] [--out ctr3ds/data]
"""
import argparse
import copy
import json
import math
import os
import re
import shutil
import sys
import tempfile

import cairo
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
NANO = os.path.dirname(HERE)
TREE = os.path.abspath(os.path.join(NANO, "..", "..", "..", ".."))
sys.path.insert(0, HERE)
import ctr_pack  # noqa: E402

PX = 8                       # texture pixels per layout unit
FONT_REG = os.path.join(NANO, "ps3xmb", "fonts", "ps3-rodin-regular.ttf")
FONT_BOLD = os.path.join(NANO, "ps3xmb", "fonts", "ps3-rodin-bold.ttf")
MARK_XML = os.path.join(TREE, "packages", "apps", "SetupWizard", "res", "drawable", "logo.xml")

SMALL_CAP = 13.0             # "NINTENDO" cap height (texture rows 1..14 of 16)
BIG_CAP = 28.0               # "DS" cap height (rows 1..29 of 32)
MARK_H = 30.0                # the "3" spans rows 1..30 of 32
TOP = 1.0                    # caps start one unit below the pane tops, like the originals
GAP = 3.5                    # space either side of the mark
MARK_WEIGHT = 30.0           # extra stroke round the mark's outlines, viewport units (of 1297
                             # tall): its hairlines are drawn for a large logo and vanish at
                             # the splash's 30 units, next to the bold OS
SMALL_TRACK = 0.16           # letter spacing in em (NINTENDO is widely spaced)
BIG_TRACK = 0.02


# ------------------------------------------------------------------------------- text

def text_image(text, font_path, cap, track):
    """White text on transparent at PX pixels per unit, cap height `cap` units, cropped to its ink
    (round letters like O and S overshoot the cap line and the baseline, so their ink is kept).
    Returns (image, width in units, cap line offset in units: how far the cap line lies below the
    image's top edge, negative when ink rises above it)."""
    target = cap * PX
    size = int(target * 1.5)
    for _ in range(6):   # fit the cap height (H) to the target
        f = ImageFont.truetype(font_path, size)
        l, t, r, b = f.getbbox("H", anchor="ls")
        size = max(8, int(round(size * target / (b - t))))
    f = ImageFont.truetype(font_path, size)
    l, t, r, b = f.getbbox("H", anchor="ls")
    capPx = b - t
    advTrack = track * size
    width = 0.0
    xs = []
    for ch in text:
        xs.append(width)
        width += f.getlength(ch) + advTrack
    width -= advTrack
    pad = int(size)
    im = Image.new("L", (int(width) + 2 * pad, int(capPx) + 2 * pad), 0)
    d = ImageDraw.Draw(im)
    baseline = pad + capPx
    for ch, x in zip(text, xs):
        d.text((pad + x, baseline), ch, fill=255, font=f, anchor="ls")
    bb = im.getbbox()
    im = im.crop(bb)
    capTop = (baseline - capPx) - bb[1]   # cap line below the crop's top, pixels
    rgba = Image.merge("RGBA", (Image.new("L", im.size, 255),) * 3 + (im,))
    return rgba, im.size[0] / PX, capTop / PX


# ------------------------------------------------------------------------------- mark

def _arc_to_beziers(x1, y1, rx, ry, phi, large, sweep, x2, y2):
    """SVG elliptical arc (endpoint form) as cubic Bezier segments."""
    if rx == 0 or ry == 0:
        return [(x1, y1, x2, y2, x2, y2)]
    phi = math.radians(phi)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    rx, ry = abs(rx), abs(ry)
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        co = -co
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    n = max(1, int(math.ceil(abs(dt) / (math.pi / 2))))
    seg = dt / n
    k = 4.0 / 3.0 * math.tan(seg / 4)
    out = []
    for i in range(n):
        a1, a2 = t1 + i * seg, t1 + (i + 1) * seg
        c1, s1, c2, s2 = math.cos(a1), math.sin(a1), math.cos(a2), math.sin(a2)
        p = [(c1 - k * s1, s1 + k * c1), (c2 + k * s2, s2 - k * c2), (c2, s2)]
        pts = []
        for ex, ey in p:
            ex, ey = ex * rx, ey * ry
            pts += [cp * ex - sp * ey + cx, sp * ex + cp * ey + cy]
        out.append(tuple(pts))
    return out


def _path_into(ctx, d):
    tokens = re.findall(r"[A-Za-z]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", d)
    i, cmd = 0, None
    x = y = sx = sy = 0.0

    def num():
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]
            i += 1
            if cmd in "zZ":
                ctx.close_path()
                x, y = sx, sy
                continue
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            ctx.move_to(x, y)
            sx, sy = x, y
            cmd = "l" if rel else "L"   # further pairs are line-tos
        elif c == "L":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if rel else (nx, ny)
            ctx.line_to(x, y)
        elif c == "H":
            nx = num()
            x = x + nx if rel else nx
            ctx.line_to(x, y)
        elif c == "V":
            ny = num()
            y = y + ny if rel else ny
            ctx.line_to(x, y)
        elif c == "C":
            p = [num() for _ in range(6)]
            if rel:
                p = [p[0] + x, p[1] + y, p[2] + x, p[3] + y, p[4] + x, p[5] + y]
            ctx.curve_to(*p)
            x, y = p[4], p[5]
        elif c == "A":
            rx, ry, phi, la, sw, nx, ny = [num() for _ in range(7)]
            if rel:
                nx, ny = nx + x, ny + y
            for b in _arc_to_beziers(x, y, rx, ry, phi, int(la), int(sw), nx, ny):
                ctx.curve_to(*b)
            x, y = nx, ny
        else:
            raise ValueError("path command %s not handled" % cmd)


def mark_image(height_units):
    """The GammaOS mark (SetupWizard logo.xml, the same vector as nano's boot plates) in white,
    `height_units` tall at PX pixels per unit. Returns (image, width in units)."""
    xml = open(MARK_XML).read()
    vw = float(re.search(r'viewportWidth="([\d.]+)"', xml).group(1))
    vh = float(re.search(r'viewportHeight="([\d.]+)"', xml).group(1))
    paths = re.findall(r'pathData="([^"]*)"', xml)
    hp = int(round(height_units * PX))
    m = MARK_WEIGHT / 2   # the stroke grows the outline this far on each side
    s = hp / (vh + 2 * m)
    wp = int(math.ceil((vw + 2 * m) * s))
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, wp, hp)
    ctx = cairo.Context(surf)
    ctx.scale(s, s)
    ctx.translate(m, m)
    ctx.set_source_rgba(1, 1, 1, 1)
    ctx.set_fill_rule(cairo.FILL_RULE_WINDING)
    ctx.set_line_join(cairo.LINE_JOIN_MITER)
    ctx.set_line_width(MARK_WEIGHT)
    for d in paths:
        _path_into(ctx, d)
        ctx.fill_preserve()
        ctx.stroke()
    surf.flush()
    a = Image.frombuffer("RGBA", (wp, hp), bytes(surf.get_data()), "raw", "BGRA", 0, 1).getchannel("A")
    rgba = Image.merge("RGBA", (Image.new("L", a.size, 255),) * 3 + (a,))
    return rgba, wp / PX


# ------------------------------------------------------------------------------- build

def put(canvas, im, x_units, y_units):
    canvas.alpha_composite(im, (int(round(x_units * PX)), int(round(y_units * PX))))


def blank(w, h):
    return Image.new("RGBA", (w, h), (255, 255, 255, 0))


def save_romfs(im, path):
    # romfs textures are stored upside down (the packer flips them back)
    im.transpose(Image.FLIP_TOP_BOTTOM).save(path)


def find_pane(p, name):
    if p.get("name") == name:
        return p
    for c in p.get("children", []):
        r = find_pane(c, name)
        if r:
            return r
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--romfs", default="/work/3ds/web/app/public/romfs")
    ap.add_argument("--out", default=os.path.join(NANO, "ctr3ds", "data"))
    ap.add_argument("--preview", default="", help="also write the finished lockup as a PNG here")
    args = ap.parse_args()

    manifest = ctr_pack.load_json(os.path.join(args.romfs, "manifest.json"))
    entry = manifest["archives"]["logo"]
    work = tempfile.mkdtemp(prefix="ctr_boot_logo_")
    try:
        src = os.path.join(args.romfs, "lyt", "logo")
        dst = os.path.join(work, "lyt", "logo")
        shutil.copytree(src, dst)
        timg = os.path.join(dst, "timg")

        gamma, wG, gCap = text_image("GAMMA", FONT_REG, SMALL_CAP, SMALL_TRACK)
        os_, wOS, osCap = text_image("OS", FONT_BOLD, BIG_CAP, BIG_TRACK)
        mark, wM = mark_image(MARK_H)
        # centre the lockup: GAMMA | gap | mark | gap | OS
        m0 = (wG - wM - wOS) / 2.0
        gEnd = m0 - GAP
        osX = m0 + wM + GAP
        if gEnd > -0.5 or osX < 0.5 or gEnd - wG < -128 or osX + wOS > 128:
            raise SystemExit("lockup does not fit the panes: GAMMA ends %.1f, OS starts %.1f" % (gEnd, osX))
        print("GAMMA %.1f..%.1f  mark %.1f..%.1f  OS %.1f..%.1f" % (gEnd - wG, gEnd, m0, m0 + wM, osX, osX + wOS))

        for name, cap_off, im_, pane_h in (("GAMMA", gCap, gamma, 16), ("OS", osCap, os_, 32)):
            top = TOP - cap_off
            if top < 0 or top + im_.size[1] / PX > pane_h:
                raise SystemExit("%s ink leaves its pane: %.2f..%.2f of %d" % (name, top, top + im_.size[1] / PX, pane_h))
        # P_Nin_00: 128 x 16, origin right-bottom at (0, 0): pane x -128..0, texture x = x + 128
        nin = blank(128 * PX, 16 * PX)
        put(nin, gamma, gEnd - wG + 128, TOP - gCap)
        # P_Ds_00: 128 x 32, origin left-centre: pane x 0..128
        ds = blank(128 * PX, 32 * PX)
        put(ds, os_, osX, TOP - osCap)
        # P_3_00: the mark centred in a square pane (power of two textures: the splash mipmaps them)
        mw = 32
        if wM > mw - 1:
            raise SystemExit("the mark is too wide for its pane: %.1f" % wM)
        three = blank(mw * PX, 32 * PX)
        put(three, mark, (mw - wM) / 2.0, (32 - MARK_H) / 2.0)

        index = ctr_pack.load_json(os.path.join(timg, "index.json"))
        for stem, im in (("3dsLogo_00", nin), ("3dsLogo_02", three), ("3dsLogo_03", ds)):
            save_romfs(im, os.path.join(timg, stem + ".png"))
            index[stem].update(width=im.size[0], height=im.size[1], textureWidth=im.size[0], textureHeight=im.size[1])
        for stem in ("3dsLogo_01", "Nintendo_128x64"):
            info = index[stem]
            save_romfs(blank(info["width"], info["height"]), os.path.join(timg, stem + ".png"))
        with open(os.path.join(timg, "index.json"), "w") as f:
            json.dump(index, f, indent=1)

        blyt_path = os.path.join(dst, "blyt", "NintendoLogo_U_00.json")
        blyt = ctr_pack.load_json(blyt_path)
        p3 = find_pane(blyt["rootPane"], "P_3_00")
        p3["size"] = [float(mw), 32.0]
        p3["translate"][0] = m0 + wM / 2.0
        p3["texCoords"] = [[[0.0, 0.0], [1.0, 0.0], [0.0, 1.0], [1.0, 1.0]]]
        for m in blyt["materials"]:
            if m["name"] == "P_3_00":
                m["tevColor"] = [255, 255, 255, 0]   # the accent is the white mark
        with open(blyt_path, "w") as f:
            json.dump(blyt, f)

        if args.preview:
            pv = Image.new("RGBA", (400 * PX // 2, 240 * PX // 2), (0, 0, 0, 255))
            full = Image.new("RGBA", (400 * PX, 240 * PX), (0, 0, 0, 0))
            put(full, nin, 200 - 128, 120 - 16)
            put(full, ds, 200, 120 - 16)
            put(full, three, 200 + m0 + wM / 2.0 - mw / 2.0, 120 - 16)
            pv.alpha_composite(full.resize(pv.size, Image.LANCZOS))
            pv.convert("RGB").save(args.preview)

        os.makedirs(args.out, exist_ok=True)
        n = ctr_pack.pack_archive(os.path.join(work), "logo", entry, args.out)
        print("lyt logo %8d" % n)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
