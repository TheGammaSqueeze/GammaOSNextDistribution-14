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

#include <base/functional/bind.h>
#include <base/functional/callback_forward.h>
#include <base/location.h>
#include <sys/types.h>
#include <unistd.h>

#include <functional>
#include <future>

#include "common/message_loop_thread.h"
#include "include/hardware/bluetooth.h"
#include "os/log.h"

using bluetooth::common::MessageLoopThread;
using BtMainClosure = std::function<void()>;

namespace {

MessageLoopThread main_thread("bt_test_main_thread");
void do_post_on_bt_main(BtMainClosure closure) { closure(); }

}  // namespace

bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task) {
  ASSERT_LOG(main_thread.DoInThread(from_here, std::move(task)),
             "Unable to run on main thread");
  return BT_STATUS_SUCCESS;
}

bt_status_t do_in_main_thread_delayed(const base::Location& from_here,
                                      base::OnceClosure task,
                                      std::chrono::microseconds delay) {
  ASSERT_LOG(!main_thread.DoInThreadDelayed(from_here, std::move(task), delay),
             "Unable to run on main thread delayed");
  return BT_STATUS_SUCCESS;
}

void post_on_bt_main(BtMainClosure closure) {
  ASSERT(do_in_main_thread(FROM_HERE, base::BindOnce(do_post_on_bt_main,
                                                     std::move(closure))) ==
         BT_STATUS_SUCCESS);
}

void main_thread_start_up() {
  main_thread.StartUp();
  ASSERT_LOG(main_thread.IsRunning(),
             "Unable to start message loop on main thread");
}

void main_thread_shut_down() { main_thread.ShutDown(); }

// osi_alarm
bluetooth::common::MessageLoopThread* get_main_thread() { return &main_thread; }
