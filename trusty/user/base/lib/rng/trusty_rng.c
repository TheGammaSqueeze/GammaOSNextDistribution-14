/*
 * Copyright (C) 2014-2015 The Android Open Source Project
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

/* Trusty Random Number Generation library.
 * Provides a CSPRNG and an interface to a HWRNG if present. The functions
 * in this library are currently not threadsafe. It is designed to be used
 * from a single-threaded Trusty application.
 */

#include <lib/rng/trusty_rng.h>

#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#include <interface/hwrng/hwrng.h>
#include <openssl/rand.h>

/*
 *	This is the generic part of the trusty app rng service.
 *	A server implementation for retrieving hardware backed
 *	random numbers, used by trusty_rng_hw_rand,
 *	is required to be provided by a hardware
 *	specific backend at tipc port HWRNG_PORT.
 *
 *	Clients of this library are encouraged to use the
 *	trusty_rng_secure_rand rather than the trusty_rng_hw_rand
 *	routine, as the latter incurs an IPC penalty with connection
 *	overhead.
 */

int trusty_rng_secure_rand(uint8_t* data, size_t len) {
    if (!data || !len)
        return ERR_INVALID_ARGS;

    int ssl_err = RAND_bytes(data, len);
    if (ssl_err != 1) {
        /*
         * BoringSSL never returns anything but success, so we should never hit
         * this.
         */
        return ERR_GENERIC;
    }

    return NO_ERROR;
}

int trusty_rng_add_entropy(const uint8_t* data, size_t len) {
    /*
     * We now use BoringSSL's PRNG, so this function does not do anything.
     */
    return NO_ERROR;
}

__WEAK int trusty_rng_hw_rand(uint8_t* data, size_t len) {
    struct hwrng_req req_hdr = {.len = len};

    struct iovec tx_iov = {
            .iov_base = &req_hdr,
            .iov_len = sizeof(req_hdr),
    };

    ipc_msg_t tx_msg = {
            .iov = &tx_iov,
            .num_iov = 1,
    };

    struct iovec rx_iov = {
            .iov_base = data,
            .iov_len = len,
    };
    ipc_msg_t rx_msg = {
            .iov = &rx_iov,
            .num_iov = 1,
    };

    long rc = connect(HWRNG_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    if (rc < 0) {
        return rc;
    }

    handle_t chan = (handle_t)rc;

    rc = send_msg(chan, &tx_msg);
    if (rc < 0) {
        goto err;
    }

    if (rc != sizeof(req_hdr)) {
        rc = ERR_IO;
        goto err;
    }

    while (rx_msg.iov[0].iov_len > 0) {
        uevent_t uevt;
        rc = wait(chan, &uevt, INFINITE_TIME);
        if (rc != NO_ERROR) {
            goto err;
        }

        ipc_msg_info_t inf;
        rc = get_msg(chan, &inf);
        if (rc != NO_ERROR) {
            goto err;
        }

        if (inf.len > rx_msg.iov[0].iov_len) {
            // received too much data
            rc = ERR_BAD_LEN;
            goto err;
        }

        rc = read_msg(chan, inf.id, 0, &rx_msg);
        if (rc < 0) {
            goto err;
        }

        size_t rx_size = (size_t)rc;
        rx_msg.iov[0].iov_base += rx_size;
        rx_msg.iov[0].iov_len -= rx_size;
        put_msg(chan, inf.id);
    }

    rc = NO_ERROR;
err:
    close(chan);
    return rc;
}
