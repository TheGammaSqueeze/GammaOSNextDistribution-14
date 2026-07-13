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
 */

// GammaOS "System Update" (OTA) front-end for the PS3 XMB and Nintendo DSi themes.
//
// nano owns the online update check + download and draws it with the EXISTING themed
// dialog chrome (renderPs3Dialog for XMB, renderNdsDialog for DSi) by opening a dynamic
// dialog and reconfiguring it in place (the same pattern the uninstall / clear-data flows
// use). When the user confirms, nano hands off to the gammaos-ota native flasher, which
// stops the framework and writes the partitions and draws its OWN themed flashing screens
// (it reads sys.gammaos.ota.theme, set here).
//
// Handoff / display ownership: nano renders the home directly on the DRM/KMS panel (DRM
// master), while gammaos-ota renders a SurfaceFlinger EGL surface. The two cannot own the
// panel at once, so nano must exit (releasing DRM master via process teardown) before the
// flasher's surface is visible. The handoff therefore sets the OTA props +
// sys.gammaos.nano.start_ota=1 then _exit(0); an init rule in gammaos-nano.rc starts
// gammaos-ota once init.svc.gammaos-nano=stopped (the same handshake the app-launch overlay
// uses), guaranteeing DRM is released first. We do NOT use the app-launch path
// (launch_app / mExitRequested), which shows a "Loading..." screen and blocks up to 10s
// waiting for sys.gammaos.nano.app_launched (never set for a native service).
//
// Threading: the check/download run on a detached worker; the render loop and input run on
// the same NanoMenu thread (otaFlowTick from render(), otaDialogAccept from ps3XmbSelect).
// All worker<->UI state is guarded by mOtaMutex, and a generation counter (mOtaGen, bumped
// under the lock on every spawn/cancel) ensures a stale worker from a cancelled-then-
// re-entered flow publishes nothing (mOtaProgress is advisory only).

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoOtaCheck.h"
#include "NanoJson.h"
#include "NanoMenuDrm.h"

#include <thread>
#include <vector>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#include <algorithm>
#include <set>

#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {

namespace {

// mOtaStage values.
enum {
    OTA_CHECKING = 0,   // querying the server (info dialog, no buttons)
    OTA_UPTODATE,       // result: already current (info dialog, OK)
    OTA_AVAIL,          // result: an update exists (confirm dialog, Cancel/Install)
    OTA_ERROR,          // result: check/download failed (info dialog, OK)
    OTA_DOWNLOADING,    // downloading the package (info dialog, live %)
    OTA_BROWSE,         // storage media: pick a package (chooser dialog of *.zip)
    OTA_BROWSE_PEEK,    // reading the selected package's manifest (info dialog)
    OTA_BROWSE_CONFIRM, // confirm install of the selected package (Cancel/Install)
    OTA_PREPARING,      // flasher is pre-staging in the background; nano shows "Preparing... %"
};

std::string humanSize(uint64_t bytes) {
    char buf[32];
    double b = static_cast<double>(bytes);
    if (b >= 1024.0 * 1024.0 * 1024.0)
        snprintf(buf, sizeof(buf), "%.2f GB", b / (1024.0 * 1024.0 * 1024.0));
    else if (b >= 1024.0 * 1024.0)
        snprintf(buf, sizeof(buf), "%.1f MB", b / (1024.0 * 1024.0));
    else if (b >= 1024.0)
        snprintf(buf, sizeof(buf), "%.0f KB", b / 1024.0);
    else
        snprintf(buf, sizeof(buf), "%llu B", static_cast<unsigned long long>(bytes));
    return buf;
}

} // namespace

