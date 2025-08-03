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

#include "AidlVhalClient.h"

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <utils/Log.h>

#include <AidlHalPropConfig.h>
#include <AidlHalPropValue.h>
#include <ParcelableUtils.h>
#include <inttypes.h>

#include <string>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

namespace {

using ::android::base::Join;
using ::android::base::StringPrintf;
using ::android::hardware::automotive::vehicle::fromStableLargeParcelable;
using ::android::hardware::automotive::vehicle::PendingRequestPool;
using ::android::hardware::automotive::vehicle::toInt;
using ::android::hardware::automotive::vehicle::vectorToStableLargeParcelable;

using ::aidl::android::hardware::automotive::vehicle::GetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::GetValueRequests;
using ::aidl::android::hardware::automotive::vehicle::GetValueResult;
using ::aidl::android::hardware::automotive::vehicle::GetValueResults;
using ::aidl::android::hardware::automotive::vehicle::IVehicle;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequests;
using ::aidl::android::hardware::automotive::vehicle::SetValueResult;
using ::aidl::android::hardware::automotive::vehicle::SetValueResults;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::toString;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfigs;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropError;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropErrors;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValues;

using ::ndk::ScopedAIBinder_DeathRecipient;
using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::ndk::SpAIBinder;

std::string toString(const std::vector<int32_t>& values) {
    std::vector<std::string> strings;
    for (int32_t value : values) {
        strings.push_back(std::to_string(value));
    }
    return "[" + Join(strings, ",") + "]";
}

}  // namespace

std::shared_ptr<IVhalClient> AidlVhalClient::create() {
    if (!AServiceManager_isDeclared(AIDL_VHAL_SERVICE)) {
        ALOGD("AIDL VHAL service is not declared, maybe HIDL VHAL is used instead?");
        return nullptr;
    }
    std::shared_ptr<IVehicle> aidlVhal =
            IVehicle::fromBinder(SpAIBinder(AServiceManager_waitForService(AIDL_VHAL_SERVICE)));
    if (aidlVhal == nullptr) {
        ALOGW("AIDL VHAL service is not available");
        return nullptr;
    }
    ABinderProcess_startThreadPool();
    return std::make_shared<AidlVhalClient>(aidlVhal);
}

std::shared_ptr<IVhalClient> AidlVhalClient::tryCreate() {
    return tryCreate(AIDL_VHAL_SERVICE);
}

std::shared_ptr<IVhalClient> AidlVhalClient::tryCreate(const char* descriptor) {
    if (!AServiceManager_isDeclared(descriptor)) {
        ALOGD("AIDL VHAL service, descriptor: %s is not declared, maybe HIDL VHAL is used instead?",
              descriptor);
        return nullptr;
    }
    std::shared_ptr<IVehicle> aidlVhal =
            IVehicle::fromBinder(SpAIBinder(AServiceManager_getService(descriptor)));
    if (aidlVhal == nullptr) {
        ALOGW("AIDL VHAL service, descriptor: %s is not available", descriptor);
        return nullptr;
    }
    ABinderProcess_startThreadPool();
    return std::make_shared<AidlVhalClient>(aidlVhal);
}

