/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <condition_variable>
#include <functional>
#include <map>
#include <mutex>
#include <thread>

#include "DrmDevice.h"

namespace android {

struct VSyncWorkerCallbacks {
  std::function<void(uint64_t /*timestamp*/)> out_event;
  std::function<uint32_t()> get_vperiod_ns;
};

class VSyncWorker {
 public:
  ~VSyncWorker() = default;

  auto static CreateInstance(DrmDisplayPipeline *pipe,
                             VSyncWorkerCallbacks &callbacks)
      -> std::shared_ptr<VSyncWorker>;

  void VSyncControl(bool enabled);
  void StopThread();

 private:
  VSyncWorker() = default;

  void ThreadFn(const std::shared_ptr<VSyncWorker> &vsw);

  int64_t GetPhasedVSync(int64_t frame_ns, int64_t current) const;
  int SyntheticWaitVBlank(int64_t *timestamp);

  VSyncWorkerCallbacks callbacks_;

  SharedFd drm_fd_;
  uint32_t high_crtc_ = 0;

  bool enabled_ = false;
  bool thread_exit_ = false;
  int64_t last_timestamp_ = -1;

  std::condition_variable cv_;
  std::thread vswt_;
  std::mutex mutex_;
};
}  // namespace android
