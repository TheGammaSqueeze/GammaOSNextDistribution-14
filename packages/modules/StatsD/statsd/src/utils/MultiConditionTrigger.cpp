/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "MultiConditionTrigger.h"

namespace android {
namespace os {
namespace statsd {

using std::function;
using std::set;
using std::string;

MultiConditionTrigger::MultiConditionTrigger(const set<string>& conditionNames,
                                             function<void()> trigger)
    : mRemainingConditionNames(conditionNames),
      mTrigger(std::move(trigger)),
      mCompleted(mRemainingConditionNames.empty()) {
    if (mCompleted) {
        startExecutorThread();
    }
}

void MultiConditionTrigger::markComplete(const string& conditionName) {
    bool doTrigger = false;
    {
        std::lock_guard<std::mutex> lg(mMutex);
        if (mCompleted) {
            return;
        }
        mRemainingConditionNames.erase(conditionName);
        mCompleted = mRemainingConditionNames.empty();
        doTrigger = mCompleted;
    }
    if (doTrigger) {
        startExecutorThread();
    }
}

void MultiConditionTrigger::startExecutorThread() {
    mExecutorThread = std::make_unique<std::thread>([this] { mTrigger(); });
}

MultiConditionTrigger::~MultiConditionTrigger() {
    if (mExecutorThread != nullptr && mExecutorThread->joinable()) {
        VLOG("MultiConditionTrigger waiting on execution thread termination");
        mExecutorThread->join();
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
