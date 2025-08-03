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

/*
 * Usually, display controllers do not use intermediate buffer for composition
 * results. Instead, they scan-out directly from the input buffers, composing
 * the planes on the fly every VSYNC.
 *
 * Flattening is a technique that reduces memory bandwidth and power consumption
 * by converting non-updating multi-plane composition into a single-plane.
 * Additionally, flattening also makes more shared planes available for use by
 * other CRTCs.
 *
 * If the client is not updating layers for 1 second, FlatCon triggers a
 * callback to refresh the screen. The compositor should mark all layers to be
 * composed by the client into a single framebuffer using GPU.
 */

#define LOG_TAG "hwc-flatcon"

#include "FlatteningController.h"

#include "utils/log.h"

namespace android {

auto FlatteningController::CreateInstance(FlatConCallbacks &cbks)
    -> std::shared_ptr<FlatteningController> {
  auto fc = std::shared_ptr<FlatteningController>(new FlatteningController());

  fc->cbks_ = cbks;

  std::thread(&FlatteningController::ThreadFn, fc).detach();

  return fc;
}

/* Compositor should call this every frame */
bool FlatteningController::NewFrame() {
  bool wake_it = false;
  auto lock = std::lock_guard<std::mutex>(mutex_);

  if (flatten_next_frame_) {
    flatten_next_frame_ = false;
    return true;
  }

  sleep_until_ = std::chrono::system_clock::now() + kTimeout;
  if (disabled_) {
    wake_it = true;
    disabled_ = false;
  }

  if (wake_it)
    cv_.notify_all();

  return false;
}

void FlatteningController::ThreadFn(
    const std::shared_ptr<FlatteningController> &fc) {
  for (;;) {
    std::unique_lock<std::mutex> lock(fc->mutex_);
    if (fc.use_count() == 1 || !fc->cbks_.trigger)
      break;

    if (fc->sleep_until_ <= std::chrono::system_clock::now() &&
        !fc->disabled_) {
      fc->disabled_ = true;
      fc->flatten_next_frame_ = true;
      ALOGV("Timeout. Sending an event to compositor");
      fc->cbks_.trigger();
    }

    if (fc->disabled_) {
      ALOGV("Wait");
      fc->cv_.wait(lock);
    } else {
      ALOGV("Wait_until");
      fc->cv_.wait_until(lock, fc->sleep_until_);
    }
  }
}

}  // namespace android
