/*
 * Copyright (c) 2009 Corey Tabaka
 * Copyright (c) 2014 Travis Geiselbrecht
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

#include <compiler.h>

#ifndef ASSEMBLY

#include <arch/x86.h>
#include <arch/x86/mp.h>

/* override of some routines */

/*
 * According to ISDM vol2, description of STI instruction:
 *   If IF = 0, maskable hardware interrupts remain inhibited on the instruction
 *   boundary following an execution of STI.
 * To ensure interrupt can be recognized, NOP instruction follows immediately of
 * STI instruction.
 */
static inline void arch_enable_ints(void)
{
    CF;
    __asm__ volatile(
            "sti\n"
            "nop\n"
    );
}

static inline void arch_disable_ints(void)
{
    __asm__ volatile("cli");
    CF;
}

static inline bool arch_ints_disabled(void)
{
    x86_flags_t state;

    __asm__ volatile(
#if ARCH_X86_32
        "pushfl;"
        "popl %%eax"
#elif ARCH_X86_64
        "pushfq;"
        "popq %%rax"
#endif
        : "=a" (state)
        :: "memory");

    return !(state & (1<<9));
}

int _atomic_and(volatile int *ptr, int val);
int _atomic_or(volatile int *ptr, int val);

static inline int atomic_add(volatile int *ptr, int val)
{
    __asm__ volatile(
        "lock xaddl %[val], %[ptr];"
        : [val]"=a" (val)
        : "a" (val), [ptr]"m" (*ptr)
        : "memory"
    );

    return val;
}

static inline int atomic_swap(volatile int *ptr, int val)
{
    __asm__ volatile(
        "xchgl %[val], %[ptr];"
        : [val]"=a" (val)
        : "a" (val), [ptr]"m" (*ptr)
        : "memory"
    );

    return val;
}


static inline int atomic_and(volatile int *ptr, int val) { return _atomic_and(ptr, val); }
static inline int atomic_or(volatile int *ptr, int val) { return _atomic_or(ptr, val); }
static inline int atomic_cmpxchg(volatile int *ptr, int oldval, int newval)
{
    __atomic_compare_exchange_n(ptr, &oldval, newval, false, __ATOMIC_RELAXED, __ATOMIC_RELAXED);
    return oldval;
}

static inline uint32_t arch_cycle_count(void)
{
    uint32_t timestamp;
    rdtscl(timestamp);

    return timestamp;
}

static inline struct thread *get_current_thread(void)
{
    return (struct thread *)x86_read_gs_with_offset(CUR_THREAD_OFF);
}

static inline void set_current_thread(struct thread *t)
{
    x86_write_gs_with_offset(CUR_THREAD_OFF, (uint64_t)t);
}

static inline uint arch_curr_cpu_num(void)
{
    return 0;
}

/**
 * arch_extract_return_addr - this function simply passes through addr.
 *
 * Return: addr is returned without modification.
 */
static inline uintptr_t arch_extract_return_addr(uintptr_t addr) {
    return addr;
}

#define mb()        __asm__ volatile ("mfence":::"memory");
#define wmb()       __asm__ volatile ("sfence":::"memory");
#define rmb()       __asm__ volatile ("lfence":::"memory");

#define smp_mb()    mb()
#define smp_wmb()   wmb()
#define smp_rmb()   rmb()

#endif // !ASSEMBLY
