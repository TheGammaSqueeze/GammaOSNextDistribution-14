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

#include "system_properties_trampolines.h"

#include <sys/system_properties.h>

#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_abi/guest_params_arch.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

// int __system_property_foreach(void (*propfn)(const prop_info* pi, void*
// cookie), void* cookie);
void DoCustomTrampoline___system_property_foreach(HostCode /* callee */,
                                                  ProcessState* state) {
  auto [guest_prop_func, cookie] =
      GuestParamsValues<decltype(__system_property_foreach)>(state);
  auto prop_func =
      WrapGuestFunction(guest_prop_func, "__system_property_foreach-callback");
  auto&& [ret] =
      GuestReturnReference<decltype(__system_property_foreach)>(state);
  ret = __system_property_foreach(prop_func, cookie);
}

// void __system_property_read_callback(
//     const prop_info* pi,
//     void (*callback)(void* cookie, const char* name, const char* value,
//     uint32_t serial), void* cookie);
void DoCustomTrampoline___system_property_read_callback(HostCode /* callee */,
                                                        ProcessState* state) {
  auto [property_info, guest_prop_func, cookie] =
      GuestParamsValues<decltype(__system_property_read_callback)>(state);
  auto prop_func = WrapGuestFunction(
      guest_prop_func, "__system_property_read_callback-callback");
  __system_property_read_callback(property_info, prop_func, cookie);
}

}  // namespace berberis
