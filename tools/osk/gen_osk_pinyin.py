#!/usr/bin/env python3
# Copyright (C) 2026 GammaOS
#
# Generate NanoOskPinyin.h: a compact toneless-pinyin -> Hanzi candidate table
# for the Nano OSK Chinese input method. There is no pinyin engine in the tree,
# so we build the data deterministically from offline sources:
#
#   * CLDR external/cldr/common/transforms/Han-Latin.xml gives Hanzi -> toned
#     pinyin. We invert it: strip tones to a toneless syllable key (what a user
#     types) and collect the Hanzi that read that way.
#   * chardet's gb2312freq.py (bundled with the prebuilt host python) gives a
#     real frequency rank for the common GB2312 level-1 Hanzi, so the most
#     common characters surface first in the candidate bar. GB2312/GBK codec
#     membership is the secondary commonness tier.
#
# Only BMP CJK Unified ideographs (U+4E00..U+9FFF) are kept, and candidates are
# capped per syllable, so the emitted table stays small.
#
# Usage:
#   python3 tools/osk/gen_osk_pinyin.py \
#       --cldr external/cldr/common/transforms/Han-Latin.xml \
#       --out  frameworks/base/cmds/gammaos-nano/NanoOskPinyin.h

import argparse
import glob
import os
import re
import sys
import unicodedata

MAX_CAND = 24          # candidates kept per syllable
CJK_LO, CJK_HI = 0x4E00, 0x9FFF

# ü (and its toned forms) maps to 'v', the standard pinyin-IME key for ü.
U_UML = {0x01D6: 'v', 0x01D8: 'v', 0x01DA: 'v', 0x01DC: 'v', 0x00FC: 'v'}


def find_gb2312freq():
    pats = [
        "prebuilts/clang/host/linux-x86/*/python3/lib/python*/site-packages/"
        "pip/_vendor/chardet/gb2312freq.py",
    ]
    for pat in pats:
        for p in sorted(glob.glob(pat)):
            return p
    return None


def load_freq(path):
    """Return a function char -> frequency rank (lower = more common)."""
    table = None
    if path and os.path.exists(path):
        ns = {}
        with open(path, "r", encoding="utf-8") as f:
            exec(compile(f.read(), path, "exec"), ns)  # noqa: S102 (trusted prebuilt)
        # chardet renamed this across versions; accept either spelling.
        table = ns.get("GB2312_CHAR_TO_FREQ_ORDER") or ns.get("GB2312CharToFreqOrder")
    size = len(table) if table else 0
    if not table:
        raise SystemExit("gb2312 freq table not found; candidate ordering would "
                         "be wrong (check gb2312freq.py variable name)")

    def rank(ch):
        # GB2312 level-1: frequency-ranked via chardet's table.
        try:
            b = ch.encode("gb2312")
            if len(b) == 2 and 0xB0 <= b[0] <= 0xF7 and 0xA1 <= b[1] <= 0xFE:
                idx = (b[0] - 0xB0) * 94 + (b[1] - 0xA1)
                # The table is a permutation of 0..size-1; rank 0 is the MOST
                # frequent char (的), so 0 is valid - do not treat it as missing.
                if table and 0 <= idx < size:
                    return table[idx]
            return 90000           # GB2312 level-2 (less common)
        except UnicodeEncodeError:
            pass
        try:
            ch.encode("gbk")
            return 150000          # GBK-only (uncommon)
        except UnicodeEncodeError:
            return 200000 + ord(ch)  # rare; stable tiebreak by codepoint
    return rank


def strip_tones(py):
    out = []
    for c in py:
        cp = ord(c)
        if cp in U_UML:
            out.append('v')
            continue
        # Decompose and drop combining tone marks; keep base ASCII letter.
        for d in unicodedata.normalize("NFD", c):
            if unicodedata.combining(d):
                continue
            dl = d.lower()
            if 'a' <= dl <= 'z':
                out.append(dl)
    return "".join(out)


# Match transliterator rules of the form  [set]->pinyin;  or  X->pinyin;
# Skip contextual rules (those use { } around the focus) and directives (::).
RULE_RE = re.compile(r'^\s*(\[[^\]]*\]|[^\s{}<>]+?)\s*[→>]\s*([A-Za-zÀ-ɏ'
                     r'Ǖ-ǜüǖǘǚǜ]+)\s*;')


def parse_cldr(path):
    syl_to_hanzi = {}          # toneless syllable -> set(codepoint)
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("#") or s.startswith("::") or s.startswith("<"):
                continue
            if "{" in s or "}" in s:        # contextual rule: skip
                continue
            m = RULE_RE.match(s)
            if not m:
                continue
            left, py = m.group(1), m.group(2)
            syl = strip_tones(py)
            if not syl:
                continue
            # Collect Hanzi from the left side (a [..] set or a single char).
            chars = left[1:-1] if left.startswith("[") else left
            for ch in chars:
                cp = ord(ch)
                if CJK_LO <= cp <= CJK_HI:
                    syl_to_hanzi.setdefault(syl, set()).add(cp)
    return syl_to_hanzi


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cldr", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    rank = load_freq(find_gb2312freq())
    syl_to_hanzi = parse_cldr(args.cldr)

    # Order each syllable's candidates by frequency, cap the list.
    syls = sorted(syl_to_hanzi.keys())
    rows = []
    flat = []
    for syl in syls:
        cps = sorted(syl_to_hanzi[syl], key=lambda c: (rank(chr(c)), c))
        cps = cps[:MAX_CAND]
        rows.append((syl, len(flat), len(cps)))
        flat.extend(cps)

    out = []
    out.append("// AUTO-GENERATED by tools/osk/gen_osk_pinyin.py. DO NOT EDIT.")
    out.append("// Toneless-pinyin -> Hanzi candidate table (CLDR Han-Latin + "
               "GB2312 freq).")
    out.append("")
    out.append("#ifndef GAMMAOS_NANO_OSK_PINYIN_H")
    out.append("#define GAMMAOS_NANO_OSK_PINYIN_H")
    out.append("")
    out.append("#include <cstdint>")
    out.append("")
    out.append("namespace android {")
    out.append("")
    out.append("// Flat array of candidate Hanzi codepoints (BMP, fits uint16).")
    out.append("static const uint16_t kPyHanzi[] = {")
    line = "    "
    for i, cp in enumerate(flat):
        line += "0x%04X," % cp
        if len(line) >= 92:
            out.append(line)
            line = "    "
    if line.strip():
        out.append(line)
    out.append("};")
    out.append("static const int kPyHanziCount = %d;" % len(flat))
    out.append("")
    out.append("// Syllable -> (offset,count) into kPyHanzi. Sorted by syl for "
               "binary search.")
    out.append("struct OskPySyl { char syl[8]; uint16_t off; uint16_t count; };")
    out.append("static const OskPySyl kPySyl[] = {")
    for syl, off, cnt in rows:
        out.append('    { "%s", %d, %d },' % (syl, off, cnt))
    out.append("};")
    out.append("static const int kPySylCount = %d;" % len(rows))
    out.append("")
    out.append("} // namespace android")
    out.append("")
    out.append("#endif // GAMMAOS_NANO_OSK_PINYIN_H")
    out.append("")

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print("Generated %s: %d syllables, %d candidate slots"
          % (args.out, len(rows), len(flat)))


if __name__ == "__main__":
    main()
