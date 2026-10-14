#!/usr/bin/env python3
"""Packs the 3DS HOME Menu assets for the nano 3DS theme.

Input: the romfs directory the 3ds-web pipeline writes (app/public/romfs, see
/work/3ds/web/docs/pipeline.md). Output: a directory of pack files that nano maps read only
(installed under /system/etc/nano_ctr).

Formats (all little endian):

CPK1 pack: magic "CPK1", u32 entry count, then per entry (sorted by name) u32 name offset,
u32 name length, u32 data offset, u32 data length; names and data follow, data 4 aligned.

BJS1 value tree (the pipeline JSON, compact): magic "BJS1", u32 string count, u32 offsets
(count + 1, relative to the string blob), string blob, then the root value. A value is a tag
byte: 0 null, 1 false, 2 true, 3 i32, 4 f32, 5 string (u32 index), 6 array (u32 count,
u32 byte size of the items, items), 7 object (u32 count, u32 byte size of the members,
members as u32 key index + value). Object keys keep the JSON order.

Layout textures (timg) are stored as PNG already in the orientation the renderer samples
(flipped like the web app's imageOrientation flipY), with A4/A8 colour cleared to black and
padded to the hardware size with the edge texels repeated, so nano uploads them as is.

CFN1 font: magic "CFN1", u16 lineFeed, height, width, ascent, baseline, cellWidth,
cellHeight, u8 format, u8 pad, u32 glyph count, u32 alternate glyph slot, then per glyph
sorted by code point: u32 code, i16 left, u8 glyphWidth, u8 charWidth. Then glyph bitmaps,
one per glyph in the same order, each the glyph cell plus a one texel border read from the
sheet with clamp to edge addressing ((cellWidth + 2) x (cellHeight + 2) texels), so linear
filtering at the cell edges reads what the console reads. Format 0: 4 bit alpha (the A4 sheets), two texels per
byte, low nibble first, (w * h + 1) / 2 bytes per glyph. Format 1:
luminance and alpha bytes interleaved, w * h * 2 bytes per glyph.
Format 2: 8 bit alpha, w * h bytes per glyph.
"""

import argparse
import io
import json
import os
import struct
import sys

from PIL import Image


# ---------------------------------------------------------------- BJS1

class BjWriter:
    def __init__(self):
        self.strings = {}
        self.order = []

    def sid(self, s):
        i = self.strings.get(s)
        if i is None:
            i = len(self.order)
            self.strings[s] = i
            self.order.append(s)
        return i

    def value(self, v):
        if v is None:
            return b"\x00"
        if v is False:
            return b"\x01"
        if v is True:
            return b"\x02"
        if isinstance(v, int):
            if -2**31 <= v < 2**31:
                return b"\x03" + struct.pack("<i", v)
            return b"\x04" + struct.pack("<f", float(v))
        if isinstance(v, float):
            if v.is_integer() and -2**31 <= v < 2**31:
                return b"\x03" + struct.pack("<i", int(v))
            return b"\x04" + struct.pack("<f", v)
        if isinstance(v, str):
            return b"\x05" + struct.pack("<I", self.sid(v))
        if isinstance(v, list):
            body = b"".join(self.value(x) for x in v)
            return b"\x06" + struct.pack("<II", len(v), len(body)) + body
        if isinstance(v, dict):
            parts = []
            for k, x in v.items():
                parts.append(struct.pack("<I", self.sid(str(k))) + self.value(x))
            body = b"".join(parts)
            return b"\x07" + struct.pack("<II", len(v), len(body)) + body
        raise TypeError(type(v))

    def encode(self, root):
        body = self.value(root)
        blob = bytearray()
        offs = []
        for s in self.order:
            offs.append(len(blob))
            blob += s.encode("utf-8")
        offs.append(len(blob))
        head = b"BJS1" + struct.pack("<I", len(self.order)) + struct.pack("<%dI" % len(offs), *offs)
        return head + bytes(blob) + body


def bj(obj):
    return BjWriter().encode(obj)


# ---------------------------------------------------------------- CPK1

def write_pack(path, entries):
    names = sorted(entries)
    head_size = 8 + 16 * len(names)
    name_blob = bytearray()
    name_offs = []
    for n in names:
        name_offs.append(head_size + len(name_blob))
        name_blob += n.encode("utf-8")
    data_start = head_size + len(name_blob)
    data_start = (data_start + 3) & ~3
    data = bytearray()
    data_offs = []
    for n in names:
        while len(data) % 4:
            data.append(0)
        data_offs.append(data_start + len(data))
        data += entries[n]
    out = bytearray(b"CPK1" + struct.pack("<I", len(names)))
    for i, n in enumerate(names):
        out += struct.pack("<IIII", name_offs[i], len(n.encode("utf-8")), data_offs[i], len(entries[n]))
    out += name_blob
    while len(out) < data_start:
        out.append(0)
    out += data
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(out)
    os.replace(tmp, path)
    return len(out)


