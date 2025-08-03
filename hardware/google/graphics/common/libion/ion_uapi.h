/*
 *  ion_uapi.h
 *
 *   Copyright 2018 Samsung Electronics Co., Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#ifndef __EXYNOS_ION_H__
#define __EXYNOS_ION_H__

#include <linux/ioctl.h>
#include <linux/types.h>

enum ion_heap_type {
    ION_HEAP_TYPE_SYSTEM,
    ION_HEAP_TYPE_SYSTEM_CONTIG,
    ION_HEAP_TYPE_CARVEOUT,
    ION_HEAP_TYPE_CHUNK,
    ION_HEAP_TYPE_DMA,
    ION_HEAP_TYPE_HPA
};

#define ION_NUM_HEAP_IDS (sizeof(unsigned int) * 8)

/*
 * ION LEGACY UAPI
 */
struct ion_allocation_data_legacy {
    size_t len;
    size_t align;
    unsigned int heap_id_mask;
    unsigned int flags;
    int handle;
};

struct ion_fd_data {
    int handle;
    int fd;
};

struct ion_fd_partial_data {
    int handle;
    int fd;
    off_t offset;
    size_t len;
};

struct ion_handle_data {
    int handle;
};

struct ion_custom_data {
    unsigned int cmd;
    unsigned long arg;
};

/*
 * ION MODERN UAPI
 */

struct ion_allocation_data_modern {
    __u64 len;
    __u32 heap_id_mask;
    __u32 flags;
    __u32 fd;
    __u32 unused;
};

#define MAX_HEAP_NAME 32

#define ION_HEAPDATA_FLAGS_DEFER_FREE		1
#define ION_HEAPDATA_FLAGS_ALLOW_PROTECTION	2
#define ION_HEAPDATA_FLAGS_UNTOUCHABLE		4

struct ion_heap_data {
    char name[MAX_HEAP_NAME];
    __u32 type;
    __u32 heap_id;
    __u32 size;       /* reserved 0 */
    __u32 heap_flags; /* reserved 1 */
    __u32 reserved2;
};

struct ion_heap_query {
    __u32 cnt;
    __u32 reserved0;
    __u64 heaps;
    __u32 reserved1;
    __u32 reserved2;
};

#define ION_IOC_MAGIC		'I'

#define ION_IOC_ALLOC_LEGACY _IOWR(ION_IOC_MAGIC, 0, struct ion_allocation_data_legacy)
#define ION_IOC_ALLOC_MODERN _IOWR(ION_IOC_MAGIC, 0, struct ion_allocation_data_modern)
#define ION_IOC_FREE         _IOWR(ION_IOC_MAGIC, 1, struct ion_handle_data)
#define ION_IOC_MAP          _IOWR(ION_IOC_MAGIC, 2, struct ion_fd_data)
#define ION_IOC_SHARE        _IOWR(ION_IOC_MAGIC, 4, struct ion_fd_data)
#define ION_IOC_IMPORT       _IOWR(ION_IOC_MAGIC, 5, struct ion_fd_data)
#define ION_IOC_SYNC         _IOWR(ION_IOC_MAGIC, 7, struct ion_fd_data)
#define ION_IOC_CUSTOM       _IOWR(ION_IOC_MAGIC, 6, struct ion_custom_data)
#define ION_IOC_HEAP_QUERY   _IOWR(ION_IOC_MAGIC, 8, struct ion_heap_query)
#define ION_IOC_SYNC_PARTIAL _IOWR(ION_IOC_MAGIC, 9, struct ion_fd_partial_data)


#endif /* __EXYNOS_ION_H__ */
