/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "logd/logevent_util.h"

namespace android {
namespace os {
namespace statsd {

std::optional<SocketLossInfo> toSocketLossInfo(const LogEvent& event) {
    const std::vector<FieldValue>& logEventValues = event.getValues();

    // check that logEvent contains the minimum required number of values to represent
    // SocketLossInfo atom data
    if (logEventValues.size() < 7) {
        // atom content is invalid
        return std::nullopt;
    }

    SocketLossInfo result;

    result.uid = event.GetUid();
    if (logEventValues[1].mField.getPosAtDepth(0) == 2 &&
        logEventValues[1].mValue.getType() == LONG) {
        result.firstLossTsNanos = logEventValues[1].mValue.long_value;
    } else {
        // atom content is invalid
        return std::nullopt;
    }

    if (logEventValues[2].mField.getPosAtDepth(0) == 3 &&
        logEventValues[2].mValue.getType() == LONG) {
        result.lastLossTsNanos = logEventValues[2].mValue.long_value;
    } else {
        // atom content is invalid
        return std::nullopt;
    }

    if (logEventValues[3].mField.getPosAtDepth(0) == 4 &&
        logEventValues[3].mValue.getType() == INT) {
        result.overflowCounter = logEventValues[3].mValue.int_value;
    } else {
        // atom content is invalid
        return std::nullopt;
    }

    // skipping uid + first & last timestamps + overflowCounter
    const int arraysOffset = 4;

    // expected to have 3 arrays of equal size
    const size_t expectedEntriesCount = (logEventValues.size() - arraysOffset) / 3;

    // first array holds errors, then come tags & the 3rd array holds counts
    result.errors.reserve(expectedEntriesCount);
    result.atomIds.reserve(expectedEntriesCount);
    result.counts.reserve(expectedEntriesCount);

    // scan over errors entries
    std::vector<FieldValue>::const_iterator valuesIt = logEventValues.begin() + arraysOffset;
    while (valuesIt != logEventValues.end() && valuesIt->mField.getPosAtDepth(0) == 5 &&
           valuesIt->mValue.getType() == INT) {
        result.errors.push_back(valuesIt->mValue.int_value);
        valuesIt++;
    }
    if (result.errors.size() != expectedEntriesCount) {
        // atom content is invalid
        return std::nullopt;
    }

    while (valuesIt != logEventValues.end() && valuesIt->mField.getPosAtDepth(0) == 6 &&
           valuesIt->mValue.getType() == INT) {
        result.atomIds.push_back(valuesIt->mValue.int_value);
        valuesIt++;
    }
    if (result.atomIds.size() != expectedEntriesCount) {
        // atom content is invalid
        return std::nullopt;
    }

    while (valuesIt != logEventValues.end() && valuesIt->mField.getPosAtDepth(0) == 7 &&
           valuesIt->mValue.getType() == INT) {
        result.counts.push_back(valuesIt->mValue.int_value);
        valuesIt++;
    }
    if (result.counts.size() != expectedEntriesCount) {
        // atom content is invalid
        return std::nullopt;
    }

    if (valuesIt != logEventValues.end()) {
        // atom content is invalid, some extra values are present
        return std::nullopt;
    }

    return result;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
