/*
 * Copyright (C) 2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <android/hardware/radio/1.0/IRadio.h>
#include <android/hardware/radio/config/1.0/IRadioConfig.h>
#include <android/hardware/radio/config/1.1/IRadioConfig.h>
#include <android/hardware/radio/config/1.1/IRadioConfigResponse.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <lineage/hardware/radio/config/1.1/IRadioConfig.h>
#include <lineage/hardware/radio/config/1.1/IRadioConfigIndication.h>
#include <lineage/hardware/radio/config/1.1/IRadioConfigResponse.h>

#include <map>
#include <mutex>

namespace android::hardware::radio::config::implementation {

using ::android::sp;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;

struct RadioConfig : public V1_1::IRadioConfig {
  public:
    RadioConfig(sp<::lineage::hardware::radio::config::V1_0::IRadioConfig> realRadioConfig,
                sp<::lineage::hardware::radio::config::V1_1::IRadioConfig> realRadioConfigV1_1);

    // Methods from ::android::hardware::radio::config::V1_0::IRadioConfig follow.
    Return<void> setResponseFunctions(
            const sp<::android::hardware::radio::config::V1_0::IRadioConfigResponse>&
                    radioConfigResponse,
            const sp<::android::hardware::radio::config::V1_0::IRadioConfigIndication>&
                    radioConfigIndication) override;
    Return<void> getSimSlotsStatus(int32_t serial) override;
    Return<void> setSimSlotsMapping(int32_t serial, const hidl_vec<uint32_t>& slotMap) override;

    // Methods from ::android::hardware::radio::config::V1_1::IRadioConfig follow.
    Return<void> getPhoneCapability(int32_t serial) override;
    Return<void> setPreferredDataModem(int32_t serial, uint8_t modemId) override;
    Return<void> setModemsConfig(
            int32_t serial,
            const ::android::hardware::radio::config::V1_1::ModemsConfig& modemsConfig) override;
    Return<void> getModemsConfig(int32_t serial) override;

  private:
    sp<::lineage::hardware::radio::config::V1_0::IRadioConfig> mRealRadioConfig;
    sp<::lineage::hardware::radio::config::V1_1::IRadioConfig> mRealRadioConfigV1_1;

    sp<::android::hardware::radio::config::V1_0::IRadioConfigResponse> mRadioConfigResponse;
    sp<::android::hardware::radio::config::V1_1::IRadioConfigResponse> mRadioConfigResponseV1_1;

    std::map<uint8_t, sp<::android::hardware::radio::V1_0::IRadio>> mModemIdToRadioCache;

    std::mutex mMutex;

    // Helper methods follow.
    sp<::android::hardware::radio::V1_0::IRadio> getRadioForModemId(uint8_t modemId);
    ::android::hardware::radio::V1_0::RadioResponseInfo getUnimplementedResponseInfo(
            int32_t serial);
};

}  // namespace android::hardware::radio::config::implementation
