/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#define LOG_TAG "bt_main_thread"

#include "stack/include/main_thread.h"

#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/run_loop.h>
#include <base/threading/thread.h>
#include <bluetooth/log.h>

#include "common/message_loop_thread.h"
#include "include/hardware/bluetooth.h"
#include "os/log.h"

using bluetooth::common::MessageLoopThread;
using namespace bluetooth;

static MessageLoopThread main_thread("bt_main_thread");

bluetooth::common::MessageLoopThread* get_main_thread() { return &main_thread; }

bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task) {
  if (!main_thread.DoInThread(from_here, std::move(task))) {
    log::error("failed from {}", from_here.ToString());
    return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}

bt_status_t do_in_main_thread_delayed(const base::Location& from_here,
                                      base::OnceClosure task,
                                      std::chrono::microseconds delay) {
  if (!main_thread.DoInThreadDelayed(from_here, std::move(task), delay)) {
    log::error("failed from {}", from_here.ToString());
    return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}

static void do_post_on_bt_main(BtMainClosure closure) { closure(); }

void post_on_bt_main(BtMainClosure closure) {
  ASSERT(do_in_main_thread(FROM_HERE, base::BindOnce(do_post_on_bt_main,
                                                     std::move(closure))) ==
         BT_STATUS_SUCCESS);
}

void main_thread_start_up() {
  main_thread.StartUp();
  if (!main_thread.IsRunning()) {
    log::fatal("unable to start btu message loop thread.");
  }
  if (!main_thread.EnableRealTimeScheduling()) {
#if defined(__ANDROID__)
    log::error("unable to enable real time scheduling");
#else
    log::error("unable to enable real time scheduling");
#endif
  }
}

void main_thread_shut_down() { main_thread.ShutDown(); }
