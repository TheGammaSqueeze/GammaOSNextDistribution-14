/*
 * Copyright (c) 2019-2020 LK Trusty Authors. All Rights Reserved.
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

#define LOCAL_TRACE 0

#include <assert.h>
#include <err.h>
#include <interface/arm_ffa/arm_ffa.h>
#include <inttypes.h>
#include <kernel/mutex.h>
#include <kernel/vm.h>
#include <lib/arm_ffa/arm_ffa.h>
#include <lib/smc/smc.h>
#include <lk/init.h>
#include <lk/macros.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>

static bool arm_ffa_init_is_success = false;
static uint16_t ffa_local_id;
static size_t ffa_buf_size;
static void* ffa_tx;
static void* ffa_rx;
static bool supports_ns_bit = false;
static bool supports_rx_release = false;

static mutex_t ffa_rxtx_buffer_lock = MUTEX_INITIAL_VALUE(ffa_rxtx_buffer_lock);

bool arm_ffa_is_init(void) {
    return arm_ffa_init_is_success;
}

static status_t arm_ffa_call_id_get(uint16_t* id) {
    struct smc_ret8 smc_ret;

    smc_ret = smc8(SMC_FC_FFA_ID_GET, 0, 0, 0, 0, 0, 0, 0);

    switch (smc_ret.r0) {
    case SMC_FC_FFA_SUCCESS:
    case SMC_FC64_FFA_SUCCESS:
        if (smc_ret.r2 & ~0xFFFFUL) {
            TRACEF("Unexpected FFA_ID_GET result: %lx\n", smc_ret.r2);
            return ERR_NOT_VALID;
        }
        *id = (uint16_t)(smc_ret.r2 & 0xFFFF);
        return NO_ERROR;

    case SMC_FC_FFA_ERROR:
        if (smc_ret.r2 == (ulong)FFA_ERROR_NOT_SUPPORTED) {
            return ERR_NOT_SUPPORTED;
        } else {
            TRACEF("Unexpected FFA_ERROR: %lx\n", smc_ret.r2);
            return ERR_NOT_VALID;
        }

    default:
        TRACEF("Unexpected FFA SMC: %lx\n", smc_ret.r0);
        return ERR_NOT_VALID;
    }
}

static status_t arm_ffa_call_version(uint16_t major,
                                     uint16_t minor,
                                     uint16_t* major_ret,
                                     uint16_t* minor_ret) {
    struct smc_ret8 smc_ret;

    uint32_t version = FFA_VERSION(major, minor);
    /* Bit 31 must be cleared. */
    ASSERT(!(version >> 31));
    smc_ret = smc8(SMC_FC_FFA_VERSION, version, 0, 0, 0, 0, 0, 0);
    if (smc_ret.r0 == (ulong)FFA_ERROR_NOT_SUPPORTED) {
        return ERR_NOT_SUPPORTED;
    }
    *major_ret = FFA_VERSION_TO_MAJOR(smc_ret.r0);
    *minor_ret = FFA_VERSION_TO_MINOR(smc_ret.r0);

    return NO_ERROR;
}

/* TODO: When adding support for FFA version 1.1 feature ids should be added. */
static status_t arm_ffa_call_features(ulong id,
                                      bool* is_implemented,
                                      ffa_features2_t* features2,
                                      ffa_features3_t* features3) {
    struct smc_ret8 smc_ret;

    ASSERT(is_implemented);

    /*
     * According to the FF-A spec section "Discovery of NS bit usage",
     * NS_BIT is optionally set by a v1.0 SP such as Trusty, and must
     * be set by a v1.1+ SP. Here, we set it unconditionally for the
     * relevant feature.
     */
    bool request_ns_bit = (id == SMC_FC_FFA_MEM_RETRIEVE_REQ) ||
                          (id == SMC_FC64_FFA_MEM_RETRIEVE_REQ);
    smc_ret = smc8(SMC_FC_FFA_FEATURES, id,
                   request_ns_bit ? FFA_FEATURES2_MEM_RETRIEVE_REQ_NS_BIT : 0,
                   0, 0, 0, 0, 0);

    switch (smc_ret.r0) {
    case SMC_FC_FFA_SUCCESS:
    case SMC_FC64_FFA_SUCCESS:
        *is_implemented = true;
        if (features2) {
            *features2 = (ffa_features2_t)smc_ret.r2;
        }
        if (features3) {
            *features3 = (ffa_features3_t)smc_ret.r3;
        }
        return NO_ERROR;

    case SMC_FC_FFA_ERROR:
        if (smc_ret.r2 == (ulong)FFA_ERROR_NOT_SUPPORTED) {
            *is_implemented = false;
            return NO_ERROR;
        } else {
            TRACEF("Unexpected FFA_ERROR: %lx\n", smc_ret.r2);
            return ERR_NOT_VALID;
        }

    default:
        TRACEF("Unexpected FFA SMC: %lx\n", smc_ret.r0);
        return ERR_NOT_VALID;
    }
}

