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

#pragma once

#include <sys/types.h>

class BtStackInfo {
 public:
  BtStackInfo();

  void DumpsysLite();

  pid_t MainPid() const { return main_pid_; }
  pid_t JniPid() const { return jni_pid_; }

 private:
  pid_t main_pid_;
  pid_t jni_pid_;
};
