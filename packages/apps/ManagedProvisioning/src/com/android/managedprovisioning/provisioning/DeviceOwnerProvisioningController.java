/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import android.content.Context;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.ConnectMobileNetworkTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.ProvisionFullyManagedDeviceTask;
import com.android.managedprovisioning.task.VerifyAdminPackageTask;

/**
 * Controller for Device Owner provisioning.
 */
public class DeviceOwnerProvisioningController extends AbstractProvisioningController {

    private Utils mUtils;

    /**
     * Instantiates a new {@link DeviceOwnerProvisioningController} instance and creates the
     * relevant tasks.
     *
     * @return the newly created instance
     */
    static DeviceOwnerProvisioningController createInstance(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback,
            Utils utils) {
        DeviceOwnerProvisioningController controller =
                new DeviceOwnerProvisioningController(context, params, userId, callback);
        controller.mUtils = utils;
        controller.setUpTasks();
        return controller;
    }

    private DeviceOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        super(context, params, userId, callback);
    }

    protected void setUpTasks() {
        // If the admin-integrated flow preconditions aren't met, then the admin app was not
        // installed as part of the admin-integrated flow preparation.
        // We must install the admin app here instead.
        if (!mUtils.checkAdminIntegratedFlowPreconditions(mParams)) {
            if (mParams.wifiInfo != null) {
                addTasks(new AddWifiNetworkTask(mContext, mParams, this));
            } else if (mParams.useMobileData) {
                addTasks(new ConnectMobileNetworkTask(mContext, mParams, this));
            }

            addDownloadAndInstallDeviceOwnerPackageTasks();
        }
        addTasks(new ProvisionFullyManagedDeviceTask(mContext, mParams, this));
    }

    @Override protected int getErrorTitle() {
        return R.string.cant_set_up_device;
    }

    @Override
    protected String getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        if (task instanceof AddWifiNetworkTask) {
            return mContext.getString(R.string.error_wifi);
        } else if (task instanceof DownloadPackageTask) {
            switch (errorCode) {
                case DownloadPackageTask.ERROR_DOWNLOAD_FAILED:
                    return mContext.getString(R.string.error_download_failed);
                case DownloadPackageTask.ERROR_OTHER:
                    return mContext.getString(R.string.cant_set_up_device);
            }
        } else if (task instanceof VerifyAdminPackageTask) {
            switch (errorCode) {
                case VerifyAdminPackageTask.ERROR_HASH_MISMATCH:
                    return mContext.getString(R.string.error_hash_mismatch);
                case VerifyAdminPackageTask.ERROR_DEVICE_ADMIN_MISSING:
                    return mContext.getString(R.string.error_package_invalid);
            }
        } else if (task instanceof InstallPackageTask) {
            switch (errorCode) {
                case InstallPackageTask.ERROR_PACKAGE_INVALID:
                    return mContext.getString(R.string.error_package_invalid);
                case InstallPackageTask.ERROR_INSTALLATION_FAILED:
                    return mContext.getString(R.string.error_installation_failed);
            }
        } else if (task instanceof ProvisionFullyManagedDeviceTask) {
            switch (errorCode) {
                case ProvisionFullyManagedDeviceTask.ERROR_FULLY_MANAGED_MODE_UNSUPPORTED_IN_HEADLESS:
                    return mContext.getString(R.string.fully_managed_device_unsupported_DPC_in_headless_mode_subheader);
            }
        }

        return mContext.getString(R.string.cant_set_up_device);
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        return !(task instanceof AddWifiNetworkTask);
    }
}
