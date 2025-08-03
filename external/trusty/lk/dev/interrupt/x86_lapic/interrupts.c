/*
 * Copyright (c) 2012-2018 LK Trusty Authors. All Rights Reserved.
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

#include <arch/arch_ops.h>
#include <arch/x86.h>
#include <dev/interrupt/local_apic.h>
#include <dev/interrupt/x86_interrupts.h>
#include <err.h>
#if WITH_LIB_SM
#include <lib/sm.h>
#endif
#include <kernel/thread.h>

static spin_lock_t intr_reg_lock;
struct int_handler_struct int_handler_table[INT_VECTORS];

/*
 * IRQ0 to IRQ7 in Master PIC are tranditionally mapped by the BIOS to
 * interrupts 8 to 15 (0x08 to 0x0F). However, interrupts from 0x0 to
 * 0x1f are reseverd for exceptions by Intel x86 architecture. In order
 * to distinguish exception and external interrupts, it is recommended
 * to change ths PIC's offset so that IRQs are mapped to un-reserved
 * interrupts vectors.
 */
static void remap_pic(void) {
    /* Send ICW1 */
    outp(PIC1, ICW1);
    outp(PIC2, ICW1);

    /* Send ICW2 to remap */
    outp(PIC1 + 1, PIC1_BASE_INTR);
    outp(PIC2 + 1, PIC2_BASE_INTR);

    /* Send ICW3  */
    outp(PIC1 + 1, ICW3_PIC1);
    outp(PIC2 + 1, ICW3_PIC2);

    /* Send ICW4 */
    outp(PIC1 + 1, ICW4);
    outp(PIC2 + 1, ICW4);
}

/*
 * PIT is connected to IRQ0 of PIC as hardware timer resource. Trusty Intel
 * architecture reference utilizes interrupt triggered by PIT as timer
 * interrupt. To other PIC's IRQs, mask them directly, since Local APIC is
 * used to handle all other interrupts except timer interrupt.
 */
static void mask_all_except_irq0(void) {
    /* Mask all other IRQs except IRQ0(PIT) */
    outp(PIC1 + 1, 0xfe);
    outp(PIC2 + 1, 0xff);
}

/*
 * Mask all IRQs of PIC, since Trusty should not receive any external interrupts
 * from PIC in Android/Trusty solution.
 */
static void mask_all_irqs(void) {
    /* Mask all other IRQs except IRQ0(PIT) */
    outp(PIC1 + 1, 0xff);
    outp(PIC2 + 1, 0xff);
}

void x86_init_interrupts(void) {
    /* Remap PIC1 IRQ0 to PIC1_BASE_INTR and PIC2 to PIC2_BASE_INTR */
    remap_pic();
    mask_all_irqs();

    /* PIC2 interrupts are cascaded through PIC1 and must be unmasked there */
    unmask_interrupt(PIC2_CASCADE_INTR);
}

static void pic_set_mask(unsigned int vector, bool masked) {
    uint16_t port;
    uint line;
    DEBUG_ASSERT(vector >= PIC1_BASE_INTR);
    DEBUG_ASSERT(vector < AFTER_PIC_INTR);
    if (vector < PIC2_BASE_INTR) {
        port = PIC1 + 1;
        line = vector - PIC1_BASE_INTR;
    } else {
        port = PIC2 + 1;
        line = vector - PIC2_BASE_INTR;
    }
    spin_lock_saved_state_t state;
    spin_lock_irqsave(&intr_reg_lock, state);
    uint8_t line_mask = 1U << line;
    uint8_t current_mask = inp(port);
    if (masked) {
        current_mask |= line_mask;
    } else {
        current_mask &= ~line_mask;
    }
    dprintf(INFO, "new pic mask 0x%x: 0x%x\n", port, current_mask);
    outp(port, current_mask);
    spin_unlock_irqrestore(&intr_reg_lock, state);
}

status_t mask_interrupt(unsigned int vector) {
    if (vector >= INT_VECTORS) {
        return ERR_INVALID_ARGS;
    }
    if (vector >= PIC1_BASE_INTR && vector < AFTER_PIC_INTR) {
        pic_set_mask(vector, true);
    } else {
        return ERR_NOT_IMPLEMENTED;
    }

    return NO_ERROR;
}

void platform_mask_irqs(void) {}

status_t unmask_interrupt(unsigned int vector) {
    if (vector >= INT_VECTORS) {
        return ERR_INVALID_ARGS;
    }
    if (vector >= PIC1_BASE_INTR && vector < AFTER_PIC_INTR) {
        pic_set_mask(vector, false);
    } else {
        return ERR_NOT_IMPLEMENTED;
    }

    return NO_ERROR;
}

enum handler_return default_isr(unsigned int vector) {
    enum handler_return ret = INT_NO_RESCHEDULE;

#if WITH_LIB_SM
    /*
     * Deliver this interrupt to Non-Secure world. At this point,
     * both Secure and Non-Secure world are all interrupt disabled
     * after triggering self IPI. This IPI would be acknowledged at
     * Non-Secure world after world switch.
     */
    send_self_ipi(vector);
    ret = sm_handle_irq();
#endif

    lapic_eoi();

    return ret;
}

enum handler_return platform_irq(x86_iframe_t* frame) {
    /* Get current vector. */
    unsigned int vector = frame->vector;
    enum handler_return ret = INT_NO_RESCHEDULE;
    int_handler handler = NULL;
    void* arg = NULL;
    spin_lock_saved_state_t state;

    THREAD_STATS_INC(interrupts);

    spin_lock_irqsave(&intr_reg_lock, state);

    handler = int_handler_table[vector].handler;
    arg = int_handler_table[vector].arg;

    spin_unlock_irqrestore(&intr_reg_lock, state);

    if (NULL != handler) {
        ret = handler(arg);
        if (vector >= PIC1_BASE_INTR && vector < AFTER_PIC_INTR) {
            if (vector >= PIC2_BASE_INTR) {
                outp(PIC2, PIC_EOI);
            }
            outp(PIC1, PIC_EOI);
        }
    } else {
        ret = default_isr(vector);
    }

    return ret;
}

void register_int_handler(unsigned int vector, int_handler handler, void* arg) {
    if (vector >= INT_VECTORS) {
        panic("register_int_handler: vector out of range %d\n", vector);
    }

    spin_lock_saved_state_t state;
    spin_lock_irqsave(&intr_reg_lock, state);

    if (NULL == int_handler_table[vector].handler) {
        int_handler_table[vector].arg = arg;
        int_handler_table[vector].handler = handler;

        mb();
    } else {
        panic("ISR for vector: %d has been already registered!\n", vector);
    }

    spin_unlock_irqrestore(&intr_reg_lock, state);
}

#if WITH_LIB_SM
long smc_intc_get_next_irq(struct smc32_args* args) {
    long vector;

    for (vector = args->params[0]; vector < INT_VECTORS; vector++) {
        if (int_handler_table[vector].handler) {
            return vector;
        }
    }
    return -1;
}

status_t sm_intc_fiq_enter(void) {
    return NO_ERROR;
}

void sm_intc_enable_interrupts(void) {}
#endif
