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

#ifndef CPP_VHAL_CLIENT_INCLUDE_AIDLVHALCLIENT_H_
#define CPP_VHAL_CLIENT_INCLUDE_AIDLVHALCLIENT_H_

#include "IVhalClient.h"

#include <aidl/android/hardware/automotive/vehicle/BnVehicleCallback.h>
#include <aidl/android/hardware/automotive/vehicle/IVehicle.h>
#include <android-base/thread_annotations.h>
#include <android/binder_auto_utils.h>
#include <android/binder_ibinder.h>

#include <PendingRequestPool.h>
#include <VehicleUtils.h>

#include <atomic>
#include <memory>
#include <mutex>  // NOLINT
#include <unordered_map>
#include <unordered_set>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

namespace aidl_test {

class AidlVhalClientTest;

}  // namespace aidl_test

class GetSetValueClient;

class AidlVhalClient final : public IVhalClient {
public:
    constexpr static char AIDL_VHAL_SERVICE[] =
            "android.hardware.automotive.vehicle.IVehicle/default";

    static std::shared_ptr<IVhalClient> create();
    static std::shared_ptr<IVhalClient> tryCreate();
    static std::shared_ptr<IVhalClient> tryCreate(const char* descriptor);

    explicit AidlVhalClient(
            std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> hal);

    AidlVhalClient(std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> hal,
                   int64_t timeoutInMs);

    ~AidlVhalClient();

    bool isAidlVhal() override;

    std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId) override;

    std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId, int32_t areaId) override;

    void getValue(const IHalPropValue& requestValue,
                  std::shared_ptr<GetValueCallbackFunc> callback) override;

    void setValue(const IHalPropValue& value,
                  std::shared_ptr<AidlVhalClient::SetValueCallbackFunc> callback) override;

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

    int32_t getRemoteInterfaceVersion() override;

    // Converts a non-okay status to an error {@code Result}.
    template <class T>
    inline static VhalClientResult<T> statusToError(const ndk::ScopedAStatus& status,
                                                    const std::string& msg) {
        using StatusCode = aidl::android::hardware::automotive::vehicle::StatusCode;
        StatusCode statusCode = StatusCode::INTERNAL_ERROR;
        if (status.getExceptionCode() == EX_SERVICE_SPECIFIC) {
            statusCode = static_cast<StatusCode>(status.getServiceSpecificError());
        } else if (status.getExceptionCode() == EX_TRANSACTION_FAILED) {
            if (status.getStatus() != STATUS_DEAD_OBJECT) {
                // STATUS_DEAD_OBJECT is fatal and should not return TRANSACTION_ERROR.
                return ClientStatusError(ErrorCode::TRANSACTION_ERROR)
                        << msg << ", error: " << status.getDescription();
            }
        }
        return ClientStatusError(statusCode) << msg << ", error: " << status.getDescription();
    }

private:
    friend class aidl_test::AidlVhalClientTest;

    class ILinkUnlinkToDeath {
    public:
        virtual ~ILinkUnlinkToDeath() = default;
        virtual binder_status_t linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                            void* cookie) = 0;
        virtual binder_status_t unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                              void* cookie) = 0;
    };

    class DefaultLinkUnlinkImpl final : public ILinkUnlinkToDeath {
    public:
        binder_status_t linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                    void* cookie) override;
        binder_status_t unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                      void* cookie) override;
    };

    std::atomic<int64_t> mRequestId = 0;
    std::shared_ptr<GetSetValueClient> mGetSetValueClient;
    std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> mHal;
    std::unique_ptr<ILinkUnlinkToDeath> mLinkUnlinkImpl;
    ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

    std::mutex mLock;
    std::unordered_set<std::shared_ptr<OnBinderDiedCallbackFunc>> mOnBinderDiedCallbacks
            GUARDED_BY(mLock);

    static void onBinderDied(void* cookie);
    static void onBinderUnlinked(void* cookie);

    void onBinderDiedWithContext();
    void onBinderUnlinkedWithContext();

    VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> parseVehiclePropConfigs(
            const aidl::android::hardware::automotive::vehicle::VehiclePropConfigs& configs);

    // Test-only functions:
    AidlVhalClient(std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> hal,
                   int64_t timeoutInMs, std::unique_ptr<ILinkUnlinkToDeath> linkUnlinkImpl);
    size_t countOnBinderDiedCallbacks();
};

