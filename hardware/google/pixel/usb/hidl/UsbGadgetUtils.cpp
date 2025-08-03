/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "libpixelusb-hidl"

#include <android-base/file.h>
#include <android-base/properties.h>
#include <utils/Log.h>

#include "hidl/include/pixelusb/UsbGadgetCommon.h"

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

using ::android::base::GetProperty;
using ::android::base::SetProperty;
using ::android::base::WriteStringToFile;
using ::android::hardware::usb::gadget::V1_0::GadgetFunction;

Status setVidPid(const char *vid, const char *pid) {
    return setVidPidCommon(vid, pid) ? Status::SUCCESS : Status::ERROR;
}

Status resetGadget() {
    return resetGadgetCommon() ? Status::SUCCESS : Status::ERROR;
}

Status addGenericAndroidFunctions(MonitorFfs *monitorFfs, uint64_t functions, bool *ffsEnabled,
                                  int *functionCount) {
    if (((functions & GadgetFunction::MTP) != 0)) {
        *ffsEnabled = true;
        ALOGI("setCurrentUsbFunctions mtp");
        if (!WriteStringToFile("1", DESC_USE_PATH))
            return Status::ERROR;

        if (!monitorFfs->addInotifyFd("/dev/usb-ffs/mtp/"))
            return Status::ERROR;

        if (linkFunction("ffs.mtp", (*functionCount)++))
            return Status::ERROR;

        // Add endpoints to be monitored.
        monitorFfs->addEndPoint("/dev/usb-ffs/mtp/ep1");
        monitorFfs->addEndPoint("/dev/usb-ffs/mtp/ep2");
        monitorFfs->addEndPoint("/dev/usb-ffs/mtp/ep3");
    } else if (((functions & GadgetFunction::PTP) != 0)) {
        *ffsEnabled = true;
        ALOGI("setCurrentUsbFunctions ptp");
        if (!WriteStringToFile("1", DESC_USE_PATH))
            return Status::ERROR;

        if (!monitorFfs->addInotifyFd("/dev/usb-ffs/ptp/"))
            return Status::ERROR;

        if (linkFunction("ffs.ptp", (*functionCount)++))
            return Status::ERROR;

        // Add endpoints to be monitored.
        monitorFfs->addEndPoint("/dev/usb-ffs/ptp/ep1");
        monitorFfs->addEndPoint("/dev/usb-ffs/ptp/ep2");
        monitorFfs->addEndPoint("/dev/usb-ffs/ptp/ep3");
    }

    if ((functions & GadgetFunction::MIDI) != 0) {
        ALOGI("setCurrentUsbFunctions MIDI");
        if (linkFunction("midi.gs5", (*functionCount)++))
            return Status::ERROR;
    }

    if ((functions & GadgetFunction::ACCESSORY) != 0) {
        ALOGI("setCurrentUsbFunctions Accessory");
        if (linkFunction("accessory.gs2", (*functionCount)++))
            return Status::ERROR;
    }

    if ((functions & GadgetFunction::AUDIO_SOURCE) != 0) {
        ALOGI("setCurrentUsbFunctions Audio Source");
        if (linkFunction("audio_source.gs3", (*functionCount)++))
            return Status::ERROR;
    }

    if ((functions & GadgetFunction::RNDIS) != 0) {
        ALOGI("setCurrentUsbFunctions rndis");
        std::string rndisFunction = GetProperty(kVendorRndisConfig, "");
        if (rndisFunction != "") {
            if (linkFunction(rndisFunction.c_str(), (*functionCount)++))
                return Status::ERROR;
        } else {
            // link gsi.rndis for older pixel projects
            if (linkFunction("gsi.rndis", (*functionCount)++))
                return Status::ERROR;
        }
    }

    return Status::SUCCESS;
}

Status addAdb(MonitorFfs *monitorFfs, int *functionCount) {
    ALOGI("setCurrentUsbFunctions Adb");
    if (!WriteStringToFile("1", DESC_USE_PATH))
        return Status::ERROR;

    if (!monitorFfs->addInotifyFd("/dev/usb-ffs/adb/"))
        return Status::ERROR;

    if (linkFunction("ffs.adb", (*functionCount)++))
        return Status::ERROR;

    monitorFfs->addEndPoint("/dev/usb-ffs/adb/ep1");
    monitorFfs->addEndPoint("/dev/usb-ffs/adb/ep2");
    ALOGI("Service started");
    return Status::SUCCESS;
}

}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
