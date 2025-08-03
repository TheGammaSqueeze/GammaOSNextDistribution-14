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

#include "HidlVhalClient.h"

#include "HidlHalPropConfig.h"
#include "HidlHalPropValue.h"

#include <aidl/android/hardware/automotive/vehicle/StatusCode.h>
#include <utils/Log.h>

#include <VehicleUtils.h>

#include <memory>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

namespace {

using ::android::sp;
using ::android::wp;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::automotive::vehicle::toInt;
using ::android::hardware::automotive::vehicle::V2_0::IVehicle;
using ::android::hardware::automotive::vehicle::V2_0::StatusCode;
using ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags;
using ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions;
using ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig;
using ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue;
using ::android::hidl::base::V1_0::IBase;

aidl::android::hardware::automotive::vehicle::StatusCode toAidlStatusCode(StatusCode code) {
    return static_cast<aidl::android::hardware::automotive::vehicle::StatusCode>(code);
}

}  // namespace

std::shared_ptr<IVhalClient> HidlVhalClient::create() {
    sp<IVehicle> hidlVhal = IVehicle::getService();
    if (hidlVhal == nullptr) {
        ALOGD("HIDL VHAL service is not declared or not available");
        return nullptr;
    }
    return std::make_shared<HidlVhalClient>(hidlVhal);
}

std::shared_ptr<IVhalClient> HidlVhalClient::tryCreate() {
    sp<IVehicle> hidlVhal = IVehicle::tryGetService();
    if (hidlVhal == nullptr) {
        return nullptr;
    }
    return std::make_shared<HidlVhalClient>(hidlVhal);
}

std::shared_ptr<IVhalClient> HidlVhalClient::tryCreate(const char* descriptor) {
    sp<IVehicle> hidlVhal = IVehicle::tryGetService(descriptor);
    if (hidlVhal == nullptr) {
        return nullptr;
    }
    return std::make_shared<HidlVhalClient>(hidlVhal);
}

HidlVhalClient::HidlVhalClient(sp<IVehicle> hal) : mHal(hal) {
    mDeathRecipient = sp<HidlVhalClient::DeathRecipient>::make(this);
    mHal->linkToDeath(mDeathRecipient, /*cookie=*/0);
}

HidlVhalClient::~HidlVhalClient() {
    mHal->unlinkToDeath(mDeathRecipient);
}

bool HidlVhalClient::isAidlVhal() {
    return false;
}

std::unique_ptr<IHalPropValue> HidlVhalClient::createHalPropValue(int32_t propId) {
    return std::make_unique<HidlHalPropValue>(propId);
}

std::unique_ptr<IHalPropValue> HidlVhalClient::createHalPropValue(int32_t propId, int32_t areaId) {
    return std::make_unique<HidlHalPropValue>(propId, areaId);
}

void HidlVhalClient::getValue(const IHalPropValue& requestValue,
                              std::shared_ptr<GetValueCallbackFunc> callback) {
    const VehiclePropValue* propValue =
            reinterpret_cast<const VehiclePropValue*>(requestValue.toVehiclePropValue());
    int32_t propId = requestValue.getPropId();
    int32_t areaId = requestValue.getAreaId();
    auto result =
            mHal->get(*propValue,
                      [callback, propId, areaId](StatusCode status, const VehiclePropValue& value) {
                          if (status == StatusCode::OK) {
                              VehiclePropValue valueCopy = value;
                              (*callback)(std::make_unique<HidlHalPropValue>(std::move(valueCopy)));
                          } else {
                              (*callback)(ClientStatusError(toAidlStatusCode(status))
                                          << "failed to get value for prop: " << propId
                                          << ", areaId: " << areaId
                                          << ": status code: " << toInt(status));
                          }
                      });

    if (!result.isOk()) {
        (*callback)(ClientStatusError(toAidlStatusCode(StatusCode::TRY_AGAIN))
                    << "failed to get value for prop: " << requestValue.getPropId() << ", areaId: "
                    << requestValue.getAreaId() << ": error: " << result.description());
    }
}

