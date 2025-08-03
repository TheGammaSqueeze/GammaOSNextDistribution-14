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

#include "SimpleAtomMatchingTracker.h"

namespace android {
namespace os {
namespace statsd {

using std::shared_ptr;
using std::unordered_map;
using std::vector;

SimpleAtomMatchingTracker::SimpleAtomMatchingTracker(const int64_t id, const uint64_t protoHash,
                                                     const SimpleAtomMatcher& matcher,
                                                     const sp<UidMap>& uidMap)
    : AtomMatchingTracker(id, protoHash), mMatcher(matcher), mUidMap(uidMap) {
    if (!matcher.has_atom_id()) {
        mInitialized = false;
    } else {
        mAtomIds.insert(matcher.atom_id());
        mInitialized = true;
    }
}

SimpleAtomMatchingTracker::~SimpleAtomMatchingTracker() {
}

MatcherInitResult SimpleAtomMatchingTracker::init(
        int matcherIndex, const vector<AtomMatcher>& allAtomMatchers,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& matcherMap, vector<uint8_t>& stack) {
    MatcherInitResult result{nullopt /* invalidConfigReason */,
                             false /* hasStringTransformation */};
    // no need to do anything.
    if (!mInitialized) {
        result.invalidConfigReason = createInvalidConfigReasonWithMatcher(
                INVALID_CONFIG_REASON_MATCHER_TRACKER_NOT_INITIALIZED, mId);
        return result;
    }

    for (const FieldValueMatcher& fvm : mMatcher.field_value_matcher()) {
        if (fvm.has_replace_string()) {
            result.hasStringTransformation = true;
            break;
        }
    }

    return result;
}

optional<InvalidConfigReason> SimpleAtomMatchingTracker::onConfigUpdated(
        const AtomMatcher& matcher, const unordered_map<int64_t, int>& atomMatchingTrackerMap) {
    // Do not need to update mMatcher since the matcher must be identical across the update.
    if (!mInitialized) {
        return createInvalidConfigReasonWithMatcher(
                INVALID_CONFIG_REASON_MATCHER_TRACKER_NOT_INITIALIZED, mId);
    }
    return nullopt;
}

void SimpleAtomMatchingTracker::onLogEvent(
        const LogEvent& event, int matcherIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        vector<MatchingState>& matcherResults,
        vector<shared_ptr<LogEvent>>& matcherTransformations) {
    if (matcherResults[matcherIndex] != MatchingState::kNotComputed) {
        VLOG("Matcher %lld already evaluated ", (long long)mId);
        return;
    }

    if (mAtomIds.find(event.GetTagId()) == mAtomIds.end()) {
        matcherResults[matcherIndex] = MatchingState::kNotMatched;
        return;
    }

    auto [matched, transformedEvent] = matchesSimple(mUidMap, mMatcher, event);
    matcherResults[matcherIndex] = matched ? MatchingState::kMatched : MatchingState::kNotMatched;
    VLOG("Stats SimpleAtomMatcher %lld matched? %d", (long long)mId, matched);

    if (matched && transformedEvent != nullptr) {
        matcherTransformations[matcherIndex] = std::move(transformedEvent);
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
