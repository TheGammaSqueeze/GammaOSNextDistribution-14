/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

// Syncthing (Settings > Syncthing): the nano client for the Syncthing daemon.
//
// Screens are ordinary Ps3Level submenus, so every home theme (PS3 XMB, DSi, Minima, ES-DE)
// renders them with its own list renderer; nothing here draws. The screen map mirrors the real
// Syncthing web GUI: this device, folders (with status and per-folder actions), devices (with
// connection state), pending requests from other devices, daemon options, the LAN web GUI,
// restart and logs. The daemon is started and stopped by init off
// persist.gammaos.syncthing.enabled; everything else goes through the REST client in
// NanoSyncthing.cpp.
//
// Data flow: while a Syncthing screen is on the nav stack a worker thread refreshes a Snapshot
// every couple of seconds; the render thread only copies it under a mutex and rebuilds the open
// screens when the sequence number moves (stTick). Mutations are short loopback calls made
// inline (tens of milliseconds against a local daemon) followed by an immediate refresh request.

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoI18n.h"
#include "NanoSyncthing.h"

#include <algorithm>
#include <cutils/properties.h>
#include <private/android_filesystem_config.h>   // AID_MEDIA_RW
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>   // uptimeMillis

namespace android {

using nanost::Snapshot;
using nanost::FolderCfg;
using nanost::DeviceCfg;
using nanost::fmtBytes;
using nanost::fmtAgo;

static const char* kStEnabledProp = "persist.gammaos.syncthing.enabled";
static const char* kStRestartProp = "sys.gammaos.syncthing.restart";
static const char* kStBinary      = "/system/bin/syncthing";
static const char* kStDefaultRoot = "/data/media/0/Syncthing";   // where new folders go by default

// Dialog theme keys owned by these screens (applyThemeSetting routes them to stDialogResult).
enum StDlg {
    ST_DLG_FOLDER_TYPE = 50, ST_DLG_VERSIONING, ST_DLG_RESCAN, ST_DLG_COMPRESSION,
    ST_DLG_REMOVE_FOLDER, ST_DLG_REMOVE_DEVICE, ST_DLG_PENDING, ST_DLG_RESTART, ST_DLG_WEBGUI,
    ST_DLG_IGNORE_ROW, ST_DLG_LAST = ST_DLG_IGNORE_ROW,
};

// Chooser option orders (the index applyThemeSetting hands back).
static const char* kStFolderTypes[]    = { "sendreceive", "sendonly", "receiveonly" };
static const char* kStVersioning[]     = { "", "trashcan", "simple", "staggered" };
static const char* kStVersioningLbl[]  = { "None", "Trash Can", "Simple", "Staggered" };
static const int   kStRescanS[]        = { 60, 600, 3600, 86400, 0 };
static const char* kStRescanLbl[]      = { "1 minute", "10 minutes", "1 hour", "1 day", "Never (watch only)" };
static const char* kStCompression[]    = { "metadata", "always", "never" };
static const char* kStCompressionLbl[] = { "Metadata Only", "All Data", "Off" };

// ---- state kept on the menu (declared in NanoMenu.h) --------------------------------------
//
// mStSnap        latest Snapshot the worker produced (guarded by mStMutex)
// mStShown       copy the screens were last built from (render thread only)
// mStWorker      refresh thread, alive while mStWorkerRun
// mStRefreshNow  set by a mutation so the worker fetches at once instead of at the next tick
// mStFolderDraft / mStDeviceDraft   the object an editor screen works on (a copy, or a new one)
// mStFolderIsNew / mStDeviceIsNew   the editor is creating, so Save is a PUT of the draft

// Existence only: nano is not allowed to execute the daemon's binary under SELinux, so X_OK would
// read as "not installed" on an enforcing device.
bool NanoMenu::stInstalled() const { return access(kStBinary, F_OK) == 0; }
bool NanoMenu::stEnabled() const { return property_get_bool(kStEnabledProp, false); }
bool NanoMenu::stScreenOpen() const {
    if (mPs3Stack.empty()) return false;
    int k = mPs3Stack.back().screenKind;
    return k >= ST_ROOT && k <= ST_LAST;
}

// ---- worker ------------------------------------------------------------------------------

void NanoMenu::stWorkerStart() {
    if (mStWorkerRun) return;
    mStWorkerRun = true;
    mStWorker = std::thread([this]() {
        nanoThreadNormalPriority();
        nanost::Client client;
        int64_t lastMs = 0;
        while (mStWorkerRun) {
            const int64_t now = (int64_t)uptimeMillis();
            if (mStRefreshNow || now - lastMs >= 2000) {
                mStRefreshNow = false;
                lastMs = now;
                Snapshot s;
                if (stEnabled()) {
                    if (!client.fetchSnapshot(s)) {
                        // Log a failure once per distinct error so a broken REST path is visible
                        // in logcat without flooding it at the 2 s refresh rate.
                        static std::string lastErr;
                        if (s.error != lastErr) { lastErr = s.error; ALOGW("syncthing: snapshot failed: %s", s.error.c_str()); }
                    }
                    // First run: Syncthing names a new device after the hostname, which on Android is
                    // "localhost". Give it the product model instead (once; the user can rename it).
                    if (s.apiOk && s.options.deviceName == "localhost" && !s.myID.empty()) {
                        char model[PROPERTY_VALUE_MAX] = {};
                        property_get("ro.product.model", model, "");
                        if (model[0]) {
                            nanost::Options o = s.options; o.deviceName = model; std::string err;
                            if (client.setOptions(o, s.myID, err)) s.options.deviceName = model;
                        }
                    }
                } else {
                    s.apiOk = false; s.error = "off";
                }
                std::lock_guard<std::mutex> lk(mStMutex);
                s.seq = mStSnap.seq + 1;
                mStSnap = std::move(s);
            }
            for (int i = 0; i < 10 && mStWorkerRun && !mStRefreshNow; i++) usleep(20000);
        }
    });
}

void NanoMenu::stWorkerStop() {
    if (!mStWorkerRun) return;
    mStWorkerRun = false;
    if (mStWorker.joinable()) mStWorker.join();
}

// Per frame from the render loop. Starts the worker when a Syncthing screen is open, stops it
// (and frees the snapshot) once the user has left, and rebuilds the open screens on new data.
void NanoMenu::stTick() {
    const bool open = stScreenOpen();
    if (!open) {
        if (mStWorkerRun) { stWorkerStop(); mStShown = Snapshot(); }
        return;
    }
    stWorkerStart();
    uint32_t seq;
    { std::lock_guard<std::mutex> lk(mStMutex); seq = mStSnap.seq; }
    if (seq == mStShown.seq) return;
    { std::lock_guard<std::mutex> lk(mStMutex); mStShown = mStSnap; }
    stRefreshStackLevels();
    mDisplayDirty = true;
}

void NanoMenu::stRefreshStackLevels() {
    for (auto& lvl : mPs3Stack) {
        int keep = lvl.sel;
        switch (lvl.screenKind) {
            case ST_ROOT:    buildStRoot(lvl); break;
            case ST_FOLDERS: buildStFolders(lvl); break;
            case ST_FOLDER:  buildStFolder(lvl); break;
            case ST_DEVICES: buildStDevices(lvl); break;
            case ST_DEVICE:  buildStDevice(lvl); break;
            case ST_PENDING: buildStPending(lvl); break;
            case ST_OPTIONS: buildStOptions(lvl); break;
            case ST_LOG:     buildStLog(lvl); break;
            case ST_SHARE:   buildStShare(lvl); break;
            case ST_IGNORES: buildStIgnores(lvl); break;
            default: continue;
        }
        int n = (int)lvl.items.size();
        if (keep >= n) keep = n - 1;
        lvl.sel = keep < 0 ? 0 : keep;
    }
}

// Ask the worker for fresh data right away (after a mutation), so the row the user just acted on
// updates within a frame or two instead of at the next 2 s tick.
void NanoMenu::stRefreshSoon() { mStRefreshNow = true; }

void NanoMenu::stPush(Ps3Level& lvl) {
    std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
    mPs3Stack.push_back(lvl);
    mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
    mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
}

// One row. Rows are clean form rows (no icon) unless an icon id is given.
// A row value longer than this would run under its label in the list themes; shorten it in the
// middle (paths keep their tail, IDs their ends) and keep the full text in the description.
static std::string stFitValue(const std::string& v) {
    const size_t kMax = 26;
    if (v.size() <= kMax) return v;
    return v.substr(0, 10) + "..." + v.substr(v.size() - 13);
}

NanoMenu::Ps3Item NanoMenu::stRow(const std::string& label, int row, const std::string& value,
                                  const std::string& desc, int aux, const std::string& payload) {
    NanoMenu::Ps3Item it;
    it.label = label; it.kind = NanoMenu::PS3_ST_ROW; it.a = row; it.b = aux;
    it.value = stFitValue(value); it.payloadStr = payload;
    it.desc = (it.value != value && desc.empty()) ? value : (it.value != value ? value + "\n" + desc : desc);
    it.iconTex = 0; it.nmapTex = 0; it.iconR = it.iconG = it.iconB = 1.0f;
    return it;
}
NanoMenu::Ps3Item NanoMenu::stInfoRow(const std::string& label, const std::string& value, const std::string& desc) {
    NanoMenu::Ps3Item it = stRow(label, NanoMenu::STR_INFO, value, desc);
    it.iconR = it.iconG = it.iconB = 0.55f;   // greyed: not actionable
    return it;
}
NanoMenu::Ps3Item NanoMenu::stToggleRow(const std::string& label, int row, bool on, const std::string& desc, int aux) {
    NanoMenu::Ps3Item it = stRow(label, row, on ? "On" : "Off", desc, aux);
    it.checkState = on ? 1 : 0;
    return it;
}

static std::string stShortId(const std::string& id) { return id.size() > 7 ? id.substr(0, 7) : id; }
// A device ID is 8 groups of 7; two lines of four fit every dialog and list width.
static std::string stWrapId(const std::string& id) {
    if (id.size() < 63) return id;
    return id.substr(0, 31) + "\n" + id.substr(32);
}

// A folder's one-line state for list rows and the root summary.
static std::string stFolderStateText(const Snapshot& s, const FolderCfg& f) {
    if (f.paused) return "Paused";
    auto it = s.folderStatus.find(f.id);
    if (it == s.folderStatus.end()) return "Unknown";
    const nanost::FolderStatus& st = it->second;
    if (!st.error.empty()) return "Error";
    std::string txt = nanost::folderStateLabel(st.state);
    if (st.state == "syncing" && st.globalBytes > 0) {
        int pct = (int)(100.0 * (double)(st.globalBytes - st.needBytes) / (double)st.globalBytes);
        if (pct < 0) pct = 0; if (pct > 100) pct = 100;
        txt += " (" + std::to_string(pct) + "%)";
    } else if (st.state == "idle" && st.needBytes > 0) {
        txt = "Out of Sync (" + fmtBytes(st.needBytes) + ")";
    } else if (st.state == "idle" && st.receiveOnlyChangedFiles > 0) {
        txt = "Local Additions";
    }
    return txt;
}

// A device's one-line state.
static std::string stDeviceStateText(const Snapshot& s, const DeviceCfg& d) {
    if (d.paused) return "Paused";
    auto it = s.connections.find(d.id);
    if (it == s.connections.end() || !it->second.connected) {
        std::string ls = (it != s.connections.end()) ? fmtAgo(it->second.lastSeen) : "never";
        return "Disconnected, last seen " + ls;
    }
    const nanost::DeviceConn& c = it->second;
    char buf[48]; snprintf(buf, sizeof(buf), "%.0f%%", c.completion);
    if (c.completion >= 99.95) return "Up to Date";
    return std::string("Syncing (") + buf + ")";
}

// ---- root --------------------------------------------------------------------------------

void NanoMenu::stOpenRoot() {
    Ps3Level lvl; buildStRoot(lvl);
    stPush(lvl);
    stWorkerStart();
}

void NanoMenu::buildStRoot(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_ROOT;
    out.title = "Syncthing";
    const Snapshot& s = mStShown;
    const bool enabled = stEnabled();

    if (!stInstalled()) {
        out.items.push_back(stInfoRow("Not Installed", "", "This build does not include the Syncthing service."));
        return;
    }
    out.items.push_back(stToggleRow("Syncthing", STR_ENABLED, enabled,
        "Keeps this device's folders in sync with your other devices. Runs in the background, including while games and apps are open."));

    std::string status, statusDesc;
    if (!enabled)            { status = "Stopped"; }
    else if (!s.apiOk)       { status = "Starting..."; statusDesc = s.error == "off" ? "" : s.error; }
    else {
        char up[48];
        if (s.uptimeS >= 3600) snprintf(up, sizeof(up), "up %lld h %lld min", (long long)(s.uptimeS / 3600), (long long)((s.uptimeS % 3600) / 60));
        else                   snprintf(up, sizeof(up), "up %lld min", (long long)(s.uptimeS / 60));
        status = "Running " + s.version + ", " + up;
        int bad = 0;
        for (const std::string& l : s.listeners) if (l.find(": ok") == std::string::npos) bad++;
        if (bad) statusDesc = std::to_string(bad) + " listener(s) failed; see Logs.";
        if (!s.discoveryErrors.empty()) statusDesc += (statusDesc.empty() ? "" : " ") + std::string("Discovery: ") + s.discoveryErrors.front();
    }
    out.items.push_back(stInfoRow("Status", status, statusDesc));

    if (enabled && s.apiOk) {
        out.items.push_back(stRow("This Device", STR_THISDEVICE, s.options.deviceName.empty() ? "(unnamed)" : s.options.deviceName,
                                  "ID " + s.myID + ". Select to show the full ID for pairing."));
        int syncing = 0; for (const FolderCfg& f : s.folders) { std::string t = stFolderStateText(s, f); if (t.rfind("Syncing", 0) == 0 || t.rfind("Scanning", 0) == 0) syncing++; }
        int connected = 0; for (const DeviceCfg& d : s.devices) { auto c = s.connections.find(d.id); if (c != s.connections.end() && c->second.connected) connected++; }
        out.items.push_back(stRow("Folders", STR_FOLDERS, std::to_string(s.folders.size()) + (syncing ? " (" + std::to_string(syncing) + " active)" : ""),
                                  "Folders shared with your other devices."));
        out.items.push_back(stRow("Devices", STR_DEVICES, std::to_string(s.devices.size()) + " (" + std::to_string(connected) + " connected)",
                                  "Other devices this one syncs with."));
        const int pending = (int)(s.pendingDevices.size() + s.pendingFolders.size());
        out.items.push_back(stRow("Pending Requests", STR_PENDING, pending ? std::to_string(pending) : "None",
                                  "Devices that want to connect and folders offered to this device."));
        out.items.push_back(stRow("Options", STR_OPTIONS, "", "Device name, discovery, relays, bandwidth limits."));
        const bool lanGui = s.gui.address.rfind("127.0.0.1", 0) != 0 && s.gui.address.rfind("localhost", 0) != 0;
        out.items.push_back(stRow("Web Interface", STR_WEBGUI, lanGui ? "On" : "Off",
                                  lanGui ? "Open http://" + stDeviceIp() + ":8384 from a browser on your network (sign-in required)."
                                         : "Turn on to manage Syncthing from a browser on your network."));
        out.items.push_back(stRow("Restart Syncthing", STR_RESTART, ""));
        out.items.push_back(stRow("Logs", STR_LOG, ""));
    }
}

std::string NanoMenu::stDeviceIp() const {
    // wlan0's IPv4 from the routing table view, without spawning a shell.
    FILE* f = fopen("/proc/net/fib_trie", "r");
    if (!f) return "<device-ip>";
    char line[256]; std::string prev, found;
    while (fgets(line, sizeof(line), f)) {
        std::string l = line;
        if (l.find("/32 host LOCAL") != std::string::npos && !prev.empty()) {
            size_t p = prev.find_last_of(' ');
            std::string ip = (p == std::string::npos) ? prev : prev.substr(p + 1);
            while (!ip.empty() && (ip.back() == '\n' || ip.back() == ' ')) ip.pop_back();
            if (ip.rfind("127.", 0) != 0 && found.empty()) found = ip;
        }
        prev = l;
    }
    fclose(f);
    return found.empty() ? "<device-ip>" : found;
}

// ---- folders -----------------------------------------------------------------------------

void NanoMenu::buildStFolders(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_FOLDERS;
    out.title = "Folders";
    const Snapshot& s = mStShown;
    for (size_t i = 0; i < s.folders.size(); i++) {
        const FolderCfg& f = s.folders[i];
        Ps3Item it = stRow(f.label.empty() ? f.id : f.label, STR_FOLDER_ROW, stFolderStateText(s, f),
                           f.path + "  (" + nanost::folderTypeLabel(f.type) + ", " + std::to_string(f.devices.size()) + " device" + (f.devices.size() == 1 ? "" : "s") + ")",
                           (int)i, f.id);
        it.iconTex = iconTexForIcon(62); it.nmapTex = nmapForIcon(62);   // folder glyph
        out.items.push_back(it);
    }
    out.items.push_back(stRow("Add Folder", STR_FOLDER_ADD, "", "Share a folder on this device with your other devices."));
}

// The editor works on mStFolderDraft: a copy of the stored folder (edits are sent as they are
// made, like the web GUI's Save on each field) or a new folder that is only sent by Save.
void NanoMenu::stOpenFolder(const std::string& id) {
    const Snapshot& s = mStShown;
    for (const FolderCfg& f : s.folders) if (f.id == id) { mStFolderDraft = f; mStFolderIsNew = false; break; }
    Ps3Level lvl; buildStFolder(lvl); stPush(lvl);
}

void NanoMenu::stAddFolder(const std::string& presetId, const std::string& presetLabel, const std::string& sharedWith) {
    mStFolderDraft = FolderCfg();
    mStFolderDraft.id = presetId.empty() ? nanost::Client::newFolderId() : presetId;
    mStFolderDraft.label = presetLabel;
    mStFolderDraft.type = "sendreceive";
    mStFolderDraft.path = std::string(kStDefaultRoot) + "/" + (presetLabel.empty() ? mStFolderDraft.id : presetLabel);
    if (!sharedWith.empty()) mStFolderDraft.devices.push_back(sharedWith);
    mStFolderIsNew = true;
    Ps3Level lvl; buildStFolder(lvl); stPush(lvl);
}

void NanoMenu::buildStFolder(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_FOLDER;
    const Snapshot& s = mStShown;
    FolderCfg& f = mStFolderDraft;
    // Keep the draft of an existing folder in step with the daemon (another client may edit it).
    if (!mStFolderIsNew) for (const FolderCfg& x : s.folders) if (x.id == f.id) { f = x; break; }
    out.title = mStFolderIsNew ? "New Folder" : (f.label.empty() ? f.id : f.label);

    if (mStFolderIsNew) out.items.push_back(stRow("Save Folder", STR_FOLDER_SAVE, "", "Create the folder with the settings below."));
    out.items.push_back(stRow("Label", STR_FOLDER_LABEL, f.label.empty() ? "(none)" : f.label, "Shown on this device only."));
    if (mStFolderIsNew) out.items.push_back(stRow("Folder ID", STR_FOLDER_ID, f.id, "Must match the ID used on the other devices."));
    else                out.items.push_back(stInfoRow("Folder ID", f.id));
    out.items.push_back(stRow("Path", STR_FOLDER_PATH, f.path, mStFolderIsNew ? "Select to browse for a folder on this device." : "The folder on this device. Cannot be changed once created."));
    out.items.push_back(stRow("Folder Type", STR_FOLDER_TYPE, nanost::folderTypeLabel(f.type)));
    std::string shared;
    for (const std::string& id : f.devices) {
        std::string n = stShortId(id);
        for (const DeviceCfg& d : s.devices) if (d.id == id) n = d.name;
        shared += (shared.empty() ? "" : ", ") + n;
    }
    out.items.push_back(stRow("Shared With", STR_FOLDER_SHARE, shared.empty() ? "(no devices)" : shared, "Devices this folder is synced with."));
    std::string ver = "None";
    for (int i = 0; i < 4; i++) if (f.versioningType == kStVersioning[i]) ver = kStVersioningLbl[i];
    if (!f.versioningType.empty() && !f.versioningParam.empty()) ver += " (" + f.versioningParam + ")";
    out.items.push_back(stRow("File Versioning", STR_FOLDER_VERSIONING, ver, "Keep old copies of changed or deleted files."));
    if (!f.versioningType.empty() && f.versioningType != "external") {
        const char* what = f.versioningType == "trashcan" ? "Clean out after (days)" : f.versioningType == "simple" ? "Versions to keep" : "Maximum age (seconds)";
        out.items.push_back(stRow(what, STR_FOLDER_VERSIONING_PARAM, f.versioningParam.empty() ? "(default)" : f.versioningParam));
    }
    std::string rescan = "Custom";
    for (int i = 0; i < 5; i++) if (f.rescanIntervalS == kStRescanS[i]) rescan = kStRescanLbl[i];
    out.items.push_back(stRow("Full Rescan Interval", STR_FOLDER_RESCAN, rescan));
    out.items.push_back(stToggleRow("Watch for Changes", STR_FOLDER_WATCH, f.fsWatcherEnabled, "Notice changes as they happen instead of only at rescans."));
    out.items.push_back(stToggleRow("Ignore Permissions", STR_FOLDER_IGNPERMS, f.ignorePerms, "Recommended on Android storage."));
    if (!mStFolderIsNew) {
        out.items.push_back(stToggleRow("Paused", STR_FOLDER_PAUSED, f.paused));
        auto st = s.folderStatus.find(f.id);
        if (st != s.folderStatus.end()) {
            const nanost::FolderStatus& x = st->second;
            out.items.push_back(stInfoRow("State", stFolderStateText(s, f), x.error.empty() ? (x.stateChanged.empty() ? "" : "Since " + fmtAgo(x.stateChanged)) : x.error));
            out.items.push_back(stInfoRow("Global State", fmtBytes(x.globalBytes) + ", " + std::to_string(x.globalFiles) + " files"));
            out.items.push_back(stInfoRow("Local State", fmtBytes(x.localBytes) + ", " + std::to_string(x.localFiles) + " files"));
            if (x.needBytes > 0) out.items.push_back(stInfoRow("Out of Sync", fmtBytes(x.needBytes) + ", " + std::to_string(x.needFiles) + " files"));
            if (x.pullErrors > 0) out.items.push_back(stInfoRow("Failed Items", std::to_string(x.pullErrors), "See Logs for the file names."));
        }
        out.items.push_back(stRow("Rescan Now", STR_FOLDER_RESCAN_NOW, ""));
        if (f.type == "sendonly")    out.items.push_back(stRow("Override Changes", STR_FOLDER_OVERRIDE, "", "Make the other devices match this folder's contents."));
        if (f.type == "receiveonly") out.items.push_back(stRow("Revert Local Changes", STR_FOLDER_REVERT, "", "Discard changes made on this device."));
        out.items.push_back(stRow("Ignore Patterns", STR_FOLDER_IGNORES, "", "Files and folders that are not synced."));
        out.items.push_back(stRow("Remove Folder", STR_FOLDER_REMOVE, "", "Stops syncing. Files on this device are kept."));
    }
}

// Make sure a new folder's directory exists and is writable by the daemon. nano runs as root, so a
// plain mkdir would leave a root-owned directory the daemon (system, group media_rw) cannot write
// into; internal storage is media_rw:media_rw with the setgid bit, so match that. Only the missing
// tail is created; an existing path is left exactly as it is.
void NanoMenu::stEnsureFolderDir(const std::string& path) {
    std::string cur;
    size_t pos = 0;
    while (pos < path.size()) {
        size_t next = path.find('/', pos + 1);
        cur = path.substr(0, next == std::string::npos ? path.size() : next);
        if (cur.size() > 1 && access(cur.c_str(), F_OK) != 0) {
            if (mkdir(cur.c_str(), 02775) == 0) chown(cur.c_str(), AID_MEDIA_RW, AID_MEDIA_RW);
        }
        if (next == std::string::npos) break;
        pos = next;
    }
}

// Apply the draft: a new folder is created on Save, an existing one is sent after every change.
bool NanoMenu::stCommitFolder() {
    if (mStFolderIsNew) return true;   // sent by Save
    nanost::Client c; std::string err;
    if (!c.putFolder(mStFolderDraft, err)) { feInfoDialog("Syncthing", "Could not update the folder: " + err); return false; }
    stRefreshSoon();
    return true;
}

void NanoMenu::stFolderPathSelect(const std::string& path) {
    mFolderPickTarget = 0;
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE) mPs3Stack.pop_back();
    mStFolderDraft.path = path;
    if (mStFolderDraft.label.empty()) {
        size_t sl = path.find_last_of('/');
        mStFolderDraft.label = (sl == std::string::npos) ? path : path.substr(sl + 1);
    }
    stRefreshStackLevels(); mDisplayDirty = true;
}

