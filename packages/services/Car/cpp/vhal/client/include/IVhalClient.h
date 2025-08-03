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

#ifndef CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_
#define CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_

#include "IHalPropConfig.h"
#include "IHalPropValue.h"

#include <aidl/android/hardware/automotive/vehicle/StatusCode.h>
#include <aidl/android/hardware/automotive/vehicle/SubscribeOptions.h>
#include <android-base/result.h>

#include <VehicleUtils.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

struct HalPropError {
    int32_t propId;
    int32_t areaId;
    aidl::android::hardware::automotive::vehicle::StatusCode status;
};

// ISubscriptionCallback is a general interface to delivery property events caused by subscription.
class ISubscriptionCallback {
public:
    virtual ~ISubscriptionCallback() = default;
    /**
     * Called when new property events happen.
     */
    virtual void onPropertyEvent(const std::vector<std::unique_ptr<IHalPropValue>>& values) = 0;

    /**
     * Called when property set errors happen.
     */
    virtual void onPropertySetError(const std::vector<HalPropError>& errors) = 0;
};

// Errors for vehicle HAL client interface.
enum class ErrorCode : int {
    // Response status is OK. No errors.
    OK = 0,
    // The argument is invalid.
    INVALID_ARG = 1,
    // The request timed out. The client may try again.
    TIMEOUT = 2,
    // Some errors occur while connecting VHAL. The client may try again.
    TRANSACTION_ERROR = 3,
    // Some unexpected errors happen in VHAL. Needs to try again.
    TRY_AGAIN_FROM_VHAL = 4,
    // The device of corresponding vehicle property is not available.
    // Example: the HVAC unit is turned OFF when user wants to adjust temperature.
    NOT_AVAILABLE_FROM_VHAL = 5,
    // The request is unauthorized.
    ACCESS_DENIED_FROM_VHAL = 6,
    // Some unexpected errors, for example OOM, happen in VHAL.
    INTERNAL_ERROR_FROM_VHAL = 7,
};

// Convert the VHAL {@code StatusCode} to {@code ErrorCode}.
static ErrorCode statusCodeToErrorCode(
        const aidl::android::hardware::automotive::vehicle::StatusCode& code) {
    switch (code) {
        case aidl::android::hardware::automotive::vehicle::StatusCode::OK:
            return ErrorCode::OK;
        case aidl::android::hardware::automotive::vehicle::StatusCode::TRY_AGAIN:
            return ErrorCode::TRY_AGAIN_FROM_VHAL;
        case aidl::android::hardware::automotive::vehicle::StatusCode::INVALID_ARG:
            return ErrorCode::INVALID_ARG;
        case aidl::android::hardware::automotive::vehicle::StatusCode::NOT_AVAILABLE:
            return ErrorCode::NOT_AVAILABLE_FROM_VHAL;
        case aidl::android::hardware::automotive::vehicle::StatusCode::ACCESS_DENIED:
            return ErrorCode::ACCESS_DENIED_FROM_VHAL;
        case aidl::android::hardware::automotive::vehicle::StatusCode::INTERNAL_ERROR:
            return ErrorCode::INTERNAL_ERROR_FROM_VHAL;
        default:
            return ErrorCode::INTERNAL_ERROR_FROM_VHAL;
    }
}

// VhalClientError is a wrapper class for {@code ErrorCode} that could act as E in {@code
// Result<T,E>}.
class VhalClientError final {
public:
    VhalClientError() : mCode(ErrorCode::OK) {}

    VhalClientError(ErrorCode&& code) : mCode(code) {}

    VhalClientError(const ErrorCode& code) : mCode(code) {}

    VhalClientError(aidl::android::hardware::automotive::vehicle::StatusCode&& code) :
          mCode(statusCodeToErrorCode(code)) {}

    VhalClientError(const aidl::android::hardware::automotive::vehicle::StatusCode& code) :
          mCode(statusCodeToErrorCode(code)) {}

    ErrorCode value() const;

    inline operator ErrorCode() const { return value(); }

    static std::string toString(ErrorCode code);

    std::string print() const;

private:
    ErrorCode mCode;
};