def load_json(path):
    with open(path, "rb") as f:
        return json.load(f)


# ---------------------------------------------------------------- textures

ALPHA_ONLY = {"A4", "A8"}


def layout_texture_png(path, info):
    im = Image.open(path).convert("RGBA").transpose(Image.FLIP_TOP_BOTTOM)
    if info and info.get("format") in ALPHA_ONLY:
        a = im.getchannel("A")
        black = Image.new("RGB", im.size, (0, 0, 0))
        im = Image.merge("RGBA", (*black.split(), a))
    w, h = im.size
    tw = max(w, (info or {}).get("textureWidth", w))
    th = max(h, (info or {}).get("textureHeight", h))
    if (tw, th) != (w, h):
        padded = Image.new("RGBA", (tw, th))
        padded.paste(im, (0, 0))
        px = padded.load()
        src = im.load()
        for y in range(th):
            sy = min(y, h - 1)
            for x in range(w, tw):
                px[x, y] = src[w - 1, sy]
        for y in range(h, th):
            for x in range(w):
                px[x, y] = src[x, h - 1]
        im = padded
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def plain_png(path, flip=False):
    im = Image.open(path).convert("RGBA")
    if flip:
        im = im.transpose(Image.FLIP_TOP_BOTTOM)
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return buf.getvalue()


# ---------------------------------------------------------------- archives

def pack_archive(romfs, name, entry, out_dir):
    base = os.path.join(romfs, "lyt", name)
    entries = {}
    for lay in entry["layouts"]:
        entries["blyt/" + lay] = bj(load_json(os.path.join(base, "blyt", lay + ".json")))
    for anim in entry["anims"]:
        entries["anim/" + anim] = bj(load_json(os.path.join(base, "anim", anim + ".json")))
    index_path = os.path.join(base, "timg", "index.json")
    index = load_json(index_path) if os.path.exists(index_path) else {}
    entries["timg/index"] = bj(index)
    for tex in entry["textures"]:
        stem = tex[:-4] if tex.endswith(".png") else tex
        p = os.path.join(base, "timg", stem + ".png")
        if not os.path.exists(p):
            continue
        entries["timg/" + stem] = layout_texture_png(p, index.get(stem))
    entries["info"] = bj({"layouts": entry["layouts"], "anims": entry["anims"], "textures": entry["textures"]})
    return write_pack(os.path.join(out_dir, "lyt_%s.cpk" % name), entries)


# ---------------------------------------------------------------- fonts

