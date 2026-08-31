/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

// Game Systems editor: the native raw-path folder picker (scan-source
// selection) and the add / remove custom-system flow. All native XMB screens
// (pushed Ps3Levels) - no Activities, no SAF picker. nano reads /storage,
// /storage/<UUID> and /mnt/media_rw/<UUID> directly (perms already relaxed).

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings

#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <stdio.h>
#include <stdlib.h>   // strtol, for the octal escapes in /proc/self/mountinfo
#include <string.h>
#include <strings.h>
#include <algorithm>
#include <utils/Log.h>
#include <cutils/properties.h>   // property_set for the DraStic data-folder override (#90)

namespace android {

// Slugify a display name into a stable lowercase id ([a-z0-9_]).
static std::string slugify(const std::string& name) {
    std::string s;
    for (char c : name) {
        if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) s += c;
        else if (c >= 'A' && c <= 'Z') s += (char)(c - 'A' + 'a');
        else if (!s.empty() && s.back() != '_') s += '_';
    }
    while (!s.empty() && s.back() == '_') s.pop_back();
    if (s.empty()) s = "system";
    return s;
}

// A short label from a platform/display name: prefer the part after the last
// " - " (the actual system), else the whole thing, capped to ~8 chars.
static std::string shortFromName(const std::string& name) {
    std::string base = name;
    size_t d = base.rfind(" - ");
    if (d != std::string::npos) base = base.substr(d + 3);
    if (base.size() > 10) base = base.substr(0, 10);
    return base;
}

// ---- Add / remove custom systems ----

void NanoMenu::gsAddSystem() {
    // Open the emulator picker in "add" mode: picking a Daijishou platform/player
    // (or Custom) creates a brand-new system. mGsEditIdx is irrelevant here.
    mGsAddMode = true;
    mGsEditIdx = -1;
    mEmuPickFilter.clear();
    loadEmuCatalog();

    std::vector<Ps3Item> parentSnap = mPs3Stack.empty() ? std::vector<Ps3Item>() : mPs3Stack.back().items;
    int parentSel = mPs3Stack.empty() ? 0 : mPs3Stack.back().sel;
    Ps3Level lvl;
    buildEmulatorPicker(lvl);
    mPs3Stack.push_back(lvl);
    // Collapse-rail slide-in, matching every other submenu push.
    mPs3SubParentItems = std::move(parentSnap);
    mPs3SubParentIdx   = parentSel;
    mPs3SubChildItems  = mPs3Stack.back().items;
    mPs3SubDir         = 1;
    mPs3SubAnimStart   = mEffectTime;
    mPs3SubAnim        = 0.0f;
    mPs3AnimItem       = 0.0f;
    mPs3ItemAnimStart  = -1.0f;
}

// Create a fresh system, slug deduped against existing ids. preferredId (the
// Daijishou platform uniqueId, e.g. "ps2") becomes the rom-folder id when given,
// so the default scan paths land on ROMs/<platformId>. Returns its index.
int nano_makeUniqueSystem(std::vector<NanoMenu::XmbSystem>& systems, const std::string& name,
                          const std::string& preferredId) {
    NanoMenu::XmbSystem s;
    s.builtin = false; s.enabled = true;
    s.name = name.empty() ? "New System" : name;
    s.shortname = shortFromName(s.name);
    std::string id = preferredId.empty() ? slugify(s.name) : slugify(preferredId);
    std::string uid = id; int k = 2;
    auto exists = [&](const std::string& q) {
        for (auto& x : systems) if (x.id == q) return true; return false;
    };
    while (exists(uid)) { uid = id + "_" + std::to_string(k++); }
    s.id = uid; s.romDir = uid;
    int maxOrd = -1; for (auto& x : systems) if (x.order > maxOrd) maxOrd = x.order;
    s.order = maxOrd + 1;
    s.launchType = NanoMenu::XLT_LIBRETRO_CORE;
    s.iconR = s.iconG = s.iconB = 1.0f;
    systems.push_back(std::move(s));
    return (int)systems.size() - 1;
}

