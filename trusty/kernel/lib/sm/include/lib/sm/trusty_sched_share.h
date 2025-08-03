/*
 * Copyright (c) 2022, Google, Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/*
 * This header file defines the SMC API and the shared data info between
 * Linux and Trusty.
 *
 * IMPORTANT: Copy of this header file is used in the Linux Trusty-Driver.
 * Linux header file: trusty/external/linux/drivers/trusty/trusy-sched-share.h
 * Please keep the copies in sync.
 */
#ifndef _TRUSTY_SCHED_SHARE_H_
#define _TRUSTY_SCHED_SHARE_H_

#include <lib/sm/smcall.h>

/*
 * trusty-shadow-priority valid values
 */
#define TRUSTY_SHADOW_PRIORITY_LOW 1
#define TRUSTY_SHADOW_PRIORITY_NORMAL 2
#define TRUSTY_SHADOW_PRIORITY_HIGH 3

/**
 * struct trusty_percpu_data - per-cpu trusty shared data
 * @cur_shadow_priority: set by Trusty-Driver/Linux
 * @ask_shadow_priority: set by Trusty Kernel
 */
struct trusty_percpu_shared_data {
    uint32_t cur_shadow_priority;
    uint32_t ask_shadow_priority;
};

/**
 * struct trusty_sched_shared_mem - information in the shared memory.
 * @hdr_size: size of the trusty_sched_shared data-structure.
 *            An instance of this data-structure is embedded at
 *            the very beginning of the shared-memory block.
 * @cpu_count: max number of available CPUs in the system.
 * @percpu_data_size: size of the per-cpu data structure.
 *                    The shared-memory block contains an array
 *                    of per-cpu instances of a data-structure that
 *                    can be indexed by cpu_id.
 * NOTE: At the end of this data-structure, additional space is
 * allocated to accommodate a variable length array as follows:
 * 'struct trusty_percpu_data percpu_data_table[]',
 * with 'cpu_count' as its number of elements.

 */
struct trusty_sched_shared_mem {
    uint32_t hdr_size;
    uint32_t cpu_count;
    uint32_t percpu_data_size;
    /* Additional space is allocated here, as noted above. */
};

#endif /* _TRUSTY_SCHED_SHARE_H_ */
