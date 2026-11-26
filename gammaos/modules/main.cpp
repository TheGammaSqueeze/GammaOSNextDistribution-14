/*
 * gammaos-modules: updates of single GammaOS programs without flashing a partition.
 *
 * A module is a signed zip (module.prop, module.sig, files/<path in the image>) that carries
 * newer builds of files the system image already has, for one program: the nano home or one
 * of the nano emulators. Installed modules live outside the image and are bind mounted over
 * the image's files at boot:
 *
 *   early store  /metadata/gammaos/modules   mounted by the first init stage; holds the files
 *                                            a module marks "early" (the home's program,
 *                                            which init starts long before /data is there)
 *   data store   /data/gammaos_modules       not under file based encryption, so it reads as
 *                                            soon as /data is mounted; holds everything else
 *
 * init runs "early" at early-init (before it starts any service), "late" first thing at
 * post-fs-data (before any service that could use a file from the data store), and
 * "confirm" once the boot has completed. A module is only mounted while it is newer than
 * what the image ships and built for the image's module platform, so a full system update
 * that brings a newer program wins by itself. A module that keeps the console from
 * finishing its boot is switched off after kMaxTries attempts. A module may also name a
 * property its program sets once it runs ("alive="): it is then only confirmed with that
 * property set, and when init sees the program stop without it, "reject" switches the module
 * off in the same boot and init starts the image's program instead.
 *
 * A module is for the system image it was made after: one that is not newer than what the
 * image ships (by version, or by build time) is superseded, never mounted and deleted. So a
 * system update always wins over the modules installed before it.
 *
 * No step makes the boot wait. The early store is small and the running home holds its own
 * file there, so a newer home goes straight into the early store when there is room beside
 * the old one, and otherwise waits in the data store until the "settle" step at shutdown,
 * when the home is stopped, puts it in place. Either way the next boot starts the home at
 * the usual time, and should a shutdown never get that far, the copy before keeps running.
 */

#define LOG_TAG "gammaos-modules"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <ftw.h>
#include <sched.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/xattr.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <map>
#include <set>
#include <string>
#include <vector>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <openssl/curve25519.h>
#include <openssl/sha.h>
#include <ziparchive/zip_archive.h>

#include "ModuleKeys.h"

using android::base::unique_fd;

namespace {

// Space the early store must keep free after a module file went in: vold keeps the
// keys of /data on the same partition.
constexpr uint64_t kEarlyReserve = 4ull << 20;
// Space /data must keep free after an install.
constexpr uint64_t kDataReserve = 32ull << 20;
// Boots a newly installed module gets to reach "boot completed".
constexpr int kMaxTries = 3;
constexpr size_t kMaxManifest = 256 * 1024;

struct Paths {
    std::string root;     // where the image's files are, "/" outside of tests
    std::string early;    // early store
    std::string data;     // data store
    std::string builtin;  // what the image ships: <name>.prop per program
    std::string state;    // which modules are installed and how their boots went
};
Paths gPaths;

std::string envOr(const char* name, const char* fallback) {
    const char* v = getenv(name);
    return (v && *v) ? v : fallback;
}

bool isDir(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

bool exists(const std::string& path) {
    struct stat st;
    return lstat(path.c_str(), &st) == 0;
}

bool makeDirs(const std::string& path, mode_t mode) {
    if (isDir(path)) return true;
    size_t slash = path.rfind('/');
    if (slash != std::string::npos && slash > 0 && !makeDirs(path.substr(0, slash), mode)) {
        return false;
    }
    return mkdir(path.c_str(), mode) == 0 || errno == EEXIST;
}

int removeOne(const char* path, const struct stat*, int, struct FTW*) {
    return remove(path);
}

void removeTree(const std::string& path) {
    if (!exists(path)) return;
    nftw(path.c_str(), removeOne, 16, FTW_DEPTH | FTW_PHYS);
}

std::string dirOf(const std::string& path) {
    size_t slash = path.rfind('/');
    return slash == std::string::npos ? "." : path.substr(0, slash);
}

void syncDir(const std::string& dir) {
    unique_fd fd(open(dir.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC));
    if (fd >= 0) fsync(fd);
}

// Whole file or nothing: written beside the destination, flushed, then renamed over it.
bool writeFileAtomic(const std::string& path, const std::string& content, mode_t mode) {
    std::string tmp = path + ".new";
    unique_fd fd(open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, mode));
    if (fd < 0) return false;
    if (!android::base::WriteStringToFd(content, fd) || fsync(fd) != 0) {
        unlink(tmp.c_str());
        return false;
    }
    fd.reset();
    if (rename(tmp.c_str(), path.c_str()) != 0) {
        unlink(tmp.c_str());
        return false;
    }
    syncDir(dirOf(path));
    return true;
}

bool sha256OfFile(const std::string& path, std::string* hex) {
    unique_fd fd(open(path.c_str(), O_RDONLY | O_CLOEXEC));
    if (fd < 0) return false;
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    std::vector<uint8_t> buf(1 << 20);
    for (;;) {
        ssize_t n = TEMP_FAILURE_RETRY(read(fd, buf.data(), buf.size()));
        if (n < 0) return false;
        if (n == 0) break;
        SHA256_Update(&ctx, buf.data(), n);
    }
    uint8_t digest[SHA256_DIGEST_LENGTH];
    SHA256_Final(digest, &ctx);
    hex->clear();
    for (uint8_t b : digest) android::base::StringAppendF(hex, "%02x", b);
    return true;
}

// The module's file takes the owner, mode and SELinux label of the image file it stands
// in for, so that the program starts in the same domain as before.
bool copyAttrs(const std::string& from, const std::string& to) {
    struct stat st;
    if (stat(from.c_str(), &st) != 0) return false;
    if (chown(to.c_str(), st.st_uid, st.st_gid) != 0) return false;
    if (chmod(to.c_str(), st.st_mode & 07777) != 0) return false;
    char label[256];
    ssize_t n = getxattr(from.c_str(), "security.selinux", label, sizeof(label));
    if (n > 0 && setxattr(to.c_str(), "security.selinux", label, n, 0) != 0) {
        PLOG(WARNING) << "label of " << to;
    }
    return true;
}

bool copyFile(const std::string& from, const std::string& to) {
    unique_fd in(open(from.c_str(), O_RDONLY | O_CLOEXEC));
    if (in < 0) return false;
    unique_fd out(open(to.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600));
    if (out < 0) return false;
    std::vector<char> buf(1 << 20);
    for (;;) {
        ssize_t n = TEMP_FAILURE_RETRY(read(in, buf.data(), buf.size()));
        if (n < 0) return false;
        if (n == 0) break;
        if (!android::base::WriteFully(out, buf.data(), n)) return false;
    }
    return fsync(out) == 0;
}

uint64_t freeBytes(const std::string& path) {
    struct statvfs vfs;
    if (statvfs(path.c_str(), &vfs) != 0) return 0;
    return static_cast<uint64_t>(vfs.f_bavail) * vfs.f_frsize;
}

bool sameFile(const std::string& a, const std::string& b) {
    struct stat sa, sb;
    if (stat(a.c_str(), &sa) != 0 || stat(b.c_str(), &sb) != 0) return false;
    return sa.st_dev == sb.st_dev && sa.st_ino == sb.st_ino;
}

// ---------------------------------------------------------------------------------------
// Versions

// "1.4.2" -> 10402. Three numbers below 100 each; anything else is no version (0).
uint64_t versionCode(const std::string& version) {
    std::vector<std::string> parts = android::base::Split(version, ".");
    if (parts.size() != 3) return 0;
    uint64_t code = 0;
    for (const std::string& part : parts) {
        unsigned n = 0;
        if (part.empty() || part.size() > 4 || !android::base::ParseUint(part, &n)) return 0;
        if (n > 99 && &part != &parts[0]) return 0;
        code = code * 100 + n;
    }
    return code;
}

// When the system image was built (seconds, UTC).
uint64_t imageBuilt() {
    std::string forced = envOr("GMOD_IMAGE_BUILT", "");
    if (!forced.empty()) return strtoull(forced.c_str(), nullptr, 10);
    return android::base::GetUintProperty<uint64_t>("ro.build.date.utc", 0);
}

int devicePlatform() {
    std::string forced = envOr("GMOD_PLATFORM", "");
    if (!forced.empty()) return atoi(forced.c_str());
    return android::base::GetIntProperty("ro.gammaos.modules.platform", 0);
}

// ---------------------------------------------------------------------------------------
// module.prop

struct FileEntry {
    std::string target;  // path in the image without the leading slash: system/bin/...
    uint64_t size = 0;
    std::string sha256;
    bool early = false;
};

struct Manifest {
    std::string name;
    std::string version;
    uint64_t code = 0;
    int platform = 0;
    uint64_t built = 0;  // when the package was made (seconds, UTC)
    std::string alive;  // property the program sets to 1 once it runs; empty if none
    std::vector<FileEntry> files;

