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

#define TLOG_TAG "apploader"

#include <apploader/package.h>
#include <assert.h>
#include <endian.h>
#include <interface/apploader/apploader.h>
#include <interface/apploader/apploader_secure.h>
#include <inttypes.h>
#include <lib/app_manifest/app_manifest.h>
#include <lib/apploader_policy_engine/apploader_policy_engine.h>
#include <lib/system_state/system_state.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty_log.h>

#include "app_manifest_parser.h"
#include "app_version.h"

struct apploader_req {
    struct apploader_header hdr;
    union {
        struct apploader_load_app_req load_app_req;
    };
} __PACKED;

/*
 * Common structure covering all possible apploader messages, only used to
 * determine the maximum message size
 */
union apploader_longest_msg {
    struct apploader_req req;
    struct apploader_resp resp;
} __PACKED;

static struct tipc_port_acl apploader_port_acl = {
#ifdef APPLOADER_ALLOW_NS_CONNECT
        .flags = IPC_PORT_ALLOW_TA_CONNECT | IPC_PORT_ALLOW_NS_CONNECT,
#else
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
#endif
        .uuid_num = 0,
        .uuids = NULL,
        .extra_data = NULL,
};

static struct tipc_port apploader_port = {
        .name = APPLOADER_PORT,
        .msg_max_size = sizeof(union apploader_longest_msg),
        .msg_queue_len = 1,
        .acl = &apploader_port_acl,
        .priv = NULL,
};

static int apploader_translate_error(int rc) {
    if (rc > 0) {
        return APPLOADER_ERR_INTERNAL;
    }

    switch (rc) {
    case ERR_NO_MEMORY:
        return APPLOADER_ERR_NO_MEMORY;
    case ERR_ALREADY_EXISTS:
        return APPLOADER_ERR_ALREADY_EXISTS;
    default:
        TLOGW("Unrecognized error (%d)\n", rc);
        return APPLOADER_ERR_INTERNAL;
    }
}

static int apploader_send_response(handle_t chan,
                                   uint32_t cmd,
                                   uint32_t error) {
    struct apploader_resp resp = {
            .hdr =
                    {
                            .cmd = cmd | APPLOADER_RESP_BIT,
                    },
            .error = error,
    };
    int rc = tipc_send1(chan, &resp, sizeof(resp));
    if (rc < 0) {
        return rc;
    }

    if ((size_t)rc != sizeof(resp)) {
        TLOGE("Failed to send message (%d). Expected to send %zu bytes.\n", rc,
              sizeof(resp));
        return ERR_BAD_LEN;
    }
    return NO_ERROR;
}

static int apploader_read(handle_t chan,
                          size_t min_sz,
                          void* buf,
                          size_t buf_sz,
                          handle_t* handles,
                          uint32_t* num_handles) {
    int rc;
    ipc_msg_info_t msg_inf;
    rc = get_msg(chan, &msg_inf);
    if (rc != NO_ERROR) {
        TLOGE("Failed to get message (%d)\n", rc);
        return rc;
    }

    if (msg_inf.len < min_sz || msg_inf.len > buf_sz) {
        TLOGE("Message is too short or too long (%zd)\n", msg_inf.len);
        rc = ERR_BAD_LEN;
        goto err;
    }

    uint32_t max_num_handles = num_handles ? *num_handles : 0;
    if (msg_inf.num_handles > max_num_handles) {
        TLOGE("Message has too many handles (%" PRIu32 ")\n",
              msg_inf.num_handles);
        rc = ERR_TOO_BIG;
        goto err;
    }

    struct iovec iov = {
            .iov_base = buf,
            .iov_len = buf_sz,
    };
    ipc_msg_t ipc_msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = handles,
            .num_handles = msg_inf.num_handles,
    };
    rc = read_msg(chan, msg_inf.id, 0, &ipc_msg);
    assert(rc < 0 || (size_t)rc == msg_inf.len);

    if (rc >= 0 && num_handles) {
        *num_handles = msg_inf.num_handles;
    }

err:
    put_msg(chan, msg_inf.id);
    return rc;
}

