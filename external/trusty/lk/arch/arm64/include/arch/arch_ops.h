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
#pragma once

#ifndef ASSEMBLY

#include <arch/ops.h>
#include <assert.h>
#include <stdbool.h>
#include <compiler.h>
#include <reg.h>
#include <arch/arm64.h>

#define USE_GCC_ATOMICS 1
#define ENABLE_CYCLE_COUNTER 1

#if ARM_MERGE_FIQ_IRQ

#define DAIF_MASK_INTS "3"
#define DAIF_MASK_FIQS "0"

static inline void check_irq_fiq_state(unsigned long state)
{
    ASSERT(((state >> 6) & 1) == ((state >> 7) & 1));
}

#else

#define DAIF_MASK_INTS "2"
#define DAIF_MASK_FIQS "1"

static inline void check_irq_fiq_state(unsigned long state)
{
}

#endif

// override of some routines
static inline void arch_enable_ints(void)
{
    CF;
    __asm__ volatile("msr daifclr, #" DAIF_MASK_INTS ::: "memory");
}

static inline void arch_disable_ints(void)
{
    __asm__ volatile("msr daifset, #" DAIF_MASK_INTS ::: "memory");
    CF;
}

static inline bool arch_ints_disabled(void)
{
    unsigned long state;

    __asm__ volatile("mrs %0, daif" : "=r"(state));
    check_irq_fiq_state(state);
    state &= (1<<7);

    return !!state;
}

static inline void arch_enable_fiqs(void)
{
    CF;
    __asm__ volatile("msr daifclr, #" DAIF_MASK_FIQS ::: "memory");
}

static inline void arch_disable_fiqs(void)
{
    __asm__ volatile("msr daifset, #" DAIF_MASK_FIQS ::: "memory");
    CF;
}

// XXX
static inline bool arch_fiqs_disabled(void)
{
    unsigned long state;

    __asm__ volatile("mrs %0, daif" : "=r"(state));
    check_irq_fiq_state(state);
    state &= (1<<6);

    return !!state;
}

#define mb()        __asm__ volatile("dsb sy" : : : "memory")
#define rmb()       __asm__ volatile("dsb ld" : : : "memory")
#define wmb()       __asm__ volatile("dsb st" : : : "memory")

#ifdef WITH_SMP
#define smp_mb()    __asm__ volatile("dmb ish" : : : "memory")
#define smp_rmb()   __asm__ volatile("dmb ishld" : : : "memory")
#define smp_wmb()   __asm__ volatile("dmb ishst" : : : "memory")
#else
#define smp_mb()    CF
#define smp_wmb()   CF
#define smp_rmb()   CF
#endif

static inline int atomic_add(volatile int *ptr, int val)
{
#if USE_GCC_ATOMICS
    return __atomic_fetch_add(ptr, val, __ATOMIC_RELAXED);
#else
    int old;
    int temp;
    int test;

    do {
        __asm__ volatile(
            "ldrex  %[old], [%[ptr]]\n"
            "adds   %[temp], %[old], %[val]\n"
            "strex  %[test], %[temp], [%[ptr]]\n"
            : [old]"=&r" (old), [temp]"=&r" (temp), [test]"=&r" (test)
            : [ptr]"r" (ptr), [val]"r" (val)
            : "memory", "cc");

    } while (test != 0);

    return old;
#endif
}

static inline int atomic_or(volatile int *ptr, int val)
{
#if USE_GCC_ATOMICS
    return __atomic_fetch_or(ptr, val, __ATOMIC_RELAXED);
#else
    int old;
    int temp;
    int test;

    do {
        __asm__ volatile(
            "ldrex  %[old], [%[ptr]]\n"
            "orrs   %[temp], %[old], %[val]\n"
            "strex  %[test], %[temp], [%[ptr]]\n"
            : [old]"=&r" (old), [temp]"=&r" (temp), [test]"=&r" (test)
            : [ptr]"r" (ptr), [val]"r" (val)
            : "memory", "cc");

    } while (test != 0);

    return old;
#endif
}