    bool hasEarly() const {
        for (const FileEntry& f : files) {
            if (f.early) return true;
        }
        return false;
    }
    uint64_t bytes(bool earlyOnly) const {
        uint64_t total = 0;
        for (const FileEntry& f : files) {
            if (!earlyOnly || f.early) total += f.size;
        }
        return total;
    }
};

bool validName(const std::string& name) {
    if (name.empty() || name.size() > 48) return false;
    for (char c : name) {
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_')) {
            return false;
        }
    }
    return true;
}

// A module may only stand in for files below /system, named without tricks.
bool validTarget(const std::string& target) {
    if (!android::base::StartsWith(target, "system/") || target.size() > 200) return false;
    if (target.find("//") != std::string::npos || target.back() == '/') return false;
    for (const std::string& part : android::base::Split(target, "/")) {
        if (part == "." || part == "..") return false;
    }
    for (char c : target) {
        bool ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
                  c == '/' || c == '.' || c == '-' || c == '_' || c == '+' || c == '@';
        if (!ok) return false;
    }
    return true;
}

bool parseManifest(const std::string& text, Manifest* m, std::string* error) {
    *m = Manifest();
    std::set<std::string> seen;
    for (const std::string& raw : android::base::Split(text, "\n")) {
        std::string line = android::base::Trim(raw);
        if (line.empty() || line[0] == '#') continue;
        size_t eq = line.find('=');
        if (eq == std::string::npos) {
            *error = "module.prop: line without '=': " + line;
            return false;
        }
        std::string key = line.substr(0, eq), value = line.substr(eq + 1);
        if (key == "name") {
            m->name = value;
        } else if (key == "version") {
            m->version = value;
        } else if (key == "platform") {
            m->platform = atoi(value.c_str());
        } else if (key == "built") {
            m->built = strtoull(value.c_str(), nullptr, 10);
        } else if (key == "alive") {
            if (!android::base::StartsWith(value, "sys.gammaos.") || value.size() > 80) {
                *error = "module.prop: alive must name a sys.gammaos. property";
                return false;
            }
            m->alive = value;
        } else if (key == "file") {
            std::vector<std::string> f = android::base::Split(value, " ");
            FileEntry entry;
            if (f.size() < 3 || f.size() > 4 || !android::base::ParseUint(f[1], &entry.size)) {
                *error = "module.prop: bad file line: " + value;
                return false;
            }
            entry.target = f[0];
            entry.sha256 = f[2];
            if (f.size() == 4) {
                if (f[3] != "early") {
                    *error = "module.prop: unknown file flag: " + f[3];
                    return false;
                }
                entry.early = true;
            }
            if (!validTarget(entry.target) || entry.sha256.size() != 64) {
                *error = "module.prop: file not allowed: " + entry.target;
                return false;
            }
            if (!seen.insert(entry.target).second) {
                *error = "module.prop: file listed twice: " + entry.target;
                return false;
            }
            m->files.push_back(entry);
        }
        // Unknown keys are left for later versions of the format.
    }
    m->code = versionCode(m->version);
    if (!validName(m->name)) {
        *error = "module.prop: bad name";
        return false;
    }
    if (m->code == 0) {
        *error = "module.prop: version must be three numbers, as in 1.2.3";
        return false;
    }
    if (m->platform <= 0) {
        *error = "module.prop: no platform";
        return false;
    }
    if (m->built == 0) {
        *error = "module.prop: no build time";
        return false;
    }
    if (m->files.empty()) {
        *error = "module.prop: no files";
        return false;
    }
    return true;
}

bool loadManifest(const std::string& path, Manifest* m) {
    std::string text, error;
    if (!android::base::ReadFileToString(path, &text)) return false;
    if (!parseManifest(text, m, &error)) {
        LOG(ERROR) << path << ": " << error;
        return false;
    }
    return true;
}

// The stored module.prop of an installed module, from whichever store has it.
bool loadStoredManifest(const std::string& name, Manifest* m) {
    return loadManifest(gPaths.data + "/" + name + "/module.prop", m) ||
           loadManifest(gPaths.early + "/" + name + "/module.prop", m);
}

// What the image itself ships of a program: /system/etc/gammaos/modules/<name>.prop.
struct Builtin {
    std::string version;
    uint64_t code = 0;
};

