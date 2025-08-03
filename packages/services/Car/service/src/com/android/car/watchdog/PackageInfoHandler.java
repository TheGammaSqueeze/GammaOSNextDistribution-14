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

package com.android.car.watchdog;

import android.annotation.Nullable;
import android.automotive.watchdog.internal.ApplicationCategoryType;
import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.PackageIdentifier;
import android.automotive.watchdog.internal.PackageInfo;
import android.automotive.watchdog.internal.UidType;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.car.CarLog;
import com.android.car.internal.util.IntArray;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Handles package info resolving */
public final class PackageInfoHandler {
    public static final String SHARED_PACKAGE_PREFIX = "shared:";

    private static final String TAG = CarLog.tagFor(PackageInfoHandler.class);

    private final PackageManager mPackageManager;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseArray<String> mGenericPackageNameByUid = new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<List<String>> mPackagesBySharedUid = new SparseArray<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, String> mGenericPackageNameByPackage = new ArrayMap<>();
    @GuardedBy("mLock")
    private final SparseArray<ArraySet<String>> mGenericPackageNamesByComponentType =
            new SparseArray<>();
    @GuardedBy("mLock")
    private List<String> mVendorPackagePrefixes = new ArrayList<>();

    public PackageInfoHandler(PackageManager packageManager) {
        mPackageManager = packageManager;
    }

    /**
     * Returns the generic package names for the given UIDs.
     *
     * <p>Some UIDs may not have names. This may occur when a UID is being removed and the
     * internal data structures are not up-to-date. The caller should handle it.
     */
    public SparseArray<String> getNamesForUids(int[] uids) {
        IntArray unmappedUids = new IntArray(uids.length);
        SparseArray<String> genericPackageNameByUid = new SparseArray<>();
        synchronized (mLock) {
            for (int uid : uids) {
                String genericPackageName = mGenericPackageNameByUid.get(uid, null);
                if (genericPackageName != null) {
                    genericPackageNameByUid.append(uid, genericPackageName);
                } else {
                    unmappedUids.add(uid);
                }
            }
        }
        if (unmappedUids.size() == 0) {
            return genericPackageNameByUid;
        }
        String[] genericPackageNames = PackageManagerHelper.getNamesForUids(mPackageManager,
                unmappedUids.toArray());
        synchronized (mLock) {
            for (int i = 0; i < unmappedUids.size(); ++i) {
                if (genericPackageNames[i] == null || genericPackageNames[i].isEmpty()) {
                    continue;
                }
                int uid = unmappedUids.get(i);
                String genericPackageName = genericPackageNames[i];
                mGenericPackageNameByUid.append(uid, genericPackageName);
                genericPackageNameByUid.append(uid, genericPackageName);
                mGenericPackageNameByPackage.put(genericPackageName, genericPackageName);
                if (!genericPackageName.startsWith(SHARED_PACKAGE_PREFIX)) {
                    continue;
                }
                populateSharedPackagesLocked(uid, genericPackageName);
            }
        }
        return genericPackageNameByUid;
    }

    /**
     * Returns the generic package name for the user package.
     *
     * <p>Returns null when no generic package name is found.
     */
    @Nullable
    public String getNameForUserPackage(String packageName, int userId) {
        synchronized (mLock) {
            String genericPackageName = mGenericPackageNameByPackage.get(packageName);
            if (genericPackageName != null) {
                return genericPackageName;
            }
        }
        try {
            return getNameForPackage(PackageManagerHelper.getPackageInfoAsUser(
                    mPackageManager, packageName, /* flags= */ 0, userId));
        } catch (PackageManager.NameNotFoundException e) {
            Slogf.e(TAG, "Package '%s' not found for user %d: %s", packageName, userId, e);
        }
        return null;
    }

    /** Returns the packages owned by the shared UID */
    public List<String> getPackagesForUid(int uid, String genericPackageName) {
        synchronized (mLock) {
            /* When fetching the packages under a shared UID update the internal DS. This will help
             * capture any recently installed packages.
             */
            populateSharedPackagesLocked(uid, genericPackageName);
            return mPackagesBySharedUid.get(uid);
        }
    }

