/******************************************************************************
 *
 *  The original Work has been changed by NXP.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Copyright 2022-2023 NXP
 *
 ******************************************************************************/
#pragma once

#include <memory>
#include <vector>

#include <aidl/android/hardware/security/sharedsecret/BnSharedSecret.h>
#include <aidl/android/hardware/security/sharedsecret/SharedSecretParameters.h>

#include "CborConverter.h"
#include "JavacardSecureElement.h"

namespace aidl::android::hardware::security::sharedsecret {
using ::keymint::javacard::CborConverter;
using ::keymint::javacard::JavacardSecureElement;
using ndk::ScopedAStatus;
using std::shared_ptr;
using std::vector;

class JavacardSharedSecret : public BnSharedSecret {
  public:
    explicit JavacardSharedSecret(shared_ptr<JavacardSecureElement> card) : card_(std::move(card)) {}
    virtual ~JavacardSharedSecret() {}

    // Methods from ::ndk::ICInterface follow.
    binder_status_t dump(int fd, const char** args, uint32_t num_args) override;

    ScopedAStatus getSharedSecretParameters(SharedSecretParameters* params) override;

    ScopedAStatus computeSharedSecret(const std::vector<SharedSecretParameters>& params,
                                      std::vector<uint8_t>* secret) override;

  private:
    shared_ptr<JavacardSecureElement> card_;
    CborConverter cbor_;
};

}  // namespace aidl::android::hardware::security::sharedsecret
