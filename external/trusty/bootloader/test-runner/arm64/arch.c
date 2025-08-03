/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <test-runner-arch.h>

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <trusty/smc.h>
#include <trusty/smcall.h>

#if GIC_VERSION > 2
#define GICD_BASE (0x08000000)
#define GICD_CTLR (GICD_BASE + 0x000)

#define GICR_BASE (0x080A0000)
#define GICR_SGI_BASE (GICR_BASE + 0x10000)
#define GICR_ISENABLER0 (GICR_SGI_BASE + 0x0100)
#define GICR_CPU_OFFSET(cpu) ((cpu)*0x20000)

#define REG32(addr) ((volatile uint32_t*)(uintptr_t)(addr))
#define GICDREG_READ(reg) (*REG32((reg)))
#define GICDREG_WRITE(reg, val) (*REG32((reg)) = (val))

#define GICRREG_READ(cpu, reg) (*REG32((reg) + GICR_CPU_OFFSET(cpu)))
#define GICRREG_WRITE(cpu, reg, val) \
    (*REG32((reg) + GICR_CPU_OFFSET(cpu)) = (val))

static uint32_t doorbell_irq;
#endif

void boot_arm64(int cpu) {
#if GIC_VERSION > 2
    if (!cpu) {
        GICDREG_WRITE(GICD_CTLR, 2); /* Enable Non-secure group 1 interrupt */
        doorbell_irq = smc(SMC_FC_GET_NEXT_IRQ, 0, TRUSTY_IRQ_TYPE_DOORBELL, 0);
    }
    if (doorbell_irq >= 32) {
        /*
         * We only support per-cpu doorbell interrupts which are all enabled by
         * GICR_ISENABLER0.
         */
        return;
    }
    GICRREG_WRITE(cpu, GICR_ISENABLER0, 1U << doorbell_irq);
    GICRREG_WRITE(cpu, GICR_ISENABLER0, 1U << 0); /* skip_cpu0_wfi interrupt */
    __asm__ volatile("msr icc_igrpen1_el1, %0" ::"r"(1UL));
#endif
    boot(cpu);
}
