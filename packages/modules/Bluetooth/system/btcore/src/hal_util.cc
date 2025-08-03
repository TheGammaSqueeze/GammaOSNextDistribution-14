//
//  Copyright 2015 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#define LOG_TAG "hal_util"

#include "btcore/include/hal_util.h"

#include <base/logging.h>
#include <base/strings/stringprintf.h>
#include <dlfcn.h>
#include <hardware/bluetooth.h>

using base::StringPrintf;

extern bt_interface_t bluetoothInterface;

int hal_util_load_bt_library(const bt_interface_t** interface) {
  *interface = &bluetoothInterface;

  return 0;
}
