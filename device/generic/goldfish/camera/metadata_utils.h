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

#pragma once

#include <optional>
#include <type_traits>
#include <unordered_map>
#include <vector>

#include <aidl/android/hardware/camera/device/CameraMetadata.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

using aidl::android::hardware::camera::device::CameraMetadata;

struct CameraMetadataValue {
    std::vector<uint8_t> data;
    unsigned count = 0;

    template <class T> CameraMetadataValue& operator=(const T& v) {
        static_assert(std::is_trivial<T>::value);
        const uint8_t* v8 = reinterpret_cast<const uint8_t*>(&v);
        data.assign(v8, v8 + sizeof(v));
        count = 1;
        return *this;
    }

    template <class T, size_t N> CameraMetadataValue& operator=(const T (&a)[N]) {
        static_assert(std::is_trivial<T>::value);
        const uint8_t* v8 = reinterpret_cast<const uint8_t*>(&a[0]);
        data.assign(v8, v8 + sizeof(a));
        count = N;
        return *this;
    }

    template <class T> CameraMetadataValue& add(const T& v) {
        static_assert(std::is_trivial<T>::value);
        const uint8_t* v8 = reinterpret_cast<const uint8_t*>(&v);
        data.insert(data.end(), v8, v8 + sizeof(v));
        ++count;
        return *this;
    }
};

using CameraMetadataMap = std::unordered_map<uint32_t, CameraMetadataValue>;

CameraMetadata metadataCompact(const CameraMetadata&);

std::optional<CameraMetadata> serializeCameraMetadataMap(const CameraMetadataMap& m);

CameraMetadataMap parseCameraMetadataMap(const CameraMetadata& m);

void metadataSetShutterTimestamp(CameraMetadata* metadata, int64_t shutterTimestampNs);

void prettyPrintCameraMetadata(const CameraMetadata&);

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
