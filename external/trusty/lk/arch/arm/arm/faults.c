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
#include <debug.h>
#include <bits.h>
#include <arch/arm.h>
#include <kernel/thread.h>
#include <platform.h>
#include <string.h>
#include <lib/trusty/trusty_app.h>
#include <inttypes.h>

struct fault_handler_table_entry {
    uint32_t pc;
    uint32_t fault_handler;
};

extern struct fault_handler_table_entry __fault_handler_table_start[];
extern struct fault_handler_table_entry __fault_handler_table_end[];

static bool check_fault_handler_table(struct arm_fault_frame *frame)
{
    struct fault_handler_table_entry *fault_handler;
    for (fault_handler = __fault_handler_table_start;
            fault_handler < __fault_handler_table_end;
            fault_handler++) {
        if (fault_handler->pc == frame->pc) {
            frame->pc = fault_handler->fault_handler;
            return true;
        }
    }
    return false;
}

static void dump_mode_regs(uint32_t spsr, uint32_t svc_r13, uint32_t svc_r14)
{
    struct arm_mode_regs regs;
    arm_save_mode_regs(&regs);

    dprintf(CRITICAL, "%c%s r13 0x%08x r14 0x%08x\n", ((spsr & CPSR_MODE_MASK) == CPSR_MODE_USR) ? '*' : ' ', "usr", regs.usr_r13, regs.usr_r14);
    dprintf(CRITICAL, "%c%s r13 0x%08x r14 0x%08x\n", ((spsr & CPSR_MODE_MASK) == CPSR_MODE_FIQ) ? '*' : ' ', "fiq", regs.fiq_r13, regs.fiq_r14);
    dprintf(CRITICAL, "%c%s r13 0x%08x r14 0x%08x\n", ((spsr & CPSR_MODE_MASK) == CPSR_MODE_IRQ) ? '*' : ' ', "irq", regs.irq_r13, regs.irq_r14);
    dprintf(CRITICAL, "%c%s r13 0x%08x r14 0x%08x\n", 'a', "svc", regs.svc_r13, regs.svc_r14);
    dprintf(CRITICAL, "%c%s r13 0x%08x r14 0x%08x\n", ((spsr & CPSR_MODE_MASK) == CPSR_MODE_SVC) ? '*' : ' ', "svc", svc_r13, svc_r14);
    dprintf(CRITICAL, "%c%s r13 0x%08x r14 0x%08x\n", ((spsr & CPSR_MODE_MASK) == CPSR_MODE_UND) ? '*' : ' ', "und", regs.und_r13, regs.und_r14);
    dprintf(CRITICAL, "%c%s r13 0x%08x r14 0x%08x\n", ((spsr & CPSR_MODE_MASK) == CPSR_MODE_SYS) ? '*' : ' ', "sys", regs.sys_r13, regs.sys_r14);

    // dump the bottom of the current stack
    addr_t stack;
    switch (spsr & CPSR_MODE_MASK) {
        case CPSR_MODE_FIQ:
            stack = regs.fiq_r13;
            break;
        case CPSR_MODE_IRQ:
            stack = regs.irq_r13;
            break;
        case CPSR_MODE_SVC:
            stack = svc_r13;
            break;
        case CPSR_MODE_UND:
            stack = regs.und_r13;
            break;
        case CPSR_MODE_SYS:
            stack = regs.sys_r13;
            break;
        default:
            stack = 0;
    }

    if (stack != 0) {
        dprintf(CRITICAL, "bottom of stack at 0x%08x:\n", (unsigned int)stack);
        hexdump((void *)stack, 128);
    }
}

static void dump_thread_info(void)
{
    struct thread *current_thread = get_current_thread();

    dprintf(CRITICAL, "current_thread %p, name %s\n",
            current_thread, current_thread ? current_thread->name : "");

    struct trusty_app *app = current_trusty_app();
    if (app) {
        dprintf(CRITICAL, "load bias %" PRIxVADDR "\n", app->load_bias);
    }

}

static void dump_fault_frame(struct arm_fault_frame *frame)
{
    dump_thread_info();
    dprintf(CRITICAL, "r0  0x%08x r1  0x%08x r2  0x%08x r3  0x%08x\n", frame->r[0], frame->r[1], frame->r[2], frame->r[3]);
    dprintf(CRITICAL, "r4  0x%08x r5  0x%08x r6  0x%08x r7  0x%08x\n", frame->r[4], frame->r[5], frame->r[6], frame->r[7]);
    dprintf(CRITICAL, "r8  0x%08x r9  0x%08x r10 0x%08x r11 0x%08x\n", frame->r[8], frame->r[9], frame->r[10], frame->r[11]);
    dprintf(CRITICAL, "r12 0x%08x usp 0x%08x ulr 0x%08x fp  0x%08x\n", frame->r[12], frame->usp, frame->ulr, frame->fp);
    dprintf(CRITICAL, "pc  0x%08x spsr 0x%08x\n", frame->pc, frame->spsr);

    dump_mode_regs(frame->spsr, (uintptr_t)(frame + 1), frame->lr);
}

