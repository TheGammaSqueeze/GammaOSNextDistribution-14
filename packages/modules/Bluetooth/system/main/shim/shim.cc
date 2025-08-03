/*
 * Copyright 2019 The Android Open Source Project
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

#define LOG_TAG "bt_shim"

#include "main/shim/shim.h"

#include "common/init_flags.h"
#include "main/shim/entry.h"
#include "main/shim/hci_layer.h"
#include "main/shim/stack.h"
#include "os/log.h"
#include "stack/include/main_thread.h"

static const hci_t* hci;

void btu_hci_msg_process(BT_HDR* p_msg);

static void post_to_main_message_loop(const base::Location& from_here,
                                      BT_HDR* p_msg) {
  if (do_in_main_thread(from_here, base::Bind(&btu_hci_msg_process, p_msg)) !=
      BT_STATUS_SUCCESS) {
    LOG_ERROR(": do_in_main_thread failed from %s",
              from_here.ToString().c_str());
  }
}

static future_t* ShimModuleStartUp() {
  hci = bluetooth::shim::hci_layer_get_interface();
  ASSERT_LOG(hci, "%s could not get hci layer interface.", __func__);

  hci->set_data_cb(base::Bind(&post_to_main_message_loop));

  bluetooth::shim::Stack::GetInstance()->StartEverything();
  return kReturnImmediate;
}

static future_t* GeneralShutDown() {
  bluetooth::shim::Stack::GetInstance()->Stop();
  return kReturnImmediate;
}

EXPORT_SYMBOL extern const module_t gd_shim_module = {
    .name = GD_SHIM_MODULE,
    .init = kUnusedModuleApi,
    .start_up = ShimModuleStartUp,
    .shut_down = GeneralShutDown,
    .clean_up = kUnusedModuleApi,
    .dependencies = {kUnusedModuleDependencies}};

bool bluetooth::shim::is_gd_stack_started_up() {
  return bluetooth::shim::Stack::GetInstance()->IsRunning();
}

bool bluetooth::shim::is_gd_dumpsys_module_started() {
  return bluetooth::shim::Stack::GetInstance()->IsDumpsysModuleStarted();
}

bool bluetooth::shim::is_classic_discovery_only_enabled() {
  return bluetooth::common::init_flags::classic_discovery_only_is_enabled();
}
