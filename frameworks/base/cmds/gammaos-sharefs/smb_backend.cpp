/*
 * Copyright (C) 2026 GammaOS
 *
 * SMB2/SMB3 backend, built on libsmb2.
 *
 * curl's SMB support is SMB1 only and disabled in this build, and no current NAS accepts SMB1, so
 * the share is spoken directly with libsmb2 instead. Authentication is NTLM, which is what home
 * shares use; Kerberos is deliberately not built (it would pull in krb5 for no benefit here).
 *
 * libsmb2's synchronous API is not thread safe, so every call holds the context lock. That is not
 * the bottleneck it looks like: these are single-user shares over wifi, where the network round
 * trip dominates. What does matter is not re-opening a file for every read, since a video player
 * issues a steady stream of small sequential reads, so the last opened file is kept around.
 */

#define LOG_TAG "gammaos-sharefs-smb"

#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <string.h>

#include <mutex>
#include <string>
#include <vector>

extern "C" {
// smb2.h first: it defines the constants and types (SMB2_GUID_SIZE, smb2_lease_key, ...) that the
// prototypes in libsmb2.h refer to, and libsmb2.h does not include it itself.
#include <smb2/smb2.h>
#include <smb2/libsmb2.h>
}

#include "sharefs.h"

namespace gammaos {
namespace sharefs {
namespace {

class SmbBackend : public Backend {
public:
    explicit SmbBackend(const ShareConfig& cfg) : mCfg(cfg) {}

    ~SmbBackend() override { disconnect(); }

    int connect() override {
        std::lock_guard<std::mutex> lk(mLock);
        return connectLocked();
    }

    void disconnect() override {
        std::lock_guard<std::mutex> lk(mLock);
        closeCachedLocked();
        if (mSmb) {
            smb2_disconnect_share(mSmb);
            smb2_destroy_context(mSmb);
            mSmb = nullptr;
        }
    }

    int getAttr(const std::string& path, struct stat* out) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        // The share root always exists; asking the server for it is pointless and some servers
        // refuse a stat of "".
        if (path == "/" || path.empty()) {
            fillDirStat(out);
            return 0;
        }

        struct smb2_stat_64 st = {};
        if (int rc = smb2_stat(mSmb, rel(path).c_str(), &st); rc < 0) {
            return mapError(rc);
        }
        memset(out, 0, sizeof(*out));
        const bool isDir = (st.smb2_type == SMB2_TYPE_DIRECTORY);
        out->st_mode = isDir ? (S_IFDIR | 0755) : (S_IFREG | (mCfg.readOnly ? 0444 : 0644));
        out->st_nlink = isDir ? 2 : 1;
        out->st_size = static_cast<off_t>(st.smb2_size);
        out->st_mtime = static_cast<time_t>(st.smb2_mtime);
        out->st_atime = static_cast<time_t>(st.smb2_atime);
        out->st_ctime = static_cast<time_t>(st.smb2_ctime);
        out->st_blocks = static_cast<blkcnt_t>((st.smb2_size + 511) / 512);
        return 0;
    }

    int readDir(const std::string& path, std::vector<DirEntry>* out) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        struct smb2dir* dir = smb2_opendir(mSmb, rel(path).c_str());
        if (!dir) return mapError();

        struct smb2dirent* ent;
        while ((ent = smb2_readdir(mSmb, dir)) != nullptr) {
            if (!ent->name) continue;
            if (!strcmp(ent->name, ".") || !strcmp(ent->name, "..")) continue;
            DirEntry e;
            e.name = ent->name;
            e.isDir = (ent->st.smb2_type == SMB2_TYPE_DIRECTORY);
            e.size = ent->st.smb2_size;
            e.mtime = static_cast<time_t>(ent->st.smb2_mtime);
            out->push_back(std::move(e));
        }
        smb2_closedir(mSmb, dir);
        return 0;
    }

    int readFile(const std::string& path, char* buf, size_t size, off_t offset) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        struct smb2fh* fh = openCachedLocked(path, O_RDONLY);
        if (!fh) return mapError();

        // A short read is normal at end of file; anything negative is a real failure.
        int n = smb2_pread(mSmb, fh, reinterpret_cast<uint8_t*>(buf),
                           static_cast<uint32_t>(size), static_cast<uint64_t>(offset));
        if (n < 0) {
            // A failed read usually means the handle went stale (server dropped the session), so
            // drop it and let the next call reopen rather than failing forever.
            closeCachedLocked();
            return mapError(n);
        }
        return n;
    }