def pack_font(romfs, name, entry, out_dir):
    fj = load_json(os.path.join(romfs, entry["json"]))
    fdir = os.path.dirname(os.path.join(romfs, entry["json"]))
    fmt = fj.get("sheetFormat", "A4")
    if fmt.startswith("LA") or fmt in ("L4", "L8"):
        kind = 1
    elif fmt == "A4":
        kind = 0
    else:
        kind = 2
    cw, ch = fj["cellWidth"], fj["cellHeight"]
    sheets = {}

    def sheet(i):
        im = sheets.get(i)
        if im is None:
            im = Image.open(os.path.join(fdir, fj["sheets"][i])).convert("RGBA")
            sheets[i] = im
        return im

    glyphs = []
    for code, g in fj["glyphs"].items():
        glyphs.append((int(code), g))
    glyphs.sort(key=lambda t: t[0])
    alt_slot = 0
    for slot, (code, g) in enumerate(glyphs):
        if g["index"] == fj["alterCharIndex"]:
            alt_slot = slot
    table = bytearray()
    bitmaps = bytearray()
    for code, g in glyphs:
        table += struct.pack("<IhBB", code, g["left"], g["glyphWidth"], g["charWidth"])
        im = sheet(g["sheet"])
        sw, sh = im.size
        src = im.load()
        bw, bh = cw + 2, ch + 2
        px = bytearray(bw * bh * 4)
        for yy in range(bh):
            sy = min(max(g["y"] - 1 + yy, 0), sh - 1)
            for xx in range(bw):
                sx = min(max(g["x"] - 1 + xx, 0), sw - 1)
                o = (yy * bw + xx) * 4
                px[o:o + 4] = bytes(src[sx, sy])
        n = bw * bh
        if kind == 1:
            out = bytearray(n * 2)
            for i in range(n):
                out[i * 2] = px[i * 4]
                out[i * 2 + 1] = px[i * 4 + 3]
        elif kind == 0:
            out = bytearray((n + 1) // 2)
            for i in range(n):
                v = (px[i * 4 + 3] + 8) // 17
                out[i >> 1] |= v << ((i & 1) * 4)
        else:
            out = bytes(px[i * 4 + 3] for i in range(n))
        bitmaps += out
    head = b"CFN1" + struct.pack(
        "<HHHHHHHBBII",
        fj["lineFeed"], fj["height"], fj["width"], fj["ascent"], fj["baselinePosition"],
        cw, ch, kind, 0, len(glyphs), alt_slot,
    )
    data = head + bytes(table) + bytes(bitmaps)
    path = os.path.join(out_dir, "font_%s.cfn" % name)
    with open(path + ".tmp", "wb") as f:
        f.write(data)
    os.replace(path + ".tmp", path)
    return len(data)


# ---------------------------------------------------------------- misc

def pack_messages(romfs, manifest, out_dir):
    entries = {}
    for lang, e in manifest["messages"].items():
        for key in ("menu", "hud", "menuStyles", "hudStyles"):
            entries["%s/%s" % (lang, key)] = bj(load_json(os.path.join(romfs, e[key])))
    return write_pack(os.path.join(out_dir, "msg.cpk"), entries)


def pack_misc(romfs, manifest, out_dir):
    entries = {"manifest": bj(manifest)}
    for name, t in manifest["themes"]["builtin"].items():
        entries["theme/" + name] = bj(load_json(os.path.join(romfs, t["json"])))
    for key, path in manifest.get("system", {}).items():
        p = os.path.join(romfs, path)
        if os.path.exists(p):
            entries["system/" + key] = bj(load_json(p))
    for key, icon in manifest.get("icons", {}).items():
        for size, path in icon.items():
            p = os.path.join(romfs, path)
            if os.path.exists(p) and path.endswith(".png"):
                entries["icon/%s_%s" % (key, size)] = plain_png(p)
    sounds = os.path.join(romfs, "sound", "sounds.json")
    if os.path.exists(sounds):
        entries["sound/sounds"] = bj(load_json(sounds))
    return write_pack(os.path.join(out_dir, "misc.cpk"), entries)


# ---------------------------------------------------------------- models

ALPHA_TEXTURE_FORMATS = {"A4", "A8"}


def model_texture_png(path, fmt):
    # CGFX texture PNG rows are already in PICA t order (sampled without a flip); alpha formats
    # sample as (0, 0, 0, a)
    im = Image.open(path).convert("RGBA")
    if fmt in ALPHA_TEXTURE_FORMATS:
        a = im.getchannel("A")
        black = Image.new("RGB", im.size, (0, 0, 0))
        im = Image.merge("RGBA", (*black.split(), a))
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def pack_model(romfs, name, out_dir):
    base = os.path.join(romfs, "model", name)
    data = load_json(os.path.join(base, name + ".json"))
    entries = {"cgfx": bj(data)}
    for model in data.get("models", []):
        g = model.get("gltf")
        if not g:
            continue
        gltf = load_json(os.path.join(base, g))
        entries["gltf"] = bj(gltf)
        for i, buf in enumerate(gltf.get("buffers", [])):
            with open(os.path.join(base, buf["uri"]), "rb") as f:
                entries["bin/%d" % i] = f.read()
        break
    for tex in data.get("textures", []):
        uri = tex.get("uri")
        if not uri:
            continue
        p = os.path.join(base, uri)
        if os.path.exists(p):
            entries["tex/" + tex["name"]] = model_texture_png(p, tex.get("format", ""))
    return write_pack(os.path.join(out_dir, "model_%s.cpk" % name), entries)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--romfs", default="/work/3ds/web/app/public/romfs")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "ctr3ds", "data"))
    ap.add_argument("--only", default="", help="comma list: lyt,font,msg,model,misc")
    args = ap.parse_args()
    romfs = os.path.abspath(args.romfs)
    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    only = set(filter(None, args.only.split(",")))
    manifest = load_json(os.path.join(romfs, "manifest.json"))
    total = 0
    if not only or "lyt" in only:
        for name, entry in sorted(manifest["archives"].items()):
            n = pack_archive(romfs, name, entry, out)
            total += n
            print("lyt %-16s %8d" % (name, n))
    if not only or "font" in only:
        for name, entry in sorted(manifest["fonts"].items()):
            n = pack_font(romfs, name, entry, out)
            total += n
            print("font %-15s %8d" % (name, n))
    if not only or "msg" in only:
        n = pack_messages(romfs, manifest, out)
        total += n
        print("msg %d" % n)
    if not only or "model" in only:
        for name in sorted(os.listdir(os.path.join(romfs, "model"))):
            if os.path.exists(os.path.join(romfs, "model", name, name + ".json")):
                n = pack_model(romfs, name, out)
                total += n
                print("model %-14s %8d" % (name, n))
    if not only or "misc" in only:
        n = pack_misc(romfs, manifest, out)
        total += n
        print("misc %d" % n)
    print("total %d bytes" % total)


if __name__ == "__main__":
    sys.exit(main())
