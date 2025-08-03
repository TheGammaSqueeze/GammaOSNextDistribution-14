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

#include <string>

namespace android {
namespace os {
namespace statsd {

using std::hash;
using std::string;

/**
 * A config key that uses a package name instead of a uid. Generally, ConfigKey which uses a uid
 * should be used. This is currently only used for restricted metrics changed operation.
 */
class ConfigKeyWithPackage {
public:
    ConfigKeyWithPackage(const string& package, int64_t id) : mPackage(package), mId(id) {
    }

    inline string GetPackage() const {
        return mPackage;
    }
    inline int64_t GetId() const {
        return mId;
    }

    inline bool operator<(const ConfigKeyWithPackage& that) const {
        if (mPackage != that.mPackage) {
            return mPackage < that.mPackage;
        }
        return mId < that.mId;
    };

    inline bool operator==(const ConfigKeyWithPackage& that) const {
        return mPackage == that.mPackage && mId == that.mId;
    };

private:
    string mPackage;
    int64_t mId;
};
}  // namespace statsd
}  // namespace os
}  // namespace android
