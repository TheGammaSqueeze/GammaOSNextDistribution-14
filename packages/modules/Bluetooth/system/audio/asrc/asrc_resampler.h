/*
 * Copyright 2023 The Android Open Source Project
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

#include <array>
#include <cstdint>
#include <memory>
#include <vector>

namespace bluetooth::audio::asrc {

class ClockHandler {
 public:
  virtual ~ClockHandler() = default;
  virtual void OnEvent(uint32_t timestamp, int link_id,
                       int num_of_completed_packets) = 0;
};

class ClockSource {
 public:
  virtual ~ClockSource() = default;
  virtual void Bind(ClockHandler*) = 0;
};

class SourceAudioHalAsrc {
 public:
  // The Asynchronous Sample Rate Conversion (ASRC) is set up from the PCM
  // stream characteristics and the length, expressed in us, of the buffers.
  //
  // A transmission `burst` is proposed, to fulfill the audio pipeline
  // buffering. After an initial delay of `burst_delay_ms`, a burst of
  // `num_burst_buffers` is generated. By experience, it looks like some
  // controllers discard and acknowledge the first packets without following
  // transmission intervals. This behavior leads to dumping initial buffers. The
  // `burst_delay_ms` helps to ensure that the synchronization with the
  // transmission intervals is done.

  SourceAudioHalAsrc(std::shared_ptr<ClockSource> clock_source, int channels,
                     int sample_rate, int bit_depth, int interval_us,
                     int num_burst_buffers = 2, int burst_delay_ms = 500);

  ~SourceAudioHalAsrc();

  // Takes an input buffer, and returns a list of resamples buffers locked to
  // the cadence of the transmission. The input and output buffers have a fixed
  // size, deducted from the PCM characteristics, given to the constructor.
  //
  // The data of `in` mest be aligned to `int16_t` or `int32_t` for respectively
  // bit depth less or equal to 16, or greater.
  //

  std::vector<const std::vector<uint8_t>*> Run(const std::vector<uint8_t>& in);

 private:
  const int sample_rate_;
  const int bit_depth_;
  const int interval_us_;

  unsigned burst_delay_us_;
  std::vector<const std::vector<uint8_t>*> burst_buffers_;

  unsigned stream_us_;
  double drift_z0_, drift_us_;
  unsigned out_counter_;

  size_t buffers_size_;

  struct {
    std::array<std::vector<uint8_t>, 3> pool;
    int initial_buffering;
    int index, offset;
  } buffers_;

  class ClockRecovery;
  std::unique_ptr<ClockRecovery> clock_recovery_;
  std::shared_ptr<ClockSource> clock_source_;

  class Resampler;
  std::unique_ptr<std::vector<Resampler>> resamplers_;
  struct {
    unsigned seconds;
    int samples;
  } resampler_pos_;

  template <typename T>
  void Resample(double, const std::vector<uint8_t>&,
                std::vector<const std::vector<uint8_t>*>*, uint32_t*);

  friend class SourceAudioHalAsrcTest;
};

}  // namespace bluetooth::audio::asrc
