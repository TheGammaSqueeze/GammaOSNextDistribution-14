/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// XMB system definitions, ROM scanning, game launch, recently played,
// on-screen keyboard (search), and XMB rendering.

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <thread>
#include <mutex>
#include <set>
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <math.h>
#include <strings.h>
#include <inttypes.h>
#include <errno.h>
#include <stdio.h>

#include <cutils/properties.h>
#include <android-base/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <GLES2/gl2.h>

#include "NanoMenuDrm.h"
#include "NanoMenuUtils.h"
#include "NanoMenu.h"
#include "NanoMenuShaders.h"
#include "NanoJson.h"

namespace android {

// ---------------------------------------------------------------------------
// XMB system definitions (from Daijisho database)
// ---------------------------------------------------------------------------

struct SystemDef {
    const char* name;
    const char* shortname;
    const char* romDir;       // Directory name under /sdcard/ROMs/
    const char* coreSo;       // RetroArch core .so filename (empty for standalone)
    const char* launchPkg;    // Package name for standalone emulators (empty for RetroArch)
    const char* launchIntent; // Intent template for standalone ({file.uri} placeholder)
    float r, g, b;            // Icon color
    const char* acceptExts;   // Comma-separated accepted extensions (lowercase, with dots)
};

// For RetroArch cores: coreSo is set, launchPkg/launchIntent are empty
// For standalone emulators: coreSo is empty, launchPkg+launchIntent are set
// Intent uses {file.uri} as placeholder for the ROM URI
static const SystemDef kXmbSystemDefs[] = {
    {"NES",             "NES",  "nes",          "nestopia_libretro_android.so",               "", "",
     0.89f, 0.00f, 0.06f, ".nes,.fds,.unf,.unif"},
    {"SNES",            "SNES", "snes",         "snes9x_libretro_android.so",                "", "",
     0.48f, 0.49f, 0.49f, ".smc,.sfc,.fig,.swc"},
    {"Game Boy",        "GB",   "gb",           "gambatte_libretro_android.so",              "", "",
     0.61f, 0.73f, 0.06f, ".gb"},
    {"Game Boy Color",  "GBC",  "gbc",          "gambatte_libretro_android.so",              "", "",
     0.42f, 0.25f, 0.63f, ".gbc,.gb"},
    {"Game Boy Advance","GBA",  "gba",          "gpsp_libretro_android.so",                  "", "",
     0.36f, 0.25f, 0.63f, ".gba"},
    {"Nintendo 64",     "N64",  "n64",          "mupen64plus_next_gles3_libretro_android.so", "", "",
     0.00f, 0.60f, 0.00f, ".n64,.v64,.z64,.ndd"},
    {"Nintendo DS",     "NDS",  "nds",          "",
     "com.dsemu.drastic",
     "-n com.dsemu.drastic/.DraSticActivity -a android.intent.action.VIEW -d {file.uri} --activity-clear-task --activity-clear-top",
     0.63f, 0.63f, 0.63f, ".nds"},
    {"Genesis",         "GEN",  "genesis",      "genesis_plus_gx_libretro_android.so",       "", "",
     0.00f, 0.38f, 0.66f, ".md,.gen,.smd,.bin"},
    {"Master System",   "SMS",  "mastersystem", "genesis_plus_gx_libretro_android.so",       "", "",
     0.78f, 0.00f, 0.00f, ".sms,.sg"},
    {"Game Gear",       "GG",   "gamegear",     "genesis_plus_gx_libretro_android.so",       "", "",
     0.09f, 0.09f, 0.85f, ".gg"},
    {"PlayStation",     "PSX",  "psx",          "pcsx_rearmed_libretro_android.so",          "", "",
     0.00f, 0.19f, 0.53f, ".cue,.pbp,.chd,.iso,.m3u,.img"},
    {"PSP",             "PSP",  "psp",          "",
     "org.ppsspp.ppsspp",
     "-n org.ppsspp.ppsspp/.PpssppActivity -a android.intent.action.VIEW -d {file.uri} -t application/octet-stream --activity-clear-task --activity-clear-top",
     0.10f, 0.10f, 0.10f, ".iso,.cso,.pbp"},
    {"Dreamcast",       "DC",   "dreamcast",    "",
     "com.flycast.emulator",
     "-n com.flycast.emulator/com.flycast.emulator.MainActivity -a android.intent.action.VIEW -d {file.uri}",
     1.00f, 0.50f, 0.00f, ".cdi,.gdi,.chd,.cue"},
    {"Neo Geo Pocket",  "NGP",  "ngpc",         "mednafen_ngp_libretro_android.so",          "", "",
     0.50f, 0.50f, 0.50f, ".ngp,.ngc,.npc"},
    {"PICO-8",          "P-8",  "pico8",        "fake08_libretro_android.so",                "", "",
     1.00f, 0.00f, 0.30f, ".p8,.png"},
};
static const int kNumXmbSystemDefs = sizeof(kXmbSystemDefs) / sizeof(kXmbSystemDefs[0]);

// Defined further down (next to the scan code); forward-declared here so
// buildScanCandidates (which precedes it) can use it.
static std::string findCaseInsensitive(const std::string& parent, const std::string& target);

// Font layout constants come from NanoMenuShaders.h (shared with NanoMenu.cpp).

// ---------------------------------------------------------------------------
// XMB System Initialization
// ---------------------------------------------------------------------------

void NanoMenu::initXmbSystems() {
    mXmbSystems.clear();

    // Migrate any legacy cache dir ownership: older nano builds ran as
    // uid graphics and left /data/system/nano_xmb_cache/ as 0700
    // graphics:graphics, which blocks the current root-uid nano from
    // traversing it when DAC_OVERRIDE is absent. Force to root:root with
    // 0755 on the directory and 0644 on contents so subsequent boots
    // work regardless of capability set. No-op if already correct.
    {
        const char* cacheDir = "/data/system/nano_xmb_cache";
        struct stat dst;
        if (stat(cacheDir, &dst) == 0) {
            if (dst.st_uid != 0 || dst.st_gid != 0)
                (void)chown(cacheDir, 0, 0);
            if ((dst.st_mode & 0777) != 0755)
                (void)chmod(cacheDir, 0755);
            DIR* d = opendir(cacheDir);
            if (d) {
                struct dirent* e;
                while ((e = readdir(d)) != nullptr) {
                    if (e->d_name[0] == '.') continue;
                    std::string fp = std::string(cacheDir) + "/" + e->d_name;
                    struct stat fst;
                    if (stat(fp.c_str(), &fst) == 0) {
                        if (fst.st_uid != 0 || fst.st_gid != 0)
                            (void)chown(fp.c_str(), 0, 0);
                        if ((fst.st_mode & 0777) != 0644)
                            (void)chmod(fp.c_str(), 0644);
                    }
                }
                closedir(d);
            }
        }
    }

    // Load the persisted dynamic-systems config from DE storage
    // (/data/system/nano_systems.json), readable at early boot before user
    // unlock. On first run / missing file / parse error, seed it from the
    // built-in defaults so behavior is byte-for-byte unchanged.
    if (!loadSystemsConfig()) {
        seedSystemsConfig();
    }

    for (auto& sys : mXmbSystems) {
        // Legacy per-system prop overrides keyed on the original built-in
        // romDir (== id). Props win, preserving today's precedence even after
        // the JSON config exists. Custom systems have no such props.
        if (sys.builtin) {
            char propBuf[PROPERTY_VALUE_MAX] = {};
            char propKey[160];
            snprintf(propKey, sizeof(propKey),
                     "persist.gammaos.nano.xmb.%s.dir", sys.id.c_str());
            property_get(propKey, propBuf, "");
            if (propBuf[0]) sys.romDir = propBuf;

            snprintf(propKey, sizeof(propKey),
                     "persist.gammaos.nano.xmb.%s.core", sys.id.c_str());
            property_get(propKey, propBuf, "");
            if (propBuf[0]) sys.coreSo = propBuf;
        }

        // Disabled systems are never scanned and never appear in Game; they
        // hold no ROM cache in memory.
        if (!sys.enabled) continue;

        // Load this system's cached ROM list (DE storage, keyed on stable id).
        loadRomCacheForSystem(sys);
    }

    int enabledCount = 0;
    for (const auto& s : mXmbSystems) if (s.enabled) enabledCount++;
    ALOGD("NanoMenu: initialized %d XMB systems (%d enabled)",
          (int)mXmbSystems.size(), enabledCount);

    // If all enabled systems loaded from cache, mark scan as done. Disabled
    // systems are excluded from the gate (they are never scanned).
    bool allCached = true;
    for (const auto& s : mXmbSystems) {
        if (s.enabled && !s.scanned) { allCached = false; break; }
    }
    if (allCached) mXmbRomScanDone = true;
}

// Load a system's cached ROM list from DE storage, keyed on the stable id
// (built-in id == romDir so existing {romDir}.list files are reused). Cache
// format: each line is a full ROM path. Legacy compat: if line 1 is a directory
// and remaining lines are bare filenames, prepend the directory to each. Clears
// then repopulates roms/displayNames/activePath and marks the system scanned if
// any ROMs were loaded (so early boot does not clear them before the bg rescan).
void NanoMenu::loadRomCacheForSystem(XmbSystem& sys) {
    sys.roms.clear();
    sys.displayNames.clear();
    std::string cachePath = xmbCachePath(sys);
    int cfd = open(cachePath.c_str(), O_RDONLY);
    if (cfd >= 0) {
        struct stat cst;
        if (fstat(cfd, &cst) == 0 && cst.st_size > 0 && cst.st_size < 512 * 1024) {
            std::string content(cst.st_size, '\0');
            ssize_t rd = read(cfd, &content[0], cst.st_size);
            if (rd > 0) {
                content.resize(rd);
                std::vector<std::string> lines;
                size_t pos = 0;
                while (pos < content.size()) {
                    size_t eol = content.find('\n', pos);
                    if (eol == std::string::npos) eol = content.size();
                    std::string line = content.substr(pos, eol - pos);
                    pos = eol + 1;
                    if (!line.empty()) lines.push_back(std::move(line));
                }
                // Detect legacy format: first line is a directory, rest bare.
                std::string dirPrefix;
                if (lines.size() >= 2 && lines[0].find('/') != std::string::npos) {
                    bool allBare = true;
                    for (size_t i = 1; i < lines.size(); i++) {
                        if (lines[i].find('/') != std::string::npos) { allBare = false; break; }
                    }
                    if (allBare) {
                        dirPrefix = lines[0];
                        if (!dirPrefix.empty() && dirPrefix.back() == '/') dirPrefix.pop_back();
                        lines.erase(lines.begin());
                        ALOGD("NanoMenu: %s: legacy cache format, dir=%s",
                              sys.name.c_str(), dirPrefix.c_str());
                    }
                }
                for (auto& l : lines) {
                    if (!dirPrefix.empty()) sys.roms.push_back(dirPrefix + "/" + l);
                    else                    sys.roms.push_back(std::move(l));
                }
                if (!sys.roms.empty()) {
                    size_t ls = sys.roms[0].rfind('/');
                    if (ls != std::string::npos) sys.activePath = sys.roms[0].substr(0, ls);
                    sys.pathExists = true;
                }
                for (const auto& rom : sys.roms) {
                    std::string dn = rom;
                    size_t sl = dn.rfind('/');
                    if (sl != std::string::npos) dn = dn.substr(sl + 1);
                    size_t d = dn.rfind('.');
                    if (d != std::string::npos) dn = dn.substr(0, d);
                    sys.displayNames.push_back(std::move(dn));
                }
                if (!sys.roms.empty())
                    ALOGD("NanoMenu: %s: loaded %zu ROMs from cache",
                          sys.name.c_str(), sys.roms.size());
            }
        }
        close(cfd);
    }
    // If cache had ROMs, mark as scanned so early boot does not clear them.
    if (!sys.roms.empty()) sys.scanned = true;
}

// ---------------------------------------------------------------------------
// Dynamic systems config (/data/system/nano_systems.json) - see the
// dynamic-game-systems plan. DE storage, JSON schema mirrors Daijishou.
// ---------------------------------------------------------------------------

// Split a comma-separated extension list ("\.nes,.fds") into a JSON array.
static njson::Value extsToJsonArray(const std::string& extStr) {
    njson::Value a = njson::Value::makeArray();
    size_t pos = 0;
    while (pos < extStr.size()) {
        size_t comma = extStr.find(',', pos);
        if (comma == std::string::npos) comma = extStr.size();
        std::string ext = extStr.substr(pos, comma - pos);
        while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
        while (!ext.empty() && ext.back() == ' ') ext.pop_back();
        if (!ext.empty()) a.arr.push_back(njson::Value::makeString(ext));
        pos = comma + 1;
    }
    return a;
}

// Join a JSON extensions array back into the comma-separated runtime form.
static std::string jsonArrayToExts(const njson::Value& a) {
    std::string out;
    if (!a.isArray()) return out;
    for (const auto& e : a.arr) {
        if (!e.isString() || e.str.empty()) continue;
        if (!out.empty()) out += ",";
        out += e.str;
    }
    return out;
}

static const char* launchTypeToStr(int lt) {
    switch (lt) {
        case NanoMenu::XLT_RETROARCH_INTENT: return "retroarch-intent";
        case NanoMenu::XLT_CUSTOM_PACKAGE:   return "custom-package";
        case NanoMenu::XLT_LIBRETRO_CORE:
        default:                             return "libretro-core";
    }
}

static int launchTypeFromStr(const std::string& s) {
    if (s == "custom-package")   return NanoMenu::XLT_CUSTOM_PACKAGE;
    if (s == "retroarch-intent") return NanoMenu::XLT_RETROARCH_INTENT;
    return NanoMenu::XLT_LIBRETRO_CORE;
}

// Serialize one runtime system to its JSON object form.
static njson::Value systemToJson(const NanoMenu::XmbSystem& sys) {
    njson::Value o = njson::Value::makeObject();
    o.set("id") = njson::Value::makeString(sys.id);
    o.set("builtin") = njson::Value::makeBool(sys.builtin);
    o.set("enabled") = njson::Value::makeBool(sys.enabled);
    o.set("order") = njson::Value::makeNumber(sys.order);
    o.set("displayName") = njson::Value::makeString(sys.name);
    o.set("shortname") = njson::Value::makeString(sys.shortname);
    o.set("romDir") = njson::Value::makeString(sys.romDir);
    o.set("launchType") = njson::Value::makeString(launchTypeToStr(sys.launchType));
    o.set("coreSo") = njson::Value::makeString(sys.coreSo);
    o.set("package") = njson::Value::makeString(sys.packageName);
    o.set("launchArgs") = njson::Value::makeString(sys.launchArgs);
    o.set("intentTemplate") = njson::Value::makeString(sys.launchIntent);
    njson::Value srcs = njson::Value::makeArray();
    for (const auto& s : sys.scanSources) {
        njson::Value sv = njson::Value::makeObject();
        sv.set("type") = njson::Value::makeString(s.type == 1 ? "safuri" : "rawpath");
        sv.set("value") = njson::Value::makeString(s.value);
        if (!s.rawHint.empty()) sv.set("rawHint") = njson::Value::makeString(s.rawHint);
        srcs.arr.push_back(std::move(sv));
    }
    o.set("scanSources") = std::move(srcs);
    o.set("extensions") = extsToJsonArray(sys.acceptExts);
    njson::Value icon = njson::Value::makeObject();
    icon.set("ref") = njson::Value::makeString(sys.iconRef);
    icon.set("tintR") = njson::Value::makeNumber(sys.iconR);
    icon.set("tintG") = njson::Value::makeNumber(sys.iconG);
    icon.set("tintB") = njson::Value::makeNumber(sys.iconB);
    o.set("icon") = std::move(icon);
    // Boxart/cover scraper per-system overrides. Only emit the sub-object when at
    // least one field is set, so untouched systems keep a clean config file.
    if (!sys.scraperOverride.empty() || !sys.scrapeUser.empty() || !sys.scrapePass.empty()
        || !sys.scrapeKey.empty() || !sys.scrapePlatform.empty()) {
        njson::Value sc = njson::Value::makeObject();
        if (!sys.scraperOverride.empty()) sc.set("override")  = njson::Value::makeString(sys.scraperOverride);
        if (!sys.scrapeUser.empty())      sc.set("user")      = njson::Value::makeString(sys.scrapeUser);
        if (!sys.scrapePass.empty())      sc.set("pass")      = njson::Value::makeString(sys.scrapePass);
        if (!sys.scrapeKey.empty())       sc.set("key")       = njson::Value::makeString(sys.scrapeKey);
        if (!sys.scrapePlatform.empty())  sc.set("platform")  = njson::Value::makeString(sys.scrapePlatform);
        o.set("scraper") = std::move(sc);
    }
    return o;
}

// Construct a runtime system from its JSON object form, deriving the legacy
// backward-compat fields (launchPkg from package for custom-package, etc.).
static NanoMenu::XmbSystem jsonToSystem(const njson::Value& o) {
    NanoMenu::XmbSystem sys;
    sys.id = o.getString("id");
    sys.builtin = o.getBool("builtin", false);
    sys.enabled = o.getBool("enabled", true);
    sys.order = o.getInt("order", 0);
    sys.name = o.getString("displayName");
    sys.shortname = o.getString("shortname");
    sys.romDir = o.getString("romDir", sys.id);
    sys.launchType = launchTypeFromStr(o.getString("launchType", "libretro-core"));
    sys.coreSo = o.getString("coreSo");
    sys.packageName = o.getString("package");
    sys.launchArgs = o.getString("launchArgs");
    sys.launchIntent = o.getString("intentTemplate");
    if (const njson::Value* srcs = o.find("scanSources")) {
        if (srcs->isArray()) {
            for (const auto& sv : srcs->arr) {
                if (!sv.isObject()) continue;
                NanoMenu::ScanSource s;
                s.type = (sv.getString("type") == "safuri") ? 1 : 0;
                s.value = sv.getString("value");
                s.rawHint = sv.getString("rawHint");
                if (!s.value.empty()) sys.scanSources.push_back(std::move(s));
            }
        }
    }
    if (const njson::Value* exts = o.find("extensions"))
        sys.acceptExts = jsonArrayToExts(*exts);
    if (const njson::Value* icon = o.find("icon")) {
        sys.iconRef = icon->getString("ref");
        // Tints are fractional, so read them as doubles (getInt would truncate).
        const njson::Value* tr = icon->find("tintR");
        const njson::Value* tg = icon->find("tintG");
        const njson::Value* tb = icon->find("tintB");
        if (tr) sys.iconR = (float)tr->asNumber(sys.iconR);
        if (tg) sys.iconG = (float)tg->asNumber(sys.iconG);
        if (tb) sys.iconB = (float)tb->asNumber(sys.iconB);
    }
    if (const njson::Value* sc = o.find("scraper")) {
        sys.scraperOverride = sc->getString("override");
        sys.scrapeUser      = sc->getString("user");
        sys.scrapePass      = sc->getString("pass");
        sys.scrapeKey       = sc->getString("key");
        sys.scrapePlatform  = sc->getString("platform");
    }
    // Derive legacy backward-compat fields the renderer/launch path read.
    if (sys.launchType == NanoMenu::XLT_CUSTOM_PACKAGE) {
        sys.launchPkg = sys.packageName;
    } else {
        sys.launchPkg.clear();  // libretro / retroarch-intent are not standalone
    }
    return sys;
}

// Nanosecond mtime stamp of nano_systems.json, or -1 when absent. The resident
// overlay and the DRM home are separate processes sharing the file; each tracks
// the stamp of its own last load/save (mSystemsCfgStamp) and reloads when an
// external writer moves it.
int64_t NanoMenu::systemsConfigStamp() const {
    struct stat st;
    if (stat("/data/system/nano_systems.json", &st) != 0) return -1;
    return (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec;
}

bool NanoMenu::loadSystemsConfig() {
    const char* path = "/data/system/nano_systems.json";
    int fd = open(path, O_RDONLY);
    if (fd < 0) return false;
    std::string content;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 4 * 1024 * 1024) {
        content.resize(st.st_size);
        ssize_t rd = read(fd, &content[0], st.st_size);
        if (rd > 0) content.resize(rd); else content.clear();
    }
    close(fd);
    if (content.empty()) return false;

    njson::Value root;
    if (!njson::parse(content, &root) || !root.isObject()) {
        ALOGW("NanoMenu: nano_systems.json parse failed; reseeding from defaults");
        return false;
    }
    const njson::Value* systems = root.find("systems");
    if (!systems || !systems->isArray() || systems->arr.empty()) return false;

    mXmbSystems.clear();
    mXmbSystems.reserve(systems->arr.size());
    for (const auto& sv : systems->arr) {
        if (!sv.isObject()) continue;
        XmbSystem sys = jsonToSystem(sv);
        if (sys.id.empty()) continue;
        mXmbSystems.push_back(std::move(sys));
    }
    if (mXmbSystems.empty()) return false;

    // Honor the explicit per-system order within the Game category.
    std::stable_sort(mXmbSystems.begin(), mXmbSystems.end(),
                     [](const XmbSystem& a, const XmbSystem& b) {
                         return a.order < b.order;
                     });
    ALOGD("NanoMenu: loaded %zu systems from nano_systems.json", mXmbSystems.size());
    mSystemsCfgStamp = systemsConfigStamp();
    return true;
}

void NanoMenu::seedSystemsConfig() {
    // Build the default config from the built-in table so first-boot behavior
    // is byte-for-byte unchanged, then persist it. Legacy prop overrides are
    // applied later in initXmbSystems (props win), so the on-disk seed holds
    // factory defaults.
    mXmbSystems.clear();
    mXmbSystems.reserve(kNumXmbSystemDefs);
    for (int i = 0; i < kNumXmbSystemDefs; i++) {
        XmbSystem sys;
        sys.id = kXmbSystemDefs[i].romDir;
        sys.builtin = true;
        sys.enabled = true;
        sys.order = i;
        sys.name = kXmbSystemDefs[i].name;
        sys.shortname = kXmbSystemDefs[i].shortname;
        sys.romDir = kXmbSystemDefs[i].romDir;
        sys.coreSo = kXmbSystemDefs[i].coreSo;
        sys.launchPkg = kXmbSystemDefs[i].launchPkg;
        sys.launchIntent = kXmbSystemDefs[i].launchIntent;
        sys.launchType = (kXmbSystemDefs[i].launchPkg[0] != '\0')
                             ? XLT_CUSTOM_PACKAGE : XLT_LIBRETRO_CORE;
        sys.packageName = kXmbSystemDefs[i].launchPkg;
        // Default tint is white so the console glyphs keep the clean PS3 glass
        // look; the Icon Tint editor lets users colour individual systems.
        sys.iconR = sys.iconG = sys.iconB = 1.0f;
        sys.acceptExts = kXmbSystemDefs[i].acceptExts;
        char ref[24];
        snprintf(ref, sizeof(ref), "builtin:%d", i);
        sys.iconRef = ref;
        mXmbSystems.push_back(std::move(sys));
    }
    ALOGI("NanoMenu: seeding nano_systems.json from %d built-in defaults",
          kNumXmbSystemDefs);
    saveSystemsConfig();
}

void NanoMenu::saveSystemsConfig() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(1);
    njson::Value systems = njson::Value::makeArray();
    for (const auto& sys : mXmbSystems)
        systems.arr.push_back(systemToJson(sys));
    root.set("systems") = std::move(systems);
    std::string text = njson::serialize(root, true);

