/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

// NanoSyncthing - a client for the Syncthing daemon's REST API, for the nano menu.
//
// The daemon (external/gammaos-syncthing) listens on 127.0.0.1:8384 and authenticates with the
// API key it writes into its config.xml. This file is the whole of nano's knowledge of that API:
// a loopback HTTP/1.1 client (no library dependency, no TLS: loopback only), a typed view of the
// parts of the config and status the menu shows, and the mutations the menu offers. Nothing here
// touches the UI or the nav stack; NanoMenuSyncthing.cpp builds the screens on top of it and
// calls these from a worker thread so the render thread never waits on a socket.
//
// The daemon itself is started and stopped by init through persist.gammaos.syncthing.enabled
// (see syncthing.rc); this client only talks to a running daemon.
#pragma once

#include "NanoJson.h"

#include <stdint.h>
#include <map>
#include <string>
#include <vector>

namespace android {
namespace nanost {

// ---- transport --------------------------------------------------------------

struct HttpResult {
    int status = 0;          // HTTP status, 0 when the request never reached the daemon
    std::string body;
    std::string error;       // transport-level failure (connect / timeout / short read), else empty
    bool ok() const { return status >= 200 && status < 300; }
};

// One request to the loopback API. `path` is the request target ("/rest/system/status?x=1"),
// `body` is sent as application/json when non-empty. Blocks up to timeoutMs. Never throws.
HttpResult httpRequest(const char* method, const std::string& path, const std::string& apiKey,
                       const std::string& body, int timeoutMs);

// ---- model (the subset of the config and status the menu shows) --------------------

struct FolderCfg {
    std::string id;                    // folder ID (shared with peers; not editable after creation)
    std::string label;                 // display name
    std::string path;                  // absolute path on this device
    std::string type;                  // sendreceive | sendonly | receiveonly
    bool paused = false;
    std::vector<std::string> devices;  // device IDs this folder is shared with (this device excluded)
    int  rescanIntervalS = 3600;
    bool fsWatcherEnabled = true;
    bool ignorePerms = true;           // Android storage has no meaningful permission bits
    std::string versioningType;        // "" | trashcan | simple | staggered | external
    std::string versioningParam;       // trashcan cleanoutDays / simple keep / staggered maxAge
    int  minDiskFreePct = 1;
    njson::Value raw;                  // the daemon's full object, so an edit round-trips the rest
};

struct FolderStatus {
    std::string state;                 // idle | scanning | syncing | sync-preparing | error | unknown
    std::string error;
    std::string stateChanged;          // RFC3339
    int64_t globalBytes = 0, localBytes = 0, needBytes = 0;
    int64_t globalFiles = 0, localFiles = 0, needFiles = 0;
    int64_t receiveOnlyChangedFiles = 0;   // local changes on a receive-only folder (revertable)
    int     pullErrors = 0;
};

struct DeviceCfg {
    std::string id;
    std::string name;
    std::vector<std::string> addresses; // "dynamic" or tcp://host:port
    bool paused = false;
    bool introducer = false;
    bool autoAcceptFolders = false;
    std::string compression;           // metadata | always | never
    njson::Value raw;
};

struct DeviceConn {
    bool connected = false;
    bool paused = false;
    std::string address;               // remote address once connected
    std::string type;                  // tcp-client | tcp-server | quic-* | relay-*
    std::string clientVersion;
    int64_t inBytesTotal = 0, outBytesTotal = 0;
    double  completion = 100.0;        // % of shared data this device has (from /rest/db/completion)
    std::string lastSeen;              // RFC3339 (from /rest/stats/device)
};

struct PendingDevice { std::string id, name, address, time; };
struct PendingFolder { std::string id, label, offeredBy, offeredByName, time; };

struct Options {
    std::string deviceName;            // the local device's own name (config.devices[myID].name)
    std::vector<std::string> listenAddresses;
    bool globalAnnounceEnabled = true;
    bool localAnnounceEnabled = true;
    bool relaysEnabled = true;
    bool natEnabled = true;
    int  maxSendKbps = 0, maxRecvKbps = 0;
    bool limitBandwidthInLan = false;
    int  maxFolderConcurrency = 0;
    int  minHomeDiskFreePct = 1;
    bool urAccepted = false;           // anonymous usage reporting
    bool crashReportingEnabled = false;
};

struct GuiCfg {
    std::string address;               // 127.0.0.1:8384 or 0.0.0.0:8384
    std::string user;
    bool passwordSet = false;
    bool useTLS = false;
};

// Everything one refresh of the menu needs, fetched together so a screen never mixes data from
// two points in time. `seq` increments per successful fetch; the UI rebuilds on change.
struct Snapshot {
    bool apiOk = false;                // the daemon answered
    std::string error;                 // why not, when !apiOk
    std::string version;               // "v2.1.5"
    std::string myID;
    int64_t uptimeS = 0;
    std::vector<FolderCfg> folders;
    std::map<std::string, FolderStatus> folderStatus;   // by folder id
    std::vector<DeviceCfg> devices;                     // remote devices only
    std::map<std::string, DeviceConn> connections;      // by device id
    std::vector<PendingDevice> pendingDevices;
    std::vector<PendingFolder> pendingFolders;
    Options options;
    GuiCfg gui;
    std::vector<std::string> discoveryErrors;           // "server: error" lines from /rest/system/discovery
    std::vector<std::string> listeners;                 // "addr: ok|error" from connectionServiceStatus
    uint32_t seq = 0;
};

// ---- client -------------------------------------------------------------------

class Client {
public:
    // Reads the API key from the daemon's config.xml (path fixed by syncthing.rc). False when the
    // daemon has never run (no config yet) or the file is unreadable.
    bool loadApiKey();
    bool hasApiKey() const { return !mApiKey.empty(); }

