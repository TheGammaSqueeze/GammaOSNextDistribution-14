/*
 * Copyright 2015 The Android Open Source Project
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

#include "trusty_gatekeeper.h"

#include <inttypes.h>
#include <trusty/time.h>
#include <uapi/err.h>

#include <lib/hwkey/hwkey.h>
#include <lib/keymaster/keymaster.h>
#include <lib/rng/trusty_rng.h>
#include <lib/storage/storage.h>

#include <openssl/hmac.h>

#define CALLS_BETWEEN_RNG_RESEEDS 32
#define RNG_RESEED_SIZE 64

#define HMAC_SHA_256_KEY_SIZE 32

#define GATEKEEPER_PREFIX "gatekeeper."

#define STORAGE_ID_LENGTH_MAX 64

#define MAX_FAILURE_RECORDS 10

namespace gatekeeper {

static const uint8_t DERIVATION_DATA[HMAC_SHA_256_KEY_SIZE] =
        "TrustyGateKeeperDerivationData0";

TrustyGateKeeper::TrustyGateKeeper() : GateKeeper(),
    cached_auth_token_key_len_(0) {
    rng_initialized_ = false;
    calls_since_reseed_ = 0;
    num_mem_records_ = 0;

    SeedRngIfNeeded();
}

long TrustyGateKeeper::OpenSession() {
    if (!SeedRngIfNeeded()) {
        return ERR_NOT_READY;
    }

    return DerivePasswordKey();
}

void TrustyGateKeeper::CloseSession() {
    ClearPasswordKey();
}

bool TrustyGateKeeper::SeedRngIfNeeded() {
    if (ShouldReseedRng())
        rng_initialized_ = ReseedRng();
    return rng_initialized_;
}

bool TrustyGateKeeper::ShouldReseedRng() {
    if (!rng_initialized_) {
        return true;
    }

    if (++calls_since_reseed_ % CALLS_BETWEEN_RNG_RESEEDS == 0) {
        return true;
    }
    return false;
}

bool TrustyGateKeeper::ReseedRng() {
    UniquePtr<uint8_t[]> rand_seed(new uint8_t[RNG_RESEED_SIZE]);
    memset(rand_seed.get(), 0, RNG_RESEED_SIZE);
    if (trusty_rng_secure_rand(rand_seed.get(), RNG_RESEED_SIZE) != NO_ERROR) {
        return false;
    }

    trusty_rng_add_entropy(rand_seed.get(), RNG_RESEED_SIZE);
    return true;
}

long TrustyGateKeeper::DerivePasswordKey() {
    long rc = hwkey_open();
    if (rc < 0) {
        return rc;
    }

    hwkey_session_t session = (hwkey_session_t)rc;

    password_key_.reset(new uint8_t[HMAC_SHA_256_KEY_SIZE]);

    uint32_t kdf_version = HWKEY_KDF_VERSION_1;
    rc = hwkey_derive(session, &kdf_version, DERIVATION_DATA,
                      password_key_.get(), HMAC_SHA_256_KEY_SIZE);

    hwkey_close(session);
    return rc;
}

void TrustyGateKeeper::ClearPasswordKey() {
    memset_s(password_key_.get(), 0, HMAC_SHA_256_KEY_SIZE);
    password_key_.reset();
}

/*
 * While the GetAuthTokenKey header file says this value cannot be cached,
 * after consulting with the GK/KM team this is incorrect - this is a per-boot
 * key, and so in-memory caching is acceptable.
 */
