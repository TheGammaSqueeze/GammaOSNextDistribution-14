/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "btcore/include/hal_util.h"
#include "btcore/include/module.h"

extern const module_t osi_module;

extern "C" {
struct android_namespace_t* android_get_exported_namespace(const char*) {
  return nullptr;
}
}

class BTCoreModuleFuzzer {
 public:
  void process();
};

void BTCoreModuleFuzzer::process() {
  const bt_interface_t* interface;
  (void)hal_util_load_bt_library(&interface);
  module_management_start();
  module_init(&osi_module);
  (void)module_start_up(&osi_module);
  (void)get_module(osi_module.name);
  module_shut_down(&osi_module);
  module_clean_up(&osi_module);
  module_management_stop();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t*, size_t) {
  BTCoreModuleFuzzer btCoreModuleFuzzer;
  btCoreModuleFuzzer.process();
  return 0;
}