    // Atomic write: temp file, fsync, rename over the target; re-own root:root
    // 0644 (nano holds CHOWN/FOWNER), matching the existing cache hygiene.
    const char* path = "/data/system/nano_systems.json";
    const char* tmp = "/data/system/nano_systems.json.tmp";
    int fd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) {
        ALOGW("NanoMenu: cannot write %s (errno %d)", tmp, errno);
        return;
    }
    size_t off = 0;
    bool ok = true;
    while (off < text.size()) {
        ssize_t w = write(fd, text.c_str() + off, text.size() - off);
        if (w <= 0) { ok = false; break; }
        off += (size_t)w;
    }
    fsync(fd);
    close(fd);
    if (!ok) { unlink(tmp); ALOGW("NanoMenu: write of nano_systems.json failed"); return; }
    if (rename(tmp, path) != 0) {
        ALOGW("NanoMenu: rename of nano_systems.json failed (errno %d)", errno);
        unlink(tmp);
        return;
    }
    (void)chown(path, 0, 0);
    (void)chmod(path, 0644);
    // Track our own write so the cross-process change poll does not see this
    // process's saves as an external edit.
    mSystemsCfgStamp = systemsConfigStamp();
    ALOGD("NanoMenu: wrote %s (%zu systems, %zu bytes)", path,
          mXmbSystems.size(), text.size());
}