// Create a blank custom system and open its editor (the "Custom..." path of the
// add flow). The emulator picker was already popped by the dispatch.
void NanoMenu::gsAddBlankSystem() {
    int idx = nano_makeUniqueSystem(mXmbSystems, "New System", "");
    mGsEditIdx = idx;
    mGsAddMode = false;
    saveSystemsConfig();
    gsRefreshStackLevels();
    Ps3Level lvl; buildGameSystemEditor(idx, lvl); mPs3Stack.push_back(lvl);
    buildPs3Cats();
}

void NanoMenu::gsRemoveSystem(int sysIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    XmbSystem& s = mXmbSystems[sysIdx];
    if (s.builtin) return;   // built-ins can only be disabled, never removed
    ALOGI("ps3menu: removing custom system %s", s.id.c_str());
    unlink(xmbCachePath(s).c_str());
    std::string userIcon = "/data/system/nano_user_icons/" + s.id + ".png";
    unlink(userIcon.c_str());
    mXmbSystems.erase(mXmbSystems.begin() + sysIdx);
    for (size_t k = 0; k < mXmbSystems.size(); k++) mXmbSystems[k].order = (int)k;
    mGsEditIdx = -1;
    // If we were removing from this system's editor, pop back to the list.
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_EDITOR) mPs3Stack.pop_back();
    saveSystemsConfig();
    gsRefreshStackLevels();
    buildPs3Cats();
}

