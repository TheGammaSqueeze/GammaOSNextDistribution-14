/*
 * Copyright (c) 2013-2016, Google, Inc. All rights reserved
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

#include <arch/mmu.h>
#include <assert.h>
#include <debug.h>
#include <err.h>
#include <lib/sm.h>
#include <lib/sm/sm_err.h>
#include <lib/sm/smcall.h>
#include <lk/init.h>
#include <stddef.h>
#include <stdlib.h>
#include <sys/types.h>
#include <trace.h>

#include "tipc_dev_ql.h"
#include "trusty_virtio.h"

#define LOCAL_TRACE 0

/*
 * NS buffer helper function
 */
static status_t get_ns_mem_buf(struct smc32_args* args,
                               ext_mem_obj_id_t* pbuf_id,
                               ns_size_t* psz) {
    DEBUG_ASSERT(pbuf_id);
    DEBUG_ASSERT(psz);

    *pbuf_id = ((uint64_t)args->params[1] << 32) | args->params[0];
    *psz = (ns_size_t)args->params[2];
    return 0;
}

/*
 * Translate intermal errors to SMC errors
 */
static long to_smc_error(long err) {
    if (err >= 0)
        return err;

    switch (err) {
    case ERR_INVALID_ARGS:
        return SM_ERR_INVALID_PARAMETERS;

    case ERR_NOT_SUPPORTED:
        return SM_ERR_NOT_SUPPORTED;

    case ERR_NOT_ALLOWED:
        return SM_ERR_NOT_ALLOWED;

    default:
        return SM_ERR_INTERNAL_FAILURE;
    }
}

/*
 *  Handle fastcall Trusted OS SMC call function
 */
static long trusty_sm_fastcall(struct smc32_args* args) {
    long res;
    ns_size_t ns_sz;
    ext_mem_obj_id_t ns_buf_id;

    LTRACEF("Trusty SM service func %u args 0x%x 0x%x 0x%x\n",
            SMC_FUNCTION(args->smc_nr), args->params[0], args->params[1],
            args->params[2]);
    switch (args->smc_nr) {
    case SMC_FC_HANDLE_QL_TIPC_DEV_CMD:
        res = get_ns_mem_buf(args, &ns_buf_id, &ns_sz);
        if (res == NO_ERROR)
            res = ql_tipc_handle_cmd(args->client_id, ns_buf_id, ns_sz, true);
        break;

    default:
        LTRACEF("unknown func 0x%x\n", SMC_FUNCTION(args->smc_nr));
        res = ERR_NOT_SUPPORTED;
        break;
    }

    return to_smc_error(res);
}

/*
 *  Handle standard Trusted OS SMC call function
 */
static long trusty_sm_stdcall(struct smc32_args* args) {
    long res;
    ns_size_t ns_sz;
    ext_mem_obj_id_t ns_buf_id;
    uint ns_mmu_flags = ARCH_MMU_FLAG_PERM_NO_EXECUTE;

    LTRACEF("Trusty SM service func %u args 0x%x 0x%x 0x%x\n",
            SMC_FUNCTION(args->smc_nr), args->params[0], args->params[1],
            args->params[2]);

    switch (args->smc_nr) {
    case SMC_SC_VIRTIO_GET_DESCR:
        res = get_ns_mem_buf(args, &ns_buf_id, &ns_sz);
        if (res == NO_ERROR)
            res = virtio_get_description(args->client_id, ns_buf_id, ns_sz,
                                         ns_mmu_flags);
        break;

    case SMC_SC_VIRTIO_START:
        res = get_ns_mem_buf(args, &ns_buf_id, &ns_sz);
        if (res == NO_ERROR)
            res = virtio_start(args->client_id, ns_buf_id, ns_sz, ns_mmu_flags);
        break;

    case SMC_SC_VIRTIO_STOP:
        res = get_ns_mem_buf(args, &ns_buf_id, &ns_sz);
        if (res == NO_ERROR)
            res = virtio_stop(args->client_id, ns_buf_id, ns_sz, ns_mmu_flags);
        break;

    case SMC_SC_VDEV_RESET:
        res = virtio_device_reset(args->client_id, args->params[0]);
        break;

    case SMC_SC_VDEV_KICK_VQ:
        res = virtio_kick_vq(args->client_id, args->params[0], args->params[1]);
        break;

    case SMC_SC_CREATE_QL_TIPC_DEV:
        res = get_ns_mem_buf(args, &ns_buf_id, &ns_sz);
        if (res == NO_ERROR)
            res = ql_tipc_create_device(args->client_id, ns_buf_id, ns_sz,
                                        ns_mmu_flags);
        break;

    case SMC_SC_SHUTDOWN_QL_TIPC_DEV:
        res = get_ns_mem_buf(args, &ns_buf_id, &ns_sz);
        if (res == NO_ERROR)
            res = ql_tipc_shutdown_device(args->client_id, ns_buf_id);
        break;

    case SMC_SC_HANDLE_QL_TIPC_DEV_CMD:
        res = get_ns_mem_buf(args, &ns_buf_id, &ns_sz);
        if (res == NO_ERROR)
            res = ql_tipc_handle_cmd(args->client_id, ns_buf_id, ns_sz, false);
        break;

    default:
        LTRACEF("unknown func 0x%x\n", SMC_FUNCTION(args->smc_nr));
        res = ERR_NOT_SUPPORTED;
        break;
    }

    return to_smc_error(res);
}

/*
 *  Handle parameterized NOP Trusted OS SMC call function
 */
static long trusty_sm_nopcall(struct smc32_args* args) {
    long res;

    LTRACEF("Trusty SM service func %u args 0x%x 0x%x 0x%x\n",
            SMC_FUNCTION(args->smc_nr), args->params[0], args->params[1],
            args->params[2]);

    switch (args->params[0]) {
    case SMC_NC_VDEV_KICK_VQ:
        res = virtio_kick_vq(args->client_id, args->params[1], args->params[2]);
        break;

    default:
        LTRACEF("unknown func 0x%x\n", SMC_FUNCTION(args->smc_nr));
        res = ERR_NOT_SUPPORTED;
        break;
    }

    return to_smc_error(res);
}

static struct smc32_entity trusty_sm_entity = {
        .fastcall_handler = trusty_sm_fastcall,
        .stdcall_handler = trusty_sm_stdcall,
        .nopcall_handler = trusty_sm_nopcall,
};

static void trusty_sm_init(uint level) {
    int err;

    dprintf(SPEW, "Initializing Trusted OS SMC handler\n");

    err = sm_register_entity(SMC_ENTITY_TRUSTED_OS, &trusty_sm_entity);
    if (err) {
        TRACEF("WARNING: Cannot register SMC entity! (%d)\n", err);
    }
}
LK_INIT_HOOK(trusty_smcall, trusty_sm_init, LK_INIT_LEVEL_APPS);
