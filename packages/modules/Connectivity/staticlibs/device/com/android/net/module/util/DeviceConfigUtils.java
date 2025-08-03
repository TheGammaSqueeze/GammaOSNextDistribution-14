/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.net.module.util;

import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;
import static android.provider.DeviceConfig.NAMESPACE_TETHERING;

import static com.android.net.module.util.FeatureVersions.CONNECTIVITY_MODULE_ID;
import static com.android.net.module.util.FeatureVersions.MODULE_MASK;
import static com.android.net.module.util.FeatureVersions.NETWORK_STACK_MODULE_ID;
import static com.android.net.module.util.FeatureVersions.VERSION_MASK;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.annotation.BoolRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Utilities for modules to query {@link DeviceConfig} and flags.
 */
public final class DeviceConfigUtils {
    private DeviceConfigUtils() {}

    private static final String TAG = DeviceConfigUtils.class.getSimpleName();
    /**
     * DO NOT MODIFY: this may be used by multiple modules that will not see the updated value
     * until they are recompiled, so modifying this constant means that different modules may
     * be referencing a different tethering module variant, or having a stale reference.
     */
    public static final String TETHERING_MODULE_NAME = "com.android.tethering";

    @VisibleForTesting
    public static final String RESOURCES_APK_INTENT =
            "com.android.server.connectivity.intent.action.SERVICE_CONNECTIVITY_RESOURCES_APK";
    private static final String CONNECTIVITY_RES_PKG_DIR = "/apex/" + TETHERING_MODULE_NAME + "/";

    @VisibleForTesting
    public static final long DEFAULT_PACKAGE_VERSION = 1000;

    @VisibleForTesting
    public static void resetPackageVersionCacheForTest() {
        sPackageVersion = -1;
        sModuleVersion = -1;
        sNetworkStackModuleVersion = -1;
    }

    private static final int FORCE_ENABLE_FEATURE_FLAG_VALUE = 1;
    private static final int FORCE_DISABLE_FEATURE_FLAG_VALUE = -1;