/*
 * Call with ffa_rxtx_buffer_lock acquired and the ffa_tx buffer already
 * populated with struct ffa_mtd. Transmit in a single fragment.
 */
static status_t arm_ffa_call_mem_retrieve_req(uint32_t* total_len,
                                              uint32_t* fragment_len) {
    struct smc_ret8 smc_ret;
    struct ffa_mtd* req = ffa_tx;
    size_t len;

    DEBUG_ASSERT(is_mutex_held(&ffa_rxtx_buffer_lock));

    len = offsetof(struct ffa_mtd, emad[0]) +
          req->emad_count * sizeof(struct ffa_emad);

    smc_ret = smc8(SMC_FC_FFA_MEM_RETRIEVE_REQ, len, len, 0, 0, 0, 0, 0);

    int error;
    switch (smc_ret.r0) {
    case SMC_FC_FFA_MEM_RETRIEVE_RESP:
        if (total_len) {
            *total_len = (uint32_t)smc_ret.r1;
        }
        if (fragment_len) {
            *fragment_len = (uint32_t)smc_ret.r2;
        }
        return NO_ERROR;
    case SMC_FC_FFA_ERROR:
        error = smc_ret.r2;
        switch (error) {
        case FFA_ERROR_NOT_SUPPORTED:
            return ERR_NOT_SUPPORTED;
        case FFA_ERROR_INVALID_PARAMETERS:
            return ERR_INVALID_ARGS;
        case FFA_ERROR_NO_MEMORY:
            return ERR_NO_MEMORY;
        case FFA_ERROR_DENIED:
            return ERR_BAD_STATE;
        case FFA_ERROR_ABORTED:
            return ERR_CANCELLED;
        default:
            TRACEF("Unknown error: 0x%x\n", error);
            return ERR_NOT_VALID;
        }
    default:
        return ERR_NOT_VALID;
    }
}

static status_t arm_ffa_call_mem_frag_rx(uint64_t handle,
                                         uint32_t offset,
                                         uint32_t* fragment_len) {
    struct smc_ret8 smc_ret;

    DEBUG_ASSERT(is_mutex_held(&ffa_rxtx_buffer_lock));

    smc_ret = smc8(SMC_FC_FFA_MEM_FRAG_RX, (uint32_t)handle, handle >> 32,
                   offset, 0, 0, 0, 0);

    /* FRAG_RX is followed by FRAG_TX on successful completion. */
    switch (smc_ret.r0) {
    case SMC_FC_FFA_MEM_FRAG_TX: {
        uint64_t handle_out = smc_ret.r1 + ((uint64_t)smc_ret.r2 << 32);
        if (handle != handle_out) {
            TRACEF("Handle for response doesn't match the request, %" PRId64
                   " != %" PRId64,
                   handle, handle_out);
            return ERR_NOT_VALID;
        }
        *fragment_len = smc_ret.r3;
        return NO_ERROR;
    }
    case SMC_FC_FFA_ERROR:
        switch ((int)smc_ret.r2) {
        case FFA_ERROR_NOT_SUPPORTED:
            return ERR_NOT_SUPPORTED;
        case FFA_ERROR_INVALID_PARAMETERS:
            return ERR_INVALID_ARGS;
        case FFA_ERROR_ABORTED:
            return ERR_CANCELLED;
        default:
            TRACEF("Unexpected error %d\n", (int)smc_ret.r2);
            return ERR_NOT_VALID;
        }
    default:
        TRACEF("Unexpected function id returned 0x%08lx\n", smc_ret.r0);
        return ERR_NOT_VALID;
    }
}

