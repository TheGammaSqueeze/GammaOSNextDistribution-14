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
 *   Functions generated:11
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_thread.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_thread {

// Function state capture and return values, if needed
struct thread_free thread_free;
struct thread_get_reactor thread_get_reactor;
struct thread_is_self thread_is_self;
struct thread_join thread_join;
struct thread_name thread_name;
struct thread_new thread_new;
struct thread_new_sized thread_new_sized;
struct thread_post thread_post;
struct thread_set_priority thread_set_priority;
struct thread_set_rt_priority thread_set_rt_priority;
struct thread_stop thread_stop;

}  // namespace osi_thread
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void thread_free(thread_t* thread) {
  inc_func_call_count(__func__);
  test::mock::osi_thread::thread_free(thread);
}
reactor_t* thread_get_reactor(const thread_t* thread) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_get_reactor(thread);
}
bool thread_is_self(const thread_t* thread) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_is_self(thread);
}
void thread_join(thread_t* thread) {
  inc_func_call_count(__func__);
  test::mock::osi_thread::thread_join(thread);
}
const char* thread_name(const thread_t* thread) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_name(thread);
}
thread_t* thread_new(const char* name) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_new(name);
}
thread_t* thread_new_sized(const char* name, size_t work_queue_capacity) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_new_sized(name, work_queue_capacity);
}
bool thread_post(thread_t* thread, thread_fn func, void* context) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_post(thread, func, context);
}
bool thread_set_priority(thread_t* thread, int priority) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_set_priority(thread, priority);
}
bool thread_set_rt_priority(thread_t* thread, int priority) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread::thread_set_rt_priority(thread, priority);
}
void thread_stop(thread_t* thread) {
  inc_func_call_count(__func__);
  test::mock::osi_thread::thread_stop(thread);
}
// Mocked functions complete
// END mockcify generation
