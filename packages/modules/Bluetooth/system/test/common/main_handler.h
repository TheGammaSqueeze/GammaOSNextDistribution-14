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

#pragma once

#include <base/functional/callback_forward.h>
#include <base/location.h>

#include <functional>

#include "common/message_loop_thread.h"
#include "include/hardware/bluetooth.h"

using bluetooth::common::MessageLoopThread;
using BtMainClosure = std::function<void()>;

bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task);
bt_status_t do_in_main_thread_delayed(const base::Location& from_here,
                                      base::OnceClosure task,
                                      std::chrono::microseconds delay);
void post_on_bt_main(BtMainClosure closure);
void main_thread_start_up();
void main_thread_shut_down();
extern const int sync_timeout_in_ms;
void sync_main_handler();
bluetooth::common::MessageLoopThread* get_main_thread();
