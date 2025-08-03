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

#ifndef _TZASC_REGIONS_H_
#define _TZASC_REGIONS_H_

#include <platform/tzasc.h>

static const tzasc_region_t tzasc_regions[] = {
        {.addr_l = 0,
         .addr_h = 0,
         .attr = TZ_ATTR(TZ_ATTR_SP_ALL,
                         TZ_ATTR_DISABLE_REGION,
                         TZ_ATTR_REGION_SIZE(TZ_REGION_SIZE_4G),
                         TZ_REGION_ENABLE)},

        {.addr_l = 0x80000000,
         .addr_h = 0,
         .attr = TZ_ATTR(TZ_ATTR_SP_ALL,
                         TZ_ATTR_DISABLE_REGION,
                         TZ_ATTR_REGION_SIZE(TZ_REGION_SIZE_512M),
                         TZ_REGION_ENABLE)},

        {.addr_l = MEMBASE,
         .addr_h = 0x0,
         .attr = TZ_ATTR(TZ_ATTR_SP_S_WR_ONLR,
                         TZ_ATTR_DISABLE_REGION,
                         TZ_ATTR_REGION_SIZE(TZ_REGION_SIZE_32M),
                         TZ_REGION_ENABLE)},
};

#endif
