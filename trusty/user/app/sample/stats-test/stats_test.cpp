/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define TLOG_LEVEL TLOG_LEVEL_DEBUG
#define TLOG_TAG "stats-test"

#include <inttypes.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>
#include <trusty/memref.h>
#include <trusty/sys/mman.h>
#include <trusty/time.h>
#include <trusty/uuid.h>
#include <trusty_log.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#include <android/frameworks/stats/atoms.h>
#include <android/trusty/stats/tz/IStats.h>
#include <consumer_consts.h>
#include <consumer_ctl.h>
#include <lib/stats/stats.h>
#include <relayer_consts.h>
#include <stats_test_consts.h>

using android::frameworks::stats::VendorAtomValue;

static __ALIGNED(PAGE_SIZE) ShmContent bss_content;

#define MM_RW (MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE)

const char TRUSTY_DOMAIN[] = "google.android.trusty";

// Number of TIME_OUT_INTERVALs to wait for roundtrip
constexpr int MAX_TIMEOUT_WAITS = 10;

static handle_t bss_memref = INVALID_IPC_HANDLE;
static handle_t consumer_chan = INVALID_IPC_HANDLE;

static int consumer_connect(handle_t* chan) {
    return tipc_connect(chan, CONSUMER_PORT_TEST_CTL);
}

enum TrustyAtoms : int32_t {
    TrustyAppCrashed = 100072,
    TrustyError = 100145,
    TrustyStorageError = 100146
};

static void init(void) {
    if (bss_memref == INVALID_IPC_HANDLE) {
        int rc = memref_create(&bss_content, PAGE_SIZE, MM_RW);
        if (rc < 0) {
            TLOGE("bss memref create failed: (%d)\n", rc);
            abort();
        }

        bss_memref = rc;
    }
    if (consumer_chan == INVALID_IPC_HANDLE) {
        ASSERT_EQ(consumer_connect(&consumer_chan), 0);
        ASSERT_NE(consumer_chan, INVALID_IPC_HANDLE);
    }
test_abort:;
}

static int wait_to_send(handle_t session, struct ipc_msg* msg) {
    int rc;
    struct uevent ev = UEVENT_INITIAL_VALUE(ev);

    rc = wait(session, &ev, INFINITE_TIME);
    if (rc < 0) {
        TLOGE("failed to wait for outgoing queue to free up\n");
        return rc;
    }

    if (ev.event & IPC_HANDLE_POLL_SEND_UNBLOCKED) {
        return send_msg(session, msg);
    }

    if (ev.event & IPC_HANDLE_POLL_MSG) {
        return ERR_BUSY;
    }

    if (ev.event & IPC_HANDLE_POLL_HUP) {
        return ERR_CHANNEL_CLOSED;
    }

    return rc;
}

static int consumer_send(handle_t chan, ConsumerCtlCommand cmd) {
    assert(bss_memref != INVALID_IPC_HANDLE);
    assert(chan != INVALID_IPC_HANDLE);
    ConsumerCtlMsg request = {
            .cmd = cmd,
            .reserved = 0,
    };
    struct iovec iov = {
            .iov_base = &request,
            .iov_len = sizeof(ConsumerCtlMsg),
    };
    struct ipc_msg msg = {
            .num_iov = 1,
            .iov = &iov,
            .num_handles = 1,
            .handles = &bss_memref,
    };

    int rc = send_msg(chan, &msg);
    if (rc == ERR_NOT_ENOUGH_BUFFER) {
        rc = wait_to_send(chan, &msg);
    }
    return rc;
}

typedef struct {
} stats_test_t;

TEST_F_SETUP(stats_test) {
    init();
    volatile ShmContent* content = &bss_content;
    content->atom_id = -1;
    strncpy((char*)content->reverse_domain_name, "undefined",
            sizeof(content->reverse_domain_name));
    content->full.store(false, std::memory_order_release);

    int rc = consumer_send(consumer_chan, CONSUMER_CTL_SHM_SHARE);
    ASSERT_EQ(rc, (ssize_t)sizeof(ConsumerCtlMsg));
test_abort:;
}

TEST_F_TEARDOWN(stats_test) {
    if (consumer_chan != INVALID_IPC_HANDLE) {
        (void)consumer_send(consumer_chan, CONSUMER_CTL_SHM_RECLAIM);
    }
}

TEST_F(stats_test, atom_trusty_app_crashed) {
    int wait_consumer = MAX_TIMEOUT_WAITS;
    char uuid_str[UUID_STR_SIZE];
    struct uuid uuid = STATS_TEST_UUID;
    uuid_to_str(&uuid, uuid_str);
    struct stats_trusty_app_crashed atom = {
            .reverse_domain_name = TRUSTY_DOMAIN,
            .reverse_domain_name_len = sizeof(TRUSTY_DOMAIN) - 1,
            .app_id = uuid_str,
            .app_id_len = UUID_STR_SIZE,
    };
    volatile ShmContent* content = &bss_content;

    ASSERT_EQ(stats_trusty_app_crashed_report(
                      RELAYER_PORT_ISTATS, sizeof(RELAYER_PORT_ISTATS), atom),
              NO_ERROR);

    // wait for the consumer to update the shared-memory content
    while (--wait_consumer && !content->full.load(std::memory_order_acquire)) {
        trusty_nanosleep(0, 0, MS_TO_NS(TIME_OUT_INTERVAL_MS));
    }

    // verify that expected data was received by the consumer
    TLOGD("wait_consumer=%d\n", wait_consumer);
    ASSERT_EQ(content->full.load(), true);
    ASSERT_EQ(content->atom_id, (int32_t)TrustyAtoms::TrustyAppCrashed);
    ASSERT_EQ(strncmp((const char*)content->reverse_domain_name,
                      atom.reverse_domain_name,
                      SHM_CONTENT_VENDOR_ATOM_STR_SIZE - 1),
              0);
    ASSERT_EQ(strncmp((const char*)content->vendor_atom_values[0].s, uuid_str,
                      UUID_STR_SIZE - 1),
              0);

    content->full.store(false, std::memory_order_release);
test_abort:;
}

