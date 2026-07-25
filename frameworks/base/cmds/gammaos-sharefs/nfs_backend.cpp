/*
 * Copyright (C) 2026 GammaOS
 *
 * NFS backend, built on libnfs.
 *
 * As with SMB, the kernel here has no NFS client and cannot be given one, but that only rules out
 * "mount -t nfs". libnfs speaks NFSv3 (and the portmap/mount protocols that go with it) over plain
 * sockets from userspace, and this turns that into the same Backend the FUSE layer serves.
 *
 * NFS authorises by address rather than by password: the server exports a directory to a host or
 * subnet, and trusts the uid/gid the client sends. So a share of this type carries no credentials,
 * and if the export does not list this device the mount fails no matter what is typed in the UI -
 * which is why connect() reports that case clearly instead of leaving a hanging mount.
 *
 * libnfs' synchronous API is not thread safe, hence the single lock, and the last opened file is
 * kept for the same reason as in the SMB backend: media playback is a stream of small sequential
 * reads, and reopening per read would make it unusable.
 */

#define LOG_TAG "gammaos-sharefs-nfs"

#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <string.h>
#include <sys/statvfs.h>   // nfs_statvfs fills a real struct statvfs; libnfs only forward-declares it

#include <mutex>
#include <string>
#include <vector>

extern "C" {
#include <nfsc/libnfs.h>
// NF3DIR and friends. libnfs.h documents nfsdirent.type as needing this header, which sits with
// the generated NFSv3 sources rather than under nfsc/.
#include <libnfs-raw-nfs.h>
}

#include "sharefs.h"

namespace gammaos {
namespace sharefs {
namespace {

class NfsBackend : public Backend {
public:
    explicit NfsBackend(const ShareConfig& cfg) : mCfg(cfg) {}
    ~NfsBackend() override { disconnect(); }

    int connect() override {
        std::lock_guard<std::mutex> lk(mLock);
        return connectLocked();
    }

    void disconnect() override {
        std::lock_guard<std::mutex> lk(mLock);
        closeCachedLocked();
        if (mNfs) {
            nfs_destroy_context(mNfs);
            mNfs = nullptr;
        }
    }

    int getAttr(const std::string& path, struct stat* out) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        struct nfs_stat_64 st = {};
        if (nfs_stat64(mNfs, path.c_str(), &st) < 0) return mapError();

        memset(out, 0, sizeof(*out));
        out->st_mode = static_cast<mode_t>(st.nfs_mode);
        // The server reports the real mode; force the type bits from it so a directory is still a
        // directory even on an export with unusual permissions.
        if (S_ISDIR(static_cast<mode_t>(st.nfs_mode))) out->st_mode |= S_IFDIR;
        out->st_nlink = static_cast<nlink_t>(st.nfs_nlink);
        out->st_size = static_cast<off_t>(st.nfs_size);
        out->st_mtime = static_cast<time_t>(st.nfs_mtime);
        out->st_atime = static_cast<time_t>(st.nfs_atime);
        out->st_ctime = static_cast<time_t>(st.nfs_ctime);
        out->st_blocks = static_cast<blkcnt_t>((st.nfs_size + 511) / 512);
        return 0;
    }

    int readDir(const std::string& path, std::vector<DirEntry>* out) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        struct nfsdir* dir = nullptr;
        if (nfs_opendir(mNfs, path.c_str(), &dir) < 0) return mapError();

        struct nfsdirent* ent;
        while ((ent = nfs_readdir(mNfs, dir)) != nullptr) {
            if (!ent->name) continue;
            if (!strcmp(ent->name, ".") || !strcmp(ent->name, "..")) continue;
            DirEntry e;
            e.name = ent->name;
            // READDIRPLUS gives the type for free; fall back to the mode bits when it does not.
            e.isDir = (ent->type == NF3DIR) || S_ISDIR(static_cast<mode_t>(ent->mode));
            e.size = ent->size;
            e.mtime = static_cast<time_t>(ent->mtime.tv_sec);
            out->push_back(std::move(e));
        }
        nfs_closedir(mNfs, dir);
        return 0;
    }

    int readFile(const std::string& path, char* buf, size_t size, off_t offset) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        struct nfsfh* fh = openCachedLocked(path, O_RDONLY);
        if (!fh) return mapError();
        int n = nfs_pread(mNfs, fh, buf, size, static_cast<uint64_t>(offset));
        if (n < 0) {
            // Most likely a stale handle after the server restarted; drop it so the next call
            // reopens rather than failing forever.
            closeCachedLocked();
            return mapError();
        }
        return n;
    }

    int writeFile(const std::string& path, const char* buf, size_t size, off_t offset) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        struct nfsfh* fh = openCachedLocked(path, O_RDWR);
        if (!fh) return mapError();
        int n = nfs_pwrite(mNfs, fh, buf, size, static_cast<uint64_t>(offset));
        if (n < 0) {
            closeCachedLocked();
            return mapError();
        }
        return n;
    }

    int createFile(const std::string& path, mode_t mode) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();
        struct nfsfh* fh = nullptr;
        // nfs_creat is the create-and-open call here; it takes the mode, not open flags.
        if (nfs_creat(mNfs, path.c_str(), static_cast<int>(mode ? mode : 0644), &fh) < 0) {
            return mapError();
        }
        nfs_close(mNfs, fh);
        return 0;
    }

    int truncateFile(const std::string& path, off_t size) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();
        return nfs_truncate(mNfs, path.c_str(), static_cast<uint64_t>(size)) < 0 ? mapError() : 0;
    }

    int unlinkFile(const std::string& path) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();
        return nfs_unlink(mNfs, path.c_str()) < 0 ? mapError() : 0;
    }

    int makeDir(const std::string& path, mode_t) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        return nfs_mkdir(mNfs, path.c_str()) < 0 ? mapError() : 0;
    }

    int removeDir(const std::string& path) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        return nfs_rmdir(mNfs, path.c_str()) < 0 ? mapError() : 0;
    }

    int renamePath(const std::string& from, const std::string& to) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();
        return nfs_rename(mNfs, from.c_str(), to.c_str()) < 0 ? mapError() : 0;
    }

    int statFs(uint64_t* totalBytes, uint64_t* freeBytes) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        struct statvfs vfs = {};
        if (nfs_statvfs(mNfs, "/", &vfs) < 0) return -ENOSYS;
        *totalBytes = static_cast<uint64_t>(vfs.f_blocks) * vfs.f_frsize;
        *freeBytes = static_cast<uint64_t>(vfs.f_bavail) * vfs.f_frsize;
        return 0;
    }

    bool isDead() const override { return mDead; }

