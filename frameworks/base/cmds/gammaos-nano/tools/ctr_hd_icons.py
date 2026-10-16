#!/usr/bin/env python3
"""Redraws the 3DS theme's top screen icons as vector art (lyt_hd.cpk).

The console draws its status bar and hint icons from tiny two toned textures (a white fill, a
dark border, an alpha cut out, 8 to 32 texels a side) which a large panel magnifies into blurry
or blocky shapes. Each tone of such a texture is redrawn here as vectors: its edge is rebuilt on
a fine grid (a Catmull-Rom bicubic through the texels, cut halfway between the two tones), traced
by potrace into straight segments and Bezier curves with real corners, and the paths are filled
by Cairo at SCALE times the texture size with exact anti-aliasing. Textures with a soft channel
(a light, a gradient, a translucent halo) stay the console's own.

Output: a CPK1 pack (tools/ctr_pack.py) of PNGs named "<archive>/<texture>", in the orientation
and layout the packer stores the originals in (flipped, A4/A8 colour black), SCALE times larger.
nano loads one in place of the original with the original's logical size, so layouts, window
frames and uv scales are unchanged.

Usage: ctr_hd_icons.py <romfs> <out dir> [--preview <dir>]
"""
import io
import json
import os
import re
import subprocess
import sys
import tempfile

import cairo
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ctr_pack import write_pack  # noqa: E402

SCALE = 8          # output texels per source texel
FINE = 8           # edge reconstruction grid (fine texels per output texel... per source texel)
ALPHA_ONLY = {"A4", "A8"}

# the top screen's pieces (HudMenu_00 and LncBase_U_00)
ICONS = {
    "hud": ["HudBat_02", "HudBatBase_01", "HudBatMask_00", "HudBatPlg_01", "HudCoin_W_00", "HudCoinLgt_00",
            "HudNetAntOut_00", "HudNetAntOut_01", "HudNetAtnL_10", "HudNetAtnLgt_00", "HudNetAtnMask_03",
            "HudNetAtnR_10", "HudNetModeL_00", "HudWalk_W_00", "HudBatLgt_01"],
    "launcher": ["CameraBase_06", "CameraPlus", "PictBtnL_02", "PictBtnR_02", "PictBtnY", "PictCam",
                 "PictCapture_05", "PictHome", "BaseWndwLT_16x32",
                 # the bottom screen: page and back arrows, the balloon's point, the pop up, the
                 # arrow button's alpha, the prize ring
                 "LncArwIconR_11", "PageArrowIcon", "LncBack", "LncBllnPnt_8x16", "LncPopUp_02",
                 "LncArwBtnAlp_10", "LncIconRing_10", "LncIconRing_11",
                 # shaded shapes (SHADED): their alpha edge is traced, their shading upsampled
                 "LncFolder_11", "LncFolder_13", "LncFolder_14", "LncFolder_15", "LncFolder_16",
                 "LncFolderIn_11", "LncFolderIn_13", "LncFolderIn_14", "LncFolderIn_15", "LncFolderIn_16",
                 "LncFolderHad_22", "LncFolderHadIn_21",
                 "LncIcon_11", "LncIcon_13", "LncIcon_14", "LncIcon_15", "LncIcon_16",
                 "LncCsr_41", "LncPetitBtn_20", "LncIconPrize_24", "LncIconPrize_48",
                 "LncCardB_01", "LncCardB_03", "LncCardB_04", "LncCardB_05", "LncCardB_06"],
}

