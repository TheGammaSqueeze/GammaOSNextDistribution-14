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

// This namespace is exclusively for vndk-sp libs.

#include "linkerconfig/environment.h"

#include <android-base/strings.h>

#include "linkerconfig/namespacebuilder.h"

using android::linkerconfig::modules::Namespace;

namespace android {
namespace linkerconfig {
namespace contents {
Namespace BuildVndkNamespace([[maybe_unused]] const Context& ctx,
                             VndkUserPartition vndk_user) {
  bool is_system_or_unrestricted_section = ctx.IsSystemSection() ||
                                           ctx.IsUnrestrictedSection();
  if (ctx.IsApexBinaryConfig()) {
    is_system_or_unrestricted_section = ctx.GetCurrentApex().InSystem();
  }
  // In the system section, we need to have an additional vndk namespace for
  // product apps. We must have a different name "vndk_product" for this
  // namespace. "vndk_product" namespace is used only from the native_loader for
  // product apps.
  const char* name;
  if (is_system_or_unrestricted_section &&
      vndk_user == VndkUserPartition::Product) {
    name = "vndk_product";
  } else {
    name = "vndk";
  }

  // Isolated and visible when used in the [system] or [unrestricted] section to
  // allow links to be created at runtime, e.g. through android_link_namespaces
  // in libnativeloader. Otherwise namespace should be isolated but not visible
  // so namespace itself keep strict and links would not be modified at runtime.
  Namespace ns(name,
               /*is_isolated=*/true,
               /*is_visible=*/is_system_or_unrestricted_section);

  std::vector<std::string> lib_paths;
  std::string vndk_version;
  if (vndk_user == VndkUserPartition::Product) {
    lib_paths = {Var("PRODUCT") + "/${LIB}"};
    vndk_version = Var("PRODUCT_VNDK_VERSION");
  } else {
    // default for vendor
    lib_paths = {"/odm/${LIB}", "/vendor/${LIB}"};
    vndk_version = Var("VENDOR_VNDK_VERSION");
  }

  // Search order:
  // 1. VNDK Extensions
  // 2. VNDK APEX
  // 3. vendor/lib or product/lib to allow extensions to use them

  // 1. VNDK Extensions
  for (const auto& lib_path : lib_paths) {
    ns.AddSearchPath(lib_path + "/vndk-sp");
    if (!is_system_or_unrestricted_section) {
      ns.AddSearchPath(lib_path + "/vndk");
    }
  }

  // 2. VNDK APEX
  ns.AddSearchPath("/apex/com.android.vndk.v" + vndk_version + "/${LIB}");

  if (vndk_user == VndkUserPartition::Vendor) {
    // It is for vendor sp-hal
    ns.AddPermittedPath("/odm/${LIB}/hw");
    ns.AddPermittedPath("/odm/${LIB}/egl");
    ns.AddPermittedPath("/vendor/${LIB}/hw");
    ns.AddPermittedPath("/vendor/${LIB}/egl");
    ns.AddPermittedPath("/system/vendor/${LIB}/hw");
    ns.AddPermittedPath("/system/vendor/${LIB}/egl");

    // This is exceptionally required since android.hidl.memory@1.0-impl.so is here
    ns.AddPermittedPath("/apex/com.android.vndk.v" +
                        Var("VENDOR_VNDK_VERSION") + "/${LIB}/hw");
  }

  // 3. vendor/lib or product/lib
  if (is_system_or_unrestricted_section || ctx.IsApexBinaryConfig()) {
    // Add (vendor|product)/lib for cases (vendor|product) namespace does not exist.
    for (const auto& lib_path : lib_paths) {
      ns.AddSearchPath(lib_path);
    }
  } else {
    // To avoid double loading library issue, add link to the default
    // namespace instead of adding search path.
    ns.GetLink("default").AllowAllSharedLibs();
  }

  AddLlndkLibraries(ctx, &ns, vndk_user);

  if (ctx.IsProductSection() || ctx.IsVendorSection()) {
    if (android::linkerconfig::modules::IsVndkInSystemNamespace()) {
      ns.GetLink("vndk_in_system")
          .AddSharedLib(Var("VNDK_USING_CORE_VARIANT_LIBRARIES"));
    }
  }

  return ns;
}
}  // namespace contents
}  // namespace linkerconfig
}  // namespace android