    int writeFile(const std::string& path, const char* buf, size_t size, off_t offset) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;

        struct smb2fh* fh = openCachedLocked(path, O_RDWR);
        if (!fh) return mapError();
        int n = smb2_pwrite(mSmb, fh, reinterpret_cast<const uint8_t*>(buf),
                            static_cast<uint32_t>(size), static_cast<uint64_t>(offset));
        if (n < 0) {
            closeCachedLocked();
            return mapError(n);
        }
        return n;
    }

    int createFile(const std::string& path, mode_t /*mode*/) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();   // the cached handle may be for the same path
        struct smb2fh* fh = smb2_open(mSmb, rel(path).c_str(), O_WRONLY | O_CREAT | O_TRUNC);
        if (!fh) return mapError();
        smb2_close(mSmb, fh);
        return 0;
    }

    int truncateFile(const std::string& path, off_t size) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();
        int rc = smb2_truncate(mSmb, rel(path).c_str(), static_cast<uint64_t>(size));
        return rc < 0 ? mapError(rc) : 0;
    }

    int unlinkFile(const std::string& path) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();
        int rc = smb2_unlink(mSmb, rel(path).c_str());
        return rc < 0 ? mapError(rc) : 0;
    }

    int makeDir(const std::string& path, mode_t /*mode*/) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        int rc = smb2_mkdir(mSmb, rel(path).c_str());
        return rc < 0 ? mapError(rc) : 0;
    }

    int removeDir(const std::string& path) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        int rc = smb2_rmdir(mSmb, rel(path).c_str());
        return rc < 0 ? mapError(rc) : 0;
    }

    int renamePath(const std::string& from, const std::string& to) override {
        if (mCfg.readOnly) return -EROFS;
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        closeCachedLocked();
        int rc = smb2_rename(mSmb, rel(from).c_str(), rel(to).c_str());
        return rc < 0 ? mapError(rc) : 0;
    }

    int statFs(uint64_t* totalBytes, uint64_t* freeBytes) override {
        std::lock_guard<std::mutex> lk(mLock);
        if (int rc = ensureLocked(); rc != 0) return rc;
        struct smb2_statvfs vfs = {};
        if (smb2_statvfs(mSmb, "", &vfs) < 0) return -ENOSYS;
        *totalBytes = static_cast<uint64_t>(vfs.f_blocks) * vfs.f_frsize;
        *freeBytes  = static_cast<uint64_t>(vfs.f_bavail) * vfs.f_frsize;
        return 0;
    }

    bool isDead() const override { return mDead; }

