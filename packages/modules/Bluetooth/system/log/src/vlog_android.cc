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

#include <log/log.h>

#include "bluetooth/log.h"
#include "truncating_buffer.h"

namespace bluetooth::log_internal {

static constexpr size_t kBufferSize = 1024;

void vlog(Level level, char const* tag, char const* file_name, int line,
          char const* function_name, fmt::string_view fmt,
          fmt::format_args vargs) {
  // Check if log is enabled.
  if (!__android_log_is_loggable(level, tag, ANDROID_LOG_INFO) &&
      !__android_log_is_loggable(level, "bluetooth", ANDROID_LOG_INFO)) {
    return;
  }

  // Format to stack buffer.
  truncating_buffer<kBufferSize> buffer;
  fmt::format_to(std::back_insert_iterator(buffer), "{}: ", function_name);
  fmt::vformat_to(std::back_insert_iterator(buffer), fmt, vargs);

  // Send message to liblog.
  struct __android_log_message message = {
      .struct_size = sizeof(__android_log_message),
      .buffer_id = LOG_ID_MAIN,
      .priority = static_cast<android_LogPriority>(level),
      .tag = tag,
      .file = file_name,
      .line = static_cast<uint32_t>(line),
      .message = buffer.c_str(),
  };
  __android_log_write_log_message(&message);

  if (level == Level::kFatal) {
    // Log assertion failures to stderr for the benefit of "adb shell" users
    // and gtests (http://b/23675822).
    char const* buf = buffer.c_str();
    TEMP_FAILURE_RETRY(write(2, buf, strlen(buf)));
    TEMP_FAILURE_RETRY(write(2, "\n", 1));
    __android_log_call_aborter(buf);
  }
}

}  // namespace bluetooth::log_internal
