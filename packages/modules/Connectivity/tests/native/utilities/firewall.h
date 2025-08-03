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

#pragma once

#include <android-base/thread_annotations.h>
#include <bpf/BpfMap.h>
#include "netd.h"

using android::base::Result;
using android::bpf::BpfMap;

class Firewall {
  public:
    Firewall() EXCLUDES(mMutex);
    static Firewall* getInstance();
    Result<void> toggleStandbyMatch(bool enable) EXCLUDES(mMutex);
    Result<void> addRule(uint32_t uid, UidOwnerMatchType match, uint32_t iif = 0) EXCLUDES(mMutex);
    Result<void> removeRule(uint32_t uid, UidOwnerMatchType match) EXCLUDES(mMutex);
    Result<void> addUidInterfaceRules(const std::string& ifName, const std::vector<int32_t>& uids);
    Result<void> removeUidInterfaceRules(const std::vector<int32_t>& uids);
    Result<bool> getDataSaverSetting();
    Result<void> setDataSaver(bool enabled);
  private:
    BpfMap<uint32_t, uint32_t> mConfigurationMap GUARDED_BY(mMutex);
    BpfMap<uint32_t, UidOwnerValue> mUidOwnerMap GUARDED_BY(mMutex);
    BpfMap<uint32_t, bool> mDataSaverEnabledMap GUARDED_BY(mMutex);
    std::mutex mMutex;
};
