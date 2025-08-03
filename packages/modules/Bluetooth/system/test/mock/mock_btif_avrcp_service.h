/*
 * Copyright 2022 The Android Open Source Project
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
 *   Functions generated:1
 *
 *  mockcify.pl ver 0.5.0
 */

#include <functional>

// Original included files, if any
#include <base/functional/bind.h>

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_avrcp_service {

// Shared state between mocked functions and tests
// Name: do_in_avrcp_jni
// Params: const base::Closure& task
// Return: void
struct do_in_avrcp_jni {
  std::function<void(const base::Closure& task)> body{
      [](const base::Closure& /* task */) {}};
  void operator()(const base::Closure& task) { body(task); };
};
extern struct do_in_avrcp_jni do_in_avrcp_jni;

}  // namespace btif_avrcp_service
}  // namespace mock
}  // namespace test

// END mockcify generation
