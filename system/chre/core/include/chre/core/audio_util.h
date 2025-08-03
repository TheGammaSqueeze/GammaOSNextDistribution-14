/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef CHRE_CORE_AUDIO_UTIL_H_
#define CHRE_CORE_AUDIO_UTIL_H_

#include <cstdint>

#include "chre/util/time.h"

namespace chre {

/**
 * Class to define utility functions for CHRE audio.
 */
class AudioUtil {
 public:
  /**
   * A convenience function to convert sample count and sample rate into a time
   * duration. It is illegal to call this function with a rate of zero.
   *
   * @param sampleCount The number of samples to convert to time at the provided
   *        rate.
   * @param sampleRate The rate to perform the time conversion at.
   * @return The duration of time for these two parameters.
   */
  static constexpr Nanoseconds getDurationFromSampleCountAndRate(
      uint32_t sampleCount, uint32_t sampleRate) {
    // This function will overflow with high sample counts but does work for
    // reasonable expected values.
    //
    // Example: 22050 * 1000000000 / 44100 = 500000000ns
    return Nanoseconds((sampleCount * kOneSecondInNanoseconds) / sampleRate);
  }

  /**
   * A convenience function to convert sample rate and duration into a sample
   * count. This can be used by platform implementations to ensure that the
   * computed buffer sizes match those expected by CHRE.
   *
   * @param sampleRate The sample rate of the audio source.
   * @param duration The duration of the buffer delivered.
   * @return The number of samples given this configuration.
   */
  static constexpr uint32_t getSampleCountFromRateAndDuration(
      uint32_t sampleRate, Nanoseconds duration) {
    // This function will overflow at high sample rates or extremely high
    // durations, but does work for reasonable expected values.
    //
    // Example: 44100 * 60 seconds (in nanoseconds) fits into a uint64_t as an
    // intermediate value before casting to uint32_t.
    return static_cast<uint32_t>((sampleRate * duration.toRawNanoseconds()) /
                                 kOneSecondInNanoseconds);
  }
};

}  // namespace chre

#endif  // CHRE_CORE_AUDIO_UTIL_H_
