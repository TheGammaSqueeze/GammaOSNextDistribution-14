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

#include "sigevent_emulation.h"

#include <cstring>

#include "berberis/runtime_primitives/guest_function_wrapper_impl.h"
#include "berberis/runtime_primitives/runtime_library.h"

namespace berberis {

namespace {

using NotifyFunc = void (*)(sigval);

}  // namespace

sigevent* ConvertGuestSigeventToHost(sigevent* guest_sigevent, sigevent* host_sigevent) {
  if (guest_sigevent == nullptr) {
    return nullptr;
  }
  if (guest_sigevent->sigev_notify != SIGEV_THREAD) {
    return guest_sigevent;
  }
  // Even though sigevent data structure is low-level and includes unions,
  // it can be safely copied with memcpy.
  memcpy(host_sigevent, guest_sigevent, sizeof(*host_sigevent));
  GuestAddr func = reinterpret_cast<GuestAddr>(guest_sigevent->sigev_notify_function);
  host_sigevent->sigev_notify_function = AsFuncPtr<NotifyFunc>(
      WrapGuestFunctionImpl(func, "vp", RunGuestCall, "sigev_notify_function"));
  return host_sigevent;
}

}  // namespace berberis
