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

#include "tests/simple_parcelable_ndk.h"

#include <android-base/stringprintf.h>
#include <android/binder_parcel.h>
#include <android/binder_parcel_utils.h>

using android::base::StringPrintf;
using ndk::AParcel_readString;
using ndk::AParcel_writeString;

namespace aidl {
namespace android {
namespace aidl {
namespace tests {

SimpleParcelable::SimpleParcelable(const std::string& name, int32_t number)
    : name_(name), number_(number) {}

binder_status_t SimpleParcelable::writeToParcel(AParcel* _Nonnull parcel) const {
  binder_status_t status = AParcel_writeString(parcel, name_);
  if (status != STATUS_OK) {
    return status;
  }
  status = AParcel_writeInt32(parcel, number_);
  return status;
}

binder_status_t SimpleParcelable::readFromParcel(const AParcel* _Nonnull parcel) {
  binder_status_t status = AParcel_readString(parcel, &name_);
  if (status != STATUS_OK) {
    return status;
  }
  return AParcel_readInt32(parcel, &number_);
}

std::string SimpleParcelable::toString() const {
  return StringPrintf("%s(%d)", name_.c_str(), number_);
}

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
