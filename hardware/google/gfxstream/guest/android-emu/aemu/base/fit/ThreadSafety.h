// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Copyright 2018 The Fuchsia Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#pragma once

// Other libraries (e.g. libbase) may have already defined these symbols.
// Only define them if they are not defined elsewhere.

// Thread-safety annotations.
// Currently these are only supported on Clang.
#ifndef THREAD_ANNOTATION_ATTRIBUTE__
#if defined(__clang__) && defined(_LIBCPP_ENABLE_THREAD_SAFETY_ANNOTATIONS) && \
    __has_attribute(acquire_capability)
#define THREAD_ANNOTATION_ATTRIBUTE__(x) __attribute__((x))
#else
#define THREAD_ANNOTATION_ATTRIBUTE__(x)
#endif
#endif  // THREAD_ANNOTATION_ATTRIBUTE__

#ifndef CAPABILITY
#define CAPABILITY(x) THREAD_ANNOTATION_ATTRIBUTE__(__capability__(x))
#endif  // CAPABILITY

#ifndef GUARDED_BY
#define GUARDED_BY(x) THREAD_ANNOTATION_ATTRIBUTE__(__guarded_by__(x))
#endif  // GUARDED_BY

#ifndef ACQUIRE
#define ACQUIRE(...) THREAD_ANNOTATION_ATTRIBUTE__(__acquire_capability__(__VA_ARGS__))
#endif  // ACQUIRE

#ifndef TRY_ACQUIRE
#define TRY_ACQUIRE(...) THREAD_ANNOTATION_ATTRIBUTE__(__try_acquire_capability__(__VA_ARGS__))
#endif  // TRY_ACQUIRE

#ifndef ACQUIRED_BEFORE
#define ACQUIRED_BEFORE(...) THREAD_ANNOTATION_ATTRIBUTE__(__acquired_before__(__VA_ARGS__))
#endif  // ACQUIRED_BEFORE

#ifndef ACQUIRED_AFTER
#define ACQUIRED_AFTER(...) THREAD_ANNOTATION_ATTRIBUTE__(__acquired_after__(__VA_ARGS__))
#endif  // ACQUIRED_AFTER

#ifndef RELEASE
#define RELEASE(...) THREAD_ANNOTATION_ATTRIBUTE__(__release_capability__(__VA_ARGS__))
#endif  // RELEASE

#ifndef REQUIRES
#define REQUIRES(...) THREAD_ANNOTATION_ATTRIBUTE__(__requires_capability__(__VA_ARGS__))
#endif  // REQUIRES

#ifndef EXCLUDES
#define EXCLUDES(...) THREAD_ANNOTATION_ATTRIBUTE__(__locks_excluded__(__VA_ARGS__))
#endif  // EXCLUDES

#ifndef RETURN_CAPABILITY
#define RETURN_CAPABILITY(x) THREAD_ANNOTATION_ATTRIBUTE__(__lock_returned__(x))
#endif  // RETURN_CAPABILITY

#ifndef SCOPED_CAPABILITY
#define SCOPED_CAPABILITY THREAD_ANNOTATION_ATTRIBUTE__(__scoped_lockable__)
#endif  // SCOPED_CAPABILITY

#ifndef NO_THREAD_SAFETY_ANALYSIS
#define NO_THREAD_SAFETY_ANALYSIS THREAD_ANNOTATION_ATTRIBUTE__(__no_thread_safety_analysis__)
#endif  // NO_THREAD_SAFETY_ANALYSIS