// ---- devices -----------------------------------------------------------------------------

void NanoMenu::buildStDevices(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_DEVICES;
    out.title = "Devices";
    const Snapshot& s = mStShown;
    for (size_t i = 0; i < s.devices.size(); i++) {
        const DeviceCfg& d = s.devices[i];
        std::string desc = stShortId(d.id);
        auto c = s.connections.find(d.id);
        if (c != s.connections.end() && c->second.connected)
            desc += "  " + c->second.address + " (" + c->second.type + ")  down " + fmtBytes(c->second.inBytesTotal) + ", up " + fmtBytes(c->second.outBytesTotal);
        Ps3Item it = stRow(d.name.empty() ? stShortId(d.id) : d.name, STR_DEVICE_ROW, stDeviceStateText(s, d), desc, (int)i, d.id);
        it.iconTex = iconTexForIcon(6); it.nmapTex = nmapForIcon(6);   // network glyph
        out.items.push_back(it);
    }
    out.items.push_back(stRow("Add Device", STR_DEVICE_ADD, "", "Pair with another device by its Device ID."));
}

void NanoMenu::stOpenDevice(const std::string& id) {
    for (const DeviceCfg& d : mStShown.devices) if (d.id == id) { mStDeviceDraft = d; mStDeviceIsNew = false; break; }
    Ps3Level lvl; buildStDevice(lvl); stPush(lvl);
}