// Open / reconfigure the shared PS3 dialog (mPs3Dlg*) as a plain dynamic dialog so it
// renders through the existing themed chrome. type: 0 = info (single OK), 3 = confirm
// (Cancel/Install). An in-place reconfigure (dialog already up) keeps the open animation.
void NanoMenu::otaSetDialog(const char* title, const std::string& body,
                            const std::vector<std::string>& opts, int type, int defSel) {
    const bool wasActive = mPs3DlgActive;
    mPs3DlgOptions.clear();
    mPs3DlgSwatch.clear();
    mPs3DlgKind = 0;
    mPs3DlgType = type;
    mPs3DlgThemeKey = 0;
    mPs3DlgBinding = nullptr;
    mPs3DlgTitle = title;
    mPs3DlgBody = body;
    for (const std::string& o : opts) {
        mPs3DlgOptions.push_back(o);
        mPs3DlgSwatch.push_back(-1);
    }
    mPs3DlgSel = defSel;
    mPs3DlgOrigSel = defSel;
    mPs3DlgActive = true;
    mPs3DlgBlurValid = false;
    if (!wasActive) mPs3DlgAnim = 0.0f;   // fresh open animates; in-place reconfigure stays up
    mDisplayDirty = true;
}

// True while the storage-media package browser (the *.zip chooser list) is on screen. The DSi
// theme uses this to render it as the vertical glossy LIST (renderNdsSidePanel) instead of a modal
// with horizontal buttons, which mangles a single long "systest.zip (1.67 GB)" label.
bool NanoMenu::otaInBrowse() const {
    return mOtaFlowActive && mOtaStage == OTA_BROWSE;
}

// True on a non-interactive OTA PROGRESS screen (auto-advancing: querying the server,
// downloading the package, reading the selected package, or pre-staging/preparing). These
// screens must NOT draw a confirm/OK button (DSi) or an "OK" footer hint (XMB) - there is
// nothing for the user to act on and a stray press is already swallowed by otaDialogAccept.
// The RESULT/info dialogs (OTA_UPTODATE / OTA_ERROR) and the confirm dialogs (OTA_AVAIL /
// OTA_BROWSE_CONFIRM) are NOT in this set and keep their buttons; likewise non-OTA 0-option
// info dialogs (System Information, network status, App/ROM Info) are unaffected since the
// OTA flow is not active for them.
bool NanoMenu::otaInProgress() const {
    return mOtaFlowActive &&
           (mOtaStage == OTA_CHECKING || mOtaStage == OTA_DOWNLOADING ||
            mOtaStage == OTA_BROWSE_PEEK || mOtaStage == OTA_PREPARING);
}

// End the flow and dismiss the dialog. Bumps mOtaGen so any in-flight worker abandons.
void NanoMenu::otaEndFlow() {
    {
        std::lock_guard<std::mutex> lk(mOtaMutex);
        ++mOtaGen;
        mOtaWorkerDone = false;
    }
    mOtaFlowActive = false;
    mPs3DlgActive = false;
    mPs3DlgBlurValid = false;
    mDisplayDirty = true;
}

// ---- Storage-media file browser (nano owns the panel; hands off only at Install) ----------

