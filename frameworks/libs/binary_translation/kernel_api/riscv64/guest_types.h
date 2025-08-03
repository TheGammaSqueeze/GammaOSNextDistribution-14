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

#ifndef BERBERIS_KERNEL_API_RISCV64_GUEST_TYPES_ARCH_H_
#define BERBERIS_KERNEL_API_RISCV64_GUEST_TYPES_ARCH_H_

#include <dirent.h>
#include <sys/epoll.h>
#include <sys/file.h>

#include "berberis/base/struct_check.h"

namespace berberis {

static_assert(EPOLL_CTL_ADD == 1);
static_assert(EPOLL_CTL_DEL == 2);
static_assert(EPOLL_CTL_MOD == 3);
static_assert(EPOLL_CLOEXEC == 02000000);

struct Guest_epoll_event {
  uint32_t events;
  alignas(64 / CHAR_BIT) uint64_t data;
};

// Verify precise layouts so ConvertHostEPollEventArrayToGuestInPlace is safe.
CHECK_STRUCT_LAYOUT(Guest_epoll_event, 128, 64);
CHECK_FIELD_LAYOUT(Guest_epoll_event, events, 0, 32);
CHECK_FIELD_LAYOUT(Guest_epoll_event, data, 64, 64);
static_assert(sizeof(epoll_event) <= sizeof(Guest_epoll_event));
static_assert(alignof(epoll_event) <= alignof(Guest_epoll_event));

CHECK_STRUCT_LAYOUT(dirent64, 2240, 64);
CHECK_FIELD_LAYOUT(dirent64, d_ino, 0, 64);
CHECK_FIELD_LAYOUT(dirent64, d_off, 64, 64);
CHECK_FIELD_LAYOUT(dirent64, d_reclen, 128, 16);
CHECK_FIELD_LAYOUT(dirent64, d_type, 144, 8);
CHECK_FIELD_LAYOUT(dirent64, d_name, 152, 2048);

using Guest_flock = struct flock;
using Guest_flock64 = struct flock64;

CHECK_STRUCT_LAYOUT(Guest_flock, 256, 64);
CHECK_FIELD_LAYOUT(Guest_flock, l_type, 0, 16);
CHECK_FIELD_LAYOUT(Guest_flock, l_whence, 16, 16);
CHECK_FIELD_LAYOUT(Guest_flock, l_start, 64, 64);
CHECK_FIELD_LAYOUT(Guest_flock, l_len, 128, 64);
CHECK_FIELD_LAYOUT(Guest_flock, l_pid, 192, 32);

CHECK_STRUCT_LAYOUT(Guest_flock64, 256, 64);
CHECK_FIELD_LAYOUT(Guest_flock64, l_type, 0, 16);
CHECK_FIELD_LAYOUT(Guest_flock64, l_whence, 16, 16);
CHECK_FIELD_LAYOUT(Guest_flock64, l_start, 64, 64);
CHECK_FIELD_LAYOUT(Guest_flock64, l_len, 128, 64);
CHECK_FIELD_LAYOUT(Guest_flock64, l_pid, 192, 32);

static_assert(F_GETLK64 == 5);
static_assert(F_SETLK64 == 6);
static_assert(F_SETLKW64 == 7);

CHECK_STRUCT_LAYOUT(timespec, 128, 64);
CHECK_FIELD_LAYOUT(timespec, tv_sec, 0, 64);
CHECK_FIELD_LAYOUT(timespec, tv_nsec, 64, 64);

struct Guest_stat {
  alignas(8) uint64_t st_dev;
  alignas(8) uint64_t st_ino;
  uint32_t st_mode;
  uint32_t st_nlink;
  uint32_t st_uid;
  uint32_t st_gid;
  alignas(8) uint64_t st_rdev;
  alignas(8) int64_t padding;
  alignas(8) int64_t st_size;
  uint32_t st_blksize;
  alignas(8) uint64_t st_blocks;
  timespec st_atim;
  timespec st_mtim;
  timespec st_ctim;
  uint64_t padding2;
};

CHECK_STRUCT_LAYOUT(Guest_stat, 1024, 64);
CHECK_FIELD_LAYOUT(Guest_stat, st_dev, 0, 64);
CHECK_FIELD_LAYOUT(Guest_stat, st_ino, 64, 64);
CHECK_FIELD_LAYOUT(Guest_stat, st_mode, 128, 32);
CHECK_FIELD_LAYOUT(Guest_stat, st_nlink, 160, 32);
CHECK_FIELD_LAYOUT(Guest_stat, st_uid, 192, 32);
CHECK_FIELD_LAYOUT(Guest_stat, st_gid, 224, 32);
CHECK_FIELD_LAYOUT(Guest_stat, st_rdev, 256, 64);
CHECK_FIELD_LAYOUT(Guest_stat, st_size, 384, 64);
CHECK_FIELD_LAYOUT(Guest_stat, st_blksize, 448, 32);
CHECK_FIELD_LAYOUT(Guest_stat, st_blocks, 512, 64);
CHECK_FIELD_LAYOUT(Guest_stat, st_atim, 576, 128);
CHECK_FIELD_LAYOUT(Guest_stat, st_mtim, 704, 128);
CHECK_FIELD_LAYOUT(Guest_stat, st_ctim, 832, 128);

}  // namespace berberis

#endif  // BERBERIS_KERNEL_API_RISCV64_GUEST_TYPES_ARCH_H_