// Adding starts with the ID, since nothing else makes sense without it; the editor opens once a
// well-formed ID has been typed (or accepted from a pending request).
void NanoMenu::stAddDevice(const std::string& presetId, const std::string& presetName, const std::string& presetAddress) {
    auto open = [this](const std::string& id, const std::string& name, const std::string& addr) {
        mStDeviceDraft = DeviceCfg();
        mStDeviceDraft.id = id; mStDeviceDraft.name = name;
        mStDeviceDraft.addresses = { "dynamic" };
        if (!addr.empty() && addr != "dynamic") mStDeviceDraft.addresses.push_back(addr);
        mStDeviceDraft.compression = "metadata";
        mStDeviceIsNew = true;
        Ps3Level lvl; buildStDevice(lvl); stPush(lvl);
    };
    if (!presetId.empty()) { open(presetId, presetName, presetAddress); return; }
    openOskForPassword("Device ID of the other device (shown in its Syncthing)", [this, open](const std::string& val) {
        std::string id;
        if (!nanost::Client::normaliseDeviceId(val, id)) { feInfoDialog("Syncthing", "That is not a valid Device ID. It is 56 letters and digits, usually shown in groups of 7."); return; }
        for (const DeviceCfg& d : mStShown.devices) if (d.id == id) { feInfoDialog("Syncthing", "That device is already added."); return; }
        if (id == mStShown.myID) { feInfoDialog("Syncthing", "That is this device's own ID."); return; }
        open(id, "", "");
    });
    mOskPasswordMode = false; mOskPlaintext = true; mOskQuery.clear(); mOsk.caret = 0;
}

