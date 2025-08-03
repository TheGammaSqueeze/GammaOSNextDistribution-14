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

#ifndef TRUSTY_GATEKEEPER_H_
#define TRUSTY_GATEKEEPER_H_

#include <stdio.h>

#include <gatekeeper/gatekeeper.h>

#define TLOG_TAG "trusty_gatekeeper"
#include <trusty_log.h>

namespace gatekeeper {

template <typename T>
struct FreeDeleter {
    inline void operator()(T* p) const {
        free(p);
    }
};

struct __attribute__((packed)) mem_failure_record_t {
    struct failure_record_t failure_record;
    uint32_t uid;
};

class TrustyGateKeeper : public GateKeeper {
public:
    TrustyGateKeeper();

    long OpenSession();
    void CloseSession();

protected:
    // See gatekeeper/gatekeeper.h for documentation

    virtual bool GetAuthTokenKey(const uint8_t** auth_token_key,
                                 uint32_t* length) const;

    virtual void GetPasswordKey(const uint8_t** password_key, uint32_t* length);

    virtual void ComputePasswordSignature(uint8_t* signature,
                                          uint32_t signature_length,
                                          const uint8_t* key,
                                          uint32_t key_length,
                                          const uint8_t* password,
                                          uint32_t password_length,
                                          salt_t salt) const;

    virtual void GetRandom(void* random, uint32_t requested_size) const;
    virtual void ComputeSignature(uint8_t* signature,
                                  uint32_t signature_length,
                                  const uint8_t* key,
                                  uint32_t key_length,
                                  const uint8_t* message,
                                  const uint32_t length) const;
    virtual uint64_t GetMillisecondsSinceBoot() const;

    virtual bool GetFailureRecord(uint32_t uid,
                                  secure_id_t user_id,
                                  failure_record_t* record,
                                  bool secure);
    virtual bool WriteFailureRecord(uint32_t uid,
                                    failure_record_t* record,
                                    bool secure);
    virtual bool ClearFailureRecord(uint32_t uid,
                                    secure_id_t user_id,
                                    bool secure);
    virtual gatekeeper_error_t RemoveUser(uint32_t uid);
    virtual gatekeeper_error_t RemoveAllUsers();

    virtual bool IsHardwareBacked() const;

private:
    bool SeedRngIfNeeded();
    bool ShouldReseedRng();
    bool ReseedRng();

    long DerivePasswordKey();
    void ClearPasswordKey();

    void InitMemoryRecords();
    bool GetMemoryRecord(uint32_t uid, secure_id_t user_id, failure_record_t* record);
    bool WriteMemoryRecord(uint32_t uid, failure_record_t* record);
    bool GetSecureFailureRecord(uint32_t uid,
                                secure_id_t user_id,
                                failure_record_t* record);
    bool WriteSecureFailureRecord(uint32_t uid, failure_record_t* record);

    UniquePtr<uint8_t[]> password_key_;
    bool rng_initialized_;
    int calls_since_reseed_;

    int num_mem_records_;
    UniquePtr<mem_failure_record_t[]> mem_records_;

    mutable UniquePtr<uint8_t, FreeDeleter<uint8_t>>
            cached_auth_token_key_;
    mutable size_t cached_auth_token_key_len_;
};

}  // namespace gatekeeper

#endif  // TRUSTY_GATEKEEPER_H_
