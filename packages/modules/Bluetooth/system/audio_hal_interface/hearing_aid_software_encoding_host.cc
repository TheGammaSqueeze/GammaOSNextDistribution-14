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

#include "audio_hal_interface/hearing_aid_software_encoding.h"

namespace bluetooth {
namespace audio {
namespace hearing_aid {

bool is_hal_enabled() { return false; }

bool init(StreamCallbacks stream_cb,
          bluetooth::common::MessageLoopThread* message_loop) {
  return false;
}

void cleanup() {}

void start_session() {}

void end_session() {}

size_t read(uint8_t* p_buf, uint32_t len) { return 0; }

void set_remote_delay(uint16_t delay_report_ms) {}

}  // namespace hearing_aid
}  // namespace audio
}  // namespace bluetooth