static uint32_t apploader_send_secure_get_memory_command(
        handle_t secure_chan,
        size_t aligned_size,
        handle_t* secure_mem_handle) {
    assert(secure_mem_handle);

    struct apploader_secure_header hdr = {
            .cmd = APPLOADER_SECURE_CMD_GET_MEMORY,
    };
    struct apploader_secure_get_memory_req get_memory_req = {
            .package_size = aligned_size,
    };
    int rc = tipc_send2(secure_chan, &hdr, sizeof(hdr), &get_memory_req,
                        sizeof(get_memory_req));
    if ((size_t)rc != sizeof(hdr) + sizeof(get_memory_req)) {
        TLOGE("Failed to send get_memory message (%d)\n", rc);
        return apploader_translate_error(rc);
    }

    uevent_t event = UEVENT_INITIAL_VALUE(event);
    rc = wait(secure_chan, &event, INFINITE_TIME);
    if (rc != NO_ERROR || !(event.event & IPC_HANDLE_POLL_MSG)) {
        TLOGE("Failed to wait for response (%d)\n", rc);
        return apploader_translate_error(rc);
    }

    uint32_t num_handles = 1;
    struct apploader_secure_resp resp;
    rc = apploader_read(secure_chan, sizeof(resp), &resp, sizeof(resp),
                        secure_mem_handle, &num_handles);
    if (rc < 0) {
        TLOGE("Failed to read response (%d)\n", rc);
        return apploader_translate_error(rc);
    }

    if (resp.hdr.cmd !=
        (APPLOADER_SECURE_CMD_GET_MEMORY | APPLOADER_SECURE_RESP_BIT)) {
        TLOGE("Invalid command in response (%u)\n", resp.hdr.cmd);
        return APPLOADER_ERR_INTERNAL;
    }

    if (resp.error != APPLOADER_NO_ERROR) {
        TLOGE("Received error from service (%" PRIu32 ")\n", resp.error);
        return resp.error;
    }

    if (num_handles != 1) {
        TLOGE("Expected 1 handle, got %" PRIu32 "\n", num_handles);
        return APPLOADER_ERR_INTERNAL;
    }

    return APPLOADER_NO_ERROR;
}

static uint32_t apploader_send_secure_load_command(
        handle_t secure_chan,
        ptrdiff_t elf_offset,
        ptrdiff_t manifest_offset,
        struct apploader_package_metadata* pkg_meta) {
    struct apploader_secure_header hdr = {
            .cmd = APPLOADER_SECURE_CMD_LOAD_APPLICATION,
    };
    struct apploader_secure_load_app_req req = {
            .manifest_start = manifest_offset,
            .manifest_end = manifest_offset + pkg_meta->manifest_size,
            .img_start = elf_offset,
            .img_end = elf_offset + pkg_meta->elf_size,
    };
    int rc = tipc_send2(secure_chan, &hdr, sizeof(hdr), &req, sizeof(req));
    if (rc != sizeof(hdr) + sizeof(req)) {
        TLOGE("Failed to send message (%d). Expected to send %zu bytes.\n", rc,
              sizeof(hdr) + sizeof(req));
        return apploader_translate_error(rc);
    }

    uevent_t event = UEVENT_INITIAL_VALUE(event);
    rc = wait(secure_chan, &event, INFINITE_TIME);
    if (rc != NO_ERROR || !(event.event & IPC_HANDLE_POLL_MSG)) {
        TLOGE("Failed to wait for response (%d)\n", rc);
        return apploader_translate_error(rc);
    }

    struct apploader_secure_resp resp;
    rc = tipc_recv1(secure_chan, sizeof(resp), &resp, sizeof(resp));
    if ((size_t)rc != sizeof(resp)) {
        TLOGE("Failed to read response for load application command\n");
        return apploader_translate_error(rc);
    }

    if (resp.hdr.cmd !=
        (APPLOADER_SECURE_CMD_LOAD_APPLICATION | APPLOADER_SECURE_RESP_BIT)) {
        TLOGE("Invalid command in response (%u)\n", resp.hdr.cmd);
        return APPLOADER_ERR_INTERNAL;
    }

    if (resp.error != APPLOADER_NO_ERROR) {
        TLOGE("Received error from service (%" PRIu32 ")\n", resp.error);
        return resp.error;
    }

    return APPLOADER_NO_ERROR;
}