    /** Returns the generic package name for the given package info. */
    public String getNameForPackage(android.content.pm.PackageInfo packageInfo) {
        synchronized (mLock) {
            String genericPackageName = mGenericPackageNameByPackage.get(packageInfo.packageName);
            if (genericPackageName != null) {
                return genericPackageName;
            }
            if (packageInfo.sharedUserId != null) {
                populateSharedPackagesLocked(packageInfo.applicationInfo.uid,
                        SHARED_PACKAGE_PREFIX + packageInfo.sharedUserId);
                return SHARED_PACKAGE_PREFIX + packageInfo.sharedUserId;
            }
            mGenericPackageNameByPackage.put(packageInfo.packageName, packageInfo.packageName);
            return packageInfo.packageName;
        }
    }

    /**
     * Returns the internal package infos for the given UIDs.
     *
     * <p>Some UIDs may not have package infos. This may occur when a UID is being removed and the
     * internal data structures are not up-to-date. The caller should handle it.
     */
    public List<PackageInfo> getPackageInfosForUids(int[] uids,
            List<String> vendorPackagePrefixes) {
        setVendorPackagePrefixes(vendorPackagePrefixes);
        SparseArray<String> genericPackageNameByUid = getNamesForUids(uids);
        ArrayList<PackageInfo> packageInfos = new ArrayList<>(genericPackageNameByUid.size());
        for (int i = 0; i < genericPackageNameByUid.size(); ++i) {
            packageInfos.add(getPackageInfo(genericPackageNameByUid.keyAt(i),
                    genericPackageNameByUid.valueAt(i)));
        }
        return packageInfos;
    }

    /** Returns component type for the given uid and package name. */
    public @ComponentType int getComponentType(int uid, String genericPackageName) {
        synchronized (mLock) {
            for (int i = 0; i < mGenericPackageNamesByComponentType.size(); ++i) {
                if (mGenericPackageNamesByComponentType.valueAt(i).contains(genericPackageName)) {
                    return mGenericPackageNamesByComponentType.keyAt(i);
                }
            }
        }
        int componentType = ComponentType.UNKNOWN;
        if (genericPackageName.startsWith(SHARED_PACKAGE_PREFIX)) {
            synchronized (mLock) {
                if (!mPackagesBySharedUid.contains(uid)) {
                    populateSharedPackagesLocked(uid, genericPackageName);
                }
                List<String> packages = mPackagesBySharedUid.get(uid);
                if (packages != null) {
                    componentType = getSharedComponentTypeInternal(
                            UserHandle.getUserHandleForUid(uid), packages, genericPackageName);
                }
            }
        } else {
            componentType = getUserPackageComponentType(
                    UserHandle.getUserHandleForUid(uid), genericPackageName);
        }
        if (componentType != ComponentType.UNKNOWN) {
            cachePackageComponentType(genericPackageName, componentType);
        }
        return componentType;
    }

    @GuardedBy("mLock")
    private void populateSharedPackagesLocked(int uid, String genericPackageName) {
        String[] packages = mPackageManager.getPackagesForUid(uid);
        for (String pkg : packages) {
            mGenericPackageNameByPackage.put(pkg, genericPackageName);
        }
        mPackagesBySharedUid.put(uid, Arrays.asList(packages));
    }

    private PackageInfo getPackageInfo(int uid, String genericPackageName) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageIdentifier = new PackageIdentifier();
        packageInfo.packageIdentifier.uid = uid;
        packageInfo.packageIdentifier.name = genericPackageName;
        packageInfo.sharedUidPackages = new ArrayList<>();
        packageInfo.componentType = getComponentType(uid, genericPackageName);
        /* Application category type mapping is handled on the daemon side. */
        packageInfo.appCategoryType = ApplicationCategoryType.OTHERS;
        int appId = UserHandle.getAppId(uid);
        packageInfo.uidType = appId >= Process.FIRST_APPLICATION_UID ? UidType.APPLICATION :
                UidType.NATIVE;

