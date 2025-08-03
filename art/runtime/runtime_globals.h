/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ART_RUNTIME_RUNTIME_GLOBALS_H_
#define ART_RUNTIME_RUNTIME_GLOBALS_H_

#include <android-base/logging.h>

#include "base/bit_utils.h"
#include "base/globals.h"
#include "base/macros.h"

namespace art HIDDEN {

// Size of Dex virtual registers.
static constexpr size_t kVRegSize = 4;

#ifdef ART_PAGE_SIZE_AGNOSTIC
// Accessor for the page size constant local to the libart.
//
// The value is only available after the Runtime initialization started - to ensure there is no
// static initialization order issues where initialization of other values is dependent on the page
// size. In those cases, GetPageSizeSlow() should be used.
struct PageSize {
  PageSize()
    : is_initialized_(true), is_access_allowed_(false) {}

  constexpr ALWAYS_INLINE operator size_t() const {
    DCHECK(is_initialized_ && is_access_allowed_);
    return value_;
  }

 private:
  friend class Runtime;

  void AllowAccess() {
    SetAccessAllowed(true);
  }

  void DisallowAccess() {
    SetAccessAllowed(false);
  }

  void SetAccessAllowed(bool is_allowed) {
    // is_initialized_ is set to true when the page size value is initialized during the static
    // initialization. This CHECK is added as an auxiliary way to help catching incorrect use of
    // the method.
    CHECK(is_initialized_);
    is_access_allowed_ = is_allowed;
  }

  // The page size value.
  //
  // It is declared as a static constant value to ensure compiler recognizes that it doesn't change
  // once it is initialized.
  //
  // It is declared as "hidden" i.e. local to the libart, to ensure:
  //  - no other library can access it, so no static initialization dependency from other libraries
  //    is possible;
  //  - the variable can be addressed via offset from the program counter, instead of the global
  //    offset table which would've added another level of indirection.
  static const size_t value_ ALWAYS_HIDDEN;

  // There are two flags in the accessor which help to ensure the value is accessed only after the
  // static initialization is complete.
  //
  // is_initialized_ is used to assert the page size value is indeed initialized when the value
  // access is allowed and when it is accessed.
  //
  // is_access_allowed_ is used to ensure the value is only accessed after Runtime initialization
  // started.
  const bool is_initialized_;
  bool is_access_allowed_;
};

// gPageSize should only be used within libart. For most of the other cases MemMap::GetPageSize()
// or GetPageSizeSlow() should be used. See also the comment for GetPageSizeSlow().
extern PageSize gPageSize ALWAYS_HIDDEN;
#else
static constexpr size_t gPageSize = kMinPageSize;
#endif

// In the page-size-agnostic configuration the compiler may not recognise gPageSize as a
// power-of-two value, and may therefore miss opportunities to optimize: divisions via a
// right-shift, modulo via a bitwise-AND.
// Here, define two functions which use the optimized implementations explicitly, which should be
// used when dividing by or applying modulo of the page size. For simplificty, the same functions
// are used under both configurations, as they optimize the page-size-agnostic configuration while
// only replicating what the compiler already does on the non-page-size-agnostic configuration.
static constexpr ALWAYS_INLINE size_t DivideByPageSize(size_t num) {
  return (num >> WhichPowerOf2(static_cast<size_t>(gPageSize)));
}
static constexpr ALWAYS_INLINE size_t ModuloPageSize(size_t num) {
  return (num & (gPageSize-1));
}

// Returns whether the given memory offset can be used for generating
// an implicit null check.
static inline bool CanDoImplicitNullCheckOn(uintptr_t offset) {
  return offset < gPageSize;
}

// Required object alignment
static constexpr size_t kObjectAlignmentShift = 3;
static constexpr size_t kObjectAlignment = 1u << kObjectAlignmentShift;

// Garbage collector constants.
static constexpr bool kMovingCollector = true;
static constexpr bool kMarkCompactSupport = false && kMovingCollector;
// True if we allow moving classes.
static constexpr bool kMovingClasses = !kMarkCompactSupport;
// When using the Concurrent Copying (CC) collector, if
// `ART_USE_GENERATIONAL_CC` is true, enable generational collection by default,
// i.e. use sticky-bit CC for minor collections and (full) CC for major
// collections.
// This default value can be overridden with the runtime option
// `-Xgc:[no]generational_cc`.
//
// TODO(b/67628039): Consider either:
// - renaming this to a better descriptive name (e.g.
//   `ART_USE_GENERATIONAL_CC_BY_DEFAULT`); or
// - removing `ART_USE_GENERATIONAL_CC` and having a fixed default value.
// Any of these changes will require adjusting users of this preprocessor
// directive and the corresponding build system environment variable (e.g. in
// ART's continuous testing).
#ifdef ART_USE_GENERATIONAL_CC
static constexpr bool kEnableGenerationalCCByDefault = true;
#else
static constexpr bool kEnableGenerationalCCByDefault = false;
#endif

// If true, enable the tlab allocator by default.
#ifdef ART_USE_TLAB
static constexpr bool kUseTlab = true;
#else
static constexpr bool kUseTlab = false;
#endif

// Kinds of tracing clocks.
enum class TraceClockSource {
  kThreadCpu,
  kWall,
  kDual,  // Both wall and thread CPU clocks.
};

#if defined(__linux__)
static constexpr TraceClockSource kDefaultTraceClockSource = TraceClockSource::kDual;
#else
static constexpr TraceClockSource kDefaultTraceClockSource = TraceClockSource::kWall;
#endif

static constexpr bool kDefaultMustRelocate = true;

// Size of a heap reference.
static constexpr size_t kHeapReferenceSize = sizeof(uint32_t);

}  // namespace art

#endif  // ART_RUNTIME_RUNTIME_GLOBALS_H_
