/* Copyright (c) 2022 The Linux Foundation. All rights reserved.
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
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#include "memtrack.h"
#include "memtrack_kgsl.h"

namespace vendor {
namespace qti {
namespace hardware {
namespace memtrack {

ndk::ScopedAStatus Memtrack::getMemory(int pid, MemtrackType type,
                                       std::vector<MemtrackRecord>* _aidl_return) {
    if (pid < 0) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_ILLEGAL_ARGUMENT));
    }
    if (type != MemtrackType::OTHER && type != MemtrackType::GL && type != MemtrackType::GRAPHICS &&
        type != MemtrackType::MULTIMEDIA && type != MemtrackType::CAMERA) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
    }

    /* When MemtrackType is GL and pid = 0, driver should return the global total
     * unaccounted GPU-private memory. EX_UNSUPPORTED_OPERATION can be returned when
     * this operation is not supported. Currently driver doesn't have support for PID 0,
     * so return EX_UNSUPPORTED_OPERATION for such request.
     */
    if (pid == 0 && type == MemtrackType::GL) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
    }

    _aidl_return->clear();

    if(type == MemtrackType::GL || type == MemtrackType::GRAPHICS) {
        getKgslMemory(pid, type, _aidl_return);
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Memtrack::getGpuDeviceInfo(std::vector<DeviceInfo>* _aidl_return) {
    _aidl_return->clear();
    DeviceInfo dev_info = {.id = 0, .name = "kgsl"};
    _aidl_return->emplace_back(dev_info);
    return ndk::ScopedAStatus::ok();
}

}  // namespace memtrack
}  // namespace hardware
}  // namespace qti
}  // namespace vendor