bool loadBuiltin(const std::string& name, Builtin* b) {
    std::string text;
    if (!android::base::ReadFileToString(gPaths.builtin + "/" + name + ".prop", &text)) {
        return false;
    }
    for (const std::string& raw : android::base::Split(text, "\n")) {
        std::string line = android::base::Trim(raw);
        if (android::base::StartsWith(line, "version=")) b->version = line.substr(8);
    }
    b->code = versionCode(b->version);
    return b->code != 0;
}

// ---------------------------------------------------------------------------------------
// State: one line per installed module.
//   <name> <version> <platform> <built> <status> <tries> <placement>
// status     pending  installed, has not yet seen a completed boot
//            good     a boot completed with it
//            failed   switched off: kMaxTries boots in a row did not complete, or its
//                     program stopped before it ran
// placement  data     every file is in the data store
//            early    its early files are in the early store
//            move     its early files wait in the data store: the early store has no room
//                     for them beside the copy the running program holds. The shutdown
//                     step moves them over; until then the copy before stays in use.

struct Entry {
    std::string name;
    std::string version;
    uint64_t code = 0;
    int platform = 0;
    uint64_t built = 0;
    std::string status;
    int tries = 0;
    std::string placement;
};

std::vector<Entry> loadState() {
    std::vector<Entry> entries;
    std::string text;
    if (!android::base::ReadFileToString(gPaths.state, &text)) return entries;
    for (const std::string& raw : android::base::Split(text, "\n")) {
        std::vector<std::string> f = android::base::Split(android::base::Trim(raw), " ");
        if (f.size() != 7 || !validName(f[0])) continue;
        Entry e;
        e.name = f[0];
        e.version = f[1];
        e.code = versionCode(f[1]);
        e.platform = atoi(f[2].c_str());
        e.built = strtoull(f[3].c_str(), nullptr, 10);
        e.status = f[4];
        e.tries = atoi(f[5].c_str());
        e.placement = f[6];
        if (e.code == 0) continue;
        entries.push_back(e);
    }
    return entries;
}

bool saveState(const std::vector<Entry>& entries) {
    // No module, no state file: init only starts the boot steps when the file is there.
    if (entries.empty()) {
        if (unlink(gPaths.state.c_str()) != 0 && errno != ENOENT) return false;
        syncDir(dirOf(gPaths.state));
        return true;
    }
    std::string text;
    for (const Entry& e : entries) {
        android::base::StringAppendF(&text, "%s %s %d %llu %s %d %s\n", e.name.c_str(),
                                     e.version.c_str(), e.platform,
                                     static_cast<unsigned long long>(e.built), e.status.c_str(),
                                     e.tries, e.placement.c_str());
    }
    if (!makeDirs(dirOf(gPaths.state), 0700)) return false;
    if (!writeFileAtomic(gPaths.state, text, 0600)) {
        PLOG(ERROR) << "cannot write " << gPaths.state;
        return false;
    }
    return true;
}

Entry* findEntry(std::vector<Entry>& entries, const std::string& name) {
    for (Entry& e : entries) {
        if (e.name == name) return &e;
    }
    return nullptr;
}

// Why a build of a program has no place on this system image; empty when it has one.
// A module is for the image it was made after: it must be a higher version than the image
// ships, built for the image's module platform, and built later than the image. So every
// system update supersedes the modules made before it, whatever their version numbers say,
// and a console never runs a program older than its system software.
std::string supersededBy(const std::string& name, uint64_t code, int platform, uint64_t built) {
    Builtin builtin;
    if (!loadBuiltin(name, &builtin)) return "this system image does not ship " + name;
    if (platform != devicePlatform()) {
        return android::base::StringPrintf("built for module platform %d, this system image is %d",
                                           platform, devicePlatform());
    }
    if (code <= builtin.code) return "the system image ships " + name + " " + builtin.version;
    if (built <= imageBuilt()) return "the system image is newer than this build of " + name;
    return "";
}

bool eligible(const Entry& e, std::string* why) {
    *why = e.status == "failed" ? "switched off"
                                : supersededBy(e.name, e.code, e.platform, e.built);
    return why->empty();
}

// ---------------------------------------------------------------------------------------
// Mounts

struct Mount {
    std::string root;   // the mounted file, as a path inside its file system
    std::string point;  // where it is mounted
};

std::vector<Mount> readMounts() {
    std::vector<Mount> mounts;
    std::string text;
    if (!android::base::ReadFileToString("/proc/self/mountinfo", &text)) return mounts;
    for (const std::string& line : android::base::Split(text, "\n")) {
        std::vector<std::string> f = android::base::Split(line, " ");
        if (f.size() < 5) continue;
        mounts.push_back({f[3], f[4]});
    }
    return mounts;
}

std::string targetPath(const std::string& target) {
    return gPaths.root + target;
}

std::string storeFile(const std::string& store, const std::string& name,
                      const std::string& target) {
    return store + "/" + name + "/files/" + target;
}

// A bind mount of ours: a file from below some module's files/ put over an image file.
bool isModuleMount(const Mount& m) {
    return android::base::StartsWith(m.point, gPaths.root + "system/") &&
           m.root.find("/files/system/") != std::string::npos;
}

// The flags of our mounts, in one mount namespace. The stores' file systems are mounted
// nosuid, and under enforcing SELinux a program on a nosuid mount cannot be started in a
// domain of its own: init could not start the home or an emulator from there. The image's
// files are not nosuid, so neither are these mounts. Read-only on top: nothing writes a
// module's file through the image's path.
void setMountFlagsHere(const std::map<std::string, std::string>& desired) {
    std::map<std::string, bool> ours;   // what is on top at each path
    for (const Mount& m : readMounts()) ours[m.point] = isModuleMount(m);
    for (const auto& [point, source] : desired) {
        auto it = ours.find(point);
        if (it == ours.end() || !it->second) continue;
        if (mount(nullptr, point.c_str(), nullptr,
                  MS_BIND | MS_REMOUNT | MS_RDONLY | MS_NODEV | MS_NOATIME, nullptr) != 0) {
            PLOG(WARNING) << "mount flags of " << point;
        }
    }
}

