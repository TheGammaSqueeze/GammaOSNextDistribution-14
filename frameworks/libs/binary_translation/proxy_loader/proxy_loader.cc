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

#include "berberis/proxy_loader/proxy_loader.h"

#include <dlfcn.h>

#include <map>
#include <mutex>
#include <string>

#include "berberis/base/logging.h"
#include "berberis/base/tracing.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace berberis {

namespace {

bool LoadProxyLibrary(ProxyLibraryBuilder* builder,
                      const char* library_name,
                      const char* proxy_prefix) {
  // library_name is the soname of original library
  std::string proxy_name = proxy_prefix;
  proxy_name += library_name;

  void* proxy = dlopen(proxy_name.c_str(), RTLD_NOW | RTLD_LOCAL);
  if (!proxy) {
    TRACE("proxy library \"%s\" not found", proxy_name.c_str());
    return false;
  }

  using InitProxyLibraryFunc = void (*)(ProxyLibraryBuilder*);
  InitProxyLibraryFunc init =
      reinterpret_cast<InitProxyLibraryFunc>(dlsym(proxy, "InitProxyLibrary"));
  if (!init) {
    TRACE("failed to initialize proxy library \"%s\"", proxy_name.c_str());
    return false;
  }

  init(builder);

  TRACE("loaded proxy library \"%s\"", proxy_name.c_str());
  return true;
}

}  // namespace

void InterceptGuestSymbol(GuestAddr addr,
                          const char* library_name,
                          const char* name,
                          const char* proxy_prefix) {
  static std::mutex g_guard_mutex;
  std::lock_guard<std::mutex> guard(g_guard_mutex);

  using Libraries = std::map<std::string, ProxyLibraryBuilder>;
  static Libraries g_libraries;

  auto res = g_libraries.insert({library_name, {}});
  if (res.second && !LoadProxyLibrary(&res.first->second, library_name, proxy_prefix)) {
    LOG_ALWAYS_FATAL(
        "Unable to load library \"%s\" (upon using symbol \"%s\")", library_name, name);
  }

  res.first->second.InterceptSymbol(addr, name);
}

}  // namespace berberis
