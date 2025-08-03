/*
 * Copyright (C) 2008 The Android Open Source Project
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


#ifndef ART_RUNTIME_FAULT_HANDLER_H_
#define ART_RUNTIME_FAULT_HANDLER_H_

#include <signal.h>
#include <stdint.h>

#include <atomic>
#include <vector>

#include "base/locks.h"  // For annotalysis.
#include "base/macros.h"
#include "base/mutex.h"
#include "runtime_globals.h"  // For CanDoImplicitNullCheckOn.

namespace art HIDDEN {

class ArtMethod;
class FaultHandler;

class FaultManager {
 public:
  FaultManager();
  ~FaultManager();

  // Use libsigchain if use_sig_chain is true. Otherwise, setup SIGBUS directly
  // using sigaction().
  void Init(bool use_sig_chain);

  // Unclaim signals.
  void Release();

  // Unclaim signals and delete registered handlers.
  void Shutdown();

  // Try to handle a SIGSEGV fault, returns true if successful.
  bool HandleSigsegvFault(int sig, siginfo_t* info, void* context);

  // Try to handle a SIGBUS fault, returns true if successful.
  bool HandleSigbusFault(int sig, siginfo_t* info, void* context);

  // Added handlers are owned by the fault handler and will be freed on Shutdown().
  EXPORT void AddHandler(FaultHandler* handler, bool generated_code);
  EXPORT void RemoveHandler(FaultHandler* handler);

  void AddGeneratedCodeRange(const void* start, size_t size);
  void RemoveGeneratedCodeRange(const void* start, size_t size)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Retrieves fault PC from architecture-dependent `context`, returns 0 on failure.
  // Called in the context of a signal handler.
  static uintptr_t GetFaultPc(siginfo_t* siginfo, void* context);

  // Retrieves SP from architecture-dependent `context`.
  // Called in the context of a signal handler.
  static uintptr_t GetFaultSp(void* context);

  // Checks if the fault happened while running generated code.
  // Called in the context of a signal handler.
  bool IsInGeneratedCode(siginfo_t* siginfo, void *context) NO_THREAD_SAFETY_ANALYSIS;

 private:
  struct GeneratedCodeRange {
    std::atomic<GeneratedCodeRange*> next;
    const void* start;
    size_t size;
  };

  GeneratedCodeRange* CreateGeneratedCodeRange(const void* start, size_t size)
      REQUIRES(generated_code_ranges_lock_);
  void FreeGeneratedCodeRange(GeneratedCodeRange* range) REQUIRES(!generated_code_ranges_lock_);

  // The HandleFaultByOtherHandlers function is only called by HandleFault function for generated code.
  bool HandleFaultByOtherHandlers(int sig, siginfo_t* info, void* context)
                                  NO_THREAD_SAFETY_ANALYSIS;

  // Check if this is an implicit suspend check that was somehow not recognized as being
  // in the compiled code. If that's the case, collect debugging data for the abort message
  // and crash. Focus on suspend checks in the boot image. Bug: 294339122
  // NO_THREAD_SAFETY_ANALYSIS: Same as `IsInGeneratedCode()`.
  void CheckForUnrecognizedImplicitSuspendCheckInBootImage(siginfo_t* siginfo, void* context)
      NO_THREAD_SAFETY_ANALYSIS;

  // Note: The lock guards modifications of the ranges but the function `IsInGeneratedCode()`
  // walks the list in the context of a signal handler without holding the lock.
  Mutex generated_code_ranges_lock_;
  std::atomic<GeneratedCodeRange*> generated_code_ranges_ GUARDED_BY(generated_code_ranges_lock_);

  std::vector<FaultHandler*> generated_code_handlers_;
  std::vector<FaultHandler*> other_handlers_;
  bool initialized_;

  // We keep a certain number of generated code ranges locally to avoid too many
  // cache misses while traversing the singly-linked list `generated_code_ranges_`.
  // 16 should be enough for the boot image (assuming `--multi-image`; there is
  // only one entry for `--single-image`), nterp, JIT code cache and a few other
  // entries for the app or system server.
  static constexpr size_t kNumLocalGeneratedCodeRanges = 16;
  GeneratedCodeRange generated_code_ranges_storage_[kNumLocalGeneratedCodeRanges];
  GeneratedCodeRange* free_generated_code_ranges_
       GUARDED_BY(generated_code_ranges_lock_);

  DISALLOW_COPY_AND_ASSIGN(FaultManager);
};

class FaultHandler {
 public:
  EXPORT explicit FaultHandler(FaultManager* manager);
  virtual ~FaultHandler() {}
  FaultManager* GetFaultManager() {
    return manager_;
  }

  virtual bool Action(int sig, siginfo_t* siginfo, void* context) = 0;

 protected:
  FaultManager* const manager_;

 private:
  DISALLOW_COPY_AND_ASSIGN(FaultHandler);
};

class NullPointerHandler final : public FaultHandler {
 public:
  explicit NullPointerHandler(FaultManager* manager);

  // NO_THREAD_SAFETY_ANALYSIS: Called after the fault manager determined that
  // the thread is `Runnable` and holds the mutator lock (shared) but without
  // telling annotalysis that we actually hold the lock.
  bool Action(int sig, siginfo_t* siginfo, void* context) override
      NO_THREAD_SAFETY_ANALYSIS;

 private:
  // Helper functions for checking whether the signal can be interpreted
  // as implicit NPE check. Note that the runtime will do more exhaustive
  // checks (that we cannot reasonably do in signal processing code) based
  // on the dex instruction faulting.

  static bool IsValidFaultAddress(uintptr_t fault_address) {
    // Our implicit NPE checks always limit the range to a page.
    return CanDoImplicitNullCheckOn(fault_address);
  }

  static bool IsValidMethod(ArtMethod* method)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static bool IsValidReturnPc(ArtMethod** sp, uintptr_t return_pc)
      REQUIRES_SHARED(Locks::mutator_lock_);

  DISALLOW_COPY_AND_ASSIGN(NullPointerHandler);
};

class SuspensionHandler final : public FaultHandler {
 public:
  explicit SuspensionHandler(FaultManager* manager);

  bool Action(int sig, siginfo_t* siginfo, void* context) override;

 private:
  DISALLOW_COPY_AND_ASSIGN(SuspensionHandler);
};

class StackOverflowHandler final : public FaultHandler {
 public:
  explicit StackOverflowHandler(FaultManager* manager);

  bool Action(int sig, siginfo_t* siginfo, void* context) override;

 private:
  DISALLOW_COPY_AND_ASSIGN(StackOverflowHandler);
};

class JavaStackTraceHandler final : public FaultHandler {
 public:
  explicit JavaStackTraceHandler(FaultManager* manager);

  bool Action(int sig, siginfo_t* siginfo, void* context) override NO_THREAD_SAFETY_ANALYSIS;

 private:
  DISALLOW_COPY_AND_ASSIGN(JavaStackTraceHandler);
};

// Statically allocated so the the signal handler can Get access to it.
EXPORT extern FaultManager fault_manager;

}  // namespace art
#endif  // ART_RUNTIME_FAULT_HANDLER_H_

