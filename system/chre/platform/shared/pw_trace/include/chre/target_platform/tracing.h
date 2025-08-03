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

#ifndef CHRE_PLATFORM_SHARED_TRACING_H_
#define CHRE_PLATFORM_SHARED_TRACING_H_

#include "chre/target_platform/tracing_util.h"
#include "pw_trace/trace.h"

// Format strings gotten from https://pigweed.dev/pw_trace/#data.
// Must be macros for string concatenation at preprocessor time.
#define PW_MAP_PREFIX "@pw_py_map_fmt:{"
#define PW_MAP_SUFFIX "}"

/**
 * Traces an instantaneous event.
 *
 * @param label A string literal which describes the trace.
 * @param group <optional> A string literal to group traces together.
 * @param trace_id <optional>  A uint32_t which groups this trace with others
 *                             with the same group and trace_id.
 *                             Every trace with a trace_id must also have a
 *                             group.
 * @see https://pigweed.dev/pw_trace/#trace-macros
 */
#define CHRE_TRACE_INSTANT(label, ...) PW_TRACE_INSTANT(label, ##__VA_ARGS__)

/**
 * Used to trace the start of a duration event. Should be paired with a
 * CHRE_TRACE_END (or CHRE_TRACE_END_DATA) with the same
 * module/label/group/trace_id.
 *
 * @param label A string literal which describes the trace.
 * @param group <optional> A string literal to group traces together.
 * @param trace_id <optional>  A uint32_t which groups this trace with others
 *                             with the same group and trace_id.
 *                             Every trace with a trace_id must also have a
 *                             group.
 * @see https://pigweed.dev/pw_trace/#trace-macros
 */
#define CHRE_TRACE_START(label, ...) PW_TRACE_START(label, ##__VA_ARGS__)

/**
 * Used to trace the end of a duration event. Should be paired with a
 * CHRE_TRACE_START (or CHRE_TRACE_START_DATA) with the same
 * module/label/group/trace_id.
 *
 * @param label A string literal which describes the trace.
 * @param group <optional> A string literal to group traces together.
 * @param trace_id <optional>  A uint32_t which groups this trace with others
 *                             with the same group and trace_id.
 *                             Every trace with a trace_id must also have a
 *                             group.
 * @see https://pigweed.dev/pw_trace/#trace-macros
 */
#define CHRE_TRACE_END(label, ...) PW_TRACE_END(label, ##__VA_ARGS__)

/**
 * For the group of CHRE_TRACE_INSTANT_DATA... macros.
 * Use the appropriate macro which contains the parameters you wish to pass to
 * the trace. If you wish to specify a group you must use either the
 * CHRE_TRACE_INSTANT_DATA_GROUP macro or CHRE_TRACE_INSTANT_DATA_TRACE_ID macro
 * with a trace_id.
 *
 * Traces an instantaneous event with data variables or literals passed to the
 * macro, correlating to the dataFmtString.
 *
 * @param label A string literal which describes the trace.
 * @param group A string literal to group traces together.
 *              Must use CHRE_TRACE_INSTANT_DATA_GROUP or
 *              CHRE_TRACE_INSTANT_DATA_TRACE_ID with a trace_id to use this
 *              parameter.
 * @param trace_id  A uint32_t which groups this trace with others with the same
 *                  group and trace_id.
 *                  Every trace with a trace_id must also have a group.
 *                  Must use CHRE_TRACE_INSTANT_DATA_TRACE_ID to use this
 *                  parameter.
 * @param dataFmtString A string literal which is used to relate data to its
 *                      size. Use the defined macros "T_X" for ease of use in
 *                      the format specifier. The format string must follow the
 *                      format "<field name>:<specifier>,..." (omitting the
 *                      final comma)
 *                      Ex. "data1:" T_U8 ",data2:" T_I32
 * @param firstData First data variable. Used to enforce proper usage of this
 *                  macro (with at least one data variable).
 * @param VA_ARGS List of variables holding data in the order specified by the
 *                dataFmtString.
 *
 * @see https://pigweed.dev/pw_trace/#trace-macros
 */
#define CHRE_TRACE_INSTANT_DATA(label, dataFmtString, firstData, ...)       \
  do {                                                                      \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__); \
    PW_TRACE_INSTANT_DATA(label, PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX, \
                          chreTraceDataBuffer, chreTraceDataSize);          \
  } while (0)

#define CHRE_TRACE_INSTANT_DATA_GROUP(label, group, dataFmtString, firstData, \
                                      ...)                                    \
  do {                                                                        \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__);   \
    PW_TRACE_INSTANT_DATA(label, group,                                       \
                          PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX,          \
                          chreTraceDataBuffer, chreTraceDataSize);            \
  } while (0)

#define CHRE_TRACE_INSTANT_DATA_TRACE_ID(label, group, trace_id,            \
                                         dataFmtString, firstData, ...)     \
  do {                                                                      \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__); \
    PW_TRACE_INSTANT_DATA(label, group, trace_id,                           \
                          PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX,        \
                          chreTraceDataBuffer, chreTraceDataSize);          \
  } while (0)