private:
    int connectLocked() {
        closeCachedLocked();
        if (mNfs) { nfs_destroy_context(mNfs); mNfs = nullptr; }

        mNfs = nfs_init_context();
        if (!mNfs) {
            ALOGE("nfs_init_context failed for %s", mCfg.name.c_str());
            return -ENOMEM;
        }
        // The uid/gid sent to the server decide what the export lets us touch. Present the same
        // media_rw identity the mount is shown as, so what the menu can see it can also write.
        nfs_set_uid(mNfs, 1023);
        nfs_set_gid(mNfs, 1023);
        // libnfs waits a full minute by default. That is far too long to sit in front of: the menu
        // is single threaded over this mount, so an unreachable server would look like the whole UI
        // had frozen. Ten seconds matches what the curl backends use to connect, and is long enough
        // to ride out a brief wifi stall without giving up on a server that is merely slow.
        nfs_set_timeout(mNfs, 10000);
        // Let libnfs re-establish the session itself where it can, on top of our own reconnect.
        nfs_set_autoreconnect(mNfs, 1);

        if (nfs_mount(mNfs, mCfg.host.c_str(), mCfg.path.c_str()) < 0) {
            // Being refused here almost always means the export does not list this device, which
            // is not something the user can fix on the handheld, so say so plainly.
            ALOGE("NFS mount of %s:%s failed: %s (check the export allows this device's address)",
                  mCfg.host.c_str(), mCfg.path.c_str(), nfs_get_error(mNfs));
            nfs_destroy_context(mNfs);
            mNfs = nullptr;
            mDead = true;
            return -EACCES;
        }
        mDead = false;
        ALOGI("NFS mounted %s:%s", mCfg.host.c_str(), mCfg.path.c_str());
        return 0;
    }

    int ensureLocked() {
        if (mNfs && !mDead) return 0;
        return connectLocked();
    }

    struct nfsfh* openCachedLocked(const std::string& path, int flags) {
        if (mCachedFh && mCachedPath == path && mCachedFlags == flags) return mCachedFh;
        closeCachedLocked();
        struct nfsfh* fh = nullptr;
        if (nfs_open(mNfs, path.c_str(), flags, &fh) < 0) return nullptr;
        mCachedFh = fh;
        mCachedPath = path;
        mCachedFlags = flags;
        return fh;
    }

    void closeCachedLocked() {
        if (mCachedFh && mNfs) nfs_close(mNfs, mCachedFh);
        mCachedFh = nullptr;
        mCachedPath.clear();
        mCachedFlags = -1;
    }

    // Map a libnfs failure to an errno, and decide whether the mount is still usable.
    //
    // Same reasoning as the SMB backend: not noticing a dropped connection is far worse than
    // reconnecting when it was not necessary, because the mount then fails forever and the user has
    // to re-add the share. So anything that is not a recognised NFS status about a file is taken as
    // a lost connection.
    int mapError() {
        const char* e = mNfs ? nfs_get_error(mNfs) : "no context";
        if (!e) e = "";
        if (strstr(e, "NFS3ERR_NOENT") || strstr(e, "ENOENT")) return -ENOENT;
        if (strstr(e, "NFS3ERR_ACCES") || strstr(e, "NFS3ERR_PERM")) return -EACCES;
        if (strstr(e, "NFS3ERR_NOTDIR")) return -ENOTDIR;
        if (strstr(e, "NFS3ERR_ISDIR")) return -EISDIR;
        if (strstr(e, "NFS3ERR_EXIST")) return -EEXIST;
        if (strstr(e, "NFS3ERR_NOSPC") || strstr(e, "NFS3ERR_DQUOT")) return -ENOSPC;
        if (strstr(e, "NFS3ERR_ROFS")) return -EROFS;
        if (strstr(e, "NFS3ERR_NOTEMPTY")) return -ENOTEMPTY;
        if (strstr(e, "NFS3ERR_STALE")) {
            // A stale handle after the server restarted: the export is fine, our handles are not.
            mDead = true;
            return -ESTALE;
        }
        ALOGW("NFS error on %s, dropping the mount to force a reconnect: %s",
              mCfg.name.c_str(), e[0] ? e : "(no detail)");
        mDead = true;
        return -EIO;
    }

    ShareConfig mCfg;
    std::mutex  mLock;
    struct nfs_context* mNfs = nullptr;
    bool mDead = false;

    struct nfsfh* mCachedFh = nullptr;
    std::string   mCachedPath;
    int           mCachedFlags = -1;
};

}  // namespace

Backend* makeNfsBackend(const ShareConfig& cfg) { return new NfsBackend(cfg); }

}  // namespace sharefs
}  // namespace gammaos