// init keeps two mount namespaces: the first one, where the services started before apexd
// live (the home), and the default one for everything after (the emulators, the home's
// overlay). A mount made in one shows up in the other, but with the flags of the store's
// file system, not ours. So the flags are set in both. init holds each of them open, which
// is the only way to the default one early in the boot, while init itself is still in the
// first.
void settleMountFlags(const std::map<std::string, std::string>& desired) {
    if (desired.empty()) return;
    setMountFlagsHere(desired);
    struct stat self;
    if (stat("/proc/self/ns/mnt", &self) != 0) return;

    std::vector<std::string> handles = {"/proc/1/ns/mnt"};
    if (DIR* dir = opendir("/proc/1/fd")) {
        while (struct dirent* d = readdir(dir)) {
            std::string path = std::string("/proc/1/fd/") + d->d_name;
            char link[64];
            ssize_t n = readlink(path.c_str(), link, sizeof(link) - 1);
            if (n > 5 && strncmp(link, "mnt:[", 5) == 0) handles.push_back(path);
        }
        closedir(dir);
    }

    std::set<ino_t> done = {self.st_ino};
    for (const std::string& path : handles) {
        unique_fd ns(open(path.c_str(), O_RDONLY | O_CLOEXEC));
        struct stat st;
        if (ns < 0 || fstat(ns, &st) != 0 || !done.insert(st.st_ino).second) continue;
        pid_t child = fork();
        if (child == 0) {
            if (setns(ns, CLONE_NEWNS) != 0) {
                PLOG(WARNING) << "cannot enter " << path;
                _exit(1);
            }
            setMountFlagsHere(desired);
            _exit(0);
        }
        if (child > 0) TEMP_FAILURE_RETRY(waitpid(child, nullptr, 0));
    }
}

// Makes the mounts over the image's files match "desired" (image file -> module file).
// Mounts of ours that are no longer wanted go away when unmountOthers is set. The paths in
// "leave" are not touched either way.
bool syncMounts(const std::map<std::string, std::string>& desired, bool unmountOthers,
                const std::set<std::string>& leave) {
    bool ok = true;
    for (int pass = 0; unmountOthers && pass < 4; ++pass) {
        bool changed = false;
        for (const Mount& m : readMounts()) {
            if (!isModuleMount(m) || leave.count(m.point)) continue;
            auto want = desired.find(m.point);
            if (want != desired.end() && sameFile(m.point, want->second)) continue;
            if (umount2(m.point.c_str(), MNT_DETACH) == 0) {
                LOG(INFO) << "unmounted " << m.point;
                changed = true;
            } else {
                PLOG(ERROR) << "cannot unmount " << m.point;
                ok = false;
            }
        }
        if (!changed) break;
    }
    for (const auto& [point, source] : desired) {
        if (leave.count(point) || sameFile(point, source)) continue;
        if (mount(source.c_str(), point.c_str(), nullptr, MS_BIND, nullptr) != 0) {
            PLOG(ERROR) << "cannot mount " << source << " on " << point;
            ok = false;
            continue;
        }
        // The new mount would otherwise be a peer of the store's own file system, and a
        // later mount on top of it would show up inside the store as well.
        mount(nullptr, point.c_str(), nullptr, MS_PRIVATE, nullptr);
    }
    settleMountFlags(desired);
    return ok;
}

