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
#include "linkerconfig/sectionbuilder.h"

#include <algorithm>

#include "linkerconfig/common.h"
#include "linkerconfig/environment.h"
#include "linkerconfig/log.h"
#include "linkerconfig/namespace.h"
#include "linkerconfig/namespacebuilder.h"
#include "linkerconfig/section.h"

namespace android {
namespace linkerconfig {
namespace contents {

using modules::LibProvider;
using modules::LibProviders;
using modules::Namespace;
using modules::Section;
using modules::SharedLibs;

Section BuildSection(const Context& ctx, const std::string& name,
                     std::vector<Namespace>&& namespaces,
                     const std::set<std::string>& visible_apexes,
                     const LibProviders& providers) {
  // add additional visible APEX namespaces
  for (const auto& apex : ctx.GetApexModules()) {
    if (visible_apexes.find(apex.name) == visible_apexes.end() &&
        !apex.visible) {
      continue;
    }
    if (auto it = std::find_if(
            namespaces.begin(),
            namespaces.end(),
            [&apex](auto& ns) { return ns.GetName() == apex.namespace_name; });
        it == namespaces.end()) {
      auto ns = ctx.BuildApexNamespace(apex, true);
      namespaces.push_back(std::move(ns));
    } else {
      // override "visible" when the apex is already created
      it->SetVisible(true);
    }
  }

  // resolve provide/require constraints
  Section section(std::move(name), std::move(namespaces));
  section.Resolve(ctx, providers);

  AddStandardSystemLinks(ctx, &section);
  return section;
}

std::vector<LibProvider> GetVndkProvider(const Context& ctx,
                                         VndkUserPartition partition) {
  std::vector<LibProvider> provider;
  std::string partition_suffix =
      partition == VndkUserPartition::Vendor ? "VENDOR" : "PRODUCT";
  provider.push_back(LibProvider{
      "vndk",
      std::bind(BuildVndkNamespace, ctx, partition),
      SharedLibs{
          std::vector{Var("VNDK_SAMEPROCESS_LIBRARIES_" + partition_suffix),
                      Var("VNDK_CORE_LIBRARIES_" + partition_suffix)}},
  });
  if (modules::IsVndkInSystemNamespace()) {
    provider.push_back(LibProvider{
        "vndk_in_system",
        std::bind(BuildVndkInSystemNamespace, ctx),
        SharedLibs{std::vector{Var("VNDK_USING_CORE_VARIANT_LIBRARIES")}},
    });
  }
  return provider;
}

}  // namespace contents
}  // namespace linkerconfig
}  // namespace android