static uint32_t apploader_copy_package(handle_t req_handle,
                                       handle_t secure_chan,
                                       uint64_t aligned_size,
                                       uint8_t** out_package) {
    uint32_t resp_error;

    handle_t secure_mem_handle;
    uint32_t get_memory_error = apploader_send_secure_get_memory_command(
            secure_chan, aligned_size, &secure_mem_handle);
    if (get_memory_error != APPLOADER_NO_ERROR) {
        TLOGE("Failed to get memory from service (%" PRIu32 ")\n",
              get_memory_error);
        resp_error = get_memory_error;
        goto err_send_get_memory;
    }

    if (secure_mem_handle == INVALID_IPC_HANDLE) {
        TLOGE("Received invalid handle from service\n");
        resp_error = APPLOADER_ERR_INTERNAL;
        goto err_invalid_secure_mem_handle;
    }

    void* req_package = mmap(NULL, aligned_size, PROT_READ, 0, req_handle, 0);
    if (req_package == MAP_FAILED) {
        TLOGE("Failed to map the request handle\n");
        resp_error = APPLOADER_ERR_NO_MEMORY;
        goto err_req_mmap;
    }

    void* resp_package = mmap(NULL, aligned_size, PROT_READ | PROT_WRITE, 0,
                              secure_mem_handle, 0);
    if (resp_package == MAP_FAILED) {
        TLOGE("Failed to map the handle from service\n");
        resp_error = APPLOADER_ERR_NO_MEMORY;
        goto err_resp_mmap;
    }

    assert(out_package);
    memcpy(resp_package, req_package, aligned_size);
    *out_package = resp_package;
    resp_error = APPLOADER_NO_ERROR;

err_resp_mmap:
    munmap(req_package, aligned_size);
err_req_mmap:
    close(secure_mem_handle);
err_invalid_secure_mem_handle:
err_send_get_memory:
    return resp_error;
}

static bool apploader_relocate_package(
        uint8_t* package,
        struct apploader_package_metadata* pkg_meta) {
    if (pkg_meta->elf_start > pkg_meta->manifest_start) {
        /*
         * For now, we only support input files where the ELF precedes
         * the manifest. The current file format follows this rule.
         */
        return false;
    }

    uint64_t unaligned_elf_size = pkg_meta->elf_size;
    uint64_t page_size = getauxval(AT_PAGESZ);
    /* ELF comes first, move it to offset 0 */
    memmove(package, pkg_meta->elf_start, unaligned_elf_size);
    pkg_meta->elf_start = package;
    pkg_meta->elf_size = round_up(unaligned_elf_size, page_size);

    if (pkg_meta->elf_size > unaligned_elf_size) {
        /*
         * There is a gap between ELF and manifest, zero it because it will
         * probably be used for .bss
         */
        memset(package + unaligned_elf_size, 0,
               pkg_meta->elf_size - unaligned_elf_size);
    }

    /*
     * Then move the manifest just after; the manifest starts
     * on the page immediately after the ELF file (elf_size is page-aligned
     * by the round_up call above), so the two never share a page
     */
    uint8_t* new_manifest_start = package + pkg_meta->elf_size;
    memmove(new_manifest_start, pkg_meta->manifest_start,
            pkg_meta->manifest_size);
    pkg_meta->manifest_start = new_manifest_start;

    return true;
}