static status_t arm_ffa_call_mem_relinquish(
        uint64_t handle,
        uint32_t flags,
        uint32_t endpoint_count,
        const ffa_endpoint_id16_t* endpoints) {
    struct smc_ret8 smc_ret;
    struct ffa_mem_relinquish_descriptor* req = ffa_tx;

    if (!req) {
        TRACEF("ERROR: no FF-A tx buffer\n");
        return ERR_NOT_CONFIGURED;
    }
    ASSERT(endpoint_count <=
           (ffa_buf_size - sizeof(struct ffa_mem_relinquish_descriptor)) /
                   sizeof(ffa_endpoint_id16_t));

    mutex_acquire(&ffa_rxtx_buffer_lock);

    req->handle = handle;
    req->flags = flags;
    req->endpoint_count = endpoint_count;

    memcpy(req->endpoint_array, endpoints,
           endpoint_count * sizeof(ffa_endpoint_id16_t));

    smc_ret = smc8(SMC_FC_FFA_MEM_RELINQUISH, 0, 0, 0, 0, 0, 0, 0);

    mutex_release(&ffa_rxtx_buffer_lock);

    switch (smc_ret.r0) {
    case SMC_FC_FFA_SUCCESS:
    case SMC_FC64_FFA_SUCCESS:
        return NO_ERROR;

    case SMC_FC_FFA_ERROR:
        switch ((int)smc_ret.r2) {
        case FFA_ERROR_NOT_SUPPORTED:
            return ERR_NOT_SUPPORTED;
        case FFA_ERROR_INVALID_PARAMETERS:
            return ERR_INVALID_ARGS;
        case FFA_ERROR_NO_MEMORY:
            return ERR_NO_MEMORY;
        case FFA_ERROR_DENIED:
            return ERR_BAD_STATE;
        case FFA_ERROR_ABORTED:
            return ERR_CANCELLED;
        default:
            TRACEF("Unexpected FFA_ERROR: %lx\n", smc_ret.r2);
            return ERR_NOT_VALID;
        }
    default:
        TRACEF("Unexpected FFA SMC: %lx\n", smc_ret.r0);
        return ERR_NOT_VALID;
    }
}

static status_t arm_ffa_call_rxtx_map(paddr_t tx_paddr,
                                      paddr_t rx_paddr,
                                      size_t page_count) {
    struct smc_ret8 smc_ret;

    /* Page count specified in bits [0:5] */
    ASSERT(page_count);
    ASSERT(page_count < (1 << 6));

#if ARCH_ARM64
    smc_ret = smc8(SMC_FC64_FFA_RXTX_MAP, tx_paddr, rx_paddr, page_count, 0, 0,
                   0, 0);
#else
    smc_ret = smc8(SMC_FC_FFA_RXTX_MAP, tx_paddr, rx_paddr, page_count, 0, 0, 0,
                   0);
#endif
    switch (smc_ret.r0) {
    case SMC_FC_FFA_SUCCESS:
    case SMC_FC64_FFA_SUCCESS:
        return NO_ERROR;

    case SMC_FC_FFA_ERROR:
        switch ((int)smc_ret.r2) {
        case FFA_ERROR_NOT_SUPPORTED:
            return ERR_NOT_SUPPORTED;
        case FFA_ERROR_INVALID_PARAMETERS:
            return ERR_INVALID_ARGS;
        case FFA_ERROR_NO_MEMORY:
            return ERR_NO_MEMORY;
        case FFA_ERROR_DENIED:
            return ERR_ALREADY_EXISTS;
        default:
            TRACEF("Unexpected FFA_ERROR: %lx\n", smc_ret.r2);
            return ERR_NOT_VALID;
        }
    default:
        TRACEF("Unexpected FFA SMC: %lx\n", smc_ret.r0);
        return ERR_NOT_VALID;
    }
}

