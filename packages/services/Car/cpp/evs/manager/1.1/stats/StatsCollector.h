/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_1_1_STATS_STATSCOLLECTOR_H_
#define CPP_EVS_MANAGER_1_1_STATS_STATSCOLLECTOR_H_

#include "CameraUsageStats.h"
#include "IStatsCollector.h"
#include "LooperWrapper.h"
#include "VirtualCamera.h"

#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <android/hardware/automotive/evs/1.1/types.h>
#include <utils/Mutex.h>

#include <deque>
#include <thread>  // NOLINT
#include <unordered_map>
#include <vector>

namespace android::automotive::evs::V1_1::implementation {

enum CollectionEvent {
    INIT = 0,
    PERIODIC,
    CUSTOM_START,
    CUSTOM_END,
    TERMINATED,

    LAST_EVENT,
};

struct CollectionRecord {
    // Latest statistics collection
    CameraUsageStatsRecord latest = {};

    // History of collected statistics records
    std::deque<CameraUsageStatsRecord> history;
};

struct CollectionInfo {
    // Collection interval between two subsequent collections
    std::chrono::nanoseconds interval = 0ns;

    // The maximum number of records this collection stores
    size_t maxCacheSize = 0;

    // Time when the latest collection was done
    nsecs_t lastCollectionTime = 0;

    // Collected statistics records per instances
    std::unordered_map<std::string, CollectionRecord> records;
};

// Statistic collector for camera usage statistics.
// Statistics are not collected until |startCollection|.
class StatsCollector : public IStatsCollector, public MessageHandler {
public:
    StatsCollector() :
          mLooper(new LooperWrapper()),
          mCurrentCollectionEvent(CollectionEvent::INIT),
          mPeriodicCollectionInfo({}),
          mCustomCollectionInfo({}) {}

    virtual ~StatsCollector();

    android::base::Result<void> startCollection() override;

    // Starts collecting CameraUsageStarts during a given duration at a given
    // interval.
    android::base::Result<void> startCustomCollection(std::chrono::nanoseconds interval,
                                                      std::chrono::nanoseconds duration)
            EXCLUDES(mMutex) override;

    // Stops current custom collection and shows the result from the device with
    // a given unique id.  If this is "all", all results will be returned.
    android::base::Result<std::string> stopCustomCollection(std::string id = "")
            EXCLUDES(mMutex) override;

    // Registers HalCamera object to monitor
    android::base::Result<void> registerClientToMonitor(const android::sp<HalCamera>& camera)
            EXCLUDES(mMutex) override;

    // Unregister HalCamera object.
    android::base::Result<void> unregisterClientToMonitor(const std::string& id)
            EXCLUDES(mMutex) override;

    // Returns a map that contains the latest statistics pulled from
    // currently active clients.
    std::unordered_map<std::string, std::string> toString(const char* indent = "") override;

private:
    // Mutex to protect records
    mutable Mutex mMutex;

    // Looper to message the collection thread
    android::sp<LooperWrapper> mLooper;

    // Background thread to pull stats from the clients
    std::thread mCollectionThread;

    // Current state of the monitor
    CollectionEvent mCurrentCollectionEvent GUARDED_BY(mMutex);

    // Periodic collection information
    CollectionInfo mPeriodicCollectionInfo GUARDED_BY(mMutex);

    // A collection during the custom period the user sets
    CollectionInfo mCustomCollectionInfo GUARDED_BY(mMutex);

    // A list of HalCamera objects to monitor
    std::unordered_map<std::string, android::wp<HalCamera>> mClientsToMonitor GUARDED_BY(mMutex);

    // Handles the messages from the looper
    void handleMessage(const Message& message) override;

    // Handles each CollectionEvent
    android::base::Result<void> handleCollectionEvent(CollectionEvent event, CollectionInfo* info)
            EXCLUDES(mMutex);

    // Pulls the statistics from each active HalCamera objects and generates the
    // records
    android::base::Result<void> collectLocked(CollectionInfo* info) REQUIRES(mMutex);

    // Returns a string corresponding to a given collection event.
    std::string collectionEventToString(const CollectionEvent& event) const;
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_STATS_STATSCOLLECTOR_H_
