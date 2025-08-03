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

#define TLOG_TAG "cast-auth-trusty"

#include "cast_auth_impl.h"

#include <binder/RpcServerTrusty.h>
#include <lib/storage/storage.h>
#include <lib/system_state/system_state.h>
#include <lk/err_ptr.h>
#include <openssl/base.h>
#include <openssl/bio.h>
#include <openssl/err.h>
#include <openssl/rsa.h>
#include <openssl/x509.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include "lib/keybox/client/keybox.h"

static const char* kKeyPath = "cast_auth_key";
const int RSA_2048_SIZE_BYTES = 256;
const int UNWRAPPED_KEY_MAX_BYTES = 1200;
const int WRAPPING_MAX_BYTES = 1024;
const int WRAPPED_KEY_MAX_BYTES = UNWRAPPED_KEY_MAX_BYTES + WRAPPING_MAX_BYTES;
const int PAYLOAD_MAX_BYTES = WRAPPED_KEY_MAX_BYTES;
constexpr int BINDER_MAX_BYTES = 128;
constexpr int MESSAGE_MAX_BYTES = PAYLOAD_MAX_BYTES + BINDER_MAX_BYTES;

using android::binder::Status;

bool is_plaintext_rsa_2048_private_key(const std::vector<uint8_t>& key) {
    bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(key.data(), key.size()));
    if (!bio) {
        TLOGE("is_plaintext_rsa_2048_private_key: failed to allocate memory for the "
              "device key\n");
        return ERR_NO_MEMORY;
    }
    bssl::UniquePtr<RSA> rsa(d2i_RSAPrivateKey_bio(bio.get(), NULL));
    return rsa && RSA_size(rsa.get()) == RSA_2048_SIZE_BYTES;
}

class StorageSessionHandle {
public:
    StorageSessionHandle(const char* type) : mSession(STORAGE_INVALID_SESSION) {
        mError = storage_open_session(&mSession, type);
    }
    ~StorageSessionHandle() {
        storage_close_session(mSession);
        mSession = STORAGE_INVALID_SESSION;
    }
    bool valid() { return mSession != STORAGE_INVALID_SESSION; }
    storage_session_t get() { return mSession; }
    int error() { return mError; }

private:
    storage_session_t mSession;
    int mError;
};

Status CastAuthImpl::ProvisionKey(const std::vector<uint8_t>& wrappedKey) {
    uint8_t unwrapped[UNWRAPPED_KEY_MAX_BYTES];
    size_t unwrapped_size = sizeof(unwrapped);
    if (!system_state_provisioning_allowed()) {
        TLOGE("CastAuthImpl::ProvisionKey: provisioning not allowed\n");
        return Status::fromServiceSpecificError(ERR_BAD_STATE);
    }
    int rc = NO_ERROR;
    if (is_plaintext_rsa_2048_private_key(wrappedKey)) {
        if (wrappedKey.size() > UNWRAPPED_KEY_MAX_BYTES)
            rc = ERR_NOT_ENOUGH_BUFFER;
        else {
            unwrapped_size = wrappedKey.size();
            memcpy(unwrapped, wrappedKey.data(), unwrapped_size);
        }
    } else {
        rc = keybox_unwrap(wrappedKey.data(), wrappedKey.size(), unwrapped,
                           unwrapped_size, &unwrapped_size);
    }
    if (rc != NO_ERROR) {
        TLOGE("CastAuthImpl::ProvisionKey: failed to unwrap key: %d\n", rc);
        return Status::fromServiceSpecificError(rc);
    }
    bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(unwrapped, unwrapped_size));
    if (!bio) {
        TLOGE("CastAuthImpl::ProvisionKey: failed to allocate memory for the "
              "device key\n");
        return Status::fromServiceSpecificError(ERR_NO_MEMORY);
    }
    bssl::UniquePtr<RSA> rsa(d2i_RSAPrivateKey_bio(bio.get(), NULL));
    if (!rsa || RSA_size(rsa.get()) != RSA_2048_SIZE_BYTES) {
        TLOGE("CastAuthImpl::ProvisionKey: failed to decode device key\n");
        return Status::fromServiceSpecificError(ERR_NOT_VALID);
    }
    rc = SaveKey(unwrapped, unwrapped_size);
    if (rc != NO_ERROR) {
        return Status::fromServiceSpecificError(rc);
    }
    return Status::ok();
}

