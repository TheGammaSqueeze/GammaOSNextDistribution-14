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

package com.android.car;

import static android.car.builtin.content.pm.PackageManagerHelper.PROPERTY_CAR_SERVICE_PACKAGE_NAME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Context for updatable package */
public final class UpdatablePackageContext extends ContextWrapper {
    private static final String TAG = UpdatablePackageContext.class.getSimpleName();

    // This is the package context of the com.android.car.updatable
    private final Context mPackageContext;

    /** Create context for updatable package */
    public static UpdatablePackageContext create(Context baseContext) {
        Context packageContext;
        try {
            PackageInfo info = findUpdatableServicePackage(baseContext);
            if (info == null || info.applicationInfo == null || !(info.applicationInfo.isSystemApp()
                    || info.applicationInfo.isUpdatedSystemApp())) {
                throw new IllegalStateException(
                        "Updated car service package is not usable:" + ((info == null)
                                ? "do not exist" : info.applicationInfo));
            }

            // Enable correct RRO package
            enableRROForCarServiceUpdatable(baseContext);

            // CONTEXT_IGNORE_SECURITY: UID is different but ok as the package is trustable system
            // app
            packageContext = baseContext.createPackageContext(info.packageName,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load updatable package code", e);
        }

        return new UpdatablePackageContext(baseContext, packageContext);
    }

    @Nullable
    private static PackageInfo findUpdatableServicePackage(Context baseContext) {
        PackageInfo info = null;
        String packageName = SystemProperties.get(
                PROPERTY_CAR_SERVICE_PACKAGE_NAME, /*def=*/null);
        if (packageName == null) {
            throw new IllegalStateException(
                    PROPERTY_CAR_SERVICE_PACKAGE_NAME + " property not defined");
        }
        try {
            info = baseContext.getPackageManager().getPackageInfo(packageName, /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            // Just log and move over. Caller will throw exception instead.
            Slogf.e(TAG, e, "Cannot find updatable car service package:%s", packageName);
        }
        return info;
    }

    // TODO(b/198516172): Add detailed description for the priority of RROs, who will replace whom.
    private static void enableRROForCarServiceUpdatable(Context baseContext) {
        List<String> packages = getEligibleRROPackages(baseContext);
        if (packages.isEmpty()) {
            Slogf.d(TAG, "No eligible RRO package to enable.");
            return;
        }

        OverlayManager manager = baseContext.getSystemService(OverlayManager.class);
        UserHandle user = baseContext.getUser();
        for (int i = 0; i < packages.size(); i++) {
            // This class is called for each user, so need to enable RRO for system and current user
            // separately.
            String rroPackageName = packages.get(i);
            try {
                manager.setEnabled(rroPackageName, /* enable= */true, user);
                Slogf.d(TAG, "RRO package %s is enabled for User %s", rroPackageName, user);
            } catch (Exception e) {
                Slogf.w(TAG, e, "RRO package %s is NOT enabled for User %s", rroPackageName, user);
            }
        }
    }

    @NonNull
    private static List<String> getEligibleRROPackages(Context baseContext) {
        List<String> eligiblePackages = new ArrayList<>();

        String packageNames = SystemProperties.get(
                PackageManagerHelper.PROPERTY_CAR_SERVICE_OVERLAY_PACKAGES,
                /* default= */ null);
        if (TextUtils.isEmpty(packageNames)) {
            // read only property not defined. No need to dynamically overlay resources.
            Slogf.d(TAG, " %s is not set. No need to dynamically overlay resources.",
                    PackageManagerHelper.PROPERTY_CAR_SERVICE_OVERLAY_PACKAGES);
            return eligiblePackages;
        }

        Set<String> installedRROPackages = getInstalledRROPackages(baseContext);

        if (installedRROPackages.isEmpty()) {
            return eligiblePackages;
        }

        String[] packages = packageNames.split(";");
        String rroPackageName;
        for (int i = 0; i < packages.length; i++) {
            rroPackageName = packages[i].trim();

            if (rroPackageName.isEmpty()) {
                continue;
            }

            if (!installedRROPackages.contains(rroPackageName)) {
                Slogf.d(TAG, "RRO package %s is not installed.", rroPackageName);
                continue;
            }

            // Check that package is part of the original image. A third party RRO
            // should not be enabled using this.
            try {
                PackageInfo info = baseContext.getPackageManager().getPackageInfo(
                        rroPackageName, 0);
                // TODO(b/198516172): Move following logic to separate class and test it.
                if (info == null || info.applicationInfo == null
                        || !(PackageManagerHelper.isSystemApp(info.applicationInfo)
                                || PackageManagerHelper.isUpdatedSystemApp(info.applicationInfo)
                                || PackageManagerHelper.isOemApp(info.applicationInfo)
                                || PackageManagerHelper.isOdmApp(info.applicationInfo)
                                || PackageManagerHelper.isVendorApp(info.applicationInfo)
                                || PackageManagerHelper.isProductApp(info.applicationInfo)
                                || PackageManagerHelper.isSystemExtApp(info.applicationInfo))) {
                    Slogf.d(TAG, "%s is not usable: %s", rroPackageName, ((info == null)
                            ? "package do not exist"
                            : info.applicationInfo));
                    continue;
                }
            } catch (Exception e) {
                Slogf.w(TAG, e, "couldn't find package: %s", rroPackageName);
                continue;
            }

            // Add RRO package to the list.
            Slogf.d(TAG, "RRO package %s is eligible for enabling.", rroPackageName);
            eligiblePackages.add(rroPackageName);
        }
        return eligiblePackages;
    }

    @NonNull
    private static Set<String> getInstalledRROPackages(Context baseContext) {
        Set<String> installedOverlayPackages = new HashSet<>();
        PackageInfo packageInfo = findUpdatableServicePackage(baseContext);
        if (packageInfo == null) {
            return installedOverlayPackages;
        }
        String updatablePackageName = packageInfo.packageName;

        OverlayManager manager = baseContext.getSystemService(OverlayManager.class);
        UserHandle user = baseContext.getUser();

        List<OverlayInfo> installedOverlays = manager.getOverlayInfosForTarget(updatablePackageName,
                user);

        if (installedOverlays == null || installedOverlays.isEmpty()) {
            return installedOverlayPackages;
        }

        for (int i = 0; i < installedOverlays.size(); i++) {
            OverlayInfo overlayInfo = installedOverlays.get(i);
            installedOverlayPackages.add(overlayInfo.getPackageName());
        }

        Slogf.d(TAG, "Total RROs packages for target package %s are %d.", updatablePackageName,
                installedOverlayPackages.size());

        return installedOverlayPackages;
    }

    private UpdatablePackageContext(Context baseContext, Context packageContext) {
        super(baseContext);
        mPackageContext = packageContext;
    }

    @Override
    public AssetManager getAssets() {
        return mPackageContext.getAssets();
    }

    @Override
    public Resources getResources() {
        return mPackageContext.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        // This context cannot load code from builtin any more.
        return mPackageContext.getClassLoader();
    }
}
