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

#pragma once

#include <string>

#include "os/bt_keystore.h"

namespace bluetooth {
namespace os {

class ParameterProvider {
 public:
  // Return the path to config file for storage module
  static std::string ConfigFilePath();

  static void OverrideConfigFilePath(const std::string& path);

  // Return the path to the default snoop log file location
  static std::string SnoopLogFilePath();

  static void OverrideSnoopLogFilePath(const std::string& path);

  // Return the path to the default snooz log file location
  static std::string SnoozLogFilePath();

  static void OverrideSnoozLogFilePath(const std::string& path);

  // Return the path to the default sysprops file
  static std::string SyspropsFilePath();

  static void OverrideSyspropsFilePath(const std::string& path);

  static bluetooth_keystore::BluetoothKeystoreInterface* GetBtKeystoreInterface();

  static void SetBtKeystoreInterface(bluetooth_keystore::BluetoothKeystoreInterface* bt_keystore);

  static bool IsCommonCriteriaMode();

  static void SetCommonCriteriaMode(bool enable);

  static int GetCommonCriteriaConfigCompareResult();

  static void SetCommonCriteriaConfigCompareResult(int result);
};

}  // namespace os
}  // namespace bluetooth