void HidlVhalClient::setValue(const IHalPropValue& value,
                              std::shared_ptr<HidlVhalClient::SetValueCallbackFunc> callback) {
    const VehiclePropValue* propValue =
            reinterpret_cast<const VehiclePropValue*>(value.toVehiclePropValue());
    auto result = mHal->set(*propValue);
    if (!result.isOk()) {
        (*callback)(ClientStatusError(toAidlStatusCode(StatusCode::TRY_AGAIN))
                    << "failed to set value for prop: " << value.getPropId()
                    << ", areaId: " << value.getAreaId() << ": error: " << result.description());
        return;
    }
    StatusCode status = result;
    if (status != StatusCode::OK) {
        (*callback)(ClientStatusError(toAidlStatusCode(status))
                    << "failed to set value for prop: " << value.getPropId()
                    << ", areaId: " << value.getAreaId() << ": status code: " << toInt(status));
        return;
    }
    (*callback)({});
}

// Add the callback that would be called when VHAL binder died.
VhalClientResult<void> HidlVhalClient::addOnBinderDiedCallback(
        std::shared_ptr<OnBinderDiedCallbackFunc> callback) {
    std::lock_guard<std::mutex> lk(mLock);
    mOnBinderDiedCallbacks.insert(callback);
    return {};
}

// Remove a previously added OnBinderDied callback.
VhalClientResult<void> HidlVhalClient::removeOnBinderDiedCallback(
        std::shared_ptr<OnBinderDiedCallbackFunc> callback) {
    std::lock_guard<std::mutex> lk(mLock);
    if (mOnBinderDiedCallbacks.find(callback) == mOnBinderDiedCallbacks.end()) {
        return ClientStatusError(toAidlStatusCode(StatusCode::INVALID_ARG))
                << "The callback to remove was not added before";
    }
    mOnBinderDiedCallbacks.erase(callback);
    return {};
}

VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> HidlVhalClient::getAllPropConfigs() {
    std::vector<std::unique_ptr<IHalPropConfig>> halPropConfigs;
    auto result = mHal->getAllPropConfigs([&halPropConfigs](
                                                  const hidl_vec<VehiclePropConfig>& propConfigs) {
        for (const VehiclePropConfig& config : propConfigs) {
            VehiclePropConfig configCopy = config;
            halPropConfigs.push_back(std::make_unique<HidlHalPropConfig>(std::move(configCopy)));
        }
    });
    if (!result.isOk()) {
        return ClientStatusError(toAidlStatusCode(StatusCode::TRY_AGAIN))
                << "failed to getAllPropConfigs: error: " << result.description();
    }
    return std::move(halPropConfigs);
}

VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> HidlVhalClient::getPropConfigs(
        std::vector<int32_t> propIds) {
    std::vector<std::unique_ptr<IHalPropConfig>> halPropConfigs;
    hidl_vec<int32_t> hidlPropIds(propIds);
    StatusCode status;
    auto result =
            mHal->getPropConfigs(hidlPropIds,
                                 [&status,
                                  &halPropConfigs](StatusCode s,
                                                   const hidl_vec<VehiclePropConfig>& propConfigs) {
                                     status = s;
                                     if (s != StatusCode::OK) {
                                         return;
                                     }
                                     for (const VehiclePropConfig& config : propConfigs) {
                                         VehiclePropConfig configCopy = config;
                                         halPropConfigs.push_back(
                                                 std::make_unique<HidlHalPropConfig>(
                                                         std::move(configCopy)));
                                     }
                                 });
    if (!result.isOk()) {
        return ClientStatusError(toAidlStatusCode(StatusCode::TRY_AGAIN))
                << "failed to getPropConfigs: error: " << result.description();
    }
    if (status != StatusCode::OK) {
        return ClientStatusError(toAidlStatusCode(status))
                << "failed to getPropConfigs: status code: " << toInt(status);
    }
    return std::move(halPropConfigs);
}

