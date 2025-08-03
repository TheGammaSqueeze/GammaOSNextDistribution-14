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

#include "WakeupList.h"

#include <android-base/logging.h>
#include <android-base/strings.h>

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

WakeupList::WakeupList(size_t capacity) : mCapacity(capacity) {}

void WakeupList::getWakeupStats(std::vector<WakeupInfo>* wakeups) const {
    std::scoped_lock lock(mLock);

    for (const WakeupInfo& w : mWakeups) {
        wakeups->push_back(w);
    }
}

void WakeupList::update(const std::vector<std::string>& wakeupReasons) {
    if (wakeupReasons.empty()) {
        LOG(ERROR) << "WakeupList: empty wakeup reasons";
        return;
    }
    std::string key = ::android::base::Join(wakeupReasons, ";");

    std::scoped_lock lock(mLock);

    auto it = mLookupTable.find(key);
    if (it == mLookupTable.end()) {
        // Create a new entry
        WakeupInfo w;
        w.name = key;
        w.count = 1;

        insert(std::move(w));
        evict();
    } else {
        // Entry found. Increment the count
        auto staleEntry = it->second;
        WakeupInfo updatedEntry = *staleEntry;
        updatedEntry.count++;

        erase(staleEntry);
        insert(std::move(updatedEntry));
    }
}

void WakeupList::evict() {
    if (mWakeups.size() > mCapacity) {
        erase(std::prev(mWakeups.end()));
        LOG(ERROR) << "WakeupList: Capacity met, consider adjusting capacity to "
                      "avoid stats eviction.";
    }
}

void WakeupList::insert(WakeupInfo entry) {
    mWakeups.push_front(entry);
    mLookupTable.insert_or_assign(entry.name, mWakeups.begin());
}

void WakeupList::erase(std::list<WakeupInfo>::iterator entry) {
    mLookupTable.erase(entry->name);
    mWakeups.erase(entry);
}

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android