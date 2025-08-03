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
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_ringbuffer.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_ringbuffer {

// Function state capture and return values, if needed
struct ringbuffer_available ringbuffer_available;
struct ringbuffer_delete ringbuffer_delete;
struct ringbuffer_free ringbuffer_free;
struct ringbuffer_init ringbuffer_init;
struct ringbuffer_insert ringbuffer_insert;
struct ringbuffer_peek ringbuffer_peek;
struct ringbuffer_pop ringbuffer_pop;
struct ringbuffer_size ringbuffer_size;

}  // namespace osi_ringbuffer
}  // namespace mock
}  // namespace test

// Mocked functions, if any
size_t ringbuffer_available(const ringbuffer_t* rb) {
  inc_func_call_count(__func__);
  return test::mock::osi_ringbuffer::ringbuffer_available(rb);
}
size_t ringbuffer_delete(ringbuffer_t* rb, size_t length) {
  inc_func_call_count(__func__);
  return test::mock::osi_ringbuffer::ringbuffer_delete(rb, length);
}
void ringbuffer_free(ringbuffer_t* rb) {
  inc_func_call_count(__func__);
  test::mock::osi_ringbuffer::ringbuffer_free(rb);
}
ringbuffer_t* ringbuffer_init(const size_t size) {
  inc_func_call_count(__func__);
  return test::mock::osi_ringbuffer::ringbuffer_init(size);
}
size_t ringbuffer_insert(ringbuffer_t* rb, const uint8_t* p, size_t length) {
  inc_func_call_count(__func__);
  return test::mock::osi_ringbuffer::ringbuffer_insert(rb, p, length);
}
size_t ringbuffer_peek(const ringbuffer_t* rb, off_t offset, uint8_t* p,
                       size_t length) {
  inc_func_call_count(__func__);
  return test::mock::osi_ringbuffer::ringbuffer_peek(rb, offset, p, length);
}
size_t ringbuffer_pop(ringbuffer_t* rb, uint8_t* p, size_t length) {
  inc_func_call_count(__func__);
  return test::mock::osi_ringbuffer::ringbuffer_pop(rb, p, length);
}
size_t ringbuffer_size(const ringbuffer_t* rb) {
  inc_func_call_count(__func__);
  return test::mock::osi_ringbuffer::ringbuffer_size(rb);
}
// Mocked functions complete
// END mockcify generation
