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
 *   Functions generated:8
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include <base/logging.h>
#include <stdlib.h>

#include "osi/include/ringbuffer.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_ringbuffer {

// Shared state between mocked functions and tests
// Name: ringbuffer_available
// Params: const ringbuffer_t* rb
// Return: size_t
struct ringbuffer_available {
  size_t return_value{0};
  std::function<size_t(const ringbuffer_t* rb)> body{
      [this](const ringbuffer_t* /* rb */) { return return_value; }};
  size_t operator()(const ringbuffer_t* rb) { return body(rb); };
};
extern struct ringbuffer_available ringbuffer_available;

// Name: ringbuffer_delete
// Params: ringbuffer_t* rb, size_t length
// Return: size_t
struct ringbuffer_delete {
  size_t return_value{0};
  std::function<size_t(ringbuffer_t* rb, size_t length)> body{
      [this](ringbuffer_t* /* rb */, size_t /* length */) {
        return return_value;
      }};
  size_t operator()(ringbuffer_t* rb, size_t length) {
    return body(rb, length);
  };
};
extern struct ringbuffer_delete ringbuffer_delete;

// Name: ringbuffer_free
// Params: ringbuffer_t* rb
// Return: void
struct ringbuffer_free {
  std::function<void(ringbuffer_t* rb)> body{[](ringbuffer_t* /* rb */) {}};
  void operator()(ringbuffer_t* rb) { body(rb); };
};
extern struct ringbuffer_free ringbuffer_free;

// Name: ringbuffer_init
// Params: const size_t size
// Return: ringbuffer_t*
struct ringbuffer_init {
  ringbuffer_t* return_value{0};
  std::function<ringbuffer_t*(const size_t size)> body{
      [this](const size_t /* size */) { return return_value; }};
  ringbuffer_t* operator()(const size_t size) { return body(size); };
};
extern struct ringbuffer_init ringbuffer_init;

// Name: ringbuffer_insert
// Params: ringbuffer_t* rb, const uint8_t* p, size_t length
// Return: size_t
struct ringbuffer_insert {
  size_t return_value{0};
  std::function<size_t(ringbuffer_t* rb, const uint8_t* p, size_t length)> body{
      [this](ringbuffer_t* /* rb */, const uint8_t* /* p */,
             size_t /* length */) { return return_value; }};
  size_t operator()(ringbuffer_t* rb, const uint8_t* p, size_t length) {
    return body(rb, p, length);
  };
};
extern struct ringbuffer_insert ringbuffer_insert;

// Name: ringbuffer_peek
// Params: const ringbuffer_t* rb, off_t offset, uint8_t* p, size_t length
// Return: size_t
struct ringbuffer_peek {
  size_t return_value{0};
  std::function<size_t(const ringbuffer_t* rb, off_t offset, uint8_t* p,
                       size_t length)>
      body{[this](const ringbuffer_t* /* rb */, off_t /* offset */,
                  uint8_t* /* p */,
                  size_t /* length */) { return return_value; }};
  size_t operator()(const ringbuffer_t* rb, off_t offset, uint8_t* p,
                    size_t length) {
    return body(rb, offset, p, length);
  };
};
extern struct ringbuffer_peek ringbuffer_peek;

// Name: ringbuffer_pop
// Params: ringbuffer_t* rb, uint8_t* p, size_t length
// Return: size_t
struct ringbuffer_pop {
  size_t return_value{0};
  std::function<size_t(ringbuffer_t* rb, uint8_t* p, size_t length)> body{
      [this](ringbuffer_t* /* rb */, uint8_t* /* p */, size_t /* length */) {
        return return_value;
      }};
  size_t operator()(ringbuffer_t* rb, uint8_t* p, size_t length) {
    return body(rb, p, length);
  };
};
extern struct ringbuffer_pop ringbuffer_pop;

// Name: ringbuffer_size
// Params: const ringbuffer_t* rb
// Return: size_t
struct ringbuffer_size {
  size_t return_value{0};
  std::function<size_t(const ringbuffer_t* rb)> body{
      [this](const ringbuffer_t* /* rb */) { return return_value; }};
  size_t operator()(const ringbuffer_t* rb) { return body(rb); };
};
extern struct ringbuffer_size ringbuffer_size;

}  // namespace osi_ringbuffer
}  // namespace mock
}  // namespace test

// END mockcify generation
