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
/*
 * Generated mock file from original source file
 *   Functions generated:2
 *
 *  mockcify.pl ver 0.6.1
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_audio_a2dp_hw_utils.h"

#include "audio_a2dp_hw/include/audio_a2dp_hw.h"
#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace audio_a2dp_hw_utils {

// Function state capture and return values, if needed
struct audio_a2dp_hw_dump_ctrl_event audio_a2dp_hw_dump_ctrl_event;
struct delay_reporting_enabled delay_reporting_enabled;

}  // namespace audio_a2dp_hw_utils
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace audio_a2dp_hw_utils {

const char* audio_a2dp_hw_dump_ctrl_event::return_value = nullptr;
bool delay_reporting_enabled::return_value = false;

}  // namespace audio_a2dp_hw_utils
}  // namespace mock
}  // namespace test

// Mocked functions, if any
const char* audio_a2dp_hw_dump_ctrl_event(tA2DP_CTRL_CMD event) {
  inc_func_call_count(__func__);
  return test::mock::audio_a2dp_hw_utils::audio_a2dp_hw_dump_ctrl_event(event);
}
bool delay_reporting_enabled() {
  inc_func_call_count(__func__);
  return test::mock::audio_a2dp_hw_utils::delay_reporting_enabled();
}
// Mocked functions complete
// END mockcify generation
