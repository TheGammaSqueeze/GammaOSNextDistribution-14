#!/usr/bin/env python3
"""
Refresh (and optionally trim) the MindTheGapps seed APKs bundled in vendor/gapps.

Why this exists
---------------
The gapps prebuilts shipped in vendor/gapps are bootstrap seeds. On a freshly
flashed GSI the seeds are years old (GmsCore in particular), so the moment setup
finishes Play Store/Play Services download a huge update, reinstall it into /data
and re-dexopt it, all while GMS runs its first-run init. On a low-power handheld
that pins the device for a long time.

This tool moves that work to build time:
  * refresh - replace the stale seed APKs with the newest build that still runs on
              the target platform (minSdk <= max_sdk), so the runtime update is a
              small delta or a no-op.
  * trim    - drop optional Google apps from the seed set so there is nothing to
              auto-update / re-dexopt for them on first boot.

Every downloaded APK is verified to be genuinely Google-signed (its signing cert
SHA-256 must equal the pinned Google cert) and to have a versionCode newer than
the seed it replaces, before it is installed. That is what keeps the ;PRESIGNED
entries valid.

Source
------
APKs are fetched from apkcombo.com, which exposes nodpi / multi-abi builds and
does not require Google account credentials. The mirror is untrusted: correctness
is enforced by signer + package-name + minSdk + native-abi checks after download,
not by trusting the source.

Usage
-----
  refresh_gapps_seeds.py --check           # report seed vs latest, download nothing
  refresh_gapps_seeds.py --refresh         # refresh seeds in place
  refresh_gapps_seeds.py --trim            # trim optional apps + regenerate makefiles
  refresh_gapps_seeds.py --refresh --trim  # both (release prep)
  refresh_gapps_seeds.py --refresh --force # reinstall even if not strictly newer
"""

import argparse
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
GAPPS_ROOT = os.path.dirname(HERE)
CONFIG = os.path.join(HERE, "gapps-seeds.json")

UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")
BASE = "https://apkcombo.com"


def log(msg):
    print(msg, flush=True)


def die(msg):
    print("ERROR: " + msg, file=sys.stderr, flush=True)
    sys.exit(1)


def which(*names):
    for n in names:
        p = shutil.which(n)
        if p:
            return p
    # fall back to the in-tree host tools if PATH has nothing
    for n in names:
        cand = os.path.join(GAPPS_ROOT, "..", "..", "out", "host",
                            "linux-x86", "bin", n)
        if os.path.exists(cand):
            return cand
    return None


# Prefer aapt v1: aapt2 dump badging fails on some very large APKs (GmsCore).
AAPT = which("aapt", "aapt2")
APKSIGNER = which("apksigner")


# --------------------------------------------------------------------------- #
# APK inspection
# --------------------------------------------------------------------------- #
def build_of(version_name):
    """Largest integer in a versionName, used as a tiebreaker.

    Some apps (GoogleServicesFramework) keep a static versionCode (34) across
    every build and only bump the build number inside versionName, e.g.
    '14-13361510'. Comparing versionCode alone would wrongly report such an app
    up-to-date, so we fall back to this build number when versionCodes tie.
    """
    if not version_name:
        return 0
    nums = re.findall(r"\d+", version_name)
    return max((int(n) for n in nums), default=0)


def apk_badging(path):
    """Return (package, versionCode, minSdk, [abis], build) for an APK."""
    if not AAPT:
        die("aapt/aapt2 not found; cannot inspect APKs")
    out = subprocess.run([AAPT, "dump", "badging", path],
                         capture_output=True, text=True).stdout
    pkg = re.search(r"package: name='([^']+)'", out)
    vc = re.search(r"versionCode='(\d+)'", out)
    vn = re.search(r"versionName='([^']*)'", out)
    minsdk = re.search(r"sdkVersion:'(\d+)'", out)
    abis = re.findall(r"native-code: (.+)", out)
    abi_list = []
    if abis:
        abi_list = [a.strip().strip("'") for a in abis[0].split()]
    return (pkg.group(1) if pkg else None,
            int(vc.group(1)) if vc else 0,
            int(minsdk.group(1)) if minsdk else 0,
            abi_list,
            build_of(vn.group(1) if vn else ""))


def apk_signer_sha256(path):
    if not APKSIGNER:
        die("apksigner not found; cannot verify APK signatures")
    out = subprocess.run([APKSIGNER, "verify", "--print-certs", path],
                         capture_output=True, text=True).stdout
    m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", out)
    return m.group(1).lower() if m else None