std::unique_ptr<ISubscriptionClient> HidlVhalClient::getSubscriptionClient(
        std::shared_ptr<ISubscriptionCallback> callback) {
    return std::make_unique<HidlSubscriptionClient>(mHal, callback);
}

void HidlVhalClient::onBinderDied() {
    std::lock_guard<std::mutex> lk(mLock);
    for (auto callback : mOnBinderDiedCallbacks) {
        (*callback)();
    }
}

HidlVhalClient::DeathRecipient::DeathRecipient(HidlVhalClient* client) : mClient(client) {}

void HidlVhalClient::DeathRecipient::serviceDied([[maybe_unused]] uint64_t cookie,
                                                 [[maybe_unused]] const wp<IBase>& who) {
    mClient->onBinderDied();
}

HidlSubscriptionClient::HidlSubscriptionClient(sp<IVehicle> hal,
                                               std::shared_ptr<ISubscriptionCallback> callback) :
      mCallback(callback), mHal(hal) {
    mVhalCallback = sp<SubscriptionCallback>::make(callback);
}

VhalClientResult<void> HidlSubscriptionClient::subscribe(
        const std::vector<::aidl::android::hardware::automotive::vehicle::SubscribeOptions>&
                options) {
    std::vector<SubscribeOptions> hidlOptions;
    for (const auto& option : options) {
        hidlOptions.push_back(SubscribeOptions{
                .propId = option.propId,
                .sampleRate = option.sampleRate,
                .flags = SubscribeFlags::EVENTS_FROM_CAR,
        });
    }
    auto result = mHal->subscribe(mVhalCallback, hidlOptions);
    if (!result.isOk()) {
        return ClientStatusError(toAidlStatusCode(StatusCode::TRY_AGAIN))
                << "failed to subscribe: error: " << result.description();
    }
    StatusCode status = result;
    if (status != StatusCode::OK) {
        return ClientStatusError(toAidlStatusCode(status))
                << "failed to subscribe: status code: " << toInt(status);
    }
    return {};
}

VhalClientResult<void> HidlSubscriptionClient::unsubscribe(const std::vector<int32_t>& propIds) {
    for (int32_t propId : propIds) {
        auto result = mHal->unsubscribe(mVhalCallback, propId);
        if (!result.isOk()) {
            return ClientStatusError(toAidlStatusCode(StatusCode::TRY_AGAIN))
                    << "failed to unsubscribe prop Id: " << propId
                    << ": error: " << result.description();
        }
        StatusCode status = result;
        if (status != StatusCode::OK) {
            return ClientStatusError(toAidlStatusCode(status))
                    << "failed to unsubscribe prop Id: " << propId
                    << ": status code: " << toInt(status);
        }
    }
    return {};
}

SubscriptionCallback::SubscriptionCallback(std::shared_ptr<ISubscriptionCallback> callback) :
      mCallback(callback) {}

Return<void> SubscriptionCallback::onPropertyEvent(const hidl_vec<VehiclePropValue>& propValues) {
    std::vector<std::unique_ptr<IHalPropValue>> halPropValues;
    for (const VehiclePropValue& value : propValues) {
        VehiclePropValue valueCopy = value;
        halPropValues.push_back(std::make_unique<HidlHalPropValue>(std::move(valueCopy)));
    }
    mCallback->onPropertyEvent(halPropValues);
    return {};
}

Return<void> SubscriptionCallback::onPropertySet(
        [[maybe_unused]] const VehiclePropValue& propValue) {
    // Deprecated
    return {};
}

Return<void> SubscriptionCallback::onPropertySetError(StatusCode status, int32_t propId,
                                                      int32_t areaId) {
    std::vector<HalPropError> halPropErrors;
    halPropErrors.push_back(HalPropError{
            .propId = propId,
            .areaId = areaId,
            .status =
                    static_cast<::aidl::android::hardware::automotive::vehicle::StatusCode>(status),
    });
    mCallback->onPropertySetError(halPropErrors);
    return {};
}

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