void NanoMenu::gsOpenRemoveConfirm(int sysIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    if (mXmbSystems[sysIdx].builtin) return;   // no remove for built-ins
    mGsEditIdx = sysIdx;   // case 23 reads mGsEditIdx
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 23; mPs3DlgTitle = "Remove System"; mPs3DlgBody.clear();
    mPs3DlgOptions.push_back("Cancel");          mPs3DlgSwatch.push_back(-1);
    mPs3DlgOptions.push_back("Remove System");   mPs3DlgSwatch.push_back(-1);
    mPs3DlgSel = 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgOrigSel = 0;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

// Confirm before dropping a scan-source folder (Cancel / Remove Folder), so removing an
// accidentally-added ROM source is a clear, deliberate action instead of a silent one-press
// delete. The folder path is shown in the body. case 45 removes on commit.
void NanoMenu::gsOpenRemoveScanSourceConfirm(int srcIdx) {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    const XmbSystem& s = mXmbSystems[mGsEditIdx];
    if (srcIdx < 0 || srcIdx >= (int)s.scanSources.size()) return;
    mGsRemoveSrcIdx = srcIdx;   // case 45 reads this
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 45; mPs3DlgTitle = "Remove Folder";
    mPs3DlgBody = s.scanSources[srcIdx].value;
    mPs3DlgOptions.push_back("Cancel");         mPs3DlgSwatch.push_back(-1);
    mPs3DlgOptions.push_back("Remove Folder");  mPs3DlgSwatch.push_back(-1);
    mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

// ---- Native folder picker ----

void NanoMenu::buildScanFoldersScreen(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Scan Folders"; out.screenKind = GS_FOLDER;
    { Ps3Item it; it.label = "Add Folder..."; it.kind = PS3_GS_ADDFOLDER;
      it.iconTex = iconTexForIcon(50); it.nmapTex = nmapForIcon(50);   // folder+ glyph
      it.iconR = it.iconG = it.iconB = 1.0f; out.items.push_back(it); }
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    const XmbSystem& sys = mXmbSystems[mGsEditIdx];

    // User-added scan folders (Y removes). When empty, the system uses the
    // default ROMs/<romDir> candidates across internal + every SD/USB mount.
    for (size_t i = 0; i < sys.scanSources.size(); i++) {
        Ps3Item it; it.label = sys.scanSources[i].value;
        it.kind = PS3_GS_SCANSRC; it.a = (int)i;
        it.iconTex = iconTexForIcon(62); it.nmapTex = nmapForIcon(62);   // folder glyph
        it.iconR = it.iconG = it.iconB = 1.0f; out.items.push_back(it);
    }

    // Normalise a path to one stable identity for de-duping AND display. /storage/emulated/0,
    // /data/media/0 and /sdcard are three separate mount VIEWS of the same internal storage
    // (realpath does NOT collapse the two FUSE mounts), so fold them all to the familiar
    // /storage/emulated/0 form users recognise; realpath first resolves the /sdcard symlink and
    // any case variants of an existing dir.
    auto norm = [](const std::string& p) -> std::string {
        char rp[4096]; std::string q = realpath(p.c_str(), rp) ? std::string(rp) : p;
        if (q.rfind("/data/media/0/", 0) == 0)      q = "/storage/emulated/0/" + q.substr(14);
        else if (q.rfind("/sdcard/", 0) == 0)       q = "/storage/emulated/0/" + q.substr(8);
        else if (q == "/data/media/0")              q = "/storage/emulated/0";
        return q;
    };

    // Read-only: the DEFAULT folders (built-in ROMs/<romDir> candidates across internal +
    // SD/USB) that are ALREADY being scanned, so the user can see which default locations
    // are in use and where to drop ROMs - even before any are found. Each is marked
    // "Default" (with the game count when populated), inert (not removable; the user removes
    // the folder itself or adds their own above). De-duped by normalised path against the
    // user-added folders and each other. (Requested: show which default folders are in use.)
    std::vector<std::string> shownNorm;
    for (const auto& src : sys.scanSources) shownNorm.push_back(norm(src.value));
    // Count ROMs found under each active scan path, keyed by normalised dir.
    std::vector<std::pair<std::string, int>> activeCount;   // (normalised activePath, count)
    for (const auto& ap : sys.activePaths) {
        int cnt = 0;
        for (const auto& r : sys.roms) {
            size_t sl = r.rfind('/');
            if (sl != std::string::npos && r.compare(0, sl, ap) == 0) cnt++;
        }
        activeCount.push_back({norm(ap), cnt});
    }
    int defaultShown = 0;
    std::vector<std::string> cands = buildScanCandidates(sys);
    for (const auto& cp : cands) {
        struct stat st;
        if (stat(cp.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) continue;   // only real, existing dirs
        std::string c = norm(cp);
        bool dup = false;
        for (const auto& s : shownNorm) if (s == c) { dup = true; break; }
        if (dup) continue;
        shownNorm.push_back(c);
        int cnt = 0;
        for (const auto& ac : activeCount) if (ac.first == c) { cnt = ac.second; break; }
        Ps3Item it; it.label = c; it.kind = PS3_GS_DEFFOLDER; it.a = -1;   // default folder; Y removes it
        // Alias = last path component (ROMs/<alias> or /<alias>), lowercased -- the key stored in
        // disabledDefaultFolders so removing here drops that alias from every mount it maps to.
        { size_t sl = c.rfind('/'); std::string al = (sl == std::string::npos) ? c : c.substr(sl + 1);
          for (char& ch : al) ch = (char)((ch >= 'A' && ch <= 'Z') ? ch + 32 : ch); it.payloadStr = al; }
        char v[40];
        if (cnt > 0) snprintf(v, sizeof(v), "%s%d", trDyn("Default, "), cnt);   // "Default, 12"
        else         snprintf(v, sizeof(v), "%s", trDyn("Default (empty)"));
        it.value = v;
        it.iconTex = iconTexForIcon(62); it.nmapTex = nmapForIcon(62);
        float m = cnt > 0 ? 0.72f : 0.5f;   // brighter when populated, dimmer when empty
        it.iconR = it.iconG = it.iconB = m;
        out.items.push_back(it);
        defaultShown++;
    }

    // Removed default folders (Y restores). Listed straight from disabledDefaultFolders because a
    // removed alias is no longer in buildScanCandidates above, so it would otherwise vanish with no
    // way to bring it back.
    for (const auto& alias : sys.disabledDefaultFolders) {
        Ps3Item it; it.label = std::string("ROMs/") + alias;
        it.kind = PS3_GS_DEFFOLDER_OFF; it.a = -1; it.payloadStr = alias;
        it.value = trDyn("Removed");
        it.iconTex = iconTexForIcon(62); it.nmapTex = nmapForIcon(62);
        it.iconR = it.iconG = it.iconB = 0.42f;   // dim: not scanned
        out.items.push_back(it);
    }

    // Nothing at all to scan (no user folders, and not even a default dir exists yet):
    // point the user at the default location so they know where to create it.
    if (sys.scanSources.empty() && defaultShown == 0) {
        Ps3Item it; it.label = std::string(trDyn("No ROMs found in ROMs/")) + sys.romDir;
        it.kind = PS3_GS_FIELD; it.a = -1;
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }
}

void NanoMenu::gsOpenScanFolders() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    mFolderPickTarget = 0;   // route the folder browser's "Select" to the Game system

    std::vector<Ps3Item> parentSnap = mPs3Stack.empty() ? std::vector<Ps3Item>() : mPs3Stack.back().items;
    int parentSel = mPs3Stack.empty() ? 0 : mPs3Stack.back().sel;
    Ps3Level lvl; buildScanFoldersScreen(lvl);
    mPs3Stack.push_back(lvl);
    mPs3SubParentItems = std::move(parentSnap);
    mPs3SubParentIdx = parentSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

void NanoMenu::gsRemoveScanSource(int srcIdx) {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    XmbSystem& s = mXmbSystems[mGsEditIdx];
    if (srcIdx < 0 || srcIdx >= (int)s.scanSources.size()) return;
    s.scanSources.erase(s.scanSources.begin() + srcIdx);
    unlink(xmbCachePath(s).c_str());
    s.scanned = false;
    if (!mBgScanThreadRunning) forceRescanAllSystems();
    saveSystemsConfig();
    gsRefreshStackLevels();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDER)
        buildScanFoldersScreen(mPs3Stack.back());
    buildPs3Cats();
}

// Re-derive + persist after a scan-folder change, then rebuild the open screen. Shared by the
// default-folder remove/restore below (mirrors gsRemoveScanSource's refresh tail).
void NanoMenu::gsAfterScanFolderChange(XmbSystem& s) {
    unlink(xmbCachePath(s).c_str());
    s.scanned = false;
    if (!mBgScanThreadRunning) forceRescanAllSystems();
    saveSystemsConfig();
    gsRefreshStackLevels();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDER)
        buildScanFoldersScreen(mPs3Stack.back());
    buildPs3Cats();
}

// Remove a default (built-in alias) scan folder from the edited system so it is no longer scanned.
void NanoMenu::gsDisableDefaultFolder(const std::string& alias) {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size() || alias.empty()) return;
    XmbSystem& s = mXmbSystems[mGsEditIdx];
    std::string la; for (char c : alias) la += (char)((c >= 'A' && c <= 'Z') ? c + 32 : c);
    for (const auto& d : s.disabledDefaultFolders) if (d == la) return;   // already removed
    s.disabledDefaultFolders.push_back(la);
    gsAfterScanFolderChange(s);
}