# --------------------------------------------------------------------------- #
# apkcombo fetch
# --------------------------------------------------------------------------- #
def http_get(url, referer=None, binary=False, retries=3):
    last = None
    for attempt in range(retries):
        req = urllib.request.Request(url, headers={
            "User-Agent": UA,
            "Accept": "*/*",
            "Referer": referer or (BASE + "/"),
        })
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
                return data if binary else data.decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 + attempt * 2)
    raise last


_TOKEN = None


def checkin_token():
    global _TOKEN
    if _TOKEN:
        return _TOKEN
    txt = http_get(BASE + "/checkin")
    m = re.search(r"fp=([a-f0-9]+)", txt)
    if not m:
        die("could not obtain apkcombo checkin token")
    _TOKEN = m.group(1)
    return _TOKEN


def parse_variants(pkgpage):
    """Return dicts {vercode, minsdk, dpi, is_xapk, abis, href} for a page.

    apkcombo renders a clean per-variant label inside <a class="variant"> anchors,
    e.g. "Google Play services 26.28.63 (260400-...) (262863035) APK 306 MB
    Android 12+ nodpi", and a matching list of <a href="/r2?u=..."> download links.
    The two lists are 1:1 in document order, so we parse metadata from the labels
    (reliable) and pair them positionally with the download links.
    """
    labels = [
        re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", m.group(1)))).strip()
        for m in re.finditer(r'class="variant"[^>]*>(.*?)</a>', pkgpage, re.S)
    ]
    hrefs = re.findall(r'/r2\?u=[^"\'<> ]+', pkgpage)
    out = []
    for i, href in enumerate(hrefs):
        text = labels[i] if i < len(labels) else ""
        # versionCode is the last parenthetical integer immediately before APK/XAPK
        vm = re.search(r"\((\d+)\)\s+(APK|XAPK)\b", text)
        vc = int(vm.group(1)) if vm else 0
        is_xapk = bool(vm and vm.group(2) == "XAPK")
        mm = re.search(r"Android\s+(\d+)(?:\.\d+)?\s*\+", text)
        minsdk = int(mm.group(1)) if mm else 0
        low = text.lower()
        if "nodpi" in low:
            dpi = "nodpi"
        else:
            dm = re.search(r"(\d{3,4})dpi", low)
            dpi = dm.group(0) if dm else ""
        abis = [tok for tok in ("arm64-v8a", "armeabi-v7a", "armeabi",
                                "x86_64", "x86-64", "x86") if tok in low]
        # build tiebreaker: largest int in the label before the "(vc) APK" token
        head = text[:vm.start()] if vm else text
        build = max((int(n) for n in re.findall(r"\d+", head)), default=0)
        out.append({
            "vercode": vc,
            "build": build,
            "minsdk": minsdk,
            "dpi": dpi,
            "is_xapk": is_xapk,
            "abis": abis,
            "href": href,
        })
    return out


def api_minsdk_to_platform(minsdk_label):
    """apkcombo prints 'Android N+' as a marketing version, not an API level."""
    table = {8: 26, 9: 28, 10: 29, 11: 30, 12: 31, 13: 33, 14: 34, 15: 35, 16: 36}
    return table.get(minsdk_label, 99 if minsdk_label else 0)


def select_variant(variants, cfg, arch, max_sdk, abi_tokens):
    want_dpi = cfg.get("dpi", "nodpi")
    want_abis = abi_tokens.get(arch, []) if cfg.get("arch_specific") else []
    best = None
    for v in variants:
        if v["is_xapk"]:
            continue
        if want_dpi and v["dpi"] and v["dpi"] != want_dpi:
            continue
        if api_minsdk_to_platform(v["minsdk"]) > max_sdk:
            continue
        if want_abis:
            # accept if the variant advertises no abi (fat/nodpi) or one we want
            if v["abis"] and not any(a in v["abis"] for a in want_abis):
                continue
        if not best or (v["vercode"], v["build"]) > (best["vercode"], best["build"]):
            best = v
    return best


def download_variant(slug, pkg, href):
    referer = f"{BASE}/{slug}/{pkg}/download/apk"
    url = f"{BASE}{href}&fp={checkin_token()}"
    return http_get(url, referer=referer, binary=True)


def fetch_page(slug, pkg):
    return http_get(f"{BASE}/{slug}/{pkg}/download/apk",
                    referer=BASE + "/")


# --------------------------------------------------------------------------- #
# refresh
# --------------------------------------------------------------------------- #
def seed_version(path):
    """Return (versionCode, build) for a seed, or None if absent."""
    if not os.path.exists(path):
        return None
    _pkg, vc, _min, _abis, build = apk_badging(path)
    return (vc, build)


