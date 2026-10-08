#define LOG_TAG "azahar-nano"

#include "GpuDriverPkg.h"

#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>

#include <log/log.h>
#include <ziparchive/zip_archive.h>

namespace azahar_nano {
namespace gpudrv {

namespace {

bool isDir(const std::string& p) {
    struct stat st{};
    return stat(p.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

bool isFile(const std::string& p, struct stat* out = nullptr) {
    struct stat st{};
    if (stat(p.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) return false;
    if (out) *out = st;
    return true;
}

bool mkdirs(const std::string& path) {
    std::string cur;
    std::istringstream ss(path);
    std::string part;
    while (std::getline(ss, part, '/')) {
        if (part.empty()) { cur += "/"; continue; }
        cur += part + "/";
        if (mkdir(cur.c_str(), 0755) != 0 && errno != EEXIST) return false;
    }
    return isDir(path);
}

void removeTree(const std::string& path) {
    if (DIR* d = opendir(path.c_str())) {
        while (dirent* e = readdir(d)) {
            if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
            const std::string p = path + "/" + e->d_name;
            if (isDir(p)) removeTree(p); else unlink(p.c_str());
        }
        closedir(d);
    }
    rmdir(path.c_str());
}

bool hasSuffix(const std::string& s, const char* suf) {
    const size_t n = strlen(suf);
    if (s.size() < n) return false;
    return strncasecmp(s.c_str() + s.size() - n, suf, n) == 0;
}

// Minimal JSON string lookup: the value of "key" in a flat object.
std::string jsonString(const std::string& text, const char* key) {
    const std::string needle = std::string("\"") + key + "\"";
    size_t p = text.find(needle);
    if (p == std::string::npos) return "";
    p = text.find(':', p + needle.size());
    if (p == std::string::npos) return "";
    p = text.find('"', p);
    if (p == std::string::npos) return "";
    const size_t e = text.find('"', p + 1);
    if (e == std::string::npos) return "";
    return text.substr(p + 1, e - p - 1);
}

std::string readFile(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return "";
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

bool copyFile(const std::string& from, const std::string& to) {
    const int in = open(from.c_str(), O_RDONLY | O_CLOEXEC);
    if (in < 0) return false;
    const int out = open(to.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (out < 0) { close(in); return false; }
    std::vector<char> buf(1 << 16);
    bool ok = true;
    for (;;) {
        const ssize_t n = read(in, buf.data(), buf.size());
        if (n < 0) { ok = false; break; }
        if (n == 0) break;
        ssize_t off = 0;
        while (off < n) {
            const ssize_t w = write(out, buf.data() + off, (size_t)(n - off));
            if (w <= 0) { ok = false; break; }
            off += w;
        }
        if (!ok) break;
    }
    if (fsync(out) != 0) ok = false;
    close(in);
    close(out);
    return ok;
}

// Entry names inside a package zip may carry a leading folder; the package
// layout is flat, so the base name is what gets written.
std::string baseName(const std::string& entry) {
    const size_t s = entry.find_last_of('/');
    return s == std::string::npos ? entry : entry.substr(s + 1);
}

bool zipHasMeta(const std::string& zipPath) {
    ZipArchiveHandle zip = nullptr;
    if (OpenArchive(zipPath.c_str(), &zip) != 0) return false;
    void* cookie = nullptr;
    bool found = false;
    if (StartIteration(zip, &cookie, "", "meta.json") == 0) {
        ZipEntry64 entry;
        std::string name;
        found = Next(cookie, &entry, &name) == 0;
        EndIteration(cookie);
    }
    CloseArchive(zip);
    return found;
}

void listZipsIn(const std::string& dir, std::vector<std::string>& out) {
    DIR* d = opendir(dir.c_str());
    if (!d) return;
    while (dirent* e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        const std::string p = dir + "/" + e->d_name;
        if (!hasSuffix(p, ".zip") || !isFile(p)) continue;
        if (zipHasMeta(p)) out.push_back(p);
    }
    closedir(d);
}

}  // namespace

std::string libraryName(const std::string& dir) {
    return jsonString(readFile(dir + "/meta.json"), "libraryName");
}

std::vector<std::string> listPackages(const std::string& root) {
    std::vector<std::string> out;
    const std::string dir = root + "/gpu_drivers";
    if (DIR* d = opendir(dir.c_str())) {
        while (dirent* e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            const std::string p = dir + "/" + e->d_name;
            if (!isDir(p)) continue;
            const std::string lib = libraryName(p);
            if (lib.empty() || !isFile(p + "/" + lib)) continue;
            out.push_back(e->d_name);
        }
        closedir(d);
    }
    std::sort(out.begin(), out.end());
    return out;
}

std::vector<std::string> listZips(const std::string& root) {
    std::vector<std::string> out;
    listZipsIn(root + "/gpu_drivers", out);
    listZipsIn(root, out);
    listZipsIn("/sdcard", out);
    listZipsIn("/sdcard/Download", out);
    std::sort(out.begin(), out.end());
    out.erase(std::unique(out.begin(), out.end()), out.end());
    return out;
}

std::string importZip(const std::string& zipPath, const std::string& root, std::string* error) {
    auto fail = [&](const char* why) {
        if (error) *error = why;
        ALOGE("gpu driver import %s: %s", zipPath.c_str(), why);
        return std::string();
    };
    std::string name = baseName(zipPath);
    if (hasSuffix(name, ".adpkg.zip")) name.resize(name.size() - 10);
    else if (hasSuffix(name, ".zip")) name.resize(name.size() - 4);
    if (name.empty()) return fail("bad file name");
    const std::string dest = root + "/gpu_drivers/" + name;
    if (!mkdirs(dest)) return fail("cannot create the package folder");

    ZipArchiveHandle zip = nullptr;
    if (OpenArchive(zipPath.c_str(), &zip) != 0) {
        removeTree(dest);
        return fail("not a zip file");
    }
    void* cookie = nullptr;
    bool ok = StartIteration(zip, &cookie) == 0;
    int files = 0;
    if (ok) {
        ZipEntry64 entry;
        std::string ename;
        while (Next(cookie, &entry, &ename) == 0) {
            if (ename.empty() || ename.back() == '/') continue;   // folders
            const std::string out = dest + "/" + baseName(ename);
            const int fd = open(out.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
            if (fd < 0) { ok = false; break; }
            const int rc = ExtractEntryToFile(zip, &entry, fd);
            close(fd);
            if (rc != 0) { ok = false; break; }
            files++;
        }
        EndIteration(cookie);
    }
    CloseArchive(zip);
    if (!ok || files == 0) {
        removeTree(dest);
        return fail("unpacking failed");
    }
    const std::string lib = libraryName(dest);
    if (lib.empty() || !isFile(dest + "/" + lib)) {
        removeTree(dest);
        return fail("meta.json names no library in the package");
    }
    ALOGI("gpu driver import: %s -> %s (%d files, %s)", zipPath.c_str(), dest.c_str(), files,
          lib.c_str());
    if (error) error->clear();
    return name;
}

std::string stage(const std::string& root, const std::string& privateRoot, const std::string& name) {
    if (name.empty()) return "";
    const std::string src = root + "/gpu_drivers/" + name;
    const std::string dst = privateRoot + "/gpu_drivers/" + name;
    const std::string lib = libraryName(src);
    struct stat srcSt{};
    if (lib.empty() || !isFile(src + "/" + lib, &srcSt)) {
        // Only a staged copy left (the data folder was moved or cleaned).
        const std::string staged = libraryName(dst);
        if (!staged.empty() && isFile(dst + "/" + staged)) return dst;
        ALOGW("gpu driver %s: no package at %s", name.c_str(), src.c_str());
        return "";
    }
    struct stat dstSt{};
    const bool fresh = isFile(dst + "/" + lib, &dstSt) && dstSt.st_size == srcSt.st_size &&
                       dstSt.st_mtime >= srcSt.st_mtime && isFile(dst + "/meta.json");
    if (fresh) return dst;
    if (!mkdirs(dst)) return "";
    bool ok = true;
    if (DIR* d = opendir(src.c_str())) {
        while (dirent* e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            const std::string f = src + "/" + e->d_name;
            if (!isFile(f)) continue;
            if (!copyFile(f, dst + "/" + e->d_name)) { ok = false; break; }
        }
        closedir(d);
    } else {
        ok = false;
    }
    if (!ok) {
        ALOGE("gpu driver %s: staging to %s failed", name.c_str(), dst.c_str());
        removeTree(dst);
        return "";
    }
    ALOGI("gpu driver %s staged at %s", name.c_str(), dst.c_str());
    return dst;
}

}  // namespace gpudrv
}  // namespace azahar_nano
