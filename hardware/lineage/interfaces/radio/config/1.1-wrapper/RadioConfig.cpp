/*
 * Copyright (C) 2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "RadioConfigWrapper"

#include <android-base/logging.h>

#include "RadioConfig.h"
#include "hidl-utils.h"

#include <vector>

#define WRAP_V1_0_CALL(method, ...)                                            \
    do {                                                                       \
        auto realRadioConfig = mRealRadioConfig;                               \
        if (realRadioConfig != nullptr) {                                      \
            return realRadioConfig->method(__VA_ARGS__);                       \
        }                                                                      \
        LOG(ERROR) << __func__ << ": realRadioConfig is null";                 \
        return Status::fromExceptionCode(Status::Exception::EX_ILLEGAL_STATE); \
    } while (0)

#define MAYBE_WRAP_V1_1_CALL(method, ...)                    \
    do {                                                     \
        auto realRadioConfigV1_1 = mRealRadioConfigV1_1;     \
        if (realRadioConfigV1_1 != nullptr) {                \
            return realRadioConfigV1_1->method(__VA_ARGS__); \
        }                                                    \
    } while (0)

namespace android::hardware::radio::config::implementation {

using namespace ::android::hardware::hidl_utils;

using ::android::hardware::radio::V1_0::IRadio;
using ::android::hardware::radio::V1_0::RadioError;
using ::android::hardware::radio::V1_0::RadioResponseInfo;
using ::android::hardware::radio::V1_0::RadioResponseType;

RadioConfig::RadioConfig(
        sp<::lineage::hardware::radio::config::V1_0::IRadioConfig> realRadioConfig,
        sp<::lineage::hardware::radio::config::V1_1::IRadioConfig> realRadioConfigV1_1)
    : mRealRadioConfig(realRadioConfig), mRealRadioConfigV1_1(realRadioConfigV1_1) {
    android::base::SetLogger(android::base::LogdLogger(android::base::RADIO));
}

// Methods from ::android::hardware::radio::config::V1_0::IRadioConfig follow.
Return<void> RadioConfig::setResponseFunctions(
        const sp<::android::hardware::radio::config::V1_0::IRadioConfigResponse>&
                radioConfigResponse,
        const sp<::android::hardware::radio::config::V1_0::IRadioConfigIndication>&
                radioConfigIndication) {
    std::lock_guard<std::mutex> lock(mMutex);
    mRadioConfigResponse = radioConfigResponse;
    mRadioConfigResponseV1_1 =
            ::android::hardware::radio::config::V1_1::IRadioConfigResponse::castFrom(
                    mRadioConfigResponse)
                    .withDefault(nullptr);

    auto realRadioConfig = mRealRadioConfig;
    if (realRadioConfig == nullptr) {
        LOG(ERROR) << __func__ << ": realRadioConfig is null";
        return Status::fromExceptionCode(Status::Exception::EX_ILLEGAL_STATE);
    }

    return realRadioConfig->setResponseFunctions(
            reinterpret_cast<
                    const sp<::lineage::hardware::radio::config::V1_0::IRadioConfigResponse>&>(
                    radioConfigResponse),
            reinterpret_cast<
                    const sp<::lineage::hardware::radio::config::V1_0::IRadioConfigIndication>&>(
                    radioConfigIndication));
}

Return<void> RadioConfig::getSimSlotsStatus(int32_t serial) {
    WRAP_V1_0_CALL(getSimSlotsStatus, serial);
}

Return<void> RadioConfig::setSimSlotsMapping(int32_t serial, const hidl_vec<uint32_t>& slotMap) {
    WRAP_V1_0_CALL(setSimSlotsMapping, serial, slotMap);
}

// Methods from ::android::hardware::radio::config::V1_1::IRadioConfig follow.
Return<void> RadioConfig::getPhoneCapability(int32_t serial) {
    MAYBE_WRAP_V1_1_CALL(getPhoneCapability, serial);

    auto radioConfigResponseV1_1 = mRadioConfigResponseV1_1;
    if (radioConfigResponseV1_1 == nullptr) {
        LOG(ERROR) << __func__ << ": radioConfigResponseV1_1 is null";
        return Status::fromExceptionCode(Status::Exception::EX_ILLEGAL_STATE);
    }

    RadioResponseInfo radioResponseInfo = {
            RadioResponseType::SOLICITED,
            serial,
            RadioError::NONE,
    };

    /*
     * Simulates android.hardware.radio.config@1.0 behavior
     * Refer to convertHalPhoneCapability() on RILUtils.java
     */
    ::android::hardware::radio::config::V1_1::PhoneCapability phoneCapability = {
            .maxActiveData = 0,
            .maxActiveInternetData = 0,
            .isInternetLingeringSupported = 0,
            .logicalModemList = {},
    };

    radioConfigResponseV1_1->getPhoneCapabilityResponse(radioResponseInfo, phoneCapability);

    return Void();
}