STAGING = os.path.join(HERE, "staging")


def staged_apk_for(package):
    """Return the path of a manually-dropped APK matching `package`, if any.

    apkcombo (and other consumer mirrors) gate downloads by the visitor's device
    profile, so they will not reliably serve an arm64-v8a build of ABI-critical
    apps like GmsCore for a given API level. For those, drop the exact
    'arm64-v8a + nodpi' APK from APKMirror into vendor/gapps/tools/staging/ and it
    is used instead of the network fetch. It still passes the same
    signer/package/abi/minSdk/newer checks before being installed.
    """
    if not os.path.isdir(STAGING):
        return None
    for fn in sorted(os.listdir(STAGING)):
        if not fn.lower().endswith(".apk"):
            continue
        p = os.path.join(STAGING, fn)
        try:
            if apk_badging(p)[0] == package:
                return p
        except Exception:  # noqa: BLE001
            continue
    return None


def install_verified(blob_or_path, entry, conf, arch, seed_path, cur, force,
                     is_path=False):
    """Verify an APK (signer/package/minSdk/abi/newer) and install it. True on install."""
    name = entry["name"]
    if is_path:
        tmp = blob_or_path
        cleanup = False
    else:
        tf = tempfile.NamedTemporaryFile(suffix=".apk", delete=False)
        tf.write(blob_or_path)
        tf.close()
        tmp = tf.name
        cleanup = True
    try:
        pkg, vc, minsdk, abis, build = apk_badging(tmp)
        if pkg != entry["package"]:
            log(f"  [{name}/{arch}] REJECT: package {pkg} != {entry['package']}")
            return False
        if minsdk > conf["max_sdk"]:
            log(f"  [{name}/{arch}] REJECT: minSdk {minsdk} > {conf['max_sdk']}")
            return False
        sig = apk_signer_sha256(tmp)
        if sig != conf["signer_sha256"].lower():
            log(f"  [{name}/{arch}] REJECT: signer {sig} != Google cert")
            return False
        if entry.get("arch_specific") and abis:
            want = conf["abi_tokens"].get(arch, [])
            if want and not any(a in abis for a in want):
                log(f"  [{name}/{arch}] REJECT: abis {abis} lack {want}")
                return False
        cand = (vc, build)
        if cur is not None and cand < cur and not force:
            log(f"  [{name}/{arch}] REJECT: {cand} older than seed {cur}")
            return False
        if cur is not None and cand == cur and not force:
            log(f"  [{name}/{arch}] already at vc={vc} build={build}")
            return False
        os.makedirs(os.path.dirname(seed_path), exist_ok=True)
        shutil.copyfile(tmp, seed_path)
        sz = os.path.getsize(tmp) // (1024 * 1024)
        log(f"  [{name}/{arch}] INSTALLED versionCode {vc} ({sz} MB, "
            f"abis={abis or 'noarch'}, signer OK)")
        return True
    finally:
        if cleanup:
            os.unlink(tmp)


def refresh_entry(entry, conf, do_download, force):
    name = entry["name"]
    arches = conf["arches"] if entry.get("arch_specific") else ["common"]

    # Prefer a manually-staged APK (for ABI-critical apps the mirror won't serve).
    staged = staged_apk_for(entry["package"])
    if staged:
        log(f"  [{name}] using staged APK {os.path.basename(staged)}")
        changed = False
        for arch in arches:
            rel = entry["paths"].get(arch)
            if not rel:
                continue
            seed_path = os.path.join(GAPPS_ROOT, rel)
            cur = seed_version(seed_path)
            if do_download and install_verified(staged, entry, conf, arch,
                                                seed_path, cur, force, is_path=True):
                changed = True
        return changed

    # download page once per package
    try:
        page = fetch_page(entry["slug"], entry["package"])
    except Exception as e:  # noqa: BLE001
        log(f"  [{name}] FETCH FAILED: {e}")
        return False
    variants = parse_variants(page)
    if not variants:
        log(f"  [{name}] no variants parsed (site layout may have changed)")
        return False

    changed = False
    for arch in arches:
        rel = entry["paths"].get(arch)
        if not rel:
            continue
        seed_path = os.path.join(GAPPS_ROOT, rel)
        cur = seed_version(seed_path)
        v = select_variant(variants, entry, arch, conf["max_sdk"],
                           conf["abi_tokens"])
        if not v:
            log(f"  [{name}/{arch}] no compatible variant (minSdk<= {conf['max_sdk']})")
            continue
        cand = (v["vercode"], v["build"])
        newer = (cur is None) or (cand > cur) or force
        status = "update" if (cur is None or cand > cur) else "up-to-date"
        seedstr = f"{cur[0]}/{cur[1]}" if cur else "none"
        log(f"  [{name}/{arch}] seed={seedstr} latest={v['vercode']}/{v['build']} "
            f"minSdk={v['minsdk']}+ dpi={v['dpi'] or '-'} -> {status}")
        if not do_download or not newer:
            continue

        blob = download_variant(entry["slug"], entry["package"], v["href"])
        if install_verified(blob, entry, conf, arch, seed_path, cur, force):
            changed = True
    return changed


