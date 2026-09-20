#define LOG_TAG "DrasticNano"

#include "DrasticAssets.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <string>
#include <vector>

#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {
namespace drastic_assets {

namespace {

bool isDir(const std::string& p) { struct stat st; return lstat(p.c_str(), &st) == 0 && S_ISDIR(st.st_mode); }
bool isLink(const std::string& p) { struct stat st; return lstat(p.c_str(), &st) == 0 && S_ISLNK(st.st_mode); }
bool isFile(const std::string& p) { struct stat st; return stat(p.c_str(), &st) == 0 && S_ISREG(st.st_mode); }
bool pathExists(const std::string& p) { struct stat st; return lstat(p.c_str(), &st) == 0; }

bool mkdirs(const std::string& p, mode_t mode = 0775) {
    if (isDir(p)) return true;
    size_t pos = 1;
    while ((pos = p.find('/', pos)) != std::string::npos) {
        std::string part = p.substr(0, pos++);
        if (!isDir(part) && mkdir(part.c_str(), mode) != 0 && errno != EEXIST) return false;
    }
    if (mkdir(p.c_str(), mode) != 0 && errno != EEXIST) return false;
    return isDir(p);
}

std::string readLinkTarget(const std::string& p) {
    char buf[PATH_MAX];
    ssize_t n = readlink(p.c_str(), buf, sizeof(buf) - 1);
    if (n <= 0) return {};
    buf[n] = 0;
    return buf;
}

// Copy a regular file (whole, then rename into place).
bool copyFile(const std::string& src, const std::string& dst, mode_t mode = 0664) {
    int in = open(src.c_str(), O_RDONLY | O_CLOEXEC);
    if (in < 0) return false;
    std::string tmp = dst + ".tmp";
    int out = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, mode);
    if (out < 0) { close(in); return false; }
    std::vector<char> buf(1 << 16);
    bool ok = true;
    for (;;) {
        ssize_t n = read(in, buf.data(), buf.size());
        if (n == 0) break;
        if (n < 0) { if (errno == EINTR) continue; ok = false; break; }
        ssize_t off = 0;
        while (off < n) {
            ssize_t w = write(out, buf.data() + off, (size_t)(n - off));
            if (w < 0) { if (errno == EINTR) continue; ok = false; break; }
            off += w;
        }
        if (!ok) break;
    }
    close(in);
    if (ok && fsync(out) != 0) ok = false;
    close(out);
    if (ok && rename(tmp.c_str(), dst.c_str()) != 0) ok = false;
    if (!ok) unlink(tmp.c_str());
    return ok;
}

// Remove a path: a symlink or file directly, a directory recursively.
void removeTree(const std::string& p) {
    struct stat st;
    if (lstat(p.c_str(), &st) != 0) return;
    if (S_ISDIR(st.st_mode)) {
        if (DIR* d = opendir(p.c_str())) {
            struct dirent* e;
            while ((e = readdir(d)) != nullptr) {
                if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
                removeTree(p + "/" + e->d_name);
            }
            closedir(d);
        }
        rmdir(p.c_str());
    } else {
        unlink(p.c_str());
    }
}

// Copy a directory tree (files copied, subdirs recursed, symlinks followed).
void copyTree(const std::string& src, const std::string& dst) {
    if (!mkdirs(dst)) return;
    DIR* d = opendir(src.c_str());
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
        std::string s = src + "/" + e->d_name, t = dst + "/" + e->d_name;
        if (isDir(s) || (isLink(s) && isDir(readLinkTarget(s)))) { removeTree(t); copyTree(s, t); }
        else { removeTree(t); copyFile(s, t); }
    }
    closedir(d);
}

// Make `linkPath` a symlink to `target` (replacing whatever is there).
void ensureLink(const std::string& linkPath, const std::string& target) {
    if (isLink(linkPath) && readLinkTarget(linkPath) == target) return;
    removeTree(linkPath);
    if (symlink(target.c_str(), linkPath.c_str()) != 0)
        ALOGE("drastic-nano assets: symlink %s -> %s failed: %s", linkPath.c_str(), target.c_str(), strerror(errno));
}

int countWithExt(const std::string& dir, const char* ext) {
    DIR* d = opendir(dir.c_str());
    if (!d) return 0;
    int n = 0;
    const size_t el = strlen(ext);
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        size_t l = strlen(e->d_name);
        if (l > el && strcasecmp(e->d_name + l - el, ext) == 0) n++;
    }
    closedir(d);
    return n;
}