// Scan the usual drop locations for GammaOS update *.zip packages. Worker-thread safe: fills
// only `out` (no member access besides the file-local humanSize). Deduped by name+size, newest
// first (so a just-copied package is at the top).
void NanoMenu::scanOtaZips(std::vector<OtaFileEntry>& out) const {
    out.clear();
    struct Cand { std::string path, label; uint64_t size; long mtime; };
    std::vector<Cand> cands;
    std::set<std::string> seen;
    const char* dirs[] = { "/sdcard", "/sdcard/Download", "/data/gammaos_ota" };
    for (const char* d : dirs) {
        DIR* dir = opendir(d);
        if (!dir) continue;
        struct dirent* e;
        while ((e = readdir(dir)) != nullptr) {
            std::string name = e->d_name;
            if (name.size() <= 4 || name.substr(name.size() - 4) != ".zip") continue;
            std::string full = std::string(d) + "/" + name;
            struct stat st;
            if (stat(full.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) continue;
            std::string key = name + "|" + std::to_string((long long)st.st_size);
            if (!seen.insert(key).second) continue;   // same package via two mounts -> once
            Cand c;
            c.path = full; c.size = (uint64_t)st.st_size; c.mtime = (long)st.st_mtime;
            c.label = name;
            if (c.size > 0) c.label += "  (" + humanSize(c.size) + ")";
            cands.push_back(std::move(c));
        }
        closedir(dir);
    }
    std::sort(cands.begin(), cands.end(),
              [](const Cand& a, const Cand& b) { return a.mtime > b.mtime; });
    for (auto& c : cands) out.push_back(OtaFileEntry{ c.path, c.label, c.size });
}

// Read ONLY manifest.json out of the zip (no full extract) and pull out the version + a
// partition summary for the confirm screen. Worker-thread safe. False if there is no version.
bool NanoMenu::peekOtaManifest(const std::string& zip, std::string& ver,
                               std::string& parts) const {
    ver.clear(); parts.clear();
    // Shell-escape the path in single quotes (defensive: the path is nano-built but stat'd).
    std::string esc;
    esc.reserve(zip.size() + 2);
    for (char ch : zip) { if (ch == '\'') esc += "'\\''"; else esc += ch; }
    std::string cmd = "/system/bin/unzip -p '" + esc + "' manifest.json 2>/dev/null";
    FILE* fp = popen(cmd.c_str(), "r");
    if (!fp) return false;
    std::string json;
    char buf[4096];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), fp)) > 0) {
        json.append(buf, n);
        if (json.size() > 65536) break;   // manifests are tiny; cap the read
    }
    pclose(fp);
    njson::Value root;
    if (!njson::parse(json, &root) || !root.isObject()) return false;
    ver = root.getString("version");
    if (ver.empty()) return false;
    const njson::Value* pv = root.find("partitions");
    if (pv && pv->isArray()) {
        for (const njson::Value& p : pv->arr) {
            std::string nm = p.getString("name");
            std::string ty = p.getString("type");
            const njson::Value* sz = p.find("size");
            uint64_t bytes = (sz && sz->isNumber()) ? (uint64_t)sz->asNumber() : 0;
            if (nm.empty()) continue;
            if (!parts.empty()) parts += "\n";
            parts += nm;
            if (!ty.empty()) parts += " (" + ty + ")";
            if (bytes > 0) parts += "  " + humanSize(bytes);
        }
    }
    return true;
}

// Storage-media entry: scan off-thread, show a "scanning" dialog; otaFlowTick shows the list.
void NanoMenu::otaBrowseInit() {
    mOtaFlowActive = true;
    mOtaStage = OTA_BROWSE;
    mOtaSelectedZip.clear();
    mOtaBrowseFiles.clear();
    uint32_t myGen;
    {
        std::lock_guard<std::mutex> lk(mOtaMutex);
        myGen = ++mOtaGen;
        mOtaWorkerDone = false;
    }
    otaSetDialog("System Update", "Looking for update packages on storage media...",
                 std::vector<std::string>(), /*info*/0, 0);
    std::thread([this, myGen]() {
        std::vector<OtaFileEntry> files;
        scanOtaZips(files);
        std::lock_guard<std::mutex> lk(mOtaMutex);
        if (myGen != mOtaGen) return;   // superseded / cancelled: publish nothing
        mOtaBrowseFiles = std::move(files);
        mOtaWorkerOk = true;
        mOtaWorkerDone = true;
    }).detach();
}

// (Re)show the discovered packages as a themed chooser dialog (reuses the scrolling list chrome).
void NanoMenu::otaBrowseShowList() {
    mOtaStage = OTA_BROWSE;
    std::vector<std::string> opts;
    opts.reserve(mOtaBrowseFiles.size());
    for (const OtaFileEntry& f : mOtaBrowseFiles) opts.push_back(f.label);
    otaSetDialog("System Update", "Select an update package.", opts, /*chooser*/1, 0);
}

// A on a package in the list: peek its manifest off-thread, then otaFlowTick shows the confirm.
void NanoMenu::otaBrowseSelect() {
    mOtaStage = OTA_BROWSE_PEEK;
    std::string zip = mOtaSelectedZip;
    uint32_t myGen;
    {
        std::lock_guard<std::mutex> lk(mOtaMutex);
        myGen = ++mOtaGen;
        mOtaWorkerDone = false;
        mOtaPeekVersion.clear();
        mOtaPeekParts.clear();
    }
    otaSetDialog("System Update", "Reading package information...",
                 std::vector<std::string>(), /*info*/0, 0);
    std::thread([this, myGen, zip]() {
        std::string ver, parts;
        bool ok = peekOtaManifest(zip, ver, parts);
        std::lock_guard<std::mutex> lk(mOtaMutex);
        if (myGen != mOtaGen) return;
        mOtaPeekVersion = ver;
        mOtaPeekParts = parts;
        mOtaWorkerOk = ok;
        mOtaWorkerDone = true;
    }).detach();
}

