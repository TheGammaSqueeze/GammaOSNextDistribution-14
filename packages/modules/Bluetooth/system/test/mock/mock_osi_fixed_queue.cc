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
 *   Functions generated:18
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_fixed_queue.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_fixed_queue {

// Function state capture and return values, if needed
struct fixed_queue_capacity fixed_queue_capacity;
struct fixed_queue_dequeue fixed_queue_dequeue;
struct fixed_queue_enqueue fixed_queue_enqueue;
struct fixed_queue_flush fixed_queue_flush;
struct fixed_queue_free fixed_queue_free;
struct fixed_queue_get_dequeue_fd fixed_queue_get_dequeue_fd;
struct fixed_queue_get_enqueue_fd fixed_queue_get_enqueue_fd;
struct fixed_queue_get_list fixed_queue_get_list;
struct fixed_queue_is_empty fixed_queue_is_empty;
struct fixed_queue_length fixed_queue_length;
struct fixed_queue_new fixed_queue_new;
struct fixed_queue_register_dequeue fixed_queue_register_dequeue;
struct fixed_queue_try_dequeue fixed_queue_try_dequeue;
struct fixed_queue_try_enqueue fixed_queue_try_enqueue;
struct fixed_queue_try_peek_first fixed_queue_try_peek_first;
struct fixed_queue_try_peek_last fixed_queue_try_peek_last;
struct fixed_queue_try_remove_from_queue fixed_queue_try_remove_from_queue;
struct fixed_queue_unregister_dequeue fixed_queue_unregister_dequeue;

}  // namespace osi_fixed_queue
}  // namespace mock
}  // namespace test

// Mocked functions, if any
size_t fixed_queue_capacity(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_capacity(queue);
}
void* fixed_queue_dequeue(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_dequeue(queue);
}
void fixed_queue_enqueue(fixed_queue_t* queue, void* data) {
  inc_func_call_count(__func__);
  test::mock::osi_fixed_queue::fixed_queue_enqueue(queue, data);
}
void fixed_queue_flush(fixed_queue_t* queue, fixed_queue_free_cb free_cb) {
  inc_func_call_count(__func__);
  test::mock::osi_fixed_queue::fixed_queue_flush(queue, free_cb);
}
void fixed_queue_free(fixed_queue_t* queue, fixed_queue_free_cb free_cb) {
  inc_func_call_count(__func__);
  test::mock::osi_fixed_queue::fixed_queue_free(queue, free_cb);
}
int fixed_queue_get_dequeue_fd(const fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_get_dequeue_fd(queue);
}
int fixed_queue_get_enqueue_fd(const fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_get_enqueue_fd(queue);
}
list_t* fixed_queue_get_list(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_get_list(queue);
}
bool fixed_queue_is_empty(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_is_empty(queue);
}
size_t fixed_queue_length(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_length(queue);
}
fixed_queue_t* fixed_queue_new(size_t capacity) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_new(capacity);
}
void fixed_queue_register_dequeue(fixed_queue_t* queue, reactor_t* reactor,
                                  fixed_queue_cb ready_cb, void* context) {
  inc_func_call_count(__func__);
  test::mock::osi_fixed_queue::fixed_queue_register_dequeue(queue, reactor,
                                                            ready_cb, context);
}
void* fixed_queue_try_dequeue(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_try_dequeue(queue);
}
bool fixed_queue_try_enqueue(fixed_queue_t* queue, void* data) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_try_enqueue(queue, data);
}
void* fixed_queue_try_peek_first(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_try_peek_first(queue);
}
void* fixed_queue_try_peek_last(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_try_peek_last(queue);
}
void* fixed_queue_try_remove_from_queue(fixed_queue_t* queue, void* data) {
  inc_func_call_count(__func__);
  return test::mock::osi_fixed_queue::fixed_queue_try_remove_from_queue(queue,
                                                                        data);
}
void fixed_queue_unregister_dequeue(fixed_queue_t* queue) {
  inc_func_call_count(__func__);
  test::mock::osi_fixed_queue::fixed_queue_unregister_dequeue(queue);
}
// Mocked functions complete
// END mockcify generation
