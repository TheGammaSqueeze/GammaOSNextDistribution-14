#!/usr/bin/env python3
# Copyright (C) 2026 GammaOS
#
# Generate NanoOskLayoutsExtra.h: the NON-Latin OSK keyboards (Korean dubeolsik,
# and later Cyrillic / Greek / Thai / Arabic / Hebrew), which are not part of the
# Leanback IME (Latin-only). These are authored here as compact codepoint specs
# and emitted with centered fraction geometry consistent with the Latin tables
# (same 428x172 box, 28dp keys, 12dp/8dp gaps).
#
# Usage:
#   python3 tools/osk/gen_osk_extra.py \
#       --out frameworks/base/cmds/gammaos-nano/NanoOskLayoutsExtra.h

import argparse

KEY_W = 28.0
GAP = 12.0
VGAP = 8.0
KEY_H = 28.0
SPACE_W = 268.0
BOX_W = 428.0  # max row width (digit / bottom rows)

# Special codes (mirror OskCode).
DEL, SHIFT, MODE, LEFT, RIGHT, SPACE = -5, -1, -2, -3, -4, 32

# Glyph markers (mirror OskGlyph names).
GN, GDEL, GSHIFT, GSPACE, GLEFT, GRIGHT, GSYM, GALPHA = (
    "GLYPH_NONE", "GLYPH_DELETE", "GLYPH_SHIFT_OFF", "GLYPH_SPACE",
    "GLYPH_LEFT", "GLYPH_RIGHT", "GLYPH_SYMBOLS", "GLYPH_ALPHABET")


def key(code, label, glyph=GN, width=KEY_W, casefold=False):
    return dict(code=code, label=label, glyph=glyph, width=width, casefold=casefold)


def letters(s, cf=False):
    # cf=True => case-foldable letters (uppercase under shift via toUpperCp).
    return [key(ord(c), c, casefold=cf) for c in s]


def digit_row():
    r = letters("1234567890")
    r.append(key(DEL, "Delete", GDEL))
    return r


def bottom_row():
    return [key(MODE, "?!#", GSYM), key(SHIFT, "Shift", GSHIFT),
            key(SPACE, "Space", GSPACE, width=SPACE_W),
            key(LEFT, "Left", GLEFT), key(RIGHT, "Right", GRIGHT)]


# ----- Keyboard specs (name -> list of rows) -----
def korean_dubeolsik():
    return [
        digit_row(),
        letters("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ"),  # ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ
        letters("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ"),        # ㅁㄴㅇㄹㅎㅗㅓㅏㅣ
        letters("ㅋㅌㅊㅍㅠㅜㅡ")                     # ㅋㅌㅊㅍㅠㅜㅡ
            + [key(ord(','), ","), key(ord('.'), ".")],
        bottom_row(),
    ]


def russian_jcuken():
    # Standard ЙЦУКЕН, 33 letters on an 11/11/11 grid (case-foldable).
    return [
        digit_row(),
        letters("йцукенгшщзх", cf=True),
        letters("фывапролджэ", cf=True),
        letters("ячсмитьбюёъ", cf=True),
        bottom_row(),
    ]


def greek():
    # Greek (QWERTY-positioned), 9/9/7 centered, case-foldable.
    return [
        digit_row(),
        letters("ςερτυθιοπ", cf=True),
        letters("ασδφγηξκλ", cf=True),
        letters("ζχψωβνμ", cf=True),
        bottom_row(),
    ]


def hebrew():
    # Standard Hebrew (RTL). 27 letters incl. finals; not case-foldable.
    return [
        digit_row(),
        letters("קראטוןםפף"),   # ק ר א ט ו ן ם פ ף
        letters("שדגכעיחלך"),   # ש ד ג כ ע י ח ל ך
        letters("זסבהנמצתץ"),   # ז ס ב ה נ מ צ ת ץ
        bottom_row(),
    ]


def arabic():
    # Standard Arabic (RTL). Letters are stored logically; the renderer shapes
    # them to contextual presentation forms (via ICU) at draw time.
    return [
        digit_row(),
        letters("ضصثقفغعهخحج"),   # ض ص ث ق ف غ ع ه خ ح ج
        letters("شسيبلاتنمكط"),   # ش س ي ب ل ا ت ن م ك ط
        letters("ذءؤرىةوزظد"),    # ذ ء ؤ ر ى ة و ز ظ د
        bottom_row(),
    ]


