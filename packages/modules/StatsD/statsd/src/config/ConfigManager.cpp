/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define STATSD_DEBUG false  // STOPSHIP if true
#include "Log.h"

#include "config/ConfigManager.h"
#include "storage/StorageManager.h"

#include "guardrail/StatsdStats.h"
#include "stats_log_util.h"
#include "stats_util.h"
#include "stats_log_util.h"

#include <stdio.h>
#include <vector>
#include "android-base/stringprintf.h"

namespace android {
namespace os {
namespace statsd {

using std::string;
using std::vector;

using Status = ::ndk::ScopedAStatus;

#define STATS_SERVICE_DIR "/data/misc/stats-service"

using android::base::StringPrintf;

ConfigManager::ConfigManager() {
}

ConfigManager::~ConfigManager() {
}

void ConfigManager::Startup() {
    map<ConfigKey, StatsdConfig> configsFromDisk;
    StorageManager::readConfigFromDisk(configsFromDisk);
    for (const auto& config : configsFromDisk) {
        UpdateConfig(config.first, config.second);
    }
}

void ConfigManager::StartupForTest() {
    // No-op function to avoid reading configs from disks for tests.
}

void ConfigManager::AddListener(const sp<ConfigListener>& listener) {
    lock_guard<mutex> lock(mMutex);
    mListeners.push_back(listener);
}

void ConfigManager::UpdateConfig(const ConfigKey& key, const StatsdConfig& config) {
    vector<sp<ConfigListener>> broadcastList;
    {
        lock_guard <mutex> lock(mMutex);

        const int numBytes = config.ByteSize();
        vector<uint8_t> buffer(numBytes);
        config.SerializeToArray(buffer.data(), numBytes);

        auto uidIt = mConfigs.find(key.GetUid());
        // GuardRail: Limit the number of configs per uid.
        if (uidIt != mConfigs.end()) {
            auto it = uidIt->second.find(key);
            if (it == uidIt->second.end() &&
                uidIt->second.size() >= StatsdStats::kMaxConfigCountPerUid) {
                ALOGE("ConfigManager: uid %d has exceeded the config count limit", key.GetUid());
                return;
            }
        }

        // Check if it's a duplicate config.
        if (uidIt != mConfigs.end() && uidIt->second.find(key) != uidIt->second.end() &&
            StorageManager::hasIdenticalConfig(key, buffer)) {
            // This is a duplicate config.
            ALOGI("ConfigManager This is a duplicate config %s", key.ToString().c_str());
            // Update saved file on disk. We still update timestamp of file when
            // there exists a duplicate configuration to avoid garbage collection.
            update_saved_configs_locked(key, buffer, numBytes);
            return;
        }

        // Update saved file on disk.
        update_saved_configs_locked(key, buffer, numBytes);

        // Add to set.
        mConfigs[key.GetUid()].insert(key);

        broadcastList = mListeners;
    }

    const int64_t timestampNs = getElapsedRealtimeNs();
    // Tell everyone
    for (const sp<ConfigListener>& listener : broadcastList) {
        listener->OnConfigUpdated(timestampNs, key, config);
    }
}

void ConfigManager::SetConfigReceiver(const ConfigKey& key,
                                      const shared_ptr<IPendingIntentRef>& pir) {
    lock_guard<mutex> lock(mMutex);
    mConfigReceivers[key] = pir;
}

void ConfigManager::RemoveConfigReceiver(const ConfigKey& key) {
    lock_guard<mutex> lock(mMutex);
    mConfigReceivers.erase(key);
}

void ConfigManager::RemoveConfigReceiver(const ConfigKey& key,
                                         const shared_ptr<IPendingIntentRef>& pir) {
    lock_guard<mutex> lock(mMutex);
    auto it = mConfigReceivers.find(key);
    if (it != mConfigReceivers.end() && it->second == pir) {
        mConfigReceivers.erase(key);
    }
}

void ConfigManager::SetActiveConfigsChangedReceiver(const int uid,
                                                    const shared_ptr<IPendingIntentRef>& pir) {
    lock_guard<mutex> lock(mMutex);
    mActiveConfigsChangedReceivers[uid] = pir;
}

void ConfigManager::RemoveActiveConfigsChangedReceiver(const int uid) {
    lock_guard<mutex> lock(mMutex);
    mActiveConfigsChangedReceivers.erase(uid);
}

void ConfigManager::RemoveActiveConfigsChangedReceiver(const int uid,
                                                       const shared_ptr<IPendingIntentRef>& pir) {
    lock_guard<mutex> lock(mMutex);
    auto it = mActiveConfigsChangedReceivers.find(uid);
    if (it != mActiveConfigsChangedReceivers.end() && it->second == pir) {
        mActiveConfigsChangedReceivers.erase(uid);
    }
}

void ConfigManager::SetRestrictedMetricsChangedReceiver(const string& configPackage,
                                                        const int64_t configId,
                                                        const int32_t callingUid,
                                                        const shared_ptr<IPendingIntentRef>& pir) {
    lock_guard<mutex> lock(mMutex);
    ConfigKeyWithPackage configKey(configPackage, configId);
    mRestrictedMetricsChangedReceivers[configKey][callingUid] = pir;
}

void ConfigManager::RemoveRestrictedMetricsChangedReceiver(const string& configPackage,
                                                           const int64_t configId,
                                                           const int32_t callingUid) {
    lock_guard<mutex> lock(mMutex);
    ConfigKeyWithPackage configKey(configPackage, configId);
    const auto& it = mRestrictedMetricsChangedReceivers.find(configKey);
    if (it != mRestrictedMetricsChangedReceivers.end()) {
        it->second.erase(callingUid);
        if (it->second.empty()) {
            mRestrictedMetricsChangedReceivers.erase(it);
        }
    }
}

void ConfigManager::RemoveRestrictedMetricsChangedReceiver(
        const ConfigKeyWithPackage& key, const int32_t delegateUid,
        const shared_ptr<IPendingIntentRef>& pir) {
    lock_guard<mutex> lock(mMutex);
    const auto& it = mRestrictedMetricsChangedReceivers.find(key);
    if (it != mRestrictedMetricsChangedReceivers.end()) {
        const auto& pirIt = it->second.find(delegateUid);
        if (pirIt != it->second.end() && pirIt->second == pir) {
            it->second.erase(delegateUid);
            if (it->second.empty()) {
                mRestrictedMetricsChangedReceivers.erase(it);
            }
        }
    }
}

void ConfigManager::SendRestrictedMetricsBroadcast(const set<string>& configPackages,
                                                   const int64_t configId,
                                                   const set<int32_t>& delegateUids,
                                                   const vector<int64_t>& metricIds) {
    map<ConfigKeyWithPackage, map<int32_t, shared_ptr<IPendingIntentRef>>> intentsToSend;
    {
        lock_guard<mutex> lock(mMutex);
        // Invoke the pending intent for all matching configs, as long as the listening delegates
        // match the allowed delegate uids specified by the config.
        for (const string& configPackage : configPackages) {
            ConfigKeyWithPackage key(configPackage, configId);
            const auto& it = mRestrictedMetricsChangedReceivers.find(key);
            if (it != mRestrictedMetricsChangedReceivers.end()) {
                for (const auto& [delegateUid, pir] : it->second) {
                    if (delegateUids.find(delegateUid) != delegateUids.end()) {
                        intentsToSend[key][delegateUid] = pir;
                    }
                }
            }
        }
    }

    // Invoke the pending intents without holding the lock.
    for (const auto& [key, innerMap] : intentsToSend) {
        for (const auto& [delegateUid, pir] : innerMap) {
            Status status = pir->sendRestrictedMetricsChangedBroadcast(metricIds);
            if (status.isOk()) {
                VLOG("ConfigManager::SendRestrictedMetricsBroadcast succeeded");
            }
            if (status.getExceptionCode() == EX_TRANSACTION_FAILED &&
                status.getStatus() == STATUS_DEAD_OBJECT) {
                // Must also be called without the lock, since remove will acquire the lock.
                RemoveRestrictedMetricsChangedReceiver(key, delegateUid, pir);
            }
        }
    }
}

void ConfigManager::RemoveConfig(const ConfigKey& key) {
    vector<sp<ConfigListener>> broadcastList;
    {
        lock_guard <mutex> lock(mMutex);

        auto uid = key.GetUid();
        auto uidIt = mConfigs.find(uid);
        if (uidIt != mConfigs.end() && uidIt->second.find(key) != uidIt->second.end()) {
            // Remove from map
            uidIt->second.erase(key);

            broadcastList = mListeners;
        }

        // Remove from disk. There can still be a lingering file on disk so we check
        // whether or not the config was on memory.
        remove_saved_configs(key);
    }

    for (const sp<ConfigListener>& listener:broadcastList) {
        listener->OnConfigRemoved(key);
    }
}

void ConfigManager::remove_saved_configs(const ConfigKey& key) {
    string suffix = StringPrintf("%d_%lld", key.GetUid(), (long long)key.GetId());
    StorageManager::deleteSuffixedFiles(STATS_SERVICE_DIR, suffix.c_str());
}

// TODO(b/xxx): consider removing all receivers associated with this uid.
void ConfigManager::RemoveConfigs(int uid) {
    vector<ConfigKey> removed;
    vector<sp<ConfigListener>> broadcastList;
    {
        lock_guard <mutex> lock(mMutex);

        auto uidIt = mConfigs.find(uid);
        if (uidIt == mConfigs.end()) {
            return;
        }

        for (auto it = uidIt->second.begin(); it != uidIt->second.end(); ++it) {
            // Remove from map
                remove_saved_configs(*it);
                removed.push_back(*it);
        }

        mConfigs.erase(uidIt);

        broadcastList = mListeners;
    }

    // Remove separately so if they do anything in the callback they can't mess up our iteration.
    for (auto& key : removed) {
        // Tell everyone
        for (const sp<ConfigListener>& listener:broadcastList) {
            listener->OnConfigRemoved(key);
        }
    }
}

void ConfigManager::RemoveAllConfigs() {
    vector<ConfigKey> removed;
    vector<sp<ConfigListener>> broadcastList;
    {
        lock_guard <mutex> lock(mMutex);

        for (auto uidIt = mConfigs.begin(); uidIt != mConfigs.end();) {
            for (auto it = uidIt->second.begin(); it != uidIt->second.end();) {
                // Remove from map
                removed.push_back(*it);
                it = uidIt->second.erase(it);
            }
            uidIt = mConfigs.erase(uidIt);
        }

        broadcastList = mListeners;
    }

    // Remove separately so if they do anything in the callback they can't mess up our iteration.
    for (auto& key : removed) {
        // Tell everyone
        for (const sp<ConfigListener>& listener:broadcastList) {
            listener->OnConfigRemoved(key);
        }
    }
}

vector<ConfigKey> ConfigManager::GetAllConfigKeys() const {
    lock_guard<mutex> lock(mMutex);

    vector<ConfigKey> ret;
    for (auto uidIt = mConfigs.cbegin(); uidIt != mConfigs.cend(); ++uidIt) {
        for (auto it = uidIt->second.cbegin(); it != uidIt->second.cend(); ++it) {
            ret.push_back(*it);
        }
    }
    return ret;
}

const shared_ptr<IPendingIntentRef> ConfigManager::GetConfigReceiver(const ConfigKey& key) const {
    lock_guard<mutex> lock(mMutex);

    auto it = mConfigReceivers.find(key);
    if (it == mConfigReceivers.end()) {
        return nullptr;
    } else {
        return it->second;
    }
}

const shared_ptr<IPendingIntentRef> ConfigManager::GetActiveConfigsChangedReceiver(const int uid)
        const {
    lock_guard<mutex> lock(mMutex);

    auto it = mActiveConfigsChangedReceivers.find(uid);
    if (it == mActiveConfigsChangedReceivers.end()) {
        return nullptr;
    } else {
        return it->second;
    }
}

void ConfigManager::Dump(FILE* out) {
    lock_guard<mutex> lock(mMutex);

    fprintf(out, "CONFIGURATIONS\n");
    fprintf(out, "     uid name\n");
    for (auto uidIt = mConfigs.cbegin(); uidIt != mConfigs.cend(); ++uidIt) {
        for (auto it = uidIt->second.cbegin(); it != uidIt->second.cend(); ++it) {
            fprintf(out, "  %6d %lld\n", it->GetUid(), (long long)it->GetId());
            auto receiverIt = mConfigReceivers.find(*it);
            if (receiverIt != mConfigReceivers.end()) {
                fprintf(out, "    -> received by PendingIntent as binder\n");
            }
        }
    }
}

void ConfigManager::update_saved_configs_locked(const ConfigKey& key,
                                                const vector<uint8_t>& buffer,
                                                const int numBytes) {
    // If there is a pre-existing config with same key we should first delete it.
    remove_saved_configs(key);

    // Then we save the latest config.
    string file_name =
        StringPrintf("%s/%ld_%d_%lld", STATS_SERVICE_DIR, time(nullptr),
                     key.GetUid(), (long long)key.GetId());
    StorageManager::writeFile(file_name.c_str(), &buffer[0], numBytes);
}

}  // namespace statsd
}  // namespace os
}  // namespace android
