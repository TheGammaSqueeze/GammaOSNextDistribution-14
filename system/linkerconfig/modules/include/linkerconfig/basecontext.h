/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <functional>
#include <string>
#include <unordered_map>
#include <vector>

#include <linker_config.pb.h>

#include "linkerconfig/apex.h"
#include "linkerconfig/namespace.h"

namespace android {
namespace linkerconfig {
namespace modules {

class BaseContext {
 public:
  BaseContext();
  virtual ~BaseContext() = default;

  void SetApexModules(std::vector<ApexInfo>&& apex_modules);
  const std::vector<ApexInfo>& GetApexModules() const;
  const std::unordered_map<std::string, std::reference_wrapper<const ApexInfo>>&
  GetApexModuleMap() const;

  void SetStrictMode(bool strict);
  bool IsStrictMode() const;

  void SetTargetApex(const std::string& target_apex);
  const std::string& GetTargetApex() const;

  virtual Namespace BuildApexNamespace(const ApexInfo& apex_info,
                                       bool visible) const;

  void SetSystemConfig(const android::linkerconfig::proto::LinkerConfig& config);
  const std::vector<std::string>& GetSystemProvideLibs() const;
  const std::vector<std::string>& GetSystemRequireLibs() const;

  void SetVendorConfig(const android::linkerconfig::proto::LinkerConfig& config);
  const std::vector<std::string>& GetVendorProvideLibs() const;
  const std::vector<std::string>& GetVendorRequireLibs() const;

  void SetProductConfig(const android::linkerconfig::proto::LinkerConfig& config);
  const std::vector<std::string>& GetProductProvideLibs() const;
  const std::vector<std::string>& GetProductRequireLibs() const;

 private:
  bool strict_;
  std::string target_apex_;

  // Available APEX Modules which contains binary and/or library
  std::vector<ApexInfo> apex_modules_;

  // Map of library to the APEX module
  std::unordered_map<std::string, std::reference_wrapper<const ApexInfo>>
      apex_module_map_;

  std::vector<std::string> system_provide_libs_;
  std::vector<std::string> system_require_libs_;
  std::vector<std::string> vendor_provide_libs_;
  std::vector<std::string> vendor_require_libs_;
  std::vector<std::string> product_provide_libs_;
  std::vector<std::string> product_require_libs_;
};

}  // namespace modules
}  // namespace linkerconfig
}  // namespace android
