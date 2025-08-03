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

// Copyright 2017 The Fuchsia Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#pragma once

#include "FunctionInternal.h"
#include "UtilityInternal.h"

namespace gfxstream::guest {
namespace fit {

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
class FunctionImpl;

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
class CallbackImpl;

// The default size allowance for storing a target inline within a function
// object, in bytes.  This default allows for inline storage of targets
// as big as two pointers, such as an object pointer and a pointer to a member
// function.
constexpr size_t kDefaultInlineTargetSize = sizeof(void*) * 2;

// A |fit::Function| is a move-only polymorphic function wrapper.
//
// If you need a class with similar characteristics that also ensures
// "run-once" semantics (such as callbacks shared with timeouts, or for
// service requests with redundant, failover, or fallback service providers),
// see |fit::Callback|.
//
// |fit::Function<T>| behaves like |std::function<T>| except that it is
// move-only instead of copyable, so it can hold targets that cannot be copied,
// such as mutable lambdas, and immutable lambdas that capture move-only
// objects.
//
// Targets of up to |inlineTargetSize| bytes in size (rounded up for memory
// alignment) are stored inline within the function object without incurring
// any heap allocation.  Larger callable objects will be moved to the heap as
// required.
//
// See also |fit::InlineFunction<T, size>| for more control over allocation
// behavior.
//
// SYNOPSIS
//
// |T| is the function's signature.  e.g. void(int, std::string).
//
// |inlineTargetSize| is the minimum size of target that is guaranteed to
// fit within a function without requiring heap allocation.
// Defaults to |kDefaultInlineTargetSize|.
//
// Class members are documented in |fit::FunctionImpl|, below.
//
// EXAMPLES
//
// -
// https://fuchsia.googlesource.com/fuchsia/+/HEAD/sdk/lib/fit/test/examples/function_example1.cc
// -
// https://fuchsia.googlesource.com/fuchsia/+/HEAD/sdk/lib/fit/test/examples/function_example2.cc
//
template <typename T, size_t inlineTargetSize = kDefaultInlineTargetSize>
using function = FunctionImpl<inlineTargetSize, /*requireInline=*/false, T>;

// A move-only callable object wrapper that forces callables to be stored inline
// and never performs heap allocation.
//
// Behaves just like |fit::Function<T, inlineTargetSize>| except that
// attempting to store a target larger than |inlineTargetSize| will fail to
// compile.
template <typename T, size_t inlineTargetSize = kDefaultInlineTargetSize>
using InlineFunction = FunctionImpl<inlineTargetSize,
                                    /*requireInline=*/true,
                                    T>;

// Synonym for a function which takes no arguments and produces no result.
using closure = function<void()>;

// A |fit::Callback| is a move-only polymorphic function wrapper that also
// ensures "run-once" semantics (such as callbacks shared with timeouts, or for
// service requests with redundant, failover, or fallback service providers).
// A |fit::Callback| releases it's resources after the first call, and can be
// inspected before calling, so a potential caller can know if it should call
// the function, or skip the call because the target was already called.
//
// If you need a move-only function class with typical function characteristics,
// that permits multiple invocations of the same function, see |fit::Function|.
//
// |fit::Callback<T>| behaves like |std::function<T>| except:
//
//   1. It is move-only instead of copyable, so it can hold targets that cannot
//      be copied, such as mutable lambdas, and immutable lambdas that capture
//      move-only objects.
//   2. On the first call to invoke a |fit::Callback|, the target function held
//      by the |fit::Callback| cannot be called again.
//
// When a |fit::Callback| is invoked for the first time, the target function is
// released and destructed, along with any resources owned by that function
// (typically the objects captured by a lambda).
//
// A |fit::Callback| in the "already called" state has the same state as a
// |fit::Callback| that has been assigned to |nullptr|. It can be compared to
// |nullptr| (via "==" or "!=", and its "operator bool()" returns false, which
// provides a convenient way to gate whether or not the |fit::Callback| should
// be called. (Note that invoking an empty |fit::Callback| or |fit::Function|
// will cause a program abort!)
//
// As an example, sharing |fit::Callback| between both a service and a timeout
// might look something like this:
//
//  void service_with_timeout(fit::Callback<void(bool)> cb, uint timeout_ms) {
//    service_request([cb = cb.share()]() mutable { if (cb) cb(false); });
//    timeout(timeout_ms, [cb = std::move(cb)]() mutable { if (cb) cb(true); });
//  }
//
// Since |fit::Callback| objects are move-only, and not copyable, duplicate
// references to the same |fit::Callback| can be obtained via share(), as shown
// in the example above. This method converts the |fit::Callback| into a
// reference-counted version of the |fit::Callback| and returns a copy of the
// reference as another |fit::Callback| with the same target function.
//
// What is notable about |fit::Callback<T>.share()| is that invoking any shared
// copy will "nullify" all shared copies, as shown in the example.
//
// Note that |fit::Callback| is NOT thread-safe by default. If multi-threaded
// support is required, you would need to implement your own mutex, or similar
// guard, before checking and calling a |fit::Callback|.
//
// Targets of up to |inlineTargetSize| bytes in size (rounded up for memory
// alignment) are stored inline within the callback object without incurring
// any heap allocation.  Larger callable objects will be moved to the heap as
// required.
//
// See also |fit::inline_callback<T, size>| for more control over allocation
// behavior.
//
// SYNOPSIS
//
// |T| is the callback's signature.  e.g. void(int, std::string).
//
// |inlineTargetSize| is the minimum size of target that is guaranteed to
// fit within a callback without requiring heap allocation.
// Defaults to |kDefaultInlineTargetSize|.
//
// Class members are documented in |fit::CallbackImpl|, below.
//
template <typename T, size_t inlineTargetSize = kDefaultInlineTargetSize>
using Callback = CallbackImpl<inlineTargetSize, /*requireInline=*/false, T>;

// A move-only, run-once, callable object wrapper that forces callables to be
// stored inline and never performs heap allocation.
//
// Behaves just like |fit::Callback<T, inlineTargetSize>| except that
// attempting to store a target larger than |inlineTargetSize| will fail to
// compile.
template <typename T, size_t inlineTargetSize = kDefaultInlineTargetSize>
using InlineCallback = CallbackImpl<inlineTargetSize,
                                    /*requireInline=*/true,
                                    T>;

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
class FunctionImpl<inlineTargetSize, requireInline, Result(Args...)> final
    : private gfxstream::guest::fit::internal::
          function_base<inlineTargetSize, requireInline, Result(Args...)> {
    using Base = gfxstream::guest::fit::internal::
        function_base<inlineTargetSize, requireInline, Result(Args...)>;

    // function_base requires private access during share()
    friend class gfxstream::guest::fit::internal::
        function_base<inlineTargetSize, requireInline, Result(Args...)>;

    // supports target() for shared functions
    friend const void* gfxstream::guest::fit::internal::get_target_type_id<>(
        const FunctionImpl<inlineTargetSize, requireInline, Result(Args...)>&);

    template <typename U>
    using NotSelfType = gfxstream::guest::fit::internal::NotSameType<FunctionImpl, U>;

    template <typename... Conditions>
    using RequiresConditions = gfxstream::guest::fit::internal::RequiresConditions<Conditions...>;

    template <typename... Conditions>
    using AssignmentRequiresConditions =
        gfxstream::guest::fit::internal::AssignmentRequiresConditions<FunctionImpl&, Conditions...>;

public:
    // The function's result type.
    using typename Base::result_type;

    // Initializes an empty (null) function. Attempting to call an empty
    // function will abort the program.
    FunctionImpl() = default;

    // Creates a function with an empty target (same outcome as the default
    // constructor).
    FunctionImpl(decltype(nullptr)) : Base(nullptr) {}

    // Creates a function bound to the specified function pointer.
    // If target == nullptr, assigns an empty target.
    FunctionImpl(Result (*target)(Args...)) : Base(target) {}

    // Creates a function bound to the specified callable object.
    // If target == nullptr, assigns an empty target.
    //
    // For functors, we need to capture the raw type but also restrict on the
    // existence of an appropriate operator () to resolve overloads and implicit
    // casts properly.
    //
    // Note that specializations of this template method that take fit::Callback
    // objects as the target Callable are deleted (see below).
    template <typename Callable,
              RequiresConditions<
                  std::is_convertible<decltype(std::declval<Callable&>()(std::declval<Args>()...)),
                                      result_type>,
                  NotSelfType<Callable>> = true>
    FunctionImpl(Callable&& target) : Base(std::forward<Callable>(target)) {}

    // Deletes the specializations of FunctionImpl(Callable) that would allow
    // a |fit::Function| to be constructed from a |fit::Callback|. This prevents
    // unexpected behavior of a |fit::Function| that would otherwise fail after
    // one call. To explicitly allow this, simply wrap the |fit::Callback| in a
    // pass-through lambda before passing it to the |fit::Function|.
    template <size_t otherInlineTargetSize, bool otherRequireInline>
    FunctionImpl(gfxstream::guest::fit::CallbackImpl<otherInlineTargetSize,
                                                    otherRequireInline,
                                                    Result(Args...)>) = delete;

    // Creates a function with a target moved from another function,
    // leaving the other function with an empty target.
    FunctionImpl(FunctionImpl&& other) : Base(static_cast<Base&&>(other)) {}

    // Destroys the function, releasing its target.
    ~FunctionImpl() = default;

    // Assigns the function to an empty target. Attempting to invoke the
    // function will abort the program.
    FunctionImpl& operator=(decltype(nullptr)) {
        Base::assign(nullptr);
        return *this;
    }

    // Assigns the function to the specified callable object. If target ==
    // nullptr, assigns an empty target.
    //
    // For functors, we need to capture the raw type but also restrict on the
    // existence of an appropriate operator () to resolve overloads and implicit
    // casts properly.
    //
    // Note that specializations of this template method that take fit::Callback
    // objects as the target Callable are deleted (see below).
    template <typename Callable>
    AssignmentRequiresConditions<
        std::is_convertible<decltype(std::declval<Callable&>()(std::declval<Args>()...)),
                            result_type>,
        NotSelfType<Callable>>
    operator=(Callable&& target) {
        Base::assign(std::forward<Callable>(target));
        return *this;
    }

    // Deletes the specializations of operator=(Callable) that would allow
    // a |fit::Function| to be assigned from a |fit::Callback|. This
    // prevents unexpected behavior of a |fit::Function| that would otherwise
    // fail after one call. To explicitly allow this, simply wrap the
    // |fit::Callback| in a pass-through lambda before assigning it to the
    // |fit::Function|.
    template <size_t otherInlineTargetSize, bool otherRequireInline>
    FunctionImpl& operator=(gfxstream::guest::fit::CallbackImpl<otherInlineTargetSize,
                                                               otherRequireInline,
                                                               Result(Args...)>) = delete;

    // Move assignment
    FunctionImpl& operator=(FunctionImpl&& other) {
        if (&other == this)
            return *this;
        Base::assign(static_cast<Base&&>(other));
        return *this;
    }

    // Swaps the functions' targets.
    void swap(FunctionImpl& other) { Base::swap(other); }

    // Returns a pointer to the function's target.
    using Base::target;

    // Returns true if the function has a non-empty target.
    using Base::operator bool;

    // Invokes the function's target.
    // Aborts if the function's target is empty.
    Result operator()(Args... args) const { return Base::invoke(std::forward<Args>(args)...); }

    // Returns a new function object that invokes the same target.
    // The target itself is not copied; it is moved to the heap and its
    // lifetime is extended until all references have been released.
    //
    // Note: This method is not supported on |fit::InlineFunction<>|
    //       because it may incur a heap allocation which is contrary to
    //       the stated purpose of |fit::InlineFunction<>|.
    FunctionImpl share() {
        FunctionImpl copy;
        Base::template share_with<FunctionImpl>(copy);
        return copy;
    }
};

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
void swap(FunctionImpl<inlineTargetSize, requireInline, Result, Args...>& a,
          FunctionImpl<inlineTargetSize, requireInline, Result, Args...>& b) {
    a.swap(b);
}

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator==(const FunctionImpl<inlineTargetSize, requireInline, Result, Args...>& f,
                decltype(nullptr)) {
    return !f;
}
template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator==(decltype(nullptr),
                const FunctionImpl<inlineTargetSize, requireInline, Result, Args...>& f) {
    return !f;
}
template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator!=(const FunctionImpl<inlineTargetSize, requireInline, Result, Args...>& f,
                decltype(nullptr)) {
    return !!f;
}
template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator!=(decltype(nullptr),
                const FunctionImpl<inlineTargetSize, requireInline, Result, Args...>& f) {
    return !!f;
}

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
class CallbackImpl<inlineTargetSize, requireInline, Result(Args...)> final
    : private gfxstream::guest::fit::internal::
          function_base<inlineTargetSize, requireInline, Result(Args...)> {
    using Base = gfxstream::guest::fit::internal::
        function_base<inlineTargetSize, requireInline, Result(Args...)>;

    // function_base requires private access during share()
    friend class gfxstream::guest::fit::internal::
        function_base<inlineTargetSize, requireInline, Result(Args...)>;

    // supports target() for shared functions
    friend const void* gfxstream::guest::fit::internal::get_target_type_id<>(
        const CallbackImpl<inlineTargetSize, requireInline, Result(Args...)>&);

    template <typename U>
    using NotSelfType = gfxstream::guest::fit::internal::NotSameType<CallbackImpl, U>;

    template <typename... Conditions>
    using RequiresConditions = gfxstream::guest::fit::internal::RequiresConditions<Conditions...>;

    template <typename... Conditions>
    using AssignmentRequiresConditions =
        gfxstream::guest::fit::internal::AssignmentRequiresConditions<CallbackImpl&, Conditions...>;

public:
    // The callback function's result type.
    using typename Base::result_type;

    // Initializes an empty (null) callback. Attempting to call an empty
    // callback will abort the program.
    CallbackImpl() = default;

    // Creates a callback with an empty target (same outcome as the default
    // constructor).
    CallbackImpl(decltype(nullptr)) : Base(nullptr) {}

    // Creates a callback bound to the specified function pointer.
    // If target == nullptr, assigns an empty target.
    CallbackImpl(Result (*target)(Args...)) : Base(target) {}

    // Creates a callback bound to the specified callable object.
    // If target == nullptr, assigns an empty target.
    //
    // For functors, we need to capture the raw type but also restrict on the
    // existence of an appropriate operator () to resolve overloads and implicit
    // casts properly.
    template <typename Callable,
              RequiresConditions<
                  std::is_convertible<decltype(std::declval<Callable&>()(std::declval<Args>()...)),
                                      result_type>,
                  NotSelfType<Callable>> = true>
    CallbackImpl(Callable&& target) : Base(std::forward<Callable>(target)) {}

    // Creates a callback with a target moved from another callback,
    // leaving the other callback with an empty target.
    CallbackImpl(CallbackImpl&& other) : Base(static_cast<Base&&>(other)) {}

    // Destroys the callback, releasing its target.
    ~CallbackImpl() = default;

    // Assigns the callback to an empty target. Attempting to invoke the
    // callback will abort the program.
    CallbackImpl& operator=(decltype(nullptr)) {
        Base::assign(nullptr);
        return *this;
    }

    // Assigns the callback to the specified callable object. If target ==
    // nullptr, assigns an empty target.
    //
    // For functors, we need to capture the raw type but also restrict on the
    // existence of an appropriate operator () to resolve overloads and implicit
    // casts properly.
    template <typename Callable>
    AssignmentRequiresConditions<
        std::is_convertible<decltype(std::declval<Callable&>()(std::declval<Args>()...)),
                            result_type>,
        NotSelfType<Callable>>
    operator=(Callable&& target) {
        Base::assign(std::forward<Callable>(target));
        return *this;
    }

    // Move assignment
    CallbackImpl& operator=(CallbackImpl&& other) {
        if (&other == this)
            return *this;
        Base::assign(static_cast<Base&&>(other));
        return *this;
    }

    // Swaps the callbacks' targets.
    void swap(CallbackImpl& other) { Base::swap(other); }

    // Returns a pointer to the callback's target.
    using Base::target;

    // Returns true if the callback has a non-empty target.
    using Base::operator bool;

    // Invokes the callback's target.
    // Aborts if the callback's target is empty.
    // |fit::Callback| must be non-const to invoke. Before the target function
    // is actually called, the fit::Callback will be set to the default empty
    // state (== nullptr, and operator bool() will subsequently return |false|).
    // The target function will then be released after the function is called.
    // If the callback was shared, any remaining copies will also be cleared.
    Result operator()(Args... args) {
        auto temp = std::move(*this);
        return temp.invoke(std::forward<Args>(args)...);
    }

    // Returns a new callback object that invokes the same target.
    // The target itself is not copied; it is moved to the heap and its
    // lifetime is extended until all references have been released.
    // For |fit::Callback| (unlike fit::Function), the first invocation of the
    // callback will release all references to the target. All callbacks
    // derived from the same original callback (via share()) will be cleared,
    // as if set to |nullptr|, and "operator bool()" will return false.
    //
    // Note: This method is not supported on |fit::InlineFunction<>|
    //       because it may incur a heap allocation which is contrary to
    //       the stated purpose of |fit::InlineFunction<>|.
    CallbackImpl share() {
        CallbackImpl copy;
        Base::template share_with<CallbackImpl>(copy);
        return copy;
    }
};

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
void swap(CallbackImpl<inlineTargetSize, requireInline, Result, Args...>& a,
          CallbackImpl<inlineTargetSize, requireInline, Result, Args...>& b) {
    a.swap(b);
}

template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator==(const CallbackImpl<inlineTargetSize, requireInline, Result, Args...>& f,
                decltype(nullptr)) {
    return !f;
}
template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator==(decltype(nullptr),
                const CallbackImpl<inlineTargetSize, requireInline, Result, Args...>& f) {
    return !f;
}
template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator!=(const CallbackImpl<inlineTargetSize, requireInline, Result, Args...>& f,
                decltype(nullptr)) {
    return !!f;
}
template <size_t inlineTargetSize, bool requireInline, typename Result, typename... Args>
bool operator!=(decltype(nullptr),
                const CallbackImpl<inlineTargetSize, requireInline, Result, Args...>& f) {
    return !!f;
}

// Returns a Callable object that invokes a member function of an object.
template <typename R, typename T, typename... Args>
auto bindMember(T* instance, R (T::*fn)(Args...)) {
    return [instance, fn](Args... args) { return (instance->*fn)(std::forward<Args>(args)...); };
}

}  // namespace fit
}  // namespace gfxstream::guest
