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
 * This trusty library module contains the SMC API for communication with
 * the Linux trusty-driver for shared memory registration/unregistration.
 * After a register request (SMC-Call) is received from the trusty-driver,
 * access to the shared-memory block, created by the trusty-driver, is
 * established. Likewise, when an unregister request is received, the
 * associated resources are release and the access is removed.
 *
 * This library module also provides the APIs to the trusty kernel for
 * exchanging various information in both directions. One such information
 * exchanged is the per-cpu trusty shadow-priorities.
 */

#include <err.h>
#include <kernel/spinlock.h>
#include <kernel/thread.h>
#include <kernel/vm.h>
#include <lib/extmem/extmem.h>
#include <lib/sm.h>
#include <lib/sm/sm_err.h>
#include <lib/sm/trusty_sched_share.h>
#include <platform.h>
#include <string.h>
#include <trace.h>

#define LOCAL_TRACE (0)

/* Trusty Shared Resources Info */
struct share_info {
    ext_mem_client_id_t client_id;
    ext_mem_obj_id_t buf_id;
    uint32_t cpu_count;
    uint32_t header_size;
    uint32_t percpu_data_size;
};

static struct share_info shareinfo = {0};

struct trusty_sched_shared_mem* sched_shared_mem = NULL;
static spin_lock_t sched_shared_datalock = SPIN_LOCK_INITIAL_VALUE;

static ext_mem_obj_id_t args_get_id(struct smc32_args* args) {
    return (((uint64_t)args->params[1] << 32) | args->params[0]);
}

static size_t args_get_sz(struct smc32_args* args) {
    return (size_t)args->params[2];
}

static long trusty_share_register(ext_mem_client_id_t client_id,
                                  ext_mem_obj_id_t buf_id,
                                  uint32_t buf_size) {
    struct trusty_sched_shared_mem* share_ptr;
    spin_lock_saved_state_t state;
    uint32_t needed_buf_sz;
    uint32_t nr_cpu;
    uint32_t hdr_sz;
    uint32_t percpu_data_sz;
    uint32_t struct_sz;
    void* va;
    status_t status;
    int retval = SM_ERR_INVALID_PARAMETERS;

    LTRACEF_LEVEL(5, "client_id=%llx,  buf_id= %llx,  buf_size=%d\n",
                  (unsigned long long)client_id, (unsigned long long)buf_id,
                  buf_size);

    status = ext_mem_map_obj_id(vmm_get_kernel_aspace(),
                                "trusty_sched_shared_mem", client_id, buf_id, 0,
                                0, buf_size, &va, PAGE_SIZE_SHIFT, 0,
                                ARCH_MMU_FLAG_PERM_NO_EXECUTE);
    if (status) {
        TRACEF("Error: ext_mem_map_obj_id() failed.\n");
        return SM_ERR_INTERNAL_FAILURE;
    }
    share_ptr = va;

    nr_cpu = READ_ONCE(share_ptr->cpu_count);
    hdr_sz = READ_ONCE(share_ptr->hdr_size);
    percpu_data_sz = READ_ONCE(share_ptr->percpu_data_size);

    struct_sz = sizeof(struct trusty_sched_shared_mem);
    if (hdr_sz < struct_sz) {
        TRACEF("Error: mismatched header-size=%d, struct-size=%d\n", hdr_sz,
               struct_sz);
        goto err_invalid_params;
    }
    LTRACEF_LEVEL(45, "header-size=%d, struct-size=%d\n", hdr_sz, struct_sz);

    struct_sz = sizeof(struct trusty_percpu_shared_data);
    if (percpu_data_sz < struct_sz) {
        TRACEF("Error: mismatched percpu-data-size=%d, struct-size=%d\n",
               percpu_data_sz, struct_sz);
        goto err_invalid_params;
    }
    LTRACEF_LEVEL(45, "percpu-data-size=%d, struct-size=%d\n", percpu_data_sz,
                  struct_sz);

    if (__builtin_mul_overflow(nr_cpu, percpu_data_sz, &needed_buf_sz)) {
        TRACEF("Error: multiply overflow while computing (nr_cpu * percpu_data_sz).\n");
        goto err_invalid_params;
    }
    if (__builtin_add_overflow(needed_buf_sz, hdr_sz, &needed_buf_sz)) {
        TRACEF("Error: Add overflow while adding header_size.\n");
        goto err_invalid_params;
    }

    if (needed_buf_sz > buf_size) {
        TRACEF("Error: Buffer size is not adequate.\n");
        goto err_invalid_params;
    }

    spin_lock_irqsave(&sched_shared_datalock, state);

    if (sched_shared_mem) {
        spin_unlock_irqrestore(&sched_shared_datalock, state);
        TRACEF("Error: Shared-Memory is already present from a previous call.\n");
        goto err_shared_mem_busy;
    }

    shareinfo.cpu_count = nr_cpu;
    shareinfo.header_size = hdr_sz;
    shareinfo.percpu_data_size = percpu_data_sz;
    shareinfo.client_id = client_id;
    shareinfo.buf_id = buf_id;
    sched_shared_mem = share_ptr;

    spin_unlock_irqrestore(&sched_shared_datalock, state);
    return 0;

err_shared_mem_busy:
err_invalid_params:
    status = vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)share_ptr);
    if (status) {
        TRACEF("Error: failed to free the allocated virtual-memory.\n");
        retval = SM_ERR_INTERNAL_FAILURE;
    }
    return retval;
}

