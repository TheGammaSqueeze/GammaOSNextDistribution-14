/*
 * Copyright (C) 2022-2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "ChargingControl.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <android/binder_status.h>
#include <fstream>
#include "android/binder_auto_utils.h"

#define LOG_TAG "vendor.lineage.health-service.default"

namespace aidl {
namespace vendor {
namespace lineage {
namespace health {

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_TOGGLE
static const std::vector<ChargingEnabledNode> kChargingEnabledNodes = {
        {HEALTH_CHARGING_CONTROL_CHARGING_PATH, HEALTH_CHARGING_CONTROL_CHARGING_ENABLED,
         HEALTH_CHARGING_CONTROL_CHARGING_DISABLED},
        {"/sys/class/power_supply/battery/battery_charging_enabled", "1", "0"},
        {"/sys/class/power_supply/battery/charging_enabled", "1", "0"},
        {"/sys/class/power_supply/battery/input_suspend", "0", "1"},
        {"/sys/class/qcom-battery/input_suspend", "0", "1"},
};

ChargingControl::ChargingControl() : mChargingEnabledNode(nullptr) {
    while (!mChargingEnabledNode) {
        for (const auto& node : kChargingEnabledNodes) {
            if (access(node.path.c_str(), R_OK | W_OK) == 0) {
                mChargingEnabledNode = &node;
                break;
            }
            PLOG(WARNING) << "Failed to access() file " << node.path;
            usleep(100000);
        }
    }
}

ndk::ScopedAStatus ChargingControl::getChargingEnabled(bool* _aidl_return) {
    std::string content;
    if (!android::base::ReadFileToString(mChargingEnabledNode->path, &content, true)) {
        LOG(ERROR) << "Failed to read current charging enabled value";
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    content = android::base::Trim(content);

    if (content == mChargingEnabledNode->value_true) {
        *_aidl_return = true;
    } else if (content == mChargingEnabledNode->value_false) {
        *_aidl_return = false;
    } else {
        LOG(ERROR) << "Unknown value " << content;
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::setChargingEnabled(bool enabled) {
    const auto& value =
            enabled ? mChargingEnabledNode->value_true : mChargingEnabledNode->value_false;
    if (!android::base::WriteStringToFile(value, mChargingEnabledNode->path, true)) {
        LOG(ERROR) << "Failed to write to charging enable node: " << strerror(errno);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    return ndk::ScopedAStatus::ok();
}
#else
ndk::ScopedAStatus ChargingControl::getChargingEnabled(bool* /* _aidl_return */) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus ChargingControl::setChargingEnabled(bool /* enabled */) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}
#endif

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_DEADLINE
static const std::vector<std::string> kChargingDeadlineNodes = {
        HEALTH_CHARGING_CONTROL_DEADLINE_PATH,
        "/sys/class/power_supply/battery/charge_deadline",
};

ChargingControl::ChargingControl() : mChargingDeadlineNode(nullptr) {
    while (!mChargingDeadlineNode) {
        for (const auto& node : kChargingDeadlineNodes) {
            if (access(node.c_str(), R_OK | W_OK) == 0) {
                mChargingDeadlineNode = &node;
                break;
            }
            PLOG(WARNING) << "Failed to access() file " << node;
            usleep(100000);
        }
    }
}

ndk::ScopedAStatus ChargingControl::setChargingDeadline(int64_t deadline) {
    std::string content = std::to_string(deadline);
    if (!android::base::WriteStringToFile(content, *mChargingDeadlineNode, true)) {
        LOG(ERROR) << "Failed to write to charging deadline node: " << strerror(errno);
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
    }

    return ndk::ScopedAStatus::ok();
}
#else
ndk::ScopedAStatus ChargingControl::setChargingDeadline(int64_t /* deadline */) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}
#endif

ndk::ScopedAStatus ChargingControl::getSupportedMode(int* _aidl_return) {
    int mode = 0;

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_TOGGLE
    mode |= static_cast<int>(ChargingControlSupportedMode::TOGGLE);
#endif

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_BYPASS
    mode |= static_cast<int>(ChargingControlSupportedMode::BYPASS);
#endif

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_DEADLINE
    mode |= static_cast<int>(ChargingControlSupportedMode::DEADLINE);
#endif

    *_aidl_return = mode;

    return ndk::ScopedAStatus::ok();
}

binder_status_t ChargingControl::dump(int fd, const char** /* args */, uint32_t /* numArgs */) {
    bool isChargingEnabled;
    int supportedMode;
    getChargingEnabled(&isChargingEnabled);
    getSupportedMode(&supportedMode);

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_TOGGLE
    dprintf(fd, "Charging control node selected: %s\n", mChargingEnabledNode->path.c_str());
    dprintf(fd, "Charging enabled: %s\n", isChargingEnabled ? "true" : "false");
#endif

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_DEADLINE
    dprintf(fd, "Charging deadline node selected: %s\n", mChargingDeadlineNode->c_str());
#endif

    dprintf(fd, "Charging control supported mode: %d\n", supportedMode);

    return STATUS_OK;
}

}  // namespace health
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
