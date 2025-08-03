// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "DevicesFactoryImpl.h"

#include <android-base/logging.h>

using android::hardware::Void;
using namespace android::hardware::audio::CPP_VERSION;

namespace audio_proxy {
namespace service {

DevicesFactoryImpl::DevicesFactoryImpl(BusStreamProvider& busStreamProvider,
                                       const ServiceConfig& config)
    : mBusStreamProvider(busStreamProvider), mConfig(config) {}

// Methods from android::hardware::audio::CPP_VERSION::IDevicesFactory follow.
Return<void> DevicesFactoryImpl::openDevice(const hidl_string& device,
                                            openDevice_cb _hidl_cb) {
  if (device == mConfig.name) {
    LOG(INFO) << "Audio Device was opened: " << device;
    _hidl_cb(Result::OK, new DeviceImpl(mBusStreamProvider, mConfig));
  } else {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr);
  }

  return Void();
}

Return<void> DevicesFactoryImpl::openPrimaryDevice(
    openPrimaryDevice_cb _hidl_cb) {
  // The AudioProxy HAL does not support a primary device.
  _hidl_cb(Result::NOT_SUPPORTED, nullptr);
  return Void();
}

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
Return<void> DevicesFactoryImpl::openDevice_7_1(const hidl_string& device,
                                                openDevice_7_1_cb _hidl_cb) {
  if (device == mConfig.name) {
    LOG(INFO) << "Audio Device was opened: " << device;
    _hidl_cb(Result::OK, new DeviceImpl(mBusStreamProvider, mConfig));
  } else {
    _hidl_cb(Result::INVALID_ARGUMENTS, nullptr);
  }

  return Void();
}

Return<void> DevicesFactoryImpl::openPrimaryDevice_7_1(
    openPrimaryDevice_7_1_cb _hidl_cb) {
  // The AudioProxy HAL does not support a primary device.
  _hidl_cb(Result::NOT_SUPPORTED, nullptr);
  return Void();
}
#endif

}  // namespace service
}  // namespace audio_proxy
