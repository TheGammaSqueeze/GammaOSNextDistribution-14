/*
 * Copyright (c) 2020 Google, Inc.
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

/*
 * This module registers smc handlers that are called by tests running in the
 * client os. This api is currently only available if lib/sm is enabled.
 */
#if WITH_LIB_SM

#include <arch/arch_ops.h>
#include <arch/ops.h>
#include <err.h>
#include <inttypes.h>
#include <kernel/vm.h>
#include <lib/sm.h>
#include <lib/sm/sm_err.h>
#include <lib/sm/smcall.h>
#include <lib/smc/smc.h>
#include <limits.h>
#include <lk/init.h>
#include <string.h>
#include <trace.h>

#include "stdcalltest.h"

static ext_mem_obj_id_t args_get_id(struct smc32_args* args) {
    return (((uint64_t)args->params[1] << 32) | args->params[0]);
}

static size_t args_get_sz(struct smc32_args* args) {
    return (size_t)args->params[2];
}

/**
 * stdcalltest_sharedmem_rw - Test shared memory buffer.
 * @id:     Shared memory id.
 * @size:   Size.
 *
 * Check that buffer contains the 64 bit integer sqequnce [0, 1, 2, ...,
 * @size / 8 - 1] and modify sequence to [@size, @size - 1, size - 2, ...,
 * @size - (@size / 8 - 1)].
 *
 * Return: 0 on success. SM_ERR_INVALID_PARAMETERS is buffer does not contain
 * expected input pattern. SM_ERR_INTERNAL_FAILURE if @id could not be mapped.
 */
static long stdcalltest_sharedmem_rw(ext_mem_client_id_t client_id,
                                     ext_mem_obj_id_t mem_obj_id,
                                     size_t size) {
    struct vmm_aspace* aspace = vmm_get_kernel_aspace();
    status_t ret;
    long status;
    void* va;
    uint64_t* va64;

    if (!IS_PAGE_ALIGNED(size)) {
        return SM_ERR_INVALID_PARAMETERS;
    }

    ret = ext_mem_map_obj_id(aspace, "stdcalltest", client_id, mem_obj_id, 0, 0,
                             size, &va, PAGE_SIZE_SHIFT, 0,
                             ARCH_MMU_FLAG_PERM_NO_EXECUTE);
    if (ret != NO_ERROR) {
        status = SM_ERR_INTERNAL_FAILURE;
        goto err_map;
    }
    va64 = va;

    for (size_t i = 0; i < size / sizeof(*va64); i++) {
        if (va64[i] != i) {
            TRACEF("input mismatch at %zd, got 0x%" PRIx64
                   " instead of 0x%zx\n",
                   i, va64[i], i);
            status = SM_ERR_INVALID_PARAMETERS;
            goto err_input_mismatch;
        }
        va64[i] = size - i;
    }
    status = 0;

err_input_mismatch:
    ret = vmm_free_region(aspace, (vaddr_t)va);
    if (ret) {
        status = SM_ERR_INTERNAL_FAILURE;
    }
err_map:
    return status;
}

#if ARCH_ARM64
long clobber_sve_asm(uint32_t byte_clobber);
long load_sve_asm(uint8_t* arr, uint64_t len);

#define SVE_VEC_LEN_BITS 128
#define SVE_NB_BYTE_VEC_LEN SVE_VEC_LEN_BITS / 8
#define SVE_SVE_REGS_COUNT 32

#define SMC_FC_TRNG_VERSION SMC_FASTCALL_NR(SMC_ENTITY_STD, 0x50)

static uint8_t sve_regs[SMP_MAX_CPUS][SVE_SVE_REGS_COUNT * SVE_NB_BYTE_VEC_LEN]
        __attribute__((aligned(16)));

enum clobber_restore_error {
    SVE_NO_ERROR = 0,
    SVE_GENERIC_ERROR = 1,
    SVE_REGISTER_NOT_RESTORED = 2,
    SVE_ERROR_LONG_TYPE = LONG_MAX
};

long stdcalltest_clobber_sve(struct smc32_args* args) {
    enum clobber_restore_error ret = SVE_NO_ERROR;
    if (!arch_sve_supported()) {
        /* test is OK, if there is no SVE there is nothing to assert but this is
         * not an ERROR */
        return ret;
    }

    uint64_t v_cpacr_el1 = arch_enable_sve();
    uint cpuid = arch_curr_cpu_num();
    long call_nb = args->params[1];

    /* First Call on cpu needs to Clobber ASM registers */
    if (call_nb == 1) {
        ret = clobber_sve_asm(args->params[0]);
        if (ret != SVE_NO_ERROR) {
            panic("Failed to Clobber ARM SVE registers: %lx\n", ret);
            ret = SVE_GENERIC_ERROR;
            goto end_stdcalltest_clobber_sve;
        }
    }

    /* Make sure registers are as expected */
    const uint8_t EXPECTED = (uint8_t)args->params[0];
    ret = load_sve_asm(sve_regs[cpuid], SVE_NB_BYTE_VEC_LEN);
    if (ret != SVE_NO_ERROR) {
        panic("Failed to Load ARM SVE registers: %lx\n", ret);
        ret = SVE_GENERIC_ERROR;
        goto end_stdcalltest_clobber_sve;
    }

    for (size_t idx = 0; idx < countof(sve_regs[cpuid]); ++idx) {
        uint8_t val = sve_regs[cpuid][idx];

        if (val != EXPECTED) {
            ret = SVE_REGISTER_NOT_RESTORED;
            goto end_stdcalltest_clobber_sve;
        }
    }

end_stdcalltest_clobber_sve:
    ARM64_WRITE_SYSREG(cpacr_el1, v_cpacr_el1);
    return ret;
}
#endif

static long stdcalltest_stdcall(struct smc32_args* args) {
    switch (args->smc_nr) {
    case SMC_SC_TEST_VERSION:
        return TRUSTY_STDCALLTEST_API_VERSION;
    case SMC_SC_TEST_SHARED_MEM_RW:
        return stdcalltest_sharedmem_rw(args->client_id, args_get_id(args),
                                        args_get_sz(args));
#if ARCH_ARM64
    case SMC_SC_TEST_CLOBBER_SVE: {
        return stdcalltest_clobber_sve(args);
    }
#endif
    default:
        return SM_ERR_UNDEFINED_SMC;
    }
}

static struct smc32_entity stdcalltest_sm_entity = {
        .stdcall_handler = stdcalltest_stdcall,
};

static void stdcalltest_init(uint level) {
    int err;

    err = sm_register_entity(SMC_ENTITY_TEST, &stdcalltest_sm_entity);
    if (err) {
        printf("trusty error register entity: %d\n", err);
    }
}
LK_INIT_HOOK(stdcalltest, stdcalltest_init, LK_INIT_LEVEL_APPS);

#endif