static long trusty_share_unregister(ext_mem_client_id_t client_id,
                                    ext_mem_obj_id_t buf_id) {
    struct trusty_sched_shared_mem* share_ptr;
    spin_lock_saved_state_t state;
    status_t status;
    int retval = SM_ERR_INVALID_PARAMETERS;

    spin_lock_irqsave(&sched_shared_datalock, state);

    share_ptr = sched_shared_mem;
    if (!share_ptr) {
        spin_unlock_irqrestore(&sched_shared_datalock, state);
        TRACEF("Error: Trusty ShareInfo not setup by register call.\n");
        return retval;
    }

    if ((client_id != shareinfo.client_id) || (buf_id != shareinfo.buf_id)) {
        spin_unlock_irqrestore(&sched_shared_datalock, state);
        TRACEF("Error: invalid arguments.\n");
        return retval;
    }

    sched_shared_mem = NULL;
    memset(&shareinfo, 0, sizeof(struct share_info));

    spin_unlock_irqrestore(&sched_shared_datalock, state);
    retval = 0;

    status = vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)share_ptr);
    if (status) {
        TRACEF("Error: failed to free the allocated virtual-memory.\n");
        retval = SM_ERR_INTERNAL_FAILURE;
    }
    return retval;
}

long smc_trusty_sched_share_register(struct smc32_args* args) {
    ext_mem_client_id_t client_id = args->client_id;
    ext_mem_obj_id_t buf_id = args_get_id(args);
    uint32_t buf_size = args_get_sz(args);

    if (!IS_PAGE_ALIGNED(buf_size)) {
        TRACEF("Error: argument buffer-size is not page-aligned.\n");
        return SM_ERR_INVALID_PARAMETERS;
    }

    return trusty_share_register(client_id, buf_id, buf_size);
}

long smc_trusty_sched_share_unregister(struct smc32_args* args) {
    ext_mem_client_id_t client_id = args->client_id;
    ext_mem_obj_id_t buf_id = args_get_id(args);

    return trusty_share_unregister(client_id, buf_id);
}

static struct trusty_percpu_shared_data* get_percpu_share_ptr(uint32_t cpu_nr) {
    struct trusty_percpu_shared_data* percpu_data_ptr;
    unsigned char* tmp;

    DEBUG_ASSERT(cpu_nr < shareinfo.cpu_count);

    tmp = (unsigned char*)sched_shared_mem;
    tmp += shareinfo.header_size;
    tmp += cpu_nr * shareinfo.percpu_data_size;

    percpu_data_ptr = (struct trusty_percpu_shared_data*)tmp;
    return percpu_data_ptr;
}

/*
 * Following function is called from trusty kernel thread.c
 */
void platform_cpu_priority_set(uint32_t cpu_nr, uint32_t priority) {
    spin_lock_saved_state_t state;
    struct trusty_percpu_shared_data* percpu_data_ptr;
    uint32_t requested_priority;

    /* Ignore the set request by irq-ns-switch-* threads, which exclusively
     * run at the HIGHEST_PRIORITY. The problem is that the irq-ns-switch-*
     * threads run on behalf of linux (or any other normal world client os)
     * and are always the threads that return to linux (client os) while
     * trusty is busy,  but those are not the threads whose priority, the
     * linux side wants to know.
     */
    if (priority >= HIGHEST_PRIORITY) {
        return;
    }

    if (priority >= HIGH_PRIORITY) {
        requested_priority = TRUSTY_SHADOW_PRIORITY_HIGH;
    } else if (priority <= LOW_PRIORITY) {
        requested_priority = TRUSTY_SHADOW_PRIORITY_LOW;
    } else {
        requested_priority = TRUSTY_SHADOW_PRIORITY_NORMAL;
    }

    /*
     * if the shared-memory is established and the reuesting
     * cpu_nr is less than the max number of CPUs supported by
     * the Linux side, proceed with the value change.
     */
    spin_lock_irqsave(&sched_shared_datalock, state);
    if ((sched_shared_mem) && (cpu_nr < shareinfo.cpu_count)) {
        percpu_data_ptr = get_percpu_share_ptr(cpu_nr);
        percpu_data_ptr->ask_shadow_priority = requested_priority;
    }
    spin_unlock_irqrestore(&sched_shared_datalock, state);
}
