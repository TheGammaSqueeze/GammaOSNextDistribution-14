/*
 * Copyright (c) 2020, Google, Inc. All rights reserved
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

#include <kernel/vm.h>
#include <lib/trusty/handle.h>
#include <sys/types.h>

/**
 * memref_create_from_vmm_obj() - Bind a part of a vmm_obj to a handle
 * @obj:       @struct vmm_obj to base memref off of
 * @offset:    Offset into the object to start at
 * @size:      How big the memref should be
 * @mmap_prot: What mmap flags (MMAP_FLAG_PROT_*) the memref should support
 * @handle:    Output parameter for the handle
 *
 * Creates a user-mappable handle out of a @struct vmm_obj.
 *
 * Can fail if the permissions requested for the handle are more
 * permissive than what the object allows or if the offset/size are out of
 * range or unaligned.
 *
 * *handle will only be modified on success.
 *
 * Return: NO_ERROR on success, negative ERR_ value on failure
 */
status_t memref_create_from_vmm_obj(struct vmm_obj *obj,
                                    size_t offset,
                                    size_t size,
                                    uint32_t mmap_prot,
                                    struct handle** handle);

/**
 * memref_create_from_aspace() - Bind a part of an aspace to a handle
 * @aspace:    Address space to create the memref from
 * @vaddr:     Virtual address in the space the memref should start at
 * @size:      How big the memref should be
 * @mmap_prot: What mmap flags (MMAP_FLAG_PROT_*) the memref should support
 * @handle:    Output parameter for the handle
 *
 * Creates a user-mappable handle out of a portion of an address space.
 * Can fail if the region requested is not backed by a @struct vmm_obj,
 * multiple regions, the permissions requested for the handle are more
 * permissive than how the region is mapped in the address space, or the
 * vaddr or size are unaligned.
 *
 * *handle will only be modified on success.
 *
 * Return: NO_ERROR on success, negative ERR_ value on failure
 */
status_t memref_create_from_aspace(const vmm_aspace_t* aspace,
                                   vaddr_t vaddr,
                                   size_t size,
                                   uint32_t mmap_prot,
                                   struct handle** handle);

/**
 * memref_handle_to_vmm_obj() - Get the vmm_obj for enclosing memref
 * @handle: Handle to extract from
 *
 * Checks a handle to see if it is backed by a memref, and returns the
 * vmm_obj if it is.
 *
 * Return: Pointer to the vmm_obj for the enclosing memref if it is a memref,
 *         NULL otherwise.
 */
struct vmm_obj* memref_handle_to_vmm_obj(struct handle* handle);
