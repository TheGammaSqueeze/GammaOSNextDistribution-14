/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "include/fastboot/Fastboot_aidl.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <dlfcn.h>
#include <endian.h>

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

using ndk::ScopedAStatus;

namespace aidl {
namespace android {
namespace hardware {
namespace fastboot {

constexpr const char *BRIGHTNESS_FILE = "/sys/class/backlight/panel0-backlight/brightness";
constexpr int DISPLAY_BRIGHTNESS_DIM_THRESHOLD = 20;

using OEMCommandHandler =
        std::function<ScopedAStatus(const std::vector<std::string> &, std::string *)>;

ScopedAStatus Fastboot::getPartitionType(const std::string &in_partitionName,
                                         FileSystemType *_aidl_return) {
    if (in_partitionName.empty()) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Invalid partition name");
    }
    // For bluecross devices, all partitions need to return raw.
    *_aidl_return = FileSystemType::RAW;
    return ScopedAStatus::ok();
}

ScopedAStatus Fastboot::getVariant(std::string *_aidl_return) {
    *_aidl_return = "MSM USF";
    return ScopedAStatus::ok();
}

ScopedAStatus Fastboot::getOffModeChargeState(bool *_aidl_return) {
    constexpr const char *kDevinfoPath = "/dev/block/by-name/devinfo";
    constexpr int kDevInfoOffModeChargeOffset = 15;

    uint8_t off_mode_charge_status = 0;
    ::android::base::unique_fd fd(TEMP_FAILURE_RETRY(open(kDevinfoPath, O_RDONLY | O_BINARY)));
    if (fd < 0) {
        std::string message = "Unable to open devinfo " + std::to_string(errno);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                  message.c_str());
    }
    auto ret = ::android::base::ReadFullyAtOffset(fd, &off_mode_charge_status, 1 /* byte count */,
                                                  kDevInfoOffModeChargeOffset);
    if (!ret) {
        std::string message = "Reading devifo failed errno:" + std::to_string(errno) +
                              " Unable to read off-mode-charge state";
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                  message.c_str());
    } else {
        *_aidl_return = (off_mode_charge_status != 0);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus Fastboot::getBatteryVoltageFlashingThreshold(int32_t *_aidl_return) {
    constexpr int kMinVoltageForFlashing = 3500;
    *_aidl_return = kMinVoltageForFlashing;
    return ScopedAStatus::ok();
}

ScopedAStatus SetBrightnessLevel(const std::vector<std::string> &args, std::string *_aidl_return) {
    if (!args.size()) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Brightness level unspecified");
    }

    auto level = std::stoi(args[0]);

    if (level < 0 || level > 100) {
        return ScopedAStatus::fromExceptionCodeWithMessage(
                EX_ILLEGAL_ARGUMENT, "Brighness level must be between 0 and 100");
    }

    // Avoid screen being dimmed too much.
    if (level < DISPLAY_BRIGHTNESS_DIM_THRESHOLD) {
        level = DISPLAY_BRIGHTNESS_DIM_THRESHOLD;
    }

    if (::android::base::WriteStringToFile(std::to_string(level), BRIGHTNESS_FILE)) {
        *_aidl_return = "";
        return ScopedAStatus::ok();
    }
    std::string message = "Writing to brightness file failed errno: " + std::to_string(errno) +
                          " Unable to set display brightness";

    return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                              message.c_str());
}

ScopedAStatus Fastboot::doOemCommand(const std::string &in_oemCmd, std::string *_aidl_return) {
    const std::unordered_map<std::string, OEMCommandHandler> kOEMCmdMap = {
            {FB_OEM_SET_BRIGHTNESS, SetBrightnessLevel},
    };

    auto args = ::android::base::Split(in_oemCmd, " ");
    if (args.size() < 2) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Invalid OEM command");
    }

    // args[0] will be "oem", args[1] will be the command name
    auto cmd_handler = kOEMCmdMap.find(args[1]);
    if (cmd_handler != kOEMCmdMap.end()) {
        return cmd_handler->second(std::vector<std::string>(args.begin() + 2, args.end()),
                                   _aidl_return);
    } else {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                  "Unknown OEM Command");
    }

    return ScopedAStatus::ok();
}

