/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "backtrace_helper.h"

#if defined(__linux__)

#include <sys/types.h>
#include <unistd.h>
#include <iomanip>

#include "unwindstack/Regs.h"
#include "unwindstack/RegsGetLocal.h"
#include "unwindstack/Memory.h"
#include "unwindstack/Unwinder.h"

#include "base/bit_utils.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "thread-inl.h"

#else

// For UNUSED
#include "base/macros.h"

#endif

namespace art HIDDEN {

// We only really support libunwindstack on linux which is unfortunate but since this is only for
// gcstress this isn't a huge deal.
#if defined(__linux__)

// Strict integrity check of the backtrace:
// All methods must have a name, all the way to "main".
static constexpr bool kStrictUnwindChecks = false;

struct UnwindHelper : public TLSData {
  static constexpr const char* kTlsKey = "UnwindHelper::kTlsKey";

  explicit UnwindHelper(size_t max_depth)
      : arch_(unwindstack::Regs::CurrentArch()),
        memory_(unwindstack::Memory::CreateProcessMemoryThreadCached(getpid())),
        jit_(unwindstack::CreateJitDebug(arch_, memory_)),
        dex_(unwindstack::CreateDexFiles(arch_, memory_)),
        unwinder_(max_depth, &maps_, memory_) {
    CHECK(maps_.Parse());
    unwinder_.SetArch(arch_);
    unwinder_.SetJitDebug(jit_.get());
    unwinder_.SetDexFiles(dex_.get());
    unwinder_.SetResolveNames(kStrictUnwindChecks);
    unwindstack::Elf::SetCachingEnabled(true);
  }

  // Reparse process mmaps to detect newly loaded libraries.
  bool Reparse(bool* any_changed) { return maps_.Reparse(any_changed); }

  static UnwindHelper* Get(Thread* self, size_t max_depth) {
    UnwindHelper* tls = reinterpret_cast<UnwindHelper*>(self->GetCustomTLS(kTlsKey));
    if (tls == nullptr) {
      tls = new UnwindHelper(max_depth);
      self->SetCustomTLS(kTlsKey, tls);
    }
    return tls;
  }

  unwindstack::Unwinder* Unwinder() { return &unwinder_; }

 private:
  unwindstack::LocalUpdatableMaps maps_;
  unwindstack::ArchEnum arch_;
  std::shared_ptr<unwindstack::Memory> memory_;
  std::unique_ptr<unwindstack::JitDebug> jit_;
  std::unique_ptr<unwindstack::DexFiles> dex_;
  unwindstack::Unwinder unwinder_;
};

void BacktraceCollector::Collect() {
  unwindstack::Unwinder* unwinder = UnwindHelper::Get(Thread::Current(), max_depth_)->Unwinder();
  if (!CollectImpl(unwinder)) {
    // Reparse process mmaps to detect newly loaded libraries and retry,
    // but only if any maps changed (we don't want to hide racy failures).
    bool any_changed;
    UnwindHelper::Get(Thread::Current(), max_depth_)->Reparse(&any_changed);
    if (!any_changed || !CollectImpl(unwinder)) {
      if (kStrictUnwindChecks) {
        std::vector<unwindstack::FrameData>& frames = unwinder->frames();
        LOG(ERROR) << "Failed to unwind stack (error " << unwinder->LastErrorCodeString() << "):";
        std::string prev_name;
        for (auto& frame : frames) {
          if (frame.map_info != nullptr) {
            std::string full_name = frame.map_info->GetFullName();
            if (prev_name != full_name) {
              LOG(ERROR) << " in " << full_name;
            }
            prev_name = full_name;
          } else {
            prev_name = "";
          }
          LOG(ERROR) << " pc " << std::setw(8) << std::setfill('0') << std::hex <<
            frame.rel_pc << " " << frame.function_name.c_str();
        }
        LOG(FATAL);
      }
    }
  }
}

bool BacktraceCollector::CollectImpl(unwindstack::Unwinder* unwinder) {
  std::unique_ptr<unwindstack::Regs> regs(unwindstack::Regs::CreateFromLocal());
  RegsGetLocal(regs.get());
  unwinder->SetRegs(regs.get());
  unwinder->Unwind();

  num_frames_ = 0;
  if (unwinder->NumFrames() > skip_count_) {
    for (auto it = unwinder->frames().begin() + skip_count_; it != unwinder->frames().end(); ++it) {
      CHECK_LT(num_frames_, max_depth_);
      out_frames_[num_frames_++] = static_cast<uintptr_t>(it->pc);

      if (kStrictUnwindChecks) {
        if (it->function_name.empty()) {
          return false;
        }
        if (it->function_name == "main" ||
            it->function_name == "start_thread" ||
            it->function_name == "__start_thread") {
          return true;
        }
      }
    }
  }

  unwindstack::ErrorCode error = unwinder->LastErrorCode();
  return error == unwindstack::ERROR_NONE || error == unwindstack::ERROR_MAX_FRAMES_EXCEEDED;
}

#else

#pragma clang diagnostic push
#pragma clang diagnostic warning "-W#warnings"
#warning "Backtrace collector is not implemented. GCStress cannot be used."
#pragma clang diagnostic pop

// We only have an implementation for linux. On other plaforms just return nothing. This is not
// really correct but we only use this for hashing and gcstress so it's not too big a deal.
void BacktraceCollector::Collect() {
  UNUSED(skip_count_);
  UNUSED(out_frames_);
  UNUSED(max_depth_);
  num_frames_ = 0;
}

#endif

}  // namespace art
