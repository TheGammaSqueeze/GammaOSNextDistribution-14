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

#pragma once

#include <android-base/thread_annotations.h>
#include <audio_utils/MelProcessor.h>
#include <map>
#include <mutex>

namespace android::audio_utils {

struct MelRecord {
    /** The port ID of the audio device where the MEL value was recorded. */
    audio_port_handle_t portId;
    /**
     * Array of continuously recorded MEL values >= RS1 (1 per second). First
     * value in the array was recorded at time: timestamp.
     */
    std::vector<float> mels;
    /** Corresponds to the time when the first MEL entry in MelRecord was recorded. */
    int64_t timestamp;

    MelRecord(audio_port_handle_t portId,
              std::vector<float> mels,
              int64_t timestamp)
        : portId(portId), mels(std::move(mels)), timestamp(timestamp) {}

    inline bool overlapsEnd(const MelRecord& record) const {
        return timestamp + static_cast<int64_t>(mels.size()) > record.timestamp;
    }
};

struct CsdRecord {
    /** Corresponds to the time when the CSD value is calculated from. */
    const int64_t timestamp;
    /** Corresponds to the duration that leads to the CSD value. */
    const size_t duration;
    /** The actual contribution to the CSD computation normalized: 1.f is 100%CSD. */
    const float value;
    /** The average MEL value that lead to this CSD value. */
    const float averageMel;

    CsdRecord(int64_t timestamp,
              size_t duration,
              float value,
              float averageMel)
        : timestamp(timestamp),
          duration(duration),
          value(value),
          averageMel(averageMel) {};
};

/**
 * Class used to aggregate MEL values from different streams that play sounds
 * simultaneously.
 *
 * The public methods are internally protected by a mutex to be thread-safe.
 */
class MelAggregator : public RefBase {
public:

    explicit MelAggregator(int64_t csdWindowSeconds)
        : mCsdWindowSeconds(csdWindowSeconds) {}

    /**
     * \returns the size of the stored CSD values.
     */
    size_t getCsdRecordsSize() const;

    /**
     * \brief Iterate over the CsdRecords and applies function f.
     *
     * \param f      function to apply on the iterated CsdRecord's sorted by timestamp
     */
    void foreachCsd(const std::function<void(const CsdRecord&)>& f) const;

    /** Returns the current CSD computed with a rolling window of mCsdWindowSeconds. */
    float getCsd();

    /**
     * \returns the size of the stored MEL records.
     */
    size_t getCachedMelRecordsSize() const;

    /**
     * \brief Iterate over the MelRecords and applies function f.
     *
     * \param f      function to apply on the iterated MelRecord's sorted by timestamp
     */
    void foreachCachedMel(const std::function<void(const MelRecord&)>& f) const;

    /**
     * New value are stored and MEL values that correspond to the same timestamp
     * will be aggregated.
     *
     * \returns a vector containing all the new CsdRecord's that were added to
     *   the current CSD value. Vector could be empty in case no new records
     *   contributed to CSD.
     */
    virtual std::vector<CsdRecord> aggregateAndAddNewMelRecord(const MelRecord& record);

    /**
     * Reset the aggregator values. Discards all the previous cached values and
     * uses the passed records for the new callbacks.
     **/
    void reset(float newCsd, const std::vector<CsdRecord>& newRecords);
private:
    /** Locked aggregateAndAddNewMelRecord method. */
    std::vector<CsdRecord> aggregateAndAddNewMelRecord_l(const MelRecord& record) REQUIRES(mLock);

    void removeOldCsdRecords_l(std::vector<CsdRecord>& removeRecords) REQUIRES(mLock);

    std::vector<CsdRecord> updateCsdRecords_l() REQUIRES(mLock);

    int64_t csdTimeIntervalStored_l() REQUIRES(mLock);

    std::map<int64_t, CsdRecord>::iterator addNewestCsdRecord_l(int64_t timestamp,
                                                                int64_t duration,
                                                                 float csdRecord,
                                                                 float averageMel) REQUIRES(mLock);

    const int64_t mCsdWindowSeconds;

    mutable std::mutex mLock;

    std::map<int64_t, MelRecord> mMelRecords GUARDED_BY(mLock);
    std::map<int64_t, CsdRecord> mCsdRecords GUARDED_BY(mLock);

    /** Current CSD value in mMelRecords. */
    float mCurrentMelRecordsCsd GUARDED_BY(mLock) = 0.f;

    /** CSD value containing sum of all CSD values stored. */
    float mCurrentCsd GUARDED_BY(mLock) = 0.f;
};

}  // naemspace android::audio_utils
