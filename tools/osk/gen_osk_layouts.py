#!/usr/bin/env python3
# Copyright (C) 2026 GammaOS
#
# Generate NanoOskLayouts.h (static C++ keyboard tables) from the LeanbackIME
# res/xml keyboard definitions. This is the source-of-truth path for the Nano
# on-screen keyboard layout data: edit the Leanback XML (or this script) and
# re-run, never hand-edit the generated header.
#
# Usage:
#   python3 tools/osk/gen_osk_layouts.py \
#       --leanback packages/inputmethods/LeanbackIME \
#       --out frameworks/base/cmds/gammaos-nano/NanoOskLayouts.h
#
# The generator:
#   - parses res/values/dimens.xml for key/gap/space widths (dp),
#   - parses each base ABC keyboard + SYM keyboard,
#   - follows popupKeyboard="@xml/..." references and emits popup tables,
#   - computes per-key fractional geometry (fx/fw) and per-row (fy/fh) using a
#     fixed keyboard box (widest row x N rows), so all tables share one
#     coordinate space and scale to any aspect ratio,
#   - maps keyIcon drawables to OskGlyph markers and resolves @string labels.

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

# Keyboards to emit, in OskKbId order. (name, kind) where kind is 'abc'|'sym'.
ABC_LAYOUTS = [
    "qwerty_us", "qwerty_eu", "qwerty_en_gb", "qwerty_en_in",
    "qwerty_es_eu", "qwerty_es_us", "qwerty_az", "qwerty_ca",
    "qwerty_da", "qwerty_et", "qwerty_fi", "qwerty_nb", "qwerty_sv",
    "qwertz", "qwertz_ch", "azerty",
]
SYM_LAYOUTS = ["sym_us", "sym_eu", "sym_en_gb", "sym_en_in", "sym_azerty"]

# keyIcon drawable name -> OskGlyph marker.
ICON_GLYPH = {
    "ic_ime_delete": "GLYPH_DELETE",
    "ic_ime_shift_off": "GLYPH_SHIFT_OFF",
    "ic_ime_shift_on": "GLYPH_SHIFT_ON",
    "ic_ime_shift_lock_on": "GLYPH_SHIFT_LOCK",
    "ic_ime_space": "GLYPH_SPACE",
    "ic_ime_left_arrow": "GLYPH_LEFT",
    "ic_ime_right_arrow": "GLYPH_RIGHT",
    "ic_ime_symbols": "GLYPH_SYMBOLS",
    "ic_ime_alphabet": "GLYPH_ALPHABET",
    "ic_ime_accent_close": "GLYPH_ACCENT_CLOSE",
}

# Fallback face text for @string/keyboardview_keycode_* labels (used only for
# accessibility; icon keys draw their glyph, not this text).
KEYCODE_STRINGS = {
    "keyboardview_keycode_space": "Space",
    "keyboardview_keycode_delete": "Delete",
    "keyboardview_keycode_mode_change": "?!#",
    "keyboardview_keycode_shift": "Shift",
    "keyboardview_keycode_caps": "Caps Lock",
    "keyboardview_keycode_left": "Left",
    "keyboardview_keycode_right": "Right",
}


def parse_dimens(leanback):
    path = os.path.join(leanback, "res/values/dimens.xml")
    tree = ET.parse(path)
    out = {}
    for d in tree.getroot().findall("dimen"):
        name = d.get("name")
        txt = (d.text or "").strip()
        m = re.match(r"([0-9]+(?:\.[0-9]+)?)\s*(dp|dip|sp|px)?", txt)
        if m:
            out[name] = float(m.group(1))
    return out


def resolve_label(raw):
    """Resolve a keyLabel attribute value to (utf8_text, is_string_ref)."""
    if raw is None:
        return ("", False)
    if raw.startswith("@string/"):
        key = raw[len("@string/"):]
        return (KEYCODE_STRINGS.get(key, key), True)
    # Android escapes: a leading backslash escapes the next char (\@ \? \\).
    # XML entity refs (&amp;) are already decoded by the parser.
    txt = raw
    if txt.startswith("\\") and len(txt) >= 2:
        txt = txt[1:]
    return (txt, False)


def icon_to_glyph(raw):
    if raw is None:
        return "GLYPH_NONE"
    if raw.startswith("@drawable/"):
        name = raw[len("@drawable/"):]
        return ICON_GLYPH.get(name, "GLYPH_NONE")
    return "GLYPH_NONE"


def parse_codes(raw):
    """codes attribute -> primary int code (first of any comma list)."""
    if raw is None:
        return 0
    first = raw.split(",")[0].strip()
    return int(first)


class Key:
    __slots__ = ("code", "label", "glyph", "width", "edge_left",
                 "edge_right", "popup", "casefold")

    def __init__(self):
        self.code = 0
        self.label = ""
        self.glyph = "GLYPH_NONE"
        self.width = 28.0
        self.edge_left = False
        self.edge_right = False
        self.popup = None      # popup resource name or None
        self.casefold = False


