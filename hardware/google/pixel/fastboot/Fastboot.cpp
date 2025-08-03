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

#include "fastboot/Fastboot.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <dlfcn.h>

#include <map>
#include <string>
#include <unordered_map>
#include <vector>

// FS headers
#include <ext4_utils/wipe.h>
#include <fs_mgr.h>
#include <fs_mgr/roots.h>

// Nugget headers
#include <app_nugget.h>
#include <nos/NuggetClient.h>
#include <nos/debug.h>

namespace android {
namespace hardware {
namespace fastboot {
namespace V1_1 {
namespace implementation {

constexpr const char* BRIGHTNESS_FILE = "/sys/class/backlight/panel0-backlight/brightness";
constexpr int DISPLAY_BRIGHTNESS_DIM_THRESHOLD = 20;

using  OEMCommandHandler = std::function<Result(const std::vector<std::string>&)>;

Return<void> Fastboot::getPartitionType(const ::android::hardware::hidl_string& /* partitionName */,
                                  getPartitionType_cb _hidl_cb) {
    // For bluecross devices, all partitions need to return raw.
    _hidl_cb(FileSystemType::RAW, { Status::SUCCESS, "" });
    return Void();
}

Return<void> Fastboot::getVariant(getVariant_cb _hidl_cb) {
    _hidl_cb("MSM USF", {Status::SUCCESS, "" });
    return Void();
}

Return<void> Fastboot::getOffModeChargeState(getOffModeChargeState_cb _hidl_cb) {
    constexpr const char* kDevinfoPath = "/dev/block/by-name/devinfo";
    constexpr int kDevInfoOffModeChargeOffset = 15;

    uint8_t off_mode_charge_status = 0;
    android::base::unique_fd fd(TEMP_FAILURE_RETRY(open(kDevinfoPath,
                                                        O_RDONLY | O_BINARY)));
    if (!android::base::ReadFullyAtOffset(fd, &off_mode_charge_status, 1 /* byte count */,
                                          kDevInfoOffModeChargeOffset)) {
        _hidl_cb(false,
                 { Status::FAILURE_UNKNOWN, "Unable to read off-mode-charge state" });
    } else {
        _hidl_cb(off_mode_charge_status != 0, { Status::SUCCESS, "" });
    }

    return Void();
}

Return<void> Fastboot::getBatteryVoltageFlashingThreshold(
        getBatteryVoltageFlashingThreshold_cb _hidl_cb) {
    constexpr int kMinVoltageForFlashing = 3500;
    _hidl_cb(kMinVoltageForFlashing, { Status::SUCCESS, "" });
    return Void();
}

Result SetBrightnessLevel(const std::vector<std::string>& args) {
    if (!args.size()) {
        return { Status::INVALID_ARGUMENT, "Brightness level unspecified" };
    }

    auto level = std::stoi(args[0]);

    if (level < 0 || level > 100)  {
        return { Status::INVALID_ARGUMENT, "Brighness level must be between 0 and 100" };
    }

    // Avoid screen being dimmed too much.
    if (level < DISPLAY_BRIGHTNESS_DIM_THRESHOLD) {
        level = DISPLAY_BRIGHTNESS_DIM_THRESHOLD;
    }

    if (android::base::WriteStringToFile(std::to_string(level), BRIGHTNESS_FILE)) {
            return { Status::SUCCESS, "" };
    }

    return { Status::FAILURE_UNKNOWN, "Unable to set display brightness" };
}

Return<void> Fastboot::doOemCommand(const ::android::hardware::hidl_string& oemCmdArgs,
                          doOemCommand_cb _hidl_cb) {
    const std::unordered_map<std::string, OEMCommandHandler> kOEMCmdMap = {
        {FB_OEM_SET_BRIGHTNESS, SetBrightnessLevel},
    };

    auto args = android::base::Split(oemCmdArgs, " ");
    if (args.size() < 2) {
        _hidl_cb({ Status::INVALID_ARGUMENT, "Invalid OEM command" });
        return Void();
    }

    // args[0] will be "oem", args[1] will be the command name
    auto cmd_handler = kOEMCmdMap.find(args[1]);
    if (cmd_handler != kOEMCmdMap.end()) {
        _hidl_cb(cmd_handler->second(std::vector<std::string>(args.begin() + 2, args.end())));
    } else {
        _hidl_cb({ Status::FAILURE_UNKNOWN, "Unknown OEM command" });
    }

    return Void();
}

static android::fs_mgr::Fstab fstab;
enum WipeVolumeStatus {
    WIPE_OK = 0,
    VOL_FSTAB,
    VOL_UNKNOWN,
    VOL_MOUNTED,
    VOL_BLK_DEV_OPEN,
    WIPE_ERROR_MAX = 0xffffffff,
};
std::map<enum WipeVolumeStatus, std::string> wipe_vol_ret_msg{
        {WIPE_OK, ""},
        {VOL_FSTAB, "Unknown FS table"},
        {VOL_UNKNOWN, "Unknown volume"},
        {VOL_MOUNTED, "Fail to unmount volume"},
        {VOL_BLK_DEV_OPEN, "Fail to open block device"},
        {WIPE_ERROR_MAX, "Unknown wipe error"}};

enum WipeVolumeStatus wipe_volume(const std::string &volume) {
    if (!android::fs_mgr::ReadDefaultFstab(&fstab)) {
        return VOL_FSTAB;
    }
    const fs_mgr::FstabEntry *v = android::fs_mgr::GetEntryForPath(&fstab, volume);
    if (v == nullptr) {
        return VOL_UNKNOWN;
    }
    if (android::fs_mgr::EnsurePathUnmounted(&fstab, volume) != true) {
        return VOL_MOUNTED;
    }

