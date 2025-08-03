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

#ifndef ATOM_MATCHING_TRACKER_H
#define ATOM_MATCHING_TRACKER_H

#include <utils/RefBase.h>

#include <set>
#include <unordered_map>
#include <vector>

#include "guardrail/StatsdStats.h"
#include "logd/LogEvent.h"
#include "matchers/matcher_util.h"
#include "src/statsd_config.pb.h"

namespace android {
namespace os {
namespace statsd {

struct MatcherInitResult {
    optional<InvalidConfigReason> invalidConfigReason;
    bool hasStringTransformation;
};

class AtomMatchingTracker : public virtual RefBase {
public:
    AtomMatchingTracker(const int64_t id, const uint64_t protoHash)
        : mId(id), mInitialized(false), mProtoHash(protoHash){};

    virtual ~AtomMatchingTracker(){};

    // Initialize this AtomMatchingTracker.
    // matcherIndex: index of this AtomMatchingTracker in allAtomMatchingTrackers.
    // allAtomMatchers: the list of the AtomMatcher proto config. This is needed because we don't
    //                  store the proto object in memory. We only need it during initilization.
    // allAtomMatchingTrackers: the list of the AtomMatchingTracker objects. It's a one-to-one
    //                          mapping with allAtomMatchers. This is needed because the
    //                          initialization is done recursively for
    //                          CombinationAtomMatchingTrackers using DFS.
    // stack: a bit map to record which matcher has been visited on the stack. This is for detecting
    //        circle dependency.
    virtual MatcherInitResult init(
            int matcherIndex, const std::vector<AtomMatcher>& allAtomMatchers,
            const std::vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
            const std::unordered_map<int64_t, int>& matcherMap, std::vector<uint8_t>& stack) = 0;

    // Update appropriate state on config updates. Primarily, all indices need to be updated.
    // This matcher and all of its children are guaranteed to be preserved across the update.
    // matcher: the AtomMatcher proto from the config.
    // atomMatchingTrackerMap: map from matcher id to index in mAllAtomMatchingTrackers
    virtual optional<InvalidConfigReason> onConfigUpdated(
            const AtomMatcher& matcher,
            const std::unordered_map<int64_t, int>& atomMatchingTrackerMap) = 0;

    // Called when a log event comes.
    // event: the log event.
    // matcherIndex: index of this AtomMatchingTracker in allAtomMatchingTrackers.
    // allAtomMatchingTrackers: the list of all AtomMatchingTrackers. This is needed because the log
    //                          processing is done recursively.
    // matcherResults: The cached results for all matchers for this event. Parent matchers can
    //                 directly access the children's matching results if they have been evaluated.
    //                 Otherwise, call children matchers' onLogEvent.
    // matcherTransformations: the cached transformations for all matchers for this event.
    virtual void onLogEvent(const LogEvent& event, int matcherIndex,
                            const std::vector<sp<AtomMatchingTracker>>& allAtomMatchingTrackers,
                            std::vector<MatchingState>& matcherResults,
                            std::vector<std::shared_ptr<LogEvent>>& matcherTransformations) = 0;

    // Get the tagIds that this matcher cares about. The combined collection is stored
    // in MetricMananger, so that we can pass any LogEvents that are not interest of us. It uses
    // some memory but hopefully it can save us much CPU time when there is flood of events.
    virtual const std::set<int>& getAtomIds() const {
        return mAtomIds;
    }

    int64_t getId() const {
        return mId;
    }

    uint64_t getProtoHash() const {
        return mProtoHash;
    }

    bool isInitialized() {
        return mInitialized;
    }

protected:
    // Name of this matching. We don't really need the name, but it makes log message easy to debug.
    const int64_t mId;

    // Whether this AtomMatchingTracker has been properly initialized.
    bool mInitialized;

    // The collection of the event tag ids that this AtomMatchingTracker cares. So we can quickly
    // return kNotMatched when we receive an event with an id not in the list. This is especially
    // useful when we have a complex CombinationAtomMatchingTracker.
    std::set<int> mAtomIds;

    // Hash of the AtomMatcher's proto bytes from StatsdConfig.
    // Used to determine if the definition of this matcher has changed across a config update.
    const uint64_t mProtoHash;

    FRIEND_TEST(MetricsManagerUtilTest, TestCreateAtomMatchingTrackerSimple);
    FRIEND_TEST(MetricsManagerUtilTest, TestCreateAtomMatchingTrackerCombination);
    FRIEND_TEST(ConfigUpdateTest, TestUpdateMatchers);
};

}  // namespace statsd
}  // namespace os
}  // namespace android

#endif  // ATOM_MATCHING_TRACKER_H
