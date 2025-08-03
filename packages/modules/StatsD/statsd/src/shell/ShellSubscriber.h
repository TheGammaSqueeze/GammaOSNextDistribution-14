/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <aidl/android/os/IStatsSubscriptionCallback.h>

#include <condition_variable>
#include <mutex>
#include <thread>

#include "external/StatsPullerManager.h"
#include "packages/UidMap.h"
#include "shell/ShellSubscriberClient.h"
#include "src/shell/shell_config.pb.h"
#include "src/statsd_config.pb.h"

namespace android {
namespace os {
namespace statsd {

/**
 * Handles atoms subscription via shell cmd.
 *
 * A shell subscription lasts *until shell exits*. Unlike config based clients, a shell client
 * communicates with statsd via file descriptors. They can subscribe pushed and pulled atoms.
 * The atoms are sent back to the client in real time, as opposed to keeping the data in memory.
 * Shell clients do not subscribe aggregated metrics, as they are responsible for doing the
 * aggregation after receiving the atom events.
 *
 * Shell clients pass ShellSubscription in the proto binary format. Clients can update the
 * subscription by sending a new subscription. The new subscription would replace the old one.
 * Input data stream format is:
 *
 * |size_t|subscription proto|size_t|subscription proto|....
 *
 * statsd sends the events back in Atom proto binary format. Each Atom message is preceded
 * with sizeof(size_t) bytes indicating the size of the proto message payload.
 *
 * The stream would be in the following format:
 * |size_t|shellData proto|size_t|shellData proto|....
 *
 */
class ShellSubscriber : public virtual RefBase {
public:
    ShellSubscriber(const sp<UidMap>& uidMap, const sp<StatsPullerManager>& pullerMgr,
                    const std::shared_ptr<LogEventFilter>& logEventFilter)
        : mUidMap(uidMap), mPullerMgr(pullerMgr), mLogEventFilter(logEventFilter){};

    ~ShellSubscriber();

    // Create new ShellSubscriberClient with file descriptors to manage a new subscription.
    bool startNewSubscription(int inFd, int outFd, int64_t timeoutSec);

    // Create new ShellSubscriberClient with Binder callback to manage a new subscription.
    bool startNewSubscription(
            const vector<uint8_t>& subscriptionConfig,
            const shared_ptr<aidl::android::os::IStatsSubscriptionCallback>& callback);

    void onLogEvent(const LogEvent& event);

    void flushSubscription(
            const shared_ptr<aidl::android::os::IStatsSubscriptionCallback>& callback);

    void unsubscribe(const shared_ptr<aidl::android::os::IStatsSubscriptionCallback>& callback);

    static size_t getMaxSizeKb() {
        return ShellSubscriberClient::getMaxSizeKb();
    }

    static size_t getMaxSubscriptions() {
        return kMaxSubscriptions;
    }

private:
    bool startNewSubscriptionLocked(unique_ptr<ShellSubscriberClient> client);

    void pullAndSendHeartbeats();

    /* Tells LogEventFilter about atom ids to parse */
    void updateLogEventFilterLocked() const;

    sp<UidMap> mUidMap;

    sp<StatsPullerManager> mPullerMgr;

    std::shared_ptr<LogEventFilter> mLogEventFilter;

    // Protects mClientSet, mThreadAlive, and ShellSubscriberClient
    mutable std::mutex mMutex;

    std::set<unique_ptr<ShellSubscriberClient>> mClientSet;

    bool mThreadAlive = false;

    std::condition_variable mThreadSleepCV;

    std::thread mThread;

    static constexpr size_t kMaxSubscriptions = 20;
};

}  // namespace statsd
}  // namespace os
}  // namespace android