void NanoMenu::buildStDevice(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_DEVICE;
    const Snapshot& s = mStShown;
    DeviceCfg& d = mStDeviceDraft;
    if (!mStDeviceIsNew) for (const DeviceCfg& x : s.devices) if (x.id == d.id) { d = x; break; }
    out.title = mStDeviceIsNew ? "New Device" : (d.name.empty() ? stShortId(d.id) : d.name);

    if (mStDeviceIsNew) out.items.push_back(stRow("Save Device", STR_DEVICE_SAVE, "", "Add the device with the settings below. The other device must add this one too."));
    out.items.push_back(stRow("Name", STR_DEVICE_NAME, d.name.empty() ? "(from the device)" : d.name, "Shown on this device only."));
    out.items.push_back(stInfoRow("Device ID", stShortId(d.id) + "...", stWrapId(d.id)));
    std::string addrs;
    for (const std::string& a : d.addresses) addrs += (addrs.empty() ? "" : ", ") + a;
    out.items.push_back(stRow("Addresses", STR_DEVICE_ADDR, addrs.empty() ? "dynamic" : addrs, "dynamic finds the device by discovery; add tcp://host:22000 for a fixed address."));
    std::string comp = "Metadata Only";
    for (int i = 0; i < 3; i++) if (d.compression == kStCompression[i]) comp = kStCompressionLbl[i];
    out.items.push_back(stRow("Compression", STR_DEVICE_COMPRESSION, comp));
    out.items.push_back(stToggleRow("Introducer", STR_DEVICE_INTRODUCER, d.introducer, "Add the devices this device is connected to automatically."));
    out.items.push_back(stToggleRow("Auto Accept Folders", STR_DEVICE_AUTOACCEPT, d.autoAcceptFolders, "Accept folders this device offers without asking."));
    std::string shared;
    for (const FolderCfg& f : s.folders) for (const std::string& id : f.devices) if (id == d.id) shared += (shared.empty() ? "" : ", ") + (f.label.empty() ? f.id : f.label);
    out.items.push_back(stRow("Shared Folders", STR_DEVICE_SHARE, shared.empty() ? "(none)" : shared, "Folders synced with this device."));
    if (!mStDeviceIsNew) {
        out.items.push_back(stToggleRow("Paused", STR_DEVICE_PAUSED, d.paused));
        auto c = s.connections.find(d.id);
        if (c != s.connections.end()) {
            const nanost::DeviceConn& x = c->second;
            out.items.push_back(stInfoRow("Connection", x.connected ? x.address + " (" + x.type + ")" : "Disconnected", x.connected ? "" : "Last seen " + fmtAgo(x.lastSeen)));
            if (x.connected) {
                out.items.push_back(stInfoRow("Version", x.clientVersion.empty() ? "(unknown)" : x.clientVersion));
                out.items.push_back(stInfoRow("Transferred", "down " + fmtBytes(x.inBytesTotal) + ", up " + fmtBytes(x.outBytesTotal)));
                char buf[32]; snprintf(buf, sizeof(buf), "%.1f%%", x.completion);
                out.items.push_back(stInfoRow("Completion", buf, "How much of the shared data this device has."));
            }
        }
        out.items.push_back(stRow("Remove Device", STR_DEVICE_REMOVE, "", "Stops syncing with this device. Folders stay on this device."));
    }
}

