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
 *
 */

#include "firewall.h"

#include <android-base/result.h>
#include <gtest/gtest.h>

Firewall::Firewall() {
    std::lock_guard guard(mMutex);
    auto result = mConfigurationMap.init(CONFIGURATION_MAP_PATH);
    EXPECT_RESULT_OK(result) << "init mConfigurationMap failed";

    result = mUidOwnerMap.init(UID_OWNER_MAP_PATH);
    EXPECT_RESULT_OK(result) << "init mUidOwnerMap failed";

    // Do not check whether DATA_SAVER_ENABLED_MAP_PATH init succeeded or failed since the map is
    // defined in tethering module, but the user of this class may be in other modules. For example,
    // DNS resolver tests statically link to this class. But when running MTS, the test infra
    // installs only DNS resolver module without installing tethering module together.
    mDataSaverEnabledMap.init(DATA_SAVER_ENABLED_MAP_PATH);
}

Firewall* Firewall::getInstance() {
    static Firewall instance;
    return &instance;
}

Result<void> Firewall::toggleStandbyMatch(bool enable) {
    std::lock_guard guard(mMutex);
    uint32_t key = UID_RULES_CONFIGURATION_KEY;
    auto oldConfiguration = mConfigurationMap.readValue(key);
    if (!oldConfiguration.ok()) {
        return Errorf("Cannot read the old configuration: {}", oldConfiguration.error().message());
    }

    BpfConfig newConfiguration = enable ? (oldConfiguration.value() | STANDBY_MATCH)
                                        : (oldConfiguration.value() & (~STANDBY_MATCH));
    auto res = mConfigurationMap.writeValue(key, newConfiguration, BPF_EXIST);
    if (!res.ok()) return Errorf("Failed to toggle STANDBY_MATCH: {}", res.error().message());

    return {};
}

Result<void> Firewall::addRule(uint32_t uid, UidOwnerMatchType match, uint32_t iif) {
    // iif should be non-zero if and only if match == MATCH_IIF
    if (match == IIF_MATCH && iif == 0) {
        return Errorf("Interface match {} must have nonzero interface index",
                      static_cast<uint32_t>(match));
    } else if (match != IIF_MATCH && iif != 0) {
        return Errorf("Non-interface match {} must have zero interface index",
                      static_cast<uint32_t>(match));
    }

    std::lock_guard guard(mMutex);
    auto oldMatch = mUidOwnerMap.readValue(uid);
    if (oldMatch.ok()) {
        UidOwnerValue newMatch = {
                .iif = iif ? iif : oldMatch.value().iif,
                .rule = oldMatch.value().rule | match,
        };
        auto res = mUidOwnerMap.writeValue(uid, newMatch, BPF_ANY);
        if (!res.ok()) return Errorf("Failed to update rule: {}", res.error().message());
    } else {
        UidOwnerValue newMatch = {
                .iif = iif,
                .rule = match,
        };
        auto res = mUidOwnerMap.writeValue(uid, newMatch, BPF_ANY);
        if (!res.ok()) return Errorf("Failed to add rule: {}", res.error().message());
    }
    return {};
}

Result<void> Firewall::removeRule(uint32_t uid, UidOwnerMatchType match) {
    std::lock_guard guard(mMutex);
    auto oldMatch = mUidOwnerMap.readValue(uid);
    if (!oldMatch.ok()) return Errorf("uid: %u does not exist in map", uid);

    UidOwnerValue newMatch = {
            .iif = (match == IIF_MATCH) ? 0 : oldMatch.value().iif,
            .rule = oldMatch.value().rule & ~match,
    };
    if (newMatch.rule == 0) {
        auto res = mUidOwnerMap.deleteValue(uid);
        if (!res.ok()) return Errorf("Failed to remove rule: {}", res.error().message());
    } else {
        auto res = mUidOwnerMap.writeValue(uid, newMatch, BPF_ANY);
        if (!res.ok()) return Errorf("Failed to update rule: {}", res.error().message());
    }
    return {};
}

Result<void> Firewall::addUidInterfaceRules(const std::string& ifName,
                                            const std::vector<int32_t>& uids) {
    unsigned int iif = if_nametoindex(ifName.c_str());
    if (!iif) return Errorf("Failed to get interface index: {}", ifName);

    for (auto uid : uids) {
        auto res = addRule(uid, IIF_MATCH, iif);
        if (!res.ok()) return res;
    }
    return {};
}

Result<void> Firewall::removeUidInterfaceRules(const std::vector<int32_t>& uids) {
    for (auto uid : uids) {
        auto res = removeRule(uid, IIF_MATCH);
        if (!res.ok()) return res;
    }
    return {};
}

Result<bool> Firewall::getDataSaverSetting() {
    std::lock_guard guard(mMutex);
    if (!mDataSaverEnabledMap.isValid()) {
        return Errorf("init mDataSaverEnabledMap failed");
    }

    auto dataSaverSetting = mDataSaverEnabledMap.readValue(DATA_SAVER_ENABLED_KEY);
    if (!dataSaverSetting.ok()) {
        return Errorf("Cannot read the data saver setting: {}", dataSaverSetting.error().message());
    }
    return dataSaverSetting;
}

Result<void> Firewall::setDataSaver(bool enabled) {
    std::lock_guard guard(mMutex);
    if (!mDataSaverEnabledMap.isValid()) {
        return Errorf("init mDataSaverEnabledMap failed");
    }

    auto res = mDataSaverEnabledMap.writeValue(DATA_SAVER_ENABLED_KEY, enabled, BPF_EXIST);
    if (!res.ok()) return Errorf("Failed to set data saver: {}", res.error().message());

    return {};
}
