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

#include "nanohub_perdevice.h"
#include "nanohubhal.h"

using namespace android::nanohub;

static context_hub_callback *mSavedCbk = nullptr;

static int legacy_cbk(uint32_t hub_id, const HubMessage &rxMsg, void *cookie)
{
    return mSavedCbk(hub_id, &rxMsg, cookie);
}

static int legacy_subscribe_messages(uint32_t hub_id, context_hub_callback *cbk, void *cookie)
{
    mSavedCbk = cbk;
    if (cbk)
        return NanoHub::subscribeMessages(hub_id, legacy_cbk, cookie);
    else
        return NanoHub::subscribeMessages(hub_id, nullptr, nullptr);
}

static int legacy_send_message(uint32_t hub_id, const hub_message_t *msg)
{
    return NanoHub::sendToNanohub(hub_id, msg, 0, ENDPOINT_UNSPECIFIED);
}

static int legacy_get_hubs(context_hub_module_t*, const context_hub_t ** list)
{
    *list = get_hub_info();

    return 1; /* we have one hub */
}

context_hub_module_t HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = CONTEXT_HUB_DEVICE_API_VERSION_1_0,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = CONTEXT_HUB_MODULE_ID,
        .name = "Nanohub HAL",
        .author = "Google",
    },

    .get_hubs = legacy_get_hubs,
    .subscribe_messages = legacy_subscribe_messages,
    .send_message = legacy_send_message,
};