// Restore a built-in system's config to its factory defaults (from
// kXmbSystemDefs, matched by id), keeping its enabled/order/id. Reloads the ROM
// cache so the system reflects the restored extensions/core. Returns false for
// custom systems or unknown ids. Caller persists + rebuilds.
bool NanoMenu::resetSystemToBuiltinDefaults(int sysIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return false;
    XmbSystem& sys = mXmbSystems[sysIdx];
    if (!sys.builtin) return false;
    int idx = -1;
    for (int i = 0; i < kNumXmbSystemDefs; i++) {
        if (sys.id == kXmbSystemDefs[i].romDir) { idx = i; break; }
    }
    if (idx < 0) return false;
    const SystemDef& def = kXmbSystemDefs[idx];
    sys.name = def.name;
    sys.shortname = def.shortname;
    sys.romDir = def.romDir;
    sys.coreSo = def.coreSo;
    sys.launchPkg = def.launchPkg;
    sys.launchIntent = def.launchIntent;
    sys.launchType = (def.launchPkg[0] != '\0') ? XLT_CUSTOM_PACKAGE : XLT_LIBRETRO_CORE;
    sys.packageName = def.launchPkg;
    sys.launchArgs.clear();
    sys.iconR = sys.iconG = sys.iconB = 1.0f;   // white default tint
    sys.acceptExts = def.acceptExts;
    sys.scanSources.clear();
    char ref[24]; snprintf(ref, sizeof(ref), "builtin:%d", idx); sys.iconRef = ref;
    sys.scanned = false;
    loadRomCacheForSystem(sys);
    ALOGI("ps3menu: reset system %s to built-in defaults", sys.id.c_str());
    return true;
}

// Equivalent ROM folder names across nano / Daijishou / ES-DE / RetroArch, so a
// system's ROMs are found regardless of which folder-naming convention the user
// organized by. ES-DE for example uses "megadrive" for Genesis and "n3ds" for
// 3DS. Returns the system's romDir plus any aliases in its equivalence group.
// (Folder list from retrogamecorps/ES-DE-Directories + the libretro/Daijishou
// conventions.)
static std::vector<std::string> getRomFolderAliases(const std::string& romDir) {
    static const char* const kGroups[] = {
        "nes,famicom",
        "snes,sfc,snesna,superfamicom,sufami,satellaview,sgb",
        "gb,gameboy",
        "gbc,gameboycolor",
        "gba,gameboyadvance",
        "n64,nintendo64,n64dd",
        "nds,ds,nintendods",
        "genesis,megadrive,md,megadrivejp",
        "mastersystem,sms",
        "gamegear,gg",
        "psx,ps1,playstation,psone",
        "psp,playstationportable",
        "dreamcast,dc",
        "ngp",
        "ngpc",
        "pico8,pico-8",
        "ps2,playstation2",
        "gc,gamecube,ngc",
        "wii", "wiiu",
        "3ds,n3ds,nintendo3ds",
        "switch", "ps3,playstation3", "psvita,vita",
        "saturn,segasaturn,saturnjp",
        "segacd,megacd,megacdjp",
        "sega32x,sega32xjp,sega32xna,32x",
        "sg-1000,sg1000",
        "pcengine,tg16,pce,turbografx16,supergrafx",
        "pcenginecd,tg-cd,pcecd",
        "neogeo,neogeocd",
        "wonderswan,ws", "wonderswancolor,wsc",
        "atari2600,a2600", "atari5200", "atari7800",
        "atarilynx,lynx", "atarijaguar,jaguar", "atarist,ast",
        "msx,msx1", "msx2", "colecovision,coleco", "intellivision,intv",
        "vectrex", "virtualboy,vb",
        "arcade,mame,fbneo,fba,mame2003,mame2010,cps1,cps2,cps3",
        "c64,commodore64", "amiga", "amstradcpc,cpc", "zxspectrum,spectrum,zx81",
        "scummvm", "ports", "dos,pc", "fds", "naomi", "atomiswave", "pokemini",
        "channelf", "odyssey2,videopac", "x68000,x68k",
        "supervision,watara", "gameandwatch,gw", "3do",
    };
    auto low = [](const std::string& in) {
        std::string o; for (char c : in) o += (char)((c >= 'A' && c <= 'Z') ? c + 32 : c); return o;
    };
    std::string lower = low(romDir);
    std::vector<std::string> out; out.push_back(romDir);
    for (const char* g : kGroups) {
        std::vector<std::string> toks; std::string t;
        for (const char* p = g; ; p++) {
            if (*p == ',' || *p == '\0') { if (!t.empty()) toks.push_back(t); t.clear(); if (!*p) break; }
            else t += (char)((*p >= 'A' && *p <= 'Z') ? *p + 32 : *p);
        }
        bool match = false; for (auto& tk : toks) if (tk == lower) { match = true; break; }
        if (!match) continue;
        for (auto& tk : toks) {
            if (tk == lower) continue;
            bool dup = false; for (auto& o : out) if (low(o) == tk) { dup = true; break; }
            if (!dup) out.push_back(tk);
        }
    }
    return out;
}

// Build the ordered, de-duplicated list of directories to scan for a system's
// ROMs. Honors user scanSources first; always appends the legacy default
// candidates (so built-ins with empty scanSources behave exactly as before),
// expanded across the system's ES-DE / libretro folder-name aliases, then
// prepends any persist.gammaos.nano.xmb.<id>.path override at the front.
std::vector<std::string> NanoMenu::buildScanCandidates(const XmbSystem& sys) {
    std::vector<std::string> scanPaths;

    // 0. User-chosen scan sources (highest priority). safuri sources scan via
    //    their resolved raw mount; sources lacking a usable path are skipped.
    for (const auto& src : sys.scanSources) {
        if (src.type == 0 && !src.value.empty())
            scanPaths.push_back(src.value);
        else if (src.type == 1 && !src.rawHint.empty())
            scanPaths.push_back(src.rawHint);
    }

    const std::string romDir = sys.romDir;
    const std::vector<std::string> aliases = getRomFolderAliases(romDir);

    // 1. Internal storage (raw + FUSE) - for every folder-name alias
    for (const auto& a : aliases) {
        scanPaths.push_back("/data/media/0/ROMs/" + a);
        scanPaths.push_back("/sdcard/ROMs/" + a);
        scanPaths.push_back("/storage/emulated/0/ROMs/" + a);
    }

    // 2. External volumes -- case-insensitive matching for ROMs dir and system dir
    auto addExternalVolume = [&](const std::string& base) {
        std::string romsDir = findCaseInsensitive(base, "ROMs");
        for (const auto& a : aliases) {
            scanPaths.push_back(base + "/ROMs/" + a);
            scanPaths.push_back(base + "/roms/" + a);
            scanPaths.push_back(base + "/" + a);
            if (!romsDir.empty()) {
                std::string sysDir = findCaseInsensitive(romsDir, a);
                if (!sysDir.empty()) scanPaths.push_back(sysDir);
            }
            std::string directDir = findCaseInsensitive(base, a);
            if (!directDir.empty()) scanPaths.push_back(directDir);
        }
    };
    {
        DIR* storageDir = opendir("/storage");
        if (storageDir) {
            struct dirent* sEntry;
            while ((sEntry = readdir(storageDir)) != nullptr) {
                if (sEntry->d_name[0] == '.') continue;
                if (!strcmp(sEntry->d_name, "emulated")) continue;
                if (!strcmp(sEntry->d_name, "self")) continue;
                addExternalVolume(std::string("/storage/") + sEntry->d_name);
            }
            closedir(storageDir);
        }
        DIR* mntDir = opendir("/mnt/media_rw");
        if (mntDir) {
            struct dirent* mEntry;
            while ((mEntry = readdir(mntDir)) != nullptr) {
                if (mEntry->d_name[0] == '.') continue;
                addExternalVolume(std::string("/mnt/media_rw/") + mEntry->d_name);
            }
            closedir(mntDir);
        }
    }

    // 3. Prop-overridden custom path (highest priority): prepend at front.
    char customPath[PROPERTY_VALUE_MAX] = {};
    char propKey[160];
    snprintf(propKey, sizeof(propKey), "persist.gammaos.nano.xmb.%s.path", sys.id.c_str());
    property_get(propKey, customPath, "");
    if (customPath[0]) scanPaths.insert(scanPaths.begin(), std::string(customPath));

    // Deduplicate candidate paths (preserve order; exact string match).
    {
        std::set<std::string> seen;
        std::vector<std::string> unique;
        for (auto& p : scanPaths) {
            if (seen.insert(p).second) unique.push_back(std::move(p));
        }
        scanPaths = std::move(unique);
    }
    return scanPaths;
}

