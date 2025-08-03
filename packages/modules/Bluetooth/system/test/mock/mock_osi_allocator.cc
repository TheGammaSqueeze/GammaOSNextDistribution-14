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
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_allocator.h"

#include "osi/include/allocator.h"
#include "test/common/mock_functions.h"

// Mocked internal structures, if any

OsiObject::OsiObject(void* ptr) : ptr_(ptr) {}

OsiObject::OsiObject(const void* ptr) : ptr_(const_cast<void*>(ptr)) {}

OsiObject::~OsiObject() {
  if (ptr_ != nullptr) {
    osi_free(ptr_);
  }
}

void* OsiObject::Release() {
  void* ptr = ptr_;
  ptr_ = nullptr;
  return ptr;
}
namespace test {
namespace mock {
namespace osi_allocator {

// Function state capture and return values, if needed
struct osi_calloc osi_calloc;
struct osi_free osi_free;
struct osi_free_and_reset osi_free_and_reset;
struct osi_malloc osi_malloc;
struct osi_strdup osi_strdup;
struct osi_strndup osi_strndup;

}  // namespace osi_allocator
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void* osi_calloc(size_t size) {
  inc_func_call_count(__func__);
  return test::mock::osi_allocator::osi_calloc(size);
}
void osi_free(void* ptr) {
  inc_func_call_count(__func__);
  test::mock::osi_allocator::osi_free(ptr);
}
void osi_free_and_reset(void** p_ptr) {
  inc_func_call_count(__func__);
  test::mock::osi_allocator::osi_free_and_reset(p_ptr);
}
void* osi_malloc(size_t size) {
  inc_func_call_count(__func__);
  return test::mock::osi_allocator::osi_malloc(size);
}
char* osi_strdup(const char* str) {
  inc_func_call_count(__func__);
  return test::mock::osi_allocator::osi_strdup(str);
}
char* osi_strndup(const char* str, size_t len) {
  inc_func_call_count(__func__);
  return test::mock::osi_allocator::osi_strndup(str, len);
}
// Mocked functions complete
// END mockcify generation
