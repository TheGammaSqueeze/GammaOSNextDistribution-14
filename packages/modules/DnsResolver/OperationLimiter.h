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

#ifndef NETUTILS_OPERATIONLIMITER_H
#define NETUTILS_OPERATIONLIMITER_H

#include <mutex>
#include <unordered_map>

#include <android-base/logging.h>
#include <android-base/thread_annotations.h>

#include "Experiments.h"

namespace android {
namespace netdutils {

// Limits the number of outstanding DNS queries by client UID.
constexpr int MAX_QUERIES_PER_UID = 256;
// Limits the total number of outstanding DNS queries.
constexpr int MAX_QUERIES_IN_TOTAL = 2500;

// Tracks the number of operations in progress on behalf of a particular key or
// ID, rejecting further attempts to start new operations after a configurable
// limit has been reached.
//
// The intended usage pattern is:
//     OperationLimiter<UserId> connections_per_user;
//     ...
//     int connectToSomeResource(int user) {
//         if (!connections_per_user.start(user)) return TRY_AGAIN_LATER;
//         // ...do expensive work here...
//         connections_per_user.finish(user);
//     }
//
// This class is thread-safe.
template <typename KeyType>
class OperationLimiter {
  public:
    OperationLimiter(int limitPerKey) : mLimitPerKey(limitPerKey) {}

    ~OperationLimiter() {
        DCHECK(mCounters.empty()) << "Destroying OperationLimiter with active operations";
    }

    // Returns false if |key| has reached the maximum number of concurrent operations,
    // or if the global limit has been reached. Otherwise, increments the counter and returns true.
    //
    // Note: each successful start(key) must be matched by exactly one call to
    // finish(key).
    bool start(KeyType key, int globalLimit = MAX_QUERIES_IN_TOTAL) EXCLUDES(mMutex) {
        std::lock_guard lock(mMutex);
        if (globalLimit < mLimitPerKey) {
            LOG(ERROR) << "Misconfiguration on max_queries_global " << globalLimit;
            globalLimit = MAX_QUERIES_IN_TOTAL;
        }
        if (mGlobalCounter >= globalLimit) {
            // Oh, no!
            LOG(ERROR) << "Query from " << key << " denied due to global limit: " << globalLimit;
            return false;
        }

        auto& cnt = mCounters[key];  // operator[] creates new entries as needed.
        if (cnt >= mLimitPerKey) {
            // Oh, no!
            LOG(ERROR) << "Query from " << key << " denied due to limit: " << mLimitPerKey;
            return false;
        }

        ++cnt;
        ++mGlobalCounter;
        return true;
    }

    // Decrements the number of operations in progress accounted to |key|.
    // See usage notes on start().
    void finish(KeyType key) EXCLUDES(mMutex) {
        std::lock_guard lock(mMutex);

        --mGlobalCounter;
        if (mGlobalCounter < 0) {
            LOG(FATAL_WITHOUT_ABORT) << "Global operations counter going negative, this is a bug.";
            return;
        }

        auto it = mCounters.find(key);
        if (it == mCounters.end()) {
            LOG(FATAL_WITHOUT_ABORT) << "Decremented non-existent counter for key=" << key;
            return;
        }
        auto& cnt = it->second;
        --cnt;
        if (cnt <= 0) {
            // Cleanup counters once they drop down to zero.
            mCounters.erase(it);
        }
    }

  private:
    // Protects access to the map below.
    std::mutex mMutex;

    // Tracks the number of outstanding queries by key.
    std::unordered_map<KeyType, int> mCounters GUARDED_BY(mMutex);

    int mGlobalCounter GUARDED_BY(mMutex) = 0;

    // Maximum number of outstanding queries from a single key.
    const int mLimitPerKey;
};

}  // namespace netdutils
}  // namespace android

#endif  // NETUTILS_OPERATIONLIMITER_H