static ::android::fs_mgr::Fstab fstab;
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
    if (!::android::fs_mgr::ReadDefaultFstab(&fstab)) {
        return VOL_FSTAB;
    }
    const ::android::fs_mgr::FstabEntry *v = ::android::fs_mgr::GetEntryForPath(&fstab, volume);
    if (v == nullptr) {
        return VOL_UNKNOWN;
    }
    if (::android::fs_mgr::EnsurePathUnmounted(&fstab, volume) != true) {
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

ScopedAStatus Fastboot::doOemSpecificErase() {
    // Erase metadata partition along with userdata partition.
    // Keep erasing Titan M even if failing on this case.
    auto wipe_status = wipe_volume("/metadata");

    bool dck_wipe_success = WipeDigitalCarKeys();

    // Connect to Titan M
    ::nos::NuggetClient client;
    client.Open();
    if (!client.IsOpen()) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                  "open Titan M fail");
    }

    // Tell Titan M to wipe user data
    const uint32_t magicValue = htole32(ERASE_CONFIRMATION);
    std::vector<uint8_t> magic(sizeof(magicValue));
    memcpy(magic.data(), &magicValue, sizeof(magicValue));
    const uint8_t retry_count = 5;
    uint32_t nugget_status;
    for (uint8_t i = 0; i < retry_count; i++) {
        nugget_status = client.CallApp(APP_ID_NUGGET, NUGGET_PARAM_NUKE_FROM_ORBIT, magic, nullptr);
        if (nugget_status == APP_SUCCESS && wipe_status == WIPE_OK) {
            return ScopedAStatus::ok();
        }
    }

    // Return exactly what happened
    if (nugget_status != APP_SUCCESS && wipe_status != WIPE_OK && !dck_wipe_success) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(
                BnFastboot::FAILURE_UNKNOWN, "Fail on wiping metadata, Titan M user data, and DCK");
    } else if (nugget_status != APP_SUCCESS && wipe_status != WIPE_OK) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(
                BnFastboot::FAILURE_UNKNOWN, "Fail on wiping metadata and Titan M user data");
    } else if (nugget_status != APP_SUCCESS && !dck_wipe_success) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(
                BnFastboot::FAILURE_UNKNOWN, "Titan M user data and DCK wipe failed");
    } else if (nugget_status != APP_SUCCESS) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                  "Titan M user data wipe failed");
    } else if (wipe_status != WIPE_OK && !dck_wipe_success) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(
                BnFastboot::FAILURE_UNKNOWN, "Fail on wiping metadata and DCK");
    } else if (!dck_wipe_success) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                  "DCK wipe failed");
    } else {
        if (wipe_vol_ret_msg.find(wipe_status) != wipe_vol_ret_msg.end())
            return ScopedAStatus::fromServiceSpecificErrorWithMessage(
                    BnFastboot::FAILURE_UNKNOWN, wipe_vol_ret_msg[wipe_status].c_str());
        else  // Should not reach here, but handle it anyway
            return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                      "Unknown failure");
    }

    // Return exactly what happened
    if (nugget_status != APP_SUCCESS && wipe_status != WIPE_OK) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(
                BnFastboot::FAILURE_UNKNOWN, "Fail on wiping metadata and Titan M user data");

    } else if (nugget_status != APP_SUCCESS) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                  "Titan M user data wipe failed");
    } else {
        if (wipe_vol_ret_msg.find(wipe_status) != wipe_vol_ret_msg.end())
            return ScopedAStatus::fromServiceSpecificErrorWithMessage(
                    BnFastboot::FAILURE_UNKNOWN, wipe_vol_ret_msg[wipe_status].c_str());
        else  // Should not reach here, but handle it anyway
            return ScopedAStatus::fromServiceSpecificErrorWithMessage(BnFastboot::FAILURE_UNKNOWN,
                                                                      "Unknown failure");
    }

    return ScopedAStatus::ok();
}

}  // namespace fastboot
}  // namespace hardware
}  // namespace android
}  // namespace aidl
