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

#include <charconv>

#include <inttypes.h>

#include <log/log.h>

#include "CameraProvider.h"
#include "CameraDevice.h"
#include "HwCamera.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace {
constexpr char kCameraIdPrefix[] = "device@1.1/internal/";

std::string getLogicalCameraId(const int index) {
    char buf[sizeof(kCameraIdPrefix) + 8];
    snprintf(buf, sizeof(buf), "%s%d", kCameraIdPrefix, index);
    return buf;
}

std::optional<int> parseLogicalCameraId(const std::string_view str) {
    if (str.size() < sizeof(kCameraIdPrefix)) {
        return FAILURE(std::nullopt);
    }

    if (memcmp(str.data(), kCameraIdPrefix, sizeof(kCameraIdPrefix) - 1) != 0) {
        return FAILURE(std::nullopt);
    }

    int index;
    const auto r = std::from_chars(&str[sizeof(kCameraIdPrefix) - 1],
                                   &*str.end(), index, 10);
    if (r.ec == std::errc()) {
        return index;
    } else {
        return FAILURE(std::nullopt);
    }
}
}  // namespace

using aidl::android::hardware::camera::common::Status;

CameraProvider::CameraProvider(const int deviceIdBase,
                               Span<const hw::HwCameraFactory> availableCameras)
        : mDeviceIdBase(deviceIdBase)
        , mAvailableCameras(availableCameras) {}

CameraProvider::~CameraProvider() {}

ScopedAStatus CameraProvider::setCallback(
        const std::shared_ptr<ICameraProviderCallback>& callback) {
    mCallback = callback;
    return ScopedAStatus::ok();
}

ScopedAStatus CameraProvider::getVendorTags(std::vector<VendorTagSection>* vts) {
    *vts = {};
    return ScopedAStatus::ok();
}

ScopedAStatus CameraProvider::getCameraIdList(std::vector<std::string>* camera_ids) {
    camera_ids->reserve(mAvailableCameras.size());

    for (int i = 0; i < mAvailableCameras.size(); ++i) {
        camera_ids->push_back(getLogicalCameraId(mDeviceIdBase + i));
    }

    return ScopedAStatus::ok();
}

ScopedAStatus CameraProvider::getCameraDeviceInterface(
        const std::string& name,
        std::shared_ptr<ICameraDevice>* device) {
    const std::optional<int> maybeIndex = parseLogicalCameraId(name);
    if (!maybeIndex) {
        return toScopedAStatus(FAILURE(Status::ILLEGAL_ARGUMENT));
    }

    const int index = maybeIndex.value() - mDeviceIdBase;
    if ((index >= 0) && (index < mAvailableCameras.size())) {
        auto hwCamera = mAvailableCameras[index]();

        if (hwCamera) {
            auto p = ndk::SharedRefBase::make<CameraDevice>(std::move(hwCamera));
            p->mSelf = p;
            *device = std::move(p);
            return ScopedAStatus::ok();
        } else {
            return toScopedAStatus(FAILURE(Status::INTERNAL_ERROR));
        }
    } else {
        return toScopedAStatus(FAILURE(Status::ILLEGAL_ARGUMENT));
    }
}

ScopedAStatus CameraProvider::notifyDeviceStateChange(const int64_t /*deviceState*/) {
    return ScopedAStatus::ok();
}

ScopedAStatus CameraProvider::getConcurrentCameraIds(
        std::vector<ConcurrentCameraIdCombination>* concurrentCameraIds) {
    *concurrentCameraIds = {};
    return ScopedAStatus::ok();
}

ScopedAStatus CameraProvider::isConcurrentStreamCombinationSupported(
        const std::vector<CameraIdAndStreamCombination>& /*configs*/,
        bool* support) {
    *support = false;
    return ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
