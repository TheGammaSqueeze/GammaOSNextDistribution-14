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
#include <string.h>
#include <unistd.h>
#include "StartPropertySetThread.h"

namespace android {

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

    // GammaOS: Decide between starting gammaos-nano (nano boot) or the stock
    // bootanim (normal Android boot) based on persist.bootanim.skip_nano.
    //
    // Persist props are loaded from /data shortly after mount, signaled via
    // ro.persistent_properties.ready=true. At the moment this thread runs, they
    // MAY not be available yet — reading persist.bootanim.skip_nano directly
    // would return an empty string and we'd misidentify the boot mode. We wait
    // here (up to 5s, typically ~10ms) for init to signal readiness so the
    // decision is reliable.
    //
    // This wait is safe because StartPropertySetThread is intentionally a
    // separate thread (see header comment + b/34499826) so SurfaceFlinger's
    // init is not blocked by property_set calls.
    //
    // In normal Android boot we MUST start stock bootanim and NOT gammaos-nano.
    // If gammaos-nano starts, it will grab DRM master via drmEarlySplash() and
    // wipe the bootloader logo to black before handing off to bootanim — the
    // user sees several seconds of black screen between bootloader and bootanim.
    {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            for (int i = 0; i < 500; i++) { // max 5s
                usleep(10000); // 10ms
                property_get("ro.persistent_properties.ready", ready, "");
                if (strcmp(ready, "true") == 0) break;
            }
        }
    }

    char skipNano[PROPERTY_VALUE_MAX] = {};
    property_get("persist.bootanim.skip_nano", skipNano, "");
    if (strcmp(skipNano, "0") == 0) {
        // Nano boot mode active — gammaos-nano takes over the boot sequence
        // (it manages bootanim internally).
        // SELinux: ctl.gammaos-nano is mapped to ctl_bootanim_prop in property_contexts.
        property_set("ctl.start", "gammaos-nano");
    } else {
        // Normal Android boot, first boot (empty prop), or explicit skip_nano=1
        // — start the stock boot animation. gammaos-nano must NOT run here
        // because it would take over the DRM master during early boot.
        property_set("ctl.start", "bootanim");
    }
    // Exit immediately
    return false;
}

} // namespace android
