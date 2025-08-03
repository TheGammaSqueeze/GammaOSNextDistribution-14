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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:5
 *
 *  mockcify.pl ver 0.6.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include "btif/include/btif_profile_queue.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_profile_queue {

// Shared state between mocked functions and tests
// Name: btif_queue_advance
// Params:
// Return: void
struct btif_queue_advance {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct btif_queue_advance btif_queue_advance;

// Name: btif_queue_cleanup
// Params: uint16_t uuid
// Return: void
struct btif_queue_cleanup {
  std::function<void(uint16_t uuid)> body{[](uint16_t /* uuid */) {}};
  void operator()(uint16_t uuid) { body(uuid); };
};
extern struct btif_queue_cleanup btif_queue_cleanup;

// Name: btif_queue_connect
// Params: uint16_t uuid, const RawAddress* bda, btif_connect_cb_t connect_cb
// Return: bt_status_t
struct btif_queue_connect {
  static bt_status_t return_value;
  std::function<bt_status_t(uint16_t uuid, const RawAddress* bda,
                            btif_connect_cb_t connect_cb)>
      body{[](uint16_t /* uuid */, const RawAddress* /* bda */,
              btif_connect_cb_t /* connect_cb */) { return return_value; }};
  bt_status_t operator()(uint16_t uuid, const RawAddress* bda,
                         btif_connect_cb_t connect_cb) {
    return body(uuid, bda, connect_cb);
  };
};
extern struct btif_queue_connect btif_queue_connect;

// Name: btif_queue_connect_next
// Params: void
// Return: bt_status_t
struct btif_queue_connect_next {
  static bt_status_t return_value;
  std::function<bt_status_t(void)> body{[](void) { return return_value; }};
  bt_status_t operator()(void) { return body(); };
};
extern struct btif_queue_connect_next btif_queue_connect_next;

// Name: btif_queue_release
// Params:
// Return: void
struct btif_queue_release {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct btif_queue_release btif_queue_release;

}  // namespace btif_profile_queue
}  // namespace mock
}  // namespace test

// END mockcify generation