class Row:
    __slots__ = ("keys", "edge_top", "edge_bottom")

    def __init__(self):
        self.keys = []
        self.edge_top = False
        self.edge_bottom = False


def parse_keyboard(leanback, name, dimens):
    """Parse one Keyboard XML into a list[Row]."""
    path = os.path.join(leanback, "res/xml", name + ".xml")
    tree = ET.parse(path)
    root = tree.getroot()
    default_w = dimens.get("key_width", 28.0)
    rows = []
    for row_el in root.findall("Row"):
        row = Row()
        rflags = row_el.get(ANDROID + "rowEdgeFlags") or ""
        row.edge_top = "top" in rflags
        row.edge_bottom = "bottom" in rflags
        for key_el in row_el.findall("Key"):
            k = Key()
            k.code = parse_codes(key_el.get(ANDROID + "codes"))
            label, is_ref = resolve_label(key_el.get(ANDROID + "keyLabel"))
            k.label = label
            k.glyph = icon_to_glyph(key_el.get(ANDROID + "keyIcon"))
            # width: @dimen ref or absent (default key width)
            wraw = key_el.get(ANDROID + "keyWidth")
            if wraw and wraw.startswith("@dimen/"):
                k.width = dimens.get(wraw[len("@dimen/"):], default_w)
            elif wraw:
                m = re.match(r"([0-9.]+)", wraw)
                k.width = float(m.group(1)) if m else default_w
            else:
                k.width = default_w
            eflags = key_el.get(ANDROID + "keyEdgeFlags") or ""
            k.edge_left = "left" in eflags
            k.edge_right = "right" in eflags
            pk = key_el.get(ANDROID + "popupKeyboard")
            if pk and pk.startswith("@xml/"):
                k.popup = pk[len("@xml/"):]
            # case-foldable: a single Unicode letter committed as a codepoint,
            # and not an icon/string-ref key.
            k.casefold = (
                k.code > 0
                and not is_ref
                and k.glyph == "GLYPH_NONE"
                and len(label) == 1
                and label.isalpha()
            )
            row.keys.append(k)
        rows.append(row)
    return rows


def c_string(s):
    """Emit a UTF-8 string as a safe C string literal (hex-escape non-ASCII,
    each escape in its own adjacent literal so hex runs do not merge)."""
    segs = []
    cur = ""
    for b in s.encode("utf-8"):
        if 0x20 <= b <= 0x7E and b not in (0x22, 0x5C):  # printable, not " or backslash
            cur += chr(b)
        else:
            if cur:
                segs.append('"%s"' % cur)
                cur = ""
            if b == 0x22:
                segs.append('"\\""')
            elif b == 0x5C:
                segs.append('"\\\\"')
            else:
                segs.append('"\\x%02x"' % b)
    if cur:
        segs.append('"%s"' % cur)
    if not segs:
        return '""'
    return " ".join(segs)


def kb_box(rows, dimens):
    """Return (width_dp, height_dp) of the keyboard box: widest row x rows."""
    hgap = dimens.get("keyboard_horizontal_gap", 12.0)
    vgap = dimens.get("keyboard_vertical_gap", 8.0)
    kh = dimens.get("key_height", 28.0)
    width = 0.0
    for row in rows:
        if not row.keys:
            continue
        w = sum(k.width for k in row.keys) + hgap * (len(row.keys) - 1)
        width = max(width, w)
    nrows = len(rows)
    height = kh * nrows + vgap * max(0, nrows - 1)
    return width, height


def emit_keys_array(out, ident, keys, box_w, popup_index):
    out.append("static const OskKey %s[] = {" % ident)
    hgap = EMIT_HGAP
    x = 0.0
    for k in keys:
        fx = x / box_w
        fw = k.width / box_w
        x += k.width + hgap
        pidx = popup_index.get(k.popup, -1) if k.popup else -1
        out.append(
            "    { %4d, %-14s, %-16s, %.6ff, %.6ff, %-5s, %-5s, %3d, %-5s },"
            % (
                k.code,
                c_string(k.label),
                k.glyph,
                fx,
                fw,
                "true" if k.edge_left else "false",
                "true" if k.edge_right else "false",
                pidx,
                "true" if k.casefold else "false",
            )
        )
    out.append("};")
    out.append("")


