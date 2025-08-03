/*
 * Copyright (C) 2016-2021 The Android Open Source Project
 * Copyright (C) 2018-2024 The LineageOS Project
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

#include <hidl/HidlTransportSupport.h>
#include "Usb.h"

using android::base::GetProperty;

using android::sp;

// libhwbinder:
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

// Generated HIDL files
using android::hardware::usb::V1_3::IUsb;
using android::hardware::usb::V1_3::implementation::Usb;

using android::OK;
using android::status_t;

int main() {
    android::sp<IUsb> service = new Usb(GetProperty(USB_DEVICE_PROP, "a600000.ssusb"),
                                        GetProperty(USB_CONTROLLER_PROP, "a600000.dwc3"));

    configureRpcThreadpool(1, true /*callerWillJoin*/);
    status_t status = service->registerAsService();

    if (status == OK) {
        ALOGI("USB HAL Ready.");
        joinRpcThreadpool();
    }

    ALOGE("Cannot register USB HAL service");
    return 1;
}
