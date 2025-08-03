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
#include "bta/sys/bta_sys.h"
#include "osi/include/alarm.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace bta_sys_main {

// Shared state between mocked functions and tests
// Name: BTA_sys_signal_hw_error
// Params:
// Return: void
struct BTA_sys_signal_hw_error {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTA_sys_signal_hw_error BTA_sys_signal_hw_error;

// Name: bta_sys_deregister
// Params: uint8_t id
// Return: void
struct bta_sys_deregister {
  std::function<void(uint8_t id)> body{[](uint8_t /* id */) {}};
  void operator()(uint8_t id) { body(id); };
};
extern struct bta_sys_deregister bta_sys_deregister;

// Name: bta_sys_disable
// Params:
// Return: void
struct bta_sys_disable {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct bta_sys_disable bta_sys_disable;

// Name: bta_sys_init
// Params: void
// Return: void
struct bta_sys_init {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct bta_sys_init bta_sys_init;

// Name: bta_sys_is_register
// Params: uint8_t id
// Return: bool
struct bta_sys_is_register {
  bool return_value{false};
  std::function<bool(uint8_t id)> body{
      [this](uint8_t /* id */) { return return_value; }};
  bool operator()(uint8_t id) { return body(id); };
};
extern struct bta_sys_is_register bta_sys_is_register;

// Name: bta_sys_register
// Params: uint8_t id, const tBTA_SYS_REG* p_reg
// Return: void
struct bta_sys_register {
  std::function<void(uint8_t id, const tBTA_SYS_REG* p_reg)> body{
      [](uint8_t /* id */, const tBTA_SYS_REG* /* p_reg */) {}};
  void operator()(uint8_t id, const tBTA_SYS_REG* p_reg) { body(id, p_reg); };
};
extern struct bta_sys_register bta_sys_register;

// Name: bta_sys_sendmsg
// Params: void* p_msg
// Return: void
struct bta_sys_sendmsg {
  std::function<void(void* p_msg)> body{[](void* /* p_msg */) {}};
  void operator()(void* p_msg) { body(p_msg); };
};
extern struct bta_sys_sendmsg bta_sys_sendmsg;

// Name: bta_sys_sendmsg_delayed
// Params: void* p_msg, std::chrono::microseconds delay
// Return: void
struct bta_sys_sendmsg_delayed {
  std::function<void(void* p_msg, std::chrono::microseconds delay)> body{
      [](void* /* p_msg */, std::chrono::microseconds /* delay */) {}};
  void operator()(void* p_msg, std::chrono::microseconds delay) {
    body(p_msg, delay);
  };
};
extern struct bta_sys_sendmsg_delayed bta_sys_sendmsg_delayed;

// Name: bta_sys_start_timer
// Params: alarm_t* alarm, uint64_t interval_ms, uint16_t event, uint16_t
// layer_specific Return: void
struct bta_sys_start_timer {
  std::function<void(alarm_t* alarm, uint64_t interval_ms, uint16_t event,
                     uint16_t layer_specific)>
      body{[](alarm_t* /* alarm */, uint64_t /* interval_ms */,
              uint16_t /* event */, uint16_t /* layer_specific */) {}};
  void operator()(alarm_t* alarm, uint64_t interval_ms, uint16_t event,
                  uint16_t layer_specific) {
    body(alarm, interval_ms, event, layer_specific);
  };
};
extern struct bta_sys_start_timer bta_sys_start_timer;

}  // namespace bta_sys_main
}  // namespace mock
}  // namespace test

// END mockcify generation