bool TrustyGateKeeper::GetAuthTokenKey(const uint8_t** auth_token_key,
                                       uint32_t* length) const {
    *length = 0;
    *auth_token_key = nullptr;

    if (!cached_auth_token_key_) {
        long rc = keymaster_open();
        if (rc < 0) {
            return false;
        }

        keymaster_session_t session = (keymaster_session_t)rc;

        uint8_t* key = nullptr;
        uint32_t local_length = 0;

        rc = keymaster_get_auth_token_key(session, &key, &local_length);
        keymaster_close(session);

        if (rc == NO_ERROR) {
            cached_auth_token_key_.reset(key);
            cached_auth_token_key_len_ = local_length;
        } else {
            return false;
        }
    }

    *auth_token_key = cached_auth_token_key_.get();
    *length = cached_auth_token_key_len_;

    return true;
}

void TrustyGateKeeper::GetPasswordKey(const uint8_t** password_key,
                                      uint32_t* length) {
    *password_key = const_cast<const uint8_t*>(password_key_.get());
    *length = HMAC_SHA_256_KEY_SIZE;
}

void TrustyGateKeeper::ComputePasswordSignature(uint8_t* signature,
                                                uint32_t signature_length,
                                                const uint8_t* key,
                                                uint32_t key_length,
                                                const uint8_t* password,
                                                uint32_t password_length,
                                                salt_t salt) const {
    // todo: heap allocate
    uint8_t salted_password[password_length + sizeof(salt)];
    memcpy(salted_password, &salt, sizeof(salt));
    memcpy(salted_password + sizeof(salt), password, password_length);
    ComputeSignature(signature, signature_length, key, key_length,
                     salted_password, password_length + sizeof(salt));
}

void TrustyGateKeeper::GetRandom(void* random, uint32_t requested_size) const {
    if (random == NULL)
        return;
    trusty_rng_secure_rand(reinterpret_cast<uint8_t*>(random), requested_size);
}

void TrustyGateKeeper::ComputeSignature(uint8_t* signature,
                                        uint32_t signature_length,
                                        const uint8_t* key,
                                        uint32_t key_length,
                                        const uint8_t* message,
                                        const uint32_t length) const {
    uint8_t buf[HMAC_SHA_256_KEY_SIZE];
    unsigned int buf_len;

    HMAC(EVP_sha256(), key, key_length, message, length, buf, &buf_len);
    size_t to_write = buf_len;
    if (buf_len > signature_length)
        to_write = signature_length;
    memset(signature, 0, signature_length);
    memcpy(signature, buf, to_write);
}

uint64_t TrustyGateKeeper::GetMillisecondsSinceBoot() const {
    int rc;
    int64_t secure_time_ns = 0;
    rc = trusty_gettime(0, &secure_time_ns);
    if (rc != NO_ERROR) {
        secure_time_ns = 0;
        TLOGE("%s Error:[0x%x].\n", __func__, rc);
    }
    return secure_time_ns / 1000 / 1000;
}

bool TrustyGateKeeper::GetSecureFailureRecord(uint32_t uid,
                                              secure_id_t user_id,
                                              failure_record_t* record) {
    storage_session_t session;
    int rc = storage_open_session(&session, GATEKEEPER_STORAGE_PORT);
    if (rc < 0) {
        TLOGE("Error: [%d] opening storage session\n", rc);
        return false;
    }

    char id[STORAGE_ID_LENGTH_MAX];
    memset(id, 0, sizeof(id));

    file_handle_t handle;
    snprintf(id, STORAGE_ID_LENGTH_MAX, GATEKEEPER_PREFIX "%u", uid);
    rc = storage_open_file(session, &handle, id, 0, 0);
    if (rc < 0) {
        TLOGE("Error:[%d] opening storage object.\n", rc);
        storage_close_session(session);
        return false;
    }

    failure_record_t owner_record;
    rc = storage_read(handle, 0, &owner_record, sizeof(owner_record));
    storage_close_file(handle);
    storage_close_session(session);

    if (rc < 0) {
        TLOGE("Error:[%d] reading storage object.\n", rc);
        return false;
    }

    if ((size_t)rc < sizeof(owner_record)) {
        TLOGE("Error: invalid object size [%d].\n", rc);
        return false;
    }

    if (owner_record.secure_user_id != user_id) {
        TLOGE("Error:[%" PRIu64 " != %" PRIu64 "] secure storage corrupt.\n",
              owner_record.secure_user_id, user_id);
        return false;
    }

    *record = owner_record;
    return true;
}