bool NanoMenu::stCommitDevice() {
    if (mStDeviceIsNew) return true;
    nanost::Client c; std::string err;
    if (!c.putDevice(mStDeviceDraft, err)) { feInfoDialog("Syncthing", "Could not update the device: " + err); return false; }
    stRefreshSoon();
    return true;
}

// ---- share (multi-select) ------------------------------------------------------------------
//
// mStShareFolderMode true: choosing the devices a folder (mStFolderDraft) is shared with.
// false: choosing the folders a device (mStDeviceDraft) is shared with (edits each folder).

void NanoMenu::buildStShare(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_SHARE;
    const Snapshot& s = mStShown;
    if (mStShareFolderMode) {
        out.title = "Shared With";
        for (size_t i = 0; i < s.devices.size(); i++) {
            const DeviceCfg& d = s.devices[i];
            bool on = false; for (const std::string& id : mStFolderDraft.devices) if (id == d.id) on = true;
            out.items.push_back(stToggleRow(d.name.empty() ? stShortId(d.id) : d.name, STR_SHARE_TOGGLE, on, stShortId(d.id), (int)i));
        }
        if (s.devices.empty()) out.items.push_back(stInfoRow("No Devices", "", "Add a device first, then share this folder with it."));
    } else {
        out.title = "Shared Folders";
        for (size_t i = 0; i < s.folders.size(); i++) {
            const FolderCfg& f = s.folders[i];
            bool on = false; for (const std::string& id : f.devices) if (id == mStDeviceDraft.id) on = true;
            out.items.push_back(stToggleRow(f.label.empty() ? f.id : f.label, STR_SHARE_TOGGLE, on, f.path, (int)i));
        }
        if (s.folders.empty()) out.items.push_back(stInfoRow("No Folders", "", "Add a folder first, then share it with this device."));
    }
}

void NanoMenu::stShareToggle(int idx) {
    const Snapshot& s = mStShown;
    if (mStShareFolderMode) {
        if (idx < 0 || idx >= (int)s.devices.size()) return;
        const std::string& id = s.devices[idx].id;
        auto& v = mStFolderDraft.devices;
        auto it = std::find(v.begin(), v.end(), id);
        if (it == v.end()) v.push_back(id); else v.erase(it);
        stCommitFolder();
    } else {
        if (idx < 0 || idx >= (int)s.folders.size()) return;
        if (mStDeviceIsNew) { feInfoDialog("Syncthing", "Save the device first, then choose the folders to share."); return; }
        FolderCfg f = s.folders[idx];
        auto& v = f.devices;
        auto it = std::find(v.begin(), v.end(), mStDeviceDraft.id);
        if (it == v.end()) v.push_back(mStDeviceDraft.id); else v.erase(it);
        nanost::Client c; std::string err;
        if (!c.putFolder(f, err)) feInfoDialog("Syncthing", "Could not update the folder: " + err);
        stRefreshSoon();
    }
    stRefreshStackLevels(); mDisplayDirty = true;
}

// ---- pending -----------------------------------------------------------------------------

void NanoMenu::buildStPending(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_PENDING;
    out.title = "Pending Requests";
    const Snapshot& s = mStShown;
    for (size_t i = 0; i < s.pendingDevices.size(); i++) {
        const nanost::PendingDevice& p = s.pendingDevices[i];
        out.items.push_back(stRow(p.name.empty() ? stShortId(p.id) : p.name, STR_PENDING_DEVICE, "Wants to connect",
                                  stShortId(p.id) + " from " + p.address + ", " + fmtAgo(p.time), (int)i, p.id));
    }
    for (size_t i = 0; i < s.pendingFolders.size(); i++) {
        const nanost::PendingFolder& p = s.pendingFolders[i];
        out.items.push_back(stRow(p.label.empty() ? p.id : p.label, STR_PENDING_FOLDER, "Folder offered",
                                  "Offered by " + p.offeredByName + ", " + fmtAgo(p.time), (int)i, p.id));
    }
    if (out.items.empty()) out.items.push_back(stInfoRow("No Pending Requests", "", "Requests from other devices appear here."));
}

// ---- options -----------------------------------------------------------------------------

void NanoMenu::buildStOptions(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_OPTIONS;
    out.title = "Options";
    const nanost::Options& o = mStShown.options;
    out.items.push_back(stRow("Device Name", STR_OPT_NAME, o.deviceName.empty() ? "(unnamed)" : o.deviceName, "How this device appears on your other devices."));
    std::string listen; for (const std::string& a : o.listenAddresses) listen += (listen.empty() ? "" : ", ") + a;
    out.items.push_back(stRow("Listen Addresses", STR_OPT_LISTEN, listen.empty() ? "default" : listen, "default listens on tcp and quic port 22000 and uses relays."));
    out.items.push_back(stToggleRow("Global Discovery", STR_OPT_GLOBAL, o.globalAnnounceEnabled, "Find devices over the internet through the public discovery servers."));
    out.items.push_back(stToggleRow("Local Discovery", STR_OPT_LOCAL, o.localAnnounceEnabled, "Find devices on the same network."));
    out.items.push_back(stToggleRow("Relaying", STR_OPT_RELAYS, o.relaysEnabled, "Connect through public relays when a direct connection is not possible."));
    out.items.push_back(stToggleRow("NAT Traversal", STR_OPT_NAT, o.natEnabled, "Ask the router to forward the sync port (UPnP / NAT-PMP)."));
    out.items.push_back(stRow("Download Limit", STR_OPT_RECV, o.maxRecvKbps > 0 ? std::to_string(o.maxRecvKbps) + " KiB/s" : "Unlimited"));
    out.items.push_back(stRow("Upload Limit", STR_OPT_SEND, o.maxSendKbps > 0 ? std::to_string(o.maxSendKbps) + " KiB/s" : "Unlimited"));
    out.items.push_back(stToggleRow("Apply Limits on LAN", STR_OPT_LANLIMIT, o.limitBandwidthInLan));
    out.items.push_back(stRow("Concurrent Scans", STR_OPT_CONCURRENCY, o.maxFolderConcurrency > 0 ? std::to_string(o.maxFolderConcurrency) : "Automatic", "Folders scanned or synced at the same time. Lower saves memory."));
    out.items.push_back(stRow("Minimum Free Space", STR_OPT_MINFREE, std::to_string(o.minHomeDiskFreePct) + "%", "Pause syncing when the storage falls below this."));
    out.items.push_back(stToggleRow("Anonymous Usage Reporting", STR_OPT_UR, o.urAccepted, "Send anonymous usage statistics to the Syncthing project."));
}

bool NanoMenu::stCommitOptions(const nanost::Options& o) {
    nanost::Client c; std::string err;
    if (!c.setOptions(o, mStShown.myID, err)) { feInfoDialog("Syncthing", "Could not save the options: " + err); return false; }
    mStShown.options = o;
    stRefreshSoon();
    return true;
}