static status_t arm_ffa_call_rx_release(void) {
    struct smc_ret8 smc_ret;

    DEBUG_ASSERT(is_mutex_held(&ffa_rxtx_buffer_lock));

    smc_ret = smc8(SMC_FC_FFA_RX_RELEASE, 0, 0, 0, 0, 0, 0, 0);
    switch (smc_ret.r0) {
    case SMC_FC_FFA_SUCCESS:
    case SMC_FC64_FFA_SUCCESS:
        return NO_ERROR;

    case SMC_FC_FFA_ERROR:
        switch ((int)smc_ret.r2) {
        case FFA_ERROR_NOT_SUPPORTED:
            return ERR_NOT_SUPPORTED;
        case FFA_ERROR_DENIED:
            return ERR_BAD_STATE;
        default:
            return ERR_NOT_VALID;
        }
    default:
        return ERR_NOT_VALID;
    }
}

static status_t arm_ffa_rx_release_is_implemented(bool* is_implemented) {
    bool is_implemented_val;
    status_t res = arm_ffa_call_features(SMC_FC_FFA_RX_RELEASE,
                                         &is_implemented_val, NULL, NULL);
    if (res != NO_ERROR) {
        TRACEF("Failed to query for feature FFA_RX_RELEASE, err = %d\n", res);
        return res;
    }
    if (is_implemented) {
        *is_implemented = is_implemented_val;
    }
    return NO_ERROR;
}

static status_t arm_ffa_rxtx_map_is_implemented(bool* is_implemented,
                                                size_t* buf_size_log2) {
    ffa_features2_t features2;
    bool is_implemented_val = false;
    status_t res;

    ASSERT(is_implemented);
#if ARCH_ARM64
    res = arm_ffa_call_features(SMC_FC64_FFA_RXTX_MAP, &is_implemented_val,
                                &features2, NULL);
#else
    res = arm_ffa_call_features(SMC_FC_FFA_RXTX_MAP, &is_implemented_val,
                                &features2, NULL);
#endif
    if (res != NO_ERROR) {
        TRACEF("Failed to query for feature FFA_RXTX_MAP, err = %d\n", res);
        return res;
    }
    if (!is_implemented_val) {
        *is_implemented = false;
        return NO_ERROR;
    }
    if (buf_size_log2) {
        ulong buf_size_id = features2 & FFA_FEATURES2_RXTX_MAP_BUF_SIZE_MASK;
        switch (buf_size_id) {
        case FFA_FEATURES2_RXTX_MAP_BUF_SIZE_4K:
            *buf_size_log2 = 12;
            break;
        case FFA_FEATURES2_RXTX_MAP_BUF_SIZE_16K:
            *buf_size_log2 = 14;
            break;
        case FFA_FEATURES2_RXTX_MAP_BUF_SIZE_64K:
            *buf_size_log2 = 16;
            break;
        default:
            TRACEF("Unexpected rxtx buffer size identifier: %lx\n",
                   buf_size_id);
            return ERR_NOT_VALID;
        }
    }

    *is_implemented = true;
    return NO_ERROR;
}

static status_t arm_ffa_mem_retrieve_req_is_implemented(
        bool* is_implemented,
        bool* dyn_alloc_supp,
        bool* has_ns_bit,
        size_t* ref_count_num_bits) {
    ffa_features2_t features2;
    ffa_features3_t features3;
    bool is_implemented_val = false;
    status_t res;

    ASSERT(is_implemented);

    res = arm_ffa_call_features(SMC_FC_FFA_MEM_RETRIEVE_REQ,
                                &is_implemented_val, &features2, &features3);
    if (res != NO_ERROR) {
        TRACEF("Failed to query for feature FFA_MEM_RETRIEVE_REQ, err = %d\n",
               res);
        return res;
    }
    if (!is_implemented_val) {
        *is_implemented = false;
        return NO_ERROR;
    }
    if (dyn_alloc_supp) {
        *dyn_alloc_supp = !!(features2 & FFA_FEATURES2_MEM_DYNAMIC_BUFFER);
    }
    if (has_ns_bit) {
        *has_ns_bit = !!(features2 & FFA_FEATURES2_MEM_RETRIEVE_REQ_NS_BIT);
    }
    if (ref_count_num_bits) {
        *ref_count_num_bits =
                (features3 & FFA_FEATURES3_MEM_RETRIEVE_REQ_REFCOUNT_MASK) + 1;
    }
    *is_implemented = true;
    return NO_ERROR;
}

/* Helper function to set up the tx buffer with standard values
   before calling FFA_MEM_RETRIEVE_REQ. */