std::string seedStamp() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("ro.build.date.utc", v, "0");
    return v;
}

std::string readSmall(const std::string& p) {
    int fd = open(p.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return {};
    char buf[256];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return {};
    buf[n] = 0;
    return buf;
}

} // namespace

std::string systemDir() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.drastic_nano.sysdir", v, "");
    if (v[0] == '/' && isDir(v)) return v;
    return kSystemDirDefault;
}

std::string systemLibDir() {
    std::string d = systemDir() + "/lib";           // developer override tree
    if (systemDir() != kSystemDirDefault && isFile(d + "/libdrastic_arm64.so")) return d;
    d = "/system/lib64";
    return isFile(d + "/libdrastic_arm64.so") ? d : std::string();
}

void mergeShaders(const std::string& root) {
    const std::string dst = root + "/shaders";
    const std::string sys = systemDir() + "/shaders";
    const std::string usr = std::string(kUserDir) + "/shaders";
    // The merged dir is entirely ours: rebuild it from scratch.
    removeTree(dst);
    mkdirs(dst);
    int nSys = 0, nUsr = 0;
    if (DIR* d = opendir(sys.c_str())) {
        struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
            ensureLink(dst + "/" + e->d_name, sys + "/" + e->d_name);
            nSys++;
        }
        closedir(d);
    }
    // User shaders override a same-named default and add new ones. Copied
    // rather than linked so the shader compile never reads through FUSE.
    if (DIR* d = opendir(usr.c_str())) {
        struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
            std::string s = usr + "/" + e->d_name, t = dst + "/" + e->d_name;
            removeTree(t);
            if (isDir(s)) copyTree(s, t); else copyFile(s, t);
            nUsr++;
        }
        closedir(d);
    }
    ALOGI("drastic-nano assets: shaders merged (%d system, %d user) into %s", nSys, nUsr, dst.c_str());
}

