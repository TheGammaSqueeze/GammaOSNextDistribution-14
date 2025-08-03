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

#pragma once

#include <err.h>
#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

/* Kernel UAPI */
#define __u8 uint8_t
#define __u16 uint16_t
#define __u32 uint32_t
#define __u64 uint64_t

#define LOCAL_PAGE_SHIFT 12
#define BASE_MEM_MAP_TRACKING_HANDLE (3ul << LOCAL_PAGE_SHIFT)

/* IOCTL */
#define KBASE_IOCTL_TYPE 0x80

typedef struct mali_gpu_info {
    bool is_csf;
    uint32_t version;
    void* tracking_page;
} mali_gpu_info;

struct kbase_ioctl_version_check {
    __u16 major;
    __u16 minor;
};

struct kbase_ioctl_set_flags {
    __u32 create_flags;
};

struct kbase_ioctl_get_gpuprops {
    __u64 buffer;
    __u32 size;
    __u32 flags;
};

struct kbase_ioctl_get_ddk_version {
    __u64 version_buffer;
    __u32 size;
    __u32 padding;
};

#define KBASE_IOCTL_GET_DDK_VERSION _IOW(KBASE_IOCTL_TYPE, 13, struct kbase_ioctl_get_ddk_version)
#define KBASE_IOCTL_SET_FLAGS _IOW(KBASE_IOCTL_TYPE, 1, struct kbase_ioctl_set_flags)
#define KBASE_IOCTL_VERSION_CHECK_JM _IOWR(KBASE_IOCTL_TYPE, 0, struct kbase_ioctl_version_check)
#define KBASE_IOCTL_VERSION_CHECK_CSF _IOWR(KBASE_IOCTL_TYPE, 52, struct kbase_ioctl_version_check)

int32_t initialize_mali_gpu(const int32_t mali_fd, mali_gpu_info* gpu_info);
void teardown(mali_gpu_info* gpu_info);
