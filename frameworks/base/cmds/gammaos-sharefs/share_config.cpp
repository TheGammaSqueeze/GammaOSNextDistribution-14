/*
 * Copyright (C) 2026 GammaOS
 *
 * Share configuration storage, shared by the daemon and the nano menu.
 *
 * Shares live in system properties rather than a config file. That is what the rest of GammaOS does
 * for settings shared between the nano menu and the Settings apps, and here it also means the
 * daemon, init, the nano menu, Settings and TvSettings all read exactly the same place with no
 * bridge in between: a property is reachable natively from all of them, whereas a file under
 * /data/system is awkward for the Java UIs and the "content"/"settings" tools are not usable from a
 * native daemon's SELinux domain.
 *
 *   persist.gammaos.share.<n>.name      display name, also the directory under /mnt/shares
 *   persist.gammaos.share.<n>.type      smb | nfs | webdav | ftp
 *   persist.gammaos.share.<n>.host      hostname or address
 *   persist.gammaos.share.<n>.port      optional, 0/unset = protocol default
 *   persist.gammaos.share.<n>.path      SMB share name, or remote directory for WebDAV/FTP/NFS
 *   persist.gammaos.share.<n>.user      empty = guest/anonymous
 *   persist.gammaos.share.<n>.pass      encrypted, see encryptSecret
 *   persist.gammaos.share.<n>.domain    SMB workgroup, optional
 *   persist.gammaos.share.<n>.tls       1 = https (WebDAV) / ftps (FTP)
 *   persist.gammaos.share.<n>.ro        1 = mount read-only
 *   persist.gammaos.share.<n>.enabled   1 = init should mount it
 */

#define LOG_TAG "gammaos-sharefs"

#include "share_config.h"

#include <fcntl.h>
#include <cutils/properties.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

