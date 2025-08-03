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

// Copyright 2016 The Fuchsia Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// A class for checking that the current thread is/isn't the same as an initial
// thread.

#pragma once

#include <assert.h>

#include <thread>

#include "ThreadSafety.h"

namespace gfxstream::guest {
namespace fit {

// A simple class that records the identity of the thread that it was created
// on, and at later points can tell if the current thread is the same as its
// creation thread. This class is thread-safe.
//
// In addition to providing an explicit check of the current thread,
// |thread_checker| complies with BasicLockable, checking the current thread
// when |lock| is called. This allows static thread safety analysis to be used
// to ensure that resources are accessed in a context that is checked (at debug
// runtime) to ensure that it's running on the correct thread:
//
// class MyClass {
//  public:
//    void Foo() {
//      std::lock_guard<fit::thread_checker> locker(thread_checker_);
//      resource_ = 0;
//    }
//  private:
//   fit::thread_checker thread_checker_;
//   int resource_ GUARDED_BY(thread_checker_);
// }
//
// Note: |lock| checks the thread in debug builds only.
//
class CAPABILITY("mutex") ThreadChecker final {
public:
    // Default constructor. Constructs a thread checker bound to the currently
    // running thread.
    ThreadChecker() : self_(std::this_thread::get_id()) {}
    // Constructs a thread checker bound to an explicit other thread.
    explicit ThreadChecker(std::thread::id self) : self_(self) {}
    ~ThreadChecker() = default;

    // Returns true if the current thread is the thread this object was created
    // on and false otherwise.
    bool isThreadValid() const { return std::this_thread::get_id() == self_; }

    // Implementation of the BaseLockable requirement
    void lock() ACQUIRE() { assert(isThreadValid()); }

    void unlock() RELEASE() {}

private:
    const std::thread::id self_;
};

#ifndef NDEBUG
#define DECLARE_THREAD_CHECKER(c) gfxstream::guest::fit::ThreadChecker c
#define DCHECK_IS_THREAD_VALID(c) assert((c).isThreadValid())
#else
#define DECLARE_THREAD_CHECKER(c)
#define DCHECK_IS_THREAD_VALID(c) ((void)0)
#endif

}  // namespace fit
}  // namespace gfxstream::guest
