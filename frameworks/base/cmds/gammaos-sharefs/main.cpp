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

#include <dirent.h>
#include <errno.h>
#include <log/log.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/mount.h>   // umount2, MNT_DETACH
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>   // usleep, fork

#include <algorithm>
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
//
// The stat runs in a child process because it is the one call here that can block indefinitely. A
// mount whose daemon is gone answers immediately, but one whose daemon is alive and waiting on a
// NAS that has stopped responding does not answer at all, and doing this inline would hang the
// caller on precisely the share it must not touch. A child can simply be killed and, since being
// unable to decide means "leave it alone", timing out gives the safe answer rather than the
// dangerous one: treating a live share as stale would unmount it out from under whatever is
// using it.
bool isStaleFuseMount(const std::string& path) {
    const pid_t pid = fork();
    if (pid < 0) return false;
    if (pid == 0) {
        struct stat st;
        if (stat(path.c_str(), &st) == 0) _exit(0);   // responding, so something is serving it
        _exit(errno == ECONNREFUSED || errno == ENOTCONN ? 2 : 3);
    }
    for (int i = 0; i < 40; i++) {   // up to ~2s
        int status = 0;
        const pid_t r = waitpid(pid, &status, WNOHANG);
        if (r == pid) return WIFEXITED(status) && WEXITSTATUS(status) == 2;
        if (r < 0) return false;
        usleep(50 * 1000);
    }
    kill(pid, SIGKILL);
    waitpid(pid, nullptr, 0);
    return false;   // it blocked, so something is still serving it
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

// A mount point as the kernel spells it, with mountinfo's octal escapes undone. Share names are
// user-chosen and the defaults contain a space, so \040 has to be decoded or nothing matches.
std::string unescapeMountPoint(const std::string& s) {
    std::string out;
    for (size_t i = 0; i < s.size(); i++) {
        if (s[i] == '\\' && i + 3 < s.size() && s[i + 1] >= '0' && s[i + 1] <= '7') {
            out.push_back(static_cast<char>((s[i + 1] - '0') * 64 + (s[i + 2] - '0') * 8 +
                                            (s[i + 3] - '0')));
            i += 3;
        } else {
            out.push_back(s[i]);
        }
    }
    return out;
}

// Every share currently in the mount table, by name.
std::vector<std::string> mountedShareNames() {
    std::vector<std::string> names;
    FILE* f = fopen("/proc/self/mountinfo", "re");
    if (!f) return names;
    char line[2048];
    while (fgets(line, sizeof(line), f)) {
        // Field 5 (1-based) is the mount point; the filesystem type follows the " - " separator.
        int field = 0;
        const char* p = line;
        const char* mpStart = nullptr;
        size_t mpLen = 0;
        while (*p) {
            const char* start = p;
            while (*p && *p != ' ') p++;
            if (++field == 5) { mpStart = start; mpLen = static_cast<size_t>(p - start); break; }
            while (*p == ' ') p++;
        }
        if (!mpStart) continue;
        if (!strstr(line, "fuse.gammaos-sharefs")) continue;
        const std::string mp = unescapeMountPoint(std::string(mpStart, mpLen));
        const std::string prefix = "/mnt/shares/";
        if (mp.compare(0, prefix.size(), prefix) != 0) continue;
        const std::string name = mp.substr(prefix.size());
        // Only the share's own mount point, nothing nested below it.
        if (name.empty() || name.find('/') != std::string::npos) continue;
        if (std::find(names.begin(), names.end(), name) == names.end()) names.push_back(name);
    }
    fclose(f);
    return names;
}

// Remove any share mount that no longer has a daemon behind it.
//
// This takes no share name on purpose. Passing one in looked simpler, but deleteShare() clears the
// share's properties immediately after setting enabled=0, and init expands ${...name} for the
// cleanup service asynchronously, so the name is racing against being wiped and the cleanup would
// sometimes be handed an empty string. Reading the mount table instead cannot get the name wrong,
// covers a daemon that crashed rather than being stopped, and tidies several shares at once.
//
// init stops a service with SIGKILL (Service::StopOrReset), which cannot be caught, so the daemon
// does not get to run fuse_main's unmount when a share is switched off. Left alone, the FUSE mount
// and its /storage bind stayed behind with nothing serving them and /storage/<name> remained
// visible to apps as a directory that failed every access, which is worse than the share simply
// being gone. gentle_kill in the .rc gives the daemon a chance to exit cleanly first; this is what
// makes the outcome certain.
//
// The retry loop is because "stop" is asynchronous: the process may still be alive on the first
// pass, and tearing down a mount that is about to be torn down cleanly is not something to rush.
// Anything still being served is simply left alone, so running this while other shares are up is
// harmless, which matters because one cleanup serves all four slots.
void cleanupStaleShares() {
    // A few passes rather than one, because "stop" is asynchronous and the daemon may still be
    // alive on the first look. Quiet passes are counted rather than live shares: a share that is
    // simply up is the normal case and must not keep this spinning, whereas a share that is on its
    // way out shows up as stale a moment later. Two consecutive passes with nothing to do means
    // the situation has settled.
    int quiet = 0;
    for (int attempt = 0; attempt < 6 && quiet < 2; attempt++) {
        if (attempt > 0) usleep(500 * 1000);
        const std::vector<std::string> names = mountedShareNames();
        if (names.empty()) return;
        bool removedAny = false;
        for (const std::string& name : names) {
            if (!isStaleFuseMount("/mnt/shares/" + name)) continue;   // still served, leave it
            const std::string bind = userBindPath(name);
            const std::string mnt = "/mnt/shares/" + name;
            // MNT_DETACH rather than a plain unmount: there is no server left, so anything still
            // holding a reference would make a normal unmount fail with EBUSY forever. The bind
            // goes first so nothing is left pointing at a mount that has already gone.
            umount2(bind.c_str(), MNT_DETACH);
            rmdir(bind.c_str());
            umount2(mnt.c_str(), MNT_DETACH);
            rmdir(mnt.c_str());
            ALOGI("removed the leftover mount for share '%s'", name.c_str());
            removedAny = true;
        }
        quiet = removedAny ? 0 : quiet + 1;
    }
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
    bool cleanup = false;
    bool debug = false;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--share") && i + 1 < argc) want = argv[++i];
        else if (!strcmp(argv[i], "--cleanup")) cleanup = true;
        else if (!strcmp(argv[i], "--debug")) debug = true;
    }

    if (cleanup) {
        cleanupStaleShares();
        return 0;
    }

    if (want.empty()) {
        ALOGE("usage: gammaos-sharefs --share <name> [--debug]");
        ALOGE("       gammaos-sharefs --cleanup");
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
