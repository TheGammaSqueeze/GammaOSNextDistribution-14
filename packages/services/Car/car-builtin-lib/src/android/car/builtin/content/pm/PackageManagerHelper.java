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

package android.car.builtin.content.pm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.text.TextUtils;

/**
 * Helper class for {@code PackageManager}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class PackageManagerHelper {

    /**
     * Read-only property to define the package name of car service updatable
     * package.
     *
     * <p>This property must be defined and will be set to {@code "com.android.car.updatable"} for
     * car service created from AOSP build. It can be set to the different package name depending on
     * who is signing the car framework apex module.
     */
    public static final String PROPERTY_CAR_SERVICE_PACKAGE_NAME =
            "ro.android.car.carservice.package";

    /**
     * Read only property which contains semicolon (;) separated list of RRO packages.
     *
     * <p>
     * RRO packages would be enabled if they are overlaying {@code CarServiceUpdatable}.
     * {@code CarServiceUpdatable} can have different package names and this property may include
     * all RROs to cover different {@code CarServiceUpdatable} package names but only those
     * overriding the current {@code CarServiceUpdatable} package name will be selected.
     */
    public static final String PROPERTY_CAR_SERVICE_OVERLAY_PACKAGES =
            "ro.android.car.carservice.overlay.packages";

    private PackageManagerHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }

    /**
     * Gets the name of the {@code SystemUI} package.
     * @param context
     * @return
     */
    @NonNull
    public static String getSystemUiPackageName(@NonNull Context context) {
        // TODO(157082995): This information can be taken from
        // PackageManageInternalImpl.getSystemUiServiceComponent()
        String flattenName = context.getResources()
                .getString(com.android.internal.R.string.config_systemUIServiceComponent);
        if (TextUtils.isEmpty(flattenName)) {
            throw new IllegalStateException("No "
                    + "com.android.internal.R.string.config_systemUIServiceComponent resource");
        }
        try {
            ComponentName componentName = ComponentName.unflattenFromString(flattenName);
            return componentName.getPackageName();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid component name defined by "
                    + "com.android.internal.R.string.config_systemUIServiceComponent resource: "
                    + flattenName, e);
        }
    }

    /** Check {@link PackageManager#getPackageInfoAsUser(String, int, int)}. */
    public static PackageInfo getPackageInfoAsUser(@NonNull PackageManager pm,
            @NonNull String packageName, int packageInfoFlags,
            @UserIdInt int userId) throws PackageManager.NameNotFoundException {
        return pm.getPackageInfoAsUser(packageName, packageInfoFlags, userId);
    }

    /** Check {@link PackageManager#getPackageUidAsUser(String, int)}. */
    public static int getPackageUidAsUser(@NonNull PackageManager pm, @NonNull String packageName,
            @UserIdInt int userId) throws PackageManager.NameNotFoundException {
        return pm.getPackageUidAsUser(packageName, userId);
    }

    /** Check {@link PackageManager#getNamesForUids(int[])}. */
    @Nullable
    public static String[] getNamesForUids(@NonNull PackageManager pm, int[] uids) {
        return pm.getNamesForUids(uids);
    }

    /** Check {@link PackageManager#getApplicationEnabledSetting(String)}. */
    public static int getApplicationEnabledSettingForUser(@NonNull String packageName,
            @UserIdInt int userId) throws RemoteException {
        IPackageManager pm = ActivityThread.getPackageManager();
        return pm.getApplicationEnabledSetting(packageName, userId);
    }

    /** Check {@link PackageManager#setApplicationEnabledSetting(String, int, int)}. */
    public static void setApplicationEnabledSettingForUser(@NonNull String packageName,
            @PackageManager.EnabledState int newState, @PackageManager.EnabledFlags int flags,
            @UserIdInt int userId, @NonNull String callingPackage) throws RemoteException {
        IPackageManager pm = ActivityThread.getPackageManager();
        pm.setApplicationEnabledSetting(packageName, newState, flags, userId, callingPackage);
    }

    /** Tells if the passed app is OEM app or not. */
    public static boolean isOemApp(@NonNull ApplicationInfo appInfo) {
        return (appInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_OEM) != 0;
    }

    /** Tells if the passed app is ODM app or not. */
    public static boolean isOdmApp(@NonNull ApplicationInfo appInfo) {
        return (appInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_ODM) != 0;
    }

    /** Tells if the passed app is vendor app or not. */
    public static boolean isVendorApp(@NonNull ApplicationInfo appInfo) {
        return (appInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_VENDOR) != 0;
    }

    /** Tells if the passed app is system app or not. */
    public static boolean isSystemApp(@NonNull ApplicationInfo appInfo) {
        return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    /** Tells if the passed app is updated system app or not. */
    public static boolean isUpdatedSystemApp(@NonNull ApplicationInfo appInfo) {
        return (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    /** Tells if the passed app is product app or not. */
    public static boolean isProductApp(@NonNull ApplicationInfo appInfo) {
        return (appInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRODUCT) != 0;
    }

    /** Tells if the passed app is system ext vendor app or not. */
    public static boolean isSystemExtApp(@NonNull ApplicationInfo appInfo) {
        return (appInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_SYSTEM_EXT) != 0;
    }

    /** Check {@link ComponentInfo#getComponentName()}. */
    public static ComponentName getComponentName(ComponentInfo info) {
        return info.getComponentName();
    }

    /** Check PackageManagerInternal#getSystemUiServiceComponent(). */
    @NonNull
    public static ComponentName getSystemUiServiceComponent(@NonNull Context context) {
        String flattenName = context.getResources()
                .getString(com.android.internal.R.string.config_systemUIServiceComponent);
        if (TextUtils.isEmpty(flattenName)) {
            throw new IllegalStateException("No "
                    + "com.android.internal.R.string.config_systemUIServiceComponent resource");
        }
        return ComponentName.unflattenFromString(flattenName);
    }

    /** Check {@link ActivityManager#forceStopPackageAsUser}. */
    public static void forceStopPackageAsUser(@NonNull Context context, @NonNull String packageName,
            @UserIdInt int userId) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        am.forceStopPackageAsUser(packageName, userId);
    }

    /** Check {@link ActivityManager#forceStopPackageAsUserEvenWhenStopping}. */
    public static void forceStopPackageAsUserEvenWhenStopping(@NonNull Context context,
            @NonNull String packageName, @UserIdInt int userId) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        am.forceStopPackageAsUserEvenWhenStopping(packageName, userId);
    }
}
