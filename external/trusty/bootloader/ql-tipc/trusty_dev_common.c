/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <trusty/arm_ffa.h>
#include <trusty/sm_err.h>
#include <trusty/smc.h>
#include <trusty/smcall.h>
#include <trusty/trusty_dev.h>
#include <trusty/trusty_mem.h>
#include <trusty/util.h>

struct trusty_dev;

#define LOCAL_LOG 0

/*
 * Select RXTX map smc variant based on register size. Note that the FF-A spec
 * does not support passing a 64 bit paddr from a 32 bit client, so the
 * allocated buffer has to be below 4G if this is called from 32 bit code.
 */
#define SMC_FCZ_FFA_RXTX_MAP \
    ((sizeof(unsigned long) <= 4) ? SMC_FC_FFA_RXTX_MAP : SMC_FC64_FFA_RXTX_MAP)

static int32_t trusty_fast_call32(struct trusty_dev* dev,
                                  uint32_t smcnr,
                                  uint32_t a0,
                                  uint32_t a1,
                                  uint32_t a2) {
    trusty_assert(dev);
    trusty_assert(SMC_IS_FASTCALL(smcnr));

    return smc(smcnr, a0, a1, a2);
}

static unsigned long trusty_std_call_inner(struct trusty_dev* dev,
                                           unsigned long smcnr,
                                           unsigned long a0,
                                           unsigned long a1,
                                           unsigned long a2) {
    unsigned long ret;
    int retry = 5;

    trusty_debug("%s(0x%lx 0x%lx 0x%lx 0x%lx)\n", __func__, smcnr, a0, a1, a2);

    while (true) {
        ret = smc(smcnr, a0, a1, a2);
        while ((int32_t)ret == SM_ERR_FIQ_INTERRUPTED)
            ret = smc(SMC_SC_RESTART_FIQ, 0, 0, 0);
        if ((int)ret != SM_ERR_BUSY || !retry)
            break;

        trusty_debug("%s(0x%lx 0x%lx 0x%lx 0x%lx) returned busy, retry\n",
                     __func__, smcnr, a0, a1, a2);

        retry--;
    }

    return ret;
}

static unsigned long trusty_std_call_helper(struct trusty_dev* dev,
                                            unsigned long smcnr,
                                            unsigned long a0,
                                            unsigned long a1,
                                            unsigned long a2) {
    unsigned long ret;
    unsigned long irq_state;

    while (true) {
        trusty_local_irq_disable(&irq_state);
        ret = trusty_std_call_inner(dev, smcnr, a0, a1, a2);
        trusty_local_irq_restore(&irq_state);

        if ((int)ret != SM_ERR_BUSY)
            break;

        trusty_idle(dev, false);
    }

    return ret;
}

static int32_t trusty_std_call32(struct trusty_dev* dev,
                                 uint32_t smcnr,
                                 uint32_t a0,
                                 uint32_t a1,
                                 uint32_t a2) {
    int ret;

    trusty_assert(dev);
    trusty_assert(!SMC_IS_FASTCALL(smcnr));

    if (smcnr != SMC_SC_NOP) {
        trusty_lock(dev);
    }

    trusty_debug("%s(0x%x 0x%x 0x%x 0x%x) started\n", __func__, smcnr, a0, a1,
                 a2);

    ret = trusty_std_call_helper(dev, smcnr, a0, a1, a2);
    while (ret == SM_ERR_INTERRUPTED || ret == SM_ERR_CPU_IDLE) {
        trusty_debug("%s(0x%x 0x%x 0x%x 0x%x) interrupted\n", __func__, smcnr,
                     a0, a1, a2);
        if (ret == SM_ERR_CPU_IDLE) {
            trusty_idle(dev, false);
        }
        ret = trusty_std_call_helper(dev, SMC_SC_RESTART_LAST, 0, 0, 0);
    }

    trusty_debug("%s(0x%x 0x%x 0x%x 0x%x) returned 0x%x\n", __func__, smcnr, a0,
                 a1, a2, ret);

    if (smcnr != SMC_SC_NOP) {
        trusty_unlock(dev);
    }

    return ret;
}

static int trusty_call32_mem_buf_id(struct trusty_dev* dev,
                                    uint32_t smcnr,
                                    trusty_shared_mem_id_t buf_id,
                                    uint32_t size) {
    trusty_assert(dev);

    if (SMC_IS_FASTCALL(smcnr)) {
        return trusty_fast_call32(dev, smcnr, (uint32_t)buf_id,
                                  (uint32_t)(buf_id >> 32), size);
    } else {
        return trusty_std_call32(dev, smcnr, (uint32_t)buf_id,
                                 (uint32_t)(buf_id >> 32), size);
    }
}

int trusty_dev_init_ipc(struct trusty_dev* dev,
                        trusty_shared_mem_id_t buf_id,
                        uint32_t buf_size) {
    return trusty_call32_mem_buf_id(dev, SMC_SC_TRUSTY_IPC_CREATE_QL_DEV,
                                    buf_id, buf_size);
}

