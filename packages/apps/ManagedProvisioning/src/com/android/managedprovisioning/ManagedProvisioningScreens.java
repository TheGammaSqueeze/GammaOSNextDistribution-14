/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning;

public enum ManagedProvisioningScreens {
    PRE_PROVISIONING,
    PRE_PROVISIONING_VIA_NFC,
    LANDING,
    PROVISIONING,
    ADMIN_INTEGRATED_PREPARE,
    RESET_AND_RETURN_DEVICE,
    RESET_DEVICE,
    WEB,
    ENCRYPT,
    POST_ENCRYPT,
    FINALIZATION_INSIDE_SUW,
    TERMS,
    FINANCED_DEVICE_LANDING,
    RETRY_LAUNCH,
    DOWNLOAD_ROLE_HOLDER,
    ESTABLISH_NETWORK_CONNECTION
}
