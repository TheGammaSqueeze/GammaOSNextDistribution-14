/*
 * Copyright (C) 2023 The Android Open Source Project
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

// Ensuring this macro is not defined since we include the tracing macros and
// tracing utilities separately and do not want to test or include the pw_trace
// functions.
#ifdef CHRE_TRACING_ENABLED
#undef CHRE_TRACING_ENABLED
#endif  // CHRE_TRACING_ENABLED

#include <stdlib.h>
#include <cstdlib>
#include <cstring>
#include <string>

#include "chre/platform/tracing.h"
#include "chre/target_platform/tracing_util.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace tracing_internal {
namespace {

using ::testing::ElementsAre;
using ::testing::ElementsAreArray;

TEST(Trace, PopulateBufferWithTracedPtr) {
  const uint8_t var = 0x12;
  const uint8_t *data = &var;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, __SIZEOF_POINTER__);

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

// Already verified in chre/platform/tracing.h this value is either 8 or 4.
#if __SIZEOF_POINTER__ == 8
  EXPECT_EQ(*((uint64_t *)chreTraceDataBuffer), (uint64_t)data);
#elif __SIZEOF_POINTER__ == 4
  EXPECT_EQ(*((uint32_t *)chreTraceDataBuffer), (uint32_t)data);
#else
#error "Pointer size is of unsupported size"
#endif
}

TEST(Trace, PopulateBufferWithTracedBool) {
  const bool data = true;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(bool));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre(1));
}

TEST(Trace, PopulateBufferWithTracedUInt8) {
  const uint8_t data = 0x12;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(uint8_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre(0x12));
}

TEST(Trace, PopulateBufferWithTracedUInt16) {
  uint16_t data = 0x1234;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(uint16_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre(0x34, 0x12));
}

TEST(Trace, PopulateBufferWithTracedUInt32) {
  const uint32_t data = 0x12345678;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(uint32_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre(0x78, 0x56, 0x34, 0x12));
}

TEST(Trace, PopulateBufferWithTracedUInt64) {
  const uint64_t data = 0x1234567890123456;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(uint64_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer,
              ElementsAre(0x56, 0x34, 0x12, 0x90, 0x78, 0x56, 0x34, 0x12));
}

TEST(Trace, PopulateBufferWithTracedInt8) {
  const int8_t data = 0x12;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(int8_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre(0x12));
}

TEST(Trace, PopulateBufferWithTracedInt16) {
  const int16_t data = 0x1234;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(int16_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre(0x34, 0x12));
}

TEST(Trace, PopulateBufferWithTracedInt32) {
  const int32_t data = 0x12345678;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(int32_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre(0x78, 0x56, 0x34, 0x12));
}

TEST(Trace, PopulateBufferWithTracedInt64) {
  const int64_t data = 0x1234567890123456;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(int64_t));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer,
              ElementsAre(0x56, 0x34, 0x12, 0x90, 0x78, 0x56, 0x34, 0x12));
}

TEST(Trace, PopulateBufferWithTracedChar) {
  char data = 'a';

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, sizeof(char));

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAre('a'));
}

TEST(Trace, PopulateBufferWithTracedStrDoesNotOverflow) {
  const char data[] = "test";
  const size_t kBufferPadding = 5;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  uint8_t chreTraceDataBuffer[chreTraceDataSize + kBufferPadding];
  memset(chreTraceDataBuffer, 0xFF, chreTraceDataSize + kBufferPadding);
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  for (std::size_t i = 0; i < kBufferPadding; i++) {
    EXPECT_EQ(chreTraceDataBuffer[chreTraceDataSize + i], 0xFF);
  }
}

TEST(Trace, PopulateBufferWithTracedShortStrAndNullBytePadding) {
  // expected variable + length
  const char data[] = "test";
  uint8_t dataLen = static_cast<uint8_t>(strlen(data));

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(data)>();

  EXPECT_EQ(chreTraceDataSize, CHRE_TRACE_STR_BUFFER_SIZE);

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer, data);

  // Fully populated buffer with data len and null-byte padding at the end.
  uint8_t expectedBuffer[CHRE_TRACE_STR_BUFFER_SIZE] = {dataLen, 't', 'e', 's',
                                                        't'};
  for (std::size_t i = dataLen + 1; i < CHRE_TRACE_STR_BUFFER_SIZE; i++) {
    expectedBuffer[i] = '\0';
  }

  EXPECT_THAT(chreTraceDataBuffer, ElementsAreArray(expectedBuffer));
}

TEST(Trace, PopulateBufferWithTracedMaxLenStrNoPadding) {
  // String data buffer to hold max-len string.
  char dataBuffer[CHRE_TRACE_MAX_STRING_SIZE + 1];
  // Fully populated buffer with data len and no null-byte padding.
  // In this case data len should equal CHRE_TRACE_MAX_STRING_SIZE.
  uint8_t expectedBuffer[CHRE_TRACE_STR_BUFFER_SIZE] = {
      CHRE_TRACE_MAX_STRING_SIZE};

  // Populate the buffer with str "0123456789..." until we hit max size.
  for (std::size_t i = 0; i < CHRE_TRACE_MAX_STRING_SIZE; i++) {
    dataBuffer[i] = '0' + (i % 10);
    expectedBuffer[i + 1] = '0' + (i % 10);
  }
  dataBuffer[CHRE_TRACE_MAX_STRING_SIZE] = '\0';

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(dataBuffer)>();

  EXPECT_EQ(chreTraceDataSize, CHRE_TRACE_STR_BUFFER_SIZE);

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer,
                                                    dataBuffer);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAreArray(expectedBuffer));
}

TEST(Trace, PopulateBufferWithTracedStringTuncateToMaxLength) {
  const size_t kBufferPadding = 5;
  const std::size_t kOversizeStrLen =
      CHRE_TRACE_MAX_STRING_SIZE + kBufferPadding;
  // String data buffer to hold oversized string.
  char dataBuffer[kOversizeStrLen + 1];

  // Populate the buffer with str "0123456789..." until we hit kOversizeStrLen.
  for (std::size_t i = 0; i < kOversizeStrLen; i++) {
    dataBuffer[i] = '0' + (i % 10);
  }
  dataBuffer[kOversizeStrLen] = '\0';

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(dataBuffer)>();

  EXPECT_EQ(chreTraceDataSize, CHRE_TRACE_STR_BUFFER_SIZE);

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer,
                                                    dataBuffer);

  // Fully populated buffer with data len and truncated string.
  // In this case data len should equal CHRE_TRACE_MAX_STRING_SIZE, not
  // kOversizeStrLen.
  uint8_t expectedBuffer[CHRE_TRACE_STR_BUFFER_SIZE] = {
      CHRE_TRACE_MAX_STRING_SIZE};

  // Populate the buffer with str "0123456789..." until we hit
  // CHRE_TRACE_MAX_STRING_SIZE.
  for (std::size_t i = 0; i < CHRE_TRACE_MAX_STRING_SIZE; i++) {
    expectedBuffer[i + 1] = '0' + (i % 10);
  }

  EXPECT_THAT(chreTraceDataBuffer, ElementsAreArray(expectedBuffer));
}

TEST(Trace, PopulateBufferWithMultipleTracedData) {
  uint8_t dataUint8 = 0x12;
  char dataChar = 'a';
  uint32_t dataUint32 = 0x12345678;
  char dataStr[CHRE_TRACE_MAX_STRING_SIZE];
  strncpy(dataStr, "test", CHRE_TRACE_MAX_STRING_SIZE);
  uint8_t dataStrLen = static_cast<uint8_t>(strlen(dataStr));
  std::size_t totalDataSize = sizeof(uint8_t) + sizeof(char) +
                              sizeof(uint32_t) + CHRE_TRACE_STR_BUFFER_SIZE;

  constexpr std::size_t chreTraceDataSize =
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(
          dataUint8, dataChar, dataUint32, dataStr)>();

  EXPECT_EQ(chreTraceDataSize, totalDataSize);

  uint8_t expectedBuffer[chreTraceDataSize] = {0x12, 'a',  0x78,      0x56,
                                               0x34, 0x12, dataStrLen};
  strncpy((char *)(&expectedBuffer[7]), dataStr, CHRE_TRACE_MAX_STRING_SIZE);

  uint8_t chreTraceDataBuffer[chreTraceDataSize];
  tracing_internal::chreTracePopulateBufferWithArgs(
      chreTraceDataBuffer, dataUint8, dataChar, dataUint32, dataStr);

  EXPECT_THAT(chreTraceDataBuffer, ElementsAreArray(expectedBuffer));
}

// TODO(b/302232350): Add a death test for unsupported data types. Currently
//                    testing unsupported types (structs) with manual testing.

}  // namespace
}  // namespace tracing_internal