# Shaded shapes of the bottom screen (folders, icon frames, the cursor, the corner button body):
# a channel that is not two toned (the shading, a gradient) is upsampled smoothly instead of
# keeping the whole texture, so the silhouette (the two toned alpha) still gets the vector edge.
SHADED = {"launcher/" + n for n in [
    "LncArwIconR_11",
    "LncFolder_11", "LncFolder_13", "LncFolder_14", "LncFolder_15", "LncFolder_16",
    "LncFolderIn_11", "LncFolderIn_13", "LncFolderIn_14", "LncFolderIn_15", "LncFolderIn_16",
    "LncFolderHad_22", "LncFolderHadIn_21",
    "LncIcon_11", "LncIcon_13", "LncIcon_14", "LncIcon_15", "LncIcon_16",
    "LncCsr_41", "LncPetitBtn_20", "LncIconPrize_24", "LncIconPrize_48",
    "LncCardB_01", "LncCardB_03", "LncCardB_04", "LncCardB_05", "LncCardB_06"]}


def reflect(i, n):
    if n == 1:
        return np.zeros_like(i)
    p = 2 * n
    i = np.mod(i, p)
    return np.where(i < n, i, p - 1 - i)


def seams(alpha):
    """Edges the shape is cut at (a solid texel on them): sampled mirrored. Elsewhere the
    texture is empty beyond its edge, so a shape that only touches the border stays round."""
    a = alpha
    return [a[:, 0].max() >= 217, a[:, -1].max() >= 217, a[0, :].max() >= 217, a[-1, :].max() >= 217]


def sampler(ch, seam):
    h, w = ch.shape

    def at(xs, ys):
        X, Y = np.meshgrid(xs, ys)
        v = ch[reflect(Y, h), reflect(X, w)].astype(np.float64)
        if not seam[0]:
            v[X < 0] = 0
        if not seam[1]:
            v[X >= w] = 0
        if not seam[2]:
            v[Y < 0] = 0
        if not seam[3]:
            v[Y >= h] = 0
        return v
    return at


def cubic_up(ch, seam, f):
    """Catmull-Rom upsample of a channel to f times its size (texel centres at (i + 0.5) / f)."""
    h, w = ch.shape
    at = sampler(ch, seam)
    xs = np.arange(-2, w + 2)
    ys = np.arange(-2, h + 2)
    grid = at(xs, ys)   # padded by 2

    def weights(t):
        t2, t3 = t * t, t * t * t
        return [(-t3 + 2 * t2 - t) / 2, (3 * t3 - 5 * t2 + 2) / 2, (-3 * t3 + 4 * t2 + t) / 2, (t3 - t2) / 2]

    def axis(n):
        s = (np.arange(n * f) + 0.5) / f - 0.5
        i0 = np.floor(s).astype(int)
        return i0, s - i0

    ix, tx = axis(w)
    iy, ty = axis(h)
    wx = weights(tx)
    wy = weights(ty)
    rows = sum(wx[k][None, :] * grid[:, ix - 1 + k + 2] for k in range(4))          # (h + 4, w f)
    out = sum(wy[k][:, None] * rows[iy - 1 + k + 2, :] for k in range(4))           # (h f, w f)
    return out