// Show the themed Install/Cancel confirm with the peeked version + partitions.
void NanoMenu::otaBrowseConfirm() {
    mOtaStage = OTA_BROWSE_CONFIRM;
    std::string ver, parts;
    {
        std::lock_guard<std::mutex> lk(mOtaMutex);
        ver = mOtaPeekVersion;
        parts = mOtaPeekParts;
    }
    // Keep the confirm body short: the DSi message box only fits ~4 lines before it marquee-scrolls
    // (which crowds the title). The "do not turn off the power" warning lives on the flasher's own
    // FLASHING screen, so it is not repeated here.
    std::string body = "Version " + ver + "\n";
    if (!parts.empty()) body += parts + "\n";
    body += "Install now? The system will restart.";
    otaSetDialog("System Update", body,
                 std::vector<std::string>{"Cancel", "Install"}, /*confirm*/3, 0);
}

// Entry from the "System Update" method chooser (option 0 = Internet, 1 = Storage Media).
void NanoMenu::startOtaFlow(bool internet) {
    if (!internet) {
        // Update via Storage Media: browse + confirm ENTIRELY inside nano (nano keeps the
        // DRM panel); we hand off to the flasher only when the user commits to Install. Drop
        // any stale online package so a later handoff can't auto-flash the wrong thing.
        system("/system/bin/rm -rf /data/gammaos_ota/package >/dev/null 2>&1");
        otaBrowseInit();
        return;
    }

    // Update via Internet: query the server off-thread and show a themed "checking" dialog.
    mOtaFlowActive = true;
    mOtaStage = OTA_CHECKING;
    mOtaProgress.store(0);
    uint32_t myGen;
    {
        std::lock_guard<std::mutex> lk(mOtaMutex);
        myGen = ++mOtaGen;
        mOtaWorkerDone = false;
        mOtaError.clear();
    }
    otaSetDialog("System Update", "Checking for the latest system software...",
                 std::vector<std::string>(), /*info*/0, 0);

    std::thread([this, myGen]() {
        nano::OtaUpdateInfo info;
        bool ok = nano::otaCheckForUpdate(info);
        std::lock_guard<std::mutex> lk(mOtaMutex);
        if (myGen != mOtaGen) return;   // superseded or cancelled: publish nothing
        mOtaAvail = info.available;
        mOtaVersion = info.version;
        mOtaUrl = info.url;
        mOtaFilename = info.filename;
        mOtaSize = info.size;
        mOtaCurrentVer = info.currentVersion;
        mOtaError = info.error;
        mOtaWorkerOk = ok;
        mOtaWorkerDone = true;
    }).detach();
}

