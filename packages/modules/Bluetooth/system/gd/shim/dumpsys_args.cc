/*
 * Copyright 2020 The Android Open Source Project
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

#include "shim/dumpsys_args.h"
#include "shim/dumpsys.h"

#include <cstring>

using namespace bluetooth;

shim::ParsedDumpsysArgs::ParsedDumpsysArgs(const char** args) {
  if (args == nullptr) return;
  const char* p = *args;
  while (p != nullptr) {
    num_args_++;
    if (!std::strcmp(p, kArgumentDeveloper)) {
      dev_arg_ = true;
    } else {
      // silently ignore unexpected option
    }
    if (++args == nullptr) break;
    p = *args;
  }
}

bool shim::ParsedDumpsysArgs::IsDeveloper() const {
  return dev_arg_;
}
