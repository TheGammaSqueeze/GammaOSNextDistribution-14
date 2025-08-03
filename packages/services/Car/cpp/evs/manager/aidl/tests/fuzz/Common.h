/*
 * Copyright 2023 The Android Open Source Project
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

#pragma once

#include "MockEvsHal.h"

#include <cstdlib>

namespace {

#define EVS_FUZZ_BASE_ENUM                                         \
    EVS_FUZZ_NOTIFY,                  /* verify notify*/           \
            EVS_FUZZ_GET_HW_CAMERA,   /* verify getHalCameras*/    \
            EVS_FUZZ_DELIVER_FRAME,   /* verify deliverFrame */    \
            EVS_FUZZ_DONE_WITH_FRAME, /* verify doneWithFrame */   \
            EVS_FUZZ_SET_PRIMARY,     /* verify setPrimary */      \
            EVS_FUZZ_FORCE_PRIMARY,   /* verify forcePrimary */    \
            EVS_FUZZ_UNSET_PRIMARY,   /* verify unsetPrimary */    \
            EVS_FUZZ_SET_PARAMETER,   /* verify setIntParameter */ \
            EVS_FUZZ_GET_PARAMETER,   /* verify getIntParameter */ \
            EVS_FUZZ_API_SUM

inline const char* kMockHWEnumeratorName = "hw/fuzzEVSMock";
inline constexpr uint64_t startMockHWCameraId = 1024;
inline constexpr uint64_t endMockHWCameraId = 1028;
inline constexpr uint64_t startMockHWDisplayId = 256;
inline constexpr uint64_t endMockHWDisplayId = 258;

}  // namespace

namespace aidl::android::automotive::evs::implementation {

std::shared_ptr<MockEvsHal> initializeMockEvsHal();
std::shared_ptr<aidl::android::hardware::automotive::evs::IEvsCamera> openFirstCamera(
        const std::shared_ptr<MockEvsHal>& handle);

}  // namespace aidl::android::automotive::evs::implementation