Return<void> RadioConfig::setPreferredDataModem(int32_t serial, uint8_t modemId) {
    MAYBE_WRAP_V1_1_CALL(setPreferredDataModem, serial, modemId);

    std::lock_guard<std::mutex> lock(mMutex);

    std::vector<sp<IRadio>> radios;

    uint8_t numSlots = 0;
    while (true) {
        auto radio = getRadioForModemId(numSlots);
        if (radio == nullptr) {
            break;
        }

        radios.push_back(radio);

        numSlots++;
    }

    LOG(DEBUG) << __func__ << ": modemId = " << std::to_string(modemId)
               << ", numSlots = " << std::to_string(numSlots);

    RadioError radioError = RadioError::NONE;

    if (radios.empty() || modemId >= radios.size()) {
        LOG(ERROR) << __func__ << ": Invalid arguments";
        radioError = RadioError::INVALID_ARGUMENTS;
    } else {
        for (uint8_t i = 0; i < radios.size(); i++) {
            if (i != modemId) radios[i]->setDataAllowed(-1, false);
        }

        radios[modemId]->setDataAllowed(-1, true);
    }

    auto radioConfigResponseV1_1 = mRadioConfigResponseV1_1;
    if (radioConfigResponseV1_1 == nullptr) {
        LOG(ERROR) << __func__ << ": radioConfigResponseV1_1 is null";
        return Status::fromExceptionCode(Status::Exception::EX_ILLEGAL_STATE);
    }

    RadioResponseInfo radioResponseInfo = {
            RadioResponseType::SOLICITED,
            serial,
            radioError,
    };

    radioConfigResponseV1_1->setPreferredDataModemResponse(radioResponseInfo);

    return Void();
}

Return<void> RadioConfig::setModemsConfig(
        int32_t serial,
        const ::android::hardware::radio::config::V1_1::ModemsConfig& modemsConfig) {
    // Cannot use MAYBE_WRAP_V1_1_CALL, needs reinterpret_cast
    auto realRadioConfigV1_1 = mRealRadioConfigV1_1;
    if (realRadioConfigV1_1 != nullptr) {
        return realRadioConfigV1_1->setModemsConfig(
                serial,
                reinterpret_cast<const ::lineage::hardware::radio::config::V1_1::ModemsConfig&>(
                        modemsConfig));
    }

    auto radioConfigResponseV1_1 = mRadioConfigResponseV1_1;
    if (radioConfigResponseV1_1 == nullptr) {
        LOG(ERROR) << __func__ << ": radioConfigResponseV1_1 is null";
        return Status::fromExceptionCode(Status::Exception::EX_ILLEGAL_STATE);
    }

    radioConfigResponseV1_1->setModemsConfigResponse(getUnimplementedResponseInfo(serial));

    return Void();
}

Return<void> RadioConfig::getModemsConfig(int32_t serial) {
    MAYBE_WRAP_V1_1_CALL(getModemsConfig, serial);

    auto radioConfigResponseV1_1 = mRadioConfigResponseV1_1;
    if (radioConfigResponseV1_1 == nullptr) {
        LOG(ERROR) << __func__ << ": radioConfigResponseV1_1 is null";
        return Status::fromExceptionCode(Status::Exception::EX_ILLEGAL_STATE);
    }

    radioConfigResponseV1_1->getModemsConfigResponse(getUnimplementedResponseInfo(serial), {});

    return Void();
}

// Helper methods follow.
sp<IRadio> RadioConfig::getRadioForModemId(uint8_t modemId) {
    if (mModemIdToRadioCache.find(modemId) == mModemIdToRadioCache.end() ||
        mModemIdToRadioCache[modemId] == nullptr) {
        mModemIdToRadioCache[modemId] = IRadio::getService("slot" + std::to_string(modemId + 1));
        if (mModemIdToRadioCache[modemId] != nullptr) {
            hidl_utils::linkDeathToDeath(mModemIdToRadioCache[modemId]);
        }
    }

    return mModemIdToRadioCache[modemId];
}

RadioResponseInfo RadioConfig::getUnimplementedResponseInfo(int32_t serial) {
    return {
            RadioResponseType::SOLICITED,
            serial,
            RadioError::REQUEST_NOT_SUPPORTED,
    };
}

}  // namespace android::hardware::radio::config::implementation
