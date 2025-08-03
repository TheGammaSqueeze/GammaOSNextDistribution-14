/*
 * Copyright 2022 The Android Open Source Project
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

#define LOG_TAG "bt_headless_messenger"

#include "test/headless/messenger.h"

#include <condition_variable>
#include <deque>
#include <memory>
#include <mutex>

#include "base/logging.h"  // LOG() stdout and android log
#include "test/headless/interface.h"
#include "test/headless/log.h"

using namespace bluetooth::test::headless;
using namespace std::chrono_literals;

template <typename T>
struct callback_queue_t {
  callback_queue_t(const std::string name) : name_(name) {}
  // Must be held with lock
  size_t size() const { return callback_queue.size(); }

 private:
  const std::string name_;
  std::deque<T> callback_queue;

 public:
  void Push(T elem) { callback_queue.push_back(elem); }
  // Must be held with lock
  T Pop() {
    T p = callback_queue.front();
    callback_queue.pop_front();
    return p;
  }
  // Must be held with lock
  bool empty() const { return callback_queue.empty(); }
};

struct messenger_t {
  mutable std::mutex mutex;
  std::condition_variable cv;
  callback_queue_t<std::shared_ptr<callback_params_t>> callback_queue =
      callback_queue_t<std::shared_ptr<callback_params_t>>("callbacks");
  void Push(std::shared_ptr<callback_params_t> elem) {
    std::unique_lock<std::mutex> lk(mutex);
    callback_queue.Push(elem);
    cv.notify_all();
  }
};

namespace bluetooth {
namespace test {
namespace headless {
namespace messenger {

// Called by client to await any callback for the given callbacks
messenger_t callback_data_;

bool await_callback(Context& context) {
  std::unique_lock<std::mutex> lk(callback_data_.mutex);
  while (!callback_data_.callback_queue.empty()) {
    std::shared_ptr<callback_params_t> cb = callback_data_.callback_queue.Pop();
    if (std::find(context.callbacks.begin(), context.callbacks.end(),
                  cb->CallbackType()) != context.callbacks.end()) {
      context.callback_ready_q.push_back(cb);
    }
  }
  if (context.callback_ready_q.size() == 0) {
    callback_data_.cv.wait_for(lk, context.timeout);
  }
  return true;
}

}  // namespace messenger
}  // namespace headless
}  // namespace test
}  // namespace bluetooth

namespace bluetooth::test::headless {

void messenger_stats() {
  //  LOG_CONSOLE("%30s cnt:%zu", "device_found",
  //  discovered::device_found_.size()); LOG_CONSOLE("%30s cnt:%zu",
  //  "remote_device_properties",
  //              properties::remote_device_properties_.size());
}

// Callbacks that the messenger will handle from the bluetooth stack
void start_messenger() {
  headless_add_callback("acl_state_changed", [](callback_data_t* data) {
    ASSERT_LOG(data != nullptr, "Received nullptr callback data:%s", __func__);
    messenger::callback_data_.Push(std::make_shared<acl_state_changed_params_t>(
        *(static_cast<acl_state_changed_params_t*>(data))));
  });
  headless_add_callback("adapter_properties", [](callback_data_t* data) {
    ASSERT_LOG(data != nullptr, "Received nullptr callback data:%s", __func__);
    messenger::callback_data_.Push(
        std::make_shared<adapter_properties_params_t>(
            *(static_cast<adapter_properties_params_t*>(data))));
  });
  headless_add_callback("device_found", [](callback_data_t* data) {
    ASSERT_LOG(data != nullptr, "Received nullptr callback data:%s", __func__);
    messenger::callback_data_.Push(std::make_shared<device_found_params_t>(
        *(static_cast<device_found_params_t*>(data))));
  });
  headless_add_callback("remote_device_properties", [](callback_data_t* data) {
    ASSERT_LOG(data != nullptr, "Received nullptr callback data:%s", __func__);
    messenger::callback_data_.Push(
        std::make_shared<remote_device_properties_params_t>(
            *(static_cast<remote_device_properties_params_t*>(data))));
  });
  LOG_CONSOLE("Started messenger service");
}

void stop_messenger() {
  headless_remove_callback("remote_device_properties");
  headless_remove_callback("device_found");
  headless_remove_callback("adapter_properties");
  headless_remove_callback("acl_state_changed");

  LOG_CONSOLE("Stopped messenger service");

  messenger_stats();
}

}  // namespace bluetooth::test::headless