static int apploader_handle_cmd_load_app(handle_t chan,
                                         struct apploader_load_app_req* req,
                                         handle_t req_handle) {
    uint32_t resp_error;

    if (req_handle == INVALID_IPC_HANDLE) {
        TLOGE("Received invalid request handle\n");
        resp_error = APPLOADER_ERR_INVALID_CMD;
        goto err_invalid_req_handle;
    }

    uint64_t page_size = getauxval(AT_PAGESZ);
    uint64_t aligned_size = round_up(req->package_size, page_size);
    TLOGD("Loading %" PRIu64 " bytes package, %" PRIu64 " aligned\n",
          req->package_size, aligned_size);

    handle_t secure_chan;
    int rc = tipc_connect(&secure_chan, APPLOADER_SECURE_PORT);
    if (rc < 0) {
        TLOGE("Failed to connect to service (%d)\n", rc);
        resp_error = apploader_translate_error(rc);
        goto err_connect_secure;
    }

    uint32_t copy_error;
    uint8_t* package;
    copy_error = apploader_copy_package(req_handle, secure_chan, aligned_size,
                                        &package);
    if (copy_error != APPLOADER_NO_ERROR) {
        TLOGE("Failed to copy package from client\n");
        resp_error = copy_error;
        goto err_copy_package;
    }

    struct apploader_package_metadata pkg_meta = {0};
    if (!apploader_parse_package_metadata(package, req->package_size,
                                          &pkg_meta)) {
        TLOGE("Failed to parse application package\n");
        resp_error = APPLOADER_ERR_VERIFICATION_FAILED;
        goto err_no_load;
    }

    if (!pkg_meta.manifest_start || !pkg_meta.manifest_size) {
        TLOGE("Could not find manifest in application package\n");
        resp_error = APPLOADER_ERR_VERIFICATION_FAILED;
        goto err_no_load;
    }

    if (!pkg_meta.elf_start || !pkg_meta.elf_size) {
        TLOGE("Could not find ELF image in application package\n");
        resp_error = APPLOADER_ERR_VERIFICATION_FAILED;
        goto err_no_load;
    }

    struct apploader_policy_data ap_data = {
            .public_key = pkg_meta.public_key,
            .public_key_size = pkg_meta.public_key_size,
            .force_store_min_version = false};

    if (!apploader_parse_manifest_from_metadata(&pkg_meta,
                                                &ap_data.manifest_extracts)) {
        TLOGE("Unable to extract manifest fields\n");
        resp_error = APPLOADER_ERR_VERIFICATION_FAILED;
        goto err_no_load;
    }

    if (!apploader_read_app_version(&ap_data.manifest_extracts.uuid,
                                    &ap_data.app_stored_version)) {
        TLOGE("Failed to read application version from storage\n");
        resp_error = APPLOADER_ERR_INTERNAL;
        goto err_no_load;
    }

    /* Prevent rollback - we can normally never load a lower version app */
    if (ap_data.manifest_extracts.version < ap_data.app_stored_version &&
        !system_state_app_loading_skip_version_check()) {
        TLOGE("Application package version (%" PRIu32
              ") is lower than storage version (%" PRIu32 ")\n",
              ap_data.manifest_extracts.version, ap_data.app_stored_version);
        resp_error = APPLOADER_ERR_INVALID_VERSION;
        goto err_no_load;
    }

    if (ap_data.manifest_extracts.requires_encryption &&
        !pkg_meta.elf_is_cose_encrypt && !system_state_app_loading_unlocked()) {
        TLOGE("Failed to meet application encryption requirement\n");
        resp_error = APPLOADER_ERR_NOT_ENCRYPTED;
        goto err_no_load;
    }

    /* Validate with the policy engine - note it may modify ap_data */
    if (!apploader_policy_engine_validate(&ap_data)) {
        TLOGE("App loading denied by policy engine\n");
        resp_error = APPLOADER_ERR_POLICY_VIOLATION;
        goto err_no_load;
    }

    if (!apploader_relocate_package(package, &pkg_meta)) {
        TLOGE("Failed to relocate package contents in memory\n");
        resp_error = APPLOADER_ERR_VERIFICATION_FAILED;
        goto err_no_load;
    }

    const uint32_t min_version = ap_data.manifest_extracts.min_version;
    const uint32_t storage_version = ap_data.app_stored_version;

    /* Update the loadable app version only if needed - reduce writes */
    if (min_version != storage_version &&
        (min_version > storage_version || ap_data.force_store_min_version)) {
        if (!system_state_app_loading_skip_version_check() &&
            !system_state_app_loading_skip_version_update()) {
            if (!apploader_write_app_version(&ap_data.manifest_extracts.uuid,
                                             min_version)) {
                TLOGE("Failed to update app version\n");
                resp_error = APPLOADER_ERR_INTERNAL;
                goto err_no_load;
            }
        }
    }

    ptrdiff_t elf_offset = pkg_meta.elf_start - package;
    ptrdiff_t manifest_offset = pkg_meta.manifest_start - package;
    munmap(package, aligned_size);
    package = NULL;

    /* Validate the relocated offsets */
    assert(elf_offset >= 0);
    assert(elf_offset + pkg_meta.elf_size <= aligned_size);
    assert(manifest_offset >= 0);
    assert(manifest_offset + pkg_meta.manifest_size <= aligned_size);

    /* Finalize the loading by sending a LOAD_APPLICATION request */
    resp_error = apploader_send_secure_load_command(secure_chan, elf_offset,
                                                    manifest_offset, &pkg_meta);
    if (resp_error != APPLOADER_NO_ERROR) {
        TLOGE("Failed to load application (%" PRIu32 ")\n", resp_error);
    }

err_no_load:
    if (pkg_meta.public_key) {
        apploader_policy_engine_put_key(pkg_meta.public_key);
    }

    if (package) {
        munmap(package, aligned_size);
    }
err_copy_package:
    close(secure_chan);
err_connect_secure:
err_invalid_req_handle:
    return apploader_send_response(chan, APPLOADER_CMD_LOAD_APPLICATION,
                                   resp_error);
}