// Per-frame pump: consume worker completion and keep the download percentage live.
// Called from render() while mOtaFlowActive. Same thread as ps3XmbSelect/otaDialogAccept.
void NanoMenu::otaFlowTick() {
    if (!mOtaFlowActive) return;
    // The user dismissed the dialog (B / Back closed it) -> end the flow (invalidates workers).
    if (!mPs3DlgActive) {
        otaEndFlow();
        return;
    }

    if (mOtaStage == OTA_CHECKING) {
        bool done = false, ok = false, avail = false;
        std::string ver, cur, err;
        uint64_t sz = 0;
        {
            std::lock_guard<std::mutex> lk(mOtaMutex);
            if (mOtaWorkerDone) {
                done = true;
                mOtaWorkerDone = false;
                ok = mOtaWorkerOk;
                avail = mOtaAvail;
                ver = mOtaVersion;
                cur = mOtaCurrentVer;
                err = mOtaError;
                sz = mOtaSize;
            }
        }
        if (!done) return;
        if (!ok) {
            mOtaStage = OTA_ERROR;
            std::string b = "Could not check for updates.";
            if (!err.empty()) b += "\n" + err;
            otaSetDialog("System Update", b, std::vector<std::string>{"OK"}, 0, 0);
        } else if (!avail) {
            mOtaStage = OTA_UPTODATE;
            std::string b = "The system software is up to date.";
            if (!cur.empty()) b += "\nVersion " + cur + ".";
            otaSetDialog("System Update", b, std::vector<std::string>{"OK"}, 0, 0);
        } else {
            mOtaStage = OTA_AVAIL;
            std::string b = "System software version " + ver;
            if (sz > 0) b += " (" + humanSize(sz) + ")";
            b += " is available.\nInstall now? The system will restart.\n"
                 "Do not turn off the power during the update.";
            otaSetDialog("System Update", b,
                         std::vector<std::string>{"Cancel", "Install"}, /*confirm*/3, 0);
        }
        return;
    }

    if (mOtaStage == OTA_DOWNLOADING) {
        const int pct = mOtaProgress.load();
        char body[128];
        snprintf(body, sizeof(body),
                 "Downloading update...  %d%%\nDo not turn off the power.", pct);
        mPs3DlgBody = body;
        mDisplayDirty = true;
        bool done = false, ok = false;
        std::string err;
        {
            std::lock_guard<std::mutex> lk(mOtaMutex);
            if (mOtaWorkerDone) {
                done = true;
                mOtaWorkerDone = false;
                ok = mOtaWorkerOk;
                err = mOtaError;
            }
        }
        if (!done) return;
        if (ok) {
            otaStartFlash("/data/gammaos_ota/package");   // pre-stage + "Preparing" screen
        } else {
            mOtaStage = OTA_ERROR;
            std::string b = "Download failed.";
            if (!err.empty()) b += "\n" + err;
            otaSetDialog("System Update", b, std::vector<std::string>{"OK"}, 0, 0);
        }
        return;
    }

    if (mOtaStage == OTA_BROWSE) {
        // Waiting on the storage-scan worker; the file list is already up (or "scanning...").
        bool done = false, ok = false;
        {
            std::lock_guard<std::mutex> lk(mOtaMutex);
            if (mOtaWorkerDone) { done = true; mOtaWorkerDone = false; ok = mOtaWorkerOk; }
        }
        if (!done) return;
        if (!ok || mOtaBrowseFiles.empty()) {
            mOtaStage = OTA_ERROR;
            otaSetDialog("System Update",
                         "No update packages were found.\n"
                         "Copy a GammaOS update .zip to the root of your storage and try again.",
                         std::vector<std::string>{"OK"}, 0, 0);
        } else {
            otaBrowseShowList();   // publish the discovered *.zip list as the chooser dialog
        }
        return;
    }

    if (mOtaStage == OTA_BROWSE_PEEK) {
        bool done = false, ok = false;
        std::string ver, parts;
        {
            std::lock_guard<std::mutex> lk(mOtaMutex);
            if (mOtaWorkerDone) {
                done = true; mOtaWorkerDone = false; ok = mOtaWorkerOk;
                ver = mOtaPeekVersion; parts = mOtaPeekParts;
            }
        }
        if (!done) return;
        if (ok) {
            otaBrowseConfirm();
        } else {
            mOtaStage = OTA_ERROR;
            otaSetDialog("System Update",
                         "Could not read the update package.\nThe file may be incomplete or invalid.",
                         std::vector<std::string>{"OK"}, 0, 0);
        }
        return;
    }

    if (mOtaStage == OTA_PREPARING) {
        // The flasher is staging in the background; keep the panel on "Preparing... X%" (real
        // progress) until it signals it is ready to take the panel, then release DRM + exit.
        if (property_get_bool("sys.gammaos.ota.staged", false)) {
            ALOGI("NanoMenu: OTA staged - releasing DRM + exiting to hand the panel to the flasher");
            drmStop();   // clean DROP_MASTER on the render thread (app-launch handoff pattern)
            _exit(0);
        }
        int pct = property_get_int32("sys.gammaos.ota.stageprog", 0);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        char body[96];
        snprintf(body, sizeof(body),
                 "Preparing update...  %d%%\nDo not turn off the power.", pct);
        mPs3DlgBody = body;
        mDisplayDirty = true;
        return;
    }
}

