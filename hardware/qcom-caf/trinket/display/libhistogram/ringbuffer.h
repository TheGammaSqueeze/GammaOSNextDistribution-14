/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (c) 2020 The Linux Foundation. All rights reserved.
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

/*
* Changes from Qualcomm Innovation Center are provided under the following license:
*
* Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted (subject to the limitations in the
* disclaimer below) provided that the following conditions are met:
*
*    * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*
*    * Redistributions in binary form must reproduce the above
*      copyright notice, this list of conditions and the following
*      disclaimer in the documentation and/or other materials provided
*      with the distribution.
*
*    * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
*      contributors may be used to endorse or promote products derived
*      from this software without specific prior written permission.
*
* NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
* GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
* HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
* IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
* GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
* IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
* OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#pragma once
#include <display/drm/msm_drm_pp.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/Timers.h>
#include <xf86drm.h>
#include <xf86drmMode.h>
#include <array>
#include <deque>
#include <memory>
#include <mutex>
#include <tuple>

namespace histogram {

struct TimeKeeper {
  virtual nsecs_t current_time() const = 0;
  virtual ~TimeKeeper() = default;

 protected:
  TimeKeeper() = default;
  TimeKeeper &operator=(TimeKeeper const &) = delete;
  TimeKeeper(TimeKeeper const &) = delete;
};

struct DefaultTimeKeeper final : TimeKeeper {
  nsecs_t current_time() const final;
};

class Ringbuffer {
 public:
  static std::unique_ptr<Ringbuffer> create(size_t ringbuffer_size, std::unique_ptr<TimeKeeper> tk);
  void insert(drm_msm_hist const &frame);
  bool resize(size_t ringbuffer_size);

  using Sample = std::tuple<uint64_t /* numFrames */, std::array<uint64_t, HIST_V_SIZE> /* bins */>;
  Sample collect_cumulative() const;
  Sample collect_ringbuffer_all() const;
  Sample collect_after(nsecs_t timestamp) const;
  Sample collect_max(uint32_t max_frames) const;
  Sample collect_max_after(nsecs_t timestamp, uint32_t max_frames) const;
  ~Ringbuffer() = default;

 private:
  Ringbuffer(size_t ringbuffer_size, std::unique_ptr<TimeKeeper> tk);
  Ringbuffer(Ringbuffer const &) = delete;
  Ringbuffer &operator=(Ringbuffer const &) = delete;

  Sample collect_max(uint32_t max_frames, std::unique_lock<std::mutex> const &) const;
  Sample collect_max_after(nsecs_t timestamp, uint32_t max_frames,
                           std::unique_lock<std::mutex> const &) const;
  void update_cumulative(nsecs_t now, uint64_t &count,
                         std::array<uint64_t, HIST_V_SIZE> &bins) const;

  std::mutex mutable mutex;
  struct HistogramEntry {
    drm_msm_hist histogram;
    nsecs_t start_timestamp;
    nsecs_t end_timestamp;
  };
  std::deque<HistogramEntry> ringbuffer;
  size_t rb_max_size;
  std::unique_ptr<TimeKeeper> const timekeeper;

  uint64_t cumulative_frame_count;
  std::array<uint64_t, HIST_V_SIZE> cumulative_bins;
};

}  // namespace histogram
