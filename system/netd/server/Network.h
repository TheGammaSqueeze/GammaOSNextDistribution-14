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

#pragma once

#include "NetdConstants.h"
#include "Permission.h"
#include "UidRanges.h"

#include <set>
#include <string>

namespace android::net {

typedef std::map<int32_t, UidRanges> UidRangeMap;

// A Network represents a collection of interfaces participating as a single administrative unit.
class Network {
public:
    // You MUST ensure that no interfaces are still assigned to this network, say by calling
    // clearInterfaces(), before deleting it. This is because interface removal may fail. If we
    // automatically removed interfaces in the destructor, you wouldn't know if it failed.
    virtual ~Network();

    virtual std::string getTypeString() const = 0;
    unsigned getNetId() const;

    bool hasInterface(const std::string& interface) const;
    const std::set<std::string>& getInterfaces() const;

    // These return 0 on success or negative errno on failure.
    [[nodiscard]] virtual int addInterface(const std::string&) { return -EINVAL; }
    [[nodiscard]] virtual int removeInterface(const std::string&) { return -EINVAL; }
    [[nodiscard]] int clearInterfaces();

    std::string toString() const;
    std::string uidRangesToString() const;
    std::string allowedUidsToString() const;
    bool appliesToUser(uid_t uid, int32_t* subPriority) const;
    virtual Permission getPermission() const = 0;
    [[nodiscard]] virtual int addUsers(const UidRanges&, int32_t /*subPriority*/) {
        return -EINVAL;
    };
    [[nodiscard]] virtual int removeUsers(const UidRanges&, int32_t /*subPriority*/) {
        return -EINVAL;
    };
    bool isSecure() const;
    virtual bool isPhysical() { return false; }
    virtual bool isUnreachable() { return false; }
    virtual bool isVirtual() { return false; }
    virtual bool canAddUsers() { return false; }
    virtual bool isValidSubPriority(int32_t /*priority*/) { return false; }
    virtual void addToUidRangeMap(const UidRanges& uidRanges, int32_t subPriority);
    virtual void removeFromUidRangeMap(const UidRanges& uidRanges, int32_t subPriority);
    void clearAllowedUids();
    void setAllowedUids(const UidRanges& uidRanges);
    bool isUidAllowed(uid_t uid);

  protected:
    explicit Network(unsigned netId, bool secure = false);
    bool canAddUidRanges(const UidRanges& uidRanges) const;

    const unsigned mNetId;
    std::set<std::string> mInterfaces;
    // Each subsidiary priority maps to a set of UID ranges of a feature.
    std::map<int32_t, UidRanges> mUidRangeMap;
    const bool mSecure;
    // UIDs that can explicitly select this network. It means no restriction for all UIDs if the
    // optional variable has no value.
    std::optional<UidRanges> mAllowedUids;

  private:
    enum Action {
        REMOVE,
        ADD,
    };
};

}  // namespace android::net
