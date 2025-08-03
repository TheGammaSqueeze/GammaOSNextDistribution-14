/*
 * Copyright (c) 2009 Corey Tabaka
 * Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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
#include <arch/ops.h>
#include <arch/x86/mmu.h>
#include <bits.h>
#include <lk/init.h>
#include <lk/macros.h>
#include <debug.h>

typedef void (*cache_func_type_t)(addr_t addr);

static cache_func_type_t cache_func;
static uint32_t cache_line_size;

static uint32_t x86_get_cache_line_size(void) {
    uint32_t unused;
    uint32_t ebx = 0;

    /* CPUID.01H:EBX[bit 19-15] indicates cache line size in bytes */
    cpuid(0x01, &unused, &ebx, &unused, &unused);

    return ((ebx >> 8) &0xff) * 8;
}

static inline void x86_wbinvd(void) {
    __asm__ __volatile__ ("wbinvd");
}

static inline void x86_clflush(addr_t addr) {
    __asm__ __volatile__ ("clflush %0"::"m"(addr));
}

static inline void x86_clflushopt(addr_t addr) {
    __asm__ __volatile__ ("clflushopt %0"::"m"(addr));
}

static inline void x86_clwb(addr_t addr) {
    __asm__ __volatile__ ("clwb %0"::"m"(addr));
}

static inline bool is_clflush_avail(void) {
    uint32_t edx;
    uint32_t unused;

    /* CPUID.01H:EDX[bit 19] indicates availablity of CLFLUSH */
    cpuid(X86_CPUID_VERSION_INFO, &unused, &unused, &unused, &edx);
    return !!BIT(edx, X86_CPUID_CLFLUSH_BIT);
}

static inline bool is_clflushopt_avail(void) {
    uint32_t ebx;
    uint32_t unused;

    /* CPUID.(EAX=7,ECX=0):EBX[bit 23] indicates availability of CLFLUSHOPT */
    cpuid_count(X86_CPUID_EXTEND_FEATURE, 0, &unused, &ebx, &unused, &unused);
    return !!BIT(ebx, X86_CPUID_CLFLUSHOPT_BIT);
}

static inline bool is_clwb_avail(void) {
    uint32_t ebx;
    uint32_t unused;

    /* CPUID.(EAX=7,ECX=0):EBX[bit 24] indicates availability of CLWB */
    cpuid_count(X86_CPUID_EXTEND_FEATURE, 0, &unused, &ebx, &unused, &unused);
    return !!BIT(ebx, X86_CPUID_CLWS_BIT);
}

static void x86_cache_operation_inner(addr_t start,
    size_t len,
    cache_func_type_t func) {

    addr_t ptr = round_down(start, cache_line_size);

    while (ptr < start + len) {
        func(ptr);

        ptr += cache_line_size;
    }
    mb();
}

void arch_clean_cache_range(addr_t start, size_t len)
{
    if (is_clwb_avail()) {
        x86_cache_operation_inner(start, len, x86_clwb);
    } else {
        arch_clean_invalidate_cache_range(start, len);
    }
}

void arch_clean_invalidate_cache_range(addr_t start, size_t len)
{
    if (NULL == cache_func) {
        x86_wbinvd();
    } else {
        x86_cache_operation_inner(start, len, cache_func);
    }
}

/* nothing to do to sync I & D cache on x86 */
void arch_sync_cache_range(addr_t start, size_t len) { }

void x86_arch_cache_init(uint level) {
    cache_line_size = x86_get_cache_line_size();

    if (is_clflushopt_avail()) {
        cache_func = x86_clflushopt;
    } else if (is_clflush_avail()) {
        cache_func = x86_clflush;
    } else {
        cache_func = NULL;
    }
}

LK_INIT_HOOK(x86_cache_init, x86_arch_cache_init, LK_INIT_LEVEL_ARCH_EARLY+1);
