/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:2
 *
 *  mockcify.pl ver 0.3.0
 */

#include <functional>

// Original included files, if any

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_mutex {

// Shared state between mocked functions and tests
// Name: mutex_global_lock
// Params: void
// Return: void
struct mutex_global_lock {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct mutex_global_lock mutex_global_lock;

// Name: mutex_global_unlock
// Params: void
// Return: void
struct mutex_global_unlock {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct mutex_global_unlock mutex_global_unlock;

}  // namespace osi_mutex
}  // namespace mock
}  // namespace test

// END mockcify generation