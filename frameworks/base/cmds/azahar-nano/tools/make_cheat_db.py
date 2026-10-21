#!/usr/bin/env python3
"""Build azahar-nano's 3DS cheat database (assets/cheats3ds.zip) from the public
Action Replay / Gateway code collections:

  CTRPF   https://github.com/iSharingan/CTRPF-AR-CHEAT-CODES   Cheats/<game>/<TID>.txt
  60FPS   https://github.com/Reshiban/60FPS-AR-CHEATS-3DS       Cheats/<game>/<TID>.txt
  Sharkive https://github.com/FlagBrew/Sharkive                 3ds/<TID>.txt

Usage: make_cheat_db.py --ctrpf <dir> --fps60 <dir> --sharkive <dir> -o assets/cheats3ds.zip

The zip holds one <TID>.txt per title in the format Azahar's GatewayCheat::LoadFile reads:

  [Cheat name]
  *comment line
  XXXXXXXX YYYYYYYY

The files are merged per title (same name and code from two sources is one cheat; the same
name with another code keeps both, the later one tagged with its source), section headers
without code ([++FPS++]) are dropped and CTRPF's {note} lines become comments. azahar-nano
merges this database with the user's own cheat files when a game starts (CheatDb.cpp).
"""
import argparse
import os
import re
import sys
import zipfile

CODE_LINE = re.compile(r'^[0-9A-Fa-f]{8} [0-9A-Fa-f]{8}$')
TID_FILE = re.compile(r'^([0-9A-Fa-f]{16})\.txt$')
ENABLED = '*citra_enabled'


def parse_file(path):
    """Yields (name, comments[], code_lines[]) for every cheat in a CTRPF/Gateway text."""
    cheats = []
    name = None
    comments = []
    code = []
    dropped = 0

    def flush():
        nonlocal name, comments, code, dropped
        if name is not None:
            if code:
                cheats.append((name, comments, code))
            else:
                dropped += 1
        name, comments, code = None, [], []

    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        for raw in f:
            line = raw.replace('\0', '').strip()
            if not line:
                continue
            if line.startswith('[') and line.endswith(']') and len(line) >= 2:
                flush()
                name = line[1:-1].strip()
                continue
            if name is None:
                continue
            if line.startswith('{') and line.endswith('}'):
                note = line[1:-1].strip()
                if note:
                    comments.append(note)
            elif line.startswith('*'):
                if line != ENABLED:
                    comments.append(line[1:].strip())
            elif line.startswith('#') or line.startswith('//'):
                comments.append(line.lstrip('#/ ').strip())
            else:
                parts = line.upper().split()
                if len(parts) == 2 and CODE_LINE.match(' '.join(parts)):
                    code.append(' '.join(parts))
                elif len(parts) == 1 and len(parts[0]) == 16 and all(c in '0123456789ABCDEF' for c in parts[0]):
                    code.append(parts[0][:8] + ' ' + parts[0][8:])
                else:
                    comments.append(line)
    flush()
    return cheats, dropped


def collect(root, source, pattern_dirs):
    """{TID: [(name, comments, code), ...]} from a tree of <TID>.txt files."""
    out = {}
    dropped = 0
    if not root:
        return out, dropped
    for dirpath, _, files in os.walk(root):
        if pattern_dirs and os.path.normpath(dirpath) == os.path.normpath(root):
            continue
        for fn in files:
            m = TID_FILE.match(fn)
            if not m:
                continue
            tid = m.group(1).upper()
            cheats, d = parse_file(os.path.join(dirpath, fn))
            dropped += d
            for name, comments, code in cheats:
                out.setdefault(tid, []).append((name, comments, code, source))
    return out, dropped


def merge(sources):
    """sources: list of (label, {TID: [...]}) in priority order."""
    db = {}
    for label, entries in sources:
        for tid, cheats in entries.items():
            have = db.setdefault(tid, [])
            for name, comments, code, src in cheats:
                key = (name.lower(), tuple(code))
                if any(k == key for k, _ in [(((c[0].lower(), tuple(c[2]))), c) for c in have]):
                    continue
                final = name
                if any(c[0].lower() == name.lower() for c in have):
                    final = f'{name} ({src})'
                have.append((final, comments, code, src))
    return db


def render(cheats):
    out = []
    for name, comments, code, src in cheats:
        out.append('[' + name + ']')
        for c in comments:
            out.append('*' + c)
        out.append('*Source: ' + src)
        out.extend(code)
        out.append('')
    return '\n'.join(out) + '\n'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ctrpf')
    ap.add_argument('--fps60')
    ap.add_argument('--sharkive')
    ap.add_argument('-o', '--out', required=True)
    a = ap.parse_args()
    ctrpf, d1 = collect(a.ctrpf, 'CTRPF', True)
    fps60, d2 = collect(a.fps60, '60FPS', True)
    shark, d3 = collect(a.sharkive, 'Sharkive', False)
    db = merge([('CTRPF', ctrpf), ('60FPS', fps60), ('Sharkive', shark)])
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    total = 0
    with zipfile.ZipFile(a.out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for tid in sorted(db):
            text = render(db[tid])
            total += len(db[tid])
            info = zipfile.ZipInfo(tid + '.txt', date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, text)
    print(f'{len(db)} titles, {total} cheats (CTRPF {sum(len(v) for v in ctrpf.values())}, '
          f'60FPS {sum(len(v) for v in fps60.values())}, Sharkive {sum(len(v) for v in shark.values())}); '
          f'{d1 + d2 + d3} codeless headers dropped; {os.path.getsize(a.out)} bytes -> {a.out}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
