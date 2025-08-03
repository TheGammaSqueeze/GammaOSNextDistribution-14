/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_UTIL_SYSTEM_STATS_CONTAINER_H_
#define CHRE_UTIL_SYSTEM_STATS_CONTAINER_H_

#include <cinttypes>
#include <type_traits>

#include "chre/util/macros.h"

namespace chre {

/**
 * A Stats tool used to collect and compute metrics of interests
 */

template <typename T>
class StatsContainer {
  static_assert(std::is_arithmetic<T>::value,
                "Type must support arithmetic operations");

 public:
  /**
   * @brief Construct a new Stats Container object
   *
   * @param averageWindow_ how many data stored before prioritizing new data,
   * it should not be bigger than the default value to prevent rounding to 0
   */
  StatsContainer(uint32_t averageWindow_ = 512)
      : mAverageWindow(averageWindow_) {}

  /**
   * Add a new value to the metric collection and update mean/max value
   * Mean calculated in rolling bases to prevent overflow by accumulating too
   * much data.
   *
   * Before mCount reaches mAverageWindow, it calculates the normal average
   * After mCount reaches mAverageWindow, weighted average is used to prioritize
   * recent data where the new value always contributes 1/mAverageWindow amount
   * to the average
   * @param value a T instance
   */
  void addValue(T value) {
    if (mCount < mAverageWindow) {
      ++mCount;
    }
    mMean = (mCount - 1) * (mMean / mCount) + value / mCount;
    mMax = MAX(value, mMax);
  }

  /**
   * @return the average value calculated by the description of the
   * addValue method
   */
  T getMean() const {
    return mMean;
  }

  /**
   * @return the max value
   */
  T getMax() const {
    return mMax;
  }

  /**
   * @return the average window
   */
  uint32_t getAverageWindow() const {
    return mAverageWindow;
  }

 private:
  //! Mean of the collections of this stats
  T mMean = 0;
  //! Number of collections of this stats
  uint32_t mCount = 0;
  //! The Window that the container will not do weighted average
  uint32_t mAverageWindow;
  //! Max of stats
  T mMax = 0;
};

}  // namespace chre

#endif  // CHRE_UTIL_SYSTEM_STATS_CONTAINER_H_
