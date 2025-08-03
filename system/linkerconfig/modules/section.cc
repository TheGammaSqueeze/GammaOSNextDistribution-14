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
#include "linkerconfig/section.h"

#include <algorithm>
#include <functional>
#include <unordered_map>
#include <utility>

#include <android-base/result.h>
#include <android-base/strings.h>

#include "linkerconfig/environment.h"
#include "linkerconfig/log.h"

using android::base::Join;
using android::base::Result;

namespace android {
namespace linkerconfig {
namespace modules {

void Section::WriteConfig(ConfigWriter& writer) const {
  writer.WriteLine("[" + name_ + "]");

  if (namespaces_.size() > 1) {
    std::vector<std::string> additional_namespaces;
    for (const auto& ns : namespaces_) {
      if (ns.GetName() != "default") {
        additional_namespaces.push_back(ns.GetName());
      }
    }
    writer.WriteLine("additional.namespaces = " +
                     Join(additional_namespaces, ','));
  }

  for (const auto& ns : namespaces_) {
    ns.WriteConfig(writer);
  }
}

static bool ShouldFailOnMissingDeps(const BaseContext& ctx,
                                    const Namespace& ns) {
  if (!ctx.IsStrictMode()) {
    return false;
  }
  // When generating for a target apex, "--strict" is applied to only the namespace
  // for the apex to avoid failing due to missing deps in other namespaces
  if (!ctx.GetTargetApex().empty()) {
    return ns.GetName() == "default" || ns.GetName() == ctx.GetTargetApex();
  }
  return true;
}

// Resolve() resolves require/provide constraints between namespaces.
// When foo.AddProvides({"libfoo.so"}) and bar.AddRequires({"libfoo.so"}),
// then Resolve() creates a linke between foo and bar:
//   foo.GetLink("bar").AddSharedLib({"libfoo.so"}).
//
// When a referenced lib is not provided by existing namespaces,
// it searches the lib in available apexes <apex_providers>
// and available aliases <lib_providers>, If found, new namespace is added.
void Section::Resolve(const BaseContext& ctx,
                      const LibProviders& lib_providers) {
  // libs provided by existing namespaces
  std::unordered_map<std::string, std::string> providers;
  for (auto& ns : namespaces_) {
    for (const auto& lib : ns.GetProvides()) {
      if (auto iter = providers.find(lib); iter != providers.end()) {
        LOG(android::base::FATAL)
            << fmt::format("duplicate: {} is provided by {} and {} in [{}]",
                           lib,
                           iter->second,
                           ns.GetName(),
                           name_);
      } else {
        providers[lib] = ns.GetName();
      }
    }
  }

  // libs provided by apexes
  const auto& apex_providers = ctx.GetApexModuleMap();

  // add a new namespace if not found
  auto add_namespace = [&](auto name, auto builder) {
    for (auto& ns : namespaces_) {
      if (ns.GetName() == name) {
        // it's there, we don't need to create a new one.
        return;
      }
    }
    auto new_ns = builder();
    // Update providing library map from the new namespace
    for (const auto& new_lib : new_ns.GetProvides()) {
      if (providers.find(new_lib) == providers.end()) {
        providers[new_lib] = new_ns.GetName();
      }
    }
    namespaces_.push_back(std::move(new_ns));
  };

  // Reserve enough space for namespace vector which can be increased maximum as
  // much as potential providers. Appending new namespaces without reserving
  // enough space from iteration can crash the process.
  namespaces_.reserve(namespaces_.size() + ctx.GetApexModules().size() +
                      lib_providers.size());

  auto iter = namespaces_.begin();
  do {
    auto& ns = *iter;
    for (const auto& lib : ns.GetRequires()) {
      // Search the required library in existing namespaces first <providers>,
      // then the available apexes <apex_providers>,
      // then the available aliases <lib_providers>
      if (auto it = providers.find(lib); it != providers.end()) {
        ns.GetLink(it->second).AddSharedLib(lib);
      } else if (auto it = apex_providers.find(lib); it != apex_providers.end()) {
        const auto& apex_info = it->second.get();
        ns.GetLink(apex_info.namespace_name).AddSharedLib(lib);
        // Add a new namespace for the apex
        add_namespace(apex_info.namespace_name, [&]() {
          return ctx.BuildApexNamespace(apex_info, false);
        });
      } else if (auto it = lib_providers.find(lib); it != lib_providers.end()) {
        for (const auto& provider : it->second) {
          // Alias is expanded to <shared_libs>.
          // For example, ":vndk" is expanded to the list of VNDK-Core/VNDK-Sp libraries
          std::visit([&](auto&& mod) { mod.Apply(ns.GetLink(provider.ns)); },
                     provider.link_modifier);
          // Add a new namespace for the alias
          add_namespace(provider.ns, provider.ns_builder);
        }
      } else if (ShouldFailOnMissingDeps(ctx, ns)) {
        LOG(FATAL) << fmt::format(
            "not found: {} is required by {} in [{}]", lib, ns.GetName(), name_);
      }
    }
    iter++;
  } while (iter != namespaces_.end());

  std::sort(namespaces_.begin(),
            namespaces_.end(),
            [](const auto& lhs, const auto& rhs) -> bool {
              // make "default" a smallest one
              if (lhs.GetName() == "default") return true;
              if (rhs.GetName() == "default") return false;
              return lhs.GetName() < rhs.GetName();
            });
}

Namespace* Section::GetNamespace(const std::string& namespace_name) {
  for (auto& ns : namespaces_) {
    if (ns.GetName() == namespace_name) {
      return &ns;
    }
  }

  return nullptr;
}

std::string Section::GetName() {
  return name_;
}
}  // namespace modules
}  // namespace linkerconfig
}  // namespace android
