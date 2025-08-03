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
#include <trace.h>
#include <arch/x86.h>
#include <arch/x86/exceptions.h>
#include <arch/fpu.h>
#include <kernel/thread.h>
#include <lib/trusty/trusty_app.h>
#include <platform.h>
#include <inttypes.h>

struct fault_handler_table_entry {
    int64_t rip;
    int64_t fault_handler;
};

extern struct fault_handler_table_entry __fault_handler_table_start[];
extern struct fault_handler_table_entry __fault_handler_table_end[];

/**
 * prel_to_abs_u64() - Convert a position-relative value to an absolute.
 * @ptr: Pointer to a 64-bit position-relative value.
 * @result: Pointer to the location for the result.
 *
 * Return: %true in case of success, %false for overflow.
 */
static inline bool prel_to_abs_u64(const int64_t* ptr, uint64_t* result) {
    return !__builtin_add_overflow((uintptr_t)ptr, *ptr, result);
}

static bool check_fault_handler_table(x86_iframe_t *frame)
{
    struct fault_handler_table_entry *fault_handler;

    for (fault_handler = __fault_handler_table_start;
            fault_handler < __fault_handler_table_end;
            fault_handler++) {
        uint64_t addr;
        if (!prel_to_abs_u64(&fault_handler->rip, &addr)) {
            /* Invalid entry, ignore it */
            continue;
        }
        if (addr == frame->ip) {
            if (!prel_to_abs_u64(&fault_handler->fault_handler, &addr)) {
                /*
                 * An entry with an invalid handler address. We don't expect
                 * another entry with the same pc, so we break out of
                 * the loop early.
                 */
                return false;
            }

            frame->ip = addr;
            return true;
        }
    }
    return false;
}

extern enum handler_return platform_irq(x86_iframe_t *frame);

static void dump_fault_frame(x86_iframe_t *frame)
{
#if ARCH_X86_32
    dprintf(CRITICAL, " CS:     %04x EIP: %08x EFL: %08x CR2: %08x\n",
            frame->cs, frame->ip, frame->flags, x86_get_cr2());
    dprintf(CRITICAL, "EAX: %08x ECX: %08x EDX: %08x EBX: %08x\n",
            frame->ax, frame->cx, frame->dx, frame->bx);
    dprintf(CRITICAL, "ESP: %08x EBP: %08x ESI: %08x EDI: %08x\n",
            frame->sp, frame->bp, frame->si, frame->di);
    dprintf(CRITICAL, " DS:     %04x  ES:     %04x  FS:   %04x  GS:     %04x\n",
            frame->ds, frame->es, frame->fs, frame->gs);
#elif ARCH_X86_64
    dprintf(CRITICAL, " CS:              %4" PRIx64 " RIP: %16" PRIx64 " EFL: %16" PRIx64 " CR2: %16" PRIx64 "\n",
            frame->cs, frame->ip, frame->flags, x86_get_cr2());
    dprintf(CRITICAL, " RAX: %16" PRIx64 " RBX: %16" PRIx64 " RCX: %16" PRIx64 " RDX: %16" PRIx64 "\n",
            frame->ax, frame->bx, frame->cx, frame->dx);
    dprintf(CRITICAL, " RSI: %16" PRIx64 " RDI: %16" PRIx64 " RBP: %16" PRIx64 " RSP: %16" PRIx64 "\n",
            frame->si, frame->di, frame->bp, frame->user_sp);
    dprintf(CRITICAL, "  R8: %16" PRIx64 "  R9: %16" PRIx64 " R10: %16" PRIx64 " R11: %16" PRIx64 "\n",
            frame->r8, frame->r9, frame->r10, frame->r11);
    dprintf(CRITICAL, " R12: %16" PRIx64 " R13: %16" PRIx64 " R14: %16" PRIx64 " R15: %16" PRIx64 "\n",
            frame->r12, frame->r13, frame->r14, frame->r15);
    dprintf(CRITICAL, "errc: %16" PRIx64 "\n",
            frame->err_code);
#endif

    // dump the bottom of the current stack
    addr_t stack = (addr_t) frame;

    if (stack != 0) {
        dprintf(CRITICAL, "bottom of stack at 0x%08x:\n", (unsigned int)stack);
        hexdump((void *)stack, 512);
    }
}

static void exception_die(x86_iframe_t *frame, const char *msg)
{
    dprintf(CRITICAL, "%s", msg);
    dump_fault_frame(frame);

    panic("die");
    for (;;) {
        x86_cli();
        x86_hlt();
    }
}