        if (genericPackageName.startsWith(SHARED_PACKAGE_PREFIX)) {
            synchronized (mLock) {
                List<String> packages = mPackagesBySharedUid.get(uid);
                if (packages != null) {
                    packageInfo.sharedUidPackages = new ArrayList<>(packages);
                }
            }
        }
        return packageInfo;
    }

    /**
     * Returns the most restrictive component type shared by the given application infos.
     *
     * A shared UID has multiple packages associated with it and these packages may be
     * mapped to different component types. Thus map the shared UID to the most restrictive
     * component type.
     */
    public @ComponentType int getSharedComponentType(
            List<ApplicationInfo> applicationInfos, String genericPackageName) {
        SparseBooleanArray seenComponents = new SparseBooleanArray();
        for (int i = 0; i < applicationInfos.size(); ++i) {
            int type = getComponentType(applicationInfos.get(i));
            seenComponents.put(type, true);
        }
        if (seenComponents.get(ComponentType.VENDOR)) {
            return ComponentType.VENDOR;
        } else if (seenComponents.get(ComponentType.SYSTEM)) {
            synchronized (mLock) {
                for (int i = 0; i < mVendorPackagePrefixes.size(); ++i) {
                    if (genericPackageName.startsWith(mVendorPackagePrefixes.get(i))) {
                        return ComponentType.VENDOR;
                    }
                }
            }
            return ComponentType.SYSTEM;
        } else if (seenComponents.get(ComponentType.THIRD_PARTY)) {
            return ComponentType.THIRD_PARTY;
        }
        return ComponentType.UNKNOWN;
    }

    private @ComponentType int getUserPackageComponentType(
            UserHandle userHandle, String packageName) {
        try {
            ApplicationInfo info = mPackageManager.getApplicationInfoAsUser(
                    packageName, /* flags= */ 0, userHandle);
            return getComponentType(info);
        } catch (PackageManager.NameNotFoundException e) {
            Slogf.e(TAG, e, "Package '%s' not found for user %d", packageName,
                    userHandle.getIdentifier());
        }
        return ComponentType.UNKNOWN;
    }

    private @ComponentType int getSharedComponentTypeInternal(
            UserHandle userHandle, List<String> packages, String genericPackageName) {
        List<ApplicationInfo> applicationInfos = new ArrayList<>();
        for (int i = 0; i < packages.size(); ++i) {
            try {
                applicationInfos.add(mPackageManager.getApplicationInfoAsUser(
                        packages.get(i), /* flags= */ 0, userHandle));
            } catch (PackageManager.NameNotFoundException e) {
                Slogf.w(TAG, "Package '%s' not found for user %d", packages.get(i),
                        userHandle.getIdentifier());
            }
        }
        return getSharedComponentType(applicationInfos, genericPackageName);
    }

    /** Returns the component type for the given application info. */
    public int getComponentType(ApplicationInfo applicationInfo) {
        if (PackageManagerHelper.isOemApp(applicationInfo)
                || PackageManagerHelper.isVendorApp(applicationInfo)
                || PackageManagerHelper.isOdmApp(applicationInfo)) {
            return ComponentType.VENDOR;
        }
        if (PackageManagerHelper.isSystemApp(applicationInfo)
                || PackageManagerHelper.isUpdatedSystemApp(applicationInfo)
                || PackageManagerHelper.isProductApp(applicationInfo)
                || PackageManagerHelper.isSystemExtApp(applicationInfo)) {
            synchronized (mLock) {
                for (int i = 0; i < mVendorPackagePrefixes.size(); ++i) {
                    if (applicationInfo.packageName.startsWith(mVendorPackagePrefixes.get(i))) {
                        return ComponentType.VENDOR;
                    }
                }
            }
            return ComponentType.SYSTEM;
        }
        return ComponentType.THIRD_PARTY;
    }

    void setVendorPackagePrefixes(List<String> vendorPackagePrefixes) {
        synchronized (mLock) {
            if (mVendorPackagePrefixes.equals(vendorPackagePrefixes)) {
                return;
            }
            mVendorPackagePrefixes = vendorPackagePrefixes;
            // When the vendor package prefixes change due to config update, the component types
            // for these packages also change. Ergo, clear the component type cache, so the
            // component types can be inferred again.
            mGenericPackageNamesByComponentType.clear();
        }
    }

    private void cachePackageComponentType(String genericPackageName,
            @ComponentType int componentType) {
        synchronized (mLock) {
            ArraySet<String> packages = mGenericPackageNamesByComponentType.get(componentType);
            if (packages == null) {
                packages = new ArraySet<>();
            }
            packages.add(genericPackageName);
            mGenericPackageNamesByComponentType.append(componentType, packages);
        }
    }
}
