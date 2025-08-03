/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <queue>

#include "interface/Event.h"

namespace android::hardware::graphics::composer {

struct EventQueue {
public:
    EventQueue() = default;

    void postEvent(VrrControllerEventType type, TimedEvent& timedEvent) {
        VrrControllerEvent event;
        event.mEventType = type;
        setTimedEventWithAbsoluteTime(timedEvent);
        event.mWhenNs = timedEvent.mWhenNs;
        event.mFunctor = std::move(timedEvent.mFunctor);
        mPriorityQueue.emplace(event);
    }

    void postEvent(VrrControllerEventType type, int64_t when) {
        VrrControllerEvent event;
        event.mEventType = type;
        event.mWhenNs = when;
        mPriorityQueue.emplace(event);
    }

    void dropEvent() { mPriorityQueue = std::priority_queue<VrrControllerEvent>(); }

    void dropEvent(VrrControllerEventType event_type) {
        std::priority_queue<VrrControllerEvent> q;
        while (!mPriorityQueue.empty()) {
            const auto& it = mPriorityQueue.top();
            if (it.mEventType != event_type) {
                q.push(it);
            }
            mPriorityQueue.pop();
        }
        mPriorityQueue = std::move(q);
    }

    size_t getNumberOfEvents(VrrControllerEventType eventType) {
        size_t res = 0;
        std::priority_queue<VrrControllerEvent> q;
        while (!mPriorityQueue.empty()) {
            const auto& it = mPriorityQueue.top();
            if (it.mEventType == eventType) {
                ++res;
            }
            mPriorityQueue.pop();
        }
        mPriorityQueue = std::move(q);
        return res;
    }

    std::priority_queue<VrrControllerEvent> mPriorityQueue;
};

} // namespace android::hardware::graphics::composer
