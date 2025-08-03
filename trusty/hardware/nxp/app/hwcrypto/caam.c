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

#include <assert.h>
#include <lk/compiler.h>
#include <lk/reg.h>
#include <malloc.h>
#include <openssl/digest.h>
#include <openssl/hkdf.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <trusty/sys/mman.h>
#include <uapi/err.h>

#include <imx-regs.h>
#include "caam.h"
#include "fsl_caam_internal.h"

#define TLOG_TAG "caam_drv"
#include <trusty_log.h>

struct caam_job_rings {
    uint32_t in[1];  /* single entry input ring */
    uint32_t out[2]; /* single entry output ring (consists of two words) */
};

/*
 * According to CAAM docs max number of descriptors in single sequence is 64
 * You can chain them though
 */
#define MAX_DSC_NUM 64

struct caam_job {
    uint32_t dsc[MAX_DSC_NUM]; /* job descriptors */
    uint32_t dsc_used;         /* number of filled entries */
    uint32_t status;           /* job result */
};

static struct caam_job_rings* g_rings;
static struct caam_job* g_job;

const uint32_t rng_inst_dsc[] = {
        RNG_INST_DESC1, RNG_INST_DESC2, RNG_INST_DESC3,
        RNG_INST_DESC4, RNG_INST_DESC5, RNG_INST_DESC6,
        RNG_INST_DESC7, RNG_INST_DESC8, RNG_INST_DESC9};

#if WITH_CAAM_SELF_TEST
static void caam_test(void);
#endif

static void caam_clk_get(void) {
    uint32_t val;

    /* make sure clock is on */
    val = readl(ccm_base + CCM_CAAM_CCGR_OFFSET);
#if defined(MACH_IMX6)
    val |= (3 << 8) | (3 < 10) | (3 << 12);
#elif defined(MACH_IMX7)
    val = (3 << 0); /* Always enabled (for now) */
#else
#error Unsupported IMX architecture
#endif
    writel(val, ccm_base + CCM_CAAM_CCGR_OFFSET);
}

static void setup_job_rings(void) {
    int rc;
    struct dma_pmem pmem;

    /* Initialize job ring addresses */
    memset(g_rings, 0, sizeof(*g_rings));
    rc = prepare_dma(g_rings, sizeof(g_rings), DMA_FLAG_TO_DEVICE, &pmem);
    if (rc != 1) {
        TLOGE("prepare_dma failed: %d\n", rc);
        abort();
    }

    writel((uint32_t)pmem.paddr + offsetof(struct caam_job_rings, in),
           CAAM_IRBAR0);  // input ring address
    writel((uint32_t)pmem.paddr + offsetof(struct caam_job_rings, out),
           CAAM_ORBAR0);  // output ring address

    /* Initialize job ring sizes */
    writel(countof(g_rings->in), CAAM_IRSR0);
    writel(countof(g_rings->in), CAAM_ORSR0);
}

static void run_job(struct caam_job* job) {
    int ret;
    uint32_t job_pa;
    struct dma_pmem pmem;

    /* prepare dma job */
    ret = prepare_dma(job->dsc, job->dsc_used * sizeof(uint32_t),
                      DMA_FLAG_TO_DEVICE, &pmem);
    assert(ret == 1);
    job_pa = (uint32_t)pmem.paddr;

    /* Add job to input ring */
    g_rings->out[0] = 0;
    g_rings->out[1] = 0;
    g_rings->in[0] = job_pa;

    ret = prepare_dma(g_rings, sizeof(g_rings), DMA_FLAG_TO_DEVICE, &pmem);
    assert(ret == 1);

    /* get clock */
    caam_clk_get();

    /* start job */
    writel(1, CAAM_IRJAR0);

    /* Wait for job ring to complete the job: 1 completed job expected */
    while (readl(CAAM_ORSFR0) != 1)
        ;

    finish_dma(g_rings->out, sizeof(g_rings->out), DMA_FLAG_FROM_DEVICE);

    /* check that descriptor address is the one expected in the out ring */
    assert(g_rings->out[0] == job_pa);

    job->status = g_rings->out[1];

    /* remove job */
    writel(1, CAAM_ORJRR0);
}