static inline int atomic_and(volatile int *ptr, int val)
{
#if USE_GCC_ATOMICS
    return __atomic_fetch_and(ptr, val, __ATOMIC_RELAXED);
#else
    int old;
    int temp;
    int test;

    do {
        __asm__ volatile(
            "ldrex  %[old], [%[ptr]]\n"
            "ands   %[temp], %[old], %[val]\n"
            "strex  %[test], %[temp], [%[ptr]]\n"
            : [old]"=&r" (old), [temp]"=&r" (temp), [test]"=&r" (test)
            : [ptr]"r" (ptr), [val]"r" (val)
            : "memory", "cc");

    } while (test != 0);

    return old;
#endif
}

static inline int atomic_swap(volatile int *ptr, int val)
{
#if USE_GCC_ATOMICS
    return __atomic_exchange_n(ptr, val, __ATOMIC_RELAXED);
#else
    int old;
    int test;

    do {
        __asm__ volatile(
            "ldrex  %[old], [%[ptr]]\n"
            "strex  %[test], %[val], [%[ptr]]\n"
            : [old]"=&r" (old), [test]"=&r" (test)
            : [ptr]"r" (ptr), [val]"r" (val)
            : "memory");

    } while (test != 0);

    return old;
#endif
}

static inline int atomic_cmpxchg(volatile int *ptr, int oldval, int newval)
{
#if USE_GCC_ATOMICS
    __atomic_compare_exchange_n(ptr, &oldval, newval, false,
                                __ATOMIC_RELAXED, __ATOMIC_RELAXED);
    return oldval;
#else
    int old;
    int test;

    do {
        __asm__ volatile(
            "ldrex  %[old], [%[ptr]]\n"
            "mov    %[test], #0\n"
            "teq    %[old], %[oldval]\n"
#if ARM_ISA_ARMV7M
            "bne    0f\n"
            "strex  %[test], %[newval], [%[ptr]]\n"
            "0:\n"
#else
            "strexeq %[test], %[newval], [%[ptr]]\n"
#endif
            : [old]"=&r" (old), [test]"=&r" (test)
            : [ptr]"r" (ptr), [oldval]"Ir" (oldval), [newval]"r" (newval)
            : "cc");

    } while (test != 0);

    return old;
#endif
}

static inline uint32_t arch_cycle_count(void)
{
#if ARM_ISA_ARM7M
#if ENABLE_CYCLE_COUNTER
#define DWT_CYCCNT (0xE0001004)
    return *REG32(DWT_CYCCNT);
#else
    return 0;
#endif
#elif ARM_ISA_ARMV7
    uint32_t count;
    __asm__ volatile("mrc       p15, 0, %0, c9, c13, 0"
                     : "=r" (count)
                    );
    return count;
#else
//#warning no arch_cycle_count implementation
    return 0;
#endif
}

/* use the cpu local thread context pointer to store current_thread */
static inline struct thread *get_current_thread(void)
{
    return (struct thread *)ARM64_READ_SYSREG(tpidr_el1);
}

static inline void set_current_thread(struct thread *t)
{
    ARM64_WRITE_SYSREG(tpidr_el1, (uint64_t)t);
}

#if WITH_SMP
extern uint arm64_curr_cpu_num(void);
static inline uint arch_curr_cpu_num(void)
{
    return arm64_curr_cpu_num();
}
#else
static inline uint arch_curr_cpu_num(void)
{
    return 0;
}
#endif

/**
 * arch_extract_return_addr - process LR to remove any protections
 *
 * Return: The lr value with any PAC removed.
 *         If PAC is not supported, lr is returned without modification.
 */
static inline uintptr_t arch_extract_return_addr(uintptr_t lr) {
    if (arch_pac_address_supported()) {
        __asm__(".arch_extension pauth\n"
                "\txpaci %0" : "+r" (lr));
    }
    return lr;
}

/**
 * arm64_tagging_supported - indicate if the CPU supports the tagging feature
 *
 * This function returns true if the CPU supports memory tagging.
 * Note that even when the CPU supports memory tagging, reading and writing
 * memory tags in Trusty may be disabled, e.g. because tag access or tag space
 * was not enabled by the bootloader.
 * Use arch_tagging_enabled() to determine if memory tagging is actually
 * functional.
 *
 * Return: true if the CPU supports the tagging feature, false if not
 */
bool arm64_tagging_supported(void);

#endif // ASSEMBLY

