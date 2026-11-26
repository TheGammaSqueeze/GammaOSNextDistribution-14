/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <cutils/properties.h>
#include <fcntl.h>
#include <log/log.h>
#include <string.h>
#include <unistd.h>
#include "StartPropertySetThread.h"

namespace android {

// GammaOS: a Quick Resume boot starts the game before SurfaceFlinger is up:
// the nano home launches the emulator (an init service that drives the panels
// through DRM itself) and sets sys.gammaos.nano.native_session. The property
// alone is not enough, it only counts while one of the services is running.
static bool nativeEmulatorOwnsPanels() {
    char value[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.native_session", value, "0");
    if (strcmp(value, "1") != 0) {
        return false;
    }
    static const char* const kServices[] = {
        "init.svc.retroarch-nano",
        "init.svc.melonds-nano",
        "init.svc.drastic-nano",
        "init.svc.azahar-nano",
    };
    for (const char* service : kServices) {
        char state[PROPERTY_VALUE_MAX] = {};
        property_get(service, state, "");
        if (strcmp(state, "running") == 0) {
            return true;
        }
    }
    return false;
}

StartPropertySetThread::StartPropertySetThread(bool timestampPropertyValue):
        Thread(false), mTimestampPropertyValue(timestampPropertyValue) {}

status_t StartPropertySetThread::Start() {
    return run("SurfaceFlinger::StartPropertySetThread", PRIORITY_NORMAL);
}

bool StartPropertySetThread::threadLoop() {
    // Set property service.sf.present_timestamp, consumer need check its readiness
    property_set(kTimestampProperty, mTimestampPropertyValue ? "1" : "0");
    // Clear BootAnimation exit flag
    property_set("service.bootanim.exit", "0");
    property_set("service.bootanim.progress", "0");

    // GammaOS: Decide between starting gammaos-nano (nano boot) or the
    // stock bootanim (normal Android boot).
    //
    // Fast path: read /data/misc/bootanim/nano_skip ("0" = nano,
    // "1" = bootanim). The on-device nano action dispatchers in
    // init.rc keep this file in sync with persist.bootanim.skip_nano,
    // and /data/misc/bootanim is DE storage so it is readable as soon
    // as /data is mounted - no FBE unlock or persistent_properties.ready
    // wait required.
    //
    // Slow path: file missing or malformed (first boot, factory reset).
    // Fall back to the persist property with a 10 s wait on
    // ro.persistent_properties.ready=true. Anything other than "0"
    // (including empty) routes to bootanim so a "Boot Android" handoff
    // never lands back in the menu.
    bool decided = false;
    bool wantBootanim = false;
    // An image built without the nano home (ro.gammaos.nano.available is
    // set only with it) always shows bootanim: a nano choice left on /data
    // by another image must not leave the screen without anything to draw.
    {
        char available[PROPERTY_VALUE_MAX] = {};
        property_get("ro.gammaos.nano.available", available, "");
        if (strcmp(available, "true") != 0) {
            decided = true;
            wantBootanim = true;
        }
    }
    if (!decided) {
        int fd = open("/data/misc/bootanim/nano_skip",
                      O_RDONLY | O_CLOEXEC);
        if (fd >= 0) {
            char buf[8] = {};
            ssize_t n = read(fd, buf, sizeof(buf) - 1);
            close(fd);
            if (n > 0) {
                for (ssize_t i = 0; i < n; i++) {
                    if (buf[i] == '\n' || buf[i] == '\r' ||
                        buf[i] == ' ') { buf[i] = 0; break; }
                }
                if (strcmp(buf, "0") == 0) {
                    decided = true;
                } else if (strcmp(buf, "1") == 0) {
                    decided = true;
                    wantBootanim = true;
                }
            }
        }
    }

    if (!decided) {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            for (int i = 0; i < 1000; i++) { // up to 10 s
                usleep(10000);
                property_get("ro.persistent_properties.ready", ready, "");
                if (strcmp(ready, "true") == 0) break;
            }
        }
        char skipNano[PROPERTY_VALUE_MAX] = {};
        property_get("persist.bootanim.skip_nano", skipNano, "");
        if (strcmp(skipNano, "0") != 0) {
            wantBootanim = true;
        }
    }

    if (wantBootanim) {
        property_set("ctl.start", "bootanim");
    } else if (nativeEmulatorOwnsPanels()) {
        // The home is not started over the game: it would fight it for the
        // display. The emulator starts the home itself when the game ends.
        ALOGI("GammaOS: a native emulator session is live, not starting gammaos-nano");
    } else {
        // SELinux: ctl.gammaos-nano is mapped to ctl_bootanim_prop in
        // property_contexts.
        property_set("ctl.start", "gammaos-nano");
    }
    // Exit immediately
    return false;
}

} // namespace android
