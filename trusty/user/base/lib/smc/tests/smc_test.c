/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define TLOG_TAG "smc-test"

#include <lib/smc/smc_ipc.h>
#include <lib/smc/smc_test.h>
#include <sys/mman.h>
#include <trusty/sys/mman.h>
#include <trusty_ipc.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

typedef struct smc {
    handle_t channel;
} smc_t;

static const int msg_len = sizeof(struct smc_msg);

TEST_F_SETUP(smc) {
    int rc;

    rc = connect(SMC_SERVICE_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    _state->channel = (handle_t)rc;
    ASSERT_GT(_state->channel, 0);

test_abort:;
}

TEST_F_TEARDOWN(smc) {
    close(_state->channel);
}

/* Macro to enable test cases for platform(s) on ARM and ARM64 architectures */
#if defined(ARCH_ARM) || defined(ARCH_ARM64)
#define ARM_ONLY_TEST(name) name
#else
#define ARM_ONLY_TEST(name) DISABLED_##name
#endif

/* Macro to enable test cases for generic ARM64 platform only */
#if defined(PLATFORM_GENERIC_ARM64)
#define GENERIC_ARM64_PLATFORM_ONLY_TEST(name) name
#else
#define GENERIC_ARM64_PLATFORM_ONLY_TEST(name) DISABLED_##name
#endif

/* Check that SM_ERR_UNDEFINED_SMC is returned for an unknown SMC number */
TEST_F(smc, ARM_ONLY_TEST(unknown_smc)) {
    int rc;
    struct smc_msg request = {
            .params[0] = ILLEGAL_SMC,
    };
    struct smc_msg response;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, ERR_GENERIC);
    ASSERT_EQ((int32_t)response.params[0], SM_ERR_UNDEFINED_SMC);

test_abort:;
}

/* Check that SMC service supports clients on multiple channels */
TEST_F(smc, ARM_ONLY_TEST(multiple_channels)) {
    int rc;
    handle_t channel1;
    handle_t channel2;
    struct smc_msg request = {
            .params[0] = ILLEGAL_SMC,
    };
    struct smc_msg response;

    channel1 = _state->channel;

    rc = connect(SMC_SERVICE_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    channel2 = (handle_t)rc;
    ASSERT_GT(channel2, 0);

    rc = smc_send_request(channel1, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_send_request(channel2, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(channel1, &response);
    ASSERT_EQ(rc, ERR_GENERIC);
    ASSERT_EQ((int32_t)response.params[0], SM_ERR_UNDEFINED_SMC);

    rc = smc_read_response(channel2, &response);
    ASSERT_EQ(rc, ERR_GENERIC);
    ASSERT_EQ((int32_t)response.params[0], SM_ERR_UNDEFINED_SMC);

test_abort:
    close(channel2);
}

/* Following test cases rely on Trusty SPD to be enabled in EL3, and are thus
 * platform-specific. */

TEST_F(smc, GENERIC_ARM64_PLATFORM_ONLY_TEST(putc)) {
    int rc;
    struct smc_msg request = {
            .params[0] = SMC_FC_DEBUG_PUTC,
            .params[1] = 'd', /* prints 'd' to serial */
    };
    struct smc_msg response;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, msg_len);
    ASSERT_EQ(response.params[0], 0);

test_abort:;
}

/* Check that we can query GICD base value from ATF */
TEST_F(smc, GENERIC_ARM64_PLATFORM_ONLY_TEST(get_gicd_base)) {
    int rc;
    struct smc_msg request = {
            .params[0] = SMC_FC_GET_REG_BASE,
            .params[1] = SMC_GET_GIC_BASE_GICD,
    };
    struct smc_msg response;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, msg_len);
    ASSERT_EQ(response.params[0], GICD_BASE);

test_abort:;
}

TEST_F(smc, GENERIC_ARM64_PLATFORM_ONLY_TEST(get_gicc_base)) {
    int rc;
    struct smc_msg request = {
            .params[0] = SMC_FC_GET_REG_BASE,
            .params[1] = SMC_GET_GIC_BASE_GICC,
    };
    struct smc_msg response;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, msg_len);
    ASSERT_EQ(response.params[0], GICC_BASE);

test_abort:;
}

