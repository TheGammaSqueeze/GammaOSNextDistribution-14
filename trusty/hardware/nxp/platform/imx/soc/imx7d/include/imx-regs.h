/*
 * Copyright (C) 2016 Freescale Semiconductor, Inc.
 * Copyright 2017 NXP
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef _IMX_REGS_H_
#define _IMX_REGS_H_

#define MACH_IMX7

#define GIC_BASE_PHY 0x31001000
#define GIC_BASE_VIRT 0x71001000

#define SOC_REGS_PHY 0x30000000
#define SOC_REGS_VIRT 0x70000000
#define SOC_REGS_SIZE 0x01000000

#define CAAM_PHY_BASE_ADDR 0x30900000
#define CAAM_REG_SIZE 0x40000

#define CAAM_PHY_ARB_BASE_ADDR 0x00100000
#define CAAM_SEC_RAM_SIZE 0x8000

#define CCM_PHY_BASE_ADDR 0x30380000
#define CCM_REG_SIZE 0x10000

#define CCM_CAAM_CCGR_OFFSET (0x4000 + 16 * 36) /* CAAM CCGR is CCGR36 */

/* Registers for GIC */
#define MAX_INT 160
#define GICBASE(b) (GIC_BASE_VIRT)

#define GICC_SIZE (0x1000)
#define GICD_SIZE (0x100)

#define GICC_OFFSET (0x1000)
#define GICD_OFFSET (0x0000)

#define GICC_BASE_VIRT (GIC_BASE_VIRT + GICC_OFFSET)
#define GICD_BASE_VIRT (GIC_BASE_VIRT + GICD_OFFSET)

#define GIC_REG_SIZE 0x2000

/* Registers for TZASC */
#define TZ_BASE 0x30780000
#define TZ_BASE_VIRT (0x40000000 + TZ_BASE)
#define TZ_REG_SIZE 0x4000
#define TZ_BYPASS_GPR_BASE 0x30340024
#define TZ_BYPASS_GPR_BASE_VIRT (0x40000000 + TZ_BYPASS_GPR_BASE)

#define SRC_A7RCR1_PHY 0x30390008
#define SRC_A7RCR1 (0x40000000 + SRC_A7RCR1_PHY)
#define SRC_GPR3_PHY 0x3039007C
#define SRC_GPR3 (0x40000000 + SRC_GPR3_PHY)
#define SRC_GPR4_PHY 0x30390080
#define SRC_GPR4 (0x40000000 + SRC_GPR4_PHY)

#endif
