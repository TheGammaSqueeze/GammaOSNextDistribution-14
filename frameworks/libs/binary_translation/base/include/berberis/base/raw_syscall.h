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

#ifndef BERBERIS_BASE_RAW_SYSCALL_H_
#define BERBERIS_BASE_RAW_SYSCALL_H_

extern "C" long berberis_RawSyscallImpl(long number,
                                        long arg1,
                                        long arg2,
                                        long arg3,
                                        long arg4,
                                        long arg5,
                                        long arg6);

namespace berberis {

inline long RawSyscall(long number,
                       long arg1 = 0,
                       long arg2 = 0,
                       long arg3 = 0,
                       long arg4 = 0,
                       long arg5 = 0,
                       long arg6 = 0) {
  return berberis_RawSyscallImpl(number, arg1, arg2, arg3, arg4, arg5, arg6);
}

}  // namespace berberis

#endif  // BERBERIS_RAW_SYSCALL_H_
