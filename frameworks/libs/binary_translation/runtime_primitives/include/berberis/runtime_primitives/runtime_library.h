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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_RUNTIME_LIBRARY_H_
#define BERBERIS_RUNTIME_PRIMITIVES_RUNTIME_LIBRARY_H_

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/host_code.h"

namespace berberis {

extern "C" {

void berberis_RunGeneratedCode(ThreadState* state, HostCode code);
void berberis_entry_Interpret();
void berberis_entry_ExitGeneratedCode();
void berberis_entry_Stop();
void berberis_entry_NoExec();
void berberis_entry_HandleLightCounterThresholdReached();

// TODO(b/232598137): use status variable instead?
void berberis_entry_NotTranslated();
void berberis_entry_Translating();
void berberis_entry_Invalidating();
void berberis_entry_Wrapping();

static_assert(berberis_entry_NotTranslated != berberis_entry_Translating,
              "code to distinguish entry status got optimized");

__attribute__((__visibility__("hidden"))) void berberis_HandleNoExec(ThreadState* state);

}  // extern "C"

// Inline const since we cannot use constexpr because of reinterpret_cast.
inline const auto kEntryInterpret = AsHostCode(berberis_entry_Interpret);
inline const auto kEntryExitGeneratedCode = AsHostCode(berberis_entry_ExitGeneratedCode);
inline const auto kEntryStop = AsHostCode(berberis_entry_Stop);
inline const auto kEntryNoExec = AsHostCode(berberis_entry_NoExec);
inline const auto kEntryNotTranslated = AsHostCode(berberis_entry_NotTranslated);
inline const auto kEntryTranslating = AsHostCode(berberis_entry_Translating);
inline const auto kEntryInvalidating = AsHostCode(berberis_entry_Invalidating);
inline const auto kEntryWrapping = AsHostCode(berberis_entry_Wrapping);

void InvalidateGuestRange(GuestAddr start, GuestAddr end);

// Don't pull in the dependency on guest_abi to runtime_primitives, since GuestArgumentBuffer is
// used strictly in opaque manner here.
struct GuestArgumentBuffer;

void RunGuestCall(GuestAddr pc, GuestArgumentBuffer* buf);
void ExecuteGuestCall(ThreadState* state);

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_RUNTIME_LIBRARY_H_
