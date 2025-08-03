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

#include "linkerconfig/namespacebuilder.h"

#include "linkerconfig/apex.h"
#include "linkerconfig/namespace.h"

using android::linkerconfig::modules::ApexInfo;
using android::linkerconfig::modules::Namespace;

namespace android {
namespace linkerconfig {
namespace contents {
Namespace BuildApexEmptyDefaultNamespace([[maybe_unused]] const Context& ctx,
                                         const ApexInfo& apex_info) {
  Namespace ns("default", /*is_isolated=*/true, /*is_visible=*/false);

  // Do not include any search or permitted path to keep this namespace empty
  // and load all libraries in the APEX from APEX namespace (com_android_foo).

  // Use all libraries from APEX namespace instead of default
  // Link to the APEX namespace should come first from output to ensure all
  // libraries from the APEX links to the APEX namespace.
  ns.GetLink(apex_info.namespace_name).AllowAllSharedLibs();
  ns.AddRequires(apex_info.require_libs);

  return ns;
}
}  // namespace contents
}  // namespace linkerconfig
}  // namespace android