def emit():
    ap = argparse.ArgumentParser()
    ap.add_argument("--leanback", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    dimens = parse_dimens(args.leanback)
    global EMIT_HGAP
    EMIT_HGAP = dimens.get("keyboard_horizontal_gap", 12.0)
    vgap = dimens.get("keyboard_vertical_gap", 8.0)
    kh = dimens.get("key_height", 28.0)

    all_layouts = [(n, "abc") for n in ABC_LAYOUTS] + \
                  [(n, "sym") for n in SYM_LAYOUTS]

    # Parse all base keyboards.
    parsed = {}
    for name, _ in all_layouts:
        parsed[name] = parse_keyboard(args.leanback, name, dimens)

    # Collect popup references in first-seen order.
    popup_order = []
    for name, _ in all_layouts:
        for row in parsed[name]:
            for k in row.keys:
                if k.popup and k.popup not in popup_order:
                    popup_order.append(k.popup)
    popup_index = {p: i for i, p in enumerate(popup_order)}

    # Parse popups (single-row keyboards).
    popups = {}
    for p in popup_order:
        popups[p] = parse_keyboard(args.leanback, p, dimens)

    out = []
    out.append("// AUTO-GENERATED by tools/osk/gen_osk_layouts.py. DO NOT EDIT.")
    out.append("// Source: packages/inputmethods/LeanbackIME/res/xml/*.xml")
    out.append("// Regenerate after changing the Leanback keyboards or the generator.")
    out.append("")
    out.append("#ifndef GAMMAOS_NANO_OSK_LAYOUTS_H")
    out.append("#define GAMMAOS_NANO_OSK_LAYOUTS_H")
    out.append("")
    out.append('#include "NanoOskTypes.h"')
    out.append("")
    out.append("namespace android {")
    out.append("")
    out.append("// Keyboard box aspect (width:height in dp) used to preserve the")
    out.append("// Leanback proportions when scaling. All tables share this box.")
    sample_w, sample_h = kb_box(parsed[ABC_LAYOUTS[0]], dimens)
    out.append("static const float kOskBoxAspect = %.6ff; // %.0f:%.0f"
               % (sample_w / sample_h, sample_w, sample_h))
    out.append("")

    # OskKbId enum lives in NanoOskTypes.h (stable, no data). We only need the
    # order here to match it; a static_assert after kOskKb[] guards that.
    out.append("// OskKbId enum is defined in NanoOskTypes.h, same order as kOskKb[] below.")
    out.append("")

    # Popup key arrays.
    out.append("// ---- Accent / shift popup cells -----------------------------------")
    for p in popup_order:
        rows = popups[p]
        keys = rows[0].keys if rows else []
        box_w, _ = kb_box(parsed[ABC_LAYOUTS[0]], dimens)  # main-grid cell size
        emit_keys_array(out, "kPopupKeys_%s" % p, keys, box_w, popup_index)

    out.append("static const OskPopup kOskPopups[] = {")
    for p in popup_order:
        rows = popups[p]
        n = len(rows[0].keys) if rows else 0
        out.append("    { kPopupKeys_%s, %d }, // [%d] %s"
                   % (p, n, popup_index[p], p))
    out.append("};")
    out.append("static const int kOskPopupCount = %d;" % len(popup_order))
    out.append("")

    # Base keyboards: per-keyboard key arrays + row arrays.
    out.append("// ---- Base ABC + SYM keyboards -------------------------------------")
    for name, _ in all_layouts:
        rows = parsed[name]
        box_w, box_h = kb_box(rows, dimens)
        # per-row key arrays
        for ri, row in enumerate(rows):
            emit_keys_array(out, "kKeys_%s_r%d" % (name, ri),
                            row.keys, box_w, popup_index)
        # row array
        out.append("static const OskRow kRows_%s[] = {" % name)
        y = 0.0
        for ri, row in enumerate(rows):
            fy = y / box_h
            fh = kh / box_h
            y += kh + vgap
            out.append(
                "    { %.6ff, %.6ff, %-5s, %-5s, kKeys_%s_r%d, %d },"
                % (fy, fh,
                   "true" if row.edge_top else "false",
                   "true" if row.edge_bottom else "false",
                   name, ri, len(row.keys))
            )
        out.append("};")
        out.append("")

    # Keyboard registry indexed by OskKbId.
    out.append("static const OskKeyboard kOskKb[] = {")
    for name, _ in all_layouts:
        rows = parsed[name]
        out.append("    { kRows_%s, %d }, // OSK_KB_%s"
                   % (name, len(rows), name.upper()))
    out.append("};")
    out.append("static_assert(sizeof(kOskKb) / sizeof(kOskKb[0]) == OSK_KB_COUNT,")
    out.append('              "kOskKb[] and OskKbId (NanoOskTypes.h) are out of sync");')
    out.append("")
    out.append("static const char* const kOskKbNames[] = {")
    for name, _ in all_layouts:
        out.append('    "%s",' % name)
    out.append("};")
    out.append("")
    out.append("} // namespace android")
    out.append("")
    out.append("#endif // GAMMAOS_NANO_OSK_LAYOUTS_H")
    out.append("")

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(out))

    # Sanity report to stderr.
    sys.stderr.write("Generated %s\n" % args.out)
    sys.stderr.write("  ABC=%d SYM=%d popups=%d\n"
                     % (len(ABC_LAYOUTS), len(SYM_LAYOUTS), len(popup_order)))
    for name, _ in all_layouts:
        rows = parsed[name]
        w, h = kb_box(rows, dimens)
        counts = "/".join(str(len(r.keys)) for r in rows)
        warn = "" if abs(w - sample_w) < 0.5 else "  <-- WIDTH MISMATCH"
        sys.stderr.write("  %-14s box=%.0fx%.0f rows=%s%s\n"
                         % (name, w, h, counts, warn))


EMIT_HGAP = 12.0
if __name__ == "__main__":
    emit()