TEST_F(stats_test, atom_trusty_storage_error) {
    int wait_consumer = MAX_TIMEOUT_WAITS;
    char uuid_str[UUID_STR_SIZE];
    struct uuid uuid = STATS_TEST_UUID;
    uuid_to_str(&uuid, uuid_str);
    struct stats_trusty_storage_error atom = {
            .reverse_domain_name = TRUSTY_DOMAIN,
            .reverse_domain_name_len = sizeof(TRUSTY_DOMAIN) - 1,
            .error = TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED,
            .app_id = uuid_str,
            .app_id_len = UUID_STR_SIZE,
            .client_app_id = uuid_str,
            .client_app_id_len = UUID_STR_SIZE,
            .write = 1,
            .file_system = TRUSTY_FS_TDP,
            .file_path_hash = 0x4BCDEFABBAFEDCBALL,
            .block_type = TRUSTY_BLOCKTYPE_FREE_INTERNAL,
            .repair_counter = 1023,
    };

    volatile ShmContent* content = &bss_content;

    ASSERT_EQ(stats_trusty_storage_error_report(
                      RELAYER_PORT_ISTATS, sizeof(RELAYER_PORT_ISTATS), atom),
              NO_ERROR);

    // wait for the consumer to update the shared-memory content
    while (--wait_consumer && !content->full.load(std::memory_order_acquire)) {
        trusty_nanosleep(0, 0, MS_TO_NS(TIME_OUT_INTERVAL_MS));
    }

    // verify that expected data was received by the consumer
    TLOGD("wait_consumer=%d\n", wait_consumer);
    ASSERT_EQ(content->full.load(), true);
    ASSERT_EQ(content->atom_id, (int32_t)TrustyAtoms::TrustyStorageError);
    ASSERT_STREQN(const_cast<const char*>(content->reverse_domain_name),
                  atom.reverse_domain_name, UUID_STR_SIZE);

    ASSERT_EQ(content->vendor_atom_values[0].tag, VendorAtomValue::intValue);
    ASSERT_EQ((stats_trusty_storage_error_type)content->vendor_atom_values[0].i,
              TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED);

    ASSERT_EQ(content->vendor_atom_values[1].tag, VendorAtomValue::stringValue);
    ASSERT_STREQN(const_cast<const char*>(content->vendor_atom_values[1].s),
                  uuid_str, UUID_STR_SIZE);

    ASSERT_EQ(content->vendor_atom_values[2].tag, VendorAtomValue::stringValue);
    ASSERT_STREQN(const_cast<const char*>(content->vendor_atom_values[2].s),
                  uuid_str, UUID_STR_SIZE);

    ASSERT_EQ(content->vendor_atom_values[3].tag, VendorAtomValue::intValue);
    ASSERT_EQ(content->vendor_atom_values[3].i, 1);

    ASSERT_EQ(content->vendor_atom_values[4].tag, VendorAtomValue::intValue);
    ASSERT_EQ((stats_trusty_file_system)content->vendor_atom_values[4].i,
              TRUSTY_FS_TDP);

    ASSERT_EQ(content->vendor_atom_values[5].tag, VendorAtomValue::longValue);
    ASSERT_EQ(content->vendor_atom_values[5].l, 0x4BCDEFABBAFEDCBALL);

    ASSERT_EQ(content->vendor_atom_values[6].tag, VendorAtomValue::intValue);
    ASSERT_EQ((stats_trusty_block_type)content->vendor_atom_values[6].i,
              TRUSTY_BLOCKTYPE_FREE_INTERNAL);

    ASSERT_EQ(content->vendor_atom_values[7].tag, VendorAtomValue::longValue);
    ASSERT_EQ(content->vendor_atom_values[7].l, 1023);

    content->full.store(false, std::memory_order_release);
test_abort:;
}

TEST(stats_test, atom_parcel_release) {
    struct stats_vendor_atom* vendor_atom = nullptr;

    // prepare the vendor atom
    int rc = stats_vendor_atom_create_parcel(&vendor_atom);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(nullptr, vendor_atom);
    stats_vendor_atom_release(&vendor_atom);
    ASSERT_EQ(nullptr, vendor_atom);
test_abort:;
    if (vendor_atom) {
        stats_vendor_atom_release(&vendor_atom);
    }
}

TEST(stats_test, atom_istats_release) {
    struct stats_istats* istats = nullptr;
    android::wp<android::trusty::stats::tz::IStats> wp_istats;
    int rc = stats_istats_get_service(RELAYER_PORT_ISTATS,
                                      strlen(RELAYER_PORT_ISTATS) + 1, &istats);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(nullptr, istats);
    wp_istats = stats_istats_to_IStats(istats);
    stats_istats_release(&istats);
    ASSERT_EQ(nullptr, istats);
    ASSERT_EQ(nullptr, wp_istats.promote().get());
test_abort:;
    if (istats) {
        stats_istats_release(&istats);
    }
}

PORT_TEST(stats_test, "com.android.trusty.stats.test")