Status CastAuthImpl::SignHash(const std::vector<uint8_t>& hash,
                              std::vector<uint8_t>* signature) {
    uint8_t key[UNWRAPPED_KEY_MAX_BYTES];
    size_t key_size = sizeof(key);
    int rc = LoadKey(key, &key_size);
    if (rc != NO_ERROR) {
        TLOGE("CastAuthImpl::SignHash: failed to load device key: %d\n", rc);
        return Status::fromServiceSpecificError(rc);
    }
    bssl::UniquePtr<BIO> bio(BIO_new_mem_buf(key, key_size));
    if (!bio) {
        TLOGE("CastAuthImpl::SignHash: failed to allocate memory for the device "
              "key\n");
        return Status::fromServiceSpecificError(ERR_NO_MEMORY);
    }
    bssl::UniquePtr<RSA> rsa(d2i_RSAPrivateKey_bio(bio.get(), NULL));
    if (!rsa) {
        TLOGE("CastAuthImpl::SignHash: failed to decode device key\n");
        return Status::fromServiceSpecificError(ERR_GENERIC);
    }
    if (!RSA_check_key(rsa.get())) {
        TLOGE("CastAuthImpl::SignHash: RSA key failed check\n");
        return Status::fromServiceSpecificError(ERR_GENERIC);
    }
    size_t expected_size = (size_t)RSA_size(rsa.get());
    signature->resize(expected_size);
    rc = RSA_private_encrypt(hash.size(), hash.data(), signature->data(),
                             rsa.get(), RSA_PKCS1_PADDING);
    if (rc != (int)expected_size) {
        TLOGE("CastAuthImpl::SignHash: RSA_private_encrypt %d \n", rc);
        return Status::fromServiceSpecificError(ERR_GENERIC);
    }
    return Status::ok();
}

int CastAuthImpl::SaveKey(const uint8_t* key, size_t length) {
    if (key == NULL || !length) {
        TLOGE("CastAuthImpl::SaveKey: no keybox provided\n");
        return ERR_GENERIC;
    }
    StorageSessionHandle session(STORAGE_CLIENT_TDP_PORT);
    if (!session.valid()) {
        TLOGE("CastAuthImpl::SaveKey: couldn't open storage session\n");
        return session.error();
    }
    file_handle_t handle;
    int rc = storage_open_file(
            session.get(), &handle, kKeyPath,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE, 0);
    if (rc < 0) {
        TLOGE("CastAuthImpl::SaveKey: failed to open key file: %d\n", rc);
        return rc;
    }
    rc = storage_write(handle, 0, key, length, STORAGE_OP_COMPLETE);
    storage_close_file(handle);
    if (rc < 0) {
        TLOGE("CastAuthImpl::SaveKey: failed to write key: %d\n", rc);
        return rc;
    }
    return NO_ERROR;
}

int CastAuthImpl::LoadKey(uint8_t* key, size_t* length) {
    if (key == NULL || length == NULL) {
        TLOGE("CastAuthImpl::LoadKey: invalid parameters\n");
        return ERR_INVALID_ARGS;
    }
    StorageSessionHandle session(STORAGE_CLIENT_TDP_PORT);
    if (!session.valid()) {
        TLOGE("CastAuthImpl::LoadKey: couldn't open storage session\n");
        return session.error();
    }

    file_handle_t handle;
    int rc = storage_open_file(session.get(), &handle, kKeyPath, 0, 0);
    if (rc < 0) {
        TLOGE("CastAuthImpl::LoadKey: failed to open key file: %d\n", rc);
        return rc;
    }
    storage_off_t keysize;
    rc = storage_get_file_size(handle, &keysize);
    if (rc < 0) {
        TLOGE("CastAuthImpl::LoadKey: couldn't get file size: %d\n", rc);
        storage_close_file(handle);
        return rc;
    }

    if (*length < keysize) {
        TLOGE("CastAuthImpl::LoadKey: output buffer too small, "
              "should be at least %zu bytes\n",
              (size_t)keysize);
        storage_close_file(handle);
        *length = keysize;
        return ERR_NOT_ENOUGH_BUFFER;
    }

    rc = storage_read(handle, 0, key, keysize);
    storage_close_file(handle);
    if (rc < 0) {
        TLOGE("CastAuthImpl::LoadKey: error reading key: %d\n", rc);
        return rc;
    }
    if ((size_t)rc != keysize) {
        TLOGE("CastAuthImpl::LoadKey: error reading key - size (%d) not matching "
              "keysize (%zu)\n",
              rc, (size_t)keysize);
        return ERR_GENERIC;
    }
    *length = keysize;

    return NO_ERROR;
}

int CastAuthImpl::runService() {
    tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return EXIT_FAILURE;
    }

    const auto port_acl = android::RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_TA_CONNECT | IPC_PORT_ALLOW_NS_CONNECT,
    };

    auto srv = android::RpcServerTrusty::make(
            hset, ICastAuth::PORT().c_str(),
            std::make_shared<const android::RpcServerTrusty::PortAcl>(port_acl),
            MESSAGE_MAX_BYTES);
    if (!srv.ok()) {
        TLOGE("Failed to create RpcServer (%d)\n", srv.error());
        return EXIT_FAILURE;
    }

    android::sp<CastAuthImpl> cast_auth = android::sp<CastAuthImpl>::make();
    (*srv)->setRootObject(cast_auth);

    return tipc_run_event_loop(hset);
}
