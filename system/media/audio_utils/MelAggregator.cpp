/*
 * Copyright 2022 The Android Open Source Project
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

// #define LOG_NDEBUG 0
#define LOG_TAG "audio_utils_MelAggregator"

#include <audio_utils/MelAggregator.h>
#include <audio_utils/power.h>
#include <cinttypes>
#include <iterator>
#include <utils/Log.h>

namespace android::audio_utils {
namespace {

/** Min value after which the MEL values are aggregated to CSD. */
constexpr float kMinCsdRecordToStore = 0.01f;

/** Threshold for 100% CSD expressed in Pa^2s. */
constexpr float kCsdThreshold = 5760.0f; // 1.6f(Pa^2h) * 3600.0f(s);

/** Reference energy used for dB calculation in Pa^2. */
constexpr float kReferenceEnergyPa = 4e-10;

/**
 * Checking the intersection of the time intervals of v1 and v2. Each MelRecord v
 * spawns an interval [t1, t2) if and only if:
 *    v.timestamp == t1 && v.mels.size() == t2 - t1
 **/
std::pair<int64_t, int64_t> intersectRegion(const MelRecord& v1, const MelRecord& v2)
{
    const int64_t maxStart = std::max(v1.timestamp, v2.timestamp);
    const int64_t v1End = v1.timestamp + v1.mels.size();
    const int64_t v2End = v2.timestamp + v2.mels.size();
    const int64_t minEnd = std::min(v1End, v2End);
    return {maxStart, minEnd};
}

float aggregateMels(const float mel1, const float mel2) {
    return audio_utils_power_from_energy(powf(10.f, mel1 / 10.f) + powf(10.f, mel2 / 10.f));
}

float averageMelEnergy(const float mel1,
                       const int64_t duration1,
                       const float mel2,
                       const int64_t duration2) {
    return audio_utils_power_from_energy((powf(10.f, mel1 / 10.f) * duration1
        + powf(10.f, mel2 / 10.f) * duration2) / (duration1 + duration2));
}

float melToCsd(float mel) {
    float energy = powf(10.f, mel / 10.0f);
    return kReferenceEnergyPa * energy / kCsdThreshold;
}

CsdRecord createRevertedRecord(const CsdRecord& record) {
    return {record.timestamp, record.duration, -record.value, record.averageMel};
}

}  // namespace

int64_t MelAggregator::csdTimeIntervalStored_l()
{
    return mCsdRecords.rbegin()->second.timestamp + mCsdRecords.rbegin()->second.duration
        - mCsdRecords.begin()->second.timestamp;
}

std::map<int64_t, CsdRecord>::iterator MelAggregator::addNewestCsdRecord_l(int64_t timestamp,
                                                                           int64_t duration,
                                                                           float csdRecord,
                                                                           float averageMel)
{
    ALOGV("%s: add new csd[%" PRId64 ", %" PRId64 "]=%f for MEL avg %f",
                      __func__,
                      timestamp,
                      duration,
                      csdRecord,
                      averageMel);

    mCurrentCsd += csdRecord;
    return mCsdRecords.emplace_hint(mCsdRecords.end(),
                                    timestamp,
                                    CsdRecord(timestamp,
                                              duration,
                                              csdRecord,
                                              averageMel));
}

void MelAggregator::removeOldCsdRecords_l(std::vector<CsdRecord>& removeRecords) {
    // Remove older CSD values
    while (!mCsdRecords.empty() && csdTimeIntervalStored_l() > mCsdWindowSeconds) {
        mCurrentCsd -= mCsdRecords.begin()->second.value;
        removeRecords.emplace_back(createRevertedRecord(mCsdRecords.begin()->second));
        mCsdRecords.erase(mCsdRecords.begin());
    }
}

std::vector<CsdRecord> MelAggregator::updateCsdRecords_l()
{
    std::vector<CsdRecord> newRecords;

    // only update if we are above threshold
    if (mCurrentMelRecordsCsd < kMinCsdRecordToStore) {
        removeOldCsdRecords_l(newRecords);
        return newRecords;
    }

    float converted = 0.f;
    float averageMel = 0.f;
    float csdValue = 0.f;
    int64_t duration = 0;
    int64_t timestamp = mMelRecords.begin()->first;
    for (const auto& storedMel: mMelRecords) {
        int melsIdx = 0;
        for (const auto& mel: storedMel.second.mels) {
            averageMel = averageMelEnergy(averageMel, duration, mel, 1.f);
            csdValue += melToCsd(mel);
            ++duration;
            if (csdValue >= kMinCsdRecordToStore
                && mCurrentMelRecordsCsd - converted - csdValue >= kMinCsdRecordToStore) {
                auto it = addNewestCsdRecord_l(timestamp,
                                               duration,
                                               csdValue,
                                               averageMel);
                newRecords.emplace_back(it->second);

                duration = 0;
                averageMel = 0.f;
                converted += csdValue;
                csdValue = 0.f;
                timestamp = storedMel.first + melsIdx;
            }
            ++ melsIdx;
        }
    }

    if(csdValue > 0) {
        auto it = addNewestCsdRecord_l(timestamp,
                                       duration,
                                       csdValue,
                                       averageMel);
        newRecords.emplace_back(it->second);
    }

    removeOldCsdRecords_l(newRecords);

    // reset mel values
    mCurrentMelRecordsCsd = 0.0f;
    mMelRecords.clear();

    return newRecords;
}

