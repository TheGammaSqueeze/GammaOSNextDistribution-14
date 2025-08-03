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

#ifndef BERBERIS_BASE_CONFIG_H_
#define BERBERIS_BASE_CONFIG_H_

#include <cstddef>
#include <cstdint>

namespace berberis::config {

// Size of the stack frame allocated in translated code prologue.
// As translated code ('slow') prologue executes much less frequently than
// region ('fast') prologue, it makes sense to allocate a frame there that
// suits most regions. Outstanding regions will expand it in their prologue.
// Assume the stack is properly aligned when entering translated code.
// TODO(b/232598137): If we discover that most regions don't need stack frame
// at all, then we might want to avoid extra altering of stack pointer in
// translated code prologue and keep stack misaligned. Then we'll need a
// kStackMisalignAtTranslatedCode config variable.
// TODO(b/232598137): 12 is what we get on x86-32 after stack alignment, update
// with, say, 90-percentile of (dynamic) frame size.
inline constexpr uint32_t kFrameSizeAtTranslatedCode = sizeof(size_t) == 4 ? 12u : 8u;
// Attention: This flag traces every entry to RunGeneratedCode which
// may be *very* slow especially if kAllJumpsExitGeneratedCode flag is
// enabled.
inline constexpr bool kTraceGeneratedCode = false;
// Setting this to true enables instrumentation of every executed region in the
// main execution loop (ExecuteGuest).
inline constexpr bool kAllJumpsExitGeneratedCode = false;
// Eliminate overhead of exiting/reentering generated code by searching in
// the translation cache directly from the generated code.
inline constexpr bool kLinkJumpsBetweenRegions = !kAllJumpsExitGeneratedCode;
// Generate local jumps if jump's target address falls within the
// current region. If false dispatch to another region instead.
inline constexpr bool kLinkJumpsWithinRegion = !kAllJumpsExitGeneratedCode;
// Guest page size. Always 4K for now.
inline constexpr size_t kGuestPageSize = 4096;
// Number of hard registers assumed by the register allocator.
inline constexpr uint32_t kMaxHardRegs = 64u;
// Threshold for switching between gears
inline constexpr uint32_t kGearSwitchThreshold = 1000;
// Scratch area size for use in intrinsics with instructions which may only work with memory.
inline constexpr uint32_t kScratchAreaSize = 32;
// Scratch area alignment (important if we would use Movaps/Movapd).
inline constexpr uint32_t kScratchAreaAlign = 16;
// Scratch area slot size if more than one scratch is needed.
inline constexpr uint32_t kScratchAreaSlotSize = 8;

}  // namespace berberis::config

#endif  // BERBERIS_BASE_CONFIG_H_