int init_caam_env(void) {
    caam_base = mmap(NULL, CAAM_REG_SIZE, PROT_READ | PROT_WRITE,
                     MMAP_FLAG_IO_HANDLE, CAAM_MMIO_ID, 0);
    if (caam_base == MAP_FAILED) {
        TLOGE("caam base mapping failed!\n");
        return ERR_GENERIC;
    }

    sram_base = mmap(NULL, CAAM_SEC_RAM_SIZE, PROT_READ | PROT_WRITE,
                     MMAP_FLAG_IO_HANDLE, CAAM_SEC_RAM_MMIO_ID, 0);
    if (sram_base == MAP_FAILED) {
        TLOGE("caam secure ram base mapping failed!\n");
        return ERR_GENERIC;
    }

    ccm_base = mmap(NULL, CCM_REG_SIZE, PROT_READ | PROT_WRITE,
                    MMAP_FLAG_IO_HANDLE, CCM_MMIO_ID, 0);
    if (ccm_base == MAP_FAILED) {
        TLOGE("ccm base mapping failed!\n");
        return ERR_GENERIC;
    }

    TLOGD("caam bases: %p, %p, %p\n", caam_base, sram_base, ccm_base);

    /* allocate rings */
    assert(sizeof(struct caam_job_rings) <= 16); /* TODO handle alignment */
    g_rings = memalign(16, sizeof(struct caam_job_rings));
    if (!g_rings) {
        TLOGE("out of memory allocating rings\n");
        return ERR_NO_MEMORY;
    }

    /* allocate jobs */
    g_job = memalign(MAX_DSC_NUM * sizeof(uint32_t), sizeof(struct caam_job));
    if (!g_job) {
        TLOGE("out of memory allocating job\n");
        return ERR_NO_MEMORY;
    }

    caam_open();
#if WITH_CAAM_SELF_TEST
    caam_test();
#endif

    return 0;
}

void caam_open(void) {
    uint32_t temp_reg;

    /* switch on CAAM clock */
    caam_clk_get();

    /* Initialize job ring addresses */
    setup_job_rings();

    /* HAB disables interrupts for JR0 so do the same here */
    temp_reg = readl(CAAM_JRCFGR0_LS) | JRCFG_LS_IMSK;
    writel(temp_reg, CAAM_JRCFGR0_LS);

    /* if RNG already instantiated then skip it */
    if ((readl(CAAM_RDSTA) & RDSTA_IF0) != RDSTA_IF0) {
        /* Enter TRNG Program mode */
        writel(RTMCTL_PGM, CAAM_RTMCTL);

        /* Set OSC_DIV field to TRNG */
        temp_reg = readl(CAAM_RTMCTL) | (RNG_TRIM_OSC_DIV << 2);
        writel(temp_reg, CAAM_RTMCTL);

        /* Set delay */
        writel(((RNG_TRIM_ENT_DLY << 16) | 0x09C4), CAAM_RTSDCTL);
        writel((RNG_TRIM_ENT_DLY >> 1), CAAM_RTFRQMIN);
        writel((RNG_TRIM_ENT_DLY << 4), CAAM_RTFRQMAX);

        /* Resume TRNG Run mode */
        temp_reg = readl(CAAM_RTMCTL) ^ RTMCTL_PGM;
        writel(temp_reg, CAAM_RTMCTL);

        temp_reg = readl(CAAM_RTMCTL) | RTMCTL_ERR;
        writel(temp_reg, CAAM_RTMCTL);

        /* init rng job */
        assert(sizeof(rng_inst_dsc) <= sizeof(g_job->dsc));
        memcpy(g_job->dsc, rng_inst_dsc, sizeof(rng_inst_dsc));
        g_job->dsc_used = countof(rng_inst_dsc);

        run_job(g_job);

        if (g_job->status & JOB_RING_STS) {
            TLOGE("job failed (0x%08x)\n", g_job->status);
            abort();
        }

        /* ensure that the RNG was correctly instantiated */
        temp_reg = readl(CAAM_RDSTA);
        if (temp_reg != (RDSTA_IF0 | RDSTA_SKVN)) {
            TLOGE("Bad RNG state 0x%X\n", temp_reg);
            abort();
        }
    }

    return;
}

