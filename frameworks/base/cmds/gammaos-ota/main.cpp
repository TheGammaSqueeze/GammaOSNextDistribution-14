/*
 * Copyright (C) 2026 GammaOS
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

#define LOG_TAG "GammaOSOta"

#include <stdint.h>
#include <inttypes.h>
#include <string.h>
#include <sys/stat.h>

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <cutils/properties.h>
#include <android-base/properties.h>
#include <sys/resource.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "OtaMenu.h"
#include "OtaFlasher.h"

using namespace android;

// Wait for SurfaceFlinger to be available
static void waitForSurfaceFlinger() {
    sp<IServiceManager> sm = defaultServiceManager();
    const String16 name("SurfaceFlinger");
    int retry = 0;
    while (sm->checkService(name) == nullptr) {
        retry++;
        if ((retry % 100) == 0) {
            ALOGW("Waiting for SurfaceFlinger...");
        }
        usleep(10000);
    }
}

int main(int argc, char** argv) {
    setpriority(PRIO_PROCESS, 0, ANDROID_PRIORITY_DISPLAY);

    ALOGI("GammaOS OTA starting...");

    // Check if we need to stage to tmpfs first
    if (!OtaFlasher::isRunningFromTmpfs()) {
        ALOGI("Not yet staged to tmpfs — staging now...");
        OtaFlasher flasher;
        // stageToTmpfs will re-exec. If it returns, staging failed.
        if (!flasher.stageToTmpfs(argc, argv)) {
            ALOGE("Failed to stage to tmpfs! Continuing from system (risky).");
            // Continue anyway — better to try than to fail silently
        }
    }

    ALOGI("Running from tmpfs: %s", OtaFlasher::isRunningFromTmpfs() ? "yes" : "no");

    // Read package path and autoinstall flag from system properties
    std::string packagePath = android::base::GetProperty("sys.gammaos.ota.package", "");
    std::string autoInstall = android::base::GetProperty("sys.gammaos.ota.autoinstall", "0");

    // Guard: if no package path and no autoinstall, check if we have anything to do.
    // This prevents the service from running after a reboot if it was spuriously started.
    if (packagePath.empty() && autoInstall != "1") {
        // Check if there's an extracted package ready
        struct stat st;
        if (stat("/data/gammaos_ota/package/manifest.json", &st) != 0) {
            ALOGI("No package to install and no autoinstall flag — exiting.");
            return 0;
        }
    }

    sp<ProcessState> proc(ProcessState::self());
    ProcessState::self()->startThreadPool();

    // OtaMenu runs as a Thread, matching NanoMenu pattern
    sp<OtaMenu> menu(new OtaMenu());
    if (!packagePath.empty()) {
        menu->setPackagePath(packagePath);
    }

    waitForSurfaceFlinger();

    menu->run("GammaOSOta", PRIORITY_DISPLAY);

    ALOGI("GammaOS OTA running. Joining thread pool.");
    IPCThreadState::self()->joinThreadPool();

    return 0;
}
