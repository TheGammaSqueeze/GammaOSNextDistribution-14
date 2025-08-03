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

#include "NetdHwAidlService.h"
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include "Controllers.h"
#include "Fwmark.h"
#include "RouteController.h"
#include "TetherController.h"

// Tells TetherController::enableForwarding who is requesting forwarding, so that TetherController
// can manage/refcount requests to enable forwarding by multiple parties such as the framework, this
// binder interface, and the legacy "ndc ipfwd enable <requester>" commands.
namespace {
constexpr const char* FORWARDING_REQUESTER = "NetdHwAidlService";
}

namespace android {
namespace net {
namespace aidl {

static int toHalStatus(int ret) {
    switch (ret) {
        case 0:
            return 0;
        case -EINVAL:
            return NetdHwAidlService::STATUS_INVALID_ARGUMENTS;
        case -EEXIST:
            return NetdHwAidlService::STATUS_ALREADY_EXISTS;
        case -ENONET:
            return NetdHwAidlService::STATUS_NO_NETWORK;
        case -EPERM:
            return NetdHwAidlService::STATUS_PERMISSION_DENIED;
        default:
            ALOGE("HAL service error=%d", ret);
            return NetdHwAidlService::STATUS_UNKNOWN_ERROR;
    }
}

void NetdHwAidlService::run() {
    std::shared_ptr<NetdHwAidlService> service = ndk::SharedRefBase::make<NetdHwAidlService>();

    const std::string instance = std::string() + NetdHwAidlService::descriptor + "/default";
    binder_status_t status =
            AServiceManager_addService(service->asBinder().get(), instance.c_str());
    if (status != STATUS_OK) {
        ALOGE("Failed to register AIDL INetd service. Status: %d.", status);
        return;
    }

    ABinderProcess_joinThreadPool();
}

ScopedAStatus NetdHwAidlService::createOemNetwork(OemNetwork* network) {
    unsigned netId;
    Permission permission = PERMISSION_SYSTEM;

    int ret = gCtls->netCtrl.createPhysicalOemNetwork(permission, &netId);

    Fwmark fwmark;
    fwmark.netId = netId;
    fwmark.explicitlySelected = true;
    fwmark.protectedFromVpn = true;
    fwmark.permission = PERMISSION_SYSTEM;
    network->networkHandle = netIdToNetHandle(netId);
    network->packetMark = fwmark.intValue;
    if (ret != 0) {
        return ScopedAStatus::fromServiceSpecificError(toHalStatus(ret));
    } else {
        return ScopedAStatus::ok();
    }
}

// Vendor code can only modify OEM networks. All other networks are managed by ConnectivityService.
#define RETURN_IF_NOT_OEM_NETWORK(netId)                                                          \
    if (((netId) < NetworkController::MIN_OEM_ID) || ((netId) > NetworkController::MAX_OEM_ID)) { \
        return ScopedAStatus::fromServiceSpecificError(STATUS_INVALID_ARGUMENTS);                 \
    }

ScopedAStatus NetdHwAidlService::destroyOemNetwork(int64_t netHandle) {
    unsigned netId = netHandleToNetId(netHandle);
    RETURN_IF_NOT_OEM_NETWORK(netId);

    auto ret = toHalStatus(gCtls->netCtrl.destroyNetwork(netId));
    if (ret != 0) {
        return ScopedAStatus::fromServiceSpecificError(toHalStatus(ret));
    } else {
        return ScopedAStatus::ok();
    }
}

const char* maybeNullString(const std::string& nexthop) {
    // std::strings can't be null, but RouteController wants null instead of an empty string.
    const char* nh = nexthop.c_str();
    if (nh && !*nh) {
        nh = nullptr;
    }
    return nh;
}

ScopedAStatus NetdHwAidlService::addRouteToOemNetwork(int64_t networkHandle,
                                                      const std::string& ifname,
                                                      const std::string& destination,
                                                      const std::string& nexthop) {
    unsigned netId = netHandleToNetId(networkHandle);
    RETURN_IF_NOT_OEM_NETWORK(netId);

    auto ret = gCtls->netCtrl.addRoute(netId, ifname.c_str(), destination.c_str(),
                                       maybeNullString(nexthop), false, INVALID_UID, 0 /* mtu */);
    if (ret != 0) {
        return ScopedAStatus::fromServiceSpecificError(toHalStatus(ret));
    } else {
        return ScopedAStatus::ok();
    }
}

ScopedAStatus NetdHwAidlService::removeRouteFromOemNetwork(int64_t networkHandle,
                                                           const std::string& ifname,
                                                           const std::string& destination,
                                                           const std::string& nexthop) {
    unsigned netId = netHandleToNetId(networkHandle);
    RETURN_IF_NOT_OEM_NETWORK(netId);

    auto ret = gCtls->netCtrl.removeRoute(netId, ifname.c_str(), destination.c_str(),
                                          maybeNullString(nexthop), false, INVALID_UID);
    if (ret != 0) {
        return ScopedAStatus::fromServiceSpecificError(toHalStatus(ret));
    } else {
        return ScopedAStatus::ok();
    }
}

ScopedAStatus NetdHwAidlService::addInterfaceToOemNetwork(int64_t networkHandle,
                                                          const std::string& ifname) {
    unsigned netId = netHandleToNetId(networkHandle);
    RETURN_IF_NOT_OEM_NETWORK(netId);

    auto ret = gCtls->netCtrl.addInterfaceToNetwork(netId, ifname.c_str());
    if (ret != 0) {
        return ScopedAStatus::fromServiceSpecificError(toHalStatus(ret));
    } else {
        return ScopedAStatus::ok();
    }
}

ScopedAStatus NetdHwAidlService::removeInterfaceFromOemNetwork(int64_t networkHandle,
                                                               const std::string& ifname) {
    unsigned netId = netHandleToNetId(networkHandle);
    RETURN_IF_NOT_OEM_NETWORK(netId);

    auto ret = gCtls->netCtrl.removeInterfaceFromNetwork(netId, ifname.c_str());
    if (ret != 0) {
        return ScopedAStatus::fromServiceSpecificError(toHalStatus(ret));
    } else {
        return ScopedAStatus::ok();
    }
}

ScopedAStatus NetdHwAidlService::setIpForwardEnable(bool enable) {
    std::lock_guard _lock(gCtls->tetherCtrl.lock);

    bool success = enable ? gCtls->tetherCtrl.enableForwarding(FORWARDING_REQUESTER)
                          : gCtls->tetherCtrl.disableForwarding(FORWARDING_REQUESTER);

    if (!success) {
        return ScopedAStatus::fromServiceSpecificError(STATUS_UNKNOWN_ERROR);
    } else {
        return ScopedAStatus::ok();
    }
}

ScopedAStatus NetdHwAidlService::setForwardingBetweenInterfaces(const std::string& inputIfName,
                                                                const std::string& outputIfName,
                                                                bool enable) {
    std::lock_guard _lock(gCtls->tetherCtrl.lock);

    // TODO: check that one interface is an OEM interface and the other is another OEM interface, an
    // IPsec interface or a dummy interface.
    int ret = enable ? RouteController::enableTethering(inputIfName.c_str(), outputIfName.c_str())
                     : RouteController::disableTethering(inputIfName.c_str(), outputIfName.c_str());
    if (ret != 0) {
        return ScopedAStatus::fromServiceSpecificError(toHalStatus(ret));
    } else {
        return ScopedAStatus::ok();
    }
}

}  // namespace aidl
}  // namespace net
}  // namespace android
