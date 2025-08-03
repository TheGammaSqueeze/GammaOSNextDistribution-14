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

#pragma once

#include "src/statsd_config.pb.h"

#include <string>

namespace android {
namespace os {
namespace statsd {

using std::hash;
using std::string;

/**
 * Uniquely identifies a configuration.
 */
class ConfigKey {
public:
    ConfigKey();
    ConfigKey(const ConfigKey& that);
    ConfigKey(int uid, int64_t id);
    ~ConfigKey();

    inline int GetUid() const {
        return mUid;
    }
    inline int64_t GetId() const {
        return mId;
    }

    inline bool operator<(const ConfigKey& that) const {
        if (mUid < that.mUid) {
            return true;
        }
        if (mUid > that.mUid) {
            return false;
        }
        return mId < that.mId;
    };

    inline bool operator==(const ConfigKey& that) const {
        return mUid == that.mUid && mId == that.mId;
    };

    string ToString() const;

private:
    int64_t mId;
    int mUid;
};

int64_t StrToInt64(const string& str);

}  // namespace statsd
}  // namespace os
}  // namespace android

/**
 * A hash function for ConfigKey so it can be used for unordered_map/set.
 */
template <>
struct std::hash<android::os::statsd::ConfigKey> {
    std::size_t operator()(const android::os::statsd::ConfigKey& key) const {
        return (7 * key.GetUid()) ^ ((hash<long long>()(key.GetId())));
    }
};