private:
    // libsmb2 wants share-relative paths with no leading slash and backslash-free components.
    static std::string rel(const std::string& path) {
        std::string p = path;
        while (!p.empty() && p.front() == '/') p.erase(p.begin());
        return p;
    }

    void fillDirStat(struct stat* out) const {
        memset(out, 0, sizeof(*out));
        out->st_mode = S_IFDIR | 0755;
        out->st_nlink = 2;
    }

    int connectLocked() {
        closeCachedLocked();
        if (mSmb) {
            smb2_disconnect_share(mSmb);
            smb2_destroy_context(mSmb);
            mSmb = nullptr;
        }
        mSmb = smb2_init_context();
        if (!mSmb) {
            ALOGE("smb2_init_context failed for %s", mCfg.name.c_str());
            return -ENOMEM;
        }
        smb2_set_security_mode(mSmb, SMB2_NEGOTIATE_SIGNING_ENABLED);
        // Bound how long a call can sit waiting. Without this a server that stops answering
        // without closing the socket (asleep, or dropped off the network) blocks the mount, and
        // since the menu is single threaded over it that reads as the whole UI hanging.
        smb2_set_timeout(mSmb, 10);
        if (!mCfg.domain.empty()) smb2_set_domain(mSmb, mCfg.domain.c_str());
        if (!mCfg.password.empty()) smb2_set_password(mSmb, mCfg.password.c_str());

        // An empty user means guest, which many home shares are set up for.
        const char* user = mCfg.user.empty() ? "guest" : mCfg.user.c_str();
        // libsmb2 takes the port as part of the server string and defaults to 445 without one
        // (see lib/socket.c), so a share on a non-standard port is expressed here.
        std::string server = mCfg.host;
        if (mCfg.port > 0 && mCfg.port != 445) server += ":" + std::to_string(mCfg.port);
        if (smb2_connect_share(mSmb, server.c_str(), mCfg.path.c_str(), user) < 0) {
            ALOGE("SMB connect to //%s/%s failed: %s", server.c_str(), mCfg.path.c_str(),
                  smb2_get_error(mSmb));
            smb2_destroy_context(mSmb);
            mSmb = nullptr;
            mDead = true;
            return -EHOSTUNREACH;
        }
        mDead = false;
        ALOGI("SMB connected //%s/%s as %s", mCfg.host.c_str(), mCfg.path.c_str(), user);
        return 0;
    }

    // Reconnect once if the session has dropped, so a NAS that sleeps does not permanently kill
    // the mount; the user should not have to re-add the share because the server took a nap.
    int ensureLocked() {
        if (mSmb && !mDead) return 0;
        return connectLocked();
    }

    struct smb2fh* openCachedLocked(const std::string& path, int flags) {
        if (mCachedFh && mCachedPath == path && mCachedFlags == flags) return mCachedFh;
        closeCachedLocked();
        struct smb2fh* fh = smb2_open(mSmb, rel(path).c_str(), flags);
        if (!fh) return nullptr;
        mCachedFh = fh;
        mCachedPath = path;
        mCachedFlags = flags;
        return fh;
    }

    void closeCachedLocked() {
        if (mCachedFh && mSmb) smb2_close(mSmb, mCachedFh);
        mCachedFh = nullptr;
        mCachedPath.clear();
        mCachedFlags = -1;
    }

    // Map a libsmb2 failure to an errno, and decide whether the session is still usable.
    //
    // The second part matters more than the first. If a dropped session is not noticed, ensureLocked
    // keeps handing back the same dead context and every later call fails forever: a NAS that sleeps
    // for a minute would leave the share broken until the device reboots. That was the behaviour
    // when this only looked for the words "Connection"/"connect"/"timeout" - a real drop reported
    // "smb2_service: POLLHUP, socket error", matched nothing, and the mount never recovered.
    //
    // So the test is inverted: a failure is assumed to have killed the session UNLESS it is one of
    // the specific, semantic errors that a healthy server returns about a file. Being wrong in that
    // direction is cheap (one needless reconnect); being wrong the other way strands the share.
    int mapError(int rc = 0) {
        const char* e = mSmb ? smb2_get_error(mSmb) : "no context";
        if (!e) e = "";

        // Errors that say something about the file, not about the connection.
        if (strstr(e, "NO_SUCH_FILE") || strstr(e, "OBJECT_NAME_NOT_FOUND") ||
            strstr(e, "PATH_NOT_FOUND")) {
            return -ENOENT;
        }
        if (strstr(e, "ACCESS_DENIED") || strstr(e, "LOGON_FAILURE")) return -EACCES;
        if (strstr(e, "NOT_A_DIRECTORY")) return -ENOTDIR;
        if (strstr(e, "FILE_IS_A_DIRECTORY")) return -EISDIR;
        if (strstr(e, "COLLISION") || strstr(e, "EXISTS")) return -EEXIST;
        if (strstr(e, "DISK_FULL")) return -ENOSPC;
        if (strstr(e, "DIRECTORY_NOT_EMPTY")) return -ENOTEMPTY;

        // The library's own return value, where it gave us one, is more precise than its message.
        if (rc < 0 && rc != -1) {
            switch (-rc) {
                case ENOENT: case EACCES: case EEXIST: case ENOTDIR:
                case EISDIR: case ENOSPC: case ENOTEMPTY:
                    return rc;      // semantic, session is fine
                default: break;     // anything else is treated as a lost session below
            }
        }

        // Everything else: assume the session is gone so the next call reconnects.
        ALOGW("SMB error on %s, dropping the session to force a reconnect: %s",
              mCfg.name.c_str(), e[0] ? e : "(no detail)");
        mDead = true;
        return rc < 0 && rc != -1 ? rc : -EIO;
    }

    ShareConfig mCfg;
    std::mutex  mLock;
    struct smb2_context* mSmb = nullptr;
    bool mDead = false;

    struct smb2fh* mCachedFh = nullptr;
    std::string    mCachedPath;
    int            mCachedFlags = -1;
};

}  // namespace

Backend* makeSmbBackend(const ShareConfig& cfg) { return new SmbBackend(cfg); }

}  // namespace sharefs
}  // namespace gammaos
