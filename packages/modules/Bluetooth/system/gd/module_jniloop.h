/*
 * Copyright 2023 The Android Open Source Project
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

#include <base/callback.h>
#include <base/functional/bind.h>
#include <base/location.h>

#include "btif/include/btif_jni_task.h"

namespace bluetooth {

class ModuleJniloop {
 protected:
  ModuleJniloop() noexcept = default;
  virtual ~ModuleJniloop() = default;
  ModuleJniloop(const ModuleJniloop& mod) = delete;

  // Threadsafe post onto jni loop a function with copyable arguments
  template <typename Functor, typename... Args>
  void PostFunctionOnJni(Functor&& functor, Args&&... args) const {
    do_in_jni_thread(
        FROM_HERE, base::BindOnce(std::forward<Functor>(functor), std::forward<Args>(args)...));
  }

  // Threadsafe post onto jni loop a method and context with copyable arguments
  template <typename T, typename Functor, typename... Args>
  void PostMethodOnJni(std::shared_ptr<T> ref, Functor&& functor, Args... args) const {
    do_in_jni_thread(
        FROM_HERE,
        base::BindOnce(
            [](std::weak_ptr<T> ref, Functor&& functor, Args&&... args) {
              if (std::shared_ptr<T> spt = ref.lock()) {
                base::BindOnce(std::forward<Functor>(functor), spt, std::forward<Args>(args)...)
                    .Run();
              }
            },
            std::weak_ptr<T>(ref),
            std::forward<Functor>(functor),
            std::forward<Args>(args)...));
  }
};

}  // namespace bluetooth
