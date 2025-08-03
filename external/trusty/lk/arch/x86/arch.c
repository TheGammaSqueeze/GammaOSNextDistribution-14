/*
 * Copyright (c) 2009 Corey Tabaka
 * Copyright (c) 2015-2018 Intel Corporation
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

#include <debug.h>
#include <arch.h>
#include <arch/ops.h>
#include <arch/x86.h>
#include <arch/x86/mmu.h>
#include <arch/x86/mp.h>
#include <arch/x86/descriptor.h>
#include <arch/fpu.h>
#include <arch/mmu.h>
#include <assert.h>
#include <platform.h>
#include <sys/types.h>
#include <string.h>

/* early stack */
uint8_t _kstack[PAGE_SIZE] __ALIGNED(8);
uint8_t _tss_start[SMP_MAX_CPUS][PAGE_SIZE] __ALIGNED(8);
uint8_t _double_fault_stack[SMP_MAX_CPUS][PAGE_SIZE] __ALIGNED(8);

/* save a pointer to the multiboot information coming in from whoever called us */
/* make sure it lives in .data to avoid it being wiped out by bss clearing */
__SECTION(".data") void *_multiboot_info;

/* main tss */
tss_t system_tss[SMP_MAX_CPUS];
x86_per_cpu_states_t per_cpu_states[SMP_MAX_CPUS];

volatile int cpu_woken_up = 0;

static void init_per_cpu_state(uint cpu)
{
    x86_per_cpu_states_t states;

    /*
     * At this point, BSP has already set up current thread in global state,
     * init global states of AP(s) only.
     */
    if (0 != cpu) {
        states = per_cpu_states[cpu];

        states.cur_thread    = NULL;
        states.syscall_stack = 0;

        write_msr(X86_MSR_GS_BASE, (uint64_t)&states);
    }
}

static void set_tss_segment_percpu(void)
{
    uint64_t addr;

    tss_t *tss_base = get_tss_base();
    uint cpu_id = arch_curr_cpu_num();
    ASSERT(tss_base);

    addr = (uint64_t)&_tss_start[cpu_id + 1];

    /*
     * Care about privilege 0 only, since privilege 1 and 2 are unused.
     * This stack is used when inter-privilege changes from privilege
     * level 3 to level 0, for instance interrupt handling when interrupt
     * raised at level 3.
     */
    tss_base->rsp0 = addr;

    /* Syscall uses same stack with RSP0 in TSS */
    x86_write_gs_with_offset(SYSCALL_STACK_OFF, addr);

    /*
     * Exception and interrupt handlers share same stack with kernel context,
     * if kernel stack is corrupted or misused, exception handler will
     * continue to use this corrupted kernel stack, it hard to trace this
     * error especially in Page Fault handler.
     *
     * In order to ensure Page Fault handler does not trigger an infinite loop,
     * Interrupt Stack Table 1 (IST1) is dedicated to Double Fault handler.
     * With this dedicated double fault stack, a Page Fault while the stack
     * pointer is invalid, will trigger a double fault, that can then exit
     * cleanly.
     */
    addr = (uint64_t)&_double_fault_stack[cpu_id + 1];
    tss_base->ist1 = addr;
}

__WEAK void x86_syscall(void)
{
    panic("unhandled syscall\n");
}

static void setup_syscall_percpu(void)
{
    /*
     * SYSENTER instruction is used to execute a fast syscall to a level 0
     * system procedure or routine from level 3. According instruction
     * description about SYSENTER in ISDM VOL 2, if all conditions check
     * passed, then:
     *      RSP          <-  SYSENTER_ESP_MSR
     *      RIP          <-  SYSENTER_EIP_MSR
     *      CS.Selector  <-  SYSENTER_CS_MSR[15:0] & 0xFFFCH
     *      SS.Selector  <-  CS.Selector + 8
     */
    write_msr(SYSENTER_CS_MSR, CODE_64_SELECTOR);
    write_msr(SYSENTER_ESP_MSR, x86_read_gs_with_offset(SYSCALL_STACK_OFF));
    write_msr(SYSENTER_EIP_MSR, (uint64_t)(x86_syscall));
}

