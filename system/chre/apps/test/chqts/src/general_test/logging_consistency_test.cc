/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <general_test/logging_consistency_test.h>

#include <cstddef>
#include <limits>

#include <shared/send_message.h>

#include <chre/util/nanoapp/log.h>

#include "chre/util/macros.h"
#include "chre/util/toolchain.h"
#include "chre_api/chre.h"

#define LOG_TAG "[LoggingConsistencyTest]"

using nanoapp_testing::sendFatalFailureToHost;
using nanoapp_testing::sendSuccessToHost;

namespace general_test {

LoggingConsistencyTest::LoggingConsistencyTest() : Test(CHRE_API_VERSION_1_0) {}

void LoggingConsistencyTest::setUp(uint32_t messageSize,
                                   const void * /* message */) {
  if (messageSize != 0) {
    sendFatalFailureToHost(
        "LoggingConsistency message expects 0 additional bytes, got ",
        &messageSize);
  }

  // Test each warning level.
  LOGE("Level: Error");
  LOGW("Level: Warn");
  LOGI("Level: Info");
  LOGD("Level: Debug");

  // Empty string
  char emptyString[1] = {'\0'};
  LOGI("%s", emptyString);

  // Try up through 10 arguments
  LOGI("%d", 1);
  LOGI("%d %d", 1, 2);
  LOGI("%d %d %d", 1, 2, 3);
  LOGI("%d %d %d %d", 1, 2, 3, 4);
  LOGI("%d %d %d %d %d", 1, 2, 3, 4, 5);
  LOGI("%d %d %d %d %d %d", 1, 2, 3, 4, 5, 6);
  LOGI("%d %d %d %d %d %d %d", 1, 2, 3, 4, 5, 6, 7);
  LOGI("%d %d %d %d %d %d %d %d", 1, 2, 3, 4, 5, 6, 7, 8);
  LOGI("%d %d %d %d %d %d %d %d %d", 1, 2, 3, 4, 5, 6, 7, 8, 9);
  LOGI("%d %d %d %d %d %d %d %d %d %d", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

  // Various 'int' specifiers.  The value of the "%u" output depends on the
  // size of 'int' on this machine.
  LOGI("%d %u 0%o 0x%x 0x%X", -1, -1, 01234, 0xF4E, 0xF4E);

  // Generic testing of all specific types.  The format string is the same
  // as the LOGI() above us, just using the appropriate prefix for each.
  // We also use the min() value for all these signed types, assuring that
  // we'll get different %d vs %u output, and we'll get letters within our
  // %x and %X output.
#define INT_TYPES(kPrefix, type)                                  \
  {                                                               \
    type value = std::numeric_limits<type>::min();                \
    LOGI("%" kPrefix "d %" kPrefix "u 0%" kPrefix "o 0x%" kPrefix \
         "x 0x%" kPrefix "X",                                     \
         value, value, value, value, value);                      \
  }

  INT_TYPES("hh", char);
  INT_TYPES("h", short);
  INT_TYPES("l", long);
  INT_TYPES("ll", long long);
  INT_TYPES("z", size_t);
  INT_TYPES("t", ptrdiff_t);

  // Disables logging-related double promotion warnings
  CHRE_LOG_PREAMBLE

  float f = 12.34f;
  // Other required formats, including escaping the '%'.
  LOGI("%% %f %c %s %p", f, '?', "str", &f);

  // OPTIONAL specifiers.  See LOGI() API documentation for extensive
  // discussion of what OPTIONAL means.
  // <width> and '-'
  LOGI("(%5s) (%-5s) (%5d) (%-5d)", "str", "str", 10, 10);
  // '+'
  LOGI("(%+d) (%+d) (%+f) (%+f)", -5, 5, -5.f, 5.f);
  // ' '
  LOGI("(% d) (% d) (% f) (% f)", -5, 5, -5.f, 5.f);
  // '#'
  LOGI("%#o %#x %#X %#f", 8, 15, 15, 1.f);
  // '0' padding
  LOGI("%08d 0x%04x", 123, 0xF);
  // '.'<precision>
  LOGI("%.3d %.3d %.3f %.3f %.3s", 12, 1234, 1.5, 1.0625, "abcdef");

  // Re-enable logging-related double warnings
  CHRE_LOG_EPILOGUE

  // TODO: In some future Android release, when LOGI() is required to
  //     output to logcat, we'll just send a Continue to the Host and have
  //     the Host verify this output.  But for Android N, we leave it to
  //     the test runner to manually verify.
  sendSuccessToHost();
}

void LoggingConsistencyTest::handleEvent(uint32_t senderInstanceId,
                                         uint16_t eventType,
                                         const void *eventData) {
  UNUSED_VAR(senderInstanceId);
  UNUSED_VAR(eventData);

  unexpectedEvent(eventType);
}

}  // namespace general_test
