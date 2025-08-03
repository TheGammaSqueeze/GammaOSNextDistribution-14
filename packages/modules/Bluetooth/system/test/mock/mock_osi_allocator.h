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

// Original included files, if any
#include <base/logging.h>
#include <stdlib.h>
#include <string.h>

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_allocator {

// Shared state between mocked functions and tests
// Name: osi_calloc
// Params: size_t size
// Return: void*
struct osi_calloc {
  void* return_value{};
  std::function<void*(size_t size)> body{
      [this](size_t /* size */) { return return_value; }};
  void* operator()(size_t size) { return body(size); };
};
extern struct osi_calloc osi_calloc;

// Name: osi_free
// Params: void* ptr
// Return: void
struct osi_free {
  std::function<void(void* ptr)> body{[](void* /* ptr */) {}};
  void operator()(void* ptr) { body(ptr); };
};
extern struct osi_free osi_free;

// Name: osi_free_and_reset
// Params: void** p_ptr
// Return: void
struct osi_free_and_reset {
  std::function<void(void** p_ptr)> body{[](void** /* p_ptr */) {}};
  void operator()(void** p_ptr) { body(p_ptr); };
};
extern struct osi_free_and_reset osi_free_and_reset;

// Name: osi_malloc
// Params: size_t size
// Return: void*
struct osi_malloc {
  void* return_value{};
  std::function<void*(size_t size)> body{
      [this](size_t /* size */) { return return_value; }};
  void* operator()(size_t size) { return body(size); };
};
extern struct osi_malloc osi_malloc;

// Name: osi_strdup
// Params: const char* str
// Return: char*
struct osi_strdup {
  char* return_value{0};
  std::function<char*(const char* str)> body{
      [this](const char* /* str */) { return return_value; }};
  char* operator()(const char* str) { return body(str); };
};
extern struct osi_strdup osi_strdup;

// Name: osi_strndup
// Params: const char* str, size_t len
// Return: char*
struct osi_strndup {
  char* return_value{0};
  std::function<char*(const char* str, size_t len)> body{
      [this](const char* /* str */, size_t /* len */) { return return_value; }};
  char* operator()(const char* str, size_t len) { return body(str, len); };
};
extern struct osi_strndup osi_strndup;

}  // namespace osi_allocator
}  // namespace mock
}  // namespace test

// END mockcify generation
