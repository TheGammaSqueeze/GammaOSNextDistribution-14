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

#include <aidl/android/hardware/audio/effect/AcousticEchoCanceler.h>
#include <aidl/android/hardware/audio/effect/DynamicsProcessing.h>
#include <aidl/android/hardware/audio/effect/Parameter.h>
#include <aidl/android/hardware/audio/effect/Range.h>

namespace aidl::android::hardware::audio::effect {

/**
 * EventFlag to indicate that the client has written data to the FMQ, align with EffectHalAidl.
 * TODO: b/277900230, Define in future AIDL version.
 */
static constexpr uint32_t kEventFlagNotEmpty = 0x1;

/**
 * Check the target Parameter with $Parameter$Range definition in Capability.
 * This method go through the elements in the ranges to find a matching tag for the target
 * parameter, and check if the target parameter is inside range use the default AIDL union
 * comparator.
 *
 * Absence of a corresponding range is an indication that there are no limits set on the parameter
 * so this method return true.
 */
template <typename T, typename R>
bool inRange(const T& target, const R& ranges) {
  for (const auto& r : ranges) {
    if (target.getTag() == r.min.getTag() &&
        target.getTag() == r.max.getTag() &&
        (target < r.min || target > r.max)) {
      return false;
    }
  }
  return true;
}

template <typename Range::Tag rangeTag, typename T>
bool inRange(const T& target, const Capability& cap) {
  if (cap.range.getTag() == rangeTag) {
      const auto& ranges = cap.range.template get<rangeTag>();
      return inRange(target, ranges);
  }
  return true;
}

template <typename T, typename R>
bool isRangeValid(const T& tag, const R& ranges) {
  for (const auto& r : ranges) {
    if (tag == r.min.getTag() && tag == r.max.getTag()) {
      return r.min <= r.max;
    }
  }

  return true;
}

template <typename Range::Tag rangeTag, typename T>
bool isRangeValid(const T& paramTag, const Capability& cap) {
  if (cap.range.getTag() == rangeTag) {
      const auto& ranges = cap.range.template get<rangeTag>();
      return isRangeValid(paramTag, ranges);
  }
  return true;
}

}  // namespace aidl::android::hardware::audio::effect
