/*
 * Copyright (c) 2020 LK Trusty Authors. All Rights Reserved.
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

#include <kernel/vm_obj.h>
#include <sys/types.h>

struct vmm_obj;
struct obj_ref;

/**
 * struct phys_mem_obj - Memory object for physical memory.
 * @vmm_obj:        VMM object.
 * @paddr:          Physical address where region starts.
 *                  Should be a multiple of PAGE_SIZE.
 * @size:           Number of bytes in region.
 *                  Should be a multiple of PAGE_SIZE.
 * @arch_mmu_flags: Memory type and required permission flags.
 * @destroy_fn:     Destructor function.
 */
struct phys_mem_obj {
    struct vmm_obj vmm_obj;
    paddr_t paddr;
    size_t size;
    uint arch_mmu_flags;
    void (*destroy_fn)(struct phys_mem_obj*);
};

/**
 * phys_mem_obj_dynamic_initialize - Initialize dynamically-allocated
 *                                   struct phys_mem_obj that is accompanied
 *                                   by a destructor function.
 * @obj:            Object to initialize.
 * @ref:            Initial reference.
 * @paddr:          Physical address where region starts.
 *                  Should be a multiple of PAGE_SIZE.
 * @size:           Number of bytes in region.
 *                  Should be a multiple of PAGE_SIZE.
 * @arch_mmu_flags: Memory type and required permission flags.
 * @destroy_fn:     Destructor function. Must not be %NULL.
 */
void phys_mem_obj_dynamic_initialize(struct phys_mem_obj* obj,
                                     struct obj_ref* ref,
                                     paddr_t paddr,
                                     size_t size,
                                     uint arch_mmu_flags,
                                     void (*destroy_fn)(struct phys_mem_obj*));

/**
 * phys_mem_obj_initialize - Initialize struct phys_mem_obj.
 * @obj:            Object to initialize.
 * @ref:            Initial reference.
 * @paddr:          Physical address where region starts.
 *                  Should be a multiple of PAGE_SIZE.
 * @size:           Number of bytes in region.
 *                  Should be a multiple of PAGE_SIZE.
 * @arch_mmu_flags: Memory type and required permission flags.
 */
void phys_mem_obj_initialize(struct phys_mem_obj* obj,
                             struct obj_ref* ref,
                             paddr_t paddr,
                             size_t size,
                             uint arch_mmu_flags);
