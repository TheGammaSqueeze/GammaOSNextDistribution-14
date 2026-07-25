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
#include <sys/stat.h>
#include <sys/types.h>

#include <string>
#include <vector>

#include "sharefs.h"

using namespace gammaos::sharefs;

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