void x86_syscall_handler(x86_iframe_t *frame)
{
    exception_die(frame, "unhandled syscall, halting\n");
}

void x86_gpf_handler(x86_iframe_t *frame)
{
    exception_die(frame, "unhandled gpf, halting\n");
}

void x86_invop_handler(x86_iframe_t *frame)
{
    exception_die(frame, "unhandled invalid op, halting\n");
}

void x86_unhandled_exception(x86_iframe_t *frame)
{
    printf("vector %u\n", (uint)frame->vector);
    exception_die(frame, "unhandled exception, halting\n");
}

void x86_pfe_handler(x86_iframe_t *frame)
{
    /* Handle a page fault exception */
    uint32_t error_code;
    thread_t *current_thread;
    error_code = frame->err_code;

    if (check_fault_handler_table(frame)) {
        return;
    }

#ifdef PAGE_FAULT_DEBUG_INFO
    dprintf(CRITICAL, "<PAGE FAULT> Instruction Pointer   = 0x%x:0x%x\n",
            (unsigned int)frame->cs & X86_8BYTE_MASK,
            (unsigned int)frame->ip);
    dprintf(CRITICAL, "<PAGE FAULT> Stack Pointer         = 0x%x:0x%x\n",
            (unsigned int)frame->user_ss & X86_8BYTE_MASK,
            (unsigned int)frame->user_sp);
    dprintf(CRITICAL, "<PAGE FAULT> Fault Linear Address = 0x%x\n",
            (unsigned int)x86_get_cr2());
    dprintf(CRITICAL, "<PAGE FAULT> Error Code Value      = 0x%x\n",
            error_code);
    dprintf(CRITICAL, "<PAGE FAULT> Error Code Type = %s %s %s%s, %s\n",
            error_code & PFEX_U ? "user" : "supervisor",
            error_code & PFEX_W ? "write" : "read",
            error_code & PFEX_I ? "instruction" : "data",
            error_code & PFEX_RSV ? " rsv" : "",
            error_code & PFEX_P ? "protection violation" : "page not present");
#endif

    current_thread = get_current_thread();
    dump_thread(current_thread);

    if (error_code & PFEX_U) {
        // User mode page fault
        switch (error_code) {
            case 4:
            case 5:
            case 6:
            case 7:
            default:
                arch_enable_ints();
                trusty_app_crash(error_code);
                break;
        }
    } else {
        // Supervisor mode page fault
        switch (error_code) {

            case 0:
            case 1:
            case 2:
            case 3:
            default:
                exception_die(frame, "Page Fault exception, halting\n");
                break;
        }
    }
}

/* top level x86 exception handler for most exceptions and irqs */
void x86_exception_handler(x86_iframe_t *frame)
{
    // get the current vector
    unsigned int vector = frame->vector;

    THREAD_STATS_INC(interrupts);

    // deliver the interrupt
    enum handler_return ret = INT_NO_RESCHEDULE;

    switch (vector) {
        case INT_GP_FAULT:
            x86_gpf_handler(frame);
            break;

        case INT_INVALID_OP:
            x86_invop_handler(frame);
            break;

        case INT_PAGE_FAULT:
            x86_pfe_handler(frame);
            break;

        case INT_DEV_NA_EX:
#if X86_WITH_FPU
            fpu_dev_na_handler();
#endif
            break;

        case INT_DOUBLE_FAULT:
            exception_die(frame, "double fault (kernel stack overflow?)\n");
            break;

        case INT_MF: { /* x87 floating point math fault */
            uint16_t fsw;
            __asm__ __volatile__("fnstsw %0" : "=m" (fsw));
            TRACEF("fsw 0x%hx\n", fsw);
            exception_die(frame, "x87 math fault\n");
            //asm volatile("fnclex");
            break;
        }
        case INT_XM: { /* simd math fault */
            uint32_t mxcsr;
            __asm__ __volatile__("stmxcsr %0" : "=m" (mxcsr));
            TRACEF("mxcsr 0x%x\n", mxcsr);
            exception_die(frame, "simd math fault\n");
            break;
        }
        case INT_DIVIDE_0:
        case INT_DEBUG_EX:
        case INT_STACK_FAULT:
        case 3:
        default:
            x86_unhandled_exception(frame);
            break;

        /* pass the rest of the irq vectors to the platform */
        case 0x20 ... 255:
            ret = platform_irq(frame);
    }

    if (ret != INT_NO_RESCHEDULE)
        thread_preempt();
}

