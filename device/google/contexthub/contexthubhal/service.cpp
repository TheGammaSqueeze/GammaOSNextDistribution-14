/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "android.hardware.contexthub@1.0-service.nanohub"

#include <android/hardware/contexthub/1.0/IContexthub.h>
#include <hidl/HidlSupport.h>
#include <hidl/HidlTransportSupport.h>

#include "NanohubHidlAdapter.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::contexthub::V1_0::IContexthub;
using android::hardware::contexthub::V1_0::implementation::Contexthub;
using namespace android;

status_t registerContexthubService()
{
    sp<IContexthub> contexthub = new Contexthub();
    return contexthub->registerAsService();
}

int main() {
    configureRpcThreadpool(1, true);
    status_t status = registerContexthubService();
    if (status != OK) {
        return status;
    }
    joinRpcThreadpool();
}
