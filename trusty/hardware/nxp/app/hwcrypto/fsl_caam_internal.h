/*
 * Copyright (c) 2012-2016, Freescale Semiconductor, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * o Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * o Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * o Neither the name of Freescale Semiconductor, Inc. nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
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

#ifndef __CAAM_INTERNAL_H__
#define __CAAM_INTERNAL_H__

static uint8_t* caam_base;
static uint8_t* ccm_base;
static uint8_t* sram_base;

/* 4kbyte pages */
#define CAAM_SEC_RAM_START_ADDR (sram_base)

#define SEC_MEM_PAGE0 CAAM_SEC_RAM_START_ADDR
#define SEC_MEM_PAGE1 (CAAM_SEC_RAM_START_ADDR + 0x1000)
#define SEC_MEM_PAGE2 (CAAM_SEC_RAM_START_ADDR + 0x2000)
#define SEC_MEM_PAGE3 (CAAM_SEC_RAM_START_ADDR + 0x3000)

/* Configuration and special key registers */
#define CAAM_MCFGR (0x0004 + caam_base)
#define CAAM_SCFGR (0x000c + caam_base)
#define CAAM_JR0MIDR (0x0010 + caam_base)
#define CAAM_JR1MIDR (0x0018 + caam_base)
#define CAAM_DECORR (0x009c + caam_base)
#define CAAM_DECO0MID (0x00a0 + caam_base)
#define CAAM_DAR (0x0120 + caam_base)
#define CAAM_DRR (0x0124 + caam_base)
#define CAAM_JDKEKR (0x0400 + caam_base)
#define CAAM_TDKEKR (0x0420 + caam_base)
#define CAAM_TDSKR (0x0440 + caam_base)
#define CAAM_SKNR (0x04e0 + caam_base)
#define CAAM_SMSTA (0x0FB4 + caam_base)
#define CAAM_STA (0x0FD4 + caam_base)
#define CAAM_SMPO_0 (0x1FBC + caam_base)

/* RNG registers */
#define CAAM_RTMCTL (0x0600 + caam_base)
#define CAAM_RTSDCTL (0x0610 + caam_base)
#define CAAM_RTFRQMIN (0x0618 + caam_base)
#define CAAM_RTFRQMAX (0x061C + caam_base)
#define CAAM_RTSTATUS (0x063C + caam_base)
#define CAAM_RDSTA (0x06C0 + caam_base)

/* Job Ring 0 registers */
#define CAAM_IRBAR0 (0x1004 + caam_base)
#define CAAM_IRSR0 (0x100c + caam_base)
#define CAAM_IRSAR0 (0x1014 + caam_base)
#define CAAM_IRJAR0 (0x101c + caam_base)
#define CAAM_ORBAR0 (0x1024 + caam_base)
#define CAAM_ORSR0 (0x102c + caam_base)
#define CAAM_ORJRR0 (0x1034 + caam_base)
#define CAAM_ORSFR0 (0x103c + caam_base)
#define CAAM_JRSTAR0 (0x1044 + caam_base)
#define CAAM_JRINTR0 (0x104c + caam_base)
#define CAAM_JRCFGR0_MS (0x1050 + caam_base)
#define CAAM_JRCFGR0_LS (0x1054 + caam_base)
#define CAAM_IRRIR0 (0x105c + caam_base)
#define CAAM_ORWIR0 (0x1064 + caam_base)
#define CAAM_JRCR0 (0x106c + caam_base)
#define CAAM_SMCJR0 (0x10f4 + caam_base)
#define CAAM_SMCSJR0 (0x10fc + caam_base)
#if 0
#define CAAM_SMAPJR0(y) (CAAM_BASE_ADDR + 0x1104 + y * 16)
#define CAAM_SMAG2JR0(y) (CAAM_BASE_ADDR + 0x1108 + y * 16)
#define CAAM_SMAG1JR0(y) (CAAM_BASE_ADDR + 0x110C + y * 16)
#define CAAM_SMAPJR0_PRTN1 CAAM_BASE_ADDR + 0x1114
#define CAAM_SMAG2JR0_PRTN1 CAAM_BASE_ADDR + 0x1118
#define CAAM_SMAG1JR0_PRTN1 CAAM_BASE_ADDR + 0x111c
#define CAAM_SMPO CAAM_BASE_ADDR + 0x1fbc
#endif

#define JRCFG_LS_IMSK 0x00000001
#define JR_MID 2
#define KS_G1 (1 << JR_MID)
#define PERM 0x0000B008

#define CMD_PAGE_ALLOC 0x1
#define CMD_PAGE_DEALLOC 0x2
#define CMD_PART_DEALLOC 0x3
#define CMD_INQUIRY 0x5
#define PAGE(x) (x << 16)
#define PARTITION(x) (x << 8)

#define SMCSJR_AERR (3 << 12)
#define SMCSJR_CERR (3 << 14)
#define CMD_COMPLETE (3 << 14)

#define SMCSJR_PO (3 << 6)
#define PAGE_AVAILABLE 0
#define PAGE_OWNED (3 << 6)

#define PARTITION_OWNER(x) (0x3 << (x * 2))

#define CAAM_BUSY_MASK 0x00000001
#define CAAM_IDLE_MASK 0x00000002
#define JOB_RING_ENTRIES 1
#define JOB_RING_STS (0xF << 28)

#define RNG_TRIM_OSC_DIV 0
#define RNG_TRIM_ENT_DLY 3200

#define RTMCTL_PGM (1 << 16)
#define RTMCTL_ERR (1 << 12)
#define RDSTA_IF0 1
#define RDSTA_SKVN (1 << 30)

#define DECAP_BLOB_DESC1 0xB0800009
#define DECAP_BLOB_DESC2 0x14C00C08
#define DECAP_BLOB_DESC3 0x00105566
#define DECAP_BLOB_DESC4 0x00000000
#define DECAP_BLOB_DESC5 0xF0000400
#define DECAP_BLOB_DESC6 0x00000000
#define DECAP_BLOB_DESC7 0xF80003d0
#define DECAP_BLOB_DESC8 SEC_MEM_PAGE1
#define DECAP_BLOB_DESC9 0x860D0008

#define ENCAP_BLOB_DESC1 0xB0800009
#define ENCAP_BLOB_DESC2 0x14C00C08
#define ENCAP_BLOB_DESC3 0x00105566
#define ENCAP_BLOB_DESC4 0x00000000
#define ENCAP_BLOB_DESC5 0xF00003d0
#define ENCAP_BLOB_DESC6 SEC_MEM_PAGE1
#define ENCAP_BLOB_DESC7 0xF8000400
#define ENCAP_BLOB_DESC8 0x00000000
#define ENCAP_BLOB_DESC9 0x870D0008

#define RNG_INST_DESC1 0xB0800009
#define RNG_INST_DESC2 0x12A00008
#define RNG_INST_DESC3 0x01020304
#define RNG_INST_DESC4 0x05060708
#define RNG_INST_DESC5 0x82500404
#define RNG_INST_DESC6 0xA2000001
#define RNG_INST_DESC7 0x10880004
#define RNG_INST_DESC8 0x00000001
#define RNG_INST_DESC9 0x82501000

#endif /* __CAAM_INTERNAL_H__ */