# --------------------------------------------------------------------------- #
# trim
# --------------------------------------------------------------------------- #
PROP_FILES = [
    "proprietary-files-common.txt",
    "proprietary-files-common-nongrouper.txt",
    "proprietary-files-arm.txt",
    "proprietary-files-arm-nongrouper.txt",
    "proprietary-files-arm64.txt",
    "proprietary-files-arm64-nongrouper.txt",
    "proprietary-files-x86_64.txt",
    "proprietary-files-x86_64-nongrouper.txt",
]


def trim_entries(trim):
    removed_any = False
    for name in [t["name"] for t in trim]:
        pass
    for pf in PROP_FILES:
        path = os.path.join(GAPPS_ROOT, pf)
        if not os.path.exists(path):
            continue
        lines = open(path, encoding="utf-8").read().splitlines()
        keep = []
        dropped = []
        for ln in lines:
            if any(t["match"] in ln for t in trim):
                dropped.append(ln)
            else:
                keep.append(ln)
        if dropped:
            with open(path, "w", encoding="utf-8") as f:
                f.write("\n".join(keep) + "\n")
            for d in dropped:
                log(f"  dropped from {pf}: {d.split(';')[0]}")
            removed_any = True
    # delete the on-disk APK dirs for trimmed apps
    for t in trim:
        # match is e.g. app/talkback/talkback.apk -> the containing dir
        leaf = t["match"].rstrip("/")
        appdir = os.path.dirname(leaf)  # app/talkback
        for root, dirs, _files in os.walk(GAPPS_ROOT):
            base = os.path.basename(appdir)
            if os.path.basename(root) == base and appdir.replace("\\", "/") in \
                    root.replace("\\", "/"):
                shutil.rmtree(root, ignore_errors=True)
                log(f"  deleted {os.path.relpath(root, GAPPS_ROOT)}")
    return removed_any


def regenerate_makefiles():
    script = os.path.join(GAPPS_ROOT, "setup-makefiles.sh")
    log("  regenerating makefiles (setup-makefiles.sh)")
    r = subprocess.run(["bash", script], cwd=GAPPS_ROOT,
                       capture_output=True, text=True)
    if r.returncode != 0:
        log("  setup-makefiles.sh FAILED:")
        log(r.stdout[-2000:])
        log(r.stderr[-2000:])
        log("  NOTE: regenerate manually from a sourced build env:")
        log("        (cd vendor/gapps && bash setup-makefiles.sh)")
        return False
    log("  makefiles regenerated")
    return True


# --------------------------------------------------------------------------- #
def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true",
                    help="report seed vs latest, download nothing")
    ap.add_argument("--refresh", action="store_true",
                    help="refresh seed APKs in place")
    ap.add_argument("--trim", action="store_true",
                    help="remove optional apps + regenerate makefiles")
    ap.add_argument("--force", action="store_true",
                    help="reinstall even if not strictly newer")
    ap.add_argument("--only", default=None,
                    help="restrict refresh to a single app by name (e.g. GmsCore)")
    ap.add_argument("--config", default=CONFIG)
    args = ap.parse_args()

    if not (args.check or args.refresh or args.trim):
        ap.print_help()
        sys.exit(2)

    conf = json.load(open(args.config))

    if args.check or args.refresh:
        log("== Refresh seeds ==")
        do_dl = args.refresh and not args.check
        touched = False
        for entry in conf["refresh"]:
            if args.only and entry["name"].lower() != args.only.lower():
                continue
            if refresh_entry(entry, conf, do_dl, args.force):
                touched = True
        if args.refresh:
            log("Refresh " + ("made changes." if touched else "made no changes."))

    if args.trim:
        log("== Trim optional apps ==")
        if trim_entries(conf["trim"]):
            regenerate_makefiles()
        else:
            log("  nothing to trim (already removed)")

    log("Done.")


if __name__ == "__main__":
    main()
