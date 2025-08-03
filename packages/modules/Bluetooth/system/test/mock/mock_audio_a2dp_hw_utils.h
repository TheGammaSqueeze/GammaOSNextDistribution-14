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
 *   Functions generated:2
 *
 *  mockcify.pl ver 0.6.1
 */

#include <functional>

// Original included files, if any
#include "audio_a2dp_hw/include/audio_a2dp_hw.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace audio_a2dp_hw_utils {

// Shared state between mocked functions and tests
// Name: audio_a2dp_hw_dump_ctrl_event
// Params: tA2DP_CTRL_CMD event
// Return: const char*
struct audio_a2dp_hw_dump_ctrl_event {
  static const char* return_value;
  std::function<const char*(tA2DP_CTRL_CMD event)> body{
      [](tA2DP_CTRL_CMD /* event */) { return return_value; }};
  const char* operator()(tA2DP_CTRL_CMD event) { return body(event); };
};
extern struct audio_a2dp_hw_dump_ctrl_event audio_a2dp_hw_dump_ctrl_event;

// Name: delay_reporting_enabled
// Params:
// Return: bool
struct delay_reporting_enabled {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct delay_reporting_enabled delay_reporting_enabled;

}  // namespace audio_a2dp_hw_utils
}  // namespace mock
}  // namespace test

// END mockcify generation