uint32_t caam_decap_blob(const uint8_t* kmod,
                         size_t kmod_size,
                         uint8_t* plain,
                         const uint8_t* blob,
                         uint32_t size) {
    int ret;
    uint32_t kmod_pa;
    uint32_t blob_pa;
    uint32_t plain_pa;
    struct dma_pmem pmem;

    assert(size + CAAM_KB_HEADER_LEN < 0xFFFFu);
    assert(kmod_size == 16);

    ret = prepare_dma((void*)kmod, kmod_size, DMA_FLAG_TO_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    kmod_pa = (uint32_t)pmem.paddr;

    ret = prepare_dma((void*)blob, size + CAAM_KB_HEADER_LEN,
                      DMA_FLAG_TO_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    blob_pa = (uint32_t)pmem.paddr;

    ret = prepare_dma((void*)plain, size, DMA_FLAG_FROM_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    plain_pa = (uint32_t)pmem.paddr;

    g_job->dsc[0] = 0xB0800008;
    g_job->dsc[1] = 0x14400010;
    g_job->dsc[2] = kmod_pa;
    g_job->dsc[3] = 0xF0000000 | (0x0000ffff & (size + CAAM_KB_HEADER_LEN));
    g_job->dsc[4] = blob_pa;
    g_job->dsc[5] = 0xF8000000 | (0x0000ffff & (size));
    g_job->dsc[6] = plain_pa;
    g_job->dsc[7] = 0x860D0000;
    g_job->dsc_used = 8;

    run_job(g_job);

    if (g_job->status & JOB_RING_STS) {
        TLOGE("job failed (0x%08x)\n", g_job->status);
        return CAAM_FAILURE;
    }

    finish_dma(plain, size, DMA_FLAG_FROM_DEVICE);
    return CAAM_SUCCESS;
}

uint32_t caam_gen_blob(const uint8_t* kmod,
                       size_t kmod_size,
                       const uint8_t* plain,
                       uint8_t* blob,
                       uint32_t size) {
    int ret;
    uint32_t kmod_pa;
    uint32_t blob_pa;
    uint32_t plain_pa;
    struct dma_pmem pmem;

    assert(size + CAAM_KB_HEADER_LEN < 0xFFFFu);
    assert(kmod_size == 16);

    ret = prepare_dma((void*)kmod, kmod_size, DMA_FLAG_TO_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    kmod_pa = (uint32_t)pmem.paddr;

    ret = prepare_dma((void*)plain, size, DMA_FLAG_TO_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    plain_pa = (uint32_t)pmem.paddr;

    ret = prepare_dma((void*)blob, size + CAAM_KB_HEADER_LEN,
                      DMA_FLAG_FROM_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    blob_pa = (uint32_t)pmem.paddr;

    g_job->dsc[0] = 0xB0800008;
    g_job->dsc[1] = 0x14400010;
    g_job->dsc[2] = kmod_pa;
    g_job->dsc[3] = 0xF0000000 | (0x0000ffff & (size));
    g_job->dsc[4] = plain_pa;
    g_job->dsc[5] = 0xF8000000 | (0x0000ffff & (size + CAAM_KB_HEADER_LEN));
    g_job->dsc[6] = blob_pa;
    g_job->dsc[7] = 0x870D0000;
    g_job->dsc_used = 8;

    run_job(g_job);

    if (g_job->status & JOB_RING_STS) {
        TLOGE("job failed (0x%08x)\n", g_job->status);
        return CAAM_FAILURE;
    }

    finish_dma(blob, size + CAAM_KB_HEADER_LEN, DMA_FLAG_FROM_DEVICE);
    return CAAM_SUCCESS;
}

uint32_t caam_aes_op(const uint8_t* key,
                     size_t key_size,
                     const uint8_t* in,
                     uint8_t* out,
                     size_t len,
                     bool enc) {
    int ret;
    uint32_t in_pa;
    uint32_t out_pa;
    uint32_t key_pa;
    struct dma_pmem pmem;

    assert(key_size == 16);
    assert(len <= 0xFFFFu);
    assert(len % 16 == 0);

    ret = prepare_dma((void*)key, key_size, DMA_FLAG_TO_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    key_pa = (uint32_t)pmem.paddr;

    ret = prepare_dma((void*)in, len, DMA_FLAG_TO_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    in_pa = (uint32_t)pmem.paddr;

    ret = prepare_dma(out, len, DMA_FLAG_FROM_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    out_pa = (uint32_t)pmem.paddr;

    /*
     * Now AES key use aeskey.
     * aeskey is derived from the first 16 bytes of RPMB key.
     */
    g_job->dsc[0] = 0xb0800008;
    g_job->dsc[1] = 0x02000010;
    g_job->dsc[2] = key_pa;
    g_job->dsc[3] = enc ? 0x8210020D : 0x8210020C;
    g_job->dsc[4] = 0x22120000 | (0x0000ffff & len);
    g_job->dsc[5] = in_pa;
    g_job->dsc[6] = 0x60300000 | (0x0000ffff & len);
    g_job->dsc[7] = out_pa;
    g_job->dsc_used = 8;

    run_job(g_job);

    if (g_job->status & JOB_RING_STS) {
        TLOGE("job failed (0x%08x)\n", g_job->status);
        return CAAM_FAILURE;
    }

    finish_dma(out, len, DMA_FLAG_FROM_DEVICE);
    return CAAM_SUCCESS;
}

uint32_t caam_hwrng(uint8_t* out, size_t len) {
    int ret;
    struct dma_pmem pmem;

    while (len) {
        ret = prepare_dma(out, len,
                          DMA_FLAG_FROM_DEVICE | DMA_FLAG_ALLOW_PARTIAL, &pmem);
        if (ret != 1) {
            TLOGE("failed (%d) to prepare dma buffer\n", ret);
            return CAAM_FAILURE;
        }

        g_job->dsc[0] = 0xB0800004;
        g_job->dsc[1] = 0x82500000;
        g_job->dsc[2] = 0x60340000 | (0x0000ffff & pmem.size);
        g_job->dsc[3] = (uint32_t)pmem.paddr;
        g_job->dsc_used = 4;

        run_job(g_job);

        if (g_job->status & JOB_RING_STS) {
            TLOGE("job failed (0x%08x)\n", g_job->status);
            return CAAM_FAILURE;
        }

        finish_dma(out, pmem.size, DMA_FLAG_FROM_DEVICE);

        len -= pmem.size;
        out += pmem.size;
    }

    return CAAM_SUCCESS;
}

void* caam_get_keybox(void) {
    return sram_base;
}

uint32_t caam_hash(uint8_t* in, uint8_t* out, uint32_t len) {
    int ret;
    uint32_t in_pa;
    uint32_t out_pa;
    struct dma_pmem pmem;

    assert(len <= 0xFFFFu);

    ret = prepare_dma((void*)in, len, DMA_FLAG_TO_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    in_pa = (uint32_t)pmem.paddr;

    ret = prepare_dma(out, len, DMA_FLAG_FROM_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    out_pa = (uint32_t)pmem.paddr;

    g_job->dsc[0] = 0xB0800006;
    g_job->dsc[1] = 0x8441000D;
    g_job->dsc[2] = 0x24140000 | (0x0000ffff & len);
    g_job->dsc[3] = in_pa;
    g_job->dsc[4] = 0x54200000 | 20;
    g_job->dsc[5] = out_pa;
    g_job->dsc_used = 6;

    run_job(g_job);

    if (g_job->status & JOB_RING_STS) {
        TLOGE("job failed (0x%08x)\n", g_job->status);
        return CAAM_FAILURE;
    }

    finish_dma(out, len, DMA_FLAG_FROM_DEVICE);
    return CAAM_SUCCESS;
}

uint32_t caam_gen_kdfv1_root_key(uint8_t* out, uint32_t size) {
    int ret;
    uint32_t pa;
    struct dma_pmem pmem;

    assert(size == 32);

    ret = prepare_dma((void*)out, size, DMA_FLAG_FROM_DEVICE, &pmem);
    if (ret != 1) {
        TLOGE("failed (%d) to prepare dma buffer\n", ret);
        return CAAM_FAILURE;
    }
    pa = (uint32_t)pmem.paddr;

    /*
     * This sequence uses caam blob generation protocol in
     * master key verification mode to generate unique for device
     * persistent 256-bit sequence that we will be using a root key
     * for our key derivation function v1. This is the only known way
     * on this platform of producing persistent unique device key that
     * does not require persistent storage. Dsc[2..5] effectively contains
     * 16 bytes of randomly generated salt that gets mixed (among other
     * things) with device master key to produce result.
     */
    g_job->dsc[0] = 0xB080000B;
    g_job->dsc[1] = 0x14C00010;
    g_job->dsc[2] = 0x7083A393; /* salt word 0 */
    g_job->dsc[3] = 0x2CC0C9F7; /* salt word 1 */
    g_job->dsc[4] = 0xFC5D2FC0; /* salt word 2 */
    g_job->dsc[5] = 0x2C4B04E7; /* salt word 3 */
    g_job->dsc[6] = 0xF0000000;
    g_job->dsc[7] = 0;
    g_job->dsc[8] = 0xF8000030;
    g_job->dsc[9] = pa;
    g_job->dsc[10] = 0x870D0002;
    g_job->dsc_used = 11;

    run_job(g_job);

    if (g_job->status & JOB_RING_STS) {
        TLOGE("job failed (0x%08x)\n", g_job->status);
        return CAAM_FAILURE;
    }

    finish_dma(out, size, DMA_FLAG_FROM_DEVICE);
    return CAAM_SUCCESS;
}

#if WITH_CAAM_SELF_TEST

/*
 * HWRNG
 */
static void caam_hwrng_test(void) {
    DECLARE_SG_SAFE_BUF(out1, 32);
    DECLARE_SG_SAFE_BUF(out2, 32);

    caam_hwrng(out1, sizeof(out1));
    caam_hwrng(out2, sizeof(out2));

    if (memcmp(out1, out2, sizeof(out1)) == 0)
        TLOGI("caam hwrng test FAILED!!!\n");
    else
        TLOGI("caam hwrng test PASS!!!\n");
}

/*
 * Blob
 */
static void caam_blob_test(void) {
    uint i = 0;
    DECLARE_SG_SAFE_BUF(keymd, 16);
    DECLARE_SG_SAFE_BUF(plain, 32);
    DECLARE_SG_SAFE_BUF(plain_bak, 32);
    DECLARE_SG_SAFE_BUF(blob, 128);

    /* generate random key mod */
    caam_hwrng(keymd, sizeof(keymd));

    /* build known input */
    for (i = 0; i < sizeof(plain); i++) {
        plain[i] = i + '0';
        plain_bak[i] = plain[i];
    }

    /* encap  blob */
    caam_gen_blob(keymd, 16, plain, blob, sizeof(plain));
    memset(plain, 0xff, sizeof(plain));

    /* decap blob */
    caam_decap_blob(keymd, 16, plain, blob, sizeof(plain));

    /* compare with original */
    if (memcmp(plain, plain_bak, sizeof(plain)))
        TLOGI("caam blob test FAILED!!!\n");
    else
        TLOGI("caam blob test PASS!!!\n");
}

/*
 *  AES
 */
static void caam_aes_test(void) {
    DECLARE_SG_SAFE_BUF(key, 16);
    DECLARE_SG_SAFE_BUF(buf1, 32);
    DECLARE_SG_SAFE_BUF(buf2, 32);
    DECLARE_SG_SAFE_BUF(buf3, 32);

    /* generate random key */
    caam_hwrng(key, sizeof(key));

    /* create input */
    for (uint i = 0; i < sizeof(buf1); i++) {
        buf1[i] = i + '0';
    }

    /* reset output */
    memset(buf2, 0x55, sizeof(buf2));
    memset(buf3, 0xAA, sizeof(buf3));

    /* encrypt same data twice */
    caam_aes_op(key, 16, buf1, buf2, sizeof(buf1), true);
    caam_aes_op(key, 16, buf1, buf3, sizeof(buf1), true);

    /* compare results */
    if (memcmp(buf2, buf3, sizeof(buf1)))
        TLOGI("caam AES enc test FAILED!!!\n");
    else
        TLOGI("caam AES enc test PASS!!!\n");

    /* decrypt res */
    caam_aes_op(key, 16, buf3, buf2, sizeof(buf3), false);

    /* compare with original */
    if (memcmp(buf1, buf2, sizeof(buf1)))
        TLOGI("caam AES enc test FAILED!!!\n");
    else
        TLOGI("caam AES enc test PASS!!!\n");
}

/*
 * HASH (SHA-1)
 */
static void caam_hash_test(void) {
    DECLARE_SG_SAFE_BUF(in, 32);
    DECLARE_SG_SAFE_BUF(hash1, 32);
    DECLARE_SG_SAFE_BUF(hash2, 32);

    /* generate input */
    for (uint i = 0; i < sizeof(in); i++) {
        in[i] = i + '1';
    }

    /* reset output */
    memset(hash1, 0x55, sizeof(hash1));
    memset(hash2, 0xAA, sizeof(hash2));

    /* invoke hash twice */
    caam_hash(in, hash1, sizeof(in));
    caam_hash(in, hash2, sizeof(in));

    /* compare results */
    if (memcmp(hash1, hash2, 20) != 0)
        TLOGI("caam hash test FAILED!!!\n");
    else
        TLOGI("caam hash test PASS!!!\n");
}

static void caam_kdfv1_root_key_test(void) {
    DECLARE_SG_SAFE_BUF(out1, 32);
    DECLARE_SG_SAFE_BUF(out2, 32);

    caam_gen_kdfv1_root_key(out1, 32);
    caam_gen_kdfv1_root_key(out2, 32);

    if (memcmp(out1, out2, 32) != 0)
        TLOGI("caam gen kdf root key test FAILED!!!\n");
    else
        TLOGI("caam gen kdf root key test PASS!!!\n");
}

static void caam_test(void) {
    caam_hwrng_test();
    caam_blob_test();
    caam_kdfv1_root_key_test();
    caam_aes_test();
    caam_hash_test();
}

#endif /* WITH_CAAM_SELF_TEST */
