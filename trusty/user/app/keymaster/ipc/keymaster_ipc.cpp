/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "keymaster_ipc.h"

#include <lib/system_state/system_state.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#include <interface/keymaster/keymaster.h>

#include <keymaster/UniquePtr.h>

#include <openssl/crypto.h>

#include "trusty_keymaster.h"
#include "trusty_logger.h"

using namespace keymaster;

typedef void (*event_handler_proc_t)(const uevent_t* ev, void* ctx);
struct tipc_event_handler {
    event_handler_proc_t proc;
    void* priv;
};

struct keymaster_chan_ctx {
    struct tipc_event_handler handler;
    uuid_t uuid;
    handle_t chan;
    long (*dispatch)(keymaster_chan_ctx*,
                     keymaster_message*,
                     uint32_t,
                     keymaster::UniquePtr<uint8_t[]>*,
                     uint32_t*);
};

struct keymaster_srv_ctx {
    handle_t port_secure;
    handle_t port_non_secure;
};

static void keymaster_port_handler_secure(const uevent_t* ev, void* priv);
static void keymaster_port_handler_non_secure(const uevent_t* ev, void* priv);

static tipc_event_handler keymaster_port_evt_handler_secure = {
        .proc = keymaster_port_handler_secure,
        .priv = NULL,
};

static tipc_event_handler keymaster_port_evt_handler_non_secure = {
        .proc = keymaster_port_handler_non_secure,
        .priv = NULL,
};

static void keymaster_chan_handler(const uevent_t* ev, void* priv);

TrustyKeymaster* device;

static long handle_port_errors(const uevent_t* ev) {
    if ((ev->event & IPC_HANDLE_POLL_ERROR) ||
        (ev->event & IPC_HANDLE_POLL_HUP) ||
        (ev->event & IPC_HANDLE_POLL_MSG) ||
        (ev->event & IPC_HANDLE_POLL_SEND_UNBLOCKED)) {
        /* should never happen with port handles */
        LOG_E("error event (0x%x) for port (%d)", ev->event, ev->handle);
        return ERR_BAD_STATE;
    }

    return NO_ERROR;
}

