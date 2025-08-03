/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "linkerconfig/basecontext.h"

namespace android {
namespace linkerconfig {
namespace contents {

enum class SectionType {
  System,
  Vendor,
  Product,
  Unrestricted,
  Other,
};

enum class LinkerConfigType {
  Default,
  Legacy,
  Recovery,
  ApexBinary,
};

class Context : public modules::BaseContext {
 public:
  Context()
      : current_section_(SectionType::System),
        current_linkerconfig_type_(LinkerConfigType::Default),
        current_apex_(nullptr) {
  }
  bool IsSystemSection() const;
  bool IsVendorSection() const;
  bool IsProductSection() const;
  bool IsUnrestrictedSection() const;
  const modules::ApexInfo& GetCurrentApex() const;

  bool IsDefaultConfig() const;
  bool IsLegacyConfig() const;
  bool IsRecoveryConfig() const;
  bool IsApexBinaryConfig() const;

  void SetCurrentSection(SectionType value);
  void SetCurrentLinkerConfigType(LinkerConfigType value);
  void SetCurrentApex(const modules::ApexInfo* apex);

  // Returns true if vndk apex is available
  bool IsVndkAvailable() const;

  // Returns the namespace that covers /system/${LIB}.
  std::string GetSystemNamespaceName() const;

  bool IsSectionVndkEnabled() const;

 private:
  SectionType current_section_;
  LinkerConfigType current_linkerconfig_type_;
  const modules::ApexInfo* current_apex_;
};

std::string Var(const std::string& name);

std::string Var(const std::string& name, const std::string& default_value);

}  // namespace contents
}  // namespace linkerconfig
}  // namespace android
