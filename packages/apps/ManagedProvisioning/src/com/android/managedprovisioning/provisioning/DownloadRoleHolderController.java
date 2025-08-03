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
import com.android.managedprovisioning.task.VerifyRoleHolderPackageTask;
import com.google.android.setupdesign.util.DeviceHelper;

/**
 * Controller which establishes network connection and downloads the device policy management
 * role holder.
 */
public class DownloadRoleHolderController extends AbstractProvisioningController {

    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final String mRoleHolderPackageName;

    /**
     * Instantiates a new {@link DownloadRoleHolderController} instance and creates the
     * relevant tasks.
     *
     * @return the newly created instance
     */
    public static DownloadRoleHolderController createInstance(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback,
            Utils utils,
            SettingsFacade settingsFacade,
            String roleHolderPackageName) {
        DownloadRoleHolderController controller =
                new DownloadRoleHolderController(context, params, userId, callback,
                        utils, settingsFacade, roleHolderPackageName);
        controller.setUpTasks();
        return controller;
    }

    private DownloadRoleHolderController(Context context,
            ProvisioningParams params, int userId,
            ProvisioningControllerCallback callback,
            Utils utils,
            SettingsFacade settingsFacade,
            String roleHolderPackageName) {
        super(context, params, userId, callback);
        mUtils = requireNonNull(utils);
        mSettingsFacade = requireNonNull(settingsFacade);
        mRoleHolderPackageName = requireNonNull(roleHolderPackageName);
    }

    @Override
    protected void setUpTasks() {
        if (mParams.wifiInfo != null) {
            addTasks(new AddWifiNetworkTask(mContext, mParams, this));
        } else if (mParams.useMobileData) {
            addTasks(new ConnectMobileNetworkTask(mContext, mParams, this));
        }

        addDownloadAndInstallRoleHolderPackageTasks();
    }

    private void addDownloadAndInstallRoleHolderPackageTasks() {
        if (mParams.roleHolderDownloadInfo == null) {
            return;
        }

        DownloadPackageTask downloadTask = new DownloadPackageTask(
                mContext, mParams, mParams.roleHolderDownloadInfo, this);
        addTasks(downloadTask,
                new VerifyRoleHolderPackageTask(
                        downloadTask, mContext, mParams, mParams.roleHolderDownloadInfo, this),
                new InstallPackageTask(downloadTask, mContext, mParams, this,
                        mRoleHolderPackageName));
    }

    @Override
    protected int getErrorTitle() {
        return R.string.cant_set_up_device;
    }

    @Override
    protected String getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        CharSequence deviceName = DeviceHelper.getDeviceName(mContext);
        if (task instanceof AddWifiNetworkTask) {
            return mContext.getString(R.string.error_wifi);
        } else if (task instanceof DownloadPackageTask) {
            switch (errorCode) {
                case DownloadPackageTask.ERROR_DOWNLOAD_FAILED:
                case DownloadPackageTask.ERROR_OTHER:
                    return mContext.getString(R.string.error_role_holder_download_install_failed);
            }
        } else if (task instanceof VerifyAdminPackageTask) {
            switch (errorCode) {
                case VerifyRoleHolderPackageTask.ERROR_HASH_MISMATCH:
                    return mContext.getString(R.string.error_role_holder_hash_mismatch, deviceName);
            }
        } else if (task instanceof InstallPackageTask) {
            switch (errorCode) {
                case InstallPackageTask.ERROR_PACKAGE_INVALID:
                    return mContext.getString(R.string.error_role_holder_package_invalid,
                            deviceName);
                case InstallPackageTask.ERROR_INSTALLATION_FAILED:
                    return mContext.getString(R.string.error_role_holder_download_install_failed);
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
