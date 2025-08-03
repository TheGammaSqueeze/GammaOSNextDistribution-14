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
#include <mutex>
#include <unordered_set>
#include <aidl/android/hardware/gnss/BnGnssConfiguration.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

struct GnssConfiguration : public BnGnssConfiguration {
    ndk::ScopedAStatus setSuplVersion(int version) override;
    ndk::ScopedAStatus setSuplMode(int mode) override;
    ndk::ScopedAStatus setLppProfile(int lppProfile) override;
    ndk::ScopedAStatus setGlonassPositioningProtocol(int protocol) override;
    ndk::ScopedAStatus setEmergencySuplPdn(bool enable) override;
    ndk::ScopedAStatus setEsExtensionSec(int emergencyExtensionSeconds) override;
    ndk::ScopedAStatus setBlocklist(const std::vector<BlocklistedSource>& blocklist) override;

    bool isBlocklisted(GnssConstellationType constellation, int svid) const;

private:
    struct BlocklistedSourceHasher {
        size_t operator()(const BlocklistedSource& x) const noexcept;
    };

    struct BlocklistedSourceEqual {
        bool operator()(const BlocklistedSource& lhs,
                        const BlocklistedSource& rhs) const noexcept;
    };

    using BlocklistedSources = std::unordered_set<BlocklistedSource,
                                                  BlocklistedSourceHasher,
                                                  BlocklistedSourceEqual>;
    BlocklistedSources mBlocklistedSources;
    mutable std::mutex mMtx;
};

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
