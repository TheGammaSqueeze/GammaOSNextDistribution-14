/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <interface/apploader/apploader.h>
#include <interface/apploader/apploader_secure.h>
#include <inttypes.h>
#include <lib/system_state/system_state.h>
#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>
#include <stdlib.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty/string.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#define TLOG_TAG "apploader-unittest"

static uint32_t make_request(handle_t channel,
                             uint32_t cmd,
                             void* req,
                             size_t req_size,
                             handle_t req_handle,
                             handle_t* resp_handles,
                             size_t num_resp_handles) {
    struct uevent event;
    ipc_msg_info_t msg_inf;
    bool got_msg = false;
    struct apploader_resp resp;

    if (HasFailure()) {
        return 0;
    }

    struct apploader_header hdr = {
            .cmd = cmd,
    };
    struct iovec req_iov[2] = {{&hdr, sizeof(hdr)}, {req, req_size}};
    ipc_msg_t req_msg = {
            .iov = req_iov,
            .num_iov = (req && req_size) ? 2 : 1,
            .handles = &req_handle,
            .num_handles = (req_handle != INVALID_IPC_HANDLE) ? 1 : 0,
    };

    int rc;
    rc = send_msg(channel, &req_msg);
    ASSERT_EQ(rc, (ssize_t)sizeof(hdr) + (ssize_t)req_size);

    rc = wait(channel, &event, INFINITE_TIME);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(event.event & IPC_HANDLE_POLL_MSG, 0);

    rc = get_msg(channel, &msg_inf);
    ASSERT_EQ(rc, NO_ERROR);

    got_msg = true;
    ASSERT_EQ(msg_inf.len, sizeof(resp));

    struct iovec resp_iov = {
            .iov_base = (void*)&resp,
            .iov_len = sizeof(resp),
    };
    ipc_msg_t resp_msg = {
            .iov = &resp_iov,
            .num_iov = 1,
            .handles = resp_handles,
            .num_handles = num_resp_handles,
    };
    rc = read_msg(channel, msg_inf.id, 0, &resp_msg);
    ASSERT_EQ(rc, (ssize_t)sizeof(resp));
    ASSERT_EQ(resp.hdr.cmd, cmd | APPLOADER_RESP_BIT);

    put_msg(channel, msg_inf.id);
    return resp.error;

test_abort:
    if (got_msg) {
        put_msg(channel, msg_inf.id);
    }
    return 0;
}

static uint32_t load_test_app(handle_t channel,
                              char* app_start,
                              char* app_end) {
    uint32_t error = 0;

    uint64_t page_size = getauxval(AT_PAGESZ);
    ptrdiff_t app_size = app_end - app_start;
    size_t aligned_app_size = round_up(app_size, page_size);

    handle_t handle;
    handle = memref_create(app_start, aligned_app_size, MMAP_FLAG_PROT_READ);
    ASSERT_GT(handle, 0);

    struct apploader_load_app_req req = {
            .package_size = app_size,
    };
    error = make_request(channel, APPLOADER_CMD_LOAD_APPLICATION, &req,
                         sizeof(req), handle, NULL, 0);

test_abort:
    if (handle > 0) {
        close(handle);
    }
    return error;
}

static void* get_memory_buffer_from_malloc(size_t size, handle_t* handle_ptr) {
    if (HasFailure()) {
        return NULL;
    }

    uint64_t page_size = getauxval(AT_PAGESZ);
    size = round_up(size, page_size);

    void* base = memalign(page_size, size);
    ASSERT_NE(base, NULL);

    if (handle_ptr) {
        int rc = memref_create(base, size,
                               MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE);
        ASSERT_GE(rc, 0);
        *handle_ptr = (handle_t)rc;
    }

    return base;

test_abort:
    if (base) {
        free(base);
    }
    return NULL;
}

static handle_t get_memory_handle_from_service(handle_t channel, size_t size) {
    if (HasFailure()) {
        return INVALID_IPC_HANDLE;
    }

    uint32_t error;
    handle_t handle;
    struct apploader_secure_get_memory_req req = {
            .package_size = size,
    };
    error = make_request(channel, APPLOADER_SECURE_CMD_GET_MEMORY, &req,
                         sizeof(req), INVALID_IPC_HANDLE, &handle, 1);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(error, APPLOADER_NO_ERROR);
    ASSERT_NE(handle, INVALID_IPC_HANDLE);

    return handle;

test_abort:
    return INVALID_IPC_HANDLE;
}

