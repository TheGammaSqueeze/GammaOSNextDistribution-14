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
 *   Functions generated:6
 *
 *  mockcify.pl ver 0.3.0
 */

#include <functional>

#include "include/hardware/bluetooth.h"

// Original included files, if any
// #include "osi/include/wakelock.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_wakelock {

// Shared state between mocked functions and tests
// Name: wakelock_acquire
// Params: void
// Return: bool
struct wakelock_acquire {
  bool return_value{false};
  std::function<bool(void)> body{[this](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct wakelock_acquire wakelock_acquire;

// Name: wakelock_cleanup
// Params: void
// Return: void
struct wakelock_cleanup {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct wakelock_cleanup wakelock_cleanup;

// Name: wakelock_debug_dump
// Params: int fd
// Return: void
struct wakelock_debug_dump {
  std::function<void(int fd)> body{[](int /* fd */) {}};
  void operator()(int fd) { body(fd); };
};
extern struct wakelock_debug_dump wakelock_debug_dump;

// Name: wakelock_release
// Params: void
// Return: bool
struct wakelock_release {
  bool return_value{false};
  std::function<bool(void)> body{[this](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct wakelock_release wakelock_release;

// Name: wakelock_set_os_callouts
// Params: bt_os_callouts_t* callouts
// Return: void
struct wakelock_set_os_callouts {
  std::function<void(bt_os_callouts_t* callouts)> body{
      [](bt_os_callouts_t* /* callouts */) {}};
  void operator()(bt_os_callouts_t* callouts) { body(callouts); };
};
extern struct wakelock_set_os_callouts wakelock_set_os_callouts;

// Name: wakelock_set_paths
// Params: const char* lock_path, const char* unlock_path
// Return: void
struct wakelock_set_paths {
  std::function<void(const char* lock_path, const char* unlock_path)> body{
      [](const char* /* lock_path */, const char* /* unlock_path */) {}};
  void operator()(const char* lock_path, const char* unlock_path) {
    body(lock_path, unlock_path);
  };
};
extern struct wakelock_set_paths wakelock_set_paths;

}  // namespace osi_wakelock
}  // namespace mock
}  // namespace test

// END mockcify generation