bool TrustyGateKeeper::GetFailureRecord(uint32_t uid,
                                        secure_id_t user_id,
                                        failure_record_t* record,
                                        bool secure) {
    if (secure) {
        return GetSecureFailureRecord(uid, user_id, record);
    } else {
        return GetMemoryRecord(uid, user_id, record);
    }
}

bool TrustyGateKeeper::ClearFailureRecord(uint32_t uid,
                                          secure_id_t user_id,
                                          bool secure) {
    failure_record_t record;
    record.secure_user_id = user_id;
    record.last_checked_timestamp = 0;
    record.failure_counter = 0;
    return WriteFailureRecord(uid, &record, secure);
}

bool TrustyGateKeeper::WriteSecureFailureRecord(uint32_t uid,
                                                failure_record_t* record) {
    storage_session_t session;
    int rc = storage_open_session(&session, GATEKEEPER_STORAGE_PORT);
    if (rc < 0) {
        TLOGE("Error: [%d] failed to open storage session\n", rc);
        return false;
    }

    char id[STORAGE_ID_LENGTH_MAX];
    memset(id, 0, sizeof(id));
    snprintf(id, STORAGE_ID_LENGTH_MAX, GATEKEEPER_PREFIX "%u", uid);

    file_handle_t handle;
    rc = storage_open_file(session, &handle, id, STORAGE_FILE_OPEN_CREATE, 0);
    if (rc < 0) {
        TLOGE("Error: [%d] failed to open storage object %s\n", rc, id);
        storage_close_session(session);
        return false;
    }

    rc = storage_write(handle, 0, record, sizeof(*record), STORAGE_OP_COMPLETE);
    storage_close_file(handle);
    storage_close_session(session);

    if (rc < 0) {
        TLOGE("Error:[%d] writing storage object.\n", rc);
        return false;
    }

    if ((size_t)rc < sizeof(*record)) {
        TLOGE("Error: invalid object size [%d].\n", rc);
        return false;
    }

    return true;
}

bool TrustyGateKeeper::WriteFailureRecord(uint32_t uid,
                                          failure_record_t* record,
                                          bool secure) {
    if (secure) {
        return WriteSecureFailureRecord(uid, record);
    } else {
        return WriteMemoryRecord(uid, record);
    }
}

bool TrustyGateKeeper::IsHardwareBacked() const {
    return true;
}

void TrustyGateKeeper::InitMemoryRecords() {
    if (!mem_records_.get()) {
        mem_failure_record_t* mem_recs = new mem_failure_record_t[MAX_FAILURE_RECORDS];
        memset(mem_recs, 0, sizeof(*mem_recs));
        mem_records_.reset(mem_recs);
        num_mem_records_ = 0;
    }
}

bool TrustyGateKeeper::GetMemoryRecord(uint32_t uid, secure_id_t user_id,
                                       failure_record_t* record) {
    InitMemoryRecords();

    for (int i = 0; i < num_mem_records_; i++) {
        if (mem_records_[i].uid == uid) {
            if (mem_records_[i].failure_record.secure_user_id == user_id) {
                *record = mem_records_[i].failure_record;
                return true;
            }
            TLOGE("Error:[%" PRIu64 " != %" PRIu64 "] mismatched SID for uid %u.\n",
                  mem_records_[i].failure_record.secure_user_id, user_id, uid);
            return false;
        }
    }

    return false;
}

