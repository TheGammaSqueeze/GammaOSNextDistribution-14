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

#include "os/handler.h"

#include <chrono>

#include "os/thread.h"
#include "test/headless/handler.h"
#include "test/headless/log.h"

namespace bluetooth {
namespace test {

headless::Handler::Handler() {
  thread_ = new os::Thread("headless_thread", os::Thread::Priority::NORMAL);
  handler_ = new os::Handler(thread_);
}

headless::Handler::~Handler() {
  handler_->Clear();
  handler_->WaitUntilStopped(std::chrono::milliseconds(2000));
  delete handler_;
  delete thread_;
}

void headless::Handler::Post(common::OnceClosure closure) {
  ASSERT_LOG(handler_ != nullptr, "Handler is not valid");
  handler_->Post(std::move(closure));
}

}  // namespace test
}  // namespace bluetooth
