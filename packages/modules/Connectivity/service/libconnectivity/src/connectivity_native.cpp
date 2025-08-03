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

#include "connectivity_native.h"

#include <android/binder_manager.h>
#include <android-modules-utils/sdk_level.h>
#include <aidl/android/net/connectivity/aidl/ConnectivityNative.h>

using aidl::android::net::connectivity::aidl::IConnectivityNative;


static std::shared_ptr<IConnectivityNative> getBinder() {
    ndk::SpAIBinder sBinder = ndk::SpAIBinder(reinterpret_cast<AIBinder*>(
        AServiceManager_checkService("connectivity_native")));
    return aidl::android::net::connectivity::aidl::IConnectivityNative::fromBinder(sBinder);
}

static int getErrno(const ::ndk::ScopedAStatus& status) {
    switch (status.getExceptionCode()) {
        case EX_NONE:
            return 0;
        case EX_ILLEGAL_ARGUMENT:
            return EINVAL;
        case EX_SECURITY:
            return EPERM;
        case EX_SERVICE_SPECIFIC:
            return status.getServiceSpecificError();
        default:
            return EPROTO;
    }
}

int AConnectivityNative_blockPortForBind(in_port_t port) {
    if (!android::modules::sdklevel::IsAtLeastU()) return ENOSYS;
    std::shared_ptr<IConnectivityNative> c = getBinder();
    if (!c) {
        return EAGAIN;
    }
    return getErrno(c->blockPortForBind(port));
}

int AConnectivityNative_unblockPortForBind(in_port_t port) {
    if (!android::modules::sdklevel::IsAtLeastU()) return ENOSYS;
    std::shared_ptr<IConnectivityNative> c = getBinder();
    if (!c) {
        return EAGAIN;
    }
    return getErrno(c->unblockPortForBind(port));
}

int AConnectivityNative_unblockAllPortsForBind() {
    if (!android::modules::sdklevel::IsAtLeastU()) return ENOSYS;
    std::shared_ptr<IConnectivityNative> c = getBinder();
    if (!c) {
        return EAGAIN;
    }
    return getErrno(c->unblockAllPortsForBind());
}

int AConnectivityNative_getPortsBlockedForBind(in_port_t *ports, size_t *count) {
    if (!android::modules::sdklevel::IsAtLeastU()) return ENOSYS;
    std::shared_ptr<IConnectivityNative> c = getBinder();
    if (!c) {
        return EAGAIN;
    }
    std::vector<int32_t> actualBlockedPorts;
    int err = getErrno(c->getPortsBlockedForBind(&actualBlockedPorts));
    if (err) {
        return err;
    }

    for (int i = 0; i < *count && i < actualBlockedPorts.size(); i++) {
        ports[i] = actualBlockedPorts[i];
    }
    *count = actualBlockedPorts.size();
    return 0;
}
