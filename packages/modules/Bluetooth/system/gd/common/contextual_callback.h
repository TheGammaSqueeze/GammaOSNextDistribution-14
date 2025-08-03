/*
 * Copyright 2019 The Android Open Source Project
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

#pragma once

#include "bind.h"
#include "callback.h"
#include "i_postable_context.h"

namespace bluetooth {
namespace common {

template <typename R, typename... Args>
class ContextualOnceCallback;

// A callback bound to an execution context that can be invoked only once.
template <typename R, typename... Args>
class ContextualOnceCallback<R(Args...)> {
 public:
  ContextualOnceCallback(common::OnceCallback<R(Args...)>&& callback, IPostableContext* context)
      : callback_(std::move(callback)), context_(context) {}

  constexpr ContextualOnceCallback() = default;
  ContextualOnceCallback(const ContextualOnceCallback&) = delete;
  ContextualOnceCallback& operator=(const ContextualOnceCallback&) = delete;

  ContextualOnceCallback(ContextualOnceCallback&&) noexcept = default;
  ContextualOnceCallback& operator=(ContextualOnceCallback&&) noexcept = default;

  void Invoke(Args... args) {
    context_->Post(common::BindOnce(std::move(callback_), std::forward<Args>(args)...));
  }

  void InvokeIfNotEmpty(Args... args) {
    if (context_ != nullptr) {
      context_->Post(common::BindOnce(std::move(callback_), std::forward<Args>(args)...));
    }
  }

  bool IsEmpty() {
    return context_ == nullptr;
  }

 private:
  common::OnceCallback<R(Args...)> callback_;
  IPostableContext* context_;
};

template <typename Callback>
ContextualOnceCallback(Callback&& callback, IPostableContext* context)
    -> ContextualOnceCallback<typename Callback::RunType>;

template <typename R, typename... Args>
class ContextualCallback;

// A callback bound to an execution context that can be invoked multiple times.
template <typename R, typename... Args>
class ContextualCallback<R(Args...)> {
 public:
  ContextualCallback(common::Callback<R(Args...)>&& callback, IPostableContext* context)
      : callback_(std::move(callback)), context_(context) {}

  constexpr ContextualCallback() = default;

  ContextualCallback(const ContextualCallback&) = default;
  ContextualCallback& operator=(const ContextualCallback&) = default;
  ContextualCallback(ContextualCallback&&) noexcept = default;
  ContextualCallback& operator=(ContextualCallback&&) noexcept = default;

  void Invoke(Args... args) {
    context_->Post(common::BindOnce(callback_, std::forward<Args>(args)...));
  }

  void InvokeIfNotEmpty(Args... args) {
    if (context_ != nullptr) {
      context_->Post(common::BindOnce(callback_, std::forward<Args>(args)...));
    }
  }

  bool IsEmpty() {
    return context_ == nullptr;
  }

 private:
  common::Callback<R(Args...)> callback_;
  IPostableContext* context_;
};

template <typename Callback>
ContextualCallback(Callback&& callback, IPostableContext* context)
    -> ContextualCallback<typename Callback::RunType>;

}  // namespace common
}  // namespace bluetooth
