/*
 * Copyright (c) 2022, The Android Open Source Project
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

#ifndef CPP_VHAL_CLIENT_INCLUDE_HIDLVHALCLIENT_H_
#define CPP_VHAL_CLIENT_INCLUDE_HIDLVHALCLIENT_H_

#include "IVhalClient.h"

#include <aidl/android/hardware/automotive/vehicle/SubscribeOptions.h>
#include <android-base/thread_annotations.h>
#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>
#include <utils/StrongPointer.h>

#include <mutex>  // NOLINT
#include <unordered_set>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

namespace hidl_test {

class HidlVhalClientTest;

}  // namespace hidl_test

class HidlVhalClient final : public IVhalClient {
public:
    static std::shared_ptr<IVhalClient> create();
    static std::shared_ptr<IVhalClient> tryCreate();
    static std::shared_ptr<IVhalClient> tryCreate(const char* descriptor);

    explicit HidlVhalClient(
            android::sp<android::hardware::automotive::vehicle::V2_0::IVehicle> hal);

    ~HidlVhalClient();

    bool isAidlVhal() override;

    std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId) override;

    std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId, int32_t areaId) override;

    void getValue(const IHalPropValue& requestValue,
                  std::shared_ptr<GetValueCallbackFunc> callback) override;

    void setValue(const IHalPropValue& value,
                  std::shared_ptr<HidlVhalClient::SetValueCallbackFunc> callback) override;

    // Add the callback that would be called when VHAL binder died.
    VhalClientResult<void> addOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) override;

    // Remove a previously added OnBinderDied callback.
    VhalClientResult<void> removeOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) override;

    VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> getAllPropConfigs() override;

    VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> getPropConfigs(
            std::vector<int32_t> propIds) override;

    std::unique_ptr<ISubscriptionClient> getSubscriptionClient(
            std::shared_ptr<ISubscriptionCallback> callback) override;

private:
    friend class hidl_test::HidlVhalClientTest;

    class DeathRecipient : public android::hardware::hidl_death_recipient {
    public:
        explicit DeathRecipient(HidlVhalClient* client);

        void serviceDied(uint64_t cookie,
                         const android::wp<android::hidl::base::V1_0::IBase>& who) override;

    private:
        HidlVhalClient* mClient;
    };

    android::sp<::android::hardware::automotive::vehicle::V2_0::IVehicle> mHal;
    android::sp<DeathRecipient> mDeathRecipient;

    std::mutex mLock;
    std::unordered_set<std::shared_ptr<OnBinderDiedCallbackFunc>> mOnBinderDiedCallbacks
            GUARDED_BY(mLock);

    void onBinderDied();
};

class SubscriptionCallback;

class HidlSubscriptionClient final : public ISubscriptionClient {
public:
    ~HidlSubscriptionClient() = default;

    HidlSubscriptionClient(android::sp<android::hardware::automotive::vehicle::V2_0::IVehicle> hal,
                           std::shared_ptr<ISubscriptionCallback> callback);

    VhalClientResult<void> subscribe(
            const std::vector<aidl::android::hardware::automotive::vehicle::SubscribeOptions>&
                    options) override;
    VhalClientResult<void> unsubscribe(const std::vector<int32_t>& propIds) override;

private:
    std::shared_ptr<ISubscriptionCallback> mCallback;
    android::sp<android::hardware::automotive::vehicle::V2_0::IVehicle> mHal;
    android::sp<SubscriptionCallback> mVhalCallback;
};

class SubscriptionCallback : public android::hardware::automotive::vehicle::V2_0::IVehicleCallback {
public:
    explicit SubscriptionCallback(std::shared_ptr<ISubscriptionCallback> callback);

    android::hardware::Return<void> onPropertyEvent(
            const android::hardware::hidl_vec<
                    hardware::automotive::vehicle::V2_0::VehiclePropValue>& propValues) override;
    android::hardware::Return<void> onPropertySet(
            const android::hardware::automotive::vehicle::V2_0::VehiclePropValue& propValue)
            override;
    android::hardware::Return<void> onPropertySetError(
            android::hardware::automotive::vehicle::V2_0::StatusCode status, int32_t propId,
            int32_t areaId) override;

private:
    std::shared_ptr<ISubscriptionCallback> mCallback;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_HIDLVHALCLIENT_H_