def thai():
    # Kedmanee (unshifted), a practical Thai subset. LTR. Combining vowels/
    # tones (zero-advance) overlay the preceding consonant at render time.
    return [
        digit_row(),
        letters("ๆไำพะัีรนยบล"),   # ๆไำพะ ั ีรนยบล
        letters("ฟหกดเ้่าสวง"),  # ฟหกดเ ้ ่ าสวง
        letters("ผปแอิืทมใฝ"),   # ผปแอ ิ ื ทมใฝ
        bottom_row(),
    ]


# (name, rows). Each becomes a kKb_<name> in NanoOskLayoutsExtra.h.
KEYBOARDS = [
    ("korean",  korean_dubeolsik()),
    ("russian", russian_jcuken()),
    ("greek",   greek()),
    ("hebrew",  hebrew()),
    ("thai",    thai()),
    ("arabic",  arabic()),
]


def c_string(s):
    segs = []
    cur = ""
    for b in s.encode("utf-8"):
        if 0x20 <= b <= 0x7E and b not in (0x22, 0x5C):
            cur += chr(b)
        else:
            if cur:
                segs.append('"%s"' % cur); cur = ""
            if b == 0x22:   segs.append('"\\""')
            elif b == 0x5C: segs.append('"\\\\"')
            else:           segs.append('"\\x%02x"' % b)
    if cur:
        segs.append('"%s"' % cur)
    return " ".join(segs) if segs else '""'


def emit_row_keys(out, ident, keys):
    out.append("static const OskKey %s[] = {" % ident)
    rowW = sum(k["width"] for k in keys) + GAP * (len(keys) - 1)
    x = (BOX_W - rowW) / 2.0    # center the row in the box
    n = len(keys)
    for i, k in enumerate(keys):
        fx = x / BOX_W
        fw = k["width"] / BOX_W
        x += k["width"] + GAP
        out.append(
            "    { %4d, %-12s, %-15s, %.6ff, %.6ff, %-5s, %-5s, %3d, %-5s },"
            % (k["code"], c_string(k["label"]), k["glyph"], fx, fw,
               "true" if i == 0 else "false",
               "true" if i == n - 1 else "false",
               -1, "true" if k["casefold"] else "false"))
    out.append("};")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    out = []
    out.append("// AUTO-GENERATED by tools/osk/gen_osk_extra.py. DO NOT EDIT.")
    out.append("// Non-Latin OSK keyboards (not part of the Leanback IME).")
    out.append("")
    out.append("#ifndef GAMMAOS_NANO_OSK_LAYOUTS_EXTRA_H")
    out.append("#define GAMMAOS_NANO_OSK_LAYOUTS_EXTRA_H")
    out.append("")
    out.append('#include "NanoOskTypes.h"')
    out.append("")
    out.append("namespace android {")
    out.append("")
    for name, rows in KEYBOARDS:
        nrows = len(rows)
        box_h = KEY_H * nrows + VGAP * (nrows - 1)
        for ri, keys in enumerate(rows):
            emit_row_keys(out, "kXKeys_%s_r%d" % (name, ri), keys)
        out.append("static const OskRow kXRows_%s[] = {" % name)
        y = 0.0
        for ri, keys in enumerate(rows):
            fy = y / box_h
            fh = KEY_H / box_h
            y += KEY_H + VGAP
            out.append("    { %.6ff, %.6ff, %-5s, %-5s, kXKeys_%s_r%d, %d },"
                       % (fy, fh, "true" if ri == 0 else "false",
                          "true" if ri == nrows - 1 else "false",
                          name, ri, len(keys)))
        out.append("};")
        out.append("static const OskKeyboard kKb_%s = { kXRows_%s, %d };"
                   % (name, name, nrows))
        out.append("")
    out.append("} // namespace android")
    out.append("")
    out.append("#endif // GAMMAOS_NANO_OSK_LAYOUTS_EXTRA_H")
    out.append("")
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print("Generated %s (%d keyboards)" % (args.out, len(KEYBOARDS)))


if __name__ == "__main__":
    main()