TEST_F(smc, GENERIC_ARM64_PLATFORM_ONLY_TEST(access_denied)) {
    int rc;
    struct smc_msg request = {
            .params[0] = SMC_FC_ECHO_ONE_ARG,
    };
    struct smc_msg response;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, ERR_ACCESS_DENIED);
    ASSERT_EQ((int32_t)response.params[0], ERR_ACCESS_DENIED);

test_abort:;
}

#if defined(ARCH_ARM)
#define ARCH_SMC_FC_ECHO_THREE_ARGS SMC_FC_ECHO_THREE_ARGS
#else
#define ARCH_SMC_FC_ECHO_THREE_ARGS SMC_FC64_ECHO_THREE_ARGS
#endif

TEST_F(smc, GENERIC_ARM64_PLATFORM_ONLY_TEST(invalid_arguments)) {
    int rc;
    struct smc_msg request = {
            .params[0] = ARCH_SMC_FC_ECHO_THREE_ARGS,
            .params[1] = SMC_ACCESS_CONTROL_ALLOW_ARGS,
            .params[2] = 0,
    };
    struct smc_msg response;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, msg_len);
    ASSERT_EQ(response.params[0], ARCH_SMC_FC_ECHO_THREE_ARGS);
    ASSERT_EQ(response.params[1], SMC_ACCESS_CONTROL_ALLOW_ARGS);

    /* should fail validation per service access policy */
    request.params[1] = SMC_ACCESS_CONTROL_VALIDATE_ARGS;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, ERR_INVALID_ARGS);
    ASSERT_EQ((int32_t)response.params[0], ERR_INVALID_ARGS);

test_abort:;
}

TEST_F(smc, GENERIC_ARM64_PLATFORM_ONLY_TEST(validate_dma_arguments)) {
    const uint32_t dma_flags = DMA_FLAG_FROM_DEVICE | DMA_FLAG_ALLOW_PARTIAL;
    const uint32_t len = 0x1000U;
    uint8_t* va_base = MAP_FAILED;
    int rc;
    struct smc_msg request = {
            .params[0] = ARCH_SMC_FC_ECHO_THREE_ARGS,
            .params[1] = SMC_ACCESS_CONTROL_VALIDATE_ARGS,
            .params[2] = 0, /* invalid per test policy */
    };
    struct smc_msg response;
    struct dma_pmem pmem;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, ERR_INVALID_ARGS);
    ASSERT_EQ((int32_t)response.params[0], ERR_INVALID_ARGS);

    va_base = mmap(NULL, len, PROT_READ | PROT_WRITE, MMAP_FLAG_IO_HANDLE,
                   /* MMAP_ID=*/1, 0);
    ASSERT_NE(va_base, MAP_FAILED);

    rc = prepare_dma((void*)va_base, len, dma_flags, &pmem);
    ASSERT_EQ(rc, 1);
    ASSERT_NE(pmem.paddr, 0);
    ASSERT_EQ(pmem.size, len);

    request.params[2] = pmem.paddr;

    rc = smc_send_request(_state->channel, &request);
    ASSERT_EQ(rc, msg_len);

    rc = smc_read_response(_state->channel, &response);
    ASSERT_EQ(rc, msg_len);
    ASSERT_EQ(response.params[0], (uint64_t)ARCH_SMC_FC_ECHO_THREE_ARGS);
    ASSERT_EQ(response.params[1], SMC_ACCESS_CONTROL_VALIDATE_ARGS);
    ASSERT_EQ(response.params[2], pmem.paddr);

    /* fallthrough */
test_abort:
    finish_dma(va_base, len, dma_flags);
    munmap(va_base, len);
}

PORT_TEST(smc, "com.android.trusty.smc.test");
