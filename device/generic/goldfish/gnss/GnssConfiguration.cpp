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

#include <aidl/android/hardware/gnss/IGnss.h>
#include <debug.h>
#include "GnssConfiguration.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

ndk::ScopedAStatus GnssConfiguration::setSuplVersion(int /*version*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssConfiguration::setSuplMode(int /*mode*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssConfiguration::setLppProfile(int /*lppProfile*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssConfiguration::setGlonassPositioningProtocol(int /*protocol*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssConfiguration::setEmergencySuplPdn(bool /*enable*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssConfiguration::setEsExtensionSec(int /*emergencyExtensionSeconds*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus GnssConfiguration::setBlocklist(const std::vector<BlocklistedSource>& blocklist) {
    BlocklistedSources blockset;

    for (const BlocklistedSource& src : blocklist) {
        if (!blockset.insert(src).second) {
            return ndk::ScopedAStatus::fromExceptionCode(FAILURE(IGnss::ERROR_INVALID_ARGUMENT));
        }
    }

    std::lock_guard<std::mutex> lock(mMtx);
    mBlocklistedSources = std::move(blockset);
    return ndk::ScopedAStatus::ok();
}

bool GnssConfiguration::isBlocklisted(const GnssConstellationType constellation,
                                      const int svid) const {
    std::lock_guard<std::mutex> lock(mMtx);

    BlocklistedSource src;
    src.constellation = constellation;
    src.svid = svid;

    if (mBlocklistedSources.count(src) != 0) {
        return true;
    }

    src.svid = 0;
    return mBlocklistedSources.count(src) != 0;
}

size_t GnssConfiguration::BlocklistedSourceHasher::operator()(
        const BlocklistedSource& x) const noexcept {
    return size_t(x.constellation) * 999983 + size_t(x.svid) * 999979;
}

bool GnssConfiguration::BlocklistedSourceEqual::operator()(
        const BlocklistedSource& lhs, const BlocklistedSource& rhs) const noexcept {
    return (lhs.constellation == rhs.constellation) && (lhs.svid == rhs.svid);
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