std::vector<CsdRecord> MelAggregator::aggregateAndAddNewMelRecord(const MelRecord& mel)
{
    std::lock_guard _l(mLock);
    return aggregateAndAddNewMelRecord_l(mel);
}

std::vector<CsdRecord> MelAggregator::aggregateAndAddNewMelRecord_l(const MelRecord& mel)
{
    for (const auto& m : mel.mels) {
        mCurrentMelRecordsCsd += melToCsd(m);
    }
    ALOGV("%s: current mel values CSD %f", __func__, mCurrentMelRecordsCsd);

    auto mergeIt = mMelRecords.lower_bound(mel.timestamp);

    if (mergeIt != mMelRecords.begin()) {
        auto prevMergeIt = std::prev(mergeIt);
        if (prevMergeIt->second.overlapsEnd(mel)) {
            mergeIt = prevMergeIt;
        }
    }

    int64_t newTimestamp = mel.timestamp;
    std::vector<float> newMels = mel.mels;
    auto mergeStart = mergeIt;
    int overlapStart = 0;
    while(mergeIt != mMelRecords.end()) {
        const auto& [melRecordStart, melRecord] = *mergeIt;
        const auto [regionStart, regionEnd] = intersectRegion(melRecord, mel);
        if (regionStart >= regionEnd) {
            // no intersection
            break;
        }

        if (melRecordStart < regionStart) {
            newTimestamp = melRecordStart;
            overlapStart = regionStart - melRecordStart;
            newMels.insert(newMels.begin(), melRecord.mels.begin(),
                           melRecord.mels.begin() + overlapStart);
        }

        for (int64_t aggregateTime = regionStart; aggregateTime < regionEnd; ++aggregateTime) {
            const int offsetStored = aggregateTime - melRecordStart;
            const int offsetNew = aggregateTime - mel.timestamp;
            newMels[overlapStart + offsetNew] =
                aggregateMels(melRecord.mels[offsetStored], mel.mels[offsetNew]);
        }

        const int64_t mergeEndTime = melRecordStart + melRecord.mels.size();
        if (mergeEndTime > regionEnd) {
            newMels.insert(newMels.end(),
                           melRecord.mels.end() - mergeEndTime + regionEnd,
                           melRecord.mels.end());
        }

        ++mergeIt;
    }

    auto hint = mergeIt;
    if (mergeStart != mergeIt) {
        hint = mMelRecords.erase(mergeStart, mergeIt);
    }

    mMelRecords.emplace_hint(hint,
                             newTimestamp,
                             MelRecord(mel.portId, newMels, newTimestamp));

    return updateCsdRecords_l();
}

void MelAggregator::reset(float newCsd, const std::vector<CsdRecord>& newRecords)
{
    std::lock_guard _l(mLock);
    mCsdRecords.clear();
    mMelRecords.clear();

    mCurrentCsd = newCsd;
    for (const auto& record : newRecords) {
        mCsdRecords.emplace_hint(mCsdRecords.end(), record.timestamp, record);
    }
}

size_t MelAggregator::getCachedMelRecordsSize() const
{
    std::lock_guard _l(mLock);
    return mMelRecords.size();
}

void MelAggregator::foreachCachedMel(const std::function<void(const MelRecord&)>& f) const
{
     std::lock_guard _l(mLock);
     for (const auto &melRecord : mMelRecords) {
         f(melRecord.second);
     }
}

float MelAggregator::getCsd() {
    std::lock_guard _l(mLock);
    return mCurrentCsd;
}

size_t MelAggregator::getCsdRecordsSize() const {
    std::lock_guard _l(mLock);
    return mCsdRecords.size();
}

void MelAggregator::foreachCsd(const std::function<void(const CsdRecord&)>& f) const
{
     std::lock_guard _l(mLock);
     for (const auto &csdRecord : mCsdRecords) {
         f(csdRecord.second);
     }
}

}  // namespace android::audio_utils
