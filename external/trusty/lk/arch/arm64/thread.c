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

#include <arch/arm64/sregs.h>
#include <sys/types.h>
#include <string.h>
#include <stdlib.h>
#include <debug.h>
#include <trace.h>
#include <kernel/thread.h>
#include <arch/arm64.h>
#include <platform/random.h>

#define LOCAL_TRACE 0

struct context_switch_frame {
    vaddr_t lr;
    vaddr_t pad;                // Padding to keep frame size a multiple of
    vaddr_t tpidr_el0;          //  sp alignment requirements (16 bytes)
    vaddr_t tpidrro_el0;
    vaddr_t r18;
    vaddr_t r19;
    vaddr_t r20;
    vaddr_t r21;
    vaddr_t r22;
    vaddr_t r23;
    vaddr_t r24;
    vaddr_t r25;
    vaddr_t r26;
    vaddr_t r27;
    vaddr_t r28;
    vaddr_t r29;
};

extern void arm64_context_switch(addr_t *old_sp, addr_t new_sp);

static void initial_thread_func(void) __NO_RETURN;
static void initial_thread_func(void)
{
    int ret;

    thread_t *current_thread = get_current_thread();

    LTRACEF("initial_thread_func: thread %p calling %p with arg %p\n", current_thread, current_thread->entry, current_thread->arg);

    /* release the thread lock that was implicitly held across the reschedule */
    thread_unlock_ints_disabled();
    arch_enable_ints();

    ret = current_thread->entry(current_thread->arg);

    LTRACEF("initial_thread_func: thread %p exiting with %d\n", current_thread, ret);

    thread_exit(ret);
}

__ARCH_NO_PAC void arch_init_thread_initialize(struct thread *thread, uint cpu)
{
    extern uint8_t __stack_end[];
    size_t stack_size = ARCH_DEFAULT_STACK_SIZE;
    uint8_t *cpu_stack_end = __stack_end - stack_size * cpu;
    thread->stack = cpu_stack_end - stack_size;
    thread->stack_high = cpu_stack_end;
    thread->stack_size = stack_size;
#if KERNEL_SCS_ENABLED
    extern uint8_t __shadow_stack[];
    /* shadow stack grows up unlike the regular stack */
    thread->shadow_stack = __shadow_stack + DEFAULT_SHADOW_STACK_SIZE * cpu;
    thread->shadow_stack_size = DEFAULT_SHADOW_STACK_SIZE;
#endif

    if (arch_pac_address_supported()) {
        uint64_t sctlr_el1 = ARM64_READ_SYSREG(SCTLR_EL1);

        sctlr_el1 &= ~(SCTLR_EL1_ENIA | SCTLR_EL1_ENIB | SCTLR_EL1_ENDA | SCTLR_EL1_ENDB);

#if KERNEL_PAC_ENABLED
        /* Generate and load the instruction A key */
        platform_random_get_bytes((void *)thread->arch.packeys.apia,
                                  sizeof(thread->arch.packeys.apia));

        ARM64_WRITE_SYSREG_RAW(APIAKeyLo_EL1, thread->arch.packeys.apia[0]);
        ARM64_WRITE_SYSREG_RAW(APIAKeyHi_EL1, thread->arch.packeys.apia[1]);

        /*
         * Enable only the A key for use in EL1 and EL0.
         * PAuth instructions are NOPs for disabled keys.
         */
        sctlr_el1 |= SCTLR_EL1_ENIA;
#endif
        /* Ensure PACIxSP are valid BR jump targets in EL0 & EL1 */
        if (arch_bti_supported()) {
            sctlr_el1 &= ~(SCTLR_EL1_BT0 | SCTLR_EL1_BT1);
        }

        ARM64_WRITE_SYSREG_RAW(SCTLR_EL1, sctlr_el1);
        ISB;
    }
}

void arch_thread_initialize(thread_t *t)
{
    // create a default stack frame on the stack
    vaddr_t stack_top = (vaddr_t)t->stack + t->stack_size;

    // make sure the top of the stack is 16 byte aligned for EABI compliance
    stack_top = round_down(stack_top, 16);

    struct context_switch_frame *frame = (struct context_switch_frame *)(stack_top);
    frame--;

    // fill it in
    memset(frame, 0, sizeof(*frame));
    frame->lr = (vaddr_t)&initial_thread_func;

    // set the stack pointer
    t->arch.sp = (vaddr_t)frame;

    // set the shadow stack pointer
#if KERNEL_SCS_ENABLED
    frame->r18 = (vaddr_t)t->shadow_stack;
#endif
#if KERNEL_PAC_ENABLED
    /* Allocate PAC keys */
    if (arch_pac_address_supported()) {
        platform_random_get_bytes((void *)t->arch.packeys.apia,
                                  sizeof(t->arch.packeys.apia));
    }
#endif
}

/*
 * Switch context from one thread to another.
 * This function produces an non-PAC protected stack frame to enable switching.
 */
__ARCH_NO_PAC __NO_INLINE void arch_context_switch(thread_t *oldthread, thread_t *newthread)
{
    LTRACEF("old %p (%s), new %p (%s)\n", oldthread, oldthread->name, newthread, newthread->name);
    arm64_fpu_pre_context_switch(oldthread);
#if WITH_SMP
    DSB; /* broadcast tlb operations in case the thread moves to another cpu */
#endif
#if KERNEL_PAC_ENABLED
    /* Set new PAC key if supported */
    if (arch_pac_address_supported()) {
        ARM64_WRITE_SYSREG_RAW(APIAKeyLo_EL1, newthread->arch.packeys.apia[0]);
        ARM64_WRITE_SYSREG_RAW(APIAKeyHi_EL1, newthread->arch.packeys.apia[1]);
        ISB;
    }
#endif
    /*
     * Call the assembly helper.  As a tail-call, lr will point to this
     * function's caller, which due to __ARCH_NO_PAC and __NO_INLINE will not
     * have a PAC - if PAC is enabled.
     */
    arm64_context_switch(&oldthread->arch.sp, newthread->arch.sp);
}

void arch_dump_thread(thread_t *t)
{
    if (t->state != THREAD_RUNNING) {
        dprintf(INFO, "\tarch: ");
        dprintf(INFO, "sp 0x%lx\n", t->arch.sp);
    }
}
