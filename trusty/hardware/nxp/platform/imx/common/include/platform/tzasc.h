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

#ifndef TZASC_H_
#define TZASC_H_

#include <imx-regs.h>
#include <reg.h>
#include <sys/types.h>

#define TZ_IRQ 140

#define TZ_REG(r) (*REG32(r))

#define TZ_CONFIG (TZ_BASE_VIRT + 0x000)
#define TZ_ACTION (TZ_BASE_VIRT + 0x004)
#define TZ_INT_STATUS (TZ_BASE_VIRT + 0x010)
#define TZ_INT_CLEAR (TZ_BASE_VIRT + 0x014)
#define TZ_FAIL_ADDR_LOW (TZ_BASE_VIRT + 0x020)
#define TZ_FAIL_ADDR_HIGH (TZ_BASE_VIRT + 0x024)
#define TZ_FAIL_CONTROL (TZ_BASE_VIRT + 0x028)
#define TZ_FAIL_ID (TZ_BASE_VIRT + 0x02c)
#define TZ_SPECULATION_CTL (TZ_BASE_VIRT + 0x030)
#define TZ_SPECULATION_CTL_DISABLE_ALL (0x3)

#define TZ_REGION_BASE (TZ_BASE_VIRT + 0x100)
#define TZ_GET_REGION_ADDR(n) (TZ_REGION_BASE + (n * 0x10))

#define TZ_SP_NS_W (0x1 << 0)
#define TZ_SP_NS_R (0x1 << 1)
#define TZ_SP_S_W (0x1 << 2)
#define TZ_SP_S_R (0x1 << 3)

#define TZ_SP_SHIFT 28
#define TZ_ATTR_SP_S_WR_ONLR (0x0 | ((TZ_SP_S_W | TZ_SP_S_R) << TZ_SP_SHIFT))
#define TZ_ATTR_SP_ALL \
    (0x0 | ((TZ_SP_S_W | TZ_SP_S_R | TZ_SP_NS_R | TZ_SP_NS_W) << TZ_SP_SHIFT))

#define TZ_REGION_SIZE_4M 0x15
#define TZ_REGION_SIZE_8M 0x16
#define TZ_REGION_SIZE_16M 0x17
#define TZ_REGION_SIZE_32M 0x18
#define TZ_REGION_SIZE_64M 0x19
#define TZ_REGION_SIZE_128M 0x1A
#define TZ_REGION_SIZE_256M 0x1B
#define TZ_REGION_SIZE_512M 0x1C
#define TZ_REGION_SIZE_1G 0x1D
#define TZ_REGION_SIZE_2G 0x1E
#define TZ_REGION_SIZE_4G 0x1F

#define TZ_REGION_SIZE_SHIFT 1
#define TZ_ATTR_REGION_SIZE(s) (0x0 | ((s) << TZ_REGION_SIZE_SHIFT))

#define TZ_ATTR_DISABLE_REGION 0x0

#define TZ_REGION_ENABLE 0x1
#define TZ_REGION_DISABLE 0x0
#define TZ_ATTR(sp, sub_dis, size, en) (0x0 | (sp | sub_dis | size | en))

typedef struct tzasc_region {
    paddr_t addr_l;
    paddr_t addr_h;
    uint32_t attr;
} tzasc_region_t;

void initial_tzasc(const tzasc_region_t* r, uint num);

#endif
