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

#define LOG_TAG "hwc-vsync-worker"

#include "VSyncWorker.h"

#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cstdlib>
#include <cstring>
#include <ctime>

#include "drm/ResourceManager.h"
#include "utils/log.h"

namespace android {

auto VSyncWorker::CreateInstance(DrmDisplayPipeline *pipe,
                                 VSyncWorkerCallbacks &callbacks)
    -> std::shared_ptr<VSyncWorker> {
  auto vsw = std::shared_ptr<VSyncWorker>(new VSyncWorker());

  vsw->callbacks_ = callbacks;

  if (pipe != nullptr) {
    vsw->high_crtc_ = pipe->crtc->Get()->GetIndexInResArray()
                      << DRM_VBLANK_HIGH_CRTC_SHIFT;
    vsw->drm_fd_ = pipe->device->GetFd();
  }

  std::thread(&VSyncWorker::ThreadFn, vsw.get(), vsw).detach();

  return vsw;
}

void VSyncWorker::VSyncControl(bool enabled) {
  {
    const std::lock_guard<std::mutex> lock(mutex_);
    enabled_ = enabled;
    last_timestamp_ = -1;
  }

  cv_.notify_all();
}

void VSyncWorker::StopThread() {
  {
    const std::lock_guard<std::mutex> lock(mutex_);
    thread_exit_ = true;
    enabled_ = false;
    callbacks_ = {};
  }

  cv_.notify_all();
}

/*
 * Returns the timestamp of the next vsync in phase with last_timestamp_.
 * For example:
 *  last_timestamp_ = 137
 *  frame_ns = 50
 *  current = 683
 *
 *  ret = (50 * ((683 - 137)/50 + 1)) + 137
 *  ret = 687
 *
 *  Thus, we must sleep until timestamp 687 to maintain phase with the last
 *  timestamp.
 */
int64_t VSyncWorker::GetPhasedVSync(int64_t frame_ns, int64_t current) const {
  if (last_timestamp_ < 0)
    return current + frame_ns;

  return frame_ns * ((current - last_timestamp_) / frame_ns + 1) +
         last_timestamp_;
}

static const int64_t kOneSecondNs = 1LL * 1000 * 1000 * 1000;

int VSyncWorker::SyntheticWaitVBlank(int64_t *timestamp) {
  auto time_now = ResourceManager::GetTimeMonotonicNs();

  // Default to 60Hz refresh rate
  constexpr uint32_t kDefaultVSPeriodNs = 16666666;
  auto period_ns = kDefaultVSPeriodNs;
  if (callbacks_.get_vperiod_ns && callbacks_.get_vperiod_ns() != 0)
    period_ns = callbacks_.get_vperiod_ns();

  auto phased_timestamp = GetPhasedVSync(period_ns, time_now);
  struct timespec vsync {};
  vsync.tv_sec = int(phased_timestamp / kOneSecondNs);
  vsync.tv_nsec = int(phased_timestamp - (vsync.tv_sec * kOneSecondNs));

  int ret = 0;
  do {
    ret = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &vsync, nullptr);
  } while (ret == EINTR);
  if (ret != 0)
    return ret;

  *timestamp = phased_timestamp;
  return 0;
}

void VSyncWorker::ThreadFn(const std::shared_ptr<VSyncWorker> &vsw) {
  int ret = 0;

  for (;;) {
    {
      std::unique_lock<std::mutex> lock(vsw->mutex_);
      if (thread_exit_)
        break;

      if (!enabled_)
        vsw->cv_.wait(lock);

      if (!enabled_)
        continue;
    }

    ret = -EAGAIN;
    int64_t timestamp = 0;
    drmVBlank vblank{};

    if (drm_fd_) {
      vblank.request.type = (drmVBlankSeqType)(DRM_VBLANK_RELATIVE |
                                               (high_crtc_ &
                                                DRM_VBLANK_HIGH_CRTC_MASK));
      vblank.request.sequence = 1;

      ret = drmWaitVBlank(*drm_fd_, &vblank);
      if (ret == -EINTR)
        continue;
    }

    if (ret != 0) {
      ret = SyntheticWaitVBlank(&timestamp);
      if (ret != 0)
        continue;
    } else {
      constexpr int kUsToNsMul = 1000;
      timestamp = (int64_t)vblank.reply.tval_sec * kOneSecondNs +
                  (int64_t)vblank.reply.tval_usec * kUsToNsMul;
    }

    decltype(callbacks_.out_event) callback;

    {
      const std::lock_guard<std::mutex> lock(mutex_);
      if (!enabled_)
        continue;
      callback = callbacks_.out_event;
    }

    if (callback)
      callback(timestamp);

    last_timestamp_ = timestamp;
  }

  ALOGI("VSyncWorker thread exit");
}
}  // namespace android
