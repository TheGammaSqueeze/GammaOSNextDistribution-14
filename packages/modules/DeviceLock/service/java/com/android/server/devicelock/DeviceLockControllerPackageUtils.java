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

package com.android.server.devicelock;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * Utility class to find properties of the device lock controller package.
 */
public final class DeviceLockControllerPackageUtils {
    private final Context mContext;

    @VisibleForTesting
    static final String SERVICE_ACTION =
            "android.app.action.DEVICE_LOCK_CONTROLLER_SERVICE";

    public DeviceLockControllerPackageUtils(Context context) {
        mContext = context;
    }

    private ServiceInfo mServiceInfo;

    private int mDeviceIdTypeBitmap = -1;

    /**
     * Find the service for device lock controller.
     *
     * @param errorMessage Reason why the service could not be found.
     * @return Service information or null for an error.
     */
    @VisibleForTesting(visibility = PACKAGE)
    @Nullable
    public synchronized ServiceInfo findService(@NonNull StringBuilder errorMessage) {
        errorMessage.setLength(0);

        if (mServiceInfo == null) {
            mServiceInfo = findServiceInternal(errorMessage);
        }

        return mServiceInfo;
    }

    @Nullable
    private ServiceInfo findServiceInternal(@NonNull StringBuilder errorMessage) {
        final Intent intent = new Intent(SERVICE_ACTION);
        final PackageManager pm = mContext.getPackageManager();

        errorMessage.setLength(0);

        final List<ResolveInfo> resolveInfoList = pm.queryIntentServicesAsUser(intent,
                PackageManager.MATCH_SYSTEM_ONLY | PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DISABLED_COMPONENTS, UserHandle.SYSTEM);

        if (resolveInfoList == null || resolveInfoList.isEmpty()) {
            errorMessage.append("Service with " + SERVICE_ACTION + " not found.");

            return null;
        }

        ServiceInfo resultServiceInfo = null;

        for (ResolveInfo resolveInfo : resolveInfoList) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;

            if ((serviceInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                continue;
            }

            if (resultServiceInfo != null) {
                errorMessage.append("Multiple system services handle " + SERVICE_ACTION + ".");

                return null;
            }

            resultServiceInfo = serviceInfo;
        }

        if (!resultServiceInfo.applicationInfo.isPrivilegedApp()) {
            errorMessage.append("Device lock controller must be a privileged app");

            return null;
        }

        return resultServiceInfo;
    }

    /**
     * Get the allowed device id type bitmap or -1 if it cannot be determined.
     */
    @VisibleForTesting
    public synchronized int getDeviceIdTypeBitmap(@NonNull StringBuilder errorMessage) {
        errorMessage.setLength(0);

        if (mDeviceIdTypeBitmap < 0) {
            mDeviceIdTypeBitmap = getDeviceIdTypeBitmapInternal(errorMessage);
        }

        return mDeviceIdTypeBitmap;
    }

    private int getDeviceIdTypeBitmapInternal(@NonNull StringBuilder errorMessage) {
        ServiceInfo serviceInfo = findService(errorMessage);

        if (serviceInfo == null) {
            return -1;
        }

        final String packageName = serviceInfo.packageName;

        final PackageManager pm = mContext.getPackageManager();
        int deviceIdTypeBitmap = -1;
        errorMessage.setLength(0);

        try {
            final Resources resources = pm.getResourcesForApplication(packageName);
            final int resId = resources.getIdentifier("device_id_type_bitmap", "integer",
                    packageName);
            if (resId == 0) {
                errorMessage.append("Cannot get device_id_type_bitmap from: " + packageName);

                return -1;
            }
            deviceIdTypeBitmap = resources.getInteger(resId);
        } catch (PackageManager.NameNotFoundException e) {
            errorMessage.append("Cannot get resources for package: " + packageName);
        }

        return deviceIdTypeBitmap;
    }
}
