/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

// Game Systems editor: the Emulator chooser, backed by the bundled Daijishou
// community platform configs (119 platforms, ~917 players). Covers RetroArch
// libretro cores AND standalone emulators (AetherSX2, Dolphin, azahar, ...).
// Parsed once at runtime with the embedded NanoJson reader; picking an entry
// auto-fills the system's launch type, core/package, intent template and
// accepted extensions per the Daijishou amStartArguments template.

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoJson.h"
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings

#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <string.h>
#include <strings.h>
#include <algorithm>
#include <utils/Log.h>

namespace android {

// Read a whole file into a string (bounded). Returns "" on failure.
static std::string readWholeFile(const std::string& path, size_t maxSz) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return "";
    std::string out;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0 && (size_t)st.st_size < maxSz) {
        out.resize(st.st_size);
        ssize_t rd = read(fd, &out[0], st.st_size);
        if (rd > 0) out.resize(rd); else out.clear();
    }
    close(fd);
    return out;
}

// Whitespace-normalize an am string to single spaces (newlines/tabs -> space).
static std::string normalizeWs(const std::string& in) {
    std::string s; bool sp = false;
    for (char c : in) {
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { sp = true; }
        else { if (sp && !s.empty()) s += ' '; sp = false; s += c; }
    }
    return s;
}

// Extract a dotted, comma-separated extension list from a Daijishou
// acceptedFilenameRegex by reading its first (?:a|b|c) alternation group.
static std::string extsFromRegex(const std::string& rx) {
    size_t a = rx.find("(?:");
    if (a == std::string::npos) return "";
    size_t b = rx.find(')', a);
    if (b == std::string::npos) return "";
    std::string grp = rx.substr(a + 3, b - a - 3);
    std::string out, tok;
    auto flush = [&]() {
        if (!tok.empty() && tok.find('\\') == std::string::npos) {
            if (!out.empty()) out += ",";
            out += "." + tok;
        }
        tok.clear();
    };
    for (char c : grp) { if (c == '|') flush(); else tok += c; }
    flush();
    return out;
}

