/*
 * Copyright 2019, The Android Open Source Project
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

import static java.util.Objects.requireNonNull;

import android.content.Context;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.ConnectMobileNetworkTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.VerifyAdminPackageTask;

/**
 * Controller for preparing admin integrated flow. This includes connecting to wi-fi and
 * mobile network, downloading, verifying and installing the admin app.
 */
public class AdminIntegratedFlowPrepareController extends AbstractProvisioningController {

    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;

    /**
     * Instantiates a new {@link AdminIntegratedFlowPrepareController} instance and creates the
     * relevant tasks.
     *
     * @return the newly created instance
     */
    static AdminIntegratedFlowPrepareController createInstance(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback,
            Utils utils,
            SettingsFacade settingsFacade) {
        AdminIntegratedFlowPrepareController controller =
                new AdminIntegratedFlowPrepareController(
                        context, params, userId, callback, utils, settingsFacade);
        controller.setUpTasks();
        return controller;
    }

    private AdminIntegratedFlowPrepareController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback,
            Utils utils,
            SettingsFacade settingsFacade) {
        super(context, params, userId, callback);
        mUtils = requireNonNull(utils);
        mSettingsFacade = requireNonNull(settingsFacade);
    }

    @Override
    protected void setUpTasks() {
        if (mParams.wifiInfo != null) {
            addTasks(new AddWifiNetworkTask(mContext, mParams, this));
        } else if (mParams.useMobileData) {
            addTasks(new ConnectMobileNetworkTask(mContext, mParams, this));
        }

        addDownloadAndInstallDeviceOwnerPackageTasks();
    }

    @Override
    protected int getErrorTitle() {
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
        }

        return mContext.getString(R.string.cant_set_up_device);
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        return !mSettingsFacade.isDeviceProvisioned(mContext)
                && mUtils.isOrganizationOwnedAllowed(mParams)
                && !(task instanceof AddWifiNetworkTask);
    }
}
