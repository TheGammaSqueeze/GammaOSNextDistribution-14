/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "FlagProvider.h"

using server_configurable_flags::GetServerConfigurableFlag;
using std::string;
using std::vector;

namespace android {
namespace os {
namespace statsd {

FlagProvider::FlagProvider()
    : mIsAtLeastSFunc(isAtLeastS), mGetServerFlagFunc(GetServerConfigurableFlag) {
}

FlagProvider& FlagProvider::getInstance() {
    static FlagProvider instance;
    return instance;
}

string FlagProvider::getFlagString(const string& flagName, const string& defaultValue) const {
    return getFlagStringInternal(flagName, defaultValue, /* isBootFlag= */ false);
}

bool FlagProvider::getFlagBool(const string& flagName, const string& defaultValue) const {
    return getFlagStringInternal(flagName, defaultValue, /* isBootFlag= */ false) == FLAG_TRUE;
}

string FlagProvider::getBootFlagString(const string& flagName, const string& defaultValue) const {
    return getFlagStringInternal(flagName, defaultValue, /* isBootFlag= */ true);
}

bool FlagProvider::getBootFlagBool(const string& flagName, const string& defaultValue) const {
    return getFlagStringInternal(flagName, defaultValue, /* isBootFlag= */ true) == FLAG_TRUE;
}

void FlagProvider::initBootFlags(const vector<string>& flags) {
    std::lock_guard<std::mutex> lock(mFlagsMutex);
    mBootFlags.clear();
    for (const string& flagName : flags) {
        string flagVal = mGetServerFlagFunc(STATSD_NATIVE_BOOT_NAMESPACE, flagName, FLAG_EMPTY);
        if (flagVal != FLAG_EMPTY) {
            mBootFlags[flagName] = flagVal;
        }
    }
}

void FlagProvider::overrideFlag(const string& flagName, const std::string& flagValue,
                                const bool isBootFlag) {
    std::lock_guard<std::mutex> lock(mFlagsMutex);
    mLocalFlags[getLocalFlagKey(flagName, isBootFlag)] = flagValue;
}

void FlagProvider::overrideFuncs(const IsAtLeastSFunc& isAtLeastSFunc,
                                 const GetServerFlagFunc& getServerFlagFunc) {
    std::lock_guard<std::mutex> lock(mFlagsMutex);
    overrideFuncsLocked(isAtLeastSFunc, getServerFlagFunc);
}

void FlagProvider::overrideFuncsLocked(const IsAtLeastSFunc& isAtLeastSFunc,
                                       const GetServerFlagFunc& getServerFlagFunc) {
    mIsAtLeastSFunc = isAtLeastSFunc;
    mGetServerFlagFunc = getServerFlagFunc;
}

string FlagProvider::getLocalFlagKey(const string& flagName, const bool isBootFlag) const {
    return isBootFlag ? STATSD_NATIVE_BOOT_NAMESPACE + "." + flagName
                      : STATSD_NATIVE_NAMESPACE + "." + flagName;
}

string FlagProvider::getFlagStringInternal(const std::string& flagName,
                                           const std::string& defaultValue,
                                           const bool isBootFlag) const {
    std::lock_guard<std::mutex> lock(mFlagsMutex);
    if (!mIsAtLeastSFunc()) {
        return defaultValue;
    }
    string localFlagKey = getLocalFlagKey(flagName, isBootFlag);
    if (mLocalFlags.find(localFlagKey) != mLocalFlags.end()) {
        return mLocalFlags.at(localFlagKey);
    }
    if (!isBootFlag) {
        return mGetServerFlagFunc(STATSD_NATIVE_NAMESPACE, flagName, defaultValue);
    }
    const auto& it = mBootFlags.find(flagName);
    return it == mBootFlags.end() ? defaultValue : it->second;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