void NanoMenu::loadEmuCatalog() {
    if (!mEmuCatalog.empty()) return;
    const char* dirs[2] = {
        "/data/system/nano_xmb/daijishou",
        "/system/etc/nano_xmb/daijishou",
    };
    std::vector<std::string> files;
    std::string base;
    for (const char* d : dirs) {
        DIR* dp = opendir(d);
        if (!dp) continue;
        struct dirent* e;
        while ((e = readdir(dp)) != nullptr) {
            const char* n = e->d_name;
            if (n[0] == '.') continue;
            size_t len = strlen(n);
            if (len < 6 || strcasecmp(n + len - 5, ".json") != 0) continue;
            files.push_back(std::string(d) + "/" + n);
        }
        closedir(dp);
        if (!files.empty()) { base = d; break; }
    }
    for (const auto& f : files) {
        std::string text = readWholeFile(f, 2 * 1024 * 1024);
        if (text.empty()) continue;
        njson::Value root;
        if (!njson::parse(text, &root) || !root.isObject()) continue;
        const njson::Value* plat = root.find("platform");
        std::string platName = plat ? plat->getString("name") : "";
        std::string platRegex = plat ? plat->getString("acceptedFilenameRegex") : "";
        std::string platId = plat ? plat->getString("uniqueId") : "";
        if (platName.empty()) platName = trDyn("Platform");
        const njson::Value* players = root.find("playerList");
        if (!players || !players->isArray()) continue;
        for (const auto& pv : players->arr) {
            if (!pv.isObject()) continue;
            EmuCatEntry e;
            e.platform = platName;
            e.platformId = platId;
            e.player = pv.getString("name");
            e.amArgs = normalizeWs(pv.getString("amStartArguments"));
            e.playerRegex = pv.getString("acceptedFilenameRegex");
            e.platformRegex = platRegex;
            if (e.player.empty() || e.amArgs.empty()) continue;
            // The catalog lists every libretro core up to three times (the
            // RetroArch / RetroArch 32 / RetroArch 64 player variants). Keep
            // ONLY the 64-bit build (com.retroarch.aarch64): nano's native
            // LibretroRunner dlopen()s aarch64 cores, so the universal and the
            // 32-bit variants are pure duplicates here (the single 32-bit-only
            // core in the whole set could never load anyway).
            {
                std::string pkg;
                size_t np = e.amArgs.find("-n ");
                if (np != std::string::npos) {
                    size_t s = np + 3;
                    size_t epos = e.amArgs.find_first_of(" /", s);
                    pkg = e.amArgs.substr(s, epos == std::string::npos
                                                 ? std::string::npos : epos - s);
                }
                if (pkg.rfind("com.retroarch", 0) == 0
                    && pkg != "com.retroarch.aarch64") continue;
            }
            // Players are named "<uid> - <player>" in the catalog; the picker
            // label already leads with the platform display name, so drop the
            // redundant uid prefix ("ps2 - Aethersx2" -> "Aethersx2").
            if (!platId.empty() && e.player.size() > platId.size() + 3
                && strncasecmp(e.player.c_str(), platId.c_str(), platId.size()) == 0
                && e.player.compare(platId.size(), 3, " - ") == 0)
                e.player = e.player.substr(platId.size() + 3);
            mEmuCatalog.push_back(std::move(e));
        }
    }
    std::sort(mEmuCatalog.begin(), mEmuCatalog.end(),
              [](const EmuCatEntry& a, const EmuCatEntry& b) {
                  int c = strcasecmp(a.platform.c_str(), b.platform.c_str());
                  if (c != 0) return c < 0;
                  return strcasecmp(a.player.c_str(), b.player.c_str()) < 0;
              });
    ALOGI("emucatalog: loaded %zu players from %s", mEmuCatalog.size(), base.c_str());
}

