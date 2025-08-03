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

#pragma once

#include <aidl/android/os/IPendingIntentRef.h>
#include <stdio.h>

#include <mutex>
#include <string>

#include "config/ConfigKey.h"
#include "config/ConfigKeyWithPackage.h"
#include "config/ConfigListener.h"

using aidl::android::os::IPendingIntentRef;
using std::shared_ptr;

namespace android {
namespace os {
namespace statsd {

/**
 * Keeps track of which configurations have been set from various sources.
 */
class ConfigManager : public virtual RefBase {
public:
    ConfigManager();
    virtual ~ConfigManager();

    /**
     * Initialize ConfigListener by reading from disk and get updates.
     */
    void Startup();

    /*
     * No-op initializer for tests.
     */
    void StartupForTest();

    /**
     * Someone else wants to know about the configs.
     */
    void AddListener(const sp<ConfigListener>& listener);

    /**
     * A configuration was added or updated.
     *
     * Reports this to listeners.
     */
    void UpdateConfig(const ConfigKey& key, const StatsdConfig& data);

    /**
     * Sets the broadcast receiver for a configuration key.
     */
    void SetConfigReceiver(const ConfigKey& key, const shared_ptr<IPendingIntentRef>& pir);

    /**
     * Returns the package name and class name representing the broadcast receiver for this config.
     */
    const shared_ptr<IPendingIntentRef> GetConfigReceiver(const ConfigKey& key) const;

    /**
     * Returns all config keys registered.
     */
    std::vector<ConfigKey> GetAllConfigKeys() const;

    /**
     * Erase any broadcast receiver associated with this config key.
     */
    void RemoveConfigReceiver(const ConfigKey& key);

    /**
     * Erase the broadcast receiver for this config key if it is equal to the provided broadcast
     * receiver.
     */
    void RemoveConfigReceiver(const ConfigKey& key, const shared_ptr<IPendingIntentRef>& pir);

    /**
     * Sets the broadcast receiver that is notified whenever the list of active configs
     * changes for this uid.
     */
    void SetActiveConfigsChangedReceiver(const int uid, const shared_ptr<IPendingIntentRef>& pir);

    /**
     * Returns the broadcast receiver for active configs changed for this uid.
     */

    const shared_ptr<IPendingIntentRef> GetActiveConfigsChangedReceiver(const int uid) const;

    /**
     * Erase any active configs changed broadcast receiver associated with this uid.
     */
    void RemoveActiveConfigsChangedReceiver(const int uid);

    /**
     * Erase the active configs changed broadcast receiver associated with this uid if it is equal
     * to the provided broadcast receiver.
     */
    void RemoveActiveConfigsChangedReceiver(const int uid,
                                            const shared_ptr<IPendingIntentRef>& pir);

    /**
     * Sets the pending intent that is notified whenever the list of restricted metrics changes
     */
    void SetRestrictedMetricsChangedReceiver(const string& configPackage, int64_t configId,
                                             const int32_t callingUid,
                                             const shared_ptr<IPendingIntentRef>& pir);

    /**
     * Erase any restricted metrics changed pending intents associated with this config key & uid.
     */
    void RemoveRestrictedMetricsChangedReceiver(const string& configPackage, int64_t configId,
                                                const int32_t callingUid);

    /**
     * Sends a restricted metrics broadcast for the valid config keys and delegate package
     */
    void SendRestrictedMetricsBroadcast(const std::set<string>& configPackages,
                                        const int64_t configId,
                                        const std::set<int32_t>& delegateUids,
                                        const std::vector<int64_t>& metricIds);

    /**
     * A configuration was removed.
     *
     * Reports this to listeners.
     */
    void RemoveConfig(const ConfigKey& key);

    /**
     * Remove all of the configs for the given uid.
     */
    void RemoveConfigs(int uid);

    /**
     * Remove all of the configs from memory.
     */
    void RemoveAllConfigs();

    /**
     * Text dump of our state for debugging.
     */
    void Dump(FILE* out);

private:
    mutable std::mutex mMutex;

    /**
     * Save the configs to disk.
     */
    void update_saved_configs_locked(const ConfigKey& key,
                                     const std::vector<uint8_t>& buffer,
                                     const int numBytes);

    /**
     * Remove saved configs from disk.
     */
    void remove_saved_configs(const ConfigKey& key);

    /**
     * Maps from uid to the config keys that have been set.
     */
    std::map<int, std::set<ConfigKey>> mConfigs;

    /**
     * Each config key can be subscribed by up to one receiver, specified as IPendingIntentRef.
     */
    std::map<ConfigKey, shared_ptr<IPendingIntentRef>> mConfigReceivers;

    /**
     * Each uid can be subscribed by up to one receiver to notify that the list of active configs
     * for this uid has changed. The receiver is specified as IPendingIntentRef.
     */
    std::map<int, shared_ptr<IPendingIntentRef>> mActiveConfigsChangedReceivers;

    /**
     * Each uid can subscribe up to one receiver for a particular config to receive the restricted
     * metrics for that config. The receiver is specified as IPendingIntentRef.
     */
    std::map<ConfigKeyWithPackage, std::map<int32_t, shared_ptr<IPendingIntentRef>>>
            mRestrictedMetricsChangedReceivers;

    /**
     * The ConfigListeners that will be told about changes.
     */
    std::vector<sp<ConfigListener>> mListeners;

    /**
     * Erase the restricted metrics changed pending intents associated with this config key & uid if
     * it is equal to the provided pending intent.
     */
    void RemoveRestrictedMetricsChangedReceiver(const ConfigKeyWithPackage& key,
                                                const int32_t delegateUid,
                                                const shared_ptr<IPendingIntentRef>& pir);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
