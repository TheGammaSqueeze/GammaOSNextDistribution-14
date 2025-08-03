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
 *   Functions generated:7
 *
 *  mockcify.pl ver 0.3.0
 */

#include <functional>

// Original included files, if any
#include "btcore/include/module.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btcore_module {

// Shared state between mocked functions and tests
// Name: get_module
// Params: const char* name
// Return: const module_t*
struct get_module {
  const module_t* return_value{0};
  std::function<const module_t*(const char* name)> body{
      [this](const char* name) { return return_value; }};
  const module_t* operator()(const char* name) { return body(name); };
};
extern struct get_module get_module;

// Name: module_clean_up
// Params: const module_t* module
// Return: void
struct module_clean_up {
  std::function<void(const module_t* module)> body{
      [](const module_t* module) {}};
  void operator()(const module_t* module) { body(module); };
};
extern struct module_clean_up module_clean_up;

// Name: module_init
// Params: const module_t* module
// Return: bool
struct module_init {
  bool return_value{false};
  std::function<bool(const module_t* module)> body{
      [this](const module_t* module) { return return_value; }};
  bool operator()(const module_t* module) { return body(module); };
};
extern struct module_init module_init;

// Name: module_management_start
// Params: void
// Return: void
struct module_management_start {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct module_management_start module_management_start;

// Name: module_management_stop
// Params: void
// Return: void
struct module_management_stop {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct module_management_stop module_management_stop;

// Name: module_shut_down
// Params: const module_t* module
// Return: void
struct module_shut_down {
  std::function<void(const module_t* module)> body{
      [](const module_t* module) {}};
  void operator()(const module_t* module) { body(module); };
};
extern struct module_shut_down module_shut_down;

// Name: module_start_up
// Params: const module_t* module
// Return: bool
struct module_start_up {
  bool return_value{false};
  std::function<bool(const module_t* module)> body{
      [this](const module_t* module) { return return_value; }};
  bool operator()(const module_t* module) { return body(module); };
};
extern struct module_start_up module_start_up;

}  // namespace btcore_module
}  // namespace mock
}  // namespace test

// END mockcify generation