/*
 * Copyright (C) 2026 GammaOS
 *
 * Entry point for gammaos-sharefs.
 *
 * The daemon is started per share by init:
 *     gammaos-sharefs --share <name> [--debug]
 * It looks that share up in the persist.gammaos.share.<n>.* properties (see share_config.cpp),
 * decrypts its password and mounts it at /mnt/shares/<name>. init restarts it if it dies, so a NAS
 * that goes away and comes back recovers on its own. Those same properties are what the nano menu,
 * Settings and TvSettings all edit, so there is one definition of a share no matter which UI
 * added it.
 */

#define LOG_TAG "gammaos-sharefs"

#include <errno.h>
#include <log/log.h>
#include <string.h>
#include <sys/mount.h>   // umount2, MNT_DETACH
#include <sys/stat.h>
#include <sys/types.h>

#include <string>
#include <vector>

#include "sharefs.h"

using namespace gammaos::sharefs;

namespace {

// Is this path a mount point with no daemon behind it?
//
// A live share and a dead one are both listed in the mount table, so the mount table alone cannot
// tell them apart. What distinguishes them is that a dead FUSE mount fails every operation with
// ECONNREFUSED (or ENOTCONN), which is exactly what libfuse trips over when it tries to mount
// there again.
bool isStaleFuseMount(const std::string& path) {
    struct stat st;
    if (stat(path.c_str(), &st) == 0) return false;   // responding, so something is serving it
    return errno == ECONNREFUSED || errno == ENOTCONN;
}

void forceUnmountStale(const std::string& path) {
    if (!isStaleFuseMount(path)) return;
    ALOGW("%s is a stale mount from a previous instance, clearing it", path.c_str());
    // MNT_DETACH rather than a plain unmount: the mount has no server, so anything still holding a
    // reference would make a normal unmount fail with EBUSY forever.
    if (umount2(path.c_str(), MNT_DETACH) != 0) {
        ALOGE("could not clear stale mount %s: %s", path.c_str(), strerror(errno));
    }
}

}  // namespace

int main(int argc, char** argv) {
    std::string want;
    bool debug = false;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--share") && i + 1 < argc) want = argv[++i];
        else if (!strcmp(argv[i], "--debug")) debug = true;
    }
    if (want.empty()) {
        ALOGE("usage: gammaos-sharefs --share <name> [--debug]");
        return 1;
    }

    for (ShareConfig& c : loadShares()) {
        if (c.name != want) continue;

        const std::string problem = shareProblem(c);
        if (!problem.empty()) {
            // Refusing here beats mounting something unusable: init would otherwise restart us in
            // a loop against a share that can never work.
            ALOGE("share '%s' is not usable: %s", c.name.c_str(), problem.c_str());
            return 1;
        }

        const std::string mnt = "/mnt/shares/" + c.name;

        // Clear a stale mount left by a previous instance before trying to mount over it.
        //
        // This matters more than it looks. If the daemon dies without unmounting - killed for
        // memory, crashed, or the device went down hard - the FUSE mount stays in the kernel with
        // nothing serving it, and every access returns ECONNREFUSED. libfuse then refuses to mount
        // over it ("bad mount point ... Connection refused"), so init restarts us in a loop and the
        // share never comes back until someone unmounts it by hand. Since init restarting us IS the
        // recovery mechanism, that would turn any one-off crash into a permanently dead share.
        forceUnmountStale(mnt);
        // init cannot create this because the share names are user-chosen, so make it here. 0771
        // with the media_rw group matches how removable storage is presented.
        if (mkdir("/mnt/shares", 0771) != 0 && errno != EEXIST) {
            ALOGE("cannot create /mnt/shares: %s", strerror(errno));
            return 1;
        }
        if (mkdir(mnt.c_str(), 0771) != 0 && errno != EEXIST) {
            ALOGE("cannot create %s: %s", mnt.c_str(), strerror(errno));
            return 1;
        }
        return runMount(c, mnt, debug);
    }

    ALOGE("no share named '%s' in the configuration", want.c_str());
    return 1;
}