// ---------------------------------------------------------------------------
// ROM Path Scanning
// ---------------------------------------------------------------------------

// Helper: find a case-insensitive match for 'target' in directory 'parent'
static std::string findCaseInsensitive(const std::string& parent, const std::string& target) {
    DIR* d = opendir(parent.c_str());
    if (!d) return "";
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (strcasecmp(e->d_name, target.c_str()) == 0) {
            std::string result = parent + "/" + e->d_name;
            closedir(d);
            return result;
        }
    }
    closedir(d);
    return "";
}

void NanoMenu::scanRomPaths() {
    ALOGD("NanoMenu: scanning ROM paths");
    for (auto& sys : mXmbSystems) {
        if (!sys.enabled) continue;   // disabled systems are never scanned
        if (sys.scanned) continue;

        std::vector<std::string> scanPaths = buildScanCandidates(sys);

        // Build extension set from comma-separated list
        std::set<std::string> exts;
        {
            const std::string& extStr = sys.acceptExts;
            size_t pos = 0;
            while (pos < extStr.size()) {
                size_t comma = extStr.find(',', pos);
                if (comma == std::string::npos) comma = extStr.size();
                std::string ext = extStr.substr(pos, comma - pos);
                while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
                if (!ext.empty()) exts.insert(ext);
                pos = comma + 1;
            }
        }
        exts.insert(".zip");
        exts.insert(".7z");

        // Clear any cached data -- fresh scan replaces it
        sys.roms.clear();
        sys.displayNames.clear();
        sys.activePaths.clear();

        // Track filenames already seen (case-insensitive) for deduplication
        std::set<std::string> seenFilenames;
        // Per-path ROM count for determining activePath (largest collection)
        std::string bestPath;
        int bestCount = 0;
        bool anyPath = false;

        // Scan ALL candidate paths and merge results
        for (const auto& candidatePath : scanPaths) {
            DIR* dir = opendir(candidatePath.c_str());
            if (!dir) continue;

            int pathRomCount = 0;
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                if (entry->d_name[0] == '.') continue;
                if (entry->d_type == DT_DIR) continue;

                std::string name(entry->d_name);
                size_t dot = name.rfind('.');
                if (dot == std::string::npos) continue;

                // Skip known non-ROM files
                std::string ext = name.substr(dot);
                for (size_t i = 0; i < ext.size(); i++) {
                    if (ext[i] >= 'A' && ext[i] <= 'Z') ext[i] += 32;
                }
                if (ext == ".txt" || ext == ".jpg" || ext == ".png" || ext == ".xml"
                    || ext == ".srm" || ext == ".sav" || ext == ".state" || ext == ".rtc"
                    || ext == ".dat" || ext == ".bak" || ext == ".cfg" || ext == ".log") {
                    continue;
                }

                if (!exts.count(ext)) continue;

                // Deduplicate by filename (case-insensitive) -- first found wins
                std::string nameLower = name;
                for (size_t i = 0; i < nameLower.size(); i++) {
                    if (nameLower[i] >= 'A' && nameLower[i] <= 'Z') nameLower[i] += 32;
                }
                if (!seenFilenames.insert(nameLower).second) continue;

                // Skip 0-byte files (dummy/placeholder files)
                std::string fullPath = candidatePath + "/" + name;
                {
                    struct stat st;
                    if (stat(fullPath.c_str(), &st) == 0 && st.st_size == 0) continue;
                }

                sys.roms.push_back(fullPath);
                pathRomCount++;
            }
            closedir(dir);

            if (pathRomCount > 0) {
                sys.activePaths.push_back(candidatePath);
                anyPath = true;
                if (pathRomCount > bestCount) {
                    bestCount = pathRomCount;
                    bestPath = candidatePath;
                }
            }
        }

        if (!anyPath) {
            sys.pathExists = false;
            ALOGD("NanoMenu: %s: no accessible path found (will retry)", sys.name.c_str());
            continue;
        }

        sys.scanned = true;
        sys.pathExists = true;
        sys.activePath = bestPath;
        sys.lastScanTime = elapsedRealtime();

        // Sort by display name (case-insensitive) -- extract filename, strip extension
        std::sort(sys.roms.begin(), sys.roms.end(),
                  [](const std::string& a, const std::string& b) {
                      // Extract filename from full path
                      size_t sa = a.rfind('/');
                      size_t sb = b.rfind('/');
                      const char* na = (sa != std::string::npos) ? a.c_str() + sa + 1 : a.c_str();
                      const char* nb = (sb != std::string::npos) ? b.c_str() + sb + 1 : b.c_str();
                      // Case-insensitive compare
                      for (size_t i = 0; na[i] && nb[i]; i++) {
                          char ca = na[i], cb = nb[i];
                          if (ca >= 'A' && ca <= 'Z') ca += 32;
                          if (cb >= 'A' && cb <= 'Z') cb += 32;
                          if (ca != cb) return ca < cb;
                      }
                      // Shorter name first if prefixes match
                      size_t la = strlen(na), lb = strlen(nb);
                      return la < lb;
                  });

        // Pre-compute display names (strip path + extension)
        sys.displayNames.reserve(sys.roms.size());
        for (const auto& rom : sys.roms) {
            std::string dn = rom;
            size_t sl = dn.rfind('/');
            if (sl != std::string::npos) dn = dn.substr(sl + 1);
            size_t d = dn.rfind('.');
            if (d != std::string::npos) dn = dn.substr(0, d);
            sys.displayNames.push_back(std::move(dn));
        }

        ALOGD("NanoMenu: %s: %zu ROMs across %zu paths (primary: %s)",
              sys.name.c_str(), sys.roms.size(), sys.activePaths.size(),
              bestPath.c_str());

        // Save cache to DE storage -- each line is a full ROM path
        {
            std::string cacheDir = "/data/system/nano_xmb_cache";
            mkdir(cacheDir.c_str(), 0755);
            std::string cachePath = cacheDir + "/" + sys.id + ".list";
            int cfd = open(cachePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (cfd >= 0) {
                for (const auto& rom : sys.roms) {
                    std::string line = rom + "\n";
                    write(cfd, line.c_str(), line.size());
                }
                close(cfd);
            }
        }
    }
    // Only mark scan complete if all systems were scanned
    bool allScanned = true;
    for (const auto& s : mXmbSystems) {
        if (!s.scanned) { allScanned = false; break; }
    }
    mXmbRomScanDone = allScanned;
}

// Scan a single system's ROM paths into temp buffers. Only updates
// the system's rom/displayNames/activePath if the result differs from
// the current data. Returns true if the system was updated.
bool NanoMenu::scanOneSystemAsync(int sysIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return false;
    auto& sys = mXmbSystems[sysIdx];
    if (!sys.enabled) return false;   // disabled systems are never scanned

    std::vector<std::string> scanPaths = buildScanCandidates(sys);

    // Build extension set
    std::set<std::string> exts;
    {
        const std::string& extStr = sys.acceptExts;
        size_t pos = 0;
        while (pos < extStr.size()) {
            size_t comma = extStr.find(',', pos);
            if (comma == std::string::npos) comma = extStr.size();
            std::string ext = extStr.substr(pos, comma - pos);
            while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
            if (!ext.empty()) exts.insert(ext);
            pos = comma + 1;
        }
    }
    exts.insert(".zip");
    exts.insert(".7z");

    // Scan into TEMP buffers (don't touch sys.roms yet)
    std::vector<std::string> newRoms;
    std::vector<std::string> newActivePaths;
    std::set<std::string> seenFilenames;
    std::string newBestPath;
    int bestCount = 0;

    for (const auto& candidatePath : scanPaths) {
        DIR* dir = opendir(candidatePath.c_str());
        if (!dir) continue;

        int pathRomCount = 0;
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_name[0] == '.') continue;
            if (entry->d_type == DT_DIR) continue;

            std::string name(entry->d_name);
            size_t dot = name.rfind('.');
            if (dot == std::string::npos) continue;

            std::string ext = name.substr(dot);
            for (size_t i = 0; i < ext.size(); i++) {
                if (ext[i] >= 'A' && ext[i] <= 'Z') ext[i] += 32;
            }
            if (ext == ".txt" || ext == ".jpg" || ext == ".png" || ext == ".xml"
                || ext == ".srm" || ext == ".sav" || ext == ".state" || ext == ".rtc"
                || ext == ".dat" || ext == ".bak" || ext == ".cfg" || ext == ".log") {
                continue;
            }
            if (!exts.count(ext)) continue;

            std::string nameLower = name;
            for (size_t i = 0; i < nameLower.size(); i++) {
                if (nameLower[i] >= 'A' && nameLower[i] <= 'Z') nameLower[i] += 32;
            }
            if (!seenFilenames.insert(nameLower).second) continue;

            std::string fullPath = candidatePath + "/" + name;
            {
                struct stat st;
                if (stat(fullPath.c_str(), &st) == 0 && st.st_size == 0) continue;
            }

            newRoms.push_back(fullPath);
            pathRomCount++;
        }
        closedir(dir);

        if (pathRomCount > 0) {
            newActivePaths.push_back(candidatePath);
            if (pathRomCount > bestCount) {
                bestCount = pathRomCount;
                newBestPath = candidatePath;
            }
        }
    }

    // Sort by display name
    std::sort(newRoms.begin(), newRoms.end(),
              [](const std::string& a, const std::string& b) {
                  size_t sa = a.rfind('/');
                  size_t sb = b.rfind('/');
                  const char* na = (sa != std::string::npos) ? a.c_str() + sa + 1 : a.c_str();
                  const char* nb = (sb != std::string::npos) ? b.c_str() + sb + 1 : b.c_str();
                  for (size_t i = 0; na[i] && nb[i]; i++) {
                      char ca = na[i], cb = nb[i];
                      if (ca >= 'A' && ca <= 'Z') ca += 32;
                      if (cb >= 'A' && cb <= 'Z') cb += 32;
                      if (ca != cb) return ca < cb;
                  }
                  return strlen(na) < strlen(nb);
              });

    ALOGD("NanoMenu: %s: scan found %zu ROMs across %zu paths (current: %zu ROMs)",
          sys.name.c_str(), newRoms.size(), newActivePaths.size(), sys.roms.size());

    // Check if result differs from current data
    bool changed = (newRoms != sys.roms);

    // Guard against downgrading cached data when storage is partially
    // mounted (e.g., SD card not yet available after reboot). Only
    // replace with fewer ROMs if the new scan found at least as many
    // source directories. Otherwise storage likely isn't fully mounted.
    if (changed && !newRoms.empty() && newRoms.size() < sys.roms.size()
        && sys.scanned) {
        size_t curPathCount = sys.activePaths.size();
        if (curPathCount == 0 && !sys.roms.empty()) {
            std::set<std::string> dirs;
            for (const auto& r : sys.roms) {
                size_t sl = r.rfind('/');
                if (sl != std::string::npos) dirs.insert(r.substr(0, sl));
            }
            curPathCount = dirs.size();
        }
        if (newActivePaths.size() < curPathCount) {
            sys.lastScanTime = elapsedRealtime();
            return false;
        }
    }

    if (changed || !sys.scanned) {
        // Swap in new data atomically (fast -- just pointer swaps)
        sys.roms = std::move(newRoms);
        sys.activePaths = std::move(newActivePaths);
        sys.activePath = newBestPath;
        sys.pathExists = !sys.roms.empty();

        // Rebuild display names
        sys.displayNames.clear();
        sys.displayNames.reserve(sys.roms.size());
        for (const auto& rom : sys.roms) {
            std::string dn = rom;
            size_t sl = dn.rfind('/');
            if (sl != std::string::npos) dn = dn.substr(sl + 1);
            size_t d = dn.rfind('.');
            if (d != std::string::npos) dn = dn.substr(0, d);
            sys.displayNames.push_back(std::move(dn));
        }

        // Update cache file (xmbCachePath keys on the stable id, matching the
        // loader and the bg-scan writer; romDir is NOT the cache key)
        mkdir("/data/system/nano_xmb_cache", 0755);
        std::string cachePath = xmbCachePath(sys);
        int cfd = open(cachePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (cfd >= 0) {
            for (const auto& rom : sys.roms) {
                std::string line = rom + "\n";
                write(cfd, line.c_str(), line.size());
            }
            close(cfd);
        }

        if (changed) {
            ALOGD("NanoMenu: %s: %zu ROMs across %zu paths (primary: %s)",
                  sys.name.c_str(), sys.roms.size(), sys.activePaths.size(),
                  sys.activePath.c_str());
            mDisplayDirty = true;
        }
    }

    sys.scanned = true;
    sys.lastScanTime = elapsedRealtime();
    return changed;
}