bool TrustyGateKeeper::WriteMemoryRecord(uint32_t uid, failure_record_t* record) {
    InitMemoryRecords();

    int idx = 0;
    int min_idx = 0;
    uint64_t min_timestamp = ~0ULL;
    for (idx = 0; idx < num_mem_records_; idx++) {
        if (mem_records_[idx].uid == uid) {
            break;
        }

        if (mem_records_[idx].failure_record.last_checked_timestamp <= min_timestamp) {
            min_timestamp = mem_records_[idx].failure_record.last_checked_timestamp;
            min_idx = idx;
        }
    }

    if (idx >= MAX_FAILURE_RECORDS) {
        // replace oldest element
        idx = min_idx;
    } else if (idx == num_mem_records_) {
        num_mem_records_++;
    }

    mem_records_[idx].uid = uid;
    mem_records_[idx].failure_record = *record;
    return true;
}

gatekeeper_error_t TrustyGateKeeper::RemoveUser(uint32_t uid) {
    bool deleted = false;

    // Remove from the in-memory record
    if (mem_records_.get()) {
        int idx = 0;
        for (idx = 0; idx < num_mem_records_; idx++) {
            if (mem_records_[idx].uid == uid) {
                memset(&mem_records_[idx], 0, sizeof(mem_failure_record_t));
                deleted = true;
            }
        }
    }

    storage_session_t session;
    int rc = storage_open_session(&session, GATEKEEPER_STORAGE_PORT);
    if (rc < 0) {
        TLOGE("Error: [%d] opening storage session\n", rc);
        return ERROR_UNKNOWN;
    }

    char id[STORAGE_ID_LENGTH_MAX];
    memset(id, 0, sizeof(id));
    snprintf(id, STORAGE_ID_LENGTH_MAX, GATEKEEPER_PREFIX "%u", uid);

    rc = storage_delete_file(session, id, STORAGE_OP_COMPLETE);
    if (rc < 0) {
        // if the user's record was added to memory, there may not be a
        // record in storage, so only report failures if we haven't already
        // deleted a record from memory.
        storage_close_session(session);
        return deleted ? ERROR_NONE : ERROR_UNKNOWN;
    }
    storage_close_session(session);

    return ERROR_NONE;
}

gatekeeper_error_t TrustyGateKeeper::RemoveAllUsers() {

    storage_session_t session;
    int rc = storage_open_session(&session, GATEKEEPER_STORAGE_PORT);
    if (rc < 0) {
        TLOGE("Error: [%d] opening storage session\n", rc);
        return ERROR_UNKNOWN;
    }

    storage_open_dir_state *state;
    rc = storage_open_dir(session, "", &state);
    if (rc < 0) {
        TLOGE("Error:[%d] opening storage dir.\n", rc);
        storage_close_session(session);
        return ERROR_UNKNOWN;
    }
    while (true) {
        uint8_t dir_flags = 0;
        char name[STORAGE_ID_LENGTH_MAX];
        rc = storage_read_dir(session, state, &dir_flags, name, STORAGE_ID_LENGTH_MAX);
        if (rc < 0) {
            TLOGE("Error:[%d] reading storage dir.\n", rc);
            storage_close_session(session);
            return ERROR_UNKNOWN;
        }
        if ((dir_flags & STORAGE_FILE_LIST_STATE_MASK) == STORAGE_FILE_LIST_END) {
            break;
        }
        if (!strncmp(name, GATEKEEPER_PREFIX, strlen(GATEKEEPER_PREFIX))) {
            rc = storage_delete_file(session, name, 0);
            if (rc < 0) {
                TLOGE("Error:[%d] deleting storage object.\n", rc);
                storage_close_session(session);
                return ERROR_UNKNOWN;
            }
        }
    }
    storage_close_dir(session, state);
    rc = storage_end_transaction(session, true);
    if (rc < 0) {
        TLOGE("Error:[%d] ending storage transaction.\n", rc);
        storage_close_session(session);
        return ERROR_UNKNOWN;
    }
    storage_close_session(session);

    num_mem_records_ = 0;

    return ERROR_NONE;
}

}  // namespace gatekeeper
