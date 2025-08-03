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

package com.android.managedprovisioning.task;

import static java.util.Objects.requireNonNull;

import android.annotation.UserIdInt;
import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.stats.devicepolicy.DevicePolicyEnums;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Task to create and provision a managed profile.
 */
public class CreateAndProvisionManagedProfileTask extends AbstractProvisioningTask {
    private final DevicePolicyManager mDpm;
    private final Utils mUtils;
    private int mProfileUserId;

    public CreateAndProvisionManagedProfileTask(
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(
                new Utils(),
                context,
                params,
                callback,
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)));
    }

    @VisibleForTesting
    CreateAndProvisionManagedProfileTask(
            Utils utils,
            Context context,
            ProvisioningParams params,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker) {
        super(context, params, callback, provisioningAnalyticsTracker);
        mDpm = requireNonNull(context.getSystemService(DevicePolicyManager.class));
        mUtils = requireNonNull(utils);
    }

    @Override
    public void run(@UserIdInt int userId) {
        startTaskTimer();
        UserHandle profile;
        ManagedProfileProvisioningParams params;

        try {
            params = buildManagedProfileProvisioningParams(userId);
        } catch (IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failure provisioning managed profile, failed to "
                    + "infer the device admin component name", e);
            error(/* resultCode= */ 0);
            return;
        }

        try {
            profile = mDpm.createAndProvisionManagedProfile(params);
        } catch (ProvisioningException provisioningException) {
            ProvisionLogger.loge("Failure provisioning managed profile.", provisioningException);
            error(/* resultCode= */ 0, provisioningException.getMessage());
            return;
        } catch (Exception e) {
            // Catching all Exceptions to allow Managed Provisioning to handle any failure
            // during provisioning properly and perform any necessary cleanup.
            ProvisionLogger.loge("Failure provisioning managed profile.", e);
            error(/* resultCode= */ 0);
            return;
        }

        if (profile == null) {
            ProvisionLogger.loge("Failure provisioning managed profile, "
                    + "createAndProvisionManagedProfile returned null");
            error(/* resultCode= */ 0);
            return;
        }
        mProfileUserId = profile.getIdentifier();

        stopTaskTimer();
        success();
    }

    private ManagedProfileProvisioningParams buildManagedProfileProvisioningParams(
            @UserIdInt int userId)
            throws IllegalProvisioningArgumentException {
        ComponentName adminComponent =
                mProvisioningParams.inferDeviceAdminComponentName(mUtils, mContext, userId);
        return new ManagedProfileProvisioningParams.Builder(
                adminComponent, adminComponent.getPackageName())
                .setProfileName(mContext.getString(R.string.default_managed_profile_name))
                .setAccountToMigrate(mProvisioningParams.accountToMigrate)
                .setLeaveAllSystemAppsEnabled(
                        mProvisioningParams.leaveAllSystemAppsEnabled)
                .setOrganizationOwnedProvisioning(
                        mProvisioningParams.isOrganizationOwnedProvisioning)
                .setKeepingAccountOnMigration(mProvisioningParams.keepAccountMigrated)
                .setAdminExtras(mProvisioningParams.adminExtrasBundle)
                .build();
    }

    public int getProfileUserId() {
        return mProfileUserId;
    }

    @Override
    protected int getMetricsCategory() {
        return DevicePolicyEnums.PROVISIONING_PROVISION_MANAGED_PROFILE_TASK_MS;
    }
}