bool checkFile(const std::string& source, const std::string& point, const FileEntry& f) {
    struct stat st;
    if (stat(source.c_str(), &st) != 0 || !S_ISREG(st.st_mode) ||
        static_cast<uint64_t>(st.st_size) != f.size) {
        LOG(ERROR) << source << " is missing or has the wrong size";
        return false;
    }
    if (lstat(point.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) {
        LOG(ERROR) << "the image has no file " << point;
        return false;
    }
    return true;
}

// The mounts of what the early store holds of a program. With a version, that version must
// be the one held. Without (code 0) whatever is held is taken as long as it still has a place
// on this image: the copy before, while a newer one waits in the data store.
bool collectEarly(const std::string& name, uint64_t code, std::map<std::string, std::string>* out,
                  Manifest* held) {
    Manifest m;
    if (!loadManifest(gPaths.early + "/" + name + "/module.prop", &m) || m.name != name) {
        return false;
    }
    if (code != 0 && m.code != code) {
        LOG(ERROR) << name << ": the early store holds version " << m.version;
        return false;
    }
    if (code == 0 && !supersededBy(name, m.code, m.platform, m.built).empty()) return false;
    std::map<std::string, std::string> mine;
    for (const FileEntry& f : m.files) {
        if (!f.early) continue;
        std::string source = storeFile(gPaths.early, name, f.target);
        if (!checkFile(source, targetPath(f.target), f)) return false;
        mine[targetPath(f.target)] = source;
    }
    out->insert(mine.begin(), mine.end());
    if (held) *held = m;
    return true;
}

// The mounts of a module's files in the data store (the ones not marked early).
bool collectData(const Entry& e, std::map<std::string, std::string>* out, Manifest* stored) {
    Manifest m;
    if (!loadManifest(gPaths.data + "/" + e.name + "/module.prop", &m) || m.name != e.name ||
        m.code != e.code) {
        return false;
    }
    std::map<std::string, std::string> mine;
    for (const FileEntry& f : m.files) {
        if (f.early) continue;
        std::string source = storeFile(gPaths.data, e.name, f.target);
        if (!checkFile(source, targetPath(f.target), f)) return false;
        mine[targetPath(f.target)] = source;
    }
    out->insert(mine.begin(), mine.end());
    if (stored) *stored = m;
    return true;
}

// Every image path covered by a stored module that has an early file. Such a module only
// changes hands at boot or at shutdown, all of its files together: the program that runs
// from it must not find another build under its name, or beside it, when it is started
// again in the same session.
std::set<std::string> rebootPoints(const std::vector<Entry>& entries) {
    std::set<std::string> points;
    for (const Entry& e : entries) {
        for (const std::string& store : {gPaths.early, gPaths.data}) {
            Manifest m;
            if (!loadManifest(store + "/" + e.name + "/module.prop", &m) || !m.hasEarly()) continue;
            for (const FileEntry& f : m.files) points.insert(targetPath(f.target));
        }
    }
    return points;
}

// Copies a module's early files from the data store into the early store and puts them in
// place of the copy there. The caller made sure of the room.
bool moveToEarlyStore(const Entry& e) {
    Manifest m;
    std::string dataDir = gPaths.data + "/" + e.name;
    if (!loadManifest(dataDir + "/module.prop", &m) || m.code != e.code) return false;
    std::string earlyDir = gPaths.early + "/" + e.name;
    std::string staging = gPaths.early + "/.staging-" + e.name;
    removeTree(staging);
    if (!makeDirs(gPaths.early, 0700)) return false;
    if (freeBytes(gPaths.early) < m.bytes(true) + kEarlyReserve) return false;
    bool ok = true;
    for (const FileEntry& f : m.files) {
        if (!f.early) continue;
        std::string from = storeFile(gPaths.data, e.name, f.target);
        std::string to = staging + "/files/" + f.target;
        std::string hash;
        ok = makeDirs(dirOf(to), 0755) && copyFile(from, to) && sha256OfFile(to, &hash) &&
             hash == f.sha256 && copyAttrs(targetPath(f.target), to);
        if (!ok) {
            LOG(ERROR) << e.name << ": copying " << f.target << " to the early store failed";
            break;
        }
    }
    std::string prop;
    ok = ok && android::base::ReadFileToString(dataDir + "/module.prop", &prop) &&
         writeFileAtomic(staging + "/module.prop", prop, 0644);
    // (an unsigned package, allowed by persist.gammaos.modules.allow_unsigned, has no signature)
    std::string sig;
    if (ok && android::base::ReadFileToString(dataDir + "/module.sig", &sig))
        ok = writeFileAtomic(staging + "/module.sig", sig, 0644);
    if (ok) {
        // An unlinked file stays readable for whoever has it mounted or running.
        removeTree(earlyDir);
        ok = rename(staging.c_str(), earlyDir.c_str()) == 0;
    }
    if (!ok) {
        removeTree(staging);
        return false;
    }
    syncDir(gPaths.early);
    for (const FileEntry& f : m.files) {
        if (f.early) unlink(storeFile(gPaths.data, e.name, f.target).c_str());
    }
    LOG(INFO) << e.name << " " << e.version << ": early files are in the early store";
    return true;
}

uint64_t treeBytes(const std::string& dir) {
    uint64_t total = 0;
    DIR* d = opendir(dir.c_str());
    if (!d) return 0;
    while (struct dirent* entry = readdir(d)) {
        std::string name = entry->d_name;
        if (name == "." || name == "..") continue;
        std::string path = dir + "/" + name;
        struct stat st;
        if (lstat(path.c_str(), &st) != 0) continue;
        total += S_ISDIR(st.st_mode) ? treeBytes(path) : static_cast<uint64_t>(st.st_size);
    }
    closedir(d);
    return total;
}

void publishVersions(const std::vector<Entry>& entries, const std::set<std::string>& applied) {
    for (const Entry& e : entries) {
        std::string prop = "sys.gammaos.module." + e.name;
        android::base::SetProperty(prop, applied.count(e.name) ? e.version : "");
    }
}

bool hasAlive(const std::string& propFile) {
    Manifest m;
    return loadManifest(propFile, &m) && !m.alive.empty();
}

// ---------------------------------------------------------------------------------------
// Boot steps. None of them waits for anything: the home starts when it always did.

// early-init: counts the boot attempt of modules not yet confirmed and mounts what the early
// store holds. Never reads /data.
int cmdEarly() {
    if (!exists(gPaths.state)) return 0;
    std::vector<Entry> entries = loadState();
    bool dirty = false;
    for (Entry& e : entries) {
        // A version that still waits for the shutdown step has not been started yet.
        if (e.status != "pending" || e.placement == "move") continue;
        dirty = true;
        if (++e.tries > kMaxTries) {
            e.status = "failed";
            LOG(ERROR) << e.name << " " << e.version << ": " << kMaxTries
                       << " boots did not complete with it, switched off";
        }
    }
    if (dirty) saveState(entries);

    std::map<std::string, std::string> desired;
    bool unproven = false;
    for (const Entry& e : entries) {
        std::string why;
        if (!eligible(e, &why)) {
            LOG(INFO) << e.name << " " << e.version << " not used: " << why;
            continue;
        }
        if (e.placement == "early") {
            if (!collectEarly(e.name, e.code, &desired, nullptr)) {
                LOG(ERROR) << e.name << ": early files not usable, the image's are used";
            } else if (e.status == "pending" &&
                       hasAlive(gPaths.early + "/" + e.name + "/module.prop")) {
                unproven = true;
            }
        } else if (e.placement == "move") {
            collectEarly(e.name, 0, &desired, nullptr);
        }
    }
    if (unproven) android::base::SetProperty("sys.gammaos.modules.unproven", "1");
    return syncMounts(desired, false, {}) ? 0 : 1;
}

enum class Apply {
    kBoot,    // the late step of a boot
    kLive,    // after an install or a removal on the running system
    kReject,  // a program stopped before it ran: its early files go as well
};

// Mounts everything that is installed and usable.
int applyAll(Apply mode) {
    std::vector<Entry> entries = loadState();
    std::map<std::string, std::string> desired;
    std::set<std::string> applied;
    bool settle = false;
    for (const Entry& e : entries) {
        std::string why;
        if (!eligible(e, &why)) continue;
        std::map<std::string, std::string> mine;
        Manifest stored;
        if (e.placement == "move") {
            // Its new version is used from the next boot on, once the shutdown step has
            // moved the early files over. Until then the copy before keeps its place.
            settle = true;
            collectEarly(e.name, 0, &desired, nullptr);
            continue;
        }
        bool ok = true;
        if (e.placement == "early") ok = collectEarly(e.name, e.code, &mine, &stored);
        // /data may have been wiped while the early store kept its files: a module that
        // lives entirely in the early store still works, any other is incomplete.
        bool allEarly = e.placement == "early" && ok && stored.bytes(true) == stored.bytes(false);
        ok = ok && (collectData(e, &mine, &stored) || allEarly);
        if (!ok) {
            LOG(ERROR) << e.name << " " << e.version << ": not usable, the image's files are used";
            continue;
        }
        desired.insert(mine.begin(), mine.end());
        applied.insert(e.name);
        if (mode == Apply::kBoot && e.status == "pending" && !stored.alive.empty()) {
            android::base::SetProperty("sys.gammaos.modules.unproven", "1");
        }
    }
    if (mode == Apply::kBoot && settle) android::base::SetProperty("sys.gammaos.modules.settle", "1");
    std::set<std::string> leave;
    if (mode == Apply::kLive) leave = rebootPoints(entries);
    bool ok = syncMounts(desired, true, leave);
    publishVersions(entries, applied);
    if (mode == Apply::kBoot) {
        for (const std::string& name : applied) {
            LOG(INFO) << name << " " << findEntry(entries, name)->version << " mounted";
        }
    }
    return ok ? 0 : 1;
}

// Shutdown, with the home stopped: a module whose early files wait in the data store gets
// them moved into the early store now, when nothing runs from the copy before any more, so
// that the next boot starts the new version at the usual time.
int cmdSettle() {
    std::vector<Entry> entries = loadState();
    bool dirty = false;
    for (Entry& e : entries) {
        std::string why;
        Manifest m;
        if (e.placement != "move" || !eligible(e, &why)) continue;
        if (!loadManifest(gPaths.data + "/" + e.name + "/module.prop", &m) || m.code != e.code) {
            continue;
        }
        std::set<std::string> points;
        for (const FileEntry& f : m.files) {
            if (f.early) points.insert(targetPath(f.target));
        }
        for (int pass = 0; pass < 4; ++pass) {
            for (const Mount& mnt : readMounts()) {
                if (isModuleMount(mnt) && points.count(mnt.point)) umount2(mnt.point.c_str(), MNT_DETACH);
            }
        }
        removeTree(gPaths.early + "/" + e.name);
        // The space comes back when the last process that ran from the old copy is gone.
        const uint64_t need = m.bytes(true) + kEarlyReserve;
        for (int i = 0; i < 60 && freeBytes(gPaths.early) < need; ++i) usleep(50 * 1000);
        if (moveToEarlyStore(e)) {
            e.placement = "early";
            dirty = true;
        } else {
            LOG(ERROR) << e.name << " " << e.version
                       << ": no room in the early store yet, tried again at the next shutdown";
        }
    }
    if (dirty) saveState(entries);
    android::base::SetProperty("sys.gammaos.modules.settle", "");
    sync();
    return 0;
}

// Boot completed: what was pending is good; what can never be used again is cleaned away.
int cmdConfirm() {
    if (!exists(gPaths.state)) return 0;
    std::vector<Entry> entries = loadState(), kept;
    bool dirty = false;
    for (Entry& e : entries) {
        Manifest m;
        bool proven = !loadStoredManifest(e.name, &m) || m.alive.empty() ||
                      android::base::GetProperty(m.alive, "") == "1";
        if (e.status == "pending" && proven && e.placement != "move") {
            e.status = "good";
            e.tries = 0;
            dirty = true;
            LOG(INFO) << e.name << " " << e.version << " confirmed";
        }
        std::string why;
        bool usable = eligible(e, &why);
        if (!usable) {
            if (e.status != "failed") {
                LOG(INFO) << e.name << " " << e.version << " removed: " << why;
            }
            removeTree(gPaths.data + "/" + e.name);
            removeTree(gPaths.early + "/" + e.name);
        }
        // A switched off module stays listed, files gone, so that the home can say so, as
        // long as it could still be told apart from what the image ships.
        bool tell = e.status == "failed" &&
                    supersededBy(e.name, e.code, e.platform, e.built).empty();
        if (usable || tell) kept.push_back(e);
    }
    if (dirty || kept.size() != entries.size()) saveState(kept);

    // Leftovers of interrupted installs and folders no line of the state knows.
    for (const std::string& store : {gPaths.data, gPaths.early}) {
        DIR* dir = opendir(store.c_str());
        if (!dir) continue;
        std::vector<std::string> stray;
        while (struct dirent* d = readdir(dir)) {
            std::string name = d->d_name;
            if (name == "." || name == ".." || d->d_type != DT_DIR) continue;
            Entry* e = findEntry(kept, name);
            bool wanted = e && e->status != "failed" &&
                          !(store == gPaths.early && e->placement == "data");
            if (!wanted) stray.push_back(store + "/" + name);
        }
        closedir(dir);
        for (const std::string& path : stray) removeTree(path);
    }
    return 0;
}

// A program that came as a module stopped before it ever reported that it runs: switch its
// module off now and take its mounts away, so that init can start the image's program in
// this same boot.
int cmdReject() {
    std::vector<Entry> entries = loadState();
    bool rejected = false;
    for (Entry& e : entries) {
        Manifest m;
        if (e.status != "pending" || !loadStoredManifest(e.name, &m) || m.alive.empty()) continue;
        if (android::base::GetProperty(m.alive, "") == "1") continue;
        e.status = "failed";
        rejected = true;
        LOG(ERROR) << e.name << " " << e.version << " stopped before it ran, switched off";
    }
    android::base::SetProperty("sys.gammaos.modules.unproven", "");
    if (!rejected) return 0;
    saveState(entries);
    int result = applyAll(Apply::kReject);
    // init restarts the program now, from the image's file (system/core/rootdir/init.rc).
    android::base::SetProperty("sys.gammaos.modules.rejected", "1");
    return result;
}

// ---------------------------------------------------------------------------------------
// Packages

bool readZipEntry(ZipArchiveHandle zip, const std::string& name, size_t limit,
                  std::string* out) {
    ZipEntry64 entry;
    if (FindEntry(zip, name, &entry) != 0 || entry.uncompressed_length > limit) return false;
    out->resize(entry.uncompressed_length);
    return ExtractToMemory(zip, &entry, reinterpret_cast<uint8_t*>(out->data()), out->size()) ==
           0;
}

bool signatureValid(const std::string& prop, const std::string& sig) {
    if (sig.size() != 64) return false;
    for (const auto& key : kModuleKeys) {
        if (ED25519_verify(reinterpret_cast<const uint8_t*>(prop.data()), prop.size(),
                           reinterpret_cast<const uint8_t*>(sig.data()), key) == 1) {
            return true;
        }
    }
    return false;
}

bool unsignedAllowed() {
    return envOr("GMOD_ALLOW_UNSIGNED", "") == "1" ||
           android::base::GetBoolProperty("persist.gammaos.modules.allow_unsigned", false);
}

struct Package {
    ZipArchiveHandle zip = nullptr;
    std::string prop;
    std::string sig;      // module.sig as it came, kept beside module.prop in the store
    Manifest manifest;
    bool isSigned = false;
    ~Package() {
        if (zip) CloseArchive(zip);
    }
};

bool openPackage(const std::string& path, Package* pkg, std::string* error) {
    if (OpenArchive(path.c_str(), &pkg->zip) != 0) {
        *error = "not a module package: " + path;
        return false;
    }
    std::string sig;
    if (!readZipEntry(pkg->zip, "module.prop", kMaxManifest, &pkg->prop)) {
        *error = "the package has no module.prop";
        return false;
    }
    if (readZipEntry(pkg->zip, "module.sig", 64, &sig)) {
        pkg->isSigned = signatureValid(pkg->prop, sig);
        if (!pkg->isSigned) {
            *error = "the package's signature is not valid";
            return false;
        }
        pkg->sig = sig;
    }
    return parseManifest(pkg->prop, &pkg->manifest, error);
}

// Whether the early files of a package fit into the early store right away (beside the
// copy the running program holds), only once that copy is gone, or not at all.
enum class EarlyRoom { kNow, kAtShutdown, kNever };

EarlyRoom earlyRoom(const Manifest& m) {
    if (!makeDirs(gPaths.early, 0700)) return EarlyRoom::kNever;
    const uint64_t need = m.bytes(true) + kEarlyReserve;
    const uint64_t room = freeBytes(gPaths.early);
    if (room >= need) return EarlyRoom::kNow;
    if (room + treeBytes(gPaths.early + "/" + m.name) >= need) return EarlyRoom::kAtShutdown;
    return EarlyRoom::kNever;
}

// Why this package cannot be installed here; empty when it can.
std::string installBlocker(const Package& pkg, const std::vector<Entry>& entries, bool force) {
    const Manifest& m = pkg.manifest;
    if (!pkg.isSigned && !unsignedAllowed()) return "the package is not signed";
    std::string superseded = supersededBy(m.name, m.code, m.platform, m.built);
    if (!superseded.empty()) return superseded;
    for (const Entry& e : entries) {
        if (e.name == m.name && e.status != "failed" && e.code >= m.code && !force) {
            return m.name + " " + e.version + " is installed already";
        }
    }
    for (const FileEntry& f : m.files) {
        struct stat st;
        if (lstat(targetPath(f.target).c_str(), &st) != 0 || !S_ISREG(st.st_mode)) {
            return "this system image has no file /" + f.target;
        }
    }
    // The home is never made to wait for /data, so its file has to fit beside vold's keys.
    if (m.hasEarly() && earlyRoom(m) == EarlyRoom::kNever) {
        return isDir(dirOf(dirOf(gPaths.early))) ? "there is not enough room in /metadata for " + m.name
                                                 : "this console has no /metadata for " + m.name;
    }
    return "";
}

int cmdInspect(const std::string& path) {
    Package pkg;
    std::string error;
    if (!openPackage(path, &pkg, &error)) {
        printf("ERROR\t%s\n", error.c_str());
        return 1;
    }
    std::vector<Entry> entries = loadState();
    std::string blocker = installBlocker(pkg, entries, false);
    const Manifest& m = pkg.manifest;
    printf("%s\t%s\t%d\t%llu\t%s\t%s\t%s\n", m.name.c_str(), m.version.c_str(), m.platform,
           static_cast<unsigned long long>(m.bytes(false)), pkg.isSigned ? "signed" : "unsigned",
           m.hasEarly() ? "reboot" : "live", blocker.empty() ? "ok" : blocker.c_str());
    return blocker.empty() ? 0 : 2;
}

int fail(const std::string& message) {
    printf("ERROR\t%s\n", message.c_str());
    LOG(ERROR) << message;
    return 1;
}

int cmdInstall(const std::string& path, bool force) {
    Package pkg;
    std::string error;
    if (!openPackage(path, &pkg, &error)) return fail(error);
    const Manifest& m = pkg.manifest;
    std::vector<Entry> entries = loadState();
    std::string blocker = installBlocker(pkg, entries, force);
    if (!blocker.empty()) return fail(blocker);

    if (!makeDirs(gPaths.data, 0755)) return fail("cannot create " + gPaths.data);
    if (freeBytes(gPaths.data) < m.bytes(false) + kDataReserve) {
        return fail("not enough free space in /data");
    }

    std::string staging = gPaths.data + "/.staging/" + m.name;
    removeTree(staging);
    for (const FileEntry& f : m.files) {
        ZipEntry64 entry;
        std::string to = staging + "/files/" + f.target;
        std::string hash;
        if (FindEntry(pkg.zip, "files/" + f.target, &entry) != 0 ||
            entry.uncompressed_length != f.size) {
            removeTree(staging);
            return fail("the package does not hold " + f.target + " as listed");
        }
        if (!makeDirs(dirOf(to), 0755)) return fail("cannot create " + dirOf(to));
        unique_fd fd(open(to.c_str(), O_RDWR | O_CREAT | O_TRUNC | O_CLOEXEC, 0600));
        bool ok = fd >= 0 && ExtractEntryToFile(pkg.zip, &entry, fd) == 0 && fsync(fd) == 0;
        fd.reset();
        ok = ok && sha256OfFile(to, &hash) && hash == f.sha256 &&
             copyAttrs(targetPath(f.target), to);
        if (!ok) {
            removeTree(staging);
            return fail("unpacking " + f.target + " failed or its checksum is wrong");
        }
    }
    if (!writeFileAtomic(staging + "/module.prop", pkg.prop, 0644)) {
        removeTree(staging);
        return fail("cannot write the module's module.prop");
    }
    // The signature stays with it: the home tells by the two whether a program is one GammaOS
    // built (gammaos-nano, NanoOfficial.cpp). Nothing here reads it again.
    if (pkg.isSigned && !writeFileAtomic(staging + "/module.sig", pkg.sig, 0644)) {
        removeTree(staging);
        return fail("cannot write the module's module.sig");
    }

    // Swap the unpacked module in. The state is written last: until then the old version,
    // or none, stays in effect, and a crash in between only leaves folders that "confirm"
    // or the next install removes.
    std::string live = gPaths.data + "/" + m.name;
    std::string old = gPaths.data + "/.old-" + m.name;
    removeTree(old);
    if (exists(live) && rename(live.c_str(), old.c_str()) != 0) {
        removeTree(staging);
        return fail("cannot replace the installed " + m.name);
    }
    if (rename(staging.c_str(), live.c_str()) != 0) {
        rename(old.c_str(), live.c_str());
        removeTree(staging);
        return fail("cannot move the unpacked module into place");
    }
    syncDir(gPaths.data);

    Entry fresh;
    fresh.name = m.name;
    fresh.version = m.version;
    fresh.code = m.code;
    fresh.platform = m.platform;
    fresh.built = m.built;
    fresh.status = "pending";
    fresh.placement = "data";
    if (m.hasEarly()) {
        // Into the early store at once when it fits beside the copy in use; else the
        // shutdown step does it.
        fresh.placement = (earlyRoom(m) == EarlyRoom::kNow && moveToEarlyStore(fresh)) ? "early"
                                                                                      : "move";
    }
    Entry* current = findEntry(entries, m.name);
    bool hadEarlyStore = current && (current->placement != "data");
    if (current) {
        *current = fresh;
    } else {
        entries.push_back(fresh);
    }
    if (!saveState(entries)) {
        // Without its state line nothing would use the new files; put the old ones back.
        removeTree(live);
        rename(old.c_str(), live.c_str());
        return fail("cannot record the install");
    }
    removeTree(old);
    rmdir((gPaths.data + "/.staging").c_str());
    sync();

    bool reboot = m.hasEarly() || hadEarlyStore;
    if (fresh.placement == "move") android::base::SetProperty("sys.gammaos.modules.settle", "1");
    applyAll(Apply::kLive);
    LOG(INFO) << m.name << " " << m.version << " installed";
    printf("OK\t%s\t%s\t%s\n", m.name.c_str(), m.version.c_str(), reboot ? "reboot" : "live");
    return 0;
}

int cmdRemove(const std::string& name) {
    std::vector<Entry> entries = loadState(), kept;
    bool found = false, reboot = false;
    for (const Entry& e : entries) {
        if (e.name != name) {
            kept.push_back(e);
            continue;
        }
        found = true;
        reboot = e.placement != "data" && e.status != "failed";
    }
    if (!found) return fail(name + " has no module installed");
    if (!saveState(kept)) return fail("cannot record the removal");
    removeTree(gPaths.data + "/" + name);
    // The early store's copy may be the program that is running; its space comes back once
    // nothing uses it, and the next boot no longer mounts it.
    removeTree(gPaths.early + "/" + name);
    applyAll(Apply::kLive);
    android::base::SetProperty("sys.gammaos.module." + name, "");
    printf("OK\t%s\t-\t%s\n", name.c_str(), reboot ? "reboot" : "live");
    return 0;
}

// One line per program the image ships:
//   name, version in the image, installed module version, status, placement, in use now
int cmdList() {
    std::vector<Entry> entries = loadState();
    std::vector<Mount> mounts = readMounts();
    std::vector<std::string> names;
    DIR* dir = opendir(gPaths.builtin.c_str());
    if (dir) {
        while (struct dirent* d = readdir(dir)) {
            std::string file = d->d_name;
            if (android::base::EndsWith(file, ".prop")) {
                names.push_back(file.substr(0, file.size() - 5));
            }
        }
        closedir(dir);
    }
    std::sort(names.begin(), names.end());
    for (const std::string& name : names) {
        Builtin builtin;
        if (!loadBuiltin(name, &builtin)) continue;
        Entry* e = findEntry(entries, name);
        bool active = false;
        for (const Mount& m : mounts) {
            if (isModuleMount(m) && m.root.find("/" + name + "/files/") != std::string::npos) {
                active = true;
            }
        }
        printf("%s\t%s\t%s\t%s\t%s\t%s\n", name.c_str(), builtin.version.c_str(),
               e ? e->version.c_str() : "-", e ? e->status.c_str() : "-",
               e ? e->placement.c_str() : "-", active ? "active" : "inactive");
    }
    return 0;
}

// Checks every stored file against its checksum.
int cmdVerify() {
    int bad = 0;
    for (const Entry& e : loadState()) {
        if (e.status == "failed") continue;
        Manifest m;
        std::string result = "ok";
        if (!loadManifest(gPaths.data + "/" + e.name + "/module.prop", &m)) {
            result = "no module.prop";
        }
        for (const FileEntry& f : m.files) {
            std::string hash;
            std::string inData = storeFile(gPaths.data, e.name, f.target);
            std::string inEarly = storeFile(gPaths.early, e.name, f.target);
            const std::string& file = (f.early && e.placement == "early") ? inEarly : inData;
            if (!sha256OfFile(file, &hash) || hash != f.sha256) result = "damaged: " + f.target;
        }
        if (result != "ok") bad++;
        printf("%s\t%s\t%s\n", e.name.c_str(), e.version.c_str(), result.c_str());
    }
    return bad ? 1 : 0;
}

int usage() {
    fprintf(stderr,
            "usage: gammaos-modules <command>\n"
            "  list                  programs, their image version and installed module\n"
            "  inspect <package>     what a package is and whether it can be installed\n"
            "  install <package>     install a module package (--force: also an older one)\n"
            "  remove <name>         go back to the program the system image ships\n"
            "  verify                check the installed files against their checksums\n"
            "  early | late | confirm | reject | settle   the steps init runs\n");
    return 64;
}

}  // namespace

