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

package com.android.managedprovisioning.task;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Task to install an existing package on a given user.
 */
public class InstallExistingPackageTask extends AbstractProvisioningTask {

    private final String mPackageName;

    private final @UserIdInt int mUserId;

    public InstallExistingPackageTask(
            String packageName,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(packageName, context, params, callback,
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)), UserHandle.USER_NULL);
    }

    public InstallExistingPackageTask(
            String packageName,
            Context context,
            ProvisioningParams params,
            Callback callback,
            @UserIdInt int userId) {
        this(packageName, context, params, callback,
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)), userId);
    }

    @VisibleForTesting
    public InstallExistingPackageTask(
            String packageName,
            Context context,
            ProvisioningParams params,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker) {
        this(packageName, context, params, callback, provisioningAnalyticsTracker,
                UserHandle.USER_NULL);
    }

    @VisibleForTesting
    public InstallExistingPackageTask(
            String packageName,
            Context context,
            ProvisioningParams params,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            @UserIdInt int userId) {
        super(context, params, callback, provisioningAnalyticsTracker);

        mPackageName = checkNotNull(packageName);
        mUserId = userId;
    }

    @VisibleForTesting
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public void run(int userId) {
        if (mUserId != UserHandle.USER_NULL) {
            ProvisionLogger.logd("Overriding user id (from " + userId + " to " + mUserId + ")");
            userId = mUserId;
        }
        ProvisionLogger.logi("Installing existing package " + mPackageName + " on user "
                + userId);
        PackageManager pm = mContext.getPackageManager();
        try {
            int status = pm.installExistingPackageAsUser(mPackageName, userId);
            if (status == PackageManager.INSTALL_SUCCEEDED) {
                success();
            } else {
                ProvisionLogger.loge("Install failed, result code = " + status);
                error(0);
            }
        } catch (PackageManager.NameNotFoundException e) {
            error(0);
        }

    }
}
