/*
 * Copyright (c) 2008-2014 Travis Geiselbrecht
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
#ifndef __ARCH_OPS_H
#define __ARCH_OPS_H

#ifndef ASSEMBLY

#include <sys/types.h>
#include <stddef.h>
#include <stdbool.h>
#include <compiler.h>

__BEGIN_CDECLS

/* fast routines that most arches will implement inline */
static void arch_enable_ints(void);
static void arch_disable_ints(void);
static bool arch_ints_disabled(void);
static bool arch_in_int_handler(void);

static int atomic_swap(volatile int *ptr, int val);
static int atomic_add(volatile int *ptr, int val);
static int atomic_and(volatile int *ptr, int val);
static int atomic_or(volatile int *ptr, int val);

static uint32_t arch_cycle_count(void);

static uint arch_curr_cpu_num(void);

/* Use to align structures on cache lines to avoid cpu aliasing. */
#define __CPU_ALIGN __ALIGNED(CACHE_LINE)

#endif // !ASSEMBLY
#define ICACHE 1
#define DCACHE 2
#define UCACHE (ICACHE|DCACHE)
#ifndef ASSEMBLY

void arch_disable_cache(uint flags);
void arch_enable_cache(uint flags);

void arch_clean_cache_range(addr_t start, size_t len);
void arch_clean_invalidate_cache_range(addr_t start, size_t len);
void arch_invalidate_cache_range(addr_t start, size_t len);
void arch_sync_cache_range(addr_t start, size_t len);

void arch_idle(void);

/* Zero the specified memory as well as the corresponding tags */
void arch_clear_pages_and_tags(vaddr_t addr, size_t size);

/**
 * arch_tagging_enabled - indicate if memory tags can be read and written
 *
 * Return: true if tags can be written and read, false if not
 */
bool arch_tagging_enabled(void);

/**
 * arch_bti_supported - indicates if branch target identification is supported.
 *
 * Return: true if BTI is supported, false if not
 */
bool arch_bti_supported(void);

/**
 * arch_pac_address_supported - indicates if PAC for addresses is supported.
 *
 * Return: true if PAC is supported, false if not
 */
bool arch_pac_address_supported(void);

/**
 * arch_pac_exception_supported - indicates if AUT* & RETA* failures generate faults.
 *
 * Return: true if FPAC is supported, false if not
 */
bool arch_pac_exception_supported(void);

/**
 * arch_sve_supported - indicates if Scalable Vector Extension (SVE) is supported.
 *
 * Return: true if SVE is supported, false if not
 */
bool arch_sve_supported(void);

/*
 * arch_enable_sve - Enables Scalable Vector Extension (SVE).
 *
 * Return: Value of CPACR_EL1 before any change was made.
 */
uint64_t arch_enable_sve(void);

/*
 * arch_disable_sve - Disables Scalable Vector Extension (SVE).
 *
 * Return: Value of CPACR_EL1 before any change was made.
 */
uint64_t arch_disable_sve(void);

__END_CDECLS

#endif // !ASSEMBLY

#include <arch/arch_ops.h>

#endif
