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

// Generic implementation that relies on guest arch-specific headers. This file must be compiled
// separately for each guest architecture.

#include "berberis/guest_os_primitives/guest_signal.h"

#include "berberis/base/host_signal.h"
#include "berberis/guest_abi/function_wrappers.h"  // WrapHostFunction
#include "berberis/guest_os_primitives/guest_signal_arch.h"
#include "berberis/guest_state/guest_addr.h"       // ToGuestAddr, ToHostAddr

namespace berberis {

GuestAddr WrapHostSigactionForGuest(const HostStructSigaction& host_sa) {
  if (host_sa.sa_flags & SA_SIGINFO) {
    WrapHostFunction(host_sa.sa_sigaction, "<host-sa_sigaction>");
    return ToGuestAddr(host_sa.sa_sigaction);
  } else if (host_sa.sa_handler == SIG_DFL) {
    return Guest_SIG_DFL;
  } else if (host_sa.sa_handler == SIG_IGN) {
    return Guest_SIG_IGN;
  } else if (host_sa.sa_handler == SIG_ERR) {
    return Guest_SIG_ERR;
  } else {
    WrapHostFunction(host_sa.sa_handler, "<host-sa_handler>");
    return ToGuestAddr(host_sa.sa_handler);
  }
}

}  // namespace berberis