void NanoMenu::forceRescanAllSystems() {
    // Launch a background thread to scan all systems. The thread builds
    // results in mBgScanResults; the render loop swaps them in when ready.
    if (mBgScanThreadRunning) return; // already scanning
    mBgScanThreadRunning = true;
    ALOGI("NanoMenu: launching background scan thread");
    std::thread(&NanoMenu::bgScanThreadFunc, this).detach();
}

// Background thread: scans all systems and stores results for the render
// thread to pick up. Never touches sys.roms/displayNames directly -- only
// writes to mBgScanResults behind a mutex.
void NanoMenu::bgScanThreadFunc() {
    int numSys = (int)mXmbSystems.size();
    std::vector<BgScanResult> results(numSys);

    for (int si = 0; si < numSys; si++) {
        const auto& sys = mXmbSystems[si];
        auto& res = results[si];
        res.valid = false;
        if (!sys.enabled) continue;   // disabled systems are never scanned

        std::vector<std::string> scanPaths = buildScanCandidates(sys);

        // Build extension set
        std::set<std::string> exts;
        { const std::string& es = sys.acceptExts;
          size_t pos = 0;
          while (pos < es.size()) {
              size_t c = es.find(',', pos);
              if (c == std::string::npos) c = es.size();
              std::string ext = es.substr(pos, c - pos);
              while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
              if (!ext.empty()) exts.insert(ext);
              pos = c + 1;
          }
        }
        exts.insert(".zip");
        exts.insert(".7z");

        // Scan all paths, merge
        std::set<std::string> seenNames;
        std::string bestPath;
        int bestCount = 0;

        for (const auto& cp : scanPaths) {
            DIR* dir = opendir(cp.c_str());
            if (!dir) continue;
            int cnt = 0;
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                if (entry->d_name[0] == '.') continue;
                if (entry->d_type == DT_DIR) continue;
                std::string name(entry->d_name);
                size_t dot = name.rfind('.');
                if (dot == std::string::npos) continue;
                std::string ext = name.substr(dot);
                for (size_t i = 0; i < ext.size(); i++)
                    if (ext[i] >= 'A' && ext[i] <= 'Z') ext[i] += 32;
                if (ext == ".txt" || ext == ".jpg" || ext == ".png" || ext == ".xml"
                    || ext == ".srm" || ext == ".sav" || ext == ".state" || ext == ".rtc"
                    || ext == ".dat" || ext == ".bak" || ext == ".cfg" || ext == ".log")
                    continue;
                if (!exts.count(ext)) continue;
                std::string nl = name;
                for (size_t i = 0; i < nl.size(); i++)
                    if (nl[i] >= 'A' && nl[i] <= 'Z') nl[i] += 32;
                if (!seenNames.insert(nl).second) continue;
                std::string fp = cp + "/" + name;
                struct stat st;
                if (stat(fp.c_str(), &st) == 0 && st.st_size == 0) continue;
                res.roms.push_back(fp);
                cnt++;
            }
            closedir(dir);
            if (cnt > 0) {
                res.activePaths.push_back(cp);
                res.valid = true;
                if (cnt > bestCount) { bestCount = cnt; bestPath = cp; }
            }
        }
        res.activePath = bestPath;

        // Sort by display name
        std::sort(res.roms.begin(), res.roms.end(),
                  [](const std::string& a, const std::string& b) {
                      size_t sa = a.rfind('/'), sb = b.rfind('/');
                      const char* na = sa != std::string::npos ? a.c_str()+sa+1 : a.c_str();
                      const char* nb = sb != std::string::npos ? b.c_str()+sb+1 : b.c_str();
                      for (size_t i = 0; na[i] && nb[i]; i++) {
                          char ca = na[i], cb = nb[i];
                          if (ca >= 'A' && ca <= 'Z') ca += 32;
                          if (cb >= 'A' && cb <= 'Z') cb += 32;
                          if (ca != cb) return ca < cb;
                      }
                      return strlen(na) < strlen(nb);
                  });

        // Build display names
        res.displayNames.reserve(res.roms.size());
        for (const auto& rom : res.roms) {
            std::string dn = rom;
            size_t sl = dn.rfind('/');
            if (sl != std::string::npos) dn = dn.substr(sl + 1);
            size_t d = dn.rfind('.');
            if (d != std::string::npos) dn = dn.substr(0, d);
            res.displayNames.push_back(std::move(dn));
        }
    }

    // Publish results for the render thread
    {
        std::lock_guard<std::mutex> lock(mBgScanMutex);
        mBgScanResults = std::move(results);
        mBgScanResultReady = true;
    }
    mBgScanThreadRunning = false;
    ALOGI("NanoMenu: background scan thread complete");
}

// ---------------------------------------------------------------------------
// XMB Recently Played
// ---------------------------------------------------------------------------

static const char* kXmbRecentFile = "/data/system/nano_xmb_recent.list";

void NanoMenu::loadXmbRecent() {
    mXmbRecent.clear();
    int fd = open(kXmbRecentFile, O_RDONLY);
    if (fd < 0) return;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size == 0 || st.st_size > 256 * 1024) {
        close(fd); return;
    }
    std::string content(st.st_size, '\0');
    ssize_t rd = read(fd, &content[0], st.st_size);
    close(fd);
    if (rd <= 0) return;
    content.resize(rd);

    // Format: one entry per 7 lines (romPath, coreSo, launchPkg, launchIntent,
    //         displayName, systemName, romDir) separated by \n, entries by \n\n
    size_t pos = 0;
    while (pos < content.size() && (int)mXmbRecent.size() < mXmbRecentMax) {
        XmbRecentEntry e;
        auto readLine = [&]() -> std::string {
            if (pos >= content.size()) return {};
            size_t eol = content.find('\n', pos);
            if (eol == std::string::npos) eol = content.size();
            std::string line = content.substr(pos, eol - pos);
            pos = eol + 1;
            return line;
        };
        e.romPath = readLine();
        if (e.romPath.empty()) { pos++; continue; }
        e.coreSo = readLine();
        e.launchPkg = readLine();
        e.launchIntent = readLine();
        e.displayName = readLine();
        e.systemName = readLine();
        e.romDir = readLine();
        e.standalone = !e.launchPkg.empty();
        // Skip blank separator line
        if (pos < content.size() && content[pos] == '\n') pos++;
        mXmbRecent.push_back(std::move(e));
    }
    ALOGD("NanoMenu: loaded %zu recent XMB entries", mXmbRecent.size());
}

