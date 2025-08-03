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

#pragma once

// clang-format off
#include PATH(android/hardware/audio/FILE_VERSION/IDevicesFactory.h)
// clang-format on

#include "DeviceImpl.h"
#include "ServiceConfig.h"

namespace audio_proxy {
namespace service {

using android::hardware::Return;

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
using android::hardware::audio::V7_1::IDevicesFactory;
#else
using android::hardware::audio::CPP_VERSION::IDevicesFactory;
#endif

class BusStreamProvider;

class DevicesFactoryImpl : public IDevicesFactory {
 public:
  DevicesFactoryImpl(BusStreamProvider& busDeviceProvider,
                     const ServiceConfig& config);

  // Methods from android::hardware::audio::V5_0::IDevicesFactory follow.
  Return<void> openDevice(const hidl_string& device,
                          openDevice_cb _hidl_cb) override;
  Return<void> openPrimaryDevice(openPrimaryDevice_cb _hidl_cb) override;

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
  Return<void> openDevice_7_1(const hidl_string& device,
                              openDevice_7_1_cb _hidl_cb) override;
  Return<void> openPrimaryDevice_7_1(
      openPrimaryDevice_7_1_cb _hidl_cb) override;
#endif

 private:
  BusStreamProvider& mBusStreamProvider;
  const ServiceConfig& mConfig;
};

}  // namespace service
}  // namespace audio_proxy
