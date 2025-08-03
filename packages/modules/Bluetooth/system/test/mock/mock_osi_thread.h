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

#include <functional>

// Original included files, if any
#include "osi/include/fixed_queue.h"
#include "osi/include/reactor.h"
#include "osi/include/thread.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_thread {

// Shared state between mocked functions and tests
// Name: thread_free
// Params: thread_t* thread
// Return: void
struct thread_free {
  std::function<void(thread_t* thread)> body{[](thread_t* /* thread */) {}};
  void operator()(thread_t* thread) { body(thread); };
};
extern struct thread_free thread_free;

// Name: thread_get_reactor
// Params: const thread_t* thread
// Return: reactor_t*
struct thread_get_reactor {
  reactor_t* return_value{0};
  std::function<reactor_t*(const thread_t* thread)> body{
      [this](const thread_t* /* thread */) { return return_value; }};
  reactor_t* operator()(const thread_t* thread) { return body(thread); };
};
extern struct thread_get_reactor thread_get_reactor;

// Name: thread_is_self
// Params: const thread_t* thread
// Return: bool
struct thread_is_self {
  bool return_value{false};
  std::function<bool(const thread_t* thread)> body{
      [this](const thread_t* /* thread */) { return return_value; }};
  bool operator()(const thread_t* thread) { return body(thread); };
};
extern struct thread_is_self thread_is_self;

// Name: thread_join
// Params: thread_t* thread
// Return: void
struct thread_join {
  std::function<void(thread_t* thread)> body{[](thread_t* /* thread */) {}};
  void operator()(thread_t* thread) { body(thread); };
};
extern struct thread_join thread_join;

// Name: thread_name
// Params: const thread_t* thread
// Return: const char*
struct thread_name {
  const char* return_value{0};
  std::function<const char*(const thread_t* thread)> body{
      [this](const thread_t* /* thread */) { return return_value; }};
  const char* operator()(const thread_t* thread) { return body(thread); };
};
extern struct thread_name thread_name;

// Name: thread_new
// Params: const char* name
// Return: thread_t*
struct thread_new {
  thread_t* return_value{0};
  std::function<thread_t*(const char* name)> body{
      [this](const char* /* name */) { return return_value; }};
  thread_t* operator()(const char* name) { return body(name); };
};
extern struct thread_new thread_new;

// Name: thread_new_sized
// Params: const char* name, size_t work_queue_capacity
// Return: thread_t*
struct thread_new_sized {
  thread_t* return_value{0};
  std::function<thread_t*(const char* name, size_t work_queue_capacity)> body{
      [this](const char* /* name */, size_t /* work_queue_capacity */) {
        return return_value;
      }};
  thread_t* operator()(const char* name, size_t work_queue_capacity) {
    return body(name, work_queue_capacity);
  };
};
extern struct thread_new_sized thread_new_sized;

// Name: thread_post
// Params: thread_t* thread, thread_fn func, void* context
// Return: bool
struct thread_post {
  bool return_value{false};
  std::function<bool(thread_t* thread, thread_fn func, void* context)> body{
      [this](thread_t* /* thread */, thread_fn /* func */,
             void* /* context */) { return return_value; }};
  bool operator()(thread_t* thread, thread_fn func, void* context) {
    return body(thread, func, context);
  };
};
extern struct thread_post thread_post;

// Name: thread_set_priority
// Params: thread_t* thread, int priority
// Return: bool
struct thread_set_priority {
  bool return_value{false};
  std::function<bool(thread_t* thread, int priority)> body{
      [this](thread_t* /* thread */, int /* priority */) {
        return return_value;
      }};
  bool operator()(thread_t* thread, int priority) {
    return body(thread, priority);
  };
};
extern struct thread_set_priority thread_set_priority;

// Name: thread_set_rt_priority
// Params: thread_t* thread, int priority
// Return: bool
struct thread_set_rt_priority {
  bool return_value{false};
  std::function<bool(thread_t* thread, int priority)> body{
      [this](thread_t* /* thread */, int /* priority */) {
        return return_value;
      }};
  bool operator()(thread_t* thread, int priority) {
    return body(thread, priority);
  };
};
extern struct thread_set_rt_priority thread_set_rt_priority;

// Name: thread_stop
// Params: thread_t* thread
// Return: void
struct thread_stop {
  std::function<void(thread_t* thread)> body{[](thread_t* /* thread */) {}};
  void operator()(thread_t* thread) { body(thread); };
};
extern struct thread_stop thread_stop;

}  // namespace osi_thread
}  // namespace mock
}  // namespace test

// END mockcify generation
