/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "include/lib/hwbcc/client/hwbcc.h"
#define TLOG_TAG "hwbcc-client"

#include <assert.h>
#include <interface/hwbcc/hwbcc.h>
#include <lib/hwbcc/client/hwbcc.h>
#include <lib/tipc/tipc.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

static int recv_resp(handle_t chan,
                     struct hwbcc_req_hdr* hdr,
                     uint8_t* buf,
                     size_t buf_size,
                     size_t* out_size) {
    uevent_t uevt;
    struct hwbcc_resp_hdr resp;

    assert(buf);
    assert(out_size);

    int rc = wait(chan, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("Failure while waiting for response: %d\n", rc);
        return rc;
    }

    rc = tipc_recv2(chan, sizeof(resp), &resp, sizeof(resp), buf, buf_size);
    if (rc < 0) {
        TLOGE("Failure on receiving response: %d\n", rc);
        return rc;
    }

    if (resp.cmd != (hdr->cmd | HWBCC_CMD_RESP_BIT)) {
        TLOGE("Unknown response cmd: %x\n", resp.cmd);
        return ERR_CMD_UNKNOWN;
    }

    if (resp.status != NO_ERROR) {
        TLOGE("Status is not SUCCESS. Actual: %d\n", resp.status);
        rc = resp.status;
        return rc;
    }

    if (resp.payload_size != (size_t)rc - sizeof(resp)) {
        return ERR_IO;
    }

    if (resp.payload_size > HWBCC_MAX_RESP_PAYLOAD_SIZE ||
        resp.payload_size > buf_size) {
        TLOGE("Response payload size: %d\n", resp.payload_size);
        return ERR_BAD_LEN;
    }

    *out_size = resp.payload_size;
    return NO_ERROR;
}

