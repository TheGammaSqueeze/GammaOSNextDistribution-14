/*
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

#pragma once

#include <arch/ops.h>
#include <stdbool.h>

/**
 * arm_ffa_is_init() - Check whether this module initialized successfully.
 *
 * This should only be called once arm_ffa_init() is guaranteed to have
 * returned.
 *
 * Return: %true in case of success, %false otherwise.
 */
bool arm_ffa_is_init(void);

/**
 * arm_ffa_mem_relinquish() - Relinquish Trusty's access to a memory region.
 * @handle:        Handle of object to relinquish.
 *
 * Relinquish shared memory object id with SPM/Hypervisor. Allows the sender to
 * reclaim the memory (if it has not been retrieved by anyone else).
 */
status_t arm_ffa_mem_relinquish(uint64_t handle);

struct arm_ffa_cons_mrd;

/**
 * struct arm_ffa_mem_frag_info - A fragment of an FF-A shared memory object.
 * @received_len: Length of the fragment.
 * @start_index: Index of the address range array where to start reading.
 * @count: Number of elements in the address range buffer.
 * @address_ranges: The array of address ranges.
 */
struct arm_ffa_mem_frag_info {
    uint32_t received_len;
    size_t start_index;
    uint32_t count;
    struct ffa_cons_mrd* address_ranges;
};

/**
 * arm_ffa_mem_address_range_get() - Gets one address range from the buffer.
 * @buffer: Buffer that describes a part of an FF-A shared memory object.
 * @index: The index of the address range to retrieve.
 * @addr: [out] Start of the retrieved address range.
 * @size: [out] Size of the retrieved address range.
 *
 * Return: 0 on success, LK error code on failure.
 */
status_t arm_ffa_mem_address_range_get(struct arm_ffa_mem_frag_info* buffer,
                                       size_t index,
                                       paddr_t* addr,
                                       size_t* size);
/**
 * arm_ffa_mem_retrieve_start() - Retrieve a memory region from the
 *                                SPMC/hypervisor for access from Trusty.
 * @sender_id: Id of the memory owner.
 * @handle: The handle identifying the memory region in the transaction.
 * @tag: The tag identifying the transaction.
 * @address_range_count: [out] The number of address ranges retrieved.
 * @arch_mmu_flags: [out] The MMU flags of the received memory.
 * @frag_info: [out] The shared memory object fragment.
 *
 * Only expects one descriptor in the returned memory access descriptor array,
 * since we don't retrieve memory on behalf of anyone else.
 *
 * Grabs RXTX buffer lock. The lock must be subsequently released through
 * `arm_ffa_rx_release()`.
 *
 * Return: 0 on success, LK error code on failure.
 */
status_t arm_ffa_mem_retrieve_start(uint16_t sender_id,
                                    uint64_t handle,
                                    uint64_t tag,
                                    uint32_t* address_range_count,
                                    uint* arch_mmu_flags,
                                    struct arm_ffa_mem_frag_info* frag_info);
/**
 * arm_ffa_mem_retrieve_next_frag() - Performs an FF-A call to retrieve the
 *                                    next fragment of a shared memory object.
 * @handle: The handle of the FF-A memory object to retrieve.
 * @frag_info: [out] the retrieved fragment of the memory object.
 *
 * Return: 0 on success, LK error code on failure.
 */
status_t arm_ffa_mem_retrieve_next_frag(
        uint64_t handle,
        struct arm_ffa_mem_frag_info* frag_info);
/**
 * arm_ffa_rx_release() - Relinquish ownership of the RX buffer.
 *
 * Return: 0 on success, LK error code on failure.
 */
status_t arm_ffa_rx_release(void);
