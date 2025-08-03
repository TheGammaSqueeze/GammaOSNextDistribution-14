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

#pragma once

#include <memory>
#include <string>
#include <utility>

#include <android-base/macros.h>
#include <keymint_support/authorization_set.h>
#include <keymint_support/keymint_tags.h>

#include <aidl/android/hardware/security/keymint/ErrorCode.h>
#include <aidl/android/system/keystore2/IKeystoreService.h>
#include <android/binder_manager.h>

namespace android {
namespace kernel {

namespace ks2 = ::aidl::android::system::keystore2;
namespace km = ::aidl::android::hardware::security::keymint;

// Wrapper for keystore2 methods that vold uses.
class Keymaster {
 public:
  Keymaster();
  // false if we failed to get a keystore2 security level.
  explicit operator bool() { return (bool)securityLevel; }
  // Generate a key using keystore2 from the given params.
  bool generateKey(const km::AuthorizationSet& inParams, std::string* key);
  // Exports a keystore2 key with STORAGE_KEY tag wrapped with a per-boot
  // ephemeral key
  bool exportKey(const std::string& kmKey, std::string* key);
  // Import a key into the keymint
  bool importKey(const km::AuthorizationSet& inParams, const std::string& key,
                 std::string* outKeyBlob);

 private:
  std::shared_ptr<ks2::IKeystoreSecurityLevel> securityLevel;
  DISALLOW_COPY_AND_ASSIGN(Keymaster);
};

}  // namespace kernel
}  // namespace android
