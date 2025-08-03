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

#include "linkerconfig/namespace.h"
#include "linkerconfig/sectionbuilder.h"

#include <functional>
#include <vector>

#include "linkerconfig/common.h"
#include "linkerconfig/environment.h"
#include "linkerconfig/log.h"
#include "linkerconfig/namespacebuilder.h"
#include "linkerconfig/section.h"

using android::linkerconfig::contents::SectionType;
using android::linkerconfig::modules::ApexInfo;
using android::linkerconfig::modules::LibProvider;
using android::linkerconfig::modules::LibProviders;
using android::linkerconfig::modules::Namespace;
using android::linkerconfig::modules::Section;
using android::linkerconfig::modules::SharedLibs;

namespace android {
namespace linkerconfig {
namespace contents {

// Builds default section for the apex
// For com.android.foo,
//   dir.com.android.foo = /apex/com.android.foo/bin
//   [com.android.foo]
//   additional.namespaces = system
//   namespace.default....
//   namespace.system...
Section BuildApexDefaultSection(Context& ctx, const ApexInfo& apex_info) {
  ctx.SetCurrentSection(SectionType::Other);

  bool target_apex_visible = apex_info.visible;
  std::set<std::string> visible_apexes;
  if (apex_info.name == "com.android.art") {
    // ld.config.txt for the ART module needs to have the namespaces with public
    // and JNI libs visible since it runs dalvikvm and hence libnativeloader,
    // which builds classloader namespaces that may link to those libs.
    for (const auto& apex : ctx.GetApexModules()) {
      if (apex.jni_libs.size() > 0 || apex.public_libs.size() > 0) {
        visible_apexes.insert(apex.name);
        if (apex.name == apex_info.name) {
          target_apex_visible = true;
        }
      }
    }
  }

  std::vector<Namespace> namespaces;

  // If target APEX should be visible, then there will be two namespaces -
  // default and APEX namespace - with same set of libraries. To avoid any
  // confusion based on two same namespaces, and also to avoid loading same
  // library twice based on the namespace, use empty default namespace which
  // does not contain any search path and fully links to visible APEX namespace.
  if (target_apex_visible) {
    namespaces.emplace_back(BuildApexEmptyDefaultNamespace(ctx, apex_info));
  } else {
    namespaces.emplace_back(BuildApexDefaultNamespace(ctx, apex_info));
  }
  namespaces.emplace_back(BuildApexPlatformNamespace(ctx));

  // Vendor APEXes can use libs provided by "vendor"
  // and Product APEXes can use libs provided by "product"
  if (android::linkerconfig::modules::IsTreblelizedDevice()) {
    if (apex_info.InVendor()) {
      namespaces.emplace_back(BuildRsNamespace(ctx));
      auto vendor = BuildVendorNamespace(ctx, "vendor");
      if (!vendor.GetProvides().empty()) {
        namespaces.emplace_back(std::move(vendor));
      }
      if (android::linkerconfig::modules::IsVendorVndkVersionDefined()) {
        namespaces.emplace_back(
            BuildVndkNamespace(ctx, VndkUserPartition::Vendor));
        if (android::linkerconfig::modules::IsVndkInSystemNamespace()) {
          namespaces.emplace_back(BuildVndkInSystemNamespace(ctx));
        }
      }
    } else if (apex_info.InProduct()) {
      auto product = BuildProductNamespace(ctx, "product");
      if (!product.GetProvides().empty()) {
        namespaces.emplace_back(std::move(product));
      }
      if (android::linkerconfig::modules::IsProductVndkVersionDefined()) {
        namespaces.emplace_back(
            BuildVndkNamespace(ctx, VndkUserPartition::Product));
        if (android::linkerconfig::modules::IsVndkInSystemNamespace()) {
          namespaces.emplace_back(BuildVndkInSystemNamespace(ctx));
        }
      }
    }
  }

  LibProviders libs_providers;
  if (apex_info.InVendor()) {
    // In Vendor APEX, sphal namespace is not required and possible to cause
    // same library being loaded from two namespaces (sphal and vendor). As
    // SPHAL itself is not required from vendor (APEX) section, add vendor
    // namespace instead.
    libs_providers[":sphal"] = {LibProvider{
        "vendor",
        std::bind(BuildVendorNamespace, ctx, "vendor"),
        SharedLibs{{}},
    }};
  } else {
    libs_providers[":sphal"] = {LibProvider{
        "sphal",
        std::bind(BuildSphalNamespace, ctx),
        SharedLibs{{}},
    }};
  }

  bool in_vendor_with_vndk_enabled =
      !apex_info.InProduct() &&
      android::linkerconfig::modules::IsVendorVndkVersionDefined();
  bool in_product_with_vndk_enabled =
      apex_info.InProduct() &&
      android::linkerconfig::modules::IsProductVndkVersionDefined();

  if (in_vendor_with_vndk_enabled || in_product_with_vndk_enabled) {
    VndkUserPartition user_partition = VndkUserPartition::Vendor;
    std::string user_partition_suffix = "VENDOR";
    if (apex_info.InProduct()) {
      user_partition = VndkUserPartition::Product;
      user_partition_suffix = "PRODUCT";
    }
    libs_providers[":sanitizer"] = {LibProvider{
        ctx.GetSystemNamespaceName(),
        std::bind(BuildApexPlatformNamespace,
                  ctx),  // "system" should be available
        SharedLibs{{Var("SANITIZER_DEFAULT_" + user_partition_suffix)}},
    }};
    libs_providers[":vndk"] = GetVndkProvider(ctx, user_partition);
    libs_providers[":vndksp"] = {LibProvider{
        "vndk",
        std::bind(BuildVndkNamespace, ctx, user_partition),
        SharedLibs{{Var("VNDK_SAMEPROCESS_LIBRARIES_" + user_partition_suffix)}},
    }};
  }

  if (apex_info.InVendor()) {
    AddVendorSubdirNamespaceProviders(ctx, libs_providers);
  }

  return BuildSection(
      ctx, apex_info.name, std::move(namespaces), visible_apexes, libs_providers);
}

}  // namespace contents
}  // namespace linkerconfig
}  // namespace android
