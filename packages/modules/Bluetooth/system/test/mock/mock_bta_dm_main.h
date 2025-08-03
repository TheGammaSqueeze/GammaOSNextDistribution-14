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

/*
 * Generated mock file from original source file
 *   Functions generated:3
 *
 *  mockcify.pl ver 0.6.2
 */

#include <functional>

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.
#include <base/strings/stringprintf.h>

#include "stack/include/bt_hdr.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace bta_dm_main {

// Shared state between mocked functions and tests
// Name: DumpsysBtaDm
// Params: int fd
// Return: void
struct DumpsysBtaDm {
  std::function<void(int fd)> body{[](int fd) {}};
  void operator()(int fd) { body(fd); };
};
extern struct DumpsysBtaDm DumpsysBtaDm;

// Name: bta_dm_search_sm_disable
// Params:
// Return: void
struct bta_dm_search_sm_disable {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct bta_dm_search_sm_disable bta_dm_search_sm_disable;

// Name: bta_dm_search_sm_execute
// Params: const BT_HDR_RIGID* p_msg
// Return: bool
struct bta_dm_search_sm_execute {
  static bool return_value;
  std::function<bool(const BT_HDR_RIGID* p_msg)> body{
      [](const BT_HDR_RIGID* p_msg) { return return_value; }};
  bool operator()(const BT_HDR_RIGID* p_msg) { return body(p_msg); };
};
extern struct bta_dm_search_sm_execute bta_dm_search_sm_execute;

}  // namespace bta_dm_main
}  // namespace mock
}  // namespace test

// END mockcify generation