int main(int argc, char** argv) {
    std::string command = argc > 1 ? argv[1] : "";
    bool bootStep = command == "early" || command == "late" || command == "confirm" ||
                    command == "reject" || command == "settle";
    struct timespec started;
    clock_gettime(CLOCK_MONOTONIC, &started);
    android::base::InitLogging(argv, bootStep ? &android::base::KernelLogger
                                              : &android::base::StderrLogger);

    gPaths.root = envOr("GMOD_ROOT", "/");
    if (gPaths.root.back() != '/') gPaths.root += '/';
    gPaths.early = envOr("GMOD_EARLY", "/metadata/gammaos/modules");
    gPaths.data = envOr("GMOD_DATA", "/data/gammaos_modules");
    gPaths.builtin = envOr("GMOD_BUILTIN", "/system/etc/gammaos/modules");
    // The state must be readable at early-init, so it sits in the early store. A console
    // without /metadata keeps it in /data and can only take modules without early files.
    gPaths.state = (isDir("/metadata") || getenv("GMOD_EARLY") ? gPaths.early : gPaths.data) +
                   "/state";

    if (command == "list") return cmdList();
    if (command == "verify") return cmdVerify();
    if (command == "inspect" && argc == 3) return cmdInspect(argv[2]);
    if (geteuid() != 0) {
        fprintf(stderr, "gammaos-modules: %s needs root\n", command.c_str());
        return 77;
    }
    // One at a time: init's steps, and an install or removal by the home, all rewrite the
    // state and the mounts. The lock is released when this process ends.
    int lock = open("/dev/.gammaos-modules.lock", O_RDWR | O_CREAT | O_CLOEXEC, 0600);
    if (lock >= 0) flock(lock, LOCK_EX);

    if (bootStep) {
        if (!exists(gPaths.state)) return 0;  // nothing installed: nothing to do, at no cost
        int rc = command == "early"     ? cmdEarly()
                 : command == "late"    ? applyAll(Apply::kBoot)
                 : command == "confirm" ? cmdConfirm()
                 : command == "reject"  ? cmdReject()
                                        : cmdSettle();
        struct timespec done;
        clock_gettime(CLOCK_MONOTONIC, &done);
        long ms = (done.tv_sec - started.tv_sec) * 1000 + (done.tv_nsec - started.tv_nsec) / 1000000;
        LOG(INFO) << command << " step took " << ms << " ms";
        return rc;
    }
    if (command == "install" && argc >= 3) {
        bool force = argc > 3 && std::string(argv[3]) == "--force";
        return cmdInstall(argv[2], force);
    }
    if (command == "remove" && argc == 3) return cmdRemove(argv[2]);
    return usage();
}
