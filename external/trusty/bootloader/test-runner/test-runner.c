/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <assert.h>
#include <interface/hwbcc/hwbcc.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <test-runner-arch.h>
#include <trusty/hwbcc.h>
#include <trusty/keymaster.h>
#include <trusty/rpmb.h>
#include <trusty/secretkeeper.h>
#include <trusty/trusty_dev.h>
#include <trusty/trusty_ipc.h>
#include <utils.h>
#include <virtio-console.h>
#include <virtio-rpmb.h>

enum test_message_header {
    TEST_PASSED = 0,
    TEST_FAILED = 1,
    TEST_MESSAGE = 2,
};

bool starts_with(const char* str1, const char* str2, size_t str2_len) {
    for (size_t i = 0; i < str2_len; i++) {
        if (str1[i] == '\0') {
            return true;
        }
        if (str1[i] != str2[i]) {
            return false;
        }
    }
    return false;
}

/*
 * Any return from this function indicates an internal error. The caller is
 * responsible for reporting the error. It currently returns to the host with
 * 2 as the exit code.
 * No attempt is made to clean up before returning.
 */
void boot(int cpu) {
    int ret;
    int chan;
    int status;
    char cmdline[256];
    size_t cmdline_len;
    const char* port;
    const char boottest_cmd[] = "boottest ";
    char test_result[256];
    struct trusty_ipc_iovec iovec = {
            .base = test_result,
            .len = sizeof(test_result),
    };
    static struct trusty_dev trusty_dev;
    struct trusty_ipc_dev* ipc_dev;
    struct trusty_ipc_chan test_chan;
    struct virtio_console* console;

    if (cpu) {
        while (true) {
            ret = trusty_dev_nop(&trusty_dev);
            if (ret >= 0) {
                trusty_idle(&trusty_dev, ret);
            } else {
                abort_msg("Secondary cpu unexpected error code\n");
            }
        }
    }

    /*
     * Initialize VirtIO console device, it contains rpmb0 port for VirtIO
     * RPMB and testrunner0 port to log message and pass test result to host
     */
    console = init_virtio_console();
    if (NULL == console) {
        return;
    }

    ret = init_log(console);
    if (ret != 0) {
        return;
    }

    /*
     * Read test arguments from host (port name of test server to connect to)
     */
    cmdline_len = host_get_cmdline(cmdline, sizeof(cmdline));
    if (!starts_with(boottest_cmd, cmdline, cmdline_len)) {
        /* No test was requested, boot next operating system */
        boot_next();
        return;
    }

    port = cmdline + sizeof(boottest_cmd) - 1;

    /* Init Trusty device */
    ret = trusty_dev_init(&trusty_dev, NULL);
    if (ret != 0) {
        return;
    }

    /* Create Trusty IPC device */
    ret = trusty_ipc_dev_create(&ipc_dev, &trusty_dev, PAGE_SIZE);
    if (ret != 0) {
        return;
    }

    /* If we don't have a VirtIO RPMB device, skip storage proxy */
    if (!init_virtio_rpmb(console)) {
        if (rpmb_storage_proxy_init(ipc_dev, NULL)) {
            log_msg("Failed to initialize storage proxy\n");
            return;
        }
    } else {
        log_msg("Could not find serial port rpmb0, skipping storage proxy.\n");
    }

    /*
     * Check that keymaster can at least be connected to.
     * TODO: Use in full boot path with typical calls.
     */
    ret = km_tipc_init(ipc_dev);
    if (ret != 0) {
        log_msg("km_tipc_init failed\n");
        return;
    }
    ret = trusty_set_boot_params(0, 0, KM_VERIFIED_BOOT_UNVERIFIED, false, NULL,
                                 0, NULL, 0);
    if (ret != 0) {
        log_msg("trusty_set_boot_params failed\n");
        return;
    }
    km_tipc_shutdown();

    /**
     * Check that HWBCC can be connected to.
     */
    ret = hwbcc_tipc_init(ipc_dev);
    if (ret != 0) {
        log_msg("hwbcc_tipc_init failed.\n");
        return;
    }
    uint8_t dice_artifacts[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t resp_payload_size = 0;
    ret = hwbcc_get_dice_artifacts(0, dice_artifacts, sizeof(dice_artifacts),
                                   &resp_payload_size);
    if (ret != 0) {
        log_msg("hwbcc_get_dice_artifacts failed.\n");
    }

    /**
     * dice_artifacts expects the following CBOR encoded structure.
     * Since the implementation of hwbcc_get_dice_artifacts serves only the
     * non-secure world, Bcc is not present in the returned dice_artifacts.
     * We calculate the expected size, including CBOR header sizes.
     * BccHandover = {
     *      1 : bstr .size 32,	// CDI_Attest
     *      2 : bstr .size 32,	// CDI_Seal
     *      ? 3 : Bcc,            // Cert_Chain
     * }
     * Bcc = [
     *      PubKeyEd25519, // UDS (Unique Device Secret)
     *      + BccEntry,    // Root -> leaf
     *  ]
     */
    size_t DICE_CDI_SIZE = 32;
    size_t bcc_handover_size = 2 * DICE_CDI_SIZE + 7 /*CBOR tags*/;

    if (resp_payload_size != bcc_handover_size) {
        log_msg("hwbcc_get_dice_artifacts failed with incorrect response size.\n");
    }

    /**
     * Note: In ABL, `hwbcc_ns_deprivilege` needs to be called
     * after retrieving dice artifacts.
     */
    ret = hwbcc_ns_deprivilege();

    if (ret != 0) {
        log_msg("hwbcc_ns_deprivilege failed.\n");
    }

    /* Close the firt connection and try to connect again, which should fail due
     * to the deprivilege call. */
    hwbcc_tipc_shutdown();

    ret = hwbcc_tipc_init(ipc_dev);
    if (ret != 0) {
        log_msg("hwbcc_tipc_init failed.\n");
        return;
    }
    memset(dice_artifacts, 0, sizeof(dice_artifacts));
    resp_payload_size = 0;
    ret = hwbcc_get_dice_artifacts(0, dice_artifacts, sizeof(dice_artifacts),
                                   &resp_payload_size);

    if (ret == 0) {
        log_msg("hwbcc_ns_deprivilege is broken.\n");
    }

    hwbcc_tipc_shutdown();

    /**
     * Check that Secretkeeper can be connected to.
     */
    ret = secretkeeper_tipc_init(ipc_dev);
    if (ret != 0) {
        log_msg("secretkeeper_tipc_init failed.\n");
        return;
    }

    /**
     * Check that we can retrieve the public key and that it has the expected
     * form.
     */
    uint8_t sk_identity[100];
    size_t sk_identity_size = 0;
    ret = secretkeeper_get_identity(sizeof(sk_identity), sk_identity,
                                    &sk_identity_size);
    if (ret != 0) {
        log_msg("secretkeeper_get_identity failed.\n");
        return;
    }

    const size_t ed25519_key_size = 32;
    const uint8_t expected_cbor_prefix[] = {
            // Map with 4 entries (see PubKeyEd25519)
            0xa4,
            // Key type: 1: 1
            0x01, 0x01,
            // Algorithm: 3: -8
            0x03, 0x27,
            // Curve: -1: 6
            0x20, 0x06,
            // Public key: -2, 32 byte bstr (which follows)
            0x21, 0x58, 0x20};
    const size_t expected_identity_size =
            sizeof(expected_cbor_prefix) + ed25519_key_size;

    if (sk_identity_size != expected_identity_size) {
        log_msg("Unexpected sk_identity_size\n");
        return;
    }

    if (memcmp(sk_identity, expected_cbor_prefix,
               sizeof(expected_cbor_prefix))) {
        log_msg("sk_identity does not start with expected prefix\n");
        return;
    }

    secretkeeper_tipc_shutdown();

    ret = arch_start_secondary_cpus();
    if (ret) {
        log_msg("Failed to start secondary CPUs\n");
        return;
    }

    /* Create connection to test server */
    trusty_ipc_chan_init(&test_chan, ipc_dev);
    chan = trusty_ipc_connect(&test_chan, port, true);
    if (chan < 0) {
        log_msg("Failed to connect to test server\n");
        return;
    }

    /* Wait for tests to complete and read status */
    while (true) {
        ret = trusty_ipc_recv(&test_chan, &iovec, 1, /* wait = */ true);
        if (ret <= 0 || ret >= (int)sizeof(test_result)) {
            return;
        }

        if (test_result[0] == TEST_PASSED) {
            break;
        } else if (test_result[0] == TEST_FAILED) {
            break;
        } else if (test_result[0] == TEST_MESSAGE) {
            log_buf(test_result + 1, ret - 1);
        } else {
            return;
        }
    }
    status = test_result[0] != TEST_PASSED;

    /* Request another read to wait for the sever to close the connection */
    ret = trusty_ipc_recv(&test_chan, &iovec, 1, /* wait = */ true);
    if (ret != TRUSTY_ERR_CHANNEL_CLOSED) {
        return;
    }

    /* Return test status to host, 0: test success, 1: test failed */
    host_exit(status);
}
