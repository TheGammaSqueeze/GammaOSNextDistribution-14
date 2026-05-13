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
    // Default to nano. Only an explicit "1" in persist.bootanim.skip_nano
    // selects the stock bootanim path. An empty string (race with
    // load_persist_props), "0", or any other value falls into the nano
    // bucket. This mirrors NanoMenu::readyToRun()'s defensive logic and
    // closes the second race we hit on the A133 / TrimUI Brick where:
    //
    //   1. init.rc fires `start gammaos-nano` from the
    //      `surfaceflinger=running && skip_nano=0` trigger and the
    //      first NanoMenu instance comes up.
    //   2. NanoMenu reads skip_nano and (depending on SELinux property
    //      label access for the bootanim domain) sometimes still sees
    //      an empty string, used to bail to bootanim — we patched that
    //      out above.
    //   3. SurfaceFlinger's StartPropertySetThread ALSO runs and used
    //      to require strcmp(skipNano, "0") == 0 to take the nano
    //      branch. If persistent props slipped in late, skipNano was
    //      "" and SF started the stock bootanim — even though the
    //      device was supposed to be in nano mode. The init.rc trigger
    //      had already started gammaos-nano (no-op since it's oneshot),
    //      so the result was both NanoMenu AND bootanim fighting for
    //      the panel.
    //
    // First-boot (no /data, prop genuinely never set) still wants the
    // Android setup wizard path — but on this build first-boot is
    // expected to be a fresh image with persistent props pre-populated
    // by the factory image, and the on-device "Boot Android" action
    // sets persist.bootanim.skip_nano=1 explicitly. So defaulting to
    // nano on the empty case here matches user expectation and removes
    // the bootanim flash before the menu.
    {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            for (int i = 0; i < 200; i++) { // bounded 2s for sanity only
                usleep(10000);
                property_get("ro.persistent_properties.ready", ready, "");
                if (strcmp(ready, "true") == 0) break;
            }
        }
    }

    char skipNano[PROPERTY_VALUE_MAX] = {};
    property_get("persist.bootanim.skip_nano", skipNano, "");
    if (strcmp(skipNano, "1") == 0) {
        // Explicit normal Android boot — start stock boot animation.
        property_set("ctl.start", "bootanim");
    } else {
        // Nano boot mode active (skip_nano in {"0", ""} or unset) —
        // gammaos-nano takes over the boot sequence and manages bootanim
        // internally if it ever needs the legacy path.
        // SELinux: ctl.gammaos-nano is mapped to ctl_bootanim_prop in property_contexts.
        property_set("ctl.start", "gammaos-nano");
    }
    // Exit immediately
    return false;
}

} // namespace android
