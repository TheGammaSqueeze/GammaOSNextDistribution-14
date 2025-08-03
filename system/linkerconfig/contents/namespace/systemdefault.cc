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

// Framework-side code runs in this namespace. Libs from /vendor partition can't
// be loaded in this namespace.

#include "linkerconfig/common.h"
#include "linkerconfig/environment.h"
#include "linkerconfig/namespace.h"
#include "linkerconfig/namespacebuilder.h"

using android::linkerconfig::modules::Namespace;

namespace android {
namespace linkerconfig {
namespace contents {

void SetupSystemPermittedPaths(Namespace* ns) {
  std::string product = Var("PRODUCT");
  std::string system_ext = Var("SYSTEM_EXT");

  // We can't have entire /system/${LIB} as permitted paths because doing so
  // makes it possible to load libs in /system/${LIB}/vndk* directories by
  // their absolute paths, e.g. dlopen("/system/lib/vndk/libbase.so"). VNDK
  // libs are built with previous versions of Android and thus must not be
  // loaded into this namespace where libs built with the current version of
  // Android are loaded. Mixing the two types of libs in the same namespace
  // can cause unexpected problems.
  const std::vector<std::string> permitted_paths = {
      "/system/${LIB}/drm",
      "/system/${LIB}/extractors",
      "/system/${LIB}/hw",
      system_ext + "/${LIB}",

      // These are where odex files are located. libart has to be able to dlopen
      // the files
      "/system/framework",

      "/system/app",
      "/system/priv-app",
      system_ext + "/framework",
      system_ext + "/app",
      system_ext + "/priv-app",
      "/vendor/framework",
      "/vendor/app",
      "/vendor/priv-app",
      "/system/vendor/framework",
      "/system/vendor/app",
      "/system/vendor/priv-app",
      "/odm/framework",
      "/odm/app",
      "/odm/priv-app",
      "/oem/app",
      product + "/framework",
      product + "/app",
      product + "/priv-app",
      "/data",
      "/mnt/expand",
      "/apex/com.android.runtime/${LIB}/bionic",
      "/system/${LIB}/bootstrap",
  };

  for (const std::string& path : permitted_paths) {
    ns->AddPermittedPath(path);
  }
  if (!android::linkerconfig::modules::IsTreblelizedDevice()) {
    // System processes can use product libs only if device is not treblelized.
    ns->AddPermittedPath(product + "/${LIB}");
  }
}

Namespace BuildSystemDefaultNamespace([[maybe_unused]] const Context& ctx) {
  bool is_fully_treblelized =
      android::linkerconfig::modules::IsTreblelizedDevice();
  std::string product = Var("PRODUCT");
  std::string system_ext = Var("SYSTEM_EXT");

  // Visible to allow links to be created at runtime, e.g. through
  // android_link_namespaces in libnativeloader.
  Namespace ns("default",
               /*is_isolated=*/is_fully_treblelized,
               /*is_visible=*/true);

  ns.AddSearchPath("/system/${LIB}");
  ns.AddSearchPath(system_ext + "/${LIB}");
  if (!is_fully_treblelized) {
    // System processes can search product libs only if product VNDK is not
    // enforced.
    ns.AddSearchPath(product + "/${LIB}");
    ns.AddSearchPath("/vendor/${LIB}");
    ns.AddSearchPath("/odm/${LIB}");
  }

  if (is_fully_treblelized) {
    SetupSystemPermittedPaths(&ns);
  }

  ns.AddRequires(ctx.GetSystemRequireLibs());
  ns.AddProvides(ctx.GetSystemProvideLibs());
  return ns;
}

}  // namespace contents
}  // namespace linkerconfig
}  // namespace android
