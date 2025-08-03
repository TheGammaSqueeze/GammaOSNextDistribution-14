/*
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_TELEMETRY_CARTELEMETRYD_TESTS_FAKELOOPERWRAPPER_H_
#define CPP_TELEMETRY_CARTELEMETRYD_TESTS_FAKELOOPERWRAPPER_H_

#include "LooperWrapper.h"

#include <algorithm>
#include <deque>
#include <map>

namespace android {
namespace automotive {
namespace telemetry {

// Fake `utils/Looper.h` implementation. Explicitly use `FakeLooperWrapper::poll()` method
// to process the messages.
//
// Not thread-safe.
class FakeLooperWrapper : public LooperWrapper {
public:
    static inline constexpr int kNoScheduledMessage = -1;

    FakeLooperWrapper() : LooperWrapper(nullptr) {}

    int pollAll(int timeoutMillis) override { return 0; }

    void sendMessageDelayed(nsecs_t uptime, const android::sp<MessageHandler>& handler,
                            const Message& message) override {
        mUptimeEntries[::systemTime() + uptime].push_back(
                {.mHandler = handler, .mMessage = message});
    }

    void removeMessages(const android::sp<MessageHandler>& handler, int what) override {
        for (auto it = mUptimeEntries.begin(); it != mUptimeEntries.end();) {
            auto [entryUptime, entries] = *it;
            for (auto eit = entries.begin(); eit != entries.end();) {
                if (eit->mMessage.what == what) {
                    eit = entries.erase(eit);
                } else {
                    ++eit;
                }
            }
            if (entries.empty()) {
                it = mUptimeEntries.erase(it);
            } else {
                ++it;
            }
        }
    }

    // Processes the next message.
    void poll() {
        auto it = mUptimeEntries.begin();
        if (it == mUptimeEntries.end() || it->second.empty()) {
            return;
        }
        auto entry = std::move(it->second.front());
        it->second.pop_front();
        if (it->second.empty()) {
            // if entries is empty, erase the uptime from the map.
            mUptimeEntries.erase(it);
        }
        entry.mHandler->handleMessage(entry.mMessage);
    }

    // Returns the next scheduled message uptime. kNoScheduledMessage if there is no message.
    nsecs_t getNextMessageUptime() {
        auto it = mUptimeEntries.begin();
        return it == mUptimeEntries.end() ? kNoScheduledMessage : it->first;
    }

private:
    struct LooperEntry {
        sp<MessageHandler> mHandler;
        Message mMessage;
    };

private:
    using Entries = std::deque<LooperEntry>;

    // <uptimeNanos, entries> - where uptimeNanos is time since boot.
    std::map<nsecs_t, Entries> mUptimeEntries;
};

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

#endif  // CPP_TELEMETRY_CARTELEMETRYD_TESTS_FAKELOOPERWRAPPER_H_
