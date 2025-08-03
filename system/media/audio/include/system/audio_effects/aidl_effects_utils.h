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
 * The first AIDL version that introduced the IEffect::reopen method.
 */
static constexpr int32_t kReopenSupportedVersion = 2;

/**
 * EventFlag to indicate that the client has written data to the FMQ, align with
 * EffectHalAidl.
 *
 * This flag is deprecated start from HAL AIDL version 2 and should not be used.
 * Bit 0x01 and 0x02 were used by FMQ internally (FMQ_NOT_FULL and
 * FMQ_NOT_EMPTY), using these event flag bits will cause conflict and may
 * result in a waiter not able to receive wake correctly.
 */
static constexpr uint32_t kEventFlagNotEmpty = 0x1;
/**
 * EventFlag for the effect instance to indicate that the data FMQ needs to be updated.
 * TODO: b/277900230, Define in future AIDL version.
 */
static constexpr uint32_t kEventFlagDataMqUpdate = 0x1 << 10;
/**
 * EventFlag to indicate that the data FMQ is not Empty after a write.
 * TODO: b/277900230, Define in future AIDL version.
 */
static constexpr uint32_t kEventFlagDataMqNotEmpty = 0x1 << 11;

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
static inline bool inRange(const T& target, const R& ranges) {
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
static inline bool inRange(const T& target, const Capability& cap) {
  if (cap.range.getTag() == rangeTag) {
    const auto& ranges = cap.range.template get<rangeTag>();
    return inRange(target, ranges);
  }
  return true;
}

/**
 * Return the range pair (as defined in aidl::android::hardware::audio::effect::Range) of a
 * parameter.
 */
template <typename Range::Tag RangeTag, typename R, typename T>
static inline std::optional<R> getRange(const Capability& cap, T tag) {
  if (cap.range.getTag() != RangeTag) {
    return std::nullopt;
  }

  const auto& ranges = cap.range.template get<RangeTag>();
  for (const auto& r : ranges) {
    if (r.min.getTag() == tag && r.max.getTag() == tag) {
      return r;
    }
  }

  return std::nullopt;
}

template <typename T, typename R>
static inline bool isRangeValid(const T& tag, const R& ranges) {
  for (const auto& r : ranges) {
    if (tag == r.min.getTag() && tag == r.max.getTag()) {
      return r.min <= r.max;
    }
  }

  return true;
}

template <typename Range::Tag rangeTag, typename T>
static inline bool isRangeValid(const T& paramTag, const Capability& cap) {
  if (cap.range.getTag() == rangeTag) {
    const auto& ranges = cap.range.template get<rangeTag>();
    return isRangeValid(paramTag, ranges);
  }
  return true;
}

}  // namespace aidl::android::hardware::audio::effect