def two_tone(ch, mask, at):
    vals = ch[mask] if mask is not None else ch.ravel()
    if vals.size == 0:
        return None
    lo, hi = int(vals.min()), int(vals.max())
    if hi - lo < 24:
        return (lo, hi, False)
    band = (hi - lo) * 0.12
    h, w = ch.shape
    xs = np.arange(-2, w + 2)
    ys = np.arange(-2, h + 2)
    g = at(xs, ys)
    tone = np.where(g - lo <= band, -1, np.where(hi - g <= band, 1, 0))
    flat = 0
    n = 0
    for y in range(h):
        for x in range(w):
            if mask is not None and not mask[y, x]:
                continue
            n += 1
            if tone[y + 2, x + 2] != 0:
                continue
            win = tone[y:y + 5, x:x + 5]
            if not ((win < 0).any() and (win > 0).any()):
                flat += 1
    return (lo, hi, flat <= n // 20)


PATH_TOKEN = re.compile(r"[MmCcLlZz]|-?\d+(?:\.\d+)?")


def trace(binary):
    """potrace a boolean image (True = inside): paths in image pixel units, y down."""
    h, w = binary.shape
    with tempfile.TemporaryDirectory() as d:
        bmp = os.path.join(d, "in.pbm")
        Image.fromarray(np.where(binary, 0, 255).astype(np.uint8)).convert("1").save(bmp)
        svg = os.path.join(d, "out.svg")
        subprocess.run(["potrace", bmp, "-b", "svg", "--flat", "-u", "100", "-t", "4", "-a", "1.0", "-O", "0.4",
                        "-o", svg], check=True)
        text = open(svg).read()
    paths = []
    for d_attr in re.findall(r' d="([^"]+)"', text):
        toks = PATH_TOKEN.findall(d_attr)
        i = 0
        cur = (0.0, 0.0)
        start = cur
        cmd = None
        sub = []
        while i < len(toks):
            t = toks[i]
            if t.isalpha():
                cmd = t
                i += 1
                if cmd in "zZ":
                    sub.append(("z",))
                    cur = start
                continue
            if cmd in "Mm":
                x, y = float(toks[i]), float(toks[i + 1])
                i += 2
                cur = (x, y) if cmd == "M" or not sub else (cur[0] + x, cur[1] + y)
                start = cur
                sub.append(("m", cur))
                cmd = "l" if cmd == "m" else "L"
            elif cmd in "Ll":
                x, y = float(toks[i]), float(toks[i + 1])
                i += 2
                cur = (x, y) if cmd == "L" else (cur[0] + x, cur[1] + y)
                sub.append(("l", cur))
            elif cmd in "Cc":
                v = [float(t) for t in toks[i:i + 6]]
                i += 6
                if cmd == "c":
                    p1 = (cur[0] + v[0], cur[1] + v[1])
                    p2 = (cur[0] + v[2], cur[1] + v[3])
                    p3 = (cur[0] + v[4], cur[1] + v[5])
                else:
                    p1, p2, p3 = (v[0], v[1]), (v[2], v[3]), (v[4], v[5])
                sub.append(("c", p1, p2, p3))
                cur = p3
            else:
                i += 1
        paths.append(sub)
    # potrace's svg: transform="translate(0,H) scale(0.01,-0.01)" with -u 100
    m = re.search(r'translate\(([-\d.]+),([-\d.]+)\) scale\(([-\d.]+),([-\d.]+)\)', text)
    tx, ty, sx, sy = (float(g) for g in m.groups())
    return paths, (tx, ty, sx, sy)


def fill_coverage(binary, out_w, out_h):
    """Traced vector coverage (0..1) of a boolean image, rendered at out_w x out_h."""
    h, w = binary.shape
    if not binary.any():
        return np.zeros((out_h, out_w))
    if binary.all():
        return np.ones((out_h, out_w))
    # a margin so shapes cut at the image edge trace with straight sides on the edge
    pad = 4
    padded = np.pad(binary, pad, mode="edge")
    paths, (tx, ty, sx, sy) = trace(padded)
    surf = cairo.ImageSurface(cairo.FORMAT_A8, out_w, out_h)
    ctx = cairo.Context(surf)
    ctx.set_antialias(cairo.ANTIALIAS_BEST)
    k = out_w / w
    # image px -> output px, removing the margin
    ctx.scale(k, k)
    ctx.translate(-pad, -pad)
    ctx.translate(tx, ty)
    ctx.scale(sx, sy)
    ctx.set_fill_rule(cairo.FILL_RULE_EVEN_ODD)
    for sub in paths:
        for seg in sub:
            if seg[0] == "m":
                ctx.move_to(*seg[1])
            elif seg[0] == "l":
                ctx.line_to(*seg[1])
            elif seg[0] == "c":
                ctx.curve_to(*seg[1], *seg[2], *seg[3])
            elif seg[0] == "z":
                ctx.close_path()
    ctx.fill()
    surf.flush()
    stride = surf.get_stride()
    buf = np.frombuffer(surf.get_data(), np.uint8).reshape(out_h, stride)[:, :out_w]
    return buf.astype(np.float64) / 255.0


def redraw(path, info, key=None):
    """The vector redraw of one texture (upright as stored in the romfs), or None if soft."""
    im = np.array(Image.open(path).convert("RGBA"))
    fmt = (info or {}).get("format", "RGBA8")
    alpha_only = fmt in ALPHA_ONLY
    h, w = im.shape[:2]
    # the colour as stored, transparent texels included: the materials read luminance where the
    # alpha is cut out (a cut out letter keeps a light luminance), so it is traced as it is
    src = im
    alpha = im[:, :, 3]
    seam = seams(alpha)
    ow, oh = w * SCALE, h * SCALE
    out = np.zeros((oh, ow, 4))
    channels = [3] if alpha_only else [0, 1, 2, 3]
    for c in channels:
        ch = src[:, :, c].astype(np.float64)
        cs = seam if c == 3 else [True] * 4
        at = sampler(ch, cs)
        mask = None
        tt = two_tone(ch, mask, at)
        if tt is None:
            continue
        lo, hi, ok = tt
        if hi - lo < 24:
            out[:, :, c] = (lo + hi) / 2
            continue
        if not ok:
            if key in SHADED:
                # the shading: a smooth upsample, the alpha channel keeps the traced edge
                out[:, :, c] = np.clip(cubic_up(ch, cs, SCALE), 0, 255)
                continue
            return None
        up = cubic_up(ch, cs, FINE)
        cov = fill_coverage(up >= (lo + hi) / 2, ow, oh)
        out[:, :, c] = lo + (hi - lo) * cov
    if alpha_only:
        out[:, :, :3] = 255   # as the romfs stores it; pack_png clears alpha-only colour
    return Image.fromarray(np.clip(np.round(out), 0, 255).astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- hand drawn icons
#
# The visible icons are drawn as geometry, in texel units of the whole upright icon (y down, as
# the console shows it): `shape` is the icon's coverage (alpha), `dark` the part drawn in the
# dark tone (luminance 0; the rest of the shape is light). Letters and lenses are cut out.
# MIRROR: the texture holds the left half of the stored (upside down) image, the layout mirrors
# the other half.


def rounded_poly(ctx, pts, r):
    """A closed polygon with its corners rounded (r per vertex, or one r for all)."""
    n = len(pts)
    rs = r if isinstance(r, (list, tuple)) else [r] * n
    for i in range(n):
        p0, p1, p2 = np.array(pts[i - 1], float), np.array(pts[i], float), np.array(pts[(i + 1) % n], float)
        ri = rs[i]
        if ri <= 0:
            (ctx.move_to if i == 0 else ctx.line_to)(*p1)
            continue
        d0 = (p0 - p1) / np.linalg.norm(p0 - p1)
        d1 = (p2 - p1) / np.linalg.norm(p2 - p1)
        a = p1 + d0 * ri
        b = p1 + d1 * ri
        (ctx.move_to if i == 0 else ctx.line_to)(*a)
        # a cubic close to a circular arc for the corner's angle
        k = 0.5523
        ctx.curve_to(*(a + (p1 - a) * k), *(b + (p1 - b) * k), *b)
    ctx.close_path()


def circle(ctx, cx, cy, r):
    ctx.new_sub_path()
    ctx.arc(cx, cy, r, 0, 2 * np.pi)
    ctx.close_path()


def border(ctx, path, width):
    """The band `width` inside the edge of a closed path (dark border of a shape)."""
    ctx.save()
    path(ctx)
    ctx.clip()
    path(ctx)
    ctx.set_line_width(2 * width)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.stroke()
    ctx.restore()


CUTS = [True]   # off for the extent pass: a cut out keeps its luminance (the materials use it)


def cut(ctx, path):
    if not CUTS[0]:
        return
    ctx.save()
    ctx.set_operator(cairo.OPERATOR_CLEAR)
    path(ctx)
    ctx.fill()
    ctx.restore()


# --- Y button: a dark ring around a light disc, the Y cut out
def y_outline(ctx):
    circle(ctx, 8, 8, 7.75)


def y_letter(ctx):
    w = 1.95
    ctx.save()
    ctx.rectangle(0, 4.3, 16, 12)   # the arms end level at the top
    ctx.clip()
    ctx.set_line_width(w)
    ctx.set_line_cap(cairo.LINE_CAP_BUTT)
    ctx.move_to(3.9, 3.3)
    ctx.line_to(8, 9.3)
    ctx.line_to(12.1, 3.3)
    ctx.set_line_join(cairo.LINE_JOIN_MITER)
    ctx.stroke()
    ctx.restore()
    ctx.rectangle(8 - w / 2, 7.9, w, 12.8 - 7.9)


def draw_y(shape, dark):
    y_outline(shape)
    shape.fill()
    cut(shape, y_letter)
    border(dark, y_outline, 1.6)


# --- L and R buttons: the shoulder button outline (each side steps one texel across its middle),
# a one texel dark border, the letter cut out
def lr_outline(mirror):
    def path(ctx):
        pts = [(2.0, 1.0), (15.0, 1.0), (15.0, 7.3), (14.0, 11.9), (14.0, 15.0), (1.0, 15.0), (1.0, 8.5), (2.0, 3.8)]
        if mirror:
            pts = [(16 - x, y) for x, y in pts]
        rounded_poly(ctx, pts, [1.0, 1.0, 2.5, 2.5, 1.0, 1.0, 2.5, 2.5])
    return path


def l_letter(ctx):
    ctx.move_to(5.0, 3.75)
    ctx.line_to(7.0, 3.2)
    ctx.line_to(7.0, 11.0)
    ctx.line_to(11.85, 11.0)
    ctx.line_to(11.25, 13.0)
    ctx.line_to(5.0, 13.0)
    ctx.close_path()


def r_letter(ctx):
    # stem, bowl and leg, with the bowl's counter as an inner contour (filled even-odd)
    ctx.move_to(5.0, 3.3)
    ctx.line_to(9.3, 3.3)
    ctx.curve_to(10.6, 3.3, 11.3, 4.3, 11.3, 5.7)
    ctx.curve_to(11.3, 7.0, 10.7, 8.0, 9.8, 8.4)
    ctx.line_to(12.05, 12.9)
    ctx.line_to(9.85, 12.9)
    ctx.line_to(8.0, 8.9)
    ctx.line_to(7.0, 8.9)
    ctx.line_to(7.0, 12.9)
    ctx.line_to(5.0, 12.9)
    ctx.close_path()
    ctx.move_to(7.0, 5.0)
    ctx.line_to(8.9, 5.0)
    ctx.curve_to(9.45, 5.0, 9.65, 5.5, 9.65, 6.05)
    ctx.curve_to(9.65, 6.65, 9.35, 7.2, 8.8, 7.2)
    ctx.line_to(7.0, 7.2)
    ctx.close_path()


def draw_lr(letter, mirror):
    outline = lr_outline(mirror)

    def draw(shape, dark):
        outline(shape)
        shape.fill()
        shape.save()
        shape.set_fill_rule(cairo.FILL_RULE_EVEN_ODD)
        cut(shape, letter)
        shape.restore()
        border(dark, outline, 1.0)
    return draw


# --- camera (screenshot hint): rounded body, trapezoid top, round lens cut out
def draw_cam(shape, dark):
    rounded_poly(shape, [(0.5, 3.5), (15.5, 3.5), (15.5, 14.5), (0.5, 14.5)], 1.0)
    shape.fill()
    rounded_poly(shape, [(4.0, 3.6), (5.6, 1.0), (10.4, 1.0), (12.0, 3.6)], [0, 0.6, 0.6, 0])
    shape.fill()
    cut(shape, lambda c: circle(c, 8, 8.95, 3.5))


# --- the capture hint: a screen outline broken by a camera
def draw_capture(shape, dark):
    outer = [(7.0, 1.0), (26.0, 1.0), (26.0, 15.0), (7.0, 15.0)]
    inner = [(8.5, 2.5), (24.5, 2.5), (24.5, 13.5), (8.5, 13.5)]
    shape.save()
    shape.set_fill_rule(cairo.FILL_RULE_EVEN_ODD)
    rounded_poly(shape, outer, 1.0)
    rounded_poly(shape, inner, 0.5)
    shape.fill()
    shape.restore()
    # the gap the camera sits in
    cut(shape, lambda c: rounded_poly(c, [(18.0, 5.0), (32.0, 5.0), (32.0, 16.0), (18.0, 16.0)], 0))
    rounded_poly(shape, [(19.0, 8.0), (31.0, 8.0), (31.0, 16.0), (19.0, 16.0)], [1.0, 1.0, 1.4, 1.4])
    shape.fill()
    rounded_poly(shape, [(22.8, 8.2), (24.0, 6.0), (28.0, 6.0), (29.2, 8.2)], [0, 0.5, 0.5, 0])
    shape.fill()
    cut(shape, lambda c: circle(c, 25.0, 12.15, 2.25))


# --- HOME: a house with a window cut out
def draw_home(shape, dark):
    slope = 1.15
    apex = 1.55
    half = (8.0 - apex) * slope
    shape.move_to(8.0, apex)
    shape.line_to(8.0 + half, 8.0)
    shape.line_to(8.0 - half, 8.0)
    shape.close_path()
    shape.fill()
    shape.rectangle(3.0, 7.5, 10.0, 6.5)
    shape.fill()
    cut(shape, lambda c: rounded_poly(c, [(6.0, 8.2), (10.0, 8.2), (10.0, 11.4), (6.0, 11.4)], 0.4))


# --- antenna: a round head on a stem
def draw_antenna(shape, dark):
    circle(shape, 4.5, 2.45, 2.45)
    shape.fill()
    shape.rectangle(3.6, 5.6, 1.8, 11.0)
    shape.fill()


# --- a footstep: sole and heel, dark outlined
def smooth_closed(ctx, pts, tension=0.5):
    """A closed Catmull-Rom spline through the points, as cubic Beziers."""
    n = len(pts)
    p = [np.array(q, float) for q in pts]
    ctx.move_to(*p[0])
    for i in range(n):
        p0, p1, p2, p3 = p[i - 1], p[i], p[(i + 1) % n], p[(i + 2) % n]
        c1 = p1 + (p2 - p0) * tension / 3
        c2 = p2 - (p3 - p1) * tension / 3
        ctx.curve_to(*c1, *c2, *p2)
    ctx.close_path()


def walk_sole(ctx):
    # a shoe print: round toe, widest across the ball, tapering to the waist (outer side right)
    smooth_closed(ctx, [(11.0, 7.75), (14.2, 9.1), (15.2, 13.0), (15.0, 17.2), (13.4, 18.9), (10.4, 18.8),
                        (8.6, 16.8), (7.6, 12.2), (8.5, 9.1)])


def walk_heel(ctx):
    rounded_poly(ctx, [(10.0, 19.0), (16.0, 19.0), (16.0, 24.2), (10.0, 24.2)], [1.0, 0.0, 2.2, 2.0])


def draw_walk(shape, dark):
    walk_sole(shape)
    shape.fill()
    walk_heel(shape)
    shape.fill()
    border(dark, walk_sole, 1.0)
    border(dark, walk_heel, 1.0)


# name: (full width, full height, mirrored halves, draw)
HAND = {
    ("launcher", "PictBtnY"): (16, 16, True, draw_y),
    ("launcher", "PictBtnL_02"): (16, 16, False, draw_lr(l_letter, False)),
    ("launcher", "PictBtnR_02"): (16, 16, False, draw_lr(r_letter, True)),
    ("launcher", "PictCam"): (16, 16, True, draw_cam),
    ("launcher", "PictCapture_05"): (32, 16, False, draw_capture),
    ("launcher", "PictHome"): (16, 16, True, draw_home),
    ("hud", "HudNetAtnL_10"): (8, 16, False, draw_antenna),
    ("hud", "HudWalk_W_00"): (16, 32, False, draw_walk),
}


def hand_drawn(key, info):
    """The hand drawn icon as the stored texture (upside down, the stored half), or None."""
    if key not in HAND:
        return None
    fw, fh, mirror, draw = HAND[key]
    # the draw function gets the shape and the dark layer's contexts; drawn twice, the second
    # time without the cut outs for the shape's extent
    def layers(cuts):
        CUTS[0] = cuts
        surfs = []
        ctxs = []
        for _ in range(2):
            s = cairo.ImageSurface(cairo.FORMAT_A8, fw * SCALE, fh * SCALE)
            c = cairo.Context(s)
            c.set_antialias(cairo.ANTIALIAS_BEST)
            c.scale(SCALE, SCALE)
            surfs.append(s)
            ctxs.append(c)
        draw(ctxs[0], ctxs[1])
        CUTS[0] = True
        out = []
        for s in surfs:
            s.flush()
            stride = s.get_stride()
            out.append(np.frombuffer(s.get_data(), np.uint8).reshape(fh * SCALE, stride)[:, :fw * SCALE].astype(np.float64) / 255)
        return out
    a, d = layers(True)
    extent, _ = layers(False)
    # luminance: light over the whole shape (cut outs included: the materials read it there),
    # dark on the dark parts, black outside, as the console's textures
    lum = extent * (1.0 - np.minimum(d, 1.0))
    rgba = np.zeros((fh * SCALE, fw * SCALE, 4))
    fmt = (info or {}).get("format", "RGBA8")
    rgba[:, :, :3] = 0 if fmt in ALPHA_ONLY else (lum * 255)[:, :, None]
    if fmt in ALPHA_ONLY:
        rgba[:, :, :3] = 255   # the romfs keeps white; pack_png clears alpha-only colour
    rgba[:, :, 3] = a * 255
    full = rgba[::-1]   # stored upside down
    if mirror:
        full = full[:, :full.shape[1] // 2]
    return Image.fromarray(np.clip(np.round(full), 0, 255).astype(np.uint8), "RGBA")


def pack_png(img, info):
    """The packer's orientation and padding (tools/ctr_pack.py layout_texture_png), scaled."""
    img = img.transpose(Image.FLIP_TOP_BOTTOM)
    if (info or {}).get("format") in ALPHA_ONLY:
        a = np.array(img)
        a[:, :, :3] = 0
        img = Image.fromarray(a, "RGBA")
    w, h = img.size
    tw = max(w, (info or {}).get("textureWidth", w // SCALE) * SCALE)
    th = max(h, (info or {}).get("textureHeight", h // SCALE) * SCALE)
    if (tw, th) != (w, h):
        a = np.array(img)
        a = np.pad(a, ((0, th - h), (0, tw - w), (0, 0)), mode="edge")
        img = Image.fromarray(a, "RGBA")
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    romfs, out_dir = sys.argv[1], sys.argv[2]
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    entries = {}
    for arch, names in ICONS.items():
        index = json.load(open(os.path.join(romfs, "lyt", arch, "timg", "index.json")))
        for n in names:
            p = os.path.join(romfs, "lyt", arch, "timg", n + ".png")
            img = hand_drawn((arch, n), index.get(n))
            if img is None:
                img = redraw(p, index.get(n), "%s/%s" % (arch, n))
            if img is None:
                print("soft, kept: %s/%s" % (arch, n))
                continue
            entries["%s/%s" % (arch, n)] = pack_png(img, index.get(n))
            if preview:
                os.makedirs(preview, exist_ok=True)
                img.save(os.path.join(preview, "%s_%s.png" % (arch, n)))
            print("redrawn: %s/%s" % (arch, n))
    write_pack(os.path.join(out_dir, "lyt_hd.cpk"), entries)
    return 0


if __name__ == "__main__":
    sys.exit(main())