static void* get_memory_buffer_from_service(handle_t channel, size_t size) {
    if (HasFailure()) {
        return NULL;
    }

    handle_t handle = get_memory_handle_from_service(channel, size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(handle, INVALID_IPC_HANDLE);

    void* buf = mmap(NULL, size, PROT_READ | PROT_WRITE, 0, handle, 0);
    ASSERT_NE(buf, MAP_FAILED);

    close(handle);

    return buf;

test_abort:
    if (handle != INVALID_IPC_HANDLE) {
        close(handle);
    }
    return NULL;
}

typedef struct apploader_user {
    handle_t channel;
} apploader_user_t;

TEST_F_SETUP(apploader_user) {
    int rc;

    rc = connect(APPLOADER_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    _state->channel = (handle_t)rc;
    ASSERT_GE(_state->channel, 0);

test_abort:;
}

TEST_F_TEARDOWN(apploader_user) {
    close(_state->channel);
}

#define UNKNOWN_CMD 0xffff0000U

TEST_F(apploader_user, UnknownCmd) {
    uint32_t error;
    error = make_request(_state->channel, UNKNOWN_CMD, NULL, 0, _state->channel,
                         NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_UNKNOWN_CMD);
}

TEST_F(apploader_user, BadLoadCmdMsgSize) {
    uint32_t error;
    error = make_request(_state->channel, APPLOADER_CMD_LOAD_APPLICATION, NULL,
                         0, _state->channel, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);
}

TEST_F(apploader_user, BadLoadCmdReqSize) {
    uint32_t error;
    struct apploader_load_app_req req = {
            .package_size = 0,
    };
    error = make_request(_state->channel, APPLOADER_CMD_LOAD_APPLICATION, &req,
                         1, _state->channel, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);
}

TEST_F(apploader_user, BadLoadCmdHandle) {
    uint32_t error;
    struct apploader_load_app_req req = {
            .package_size = 0,
    };
    error = make_request(_state->channel, APPLOADER_CMD_LOAD_APPLICATION, &req,
                         sizeof(req), INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);
}

TEST_F(apploader_user, LoadCmdPackageTooSmall) {
    const int package_size = 2;
    handle_t handle = INVALID_IPC_HANDLE;
    void* package = get_memory_buffer_from_malloc(package_size, &handle);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(handle, INVALID_IPC_HANDLE);
    ASSERT_NE(package, NULL);

    uint32_t error;
    struct apploader_load_app_req req = {
            .package_size = package_size,
    };
    error = make_request(_state->channel, APPLOADER_CMD_LOAD_APPLICATION, &req,
                         sizeof(req), handle, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_VERIFICATION_FAILED);

test_abort:
    if (package) {
        free(package);
    }
    if (handle != INVALID_IPC_HANDLE) {
        close(handle);
    }
}

static void send_cbor_package(handle_t chan,
                              const char* cbor,
                              size_t cbor_size) {
    handle_t handle = INVALID_IPC_HANDLE;
    void* package = get_memory_buffer_from_malloc(cbor_size, &handle);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(handle, INVALID_IPC_HANDLE);
    ASSERT_NE(package, NULL);

    memcpy(package, cbor, cbor_size);

    uint32_t error;
    struct apploader_load_app_req req = {
            .package_size = cbor_size,
    };
    error = make_request(chan, APPLOADER_CMD_LOAD_APPLICATION, &req,
                         sizeof(req), handle, NULL, 0);
    ASSERT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_VERIFICATION_FAILED);

test_abort:
    if (package) {
        free(package);
    }
    if (handle != INVALID_IPC_HANDLE) {
        close(handle);
    }
}

TEST_F(apploader_user, BadLoadCmdPackageCBORTag) {
    /* Write an untagged UInt */
    const char bad_tag_cbor[] = {
            /* UInt: 0 */
            0x00,
    };
    send_cbor_package(_state->channel, bad_tag_cbor, sizeof(bad_tag_cbor));
}

TEST_F(apploader_user, BadLoadCmdPackageCBORLength) {
    /* Send an incomplete message */
    const char bad_length_cbor[] = {
            /* UInt followed by missing byte */
            0x18,
    };
    send_cbor_package(_state->channel, bad_length_cbor,
                      sizeof(bad_length_cbor));
}

TEST_F(apploader_user, BadLoadCmdPackageCBORType) {
    /* Write a tagged UInt */
    const char bad_type_cbor[] = {
            /* CBOR tag: 65536 */
            0xda,
            0x00,
            0x01,
            0x00,
            0x00,
            /* UInt: 0 */
            0x00,
    };
    send_cbor_package(_state->channel, bad_type_cbor, sizeof(bad_type_cbor));
}

TEST_F(apploader_user, BadLoadCmdPackageCBORMap) {
    /* Write a tagged empty map */
    const char bad_map_cbor[] = {
            /* CBOR tag: 65536 */
            0xda,
            0x00,
            0x01,
            0x00,
            0x00,
            /* Map: empty */
            0xa0,
    };
    send_cbor_package(_state->channel, bad_map_cbor, sizeof(bad_map_cbor));
}

extern char version_test_app_v1_start[], version_test_app_v1_end[];
extern char version_test_app_v2_start[], version_test_app_v2_end[];
extern char version_test_app_v3_start[], version_test_app_v3_end[];

/*
 * App with versions v1, v2 & v3 - min_version=1.
 *  Test that v1 cannot be loaded after v2.
 *  Then v3 can load after v2 (but has min_version to v1).
 *  Then v1 still cannot be loaded.
 *  Then v2 still can be loaded.
 */
TEST_F(apploader_user, AppVersionTest) {
    uint32_t error;

    /* Load v2, no min_version (so min defaults to v2) */
    error = load_test_app(_state->channel, version_test_app_v2_start,
                          version_test_app_v2_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* Try to load v1 - this should not be allowed */
    error = load_test_app(_state->channel, version_test_app_v1_start,
                          version_test_app_v1_end);
    ASSERT_EQ(false, HasFailure());

    if (system_state_app_loading_skip_version_update()) {
        trusty_unittest_printf(
                "[  SKIPPED ] AppVersionTest - version update is disabled\n");
        ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                                error == APPLOADER_ERR_ALREADY_EXISTS);
    } else {
        ASSERT_EQ(error, APPLOADER_ERR_INVALID_VERSION);
    }

    /* Load v3, min_version = 1 */
    error = load_test_app(_state->channel, version_test_app_v3_start,
                          version_test_app_v3_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* Retry to load v1 - this should still not be allowed */
    error = load_test_app(_state->channel, version_test_app_v1_start,
                          version_test_app_v1_end);
    ASSERT_EQ(false, HasFailure());

    if (system_state_app_loading_skip_version_update()) {
        ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                                error == APPLOADER_ERR_ALREADY_EXISTS);
    } else {
        ASSERT_EQ(error, APPLOADER_ERR_INVALID_VERSION);
    }

    /* Load v2, should still be allowed */
    error = load_test_app(_state->channel, version_test_app_v2_start,
                          version_test_app_v2_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);
test_abort:;
}

extern char mmio_test_app_allowed_start[], mmio_test_app_allowed_end[];
extern char mmio_test_app_bad_uuid_start[], mmio_test_app_bad_uuid_end[];
extern char mmio_test_app_bad_range_low_start[],
        mmio_test_app_bad_range_low_end[];
extern char mmio_test_app_bad_range_high_start[],
        mmio_test_app_bad_range_high_end[];

TEST_F(apploader_user, MmioTest) {
    uint32_t error;

    /* The allowed app should get loaded successfully */
    error = load_test_app(_state->channel, mmio_test_app_allowed_start,
                          mmio_test_app_allowed_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* The app with an unknown UUID should get rejected */
    error = load_test_app(_state->channel, mmio_test_app_bad_uuid_start,
                          mmio_test_app_bad_uuid_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(APPLOADER_ERR_LOADING_FAILED, error);

    /* The apps with mappings outside the allowed range should get rejected */
    error = load_test_app(_state->channel, mmio_test_app_bad_range_low_start,
                          mmio_test_app_bad_range_low_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(APPLOADER_ERR_LOADING_FAILED, error);

    error = load_test_app(_state->channel, mmio_test_app_bad_range_high_start,
                          mmio_test_app_bad_range_high_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(APPLOADER_ERR_LOADING_FAILED, error);

test_abort:;
}

extern char encryption_test_app_encrypted_app_encryption_optional_start[],
        encryption_test_app_encrypted_app_encryption_optional_end[];
extern char encryption_test_app_encrypted_app_encryption_required_start[],
        encryption_test_app_encrypted_app_encryption_required_end[];
extern char encryption_test_app_unencrypted_app_encryption_optional_start[],
        encryption_test_app_unencrypted_app_encryption_optional_end[];
extern char encryption_test_app_unencrypted_app_encryption_required_start[],
        encryption_test_app_unencrypted_app_encryption_required_end[];

TEST_F(apploader_user, AppEncryptionTest) {
    uint32_t error;

    /* The encrypted app not requiring encryption should load successfully */
    error = load_test_app(
            _state->channel,
            encryption_test_app_encrypted_app_encryption_optional_start,
            encryption_test_app_encrypted_app_encryption_optional_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* The encrypted app requiring encryption should also load successfully */
    error = load_test_app(
            _state->channel,
            encryption_test_app_encrypted_app_encryption_required_start,
            encryption_test_app_encrypted_app_encryption_required_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* The unencrypted app not requiring encryption should load successfully */
    error = load_test_app(
            _state->channel,
            encryption_test_app_unencrypted_app_encryption_optional_start,
            encryption_test_app_unencrypted_app_encryption_optional_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /*
     * The unencrypted app requiring encryption should fail to load if app
     * loading is locked.
     */
    error = load_test_app(
            _state->channel,
            encryption_test_app_unencrypted_app_encryption_required_start,
            encryption_test_app_unencrypted_app_encryption_required_end);
    ASSERT_EQ(false, HasFailure());
    if (system_state_app_loading_unlocked()) {
        trusty_unittest_printf(
                "[  SKIPPED ] AppEncryptionTest - app loading is unlocked\n");
        ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                                error == APPLOADER_ERR_ALREADY_EXISTS);
    } else {
        ASSERT_EQ(error, APPLOADER_ERR_NOT_ENCRYPTED);
    }

test_abort:;
}

typedef struct apploader_service {
    handle_t channel;
} apploader_service_t;

TEST_F_SETUP(apploader_service) {
    int rc;

    rc = connect(APPLOADER_SECURE_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    _state->channel = (handle_t)rc;
    ASSERT_GE(_state->channel, 0);

test_abort:;
}

TEST_F_TEARDOWN(apploader_service) {
    close(_state->channel);
}

TEST_F(apploader_service, UnknownCmd) {
    uint32_t error;
    error = make_request(_state->channel, UNKNOWN_CMD, NULL, 0,
                         INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_UNKNOWN_CMD);
}

TEST_F(apploader_service, BadGetMemoryCmdMsgSize) {
    uint32_t error;
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_GET_MEMORY, NULL,
                         0, INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);
}

/* Make two GET_MEMORY requests */
TEST_F(apploader_service, DoubleGetMemory) {
    handle_t handle1 = INVALID_IPC_HANDLE;
    handle_t handle2 = INVALID_IPC_HANDLE;

    uint64_t page_size = getauxval(AT_PAGESZ);
    handle1 = get_memory_handle_from_service(_state->channel, page_size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(handle1, INVALID_IPC_HANDLE);

    uint32_t error;
    struct apploader_secure_get_memory_req req = {
            .package_size = page_size,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_GET_MEMORY, &req,
                         sizeof(req), INVALID_IPC_HANDLE, &handle2, 1);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);
    EXPECT_EQ(handle2, INVALID_IPC_HANDLE);

test_abort:
    if (handle1 != INVALID_IPC_HANDLE) {
        close(handle1);
    }
    if (handle2 != INVALID_IPC_HANDLE) {
        close(handle2);
    }
}

TEST_F(apploader_service, BadGetMemoryCmdReqSize) {
    uint32_t error;
    struct apploader_secure_get_memory_req req = {
            .package_size = 0,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_GET_MEMORY, &req,
                         1, INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);

test_abort:;
}

TEST_F(apploader_service, GetMemory) {
    uint64_t page_size = getauxval(AT_PAGESZ);
    void* buf = get_memory_buffer_from_service(_state->channel, page_size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(buf, NULL);

    uint32_t* p = buf;
    memset(buf, 0x5a, page_size);
    for (size_t i = 0; i < page_size / sizeof(uint32_t); i++, p++) {
        ASSERT_EQ(*p, 0x5a5a5a5aU);
    }

test_abort:
    if (buf) {
        munmap(buf, page_size);
    }
}

TEST_F(apploader_service, BadLoadCmdMsgSize) {
    uint32_t error;
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         NULL, 0, INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);

test_abort:;
}

TEST_F(apploader_service, BadLoadCmdReqSize) {
    uint32_t error;
    struct apploader_secure_load_app_req req = {
            .manifest_start = 0,
            .manifest_end = 0,
            .img_start = 0,
            .img_end = 0,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         &req, 1, INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);

test_abort:;
}

TEST_F(apploader_service, LoadWithoutGetMemory) {
    uint32_t error;
    struct apploader_secure_load_app_req req = {
            .manifest_start = 0,
            .manifest_end = 0,
            .img_start = 0,
            .img_end = 0,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         &req, sizeof(req), INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);

test_abort:;
}

TEST_F(apploader_service, BadLoadCmdOffsets) {
    uint64_t page_size = getauxval(AT_PAGESZ);
    handle_t handle = INVALID_IPC_HANDLE;
    handle = get_memory_handle_from_service(_state->channel, page_size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(handle, INVALID_IPC_HANDLE);

    close(handle);
    handle = INVALID_IPC_HANDLE;

    uint32_t error;
    struct apploader_secure_load_app_req req = {
            .manifest_start = 0,
            .manifest_end = 0,
            .img_start = 0,
            .img_end = 0,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         &req, sizeof(req), handle, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);

test_abort:
    if (handle != INVALID_IPC_HANDLE) {
        close(handle);
    }
}

TEST_F(apploader_service, BadLoadCmdImageAlignment) {
    uint64_t page_size = getauxval(AT_PAGESZ);
    handle_t handle = INVALID_IPC_HANDLE;
    handle = get_memory_handle_from_service(_state->channel, page_size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(handle, INVALID_IPC_HANDLE);

    close(handle);
    handle = INVALID_IPC_HANDLE;

    uint32_t error;
    struct apploader_secure_load_app_req req = {
            .manifest_start = 0,
            .manifest_end = 1,
            .img_start = 2,
            .img_end = 3,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         &req, sizeof(req), INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_LOADING_FAILED);

test_abort:
    if (handle != INVALID_IPC_HANDLE) {
        close(handle);
    }
}

/* Send a LOAD_APPLICATION command without closing the handle */
TEST_F(apploader_service, LoadCmdHoldHandle) {
    uint64_t page_size = getauxval(AT_PAGESZ);
    size_t buf_size = 2 * page_size;
    handle_t handle = INVALID_IPC_HANDLE;
    handle = get_memory_handle_from_service(_state->channel, buf_size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(handle, INVALID_IPC_HANDLE);

    uint32_t error;
    struct apploader_secure_load_app_req req = {
            .manifest_start = page_size,
            .manifest_end = buf_size,
            .img_start = 0,
            .img_end = page_size,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         &req, sizeof(req), INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);

test_abort:
    if (handle != INVALID_IPC_HANDLE) {
        close(handle);
    }
}

/* Send a LOAD_APPLICATION command without unmapping the memref */
TEST_F(apploader_service, LoadCmdHoldMapping) {
    uint64_t page_size = getauxval(AT_PAGESZ);
    size_t buf_size = 2 * page_size;
    void* buf = get_memory_buffer_from_service(_state->channel, buf_size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(buf, NULL);

    memset(buf, 0x5a, buf_size);

    uint32_t error;
    struct apploader_secure_load_app_req req = {
            .manifest_start = page_size,
            .manifest_end = buf_size,
            .img_start = 0,
            .img_end = page_size,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         &req, sizeof(req), INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_INVALID_CMD);

test_abort:
    if (buf) {
        munmap(buf, buf_size);
    }
}

TEST_F(apploader_service, BadLoadCmdImageELFHeader) {
    uint64_t page_size = getauxval(AT_PAGESZ);
    size_t buf_size = 2 * page_size;
    void* buf = get_memory_buffer_from_service(_state->channel, buf_size);
    ASSERT_EQ(false, HasFailure());
    ASSERT_NE(buf, NULL);

    /* Fill the image contents with 0x5a, so the ELF header check fails */
    memset(buf, 0x5a, buf_size);
    munmap(buf, buf_size);
    buf = NULL;

    uint32_t error;
    struct apploader_secure_load_app_req req = {
            .manifest_start = page_size,
            .manifest_end = buf_size,
            .img_start = 0,
            .img_end = page_size,
    };
    error = make_request(_state->channel, APPLOADER_SECURE_CMD_LOAD_APPLICATION,
                         &req, sizeof(req), INVALID_IPC_HANDLE, NULL, 0);
    EXPECT_EQ(false, HasFailure());
    EXPECT_EQ(error, APPLOADER_ERR_LOADING_FAILED);

test_abort:
    if (buf) {
        munmap(buf, buf_size);
    }
}

PORT_TEST(apploader, "com.android.trusty.apploader.test")
