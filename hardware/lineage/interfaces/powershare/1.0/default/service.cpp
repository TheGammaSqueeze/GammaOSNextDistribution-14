/*
 * Copyright (C) 2024 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "PowerShare.h"

#include <hidl/HidlTransportSupport.h>

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::sp;

using vendor::lineage::powershare::V1_0::IPowerShare;
using vendor::lineage::powershare::V1_0::implementation::PowerShare;

int main() {
    sp<IPowerShare> powerShareService = new PowerShare();

    configureRpcThreadpool(1, true /*callerWillJoin*/);

    if (powerShareService->registerAsService() != android::OK) {
        LOG(ERROR) << "Can't register PowerShare HAL service";
        return 1;
    }

    joinRpcThreadpool();

    return 0; // should never get here
}
