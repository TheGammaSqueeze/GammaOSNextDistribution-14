/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "LogEventQueue.h"

namespace android {
namespace os {
namespace statsd {

using std::unique_lock;
using std::unique_ptr;

unique_ptr<LogEvent> LogEventQueue::waitPop() {
    std::unique_lock<std::mutex> lock(mMutex);

    if (mQueue.empty()) {
        mCondition.wait(lock, [this] { return !this->mQueue.empty(); });
    }

    unique_ptr<LogEvent> item = std::move(mQueue.front());
    mQueue.pop();

    return item;
}

LogEventQueue::Result LogEventQueue::push(unique_ptr<LogEvent> item) {
    Result result;
    {
        std::unique_lock<std::mutex> lock(mMutex);
        if (mQueue.size() < mQueueLimit) {
            mQueue.push(std::move(item));
            result.success = true;
        } else {
            // safe operation as queue must not be empty.
            result.oldestTimestampNs = mQueue.front()->GetElapsedTimestampNs();
            result.success = false;
        }
        result.size = mQueue.size();
    }

    mCondition.notify_one();
    return result;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
