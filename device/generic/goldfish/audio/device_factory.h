/*
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

#pragma once
#include <memory>
#include PATH(android/hardware/audio/FILE_VERSION/IDevicesFactory.h)
#include <dlfcn.h>

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {

using ::android::hardware::hidl_string;
using ::android::hardware::Return;
using namespace ::android::hardware::audio::CPP_VERSION;

struct DevicesFactory : public IDevicesFactory {
    DevicesFactory();

    Return<void> openDevice(const hidl_string& device, openDevice_cb _hidl_cb) override;
    Return<void> openPrimaryDevice(openPrimaryDevice_cb _hidl_cb) override;
#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
    Return<void> openDevice_7_1(const hidl_string& device, openDevice_7_1_cb _hidl_cb) override;
    Return<void> openPrimaryDevice_7_1(openPrimaryDevice_7_1_cb _hidl_cb) override;
#endif

private:
    struct DLDeleter {
        void operator()(void* dl) const {
            ::dlclose(dl);
        }
    };

    std::unique_ptr<void, DLDeleter> mLegacyLib;
    std::unique_ptr<IDevicesFactory> mLegacyFactory;
};

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
