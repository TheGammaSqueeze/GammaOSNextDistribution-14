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
#pragma once

#include <android/system/suspend/internal/WakeupInfo.h>

#include <utils/Mutex.h>

#include <list>
#include <unordered_map>

using ::android::system::suspend::internal::WakeupInfo;

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

/*
 * WakeupList to collect wakeup stats.
 * This class is thread safe.
 */
class WakeupList {
   public:
    WakeupList(size_t capacity);
    void getWakeupStats(std::vector<WakeupInfo>* wakeups) const;
    void update(const std::vector<std::string>& wakeupReasons);

   private:
    void evict() REQUIRES(mLock);
    void insert(WakeupInfo entry) REQUIRES(mLock);
    void erase(std::list<WakeupInfo>::iterator entry) REQUIRES(mLock);

    size_t mCapacity;
    mutable std::mutex mLock;
    std::list<WakeupInfo> mWakeups GUARDED_BY(mLock);
    std::unordered_map<std::string, std::list<WakeupInfo>::iterator> mLookupTable GUARDED_BY(mLock);
};

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android