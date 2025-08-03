/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_RUNTIME_READ_BARRIER_CONFIG_H_
#define ART_RUNTIME_READ_BARRIER_CONFIG_H_

// This is a mixed C-C++ header file that has a global section at the start
// and a C++ section at the end, because asm_support.h is a C header file and
// cannot include any C++ syntax.

// Global (C) part.

// Uncomment one of the following two and the two fields in
// Object.java (libcore) to enable baker, or
// table-lookup read barriers.

#ifdef ART_USE_READ_BARRIER
#if ART_READ_BARRIER_TYPE_IS_BAKER
#define USE_BAKER_READ_BARRIER
#elif ART_READ_BARRIER_TYPE_IS_TABLELOOKUP
#define USE_TABLE_LOOKUP_READ_BARRIER
#else
#error "ART read barrier type must be set"
#endif
#endif  // ART_USE_READ_BARRIER

#if defined(USE_BAKER_READ_BARRIER) || defined(USE_TABLE_LOOKUP_READ_BARRIER)
#define USE_READ_BARRIER
#endif

// Reserve marking register (and its refreshing logic) for all GCs as nterp
// requires it. In the future if and when nterp is made independent of
// read-barrier, we can switch back to the current behavior by making this
// definition conditional on USE_BAKER_READ_BARRIER and setting
// kReserveMarkingRegister to kUseBakerReadBarrier.
#define RESERVE_MARKING_REGISTER

// C++-specific configuration part..

#ifdef __cplusplus

#include "base/globals.h"
#include "base/macros.h"

namespace art HIDDEN {

#ifdef USE_BAKER_READ_BARRIER
static constexpr bool kUseBakerReadBarrier = true;
#else
static constexpr bool kUseBakerReadBarrier = false;
#endif

// Read comment for RESERVE_MARKING_REGISTER above
static constexpr bool kReserveMarkingRegister = true;

#ifdef USE_TABLE_LOOKUP_READ_BARRIER
static constexpr bool kUseTableLookupReadBarrier = true;
#else
static constexpr bool kUseTableLookupReadBarrier = false;
#endif

// Only if read-barrier isn't forced (see build/art.go) but is selected, that we need
// to see if we support userfaultfd GC. All the other cases can be constexpr here.
#ifdef ART_FORCE_USE_READ_BARRIER
constexpr bool gUseReadBarrier = kUseBakerReadBarrier || kUseTableLookupReadBarrier;
constexpr bool gUseUserfaultfd = !gUseReadBarrier;
static_assert(!gUseUserfaultfd);
#else
#ifndef ART_USE_READ_BARRIER
constexpr bool gUseReadBarrier = false;
#ifdef ART_DEFAULT_GC_TYPE_IS_CMC
constexpr bool gUseUserfaultfd = true;
#else
constexpr bool gUseUserfaultfd = false;
#endif
#else
EXPORT extern const bool gUseReadBarrier;
EXPORT extern const bool gUseUserfaultfd;
#endif
#endif

// Disabled for performance reasons.
static constexpr bool kCheckDebugDisallowReadBarrierCount = kIsDebugBuild;

}  // namespace art

#endif  // __cplusplus

#endif  // ART_RUNTIME_READ_BARRIER_CONFIG_H_