void NanoMenu::saveXmbRecent() {
    int fd = open(kXmbRecentFile, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return;
    chmod(kXmbRecentFile, 0644);
    for (const auto& e : mXmbRecent) {
        std::string block = e.romPath + "\n" + e.coreSo + "\n" + e.launchPkg + "\n"
            + e.launchIntent + "\n" + e.displayName + "\n" + e.systemName + "\n"
            + e.romDir + "\n\n";
        write(fd, block.c_str(), block.size());
    }
    close(fd);
}

void NanoMenu::addXmbRecent(int sysIdx, int gameIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    const auto& sys = mXmbSystems[sysIdx];
    if (gameIdx < 0 || gameIdx >= (int)sys.roms.size()) return;

    XmbRecentEntry e;
    // roms[] contains full paths -- use directly, convert for app access
    e.romPath = sys.roms[gameIdx];
    if (e.romPath.find("/data/media/0/") == 0) {
        e.romPath = "/sdcard/" + e.romPath.substr(14);
    }
    if (e.romPath.find("/mnt/media_rw/") == 0) {
        // Convert raw SD path to FUSE path for app access
        e.romPath = "/storage/" + e.romPath.substr(14);
    }
    e.coreSo = sys.coreSo;
    e.launchPkg = sys.launchPkg;
    e.launchIntent = sys.launchIntent;
    e.displayName = sys.displayNames[gameIdx];
    e.systemName = sys.shortname;
    e.romDir = sys.romDir;
    e.standalone = sys.isStandalone();

    // Remove duplicate if already in list
    for (auto it = mXmbRecent.begin(); it != mXmbRecent.end(); ++it) {
        if (it->romPath == e.romPath) {
            mXmbRecent.erase(it);
            break;
        }
    }
    // Insert at front (most recent first)
    mXmbRecent.insert(mXmbRecent.begin(), std::move(e));
    // Cap size
    if ((int)mXmbRecent.size() > mXmbRecentMax) {
        mXmbRecent.resize(mXmbRecentMax);
    }
    saveXmbRecent();
}

// ---------------------------------------------------------------------------
// XMB Navigation
// ---------------------------------------------------------------------------

void NanoMenu::handleLeft() {
    if (mScrapeProgActive) return;   // modal swallows navigation
    // GammaOS Nano: navigating cancels any queued launch.
    cancelPendingLaunch();
    if (mOskActive) {
        oskMoveCursor(NavDir::Left);
        return;
    }
    if (mMenuState == MENU_WIFI || mMenuState == MENU_BT) return;
    if (mMenuState == MENU_SETTINGS) { handleSettingsTreeLeft(); return; }
    if (mPs3Xmb || mPs3WizActive) { ps3XmbLeft(); return; }   // mPs3WizActive: setup-wizard net/BT step
    if (!mXmbMode) return;
    if (mSearchActive) return;
    int next = mXmbSystemIndex - 1;
    while (next >= -2) {
        if (next == -1 && mXmbRecent.empty()) { next--; continue; }
        if (next == -2 && mSettingsItems.empty()) { next--; continue; }
        mXmbSystemIndex = next;
        mXmbGameIndex = 0;
        mXmbGameScrollTop = 0;
        mSettingsSelectedIndex = 0;
        mDisplayDirty = true;
        return;
    }
}

void NanoMenu::handleRight() {
    if (mScrapeProgActive) return;   // modal swallows navigation
    // GammaOS Nano: navigating cancels any queued launch.
    cancelPendingLaunch();
    if (mOskActive) {
        oskMoveCursor(NavDir::Right);
        return;
    }
    if (mMenuState == MENU_WIFI || mMenuState == MENU_BT) return;
    if (mMenuState == MENU_SETTINGS) { handleSettingsTreeRight(); return; }
    if (mPs3Xmb || mPs3WizActive) { ps3XmbRight(); return; }   // mPs3WizActive: setup-wizard net/BT step
    if (!mXmbMode) return;
    if (mSearchActive) return;
    int numSys = (int)mXmbSystems.size();
    int next = mXmbSystemIndex + 1;
    while (next <= numSys - 1) {
        if (next == -1 && mXmbRecent.empty()) { next++; continue; }
        mXmbSystemIndex = next;
        mXmbGameIndex = 0;
        mXmbGameScrollTop = 0;
        mSettingsSelectedIndex = 0;
        mDisplayDirty = true;
        return;
    }
}

void NanoMenu::launchXmbGame() {
    // Overlay XMB: the home exit-to-launch handshake (set launch_* props + exit so
    // the framework starts the app) does not apply to the resident overlay - and
    // running it would make the overlay exit/restart without launching. Game
    // relaunch from the overlay needs the am-start intent path (follow-up); for
    // now select-on-a-game is a no-op in overlay mode (apps launch via
    // overlayLaunchPackage; Back resumes, and the running game can be quit).
    if (mOverlayMode) { ALOGI("overlay: game relaunch from overlay not yet wired"); return; }
    int sysIdx, gameIdx;

    // GammaOS Nano: gate the entire XMB launch path until the system
    // is ready to accept a handoff to a home app. Without this, an
    // early A press on a freshly-painted XMB exits NanoMenu /
    // bootanim while user 0 is still locked, leaving the panel black.
    // See NanoMenuSystem.cpp / isLaunchReady() for the readiness
    // criteria. The press is queued -- the main loop fires
    // handleSelect() again as soon as readiness flips, so the user
    // does not need to press A a second time after boot.
    if (!isLaunchReady()) {
        ALOGI("NanoMenu: XMB launch deferred -- boot not ready");
        showLaunchBusyToast();
        return;
    }

    // Recently Played mode (index -1): launch directly from recent entry
    if (mXmbSystemIndex == -1 && !mSearchActive) {
        if (mXmbRecent.empty()) return;
        if (mXmbGameIndex < 0 || mXmbGameIndex >= (int)mXmbRecent.size()) return;
        // Take a copy -- the vector reorder below invalidates references.
        XmbRecentEntry re = mXmbRecent[mXmbGameIndex];

        // Move to front of recent list -- only on disk, not in-memory.
        // Modifying the vector causes a visible shuffle during the
        // transition frames before NanoMenu exits.
        if (mXmbGameIndex > 0) {
            std::vector<XmbRecentEntry> saved = mXmbRecent;
            saved.erase(saved.begin() + mXmbGameIndex);
            saved.insert(saved.begin(), re);
            std::swap(mXmbRecent, saved);
            saveXmbRecent();
            std::swap(mXmbRecent, saved); // restore in-memory order
        }

        if (re.standalone) {
            // Build content URI and intent file
            std::string filename = re.romPath;
            size_t lastSlash = filename.rfind('/');
            if (lastSlash != std::string::npos) filename = filename.substr(lastSlash + 1);
            std::string encodedFilename;
            for (char c : filename) {
                if (c == ' ') encodedFilename += "%20";
                else if (c == '(') encodedFilename += "%28";
                else if (c == ')') encodedFilename += "%29";
                else if (c == '&') encodedFilename += "%26";
                else if (c == '+') encodedFilename += "%2B";
                else if (c == '!') encodedFilename += "%21";
                else if (c == '\'') encodedFilename += "%27";
                else encodedFilename += c;
            }
            // Determine volume ID from romPath (same logic as launchXmbGame)
            std::string volumeId = "primary";
            std::string relDir = "ROMs%2F" + re.romDir;
            if (re.romPath.find("/storage/") == 0) {
                std::string work = re.romPath.substr(9);
                size_t sl1 = work.find('/');
                if (sl1 != std::string::npos) {
                    std::string uuid = work.substr(0, sl1);
                    if (uuid != "emulated") {
                        size_t lastSl = work.rfind('/');
                        std::string subdir = work.substr(sl1 + 1, lastSl - sl1 - 1);
                        std::string encodedDir;
                        for (char c : subdir) {
                            if (c == '/') encodedDir += "%2F";
                            else if (c == ' ') encodedDir += "%20";
                            else encodedDir += c;
                        }
                        volumeId = uuid;
                        relDir = encodedDir;
                    }
                }
            }
            std::string treeRoot = volumeId + "%3A" + relDir;
            std::string contentUri = "content://com.android.externalstorage.documents/tree/"
                + treeRoot + "/document/" + treeRoot + "%2F" + encodedFilename;
            std::string intent = re.launchIntent;
            size_t pos = intent.find("{file.uri}");
            if (pos != std::string::npos) intent.replace(pos, 10, contentUri);
            std::string tabIntent;
            { const char* p = intent.c_str(); while (*p) { while (*p == ' ') p++;
              if (!*p) break; if (!tabIntent.empty()) tabIntent += '\t';
              const char* s = p; while (*p && *p != ' ') p++; tabIntent.append(s, p - s); } }
            android::base::SetProperty("sys.gammaos.nano.launch_app", re.launchPkg);
            { const char* f = "/data/system/nano_launch_intent.txt";
              int ifd = open(f, O_WRONLY|O_CREAT|O_TRUNC, 0666);
              if (ifd >= 0) { write(ifd, tabIntent.c_str(), tabIntent.size()); close(ifd); chmod(f, 0644); }
              android::base::SetProperty("sys.gammaos.nano.launch_intent", "file"); }
            setLaunchRomPath("");
            android::base::SetProperty("sys.gammaos.nano.launch_core", "");

            // GammaOS: Drastic quick-resume prime (recent-played path).
            if (re.launchPkg == "com.dsemu.drastic") {
                if (mQuickResumeEnabled) {
                    setQrRomPath(re.romPath);
                    android::base::SetProperty(
                            "persist.gammaos.nano.qr_core", "drastic");
                    property_set("persist.gammaos.nano.qr_prepared", "1");
                    std::string gameName = filename;
                    size_t dotPos = gameName.rfind('.');
                    if (dotPos != std::string::npos) gameName.erase(dotPos);
                    android::base::SetProperty(
                            "persist.gammaos.nano.qr_game_name", gameName);
                    { const char* qf = "/data/system/nano_drastic_qr_intent.txt";
                      int qfd = open(qf, O_WRONLY|O_CREAT|O_TRUNC, 0666);
                      if (qfd >= 0) {
                          write(qfd, tabIntent.c_str(), tabIntent.size());
                          close(qfd);
                          chmod(qf, 0644);
                      } }
                    property_set("sys.gammaos.nano.cache_ready", "0");
                    property_set("sys.gammaos.nano.cache_op", "populate_drastic");
                } else {
                    // QR disabled: clear any stale prime so we don't
                    // auto-resume a previous game after drastic exits.
                    property_set("persist.gammaos.nano.qr_prepared", "0");
                    android::base::SetProperty("persist.gammaos.nano.qr_core", "");
                }

                // GammaOS: Drastic Nano intercept. Fires regardless of
                // QR state -- drastic-nano reads the ROM path from
                // nano_drastic_nano_rom.txt, not from the QR intent.
                char dn[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.drastic_nano",
                             dn, "0");
                if (dn[0] == '1') {
                    setDrasticNanoRomPath(re.romPath);
                    ALOGW("drastic nano: XMB recent-played launch, "
                          "qr=%d", mQuickResumeEnabled ? 1 : 0);
                    mDrasticNanoPending = true;
                    mSearchActive = false;
                    mOskActive = false;
                    return;
                }
            } else {
                // Non-drastic standalone (PPSSPP, etc.): clear any stale
                // QR prime so the next nano start does not auto-resume an
                // unrelated drastic/retroarch game.
                property_set("persist.gammaos.nano.qr_prepared", "0");
                android::base::SetProperty("persist.gammaos.nano.qr_core", "");
            }
        } else {
            std::string corePath = "/data/data/com.retroarch.aarch64/cores/" + re.coreSo;
            setLaunchRomPath(re.romPath);
            android::base::SetProperty("sys.gammaos.nano.launch_core", corePath);
            android::base::SetProperty("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");
            android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
            property_set("sys.gammaos.nano.cache_ready", "0");
            property_set("sys.gammaos.nano.cache_op", "populate");
            if (mQuickResumeEnabled) {
                setQrRomPath(re.romPath);
                android::base::SetProperty("persist.gammaos.nano.qr_core", corePath);
                property_set("persist.gammaos.nano.qr_prepared", "1");
                std::string gameName;
                { size_t ls = re.romPath.rfind('/');
                  gameName = (ls != std::string::npos)
                          ? re.romPath.substr(ls + 1) : re.romPath; }
                size_t dotPos = gameName.rfind('.');
                if (dotPos != std::string::npos) gameName.erase(dotPos);
                android::base::SetProperty(
                        "persist.gammaos.nano.qr_game_name", gameName);
            }
        }
        ALOGI("NanoMenu XMB: recent launch %s [%s]", re.displayName.c_str(), re.systemName.c_str());
        mSearchActive = false; mOskActive = false;
        property_set("sys.gammaos.nano.xmb_return_sys", "-1");
        property_set("sys.gammaos.nano.xmb_return_game", "0");
        property_set("sys.gammaos.nano.return_recent", "0");
        property_set("sys.gammaos.nano.return_apps", "0");
        property_set("service.bootanim.nano_retroarch", "1");
        property_set("sys.gammaos.nano.drop_input", "1");
        struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
        int64_t fenceNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
        char buf[32]; snprintf(buf, sizeof(buf), "%" PRId64, fenceNs);
        property_set("sys.gammaos.nano.drop_fence_ns", buf);
        mWaitForRelease = true;
        return;
    }

    if (mSearchActive) {
        if (mSearchResults.empty()) return;
        if (mSearchSelectedIndex < 0 || mSearchSelectedIndex >= (int)mSearchResults.size())
            return;
        sysIdx = mSearchResults[mSearchSelectedIndex].sysIdx;
        gameIdx = mSearchResults[mSearchSelectedIndex].gameIdx;
    } else {
        sysIdx = mXmbSystemIndex;
        gameIdx = mXmbGameIndex;
    }

    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    const auto& sys = mXmbSystems[sysIdx];
    if (gameIdx < 0 || gameIdx >= (int)sys.roms.size()) return;

    std::string romPath = sys.roms[gameIdx];
    if (romPath.find("/data/media/0/") == 0) {
        romPath = "/sdcard/" + romPath.substr(14);
    }
    if (romPath.find("/mnt/media_rw/") == 0) {
        romPath = "/storage/" + romPath.substr(14);
    }

    if (sys.isStandalone()) {
        std::string fullRomPath = sys.roms[gameIdx];
        std::string filename;
        { size_t ls = fullRomPath.rfind('/');
          filename = (ls != std::string::npos) ? fullRomPath.substr(ls + 1) : fullRomPath; }
        std::string encodedFilename;
        for (char c : filename) {
            if (c == ' ') encodedFilename += "%20";
            else if (c == '(') encodedFilename += "%28";
            else if (c == ')') encodedFilename += "%29";
            else if (c == '&') encodedFilename += "%26";
            else if (c == '+') encodedFilename += "%2B";
            else if (c == '!') encodedFilename += "%21";
            else if (c == '\'') encodedFilename += "%27";
            else encodedFilename += c;
        }
        std::string volumeId = "primary";
        std::string relDir = "ROMs%2F" + sys.romDir;
        if (fullRomPath.find("/mnt/media_rw/") == 0 || fullRomPath.find("/storage/") == 0) {
            std::string work = fullRomPath;
            if (work.find("/mnt/media_rw/") == 0) work = work.substr(14);
            else if (work.find("/storage/") == 0) work = work.substr(9);
            size_t sl1 = work.find('/');
            if (sl1 != std::string::npos) {
                std::string uuid = work.substr(0, sl1);
                if (uuid != "emulated") {
                    size_t lastSl = work.rfind('/');
                    std::string subdir = work.substr(sl1 + 1, lastSl - sl1 - 1);
                    std::string encodedDir;
                    for (char c : subdir) {
                        if (c == '/') encodedDir += "%2F";
                        else if (c == ' ') encodedDir += "%20";
                        else encodedDir += c;
                    }
                    volumeId = uuid;
                    relDir = encodedDir;
                }
            }
        }
        std::string treeRoot = volumeId + "%3A" + relDir;
        std::string contentUri = "content://com.android.externalstorage.documents/tree/"
            + treeRoot + "/document/" + treeRoot + "%2F" + encodedFilename;

        // custom-package launch: intent template + any user launch args, with the
        // Daijisho token vocabulary substituted ({file.uri} content URI, {file.path}
        // raw path, {file.mime} generic mime). Every occurrence is replaced.
        std::string intent = sys.launchIntent;
        if (!sys.launchArgs.empty()) intent += " " + sys.launchArgs;
        {
            auto subst = [&](const char* token, const std::string& val) {
                size_t p, tl = strlen(token);
                while ((p = intent.find(token)) != std::string::npos) intent.replace(p, tl, val);
            };
            subst("{file.uri}", contentUri);
            subst("{file.path}", romPath);
            subst("{file.mime}", "application/octet-stream");
        }
        std::string tabIntent;
        {
            const char* p = intent.c_str();
            while (*p) {
                while (*p == ' ') p++;
                if (!*p) break;
                if (!tabIntent.empty()) tabIntent += '\t';
                const char* start = p;
                while (*p && *p != ' ') p++;
                tabIntent.append(start, p - start);
            }
        }
        ALOGI("NanoMenu XMB: standalone launch %s uri=%s",
              sys.launchPkg.c_str(), contentUri.c_str());
        android::base::SetProperty("sys.gammaos.nano.launch_app", sys.launchPkg);
        {
            const char* intentFile = "/data/system/nano_launch_intent.txt";
            int ifd = open(intentFile, O_WRONLY | O_CREAT | O_TRUNC, 0666);
            if (ifd >= 0) {
                write(ifd, tabIntent.c_str(), tabIntent.size());
                close(ifd);
                chmod(intentFile, 0644);
                android::base::SetProperty("sys.gammaos.nano.launch_intent", "file");
            } else {
                ALOGE("NanoMenu: failed to write intent file: %s", strerror(errno));
                android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
            }
        }
        setLaunchRomPath("");
        android::base::SetProperty("sys.gammaos.nano.launch_core", "");

        if (sys.launchPkg == "com.dsemu.drastic") {
            if (mQuickResumeEnabled) {
                setQrRomPath(romPath);
                android::base::SetProperty(
                        "persist.gammaos.nano.qr_core", "drastic");
                property_set("persist.gammaos.nano.qr_prepared", "1");
                std::string gameName = filename;
                size_t dotPos = gameName.rfind('.');
                if (dotPos != std::string::npos) gameName.erase(dotPos);
                android::base::SetProperty(
                        "persist.gammaos.nano.qr_game_name", gameName);
                { const char* qf = "/data/system/nano_drastic_qr_intent.txt";
                  int qfd = open(qf, O_WRONLY|O_CREAT|O_TRUNC, 0666);
                  if (qfd >= 0) {
                      write(qfd, tabIntent.c_str(), tabIntent.size());
                      close(qfd);
                      chmod(qf, 0644);
                  } }
                property_set("sys.gammaos.nano.cache_ready", "0");
                property_set("sys.gammaos.nano.cache_op", "populate_drastic");
            } else {
                // QR disabled: clear any stale prime so we don't
                // auto-resume a previous game after drastic exits.
                property_set("persist.gammaos.nano.qr_prepared", "0");
                android::base::SetProperty("persist.gammaos.nano.qr_core", "");
            }

            // GammaOS: Drastic Nano intercept (system browse path).
            // Fires regardless of QR state -- drastic-nano reads the
            // ROM path from nano_drastic_nano_rom.txt, not from the
            // QR intent file.
            char dn[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.nano.drastic_nano",
                         dn, "0");
            if (dn[0] == '1') {
                setDrasticNanoRomPath(romPath);
                ALOGW("drastic nano: XMB system launch, "
                      "qr=%d", mQuickResumeEnabled ? 1 : 0);
                mDrasticNanoPending = true;
                mSearchActive = false;
                mOskActive = false;
                return;
            }
        } else {
            // Non-drastic standalone (PPSSPP, etc.): clear any stale QR
            // prime so the next nano start does not auto-resume an
            // unrelated drastic/retroarch game.
            property_set("persist.gammaos.nano.qr_prepared", "0");
            android::base::SetProperty("persist.gammaos.nano.qr_core", "");
        }
    } else {
        // RetroArch core
        std::string corePath = "/data/data/com.retroarch.aarch64/cores/" + sys.coreSo;
        ALOGI("NanoMenu XMB: launching %s core=%s", romPath.c_str(), corePath.c_str());
        setLaunchRomPath(romPath);
        android::base::SetProperty("sys.gammaos.nano.launch_core", corePath);
        android::base::SetProperty("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");
        android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
        property_set("sys.gammaos.nano.cache_ready", "0");
        property_set("sys.gammaos.nano.cache_op", "populate");

        if (mQuickResumeEnabled) {
            setQrRomPath(romPath);
            android::base::SetProperty("persist.gammaos.nano.qr_core", corePath);
            property_set("persist.gammaos.nano.qr_prepared", "1");
            std::string gameName;
            { size_t ls = romPath.rfind('/');
              gameName = (ls != std::string::npos)
                      ? romPath.substr(ls + 1) : romPath; }
            size_t dotPos = gameName.rfind('.');
            if (dotPos != std::string::npos) gameName.erase(dotPos);
            android::base::SetProperty(
                    "persist.gammaos.nano.qr_game_name", gameName);
        }
    }

    // Record in recently played
    if (!mSearchActive) {
        addXmbRecent(sysIdx, gameIdx);
    } else {
        addXmbRecent(sysIdx, gameIdx);
    }

    mSearchActive = false;
    mOskActive = false;

    // Save return state
    {
        char buf[32];
        if (!mSearchActive && mXmbSystemIndex >= 0) {
            snprintf(buf, sizeof(buf), "%d", mXmbSystemIndex);
            property_set("sys.gammaos.nano.xmb_return_sys", buf);
            snprintf(buf, sizeof(buf), "%d", gameIdx);
            property_set("sys.gammaos.nano.xmb_return_game", buf);
        } else {
            property_set("sys.gammaos.nano.xmb_return_sys", "-1");
            property_set("sys.gammaos.nano.xmb_return_game", "0");
        }
    }

    property_set("sys.gammaos.nano.return_recent", "0");
    property_set("sys.gammaos.nano.return_apps", "0");
    property_set("service.bootanim.nano_retroarch", "1");
    property_set("sys.gammaos.nano.drop_input", "1");

    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t fenceNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
    char buf[32];
    snprintf(buf, sizeof(buf), "%" PRId64, fenceNs);
    property_set("sys.gammaos.nano.drop_fence_ns", buf);

    mWaitForRelease = true;
}

// ---------------------------------------------------------------------------
// On-Screen Keyboard (Search)
// ---------------------------------------------------------------------------

// openOsk / closeOsk / oskType / oskBackspace / oskConfirm and renderOsk now
// live in NanoOsk.cpp (the Leanback-derived multi-script keyboard). Only the
// XMB-coupled search-result query stays here.

void NanoMenu::updateSearchResults() {
    mSearchResults.clear();
    if (mOskQuery.empty()) return;
    for (int s = 0; s < (int)mXmbSystems.size(); s++) {
        const auto& sys = mXmbSystems[s];
        for (int g = 0; g < (int)sys.displayNames.size(); g++) {
            if (containsInsensitive(sys.displayNames[g], mOskQuery)) {
                mSearchResults.push_back({s, g});
                if (mSearchResults.size() >= 100) return; // cap results
            }
        }
    }
    mSearchActive = !mSearchResults.empty() || !mOskQuery.empty();
}

// ---------------------------------------------------------------------------
// XMB Rendering
// ---------------------------------------------------------------------------

void NanoMenu::renderXmb() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Smooth animation -- frame-rate-independent exponential decay.
    float dt = mFrameDt;
    float decay = 1.0f - expf(-12.0f * dt);
    mXmbAnimX += ((float)mXmbSystemIndex - mXmbAnimX) * decay;
    if (fabsf(mXmbAnimX - mXmbSystemIndex) < 0.005f) mXmbAnimX = mXmbSystemIndex;
    // Settings column drives its own cursor variable; the rest of XMB
    // uses mXmbGameIndex / mSearchSelectedIndex. Without this check the
    // animation target stays pinned at game-index 0 while on Settings,
    // so Up/Down appear to do nothing visually.
    float targetY;
    if (mSearchActive) {
        targetY = (float)mSearchSelectedIndex;
    } else if (isOnSettingsColumn()) {
        targetY = (float)mSettingsSelectedIndex;
    } else {
        targetY = (float)mXmbGameIndex;
    }
    mXmbAnimY += (targetY - mXmbAnimY) * decay;
    if (fabsf(mXmbAnimY - targetY) < 0.005f) mXmbAnimY = targetY;

    int numSys = (int)mXmbSystems.size();
    if (numSys == 0) return;

    // PSP-style: white/gray icons, no gold tint
    float iconR = 0.85f, iconG = 0.85f, iconB = 0.85f;
    float dimIconR = 0.45f, dimIconG = 0.45f, dimIconB = 0.45f;

    // Layout -- based on RetroArch XMB driver constants
    float scaleFactor = sf;
    float iconSize = 100.0f * scaleFactor;
    float catActiveZoom = 1.0f;
    float catPassiveZoom = 0.55f;
    float itemActiveZoom = 0.8f;
    float itemPassiveZoom = 0.4f;
    float iconSpacingH = 200.0f * scaleFactor;
    float iconSpacingV = 110.0f * scaleFactor;
    float marginTop = 180.0f * scaleFactor;
    float marginLeft = 120.0f * scaleFactor;
    float labelLeft = 20.0f * scaleFactor;
    float aboveItemOff = -1.5f;
    float underItemOff = 2.5f;
    float iconBarY = marginTop;
    float selIconX = marginLeft;
    float textScale = 2.2f * sf;
    float selTextScale = 2.8f * sf;
    float footScale = 1.4f * sf;
    float catNameScale = 1.8f * sf;

    (void)itemActiveZoom;
    (void)itemPassiveZoom;
    (void)aboveItemOff;
    (void)underItemOff;

    bool isRecent = (mXmbSystemIndex == -1);
    bool isSettings = isOnSettingsColumn();

    // --- Horizontal category bar ---
    auto drawCatIcon = [&](int idx, float hOffset, bool isSel, int iconId) {
        float zoom = isSel ? catActiveZoom : catPassiveZoom;
        float sz = iconSize * zoom;
        float alpha = isSel ? 1.0f : fmaxf(0.15f, 1.0f - fabsf(hOffset) * 0.15f);
        float ix = selIconX + hOffset * iconSpacingH;
        float iy = iconBarY - sz / 2.0f;
        if (ix < -sz || ix > mWidth + sz) return; // cull
        float cr = isSel ? iconR : dimIconR;
        float cg = isSel ? iconG : dimIconG;
        float cb = isSel ? iconB : dimIconB;
        drawIcon(iconId, ix, iy, sz, cr, cg, cb, alpha);
        if (isSel) {
            const char* name = (idx == -2) ? "Settings"
                             : (idx == -1) ? "Recently Played"
                             : (idx >= 0 && idx < numSys) ? mXmbSystems[idx].name.c_str()
                             : "";
            float nameY = iy + sz + 6.0f * sf;
            float nameW = measureText(name, catNameScale);
            float nameCX = ix + sz / 2.0f - nameW / 2.0f;
            drawText(name, nameCX, nameY, catNameScale, 0.8f, 0.8f, 0.8f, 0.9f);
        }
    };

    // Settings (index -2), leftmost column
    if (!mSettingsItems.empty()) {
        float hOff = -2.0f - mXmbAnimX;
        drawCatIcon(-2, hOff, isSettings, 17);
    }
    // Recently Played (index -1)
    if (!mXmbRecent.empty()) {
        float hOff = -1.0f - mXmbAnimX;
        drawCatIcon(-1, hOff, isRecent, 15);
    }
    // System icons (index 0..N-1)
    for (int i = 0; i < numSys; i++) {
        float hOff = (float)i - mXmbAnimX;
        if (fabsf(hOff) > 8.0f) continue;
        drawCatIcon(i, hOff, !isRecent && !isSettings && i == mXmbSystemIndex,
                    i < 16 ? i : 0);
    }

    // --- Vertical item list ---
    float selSz = iconSize * catActiveZoom;
    float textStartX = selIconX + selSz + labelLeft;
    float contentRight = mWidth * 0.93f;

    (void)textStartX;

    int numItems = 0;
    bool hasItems = true;
    if (isRecent) {
        numItems = (int)mXmbRecent.size();
    } else if (mSearchActive) {
        numItems = (int)mSearchResults.size();
    } else if (isSettings) {
        numItems = (int)mSettingsItems.size();
    } else {
        int si = mXmbSystemIndex;
        if (si >= 0 && si < numSys) numItems = (int)mXmbSystems[si].roms.size();
    }
    if (numItems == 0) hasItems = false;

    int curIdx = isRecent ? mXmbGameIndex
               : mSearchActive ? mSearchSelectedIndex
               : isSettings ? mSettingsSelectedIndex : mXmbGameIndex;
    if (curIdx >= numItems) curIdx = numItems - 1;
    if (curIdx < 0) curIdx = 0;

    if (hasItems) {
        int maxAbove = (int)(iconBarY / iconSpacingV) + 1;
        int maxBelow = (int)((mHeight - iconBarY) / iconSpacingV) + 1;
        int startItem = curIdx - maxAbove;
        int endItem = curIdx + maxBelow;
        if (startItem < 0) startItem = 0;
        if (endItem >= numItems) endItem = numItems - 1;

        float selCatSz = iconSize * catActiveZoom;
        float itemListTop = iconBarY + selCatSz / 2.0f + FONT_CHAR_H * catNameScale + 140.0f * sf;

        float clipTop = iconBarY + selCatSz / 2.0f + 10.0f * sf;
        float animCur = mXmbAnimY;
        float itemIconBaseX = selIconX + (iconSize * catActiveZoom) / 2.0f;

        const float selIconSz = iconSize * catActiveZoom;
        const float listScissorX = itemIconBaseX - selIconSz / 2.0f;
        const float listScissorW = contentRight - listScissorX;
        glEnable(GL_SCISSOR_TEST);
        {
            int sx, sy, sw, sh;
            int lx = (int)listScissorX, ly = 0,
                lw = (int)listScissorW, lh = (int)mHeight;
            switch (sDrmGlRotation ? sDrmRotationDeg : 0) {
            case 90:
                sx = ly; sy = mWidth - lx - lw;
                sw = lh; sh = lw;
                break;
            case 180:
                sx = mWidth - lx - lw; sy = mHeight - ly - lh;
                sw = lw; sh = lh;
                break;
            case 270:
                sx = mHeight - ly - lh; sy = lx;
                sw = lh; sh = lw;
                break;
            default:
                sx = lx; sy = ly; sw = lw; sh = lh;
                break;
            }
            glScissor(sx, sy, sw, sh);
        }

        for (int i = startItem; i <= endItem; i++) {
            float relPos = (float)i - animCur;
            float fy = itemListTop + relPos * iconSpacingV;
            if (fy < clipTop - iconSpacingV * 0.3f || fy > mHeight + iconSpacingV) continue;

            bool isSel = (fabsf((float)i - animCur) < 0.5f);
            float iAlpha = isSel ? 1.0f : 0.55f;
            float tSc = isSel ? selTextScale : textScale;

            const char* displayText = "";
            const char* sysLabel = nullptr;
            if (isRecent && i < (int)mXmbRecent.size()) {
                displayText = mXmbRecent[i].displayName.c_str();
                sysLabel = mXmbRecent[i].systemName.c_str();
            } else if (mSearchActive && i < (int)mSearchResults.size()) {
                const auto& res = mSearchResults[i];
                if (res.sysIdx < numSys) {
                    displayText = mXmbSystems[res.sysIdx].displayNames[res.gameIdx].c_str();
                    sysLabel = mXmbSystems[res.sysIdx].shortname.c_str();
                }
            } else if (isSettings && i < (int)mSettingsItems.size()) {
                displayText = mSettingsItems[i].label.c_str();
            } else {
                int si = mXmbSystemIndex;
                if (si >= 0 && si < numSys && i < (int)mXmbSystems[si].displayNames.size()) {
                    displayText = mXmbSystems[si].displayNames[i].c_str();
                }
            }

            float itemIconSz = isSel ? 50.0f * sf : 30.0f * sf;
            float iconX = itemIconBaseX - itemIconSz / 2.0f;
            float iconY = fy - itemIconSz / 2.0f;
            drawIcon(16, iconX, iconY, itemIconSz,
                     isSel ? iconR : dimIconR, isSel ? iconG : dimIconG,
                     isSel ? iconB : dimIconB, iAlpha);

            float tx = iconX + itemIconSz + 10.0f * sf;
            float ty = fy - FONT_CHAR_H * tSc * 0.4f;
            float tr = isSel ? 1.0f : 0.6f;
            float tg = isSel ? 1.0f : 0.6f;
            float tb = isSel ? 1.0f : 0.6f;

            drawText(displayText, tx, ty, tSc, tr, tg, tb, iAlpha);
            if (isSel && sysLabel && *sysLabel) {
                float tagY = ty + FONT_CHAR_H * tSc + 2.0f * sf;
                drawText(sysLabel, tx, tagY, catNameScale * 0.9f,
                         0.5f, 0.5f, 0.55f, 0.7f);
            }
        }
        glDisable(GL_SCISSOR_TEST);
    } else if (!mSearchActive) {
        const char* msg = isRecent ? "No recently played games"
                        : (mXmbSystemIndex >= 0 && mXmbSystemIndex < numSys
                           && mXmbSystems[mXmbSystemIndex].pathExists)
                          ? "No games found" : "ROM folder not found";
        float msgW = measureText(msg, textScale);
        drawText(msg, (mWidth - msgW) / 2.0f, iconBarY + 40.0f * sf,
                 textScale, 0.5f, 0.5f, 0.5f, 0.7f);
    }

    // Footer
    float footH = FONT_CHAR_H * footScale;
    float footY = mHeight - footH - 8.0f * sf;
    const char* footer = mSearchActive
        ? "Up/Dn: Browse | A: Launch | B: Clear | Y: Refine"
        : "L/R: System | Up/Dn: Game | A: Play | Y: Search | X: FX | L1: List | R1: QR";
    float fW = measureText(footer, footScale);
    if (!mOskActive)   // the OSK draws its own footer on top
        drawText(footer, (mWidth - fW) / 2.0f, footY, footScale, 0.35f, 0.35f, 0.4f, 0.8f);

    // Search indicator
    if (mSearchActive && !mOskActive) {
        char searchHdr[64];
        snprintf(searchHdr, sizeof(searchHdr), "Search: \"%s\"", mOskQuery.c_str());
        drawText(searchHdr, 10.0f * sf, footY - FONT_CHAR_H * footScale - 4.0f * sf,
                 footScale, 0.7f, 0.7f, 0.2f, 0.9f);
    }

    // OSK overlay is rendered by render() AFTER the Wi-Fi/BT screens, so the
    // password keyboard sits on top of the network list instead of being
    // overdrawn by it.
}

} // namespace android