bool seedRoot(const std::string& root) {
    const std::string sysDir = systemDir();
    if (!isDir(sysDir)) {
        ALOGE("drastic-nano assets: %s missing from the image", sysDir.c_str());
        return false;
    }
    static const char* kSubdirs[] = {
        "system", "config", "cheats", "slot2", "microphone", "input_record",
        "unzip_cache", "users", "backgrounds", "scripts", "virtual_controller", nullptr,
    };
    if (!mkdirs(root)) {
        ALOGE("drastic-nano assets: cannot create %s: %s", root.c_str(), strerror(errno));
        return false;
    }
    for (int i = 0; kSubdirs[i]; i++) mkdirs(root + "/" + kSubdirs[i]);

    // User data on shared storage. FUSE is up by the time a game launches; if
    // it is not (very early boot), the symlinks below still point at the right
    // place and resolve once it mounts.
    const std::string saves  = std::string(kUserDir) + "/saves";
    const std::string states = std::string(kUserDir) + "/savestates";
    mkdirs(std::string(kUserDir));
    mkdirs(saves);
    mkdirs(states);
    mkdirs(std::string(kUserDir) + "/shaders");
    // A real backup/ or savestates/ directory left from an older layout: carry
    // its files over before replacing it with the link.
    for (const auto& pr : { std::make_pair(root + "/backup", saves), std::make_pair(root + "/savestates", states) }) {
        if (isDir(pr.first) && !isLink(pr.first)) {
            if (DIR* d = opendir(pr.first.c_str())) {
                struct dirent* e;
                while ((e = readdir(d)) != nullptr) {
                    if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
                    std::string s = pr.first + "/" + e->d_name, t = pr.second + "/" + e->d_name;
                    if (!pathExists(t) && copyFile(s, t)) unlink(s.c_str());
                }
                closedir(d);
            }
        }
        ensureLink(pr.first, pr.second);
    }

    // Read-only databases: linked (13.7 MB of cheats never copied).
    ensureLink(root + "/game_database.xml", systemDir() + "/game_database.xml");
    ensureLink(root + "/usrcheat.dat",      systemDir() + "/usrcheat.dat");

    // BIOS, firmware and the default layout: copied so libdrastic may open them
    // for writing (the firmware carries the user settings). Refreshed when the
    // image changes, except the layout which the user may have edited.
    const std::string stampPath = root + "/.seed";
    const bool fresh = readSmall(stampPath) != seedStamp();
    static const char* kCopied[] = {
        "system/drastic_bios_arm7.bin", "system/drastic_bios_arm9.bin",
        "system/nds_firmware_modified.bin", nullptr,
    };
    for (int i = 0; kCopied[i]; i++) {
        std::string t = root + "/" + kCopied[i];
        if (fresh || !isFile(t)) {
            if (isLink(t)) unlink(t.c_str());
            if (!copyFile(systemDir() + "/" + kCopied[i], t))
                ALOGE("drastic-nano assets: copy %s failed: %s", kCopied[i], strerror(errno));
        }
    }
    if (!isFile(root + "/config/LC_default.dat"))
        copyFile(systemDir() + "/config/LC_default.dat", root + "/config/LC_default.dat");

    // Every launch: cheap (a few dozen links) and picks up shaders the user added.
    mergeShaders(root);

    if (fresh) {
        int fd = open(stampPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
        if (fd >= 0) { std::string s = seedStamp(); write(fd, s.c_str(), s.size()); close(fd); }
        ALOGI("drastic-nano assets: root %s seeded from %s", root.c_str(), sysDir.c_str());
    }
    return isFile(root + "/system/drastic_bios_arm7.bin") && isFile(root + "/system/drastic_bios_arm9.bin");
}

LegacyCount scanLegacy() {
    LegacyCount c;
    // DraStic writes raw .sav files by default and .dsv in its own format.
    c.saves  = countWithExt(std::string(kLegacyRoot) + "/backup", ".sav")
             + countWithExt(std::string(kLegacyRoot) + "/backup", ".dsv");
    c.states = countWithExt(std::string(kLegacyRoot) + "/savestates", ".dss");
    return c;
}

ImportResult importLegacy(const std::function<void(const char*, float)>& progress,
                          const std::string& excludeBase) {
    ImportResult r;
    struct Job { std::string src, dst; };
    std::vector<Job> jobs;
    auto collect = [&](const std::string& dir, const char* ext, const std::string& dstDir) {
        DIR* d = opendir(dir.c_str());
        if (!d) return;
        const size_t el = strlen(ext);
        struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            size_t l = strlen(e->d_name);
            if (l <= el || strcasecmp(e->d_name + l - el, ext) != 0) continue;
            // The running game's own files are left alone: it holds them open
            // and rewrites them at exit, which would clobber the import.
            if (!excludeBase.empty() && strncmp(e->d_name, excludeBase.c_str(), excludeBase.size()) == 0) {
                r.skipped++;
                continue;
            }
            jobs.push_back({dir + "/" + e->d_name, dstDir + "/" + e->d_name});
        }
        closedir(d);
    };
    collect(std::string(kLegacyRoot) + "/backup",     ".sav", std::string(kUserDir) + "/saves");
    collect(std::string(kLegacyRoot) + "/backup",     ".dsv", std::string(kUserDir) + "/saves");
    collect(std::string(kLegacyRoot) + "/savestates", ".dss", std::string(kUserDir) + "/savestates");
    mkdirs(std::string(kUserDir) + "/saves");
    mkdirs(std::string(kUserDir) + "/savestates");
    for (size_t i = 0; i < jobs.size(); i++) {
        if (progress) progress("Moving DraStic saves...", (float)i / (float)(jobs.size() ? jobs.size() : 1));
        // Never overwrite real data at the destination. An empty file there is
        // the placeholder a fresh session created before any save happened.
        struct stat dst;
        if (stat(jobs[i].dst.c_str(), &dst) == 0 && dst.st_size > 0) { r.skipped++; continue; }
        if (copyFile(jobs[i].src, jobs[i].dst)) { unlink(jobs[i].src.c_str()); r.moved++; }
        else { r.failed++; ALOGE("drastic-nano assets: import %s failed: %s", jobs[i].src.c_str(), strerror(errno)); }
    }
    if (progress) progress("Moving DraStic saves...", 1.0f);
    ALOGI("drastic-nano assets: legacy import moved=%d skipped=%d failed=%d", r.moved, r.skipped, r.failed);
    return r;
}

} // namespace drastic_assets
} // namespace android
