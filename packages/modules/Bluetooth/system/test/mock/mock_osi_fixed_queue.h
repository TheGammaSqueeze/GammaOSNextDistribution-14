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

#include <functional>

// Original included files, if any
#include <base/logging.h>
#include <string.h>

#include "osi/include/fixed_queue.h"
#include "osi/include/list.h"
#include "osi/include/reactor.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_fixed_queue {

// Shared state between mocked functions and tests
// Name: fixed_queue_capacity
// Params: fixed_queue_t* queue
// Return: size_t
struct fixed_queue_capacity {
  size_t return_value{0};
  std::function<size_t(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  size_t operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_capacity fixed_queue_capacity;

// Name: fixed_queue_dequeue
// Params: fixed_queue_t* queue
// Return: void*
struct fixed_queue_dequeue {
  void* return_value{};
  std::function<void*(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  void* operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_dequeue fixed_queue_dequeue;

// Name: fixed_queue_enqueue
// Params: fixed_queue_t* queue, void* data
// Return: void
struct fixed_queue_enqueue {
  std::function<void(fixed_queue_t* queue, void* data)> body{
      [](fixed_queue_t* /* queue */, void* /* data */) {}};
  void operator()(fixed_queue_t* queue, void* data) { body(queue, data); };
};
extern struct fixed_queue_enqueue fixed_queue_enqueue;

// Name: fixed_queue_flush
// Params: fixed_queue_t* queue, fixed_queue_free_cb free_cb
// Return: void
struct fixed_queue_flush {
  std::function<void(fixed_queue_t* queue, fixed_queue_free_cb free_cb)> body{
      [](fixed_queue_t* /* queue */, fixed_queue_free_cb /* free_cb */) {}};
  void operator()(fixed_queue_t* queue, fixed_queue_free_cb free_cb) {
    body(queue, free_cb);
  };
};
extern struct fixed_queue_flush fixed_queue_flush;

// Name: fixed_queue_free
// Params: fixed_queue_t* queue, fixed_queue_free_cb free_cb
// Return: void
struct fixed_queue_free {
  std::function<void(fixed_queue_t* queue, fixed_queue_free_cb free_cb)> body{
      [](fixed_queue_t* /* queue */, fixed_queue_free_cb /* free_cb */) {}};
  void operator()(fixed_queue_t* queue, fixed_queue_free_cb free_cb) {
    body(queue, free_cb);
  };
};
extern struct fixed_queue_free fixed_queue_free;

// Name: fixed_queue_get_dequeue_fd
// Params: const fixed_queue_t* queue
// Return: int
struct fixed_queue_get_dequeue_fd {
  int return_value{0};
  std::function<int(const fixed_queue_t* queue)> body{
      [this](const fixed_queue_t* /* queue */) { return return_value; }};
  int operator()(const fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_get_dequeue_fd fixed_queue_get_dequeue_fd;

// Name: fixed_queue_get_enqueue_fd
// Params: const fixed_queue_t* queue
// Return: int
struct fixed_queue_get_enqueue_fd {
  int return_value{0};
  std::function<int(const fixed_queue_t* queue)> body{
      [this](const fixed_queue_t* /* queue */) { return return_value; }};
  int operator()(const fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_get_enqueue_fd fixed_queue_get_enqueue_fd;

// Name: fixed_queue_get_list
// Params: fixed_queue_t* queue
// Return: list_t*
struct fixed_queue_get_list {
  list_t* return_value{0};
  std::function<list_t*(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  list_t* operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_get_list fixed_queue_get_list;

// Name: fixed_queue_is_empty
// Params: fixed_queue_t* queue
// Return: bool
struct fixed_queue_is_empty {
  bool return_value{false};
  std::function<bool(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  bool operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_is_empty fixed_queue_is_empty;

// Name: fixed_queue_length
// Params: fixed_queue_t* queue
// Return: size_t
struct fixed_queue_length {
  size_t return_value{0};
  std::function<size_t(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  size_t operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_length fixed_queue_length;

// Name: fixed_queue_new
// Params: size_t capacity
// Return: fixed_queue_t*
struct fixed_queue_new {
  fixed_queue_t* return_value{0};
  std::function<fixed_queue_t*(size_t capacity)> body{
      [this](size_t /* capacity */) { return return_value; }};
  fixed_queue_t* operator()(size_t capacity) { return body(capacity); };
};
extern struct fixed_queue_new fixed_queue_new;

// Name: fixed_queue_register_dequeue
// Params: fixed_queue_t* queue, reactor_t* reactor, fixed_queue_cb ready_cb,
// void* context Return: void
struct fixed_queue_register_dequeue {
  std::function<void(fixed_queue_t* queue, reactor_t* reactor,
                     fixed_queue_cb ready_cb, void* context)>
      body{[](fixed_queue_t* /* queue */, reactor_t* /* reactor */,
              fixed_queue_cb /* ready_cb */, void* /* context */) {}};
  void operator()(fixed_queue_t* queue, reactor_t* reactor,
                  fixed_queue_cb ready_cb, void* context) {
    body(queue, reactor, ready_cb, context);
  };
};
extern struct fixed_queue_register_dequeue fixed_queue_register_dequeue;

// Name: fixed_queue_try_dequeue
// Params: fixed_queue_t* queue
// Return: void*
struct fixed_queue_try_dequeue {
  void* return_value{};
  std::function<void*(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  void* operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_try_dequeue fixed_queue_try_dequeue;

// Name: fixed_queue_try_enqueue
// Params: fixed_queue_t* queue, void* data
// Return: bool
struct fixed_queue_try_enqueue {
  bool return_value{false};
  std::function<bool(fixed_queue_t* queue, void* data)> body{
      [this](fixed_queue_t* /* queue */, void* /* data */) {
        return return_value;
      }};
  bool operator()(fixed_queue_t* queue, void* data) {
    return body(queue, data);
  };
};
extern struct fixed_queue_try_enqueue fixed_queue_try_enqueue;

// Name: fixed_queue_try_peek_first
// Params: fixed_queue_t* queue
// Return: void*
struct fixed_queue_try_peek_first {
  void* return_value{};
  std::function<void*(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  void* operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_try_peek_first fixed_queue_try_peek_first;

// Name: fixed_queue_try_peek_last
// Params: fixed_queue_t* queue
// Return: void*
struct fixed_queue_try_peek_last {
  void* return_value{};
  std::function<void*(fixed_queue_t* queue)> body{
      [this](fixed_queue_t* /* queue */) { return return_value; }};
  void* operator()(fixed_queue_t* queue) { return body(queue); };
};
extern struct fixed_queue_try_peek_last fixed_queue_try_peek_last;

// Name: fixed_queue_try_remove_from_queue
// Params: fixed_queue_t* queue, void* data
// Return: void*
struct fixed_queue_try_remove_from_queue {
  void* return_value{};
  std::function<void*(fixed_queue_t* queue, void* data)> body{
      [this](fixed_queue_t* /* queue */, void* /* data */) {
        return return_value;
      }};
  void* operator()(fixed_queue_t* queue, void* data) {
    return body(queue, data);
  };
};
extern struct fixed_queue_try_remove_from_queue
    fixed_queue_try_remove_from_queue;

// Name: fixed_queue_unregister_dequeue
// Params: fixed_queue_t* queue
// Return: void
struct fixed_queue_unregister_dequeue {
  std::function<void(fixed_queue_t* queue)> body{
      [](fixed_queue_t* /* queue */) {}};
  void operator()(fixed_queue_t* queue) { body(queue); };
};
extern struct fixed_queue_unregister_dequeue fixed_queue_unregister_dequeue;

}  // namespace osi_fixed_queue
}  // namespace mock
}  // namespace test

// END mockcify generation
