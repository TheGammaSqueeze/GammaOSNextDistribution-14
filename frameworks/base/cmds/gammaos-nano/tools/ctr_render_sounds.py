#!/usr/bin/env python3
"""Renders the 3DS HOME Menu sequence sounds to WAV for the nano 3DS theme.

Drives the 3ds-web app (its own CSEQ player, docs/menu.md "Sequence player") in headless
Chromium through window.__audio.render. The app copy must log loop jumps through
globalThis.__seqJump(track, from, target, time, loopCount) (see STATE.md for the two line
patch), so looping sounds get an exact loop: the render covers the intro plus two passes of
the loop, and the loop body written is the second pass (steady state, it carries the tails of
the previous pass), with its sample range stored in a "smpl" chunk.

One shot sounds are rendered for up to 6 s and trimmed after the last audible sample.
Wave sounds (plain WAVs in the romfs) are copied as they are.
"""

import argparse
import base64
import io
import json
import os
import struct
import sys
import wave

from playwright.sync_api import sync_playwright

RATE = 44100


def read_wav(data):
    with wave.open(io.BytesIO(data)) as w:
        ch = w.getnchannels()
        frames = w.readframes(w.getnframes())
        return ch, w.getframerate(), frames


def write_wav(path, ch, rate, frames, loop=None):
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(ch)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(frames)
    data = bytearray(out.getvalue())
    if loop is not None:
        start, end = loop
        smpl = struct.pack("<IIIIIIIII", 0, 0, int(1e9 / rate), 60, 0, 0, 0, 1, 0)
        smpl += struct.pack("<IIIIII", 0, 0, start, end - 1, 0, 0)
        data += b"smpl" + struct.pack("<I", len(smpl)) + smpl
        struct.pack_into("<I", data, 4, len(data) - 8)
    with open(path + ".tmp", "wb") as f:
        f.write(data)
    os.replace(path + ".tmp", path)


def trim(ch, frames, threshold=8):
    n = len(frames) // (2 * ch)
    samples = struct.unpack("<%dh" % (n * ch), frames)
    last = 0
    for i in range(n * ch - 1, -1, -1):
        if abs(samples[i]) > threshold:
            last = i // ch + 1
            break
    last = min(n, last + int(RATE * 0.02))
    return frames[: last * 2 * ch]


TICK = 60.0 / (120 * 48)


def loop_of(jumps):
    """(start seconds, period seconds) of a sound whose tracks loop forever, or None.

    Every track that jumps backward forever has a period (time between two passes of the
    same jump). The whole sound repeats with the least common multiple of those periods once
    every track has passed its jump once; the body starts at the latest first pass, so the
    state at its start and at its end are the same."""
    tracks = {}
    for j in jumps:
        if len(j) != 5 or j[2] is None or j[1] is None:
            continue
        t, frm, to, time, count = j
        if to >= frm or count not in (-1, 0):
            continue
        tracks.setdefault((t, frm, to), []).append(time)
    first = {}
    for (t, frm, to), times in tracks.items():
        if len(times) < 2 or t in first:
            continue
        first[t] = (times[0], times[1] - times[0])
    if not first:
        return None
    from math import gcd
    lcm = 1
    for start, period in first.values():
        ticks = max(1, int(round(period / TICK)))
        lcm = lcm * ticks // gcd(lcm, ticks)
    start = max(s for s, _ in first.values())
    return start, lcm * TICK


def still_sounding(b64, tail=0.25, threshold=64):
    """True when the render is still audible in its last `tail` seconds (cut off, not ended)."""
    ch, rate, frames = read_wav(base64.b64decode(b64))
    n = len(frames) // (2 * ch)
    start = max(0, n - int(tail * rate))
    samples = struct.unpack("<%dh" % ((n - start) * ch), frames[start * 2 * ch:])
    return max((abs(v) for v in samples), default=0) > threshold


def has_forever(jumps):
    return any(len(j) == 5 and j[1] is not None and j[2] is not None and j[2] < j[1] and j[4] in (-1, 0)
               for j in jumps)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:5199/?fresh")
    ap.add_argument("--romfs", default="/work/3ds/web/app/public/romfs")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "ctr3ds", "sound"))
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    manifest = json.load(open(os.path.join(args.romfs, "sound", "sounds.json")))
    sounds = manifest["sounds"]
    only = set(filter(None, args.only.split(",")))
    report = {}
    with sync_playwright() as p:
        browser = p.chromium.launch(args=["--autoplay-policy=no-user-gesture-required"])
        page = browser.new_page()
        page.goto(args.url)
        page.wait_for_function("window.__audio !== undefined", timeout=60000)
        page.evaluate("window.__audio.preload()")
        for s in sounds:
            name = s["name"]
            if only and name not in only:
                continue
            if s["type"] == "wave":
                src = s.get("wave", {}).get("path") or s.get("file")
                report[name] = {"type": "wave", "src": src}
                continue
            if s["type"] != "sequence":
                report[name] = {"type": s["type"], "skipped": True}
                continue
            page.evaluate("window.__jumps = []; globalThis.__seqJump = (t, f, to, time, loop) => window.__jumps.push([t, f, to, time, loop === undefined ? -1 : loop]);")
            seconds = 6.0
            seconds = 6.0
            b64 = page.evaluate("([n, s]) => window.__audio.render(n, s, 1)", [name, seconds])
            jumps = page.evaluate("window.__jumps")
            loop = loop_of(jumps)
            if loop is None and (has_forever(jumps) or still_sounding(b64)):
                # a loop longer than the first render: render long enough to see two passes
                seconds = 200.0
                page.evaluate("window.__jumps = []")
                b64 = page.evaluate("([n, s]) => window.__audio.render(n, s, 1)", [name, seconds])
                loop = loop_of(page.evaluate("window.__jumps"))
            if loop and loop[1] > 60.0:
                # tracks loop with unrelated periods: a sample loop would be minutes long,
                # these play through the runtime sequencer instead
                report[name] = {"type": "sequence", "runtime": True, "period": loop[1]}
                print(name, report[name], flush=True)
                continue
            if loop:
                need = loop[0] + 2 * loop[1] + 0.5
                if need > seconds:
                    seconds = need
                    page.evaluate("window.__jumps = []")
                    b64 = page.evaluate("([n, s]) => window.__audio.render(n, s, 1)", [name, seconds])
                    loop = loop_of(page.evaluate("window.__jumps"))
                start, period = loop
                ch, rate, frames = read_wav(base64.b64decode(b64))
                a = int(round(start * rate))
                b = a + int(round(period * rate))
                write_wav(os.path.join(out, name + ".wav"), ch, rate, frames[: b * 2 * ch], loop=(a, b))
                report[name] = {"type": "sequence", "loop": [a, b], "seconds": b / rate}
            else:
                ch, rate, frames = read_wav(base64.b64decode(b64))
                frames = trim(ch, frames)
                write_wav(os.path.join(out, name + ".wav"), ch, rate, frames)
                report[name] = {"type": "sequence", "seconds": len(frames) / (2 * ch * rate)}
            print(name, report[name], flush=True)
        browser.close()
    with open(os.path.join(out, "report.json"), "w") as f:
        json.dump(report, f, indent=1)


if __name__ == "__main__":
    sys.exit(main())