static void arm_ffa_populate_receive_req_tx_buffer(uint16_t sender_id,
                                                   uint64_t handle,
                                                   uint64_t tag) {
    struct ffa_mtd* req = ffa_tx;
    DEBUG_ASSERT(is_mutex_held(&ffa_rxtx_buffer_lock));

    memset(req, 0, sizeof(struct ffa_mtd));

    req->sender_id = sender_id;
    req->handle = handle;
    /* We must use the same tag as the one used by the sender to retrieve. */
    req->tag = tag;

    /*
     * We only support retrieving memory for ourselves for now.
     * TODO: Also support stream endpoints. Possibly more than one.
     */
    req->emad_count = 1;
    memset(req->emad, 0, sizeof(struct ffa_emad));
    req->emad[0].mapd.endpoint_id = ffa_local_id;
}

/* *desc_buffer is malloc'd and on success passes responsibility to free to
   the caller. Populate the tx buffer before calling. */
static status_t arm_ffa_mem_retrieve(uint16_t sender_id,
                                     uint64_t handle,
                                     uint32_t* len,
                                     uint32_t* fragment_len) {
    status_t res = NO_ERROR;

    DEBUG_ASSERT(is_mutex_held(&ffa_rxtx_buffer_lock));
    DEBUG_ASSERT(len);

    uint32_t len_out, fragment_len_out;
    res = arm_ffa_call_mem_retrieve_req(&len_out, &fragment_len_out);
    LTRACEF("total_len: %u, fragment_len: %u\n", len_out, fragment_len_out);
    if (res != NO_ERROR) {
        TRACEF("FF-A memory retrieve request failed, err = %d\n", res);
        return res;
    }
    if (fragment_len_out > len_out) {
        TRACEF("Fragment length larger than total length %u > %u\n",
               fragment_len_out, len_out);
        return ERR_IO;
    }

    /* Check that the first fragment fits in our buffer */
    if (fragment_len_out > ffa_buf_size) {
        TRACEF("Fragment length %u larger than buffer size\n",
               fragment_len_out);
        return ERR_IO;
    }

    if (fragment_len) {
        *fragment_len = fragment_len_out;
    }
    if (len) {
        *len = len_out;
    }

    return NO_ERROR;
}

status_t arm_ffa_mem_address_range_get(struct arm_ffa_mem_frag_info* frag_info,
                                       size_t index,
                                       paddr_t* addr,
                                       size_t* size) {
    uint32_t page_count;
    size_t frag_idx;

    DEBUG_ASSERT(frag_info);

    if (index < frag_info->start_index ||
        index >= frag_info->start_index + frag_info->count) {
        return ERR_OUT_OF_RANGE;
    }

    frag_idx = index - frag_info->start_index;

    page_count = frag_info->address_ranges[frag_idx].page_count;
    LTRACEF("address %p, page_count 0x%x\n",
            (void*)frag_info->address_ranges[frag_idx].address,
            frag_info->address_ranges[frag_idx].page_count);
    if (page_count < 1 || ((size_t)page_count > (SIZE_MAX / FFA_PAGE_SIZE))) {
        TRACEF("bad page count 0x%x at %zu\n", page_count, index);
        return ERR_IO;
    }

    if (addr) {
        *addr = (paddr_t)frag_info->address_ranges[frag_idx].address;
    }
    if (size) {
        *size = page_count * FFA_PAGE_SIZE;
    }

    return NO_ERROR;
}

