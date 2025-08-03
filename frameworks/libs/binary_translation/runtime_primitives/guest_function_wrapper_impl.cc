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

#include "berberis/runtime_primitives/guest_function_wrapper_impl.h"

#include <mutex>
#include <string>
#include <tuple>
#include <utility>

#include "berberis/assembler/machine_code.h"
#include "berberis/base/forever_map.h"
#include "berberis/base/logging.h"
#include "berberis/code_gen_lib/gen_wrapper.h"
#include "berberis/guest_abi/guest_type.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/code_pool.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/translation_cache.h"  // LookupGuestPC

namespace berberis {

namespace {

// Guest function wrappers are identified by guest function address, signature
// string and guest runner. The guest function address alone is not enough.
//
// Example: on RISC-V soft float guest, these functions are binary equal:
//   float foo(float x, float y) { return y; }
//   int bar(int x, int y) { return y; }
// And it is possible that guest compiler generates code only once and sets
// both foo and bar to it. However, on x86 hosts and RISC-V hard float guest,
// foo and bar need different wrappers, as floats and ints are passed and
// returned in differently.
//
// Example: imagine we wrap thread_func to run from pthread_create.
// In addition to running thread_func, the guest runner we provide also cleans
// up guest thread on exit. If we also want to call thread_func in regular way,
// we need another guest runner, otherwise we'll get an unexpected thread
// cleanup.
//
// TODO(b/232598137): implementation is inefficient, check if that matters!
class WrapperCache {
 public:
  static WrapperCache* GetInstance() {
    static WrapperCache g_wrapper_cache;
    return &g_wrapper_cache;
  }

  HostCode Find(GuestAddr pc, const char* signature, HostCode guest_runner) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = map_.find(std::make_tuple(pc, signature, guest_runner));
    if (it != map_.end()) {
      return it->second;
    }
    return nullptr;
  }

  // Another thread might have already inserted a wrapper for this key.
  // In this case, discard the new wrapper and return the existing one.
  HostCode Insert(GuestAddr pc, const char* signature, HostCode guest_runner, MachineCode* mc) {
    std::lock_guard<std::mutex> lock(mutex_);
    std::pair<WrapperMap::iterator, bool> res = map_.insert(
        std::make_pair(std::make_tuple(pc, std::string(signature), guest_runner), nullptr));
    if (res.second) {
      res.first->second = GetFunctionWrapperCodePoolInstance()->Add(mc);
    }
    return res.first->second;
  }

  GuestAddr SlowFindGuestAddrByWrapperAddr(void* wrapper_addr) {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& entry : map_) {
      if (entry.second == wrapper_addr) {
        // Return pc.
        return std::get<0>(entry.first);
      }
    }
    return 0;
  }

 private:
  using WrapperKey = std::tuple<GuestAddr, std::string, HostCode>;
  using WrapperMap = ForeverMap<WrapperKey, HostCode>;

  WrapperCache() = default;

  WrapperMap map_;
  mutable std::mutex mutex_;
};

IsAddressGuestExecutableFunc g_is_address_guest_executable_func = nullptr;

}  // namespace

void InitGuestFunctionWrapper(IsAddressGuestExecutableFunc func) {
  g_is_address_guest_executable_func = func;
}

HostCode WrapGuestFunctionImpl(GuestAddr pc,
                               const char* signature,
                               GuestRunnerFunc runner,
                               const char* name) {
  if (!pc) {
    return nullptr;
  }

  HostCode guest_runner = AsHostCode(runner);
  WrapperCache* wrapper_cache = WrapperCache::GetInstance();
  HostCode wrapper = wrapper_cache->Find(pc, signature, guest_runner);
  if (!wrapper) {
    // We can only wrap executable guest address! Even though execution will still fail, an early
    // check here helps a lot when debugging.
    // One special case is  wrapped host function (trampoline) that is passed back to the host.
    // It should still go through the guest function wrapper and call trampoline code.
    CHECK(g_is_address_guest_executable_func);
    if (!g_is_address_guest_executable_func(pc) &&
        !TranslationCache::GetInstance()->IsHostFunctionWrapped(pc)) {
      LOG_ALWAYS_FATAL("Trying to wrap non-executable guest address 0x%zx", pc);
    }
    MachineCode mc;
    GenWrapGuestFunction(&mc, pc, signature, guest_runner, name);
    wrapper = wrapper_cache->Insert(pc, signature, guest_runner, &mc);
  }
  return wrapper;
}

GuestAddr SlowFindGuestAddrByWrapperAddr(void* wrapper_addr) {
  return WrapperCache::GetInstance()->SlowFindGuestAddrByWrapperAddr(wrapper_addr);
}

}  // namespace berberis
