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

#include "jit_options.h"

#include "runtime_options.h"

namespace art HIDDEN {
namespace jit {

// Maximum permitted threshold value.
static constexpr uint32_t kJitMaxThreshold = std::numeric_limits<uint16_t>::max();

static constexpr uint32_t kJitDefaultOptimizeThreshold = 0xffff;
// Different optimization threshold constants. These default to the equivalent optimization
// thresholds divided by 2, but can be overridden at the command-line.
static constexpr uint32_t kJitStressDefaultOptimizeThreshold = kJitDefaultOptimizeThreshold / 2;
static constexpr uint32_t kJitSlowStressDefaultOptimizeThreshold =
    kJitStressDefaultOptimizeThreshold / 2;

static constexpr uint32_t kJitDefaultWarmupThreshold = 0x3fff;
// Different warm-up threshold constants. These default to the equivalent warmup thresholds divided
// by 2, but can be overridden at the command-line.
static constexpr uint32_t kJitStressDefaultWarmupThreshold = kJitDefaultWarmupThreshold / 2;
static constexpr uint32_t kJitSlowStressDefaultWarmupThreshold =
    kJitStressDefaultWarmupThreshold / 2;

static constexpr size_t kDefaultPriorityThreadWeightRatio = 1000;
static constexpr size_t kDefaultInvokeTransitionWeightRatio = 500;

DEFINE_RUNTIME_DEBUG_FLAG(JitOptions, kSlowMode);

JitOptions* JitOptions::CreateFromRuntimeArguments(const RuntimeArgumentMap& options) {
  auto* jit_options = new JitOptions;
  jit_options->use_jit_compilation_ = options.GetOrDefault(RuntimeArgumentMap::UseJitCompilation);
  jit_options->use_profiled_jit_compilation_ =
      options.GetOrDefault(RuntimeArgumentMap::UseProfiledJitCompilation);

  jit_options->code_cache_initial_capacity_ =
      options.GetOrDefault(RuntimeArgumentMap::JITCodeCacheInitialCapacity);
  jit_options->code_cache_max_capacity_ =
      options.GetOrDefault(RuntimeArgumentMap::JITCodeCacheMaxCapacity);
  jit_options->dump_info_on_shutdown_ =
      options.Exists(RuntimeArgumentMap::DumpJITInfoOnShutdown);
  jit_options->profile_saver_options_ =
      options.GetOrDefault(RuntimeArgumentMap::ProfileSaverOpts);
  jit_options->thread_pool_pthread_priority_ =
      options.GetOrDefault(RuntimeArgumentMap::JITPoolThreadPthreadPriority);
  jit_options->zygote_thread_pool_pthread_priority_ =
      options.GetOrDefault(RuntimeArgumentMap::JITZygotePoolThreadPthreadPriority);

  // Set default optimize threshold to aid with checking defaults.
  jit_options->optimize_threshold_ = kIsDebugBuild
      ? (kSlowMode ? kJitSlowStressDefaultOptimizeThreshold : kJitStressDefaultOptimizeThreshold)
      : kJitDefaultOptimizeThreshold;

  // Set default warm-up threshold to aid with checking defaults.
  jit_options->warmup_threshold_ = kIsDebugBuild
      ? (kSlowMode ? kJitSlowStressDefaultWarmupThreshold : kJitStressDefaultWarmupThreshold)
      : kJitDefaultWarmupThreshold;

  if (options.Exists(RuntimeArgumentMap::JITOptimizeThreshold)) {
    jit_options->optimize_threshold_ = *options.Get(RuntimeArgumentMap::JITOptimizeThreshold);
  }
  DCHECK_LE(jit_options->optimize_threshold_, kJitMaxThreshold);

  if (options.Exists(RuntimeArgumentMap::JITWarmupThreshold)) {
    jit_options->warmup_threshold_ = *options.Get(RuntimeArgumentMap::JITWarmupThreshold);
  }
  DCHECK_LE(jit_options->warmup_threshold_, kJitMaxThreshold);

  if (options.Exists(RuntimeArgumentMap::JITPriorityThreadWeight)) {
    jit_options->priority_thread_weight_ =
        *options.Get(RuntimeArgumentMap::JITPriorityThreadWeight);
    if (jit_options->priority_thread_weight_ > jit_options->warmup_threshold_) {
      LOG(FATAL) << "Priority thread weight is above the warmup threshold.";
    } else if (jit_options->priority_thread_weight_ == 0) {
      LOG(FATAL) << "Priority thread weight cannot be 0.";
    }
  } else {
    jit_options->priority_thread_weight_ = std::max(
        jit_options->warmup_threshold_ / kDefaultPriorityThreadWeightRatio,
        static_cast<size_t>(1));
  }

  if (options.Exists(RuntimeArgumentMap::JITInvokeTransitionWeight)) {
    jit_options->invoke_transition_weight_ =
        *options.Get(RuntimeArgumentMap::JITInvokeTransitionWeight);
    if (jit_options->invoke_transition_weight_ > jit_options->warmup_threshold_) {
      LOG(FATAL) << "Invoke transition weight is above the warmup threshold.";
    } else if (jit_options->invoke_transition_weight_  == 0) {
      LOG(FATAL) << "Invoke transition weight cannot be 0.";
    }
  } else {
    jit_options->invoke_transition_weight_ = std::max(
        jit_options->warmup_threshold_ / kDefaultInvokeTransitionWeightRatio,
        static_cast<size_t>(1));
  }

  return jit_options;
}

}  // namespace jit
}  // namespace art
