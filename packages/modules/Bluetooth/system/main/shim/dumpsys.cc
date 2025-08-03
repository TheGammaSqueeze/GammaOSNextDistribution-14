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

#define LOG_TAG "bt_shim_storage"

#include "main/shim/dumpsys.h"

#include <unordered_map>

#include "include/check.h"
#include "main/shim/entry.h"
#include "main/shim/shim.h"
#include "main/shim/stack.h"
#include "shim/dumpsys.h"

namespace {

constexpr char kModuleName[] = "shim::legacy::dumpsys";
static std::unordered_map<const void*, bluetooth::shim::DumpsysFunction>
    dumpsys_functions_;

}  // namespace

void bluetooth::shim::RegisterDumpsysFunction(const void* token,
                                              DumpsysFunction func) {
  CHECK(dumpsys_functions_.find(token) == dumpsys_functions_.end());
  dumpsys_functions_.insert({token, func});
}

void bluetooth::shim::UnregisterDumpsysFunction(const void* token) {
  CHECK(dumpsys_functions_.find(token) != dumpsys_functions_.end());
  dumpsys_functions_.erase(token);
}

void bluetooth::shim::Dump(int fd, const char** args) {
  if (dumpsys_functions_.empty()) {
    dprintf(fd, "%s No registered dumpsys shim legacy targets\n", kModuleName);
  } else {
    dprintf(fd, "%s Dumping shim legacy targets:%zd\n", kModuleName,
            dumpsys_functions_.size());
    for (auto& dumpsys : dumpsys_functions_) {
      dumpsys.second(fd);
    }
  }
  bluetooth::shim::Stack::GetInstance()->LockForDumpsys([=]() {
    if (bluetooth::shim::is_gd_stack_started_up()) {
      if (bluetooth::shim::is_gd_dumpsys_module_started()) {
        bluetooth::shim::GetDumpsys()->Dump(fd, args);
      } else {
        dprintf(fd, "%s NOTE: gd dumpsys module not loaded or started\n",
                kModuleName);
      }
    } else {
      dprintf(fd, "%s gd stack is enabled but not started\n", kModuleName);
    }
  });
}
