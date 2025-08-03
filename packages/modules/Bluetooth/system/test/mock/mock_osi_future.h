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
 *   Functions generated:4
 *
 *  mockcify.pl ver 0.3.0
 */

#include <functional>

// Original included files, if any
#include <base/logging.h>

#include "include/check.h"
#include "osi/include/future.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_future {

// Shared state between mocked functions and tests
// Name: future_await
// Params: future_t* future
// Return: void*
struct future_await {
  void* return_value{};
  std::function<void*(future_t* future)> body{
      [this](future_t* /* future */) { return return_value; }};
  void* operator()(future_t* future) { return body(future); };
};
extern struct future_await future_await;

// Name: future_new
// Params: void
// Return: future_t*
struct future_new {
  future_t* return_value{0};
  std::function<future_t*(void)> body{[this](void) { return return_value; }};
  future_t* operator()(void) { return body(); };
};
extern struct future_new future_new;

// Name: future_new_named
// Params: const char* name
// Return: future_t*
struct future_new_named {
  future_t* return_value{0};
  std::function<future_t*(const char* name)> body{
      [this](const char* /* name */) { return return_value; }};
  future_t* operator()(const char* name) { return body(name); };
};
extern struct future_new_named future_new_named;

// Name: future_new_immediate
// Params: void* value
// Return: future_t*
struct future_new_immediate {
  future_t* return_value{0};
  std::function<future_t*(void* value)> body{[this](void* /* value */) {
    CHECK(0);
    return return_value;
  }};
  future_t* operator()(void* value) { return body(value); };
};
extern struct future_new_immediate future_new_immediate;

// Name: future_ready
// Params: future_t* future, void* value
// Return: void
struct future_ready {
  std::function<void(future_t* future, void* value)> body{
      [](future_t* /* future */, void* /* value */) {}};
  void operator()(future_t* future, void* value) { body(future, value); };
};
extern struct future_ready future_ready;

}  // namespace osi_future
}  // namespace mock
}  // namespace test

// END mockcify generation
