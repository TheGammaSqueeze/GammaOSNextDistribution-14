/*
 * Copyright (C) 2022-2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lineage/health/BnChargingControl.h>
#include <aidl/vendor/lineage/health/ChargingControlSupportedMode.h>
#include <android/binder_status.h>
#include <string>
#include "android/binder_auto_utils.h"

namespace aidl {
namespace vendor {
namespace lineage {
namespace health {

struct ChargingEnabledNode {
    const std::string path;
    const std::string value_true;
    const std::string value_false;
};

struct ChargingControl : public BnChargingControl {
    ChargingControl();

    ndk::ScopedAStatus getChargingEnabled(bool* _aidl_return) override;
    ndk::ScopedAStatus setChargingEnabled(bool enabled) override;
    ndk::ScopedAStatus setChargingDeadline(int64_t deadline) override;
    ndk::ScopedAStatus getSupportedMode(int* _aidl_return) override;
    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;

  private:
#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_TOGGLE
    const ChargingEnabledNode* mChargingEnabledNode;
#endif

#ifdef HEALTH_CHARGING_CONTROL_SUPPORTS_DEADLINE
    const std::string* mChargingDeadlineNode;
#endif
};

}  // namespace health
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
