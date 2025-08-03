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

#include <aidl/android/media/audio/common/AudioUuid.h>
#include <android-base/stringprintf.h>

namespace android::audio::utils {

/**
 * This method returns a string representation of the AudioUuid object in hexadecimal format which
 * aligns with the UUID canonical textual representation.
 *
 * There is also an AIDL generated ::aidl::android::media::audio::common::AudioUuid::toString()
 * method that returns a decimal format string of the AudioUuid object.
 *
 * If this header is included, then android::internal::ToString(AudioUuid) is expected to use this
 * toString() override implementation instead of the generated one.
 */
static inline std::string toString(const ::aidl::android::media::audio::common::AudioUuid& uuid) {
  return android::base::StringPrintf(
      "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x", uuid.timeLow,
      uuid.timeMid, uuid.timeHiAndVersion, uuid.clockSeq, uuid.node[0],
      uuid.node[1], uuid.node[2], uuid.node[3], uuid.node[4], uuid.node[5]);
}

} // namespace android::audio::utils