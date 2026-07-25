/*
 * Copyright (C) 2026 GammaOS
 *
 * gammaos-sharefs: presents a network share as an ordinary directory.
 *
 * The devices this runs on have no cifs or nfs in the kernel and the kernel cannot be replaced, so
 * a share cannot be mounted the usual way. Instead this daemon speaks the share protocol itself in
 * userspace and exposes the result through FUSE, which the kernel does have (the sdcard volume is
 * already a FUSE mount). Everything above it - the nano file browser, the photo/music/video
 * scanners, the media players, and copying between local and remote - then works on a normal path
 * with no protocol knowledge at all.
 *
 * One process serves one share, mounted at /mnt/shares/<name>. Running a process per share keeps a
 * dead or unreachable server from taking the others down with it.
 */

#ifndef GAMMAOS_SHAREFS_H
#define GAMMAOS_SHAREFS_H

#include <sys/stat.h>

#include <cstdint>
#include <string>
#include <vector>

// ShareType / ShareConfig / the property schema / credential encoding. Shared with the nano menu,
// which links share_config.cpp so both write a share identically.
#include "share_config.h"

namespace gammaos {
namespace sharefs {

// One entry of a directory listing.
struct DirEntry {
    std::string name;
    bool        isDir = false;
    uint64_t    size = 0;
    time_t      mtime = 0;
};

/*
 * A protocol backend. Paths handed to these are always share-relative and start with '/'.
 *
 * Implementations must be safe to call from several FUSE worker threads at once; the simplest way
 * to satisfy that (and what the backends here do) is to serialise on their own connection lock,
 * because these are single-user home shares where throughput is bounded by the network anyway.
 *
 * Every call returns 0 on success or a negative errno, which is exactly what FUSE wants back.
 */
class Backend {
public:
    virtual ~Backend() = default;

    // Establish the connection. Called once before the mount is published.
    virtual int connect() = 0;
    virtual void disconnect() = 0;

    virtual int getAttr(const std::string& path, struct stat* out) = 0;
    virtual int readDir(const std::string& path, std::vector<DirEntry>* out) = 0;

    // Read up to size bytes at offset. Returns the byte count, or a negative errno.
    virtual int readFile(const std::string& path, char* buf, size_t size, off_t offset) = 0;

    // Write support is optional: a backend that cannot do it returns -EROFS and the share simply
    // behaves read-only, which is still useful for playing media off a NAS.
    virtual int writeFile(const std::string& path, const char* buf, size_t size, off_t offset) = 0;

    // Called when a file the caller was writing is closed.
    //
    // WebDAV and FTP can only replace a file whole (PUT / STOR), but FUSE hands a write down in
    // chunks at increasing offsets, so those backends stage the chunks and send the result here.
    // Backends that can write at an offset directly, like SMB and NFS, have nothing to do.
    virtual int flushFile(const std::string& /*path*/) { return 0; }
    virtual int createFile(const std::string& path, mode_t mode) = 0;
    virtual int truncateFile(const std::string& path, off_t size) = 0;
    virtual int unlinkFile(const std::string& path) = 0;
    virtual int makeDir(const std::string& path, mode_t mode) = 0;
    virtual int removeDir(const std::string& path) = 0;
    virtual int renamePath(const std::string& from, const std::string& to) = 0;

    // Free space, for the file browser's copy checks. Returning -ENOSYS is fine.
    virtual int statFs(uint64_t* totalBytes, uint64_t* freeBytes) = 0;

    // True when the connection has dropped and the mount should be torn down.
    virtual bool isDead() const = 0;
};

Backend* makeSmbBackend(const ShareConfig& cfg);
Backend* makeWebdavBackend(const ShareConfig& cfg);
Backend* makeFtpBackend(const ShareConfig& cfg);
Backend* makeNfsBackend(const ShareConfig& cfg);

// Run the FUSE loop for one share. Blocks until unmounted. Returns a process exit code.
int runMount(const ShareConfig& cfg, const std::string& mountPoint, bool debug);

}  // namespace sharefs
}  // namespace gammaos

#endif  // GAMMAOS_SHAREFS_H