// Restore a previously-removed default scan folder.
void NanoMenu::gsEnableDefaultFolder(const std::string& alias) {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size() || alias.empty()) return;
    XmbSystem& s = mXmbSystems[mGsEditIdx];
    std::string la; for (char c : alias) la += (char)((c >= 'A' && c <= 'Z') ? c + 32 : c);
    for (size_t i = 0; i < s.disabledDefaultFolders.size(); i++) {
        if (s.disabledDefaultFolders[i] == la) { s.disabledDefaultFolders.erase(s.disabledDefaultFolders.begin() + i); break; }
    }
    gsAfterScanFolderChange(s);
}

// Names of the network shares that are actually mounted right now.
//
// This reads the kernel's mount table rather than looking in /mnt/shares, for two reasons: the
// share daemon creates its mount point before it connects, so an empty directory there does not
// mean a usable share, and any stat of a mount point whose server has gone away can block until
// FUSE times out. Reading /proc/self/mountinfo never touches the network.
std::vector<std::string> NanoMenu::mountedShareNames() const {
    std::vector<std::string> out;
    FILE* f = fopen("/proc/self/mountinfo", "re");
    if (!f) return out;
    char line[1024];
    while (fgets(line, sizeof(line), f)) {
        // Field 5 is the mount point; it is the field after the "major:minor root" pair.
        int n = 0;
        const char* p = line;
        const char* mp = nullptr;
        while (*p) {
            if (n == 4) { mp = p; break; }
            while (*p && *p != ' ') p++;
            while (*p == ' ') p++;
            n++;
        }
        if (!mp) continue;
        const char* end = strchr(mp, ' ');
        if (!end) continue;
        std::string path(mp, end - mp);
        if (path.compare(0, 12, "/mnt/shares/") != 0) continue;
        std::string name = path.substr(12);
        // Only the share's own mount point, not anything nested below it.
        if (name.empty() || name.find('/') != std::string::npos) continue;
        // mountinfo escapes spaces and friends as octal; undo that so the name matches the
        // directory and the label reads properly.
        std::string unesc;
        for (size_t i = 0; i < name.size(); i++) {
            if (name[i] == '\\' && i + 3 < name.size()) {
                unesc += (char)strtol(name.substr(i + 1, 3).c_str(), nullptr, 8);
                i += 3;
            } else {
                unesc += name[i];
            }
        }
        out.push_back(unesc);
    }
    fclose(f);
    std::sort(out.begin(), out.end(), [](const std::string& a, const std::string& b) {
        return strcasecmp(a.c_str(), b.c_str()) < 0;
    });
    out.erase(std::unique(out.begin(), out.end()), out.end());
    return out;
}