int trusty_dev_exec_ipc(struct trusty_dev* dev,
                        trusty_shared_mem_id_t buf_id,
                        uint32_t buf_size) {
    return trusty_call32_mem_buf_id(dev, SMC_SC_TRUSTY_IPC_HANDLE_QL_DEV_CMD,
                                    buf_id, buf_size);
}

int trusty_dev_exec_fc_ipc(struct trusty_dev* dev,
                           trusty_shared_mem_id_t buf_id,
                           uint32_t buf_size) {
    return trusty_call32_mem_buf_id(dev, SMC_FC_HANDLE_QL_TIPC_DEV_CMD, buf_id,
                                    buf_size);
}

int trusty_dev_shutdown_ipc(struct trusty_dev* dev,
                            trusty_shared_mem_id_t buf_id,
                            uint32_t buf_size) {
    return trusty_call32_mem_buf_id(dev, SMC_SC_TRUSTY_IPC_SHUTDOWN_QL_DEV,
                                    buf_id, buf_size);
}

static int trusty_init_api_version(struct trusty_dev* dev) {
    uint32_t api_version;

    api_version = trusty_fast_call32(dev, SMC_FC_API_VERSION,
                                     TRUSTY_API_VERSION_CURRENT, 0, 0);
    if (api_version == SM_ERR_UNDEFINED_SMC)
        api_version = 0;

    if (api_version > TRUSTY_API_VERSION_CURRENT) {
        trusty_error("unsupported trusty api version %u > %u\n", api_version,
                     TRUSTY_API_VERSION_CURRENT);
        return -1;
    }

    trusty_info("selected trusty api version: %u (requested %u)\n", api_version,
                TRUSTY_API_VERSION_CURRENT);

    dev->api_version = api_version;

    return 0;
}

int trusty_dev_init(struct trusty_dev* dev, void* priv_data) {
    int ret;
    struct smc_ret8 smc_ret;
    struct ns_mem_page_info tx_pinfo;
    struct ns_mem_page_info rx_pinfo;
    const size_t rxtx_page_count = 1;
    trusty_assert(dev);

    dev->priv_data = priv_data;
    dev->ffa_tx = NULL;
    ret = trusty_init_api_version(dev);
    if (ret) {
        return ret;
    }
    if (dev->api_version < TRUSTY_API_VERSION_MEM_OBJ) {
        return 0;
    }

    /* Get supported FF-A version and check if it is compatible */
    smc_ret = smc8(SMC_FC_FFA_VERSION, FFA_CURRENT_VERSION, 0, 0, 0, 0, 0, 0);
    if (FFA_VERSION_TO_MAJOR(smc_ret.r0) != FFA_CURRENT_VERSION_MAJOR) {
        /* TODO: support more than one (minor) version. */
        trusty_error("%s: unsupported FF-A version 0x%lx, expected 0x%x\n",
                     __func__, smc_ret.r0, FFA_CURRENT_VERSION);
        goto err_version;
    }

    /* Check that SMC_FC_FFA_MEM_SHARE is implemented */
    smc_ret = smc8(SMC_FC_FFA_FEATURES, SMC_FC_FFA_MEM_SHARE, 0, 0, 0, 0, 0, 0);
    if (smc_ret.r0 != SMC_FC_FFA_SUCCESS) {
        trusty_error(
                "%s: SMC_FC_FFA_FEATURES(SMC_FC_FFA_MEM_SHARE) failed 0x%lx 0x%lx 0x%lx\n",
                __func__, smc_ret.r0, smc_ret.r1, smc_ret.r2);
        goto err_features;
    }

    /*
     * Set FF-A endpoint IDs.
     *
     * Hardcode 0x8000 for the secure os.
     * TODO: Use FFA call or device tree to configure this dynamically
     */
    smc_ret = smc8(SMC_FC_FFA_ID_GET, 0, 0, 0, 0, 0, 0, 0);
    if (smc_ret.r0 != SMC_FC_FFA_SUCCESS) {
        trusty_error("%s: SMC_FC_FFA_ID_GET failed 0x%lx 0x%lx 0x%lx\n",
                     __func__, smc_ret.r0, smc_ret.r1, smc_ret.r2);
        goto err_id_get;
    }
    dev->ffa_local_id = smc_ret.r2;
    dev->ffa_remote_id = 0x8000;

    dev->ffa_tx = trusty_alloc_pages(rxtx_page_count);
    if (!dev->ffa_tx) {
        goto err_alloc_ffa_tx;
    }
    dev->ffa_rx = trusty_alloc_pages(rxtx_page_count);
    if (!dev->ffa_rx) {
        goto err_alloc_ffa_rx;
    }
    ret = trusty_encode_page_info(&tx_pinfo, dev->ffa_tx);
    if (ret) {
        goto err_encode_page_info;
    }
    ret = trusty_encode_page_info(&rx_pinfo, dev->ffa_rx);
    if (ret) {
        goto err_encode_page_info;
    }

    /*
     * TODO: check or pass memory attributes. The FF-A spec says the buffer has
     * to be cached, but we currently have callers that don't match this.
     */

    smc_ret = smc8(SMC_FCZ_FFA_RXTX_MAP, tx_pinfo.paddr, rx_pinfo.paddr,
                   rxtx_page_count, 0, 0, 0, 0);
    if (smc_ret.r0 != SMC_FC_FFA_SUCCESS) {
        trusty_error("%s: FFA_RXTX_MAP failed 0x%lx 0x%lx 0x%lx\n", __func__,
                     smc_ret.r0, smc_ret.r1, smc_ret.r2);
        goto err_rxtx_map;
    }

    if (ret) {
        goto err_setup_msg_buf;
    }
    return 0;

err_setup_msg_buf:
err_rxtx_map:
err_encode_page_info:
err_alloc_ffa_rx:
err_alloc_ffa_tx:
err_id_get:
err_features:
err_version:
    trusty_fatal("%s: init failed\n", __func__, ret);
}

