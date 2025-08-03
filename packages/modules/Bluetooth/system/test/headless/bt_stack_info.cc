/*
 * Copyright 2023 The Android Open Source Project
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

#include "test/headless/bt_stack_info.h"

#include <unistd.h>

#include "btif/include/btif_common.h"  // do_in_jni_thread
#include "btif/include/btif_hh.h"      // DumpsysHid
#include "main/shim/dumpsys.h"
#include "stack/gatt/connection_manager.h"
#include "stack/include/main_thread.h"
#include "stack/include/pan_api.h"  // PAN_Dumpsys
#include "test/headless/log.h"

BtStackInfo::BtStackInfo() {
  {
    std::promise<pid_t> promise;
    auto future = promise.get_future();
    do_in_main_thread(FROM_HERE, base::BindOnce(
                                     [](std::promise<pid_t> promise) {
                                       promise.set_value(getpid());
                                     },
                                     std::move(promise)));
    main_pid_ = future.get();
  }

  {
    std::promise<pid_t> promise;
    auto future = promise.get_future();
    do_in_jni_thread(FROM_HERE, base::BindOnce(
                                    [](std::promise<pid_t> promise) {
                                      promise.set_value(getpid());
                                    },
                                    std::move(promise)));
    jni_pid_ = future.get();
  }
}

void BtStackInfo::DumpsysLite() {
  LOG_CONSOLE("main_pid:%u", main_pid_);
  LOG_CONSOLE("jni_pid:%u", jni_pid_);

  int fd = STDIN_FILENO;
  const char** arguments = nullptr;

  connection_manager::dump(fd);
  PAN_Dumpsys(fd);
  DumpsysHid(fd);
  DumpsysBtaDm(fd);
  bluetooth::shim::Dump(fd, arguments);
}
