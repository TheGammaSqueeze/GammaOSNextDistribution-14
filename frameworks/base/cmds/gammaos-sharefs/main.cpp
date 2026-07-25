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
#include <unistd.h>   // usleep

#include <string>
#include <thread>
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

// Where the share is bind-mounted so apps can reach it by path.
//
// /storage is a bind of /mnt/user/0, so binding here is what makes the share turn up as
// /storage/<name>. Binding into /mnt/user/0 rather than /storage directly is deliberate: the kernel
// propagates it from there to /storage, /mnt/androidwritable/0 and /mnt/installer/0 on its own,
// which is exactly the set of views an app might be handed.
std::string userBindPath(const std::string& name) {
    return "/mnt/user/0/" + name;
}

// Make a share reachable by path, not just through the Storage Access Framework.
//
// This is what lets an app that takes a filename - every emulator on this device, among others -
// open a ROM or a video that lives on a NAS. Those apps cannot use a content:// URI, so a
// DocumentsProvider alone would leave the feature unusable for the case it most exists for.
//
// The share content is labelled fuse:, and so is /storage/emulated on this device, so the existing
// app policy covers it: an app reads a share through exactly the same rules it reads internal
// storage with, and cannot tell them apart.
void publishToStorage(const std::string& name, const std::string& source) {
    const std::string target = userBindPath(name);
    if (mkdir(target.c_str(), 0771) != 0 && errno != EEXIST) {
        ALOGW("cannot create %s, the share will not appear under /storage: %s",
              target.c_str(), strerror(errno));
        return;
    }
    if (mount(source.c_str(), target.c_str(), nullptr, MS_BIND, nullptr) != 0) {
        ALOGW("cannot bind %s to %s, the share will not appear under /storage: %s",
              source.c_str(), target.c_str(), strerror(errno));
        return;
    }
    // Share the bind so it reaches app mount namespaces rather than staying in this one.
    if (mount(nullptr, target.c_str(), nullptr, MS_SHARED, nullptr) != 0) {
        ALOGW("%s is mounted but not shared, apps started earlier may not see it: %s",
              target.c_str(), strerror(errno));
    }
    ALOGI("share '%s' also available at /storage/%s", name.c_str(), name.c_str());
}

// Take the bind down. Called before the share itself is unmounted, so nothing is left pointing at
// a mount that has gone.
void unpublishFromStorage(const std::string& name) {
    const std::string target = userBindPath(name);
    struct stat st;
    if (stat(target.c_str(), &st) != 0 && errno != ECONNREFUSED && errno != ENOTCONN) return;
    umount2(target.c_str(), MNT_DETACH);
    rmdir(target.c_str());
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
        // A stale bind from a previous instance would otherwise shadow the new mount.
        unpublishFromStorage(c.name);
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
        // Publishing has to happen after the FUSE mount exists, and runMount blocks for the life
        // of the share, so do it from a helper thread that waits for the mount to appear. Binding
        // an empty directory would give apps a permanently empty share.
        std::thread([name = c.name, mnt]() {
            for (int i = 0; i < 60; i++) {
                struct stat st;
                if (stat(mnt.c_str(), &st) == 0 && st.st_ino == 1) {
                    // st_ino 1 is the FUSE root, so the daemon is serving it rather than this
                    // being the bare directory it created earlier.
                    publishToStorage(name, mnt);
                    return;
                }
                usleep(250 * 1000);
            }
            ALOGW("share '%s' never became a live mount, not publishing it to /storage",
                  name.c_str());
        }).detach();

        const int rc = runMount(c, mnt, debug);
        unpublishFromStorage(c.name);
        return rc;
    }

    ALOGE("no share named '%s' in the configuration", want.c_str());
    return 1;
}
