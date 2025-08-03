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

#include "linkerconfig/baseconfig.h"
#include "linkerconfig/environment.h"
#include "linkerconfig/sectionbuilder.h"
#include "linkerconfig/variables.h"

using android::linkerconfig::modules::DirToSection;
using android::linkerconfig::modules::Section;

namespace {
void RemoveSection(std::vector<DirToSection>& dir_to_section,
                   const std::string& to_be_removed) {
  dir_to_section.erase(
      std::remove_if(dir_to_section.begin(),
                     dir_to_section.end(),
                     [&](auto pair) { return (pair.second == to_be_removed); }),
      dir_to_section.end());
}
}  // namespace

namespace android {
namespace linkerconfig {
namespace contents {
android::linkerconfig::modules::Configuration CreateBaseConfiguration(
    Context& ctx) {
  std::vector<Section> sections;

  ctx.SetCurrentLinkerConfigType(LinkerConfigType::Default);

  // Don't change the order here. The first pattern that matches with the
  // absolute path of an executable is selected.
  std::vector<DirToSection> dirToSection = {
      {"/system/bin/", "system"},
      {"/system/xbin/", "system"},
      {Var("SYSTEM_EXT") + "/bin/", "system"},

      // Processes from the product partition will have a separate section if
      // PRODUCT_PRODUCT_VNDK_VERSION is defined. Otherwise, they are run from
      // the "system" section.
      {Var("PRODUCT") + "/bin/", "product"},

      {"/odm/bin/", "vendor"},
      {"/vendor/bin/", "vendor"},
      {"/data/nativetest/odm", "vendor"},
      {"/data/nativetest64/odm", "vendor"},
      {"/data/benchmarktest/odm", "vendor"},
      {"/data/benchmarktest64/odm", "vendor"},
      {"/data/nativetest/vendor", "vendor"},
      {"/data/nativetest64/vendor", "vendor"},
      {"/data/benchmarktest/vendor", "vendor"},
      {"/data/benchmarktest64/vendor", "vendor"},

      {"/data/nativetest/unrestricted", "unrestricted"},
      {"/data/nativetest64/unrestricted", "unrestricted"},

      // Create isolated namespace for development purpose.
      // This isolates binary from the system so binaries and libraries from
      // this location can be separated from system libraries.
      {"/data/local/tmp/isolated", "isolated"},

      // Create directories under shell-writable /data/local/tests for
      // each namespace in order to run tests.
      {"/data/local/tests/product", "product"},
      {"/data/local/tests/system", "system"},
      {"/data/local/tests/unrestricted", "unrestricted"},
      {"/data/local/tests/vendor", "vendor"},

      // TODO(b/123864775): Ensure tests are run from one of the subdirectories
      // above.  Then clean this up.
      {"/data/local/tmp", "unrestricted"},

      {"/data/fuzz", "fuzz"},

      {"/postinstall", "postinstall"},
      // Fallback entry to provide APEX namespace lookups for binaries anywhere
      // else. This must be last.
      {"/data", "system"},
      // TODO(b/168556887): Remove this when we have a dedicated section for
      // binaries in APKs
      {Var("PRODUCT") + "/app/", "system"},
  };

  sections.emplace_back(BuildSystemSection(ctx));
  if (android::linkerconfig::modules::IsTreblelizedDevice()) {
    sections.emplace_back(BuildVendorSection(ctx));
    sections.emplace_back(BuildProductSection(ctx));
  } else {
    RemoveSection(dirToSection, "product");
    RemoveSection(dirToSection, "vendor");
  }

  sections.emplace_back(BuildUnrestrictedSection(ctx));
  sections.emplace_back(BuildPostInstallSection(ctx));

  sections.emplace_back(BuildIsolatedSection(ctx));

  sections.emplace_back(BuildFuzzSection(ctx));

  return android::linkerconfig::modules::Configuration(std::move(sections),
                                                       std::move(dirToSection));
}
}  // namespace contents
}  // namespace linkerconfig
}  // namespace android
