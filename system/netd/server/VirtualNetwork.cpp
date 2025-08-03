/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <set>

#define LOG_TAG "Netd"

#include "VirtualNetwork.h"

#include "RouteController.h"

#include "log/log.h"

namespace android {
namespace net {

VirtualNetwork::VirtualNetwork(unsigned netId, bool secure, bool excludeLocalRoutes)
    : Network(netId, secure), mExcludeLocalRoutes(excludeLocalRoutes) {}

VirtualNetwork::~VirtualNetwork() {}

int VirtualNetwork::addUsers(const UidRanges& uidRanges, int32_t subPriority) {
    if (!isValidSubPriority(subPriority) || !canAddUidRanges(uidRanges)) {
        return -EINVAL;
    }

    for (const std::string& interface : mInterfaces) {
        int ret = RouteController::addUsersToVirtualNetwork(mNetId, interface.c_str(), mSecure,
                                                            {{subPriority, uidRanges}},
                                                            mExcludeLocalRoutes);
        if (ret) {
            ALOGE("failed to add users on interface %s of netId %u", interface.c_str(), mNetId);
            return ret;
        }
    }
    addToUidRangeMap(uidRanges, subPriority);
    return 0;
}

int VirtualNetwork::removeUsers(const UidRanges& uidRanges, int32_t subPriority) {
    if (!isValidSubPriority(subPriority)) return -EINVAL;

    for (const std::string& interface : mInterfaces) {
        int ret = RouteController::removeUsersFromVirtualNetwork(mNetId, interface.c_str(), mSecure,
                                                                 {{subPriority, uidRanges}},
                                                                 mExcludeLocalRoutes);
        if (ret) {
            ALOGE("failed to remove users on interface %s of netId %u", interface.c_str(), mNetId);
            return ret;
        }
    }
    removeFromUidRangeMap(uidRanges, subPriority);
    return 0;
}

int VirtualNetwork::addInterface(const std::string& interface) {
    if (hasInterface(interface)) {
        return 0;
    }
    if (int ret = RouteController::addInterfaceToVirtualNetwork(
                mNetId, interface.c_str(), mSecure, mUidRangeMap, mExcludeLocalRoutes)) {
        ALOGE("failed to add interface %s to VPN netId %u", interface.c_str(), mNetId);
        return ret;
    }
    mInterfaces.insert(interface);
    return 0;
}

int VirtualNetwork::removeInterface(const std::string& interface) {
    if (!hasInterface(interface)) {
        return 0;
    }
    if (int ret = RouteController::removeInterfaceFromVirtualNetwork(
                mNetId, interface.c_str(), mSecure, mUidRangeMap, mExcludeLocalRoutes)) {
        ALOGE("failed to remove interface %s from VPN netId %u", interface.c_str(), mNetId);
        return ret;
    }
    mInterfaces.erase(interface);
    return 0;
}

bool VirtualNetwork::isValidSubPriority(int32_t priority) {
    // Only supports default subsidiary permissions.
    return priority == UidRanges::SUB_PRIORITY_HIGHEST;
}

}  // namespace net
}  // namespace android