    // One full refresh. Returns false and fills out.error when the daemon did not answer at all;
    // partial failures of secondary calls (stats, pending) leave those parts empty but succeed.
    bool fetchSnapshot(Snapshot& out, int timeoutMs = 4000);

    // Folder mutations. Add and edit share one call (PUT is an upsert); a new folder must carry
    // id, label, path and type. Errors come back as a short message for a dialog.
    bool putFolder(const FolderCfg& f, std::string& err);
    bool removeFolder(const std::string& id, std::string& err);
    bool setFolderPaused(const std::string& id, bool paused, std::string& err);
    bool rescanFolder(const std::string& id, std::string& err);
    bool overrideFolder(const std::string& id, std::string& err);   // send-only: push local state
    bool revertFolder(const std::string& id, std::string& err);     // receive-only: drop local changes
    bool getIgnores(const std::string& id, std::vector<std::string>& lines, std::string& err);
    bool setIgnores(const std::string& id, const std::vector<std::string>& lines, std::string& err);

    // Device mutations.
    bool putDevice(const DeviceCfg& d, std::string& err);
    bool removeDevice(const std::string& id, std::string& err);
    bool setDevicePaused(const std::string& id, bool paused, std::string& err);

    // Pending requests from other devices.
    bool dismissPendingDevice(const std::string& id, std::string& err);
    bool dismissPendingFolder(const std::string& folderId, const std::string& deviceId, std::string& err);

    // Daemon options and GUI.
    bool setOptions(const Options& o, const std::string& myID, std::string& err);
    bool setGui(const GuiCfg& g, const std::string& newPassword, std::string& err);
    bool restart(std::string& err);
    bool shutdown(std::string& err);

    // Tail of the daemon log (most recent last), for the Logs screen.
    bool fetchLog(std::vector<std::string>& lines, int maxLines, std::string& err);

    // The folder ID Syncthing would generate for a new folder (matches the web GUI's style).
    static std::string newFolderId();
    // Validates the 56-character device ID format the user typed (with or without dashes).
    static bool normaliseDeviceId(const std::string& in, std::string& out);

private:
    HttpResult call(const char* method, const std::string& path, const std::string& body = std::string(),
                    int timeoutMs = 4000);
    bool getJson(const std::string& path, njson::Value& out, std::string& err, int timeoutMs = 4000);
    bool sendJson(const char* method, const std::string& path, const njson::Value& v, std::string& err);
    bool post(const std::string& path, std::string& err);
    std::string mApiKey;
};

// Human formatting shared by every screen (bytes -> "1.2 GB", RFC3339 -> "3 min ago").
std::string fmtBytes(int64_t b);
std::string fmtAgo(const std::string& rfc3339);
std::string fmtRate(int64_t bytesPerS);
const char* folderTypeLabel(const std::string& type);
const char* folderStateLabel(const std::string& state);

// ---- folder paths ------------------------------------------------------------------------
// The daemon runs outside the app sandbox, so a synced folder must sit on a raw storage mount:
// internal storage is /data/media/<user>/... and a removable card is /mnt/media_rw/<volume>/...
// (what vold mounts underneath the FUSE views apps see at /storage). canonicalFolderPath maps the
// paths a user picks or types (/storage/emulated/0, /sdcard, /storage/XXXX-XXXX) onto those raw
// mounts and leaves anything else alone; isSupportedFolderPath then says whether the result is a
// place the daemon may use at all (its SELinux domain covers exactly these two trees).
std::string canonicalFolderPath(const std::string& path);
bool isSupportedFolderPath(const std::string& path);
bool isRemovableFolderPath(const std::string& path);   // on a removable card (FAT: no permission bits)

} // namespace nanost
} // namespace android