static void dump_iframe(struct arm_iframe *frame)
{
    dump_thread_info();
    dprintf(CRITICAL, "r0  0x%08x r1  0x%08x r2  0x%08x r3  0x%08x\n", frame->r0, frame->r1, frame->r2, frame->r3);
    dprintf(CRITICAL, "r12 0x%08x usp 0x%08x ulr 0x%08x fp  0x%08x\n", frame->r12, frame->usp, frame->ulr, frame->fp);
    dprintf(CRITICAL, "pc  0x%08x spsr 0x%08x\n", frame->pc, frame->spsr);

    dump_mode_regs(frame->spsr, (uintptr_t)(frame + 1), frame->lr);
}

/*
 * Differentiate between crashes coming from different exception handlers
 * using the high order bits. DFSR and IFSR have the high order bits
 * reserved/zeroed, so it should be fine to overwrite them.
 */
enum crash_reason_bits {
    CRASH_REASON_SYSCALL        = 0x00000000,
    CRASH_REASON_UNDEFINED      = 0x10000000,
    CRASH_REASON_DATA_ABORT     = 0x20000000,
    CRASH_REASON_PREFETCH_ABORT = 0x30000000,
};

static void halt_thread(uint32_t spsr, uint32_t crash_reason)
{
    if ((spsr & CPSR_MODE_MASK) == CPSR_MODE_USR) {
        arch_enable_fiqs();
        arch_enable_ints();
        trusty_app_crash(crash_reason);
    }

    panic("fault\n");
    for (;;);
}

static void exception_die(struct arm_fault_frame *frame, const char *msg, uint32_t crash_reason)
{
    dprintf(CRITICAL, "%s", msg);
    dump_fault_frame(frame);

    halt_thread(frame->spsr, crash_reason);
}

static void exception_die_iframe(struct arm_iframe *frame, const char *msg, uint32_t crash_reason)
{
    dprintf(CRITICAL, "%s", msg);
    dump_iframe(frame);

    halt_thread(frame->spsr, crash_reason);
}

__WEAK void arm_syscall_handler(struct arm_fault_frame *frame)
{
    exception_die(frame, "unhandled syscall, halting\n", CRASH_REASON_SYSCALL);
}

void arm_undefined_handler(struct arm_iframe *frame)
{
#if ARM_WITH_VFP
    /* look at the undefined instruction, figure out if it's something we can handle */
    bool in_thumb = frame->spsr & (1<<5);
    if (in_thumb) {
        frame->pc -= 2;
    } else {
        frame->pc -= 4;
    }

    // pc may be unaligned, need to construct uint32_t byte by byte
    uint32_t opcode;
    memcpy(&opcode, (uint8_t *)frame->pc, sizeof(opcode));

    if (in_thumb) {
        /* look for a 32bit thumb instruction */
        if (opcode & 0x0000e800) {
            /* swap the 16bit words */
            opcode = (opcode >> 16) | (opcode << 16);
        }

        if (((opcode & 0xec000e00) == 0xec000a00) || // vfp
                ((opcode & 0xef000000) == 0xef000000) || // advanced simd data processing
                ((opcode & 0xff100000) == 0xf9000000)) { // VLD

            //dprintf(CRITICAL, "vfp/neon thumb instruction 0x%08x at 0x%x\n", opcode, frame->pc);
            arm_fpu_undefined_instruction(frame);
            return;
        }
    } else {
        /* look for arm vfp/neon coprocessor instructions */
        if (((opcode & 0x0c000e00) == 0x0c000a00) || // vfp
                ((opcode & 0xfe000000) == 0xf2000000) || // advanced simd data processing
                ((opcode & 0xff100000) == 0xf4000000)) { // VLD
            //dprintf(CRITICAL, "vfp/neon arm instruction 0x%08x at 0x%x\n", opcode, frame->pc);
            arm_fpu_undefined_instruction(frame);
            return;
        }
    }
#endif

    exception_die_iframe(frame, "undefined abort, halting\n", CRASH_REASON_UNDEFINED);
}

