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
 *   Functions generated:1
 *
 *  mockcify.pl ver 0.3.0
 */

#include <functional>

// Original included files, if any
#include <private/android_filesystem_config.h>
#include <unistd.h>

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace common_os_utils {

// Shared state between mocked functions and tests
// Name: is_bluetooth_uid
// Params:
// Return: bool
struct is_bluetooth_uid {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct is_bluetooth_uid is_bluetooth_uid;

}  // namespace common_os_utils
}  // namespace mock
}  // namespace test

// END mockcify generation