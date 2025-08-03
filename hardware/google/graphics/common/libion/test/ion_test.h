/*
 * Copyright (C) 2018 Samsung Electronics Co., Ltd.
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

#ifndef _UAPI_LINUX_ION_TEST_H
#define _UAPI_LINUX_ION_TEST_H

#include <linux/ioctl.h>
#include <linux/types.h>

struct ion_test_rw_data {
    __u64 ptr;
    __u64 offset;
    __u64 size;
    int   write;
    int   __padding;
};

enum {
    PHYS_CHUNK_IS_IDENTICAL_SIZE = 0,
    PHYS_IS_ORDERED_IN_ADDRESS   = 1,
    PHYS_IS_RESERVED             = 2,
    PHYS_IS_CMA                  = 3,
    PHYS_IS_ALIGNED              = 4,
};

struct ion_test_phys_data {
    __u32 cmd;
    __u32 arg;
    __u32 result;
};

#define ION_IOC_MAGIC		        'I'
#define ION_IOC_TEST_SET_FD         _IO(ION_IOC_MAGIC,   0xf0)
#define ION_IOC_TEST_DMA_MAPPING    _IOW(ION_IOC_MAGIC,  0xf1, struct ion_test_rw_data)
#define ION_IOC_TEST_KERNEL_MAPPING _IOW(ION_IOC_MAGIC,  0xf2, struct ion_test_rw_data)
#define ION_IOC_TEST_PHYS           _IOWR(ION_IOC_MAGIC, 0xf3, struct ion_test_phys_data)

#endif //_UAPI_LINUX_ION_TEST_H