    private static volatile long sPackageVersion = -1;
    private static long getPackageVersion(@NonNull final Context context) {
        // sPackageVersion may be set by another thread just after this check, but querying the
        // package version several times on rare occasions is fine.
        if (sPackageVersion >= 0) {
            return sPackageVersion;
        }
        try {
            final long version = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).getLongVersionCode();
            sPackageVersion = version;
            return version;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get package info: " + e);
            return DEFAULT_PACKAGE_VERSION;
        }
    }

    /**
     * Look up the value of a property for a particular namespace from {@link DeviceConfig}.
     * @param namespace The namespace containing the property to look up.
     * @param name The name of the property to look up.
     * @param defaultValue The value to return if the property does not exist or has no valid value.
     * @return the corresponding value, or defaultValue if none exists.
     */
    @Nullable
    public static String getDeviceConfigProperty(@NonNull String namespace, @NonNull String name,
            @Nullable String defaultValue) {
        String value = DeviceConfig.getProperty(namespace, name);
        return value != null ? value : defaultValue;
    }

    /**
     * Look up the value of a property for a particular namespace from {@link DeviceConfig}.
     * @param namespace The namespace containing the property to look up.
     * @param name The name of the property to look up.
     * @param defaultValue The value to return if the property does not exist or its value is null.
     * @return the corresponding value, or defaultValue if none exists.
     */
    public static int getDeviceConfigPropertyInt(@NonNull String namespace, @NonNull String name,
            int defaultValue) {
        String value = getDeviceConfigProperty(namespace, name, null /* defaultValue */);
        try {
            return (value != null) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Look up the value of a property for a particular namespace from {@link DeviceConfig}.
     *
     * Flags like timeouts should use this method and set an appropriate min/max range: if invalid
     * values like "0" or "1" are pushed to devices, everything would timeout. The min/max range
     * protects against this kind of breakage.
     * @param namespace The namespace containing the property to look up.
     * @param name The name of the property to look up.
     * @param minimumValue The minimum value of a property.
     * @param maximumValue The maximum value of a property.
     * @param defaultValue The value to return if the property does not exist or its value is null.
     * @return the corresponding value, or defaultValue if none exists or the fetched value is
     *         not in the provided range.
     */
    public static int getDeviceConfigPropertyInt(@NonNull String namespace, @NonNull String name,
            int minimumValue, int maximumValue, int defaultValue) {
        int value = getDeviceConfigPropertyInt(namespace, name, defaultValue);
        if (value < minimumValue || value > maximumValue) return defaultValue;
        return value;
    }

    /**
     * Look up the value of a property for a particular namespace from {@link DeviceConfig}.
     * @param namespace The namespace containing the property to look up.
     * @param name The name of the property to look up.
     * @param defaultValue The value to return if the property does not exist or its value is null.
     * @return the corresponding value, or defaultValue if none exists.
     */
    public static boolean getDeviceConfigPropertyBoolean(@NonNull String namespace,
            @NonNull String name, boolean defaultValue) {
        String value = getDeviceConfigProperty(namespace, name, null /* defaultValue */);
        return (value != null) ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * Check whether or not one specific experimental feature for a particular namespace from
     * {@link DeviceConfig} is enabled by comparing module package version
     * with current version of property. If this property version is valid, the corresponding
     * experimental feature would be enabled, otherwise disabled.
     *
     * This is useful to ensure that if a module install is rolled back, flags are not left fully
     * rolled out on a version where they have not been well tested.
     *
     * If the feature is disabled by default and enabled by flag push, this method should be used.
     * If the feature is enabled by default and disabled by flag push (kill switch),
     * {@link #isNetworkStackFeatureNotChickenedOut(Context, String)} should be used.
     *
     * @param context The global context information about an app environment.
     * @param name The name of the property to look up.
     * @return true if this feature is enabled, or false if disabled.
     */
    public static boolean isNetworkStackFeatureEnabled(@NonNull Context context,
            @NonNull String name) {
        return isFeatureEnabled(NAMESPACE_CONNECTIVITY, name, false /* defaultEnabled */,
                () -> getPackageVersion(context));
    }

    /**
     * Check whether or not one specific experimental feature for a particular namespace from
     * {@link DeviceConfig} is enabled by comparing module package version
     * with current version of property. If this property version is valid, the corresponding
     * experimental feature would be enabled, otherwise disabled.
     *
     * This is useful to ensure that if a module install is rolled back, flags are not left fully
     * rolled out on a version where they have not been well tested.
     *
     * If the feature is disabled by default and enabled by flag push, this method should be used.
     * If the feature is enabled by default and disabled by flag push (kill switch),
     * {@link #isTetheringFeatureNotChickenedOut(Context, String)} should be used.
     *
     * @param context The global context information about an app environment.
     * @param name The name of the property to look up.
     * @return true if this feature is enabled, or false if disabled.
     */
    public static boolean isTetheringFeatureEnabled(@NonNull Context context,
            @NonNull String name) {
        return isFeatureEnabled(NAMESPACE_TETHERING, name, false /* defaultEnabled */,
                () -> getTetheringModuleVersion(context));
    }

    private static boolean isFeatureEnabled(@NonNull String namespace,
            String name, boolean defaultEnabled, Supplier<Long> packageVersionSupplier) {
        final int flagValue = getDeviceConfigPropertyInt(namespace, name, 0 /* default value */);
        switch (flagValue) {
            case 0:
                return defaultEnabled;
            case FORCE_DISABLE_FEATURE_FLAG_VALUE:
                return false;
            case FORCE_ENABLE_FEATURE_FLAG_VALUE:
                return true;
            default:
                final long packageVersion = packageVersionSupplier.get();
                return packageVersion >= (long) flagValue;
        }
    }

    // Guess the tethering module name based on the package prefix of the connectivity resources
    // Take the resource package name, cut it before "connectivity" and append "tethering".
    // Then resolve that package version number with packageManager.
    // If that fails retry by appending "go.tethering" instead
    private static long resolveTetheringModuleVersion(@NonNull Context context)
            throws PackageManager.NameNotFoundException {
        final String pkgPrefix = resolvePkgPrefix(context);
        final PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getPackageInfo(pkgPrefix + "tethering",
                    PackageManager.MATCH_APEX).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Device is using go modules");
            // fall through
        }

        return packageManager.getPackageInfo(pkgPrefix + "go.tethering",
                PackageManager.MATCH_APEX).getLongVersionCode();
    }

    private static String resolvePkgPrefix(Context context) {
        final String connResourcesPackage = getConnectivityResourcesPackageName(context);
        final int pkgPrefixLen = connResourcesPackage.indexOf("connectivity");
        if (pkgPrefixLen < 0) {
            throw new IllegalStateException(
                    "Invalid connectivity resources package: " + connResourcesPackage);
        }

        return connResourcesPackage.substring(0, pkgPrefixLen);
    }

    private static volatile long sModuleVersion = -1;
    private static long getTetheringModuleVersion(@NonNull Context context) {
        if (sModuleVersion >= 0) return sModuleVersion;

        try {
            sModuleVersion = resolveTetheringModuleVersion(context);
        } catch (PackageManager.NameNotFoundException e) {
            // It's expected to fail tethering module version resolution on the devices with
            // flattened apex
            Log.e(TAG, "Failed to resolve tethering module version: " + e);
            return DEFAULT_PACKAGE_VERSION;
        }
        return sModuleVersion;
    }

    private static volatile long sNetworkStackModuleVersion = -1;

    /**
     * Get networkstack module version.
     */
    @VisibleForTesting
    static long getNetworkStackModuleVersion(@NonNull Context context) {
        if (sNetworkStackModuleVersion >= 0) return sNetworkStackModuleVersion;

        try {
            sNetworkStackModuleVersion = resolveNetworkStackModuleVersion(context);
        } catch (PackageManager.NameNotFoundException e) {
            Log.wtf(TAG, "Failed to resolve networkstack module version: " + e);
            return DEFAULT_PACKAGE_VERSION;
        }
        return sNetworkStackModuleVersion;
    }

    private static long resolveNetworkStackModuleVersion(@NonNull Context context)
            throws PackageManager.NameNotFoundException {
        // TODO(b/293975546): Strictly speaking this is the prefix for connectivity and not
        //  network stack. In practice, it's the same. Read the prefix from network stack instead.
        final String pkgPrefix = resolvePkgPrefix(context);
        final PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getPackageInfo(pkgPrefix + "networkstack",
                    PackageManager.MATCH_SYSTEM_ONLY).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Device is using go or non-mainline modules");
            // fall through
        }

        return packageManager.getPackageInfo(pkgPrefix + "go.networkstack",
                PackageManager.MATCH_ALL).getLongVersionCode();
    }

    /**
     * Check whether one specific feature is supported from the feature Id. The feature Id is
     * composed by a module package Id and version Id from {@link FeatureVersions}.
     *
     * This is useful when a feature required minimal module version supported and cannot function
     * well with a standalone newer module.
     * @param context The global context information about an app environment.
     * @param featureId The feature id that contains required module id and minimal module version
     * @return true if this feature is supported, or false if not supported.
     **/
    public static boolean isFeatureSupported(@NonNull Context context, long featureId) {
        final long moduleVersion;
        final long moduleId = featureId & MODULE_MASK;
        if (moduleId == CONNECTIVITY_MODULE_ID) {
            moduleVersion = getTetheringModuleVersion(context);
        } else if (moduleId == NETWORK_STACK_MODULE_ID) {
            moduleVersion = getNetworkStackModuleVersion(context);
        } else {
            throw new IllegalArgumentException("Unknown module " + moduleId);
        }
        // Support by default if no module version is available.
        return moduleVersion == DEFAULT_PACKAGE_VERSION
                || moduleVersion >= (featureId & VERSION_MASK);
    }

    /**
     * Check whether one specific experimental feature in Tethering module from {@link DeviceConfig}
     * is not disabled.
     * If the feature is enabled by default and disabled by flag push (kill switch), this method
     * should be used.
     * If the feature is disabled by default and enabled by flag push,
     * {@link #isTetheringFeatureEnabled(Context, String)} should be used.
     *
     * @param context The global context information about an app environment.
     * @param name The name of the property in tethering module to look up.
     * @return true if this feature is enabled, or false if disabled.
     */
    public static boolean isTetheringFeatureNotChickenedOut(@NonNull Context context, String name) {
        return isFeatureEnabled(NAMESPACE_TETHERING, name, true /* defaultEnabled */,
                () -> getTetheringModuleVersion(context));
    }

    /**
     * Check whether one specific experimental feature in NetworkStack module from
     * {@link DeviceConfig} is not disabled.
     * If the feature is enabled by default and disabled by flag push (kill switch), this method
     * should be used.
     * If the feature is disabled by default and enabled by flag push,
     * {@link #isNetworkStackFeatureEnabled(Context, String)} should be used.
     *
     * @param context The global context information about an app environment.
     * @param name The name of the property in NetworkStack module to look up.
     * @return true if this feature is enabled, or false if disabled.
     */
    public static boolean isNetworkStackFeatureNotChickenedOut(
            @NonNull Context context, String name) {
        return isFeatureEnabled(NAMESPACE_CONNECTIVITY, name, true /* defaultEnabled */,
                () -> getPackageVersion(context));
    }

    /**
     * Gets boolean config from resources.
     */
    public static boolean getResBooleanConfig(@NonNull final Context context,
            @BoolRes int configResource, final boolean defaultValue) {
        final Resources res = context.getResources();
        try {
            return res.getBoolean(configResource);
        } catch (Resources.NotFoundException e) {
            return defaultValue;
        }
    }

    /**
     * Gets int config from resources.
     */
    public static int getResIntegerConfig(@NonNull final Context context,
            @BoolRes int configResource, final int defaultValue) {
        final Resources res = context.getResources();
        try {
            return res.getInteger(configResource);
        } catch (Resources.NotFoundException e) {
            return defaultValue;
        }
    }

    /**
     * Get the package name of the ServiceConnectivityResources package, used to provide resources
     * for service-connectivity.
     */
    @NonNull
    public static String getConnectivityResourcesPackageName(@NonNull Context context) {
        final List<ResolveInfo> pkgs = new ArrayList<>(context.getPackageManager()
                .queryIntentActivities(new Intent(RESOURCES_APK_INTENT), MATCH_SYSTEM_ONLY));
        pkgs.removeIf(pkg -> !pkg.activityInfo.applicationInfo.sourceDir.startsWith(
                CONNECTIVITY_RES_PKG_DIR));
        if (pkgs.size() > 1) {
            Log.wtf(TAG, "More than one connectivity resources package found: " + pkgs);
        }
        if (pkgs.isEmpty()) {
            throw new IllegalStateException("No connectivity resource package found");
        }

        return pkgs.get(0).activityInfo.applicationInfo.packageName;
    }
}
