/*
 * Copyright (C) 2023 The Android Open Source Project
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

// This binary is run on boot as a oneshot service. It should not be run at any
// other point.

#include <string>

#include "android-base/logging.h"
#include "android-base/properties.h"

static void SetPropertyAndLog(const std::string& key,
                              const std::string& value,
                              const std::string& message = "") {
  if (android::base::SetProperty(key, value)) {
    LOG(INFO) << "Set property " << key << " to " << value << " " << message;
  } else {
    LOG(ERROR) << "Failed to set property " << key << " to " << value << " " << message;
  }
}

// Copies the value of one system property to another if it isn't empty and
// passes the predicate test_fn.
static void CopyPropertyIf(const char* src, const char* dst, bool (*test_fn)(const std::string&)) {
  std::string prop = android::base::GetProperty(src, "");
  if (prop.empty()) {
    LOG(INFO) << "Property " << src << " not set";
  } else if (!test_fn(prop)) {
    LOG(INFO) << "Property " << src << " has ignored value " << prop;
  } else {
    SetPropertyAndLog(dst, prop, std::string("from ") + src);
  }
}

int main(int, char** argv) {
  android::base::InitLogging(argv);

  // Copy properties that must only be set at boot and not change value later.
  // Note that P/H can change the properties in the experiment namespaces at any
  // time.
  CopyPropertyIf("persist.device_config.runtime_native_boot.useartservice",
                 "dalvik.vm.useartservice",
                 // If an OEM has set dalvik.vm.useartservice to false we
                 // shouldn't override it to true from the P/H property.
                 [](const std::string& prop) { return prop == "false"; });

  // The following system properties are temporarily used as feature flags to indicate whether the
  // module has a particular change or not.
  // Note that they don't actually control the runtime behavior within the module. Instead, they are
  // only used for guarding tests and calls from the platform.
  // TODO(b/305000383): Clean these up.
  SetPropertyAndLog("dalvik.vm.features.embedded_profile", "true");
  SetPropertyAndLog("dalvik.vm.features.art_managed_file_stats", "true");

  return 0;
}
