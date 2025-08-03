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

#include <utility>

#include "Function.h"
#include "Nullable.h"

namespace gfxstream::guest {
namespace fit {

// A move-only deferred action wrapper with RAII semantics.
// This class is not thread safe.
//
// The wrapper holds a function-like callable target with no arguments
// which it invokes when it goes out of scope unless canceled, called, or
// moved to a wrapper in a different scope.
//
// See |fit::defer()| for idiomatic usage.
template <typename T>
class DeferredAction final {
public:
    // Creates a deferred action without a pending target.
    DeferredAction() = default;
    explicit DeferredAction(decltype(nullptr)) {}

    // Creates a deferred action with a pending target.
    explicit DeferredAction(T target) : mTarget(std::move(target)) {}

    // Creates a deferred action with a pending target moved from another
    // deferred action, leaving the other one without a pending target.
    DeferredAction(DeferredAction&& other) : mTarget(std::move(other.mTarget)) {
        other.mTarget.reset();
    }

    // Invokes and releases the deferred action's pending target (if any).
    ~DeferredAction() { call(); }

    // Returns true if the deferred action has a pending target.
    explicit operator bool() const { return !!mTarget; }

    // Invokes and releases the deferred action's pending target (if any),
    // then move-assigns it from another deferred action, leaving the latter
    // one without a pending target.
    DeferredAction& operator=(DeferredAction&& other) {
        if (&other == this)
            return *this;
        call();
        mTarget = std::move(other.mTarget);
        other.mTarget.reset();
        return *this;
    }

    // Invokes and releases the deferred action's pending target (if any).
    void call() {
        if (mTarget) {
            // Move to a local to guard against re-entrance.
            T local_target = std::move(*mTarget);
            mTarget.reset();
            local_target();
        }
    }

    // Releases the deferred action's pending target (if any) without
    // invoking it.
    void cancel() { mTarget.reset(); }
    DeferredAction& operator=(decltype(nullptr)) {
        cancel();
        return *this;
    }

    // Assigns a new target to the deferred action.
    DeferredAction& operator=(T target) {
        mTarget = std::move(target);
        return *this;
    }

    DeferredAction(const DeferredAction& other) = delete;
    DeferredAction& operator=(const DeferredAction& other) = delete;

private:
    Nullable<T> mTarget;
};

template <typename T>
bool operator==(const DeferredAction<T>& action, decltype(nullptr)) {
    return !action;
}
template <typename T>
bool operator==(decltype(nullptr), const DeferredAction<T>& action) {
    return !action;
}
template <typename T>
bool operator!=(const DeferredAction<T>& action, decltype(nullptr)) {
    return !!action;
}
template <typename T>
bool operator!=(decltype(nullptr), const DeferredAction<T>& action) {
    return !!action;
}

// Defers execution of a function-like callable target with no arguments
// until the value returned by this function goes out of scope unless canceled,
// called, or moved to a wrapper in a different scope.
//
// // This example prints "Hello..." then "Goodbye!".
// void test() {
//     auto d = fit::defer([]{ puts("Goodbye!"); });
//     puts("Hello...");
// }
//
// // This example prints nothing because the deferred action is canceled.
// void do_nothing() {
//     auto d = fit::defer([]{ puts("I'm not here."); });
//     d.cancel();
// }
//
// // This example shows how the deferred action can be reassigned assuming
// // the new target has the same type and the old one, in this case by
// // representing the target as a |fit::Closure|.
// void reassign() {
//     auto d = fit::defer<fit::Closure>([] { puts("This runs first."); });
//     d = fit::defer<fit::Closure>([] { puts("This runs afterwards."); });
// }
template <typename T>
inline DeferredAction<T> defer(T target) {
    return DeferredAction<T>(std::move(target));
}

// Alias for a deferred_action using a fit::Callback.
using DeferredCallback = DeferredAction<fit::Callback<void()>>;

// Defers execution of a fit::Callback with no arguments. See |fit::defer| for
// details.
inline DeferredCallback deferCallback(fit::Callback<void()> target) {
    return DeferredCallback(std::move(target));
}

}  // namespace fit
}  // namespace gfxstream::guest
