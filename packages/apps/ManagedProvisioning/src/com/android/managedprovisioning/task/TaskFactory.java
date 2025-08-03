/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.managedprovisioning.task;

import android.content.Context;

import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * A factory which creates provisioning tasks.
 */
public class TaskFactory {

    /**
     * Creates a task that adds wifi network.
     */
    public AbstractProvisioningTask createAddWifiNetworkTask(
            Context context, ProvisioningParams provisioningParams,
            AbstractProvisioningTask.Callback callback) {
        return new AddWifiNetworkTask(context, provisioningParams, callback);
    }

    /**
     * Creates a task that connects to mobile network.
     */
    public AbstractProvisioningTask createConnectMobileNetworkTask(
            Context context, ProvisioningParams provisioningParams,
            AbstractProvisioningTask.Callback callback) {
        return new ConnectMobileNetworkTask(context, provisioningParams, callback);
    }
}
