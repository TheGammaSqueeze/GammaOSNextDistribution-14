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
#define LOG_TAG "Netd"

#include "Network.h"

#include "RouteController.h"
#include "SockDiag.h"
#include "log/log.h"

#include <android-base/strings.h>
#include <sstream>

namespace android {
namespace net {

Network::~Network() {
    if (!mInterfaces.empty()) {
        ALOGE("deleting network with netId %u without clearing its interfaces", mNetId);
    }
}

unsigned Network::getNetId() const {
    return mNetId;
}

bool Network::hasInterface(const std::string& interface) const {
    return mInterfaces.find(interface) != mInterfaces.end();
}

const std::set<std::string>& Network::getInterfaces() const {
    return mInterfaces;
}

int Network::clearInterfaces() {
    while (!mInterfaces.empty()) {
        // Make a copy of the string, so removeInterface() doesn't lose its parameter when it
        // removes the string from the set.
        std::string interface = *mInterfaces.begin();
        if (int ret = removeInterface(interface)) {
            return ret;
        }
    }
    return 0;
}

std::string Network::toString() const {
    const char kSeparator[] = " ";
    std::stringstream repr;

    repr << mNetId << kSeparator << getTypeString();

    if (mInterfaces.size() > 0) {
        repr << kSeparator << android::base::Join(mInterfaces, ",");
    }

    return repr.str();
}

std::string Network::uidRangesToString() const {
    if (mUidRangeMap.empty()) {
        return "";
    }

    std::ostringstream result;
    for (auto it = mUidRangeMap.begin(); it != mUidRangeMap.end(); ++it) {
        result << "prio " << it->first << " " << it->second.toString();
        if (std::next(it) != mUidRangeMap.end()) result << "; ";
    }
    return result.str();
}

std::string Network::allowedUidsToString() const {
    if (!mAllowedUids) {
        return "unrestricted";
    }
    return mAllowedUids->toString();
}

// Check if the user has been added to this network. If yes, the highest priority of matching
// setting is returned by subPriority. Thus caller can make choice among several matching
// networks.
bool Network::appliesToUser(uid_t uid, int32_t* subPriority) const {
    for (const auto& [priority, uidRanges] : mUidRangeMap) {
        if (uidRanges.hasUid(uid)) {
            *subPriority = priority;
            return true;
        }
    }
    return false;
}

void Network::addToUidRangeMap(const UidRanges& uidRanges, int32_t subPriority) {
    auto iter = mUidRangeMap.find(subPriority);
    if (iter != mUidRangeMap.end()) {
        iter->second.add(uidRanges);
    } else {
        mUidRangeMap[subPriority] = uidRanges;
    }
}

void Network::removeFromUidRangeMap(const UidRanges& uidRanges, int32_t subPriority) {
    auto iter = mUidRangeMap.find(subPriority);
    if (iter != mUidRangeMap.end()) {
        iter->second.remove(uidRanges);
        if (iter->second.empty()) {
            mUidRangeMap.erase(subPriority);
        }
    } else {
        ALOGW("uidRanges with priority %d not found", subPriority);
    }
}

void Network::clearAllowedUids() {
    mAllowedUids.reset();
}

void Network::setAllowedUids(const UidRanges& uidRanges) {
    mAllowedUids = uidRanges;
}

bool Network::isUidAllowed(uid_t uid) {
    return !mAllowedUids || mAllowedUids->hasUid(uid);
}

bool Network::canAddUidRanges(const UidRanges& uidRanges) const {
    if (uidRanges.overlapsSelf()) {
        ALOGE("uid range %s overlaps self", uidRanges.toString().c_str());
        return false;
    }

    return true;
}

bool Network::isSecure() const {
    return mSecure;
}

Network::Network(unsigned netId, bool secure) : mNetId(netId), mSecure(secure) {}

}  // namespace net
}  // namespace android
