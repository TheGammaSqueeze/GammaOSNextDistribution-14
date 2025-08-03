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

#ifndef _CAAM_H
#define _CAAM_H

#include <stdbool.h>
#include <stdint.h>

#include <nxp_hwcrypto_consts.h>

#define CAAM_KB_HEADER_LEN 48
#define CAAM_SUCCESS 0
#define CAAM_FAILURE 1

int init_caam_env(void);

void caam_open(void);

uint32_t caam_gen_blob(const uint8_t* kmod,
                       size_t kmod_size,
                       const uint8_t* plain_text,
                       uint8_t* blob,
                       uint32_t size);

uint32_t caam_decap_blob(const uint8_t* kmod,
                         size_t kmod_size,
                         uint8_t* plain_text,
                         const uint8_t* blob,
                         uint32_t size);

uint32_t caam_aes_op(const uint8_t* key,
                     size_t key_size,
                     const uint8_t* input,
                     uint8_t* output,
                     size_t len,
                     bool enc);

uint32_t caam_hwrng(uint8_t* output_ptr, uint32_t output_len);

uint32_t caam_gen_kdfv1_root_key(uint8_t* out, size_t size);

void* caam_get_keybox(void);

/* Declare small scatter gather safe buffer (size must be power of 2) */
#define DECLARE_SG_SAFE_BUF(nm, sz) uint8_t nm[sz] __attribute__((aligned(sz)))

#endif