/**
 * For the group of CHRE_TRACE_START_DATA... macros.
 * Use the appropriate macro which contains the parameters you wish to pass to
 * the trace. If you wish to specify a group you must use either the
 * CHRE_TRACE_START_DATA_GROUP macro or CHRE_TRACE_START_DATA_TRACE_ID macro
 * with a trace_id.
 *
 * Used to trace the start of a duration event with data variables or literals
 * passed to the macro, correlating to the dataFmtString. This should be paired
 * with a CHRE_TRACE_END (or CHRE_TRACE_END_DATA) with the same
 * module/label/group/trace_id to measure the duration of this event.
 *
 * @param label A string literal which describes the trace.
 * @param group A string literal to group traces together.
 *              Must use CHRE_TRACE_START_DATA_GROUP or
 *              CHRE_TRACE_START_DATA_TRACE_ID with a trace_id to use this
 *              parameter.
 * @param trace_id  A uint32_t which groups this trace with others with the same
 *                  group and trace_id.
 *                  Every trace with a trace_id must also have a group.
 *                  Must use CHRE_TRACE_START_DATA_TRACE_ID to use this
 *                  parameter.
 * @param dataFmtString A string literal which is used to relate data to its
 *                      size. Use the defined macros "T_X" for ease of use in
 *                      the format specifier. The format string must follow the
 *                      format "<field name>:<specifier>,..." (omitting the
 *                      final comma)
 *                      Ex. "data1:" T_U8 ",data2:" T_I32
 * @param firstData First data variable. Used to enforce proper usage of this
 *                  macro (with at least one data variable).
 * @param VA_ARGS List of variables holding data in the order specified by the
 *                dataFmtString.
 *
 * @see https://pigweed.dev/pw_trace/#trace-macros
 */
#define CHRE_TRACE_START_DATA(label, dataFmtString, firstData, ...)         \
  do {                                                                      \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__); \
    PW_TRACE_START_DATA(label, PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX,   \
                        chreTraceDataBuffer, chreTraceDataSize);            \
  } while (0)

#define CHRE_TRACE_START_DATA_GROUP(label, group, dataFmtString, firstData, \
                                    ...)                                    \
  do {                                                                      \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__); \
    PW_TRACE_START_DATA(label, group,                                       \
                        PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX,          \
                        chreTraceDataBuffer, chreTraceDataSize);            \
  } while (0)

#define CHRE_TRACE_START_DATA_TRACE_ID(label, group, trace_id, dataFmtString, \
                                       firstData, ...)                        \
  do {                                                                        \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__);   \
    PW_TRACE_START_DATA(label, group, trace_id,                               \
                        PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX,            \
                        chreTraceDataBuffer, chreTraceDataSize);              \
  } while (0)

/**
 * For the group of CHRE_TRACE_END_DATA... macros.
 * Use the appropriate macro which contains the parameters you wish to pass to
 * the trace. If you wish to specify a group you must use either the
 * CHRE_TRACE_END_DATA_GROUP macro or CHRE_TRACE_END_DATA_TRACE_ID macro
 * with a trace_id.
 *
 * Used to trace the end of a duration event with data variables or literals
 * passed to the macro, correlating to the dataFmtString. This should be paired
 * with a CHRE_TRACE_START (or CHRE_TRACE_START_DATA) with the same
 * module/label/group/trace_id to measure the duration of this event.
 *
 * @param label A string literal which describes the trace.
 * @param group A string literal to group traces together.
 *              Must use CHRE_TRACE_END_DATA_GROUP or
 *              CHRE_TRACE_END_DATA_TRACE_ID with a trace_id to use this
 *              parameter.
 * @param trace_id  A uint32_t which groups this trace with others with the same
 *                  group and trace_id.
 *                  Every trace with a trace_id must also have a group.
 *                  Must use CHRE_TRACE_END_DATA_TRACE_ID to use this
 *                  parameter.
 * @param dataFmtString A string literal which is used to relate data to its
 *                      size. Use the defined macros "T_X" for ease of use in
 *                      the format specifier. The format string must follow the
 *                      format "<field name>:<specifier>,..." (omitting the
 *                      final comma)
 *                      Ex. "data1:" T_U8 ",data2:" T_I32
 * @param firstData First data variable. Used to enforce proper usage of this
 *                  macro (with at least one data variable).
 * @param VA_ARGS List of variables holding data in the order specified by the
 *                dataFmtString.
 *
 * @see https://pigweed.dev/pw_trace/#trace-macros
 */
#define CHRE_TRACE_END_DATA(label, dataFmtString, firstData, ...)           \
  do {                                                                      \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__); \
    PW_TRACE_END_DATA(label, PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX,     \
                      chreTraceDataBuffer, chreTraceDataSize);              \
  } while (0)

#define CHRE_TRACE_END_DATA_GROUP(label, group, dataFmtString, firstData, ...) \
  do {                                                                         \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__);    \
    PW_TRACE_END_DATA(label, group, PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX, \
                      chreTraceDataBuffer, chreTraceDataSize);                 \
  } while (0)

#define CHRE_TRACE_END_DATA_TRACE_ID(label, group, trace_id, dataFmtString, \
                                     firstData, ...)                        \
  do {                                                                      \
    CHRE_TRACE_ALLOCATE_AND_POPULATE_DATA_BUFFER(firstData, ##__VA_ARGS__); \
    PW_TRACE_END_DATA(label, group, trace_id,                               \
                      PW_MAP_PREFIX dataFmtString PW_MAP_SUFFIX,            \
                      chreTraceDataBuffer, chreTraceDataSize);              \
  } while (0)

#endif  // CHRE_PLATFORM_SHARED_TRACING_H_