status_t arm_ffa_mem_retrieve_start(uint16_t sender_id,
                                    uint64_t handle,
                                    uint64_t tag,
                                    uint32_t* address_range_count,
                                    uint* arch_mmu_flags,
                                    struct arm_ffa_mem_frag_info* frag_info) {
    status_t res;
    struct ffa_mtd* mtd;
    struct ffa_emad* emad;
    struct ffa_comp_mrd* comp_mrd;
    uint32_t computed_len;
    uint32_t header_size;

    uint32_t total_len;
    uint32_t fragment_len;

    DEBUG_ASSERT(frag_info);

    mutex_acquire(&ffa_rxtx_buffer_lock);
    arm_ffa_populate_receive_req_tx_buffer(sender_id, handle, tag);
    res = arm_ffa_mem_retrieve(sender_id, handle, &total_len, &fragment_len);

    if (res != NO_ERROR) {
        TRACEF("FF-A memory retrieve failed err=%d\n", res);
        return res;
    }

    if (fragment_len <
        offsetof(struct ffa_mtd, emad) + sizeof(struct ffa_emad)) {
        TRACEF("Fragment too short for memory transaction descriptor\n");
        return ERR_IO;
    }

    mtd = ffa_rx;
    emad = mtd->emad;

    /*
     * We don't retrieve the memory on behalf of anyone else, so we only
     * expect one receiver address range descriptor.
     */
    if (mtd->emad_count != 1) {
        TRACEF("unexpected response count %d != 1\n", mtd->emad_count);
        return ERR_IO;
    }

    LTRACEF("comp_mrd_offset: %u\n", emad->comp_mrd_offset);
    if (emad->comp_mrd_offset + sizeof(*comp_mrd) > fragment_len) {
        TRACEF("Fragment length %u too short for comp_mrd_offset %u\n",
               fragment_len, emad->comp_mrd_offset);
        return ERR_IO;
    }

    comp_mrd = ffa_rx + emad->comp_mrd_offset;

    uint32_t address_range_count_out = comp_mrd->address_range_count;
    frag_info->address_ranges = comp_mrd->address_range_array;
    LTRACEF("address_range_count: %u\n", address_range_count_out);

    computed_len = emad->comp_mrd_offset +
                   offsetof(struct ffa_comp_mrd, address_range_array) +
                   sizeof(struct ffa_cons_mrd) * comp_mrd->address_range_count;
    if (total_len != computed_len) {
        TRACEF("Reported length %u != computed length %u\n", total_len,
               computed_len);
        return ERR_IO;
    }

    header_size = emad->comp_mrd_offset +
                  offsetof(struct ffa_comp_mrd, address_range_array);
    frag_info->count =
            (fragment_len - header_size) / sizeof(struct ffa_cons_mrd);
    LTRACEF("Descriptors in fragment %u\n", frag_info->count);

    if (frag_info->count * sizeof(struct ffa_cons_mrd) + header_size !=
        fragment_len) {
        TRACEF("fragment length %u, contains partial descriptor\n",
               fragment_len);
        return ERR_IO;
    }

    frag_info->received_len = fragment_len;
    frag_info->start_index = 0;

    uint arch_mmu_flags_out = 0;

    switch (mtd->flags & FFA_MTD_FLAG_TYPE_MASK) {
    case FFA_MTD_FLAG_TYPE_SHARE_MEMORY:
        /*
         * If memory is shared, assume it is not safe to execute out of. This
         * specifically indicates that another party may have access to the
         * memory.
         */
        arch_mmu_flags_out |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
        break;
    case FFA_MTD_FLAG_TYPE_LEND_MEMORY:
        break;
    case FFA_MTD_FLAG_TYPE_DONATE_MEMORY:
        TRACEF("Unexpected donate memory transaction type is not supported\n");
        return ERR_NOT_IMPLEMENTED;
    default:
        TRACEF("Unknown memory transaction type: 0x%x\n", mtd->flags);
        return ERR_NOT_VALID;
    }

    switch (mtd->memory_region_attributes & ~FFA_MEM_ATTR_NONSECURE) {
    case FFA_MEM_ATTR_DEVICE_NGNRE:
        arch_mmu_flags_out |= ARCH_MMU_FLAG_UNCACHED_DEVICE;
        break;
    case FFA_MEM_ATTR_NORMAL_MEMORY_UNCACHED:
        arch_mmu_flags_out |= ARCH_MMU_FLAG_UNCACHED;
        break;
    case (FFA_MEM_ATTR_NORMAL_MEMORY_CACHED_WB | FFA_MEM_ATTR_INNER_SHAREABLE):
        arch_mmu_flags_out |= ARCH_MMU_FLAG_CACHED;
        break;
    default:
        TRACEF("Invalid memory attributes, 0x%x\n",
               mtd->memory_region_attributes);
        return ERR_NOT_VALID;
    }

    if (!(emad->mapd.memory_access_permissions & FFA_MEM_PERM_RW)) {
        arch_mmu_flags_out |= ARCH_MMU_FLAG_PERM_RO;
    }
    if (emad->mapd.memory_access_permissions & FFA_MEM_PERM_NX) {
        /*
         * Don't allow executable mappings if the stage 2 page tables don't
         * allow it. The hardware allows the stage 2 NX bit to only apply to
         * EL1, not EL0, but neither FF-A nor LK can currently express this, so
         * disallow both if FFA_MEM_PERM_NX is set.
         */
        arch_mmu_flags_out |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
    }

    if (!supports_ns_bit ||
        (mtd->memory_region_attributes & FFA_MEM_ATTR_NONSECURE)) {
        arch_mmu_flags_out |= ARCH_MMU_FLAG_NS;
        /* Regardless of origin, we don't want to execute out of NS memory. */
        arch_mmu_flags_out |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
    }

    if (arch_mmu_flags) {
        *arch_mmu_flags = arch_mmu_flags_out;
    }
    if (address_range_count) {
        *address_range_count = address_range_count_out;
    }

    return res;
}

