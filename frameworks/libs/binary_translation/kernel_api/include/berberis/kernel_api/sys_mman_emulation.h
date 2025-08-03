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

#ifndef BERBERIS_KERNEL_API_SYS_MMAN_EMULATION_H_
#define BERBERIS_KERNEL_API_SYS_MMAN_EMULATION_H_

#include <sys/types.h>

namespace berberis {

void* MmapForGuest(void* addr, size_t length, int prot, int flags, int fd, off64_t offset);

int MunmapForGuest(void* addr, size_t length);

int MprotectForGuest(void* addr, size_t length, int prot);

void* MremapForGuest(void* old_addr, size_t old_size, size_t new_size, int flags, void* new_addr);

}  // namespace berberis

#endif  // BERBERIS_KERNEL_API_SYS_MMAN_EMULATION_H_
