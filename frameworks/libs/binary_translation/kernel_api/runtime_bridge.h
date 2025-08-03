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

#ifndef BERBERIS_KERNEL_API_RUNTIME_BRIDGE_H_
#define BERBERIS_KERNEL_API_RUNTIME_BRIDGE_H_

namespace berberis {

long RunGuestSyscall___NR_rt_sigaction(long sig_num_arg,
                                       long act_arg,
                                       long old_act_arg,
                                       long sigset_size_arg);
long RunGuestSyscall___NR_sigaltstack(long stack, long old_stack);
long RunGuestSyscall___NR_timer_create(long arg_1, long arg_2, long arg_3);
long RunGuestSyscall___NR_exit(long arg);
long RunGuestSyscall___NR_clone(long arg_1, long arg_2, long arg_3, long arg_4, long arg_5);
long RunGuestSyscall___NR_mmap(long arg_1,
                               long arg_2,
                               long arg_3,
                               long arg_4,
                               long arg_5,
                               long arg_6);
long RunGuestSyscall___NR_mmap2(long arg_1,
                                long arg_2,
                                long arg_3,
                                long arg_4,
                                long arg_5,
                                long arg_6);
long RunGuestSyscall___NR_munmap(long arg_1, long arg_2);
long RunGuestSyscall___NR_mprotect(long arg_1, long arg_2, long arg_3);
long RunGuestSyscall___NR_mremap(long arg_1, long arg_2, long arg_3, long arg_4, long arg_5);

}  // namespace berberis

#endif  // BERBERIS_KERNEL_API_RUNTIME_BRIDGE_H_