void arm_data_abort_handler(struct arm_fault_frame *frame)
{
    uint32_t fsr = arm_read_dfsr();
    uint32_t far = arm_read_dfar();

    uint32_t fault_status = (BIT(fsr, 10) ? (1<<4) : 0) |  BITS(fsr, 3, 0);

    if (check_fault_handler_table(frame)) {
        return;
    }

    dprintf(CRITICAL, "\n\ncpu %u data abort, ", arch_curr_cpu_num());
    bool write = !!BIT(fsr, 11);

    /* decode the fault status (from table B3-23) */
    switch (fault_status) {
        case 0b00001: // alignment fault
            dprintf(CRITICAL, "alignment fault on %s\n", write ? "write" : "read");
            break;
        case 0b00101:
        case 0b00111: // translation fault
            dprintf(CRITICAL, "translation fault on %s\n", write ? "write" : "read");
            break;
        case 0b00011:
        case 0b00110: // access flag fault
            dprintf(CRITICAL, "access flag fault on %s\n", write ? "write" : "read");
            break;
        case 0b01001:
        case 0b01011: // domain fault
            dprintf(CRITICAL, "domain fault, domain %lu\n", BITS_SHIFT(fsr, 7, 4));
            break;
        case 0b01101:
        case 0b01111: // permission fault
            dprintf(CRITICAL, "permission fault on %s\n", write ? "write" : "read");
            break;
        case 0b00010: // debug event
            dprintf(CRITICAL, "debug event\n");
            break;
        case 0b01000: // synchronous external abort
            dprintf(CRITICAL, "synchronous external abort on %s\n", write ? "write" : "read");
            break;
        case 0b10110: // asynchronous external abort
            dprintf(CRITICAL, "asynchronous external abort on %s\n", write ? "write" : "read");
            break;
        case 0b10000: // TLB conflict event
        case 0b11001: // synchronous parity error on memory access
        case 0b00100: // fault on instruction cache maintenance
        case 0b01100: // synchronous external abort on translation table walk
        case 0b01110: //    "
        case 0b11100: // synchronous parity error on translation table walk
        case 0b11110: //    "
        case 0b11000: // asynchronous parity error on memory access
        default:
            dprintf(CRITICAL, "unhandled fault\n");
            ;
    }

    dprintf(CRITICAL, "DFAR 0x%x (fault address)\n", far);
    dprintf(CRITICAL, "DFSR 0x%x (fault status register)\n", fsr);

    exception_die(frame, "halting\n", CRASH_REASON_DATA_ABORT | fsr);
}

void arm_data_abort_handler_stack_overflow(struct arm_fault_frame *frame)
{
    arm_data_abort_handler(frame);
    panic("kernel stack overflow, can't return\n");
}

void arm_prefetch_abort_handler(struct arm_fault_frame *frame)
{
    uint32_t fsr = arm_read_ifsr();
    uint32_t far = arm_read_ifar();

    uint32_t fault_status = (BIT(fsr, 10) ? (1<<4) : 0) |  BITS(fsr, 3, 0);

    if (check_fault_handler_table(frame)) {
        return;
    }

    dprintf(CRITICAL, "\n\ncpu %u prefetch abort, ", arch_curr_cpu_num());

    /* decode the fault status (from table B3-23) */
    switch (fault_status) {
        case 0b00001: // alignment fault
            dprintf(CRITICAL, "alignment fault\n");
            break;
        case 0b00101:
        case 0b00111: // translation fault
            dprintf(CRITICAL, "translation fault\n");
            break;
        case 0b00011:
        case 0b00110: // access flag fault
            dprintf(CRITICAL, "access flag fault\n");
            break;
        case 0b01001:
        case 0b01011: // domain fault
            dprintf(CRITICAL, "domain fault, domain %lu\n", BITS_SHIFT(fsr, 7, 4));
            break;
        case 0b01101:
        case 0b01111: // permission fault
            dprintf(CRITICAL, "permission fault\n");
            break;
        case 0b00010: // debug event
            dprintf(CRITICAL, "debug event\n");
            break;
        case 0b01000: // synchronous external abort
            dprintf(CRITICAL, "synchronous external abort\n");
            break;
        case 0b10110: // asynchronous external abort
            dprintf(CRITICAL, "asynchronous external abort\n");
            break;
        case 0b10000: // TLB conflict event
        case 0b11001: // synchronous parity error on memory access
        case 0b00100: // fault on instruction cache maintenance
        case 0b01100: // synchronous external abort on translation table walk
        case 0b01110: //    "
        case 0b11100: // synchronous parity error on translation table walk
        case 0b11110: //    "
        case 0b11000: // asynchronous parity error on memory access
        default:
            dprintf(CRITICAL, "unhandled fault\n");
            ;
    }

    dprintf(CRITICAL, "IFAR 0x%x (fault address)\n", far);
    dprintf(CRITICAL, "IFSR 0x%x (fault status register)\n", fsr);

    exception_die(frame, "halting\n", CRASH_REASON_PREFETCH_ABORT | fsr);
}
