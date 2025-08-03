/*
 * Copyright (C) 2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "android.hardware.radio.config@1.1-service.wrapper"

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>

#include "RadioConfig.h"
#include "hidl-utils.h"

using namespace android::hardware::hidl_utils;

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

using android::hardware::radio::config::implementation::RadioConfig;

using android::OK;
using android::sp;
using android::status_t;

int main() {
    sp<lineage::hardware::radio::config::V1_0::IRadioConfig> realRadioConfig =
            lineage::hardware::radio::config::V1_0::IRadioConfig::getService();
    CHECK(realRadioConfig) << "Cannot get backend radio config V1.0 service.";
    linkDeathToDeath(realRadioConfig);

    sp<lineage::hardware::radio::config::V1_1::IRadioConfig> realRadioConfigV1_1 =
            lineage::hardware::radio::config::V1_1::IRadioConfig::getService();
    if (realRadioConfigV1_1 == nullptr) {
        LOG(ERROR) << "Cannot get backend radio config V1.1 service (not fatal).";
    } else {
        linkDeathToDeath(realRadioConfigV1_1);
    }

    sp<android::hardware::radio::config::V1_1::IRadioConfig> radioConfig =
            new RadioConfig(realRadioConfig, realRadioConfigV1_1);

    configureRpcThreadpool(1, true);

    status_t status = radioConfig->registerAsService();
    CHECK_EQ(status, OK) << "Cannot register RadioConfig HAL service.";

    LOG(INFO) << "RadioConfig HAL service ready.";

    joinRpcThreadpool();

    LOG(FATAL) << "RadioConfig HAL service failed to join thread pool.";
    return EXIT_FAILURE;  // should not reach
}
