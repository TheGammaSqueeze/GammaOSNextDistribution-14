/**
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

#pragma once

#include <condition_variable>
#include <map>
#include <queue>
#include <string>
#include <utility>

#include <aidl/android/net/resolv/aidl/BnDnsResolverUnsolicitedEventListener.h>
#include <android-base/result.h>
#include <android-base/thread_annotations.h>

namespace android::net::resolv::aidl {

class UnsolicitedEventListener
    : public ::aidl::android::net::resolv::aidl::BnDnsResolverUnsolicitedEventListener {
  public:
    UnsolicitedEventListener() = delete;
    UnsolicitedEventListener(int32_t netId) : mNetId(netId){};
    ~UnsolicitedEventListener() = default;

    ::ndk::ScopedAStatus onDnsHealthEvent(
            const ::aidl::android::net::resolv::aidl::DnsHealthEventParcel&) override;
    ::ndk::ScopedAStatus onNat64PrefixEvent(
            const ::aidl::android::net::resolv::aidl::Nat64PrefixEventParcel&) override;
    ::ndk::ScopedAStatus onPrivateDnsValidationEvent(
            const ::aidl::android::net::resolv::aidl::PrivateDnsValidationEventParcel&) override;

    // Wait for the expected private DNS validation result until timeout.
    bool waitForPrivateDnsValidation(const std::string& serverAddr, int validation, int protocol);

    // Wait for expected NAT64 prefix operation until timeout.
    bool waitForNat64Prefix(int operation, const std::chrono::milliseconds& timeout)
            EXCLUDES(mMutex);

    // Pop up last receiving dns health result.
    android::base::Result<int> popDnsHealthResult() EXCLUDES(mMutex);

    // Return true if a validation result for |serverAddr| is found; otherwise, return false.
    bool findValidationRecord(const std::string& serverAddr, int protocol) const EXCLUDES(mMutex) {
        std::lock_guard lock(mMutex);
        return mValidationRecords.find({mNetId, serverAddr, protocol}) != mValidationRecords.end();
    }

    // Returns the number of updates to the NAT64 prefix that have not yet been waited for.
    int getUnexpectedNat64PrefixUpdates() const EXCLUDES(mMutex) {
        std::lock_guard lock(mMutex);
        return mUnexpectedNat64PrefixUpdates;
    }

    void reset() EXCLUDES(mMutex) {
        std::lock_guard lock(mMutex);
        mValidationRecords.clear();
        mUnexpectedNat64PrefixUpdates = 0;

        std::queue<int> emptyQueue;
        std::swap(mDnsHealthResultRecords, emptyQueue);
    }

  private:
    //  Stores (netId, ipAddress, protocol) from onPrivateDnsValidationEvent.
    using ServerKey = std::tuple<int, std::string, int>;

    // Search mValidationRecords. Return true if |key| exists and its value is equal to
    // |value|, and then remove it; otherwise, return false.
    bool findAndRemoveValidationRecord(const ServerKey& key, int value) REQUIRES(mMutex);

    // Monitor the event which was fired on specific network id.
    const int32_t mNetId;

    // Used to store the data from onPrivateDnsValidationEvent.
    std::map<ServerKey, int> mValidationRecords GUARDED_BY(mMutex);

    // The NAT64 prefix address of the network |mNetId|. It is updated by onNat64PrefixEvent().
    std::string mNat64PrefixAddress GUARDED_BY(mMutex);

    // The number of updates to the NAT64 prefix of network |mNetId| that have not yet been waited
    // for. Increases by 1 every time onNat64PrefixEvent is called, and decreases by 1 every time
    // waitForNat64Prefix returns true.
    // This allows tests to check that no unexpected events have been received without having to
    // resort to timeouts that make the tests slower and flakier.
    int mUnexpectedNat64PrefixUpdates GUARDED_BY(mMutex);

    // Used to store the dns health result from onDnsHealthEvent().
    std::queue<int> mDnsHealthResultRecords GUARDED_BY(mMutex);

    mutable std::mutex mMutex;
    std::condition_variable mCv;
};

}  // namespace android::net::resolv::aidl