// ---- ignore patterns ---------------------------------------------------------------------

void NanoMenu::buildStIgnores(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_IGNORES;
    out.title = "Ignore Patterns";
    for (size_t i = 0; i < mStIgnores.size(); i++)
        out.items.push_back(stRow(mStIgnores[i], STR_IGNORE_ROW, "", "", (int)i));
    out.items.push_back(stRow("Add Pattern", STR_IGNORE_ADD, "", "Examples: *.tmp   (?d).DS_Store   /Cache"));
}

void NanoMenu::stOpenIgnores() {
    nanost::Client c; std::string err;
    if (!c.getIgnores(mStFolderDraft.id, mStIgnores, err)) { feInfoDialog("Syncthing", "Could not read the ignore patterns: " + err); return; }
    Ps3Level lvl; buildStIgnores(lvl); stPush(lvl);
}

void NanoMenu::stSaveIgnores() {
    nanost::Client c; std::string err;
    if (!c.setIgnores(mStFolderDraft.id, mStIgnores, err)) feInfoDialog("Syncthing", "Could not save the ignore patterns: " + err);
    stRefreshStackLevels(); mDisplayDirty = true;
}

// ---- log ---------------------------------------------------------------------------------

void NanoMenu::buildStLog(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = ST_LOG;
    out.title = "Syncthing Log";
    nanost::Client c; std::string err; std::vector<std::string> lines;
    if (!c.fetchLog(lines, 60, err)) { out.items.push_back(stInfoRow("Log unavailable", "", err)); return; }
    for (size_t i = 0; i < lines.size(); i++) {
        Ps3Item it = stInfoRow(lines[i], "");
        it.iconR = it.iconG = it.iconB = 0.85f;
        out.items.push_back(it);
    }
    if (lines.empty()) out.items.push_back(stInfoRow("(empty)", ""));
    out.sel = out.items.empty() ? 0 : (int)out.items.size() - 1;   // newest at the bottom, start there
}

// ---- choosers and confirms ---------------------------------------------------------------

void NanoMenu::stOpenChooser(int key, const std::string& title, const std::vector<std::string>& opts, int sel) {
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = key;
    mPs3DlgTitle = title; mPs3DlgBody.clear();
    for (const std::string& o : opts) { mPs3DlgOptions.push_back(o); mPs3DlgSwatch.push_back(-1); }
    mPs3DlgSel = (sel >= 0 && sel < (int)opts.size()) ? sel : 0; mPs3DlgOrigSel = mPs3DlgSel;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(6);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

void NanoMenu::stOpenConfirm(int key, const std::string& title, const std::string& body, const char* action) {
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = key;
    mPs3DlgTitle = title; mPs3DlgBody = body;
    mPs3DlgOptions.push_back("Cancel"); mPs3DlgSwatch.push_back(-1);
    mPs3DlgOptions.push_back(action);   mPs3DlgSwatch.push_back(-1);
    mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(6);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

// A text field through the OSK, prefilled with the current value.
void NanoMenu::stOpenText(const std::string& prompt, const std::string& prefill, std::function<void(const std::string&)> onSubmit) {
    openOskForPassword(prompt, std::move(onSubmit));
    mOskPasswordMode = false; mOskPlaintext = true;
    mOskQuery = prefill; mOsk.caret = (int)mOskQuery.size();
}

bool NanoMenu::stDialogResult(int key, int sel) {
    if (key < ST_DLG_FOLDER_TYPE || key > ST_DLG_LAST) return false;
    nanost::Client c; std::string err;
    switch (key) {
        case ST_DLG_FOLDER_TYPE:
            if (sel >= 0 && sel < 3) { mStFolderDraft.type = kStFolderTypes[sel]; stCommitFolder(); }
            break;
        case ST_DLG_VERSIONING:
            if (sel >= 0 && sel < 4) { mStFolderDraft.versioningType = kStVersioning[sel]; mStFolderDraft.versioningParam.clear(); stCommitFolder(); }
            break;
        case ST_DLG_RESCAN:
            if (sel >= 0 && sel < 5) { mStFolderDraft.rescanIntervalS = kStRescanS[sel]; stCommitFolder(); }
            break;
        case ST_DLG_COMPRESSION:
            if (sel >= 0 && sel < 3) { mStDeviceDraft.compression = kStCompression[sel]; stCommitDevice(); }
            break;
        case ST_DLG_REMOVE_FOLDER:
            if (sel == 1) {
                if (!c.removeFolder(mStFolderDraft.id, err)) feInfoDialog("Syncthing", "Could not remove the folder: " + err);
                else if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == ST_FOLDER) mPs3Stack.pop_back();
                stRefreshSoon();
            }
            break;
        case ST_DLG_REMOVE_DEVICE:
            if (sel == 1) {
                if (!c.removeDevice(mStDeviceDraft.id, err)) feInfoDialog("Syncthing", "Could not remove the device: " + err);
                else if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == ST_DEVICE) mPs3Stack.pop_back();
                stRefreshSoon();
            }
            break;
        case ST_DLG_PENDING: {
            // Options: 0 Accept, 1 Ignore, 2 Cancel. mStPendingIsDevice / mStPendingIdx say which row.
            const Snapshot& s = mStShown;
            if (sel == 0) {
                if (mStPendingIsDevice && mStPendingIdx < (int)s.pendingDevices.size()) {
                    const nanost::PendingDevice& p = s.pendingDevices[mStPendingIdx];
                    stAddDevice(p.id, p.name, p.address);
                } else if (!mStPendingIsDevice && mStPendingIdx < (int)s.pendingFolders.size()) {
                    const nanost::PendingFolder& p = s.pendingFolders[mStPendingIdx];
                    stAddFolder(p.id, p.label, p.offeredBy);
                }
            } else if (sel == 1) {
                if (mStPendingIsDevice && mStPendingIdx < (int)s.pendingDevices.size()) c.dismissPendingDevice(s.pendingDevices[mStPendingIdx].id, err);
                else if (!mStPendingIsDevice && mStPendingIdx < (int)s.pendingFolders.size()) c.dismissPendingFolder(s.pendingFolders[mStPendingIdx].id, s.pendingFolders[mStPendingIdx].offeredBy, err);
                if (!err.empty()) feInfoDialog("Syncthing", err);
                stRefreshSoon();
            }
            break;
        }
        case ST_DLG_RESTART:
            if (sel == 1) { if (!c.restart(err)) feInfoDialog("Syncthing", "Could not restart: " + err); stRefreshSoon(); }
            break;
        case ST_DLG_WEBGUI:
            // 0 = Off (loopback only), 1 = On (all interfaces, sign-in required)
            if (sel == 0) {
                nanost::GuiCfg g = mStShown.gui; g.address = "127.0.0.1:8384";
                if (!c.setGui(g, "", err)) feInfoDialog("Syncthing", "Could not change the web interface: " + err);
                stRefreshSoon();
            } else if (sel == 1) {
                stOpenText("Username for the web interface", mStShown.gui.user.empty() ? "gammaos" : mStShown.gui.user, [this](const std::string& user) {
                    if (user.empty()) return;
                    stOpenText("Password for the web interface", "", [this, user](const std::string& pw) {
                        if (pw.size() < 4) { feInfoDialog("Syncthing", "The web interface needs a password of at least 4 characters when it is open to the network."); return; }
                        nanost::Client c2; std::string e2;
                        nanost::GuiCfg g = mStShown.gui; g.address = "0.0.0.0:8384"; g.user = user;
                        if (!c2.setGui(g, pw, e2)) { feInfoDialog("Syncthing", "Could not change the web interface: " + e2); return; }
                        // The GUI listener rebinds only on restart.
                        property_set(kStRestartProp, "1");
                        stRefreshSoon();
                    });
                    mOskPasswordMode = true; mOskPlaintext = false;
                });
            }
            break;
        case ST_DLG_IGNORE_ROW:
            // 0 Edit, 1 Remove, 2 Cancel for pattern mStIgnoreIdx
            if (mStIgnoreIdx >= 0 && mStIgnoreIdx < (int)mStIgnores.size()) {
                if (sel == 0) {
                    const int idx = mStIgnoreIdx;
                    stOpenText("Ignore pattern", mStIgnores[idx], [this, idx](const std::string& v) {
                        if (idx >= (int)mStIgnores.size()) return;
                        if (v.empty()) mStIgnores.erase(mStIgnores.begin() + idx); else mStIgnores[idx] = v;
                        stSaveIgnores();
                    });
                } else if (sel == 1) {
                    mStIgnores.erase(mStIgnores.begin() + mStIgnoreIdx);
                    stSaveIgnores();
                }
            }
            break;
        default: break;
    }
    stRefreshStackLevels(); mDisplayDirty = true;
    return true;
}

