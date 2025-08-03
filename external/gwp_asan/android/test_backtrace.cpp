/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "gwp_asan/common.h"
#include "gwp_asan/optional/backtrace.h"
#include "gwp_asan/optional/segv_handler.h"

#include <unwindstack/Maps.h>
#include <unwindstack/Memory.h>
#include <unwindstack/Regs.h>
#include <unwindstack/RegsGetLocal.h>
#include <unwindstack/Unwinder.h>

namespace {
// In reality, on Android, we use two separate unwinders. GWP-ASan internally
// uses a fast, frame-pointer unwinder for allocation/deallocation stack traces
// (android_unsafe_frame_pointer_chase, provided by bionic libc). When a process
// crashes, debuggerd unwinds the access trace using libunwindstack, which is a
// slow CFI-based unwinder. We don't split the unwinders in the test harness,
// and frame-pointer unwinding doesn't work properly though a signal handler, so
// we opt to use libunwindstack in this test. This has the effect that we get
// potentially more detailed stack frames in the allocation/deallocation traces
// (as we don't use the production unwinder), but that's fine for test-only.
size_t BacktraceUnwindstack(uintptr_t *TraceBuffer, size_t Size) {
  unwindstack::LocalMaps maps;
  if (!maps.Parse()) {
    return 0;
  }

  auto process_memory = unwindstack::Memory::CreateProcessMemoryThreadCached(getpid());
  std::unique_ptr<unwindstack::Regs> regs(unwindstack::Regs::CreateFromLocal());
  unwindstack::RegsGetLocal(regs.get());
  unwindstack::Unwinder unwinder(Size, &maps, regs.get(), process_memory);
  unwinder.Unwind();
  for (const auto &frame : unwinder.frames()) {
    *TraceBuffer = frame.pc;
    TraceBuffer++;
  }
  return unwinder.NumFrames();
}

// We don't need any custom handling for the Segv backtrace - the unwindstack
// unwinder has no problems with unwinding through a signal handler.
size_t SegvBacktrace(uintptr_t *TraceBuffer, size_t Size, void * /*Context*/) {
  return BacktraceUnwindstack(TraceBuffer, Size);
}

// This function is a good mimic as to what's happening in the out-of-process
// tombstone daemon (see debuggerd for more information). In our case, we want
// to provide symbolized backtraces during ***testing only*** here. This
// function called from a signal handler, and is extraordinarily not
// signal-safe, but works for our purposes.
void PrintBacktraceUnwindstack(uintptr_t *TraceBuffer, size_t TraceLength,
                               gwp_asan::Printf_t Print) {
  unwindstack::UnwinderFromPid unwinder(
      gwp_asan::AllocationMetadata::kMaxTraceLengthToCollect, getpid());
  unwinder.SetRegs(unwindstack::Regs::CreateFromLocal());
  if (!unwinder.Init()) {
    Print("  Unable to init unwinder: %s\n", unwinder.LastErrorCodeString());
    return;
  }

  for (size_t i = 0; i < TraceLength; ++i) {
    unwindstack::FrameData frame_data =
        unwinder.BuildFrameFromPcOnly(TraceBuffer[i]);
    frame_data.num = i;
    Print("  %s\n", unwinder.FormatFrame(frame_data).c_str());
  }
}

} // anonymous namespace

namespace gwp_asan {
namespace backtrace {
options::Backtrace_t getBacktraceFunction() { return BacktraceUnwindstack; }

PrintBacktrace_t getPrintBacktraceFunction() {
  return PrintBacktraceUnwindstack;
}

SegvBacktrace_t getSegvBacktraceFunction() { return SegvBacktrace; }
} // namespace backtrace
} // namespace gwp_asan
