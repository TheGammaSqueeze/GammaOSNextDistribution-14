/*
 * Copyright (C) 2026 GammaOS
 *
 * The FUSE side: turns a Backend into a directory under /mnt/shares.
 *
 * The mount is made with allow_other so processes other than this daemon can read it - without
 * that only the daemon's own uid could see the files, which would defeat the point. That is the
 * same option the platform's own sdcard FUSE mount uses.
 *
 * Attributes are cached briefly. A network round trip per getattr makes even listing a directory
 * painful, and the media scanners stat every file they walk, so a short cache is the difference
 * between a share that feels usable and one that does not. The window is deliberately small so a
 * file written from another machine still shows up promptly.
 */

#define LOG_TAG "gammaos-sharefs"
#define FUSE_USE_VERSION 35

#include <errno.h>
#include <fuse.h>
#include <log/log.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "sharefs.h"

namespace gammaos {
namespace sharefs {
namespace {

constexpr double kAttrTimeoutSec = 3.0;   // how long the kernel may trust a stat
constexpr double kEntryTimeoutSec = 3.0;  // ... and a name lookup

// The largest read the kernel may hand us in one go. libfuse insists the mount option and what
// fsInit reports are identical, so both come from here rather than being written out twice.
constexpr unsigned kMaxReadBytes = 131072;

struct MountCtx {
    Backend* backend = nullptr;
    uid_t    uid = 0;      // everything is presented as owned by this uid
    gid_t    gid = 0;
    bool     readOnly = false;

    std::mutex cacheLock;
    struct CachedAttr {
        struct stat st;
        time_t      at;
    };
    std::map<std::string, CachedAttr> attrCache;
};

MountCtx* ctx() { return static_cast<MountCtx*>(fuse_get_context()->private_data); }

void applyOwner(struct stat* st) {
    MountCtx* c = ctx();
    st->st_uid = c->uid;
    st->st_gid = c->gid;
    if (c->readOnly) st->st_mode &= ~static_cast<mode_t>(0222);
}

bool cacheLookup(const std::string& path, struct stat* out) {
    MountCtx* c = ctx();
    std::lock_guard<std::mutex> lk(c->cacheLock);
    auto it = c->attrCache.find(path);
    if (it == c->attrCache.end()) return false;
    if (time(nullptr) - it->second.at > static_cast<time_t>(kAttrTimeoutSec)) {
        c->attrCache.erase(it);
        return false;
    }
    *out = it->second.st;
    return true;
}

void cacheStore(const std::string& path, const struct stat& st) {
    MountCtx* c = ctx();
    std::lock_guard<std::mutex> lk(c->cacheLock);
    // Keep the cache from growing without bound on a big share; this is a hint, not a mirror.
    if (c->attrCache.size() > 4096) c->attrCache.clear();
    c->attrCache[path] = {st, time(nullptr)};
}

void cacheDrop(const std::string& path) {
    MountCtx* c = ctx();
    std::lock_guard<std::mutex> lk(c->cacheLock);
    c->attrCache.erase(path);
}

void cacheDropAll() {
    MountCtx* c = ctx();
    std::lock_guard<std::mutex> lk(c->cacheLock);
    c->attrCache.clear();
}

int fsGetattr(const char* path, struct stat* st, struct fuse_file_info*) {
    if (cacheLookup(path, st)) {
        applyOwner(st);
        return 0;
    }
    int rc = ctx()->backend->getAttr(path, st);
    if (rc != 0) return rc;
    cacheStore(path, *st);
    applyOwner(st);
    return 0;
}

int fsReaddir(const char* path, void* buf, fuse_fill_dir_t filler, off_t,
              struct fuse_file_info*, enum fuse_readdir_flags) {
    std::vector<DirEntry> entries;
    int rc = ctx()->backend->readDir(path, &entries);
    if (rc != 0) return rc;

    filler(buf, ".", nullptr, 0, static_cast<fuse_fill_dir_flags>(0));
    filler(buf, "..", nullptr, 0, static_cast<fuse_fill_dir_flags>(0));

    std::string base(path);
    if (base.empty() || base.back() != '/') base += '/';

