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

#include "Log.h"

#include "CombinationAtomMatchingTracker.h"

#include "matchers/matcher_util.h"

namespace android {
namespace os {
namespace statsd {

using std::set;
using std::shared_ptr;
using std::unordered_map;
using std::vector;

CombinationAtomMatchingTracker::CombinationAtomMatchingTracker(const int64_t id,
                                                               const uint64_t protoHash)
    : AtomMatchingTracker(id, protoHash) {
}

CombinationAtomMatchingTracker::~CombinationAtomMatchingTracker() {
}

MatcherInitResult CombinationAtomMatchingTracker::init(
        int matcherIndex, const vector<AtomMatcher>& allAtomMatchers,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        const unordered_map<int64_t, int>& matcherMap, vector<uint8_t>& stack) {
    MatcherInitResult result{nullopt /* invalidConfigReason */,
                             false /* hasStringTransformation */};
    if (mInitialized) {
        // CombinationMatchers do not support string transformations so if mInitialized = true,
        // we know that there is no string transformation and we do not need to check for it again.
        return result;
    }

    // mark this node as visited in the recursion stack.
    stack[matcherIndex] = true;

    AtomMatcher_Combination matcher = allAtomMatchers[matcherIndex].combination();

    // LogicalOperation is missing in the config
    if (!matcher.has_operation()) {
        result.invalidConfigReason = createInvalidConfigReasonWithMatcher(
                INVALID_CONFIG_REASON_MATCHER_NO_OPERATION, mId);
        return result;
    }

    mLogicalOperation = matcher.operation();

    if (mLogicalOperation == LogicalOperation::NOT && matcher.matcher_size() != 1) {
        result.invalidConfigReason = createInvalidConfigReasonWithMatcher(
                INVALID_CONFIG_REASON_MATCHER_NOT_OPERATION_IS_NOT_UNARY, mId);
        return result;
    }

    for (const auto& child : matcher.matcher()) {
        auto pair = matcherMap.find(child);
        if (pair == matcherMap.end()) {
            ALOGW("Matcher %lld not found in the config", (long long)child);
            result.invalidConfigReason = createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_MATCHER_CHILD_NOT_FOUND, mId);
            result.invalidConfigReason->matcherIds.push_back(child);
            return result;
        }

        int childIndex = pair->second;

        // if the child is a visited node in the recursion -> circle detected.
        if (stack[childIndex]) {
            ALOGE("Circle detected in matcher config");
            result.invalidConfigReason =
                    createInvalidConfigReasonWithMatcher(INVALID_CONFIG_REASON_MATCHER_CYCLE, mId);
            result.invalidConfigReason->matcherIds.push_back(child);
            return result;
        }
        auto [invalidConfigReason, hasStringTransformation] =
                allAtomMatchingTrackers[childIndex]->init(
                        childIndex, allAtomMatchers, allAtomMatchingTrackers, matcherMap, stack);
        if (hasStringTransformation) {
            ALOGE("String transformation detected in CombinationMatcher");
            result.invalidConfigReason = createInvalidConfigReasonWithMatcher(
                    INVALID_CONFIG_REASON_MATCHER_COMBINATION_WITH_STRING_REPLACE, mId);
            result.hasStringTransformation = true;
            return result;
        }

        if (invalidConfigReason.has_value()) {
            ALOGW("child matcher init failed %lld", (long long)child);
            invalidConfigReason->matcherIds.push_back(mId);
            result.invalidConfigReason = invalidConfigReason;
            return result;
        }

        mChildren.push_back(childIndex);

        const set<int>& childTagIds = allAtomMatchingTrackers[childIndex]->getAtomIds();
        mAtomIds.insert(childTagIds.begin(), childTagIds.end());
    }

    mInitialized = true;
    // unmark this node in the recursion stack.
    stack[matcherIndex] = false;
    return result;
}

optional<InvalidConfigReason> CombinationAtomMatchingTracker::onConfigUpdated(
        const AtomMatcher& matcher, const unordered_map<int64_t, int>& atomMatchingTrackerMap) {
    mChildren.clear();
    const AtomMatcher_Combination& combinationMatcher = matcher.combination();
    for (const int64_t child : combinationMatcher.matcher()) {
        const auto& pair = atomMatchingTrackerMap.find(child);
        if (pair == atomMatchingTrackerMap.end()) {
            ALOGW("Matcher %lld not found in the config", (long long)child);
            optional<InvalidConfigReason> invalidConfigReason =
                    createInvalidConfigReasonWithMatcher(
                            INVALID_CONFIG_REASON_MATCHER_CHILD_NOT_FOUND, matcher.id());
            invalidConfigReason->matcherIds.push_back(child);
            return invalidConfigReason;
        }
        mChildren.push_back(pair->second);
    }
    return nullopt;
}

void CombinationAtomMatchingTracker::onLogEvent(
        const LogEvent& event, int matcherIndex,
        const vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
        vector<MatchingState>& matcherResults,
        vector<shared_ptr<LogEvent>>& matcherTransformations) {
    // this event has been processed.
    if (matcherResults[matcherIndex] != MatchingState::kNotComputed) {
        return;
    }

    if (mAtomIds.find(event.GetTagId()) == mAtomIds.end()) {
        matcherResults[matcherIndex] = MatchingState::kNotMatched;
        return;
    }

    // evaluate children matchers if they haven't been evaluated.
    for (const int childIndex : mChildren) {
        if (matcherResults[childIndex] == MatchingState::kNotComputed) {
            const sp<AtomMatchingTracker>& child = allAtomMatchingTrackers[childIndex];
            child->onLogEvent(event, childIndex, allAtomMatchingTrackers, matcherResults,
                              matcherTransformations);
        }
    }

    bool matched = combinationMatch(mChildren, mLogicalOperation, matcherResults);
    matcherResults[matcherIndex] = matched ? MatchingState::kMatched : MatchingState::kNotMatched;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