// VhalClientResult is a {@code Result} that contains {@code ErrorCode} as error type.
template <class T>
using VhalClientResult = android::base::Result<T, VhalClientError>;

// ClientStatusError could be cast to {@code ResultError} with a {@code ErrorCode}
// and should be used as error type for {@VhalClientResult}.
using ClientStatusError = android::base::Error<VhalClientError>;

// ISubscriptionCallback is a client that could be used to subscribe/unsubscribe.
class ISubscriptionClient {
public:
    virtual ~ISubscriptionClient() = default;

    // Subscribes to properties. It is recommended to use SubscribeOptionsBuilder to build
    // the options.
    virtual VhalClientResult<void> subscribe(
            const std::vector<aidl::android::hardware::automotive::vehicle::SubscribeOptions>&
                    options) = 0;

    virtual VhalClientResult<void> unsubscribe(const std::vector<int32_t>& propIds) = 0;
};

class SubscribeOptionsBuilder {
private:
    int32_t mPropId;
    std::vector<int32_t> mAreaIds;
    float mSampleRate = 0;
    float mResolution = 0.0f;
    // VUR is on by default.
    bool mEnableVariableUpdateRate = true;

public:
    explicit SubscribeOptionsBuilder(int32_t propId) : mPropId(propId) {}

    // Sets the sample rate.
    void setSampleRate(float sampleRate) { mSampleRate = sampleRate; }

    // Adds an area ID to subscribe.
    //
    // Only supported for AIDL VHAL client.
    //
    // For HIDL VHAL client, per-area ID subscription is not supported, this is ignored and all
    // area IDs for the property will be subscribed.
    void addAreaId(int32_t areaId) { mAreaIds.push_back(areaId); }

    // Sets the resolution of property updates for continuous property.
    //
    // Only supported for continuous property. Ignored for non-cotinuous property.
    //
    // Only supported for AIDL VHAL client. Ignored for HIDL VHAL client.
    //
    // This value indicates the resolution at which continuous property updates should be sent to
    // the platform. For example, if resolution is 0.01, the subscribed property value should be
    // rounded to two decimal places.
    //
    // The resolution must be an integer power of 10, (e.g. 0.01, 0.1, 1, 10, etc.).
    void setResolution(float resolution) { mResolution = resolution; }

    // Sets whether to enable varialbe update rate.
    //
    // Only supported for continuous property. Ignored for non-cotinuous property.
    //
    // Only supported for AIDL VHAL client. Ignored for HIDL VHAL client.
    //
    // If variable update rate is enabled, for each given areaId, if VHAL supports variable update
    // rate for the [propId, areaId], VHAL must ignore duplicate property value events
    // and only sends changed value events (a.k.a treat continuous as an on-change property).
    void setEnableVariableUpdateRate(bool enableVariableUpdateRate) {
        mEnableVariableUpdateRate = enableVariableUpdateRate;
    }

    // Builds the SubscribeOptions.
    aidl::android::hardware::automotive::vehicle::SubscribeOptions build() {
        return {
                .propId = mPropId,
                .areaIds = mAreaIds,
                .sampleRate = mSampleRate,
                .resolution = mResolution,
                .enableVariableUpdateRate = mEnableVariableUpdateRate,
        };
    }
};

// IVhalClient is a thread-safe client for AIDL or HIDL VHAL backend.
class IVhalClient {
public:
    // Wait for VHAL service and create a client. Return nullptr if failed to connect to VHAL.
    static std::shared_ptr<IVhalClient> create();

    // Try to get the VHAL service and create a client. Return nullptr if failed to connect to VHAL.
    static std::shared_ptr<IVhalClient> tryCreate();

    // Try to create a client based on the AIDL VHAL service descriptor.
    static std::shared_ptr<IVhalClient> tryCreateAidlClient(const char* descriptor);

    // Try to create a client based on the HIDL VHAL service descriptor.
    static std::shared_ptr<IVhalClient> tryCreateHidlClient(const char* descriptor);

    // The default timeout for callbacks.
    constexpr static int64_t DEFAULT_TIMEOUT_IN_SEC = 10;

    virtual ~IVhalClient() = default;