// ---- row dispatch (from ps3XmbSelect on a PS3_ST_ROW) -----------------------------------------

void NanoMenu::stSelectRow(const Ps3Item& it) {
    nanost::Client c; std::string err;
    const Snapshot& s = mStShown;
    switch (it.a) {
        case STR_INFO: return;

        // root
        case STR_ENABLED: {
            const bool on = !stEnabled();
            property_set(kStEnabledProp, on ? "1" : "0");
            ALOGI("syncthing: %s", on ? "enabled" : "disabled");
            if (!on) { std::lock_guard<std::mutex> lk(mStMutex); mStSnap = Snapshot(); mStSnap.seq = mStShown.seq + 1; }
            stRefreshSoon(); stRefreshStackLevels(); mDisplayDirty = true;
            return;
        }
        case STR_THISDEVICE:
            feInfoDialog(s.options.deviceName.empty() ? "This Device" : s.options.deviceName,
                         "Device ID\n" + stWrapId(s.myID) + "\n\nEnter this ID on another device to pair it. Devices that add this ID show up under Pending Requests.");
            return;
        case STR_FOLDERS: { Ps3Level lvl; buildStFolders(lvl); stPush(lvl); return; }
        case STR_DEVICES: { Ps3Level lvl; buildStDevices(lvl); stPush(lvl); return; }
        case STR_PENDING: { Ps3Level lvl; buildStPending(lvl); stPush(lvl); return; }
        case STR_OPTIONS: { Ps3Level lvl; buildStOptions(lvl); stPush(lvl); return; }
        case STR_LOG:     { Ps3Level lvl; buildStLog(lvl); stPush(lvl); return; }
        case STR_RESTART: stOpenConfirm(ST_DLG_RESTART, "Restart Syncthing", "Syncing pauses for a few seconds while the service restarts.", "Restart"); return;
        case STR_WEBGUI: {
            const bool lanGui = s.gui.address.rfind("127.0.0.1", 0) != 0 && s.gui.address.rfind("localhost", 0) != 0;
            stOpenChooser(ST_DLG_WEBGUI, "Web Interface", { "Off (this device only)", "On (available on the network)" }, lanGui ? 1 : 0);
            return;
        }

        // folders
        case STR_FOLDER_ROW: stOpenFolder(it.payloadStr); return;
        case STR_FOLDER_ADD: stAddFolder("", "", ""); return;
        case STR_FOLDER_SAVE: {
            FolderCfg& f = mStFolderDraft;
            if (f.path.empty()) { feInfoDialog("Syncthing", "Choose a path for the folder first."); return; }
            stEnsureFolderDir(f.path);
            if (!c.putFolder(f, err)) { feInfoDialog("Syncthing", "Could not create the folder: " + err); return; }
            mStFolderIsNew = false;
            stRefreshSoon();
            if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == ST_FOLDER) mPs3Stack.pop_back();
            return;
        }
        case STR_FOLDER_LABEL:
            stOpenText("Folder label", mStFolderDraft.label, [this](const std::string& v) { mStFolderDraft.label = v; stCommitFolder(); stRefreshStackLevels(); });
            return;
        case STR_FOLDER_ID:
            stOpenText("Folder ID (letters, digits, dashes)", mStFolderDraft.id, [this](const std::string& v) {
                std::string id; for (char ch : v) if (isalnum((unsigned char)ch) || ch == '-' || ch == '_' || ch == '.') id += ch;
                if (!id.empty()) mStFolderDraft.id = id;
                stRefreshStackLevels();
            });
            return;
        case STR_FOLDER_PATH:
            if (!mStFolderIsNew) return;
            mFolderPickTarget = 6;   // PS3_GS_SELFOLDER -> stFolderPathSelect
            { std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
              Ps3Level lvl; buildFolderBrowser("", lvl); mPs3Stack.push_back(lvl);
              mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
              mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
              mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f; }
            return;
        case STR_FOLDER_TYPE: {
            int cur = 0; for (int i = 0; i < 3; i++) if (mStFolderDraft.type == kStFolderTypes[i]) cur = i;
            stOpenChooser(ST_DLG_FOLDER_TYPE, "Folder Type", { "Send & Receive", "Send Only", "Receive Only" }, cur);
            return;
        }
        case STR_FOLDER_SHARE: { mStShareFolderMode = true; Ps3Level lvl; buildStShare(lvl); stPush(lvl); return; }
        case STR_FOLDER_VERSIONING: {
            int cur = 0; for (int i = 0; i < 4; i++) if (mStFolderDraft.versioningType == kStVersioning[i]) cur = i;
            stOpenChooser(ST_DLG_VERSIONING, "File Versioning", { kStVersioningLbl[0], kStVersioningLbl[1], kStVersioningLbl[2], kStVersioningLbl[3] }, cur);
            return;
        }
        case STR_FOLDER_VERSIONING_PARAM:
            stOpenText(it.label, mStFolderDraft.versioningParam, [this](const std::string& v) {
                std::string n; for (char ch : v) if (isdigit((unsigned char)ch)) n += ch;
                mStFolderDraft.versioningParam = n; stCommitFolder(); stRefreshStackLevels();
            });
            return;
        case STR_FOLDER_RESCAN: {
            int cur = 2; for (int i = 0; i < 5; i++) if (mStFolderDraft.rescanIntervalS == kStRescanS[i]) cur = i;
            stOpenChooser(ST_DLG_RESCAN, "Full Rescan Interval", { kStRescanLbl[0], kStRescanLbl[1], kStRescanLbl[2], kStRescanLbl[3], kStRescanLbl[4] }, cur);
            return;
        }
        case STR_FOLDER_WATCH:    mStFolderDraft.fsWatcherEnabled = !mStFolderDraft.fsWatcherEnabled; stCommitFolder(); break;
        case STR_FOLDER_IGNPERMS: mStFolderDraft.ignorePerms = !mStFolderDraft.ignorePerms; stCommitFolder(); break;
        case STR_FOLDER_PAUSED:
            if (!c.setFolderPaused(mStFolderDraft.id, !mStFolderDraft.paused, err)) feInfoDialog("Syncthing", err);
            else mStFolderDraft.paused = !mStFolderDraft.paused;
            stRefreshSoon(); break;
        case STR_FOLDER_RESCAN_NOW: if (!c.rescanFolder(mStFolderDraft.id, err)) feInfoDialog("Syncthing", err); stRefreshSoon(); break;
        case STR_FOLDER_OVERRIDE:   if (!c.overrideFolder(mStFolderDraft.id, err)) feInfoDialog("Syncthing", err); stRefreshSoon(); break;
        case STR_FOLDER_REVERT:     if (!c.revertFolder(mStFolderDraft.id, err)) feInfoDialog("Syncthing", err); stRefreshSoon(); break;
        case STR_FOLDER_IGNORES:    stOpenIgnores(); return;
        case STR_FOLDER_REMOVE:
            stOpenConfirm(ST_DLG_REMOVE_FOLDER, "Remove " + (mStFolderDraft.label.empty() ? mStFolderDraft.id : mStFolderDraft.label),
                          "This device stops syncing the folder. The files already on this device are kept.", "Remove");
            return;

        // devices
        case STR_DEVICE_ROW: stOpenDevice(it.payloadStr); return;
        case STR_DEVICE_ADD: stAddDevice("", "", ""); return;
        case STR_DEVICE_SAVE: {
            if (!c.putDevice(mStDeviceDraft, err)) { feInfoDialog("Syncthing", "Could not add the device: " + err); return; }
            mStDeviceIsNew = false;
            stRefreshSoon();
            if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == ST_DEVICE) mPs3Stack.pop_back();
            return;
        }
        case STR_DEVICE_NAME:
            stOpenText("Device name", mStDeviceDraft.name, [this](const std::string& v) { mStDeviceDraft.name = v; stCommitDevice(); stRefreshStackLevels(); });
            return;
        case STR_DEVICE_ADDR: {
            std::string cur; for (const std::string& a : mStDeviceDraft.addresses) cur += (cur.empty() ? "" : ", ") + a;
            stOpenText("Addresses (comma separated; dynamic or tcp://host:22000)", cur, [this](const std::string& v) {
                std::vector<std::string> out; std::string tok;
                for (char ch : v + ",") { if (ch == ',' || ch == ' ') { if (!tok.empty()) out.push_back(tok); tok.clear(); } else tok += ch; }
                mStDeviceDraft.addresses = out.empty() ? std::vector<std::string>{"dynamic"} : out;
                stCommitDevice(); stRefreshStackLevels();
            });
            return;
        }
        case STR_DEVICE_COMPRESSION: {
            int cur = 0; for (int i = 0; i < 3; i++) if (mStDeviceDraft.compression == kStCompression[i]) cur = i;
            stOpenChooser(ST_DLG_COMPRESSION, "Compression", { kStCompressionLbl[0], kStCompressionLbl[1], kStCompressionLbl[2] }, cur);
            return;
        }
        case STR_DEVICE_INTRODUCER: mStDeviceDraft.introducer = !mStDeviceDraft.introducer; stCommitDevice(); break;
        case STR_DEVICE_AUTOACCEPT: mStDeviceDraft.autoAcceptFolders = !mStDeviceDraft.autoAcceptFolders; stCommitDevice(); break;
        case STR_DEVICE_SHARE: { mStShareFolderMode = false; Ps3Level lvl; buildStShare(lvl); stPush(lvl); return; }
        case STR_DEVICE_PAUSED:
            if (!c.setDevicePaused(mStDeviceDraft.id, !mStDeviceDraft.paused, err)) feInfoDialog("Syncthing", err);
            else mStDeviceDraft.paused = !mStDeviceDraft.paused;
            stRefreshSoon(); break;
        case STR_DEVICE_REMOVE:
            stOpenConfirm(ST_DLG_REMOVE_DEVICE, "Remove " + (mStDeviceDraft.name.empty() ? stShortId(mStDeviceDraft.id) : mStDeviceDraft.name),
                          "This device stops syncing with it. Folders and files on this device are kept.", "Remove");
            return;

        // share
        case STR_SHARE_TOGGLE: stShareToggle(it.b); return;

        // pending
        case STR_PENDING_DEVICE: case STR_PENDING_FOLDER:
            mStPendingIsDevice = (it.a == STR_PENDING_DEVICE); mStPendingIdx = it.b;
            stOpenChooser(ST_DLG_PENDING, it.label, { mStPendingIsDevice ? "Add Device" : "Add Folder", "Ignore", "Cancel" }, 0);
            return;

        // options
        case STR_OPT_NAME:
            stOpenText("Device name", s.options.deviceName, [this](const std::string& v) { nanost::Options o = mStShown.options; o.deviceName = v; stCommitOptions(o); stRefreshStackLevels(); });
            return;
        case STR_OPT_LISTEN: {
            std::string cur; for (const std::string& a : s.options.listenAddresses) cur += (cur.empty() ? "" : ", ") + a;
            stOpenText("Listen addresses (comma separated)", cur, [this](const std::string& v) {
                std::vector<std::string> out; std::string tok;
                for (char ch : v + ",") { if (ch == ',' || ch == ' ') { if (!tok.empty()) out.push_back(tok); tok.clear(); } else tok += ch; }
                nanost::Options o = mStShown.options; o.listenAddresses = out.empty() ? std::vector<std::string>{"default"} : out;
                stCommitOptions(o); stRefreshStackLevels();
            });
            return;
        }
        case STR_OPT_GLOBAL:   { nanost::Options o = s.options; o.globalAnnounceEnabled = !o.globalAnnounceEnabled; stCommitOptions(o); break; }
        case STR_OPT_LOCAL:    { nanost::Options o = s.options; o.localAnnounceEnabled = !o.localAnnounceEnabled; stCommitOptions(o); break; }
        case STR_OPT_RELAYS:   { nanost::Options o = s.options; o.relaysEnabled = !o.relaysEnabled; stCommitOptions(o); break; }
        case STR_OPT_NAT:      { nanost::Options o = s.options; o.natEnabled = !o.natEnabled; stCommitOptions(o); break; }
        case STR_OPT_LANLIMIT: { nanost::Options o = s.options; o.limitBandwidthInLan = !o.limitBandwidthInLan; stCommitOptions(o); break; }
        case STR_OPT_UR:       { nanost::Options o = s.options; o.urAccepted = !o.urAccepted; stCommitOptions(o); break; }
        case STR_OPT_RECV: case STR_OPT_SEND: case STR_OPT_CONCURRENCY: case STR_OPT_MINFREE: {
            const int which = it.a;
            const char* prompt = which == STR_OPT_RECV ? "Download limit in KiB/s (0 = unlimited)" : which == STR_OPT_SEND ? "Upload limit in KiB/s (0 = unlimited)"
                               : which == STR_OPT_CONCURRENCY ? "Concurrent scans (0 = automatic)" : "Minimum free space in percent";
            int cur = which == STR_OPT_RECV ? s.options.maxRecvKbps : which == STR_OPT_SEND ? s.options.maxSendKbps
                    : which == STR_OPT_CONCURRENCY ? s.options.maxFolderConcurrency : s.options.minHomeDiskFreePct;
            stOpenText(prompt, std::to_string(cur), [this, which](const std::string& v) {
                int n = atoi(v.c_str()); if (n < 0) n = 0;
                nanost::Options o = mStShown.options;
                if (which == STR_OPT_RECV) o.maxRecvKbps = n; else if (which == STR_OPT_SEND) o.maxSendKbps = n;
                else if (which == STR_OPT_CONCURRENCY) o.maxFolderConcurrency = n; else o.minHomeDiskFreePct = n > 100 ? 100 : n;
                stCommitOptions(o); stRefreshStackLevels();
            });
            return;
        }

        // ignore patterns
        case STR_IGNORE_ROW:
            mStIgnoreIdx = it.b;
            stOpenChooser(ST_DLG_IGNORE_ROW, it.label, { "Edit", "Remove", "Cancel" }, 0);
            return;
        case STR_IGNORE_ADD:
            stOpenText("Ignore pattern", "", [this](const std::string& v) { if (!v.empty()) { mStIgnores.push_back(v); stSaveIgnores(); } });
            return;

        default: break;
    }
    stRefreshStackLevels(); mDisplayDirty = true;
}

} // namespace android
