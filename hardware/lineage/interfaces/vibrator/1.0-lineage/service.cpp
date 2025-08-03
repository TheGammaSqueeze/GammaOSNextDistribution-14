/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#define LOG_TAG "android.hardware.vibrator@1.0-service.lineage"

#include <hidl/HidlTransportSupport.h>

#include "Vibrator.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

using android::hardware::vibrator::V1_0::IVibrator;
using android::hardware::vibrator::V1_0::implementation::Vibrator;

using android::OK;
using android::sp;
using android::status_t;

int main() {
    android::sp<IVibrator> vibrator = new Vibrator();

    configureRpcThreadpool(1, true);

    status_t status = vibrator->registerAsService();
    if (status != OK) {
        ALOGE("Cannot register Vibrator HAL service.");
        return 1;
    }

    ALOGI("Vibrator HAL service ready.");

    joinRpcThreadpool();

    ALOGI("Vibrator HAL service failed to join thread pool.");
    return 1;
}