    for (const DirEntry& e : entries) {
        struct stat st = {};
        st.st_mode = e.isDir ? (S_IFDIR | 0755) : (S_IFREG | 0644);
        st.st_nlink = e.isDir ? 2 : 1;
        st.st_size = static_cast<off_t>(e.size);
        st.st_mtime = e.mtime;
        st.st_uid = ctx()->uid;
        st.st_gid = ctx()->gid;
        if (ctx()->readOnly) st.st_mode &= ~static_cast<mode_t>(0222);

        // The listing already told us everything a stat would, so seed the cache: otherwise the
        // scanner immediately stats every entry we just returned, one round trip each.
        cacheStore(base + e.name, st);
        filler(buf, e.name.c_str(), &st, 0, static_cast<fuse_fill_dir_flags>(0));
    }
    return 0;
}

int fsOpen(const char* path, struct fuse_file_info* fi) {
    if (ctx()->readOnly && (fi->flags & (O_WRONLY | O_RDWR | O_CREAT | O_TRUNC))) return -EROFS;
    // Opening is validated by stat rather than held state: the backends keep their own handle,
    // which lets a dropped connection be re-established underneath without the caller noticing.
    struct stat st;
    int rc = ctx()->backend->getAttr(path, &st);
    if (rc != 0) return rc;
    if (S_ISDIR(st.st_mode)) return -EISDIR;
    return 0;
}

int fsRead(const char* path, char* buf, size_t size, off_t offset, struct fuse_file_info*) {
    return ctx()->backend->readFile(path, buf, size, offset);
}

int fsWrite(const char* path, const char* buf, size_t size, off_t offset, struct fuse_file_info*) {
    if (ctx()->readOnly) return -EROFS;
    cacheDrop(path);
    return ctx()->backend->writeFile(path, buf, size, offset);
}

int fsCreate(const char* path, mode_t mode, struct fuse_file_info*) {
    if (ctx()->readOnly) return -EROFS;
    cacheDrop(path);
    return ctx()->backend->createFile(path, mode);
}

int fsTruncate(const char* path, off_t size, struct fuse_file_info*) {
    if (ctx()->readOnly) return -EROFS;
    cacheDrop(path);
    return ctx()->backend->truncateFile(path, size);
}

int fsUnlink(const char* path) {
    if (ctx()->readOnly) return -EROFS;
    cacheDrop(path);
    return ctx()->backend->unlinkFile(path);
}

int fsMkdir(const char* path, mode_t mode) {
    if (ctx()->readOnly) return -EROFS;
    cacheDropAll();
    return ctx()->backend->makeDir(path, mode);
}

int fsRmdir(const char* path) {
    if (ctx()->readOnly) return -EROFS;
    cacheDropAll();
    return ctx()->backend->removeDir(path);
}

int fsRename(const char* from, const char* to, unsigned int flags) {
    if (ctx()->readOnly) return -EROFS;
    if (flags) return -EINVAL;   // RENAME_EXCHANGE / NOREPLACE are not offered by these protocols
    cacheDropAll();
    return ctx()->backend->renamePath(from, to);
}

int fsStatfs(const char*, struct statvfs* stv) {
    uint64_t total = 0, freeB = 0;
    memset(stv, 0, sizeof(*stv));
    stv->f_bsize = 4096;
    stv->f_frsize = 4096;
    stv->f_namemax = 255;
    if (ctx()->backend->statFs(&total, &freeB) == 0) {
        stv->f_blocks = total / 4096;
        stv->f_bfree = freeB / 4096;
        stv->f_bavail = freeB / 4096;
    }
    return 0;
}

// libfuse cross-checks the max_read mount option against what the filesystem asks for here, and
// aborts the session with EPROTO if they disagree. Passing max_read= on the command line is
// therefore only half the job: without this the mount comes up and then dies immediately with
// "init() and fuse_session_new() requested different maximum read size (0 vs 131072)".
//
// The large value is deliberate. Every read is a network round trip, so the difference between
// 128KB and the 128KB-default-if-unset is the difference between one request and many for the same
// data when a player streams a file.
void* fsInit(struct fuse_conn_info* conn, struct fuse_config* cfg) {
    conn->max_read = kMaxReadBytes;
    // Let the kernel cache attributes for the same short window the backend cache uses, rather
    // than asking the server again for data we just returned.
    cfg->attr_timeout = kAttrTimeoutSec;
    cfg->entry_timeout = kEntryTimeoutSec;
    // Paths are what the backends address by, so keep them.
    cfg->nullpath_ok = 0;
    return fuse_get_context()->private_data;
}

// The writer is done with this file. WebDAV and FTP stage their writes and upload the whole body
// here, because those protocols can only replace a file, not patch it. SMB and NFS wrote as they
// went and have nothing left to do.
int fsRelease(const char* path, struct fuse_file_info*) {
    if (ctx()->readOnly) return 0;
    cacheDrop(path);
    return ctx()->backend->flushFile(path);
}

// fsync must push the staged body out too: a caller that fsyncs and then reads its file back has
// every right to see what it wrote.
int fsFsync(const char* path, int, struct fuse_file_info*) {
    if (ctx()->readOnly) return 0;
    return ctx()->backend->flushFile(path);
}

// Permission bits are synthesised, so accept the chmod/chown a copy tool will attempt rather than
// failing the whole copy over metadata the server does not model anyway.
int fsChmod(const char*, mode_t, struct fuse_file_info*) { return 0; }
int fsChown(const char*, uid_t, gid_t, struct fuse_file_info*) { return 0; }
int fsUtimens(const char*, const struct timespec[2], struct fuse_file_info*) { return 0; }

const struct fuse_operations kOps = [] {
    struct fuse_operations ops = {};
    ops.init = fsInit;
    ops.getattr = fsGetattr;
    ops.readdir = fsReaddir;
    ops.open = fsOpen;
    ops.read = fsRead;
    ops.write = fsWrite;
    ops.create = fsCreate;
    ops.truncate = fsTruncate;
    ops.unlink = fsUnlink;
    ops.mkdir = fsMkdir;
    ops.rmdir = fsRmdir;
    ops.rename = fsRename;
    ops.statfs = fsStatfs;
    ops.release = fsRelease;
    ops.fsync = fsFsync;
    ops.chmod = fsChmod;
    ops.chown = fsChown;
    ops.utimens = fsUtimens;
    return ops;
}();

}  // namespace

int runMount(const ShareConfig& cfg, const std::string& mountPoint, bool debug) {
    std::unique_ptr<Backend> backend;
    switch (cfg.type) {
        case ShareType::kSmb:    backend.reset(makeSmbBackend(cfg)); break;
        case ShareType::kWebdav: backend.reset(makeWebdavBackend(cfg)); break;
        case ShareType::kFtp:    backend.reset(makeFtpBackend(cfg)); break;
        case ShareType::kNfs:    backend.reset(makeNfsBackend(cfg)); break;
        default:
            ALOGE("share '%s' has an unknown type", cfg.name.c_str());
            return 2;
    }

    // Connect before mounting. Publishing a mount that cannot serve anything would leave a
    // directory that hangs on every access, which is worse than reporting the failure now.
    if (int rc = backend->connect(); rc != 0) {
        ALOGE("share '%s' could not connect (%d)", cfg.name.c_str(), rc);
        return 3;
    }

    MountCtx mctx;
    mctx.backend = backend.get();
    mctx.readOnly = cfg.readOnly;
    // Present the files as owned by media_rw/everybody, which is what the rest of the system
    // expects of removable storage, so the menu and any app can read them.
    mctx.uid = 1023;   // AID_MEDIA_RW
    mctx.gid = 1023;

    std::vector<std::string> args;
    args.push_back("gammaos-sharefs");
    args.push_back(mountPoint);
    args.push_back("-f");                 // stay in the foreground; init owns the lifecycle
    args.push_back("-o");
    { char o[128];
      snprintf(o, sizeof(o), "allow_other,default_permissions,noatime,max_read=%u", kMaxReadBytes);
      args.push_back(o); }
    if (cfg.readOnly) { args.push_back("-o"); args.push_back("ro"); }
    if (debug) args.push_back("-d");

    std::vector<char*> argv;
    for (auto& a : args) argv.push_back(const_cast<char*>(a.c_str()));

    ALOGI("mounting %s share '%s' at %s", shareTypeName(cfg.type), cfg.name.c_str(),
          mountPoint.c_str());
    int rc = fuse_main(static_cast<int>(argv.size()), argv.data(), &kOps, &mctx);
    ALOGI("share '%s' unmounted (%d)", cfg.name.c_str(), rc);
    backend->disconnect();
    return rc;
}

}  // namespace sharefs
}  // namespace gammaos