class GetSetValueClient final :
      public aidl::android::hardware::automotive::vehicle::BnVehicleCallback {
public:
    struct PendingGetValueRequest {
        std::shared_ptr<AidlVhalClient::GetValueCallbackFunc> callback;
        int32_t propId;
        int32_t areaId;
    };

    struct PendingSetValueRequest {
        std::shared_ptr<AidlVhalClient::SetValueCallbackFunc> callback;
        int32_t propId;
        int32_t areaId;
    };

    GetSetValueClient(int64_t timeoutInNs,
                      std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> mHal);

    ~GetSetValueClient();

    ndk::ScopedAStatus onGetValues(
            const aidl::android::hardware::automotive::vehicle::GetValueResults& results) override;
    ndk::ScopedAStatus onSetValues(
            const aidl::android::hardware::automotive::vehicle::SetValueResults& results) override;
    ndk::ScopedAStatus onPropertyEvent(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValues& values,
            int32_t sharedMemoryCount) override;
    ndk::ScopedAStatus onPropertySetError(
            const aidl::android::hardware::automotive::vehicle::VehiclePropErrors& errors) override;

    void getValue(int64_t requestId, const IHalPropValue& requestValue,
                  std::shared_ptr<AidlVhalClient::GetValueCallbackFunc> clientCallback,
                  std::shared_ptr<GetSetValueClient> vhalCallback);
    void setValue(int64_t requestId, const IHalPropValue& requestValue,
                  std::shared_ptr<AidlVhalClient::SetValueCallbackFunc> clientCallback,
                  std::shared_ptr<GetSetValueClient> vhalCallback);

private:
    std::mutex mLock;
    std::unordered_map<int64_t, std::unique_ptr<PendingGetValueRequest>> mPendingGetValueCallbacks
            GUARDED_BY(mLock);
    std::unordered_map<int64_t, std::unique_ptr<PendingSetValueRequest>> mPendingSetValueCallbacks
            GUARDED_BY(mLock);
    std::unique_ptr<hardware::automotive::vehicle::PendingRequestPool> mPendingRequestPool;
    std::shared_ptr<android::hardware::automotive::vehicle::PendingRequestPool::TimeoutCallbackFunc>
            mOnGetValueTimeout;
    std::shared_ptr<android::hardware::automotive::vehicle::PendingRequestPool::TimeoutCallbackFunc>
            mOnSetValueTimeout;
    std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> mHal;

    // Add a new GetValue pending request.
    void addGetValueRequest(int64_t requestId, const IHalPropValue& requestValue,
                            std::shared_ptr<AidlVhalClient::GetValueCallbackFunc> callback);
    // Add a new SetValue pending request.
    void addSetValueRequest(int64_t requestId, const IHalPropValue& requestValue,
                            std::shared_ptr<AidlVhalClient::SetValueCallbackFunc> callback);
    // Try to finish the pending GetValue request according to the requestId. If there is an
    // existing pending request, the request would be finished and returned. Otherwise, if the
    // request has already timed-out, nullptr would be returned.
    std::unique_ptr<PendingGetValueRequest> tryFinishGetValueRequest(int64_t requestId);
    // Try to finish the pending SetValue request according to the requestId. If there is an
    // existing pending request, the request would be finished and returned. Otherwise, if the
    // request has already timed-out, nullptr would be returned.
    std::unique_ptr<PendingSetValueRequest> tryFinishSetValueRequest(int64_t requestId);

    template <class T>
    std::unique_ptr<T> tryFinishRequest(int64_t requestId,
                                        std::unordered_map<int64_t, std::unique_ptr<T>>* callbacks)
            REQUIRES(mLock);

    void onGetValue(const aidl::android::hardware::automotive::vehicle::GetValueResult& result);
    void onSetValue(const aidl::android::hardware::automotive::vehicle::SetValueResult& result);

    template <class T>
    void onTimeout(const std::unordered_set<int64_t>& requestIds,
                   std::unordered_map<int64_t, std::unique_ptr<T>>* callbacks);
};

class SubscriptionVehicleCallback final :
      public aidl::android::hardware::automotive::vehicle::BnVehicleCallback {
public:
    explicit SubscriptionVehicleCallback(std::shared_ptr<ISubscriptionCallback> callback);

    ndk::ScopedAStatus onGetValues(
            const aidl::android::hardware::automotive::vehicle::GetValueResults& results) override;
    ndk::ScopedAStatus onSetValues(
            const aidl::android::hardware::automotive::vehicle::SetValueResults& results) override;
    ndk::ScopedAStatus onPropertyEvent(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValues& values,
            int32_t sharedMemoryCount) override;
    ndk::ScopedAStatus onPropertySetError(
            const aidl::android::hardware::automotive::vehicle::VehiclePropErrors& errors) override;

private:
    std::shared_ptr<ISubscriptionCallback> mCallback;
};

class AidlSubscriptionClient final : public ISubscriptionClient {
public:
    ~AidlSubscriptionClient() = default;

    AidlSubscriptionClient(
            std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> hal,
            std::shared_ptr<ISubscriptionCallback> callback);

    VhalClientResult<void> subscribe(
            const std::vector<aidl::android::hardware::automotive::vehicle::SubscribeOptions>&
                    options) override;
    VhalClientResult<void> unsubscribe(const std::vector<int32_t>& propIds) override;

private:
    std::shared_ptr<SubscriptionVehicleCallback> mSubscriptionCallback;
    std::shared_ptr<aidl::android::hardware::automotive::vehicle::IVehicle> mHal;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_AIDLVHALCLIENT_H_
