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

#include <pthread.h>

#include <memory>
#include <optional>

#include "compositor/DrmKmsPlan.h"
#include "compositor/LayerData.h"
#include "drm/DrmPlane.h"
#include "drm/ResourceManager.h"
#include "drm/VSyncWorker.h"

namespace android {

struct AtomicCommitArgs {
  /* inputs. All fields are optional, but at least one has to be specified */
  bool test_only = false;
  std::optional<DrmMode> display_mode;
  std::optional<bool> active;
  std::shared_ptr<DrmKmsPlan> composition;
  std::shared_ptr<drm_color_ctm> color_matrix;

  /* out */
  SharedFd out_fence;

  /* helpers */
  auto HasInputs() -> bool {
    return display_mode || active || composition;
  }
};

class DrmAtomicStateManager {
 public:
  static auto CreateInstance(DrmDisplayPipeline *pipe)
      -> std::shared_ptr<DrmAtomicStateManager>;

  ~DrmAtomicStateManager() = default;

  auto ExecuteAtomicCommit(AtomicCommitArgs &args) -> int;
  auto ActivateDisplayUsingDPMS() -> int;

  void StopThread() {
    {
      const std::unique_lock lock(mutex_);
      exit_thread_ = true;
    }
    cv_.notify_all();
  }

 private:
  DrmAtomicStateManager() = default;
  auto CommitFrame(AtomicCommitArgs &args) -> int;

  struct KmsState {
    /* Required to cleanup unused planes */
    std::vector<std::shared_ptr<BindingOwner<DrmPlane>>> used_planes;
    /* We have to hold a reference to framebuffer while displaying it ,
     * otherwise picture will blink */
    std::vector<std::shared_ptr<DrmFbIdHandle>> used_framebuffers;

    DrmModeUserPropertyBlobUnique mode_blob;
    DrmModeUserPropertyBlobUnique ctm_blob;

    int release_fence_pt_index{};

    /* To avoid setting the inactive state twice, which will fail the commit */
    bool crtc_active_state{};
  } active_frame_state_;

  auto NewFrameState() -> KmsState {
    auto *prev_frame_state = &active_frame_state_;
    return (KmsState){
        .used_planes = prev_frame_state->used_planes,
        .crtc_active_state = prev_frame_state->crtc_active_state,
    };
  }

  DrmDisplayPipeline *pipe_{};

  void CleanupPriorFrameResources();

  KmsState staged_frame_state_;
  SharedFd last_present_fence_;
  int frames_staged_{};
  int frames_tracked_{};

  void ThreadFn(const std::shared_ptr<DrmAtomicStateManager> &dasm);
  std::condition_variable cv_;
  std::mutex mutex_;
  bool exit_thread_{};
};

}  // namespace android
