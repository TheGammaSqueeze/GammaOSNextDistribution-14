/*
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <future>
#include <optional>

#include <android/hardware/security/keymint/IRemotelyProvisionedComponent.h>
#include <android/security/rkp/RemotelyProvisionedKey.h>

namespace android::security::rkp::support {

using ::android::hardware::security::keymint::IRemotelyProvisionedComponent;
using ::android::security::rkp::RemotelyProvisionedKey;

// Callers of getRpcKeyFuture() and getRpcKey() need at least two threads to
// retrieve the key, one to asynchronously handle binder callbacks and one to
// wait on the future.
std::optional<std::future<std::optional<RemotelyProvisionedKey>>>
getRpcKeyFuture(const sp<IRemotelyProvisionedComponent>& rpc, int32_t keyId);

std::optional<RemotelyProvisionedKey> getRpcKey(const sp<IRemotelyProvisionedComponent>& rpc,
                                                int32_t keyId, int32_t timeout_sec = 10);

}  // namespace android::security::rkp::support
