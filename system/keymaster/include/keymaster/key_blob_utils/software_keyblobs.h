/*
**
** Copyright 2017, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#pragma once

#include <optional>

#include <hardware/keymaster_defs.h>
#include <keymaster/android_keymaster_utils.h>
#include <keymaster/km_version.h>
#include <openssl/base.h>

namespace keymaster {

template <typename> struct TKeymasterBlob;
typedef TKeymasterBlob<keymaster_key_blob_t> KeymasterKeyBlob;
class AuthorizationSet;
class Key;

extern KeymasterBlob softwareRootOfTrust;

keymaster_error_t BuildHiddenAuthorizations(const AuthorizationSet& input_set,
                                            AuthorizationSet* hidden,
                                            const KeymasterBlob& root_of_trust);

keymaster_error_t FakeKeyAuthorizations(EVP_PKEY* pubkey, AuthorizationSet* hw_enforced,
                                        AuthorizationSet* sw_enforced);

keymaster_error_t ParseOldSoftkeymasterBlob(const KeymasterKeyBlob& blob,
                                            KeymasterKeyBlob* key_material,
                                            AuthorizationSet* hw_enforced,
                                            AuthorizationSet* sw_enforced);

keymaster_error_t ParseAuthEncryptedBlob(const KeymasterKeyBlob& blob,
                                         const AuthorizationSet& hidden,
                                         KeymasterKeyBlob* key_material,
                                         AuthorizationSet* hw_enforced,
                                         AuthorizationSet* sw_enforced);

keymaster_error_t SetKeyBlobAuthorizations(const AuthorizationSet& key_description,
                                           keymaster_key_origin_t origin, uint32_t os_version,
                                           uint32_t os_patchlevel, AuthorizationSet* hw_enforced,
                                           AuthorizationSet* sw_enforced,
                                           KmVersion version = KmVersion::KEYMINT_1);

keymaster_error_t ExtendKeyBlobAuthorizations(AuthorizationSet* hw_enforced,
                                              AuthorizationSet* sw_enforced,
                                              std::optional<uint32_t> vendor_patchlevel,
                                              std::optional<uint32_t> boot_patchlevel);

keymaster_error_t UpgradeSoftKeyBlob(const UniquePtr<Key>& key, const uint32_t os_version,
                                     const uint32_t os_patchlevel,
                                     const AuthorizationSet& upgrade_params,
                                     KeymasterKeyBlob* upgraded_key);

keymaster_error_t FullUpgradeSoftKeyBlob(const UniquePtr<Key>& key, const uint32_t os_version,
                                         uint32_t os_patchlevel,
                                         std::optional<uint32_t> vendor_patchlevel,
                                         std::optional<uint32_t> boot_patchlevel,
                                         const AuthorizationSet& upgrade_params,
                                         KeymasterKeyBlob* upgraded_key);
}  // namespace keymaster