    using GetValueCallbackFunc =
            std::function<void(VhalClientResult<std::unique_ptr<IHalPropValue>>)>;
    using SetValueCallbackFunc = std::function<void(VhalClientResult<void>)>;
    using OnBinderDiedCallbackFunc = std::function<void()>;

    /**
     * Check whether we are connected to AIDL VHAL backend.
     *
     * Returns {@code true} if we are connected to AIDL VHAL backend, {@code false} if we are
     * connected to HIDL backend.
     */
    virtual bool isAidlVhal() = 0;

    /**
     * Create a new {@code IHalpropValue}.
     *
     * @param propId The property ID.
     * @return The created {@code IHalPropValue}.
     */
    virtual std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId) = 0;

    /**
     * Create a new {@code IHalpropValue}.
     *
     * @param propId The property ID.
     * @param areaId The area ID for the property.
     * @return The created {@code IHalPropValue}.
     */
    virtual std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId, int32_t areaId) = 0;

    /**
     * Get a property value asynchronously.
     *
     * @param requestValue The value to request.
     * @param callback The callback that would be called when the result is ready. The callback
     *    would be called with an okay result with the got value inside on success. The callback
     *    would be called with an error result with error code as the returned status code on
     *    failure.
     */
    virtual void getValue(const IHalPropValue& requestValue,
                          std::shared_ptr<GetValueCallbackFunc> callback) = 0;

    /**
     * Get a property value synchronously.
     *
     * @param requestValue the value to request.
     * @return An okay result with the returned value on success or an error result with returned
     *    status code as error code. For AIDL backend, this would return TRY_AGAIN error on timeout.
     *    For HIDL backend, because HIDL backend is synchronous, timeout does not apply.
     */
    virtual VhalClientResult<std::unique_ptr<IHalPropValue>> getValueSync(
            const IHalPropValue& requestValue);

    /**
     * Set a property value asynchronously.
     *
     * @param requestValue The value to set.
     * @param callback The callback that would be called when the request is processed. The callback
     *    would be called with an empty okay result on success. The callback would be called with
     *    an error result with error code as the returned status code on failure.
     */
    virtual void setValue(const IHalPropValue& requestValue,
                          std::shared_ptr<SetValueCallbackFunc> callback) = 0;

    /**
     * Set a property value synchronously.
     *
     * @param requestValue the value to set.
     * @return An empty okay result on success or an error result with returned status code as
     *    error code. For AIDL backend, this would return TIMEOUT error on timeout.
     *    For HIDL backend, because HIDL backend is synchronous, timeout does not apply.
     */
    virtual VhalClientResult<void> setValueSync(const IHalPropValue& requestValue);

    /**
     * Add a callback that would be called when the binder connection to VHAL died.
     *
     * @param callback The callback that would be called when the binder died.
     * @return An okay result on success or an error on failure.
     */
    virtual VhalClientResult<void> addOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) = 0;

    /**
     * Remove a previously added OnBinderDied callback.
     *
     * @param callback The callback that would be removed.
     * @return An okay result on success, or an error if the callback is not added before.
     */
    virtual VhalClientResult<void> removeOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) = 0;

    /**
     * Get all the property configurations.
     *
     * @return An okay result that contains all property configs on success or an error on failure.
     */
    virtual VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> getAllPropConfigs() = 0;

    /**
     * Get the configs for specified properties.
     *
     * @param propIds A list of property IDs to get configs for.
     * @return An okay result that contains property configs for specified properties on success or
     *    an error if failed to get any of the property configs.
     */
    virtual VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>> getPropConfigs(
            std::vector<int32_t> propIds) = 0;

    /**
     * Get a {@code ISubscriptionClient} that could be used to subscribe/unsubscribe to properties.
     *
     * @param callback The callback that would be called when property event happens.
     * @return A {@code ISubscriptionClient} used to subscribe/unsubscribe.
     */
    virtual std::unique_ptr<ISubscriptionClient> getSubscriptionClient(
            std::shared_ptr<ISubscriptionCallback> callback) = 0;

    /**
     * Gets the VHAL interface version used by VHAL.
     *
     * This is only useful for AIDL VHAL.
     */
    virtual int32_t getRemoteInterfaceVersion() { return 0; }
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_
