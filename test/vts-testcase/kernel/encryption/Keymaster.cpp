/*
 * Copyright (C) 2016 The Android Open Source Project
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

// TODO(154013771): this is copied from vold and modified to remove un-needed
// methods and use std::string instead of KeyBuffer. We should instead
// create a library to support both.

#include "Keymaster.h"

#include <android-base/logging.h>

#include <aidl/android/hardware/security/keymint/SecurityLevel.h>
#include <aidl/android/system/keystore2/Domain.h>
#include <aidl/android/system/keystore2/EphemeralStorageKeyResponse.h>
#include <aidl/android/system/keystore2/KeyDescriptor.h>

// Keep these in sync with system/security/keystore2/src/keystore2_main.rs
static constexpr const char keystore2_service_name[] =
    "android.system.keystore2.IKeystoreService/default";

// Keep this in sync with system/sepolicy/private/keystore2_key_contexts
static constexpr const int ROOT_NAMESPACE = 0;

namespace android {
namespace kernel {

static void zeroize_vector(std::vector<uint8_t>& vec) {
  // Not secure, but doesn't really matter since this is just test code.
  memset(vec.data(), 0, vec.size());
}

static bool logKeystore2ExceptionIfPresent(::ndk::ScopedAStatus& rc,
                                           const std::string& func_name) {
  if (rc.isOk()) return false;

  auto exception_code = rc.getExceptionCode();
  if (exception_code == EX_SERVICE_SPECIFIC) {
    LOG(ERROR) << "keystore2 Keystore " << func_name
               << " returned service specific error: "
               << rc.getServiceSpecificError();
  } else {
    LOG(ERROR) << "keystore2 Communication with Keystore " << func_name
               << " failed error: " << exception_code;
  }
  return true;
}

Keymaster::Keymaster() {
  ::ndk::SpAIBinder binder(AServiceManager_getService(keystore2_service_name));
  auto keystore2Service = ks2::IKeystoreService::fromBinder(binder);

  if (!keystore2Service) {
    LOG(ERROR) << "Vold unable to connect to keystore2.";
    return;
  }

  /*
   * There are only two options available to vold for the SecurityLevel:
   * TRUSTED_ENVIRONMENT (TEE) and STRONGBOX. We don't use STRONGBOX because if
   * a TEE is present it will have Weaver, which already strengthens CE, so
   * there's no additional benefit from using StrongBox.
   *
   * The picture is slightly more complicated because Keystore2 reports a
   * SOFTWARE instance as a TEE instance when there isn't a TEE instance
   * available, but in that case, a STRONGBOX instance won't be available
   * either, so we'll still be doing the best we can.
   */
  auto rc = keystore2Service->getSecurityLevel(
      km::SecurityLevel::TRUSTED_ENVIRONMENT, &securityLevel);
  if (logKeystore2ExceptionIfPresent(rc, "getSecurityLevel"))
    LOG(ERROR) << "Vold unable to get security level from keystore2.";
}

bool Keymaster::generateKey(const km::AuthorizationSet& inParams,
                            std::string* key) {
  ks2::KeyDescriptor in_key = {
      .domain = ks2::Domain::BLOB,
      .alias = std::nullopt,
      .nspace = ROOT_NAMESPACE,
      .blob = std::nullopt,
  };
  ks2::KeyMetadata keyMetadata;
  auto rc = securityLevel->generateKey(
      in_key, std::nullopt, inParams.vector_data(), 0, {}, &keyMetadata);

  if (logKeystore2ExceptionIfPresent(rc, "generateKey")) return false;

  if (keyMetadata.key.blob == std::nullopt) {
    LOG(ERROR) << "keystore2 generated key blob was null";
    return false;
  }
  if (key)
    *key =
        std::string(keyMetadata.key.blob->begin(), keyMetadata.key.blob->end());

  zeroize_vector(keyMetadata.key.blob.value());
  return true;
}

bool Keymaster::importKey(const km::AuthorizationSet& inParams,
                          const std::string& key, std::string* outKeyBlob) {
  ks2::KeyDescriptor key_desc = {
      .domain = ks2::Domain::BLOB,
      .alias = std::nullopt,
      .nspace = ROOT_NAMESPACE,
      .blob = std::nullopt,
  };
  std::vector<uint8_t> key_vec(key.begin(), key.end());
  ks2::KeyMetadata keyMetadata;
  auto rc = securityLevel->importKey(
      key_desc, std::nullopt, inParams.vector_data(), 0, key_vec, &keyMetadata);

  if (logKeystore2ExceptionIfPresent(rc, "importKey")) return false;

  if (keyMetadata.key.blob == std::nullopt) {
    LOG(ERROR) << "keystore2 imported key blob was null";
    return false;
  }

  if (outKeyBlob)
    *outKeyBlob =
        std::string(keyMetadata.key.blob->begin(), keyMetadata.key.blob->end());

  return true;
}

bool Keymaster::exportKey(const std::string& kmKey, std::string* key) {
  bool ret = false;
  ks2::KeyDescriptor storageKey = {
      .domain = ks2::Domain::BLOB,
      .alias = std::nullopt,
      .nspace = ROOT_NAMESPACE,
  };
  storageKey.blob =
      std::make_optional<std::vector<uint8_t>>(kmKey.begin(), kmKey.end());
  ks2::EphemeralStorageKeyResponse ephemeral_key_response;
  auto rc = securityLevel->convertStorageKeyToEphemeral(
      storageKey, &ephemeral_key_response);

  if (logKeystore2ExceptionIfPresent(rc, "exportKey")) goto out;
  if (key)
    *key = std::string(ephemeral_key_response.ephemeralKey.begin(),
                       ephemeral_key_response.ephemeralKey.end());

  ret = true;
out:
  zeroize_vector(ephemeral_key_response.ephemeralKey);
  zeroize_vector(storageKey.blob.value());
  return ret;
}

}  // namespace kernel
}  // namespace android