static int apploader_on_message(const struct tipc_port* port,
                                handle_t chan,
                                void* ctx) {
    assert(port == &apploader_port);
    assert(ctx == NULL);
    int rc;
    handle_t handle = INVALID_IPC_HANDLE;
    uint32_t num_handles = 1;
    struct apploader_req req;
    rc = apploader_read(chan, sizeof(req.hdr), &req, sizeof(req), &handle,
                        &num_handles);
    if (rc < 0) {
        TLOGE("Failed to read request (%d)\n", rc);
        return rc;
    }

    TLOGD("Command: 0x%x\n", req.hdr.cmd);

    size_t cmd_len;
    switch (req.hdr.cmd) {
    case APPLOADER_CMD_LOAD_APPLICATION:
        /* Check the message length */
        cmd_len = sizeof(req.hdr) + sizeof(req.load_app_req);
        if (rc != (int)cmd_len) {
            TLOGE("Expected to read %zu bytes, got %d.\n", cmd_len, rc);
            rc = apploader_send_response(chan, req.hdr.cmd,
                                         APPLOADER_ERR_INVALID_CMD);
            break;
        }

        if (num_handles != 1) {
            TLOGE("Expected 1 handle, got %" PRIu32 "\n", num_handles);
            rc = apploader_send_response(chan, req.hdr.cmd,
                                         APPLOADER_ERR_INVALID_CMD);
            break;
        }

        rc = apploader_handle_cmd_load_app(chan, &req.load_app_req, handle);
        break;

    default:
        TLOGE("Received unknown apploader command: %" PRIu32 "\n", req.hdr.cmd);
        rc = apploader_send_response(chan, req.hdr.cmd,
                                     APPLOADER_ERR_UNKNOWN_CMD);
        break;
    }

    if (rc < 0) {
        TLOGE("Failed to run command (%d)\n", rc);
    }

    if (handle != INVALID_IPC_HANDLE) {
        close(handle);
    }

    return rc;
}

static struct tipc_srv_ops apploader_ops = {
        .on_message = apploader_on_message,
};

int main(void) {
    struct tipc_hset* hset = tipc_hset_create();

    if (IS_ERR(hset)) {
        return PTR_ERR(hset);
    }

    int rc = tipc_add_service(hset, &apploader_port, 1, 1, &apploader_ops);
    if (rc < 0) {
        return rc;
    }

    rc = tipc_run_event_loop(hset);
    printf("app loader going down: (%d)\n", rc);
    return rc;
}