namespace gammaos {
namespace sharefs {

ShareType shareTypeFromString(const std::string& s) {
    if (s == "smb" || s == "cifs") return ShareType::kSmb;
    if (s == "webdav" || s == "http" || s == "https") return ShareType::kWebdav;
    if (s == "ftp" || s == "ftps") return ShareType::kFtp;
    if (s == "nfs") return ShareType::kNfs;
    return ShareType::kUnknown;
}

const char* shareTypeName(ShareType t) {
    switch (t) {
        case ShareType::kSmb: return "SMB";
        case ShareType::kWebdav: return "WebDAV";
        case ShareType::kFtp: return "FTP";
        case ShareType::kNfs: return "NFS";
        default: return "unknown";
    }
}

const char* shareTypeKey(ShareType t) {
    switch (t) {
        case ShareType::kSmb: return "smb";
        case ShareType::kWebdav: return "webdav";
        case ShareType::kFtp: return "ftp";
        case ShareType::kNfs: return "nfs";
        default: return "";
    }
}

/*
 * Credential storage.
 *
 * Passwords are kept obfuscated rather than in clear text, keyed off a value that differs per
 * device. To be explicit about what this is worth: anyone with root on the device can recover them,
 * because the daemon itself has to be able to. It stops a share password being readable in a
 * property dump that gets copied off the device, pasted into a bug report, or read over adb, which
 * is the realistic exposure here. A stronger scheme would need a keystore-backed key, which this
 * native daemon cannot reach before the framework is up, and shares must mount at boot.
 *
 * GammaShareConfig.java implements exactly this; keep the two in step.
 */
namespace {

// The first 127 bytes of build.prop, or fewer if it is shorter. Read in a loop rather than with a
// single read(): a short read would silently produce a different key, and GammaShareConfig.java has
// to derive byte-for-byte the same one or passwords written by Settings decrypt to rubbish here.
constexpr size_t kKeyFileBytes = 127;

std::string deviceKey() {
    // The key has to be derivable before the framework is up (shares mount at boot) and stable
    // across reboots, which rules out anything random or keystore-backed. The head of build.prop
    // satisfies both and differs between builds/devices.
    std::string seed = "gammaos-sharefs";
    char buf[kKeyFileBytes];
    int fd = open("/system/build.prop", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        size_t got = 0;
        while (got < sizeof(buf)) {
            ssize_t n = read(fd, buf + got, sizeof(buf) - got);
            if (n <= 0) break;      // EOF, or an error we cannot do anything useful about
            got += static_cast<size_t>(n);
        }
        close(fd);
        seed.append(buf, got);
    }
    return seed;
}

std::string shareProp(int idx, const char* field, const char* def = "") {
    char key[128];
    snprintf(key, sizeof(key), "persist.gammaos.share.%d.%s", idx, field);
    char val[PROPERTY_VALUE_MAX] = {};
    property_get(key, val, def);
    return val;
}

void setShareProp(int idx, const char* field, const std::string& val) {
    char key[128];
    snprintf(key, sizeof(key), "persist.gammaos.share.%d.%s", idx, field);
    property_set(key, val.c_str());
}

}  // namespace

std::string encryptSecret(const std::string& plain) {
    const std::string key = deviceKey();
    std::string out;
    static const char* hex = "0123456789abcdef";
    for (size_t i = 0; i < plain.size(); i++) {
        unsigned char c = static_cast<unsigned char>(plain[i]) ^
                          static_cast<unsigned char>(key[i % key.size()]);
        out.push_back(hex[c >> 4]);
        out.push_back(hex[c & 0xF]);
    }
    return out;
}

std::string decryptSecret(const std::string& stored) {
    const std::string key = deviceKey();
    std::string out;
    for (size_t i = 0; i + 1 < stored.size(); i += 2) {
        unsigned char c =
                static_cast<unsigned char>(strtol(stored.substr(i, 2).c_str(), nullptr, 16));
        out.push_back(static_cast<char>(c ^ static_cast<unsigned char>(key[(i / 2) % key.size()])));
    }
    return out;
}

bool loadShare(int slot, ShareConfig* out) {
    if (slot < 1 || slot > kMaxShares) return false;
    ShareConfig c;
    c.slot = slot;
    c.name = shareProp(slot, "name");
    if (c.name.empty()) return false;
    c.type = shareTypeFromString(shareProp(slot, "type"));
    c.host = shareProp(slot, "host");
    c.path = shareProp(slot, "path");
    c.user = shareProp(slot, "user");
    c.domain = shareProp(slot, "domain");
    c.password = decryptSecret(shareProp(slot, "pass"));
    c.port = atoi(shareProp(slot, "port", "0").c_str());
    c.readOnly = (shareProp(slot, "ro", "0") == "1");
    c.useTls = (shareProp(slot, "tls", "0") == "1");
    c.enabled = (shareProp(slot, "enabled", "0") == "1");
    *out = std::move(c);
    return true;
}

std::vector<ShareConfig> loadShares() {
    std::vector<ShareConfig> out;
    for (int i = 1; i <= kMaxShares; i++) {
        ShareConfig c;
        if (loadShare(i, &c)) out.push_back(std::move(c));
    }
    return out;
}

void saveShare(const ShareConfig& cfg) {
    if (cfg.slot < 1 || cfg.slot > kMaxShares) return;
    setShareProp(cfg.slot, "name", cfg.name);
    setShareProp(cfg.slot, "type", shareTypeKey(cfg.type));
    setShareProp(cfg.slot, "host", cfg.host);
    setShareProp(cfg.slot, "path", cfg.path);
    setShareProp(cfg.slot, "user", cfg.user);
    setShareProp(cfg.slot, "domain", cfg.domain);
    setShareProp(cfg.slot, "pass", encryptSecret(cfg.password));
    setShareProp(cfg.slot, "port", cfg.port > 0 ? std::to_string(cfg.port) : "0");
    setShareProp(cfg.slot, "ro", cfg.readOnly ? "1" : "0");
    setShareProp(cfg.slot, "tls", cfg.useTls ? "1" : "0");
    // enabled is written by setShareEnabled, which is also what starts and stops the mount, so a
    // plain edit of an already-running share does not silently restart it under the user.
}

void setShareEnabled(int slot, bool enabled) {
    if (slot < 1 || slot > kMaxShares) return;
    // The property is the whole mechanism: init has an "on property:...enabled=1" trigger per slot
    // that starts or stops the service. Nothing here needs permission to control services.
    setShareProp(slot, "enabled", enabled ? "1" : "0");
}

void deleteShare(int slot) {
    if (slot < 1 || slot > kMaxShares) return;
    setShareEnabled(slot, false);   // stop the mount before the config it needs disappears
    static const char* kFields[] = {"name", "type", "host", "path", "user",
                                    "domain", "pass", "port", "ro", "tls"};
    for (const char* f : kFields) setShareProp(slot, f, "");
}

int firstFreeSlot() {
    for (int i = 1; i <= kMaxShares; i++) {
        if (shareProp(i, "name").empty()) return i;
    }
    return 0;
}

bool isShareMounted(const std::string& name) {
    if (name.empty()) return false;
    const std::string want = "/mnt/shares/" + name;
    FILE* f = fopen("/proc/self/mountinfo", "re");
    if (!f) return false;
    char line[1024];
    bool found = false;
    while (!found && fgets(line, sizeof(line), f)) {
        // Field 5 (1-based) is the mount point.
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
        if (want.size() == static_cast<size_t>(end - mp) &&
            !memcmp(want.data(), mp, want.size())) {
            found = true;
        }
    }
    fclose(f);
    return found;
}

std::string shareProblem(const ShareConfig& cfg) {
    if (cfg.name.empty()) return "Give the share a name";
    // The name becomes a directory under /mnt/shares, so it has to be a usable one.
    if (cfg.name.find('/') != std::string::npos || cfg.name == "." || cfg.name == "..")
        return "The name cannot contain a slash";
    if (cfg.type == ShareType::kUnknown) return "Choose a share type";
    if (cfg.host.empty()) return "Enter the server address";
    if (cfg.type == ShareType::kSmb && cfg.path.empty())
        return "Enter the share name on the server";
    if (cfg.type == ShareType::kNfs && cfg.path.empty())
        return "Enter the exported path on the server";
    return std::string();
}

}  // namespace sharefs
}  // namespace gammaos