/* This assumes that the fragment is completely composed of memory
   region descriptors (struct ffa_cons_mrd) */
status_t arm_ffa_mem_retrieve_next_frag(
        uint64_t handle,
        struct arm_ffa_mem_frag_info* frag_info) {
    status_t res;
    uint32_t fragment_len;

    mutex_acquire(&ffa_rxtx_buffer_lock);

    res = arm_ffa_call_mem_frag_rx(handle, frag_info->received_len,
                                   &fragment_len);

    if (res != NO_ERROR) {
        TRACEF("Failed to get memory retrieve fragment, err = %d\n", res);
        return res;
    }

    frag_info->received_len += fragment_len;
    frag_info->start_index += frag_info->count;

    frag_info->count = fragment_len / sizeof(struct ffa_cons_mrd);
    if (frag_info->count * sizeof(struct ffa_cons_mrd) != fragment_len) {
        TRACEF("fragment length %u, contains partial descriptor\n",
               fragment_len);
        return ERR_IO;
    }

    frag_info->address_ranges = ffa_rx;

    return NO_ERROR;
}

status_t arm_ffa_rx_release(void) {
    status_t res;
    ASSERT(is_mutex_held(&ffa_rxtx_buffer_lock));

    if (!supports_rx_release) {
        res = NO_ERROR;
    } else {
        res = arm_ffa_call_rx_release();
    }

    mutex_release(&ffa_rxtx_buffer_lock);

    if (res == ERR_NOT_SUPPORTED) {
        TRACEF("Tried to release rx buffer when the operation is not supported!\n");
    } else if (res != NO_ERROR) {
        TRACEF("Failed to release rx buffer, err = %d\n", res);
        return res;
    }
    return NO_ERROR;
}

status_t arm_ffa_mem_relinquish(uint64_t handle) {
    status_t res;

    /* As flags are set to 0 no request to zero the memory is made */
    res = arm_ffa_call_mem_relinquish(handle, 0, 1, &ffa_local_id);
    if (res != NO_ERROR) {
        TRACEF("Failed to relinquish memory region, err = %d\n", res);
    }

    return res;
}