void NanoMenu::buildEmulatorPicker(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Emulator"; out.screenKind = GS_EMUPICK;
    // "Custom..." first so manual entry is always one press away.
    { Ps3Item it; it.label = "Custom (type core / package)..."; it.kind = PS3_GS_EMU_CUSTOM;
      it.iconTex = 0; it.nmapTex = nmapForIcon(22);
      it.iconR = it.iconG = it.iconB = 1.0f; out.items.push_back(it); }
    for (int i = 0; i < (int)mEmuCatalog.size(); i++) {
        const EmuCatEntry& e = mEmuCatalog[i];
        if (!mEmuPickFilter.empty()) {
            if (!strcasestr(e.platform.c_str(), mEmuPickFilter.c_str()) &&
                !strcasestr(e.player.c_str(), mEmuPickFilter.c_str()))
                continue;
        }
        Ps3Item it;
        it.label = e.platform + " - " + e.player;
        it.kind = PS3_GS_EMUROW; it.a = i;
        it.iconTex = 0; it.nmapTex = nmapForIcon(22);
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::gsOpenEmulatorPicker() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    loadEmuCatalog();
    // Pre-filter to the system's name/shortname so relevant emulators show first.
    const XmbSystem& sys = mXmbSystems[mGsEditIdx];
    mEmuPickFilter = sys.shortname.empty() ? sys.name : sys.shortname;
    // Snapshot the parent (editor) for the collapse-rail animation, since this
    // push bypasses ps3XmbSelect's switch (which normally arms it).
    std::vector<Ps3Item> parentSnap = mPs3Stack.empty() ? std::vector<Ps3Item>() : mPs3Stack.back().items;
    int parentSel = mPs3Stack.empty() ? 0 : mPs3Stack.back().sel;

    Ps3Level lvl;
    buildEmulatorPicker(lvl);
    // If the pre-filter matched nothing, fall back to the full list.
    if (lvl.items.size() <= 1) { mEmuPickFilter.clear(); buildEmulatorPicker(lvl); }
    mPs3Stack.push_back(lvl);

    // Animate the editor collapsing into the breadcrumb + the picker sliding in
    // (250ms easeOutCubic, dir +1) - same language as every other submenu push.
    mPs3SubParentItems = std::move(parentSnap);
    mPs3SubParentIdx   = parentSel;
    mPs3SubChildItems  = mPs3Stack.back().items;
    mPs3SubDir         = 1;
    mPs3SubAnimStart   = mEffectTime;
    mPs3SubAnim        = 0.0f;
    mPs3AnimItem       = 0.0f;
    mPs3ItemAnimStart  = -1.0f;
}

// Daijishou platform uniqueId -> LibretroDB monochrome icon basename (the
// bundled /system/etc/nano_xmb/icons_retroarch set). The catalog platform
// display names ("Sony PlayStation 2") do NOT match the LibretroDB icon
// naming ("Sony - PlayStation 2"), so new systems created from the catalog
// resolve their icon through this table. Verified complete against the
// bundled icon set; platforms with no sensible icon (pico8/lowresnx/wasm4)
// are absent and fall back to the sanitized-name guess -> generic cartridge.
static const struct { const char* uid; const char* icon; } kPlatformIconMap[] = {
    { "3do", "The_3DO_Company_-_3DO" },   // 3DO
    { "3ds", "Nintendo_-_Nintendo_3DS" },   // Nintendo 3DS
    { "amiga", "Commodore_-_Amiga" },   // Commodore Amiga
    { "appleii", "Apple_-_II" },   // Apple II
    { "arcadia", "Emerson_-_Arcadia_2001" },   // Arcadia 2001
    { "arduboy", "Arduboy_Inc_-_Arduboy" },   // Arduboy
    { "atari2600", "Atari_-_2600" },   // Atari 2600
    { "atari5200", "Atari_-_5200" },   // Atari 5200
    { "atari7800", "Atari_-_7800" },   // Atari 7800
    { "atarist", "Atari_-_ST" },   // Atari ST
    { "atomiswave", "Sega_-_Dreamcast" },   // Atomiswave
    { "bbcmicro", "Acorn_-_BBC_Micro" },   // Acorn Computers BBC Micro
    { "c64", "Commodore_-_64" },   // Commodore 64
    { "cannonball", "Cannonball" },   // Cannonball OutRun Engine
    { "cavestory", "Cave_Story" },   // Cave Story Game Engine
    { "cdi", "Philips_-_CD-i" },   // Philips CD-i
    { "channelf", "Fairchild_-_Channel_F" },   // Fairchild Channel F
    { "chip8", "CHIP-8" },   // CHIP-8
    { "coleco", "Coleco_-_ColecoVision" },   // ColecoVision
    { "cpc", "Amstrad_-_CPC" },   // Amstrad CPC
    { "cps1", "Capcom_-_CP_System_I" },   // CP System I
    { "cps2", "Capcom_-_CP_System_II" },   // CP System II
    { "cps3", "Capcom_-_CP_System_III" },   // CP System III
    { "doom", "DOOM" },   // DOOM Game Engine
    { "dos", "DOS" },   // DOS
    { "dreamcast", "Sega_-_Dreamcast" },   // Dreamcast
    { "ebook", "Non_Redump_-_Sony_Electronic_Book" },   // Book Reader
    { "elektor", "1292_Advanced_Programmable_Video_System" },   // Elektor TV Games Computer
    { "fbneo", "FBNeo_-_Arcade_Games" },   // Arcade (FinalBurn Neo)
    { "fds", "Nintendo_-_Family_Computer_Disk_System" },   // Famicom Disk System
    { "flashback", "Flashback" },   // Flashback Game Engine
    { "g7400", "Philips_-_Videopacplus" },   // Philips Videopac+ G7400
    { "gamegear", "Sega_-_Game_Gear" },   // Sega Game Gear
    { "gb", "Nintendo_-_Game_Boy" },   // Nintendo - Game Boy
    { "gba", "Nintendo_-_Game_Boy_Advance" },   // Nintendo - Game Boy Advance
    { "gbc", "Nintendo_-_Game_Boy_Color" },   // Nintendo - Game Boy Color
    { "gc", "Nintendo_-_GameCube" },   // Nintendo - GameCube
    { "genesis", "Sega_-_Mega_Drive_-_Genesis" },   // Sega Genesis
    { "genesismsu", "Sega_-_Mega_Drive_-_Genesis" },   // Sega Genesis - MSU
    { "gw", "Nintendo_-_Game_and_Watch" },   // Nintendo - Game & Watch
    { "idtech", "DOOM" },   // idTech4A++
    { "intellivision", "Mattel_-_Intellivision" },   // Intellivision
    { "j2me", "Mobile_-_J2ME" },   // Java Me
    { "jaguar", "Atari_-_Jaguar" },   // Atari Jaguar
    { "jaguarcd", "Atari_-_Jaguar_CD" },   // Atari Jaguar CD
    { "lynx", "Atari_-_Lynx" },   // Atari Lynx
    { "mame", "MAME" },   // Arcade (MAME)
    { "master", "Sega_-_Master_System_-_Mark_III" },   // Sega Master System
    { "megaduck", "Welback_-_Mega-Duck" },   // Mega Duck
    { "model3", "MAME" },   // Sega Model 3
    { "moonlight", "menu_stream" },   // Moonlight Streaming
    { "msx", "Microsoft_-_MSX" },   // MSX
    { "n64", "Nintendo_-_Nintendo_64" },   // Nintendo 64
    { "naomi", "Sega_-_Dreamcast" },   // Sega Naomi
    { "nds", "Nintendo_-_Nintendo_DS" },   // Nintendo DS
    { "ndsi", "Nintendo_-_Nintendo_DSi" },   // Nintendo DSi
    { "neogeo", "SNK_-_Neo_Geo" },   // Neo Geo
    { "neogeocd", "SNK_-_Neo_Geo_CD" },   // Neo Geo CD
    { "nes", "Nintendo_-_Nintendo_Entertainment_System" },   // Nintendo Entertainment System
    { "ngage", "Nokia_-_N-Gage" },   // Nokia N-Gage
    { "ngp", "SNK_-_Neo_Geo_Pocket" },   // NeoGeo Pocket
    { "ngpc", "SNK_-_Neo_Geo_Pocket_Color" },   // NeoGeo Pocket Color
    { "odyssey2", "Magnavox_-_Odyssey2" },   // Magnavox Odyssey 2
    { "palm", "Mobile_-_Palm_OS" },   // Palm OS
    { "pc88", "NEC_-_PC-8001_-_PC-8801" },   // NEC PC-88
    { "pc98", "NEC_-_PC-98" },   // NEC PC-98
    { "pcfx", "NEC_-_PC-FX" },   // NEC PC-FX
    { "pet", "Commodore_-_PET" },   // Commodore - PET
    { "pico", "Sega_-_PICO" },   // Sega Pico
    { "plus4", "Commodore_-_Plus-4" },   // Commodore - PLUS/4
    { "pokemini", "Nintendo_-_Pokemon_Mini" },   // Pokemon Mini
    { "ps2", "Sony_-_PlayStation_2" },   // Sony PlayStation 2
    { "ps3", "Sony_-_PlayStation_3" },   // Sony PlayStation 3
    { "psp", "Sony_-_PlayStation_Portable" },   // PlayStation Portable
    { "pspminis", "Sony_-_PlayStation_Portable_PSN" },   // PlayStation Portable Minis
    { "psx", "Sony_-_PlayStation" },   // Sony PlayStation
    { "quake", "Quake" },   // Quake Game Engine
    { "quake2", "Quake_II" },   // Quake II Game Engine
    { "rpgmaker", "RPG_Maker" },   // RPG Maker
    { "satellaview", "Nintendo_-_Satellaview" },   // Nintendo Satellaview
    { "saturn", "Sega_-_Saturn" },   // Sega Saturn
    { "scummvm", "ScummVM" },   // ScummVM
    { "sega32x", "Sega_-_32X" },   // Sega 32X
    { "segacd", "Sega_-_Mega-CD_-_Sega_CD" },   // Sega CD
    { "sg1000", "Sega_-_SG-1000" },   // Sega SG-1000
    { "snes", "Nintendo_-_Super_Nintendo_Entertainment_System" },   // Super Nintendo Entertainment System
    { "snesmsu1", "Nintendo_-_Super_Nintendo_Entertainment_System" },   // Super Nintendo Entertainment System - MSU-1
    { "steam", "IBM_-_PC_and_Compatibles" },   // Steam
    { "supergrafx", "NEC_-_PC_Engine_SuperGrafx" },   // SuperGrafx
    { "supervision", "Watara_-_Supervision" },   // Watara Supervision
    { "switch", "Nintendo_-_Switch" },   // Nintendo Switch
    { "tg16", "NEC_-_PC_Engine_-_TurboGrafx_16" },   // TurboGrafx-16
    { "tgcd", "NEC_-_PC_Engine_CD_-_TurboGrafx-CD" },   // TurboGrafx-CD
    { "tic80", "TIC-80" },   // TIC-80
    { "triforce", "Namco_Sega_Nintendo_-_TriForce_Cartridges" },   // Triforce
    { "uzebox", "Uzebox" },   // Uzebox
    { "vc4000", "Interton_-_VC_4000" },   // Interton VC 4000
    { "vectrex", "GCE_-_Vectrex" },   // Vectrex
    { "vic20", "Commodore_-_VIC-20" },   // VIC-20
    { "videos", "movies" },   // Video Player
    { "virtualboy", "Nintendo_-_Virtual_Boy" },   // Virtual Boy
    { "vita", "Sony_-_PlayStation_Vita" },   // Sony PS Vita
    { "wii", "Nintendo_-_Wii" },   // Nintendo Wii
    { "wiiu", "Nintendo_-_Wii_U" },   // Nintendo Wii U
    { "wiiware", "Nintendo_-_Wii_Digital_CDN" },   // Nintendo WiiWare
    { "windows", "IBM_-_PC_and_Compatibles" },   // Windows
    { "ws", "Bandai_-_WonderSwan" },   // WonderSwan
    { "wsc", "Bandai_-_WonderSwan_Color" },   // WonderSwan Color
    { "x1", "Sharp_-_X1" },   // Sharp X1
    { "x68000", "Sharp_-_X68000" },   // Sharp X68000
    { "xbox", "Microsoft_-_Xbox" },   // Microsoft - Xbox
    { "xbox360", "Microsoft_-_Xbox_360" },   // Microsoft - XBox 360
    { "xcloud", "Microsoft_-_Xbox_One_Digital" },   // XBox Game Pass
    { "zx81", "Sinclair_-_ZX_81" },   // ZX 81
    { "zxspectrum", "Sinclair_-_ZX_Spectrum" },   // ZX Spectrum
};

// Map a platform uniqueId to its bundled icon name, or "" when unmapped.
static std::string platformIconFor(const std::string& uid) {
    for (const auto& m : kPlatformIconMap)
        if (uid == m.uid) return m.icon;
    return "";
}

// Defined in NanoMenuPS3Folder.cpp.
int nano_makeUniqueSystem(std::vector<NanoMenu::XmbSystem>& systems, const std::string& name,
                          const std::string& preferredId);

// Apply a catalog entry's launch config (type / core / package / intent / exts)
// to a system, classifying RetroArch libretro players vs standalone packages.
void NanoMenu::applyEmuEntryToSystem(XmbSystem& s, const EmuCatEntry& e) {
    std::vector<std::string> toks;
    { std::string t; for (char c : e.amArgs) { if (c == ' ') { if (!t.empty()) { toks.push_back(t); t.clear(); } } else t += c; }
      if (!t.empty()) toks.push_back(t); }
    std::string comp, libretro;
    for (size_t i = 0; i < toks.size(); i++) {
        if (toks[i] == "-n" && i + 1 < toks.size()) comp = toks[i + 1];
        if (toks[i] == "-e" && i + 2 < toks.size() && toks[i + 1] == "LIBRETRO") libretro = toks[i + 2];
    }
    std::string package = comp;
    { size_t sl = package.find('/'); if (sl != std::string::npos) package = package.substr(0, sl); }
    bool isRA = (package.find("retroarch") != std::string::npos) && !libretro.empty();
    if (isRA) {
        s.launchType = NanoMenu::XLT_LIBRETRO_CORE;
        s.coreSo = libretro + "_libretro_android.so";
        s.packageName.clear(); s.launchPkg.clear(); s.launchIntent.clear();
    } else {
        s.launchType = NanoMenu::XLT_CUSTOM_PACKAGE;
        s.coreSo.clear(); s.packageName = package; s.launchPkg = package;
        s.launchIntent = e.amArgs;   // tokens ({file.uri} etc.) preserved for launchXmbGame
    }
    std::string exts = extsFromRegex(e.playerRegex);
    if (exts.empty()) exts = extsFromRegex(e.platformRegex);
    if (!exts.empty()) s.acceptExts = exts;
}

void NanoMenu::applyEmulatorChoice(int catIdx) {
    if (catIdx < 0 || catIdx >= (int)mEmuCatalog.size()) return;
    const EmuCatEntry& e = mEmuCatalog[catIdx];

    bool adding = mGsAddMode;
    int idx;
    if (adding) {
        idx = nano_makeUniqueSystem(mXmbSystems, e.platform, e.platformId);
        // Platform icon from the curated uniqueId map (the Daijishou display
        // names do not match the LibretroDB icon names). Unmapped platforms
        // fall back to a sanitized-name guess, then to the generic cartridge
        // inside resolveSystemIcon.
        std::string iconName = platformIconFor(e.platformId);
        if (iconName.empty()) {
            iconName = e.platform;
            for (char& c : iconName)
                if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                      (c >= '0' && c <= '9') || c == '.' || c == '-')) c = '_';
        }
        mXmbSystems[idx].iconRef = "retroarch:" + iconName;
        mGsAddMode = false;
    } else {
        if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
        idx = mGsEditIdx;
    }
    XmbSystem& s = mXmbSystems[idx];
    applyEmuEntryToSystem(s, e);
    ALOGI("emucatalog: %s '%s - %s' to %s (type=%d pkg=%s core=%s)",
          adding ? "added" : "applied", e.platform.c_str(), e.player.c_str(),
          s.id.c_str(), s.launchType, s.packageName.c_str(), s.coreSo.c_str());

    // Extensions likely changed: invalidate + rescan (off the bg path).
    unlink(xmbCachePath(s).c_str());
    s.scanned = false;
    if (!mBgScanThreadRunning) forceRescanAllSystems();
    saveSystemsConfig();

    if (adding) {
        // Open the editor for the brand-new system. The picker was already
        // popped by the dispatch; the Game Systems list is below it.
        mGsEditIdx = idx;
        gsRefreshStackLevels();
        Ps3Level lvl; buildGameSystemEditor(idx, lvl); mPs3Stack.push_back(lvl);
    } else {
        gsRefreshStackLevels();
    }
    buildPs3Cats();
}

} // namespace android
