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

#include <cstdint>
#include <functional>

// Original included files, if any
#include <hardware/bluetooth.h>

#include "osi/include/alarm.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_alarm {

// Shared state between mocked functions and tests
// Name: alarm_cancel
// Params: alarm_t* alarm
// Return: void
struct alarm_cancel {
  std::function<void(alarm_t* alarm)> body{[](alarm_t* /* alarm */) {}};
  void operator()(alarm_t* alarm) { body(alarm); };
};
extern struct alarm_cancel alarm_cancel;

// Name: alarm_cleanup
// Params: void
// Return: void
struct alarm_cleanup {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct alarm_cleanup alarm_cleanup;

// Name: alarm_debug_dump
// Params: int fd
// Return: void
struct alarm_debug_dump {
  std::function<void(int fd)> body{[](int /* fd */) {}};
  void operator()(int fd) { body(fd); };
};
extern struct alarm_debug_dump alarm_debug_dump;

// Name: alarm_free
// Params: alarm_t* alarm
// Return: void
struct alarm_free {
  std::function<void(alarm_t* alarm)> body{[](alarm_t* /* alarm */) {}};
  void operator()(alarm_t* alarm) { body(alarm); };
};
extern struct alarm_free alarm_free;

// Name: alarm_get_remaining_ms
// Params: const alarm_t* alarm
// Return: uint64_t
struct alarm_get_remaining_ms {
  uint64_t return_value{0};
  std::function<uint64_t(const alarm_t* alarm)> body{
      [this](const alarm_t* /* alarm */) { return return_value; }};
  uint64_t operator()(const alarm_t* alarm) { return body(alarm); };
};
extern struct alarm_get_remaining_ms alarm_get_remaining_ms;

// Name: alarm_is_scheduled
// Params: const alarm_t* alarm
// Return: bool
struct alarm_is_scheduled {
  bool return_value{false};
  std::function<bool(const alarm_t* alarm)> body{
      [this](const alarm_t* /* alarm */) { return return_value; }};
  bool operator()(const alarm_t* alarm) { return body(alarm); };
};
extern struct alarm_is_scheduled alarm_is_scheduled;

// Name: alarm_new
// Params: const char* name
// Return: alarm_t*
struct alarm_new {
  alarm_t* return_value{0};
  std::function<alarm_t*(const char* name)> body{
      [this](const char* /* name */) { return return_value; }};
  alarm_t* operator()(const char* name) { return body(name); };
};
extern struct alarm_new alarm_new;

// Name: alarm_new_periodic
// Params: const char* name
// Return: alarm_t*
struct alarm_new_periodic {
  alarm_t* return_value{0};
  std::function<alarm_t*(const char* name)> body{
      [this](const char* /* name */) { return return_value; }};
  alarm_t* operator()(const char* name) { return body(name); };
};
extern struct alarm_new_periodic alarm_new_periodic;

// Name: alarm_set
// Params: alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb, void* data
// Return: void
struct alarm_set {
  std::function<void(alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
                     void* data)>
      body{[](alarm_t* /* alarm */, uint64_t /* interval_ms */,
              alarm_callback_t /* cb */, void* /* data */) {}};
  void operator()(alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
                  void* data) {
    body(alarm, interval_ms, cb, data);
  };
};
extern struct alarm_set alarm_set;

// Name: alarm_set_on_mloop
// Params: alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb, void* data
// Return: void
struct alarm_set_on_mloop {
  std::function<void(alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
                     void* data)>
      body{[](alarm_t* /* alarm */, uint64_t /* interval_ms */,
              alarm_callback_t /* cb */, void* /* data */) {}};
  void operator()(alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
                  void* data) {
    body(alarm, interval_ms, cb, data);
  };
};
extern struct alarm_set_on_mloop alarm_set_on_mloop;

}  // namespace osi_alarm
}  // namespace mock
}  // namespace test

// END mockcify generation