// Is this path one of the storage roots (its parent should be the roots list)?
static bool isStorageRoot(const std::string& p) {
    if (p == "/storage/emulated/0") return true;
    if (p.compare(0, 9, "/storage/") == 0 && p.find('/', 9) == std::string::npos) return true;
    if (p.compare(0, 14, "/mnt/media_rw/") == 0 && p.find('/', 14) == std::string::npos) return true;
    // A mounted network share is a root of its own: there is nothing above it to browse.
    if (p.compare(0, 12, "/mnt/shares/") == 0 && p.find('/', 12) == std::string::npos) return true;
    return false;
}

void NanoMenu::buildFolderBrowser(const std::string& path, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = GS_FOLDERBROWSE;
    mGsFolderPath = path;
    GLuint folderNmap = nmapForIcon(62);
    auto addDir = [&](const std::string& label, const std::string& target) {
        Ps3Item it; it.label = label; it.kind = PS3_GS_DIR; it.payloadStr = target;
        it.iconTex = iconTexForIcon(62); it.nmapTex = folderNmap; it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    };
    if (path.empty()) {
        // Storage roots.
        out.title = "Storage";
        // #90 DraStic data-folder picker (target 5): offer a reset-to-default at the top of the roots,
        // since the roots pseudo-folder has no "Select This Folder". Selecting it clears the override.
        if (mFolderPickTarget == 5) {
            Ps3Item it; it.label = "Use Default Folder"; it.kind = PS3_GS_SELFOLDER; it.payloadStr = "@default";
            it.iconTex = iconTexForIcon(22); it.nmapTex = nmapForIcon(22); it.iconR = it.iconG = it.iconB = 1.0f;
            out.items.push_back(it);
        }
        addDir("Internal storage", "/storage/emulated/0");
        // Network shares are bind-mounted into /storage so apps can open them by path, which means
        // they turn up in this scan too and would otherwise be listed twice: once here as "SD: name"
        // and again below as "Share: name". Skip them here and let the share pass label them, since
        // calling a NAS an SD card is worse than the duplication.
        const std::vector<std::string> shareNames = mountedShareNames();
        auto isShare = [&](const char* n) {
            return std::find(shareNames.begin(), shareNames.end(), std::string(n)) != shareNames.end();
        };
        DIR* d = opendir("/storage");
        if (d) { struct dirent* e; while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            if (!strcmp(e->d_name, "emulated") || !strcmp(e->d_name, "self")) continue;
            if (isShare(e->d_name)) continue;
            addDir(std::string(trDyn("SD: ")) + e->d_name, std::string("/storage/") + e->d_name);
        } closedir(d); }
        d = opendir("/mnt/media_rw");
        if (d) { struct dirent* e; while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            addDir(std::string(trDyn("Removable: ")) + e->d_name, std::string("/mnt/media_rw/") + e->d_name);
        } closedir(d); }
        // Mounted network shares, so a media folder can live on a NAS. Listing them here is what
        // puts them behind "Search for Media Servers" in Photos/Music/Video as well, since all of
        // those open this same browser.
        for (const std::string& s : shareNames)
            addDir(std::string(trDyn("Share: ")) + s, std::string("/mnt/shares/") + s);
        return;
    }
    out.title = path;
    // Parent ("..") - a storage root goes back to the roots list ("").
    if (isStorageRoot(path)) addDir("..", "");
    else { size_t sl = path.rfind('/'); addDir("..", sl == std::string::npos ? "" : path.substr(0, sl)); }

    // Subdirectories, from the worker rather than from a blocking opendir+stat here.
    //
    // This is the screen a user picks a media folder with, so it is routinely pointed at a network
    // share. Listing one inline cost a round trip per entry with the render thread held throughout,
    // which is what let the watchdog abort nano when a folder on an FTP share was opened. The
    // listing is requested once and cached; until it arrives the screen shows "Loading..." and
    // stays fully responsive, so Back still works if the server never answers.
    // Icon-import picker (target 6) also lists selectable image files, so the user can browse to
    // their own PNG. Every other picker lists directories only.
    const bool iconPick = (mFolderPickTarget == 6);
    auto isImageName = [](const std::string& n) {
        size_t dot = n.rfind('.');
        if (dot == std::string::npos) return false;
        std::string ext = n.substr(dot + 1);
        return strcasecmp(ext.c_str(), "png") == 0 || strcasecmp(ext.c_str(), "jpg") == 0 ||
               strcasecmp(ext.c_str(), "jpeg") == 0;
    };
    GLuint pngNmap = nmapForIcon(25);   // document/page glyph for a file row
    if (mFbCacheValid && mFbCachePath == path) {
        for (const auto& e : mFbCacheEntries)
            if (e.isDir) addDir(e.name, path + "/" + e.name);
        if (iconPick) {
            for (const auto& e : mFbCacheEntries) {
                if (e.isDir || !isImageName(e.name)) continue;
                Ps3Item it; it.label = e.name; it.kind = PS3_GS_PICKFILE; it.payloadStr = path + "/" + e.name;
                it.iconTex = iconTexForIcon(25); it.nmapTex = pngNmap; it.iconR = it.iconG = it.iconB = 1.0f;
                out.items.push_back(it);
            }
        }
    } else {
        fbRequestListing(path);
        Ps3Item it; it.kind = PS3_DATA_LEAF; it.label = trDyn("Loading...");
        it.desc = "Reading the folder.";
        it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 0.55f;
        out.items.push_back(it);
    }
    // "Select this folder" - only when picking a folder. The icon-import picker selects a file
    // instead, so it has no whole-folder action.
    if (!iconPick) {
        Ps3Item it; it.label = "Select This Folder"; it.kind = PS3_GS_SELFOLDER; it.payloadStr = path;
        it.iconTex = iconTexForIcon(22); it.nmapTex = nmapForIcon(22); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

// Queue a directory listing for the folder browser. Render thread; never blocks.
void NanoMenu::fbRequestListing(const std::string& path) {
    if (!mFbStarted) {
        mFbStarted = true;
        mFbThread = std::thread([this]() {
            for (;;) {
                std::string p;
                {
                    std::unique_lock<std::mutex> lk(mFbLock);
                    mFbCv.wait(lk, [this]{
                        return mFbQuit.load(std::memory_order_relaxed) || !mFbQueue.empty();
                    });
                    if (mFbQuit.load(std::memory_order_relaxed)) return;
                    p = mFbQueue.front();
                    mFbQueue.pop_front();
                }
                FbResult r;
                r.path = p;
                if (DIR* d = opendir(p.c_str())) {
                    while (struct dirent* e = readdir(d)) {
                        if (e->d_name[0] == '.') continue;
                        const std::string child = p + "/" + e->d_name;
                        struct stat st;
                        if (stat(child.c_str(), &st) != 0) continue;
                        FbEntry fe;
                        fe.name = e->d_name;
                        fe.isDir = S_ISDIR(st.st_mode);
                        fe.size = (long long)st.st_size;
                        // Directories and regular files only: a socket or a device node in a share
                        // is not something either browser can do anything with.
                        if (fe.isDir || S_ISREG(st.st_mode)) r.entries.push_back(std::move(fe));
                    }
                    closedir(d);
                    r.ok = true;
                }
                // Directories first, then files, each case-insensitively by name - the order both
                // browsers present.
                std::sort(r.entries.begin(), r.entries.end(),
                          [](const FbEntry& a, const FbEntry& b) {
                              if (a.isDir != b.isDir) return a.isDir;
                              return strcasecmp(a.name.c_str(), b.name.c_str()) < 0;
                          });
                {
                    std::lock_guard<std::mutex> lk(mFbLock);
                    mFbDone.push_back(std::move(r));
                }
            }
        });
    }
    {
        std::lock_guard<std::mutex> lk(mFbLock);
        if (!mFbPending.insert(path).second) return;   // already queued or in flight
        mFbQueue.push_back(path);
    }
    mFbCv.notify_one();
}

// Render thread: adopt a finished listing and rebuild the screen showing it.
void NanoMenu::fbTick() {
    std::vector<FbResult> done;
    {
        std::lock_guard<std::mutex> lk(mFbLock);
        if (mFbDone.empty()) return;
        done.swap(mFbDone);
        for (const auto& r : done) mFbPending.erase(r.path);
    }
    for (auto& r : done) {
        // Cache even a failed listing, so an unreachable folder shows as empty instead of
        // re-requesting forever; leaving the screen and coming back retries it.
        mFbCachePath = r.path;
        mFbCacheEntries = std::move(r.entries);
        mFbCacheValid = true;
        // Only the screen currently showing this path needs rebuilding.
        const int kind = mPs3Stack.empty() ? -1 : mPs3Stack.back().screenKind;
        const bool isFolder = (kind == GS_FOLDERBROWSE && mGsFolderPath == r.path);
        const bool isFile   = (kind == FE_BROWSE      && mFeBrowsePath  == r.path);
        if (isFolder || isFile) {
            int keep = mPs3Stack.back().sel;
            if (isFolder) buildFolderBrowser(r.path, mPs3Stack.back());
            else          buildFileBrowser(r.path, mPs3Stack.back());
            int n = (int)mPs3Stack.back().items.size();
            if (keep >= n) keep = n - 1;
            mPs3Stack.back().sel = keep < 0 ? 0 : keep;
            mDisplayDirty = true;
        }
    }
}

void NanoMenu::fbStopWorker() {
    if (!mFbStarted) return;
    mFbQuit.store(true, std::memory_order_relaxed);
    mFbCv.notify_all();
    if (mFbThread.joinable()) mFbThread.join();
    mFbStarted = false;
}

void NanoMenu::gsFolderSelect(const std::string& path) {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size() || path.empty()) return;
    XmbSystem& s = mXmbSystems[mGsEditIdx];
    // Skip duplicates.
    for (const auto& src : s.scanSources) if (src.value == path) {
        if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
        return;
    }
    ScanSource src; src.type = 0; src.value = path; s.scanSources.push_back(src);
    ALOGI("ps3menu: added scan folder %s to %s", path.c_str(), s.id.c_str());
    unlink(xmbCachePath(s).c_str());
    s.scanned = false;
    if (!mBgScanThreadRunning) forceRescanAllSystems();
    saveSystemsConfig();
    // Pop the browser, refresh the scan-folders screen + the Game category.
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDER)
        buildScanFoldersScreen(mPs3Stack.back());
    gsRefreshStackLevels();
    buildPs3Cats();
}

// #90: point the native drastic-nano DS core at the DraStic data/saves/BIOS folder. Writes
// persist.gammaos.drastic.data_dir, which drastic-nano reads at each DS launch (main.cpp). Empty /
// "@default" (the "Use Default Folder" row) clears it back to the installed app's own files dir.
// This lets a user who moved DraStic to scoped / SD storage keep DS games launching via the core
// that is immune to DraStic's scoped-storage ROM-open failure. Pops the browser back to Game Settings.
void NanoMenu::drasticDataFolderSelect(const std::string& path) {
    if (path.empty() || path == "@default") {
        property_set("persist.gammaos.drastic.data_dir", "");
        photoShowBanner(trDyn("DraStic data folder: Default"));
        ALOGI("ps3menu: DraStic data folder reset to default");
    } else {
        property_set("persist.gammaos.drastic.data_dir", path.c_str());
        photoShowBanner(trDyn("DraStic data folder set"));
        ALOGI("ps3menu: DraStic data folder -> %s", path.c_str());
    }
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
    mDisplayDirty = true;
}

} // namespace android
