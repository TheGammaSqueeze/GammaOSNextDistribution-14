/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "epoll_emulation.h"

#include <linux/unistd.h>
#include <sys/epoll.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>

#include "berberis/base/bit_util.h"
#include "berberis/kernel_api/tracing.h"

#include "guest_types.h"

namespace berberis {

namespace {

// Note that we are doing somewhat dangerous operation here. We are converting array of epoll_event
// structs into array of Guest_epoll_event structs in place. This works because Guest_epoll_event
// is larger than epoll_event but contain the same data types, only alignment differs.
void ConvertHostEPollEventArrayToGuestInPlace(Guest_epoll_event* guest_events, int count) {
  auto host_events = reinterpret_cast<epoll_event*>(guest_events);

  // Handle negative count safely!
  while (count-- > 0) {
    // Use memmove to guarantee that there wouldn't any aliasing issues.
    //
    // Copy "data" first, "event" second because this guarantees that we always copy
    // data "down" and don't rely on padding.
    //
    // CHECK_FIELD_LAYOUT checks in epoll_emulation.h guarantee that offsetof "data"
    // is larger than offsetof "event" and that offsetof fields on host are not
    // larger than offsetof fields on guest.
    memmove(&guest_events[count].data, &host_events[count].data, sizeof(guest_events[count].data));
    memmove(&guest_events[count].events,
            &host_events[count].events,
            sizeof(guest_events[count].events));
  }
}

}  // namespace

long RunGuestSyscall___NR_epoll_ctl(long arg_1, long arg_2, long arg_3, long arg_4) {
  if (arg_4 == 0) {
    return syscall(__NR_epoll_ctl, arg_1, arg_2, arg_3, nullptr);
  }

  Guest_epoll_event* guest_event = bit_cast<Guest_epoll_event*>(arg_4);
  epoll_event host_event;
  host_event.events = guest_event->events;
  host_event.data.u64 = guest_event->data;
  return syscall(__NR_epoll_ctl, arg_1, arg_2, arg_3, &host_event);
}

long RunGuestSyscall___NR_epoll_pwait(long arg_1,
                                      long arg_2,
                                      long arg_3,
                                      long arg_4,
                                      long arg_5,
                                      long arg_6) {
  long res = syscall(__NR_epoll_pwait, arg_1, arg_2, arg_3, arg_4, arg_5, arg_6);
  if (res != -1 && arg_2 != 0) {
    ConvertHostEPollEventArrayToGuestInPlace(bit_cast<Guest_epoll_event*>(arg_2), arg_3);
  }
  return res;
}

long RunGuestSyscall___NR_epoll_pwait2(long, long, long, long, long, long) {
  KAPI_TRACE("unsupported syscall __NR_epoll_pwait2");
  errno = ENOSYS;
  return -1;
}

}  // namespace berberis