    int fd = open(v->blk_device.c_str(), O_WRONLY | O_CREAT, 0644);
    if (fd == -1) {
        return VOL_BLK_DEV_OPEN;
    }
    wipe_block_device(fd, get_block_device_size(fd));
    close(fd);

    return WIPE_OK;
}

// Attempt to reuse a WipeKeys function that might be found in the recovery
// library in order to clear any digital car keys on the secure element.
bool WipeDigitalCarKeys(void) {
    static constexpr const char *kDefaultLibRecoveryUIExt = "librecovery_ui_ext.so";
    void *librecovery_ui_ext = dlopen(kDefaultLibRecoveryUIExt, RTLD_NOW);
    if (librecovery_ui_ext == nullptr) {
        // Dynamic library not found. Returning true since this likely
        // means target does not support DCK.
        return true;
    }

    bool *(*WipeKeysFunc)(void *const);
    reinterpret_cast<void *&>(WipeKeysFunc) = dlsym(librecovery_ui_ext, "WipeKeys");
    if (WipeKeysFunc == nullptr) {
        // No WipeKeys implementation found. Returning true since this likely
        // means target does not support DCK.
        return true;
    }

    return (*WipeKeysFunc)(nullptr);
}

Return<void> Fastboot::doOemSpecificErase(V1_1::IFastboot::doOemSpecificErase_cb _hidl_cb) {
    // Erase metadata partition along with userdata partition.
    // Keep erasing Titan M even if failing on this case.
    auto wipe_status = wipe_volume("/metadata");

    bool dck_wipe_success = WipeDigitalCarKeys();

    // Connect to Titan M
    ::nos::NuggetClient client;
    client.Open();
    if (!client.IsOpen()) {
        _hidl_cb({ Status::FAILURE_UNKNOWN, "open Titan M fail" });
        return Void();
    }

    // Tell Titan M to wipe user data
    const uint32_t magicValue = htole32(ERASE_CONFIRMATION);
    std::vector<uint8_t> magic(sizeof(magicValue));
    memcpy(magic.data(), &magicValue, sizeof(magicValue));
    const uint8_t retry_count = 5;
    uint32_t nugget_status;
    for (uint8_t i = 0; i < retry_count; i++) {
        nugget_status = client.CallApp(APP_ID_NUGGET, NUGGET_PARAM_NUKE_FROM_ORBIT, magic, nullptr);
        if (nugget_status == APP_SUCCESS && wipe_status == WIPE_OK && dck_wipe_success) {
            _hidl_cb({Status::SUCCESS, wipe_vol_ret_msg[wipe_status]});
            return Void();
        }
    }

    // Return exactly what happened
    if (nugget_status != APP_SUCCESS && wipe_status != WIPE_OK && !dck_wipe_success) {
        _hidl_cb({Status::FAILURE_UNKNOWN, "Fail on wiping metadata, Titan M user data, and DCK"});
    } else if (nugget_status != APP_SUCCESS && wipe_status != WIPE_OK) {
        _hidl_cb({Status::FAILURE_UNKNOWN, "Fail on wiping metadata and Titan M user data"});
    } else if (nugget_status != APP_SUCCESS && !dck_wipe_success) {
        _hidl_cb({Status::FAILURE_UNKNOWN, "Titan M user data and DCK wipe failed"});
    } else if (nugget_status != APP_SUCCESS) {
        _hidl_cb({Status::FAILURE_UNKNOWN, "Titan M user data wipe failed"});
    } else if (wipe_status != WIPE_OK && !dck_wipe_success) {
        _hidl_cb({Status::FAILURE_UNKNOWN, "Fail on wiping metadata and DCK"});
    } else if (!dck_wipe_success) {
        _hidl_cb({Status::FAILURE_UNKNOWN, "DCK wipe failed"});
    } else {
        if (wipe_vol_ret_msg.find(wipe_status) != wipe_vol_ret_msg.end())
            _hidl_cb({Status::FAILURE_UNKNOWN, wipe_vol_ret_msg[wipe_status]});
        else  // Should not reach here, but handle it anyway
            _hidl_cb({Status::FAILURE_UNKNOWN, "Unknown failure"});
    }

    return Void();
}

Fastboot::Fastboot() {}

// Methods from ::android::hidl::base::V1_0::IBase follow.

extern "C" IFastboot* HIDL_FETCH_IFastboot(const char* /* name */) {
    return new Fastboot();
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace fastboot
}  // namespace hardware
}  // namespace android
