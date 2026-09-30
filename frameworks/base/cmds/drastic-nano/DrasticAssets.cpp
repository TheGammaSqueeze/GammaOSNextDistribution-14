#define LOG_TAG "DrasticNano"

#include "DrasticAssets.h"
#include "DrasticCheatDb.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
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
        if (l > el && strcasecmp(e->d_name + l - el, ext) == 0) {
            // Only count files with content. DraStic leaves zero-byte .dsv placeholders
            // behind for games it merely opened (the bundled demos on a fresh device),
            // and those would offer an import of nothing on the very first launch.
            struct stat st;
            if (stat((dir + "/" + e->d_name).c_str(), &st) == 0 && st.st_size > 0) n++;
        }
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

// The user data folder (saves / savestates / shaders). persist.gammaos.drastic.data_dir, when it
// is an absolute path, relocates it; anything else (unset, relative, "@default") is the default.
// Trailing slashes are trimmed so "<dir>/saves" joins cleanly.
std::string userDir() {
    char dd[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic.data_dir", dd, "");
    if (dd[0] != '/') return kUserDirDefault;
    std::string d = dd;
    while (d.size() > 1 && d.back() == '/') d.pop_back();
    return d;
}

void mergeShaders(const std::string& root) {
    const std::string dst = root + "/shaders";
    const std::string sys = systemDir() + "/shaders";
    const std::string usr = userDir() + "/shaders";
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

// ---------------------------------------------------------------------------
// User cheat databases
// ---------------------------------------------------------------------------
std::string cheatsDir() {
    char dd[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic.cheats_dir", dd, "");
    if (dd[0] != '/') return userDir() + "/cheats";
    std::string d = dd;
    while (d.size() > 1 && d.back() == '/') d.pop_back();
    return d;
}

namespace {

std::string readWhole(const std::string& p) {
    std::string out;
    int fd = open(p.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return out;
    char buf[4096];
    for (;;) {
        ssize_t n = read(fd, buf, sizeof(buf));
        if (n <= 0) break;
        out.append(buf, (size_t)n);
    }
    close(fd);
    return out;
}

bool writeWhole(const std::string& p, const std::string& text) {
    int fd = open(p.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) return false;
    ssize_t n = write(fd, text.data(), text.size());
    close(fd);
    return n == (ssize_t)text.size();
}

// The user's database files, sorted by name so the merge order is stable.
std::vector<std::string> userCheatFiles(const std::string& dir) {
    std::vector<std::string> files;
    if (DIR* d = opendir(dir.c_str())) {
        struct dirent* e;
        while ((e = readdir(d)) != nullptr) {
            if (e->d_name[0] == '.') continue;
            std::string p = dir + "/" + e->d_name;
            if (isFile(p) && drastic_cheatdb::isCheatDb(p)) files.push_back(p);
        }
        closedir(d);
    }
    std::sort(files.begin(), files.end());
    return files;
}

// One line per merged game in <root>/.cheatsplit: code crc builtinCheats builtinFolders
// totalCheats totalFolders. The overlay matches the running game against it.
const char kSplitFile[] = "/.cheatsplit";
const char kMergeStamp[] = "/.cheatmerge";

std::string cheatName4(const std::string& romPath) {
    int fd = open(romPath.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return {};
    char hdr[0x10] = {};
    ssize_t n = pread(fd, hdr, sizeof(hdr), 0);
    close(fd);
    if (n < 0x10) return {};
    return std::string(hdr + 0x0c, 4);
}

// Enabled bits libdrastic wrote into the previous merged file, keyed by
// "code|crc|folder|cheat" so a rebuild keeps the user's selections.
void collectEnabled(const drastic_cheatdb::Db& db, std::vector<std::string>* keys) {
    for (const auto& g : db.games) {
        const std::string base = g.code + "|" + std::to_string(g.crc) + "|";
        for (const auto& r : g.records) {
            if (r.folder) {
                for (const auto& c : r.children)
                    if (c.flags & 1) keys->push_back(base + r.name + "|" + c.name);
            } else if (r.flags & 1) {
                keys->push_back(base + "|" + r.name);
            }
        }
    }
}

} // namespace

void mergeCheats(const std::string& root) {
    const std::string dir = cheatsDir();
    mkdirs(dir);
    const std::string readme = dir + "/README.txt";
    if (!isFile(readme))
        writeWhole(readme,
                   "Put cheat database files here (R4 usrcheat.dat format, any file name).\n"
                   "They are merged with the built-in cheats the next time a DS game starts.\n"
                   "The in-game menu's Cheats page can show built-in and custom cheats separately.\n");

    const std::vector<std::string> files = userCheatFiles(dir);
    std::string stamp = "v1|" + seedStamp() + "|" + dir;
    for (const std::string& f : files) {
        struct stat st = {};
        stat(f.c_str(), &st);
        stamp += "|" + f + ":" + std::to_string((long long)st.st_size) + ":" + std::to_string((long long)st.st_mtime);
    }
    const std::string target = root + "/usrcheat.dat";
    if (readWhole(root + kMergeStamp) == stamp && isFile(target)) return;   // nothing changed

    if (files.empty()) {
        // Back to the shipped database (the copy seedRoot keeps fresh, unless a previous
        // merge replaced it).
        if (isFile(root + kSplitFile) || !isFile(target)) {
            unlink(target.c_str());
            if (!copyFile(systemDir() + "/usrcheat.dat", target))
                ALOGE("drastic-nano cheats: restoring the shipped usrcheat.dat failed: %s", strerror(errno));
        }
        unlink((root + kSplitFile).c_str());
        writeWhole(root + kMergeStamp, stamp);
        ALOGI("drastic-nano cheats: no user databases in %s, shipped database in use", dir.c_str());
        return;
    }

    std::string err;
    drastic_cheatdb::Db merged;
    if (!drastic_cheatdb::load(systemDir() + "/usrcheat.dat", &merged, &err)) {
        ALOGE("drastic-nano cheats: shipped usrcheat.dat unreadable (%s), user files not merged", err.c_str());
        return;
    }
    // Selections libdrastic stored in the file being replaced.
    std::vector<std::string> enabledKeys;
    {
        drastic_cheatdb::Db prev;
        if (isFile(target) && drastic_cheatdb::load(target, &prev)) collectEnabled(prev, &enabledKeys);
    }
    struct Split { std::string code; uint32_t crc; int bc, bf, tc, tf; };
    std::vector<Split> splits;
    int addedGames = 0, addedCheats = 0, usedFiles = 0;
    for (const std::string& f : files) {
        drastic_cheatdb::Db user;
        if (!drastic_cheatdb::load(f, &user, &err)) {
            ALOGW("drastic-nano cheats: skipping %s: %s", f.c_str(), err.c_str());
            continue;
        }
        usedFiles++;
        for (auto& ug : user.games) {
            int uc = 0, uf = 0;
            drastic_cheatdb::countRecords(ug.records, &uc, &uf);
            if (uc == 0) continue;
            addedCheats += uc;
            drastic_cheatdb::Game* target_game = nullptr;
            for (auto& g : merged.games)
                if (g.code == ug.code && g.crc == ug.crc) { target_game = &g; break; }
            Split* sp = nullptr;
            for (auto& s : splits)
                if (s.code == ug.code && s.crc == ug.crc) { sp = &s; break; }
            if (!target_game) {
                merged.games.push_back(ug);
                addedGames++;
                splits.push_back({ug.code, ug.crc, 0, 0, uc, uf});
            } else {
                if (!sp) {
                    int bc = 0, bf = 0;
                    drastic_cheatdb::countRecords(target_game->records, &bc, &bf);
                    splits.push_back({ug.code, ug.crc, bc, bf, bc, bf});
                    sp = &splits.back();
                }
                for (auto& r : ug.records) target_game->records.push_back(std::move(r));
                sp->tc += uc; sp->tf += uf;
            }
        }
    }
    // Re-apply the stored selections by name.
    if (!enabledKeys.empty()) {
        std::sort(enabledKeys.begin(), enabledKeys.end());
        for (auto& g : merged.games) {
            const std::string base = g.code + "|" + std::to_string(g.crc) + "|";
            auto on = [&](const std::string& k) {
                return std::binary_search(enabledKeys.begin(), enabledKeys.end(), k);
            };
            for (auto& r : g.records) {
                if (r.folder) {
                    for (auto& c : r.children)
                        if (on(base + r.name + "|" + c.name)) c.flags |= 1;
                } else if (on(base + "|" + r.name)) {
                    r.flags |= 1;
                }
            }
        }
    }
    if (!drastic_cheatdb::save(target, merged, &err)) {
        ALOGE("drastic-nano cheats: writing merged usrcheat.dat failed: %s", err.c_str());
        return;
    }
    std::string splitText;
    for (const auto& s : splits)
        splitText += s.code + " " + std::to_string(s.crc) + " " + std::to_string(s.bc) + " "
                     + std::to_string(s.bf) + " " + std::to_string(s.tc) + " " + std::to_string(s.tf) + "\n";
    writeWhole(root + kSplitFile, splitText);
    writeWhole(root + kMergeStamp, stamp);
    ALOGI("drastic-nano cheats: merged %d user database(s) from %s: %d new games, %d cheats added, %zu games touched",
          usedFiles, dir.c_str(), addedGames, addedCheats, splits.size());
}

CheatSplit cheatSplitFor(const std::string& root, const std::string& romPath,
                         int cheatCount, int folderCount) {
    CheatSplit out;
    out.userFiles = (int)userCheatFiles(cheatsDir()).size();
    out.builtinCheats = cheatCount;
    out.builtinFolders = folderCount;
    const std::string code = cheatName4(romPath);
    if (code.size() != 4) return out;
    const std::string text = readWhole(root + kSplitFile);
    size_t pos = 0;
    while (pos < text.size()) {
        size_t nl = text.find('\n', pos);
        if (nl == std::string::npos) nl = text.size();
        std::string line = text.substr(pos, nl - pos);
        pos = nl + 1;
        char c[8] = {};
        unsigned long crc = 0;
        int bc = 0, bf = 0, tc = 0, tf = 0;
        if (sscanf(line.c_str(), "%4s %lu %d %d %d %d", c, &crc, &bc, &bf, &tc, &tf) != 6) continue;
        if (code != c) continue;
        // Several revisions of one game code can be listed; the one libdrastic loaded is the
        // one whose totals match what it reports.
        if (tc == cheatCount && tf == folderCount) {
            out.builtinCheats = bc;
            out.builtinFolders = bf;
            return out;
        }
    }
    return out;
}

bool seedRoot(const std::string& root) {
    const std::string sysDir = systemDir();
    if (!isDir(sysDir)) {
        ALOGE("drastic-nano assets: %s missing from the image", sysDir.c_str());
        return false;
    }
    static const char* kSubdirs[] = {
        "system", "config", "cheats", "microphone", "input_record",
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
    const std::string usr    = userDir();
    const std::string saves  = usr + "/saves";
    const std::string states = usr + "/savestates";
    // Slot-2 cartridge files (the System page's Slot-2 Cartridge): drastic opens
    // <root>/slot2/<rom>.gba for a GBA Cart and <rom>.sav beside it, so the folder
    // has to be one the user can reach, like the saves.
    const std::string slot2  = usr + "/slot2";
    mkdirs(usr);
    mkdirs(saves);
    mkdirs(states);
    mkdirs(slot2);
    mkdirs(usr + "/shaders");
    // A real backup/, savestates/ or slot2/ directory left from an older layout:
    // carry its files over before replacing it with the link.
    for (const auto& pr : { std::make_pair(root + "/backup", saves), std::make_pair(root + "/savestates", states),
                            std::make_pair(root + "/slot2", slot2) }) {
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

    // The game database is read-only: linked. The cheat database is NOT: libdrastic's
    // updateCheats opens User/usrcheat.dat with "rb+" to store the enabled flags, and
    // a link into the read-only system image made every cheat apply fail with EROFS
    // (reported 2026-09-22: "cheats no longer work"). Copy it once (13.7 MB), replace
    // an older link, and refresh it when the image changes like the BIOS files.
    ensureLink(root + "/game_database.xml", systemDir() + "/game_database.xml");
    {
        const std::string t = root + "/usrcheat.dat";
        const bool imageChanged = readSmall(root + "/.seed") != seedStamp();
        if (isLink(t) || imageChanged || !isFile(t)) {
            unlink(t.c_str());
            if (!copyFile(systemDir() + "/usrcheat.dat", t))
                ALOGE("drastic-nano assets: copy usrcheat.dat failed: %s", strerror(errno));
        }
    }

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
    // Every launch too: a stamp makes it a directory listing unless a cheat file changed.
    mergeCheats(root);

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
    const std::string usr = userDir();
    collect(std::string(kLegacyRoot) + "/backup",     ".sav", usr + "/saves");
    collect(std::string(kLegacyRoot) + "/backup",     ".dsv", usr + "/saves");
    collect(std::string(kLegacyRoot) + "/savestates", ".dss", usr + "/savestates");
    mkdirs(usr + "/saves");
    mkdirs(usr + "/savestates");
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
