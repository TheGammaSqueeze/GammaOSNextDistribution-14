/*
 * Copyright 2024 The Android Open Source Project
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

#ifndef ART_RUNTIME_JIT_JIT_OPTIONS_H_
#define ART_RUNTIME_JIT_JIT_OPTIONS_H_

#include "base/macros.h"
#include "base/runtime_debug.h"
#include "profile_saver_options.h"

namespace art HIDDEN {

struct RuntimeArgumentMap;

namespace jit {

// At what priority to schedule jit threads. 9 is the lowest foreground priority on device.
// See android/os/Process.java.
static constexpr int kJitPoolThreadPthreadDefaultPriority = 9;
// At what priority to schedule jit zygote threads compiling profiles in the background.
// 19 is the lowest background priority on device.
// See android/os/Process.java.
static constexpr int kJitZygotePoolThreadPthreadDefaultPriority = 19;

class JitOptions {
 public:
  DECLARE_RUNTIME_DEBUG_FLAG(kSlowMode);

  static JitOptions* CreateFromRuntimeArguments(const RuntimeArgumentMap& options);

  uint16_t GetOptimizeThreshold() const {
    return optimize_threshold_;
  }

  uint16_t GetWarmupThreshold() const {
    return warmup_threshold_;
  }

  uint16_t GetPriorityThreadWeight() const {
    return priority_thread_weight_;
  }

  uint16_t GetInvokeTransitionWeight() const {
    return invoke_transition_weight_;
  }

  size_t GetCodeCacheInitialCapacity() const {
    return code_cache_initial_capacity_;
  }

  size_t GetCodeCacheMaxCapacity() const {
    return code_cache_max_capacity_;
  }

  bool DumpJitInfoOnShutdown() const {
    return dump_info_on_shutdown_;
  }

  const ProfileSaverOptions& GetProfileSaverOptions() const {
    return profile_saver_options_;
  }

  bool GetSaveProfilingInfo() const {
    return profile_saver_options_.IsEnabled();
  }

  int GetThreadPoolPthreadPriority() const {
    return thread_pool_pthread_priority_;
  }

  int GetZygoteThreadPoolPthreadPriority() const {
    return zygote_thread_pool_pthread_priority_;
  }

  bool UseJitCompilation() const {
    return use_jit_compilation_;
  }

  bool UseProfiledJitCompilation() const {
    return use_profiled_jit_compilation_;
  }

  void SetUseJitCompilation(bool b) {
    use_jit_compilation_ = b;
  }

  void SetSaveProfilingInfo(bool save_profiling_info) {
    profile_saver_options_.SetEnabled(save_profiling_info);
  }

  void SetWaitForJitNotificationsToSaveProfile(bool value) {
    profile_saver_options_.SetWaitForJitNotificationsToSave(value);
  }

  void SetJitAtFirstUse() {
    use_jit_compilation_ = true;
    optimize_threshold_ = 0;
  }

  void SetUseBaselineCompiler() {
    use_baseline_compiler_ = true;
  }

  bool UseBaselineCompiler() const {
    return use_baseline_compiler_;
  }

 private:
  // We add the sample in batches of size kJitSamplesBatchSize.
  // This method rounds the threshold so that it is multiple of the batch size.
  static uint32_t RoundUpThreshold(uint32_t threshold);

  bool use_jit_compilation_;
  bool use_profiled_jit_compilation_;
  bool use_baseline_compiler_;
  size_t code_cache_initial_capacity_;
  size_t code_cache_max_capacity_;
  uint32_t optimize_threshold_;
  uint32_t warmup_threshold_;
  uint16_t priority_thread_weight_;
  uint16_t invoke_transition_weight_;
  bool dump_info_on_shutdown_;
  int thread_pool_pthread_priority_;
  int zygote_thread_pool_pthread_priority_;
  ProfileSaverOptions profile_saver_options_;

  JitOptions()
      : use_jit_compilation_(false),
        use_profiled_jit_compilation_(false),
        use_baseline_compiler_(false),
        code_cache_initial_capacity_(0),
        code_cache_max_capacity_(0),
        optimize_threshold_(0),
        warmup_threshold_(0),
        priority_thread_weight_(0),
        invoke_transition_weight_(0),
        dump_info_on_shutdown_(false),
        thread_pool_pthread_priority_(kJitPoolThreadPthreadDefaultPriority),
        zygote_thread_pool_pthread_priority_(kJitZygotePoolThreadPthreadDefaultPriority) {}

  DISALLOW_COPY_AND_ASSIGN(JitOptions);
};

}  // namespace jit
}  // namespace art

#endif  // ART_RUNTIME_JIT_JIT_OPTIONS_H_
