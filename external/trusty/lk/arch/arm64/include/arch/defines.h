/*
 * Copyright (c) 2008 Travis Geiselbrecht
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

#define SHIFT_4K        (12)
#define SHIFT_16K       (14)
#define SHIFT_64K       (16)

/* arm specific stuff */
#ifdef ARM64_LARGE_PAGESIZE_64K
#define PAGE_SIZE_SHIFT (SHIFT_64K)
#elif ARM64_LARGE_PAGESIZE_16K
#define PAGE_SIZE_SHIFT (SHIFT_16K)
#else
#define PAGE_SIZE_SHIFT (SHIFT_4K)
#endif
#define USER_PAGE_SIZE_SHIFT SHIFT_4K

#define PAGE_SIZE (1UL << PAGE_SIZE_SHIFT)
#define USER_PAGE_SIZE (1UL << USER_PAGE_SIZE_SHIFT)

/* Determine the d-cache line size in bytes, unless already set by the build */
#ifndef CACHE_LINE
#if ARM64_CPU_CORTEX_A53 || ARM64_CPU_CORTEX_A57 || ARM64_CPU_CORTEX_A72
#define CACHE_LINE 64
#else
#define CACHE_LINE 32
#endif
#endif

#define ARM64_EXC_SP_EL1_BUF_SIZE (16)

#define ARM64_PHYSICAL_STACK_CANARY         (0x5A41A5425A43A544UL)
#define ARM64_PHYSICAL_STACK_CANARY_WORDS   (8)
#define ARM64_PHYSICAL_STACK_CANARY_ROTATE  (8)

#define MTE_GRANULE_SIZE (16)

/* Macro to remove Pointer Authentication Code ROP protection from a function */
#if defined(KERNEL_PAC_ENABLED) && defined(KERNEL_BTI_ENABLED)
#define __ARCH_NO_PAC __attribute__((target("branch-protection=bti")))
#else
#define __ARCH_NO_PAC __attribute__((target("branch-protection=none")))
#endif
