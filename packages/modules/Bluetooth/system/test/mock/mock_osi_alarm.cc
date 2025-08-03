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

/*
 * Generated mock file from original source file
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_alarm.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_alarm {

// Function state capture and return values, if needed
struct alarm_cancel alarm_cancel;
struct alarm_cleanup alarm_cleanup;
struct alarm_debug_dump alarm_debug_dump;
struct alarm_free alarm_free;
struct alarm_get_remaining_ms alarm_get_remaining_ms;
struct alarm_is_scheduled alarm_is_scheduled;
struct alarm_new alarm_new;
struct alarm_new_periodic alarm_new_periodic;
struct alarm_set alarm_set;
struct alarm_set_on_mloop alarm_set_on_mloop;

}  // namespace osi_alarm
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void alarm_cancel(alarm_t* alarm) {
  inc_func_call_count(__func__);
  test::mock::osi_alarm::alarm_cancel(alarm);
}
void alarm_cleanup(void) {
  inc_func_call_count(__func__);
  test::mock::osi_alarm::alarm_cleanup();
}
void alarm_debug_dump(int fd) {
  inc_func_call_count(__func__);
  test::mock::osi_alarm::alarm_debug_dump(fd);
}
void alarm_free(alarm_t* alarm) {
  inc_func_call_count(__func__);
  test::mock::osi_alarm::alarm_free(alarm);
}
uint64_t alarm_get_remaining_ms(const alarm_t* alarm) {
  inc_func_call_count(__func__);
  return test::mock::osi_alarm::alarm_get_remaining_ms(alarm);
}
bool alarm_is_scheduled(const alarm_t* alarm) {
  inc_func_call_count(__func__);
  return test::mock::osi_alarm::alarm_is_scheduled(alarm);
}
alarm_t* alarm_new(const char* name) {
  inc_func_call_count(__func__);
  return test::mock::osi_alarm::alarm_new(name);
}
alarm_t* alarm_new_periodic(const char* name) {
  inc_func_call_count(__func__);
  return test::mock::osi_alarm::alarm_new_periodic(name);
}
void alarm_set(alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
               void* data) {
  inc_func_call_count(__func__);
  test::mock::osi_alarm::alarm_set(alarm, interval_ms, cb, data);
}
void alarm_set_on_mloop(alarm_t* alarm, uint64_t interval_ms,
                        alarm_callback_t cb, void* data) {
  inc_func_call_count(__func__);
  test::mock::osi_alarm::alarm_set_on_mloop(alarm, interval_ms, cb, data);
}
// Mocked functions complete
// END mockcify generation
