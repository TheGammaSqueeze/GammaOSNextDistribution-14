/*
 * Copyright (C) 2026 GammaOS
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

#include <atomic>
#include <chrono>
#include <memory>
#include <string>
#include <thread>
#include <sys/stat.h>

namespace nano_launch {

inline bool volumeReady(std::string path,
                        int (*statPath)(const char*, struct stat*) = ::stat) {
    if (path.rfind("/mnt/media_rw/", 0) == 0)
        path = "/storage/" + path.substr(14);
    if (path.rfind("/storage/", 0) != 0) return true;
    const size_t end = path.find('/', 9);
    if (end == std::string::npos) return true;
    const std::string volume = path.substr(9, end - 9);
    if (volume.empty() || volume == "emulated" || volume == "self") return true;

    // vold can create an empty directory on /storage's tmpfs before mounting.
    struct stat root, storage;
    return statPath(path.substr(0, end).c_str(), &root) == 0 && S_ISDIR(root.st_mode)
            && statPath("/storage", &storage) == 0 && root.st_dev != storage.st_dev;
}

// UI-owned queue. Workers own only their request, never the launcher.
class Readiness {
    enum Status { Waiting, Ready, Dispatching, Cancelled };
    std::shared_ptr<std::atomic<Status>> mResult;
    std::string mPath;
public:
    Readiness() = default;
    Readiness(const Readiness&) = delete;
    Readiness& operator=(const Readiness&) = delete;
    ~Readiness() { cancel(); }

    template<class Probe>
    void start(const std::string& path, Probe probe, void (*initWorker)() = nullptr) {
        if (mResult && mPath == path && mResult->load() != Dispatching) return;
        cancel();
        mPath = path;
        auto result = mResult = std::make_shared<std::atomic<Status>>(Waiting);
        std::thread([result, path, probe, initWorker] {
            if (initWorker) initWorker();
            while (result->load() == Waiting) {
                if (probe(path)) {
                    Status expected = Waiting;
                    result->compare_exchange_strong(expected, Ready);
                    return;
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(250));
            }
        }).detach();
    }

    // Per-frame check: one atomic handoff, no I/O or allocation.
    bool takeReady() {
        Status expected = Ready;
        return mResult && mResult->compare_exchange_strong(expected, Dispatching);
    }

    bool dispatching(const std::string& path) const {
        return mResult && mPath == path && mResult->load() == Dispatching;
    }

    void cancel() {
        if (mResult) mResult->store(Cancelled);
        mResult.reset();
        mPath.clear();
    }
};

} // namespace nano_launch
