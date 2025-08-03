/*
 * Copyright (C) 2023, The Android Open Source Project
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

#include <aidl/android/os/BnStatsSubscriptionCallback.h>
#include <aidl/android/os/IStatsd.h>
#include <aidl/android/os/StatsSubscriptionCallbackReason.h>
#include <android/binder_auto_utils.h>
#include <stats_provider.h>
#include <stats_subscription.h>

#include <atomic>
#include <map>
#include <vector>

using Status = ::ndk::ScopedAStatus;
using aidl::android::os::BnStatsSubscriptionCallback;
using aidl::android::os::IStatsd;
using aidl::android::os::StatsSubscriptionCallbackReason;
using ::ndk::SharedRefBase;

class Subscription;

// Mutex for accessing subscriptions map.
static std::mutex subscriptionsMutex;

// TODO(b/271039569): Store subscriptions in a singleton object.
// Added subscriptions keyed by their subscription ID.
static std::map</* subscription ID */ int32_t, std::shared_ptr<Subscription>> subscriptions;

class Subscription : public BnStatsSubscriptionCallback {
public:
    Subscription(const int32_t subscriptionId, const std::vector<uint8_t>& subscriptionConfig,
                 const AStatsManager_SubscriptionCallback callback, void* cookie)
        : mSubscriptionId(subscriptionId),
          mSubscriptionParamsBytes(subscriptionConfig),
          mCallback(callback),
          mCookie(cookie) {
    }

    Status onSubscriptionData(const StatsSubscriptionCallbackReason reason,
                              const std::vector<uint8_t>& subscriptionPayload) override {
        std::vector<uint8_t> mutablePayload = subscriptionPayload;
        mCallback(mSubscriptionId, static_cast<AStatsManager_SubscriptionCallbackReason>(reason),
                  mutablePayload.data(), mutablePayload.size(), mCookie);

        std::shared_ptr<Subscription> thisSubscription;
        if (reason == StatsSubscriptionCallbackReason::SUBSCRIPTION_ENDED) {
            std::lock_guard<std::mutex> lock(subscriptionsMutex);

            auto subscriptionsIt = subscriptions.find(mSubscriptionId);
            if (subscriptionsIt != subscriptions.end()) {
                // Ensure this subscription's refcount doesn't hit 0 when we erase it from the
                // subscriptions map by adding a local reference here.
                thisSubscription = subscriptionsIt->second;

                subscriptions.erase(subscriptionsIt);
            }
        }

        return Status::ok();
    }

    const std::vector<uint8_t>& getSubscriptionParamsBytes() const {
        return mSubscriptionParamsBytes;
    }

private:
    const int32_t mSubscriptionId;
    const std::vector<uint8_t> mSubscriptionParamsBytes;
    const AStatsManager_SubscriptionCallback mCallback;
    void* mCookie;
};

// forward declare so it can be referenced in StatsProvider constructor.
static void onStatsBinderRestart();

static std::shared_ptr<StatsProvider> statsProvider =
        std::make_shared<StatsProvider>(onStatsBinderRestart);

static void onStatsBinderRestart() {
    const std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();
    if (statsService == nullptr) {
        return;
    }

    // Since we do not want to make an IPC with the lock held, we first create a
    // copy of the data with the lock held before iterating through the map.
    std::map<int32_t, std::shared_ptr<Subscription>> subscriptionsCopy;
    {
        std::lock_guard<std::mutex> lock(subscriptionsMutex);
        subscriptionsCopy = subscriptions;
    }
    for (const auto& [_, subscription] : subscriptionsCopy) {
        statsService->addSubscription(subscription->getSubscriptionParamsBytes(), subscription);
    }
}

static int32_t getNextSubscriptionId() {
    static std::atomic_int32_t nextSubscriptionId(0);
    return ++nextSubscriptionId;
}

static std::shared_ptr<Subscription> getBinderCallbackForSubscription(
        const int32_t subscription_id) {
    std::lock_guard<std::mutex> lock(subscriptionsMutex);
    auto subscriptionsIt = subscriptions.find(subscription_id);
    if (subscriptionsIt == subscriptions.end()) {
        return nullptr;
    }
    return subscriptionsIt->second;
}

int32_t AStatsManager_addSubscription(const uint8_t* subscription_config, const size_t num_bytes,
                                      const AStatsManager_SubscriptionCallback callback,
                                      void* cookie) {
    const std::vector<uint8_t> subscriptionConfig(subscription_config,
                                                  subscription_config + num_bytes);
    const int32_t subscriptionId(getNextSubscriptionId());
    std::shared_ptr<Subscription> subscription =
            SharedRefBase::make<Subscription>(subscriptionId, subscriptionConfig, callback, cookie);

    {
        std::lock_guard<std::mutex> lock(subscriptionsMutex);

        subscriptions[subscriptionId] = subscription;
    }

    // TODO(b/270648168): Queue the binder call to not block on binder
    const std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();
    if (statsService != nullptr) {
        statsService->addSubscription(subscriptionConfig, subscription);
    }

    return subscriptionId;
}

void AStatsManager_removeSubscription(const int32_t subscription_id) {
    std::shared_ptr<Subscription> subscription = getBinderCallbackForSubscription(subscription_id);
    if (subscription == nullptr) {
        return;
    }

    // TODO(b/270648168): Queue the binder call to not block on binder
    const std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();
    if (statsService == nullptr) {
        // Statsd not available.
        // TODO(b/270656443): keep track of removeSubscription request and make the IPC call when
        // statsd binder comes back up.
        return;
    }
    statsService->removeSubscription(subscription);
}

void AStatsManager_flushSubscription(const int32_t subscription_id) {
    std::shared_ptr<Subscription> subscription = getBinderCallbackForSubscription(subscription_id);
    if (subscription == nullptr) {
        return;
    }

    // TODO(b/270648168): Queue the binder call to not block on binder
    const std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();
    if (statsService == nullptr) {
        // Statsd not available.
        // TODO(b/270656443): keep track of flushSubscription request and make the IPC call when
        // statsd binder comes back up.
        return;
    }

    // TODO(b/273649282): Ensure the subscription is cleared in case the final Binder data
    // callback fails.
    statsService->flushSubscription(subscription);
}
