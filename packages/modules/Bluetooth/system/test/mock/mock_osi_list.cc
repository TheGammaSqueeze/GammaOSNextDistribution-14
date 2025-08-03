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
 *   Functions generated:19
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_list.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_list {

// Function state capture and return values, if needed
struct list_append list_append;
struct list_back list_back;
struct list_back_node list_back_node;
struct list_begin list_begin;
struct list_clear list_clear;
struct list_contains list_contains;
struct list_end list_end;
struct list_foreach list_foreach;
struct list_free list_free;
struct list_front list_front;
struct list_insert_after list_insert_after;
struct list_is_empty list_is_empty;
struct list_length list_length;
struct list_new list_new;
struct list_new_internal list_new_internal;
struct list_next list_next;
struct list_node list_node;
struct list_prepend list_prepend;
struct list_remove list_remove;

}  // namespace osi_list
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool list_append(list_t* list, void* data) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_append(list, data);
}
void* list_back(const list_t* list) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_back(list);
}
list_node_t* list_back_node(const list_t* list) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_back_node(list);
}
list_node_t* list_begin(const list_t* list) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_begin(list);
}
void list_clear(list_t* list) {
  inc_func_call_count(__func__);
  test::mock::osi_list::list_clear(list);
}
bool list_contains(const list_t* list, const void* data) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_contains(list, data);
}
list_node_t* list_end(const list_t* list) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_end(list);
}
list_node_t* list_foreach(const list_t* list, list_iter_cb callback,
                          void* context) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_foreach(list, callback, context);
}
void list_free(list_t* list) {
  inc_func_call_count(__func__);
  test::mock::osi_list::list_free(list);
}
void* list_front(const list_t* list) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_front(list);
}
bool list_insert_after(list_t* list, list_node_t* prev_node, void* data) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_insert_after(list, prev_node, data);
}
bool list_is_empty(const list_t* list) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_is_empty(list);
}
size_t list_length(const list_t* list) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_length(list);
}
list_t* list_new(list_free_cb callback) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_new(callback);
}
list_t* list_new_internal(list_free_cb callback,
                          const allocator_t* zeroed_allocator) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_new_internal(callback, zeroed_allocator);
}
list_node_t* list_next(const list_node_t* node) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_next(node);
}
void* list_node(const list_node_t* node) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_node(node);
}
bool list_prepend(list_t* list, void* data) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_prepend(list, data);
}
bool list_remove(list_t* list, void* data) {
  inc_func_call_count(__func__);
  return test::mock::osi_list::list_remove(list, data);
}
// Mocked functions complete
// END mockcify generation