void arch_early_init(void)
{
    seg_sel_t sel = 0;
    uint cpu_id = 1;

    cpu_id = atomic_add(&cpu_woken_up, cpu_id);

    init_per_cpu_state(cpu_id);

    if (check_fsgsbase_avail()) {
        x86_set_cr4(x86_get_cr4() | X86_CR4_FSGSBASE);
    }

    sel = (seg_sel_t)(cpu_id << 4);
    sel += TSS_SELECTOR;

    /* enable caches here for now */
    clear_in_cr0(X86_CR0_NW | X86_CR0_CD);

    set_global_desc(sel,
            &system_tss[cpu_id],
            sizeof(tss_t),
            1,
            0,
            0,
            SEG_TYPE_TSS,
            0,
            0);
    x86_ltr(sel);

    x86_mmu_early_init();
    platform_init_mmu_mappings();
}

void arch_init(void)
{
    x86_mmu_init();

    set_tss_segment_percpu();
    setup_syscall_percpu();

#ifdef X86_WITH_FPU
    fpu_init();
#endif
}

void arch_chain_load(void *ep, ulong arg0, ulong arg1, ulong arg2, ulong arg3)
{
    PANIC_UNIMPLEMENTED;
}

void arch_enter_uspace(vaddr_t ep,
                       vaddr_t stack,
                       vaddr_t shadow_stack_base,
                       uint32_t flags,
                       ulong arg0)
{
    register uint64_t sp_usr = round_down(stack + 8, 16) - 8;
    register uint64_t entry = ep;
    register uint64_t code_seg = USER_CODE_64_SELECTOR | USER_RPL;
    register uint64_t data_seg = USER_DATA_64_SELECTOR | USER_RPL;
    register uint64_t usr_flags = USER_EFLAGS;

    //DEBUG_ASSERT(shadow_stack_base == 0);

    /*
     * Clear all General Purpose Registers except RDI, since RDI carries
     * parameter to user space.
     *
     * IRETQ instruction is used here to perform inter-privilege level return.
     * Input parameters 'flags' is ignored when entering level 3.
     *
     * LK kernel runs at IA-32e mode, when iretq instruction invoked,
     * processor performs:
     *
     * 1. IA-32e-MODE operation steps, pops RIP/CS/tempRFLAGS:
     *      RIP          <- POP()       --  entry
     *      CS.Selector  <- POP()       --  code_seg
     *      tempRFLAGS   <- POP()       --  usr_flags
     * 2. Since CS.RPL(3) > CPL(0), then goto return-to-outer-privilege-level:
     *      RSP          <- POP()       --  sp_usr
     *      SS           <- POP()       --  data_seg
     *      RFLAGS       <- tempRFLAGS
     *      CPL          <- CS.RPL
     *
     * After IRETQ executed, processor runs at RIP in 64-bit level 3.
     *
     * More details please refer "IRET/IRETD -- Interrupt Return" in Intel
     * ISDM VOL2 <Instruction Set Reference>.
     */
    __asm__ __volatile__ (
            "pushq %0   \n"
            "pushq %1   \n"
            "pushq %2   \n"
            "pushq %3   \n"
            "pushq %4   \n"
            "pushq %5   \n"
            "swapgs \n"
            "xorq %%r15, %%r15 \n"
            "xorq %%r14, %%r14 \n"
            "xorq %%r13, %%r13 \n"
            "xorq %%r12, %%r12 \n"
            "xorq %%r11, %%r11 \n"
            "xorq %%r10, %%r10 \n"
            "xorq %%r9, %%r9 \n"
            "xorq %%r8, %%r8 \n"
            "xorq %%rbp, %%rbp \n"
            "xorq %%rdx, %%rdx \n"
            "xorq %%rcx, %%rcx \n"
            "xorq %%rbx, %%rbx \n"
            "xorq %%rax, %%rax \n"
            "xorq %%rsi, %%rsi \n"
            "popq %%rdi \n"
            "iretq"
            :
            :"r" (data_seg), "r" (sp_usr), "r" (usr_flags),
             "r" (code_seg), "r"(entry), "r" (arg0));

    __UNREACHABLE;
}

void arch_set_user_tls(vaddr_t tls_ptr)
{
    thread_t *cur_thread = get_current_thread();

    cur_thread->arch.fs_base = tls_ptr;
    write_msr(X86_MSR_FS_BASE, tls_ptr);
}
