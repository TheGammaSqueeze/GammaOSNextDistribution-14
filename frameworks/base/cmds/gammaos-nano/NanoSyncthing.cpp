/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

// NanoSyncthing - the Syncthing REST client (see NanoSyncthing.h).
//
// Endpoints are those of Syncthing 2.x (unchanged from the 1.x REST API where used here):
// https://docs.syncthing.net/dev/rest.html

#define LOG_TAG "GammaOSNano"

#include "NanoSyncthing.h"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#include <utils/Log.h>

namespace android {
namespace nanost {

// Fixed by syncthing.rc (--gui-address / --home).
static const char* kApiHost  = "127.0.0.1";
static const int   kApiPort  = 8384;
static const char* kConfigXml = "/data/misc/syncthing/config.xml";

// ---- HTTP ---------------------------------------------------------------------

// Read until the socket closes or `want` bytes arrived (want < 0 = until close).
static bool readAll(int fd, std::string& out, long want) {
    char buf[8192];
    while (want < 0 || (long)out.size() < want) {
        ssize_t n = read(fd, buf, sizeof(buf));
        if (n < 0) { if (errno == EINTR) continue; return false; }
        if (n == 0) break;
        out.append(buf, (size_t)n);
    }
    return want < 0 || (long)out.size() >= want;
}

// Decode a chunked transfer body (the daemon uses it for JSON of unknown length).
static bool dechunk(const std::string& in, std::string& out) {
    size_t p = 0;
    while (p < in.size()) {
        size_t eol = in.find("\r\n", p);
        if (eol == std::string::npos) return false;
        long n = strtol(in.substr(p, eol - p).c_str(), nullptr, 16);
        if (n < 0) return false;
        if (n == 0) return true;
        p = eol + 2;
        if (p + (size_t)n > in.size()) return false;
        out.append(in, p, (size_t)n);
        p += (size_t)n + 2;
    }
    return true;
}

HttpResult httpRequest(const char* method, const std::string& path, const std::string& apiKey,
                       const std::string& body, int timeoutMs) {
    HttpResult r;
    int fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) { r.error = "socket"; return r; }
    struct timeval tv = { timeoutMs / 1000, (timeoutMs % 1000) * 1000 };
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    int one = 1; setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
    struct sockaddr_in sa; memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET; sa.sin_port = htons(kApiPort);
    inet_pton(AF_INET, kApiHost, &sa.sin_addr);
    if (connect(fd, (struct sockaddr*)&sa, sizeof(sa)) != 0) {
        r.error = std::string("connect: ") + strerror(errno);
        close(fd); return r;
    }
    std::string req = std::string(method) + " " + path + " HTTP/1.1\r\n"
                      "Host: 127.0.0.1:" + std::to_string(kApiPort) + "\r\n"
                      "X-API-Key: " + apiKey + "\r\n"
                      "Connection: close\r\n";
    if (!body.empty()) {
        req += "Content-Type: application/json\r\n";
        req += "Content-Length: " + std::to_string(body.size()) + "\r\n";
    }
    req += "\r\n"; req += body;
    size_t off = 0;
    while (off < req.size()) {
        ssize_t n = write(fd, req.data() + off, req.size() - off);
        if (n < 0) { if (errno == EINTR) continue; r.error = std::string("write: ") + strerror(errno); close(fd); return r; }
        off += (size_t)n;
    }
    std::string raw;
    readAll(fd, raw, -1);
    close(fd);
    size_t hdrEnd = raw.find("\r\n\r\n");
    if (raw.size() < 12 || strncmp(raw.c_str(), "HTTP/1.", 7) != 0 || hdrEnd == std::string::npos) {
        r.error = raw.empty() ? "empty reply" : "malformed reply";
        return r;
    }
    r.status = atoi(raw.c_str() + 9);
    std::string hdr = raw.substr(0, hdrEnd);
    std::string rest = raw.substr(hdrEnd + 4);
    // Header names are case-insensitive; lower-case the block once for the two lookups.
    std::string lh = hdr; for (char& c : lh) c = (char)tolower((unsigned char)c);
    if (lh.find("transfer-encoding: chunked") != std::string::npos) {
        if (!dechunk(rest, r.body)) r.error = "bad chunked body";
    } else {
        r.body = rest;
    }
    return r;
}

// ---- helpers ------------------------------------------------------------------

static std::string jsonStr(const njson::Value& v, const char* key) { return v.getString(key); }

static void strArray(const njson::Value* v, std::vector<std::string>& out) {
    out.clear();
    if (!v || !v->isArray()) return;
    for (const auto& e : v->arr) out.push_back(e.asString());
}

static njson::Value strArrayValue(const std::vector<std::string>& in) {
    njson::Value a = njson::Value::makeArray();
    for (const auto& s : in) a.arr.push_back(njson::Value::makeString(s));
    return a;
}

static int64_t jsonI64(const njson::Value& v, const char* key) {
    const njson::Value* x = v.find(key);
    return x ? (int64_t)x->asNumber() : 0;
}

// The daemon reports errors as JSON {"error": "..."} or as plain text; keep whichever is there.
static std::string errorOf(const HttpResult& r) {
    if (!r.error.empty()) return r.error;
    njson::Value v;
    if (njson::parse(r.body, &v) && v.isObject()) {
        std::string e = v.getString("error");
        if (!e.empty()) return e;
    }
    std::string b = r.body;
    while (!b.empty() && (b.back() == '\n' || b.back() == '\r')) b.pop_back();
    if (b.size() > 160) b = b.substr(0, 160) + "...";
    return b.empty() ? ("HTTP " + std::to_string(r.status)) : b;
}

static FolderCfg parseFolder(const njson::Value& f) {
    FolderCfg c;
    c.id = jsonStr(f, "id"); c.label = jsonStr(f, "label"); c.path = jsonStr(f, "path");
    c.type = jsonStr(f, "type"); c.paused = f.getBool("paused");
    c.rescanIntervalS = f.getInt("rescanIntervalS", 3600);
    c.fsWatcherEnabled = f.getBool("fsWatcherEnabled", true);
    c.ignorePerms = f.getBool("ignorePerms", false);
    c.devices.clear();
    if (const njson::Value* d = f.find("devices"); d && d->isArray())
        for (const auto& e : d->arr) c.devices.push_back(e.getString("deviceID"));
    if (const njson::Value* v = f.find("versioning"); v && v->isObject()) {
        c.versioningType = v->getString("type");
        if (const njson::Value* p = v->find("params"); p && p->isObject()) {
            // The one parameter each scheme exposes in the web GUI's simple form.
            if (c.versioningType == "trashcan")       c.versioningParam = p->getString("cleanoutDays");
            else if (c.versioningType == "simple")    c.versioningParam = p->getString("keep");
            else if (c.versioningType == "staggered") c.versioningParam = p->getString("maxAge");
        }
    }
    if (const njson::Value* m = f.find("minDiskFree"); m && m->isObject()) c.minDiskFreePct = m->getInt("value", 1);
    c.raw = f;
    return c;
}

static DeviceCfg parseDevice(const njson::Value& d) {
    DeviceCfg c;
    c.id = jsonStr(d, "deviceID"); c.name = jsonStr(d, "name");
    strArray(d.find("addresses"), c.addresses);
    c.paused = d.getBool("paused"); c.introducer = d.getBool("introducer");
    c.autoAcceptFolders = d.getBool("autoAcceptFolders");
    c.compression = jsonStr(d, "compression");
    c.raw = d;
    return c;
}

// ---- client -------------------------------------------------------------------

bool Client::loadApiKey() {
    FILE* f = fopen(kConfigXml, "r");
    if (!f) return false;
    std::string text; char buf[4096]; size_t n;
    // The key sits in the <gui> block near the top; 64 KB is far more than the whole file.
    while (text.size() < 65536 && (n = fread(buf, 1, sizeof(buf), f)) > 0) text.append(buf, n);
    fclose(f);
    size_t a = text.find("<apikey>");
    size_t b = (a == std::string::npos) ? a : text.find("</apikey>", a);
    if (a == std::string::npos || b == std::string::npos) return false;
    mApiKey = text.substr(a + 8, b - a - 8);
    return !mApiKey.empty();
}

HttpResult Client::call(const char* method, const std::string& path, const std::string& body, int timeoutMs) {
    if (mApiKey.empty() && !loadApiKey()) {
        HttpResult r; r.error = "no API key (daemon has not run yet)"; return r;
    }
    HttpResult r = httpRequest(method, path, mApiKey, body, timeoutMs);
    if (r.status == 403) {
        // The key rotates if the config is regenerated; reload once and retry.
        if (loadApiKey()) r = httpRequest(method, path, mApiKey, body, timeoutMs);
    }
    return r;
}

bool Client::getJson(const std::string& path, njson::Value& out, std::string& err, int timeoutMs) {
    HttpResult r = call("GET", path, std::string(), timeoutMs);
    if (!r.ok()) { err = errorOf(r); return false; }
    if (!njson::parse(r.body, &out)) { err = "bad JSON from " + path; return false; }
    return true;
}

bool Client::sendJson(const char* method, const std::string& path, const njson::Value& v, std::string& err) {
    HttpResult r = call(method, path, njson::serialize(v, false));
    if (!r.ok()) { err = errorOf(r); ALOGW("syncthing: %s %s -> %s", method, path.c_str(), err.c_str()); return false; }
    return true;
}

bool Client::post(const std::string& path, std::string& err) {
    HttpResult r = call("POST", path);
    if (!r.ok()) { err = errorOf(r); ALOGW("syncthing: POST %s -> %s", path.c_str(), err.c_str()); return false; }
    return true;
}

bool Client::fetchSnapshot(Snapshot& out, int timeoutMs) {
    Snapshot s;
    njson::Value st;
    if (!getJson("/rest/system/status", st, s.error, timeoutMs)) { out = s; return false; }
    s.apiOk = true;
    s.myID = st.getString("myID");
    s.uptimeS = jsonI64(st, "uptime");
    if (const njson::Value* css = st.find("connectionServiceStatus"); css && css->isObject())
        for (const auto& kv : css->obj) {
            std::string e = kv.second.getString("error");
            s.listeners.push_back(kv.first + ": " + (e.empty() ? "ok" : e));
        }
    if (const njson::Value* de = st.find("discoveryErrors"); de && de->isObject())
        for (const auto& kv : de->obj) if (!kv.second.asString().empty()) s.discoveryErrors.push_back(kv.first + ": " + kv.second.asString());

    njson::Value ver; std::string e2;
    if (getJson("/rest/system/version", ver, e2, timeoutMs)) s.version = ver.getString("version");

    njson::Value cfg;
    if (!getJson("/rest/config", cfg, s.error, timeoutMs)) { s.apiOk = false; out = s; return false; }
    if (const njson::Value* fs = cfg.find("folders"); fs && fs->isArray())
        for (const auto& f : fs->arr) s.folders.push_back(parseFolder(f));
    if (const njson::Value* ds = cfg.find("devices"); ds && ds->isArray())
        for (const auto& d : ds->arr) {
            DeviceCfg c = parseDevice(d);
            if (c.id == s.myID) s.options.deviceName = c.name;   // our own entry carries our name
            else s.devices.push_back(c);
        }
    if (const njson::Value* o = cfg.find("options"); o && o->isObject()) {
        Options& op = s.options;
        strArray(o->find("listenAddresses"), op.listenAddresses);
        op.globalAnnounceEnabled = o->getBool("globalAnnounceEnabled", true);
        op.localAnnounceEnabled  = o->getBool("localAnnounceEnabled", true);
        op.relaysEnabled         = o->getBool("relaysEnabled", true);
        op.natEnabled            = o->getBool("natEnabled", true);
        op.maxSendKbps           = o->getInt("maxSendKbps");
        op.maxRecvKbps           = o->getInt("maxRecvKbps");
        op.limitBandwidthInLan   = o->getBool("limitBandwidthInLan");
        op.maxFolderConcurrency  = o->getInt("maxFolderConcurrency");
        op.crashReportingEnabled = o->getBool("crashReportingEnabled");
        op.urAccepted            = o->getInt("urAccepted") > 0;
        if (const njson::Value* m = o->find("minHomeDiskFree"); m && m->isObject()) op.minHomeDiskFreePct = m->getInt("value", 1);
    }
    if (const njson::Value* g = cfg.find("gui"); g && g->isObject()) {
        s.gui.address = g->getString("address"); s.gui.user = g->getString("user");
        s.gui.passwordSet = !g->getString("password").empty(); s.gui.useTLS = g->getBool("useTLS");
    }

    for (const FolderCfg& f : s.folders) {
        njson::Value fst; std::string e;
        if (!getJson("/rest/db/status?folder=" + f.id, fst, e, timeoutMs)) continue;
        FolderStatus& x = s.folderStatus[f.id];
        x.state = fst.getString("state"); x.error = fst.getString("error");
        x.stateChanged = fst.getString("stateChanged");
        x.globalBytes = jsonI64(fst, "globalBytes"); x.localBytes = jsonI64(fst, "localBytes"); x.needBytes = jsonI64(fst, "needBytes");
        x.globalFiles = jsonI64(fst, "globalFiles"); x.localFiles = jsonI64(fst, "localFiles"); x.needFiles = jsonI64(fst, "needFiles");
        x.receiveOnlyChangedFiles = jsonI64(fst, "receiveOnlyChangedFiles");
        x.pullErrors = fst.getInt("pullErrors");
    }

    njson::Value conns; std::string e3;
    if (getJson("/rest/system/connections", conns, e3, timeoutMs))
        if (const njson::Value* c = conns.find("connections"); c && c->isObject())
            for (const auto& kv : c->obj) {
                DeviceConn& d = s.connections[kv.first];
                d.connected = kv.second.getBool("connected"); d.paused = kv.second.getBool("paused");
                d.address = kv.second.getString("address"); d.type = kv.second.getString("type");
                d.clientVersion = kv.second.getString("clientVersion");
                d.inBytesTotal = jsonI64(kv.second, "inBytesTotal"); d.outBytesTotal = jsonI64(kv.second, "outBytesTotal");
            }
    njson::Value stats; std::string e4;
    if (getJson("/rest/stats/device", stats, e4, timeoutMs) && stats.isObject())
        for (const auto& kv : stats.obj) s.connections[kv.first].lastSeen = kv.second.getString("lastSeen");
    for (const DeviceCfg& d : s.devices) {
        njson::Value comp; std::string e;
        if (getJson("/rest/db/completion?device=" + d.id, comp, e, timeoutMs))
            s.connections[d.id].completion = comp.find("completion") ? comp.find("completion")->asNumber(100.0) : 100.0;
    }

    njson::Value pd; std::string e5;
    if (getJson("/rest/cluster/pending/devices", pd, e5, timeoutMs) && pd.isObject())
        for (const auto& kv : pd.obj) {
            PendingDevice p; p.id = kv.first; p.name = kv.second.getString("name");
            p.address = kv.second.getString("address"); p.time = kv.second.getString("time");
            s.pendingDevices.push_back(p);
        }
    njson::Value pf; std::string e6;
    if (getJson("/rest/cluster/pending/folders", pf, e6, timeoutMs) && pf.isObject())
        for (const auto& kv : pf.obj) {
            if (const njson::Value* ob = kv.second.find("offeredBy"); ob && ob->isObject())
                for (const auto& dv : ob->obj) {
                    PendingFolder p; p.id = kv.first; p.offeredBy = dv.first;
                    p.label = dv.second.getString("label"); p.time = dv.second.getString("time");
                    for (const DeviceCfg& d : s.devices) if (d.id == p.offeredBy) p.offeredByName = d.name;
                    if (p.offeredByName.empty()) p.offeredByName = p.offeredBy.substr(0, 7);
                    s.pendingFolders.push_back(p);
                }
        }
    out = s;
    return true;
}

// Folder objects are sent whole: start from the daemon's own defaults for a new folder (so every
// field the daemon expects is present and current for this version), or from the stored object
// for an edit, and overlay the fields the menu edits.
bool Client::putFolder(const FolderCfg& f, std::string& err) {
    njson::Value obj;
    if (f.raw.isObject()) obj = f.raw;
    else if (!getJson("/rest/config/defaults/folder", obj, err)) return false;
    obj.set("id") = njson::Value::makeString(f.id);
    obj.set("label") = njson::Value::makeString(f.label);
    obj.set("path") = njson::Value::makeString(f.path);
    obj.set("type") = njson::Value::makeString(f.type);
    obj.set("paused") = njson::Value::makeBool(f.paused);
    obj.set("rescanIntervalS") = njson::Value::makeNumber(f.rescanIntervalS);
    obj.set("fsWatcherEnabled") = njson::Value::makeBool(f.fsWatcherEnabled);
    obj.set("ignorePerms") = njson::Value::makeBool(f.ignorePerms);
    njson::Value devs = njson::Value::makeArray();
    for (const std::string& id : f.devices) { njson::Value d = njson::Value::makeObject(); d.set("deviceID") = njson::Value::makeString(id); devs.arr.push_back(d); }
    obj.set("devices") = devs;
    njson::Value ver = njson::Value::makeObject();
    ver.set("type") = njson::Value::makeString(f.versioningType);
    njson::Value params = njson::Value::makeObject();
    if (!f.versioningParam.empty()) {
        const char* key = f.versioningType == "trashcan" ? "cleanoutDays" : f.versioningType == "simple" ? "keep" : f.versioningType == "staggered" ? "maxAge" : nullptr;
        if (key) params.set(key) = njson::Value::makeString(f.versioningParam);
    }
    ver.set("params") = params;
    obj.set("versioning") = ver;
    njson::Value mdf = njson::Value::makeObject();
    mdf.set("value") = njson::Value::makeNumber(f.minDiskFreePct); mdf.set("unit") = njson::Value::makeString("%");
    obj.set("minDiskFree") = mdf;
    return sendJson("PUT", "/rest/config/folders/" + f.id, obj, err);
}

bool Client::removeFolder(const std::string& id, std::string& err) {
    HttpResult r = call("DELETE", "/rest/config/folders/" + id);
    if (!r.ok()) { err = errorOf(r); return false; }
    return true;
}

bool Client::setFolderPaused(const std::string& id, bool paused, std::string& err) {
    njson::Value v = njson::Value::makeObject(); v.set("paused") = njson::Value::makeBool(paused);
    return sendJson("PATCH", "/rest/config/folders/" + id, v, err);
}
bool Client::rescanFolder(const std::string& id, std::string& err)   { return post("/rest/db/scan?folder=" + id, err); }
bool Client::overrideFolder(const std::string& id, std::string& err) { return post("/rest/db/override?folder=" + id, err); }
bool Client::revertFolder(const std::string& id, std::string& err)   { return post("/rest/db/revert?folder=" + id, err); }

bool Client::getIgnores(const std::string& id, std::vector<std::string>& lines, std::string& err) {
    njson::Value v;
    if (!getJson("/rest/db/ignores?folder=" + id, v, err)) return false;
    strArray(v.find("ignore"), lines);
    return true;
}
bool Client::setIgnores(const std::string& id, const std::vector<std::string>& lines, std::string& err) {
    njson::Value v = njson::Value::makeObject(); v.set("ignore") = strArrayValue(lines);
    return sendJson("POST", "/rest/db/ignores?folder=" + id, v, err);
}

bool Client::putDevice(const DeviceCfg& d, std::string& err) {
    njson::Value obj;
    if (d.raw.isObject()) obj = d.raw;
    else if (!getJson("/rest/config/defaults/device", obj, err)) return false;
    obj.set("deviceID") = njson::Value::makeString(d.id);
    obj.set("name") = njson::Value::makeString(d.name);
    obj.set("addresses") = strArrayValue(d.addresses.empty() ? std::vector<std::string>{"dynamic"} : d.addresses);
    obj.set("paused") = njson::Value::makeBool(d.paused);
    obj.set("introducer") = njson::Value::makeBool(d.introducer);
    obj.set("autoAcceptFolders") = njson::Value::makeBool(d.autoAcceptFolders);
    if (!d.compression.empty()) obj.set("compression") = njson::Value::makeString(d.compression);
    return sendJson("PUT", "/rest/config/devices/" + d.id, obj, err);
}

bool Client::removeDevice(const std::string& id, std::string& err) {
    HttpResult r = call("DELETE", "/rest/config/devices/" + id);
    if (!r.ok()) { err = errorOf(r); return false; }
    return true;
}

bool Client::setDevicePaused(const std::string& id, bool paused, std::string& err) {
    njson::Value v = njson::Value::makeObject(); v.set("paused") = njson::Value::makeBool(paused);
    return sendJson("PATCH", "/rest/config/devices/" + id, v, err);
}

bool Client::dismissPendingDevice(const std::string& id, std::string& err) {
    HttpResult r = call("DELETE", "/rest/cluster/pending/devices?device=" + id);
    if (!r.ok()) { err = errorOf(r); return false; }
    return true;
}
bool Client::dismissPendingFolder(const std::string& folderId, const std::string& deviceId, std::string& err) {
    HttpResult r = call("DELETE", "/rest/cluster/pending/folders?folder=" + folderId + (deviceId.empty() ? "" : "&device=" + deviceId));
    if (!r.ok()) { err = errorOf(r); return false; }
    return true;
}

bool Client::setOptions(const Options& o, const std::string& myID, std::string& err) {
    njson::Value v = njson::Value::makeObject();
    v.set("listenAddresses") = strArrayValue(o.listenAddresses);
    v.set("globalAnnounceEnabled") = njson::Value::makeBool(o.globalAnnounceEnabled);
    v.set("localAnnounceEnabled")  = njson::Value::makeBool(o.localAnnounceEnabled);
    v.set("relaysEnabled")         = njson::Value::makeBool(o.relaysEnabled);
    v.set("natEnabled")            = njson::Value::makeBool(o.natEnabled);
    v.set("maxSendKbps")           = njson::Value::makeNumber(o.maxSendKbps);
    v.set("maxRecvKbps")           = njson::Value::makeNumber(o.maxRecvKbps);
    v.set("limitBandwidthInLan")   = njson::Value::makeBool(o.limitBandwidthInLan);
    v.set("maxFolderConcurrency")  = njson::Value::makeNumber(o.maxFolderConcurrency);
    v.set("crashReportingEnabled") = njson::Value::makeBool(o.crashReportingEnabled);
    // Usage reporting: -1 declines, a positive value is the accepted report version (3 = current).
    v.set("urAccepted")            = njson::Value::makeNumber(o.urAccepted ? 3 : -1);
    njson::Value m = njson::Value::makeObject();
    m.set("value") = njson::Value::makeNumber(o.minHomeDiskFreePct); m.set("unit") = njson::Value::makeString("%");
    v.set("minHomeDiskFree") = m;
    if (!sendJson("PATCH", "/rest/config/options", v, err)) return false;
    if (!myID.empty()) {
        njson::Value n = njson::Value::makeObject(); n.set("name") = njson::Value::makeString(o.deviceName);
        if (!sendJson("PATCH", "/rest/config/devices/" + myID, n, err)) return false;
    }
    return true;
}

bool Client::setGui(const GuiCfg& g, const std::string& newPassword, std::string& err) {
    njson::Value v = njson::Value::makeObject();
    v.set("address") = njson::Value::makeString(g.address);
    v.set("user")    = njson::Value::makeString(g.user);
    v.set("useTLS")  = njson::Value::makeBool(g.useTLS);
    if (!newPassword.empty()) v.set("password") = njson::Value::makeString(newPassword);   // the daemon hashes it
    return sendJson("PATCH", "/rest/config/gui", v, err);
}

bool Client::restart(std::string& err)  { return post("/rest/system/restart", err); }
bool Client::shutdown(std::string& err) { return post("/rest/system/shutdown", err); }

bool Client::fetchLog(std::vector<std::string>& lines, int maxLines, std::string& err) {
    njson::Value v;
    if (!getJson("/rest/system/log", v, err)) return false;
    lines.clear();
    if (const njson::Value* m = v.find("messages"); m && m->isArray()) {
        size_t start = m->arr.size() > (size_t)maxLines ? m->arr.size() - (size_t)maxLines : 0;
        for (size_t i = start; i < m->arr.size(); i++) {
            const njson::Value& e = m->arr[i];
            std::string when = e.getString("when");
            if (when.size() >= 19) when = when.substr(11, 8);   // HH:MM:SS from the RFC3339 stamp
            std::string msg = e.getString("message");
            size_t tail = msg.rfind(" (log.pkg=");           // structured fields the daemon appends
            if (tail != std::string::npos) msg.erase(tail);
            lines.push_back(when + "  " + msg);
        }
    }
    return true;
}

std::string Client::newFolderId() {
    // Same shape as the web GUI's generated IDs: xxxxx-xxxxx from a lower-case alphanumeric alphabet.
    static const char* alphabet = "abcdefghijkmnpqrstuvwxyz23456789";
    unsigned seed = (unsigned)time(nullptr) ^ (unsigned)getpid();
    std::string id;
    for (int i = 0; i < 11; i++) {
        if (i == 5) { id += '-'; continue; }
        seed = seed * 1103515245u + 12345u;
        id += alphabet[(seed >> 16) % 32];
    }
    return id;
}

bool Client::normaliseDeviceId(const std::string& in, std::string& out) {
    std::string s;
    for (char c : in) {
        if (c == '-' || c == ' ' || c == '\n' || c == '\r') continue;
        s += (char)toupper((unsigned char)c);
    }
    if (s.size() != 56) return false;
    for (char c : s) if (!((c >= 'A' && c <= 'Z') || (c >= '2' && c <= '7'))) return false;
    out.clear();
    for (size_t i = 0; i < 56; i += 7) { if (i) out += '-'; out += s.substr(i, 7); }
    return true;
}

// ---- formatting ---------------------------------------------------------------

std::string fmtBytes(int64_t b) {
    char buf[32];
    const double d = (double)b;
    if (b < 1024)                snprintf(buf, sizeof(buf), "%lld B", (long long)b);
    else if (b < 1024 * 1024)    snprintf(buf, sizeof(buf), "%.1f KB", d / 1024.0);
    else if (b < 1024LL * 1024 * 1024) snprintf(buf, sizeof(buf), "%.1f MB", d / (1024.0 * 1024.0));
    else                         snprintf(buf, sizeof(buf), "%.2f GB", d / (1024.0 * 1024.0 * 1024.0));
    return buf;
}

std::string fmtRate(int64_t bytesPerS) { return fmtBytes(bytesPerS) + "/s"; }

// RFC3339 with fractional seconds and a numeric zone or Z, e.g. 2026-09-17T00:05:57.123456+01:00.
std::string fmtAgo(const std::string& ts) {
    if (ts.size() < 19) return "never";
    struct tm tm; memset(&tm, 0, sizeof(tm));
    tm.tm_year = atoi(ts.substr(0, 4).c_str()) - 1900; tm.tm_mon = atoi(ts.substr(5, 2).c_str()) - 1;
    tm.tm_mday = atoi(ts.substr(8, 2).c_str()); tm.tm_hour = atoi(ts.substr(11, 2).c_str());
    tm.tm_min = atoi(ts.substr(14, 2).c_str()); tm.tm_sec = atoi(ts.substr(17, 2).c_str());
    if (tm.tm_year < 80) return "never";   // Syncthing's zero time (0001-01-01) means "not yet"
    time_t t = timegm(&tm);
    size_t z = ts.find_last_of("+-");
    if (z != std::string::npos && z >= 19 && ts.size() >= z + 6) {
        int oh = atoi(ts.substr(z + 1, 2).c_str()), om = atoi(ts.substr(z + 4, 2).c_str());
        int off = oh * 3600 + om * 60;
        t -= (ts[z] == '+') ? off : -off;
    }
    long d = (long)(time(nullptr) - t);
    char buf[40];
    if (d < 0) d = 0;
    if (d < 60)          snprintf(buf, sizeof(buf), "%ld s ago", d);
    else if (d < 3600)   snprintf(buf, sizeof(buf), "%ld min ago", d / 60);
    else if (d < 86400)  snprintf(buf, sizeof(buf), "%ld h ago", d / 3600);
    else                 snprintf(buf, sizeof(buf), "%ld d ago", d / 86400);
    return buf;
}

const char* folderTypeLabel(const std::string& t) {
    if (t == "sendonly")    return "Send Only";
    if (t == "receiveonly") return "Receive Only";
    if (t == "receiveencrypted") return "Receive Encrypted";
    return "Send & Receive";
}

// ---- folder paths ------------------------------------------------------------------------

static bool startsWith(const std::string& s, const char* pfx) { return s.rfind(pfx, 0) == 0; }

std::string canonicalFolderPath(const std::string& in) {
    // Collapse repeated slashes and drop a trailing one so the prefix tests below are exact.
    std::string p;
    for (char c : in) if (c != '/' || p.empty() || p.back() != '/') p += c;
    while (p.size() > 1 && p.back() == '/') p.pop_back();
    // /sdcard and /storage/self/primary are the primary user's internal storage.
    if (p == "/sdcard" || startsWith(p, "/sdcard/")) return "/data/media/0" + p.substr(7);
    if (p == "/storage/self/primary" || startsWith(p, "/storage/self/primary/")) return "/data/media/0" + p.substr(21);
    // /storage/emulated/<user>/... and the vold view /mnt/user/<user>/emulated/<user>/... .
    static const char* kEmu[] = { "/storage/emulated/", "/mnt/user/" };
    for (const char* root : kEmu) {
        if (!startsWith(p, root)) continue;
        std::string rest = p.substr(strlen(root));
        if (root[1] == 'm') { size_t e = rest.find("emulated/"); if (e == std::string::npos) return p; rest = rest.substr(e + 9); }
        size_t sl = rest.find('/');
        std::string user = rest.substr(0, sl);
        if (user.empty() || user.find_first_not_of("0123456789") != std::string::npos) return p;
        return "/data/media/" + user + (sl == std::string::npos ? "" : rest.substr(sl));
    }
    // /storage/<volume>/... is a removable card; vold's raw mount of it is /mnt/media_rw/<volume>.
    if (startsWith(p, "/storage/")) {
        std::string rest = p.substr(9);
        size_t sl = rest.find('/');
        std::string vol = rest.substr(0, sl);
        if (!vol.empty() && vol != "emulated" && vol != "self") return "/mnt/media_rw/" + rest;
    }
    return p;
}

bool isSupportedFolderPath(const std::string& path) {
    const std::string p = canonicalFolderPath(path);
    if (startsWith(p, "/data/media/")) {
        size_t sl = p.find('/', 12);
        std::string user = p.substr(12, sl == std::string::npos ? std::string::npos : sl - 12);
        return !user.empty() && user.find_first_not_of("0123456789") == std::string::npos;
    }
    return isRemovableFolderPath(p);
}

bool isRemovableFolderPath(const std::string& path) {
    const std::string p = canonicalFolderPath(path);
    return startsWith(p, "/mnt/media_rw/") && p.size() > 14 && p[14] != '/';
}

const char* folderStateLabel(const std::string& s) {
    if (s == "idle")            return "Up to Date";
    if (s == "scanning")        return "Scanning";
    if (s == "scan-waiting")    return "Waiting to Scan";
    if (s == "syncing")         return "Syncing";
    if (s == "sync-preparing")  return "Preparing to Sync";
    if (s == "sync-waiting")    return "Waiting to Sync";
    if (s == "cleaning")        return "Cleaning";
    if (s == "clean-waiting")   return "Waiting to Clean";
    if (s == "error")           return "Error";
    return "Unknown";
}

} // namespace nanost
} // namespace android
