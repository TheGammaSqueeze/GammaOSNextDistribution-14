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

#include "runtime_bridge.h"

#include <cerrno>
#include <csignal>
#include <cstdlib>

#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"
#include "berberis/base/config.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_signal.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/kernel_api/sys_mman_emulation.h"

#include "sigevent_emulation.h"

namespace berberis {

long RunGuestSyscall___NR_rt_sigaction(long sig_num_arg,
                                       long act_arg,
                                       long old_act_arg,
                                       long sigset_size_arg) {
  TRACE("'rt_sigaction' called for signal %ld", sig_num_arg);
  int sig_num = static_cast<int>(sig_num_arg);
  const Guest_sigaction* act = bit_cast<const Guest_sigaction*>(act_arg);
  Guest_sigaction* old_act = bit_cast<Guest_sigaction*>(old_act_arg);
  size_t sigset_size = bit_cast<size_t>(sigset_size_arg);

  if (sigset_size != sizeof(Guest_sigset_t)) {
    errno = EINVAL;
    return -1;
  }

  int error;
  if (SetGuestSignalHandler(sig_num, act, old_act, &error)) {
    return 0;
  }
  errno = error;
  return -1;
}

long RunGuestSyscall___NR_sigaltstack(long stack, long old_stack) {
  int error;
  if (GetCurrentGuestThread()->SigAltStack(
          bit_cast<const stack_t*>(stack), bit_cast<stack_t*>(old_stack), &error)) {
    return 0;
  }
  errno = error;
  return -1;
}

long RunGuestSyscall___NR_timer_create(long arg_1, long arg_2, long arg_3) {
  struct sigevent host_sigevent;
  return syscall(__NR_timer_create,
                 arg_1,
                 ConvertGuestSigeventToHost(bit_cast<struct sigevent*>(arg_2), &host_sigevent),
                 arg_3);
}

long RunGuestSyscall___NR_exit(long code) {
  ExitCurrentThread(code);
  return 0;
}

long RunGuestSyscall___NR_clone(long arg_1, long arg_2, long arg_3, long arg_4, long arg_5) {
  // NOTE: clone syscall argument ordering is architecture dependent.  This implementation assumes
  // CLONE_BACKWARDS is enabled (tls before child_tid), which is true for both x86 and RISC-V.
  return CloneGuestThread(GetCurrentGuestThread(), arg_1, arg_2, arg_3, arg_4, arg_5);
}

long RunGuestSyscall___NR_mmap(long arg_1,
                               long arg_2,
                               long arg_3,
                               long arg_4,
                               long arg_5,
                               long arg_6) {
  return bit_cast<long>(MmapForGuest(bit_cast<void*>(arg_1),         // addr
                                     bit_cast<size_t>(arg_2),        // length
                                     static_cast<int>(arg_3),        // prot
                                     static_cast<int>(arg_4),        // flags
                                     static_cast<int>(arg_5),        // fd
                                     static_cast<off64_t>(arg_6)));  // offset
}

long RunGuestSyscall___NR_mmap2(long arg_1,
                                long arg_2,
                                long arg_3,
                                long arg_4,
                                long arg_5,
                                long arg_6) {
  return bit_cast<long>(
      MmapForGuest(bit_cast<void*>(arg_1),                                  // addr
                   bit_cast<size_t>(arg_2),                                 // length
                   static_cast<int>(arg_3),                                 // prot
                   static_cast<int>(arg_4),                                 // flags
                   static_cast<int>(arg_5),                                 // fd
                   static_cast<off64_t>(arg_6) * config::kGuestPageSize));  // pgoffset to offset
}

long RunGuestSyscall___NR_munmap(long arg_1, long arg_2) {
  return static_cast<long>(MunmapForGuest(bit_cast<void*>(arg_1),     // addr
                                          bit_cast<size_t>(arg_2)));  // length
}

long RunGuestSyscall___NR_mprotect(long arg_1, long arg_2, long arg_3) {
  return static_cast<long>(MprotectForGuest(bit_cast<void*>(arg_1),     // addr
                                            bit_cast<size_t>(arg_2),    // length
                                            static_cast<int>(arg_3)));  // prot
}

long RunGuestSyscall___NR_mremap(long arg_1, long arg_2, long arg_3, long arg_4, long arg_5) {
  return bit_cast<long>(MremapForGuest(bit_cast<void*>(arg_1),    // old_addr
                                       bit_cast<size_t>(arg_2),   // old_size
                                       bit_cast<size_t>(arg_3),   // new_size
                                       static_cast<int>(arg_4),   // flags
                                       bit_cast<void*>(arg_5)));  // new_addr
}

}  // namespace berberis