int trusty_dev_shutdown(struct trusty_dev* dev) {
    trusty_assert(dev);

    if (dev->ffa_tx) {
        smc(SMC_FC_FFA_RXTX_UNMAP, 0, 0, 0);
    }
    dev->priv_data = NULL;
    return 0;
}

int trusty_dev_nop(struct trusty_dev* dev) {
    int ret = trusty_std_call32(dev, SMC_SC_NOP, 0, 0, 0);
    return ret == SM_ERR_NOP_DONE ? 0 : ret == SM_ERR_NOP_INTERRUPTED ? 1 : -1;
}

int trusty_dev_share_memory(struct trusty_dev* dev,
                            trusty_shared_mem_id_t* idp,
                            struct ns_mem_page_info* pinfo,
                            size_t page_count) {
    struct smc_ret8 smc_ret;
    struct ffa_mtd* mtd = dev->ffa_tx;
    size_t comp_mrd_offset = offsetof(struct ffa_mtd, emad[1]);
    struct ffa_comp_mrd* comp_mrd = dev->ffa_tx + comp_mrd_offset;
    struct ffa_cons_mrd* cons_mrd = comp_mrd->address_range_array;
    size_t tx_size = ((void*)cons_mrd - dev->ffa_tx) + sizeof(*cons_mrd);

    if (!dev->ffa_tx) {
        /*
         * If the trusty api version is before TRUSTY_API_VERSION_MEM_OBJ, fall
         * back to old api of passing the 64 bit paddr/attr value directly.
         */
        *idp = pinfo->attr;
        return 0;
    }

    trusty_memset(mtd, 0, tx_size);
    mtd->sender_id = dev->ffa_local_id;
    mtd->memory_region_attributes = pinfo->ffa_mem_attr;
    mtd->emad_count = 1;
    mtd->emad[0].mapd.endpoint_id = dev->ffa_remote_id;
    mtd->emad[0].mapd.memory_access_permissions = pinfo->ffa_mem_perm;
    mtd->emad[0].comp_mrd_offset = comp_mrd_offset;
    comp_mrd->total_page_count = page_count;
    comp_mrd->address_range_count = 1;
    cons_mrd->address = pinfo->paddr;
    cons_mrd->page_count = page_count;

    /*
     * Tell the SPM/Hypervisor to share the memory.
     */
    smc_ret = smc8(SMC_FC_FFA_MEM_SHARE, tx_size, tx_size, 0, 0, 0, 0, 0);
    if ((unsigned int)smc_ret.r0 != SMC_FC_FFA_SUCCESS) {
        trusty_error("%s: SMC_FC_FFA_MEM_SHARE failed 0x%lx 0x%lx 0x%lx\n",
                     __func__, smc_ret.r0, smc_ret.r1, smc_ret.r2);
        return -1;
    }

    *idp = smc_ret.r2;

    return 0;
}

int trusty_dev_reclaim_memory(struct trusty_dev* dev,
                              trusty_shared_mem_id_t id) {
    struct smc_ret8 smc_ret;

    if (!dev->ffa_tx) {
        /*
         * If the trusty api version is before TRUSTY_API_VERSION_MEM_OBJ, fall
         * back to old api.
         */
        return 0;
    }

    /*
     * Tell the SPM/Hypervisor to reclaim the memory. If the memory is still in
     * use this will fail.
     */
    smc_ret =
            smc8(SMC_FC_FFA_MEM_RECLAIM, (uint32_t)id, id >> 32, 0, 0, 0, 0, 0);
    if ((unsigned int)smc_ret.r0 != SMC_FC_FFA_SUCCESS) {
        trusty_error("%s: SMC_FC_FFA_MEM_RECLAIM failed 0x%lx 0x%lx 0x%lx\n",
                     __func__, smc_ret.r0, smc_ret.r1, smc_ret.r2);
        return -1;
    }

    return 0;
}