/**
 * sign_data() - Signs data and returns a COSE_Sign1 message.
 * @chan:                TIPC channel to HWBCC server
 * @test_mode:           Whether or not a to return test values.
 * @cose_algorithm:      COSE encoding of which signing algorithm to use.
 * @data:                Pointer to data.
 * @data_size:           Size of @data.
 * @aad:                 Pointer to AAD.
 * @aad_size:            Size of @aad.
 * @cose_sign1:          Buffer to push the formatted Sign1 msg into.
 * @cose_sign1_buf_size: Size of the buffer.
 * @cose_sign1_size:     Out parameter for actual size of the buffer used.
 *
 * Signs data using the device private key, encoding the result in a
 * COSE_Sign1 message.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
static int sign_data(handle_t chan,
                     uint8_t test_mode,
                     int32_t cose_algorithm,
                     const uint8_t* data,
                     uint32_t data_size,
                     const uint8_t* aad,
                     size_t aad_size,
                     uint8_t* cose_sign1,
                     size_t cose_sign1_buf_size,
                     size_t* cose_sign1_size) {
    int rc;
    struct hwbcc_sign_data_hdr {
        struct hwbcc_req_hdr hdr;
        struct hwbcc_req_sign_data args;
    } req;
    STATIC_ASSERT(sizeof(struct hwbcc_sign_data_hdr) ==
                  sizeof(struct hwbcc_req_hdr) +
                          sizeof(struct hwbcc_req_sign_data));
    assert(data);
    if (aad_size > 0) {
        assert(aad);
    }
    assert(cose_sign1);
    assert(cose_sign1_size);

    if (data_size > HWBCC_MAX_DATA_TO_SIGN_SIZE) {
        TLOGE("Data exceeds HWBCC_MAX_DATA_TO_SIGN_SIZE limit: %u > %u.\n",
              data_size, HWBCC_MAX_DATA_TO_SIGN_SIZE);
        return ERR_BAD_LEN;
    }

    if (aad_size > HWBCC_MAX_AAD_SIZE) {
        TLOGE("AAD exceeds HWBCC_MAX_AAD_SIZE limit: %zu > %u\n", aad_size,
              HWBCC_MAX_AAD_SIZE);
        return ERR_BAD_LEN;
    }

    memset(&req, 0, sizeof(req));
    req.hdr.cmd = HWBCC_CMD_SIGN_DATA;
    req.hdr.test_mode = test_mode;
    req.args.algorithm = cose_algorithm;
    req.args.data_size = data_size;
    req.args.aad_size = aad_size;

    uint32_t iov_count = 2;
    struct iovec iovs[3] = {
            {
                    .iov_base = (void*)&req,
                    .iov_len = sizeof(req),
            },
            {
                    .iov_base = (void*)data,
                    .iov_len = data_size,
            },
            {
                    .iov_base = NULL,
                    .iov_len = aad_size,
            },
    };
    if (aad) {
        iov_count = 3;
        iovs[2].iov_base = (void*)aad;
    }
    ipc_msg_t msg = {
            .iov = iovs,
            .num_iov = iov_count,
            .handles = NULL,
            .num_handles = 0,
    };

    rc = send_msg(chan, &msg);
    if (rc < 0) {
        TLOGE("Unable to send sign_data request: %d\n", rc);
        return rc;
    }

    if ((size_t)rc != sizeof(req) + req.args.data_size + req.args.aad_size) {
        TLOGE("Only sent %d bytes of the sign_data request.\n", rc);
        return rc;
    }

    return recv_resp(chan, &req.hdr, cose_sign1, cose_sign1_buf_size,
                     cose_sign1_size);
}

/**
 * get_bcc() - Retrieves the Boot Certificate Chain for the device.
 * @chan:         TIPC channel to HWBCC server
 * @test_mode:    Whether or not a to return test values.
 * @bcc:          Pointer to a buffer to store the BCC in.
 * @bcc_buf_size: Size of the @bcc buffer.
 * @bcc_size:     Actual size of the buffer used.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
static int get_bcc(handle_t chan,
                   uint8_t test_mode,
                   uint8_t* bcc,
                   size_t bcc_buf_size,
                   size_t* bcc_size) {
    int rc;

    assert(bcc);
    assert(bcc_size);

    struct hwbcc_req_hdr hdr = {
        .cmd = HWBCC_CMD_GET_BCC,
        .test_mode = test_mode
    };

    rc = tipc_send1(chan, &hdr, sizeof(hdr));
    if (rc < 0) {
        TLOGE("Unable to send get_bcc request: %d\n", rc);
        return rc;
    }

    if ((size_t)rc != sizeof(hdr)) {
        TLOGE("Only sent %d bytes of the get_bcc request.\n", rc);
        return rc;
    }

    return recv_resp(chan, &hdr, bcc, bcc_buf_size, bcc_size);
}

int hwbcc_get_protected_data(uint8_t test_mode,
                             int32_t cose_algorithm,
                             const uint8_t* data,
                             uint32_t data_size,
                             const uint8_t* aad,
                             size_t aad_size,
                             uint8_t* cose_sign1,
                             size_t cose_sign1_buf_size,
                             size_t* cose_sign1_size,
                             uint8_t* bcc,
                             size_t bcc_buf_size,
                             size_t* bcc_size) {
    int rc;
    handle_t chan;

    rc = tipc_connect(&chan, HWBCC_PORT);
    if (rc < 0) {
        TLOGE("Failed to connect to %s: %d\n", HWBCC_PORT, rc);
        return rc;
    }

    rc = sign_data(chan, test_mode, cose_algorithm, data, data_size, aad,
                   aad_size, cose_sign1, cose_sign1_buf_size, cose_sign1_size);
    if (rc != NO_ERROR) {
        TLOGE("Failed sign_data(): %d\n", rc);
        goto out;
    }

    rc = get_bcc(chan, test_mode, bcc, bcc_buf_size, bcc_size);
    if (rc != NO_ERROR) {
        TLOGE("Failed get_bcc(): %d\n", rc);
        goto out;
    }

out:
    close(chan);
    return rc;
}

int hwbcc_get_dice_artifacts(uint64_t context,
                             uint8_t* dice_artifacts,
                             size_t dice_artifacts_buf_size,
                             size_t* dice_artifacts_size) {
    int rc;
    handle_t chan;

    rc = tipc_connect(&chan, HWBCC_PORT);
    if (rc < 0) {
        TLOGE("Failed to connect to %s: %d\n", HWBCC_PORT, rc);
        return rc;
    }

    assert(dice_artifacts);
    assert(dice_artifacts_size);

    struct hwbcc_req_hdr hdr = {
        .cmd = HWBCC_CMD_GET_DICE_ARTIFACTS,
        .context = context
    };

    rc = tipc_send1(chan, &hdr, sizeof(hdr));
    if (rc < 0) {
        TLOGE("Unable to send get_dice_artifacts request: %d\n", rc);
        goto out;
    }

    if ((size_t)rc != sizeof(hdr)) {
        TLOGE("Only sent %d bytes of get_dice_artifacts request.\n", rc);
        goto out;
    }

    rc = recv_resp(chan, &hdr, dice_artifacts, dice_artifacts_buf_size,
                   dice_artifacts_size);
    if (rc != NO_ERROR) {
        TLOGE("Failed get_dice_artifacts(): %d\n", rc);
        goto out;
    }

out:
    close(chan);
    return rc;
}

int hwbcc_ns_deprivilege(void) {
    int rc;
    handle_t chan;

    rc = tipc_connect(&chan, HWBCC_PORT);
    if (rc < 0) {
        TLOGE("Failed to connect to %s: %d\n", HWBCC_PORT, rc);
        return rc;
    }

    struct hwbcc_req_hdr hdr = {
        .cmd = HWBCC_CMD_NS_DEPRIVILEGE
    };

    rc = tipc_send1(chan, &hdr, sizeof(hdr));
    if (rc < 0) {
        TLOGE("Unable to send deprivilege request: %d\n", rc);
        goto out;
    }

    if ((size_t)rc != sizeof(hdr)) {
        TLOGE("Only sent %d bytes of deprivilege request.\n", rc);
        goto out;
    }

    /*Receive respose which only contains a header*/
    uevent_t uevt;
    struct hwbcc_resp_hdr resp;

    rc = wait(chan, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("Failure while waiting for response: %d\n", rc);
        goto out;
    }

    rc = tipc_recv1(chan, sizeof(resp), &resp, sizeof(resp));
    if (rc < 0) {
        TLOGE("Failure on receiving response: %d\n", rc);
        goto out;
    }

    if (resp.cmd != (hdr.cmd | HWBCC_CMD_RESP_BIT)) {
        TLOGE("Unknown response cmd: %x\n", resp.cmd);
        rc = ERR_CMD_UNKNOWN;
        goto out;
    }

    if (resp.status != NO_ERROR) {
        TLOGE("Status is not SUCCESS. Actual: %d\n", resp.status);
        rc = resp.status;
        goto out;
    }

    rc = NO_ERROR;

out:
    close(chan);
    return rc;
}