// Called from ps3XmbSelect() when mOtaFlowActive. Returns true if the accept was consumed.
bool NanoMenu::otaDialogAccept() {
    if (!mOtaFlowActive) return false;
    switch (mOtaStage) {
        case OTA_UPTODATE:
        case OTA_ERROR:
            otaEndFlow();
            return true;
        case OTA_AVAIL:
            if (mPs3DlgSel == 1) {   // "Install"
                mOtaStage = OTA_DOWNLOADING;
                mOtaProgress.store(0);
                uint32_t myGen;
                nano::OtaUpdateInfo info;
                {
                    std::lock_guard<std::mutex> lk(mOtaMutex);
                    myGen = ++mOtaGen;
                    mOtaWorkerDone = false;
                    info.available = true;
                    info.version = mOtaVersion;
                    info.url = mOtaUrl;
                    info.filename = mOtaFilename;
                    info.size = mOtaSize;
                }
                otaSetDialog("System Update",
                             "Downloading update...  0%\nDo not turn off the power.",
                             std::vector<std::string>(), /*info*/0, 0);
                std::thread([this, myGen, info]() {
                    bool ok = nano::otaDownloadAndStage(info,
                        [this](int p, const char* /*phase*/) { mOtaProgress.store(p); });
                    std::lock_guard<std::mutex> lk(mOtaMutex);
                    if (myGen != mOtaGen) return;   // superseded or cancelled
                    if (!ok && mOtaError.empty()) mOtaError = "Download failed";
                    mOtaWorkerOk = ok;
                    mOtaWorkerDone = true;
                }).detach();
            } else {   // "Cancel"
                otaEndFlow();
            }
            return true;
        case OTA_BROWSE:
            // A on a file in the list: peek that package's manifest (mPs3DlgSel = file index).
            if (mPs3DlgSel >= 0 && mPs3DlgSel < (int)mOtaBrowseFiles.size()) {
                mOtaSelectedZip = mOtaBrowseFiles[mPs3DlgSel].path;
                otaBrowseSelect();
            }
            return true;
        case OTA_BROWSE_CONFIRM:
            if (mPs3DlgSel == 1) {   // "Install": commit + hand off the chosen package to the flasher
                otaStartFlash(mOtaSelectedZip.c_str());   // pre-stage + "Preparing" screen
            } else {                 // "Cancel": go back to the package list
                otaBrowseShowList();
            }
            return true;
        case OTA_CHECKING:
        case OTA_DOWNLOADING:
        case OTA_BROWSE_PEEK:
        default:
            // A worker is running; swallow the accept (there is nothing to confirm).
            return true;
    }
}

// Point of commit: start the gammaos-ota flasher NOW (while nano stays up on a "Preparing update"
// screen) so it can pre-stage itself to tmpfs in the background. nano keeps drawing real staging
// progress (sys.gammaos.ota.stageprog) so the panel is NEVER black. When the flasher signals it is
// staged + ready to take the panel (sys.gammaos.ota.staged=1), the OTA_PREPARING tick releases DRM
// (drmStop, mirroring the app-launch handoff) and exits, and the flasher takes over with its own
// progress - no black gap, no surfaceflinger restart.
void NanoMenu::otaStartFlash(const char* pkg) {
    const char* theme = mNdsTheme ? "dsi" : (mPs3Xmb ? "ps3" : "");
    property_set("sys.gammaos.ota.theme", theme);
    property_set("sys.gammaos.ota.package", pkg ? pkg : "");
    property_set("sys.gammaos.ota.autoinstall", "1");
    property_set("sys.gammaos.ota.stageprog", "0");
    property_set("sys.gammaos.ota.staged", "");
    sync();
    mOtaStage = OTA_PREPARING;
    otaSetDialog("System Update", "Preparing update...  0%\nDo not turn off the power.",
                 std::vector<std::string>(), /*info*/0, 0);
    // Set the trigger LAST (after the theme/package/autoinstall props above are set): an init rule
    // (on property:sys.gammaos.ota.prestage=1) starts gammaos-ota while nano is still alive. The
    // flasher stages without touching the display and waits for nano to release DRM before drawing.
    property_set("sys.gammaos.ota.prestage", "1");
    ALOGI("NanoMenu: OTA prepare (theme='%s' pkg='%s') - flasher pre-staging in background",
          theme, pkg ? pkg : "");
}

} // namespace android