static status_t arm_ffa_setup(void) {
    status_t res;
    uint16_t ver_major_ret;
    uint16_t ver_minor_ret;
    bool is_implemented;
    size_t buf_size_log2;
    size_t ref_count_num_bits;
    size_t arch_page_count;
    size_t ffa_page_count;
    size_t count;
    paddr_t tx_paddr;
    paddr_t rx_paddr;
    void* tx_vaddr;
    void* rx_vaddr;
    struct list_node page_list = LIST_INITIAL_VALUE(page_list);

    res = arm_ffa_call_version(FFA_CURRENT_VERSION_MAJOR,
                               FFA_CURRENT_VERSION_MINOR, &ver_major_ret,
                               &ver_minor_ret);
    if (res != NO_ERROR) {
        TRACEF("No compatible FF-A version found\n");
        return res;
    } else if (FFA_CURRENT_VERSION_MAJOR != ver_major_ret ||
               FFA_CURRENT_VERSION_MINOR > ver_minor_ret) {
        /* When trusty supports more FF-A versions downgrade may be possible */
        TRACEF("Incompatible FF-A interface version, %" PRIu16 ".%" PRIu16 "\n",
               ver_major_ret, ver_minor_ret);
        return ERR_NOT_SUPPORTED;
    }

    res = arm_ffa_call_id_get(&ffa_local_id);
    if (res != NO_ERROR) {
        TRACEF("Failed to get FF-A partition id (err=%d)\n", res);
        return res;
    }

    res = arm_ffa_rx_release_is_implemented(&is_implemented);
    if (res != NO_ERROR) {
        TRACEF("Error checking if FFA_RX_RELEASE is implemented (err=%d)\n",
               res);
        return res;
    }
    if (is_implemented) {
        supports_rx_release = true;
    } else {
        TRACEF("FFA_RX_RELEASE is not implemented\n");
    }

    res = arm_ffa_rxtx_map_is_implemented(&is_implemented, &buf_size_log2);
    if (res != NO_ERROR) {
        TRACEF("Error checking if FFA_RXTX_MAP is implemented (err=%d)\n", res);
        return res;
    }
    if (!is_implemented) {
        TRACEF("FFA_RXTX_MAP is not implemented\n");
        return ERR_NOT_SUPPORTED;
    }

    res = arm_ffa_mem_retrieve_req_is_implemented(
            &is_implemented, NULL, &supports_ns_bit, &ref_count_num_bits);
    if (res != NO_ERROR) {
        TRACEF("Error checking if FFA_MEM_RETRIEVE_REQ is implemented (err=%d)\n",
               res);
        return res;
    }
    if (!is_implemented) {
        TRACEF("FFA_MEM_RETRIEVE_REQ is not implemented\n");
        return ERR_NOT_SUPPORTED;
    }

    if (ref_count_num_bits < 64) {
        /*
         * Expect 64 bit reference count. If we don't have it, future calls to
         * SMC_FC_FFA_MEM_RETRIEVE_REQ can fail if we receive the same handle
         * multiple times. Warn about this, but don't return an error as we only
         * receive each handle once in the typical case.
         */
        TRACEF("Warning FFA_MEM_RETRIEVE_REQ does not have 64 bit reference count (%zu)\n",
               ref_count_num_bits);
    }

    ffa_buf_size = 1U << buf_size_log2;
    ASSERT((ffa_buf_size % FFA_PAGE_SIZE) == 0);

    arch_page_count = DIV_ROUND_UP(ffa_buf_size, PAGE_SIZE);
    ffa_page_count = ffa_buf_size / FFA_PAGE_SIZE;
    count = pmm_alloc_contiguous(arch_page_count, buf_size_log2, &tx_paddr,
                                 &page_list);
    if (count != arch_page_count) {
        TRACEF("Failed to allocate tx buffer %zx!=%zx\n", count,
               arch_page_count);
        res = ERR_NO_MEMORY;
        goto err_alloc_tx;
    }
    tx_vaddr = paddr_to_kvaddr(tx_paddr);
    ASSERT(tx_vaddr);

    count = pmm_alloc_contiguous(arch_page_count, buf_size_log2, &rx_paddr,
                                 &page_list);
    if (count != arch_page_count) {
        TRACEF("Failed to allocate rx buffer %zx!=%zx\n", count,
               arch_page_count);
        res = ERR_NO_MEMORY;
        goto err_alloc_rx;
    }
    rx_vaddr = paddr_to_kvaddr(rx_paddr);
    ASSERT(rx_vaddr);

    res = arm_ffa_call_rxtx_map(tx_paddr, rx_paddr, ffa_page_count);
    if (res != NO_ERROR) {
        TRACEF("Failed to map tx @ %p, rx @ %p, page count 0x%zx (err=%d)\n",
               (void*)tx_paddr, (void*)rx_paddr, ffa_page_count, res);
        goto err_rxtx_map;
    }

    ffa_tx = tx_vaddr;
    ffa_rx = rx_vaddr;

    return res;

err_rxtx_map:
err_alloc_rx:
    pmm_free(&page_list);
err_alloc_tx:
    /* pmm_alloc_contiguous leaves the page list unchanged on failure */

    return res;
}

static void arm_ffa_init(uint level) {
    status_t res;

    res = arm_ffa_setup();

    if (res == NO_ERROR) {
        arm_ffa_init_is_success = true;
    } else {
        TRACEF("Failed to initialize FF-A (err=%d)\n", res);
    }
}

LK_INIT_HOOK(arm_ffa_init, arm_ffa_init, LK_INIT_LEVEL_PLATFORM - 2);