AidlVhalClient::AidlVhalClient(std::shared_ptr<IVehicle> hal) :
      AidlVhalClient(hal, DEFAULT_TIMEOUT_IN_SEC * 1'000) {}

AidlVhalClient::AidlVhalClient(std::shared_ptr<IVehicle> hal, int64_t timeoutInMs) :
      AidlVhalClient(hal, timeoutInMs, std::make_unique<DefaultLinkUnlinkImpl>()) {}

AidlVhalClient::AidlVhalClient(std::shared_ptr<IVehicle> hal, int64_t timeoutInMs,
                               std::unique_ptr<ILinkUnlinkToDeath> linkUnlinkImpl) :
      mHal(hal) {
    mGetSetValueClient = SharedRefBase::make<GetSetValueClient>(
            /*timeoutInNs=*/timeoutInMs * 1'000'000, hal);
    mDeathRecipient = ScopedAIBinder_DeathRecipient(
            AIBinder_DeathRecipient_new(&AidlVhalClient::onBinderDied));
    mLinkUnlinkImpl = std::move(linkUnlinkImpl);
    binder_status_t status =
            mLinkUnlinkImpl->linkToDeath(hal->asBinder().get(), mDeathRecipient.get(),
                                         static_cast<void*>(this));
    if (status != STATUS_OK) {
        ALOGE("failed to link to VHAL death, status: %d", static_cast<int32_t>(status));
    }
}

AidlVhalClient::~AidlVhalClient() {
    mLinkUnlinkImpl->unlinkToDeath(mHal->asBinder().get(), mDeathRecipient.get(),
                                   static_cast<void*>(this));
}

bool AidlVhalClient::isAidlVhal() {
    return true;
}

std::unique_ptr<IHalPropValue> AidlVhalClient::createHalPropValue(int32_t propId) {
    return std::make_unique<AidlHalPropValue>(propId);
}

std::unique_ptr<IHalPropValue> AidlVhalClient::createHalPropValue(int32_t propId, int32_t areaId) {
    return std::make_unique<AidlHalPropValue>(propId, areaId);
}

binder_status_t AidlVhalClient::DefaultLinkUnlinkImpl::linkToDeath(
        AIBinder* binder, AIBinder_DeathRecipient* recipient, void* cookie) {
    return AIBinder_linkToDeath(binder, recipient, cookie);
}

binder_status_t AidlVhalClient::DefaultLinkUnlinkImpl::unlinkToDeath(
        AIBinder* binder, AIBinder_DeathRecipient* recipient, void* cookie) {
    return AIBinder_unlinkToDeath(binder, recipient, cookie);
}

void AidlVhalClient::getValue(const IHalPropValue& requestValue,
                              std::shared_ptr<GetValueCallbackFunc> callback) {
    int64_t requestId = mRequestId++;
    mGetSetValueClient->getValue(requestId, requestValue, callback, mGetSetValueClient);
}

void AidlVhalClient::setValue(const IHalPropValue& requestValue,
                              std::shared_ptr<SetValueCallbackFunc> callback) {
    int64_t requestId = mRequestId++;
    mGetSetValueClient->setValue(requestId, requestValue, callback, mGetSetValueClient);
}

VhalClientResult<void> AidlVhalClient::addOnBinderDiedCallback(
        std::shared_ptr<OnBinderDiedCallbackFunc> callback) {
    std::lock_guard<std::mutex> lk(mLock);
    mOnBinderDiedCallbacks.insert(callback);
    return {};
}

VhalClientResult<void> AidlVhalClient::removeOnBinderDiedCallback(
        std::shared_ptr<OnBinderDiedCallbackFunc> callback) {
    std::lock_guard<std::mutex> lk(mLock);
    if (mOnBinderDiedCallbacks.find(callback) == mOnBinderDiedCallbacks.end()) {
        return ClientStatusError(ErrorCode::INVALID_ARG)
                << "The callback to remove was not added before";
    }
    mOnBinderDiedCallbacks.erase(callback);
    return {};
}

VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> AidlVhalClient::getAllPropConfigs() {
    VehiclePropConfigs configs;
    if (ScopedAStatus status = mHal->getAllPropConfigs(&configs); !status.isOk()) {
        return statusToError<
                std::vector<std::unique_ptr<IHalPropConfig>>>(status,
                                                              "failed to get all property configs");
    }
    return parseVehiclePropConfigs(configs);
}

VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> AidlVhalClient::getPropConfigs(
        std::vector<int32_t> propIds) {
    VehiclePropConfigs configs;
    if (ScopedAStatus status = mHal->getPropConfigs(propIds, &configs); !status.isOk()) {
        return statusToError<std::vector<std::unique_ptr<
                IHalPropConfig>>>(status,
                                  StringPrintf("failed to get prop configs for prop IDs: %s",
                                               toString(propIds).c_str()));
    }
    return parseVehiclePropConfigs(configs);
}

VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>>
AidlVhalClient::parseVehiclePropConfigs(const VehiclePropConfigs& configs) {
    auto parcelableResult = fromStableLargeParcelable(configs);
    if (!parcelableResult.ok()) {
        return ClientStatusError(ErrorCode::INTERNAL_ERROR_FROM_VHAL)
                << "failed to parse VehiclePropConfigs returned from VHAL, error: "
                << parcelableResult.error().getMessage();
    }
    std::vector<std::unique_ptr<IHalPropConfig>> out;
    for (const VehiclePropConfig& config : parcelableResult.value().getObject()->payloads) {
        VehiclePropConfig configCopy = config;
        out.push_back(std::make_unique<AidlHalPropConfig>(std::move(configCopy)));
    }
    return out;
}

void AidlVhalClient::onBinderDied(void* cookie) {
    AidlVhalClient* vhalClient = reinterpret_cast<AidlVhalClient*>(cookie);
    vhalClient->onBinderDiedWithContext();
}

void AidlVhalClient::onBinderUnlinked(void* cookie) {
    AidlVhalClient* vhalClient = reinterpret_cast<AidlVhalClient*>(cookie);
    vhalClient->onBinderUnlinkedWithContext();
}

void AidlVhalClient::onBinderDiedWithContext() {
    std::lock_guard<std::mutex> lk(mLock);
    for (auto callback : mOnBinderDiedCallbacks) {
        (*callback)();
    }
}

void AidlVhalClient::onBinderUnlinkedWithContext() {
    std::lock_guard<std::mutex> lk(mLock);
    mOnBinderDiedCallbacks.clear();
}

size_t AidlVhalClient::countOnBinderDiedCallbacks() {
    std::lock_guard<std::mutex> lk(mLock);
    return mOnBinderDiedCallbacks.size();
}

int32_t AidlVhalClient::getRemoteInterfaceVersion() {
    int32_t interfaceVersion = 0;
    if (auto status = mHal->getInterfaceVersion(&interfaceVersion); !status.isOk()) {
        ALOGE("failed to get VHAL interface version, assume 0");
    }
    return interfaceVersion;
}

std::unique_ptr<ISubscriptionClient> AidlVhalClient::getSubscriptionClient(
        std::shared_ptr<ISubscriptionCallback> callback) {
    return std::make_unique<AidlSubscriptionClient>(mHal, callback);
}

GetSetValueClient::GetSetValueClient(int64_t timeoutInNs, std::shared_ptr<IVehicle> hal) :
      mHal(hal) {
    mPendingRequestPool = std::make_unique<PendingRequestPool>(timeoutInNs);
    mOnGetValueTimeout = std::make_unique<PendingRequestPool::TimeoutCallbackFunc>(
            [this](const std::unordered_set<int64_t>& requestIds) {
                onTimeout(requestIds, &mPendingGetValueCallbacks);
            });
    mOnSetValueTimeout = std::make_unique<PendingRequestPool::TimeoutCallbackFunc>(
            [this](const std::unordered_set<int64_t>& requestIds) {
                onTimeout(requestIds, &mPendingSetValueCallbacks);
            });
}

GetSetValueClient::~GetSetValueClient() {
    // Delete the pending request pool, mark all pending request as timed-out.
    mPendingRequestPool.reset();
}

void GetSetValueClient::getValue(
        int64_t requestId, const IHalPropValue& requestValue,
        std::shared_ptr<AidlVhalClient::GetValueCallbackFunc> clientCallback,
        std::shared_ptr<GetSetValueClient> vhalCallback) {
    int32_t propId = requestValue.getPropId();
    int32_t areaId = requestValue.getAreaId();
    std::vector<GetValueRequest> requests = {
            {
                    .requestId = requestId,
                    .prop = *(reinterpret_cast<const VehiclePropValue*>(
                            requestValue.toVehiclePropValue())),
            },
    };

    GetValueRequests getValueRequests;
    ScopedAStatus status = vectorToStableLargeParcelable(std::move(requests), &getValueRequests);
    if (!status.isOk()) {
        tryFinishGetValueRequest(requestId);
        (*clientCallback)(AidlVhalClient::statusToError<
                          std::unique_ptr<IHalPropValue>>(status,
                                                          StringPrintf("failed to serialize "
                                                                       "request for prop: %" PRId32
                                                                       ", areaId: %" PRId32,
                                                                       propId, areaId)));
    }

    addGetValueRequest(requestId, requestValue, clientCallback);
    status = mHal->getValues(vhalCallback, getValueRequests);
    if (!status.isOk()) {
        tryFinishGetValueRequest(requestId);
        (*clientCallback)(
                AidlVhalClient::statusToError<std::unique_ptr<
                        IHalPropValue>>(status,
                                        StringPrintf("failed to get value for prop: %" PRId32
                                                     ", areaId: %" PRId32,
                                                     propId, areaId)));
    }
}

void GetSetValueClient::setValue(
        int64_t requestId, const IHalPropValue& requestValue,
        std::shared_ptr<AidlVhalClient::SetValueCallbackFunc> clientCallback,
        std::shared_ptr<GetSetValueClient> vhalCallback) {
    int32_t propId = requestValue.getPropId();
    int32_t areaId = requestValue.getAreaId();
    std::vector<SetValueRequest> requests = {
            {
                    .requestId = requestId,
                    .value = *(reinterpret_cast<const VehiclePropValue*>(
                            requestValue.toVehiclePropValue())),
            },
    };

    SetValueRequests setValueRequests;
    ScopedAStatus status = vectorToStableLargeParcelable(std::move(requests), &setValueRequests);
    if (!status.isOk()) {
        tryFinishSetValueRequest(requestId);
        (*clientCallback)(AidlVhalClient::statusToError<
                          void>(status,
                                StringPrintf("failed to serialize request for prop: %" PRId32
                                             ", areaId: %" PRId32,
                                             propId, areaId)));
    }

    addSetValueRequest(requestId, requestValue, clientCallback);
    status = mHal->setValues(vhalCallback, setValueRequests);
    if (!status.isOk()) {
        tryFinishSetValueRequest(requestId);
        (*clientCallback)(AidlVhalClient::statusToError<
                          void>(status,
                                StringPrintf("failed to set value for prop: %" PRId32
                                             ", areaId: %" PRId32,
                                             propId, areaId)));
    }
}

void GetSetValueClient::addGetValueRequest(
        int64_t requestId, const IHalPropValue& requestProp,
        std::shared_ptr<AidlVhalClient::GetValueCallbackFunc> callback) {
    std::lock_guard<std::mutex> lk(mLock);
    mPendingGetValueCallbacks[requestId] =
            std::make_unique<PendingGetValueRequest>(PendingGetValueRequest{
                    .callback = callback,
                    .propId = requestProp.getPropId(),
                    .areaId = requestProp.getAreaId(),
            });
    mPendingRequestPool->addRequests(/*clientId=*/nullptr, {requestId}, mOnGetValueTimeout);
}

void GetSetValueClient::addSetValueRequest(
        int64_t requestId, const IHalPropValue& requestProp,
        std::shared_ptr<AidlVhalClient::SetValueCallbackFunc> callback) {
    std::lock_guard<std::mutex> lk(mLock);
    mPendingSetValueCallbacks[requestId] =
            std::make_unique<PendingSetValueRequest>(PendingSetValueRequest{
                    .callback = callback,
                    .propId = requestProp.getPropId(),
                    .areaId = requestProp.getAreaId(),
            });
    mPendingRequestPool->addRequests(/*clientId=*/nullptr, {requestId}, mOnSetValueTimeout);
}

std::unique_ptr<GetSetValueClient::PendingGetValueRequest>
GetSetValueClient::tryFinishGetValueRequest(int64_t requestId) {
    std::lock_guard<std::mutex> lk(mLock);
    return tryFinishRequest(requestId, &mPendingGetValueCallbacks);
}

std::unique_ptr<GetSetValueClient::PendingSetValueRequest>
GetSetValueClient::tryFinishSetValueRequest(int64_t requestId) {
    std::lock_guard<std::mutex> lk(mLock);
    return tryFinishRequest(requestId, &mPendingSetValueCallbacks);
}

template <class T>
std::unique_ptr<T> GetSetValueClient::tryFinishRequest(
        int64_t requestId, std::unordered_map<int64_t, std::unique_ptr<T>>* callbacks) {
    auto finished = mPendingRequestPool->tryFinishRequests(/*clientId=*/nullptr, {requestId});
    if (finished.empty()) {
        return nullptr;
    }
    auto it = callbacks->find(requestId);
    if (it == callbacks->end()) {
        return nullptr;
    }
    auto request = std::move(it->second);
    callbacks->erase(requestId);
    return std::move(request);
}

template std::unique_ptr<GetSetValueClient::PendingGetValueRequest>
GetSetValueClient::tryFinishRequest(
        int64_t requestId,
        std::unordered_map<int64_t, std::unique_ptr<PendingGetValueRequest>>* callbacks);
template std::unique_ptr<GetSetValueClient::PendingSetValueRequest>
GetSetValueClient::tryFinishRequest(
        int64_t requestId,
        std::unordered_map<int64_t, std::unique_ptr<PendingSetValueRequest>>* callbacks);

ScopedAStatus GetSetValueClient::onGetValues(const GetValueResults& results) {
    auto parcelableResult = fromStableLargeParcelable(results);
    if (!parcelableResult.ok()) {
        ALOGE("failed to parse GetValueResults returned from VHAL, error: %s",
              parcelableResult.error().getMessage());
        return std::move(parcelableResult.error());
    }
    for (const GetValueResult& result : parcelableResult.value().getObject()->payloads) {
        onGetValue(result);
    }
    return ScopedAStatus::ok();
}

void GetSetValueClient::onGetValue(const GetValueResult& result) {
    int64_t requestId = result.requestId;

    auto pendingRequest = tryFinishGetValueRequest(requestId);
    if (pendingRequest == nullptr) {
        ALOGD("failed to find pending request for ID: %" PRId64 ", maybe already timed-out",
              requestId);
        return;
    }

    std::shared_ptr<AidlVhalClient::GetValueCallbackFunc> callback = pendingRequest->callback;
    int32_t propId = pendingRequest->propId;
    int32_t areaId = pendingRequest->areaId;
    if (result.status != StatusCode::OK) {
        StatusCode status = result.status;
        (*callback)(ClientStatusError(status)
                    << "failed to get value for propId: " << propId << ", areaId: " << areaId
                    << ": status: " << toString(status));
    } else if (!result.prop.has_value()) {
        (*callback)(ClientStatusError(ErrorCode::INTERNAL_ERROR_FROM_VHAL)
                    << "failed to get value for propId: " << propId << ", areaId: " << areaId
                    << ": returns no value");
    } else {
        VehiclePropValue valueCopy = result.prop.value();
        std::unique_ptr<IHalPropValue> propValue =
                std::make_unique<AidlHalPropValue>(std::move(valueCopy));
        (*callback)(std::move(propValue));
    }
}

ScopedAStatus GetSetValueClient::onSetValues(const SetValueResults& results) {
    auto parcelableResult = fromStableLargeParcelable(results);
    if (!parcelableResult.ok()) {
        ALOGE("failed to parse SetValueResults returned from VHAL, error: %s",
              parcelableResult.error().getMessage());
        return std::move(parcelableResult.error());
    }
    for (const SetValueResult& result : parcelableResult.value().getObject()->payloads) {
        onSetValue(result);
    }
    return ScopedAStatus::ok();
}

void GetSetValueClient::onSetValue(const SetValueResult& result) {
    int64_t requestId = result.requestId;

    auto pendingRequest = tryFinishSetValueRequest(requestId);
    if (pendingRequest == nullptr) {
        ALOGD("failed to find pending request for ID: %" PRId64 ", maybe already timed-out",
              requestId);
        return;
    }

    std::shared_ptr<AidlVhalClient::SetValueCallbackFunc> callback = pendingRequest->callback;
    int32_t propId = pendingRequest->propId;
    int32_t areaId = pendingRequest->areaId;
    if (result.status != StatusCode::OK) {
        (*callback)(ClientStatusError(result.status)
                    << "failed to set value for propId: " << propId << ", areaId: " << areaId
                    << ": status: " << toString(result.status));
    } else {
        (*callback)({});
    }
}

ScopedAStatus GetSetValueClient::onPropertyEvent([[maybe_unused]] const VehiclePropValues&,
                                                 int32_t) {
    return ScopedAStatus::
            fromServiceSpecificErrorWithMessage(toInt(ErrorCode::INTERNAL_ERROR_FROM_VHAL),
                                                "onPropertyEvent should never be "
                                                "called from GetSetValueClient");
}

ScopedAStatus GetSetValueClient::onPropertySetError([[maybe_unused]] const VehiclePropErrors&) {
    return ScopedAStatus::
            fromServiceSpecificErrorWithMessage(toInt(ErrorCode::INTERNAL_ERROR_FROM_VHAL),
                                                "onPropertySetError should never be "
                                                "called from GetSetValueClient");
}

template <class T>
void GetSetValueClient::onTimeout(const std::unordered_set<int64_t>& requestIds,
                                  std::unordered_map<int64_t, std::unique_ptr<T>>* callbacks) {
    for (int64_t requestId : requestIds) {
        std::unique_ptr<T> pendingRequest;
        {
            std::lock_guard<std::mutex> lk(mLock);
            auto it = callbacks->find(requestId);
            if (it == callbacks->end()) {
                ALOGW("failed to find the timed-out pending request for ID: %" PRId64 ", ignore",
                      requestId);
                continue;
            }
            pendingRequest = std::move(it->second);
            callbacks->erase(requestId);
        }

        (*pendingRequest->callback)(
                ClientStatusError(ErrorCode::TIMEOUT)
                << "failed to get/set value for propId: " << pendingRequest->propId
                << ", areaId: " << pendingRequest->areaId << ": request timed out");
    }
}

template void GetSetValueClient::onTimeout(
        const std::unordered_set<int64_t>& requestIds,
        std::unordered_map<int64_t, std::unique_ptr<PendingGetValueRequest>>* callbacks);
template void GetSetValueClient::onTimeout(
        const std::unordered_set<int64_t>& requestIds,
        std::unordered_map<int64_t, std::unique_ptr<PendingSetValueRequest>>* callbacks);

AidlSubscriptionClient::AidlSubscriptionClient(std::shared_ptr<IVehicle> hal,
                                               std::shared_ptr<ISubscriptionCallback> callback) :
      mHal(hal) {
    mSubscriptionCallback = SharedRefBase::make<SubscriptionVehicleCallback>(callback);
}

VhalClientResult<void> AidlSubscriptionClient::subscribe(
        const std::vector<SubscribeOptions>& options) {
    std::vector<int32_t> propIds;
    for (const SubscribeOptions& option : options) {
        propIds.push_back(option.propId);
    }

    // TODO(b/205189110): Fill in maxSharedMemoryFileCount after we support memory pool.
    if (auto status = mHal->subscribe(mSubscriptionCallback, options,
                                      /*maxSharedMemoryFileCount=*/0);
        !status.isOk()) {
        return AidlVhalClient::statusToError<
                void>(status,
                      StringPrintf("failed to subscribe to prop IDs: %s",
                                   toString(propIds).c_str()));
    }
    return {};
}

VhalClientResult<void> AidlSubscriptionClient::unsubscribe(const std::vector<int32_t>& propIds) {
    if (auto status = mHal->unsubscribe(mSubscriptionCallback, propIds); !status.isOk()) {
        return AidlVhalClient::statusToError<
                void>(status,
                      StringPrintf("failed to unsubscribe to prop IDs: %s",
                                   toString(propIds).c_str()));
    }
    return {};
}

SubscriptionVehicleCallback::SubscriptionVehicleCallback(
        std::shared_ptr<ISubscriptionCallback> callback) :
      mCallback(callback) {}

ScopedAStatus SubscriptionVehicleCallback::onGetValues(
        [[maybe_unused]] const GetValueResults& results) {
    return ScopedAStatus::
            fromServiceSpecificErrorWithMessage(toInt(ErrorCode::INTERNAL_ERROR_FROM_VHAL),
                                                "onGetValues should never be called "
                                                "from SubscriptionVehicleCallback");
}

ScopedAStatus SubscriptionVehicleCallback::onSetValues(
        [[maybe_unused]] const SetValueResults& results) {
    return ScopedAStatus::
            fromServiceSpecificErrorWithMessage(toInt(ErrorCode::INTERNAL_ERROR_FROM_VHAL),
                                                "onSetValues should never be called "
                                                "from SubscriptionVehicleCallback");
}

ScopedAStatus SubscriptionVehicleCallback::onPropertyEvent(
        const VehiclePropValues& values, [[maybe_unused]] int32_t sharedMemoryCount) {
    auto parcelableResult = fromStableLargeParcelable(values);
    if (!parcelableResult.ok()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(toInt(ErrorCode::INTERNAL_ERROR_FROM_VHAL),
                                                    StringPrintf("failed to parse "
                                                                 "VehiclePropValues returned from "
                                                                 "VHAL, error: %s",
                                                                 parcelableResult.error()
                                                                         .getMessage())
                                                            .c_str());
    }

    std::vector<std::unique_ptr<IHalPropValue>> halPropValues;
    for (const VehiclePropValue& value : parcelableResult.value().getObject()->payloads) {
        VehiclePropValue valueCopy = value;
        halPropValues.push_back(std::make_unique<AidlHalPropValue>(std::move(valueCopy)));
    }
    mCallback->onPropertyEvent(halPropValues);
    return ScopedAStatus::ok();
}

ScopedAStatus SubscriptionVehicleCallback::onPropertySetError(const VehiclePropErrors& errors) {
    auto parcelableResult = fromStableLargeParcelable(errors);
    if (!parcelableResult.ok()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(toInt(ErrorCode::INTERNAL_ERROR_FROM_VHAL),
                                                    StringPrintf("failed to parse "
                                                                 "VehiclePropErrors returned from "
                                                                 "VHAL, error: %s",
                                                                 parcelableResult.error()
                                                                         .getMessage())
                                                            .c_str());
    }
    std::vector<HalPropError> halPropErrors;
    for (const VehiclePropError& error : parcelableResult.value().getObject()->payloads) {
        halPropErrors.push_back(HalPropError{
                .propId = error.propId,
                .areaId = error.areaId,
                .status = error.errorCode,
        });
    }
    mCallback->onPropertySetError(halPropErrors);
    return ScopedAStatus::ok();
}

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
