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

#define STATSD_DEBUG false
#include "Log.h"

#include "StatsPullerManager.h"

#include <cutils/log.h>
#include <math.h>
#include <stdint.h>

#include <algorithm>
#include <iostream>

#include "../StatsService.h"
#include "../logd/LogEvent.h"
#include "../stats_log_util.h"
#include "../statscompanion_util.h"
#include "StatsCallbackPuller.h"
#include "TrainInfoPuller.h"
#include "statslog_statsd.h"

using std::shared_ptr;
using std::vector;

namespace android {
namespace os {
namespace statsd {

// Values smaller than this may require to update the alarm.
const int64_t NO_ALARM_UPDATE = INT64_MAX;

StatsPullerManager::StatsPullerManager()
    : kAllPullAtomInfo({
              // TrainInfo.
              {{.uid = AID_STATSD, .atomTag = util::TRAIN_INFO}, new TrainInfoPuller()},
      }),
      mNextPullTimeNs(NO_ALARM_UPDATE) {
}

bool StatsPullerManager::Pull(int tagId, const ConfigKey& configKey, const int64_t eventTimeNs,
                              vector<shared_ptr<LogEvent>>* data) {
    std::lock_guard<std::mutex> _l(mLock);
    return PullLocked(tagId, configKey, eventTimeNs, data);
}

bool StatsPullerManager::Pull(int tagId, const vector<int32_t>& uids, const int64_t eventTimeNs,
                              vector<std::shared_ptr<LogEvent>>* data) {
    std::lock_guard<std::mutex> _l(mLock);
    return PullLocked(tagId, uids, eventTimeNs, data);
}

bool StatsPullerManager::PullLocked(int tagId, const ConfigKey& configKey,
                                    const int64_t eventTimeNs, vector<shared_ptr<LogEvent>>* data) {
    vector<int32_t> uids;
    const auto& uidProviderIt = mPullUidProviders.find(configKey);
    if (uidProviderIt == mPullUidProviders.end()) {
        ALOGE("Error pulling tag %d. No pull uid provider for config key %s", tagId,
              configKey.ToString().c_str());
        StatsdStats::getInstance().notePullUidProviderNotFound(tagId);
        return false;
    }
    sp<PullUidProvider> pullUidProvider = uidProviderIt->second.promote();
    if (pullUidProvider == nullptr) {
        ALOGE("Error pulling tag %d, pull uid provider for config %s is gone.", tagId,
              configKey.ToString().c_str());
        StatsdStats::getInstance().notePullUidProviderNotFound(tagId);
        return false;
    }
    uids = pullUidProvider->getPullAtomUids(tagId);
    return PullLocked(tagId, uids, eventTimeNs, data);
}

bool StatsPullerManager::PullLocked(int tagId, const vector<int32_t>& uids,
                                    const int64_t eventTimeNs, vector<shared_ptr<LogEvent>>* data) {
    VLOG("Initiating pulling %d", tagId);
    for (int32_t uid : uids) {
        PullerKey key = {.uid = uid, .atomTag = tagId};
        auto pullerIt = kAllPullAtomInfo.find(key);
        if (pullerIt != kAllPullAtomInfo.end()) {
            PullErrorCode status = pullerIt->second->Pull(eventTimeNs, data);
            VLOG("pulled %zu items", data->size());
            if (status != PULL_SUCCESS) {
                StatsdStats::getInstance().notePullFailed(tagId);
            }
            // If we received a dead object exception, it means the client process has died.
            // We can remove the puller from the map.
            if (status == PULL_DEAD_OBJECT) {
                StatsdStats::getInstance().notePullerCallbackRegistrationChanged(
                        tagId,
                        /*registered=*/false);
                kAllPullAtomInfo.erase(pullerIt);
            }
            return status == PULL_SUCCESS;
        }
    }
    StatsdStats::getInstance().notePullerNotFound(tagId);
    ALOGW("StatsPullerManager: Unknown tagId %d", tagId);
    return false;  // Return early since we don't know what to pull.
}

bool StatsPullerManager::PullerForMatcherExists(int tagId) const {
    // Pulled atoms might be registered after we parse the config, so just make sure the id is in
    // an appropriate range.
    return isVendorPulledAtom(tagId) || isPulledAtom(tagId);
}

void StatsPullerManager::updateAlarmLocked() {
    if (mNextPullTimeNs == NO_ALARM_UPDATE) {
        VLOG("No need to set alarms. Skipping");
        return;
    }

    // TODO(b/151045771): do not hold a lock while making a binder call
    if (mStatsCompanionService != nullptr) {
        mStatsCompanionService->setPullingAlarm(mNextPullTimeNs / 1000000);
    } else {
        VLOG("StatsCompanionService not available. Alarm not set.");
    }
    return;
}

void StatsPullerManager::SetStatsCompanionService(
        const shared_ptr<IStatsCompanionService>& statsCompanionService) {
    std::lock_guard<std::mutex> _l(mLock);
    shared_ptr<IStatsCompanionService> tmpForLock = mStatsCompanionService;
    mStatsCompanionService = statsCompanionService;
    for (const auto& pulledAtom : kAllPullAtomInfo) {
        pulledAtom.second->SetStatsCompanionService(statsCompanionService);
    }
    if (mStatsCompanionService != nullptr) {
        updateAlarmLocked();
    }
}

void StatsPullerManager::RegisterReceiver(int tagId, const ConfigKey& configKey,
                                          const wp<PullDataReceiver>& receiver,
                                          int64_t nextPullTimeNs, int64_t intervalNs) {
    std::lock_guard<std::mutex> _l(mLock);
    auto& receivers = mReceivers[{.atomTag = tagId, .configKey = configKey}];
    for (auto it = receivers.begin(); it != receivers.end(); it++) {
        if (it->receiver == receiver) {
            VLOG("Receiver already registered of %d", (int)receivers.size());
            return;
        }
    }
    ReceiverInfo receiverInfo;
    receiverInfo.receiver = receiver;

    // Round it to the nearest minutes. This is the limit of alarm manager.
    // In practice, we should always have larger buckets.
    int64_t roundedIntervalNs = intervalNs / NS_PER_SEC / 60 * NS_PER_SEC * 60;
    // Scheduled pulling should be at least 1 min apart.
    // This can be lower in cts tests, in which case we round it to 1 min.
    if (roundedIntervalNs < 60 * (int64_t)NS_PER_SEC) {
        roundedIntervalNs = 60 * (int64_t)NS_PER_SEC;
    }

    receiverInfo.intervalNs = roundedIntervalNs;
    receiverInfo.nextPullTimeNs = nextPullTimeNs;
    receivers.push_back(receiverInfo);

    // There is only one alarm for all pulled events. So only set it to the smallest denom.
    if (nextPullTimeNs < mNextPullTimeNs) {
        VLOG("Updating next pull time %lld", (long long)mNextPullTimeNs);
        mNextPullTimeNs = nextPullTimeNs;
        updateAlarmLocked();
    }
    VLOG("Puller for tagId %d registered of %d", tagId, (int)receivers.size());
}

void StatsPullerManager::UnRegisterReceiver(int tagId, const ConfigKey& configKey,
                                            const wp<PullDataReceiver>& receiver) {
    std::lock_guard<std::mutex> _l(mLock);
    auto receiversIt = mReceivers.find({.atomTag = tagId, .configKey = configKey});
    if (receiversIt == mReceivers.end()) {
        VLOG("Unknown pull code or no receivers: %d", tagId);
        return;
    }
    std::list<ReceiverInfo>& receivers = receiversIt->second;
    for (auto it = receivers.begin(); it != receivers.end(); it++) {
        if (receiver == it->receiver) {
            receivers.erase(it);
            VLOG("Puller for tagId %d unregistered of %d", tagId, (int)receivers.size());
            return;
        }
    }
}

void StatsPullerManager::RegisterPullUidProvider(const ConfigKey& configKey,
                                                 const wp<PullUidProvider>& provider) {
    std::lock_guard<std::mutex> _l(mLock);
    mPullUidProviders[configKey] = provider;
}

void StatsPullerManager::UnregisterPullUidProvider(const ConfigKey& configKey,
                                                   const wp<PullUidProvider>& provider) {
    std::lock_guard<std::mutex> _l(mLock);
    const auto& it = mPullUidProviders.find(configKey);
    if (it != mPullUidProviders.end() && it->second == provider) {
        mPullUidProviders.erase(it);
    }
}

void StatsPullerManager::OnAlarmFired(int64_t elapsedTimeNs) {
    std::lock_guard<std::mutex> _l(mLock);
    int64_t wallClockNs = getWallClockNs();

    int64_t minNextPullTimeNs = NO_ALARM_UPDATE;

    vector<pair<const ReceiverKey*, vector<ReceiverInfo*>>> needToPull;
    for (auto& pair : mReceivers) {
        vector<ReceiverInfo*> receivers;
        if (pair.second.size() != 0) {
            for (ReceiverInfo& receiverInfo : pair.second) {
                // If pullNecessary and enough time has passed for the next bucket, then add
                // receiver to the list that will pull on this alarm.
                // If pullNecessary is false, check if next pull time needs to be updated.
                sp<PullDataReceiver> receiverPtr = receiverInfo.receiver.promote();
                const bool pullNecessary = receiverPtr != nullptr && receiverPtr->isPullNeeded();
                if (receiverInfo.nextPullTimeNs <= elapsedTimeNs && pullNecessary) {
                    receivers.push_back(&receiverInfo);
                } else {
                    if (receiverInfo.nextPullTimeNs <= elapsedTimeNs) {
                        receiverPtr->onDataPulled({}, PullResult::PULL_NOT_NEEDED, elapsedTimeNs);
                        int numBucketsAhead = (elapsedTimeNs - receiverInfo.nextPullTimeNs) /
                                              receiverInfo.intervalNs;
                        receiverInfo.nextPullTimeNs +=
                                (numBucketsAhead + 1) * receiverInfo.intervalNs;
                    }
                    minNextPullTimeNs = min(receiverInfo.nextPullTimeNs, minNextPullTimeNs);
                }
            }
            if (receivers.size() > 0) {
                needToPull.push_back(make_pair(&pair.first, receivers));
            }
        }
    }
    for (const auto& pullInfo : needToPull) {
        vector<shared_ptr<LogEvent>> data;
        PullResult pullResult =
                PullLocked(pullInfo.first->atomTag, pullInfo.first->configKey, elapsedTimeNs, &data)
                        ? PullResult::PULL_RESULT_SUCCESS
                        : PullResult::PULL_RESULT_FAIL;
        if (pullResult == PullResult::PULL_RESULT_FAIL) {
            VLOG("pull failed at %lld, will try again later", (long long)elapsedTimeNs);
        }

        // Convention is to mark pull atom timestamp at request time.
        // If we pull at t0, puller starts at t1, finishes at t2, and send back
        // at t3, we mark t0 as its timestamp, which should correspond to its
        // triggering event, such as condition change at t0.
        // Here the triggering event is alarm fired from AlarmManager.
        // In ValueMetricProducer and GaugeMetricProducer we do same thing
        // when pull on condition change, etc.
        for (auto& event : data) {
            event->setElapsedTimestampNs(elapsedTimeNs);
            event->setLogdWallClockTimestampNs(wallClockNs);
        }

        for (const auto& receiverInfo : pullInfo.second) {
            sp<PullDataReceiver> receiverPtr = receiverInfo->receiver.promote();
            if (receiverPtr != nullptr) {
                receiverPtr->onDataPulled(data, pullResult, elapsedTimeNs);
                // We may have just come out of a coma, compute next pull time.
                int numBucketsAhead =
                        (elapsedTimeNs - receiverInfo->nextPullTimeNs) / receiverInfo->intervalNs;
                receiverInfo->nextPullTimeNs += (numBucketsAhead + 1) * receiverInfo->intervalNs;
                minNextPullTimeNs = min(receiverInfo->nextPullTimeNs, minNextPullTimeNs);
            } else {
                VLOG("receiver already gone.");
            }
        }
    }

    VLOG("mNextPullTimeNs: %lld updated to %lld", (long long)mNextPullTimeNs,
         (long long)minNextPullTimeNs);
    mNextPullTimeNs = minNextPullTimeNs;
    updateAlarmLocked();
}

int StatsPullerManager::ForceClearPullerCache() {
    std::lock_guard<std::mutex> _l(mLock);
    int totalCleared = 0;
    for (const auto& pulledAtom : kAllPullAtomInfo) {
        totalCleared += pulledAtom.second->ForceClearCache();
    }
    return totalCleared;
}

int StatsPullerManager::ClearPullerCacheIfNecessary(int64_t timestampNs) {
    std::lock_guard<std::mutex> _l(mLock);
    int totalCleared = 0;
    for (const auto& pulledAtom : kAllPullAtomInfo) {
        totalCleared += pulledAtom.second->ClearCacheIfNecessary(timestampNs);
    }
    return totalCleared;
}

void StatsPullerManager::RegisterPullAtomCallback(const int uid, const int32_t atomTag,
                                                  const int64_t coolDownNs, const int64_t timeoutNs,
                                                  const vector<int32_t>& additiveFields,
                                                  const shared_ptr<IPullAtomCallback>& callback) {
    std::lock_guard<std::mutex> _l(mLock);
    VLOG("RegisterPullerCallback: adding puller for tag %d", atomTag);

    if (callback == nullptr) {
        ALOGW("SetPullAtomCallback called with null callback for atom %d.", atomTag);
        return;
    }

    int64_t actualCoolDownNs = coolDownNs < kMinCoolDownNs ? kMinCoolDownNs : coolDownNs;
    int64_t actualTimeoutNs = timeoutNs > kMaxTimeoutNs ? kMaxTimeoutNs : timeoutNs;

    sp<StatsCallbackPuller> puller = new StatsCallbackPuller(atomTag, callback, actualCoolDownNs,
                                                             actualTimeoutNs, additiveFields);
    PullerKey key = {.uid = uid, .atomTag = atomTag};
    auto it = kAllPullAtomInfo.find(key);
    if (it != kAllPullAtomInfo.end()) {
        StatsdStats::getInstance().notePullerCallbackRegistrationChanged(atomTag,
                                                                         /*registered=*/false);
    }
    kAllPullAtomInfo[key] = puller;
    StatsdStats::getInstance().notePullerCallbackRegistrationChanged(atomTag, /*registered=*/true);
}

void StatsPullerManager::UnregisterPullAtomCallback(const int uid, const int32_t atomTag) {
    std::lock_guard<std::mutex> _l(mLock);
    PullerKey key = {.uid = uid, .atomTag = atomTag};
    if (kAllPullAtomInfo.find(key) != kAllPullAtomInfo.end()) {
        StatsdStats::getInstance().notePullerCallbackRegistrationChanged(atomTag,
                                                                         /*registered=*/false);
        kAllPullAtomInfo.erase(key);
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
