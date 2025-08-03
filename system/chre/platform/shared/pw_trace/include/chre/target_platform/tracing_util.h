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

#ifndef CHRE_PLATFORM_SHARED_TRACING_UTIL_H_
#define CHRE_PLATFORM_SHARED_TRACING_UTIL_H_

#include "chre/util/macros.h"

namespace tracing_internal {

// Check to make sure pointer size macro is defined.
#ifndef __SIZEOF_POINTER__
#error "__SIZEOF_POINTER__ macro not defined - unsupported toolchain being used"
#endif

constexpr size_t kMaxTraceDataSize = 256;

template <typename T>
inline constexpr std::size_t chreTraceGetSizeOf() {
  if constexpr (std::is_pointer_v<T>) {
    return __SIZEOF_POINTER__;
  }

  return sizeof(T);
}

/**
 * Special case for strings.
 *
 * Due to how python struct unpacking works, reading strings requires the data
 * format string to specify the length of buffer containing the string.
 * PW_TRACE macros require the data format string to be a string literal, and we
 * don't always know the str length at compile-time and thus opt to put all
 * strings in a fixed size buffer of length CHRE_TRACE_MAX_STRING_SIZE. Using
 * the pascal string option indicates the buffer's first byte contains the size
 * of string, followed by the string characters.
 */
template <>
inline constexpr std::size_t chreTraceGetSizeOf<const char *>() {
  return CHRE_TRACE_STR_BUFFER_SIZE;
}
template <>
inline constexpr std::size_t chreTraceGetSizeOf<char *>() {
  return chreTraceGetSizeOf<const char *>();
}

template <typename... Types>
constexpr std::size_t chreTraceGetSizeOfVarArgs() {
  return (chreTraceGetSizeOf<std::decay_t<Types>>() + ...);
}

template <typename Data>
inline void chreTraceInsertVar(uint8_t *buffer, Data data,
                               std::size_t dataSize) {
  static_assert(std::is_integral_v<Data> || std::is_pointer_v<Data>,
                "Unsupported data type");
  memcpy(buffer, &data, dataSize);
}
template <>
inline void chreTraceInsertVar<const char *>(uint8_t *buffer, const char *data,
                                             std::size_t) {
  // Insert size byte metadata as the first byte of the pascal string.
  *buffer = static_cast<uint8_t>(strnlen(data, CHRE_TRACE_MAX_STRING_SIZE));

  // Insert string after size byte and zero out remainder of buffer.
  strncpy(reinterpret_cast<char *>(buffer + 1), data,
          CHRE_TRACE_MAX_STRING_SIZE);
}
template <>
inline void chreTraceInsertVar<char *>(uint8_t *buffer, char *data,
                                       std::size_t dataSize) {
  chreTraceInsertVar<const char *>(buffer, data, dataSize);
}

/**
 * Populate the pre-allocated buffer with data passed in.
 * Should only be called in the CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER
 * macro.
 *
 * Example Usage:
 *   uint_16_t data1; char data2; uint32_t data3;
 *   ... // define data
 *   chreTracePopulateBufferWithArgs(buf, data1, data2, data3);
 *
 * @param buffer A buffer to insert data into.
 *               Assumed to be large enough to hold all data since we use the
 *               same size logic to allocate space for the buffer.
 * @param data   Single piece of data to insert into the buffer. Assumed to
 *               have >= 1 data element to insert into the buffer. Strings
 *               assumed to be null-terminated or have length >=
 *               CHRE_TRACE_MAX_STRING_SIZE.
 * @param args   Variable length argument to hold the remainder of the data
 *               to insert into the buffer.
 */
template <typename Data, typename... Types>
void chreTracePopulateBufferWithArgs(uint8_t *buffer, Data data,
                                     Types... args) {
  constexpr std::size_t dataSize = chreTraceGetSizeOf<Data>();
  tracing_internal::chreTraceInsertVar(buffer, data, dataSize);
  buffer += dataSize;

  if constexpr (sizeof...(args) > 0) {
    chreTracePopulateBufferWithArgs(buffer, args...);
  }
}

// Create and populate the chreTraceDataBuffer. We allocate only the amount of
// space required to store all data.
// Unscoped to export the variables holding the buffer and data size.
#define CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(...)                    \
  constexpr std::size_t chreTraceDataSize =                                  \
      tracing_internal::chreTraceGetSizeOfVarArgs<TYPE_LIST(__VA_ARGS__)>(); \
  static_assert(chreTraceDataSize <= tracing_internal::kMaxTraceDataSize,    \
                "Trace data size too large");                                \
  uint8_t chreTraceDataBuffer[chreTraceDataSize];                            \
  tracing_internal::chreTracePopulateBufferWithArgs(chreTraceDataBuffer,     \
                                                    __VA_ARGS__);

}  // namespace tracing_internal

#endif  // CHRE_PLATFORM_SHARED_TRACING_UTIL_H_