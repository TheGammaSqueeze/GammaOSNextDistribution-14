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

#include <string>
#include <vector>

#include <aidl/android/hardware/gnss/IGnssCallback.h>
#include <aidl/android/hardware/gnss/GnssLocation.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

struct IDataSink {
    virtual ~IDataSink() {}

    virtual void onGnssStatusCb(IGnssCallback::GnssStatusValue) = 0;
    virtual void onGnssSvStatusCb(std::vector<IGnssCallback::GnssSvInfo>) = 0;
    virtual void onGnssNmeaCb(int64_t timestampMs, std::string nmea) = 0;
    virtual void onGnssLocationCb(GnssLocation location) = 0;
};

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
