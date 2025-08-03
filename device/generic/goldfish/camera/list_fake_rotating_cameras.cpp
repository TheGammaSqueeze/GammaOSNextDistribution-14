/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <android-base/properties.h>
#include <log/log.h>

#include "debug.h"
#include "FakeRotatingCamera.h"
#include "list_fake_rotating_cameras.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {
namespace {

} // namespace

bool listFakeRotatingCameras(const std::function<void(HwCameraFactory)>& cameraSink) {
    if (base::GetBoolProperty("ro.boot.qemu.legacy_fake_camera", false)) {
        // only the `backfacing=true` camera is supported for now.
        cameraSink([]() { return std::make_unique<FakeRotatingCamera>(true); });
    }

    return true;
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
