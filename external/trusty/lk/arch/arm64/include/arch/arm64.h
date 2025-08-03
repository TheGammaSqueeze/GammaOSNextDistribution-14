/*
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

#include <stdbool.h>
#include <sys/types.h>
#include <compiler.h>

__BEGIN_CDECLS

#define DSB __asm__ volatile("dsb sy" ::: "memory")
#define ISB __asm__ volatile("isb" ::: "memory")

#define STRINGIFY(x) #x
#define TOSTRING(x) STRINGIFY(x)

#define ARM64_READ_SYSREG(reg) \
({ \
    uint64_t _val; \
    __asm__ volatile("mrs %0," TOSTRING(reg) : "=r" (_val)); \
    _val; \
})

#define ARM64_WRITE_SYSREG_RAW(reg, val) \
({ \
    __asm__ volatile("msr " TOSTRING(reg) ", %0" :: "r" (val)); \
})

#define ARM64_WRITE_SYSREG(reg, val) \
({ \
    ARM64_WRITE_SYSREG_RAW(reg, val); \
    ISB; \
})

void arm64_context_switch(vaddr_t *old_sp, vaddr_t new_sp);

/* exception handling */
struct arm64_iframe_long {
    uint64_t r[29];
    uint64_t lr;
    uint64_t sp;
    uint64_t spsr;
    uint64_t fp;
    uint64_t elr;
};

struct arm64_iframe_short {
    uint64_t r[19];
    uint64_t lr;
    uint64_t sp;
    uint64_t spsr;
    uint64_t fp;
    uint64_t elr;
};

struct thread;

/*
 * This declaration is made to avoid issues with CFI while setting
 * vector base, with CFI enabled VBAR_EL1 was set wrong, so instead
 * of jumping to the correct exception entrypoint it was jumping into
 * the middle of an unrelated function
 */
extern uint32_t arm64_exception_base[];

void arm64_el3_to_el1(void);
void arm64_fpu_exception(struct arm64_iframe_long *iframe);
void arm64_fpu_save_state(struct thread *thread);

static inline void arm64_fpu_pre_context_switch(struct thread *thread)
{
    uint64_t cpacr = ARM64_READ_SYSREG(cpacr_el1);
    if ((cpacr >> 20) & 3) {
        arm64_fpu_save_state(thread);
        cpacr &= ~(3U << 20);
        ARM64_WRITE_SYSREG(cpacr_el1, cpacr);
    }
}

/* overridable syscall handler */
void arm64_syscall(struct arm64_iframe_long *iframe, bool is_64bit);

/* Macro to remove PAC protection from a function */
#if defined(KERNEL_PAC_ENABLED) && defined(KERNEL_BTI_ENABLED)
#define __ARM64_NO_PAC __attribute__((target("branch-protection=bti")))
#else
#define __ARM64_NO_PAC __attribute__((target("branch-protection=none")))
#endif

__END_CDECLS

