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

#include <functional>

// Original included files, if any
#include <base/logging.h>

#include "osi/include/allocator.h"
#include "osi/include/list.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_list {

// Shared state between mocked functions and tests
// Name: list_append
// Params: list_t* list, void* data
// Return: bool
struct list_append {
  bool return_value{false};
  std::function<bool(list_t* list, void* data)> body{
      [this](list_t* /* list */, void* /* data */) { return return_value; }};
  bool operator()(list_t* list, void* data) { return body(list, data); };
};
extern struct list_append list_append;

// Name: list_back
// Params: const list_t* list
// Return: void*
struct list_back {
  void* return_value{};
  std::function<void*(const list_t* list)> body{
      [this](const list_t* /* list */) { return return_value; }};
  void* operator()(const list_t* list) { return body(list); };
};
extern struct list_back list_back;

// Name: list_back_node
// Params: const list_t* list
// Return: list_node_t*
struct list_back_node {
  list_node_t* return_value{0};
  std::function<list_node_t*(const list_t* list)> body{
      [this](const list_t* /* list */) { return return_value; }};
  list_node_t* operator()(const list_t* list) { return body(list); };
};
extern struct list_back_node list_back_node;

// Name: list_begin
// Params: const list_t* list
// Return: list_node_t*
struct list_begin {
  list_node_t* return_value{0};
  std::function<list_node_t*(const list_t* list)> body{
      [this](const list_t* /* list */) { return return_value; }};
  list_node_t* operator()(const list_t* list) { return body(list); };
};
extern struct list_begin list_begin;

// Name: list_clear
// Params: list_t* list
// Return: void
struct list_clear {
  std::function<void(list_t* list)> body{[](list_t* /* list */) {}};
  void operator()(list_t* list) { body(list); };
};
extern struct list_clear list_clear;

// Name: list_contains
// Params: const list_t* list, const void* data
// Return: bool
struct list_contains {
  bool return_value{false};
  std::function<bool(const list_t* list, const void* data)> body{
      [this](const list_t* /* list */, const void* /* data */) {
        return return_value;
      }};
  bool operator()(const list_t* list, const void* data) {
    return body(list, data);
  };
};
extern struct list_contains list_contains;

// Name: list_end
// Params:  const list_t* list
// Return: list_node_t*
struct list_end {
  list_node_t* return_value{0};
  std::function<list_node_t*(const list_t* list)> body{
      [this](const list_t* /* list */) { return return_value; }};
  list_node_t* operator()(const list_t* list) { return body(list); };
};
extern struct list_end list_end;

// Name: list_foreach
// Params: const list_t* list, list_iter_cb callback, void* context
// Return: list_node_t*
struct list_foreach {
  list_node_t* return_value{0};
  std::function<list_node_t*(const list_t* list, list_iter_cb callback,
                             void* context)>
      body{[this](const list_t* /* list */, list_iter_cb /* callback */,
                  void* /* context */) { return return_value; }};
  list_node_t* operator()(const list_t* list, list_iter_cb callback,
                          void* context) {
    return body(list, callback, context);
  };
};
extern struct list_foreach list_foreach;

// Name: list_free
// Params: list_t* list
// Return: void
struct list_free {
  std::function<void(list_t* list)> body{[](list_t* /* list */) {}};
  void operator()(list_t* list) { body(list); };
};
extern struct list_free list_free;

// Name: list_front
// Params: const list_t* list
// Return: void*
struct list_front {
  void* return_value{};
  std::function<void*(const list_t* list)> body{
      [this](const list_t* /* list */) { return return_value; }};
  void* operator()(const list_t* list) { return body(list); };
};
extern struct list_front list_front;

// Name: list_insert_after
// Params: list_t* list, list_node_t* prev_node, void* data
// Return: bool
struct list_insert_after {
  bool return_value{false};
  std::function<bool(list_t* list, list_node_t* prev_node, void* data)> body{
      [this](list_t* /* list */, list_node_t* /* prev_node */,
             void* /* data */) { return return_value; }};
  bool operator()(list_t* list, list_node_t* prev_node, void* data) {
    return body(list, prev_node, data);
  };
};
extern struct list_insert_after list_insert_after;

// Name: list_is_empty
// Params: const list_t* list
// Return: bool
struct list_is_empty {
  bool return_value{false};
  std::function<bool(const list_t* list)> body{
      [this](const list_t* /* list */) { return return_value; }};
  bool operator()(const list_t* list) { return body(list); };
};
extern struct list_is_empty list_is_empty;

// Name: list_length
// Params: const list_t* list
// Return: size_t
struct list_length {
  size_t return_value{0};
  std::function<size_t(const list_t* list)> body{
      [this](const list_t* /* list */) { return return_value; }};
  size_t operator()(const list_t* list) { return body(list); };
};
extern struct list_length list_length;

// Name: list_new
// Params: list_free_cb callback
// Return: list_t*
struct list_new {
  list_t* return_value{0};
  std::function<list_t*(list_free_cb callback)> body{
      [this](list_free_cb /* callback */) { return return_value; }};
  list_t* operator()(list_free_cb callback) { return body(callback); };
};
extern struct list_new list_new;

// Name: list_new_internal
// Params: list_free_cb callback, const allocator_t* zeroed_allocator
// Return: list_t*
struct list_new_internal {
  list_t* return_value{0};
  std::function<list_t*(list_free_cb callback,
                        const allocator_t* zeroed_allocator)>
      body{[this](list_free_cb /* callback */,
                  const allocator_t* /* zeroed_allocator */) {
        return return_value;
      }};
  list_t* operator()(list_free_cb callback,
                     const allocator_t* zeroed_allocator) {
    return body(callback, zeroed_allocator);
  };
};
extern struct list_new_internal list_new_internal;

// Name: list_next
// Params: const list_node_t* node
// Return: list_node_t*
struct list_next {
  list_node_t* return_value{0};
  std::function<list_node_t*(const list_node_t* node)> body{
      [this](const list_node_t* /* node */) { return return_value; }};
  list_node_t* operator()(const list_node_t* node) { return body(node); };
};
extern struct list_next list_next;

// Name: list_node
// Params: const list_node_t* node
// Return: void*
struct list_node {
  void* return_value{};
  std::function<void*(const list_node_t* node)> body{
      [this](const list_node_t* /* node */) { return return_value; }};
  void* operator()(const list_node_t* node) { return body(node); };
};
extern struct list_node list_node;

// Name: list_prepend
// Params: list_t* list, void* data
// Return: bool
struct list_prepend {
  bool return_value{false};
  std::function<bool(list_t* list, void* data)> body{
      [this](list_t* /* list */, void* /* data */) { return return_value; }};
  bool operator()(list_t* list, void* data) { return body(list, data); };
};
extern struct list_prepend list_prepend;

// Name: list_remove
// Params: list_t* list, void* data
// Return: bool
struct list_remove {
  bool return_value{false};
  std::function<bool(list_t* list, void* data)> body{
      [this](list_t* /* list */, void* /* data */) { return return_value; }};
  bool operator()(list_t* list, void* data) { return body(list, data); };
};
extern struct list_remove list_remove;

}  // namespace osi_list
}  // namespace mock
}  // namespace test

// END mockcify generation
