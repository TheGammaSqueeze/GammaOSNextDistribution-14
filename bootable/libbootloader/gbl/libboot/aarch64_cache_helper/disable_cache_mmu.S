/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 #include <arch.h>

.global disable_cache_mmu

/*
 * Cache flush and Cache/MMU disable should be done in order atomically to make sure RAM is up to
 * date.
 */
disable_cache_mmu:
    stp x29, x30, [sp, #-0x10]!
    /*
     * Call helper function "dcsw_op_all(DCCISW)" from ATF library to flush all cache.
     */
    mov x0, #DCCISW
    bl dcsw_op_all
    ic  iallu
    isb

    // Query current EL
    mrs     x0, CurrentEL
    cmp     x0, #(1 << 3)
    beq     disable_cache_mmu_el2

disable_cache_mmu_el1:
    mrs x1, sctlr_el1
    bic x1, x1, #SCTLR_M_BIT
    bic x1, x1, #SCTLR_C_BIT
    bic x1, x1, #SCTLR_I_BIT
    msr sctlr_el1, x1
    b finish

disable_cache_mmu_el2:
    mrs x1, sctlr_el2
    bic x1, x1, #SCTLR_M_BIT
    bic x1, x1, #SCTLR_C_BIT
    bic x1, x1, #SCTLR_I_BIT
    msr sctlr_el2, x1

finish:
    mov x0, #DCCISW
    bl dcsw_op_all
    ic  iallu
    isb
    tlbi vmalle1

    ldp x29, x30, [sp], #0x10
    ret
