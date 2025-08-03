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

import static com.android.internal.util.Preconditions.checkNotNull;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.io.File;

/**
 * Verifies the management app apk downloaded previously in {@link DownloadPackageTask}.
 *
 * <p>The first check verifies that a {@link android.app.admin.DeviceAdminReceiver} is present in
 * the apk and that it corresponds to the one provided via
 * {@link ProvisioningParams#deviceAdminComponentName}.</p>
 *
 * <p>The second check verifies that the package or signature checksum matches the ones given via
 * {@link PackageDownloadInfo#packageChecksum} or {@link PackageDownloadInfo#signatureChecksum}
 * respectively. The package checksum takes priority in case both are present.</p>
 */
public class VerifyAdminPackageTask extends AbstractProvisioningTask {
    public static final int ERROR_HASH_MISMATCH = 0;
    public static final int ERROR_DEVICE_ADMIN_MISSING = 1;

    private final Utils mUtils;
    private final PackageLocationProvider mDownloadLocationProvider;
    private final PackageManager mPackageManager;
    private final PackageDownloadInfo mPackageDownloadInfo;
    private final ChecksumUtils mChecksumUtils;

    public VerifyAdminPackageTask(
            PackageLocationProvider downloadLocationProvider,
            Context context,
            ProvisioningParams params,
            PackageDownloadInfo packageDownloadInfo,
            Callback callback) {
        this(new Utils(), downloadLocationProvider, context, params, packageDownloadInfo, callback,
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)),
                new ChecksumUtils(new Utils()));
    }

    @VisibleForTesting
    VerifyAdminPackageTask(
            Utils utils,
            PackageLocationProvider downloadLocationProvider,
            Context context,
            ProvisioningParams params,
            PackageDownloadInfo packageDownloadInfo,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker,
            ChecksumUtils checksumUtils) {
        super(context, params, callback, provisioningAnalyticsTracker);

        mUtils = checkNotNull(utils);
        mDownloadLocationProvider = checkNotNull(downloadLocationProvider);
        mPackageManager = mContext.getPackageManager();
        mPackageDownloadInfo = checkNotNull(packageDownloadInfo);
        mChecksumUtils = requireNonNull(checksumUtils);
    }

    @Override
    public void run(int userId) {
        final File packageLocation = mDownloadLocationProvider.getPackageLocation();
        if (packageLocation == null) {
            ProvisionLogger.logw("VerifyPackageTask invoked, but package is null");
            success();
            return;
        }
        ProvisionLogger.logi("Verifying package from location " + packageLocation.getAbsolutePath()
                + " for user " + userId);

        PackageInfo packageInfo = mPackageManager.getPackageArchiveInfo(
                packageLocation.getAbsolutePath(),
                PackageManager.GET_SIGNATURES | PackageManager.GET_RECEIVERS);
        String packageName = mProvisioningParams.inferDeviceAdminPackageName();
        // Device admin package name can't be null
        if (packageInfo == null || packageName == null) {
            ProvisionLogger.loge("Device admin package info or name is null");
            error(ERROR_DEVICE_ADMIN_MISSING);
            return;
        }

        if (mUtils.findDeviceAdminInPackageInfo(packageName,
                mProvisioningParams.deviceAdminComponentName, packageInfo) == null) {
            error(ERROR_DEVICE_ADMIN_MISSING);
            return;
        }

        if (mPackageDownloadInfo.packageChecksum.length > 0) {
            if (!mChecksumUtils.doesPackageHashMatch(
                    packageLocation.getAbsolutePath(), mPackageDownloadInfo.packageChecksum)) {
                error(ERROR_HASH_MISMATCH);
                return;
            }
        } else {
            if (!mChecksumUtils.doesASignatureHashMatch(
                    packageInfo, mPackageDownloadInfo.signatureChecksum)) {
                error(ERROR_HASH_MISMATCH);
                return;
            }
        }

        success();
    }
}
