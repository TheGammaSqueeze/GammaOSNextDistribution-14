/*
 * Copyright 2020 The Android Open Source Project
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

#include <fuzzer/FuzzedDataProvider.h>
#include <sys/select.h>
#include "osi/include/fixed_queue.h"
#include "osi/include/future.h"
#include "osi/include/thread.h"
#include "osi/test/fuzzers/include/libosiFuzzHelperFunctions.h"

#define MAX_START_SIZE 2048
#define MAX_NUM_FUNCTIONS 512
#define MAX_BUF_SIZE 512

static future_t* received_message_future = nullptr;

// Empty callback function
void fqFreeCb(void* data) {}
void fqCb(fixed_queue_t* queue, void* data) {
  void* msg = fixed_queue_try_dequeue(queue);
  future_ready(received_message_future, msg);
}

// Returns either a nullptr or a function ptr to the placeholder cb function
fixed_queue_free_cb cbOrNull(FuzzedDataProvider* dataProvider) {
  bool null_cb = dataProvider->ConsumeBool();
  if (null_cb) {
    return nullptr;
  } else {
    return fqFreeCb;
  }
}

bool fdIsAvailable(int fd) {
  int nfds = 1;
  fd_set readfds, writefds, exceptfds;
  timeval timeout;

  FD_ZERO(&readfds);
  FD_ZERO(&writefds);
  FD_ZERO(&exceptfds);
  FD_SET(fd, &readfds);
  timeout.tv_sec = 0;
  timeout.tv_usec = 50;

  return select(nfds, &readfds, &writefds, &exceptfds, &timeout) > 0;
}

void createNewFuture() {
  // Free the existing future if it exists
  if (received_message_future != nullptr) {
    future_ready(received_message_future, nullptr);
    future_await(received_message_future);
  }

  // Create a new one
  received_message_future = future_new();
}

void callArbitraryFunction(fixed_queue_t* fixed_queue,
                           std::vector<void*>* live_buffer_vector,
                           std::vector<thread_t*>* live_thread_vector,
                           FuzzedDataProvider* dataProvider) {
  void* buf_ptr = nullptr;
  size_t index = 0;
  int fd = 0;
  // Get our function identifier
  switch (dataProvider->ConsumeIntegralInRange<char>(0, 17)) {
    // Let 0 be a NO-OP, as ConsumeIntegral will return 0 on an empty buffer
    // (This will likely bias whatever action is here to run more often)
    case 0:
      return;
    // Clear the queue
    case 1:
      fixed_queue_flush(fixed_queue, cbOrNull(dataProvider));
      return;
    // Check if empty
    case 2:
      fixed_queue_is_empty(fixed_queue);
      return;
    // Check length
    case 3:
      fixed_queue_length(fixed_queue);
      return;
    // Check capacity (Cannot be null)
    case 4:
      if (fixed_queue) {
        fixed_queue_capacity(fixed_queue);
      }
      return;
    // Add to the queue (Cannot be null)
    case 5:
      if (fixed_queue) {
        buf_ptr = generateBuffer(dataProvider, MAX_BUF_SIZE, false);
        live_buffer_vector->push_back(buf_ptr);
        if (buf_ptr) {
          // Make sure we won't block
          fd = fixed_queue_get_enqueue_fd(fixed_queue);
          if (fdIsAvailable(fd)) {
            fixed_queue_enqueue(fixed_queue, buf_ptr);
          }
        }
      }
      return;
    case 6:
      if (fixed_queue) {
        buf_ptr = generateBuffer(dataProvider, MAX_BUF_SIZE, false);
        live_buffer_vector->push_back(buf_ptr);
        if (buf_ptr) {
          fixed_queue_try_enqueue(fixed_queue, buf_ptr);
        }
      }
      return;
    // Remove from the queue (Cannot be null)
    case 7:
      if (fixed_queue && fixed_queue_length(fixed_queue) > 0) {
        fixed_queue_dequeue(fixed_queue);
      }
      return;
    case 8:
      if (fixed_queue) {
        fixed_queue_try_dequeue(fixed_queue);
      }
      return;
    // Peeks
    case 9:
      fixed_queue_try_peek_first(fixed_queue);
      return;
    case 10:
      fixed_queue_try_peek_last(fixed_queue);
      return;
    // Try to remove existing specific element
    case 11:
      if (live_buffer_vector->empty()) {
        return;
      }
      // Grab an existing buffer
      index = dataProvider->ConsumeIntegralInRange<size_t>(
          0, live_buffer_vector->size() - 1);
      buf_ptr = live_buffer_vector->at(index);
      if (buf_ptr != nullptr) {
        fixed_queue_try_remove_from_queue(fixed_queue, buf_ptr);
      }
      return;
    // Try to remove nonexistant element
    case 12:
      buf_ptr =
          reinterpret_cast<void*>(dataProvider->ConsumeIntegral<uint64_t>());
      if (buf_ptr != nullptr) {
        fixed_queue_try_remove_from_queue(fixed_queue, buf_ptr);
      }
      return;
    // Convert the queue to a list (Cannot be null)
    case 13:
      if (fixed_queue) {
        fixed_queue_get_list(fixed_queue);
      }
      return;
    // Check if enqueue is blocking
    case 14:
      fixed_queue_get_enqueue_fd(fixed_queue);
      return;
    // Check if dequeue is blocking
    case 15:
      fixed_queue_get_dequeue_fd(fixed_queue);
      return;
    // NOTE: thread appears to have a memleak, disabling this for now.
    case 16:
      // if (fixed_queue) {
      //   createNewFuture();
      //   // Start up a thread and register with it.
      //   thread_t* tmp_thread = thread_new(
      //       dataProvider->ConsumeRandomLengthString().c_str());
      //   if (tmp_thread == nullptr) {
      //     return;
      //   }
      //   live_thread_vector->push_back(tmp_thread);
      //   reactor_t* reactor = thread_get_reactor(tmp_thread);
      //   if (reactor == nullptr) {
      //     return;
      //   }
      //   fixed_queue_register_dequeue(fixed_queue, reactor, fqCb, nullptr);
      //   fixed_queue_enqueue(fixed_queue, (void*)"test");
      //   future_await(received_message_future);
      // }
      return;
    case 17:
      fixed_queue_unregister_dequeue(fixed_queue);
      return;
    default:
      return;
  }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  // Init our wrapper
  FuzzedDataProvider dataProvider(Data, Size);

  // Make vectors to keep track of objects we generate, for freeing
  std::vector<void*> live_buffer_vector;
  std::vector<thread_t*> live_thread_vector;

  size_t start_capacity =
      dataProvider.ConsumeIntegralInRange<size_t>(0, MAX_START_SIZE);
  fixed_queue_t* fixed_queue = fixed_queue_new(start_capacity);

  // How many functions are we going to call?
  size_t num_functions =
      dataProvider.ConsumeIntegralInRange<size_t>(0, MAX_NUM_FUNCTIONS);
  for (size_t i = 0; i < num_functions; i++) {
    callArbitraryFunction(fixed_queue, &live_buffer_vector, &live_thread_vector,
                          &dataProvider);
  }

  // Free our queue (with either a null or placeholder callback)
  fixed_queue_free(fixed_queue, cbOrNull(&dataProvider));

  // Free buffers we've created through fn calls during this fuzzer loop.
  for (const auto& buffer : live_buffer_vector) {
    free(buffer);
  }
  for (const auto& thread : live_thread_vector) {
    thread_free(thread);
  }

  return 0;
}
