/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.provisioning;

import android.content.Context;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.DeviceOwnerInitializeProvisioningTask;
import com.android.managedprovisioning.task.DisallowAddUserTask;
import com.android.managedprovisioning.task.SetDeviceOwnerPolicyTask;
import com.google.android.setupdesign.util.DeviceHelper;

/**
 * Controller for financed device provisioning.
 */
// TODO(b/178711424): move any business logic from here into the framework.
public final class FinancedDeviceProvisioningController extends AbstractProvisioningController  {

    /**
     * Instantiates a new {@link FinancedDeviceProvisioningController} instance and creates the
     * relevant tasks.
     *
     * @return the newly created instance
     */
    static FinancedDeviceProvisioningController createInstance(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        FinancedDeviceProvisioningController controller =
                new FinancedDeviceProvisioningController(context, params, userId, callback);
        controller.setUpTasks();
        return controller;
    }

    private FinancedDeviceProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        super(context, params, userId, callback);
    }

    @Override
    protected void setUpTasks() {
        addTasks(new DeviceOwnerInitializeProvisioningTask(mContext, mParams, this));

        addDownloadAndInstallDeviceOwnerPackageTasks();

        addTasks(
                new SetDeviceOwnerPolicyTask(mContext, mParams, this),
                new DisallowAddUserTask(mContext, mParams, this));
    }

    @Override
    protected int getErrorTitle() {
        return R.string.something_went_wrong;
    }

    @Override
    protected String getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        CharSequence deviceName = DeviceHelper.getDeviceName(mContext);
        return mContext.getString(R.string.reset_device, deviceName);
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        return true;
    }
}
