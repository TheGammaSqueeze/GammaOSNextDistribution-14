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

#pragma once

#include <condition_variable>
#include <deque>
#include <mutex>
#include <optional>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

template <class T> struct BlockingQueue {
    BlockingQueue() = default;

    bool put(T* x)  {
        std::lock_guard lock(mtx);
        if (cancelled) {
            return false;
        } else {
            queue.push_back(std::move(*x));
            available.notify_one();
            return true;
        }
    }

    std::optional<T> get() {
        std::unique_lock lock(mtx);
        while (true) {
            if (!queue.empty()) {
                T x = std::move(queue.front());
                queue.pop_front();
                return x;
            } else if (cancelled) {
                return std::nullopt;
            } else {
                available.wait(lock);
            }
        }
    }

    std::optional<T> tryGet() {
        std::lock_guard lock(mtx);
        if (queue.empty()) {
            return std::nullopt;
        } else {
            T x = std::move(queue.front());
            queue.pop_front();
            return x;
        }
    }

    void cancel() {
        std::lock_guard lock(mtx);
        cancelled = true;
        available.notify_one();
    }

    BlockingQueue(const BlockingQueue&) = delete;
    BlockingQueue(BlockingQueue&&) = delete;
    BlockingQueue& operator=(const BlockingQueue&) = delete;
    BlockingQueue& operator=(BlockingQueue&&) = delete;

private:
    std::deque<T> queue;
    std::condition_variable available;
    bool cancelled = false;
    std::mutex mtx;
};

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