static int wait_to_send(handle_t session, struct ipc_msg* msg) {
    int rc;
    struct uevent ev = UEVENT_INITIAL_VALUE(ev);

    rc = wait(session, &ev, INFINITE_TIME);
    if (rc < 0) {
        LOG_E("failed to wait for outgoing queue to free up");
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

static long send_response(handle_t chan,
                          uint32_t cmd,
                          uint8_t* out_buf,
                          uint32_t out_buf_size) {
    struct keymaster_message km_msg;
    km_msg.cmd = cmd | KEYMASTER_RESP_BIT;
    struct iovec iov[2] = {{&km_msg, sizeof(km_msg)}, {nullptr, 0}};
    ipc_msg_t msg = {2, iov, 0, NULL};
    uint32_t msg_size;
    uint32_t bytes_remaining = out_buf_size;
    uint32_t bytes_sent = 0;
    uint32_t max_msg_size = KEYMASTER_MAX_BUFFER_LENGTH - 64;

    do {
        msg_size = MIN(max_msg_size, bytes_remaining);
        if (msg_size == bytes_remaining) {
            km_msg.cmd = km_msg.cmd | KEYMASTER_STOP_BIT;
        }
        iov[1] = {out_buf + bytes_sent, msg_size};

        long rc = send_msg(chan, &msg);
        if (rc == ERR_NOT_ENOUGH_BUFFER) {
            rc = wait_to_send(chan, &msg);
        }

        // fatal error
        if (rc < 0) {
            LOG_E("failed (%ld) to send_msg for chan (%d)", rc, chan);
            return rc;
        }
        bytes_remaining -= msg_size;
        bytes_sent += msg_size;
    } while (bytes_remaining);

    return NO_ERROR;
}

static long send_error_response(handle_t chan,
                                uint32_t cmd,
                                keymaster_error_t err) {
    return send_response(chan, cmd, reinterpret_cast<uint8_t*>(&err),
                         sizeof(err));
}

/*
 * deseralize_request and serialize_request are used by the different
 * overloads of the do_dispatch template to handle the new API signatures
 * that keymaster is migrating to.
 */
template <typename Request>
static long deserialize_request(struct keymaster_message* msg,
                                uint32_t payload_size,
                                Request& req) {
    const uint8_t* payload = msg->payload;
    if (!req.Deserialize(&payload, msg->payload + payload_size))
        return ERR_NOT_VALID;

    return NO_ERROR;
}

template <typename Response>
static long serialize_response(Response& rsp,
                               keymaster::UniquePtr<uint8_t[]>* out,
                               uint32_t* out_size) {
    *out_size = rsp.SerializedSize();

    out->reset(new (std::nothrow) uint8_t[*out_size]);
    if (out->get() == NULL) {
        *out_size = 0;
        return ERR_NO_MEMORY;
    }

    rsp.Serialize(out->get(), out->get() + *out_size);

    return NO_ERROR;
}

template <typename Keymaster, typename Request, typename Response>
static long do_dispatch(void (Keymaster::*operation)(const Request&, Response*),
                        struct keymaster_message* msg,
                        uint32_t payload_size,
                        keymaster::UniquePtr<uint8_t[]>* out,
                        uint32_t* out_size) {
    long err;
    Request req(device->message_version());

    err = deserialize_request(msg, payload_size, req);
    if (err != NO_ERROR)
        return err;

    Response rsp(device->message_version());
    (device->*operation)(req, &rsp);
    LOG_D("do_dispatch #1 err: %d", rsp.error);

    if (msg->cmd == KM_CONFIGURE) {
        device->set_configure_error(rsp.error);
    }

    err = serialize_response(rsp, out, out_size);
    LOG_D("do_dispatch #1: serialized response, %d bytes", *out_size);
    if (err != NO_ERROR) {
        LOG_E("Error serializing response: %ld", err);
    }

    return err;
}

/*
 * Keymaster is migrating to new API signatures.
 * This overloaded dispatch is used for methods that accept one Request argument
 * and return a Response (e.g. COMPUTE_SHARED_HMAC_RESPONSE)
 */
template <typename Keymaster, typename Request, typename Response>
static long do_dispatch(Response (Keymaster::*operation)(const Request&),
                        struct keymaster_message* msg,
                        uint32_t payload_size,
                        keymaster::UniquePtr<uint8_t[]>* out,
                        uint32_t* out_size) {
    long err;
    Request req(device->message_version());

    err = deserialize_request(msg, payload_size, req);
    if (err != NO_ERROR)
        return err;

    Response rsp = ((device->*operation)(req));
    LOG_D("do_dispatch #2 err: %d", rsp.error);

    if (msg->cmd == KM_CONFIGURE) {
        device->set_configure_error(rsp.error);
    }

    err = serialize_response(rsp, out, out_size);
    LOG_D("do_dispatch #2: serialized response, %d bytes", *out_size);
    if (err != NO_ERROR) {
        LOG_E("Error serializing response: %ld", err);
    }

    return err;
}

/* Keymaster is migrating to new API signatures.
 * This overloaded dispatch is used for methods that do not have arguments
 * and return a Response (e.g. GET_HMAC_SHARING_PARAMETERS)
 * */
template <typename Keymaster, typename Response>
static long do_dispatch(Response (Keymaster::*operation)(),
                        struct keymaster_message* msg,
                        uint32_t payload_size,
                        keymaster::UniquePtr<uint8_t[]>* out,
                        uint32_t* out_size) {
    long err;
    Response rsp = ((device->*operation)());
    LOG_D("do_dispatch #3 err: %d", rsp.error);

    if (msg->cmd == KM_CONFIGURE) {
        device->set_configure_error(rsp.error);
    }

    err = serialize_response(rsp, out, out_size);
    LOG_D("do_dispatch #3: serialized response, %d bytes", *out_size);
    if (err != NO_ERROR) {
        LOG_E("Error serializing response: %ld", err);
    }

    return err;
}

static long get_auth_token_key(keymaster::UniquePtr<uint8_t[]>* key_buf,
                               uint32_t* key_size) {
    keymaster_key_blob_t key;
    long rc = device->GetAuthTokenKey(&key);

    if (rc != NO_ERROR) {
        return rc;
    }

    if (key.key_material_size > KEYMASTER_MAX_BUFFER_LENGTH) {
        return ERR_NOT_ENOUGH_BUFFER;
    }

    key_buf->reset(new (std::nothrow) uint8_t[key.key_material_size]);
    if (key_buf->get() == NULL) {
        return ERR_NO_MEMORY;
    }

    *key_size = key.key_material_size;

    memcpy(key_buf->get(), key.key_material, key.key_material_size);
    return NO_ERROR;
}

static long get_device_info(keymaster::UniquePtr<uint8_t[]>* ids_buf,
                            uint32_t* buf_size) {
    auto ids = device->GetDeviceInfo();

    if (ids->encodedSize() > KEYMASTER_MAX_BUFFER_LENGTH) {
        return ERR_NOT_ENOUGH_BUFFER;
    }

    ids_buf->reset(new (std::nothrow) uint8_t[ids->encodedSize()]);
    if (ids_buf->get() == NULL) {
        return ERR_NO_MEMORY;
    }

    *buf_size = ids->encodedSize();
    ids->encode(ids_buf->get(), ids_buf->get() + ids->encodedSize());
    return NO_ERROR;
}

static long keymaster_dispatch_secure(keymaster_chan_ctx* ctx,
                                      keymaster_message* msg,
                                      uint32_t payload_size,
                                      keymaster::UniquePtr<uint8_t[]>* out,
                                      uint32_t* out_size) {
    if (msg->cmd == KM_SET_ATTESTATION_IDS_SECURE &&
        !keymaster_check_secure_target_access_policy_provisioning(&ctx->uuid)) {
        LOG_E("Command %d by this UUID is not allowed\n", msg->cmd);
        return ERR_ACCESS_DENIED;
    }

    switch (msg->cmd) {
    case KM_GET_AUTH_TOKEN_KEY:
        return get_auth_token_key(out, out_size);
    case KM_GET_DEVICE_INFO:
        return get_device_info(out, out_size);
    case KM_SET_ATTESTATION_IDS_SECURE:
        LOG_D("Dispatching SET_ATTESTATION_IDS_SECURE, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SetAttestationIds, msg,
                           payload_size, out, out_size);
    default:
        return ERR_NOT_IMPLEMENTED;
    }
}

static bool system_state_provisioning_allowed_at_boot(void) {
    uint64_t value = system_state_get_flag_default(
            SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED,
            SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_NOT_ALLOWED);
    return value == SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED ||
           value ==
                   SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED_AT_BOOT;
}

static bool provisioning_allowed(void) {
    if (device->ConfigureCalled()) {
        return system_state_provisioning_allowed();
    } else {
        return system_state_provisioning_allowed_at_boot();
    }
}

// Returns true if |cmd| is only allowed in provisioning mode
static bool cmd_is_provisioning(uint32_t cmd) {
    return (cmd == KM_SET_ATTESTATION_KEY || cmd == KM_SET_ATTESTATION_IDS ||
            cmd == KM_SET_ATTESTATION_IDS_KM3 ||
            cmd == KM_APPEND_ATTESTATION_CERT_CHAIN ||
            cmd == KM_CLEAR_ATTESTATION_CERT_CHAIN ||
            cmd == KM_SET_WRAPPED_ATTESTATION_KEY);
}

// Returns true if |cmd| is called from the bootloader
static bool cmd_is_from_bootloader(uint32_t cmd) {
    return (cmd == KM_SET_BOOT_PARAMS || cmd == KM_ATAP_GET_CA_REQUEST ||
            cmd == KM_ATAP_SET_CA_RESPONSE_BEGIN ||
            cmd == KM_ATAP_SET_CA_RESPONSE_UPDATE ||
            cmd == KM_ATAP_SET_CA_RESPONSE_FINISH || cmd == KM_ATAP_READ_UUID ||
            cmd == KM_SET_PRODUCT_ID || cmd == KM_CONFIGURE_BOOT_PATCHLEVEL);
}

// Returns true if |cmd| can be used before the configure command
static bool cmd_allowed_before_configure(uint32_t cmd) {
    return cmd == KM_CONFIGURE || cmd == KM_GET_VERSION ||
           cmd_is_from_bootloader(cmd) || cmd_is_provisioning(cmd);
}

static long keymaster_dispatch_non_secure(keymaster_chan_ctx* ctx,
                                          keymaster_message* msg,
                                          uint32_t payload_size,
                                          keymaster::UniquePtr<uint8_t[]>* out,
                                          uint32_t* out_size) {
    if (msg->cmd == KM_GET_VERSION || msg->cmd == KM_GET_VERSION_2) {
        // KM_GET_VERSION and KM_GET_VERSION_2 commands are always allowed
    } else if (!device->ConfigureCalled()) {
        if (!cmd_allowed_before_configure(msg->cmd)) {
            LOG_E("Command %d not allowed before configure command\n",
                  msg->cmd);
            return ERR_NOT_CONFIGURED;
        }
    } else if (device->ConfigureCalled()) {
        if (device->get_configure_error() != KM_ERROR_OK) {
            LOG_E("Previous configure command failed\n");
            return ERR_NOT_CONFIGURED;
        } else if (cmd_is_from_bootloader(msg->cmd)) {
            LOG_E("Bootloader command %d not allowed after configure command\n",
                  msg->cmd);
            return ERR_NOT_IMPLEMENTED;
        }
    }

    if (cmd_is_provisioning(msg->cmd) && !provisioning_allowed()) {
        LOG_E("Provisioning command %d not allowed\n", msg->cmd);
        return ERR_NOT_IMPLEMENTED;
    }

    switch (static_cast<keymaster_command>(msg->cmd)) {
    case KM_GENERATE_KEY:
        LOG_D("Dispatching GENERATE_KEY, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GenerateKey, msg, payload_size,
                           out, out_size);

    case KM_BEGIN_OPERATION:
        LOG_D("Dispatching BEGIN_OPERATION, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::BeginOperation, msg, payload_size,
                           out, out_size);

    case KM_UPDATE_OPERATION:
        LOG_D("Dispatching UPDATE_OPERATION, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::UpdateOperation, msg, payload_size,
                           out, out_size);

    case KM_FINISH_OPERATION:
        LOG_D("Dispatching FINISH_OPERATION, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::FinishOperation, msg, payload_size,
                           out, out_size);

    case KM_IMPORT_KEY:
        LOG_D("Dispatching IMPORT_KEY, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::ImportKey, msg, payload_size, out,
                           out_size);

    case KM_EXPORT_KEY:
        LOG_D("Dispatching EXPORT_KEY, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::ExportKey, msg, payload_size, out,
                           out_size);

    case KM_GET_VERSION:
        LOG_I("Dispatching GET_VERSION, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GetVersion, msg, payload_size, out,
                           out_size);

    case KM_GET_VERSION_2:
        LOG_I("Dispatching GET_VERSION_2, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GetVersion2, msg, payload_size,
                           out, out_size);

    case KM_ADD_RNG_ENTROPY:
        LOG_D("Dispatching ADD_RNG_ENTROPY, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::AddRngEntropy, msg, payload_size,
                           out, out_size);

    case KM_GET_SUPPORTED_ALGORITHMS:
        LOG_D("Dispatching GET_SUPPORTED_ALGORITHMS, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SupportedAlgorithms, msg,
                           payload_size, out, out_size);

    case KM_GET_SUPPORTED_BLOCK_MODES:
        LOG_D("Dispatching GET_SUPPORTED_BLOCK_MODES, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SupportedBlockModes, msg,
                           payload_size, out, out_size);

    case KM_GET_SUPPORTED_PADDING_MODES:
        LOG_D("Dispatching GET_SUPPORTED_PADDING_MODES, size: %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::SupportedPaddingModes, msg,
                           payload_size, out, out_size);

    case KM_GET_SUPPORTED_DIGESTS:
        LOG_D("Dispatching GET_SUPPORTED_DIGESTS, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SupportedDigests, msg,
                           payload_size, out, out_size);

    case KM_GET_SUPPORTED_IMPORT_FORMATS:
        LOG_D("Dispatching GET_SUPPORTED_IMPORT_FORMATS, size: %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::SupportedImportFormats, msg,
                           payload_size, out, out_size);

    case KM_GET_SUPPORTED_EXPORT_FORMATS:
        LOG_D("Dispatching GET_SUPPORTED_EXPORT_FORMATS, size: %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::SupportedExportFormats, msg,
                           payload_size, out, out_size);

    case KM_GET_KEY_CHARACTERISTICS:
        LOG_D("Dispatching GET_KEY_CHARACTERISTICS, size: %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GetKeyCharacteristics, msg,
                           payload_size, out, out_size);

    case KM_ABORT_OPERATION:
        LOG_D("Dispatching ABORT_OPERATION, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::AbortOperation, msg, payload_size,
                           out, out_size);

    case KM_ATTEST_KEY:
        LOG_D("Dispatching ATTEST_KEY, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::AttestKey, msg, payload_size, out,
                           out_size);

    case KM_UPGRADE_KEY:
        LOG_D("Dispatching UPGRADE_KEY, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::UpgradeKey, msg, payload_size, out,
                           out_size);

    case KM_CONFIGURE:
        LOG_D("Dispatching CONFIGURE, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::Configure, msg, payload_size, out,
                           out_size);

    case KM_GET_HMAC_SHARING_PARAMETERS:
        LOG_D("Dispatching GET_HMAC_SHARING_PARAMETERS, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GetHmacSharingParameters, msg,
                           payload_size, out, out_size);

    case KM_COMPUTE_SHARED_HMAC:
        LOG_D("Dispatching COMPUTE_SHARED_HMAC, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::ComputeSharedHmac, msg,
                           payload_size, out, out_size);

    case KM_VERIFY_AUTHORIZATION:
        LOG_D("Dispatching VERIFY_AUTHORIZATION, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::VerifyAuthorization, msg,
                           payload_size, out, out_size);

    case KM_IMPORT_WRAPPED_KEY:
        LOG_D("Dispatching IMPORT_WRAPPED_KEY, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::ImportWrappedKey, msg,
                           payload_size, out, out_size);

    case KM_DELETE_KEY:
        LOG_D("Dispatching DELETE_KEY, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::DeleteKey, msg, payload_size, out,
                           out_size);

    case KM_DELETE_ALL_KEYS:
        LOG_D("Dispatching DELETE_ALL_KEYS, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::DeleteAllKeys, msg, payload_size,
                           out, out_size);

    case KM_SET_BOOT_PARAMS:
        LOG_D("Dispatching SET_BOOT_PARAMS, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SetBootParams, msg, payload_size,
                           out, out_size);

    case KM_SET_ATTESTATION_KEY:
        LOG_D("Dispatching SET_ATTESTION_KEY, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SetAttestationKey, msg,
                           payload_size, out, out_size);

    case KM_SET_ATTESTATION_IDS:
        LOG_D("Dispatching SET_ATTESTATION_IDS, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SetAttestationIds, msg,
                           payload_size, out, out_size);

    case KM_SET_ATTESTATION_IDS_KM3:
        LOG_D("Dispatching SET_ATTESTATION_IDS_KM3, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::SetAttestationIdsKM3, msg,
                           payload_size, out, out_size);

    case KM_APPEND_ATTESTATION_CERT_CHAIN:
        LOG_D("Dispatching SET_ATTESTATION_CERT_CHAIN, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::AppendAttestationCertChain, msg,
                           payload_size, out, out_size);

    case KM_ATAP_GET_CA_REQUEST:
        LOG_D("Dispatching KM_ATAP_GET_CA_REQUEST, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::AtapGetCaRequest, msg,
                           payload_size, out, out_size);

    case KM_ATAP_SET_CA_RESPONSE_BEGIN:
        LOG_D("Dispatching KM_ATAP_SET_CA_RESPONSE_BEGIN, size %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::AtapSetCaResponseBegin, msg,
                           payload_size, out, out_size);

    case KM_ATAP_SET_CA_RESPONSE_UPDATE:
        LOG_D("Dispatching KM_ATAP_SET_CA_RESPONSE_UPDATE, size %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::AtapSetCaResponseUpdate, msg,
                           payload_size, out, out_size);

    case KM_ATAP_SET_CA_RESPONSE_FINISH:
        LOG_D("Dispatching KM_ATAP_SET_CA_RESPONSE_FINISH, size %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::AtapSetCaResponseFinish, msg,
                           payload_size, out, out_size);

    case KM_ATAP_READ_UUID:
        LOG_D("Dispatching KM_ATAP_READ_UUID, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::AtapReadUuid, msg, payload_size,
                           out, out_size);

    case KM_SET_PRODUCT_ID:
        LOG_D("Dispatching KM_SET_PRODUCT_ID, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::AtapSetProductId, msg,
                           payload_size, out, out_size);

    case KM_CLEAR_ATTESTATION_CERT_CHAIN:
        LOG_D("Dispatching KM_CLEAR_ATTESTATION_CERT_CHAIN, size %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::ClearAttestationCertChain, msg,
                           payload_size, out, out_size);

    case KM_SET_WRAPPED_ATTESTATION_KEY:
        LOG_D("Dispatching KM_SET_WRAPPED_ATTESTATION_KEY, size %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::SetWrappedAttestationKey, msg,
                           payload_size, out, out_size);

    case KM_DESTROY_ATTESTATION_IDS:
        LOG_E("Dispatching destroy attestation IDs, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::DestroyAttestationIds, msg,
                           payload_size, out, out_size);

    case KM_EARLY_BOOT_ENDED:
        LOG_D("Dispatching KM_EARLY_BOOT_ENDED, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::EarlyBootEnded, msg, payload_size,
                           out, out_size);

    case KM_DEVICE_LOCKED:
        LOG_D("Dispatching KM_DEVICE_LOCKED, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::DeviceLocked, msg, payload_size,
                           out, out_size);

    case KM_GENERATE_RKP_KEY:
        LOG_D("Dispatching KM_GENERATE_RKP_KEY, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GenerateRkpKey, msg, payload_size,
                           out, out_size);

    case KM_GENERATE_CSR:
        LOG_D("Dispatching KM_GENERATE_CSR, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GenerateCsr, msg, payload_size,
                           out, out_size);

    case KM_GENERATE_CSR_V2:
        LOG_D("Dispatching KM_GENERATE_CSR_V2, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GenerateCsrV2, msg, payload_size,
                           out, out_size);

    case KM_CONFIGURE_VENDOR_PATCHLEVEL:
        LOG_D("Dispatching KM_CONFIGURE_VENDOR_PATCHLEVEL, size %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::ConfigureVendorPatchlevel, msg,
                           payload_size, out, out_size);

    case KM_CONFIGURE_BOOT_PATCHLEVEL:
        LOG_D("Dispatching KM_CONFIGURE_BOOT_PATCHLEVEL, size %d",
              payload_size);
        return do_dispatch(&TrustyKeymaster::ConfigureBootPatchlevel, msg,
                           payload_size, out, out_size);

    case KM_GET_ROOT_OF_TRUST:
        LOG_D("Dispatching KM_GET_ROOT_OF_TRUST, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GetRootOfTrust, msg, payload_size,
                           out, out_size);

    case KM_GET_HW_INFO:
        LOG_D("Dispatching KM_GET_HW_INFO, size %d", payload_size);
        return do_dispatch(&TrustyKeymaster::GetHwInfo, msg, payload_size, out,
                           out_size);
    }

    LOG_E("Cannot dispatch unknown command %d", msg->cmd);
    return ERR_NOT_IMPLEMENTED;
}

static bool keymaster_port_accessible(uuid_t* uuid, bool secure) {
    return !secure || keymaster_check_target_access_policy(uuid);
}

static keymaster_chan_ctx* keymaster_ctx_open(handle_t chan,
                                              uuid_t* uuid,
                                              bool secure) {
    if (!keymaster_port_accessible(uuid, secure)) {
        LOG_E("access denied for client uuid");
        return NULL;
    }

    keymaster_chan_ctx* ctx = new (std::nothrow) keymaster_chan_ctx;
    if (ctx == NULL) {
        return ctx;
    }

    ctx->handler.proc = &keymaster_chan_handler;
    ctx->handler.priv = ctx;
    ctx->uuid = *uuid;
    ctx->chan = chan;
    ctx->dispatch = secure ? &keymaster_dispatch_secure
                           : &keymaster_dispatch_non_secure;
    return ctx;
}

static void keymaster_ctx_close(keymaster_chan_ctx* ctx) {
    close(ctx->chan);
    delete ctx;
}

static long handle_msg(keymaster_chan_ctx* ctx) {
    handle_t chan = ctx->chan;

    /* get message info */
    ipc_msg_info_t msg_inf;
    int rc = get_msg(chan, &msg_inf);
    if (rc == ERR_NO_MSG)
        return NO_ERROR; /* no new messages */

    // fatal error
    if (rc != NO_ERROR) {
        LOG_E("failed (%d) to get_msg for chan (%d), closing connection", rc,
              chan);
        return rc;
    }

    // allocate msg_buf, with one extra byte for null-terminator
    keymaster::UniquePtr<uint8_t[]> msg_buf(new (std::nothrow)
                                                    uint8_t[msg_inf.len + 1]);
    if (msg_buf.get() == nullptr) {
        return ERR_NO_MEMORY;
    }
    msg_buf[msg_inf.len] = 0;

    /* read msg content */
    struct iovec iov = {msg_buf.get(), msg_inf.len};
    ipc_msg_t msg = {1, &iov, 0, NULL};

    rc = read_msg(chan, msg_inf.id, 0, &msg);

    // retire the message (note msg_inf.id becomes invalid after put_msg)
    put_msg(chan, msg_inf.id);

    // fatal error
    if (rc < 0) {
        LOG_E("failed to read msg (%d)", rc);
        return rc;
    }
    LOG_D("Read %d-byte message", rc);

    if (((unsigned long)rc) < sizeof(keymaster_message)) {
        LOG_E("invalid message of size (%d)", rc);
        return ERR_NOT_VALID;
    }

    keymaster::UniquePtr<uint8_t[]> out_buf;
    uint32_t out_buf_size = 0;
    keymaster_message* in_msg =
            reinterpret_cast<keymaster_message*>(msg_buf.get());

    rc = ctx->dispatch(ctx, in_msg, msg_inf.len - sizeof(*in_msg), &out_buf,
                       &out_buf_size);
    if (rc == ERR_NOT_CONFIGURED) {
        LOG_E("configure error (%d)", rc);
        return send_error_response(chan, in_msg->cmd,
                                   device->get_configure_error());
    } else if (rc < 0) {
        LOG_E("error handling message (%d)", rc);
        return send_error_response(chan, in_msg->cmd, KM_ERROR_UNKNOWN_ERROR);
    }

    LOG_D("Sending %d-byte response", out_buf_size);
    return send_response(chan, in_msg->cmd, out_buf.get(), out_buf_size);
}

static void keymaster_chan_handler(const uevent_t* ev, void* priv) {
    keymaster_chan_ctx* ctx = reinterpret_cast<keymaster_chan_ctx*>(priv);
    if (ctx == NULL) {
        LOG_E("error: no context on channel %d", ev->handle);
        close(ev->handle);
        return;
    }

    if ((ev->event & IPC_HANDLE_POLL_ERROR) ||
        (ev->event & IPC_HANDLE_POLL_READY)) {
        /* close it as it is in an error state */
        LOG_E("error event (0x%x) for chan (%d)", ev->event, ev->handle);
        close(ev->handle);
        return;
    }

    if (ev->event & IPC_HANDLE_POLL_MSG) {
        long rc = handle_msg(ctx);
        if (rc != NO_ERROR) {
            /* report an error and close channel */
            LOG_E("failed (%ld) to handle event on channel %d", rc, ev->handle);
            keymaster_ctx_close(ctx);
            return;
        }
    }

    if (ev->event & IPC_HANDLE_POLL_HUP) {
        /* closed by peer. */
        keymaster_ctx_close(ctx);
        return;
    }
}

static void keymaster_port_handler(const uevent_t* ev,
                                   void* priv,
                                   bool secure) {
    long rc = handle_port_errors(ev);
    if (rc != NO_ERROR) {
        abort();
    }

    uuid_t peer_uuid;
    if (ev->event & IPC_HANDLE_POLL_READY) {
        /* incoming connection: accept it */
        int rc = accept(ev->handle, &peer_uuid);
        if (rc < 0) {
            LOG_E("failed (%d) to accept on port %d", rc, ev->handle);
            return;
        }

        handle_t chan = (handle_t)rc;
        keymaster_chan_ctx* ctx = keymaster_ctx_open(chan, &peer_uuid, secure);
        if (ctx == NULL) {
            LOG_E("failed to allocate context on chan %d", chan);
            close(chan);
            return;
        }

        rc = set_cookie(chan, ctx);
        if (rc < 0) {
            LOG_E("failed (%d) to set_cookie on chan %d", rc, chan);
            keymaster_ctx_close(ctx);
            return;
        }
    }
}

static void keymaster_port_handler_secure(const uevent_t* ev, void* priv) {
    keymaster_port_handler(ev, priv, true);
}

static void keymaster_port_handler_non_secure(const uevent_t* ev, void* priv) {
    keymaster_port_handler(ev, priv, false);
}

static void dispatch_event(const uevent_t* ev) {
    if (ev == NULL)
        return;

    if (ev->event == IPC_HANDLE_POLL_NONE) {
        /* not really an event, do nothing */
        LOG_E("got an empty event");
        return;
    }

    /* check if we have handler */
    tipc_event_handler* handler =
            reinterpret_cast<tipc_event_handler*>(ev->cookie);
    if (handler && handler->proc) {
        /* invoke it */
        handler->proc(ev, handler->priv);
        return;
    }

    /* no handler? close it */
    LOG_E("no handler for event (0x%x) with handle %d", ev->event, ev->handle);

    close(ev->handle);

    return;
}

static long keymaster_ipc_init(keymaster_srv_ctx* ctx) {
    int rc;

    /* Initialize secure-side service */
    rc = port_create(KEYMASTER_SECURE_PORT, 1, KEYMASTER_MAX_BUFFER_LENGTH,
                     IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        LOG_E("Failed (%d) to create port %s", rc, KEYMASTER_SECURE_PORT);
        return rc;
    }

    ctx->port_secure = (handle_t)rc;

    rc = set_cookie(ctx->port_secure, &keymaster_port_evt_handler_secure);
    if (rc) {
        LOG_E("failed (%d) to set_cookie on port %d", rc, ctx->port_secure);
        close(ctx->port_secure);
        return rc;
    }

    /* initialize non-secure side service */
    rc = port_create(KEYMASTER_PORT, 1, KEYMASTER_MAX_BUFFER_LENGTH,
                     IPC_PORT_ALLOW_NS_CONNECT);
    if (rc < 0) {
        LOG_E("Failed (%d) to create port %s", rc, KEYMASTER_PORT);
        return rc;
    }

    ctx->port_non_secure = (handle_t)rc;

    rc = set_cookie(ctx->port_non_secure,
                    &keymaster_port_evt_handler_non_secure);
    if (rc) {
        LOG_E("failed (%d) to set_cookie on port %d", rc, ctx->port_non_secure);
        close(ctx->port_non_secure);
        return rc;
    }

    return NO_ERROR;
}

int main(void) {
    long rc;
    uevent_t event;

    device = new (std::nothrow)
            TrustyKeymaster(new (std::nothrow) TrustyKeymasterContext, 16);

    TrustyLogger::initialize();

    LOG_I("Initializing");

    // Run the BoringSSL self-tests
    if (!BORINGSSL_self_test()) {
        LOG_E("BoringSSL self-test: FAILED");
        return ERR_GENERIC;
    } else {
        LOG_I("BoringSSL self-test: PASSED");
    }

    keymaster_srv_ctx ctx;
    rc = keymaster_ipc_init(&ctx);
    if (rc < 0) {
        LOG_E("failed (%ld) to initialize keymaster", rc);
        return rc;
    }

    /* enter main event loop */
    while (true) {
        event.handle = INVALID_IPC_HANDLE;
        event.event = 0;
        event.cookie = NULL;

        rc = wait_any(&event, INFINITE_TIME);
        if (rc < 0) {
            LOG_E("wait_any failed (%ld)", rc);
            break;
        }

        if (rc == NO_ERROR) { /* got an event */
            dispatch_event(&event);
        }
    }

    return 0;
}
