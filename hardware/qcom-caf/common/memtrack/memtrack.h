/* Copyright (c) 2021 The Linux Foundation. All rights reserved.
 * Not a contribution
 * Copyright (C) 2020 The Android Open Source Project
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

/*
 * Changes from Qualcomm Innovation Center are provided under the following
 * license:
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */
#pragma once

#include <aidl/android/hardware/memtrack/BnMemtrack.h>
#include <aidl/android/hardware/memtrack/DeviceInfo.h>
#include <aidl/android/hardware/memtrack/MemtrackRecord.h>
#include <aidl/android/hardware/memtrack/MemtrackType.h>
#include <log/log.h>


using aidl::android::hardware::memtrack::BnMemtrack;
using aidl::android::hardware::memtrack::DeviceInfo;
using aidl::android::hardware::memtrack::MemtrackRecord;
using aidl::android::hardware::memtrack::MemtrackType;

namespace vendor {
namespace qti {
namespace hardware {
namespace memtrack {

class Memtrack : public BnMemtrack {
    ndk::ScopedAStatus getMemory(int pid, MemtrackType type,
                                 std::vector<MemtrackRecord>* _aidl_return) override;

    ndk::ScopedAStatus getGpuDeviceInfo(std::vector<DeviceInfo>* _aidl_return) override;
};

}  // namespace memtrack
}  // namespace hardware
}  // namespace qti
}  // namespace vendor
