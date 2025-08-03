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

package com.android.server.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.sdksandbox.LogUtil;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.pm.PackageManagerLocal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class to handle all logics related to sdk data
 *
 * @hide
 */
public class SdkSandboxStorageManager {
    private static final String TAG = "SdkSandboxManager";

    private final Context mContext;
    private final Object mLock = new Object();

    // Prefix to prepend with all sdk storage paths.
    private final String mRootDir;

    private final SdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private final PackageManagerLocal mPackageManagerLocal;

    SdkSandboxStorageManager(
            Context context,
            SdkSandboxManagerLocal sdkSandboxManagerLocal,
            PackageManagerLocal packageManagerLocal) {
        this(context, sdkSandboxManagerLocal, packageManagerLocal, /*rootDir=*/ "");
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxStorageManager(
            Context context,
            SdkSandboxManagerLocal sdkSandboxManagerLocal,
            PackageManagerLocal packageManagerLocal,
            String rootDir) {
        mContext = context;
        mSdkSandboxManagerLocal = sdkSandboxManagerLocal;
        mPackageManagerLocal = packageManagerLocal;
        mRootDir = rootDir;
    }

    public void notifyInstrumentationStarted(CallingInfo callingInfo) {
        synchronized (mLock) {
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/true);
        }
    }

    /**
     * Handle package added or updated event.
     *
     * <p>On package added or updated, we need to reconcile sdk subdirectories for the new/updated
     * package.
     */
    public void onPackageAddedOrUpdated(CallingInfo callingInfo) {
        LogUtil.d(TAG, "Preparing SDK data on package added or update for: " + callingInfo);
        synchronized (mLock) {
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/false);
        }
    }

    /**
     * Handle user unlock event.
     *
     * When user unlocks their device, the credential encrypted storage becomes available for
     * reconcilation.
     */
    public void onUserUnlocking(int userId) {
        synchronized (mLock) {
            reconcileSdkDataPackageDirs(userId);
        }
    }

    public void prepareSdkDataOnLoad(CallingInfo callingInfo) {
        LogUtil.d(TAG, "Preparing SDK data on load for: " + callingInfo);
        synchronized (mLock) {
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/false);
        }
    }

    public StorageDirInfo getSdkStorageDirInfo(CallingInfo callingInfo, String sdkName) {
        final StorageDirInfo packageDirInfo = getSdkDataPackageDirInfo(callingInfo);
        if (packageDirInfo == null) {
            // TODO(b/238164644): SdkSandboxManagerService should fail loadSdk
            return new StorageDirInfo(null, null);
        }
        // TODO(b/232924025): We should have these information cached, instead of rescanning dirs.
        synchronized (mLock) {
            final SubDirectories ceSubDirs = new SubDirectories(packageDirInfo.getCeDataDir());
            final SubDirectories deSubDirs = new SubDirectories(packageDirInfo.getDeDataDir());
            final String sdkCeSubDirPath = ceSubDirs.getSdkSubDir(sdkName, /*fullPath=*/ true);
            final String sdkDeSubDirPath = deSubDirs.getSdkSubDir(sdkName, /*fullPath=*/ true);
            return new StorageDirInfo(sdkCeSubDirPath, sdkDeSubDirPath);
        }
    }

    public List<StorageDirInfo> getSdkStorageDirInfo(CallingInfo callingInfo) {
        final StorageDirInfo packageDirInfo = getSdkDataPackageDirInfo(callingInfo);
        if (packageDirInfo == null) {
            // TODO(b/238164644): SdkSandboxManagerService should fail loadSdk
            return new ArrayList<>();
        }

        final List<StorageDirInfo> sdkStorageDirInfos = new ArrayList<>();

        synchronized (mLock) {
            final SubDirectories ceSubDirs = new SubDirectories(packageDirInfo.getCeDataDir());
            final SubDirectories deSubDirs = new SubDirectories(packageDirInfo.getDeDataDir());

            /**
             * Getting the SDKs name with deSubDir only assuming that ceSubDirs and deSubDirs have
             * the same list of SDKs
             */
            final ArrayList<String> sdkNames = deSubDirs.getSdkNames();
            int sdkNamesSize = sdkNames.size();

            for (int i = 0; i < sdkNamesSize; i++) {
                final String sdkCeSubDirPath =
                        ceSubDirs.getSdkSubDir(sdkNames.get(i), /*fullPath=*/ true);
                final String sdkDeSubDirPath =
                        deSubDirs.getSdkSubDir(sdkNames.get(i), /*fullPath=*/ true);
                sdkStorageDirInfos.add(new StorageDirInfo(sdkCeSubDirPath, sdkDeSubDirPath));
            }
            return sdkStorageDirInfos;
        }
    }

    public StorageDirInfo getInternalStorageDirInfo(CallingInfo callingInfo, String subDirName) {
        final StorageDirInfo packageDirInfo = getSdkDataPackageDirInfo(callingInfo);
        if (packageDirInfo == null) {
            // TODO(b/238164644): SdkSandboxManagerService should fail loadSdk
            return new StorageDirInfo(null, null);
        }
        synchronized (mLock) {
            final SubDirectories ceSubDirs = new SubDirectories(packageDirInfo.getCeDataDir());
            final SubDirectories deSubDirs = new SubDirectories(packageDirInfo.getDeDataDir());
            final String ceSubDirPath = ceSubDirs.getInternalSubDir(subDirName, /*fullPath=*/ true);
            final String deSubDirPath = deSubDirs.getInternalSubDir(subDirName, /*fullPath=*/ true);
            return new StorageDirInfo(ceSubDirPath, deSubDirPath);
        }
    }

    public List<StorageDirInfo> getInternalStorageDirInfo(CallingInfo callingInfo) {
        final StorageDirInfo packageDirInfo = getSdkDataPackageDirInfo(callingInfo);
        if (packageDirInfo == null) {
            // TODO(b/238164644): SdkSandboxManagerService should fail loadSdk
            return new ArrayList<>();
        }

        final List<StorageDirInfo> internalStorageDirInfos = new ArrayList<>();

        synchronized (mLock) {
            final SubDirectories ceSubDirs = new SubDirectories(packageDirInfo.getCeDataDir());
            final SubDirectories deSubDirs = new SubDirectories(packageDirInfo.getDeDataDir());

            List<String> internalSubDirNames =
                    Arrays.asList(SubDirectories.SHARED_DIR, SubDirectories.SANDBOX_DIR);

            for (int i = 0; i < 2; i++) {
                final String sdkCeSubDirPath =
                        ceSubDirs.getInternalSubDir(internalSubDirNames.get(i), /*fullPath=*/ true);
                final String sdkDeSubDirPath =
                        deSubDirs.getInternalSubDir(internalSubDirNames.get(i), /*fullPath=*/ true);
                internalStorageDirInfos.add(new StorageDirInfo(sdkCeSubDirPath, sdkDeSubDirPath));
            }
            return internalStorageDirInfos;
        }
    }

    @Nullable
    private StorageDirInfo getSdkDataPackageDirInfo(CallingInfo callingInfo) {
        final int uid = callingInfo.getUid();
        final String packageName = callingInfo.getPackageName();
        String volumeUuid = null;
        try {
            volumeUuid = getVolumeUuidForPackage(getUserId(uid), packageName);
        } catch (Exception e) {
            Log.w(TAG, "Failed to find package " + packageName + " error: " + e.getMessage());
            return null;
        }
        final String cePackagePath =
                getSdkDataPackageDirectory(
                        volumeUuid, getUserId(uid), packageName, /*isCeData=*/ true);
        final String dePackagePath =
                getSdkDataPackageDirectory(
                        volumeUuid, getUserId(uid), packageName, /*isCeData=*/ false);
        return new StorageDirInfo(cePackagePath, dePackagePath);
    }

    private int getUserId(int uid) {
        final UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        return userHandle.getIdentifier();
    }

    @GuardedBy("mLock")
    private void reconcileSdkDataSubDirs(CallingInfo callingInfo, boolean forInstrumentation) {
        final int uid = callingInfo.getUid();
        final int userId = getUserId(uid);
        final String packageName = callingInfo.getPackageName();
        final List<String> sdksUsed = getSdksUsed(userId, packageName);
        if (sdksUsed.isEmpty()) {
            if (forInstrumentation) {
                Log.w(TAG,
                        "Running instrumentation for the sdk-sandbox process belonging to client "
                                + "app "
                                + packageName + " (uid = " + uid
                                + "). However client app doesn't depend on any SDKs. Only "
                                + "creating \"shared\" sdk sandbox data sub directory");
            } else {
                Log.i(TAG, "No SDKs used. Skipping SDK data reconcilation for " + callingInfo);
                return;
            }
        }
        String volumeUuid = null;
        try {
            volumeUuid = getVolumeUuidForPackage(userId, packageName);
        } catch (Exception e) {
            Log.w(TAG, "Failed to find package " + packageName + " error: " + e.getMessage());
            return;
        }
        final String deSdkDataPackagePath =
                getSdkDataPackageDirectory(volumeUuid, userId, packageName, /*isCeData=*/ false);
        final SubDirectories existingDeSubDirs = new SubDirectories(deSdkDataPackagePath);

        final int appId = UserHandle.getAppId(uid);
        final UserManager um = mContext.getSystemService(UserManager.class);
        int flags = 0;
        boolean doesCeNeedReconcile = false;
        boolean doesDeNeedReconcile = false;
        final Set<String> expectedSdkNames = new ArraySet<>(sdksUsed);
        final UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        if (um.isUserUnlockingOrUnlocked(userHandle)) {
            final String ceSdkDataPackagePath =
                    getSdkDataPackageDirectory(volumeUuid, userId, packageName, /*isCeData=*/ true);
            final SubDirectories ceSubDirsBeforeReconcilePrefix =
                    new SubDirectories(ceSdkDataPackagePath);
            flags = PackageManagerLocal.FLAG_STORAGE_CE | PackageManagerLocal.FLAG_STORAGE_DE;
            doesCeNeedReconcile = !ceSubDirsBeforeReconcilePrefix.isValid(expectedSdkNames);
            doesDeNeedReconcile = !existingDeSubDirs.isValid(expectedSdkNames);
        } else {
            flags = PackageManagerLocal.FLAG_STORAGE_DE;
            doesDeNeedReconcile = !existingDeSubDirs.isValid(expectedSdkNames);
        }

        // Reconcile only if ce or de subdirs are different than expectation
        if (doesCeNeedReconcile || doesDeNeedReconcile) {
            // List of all the sub-directories we need to create
            final List<String> subDirNames = existingDeSubDirs.generateSubDirNames(sdksUsed);
            try {
                // TODO(b/224719352): Pass actual seinfo from here
                mPackageManagerLocal.reconcileSdkData(
                        volumeUuid,
                        packageName,
                        subDirNames,
                        userId,
                        appId,
                        /*previousAppId=*/ -1,
                        /*seInfo=*/ "default",
                        flags);
                Log.i(TAG, "SDK data reconciled for " + callingInfo);
            } catch (Exception e) {
                // We will retry when sdk gets loaded
                Log.w(TAG, "Failed to reconcileSdkData for " + packageName + " subDirNames: "
                        + String.join(", ", subDirNames) + " error: " + e.getMessage());
            }
        } else {
            Log.i(TAG, "Skipping SDK data reconcilation for " + callingInfo);
        }
    }

    /**
     * Returns list of sdks {@code packageName} uses
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    private List<String> getSdksUsed(int userId, String packageName) {
        PackageManager pm = getPackageManager(userId);
        try {
            ApplicationInfo info = pm.getApplicationInfo(
                    packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            return getSdksUsed(info);
        } catch (PackageManager.NameNotFoundException ignored) {
            return Collections.emptyList();
        }
    }

    private static List<String> getSdksUsed(ApplicationInfo info) {
        List<String> result = new ArrayList<>();
        List<SharedLibraryInfo> sharedLibraries = info.getSharedLibraryInfos();
        for (int i = 0; i < sharedLibraries.size(); i++) {
            final SharedLibraryInfo sharedLib = sharedLibraries.get(i);
            if (sharedLib.getType() != SharedLibraryInfo.TYPE_SDK_PACKAGE) {
                continue;
            }
            result.add(sharedLib.getName());
        }
        return result;
    }

    /**
     * For the given {@code userId}, ensure that sdk data package directories are still valid.
     *
     * <p>The primary concern of this method is to remove invalid data directories. Missing valid
     * directories will get created when the app loads sdk for the first time.
     */
    @GuardedBy("mLock")
    private void reconcileSdkDataPackageDirs(int userId) {
        Log.i(TAG, "Reconciling sdk data package directories for " + userId);
        PackageInfoHolder pmInfoHolder = new PackageInfoHolder(mContext, userId);
        reconcileSdkDataPackageDirs(userId, /*isCeData=*/ true, pmInfoHolder);
        reconcileSdkDataPackageDirs(userId, /*isCeData=*/ false, pmInfoHolder);
    }

    @GuardedBy("mLock")
    private void reconcileSdkDataPackageDirs(
            int userId, boolean isCeData, PackageInfoHolder pmInfoHolder) {

        final List<String> volumeUuids = getMountedVolumes();
        for (int i = 0; i < volumeUuids.size(); i++) {
            final String volumeUuid = volumeUuids.get(i);
            final String rootDir = getSdkDataRootDirectory(volumeUuid, userId, isCeData);
            final String[] sdkPackages = new File(rootDir).list();
            if (sdkPackages == null) {
                continue;
            }
            // Now loop over package directories and remove the ones that are invalid
            for (int j = 0; j < sdkPackages.length; j++) {
                final String packageName = sdkPackages[j];
                // Only consider installed packages which are not instrumented and either
                // not using sdk or on incorrect volume for destroying
                final int uid = pmInfoHolder.getUid(packageName);
                final boolean isInstrumented =
                        mSdkSandboxManagerLocal.isInstrumentationRunning(packageName, uid);
                final boolean hasCorrectVolume =
                        TextUtils.equals(volumeUuid, pmInfoHolder.getVolumeUuid(packageName));
                final boolean isInstalled = !pmInfoHolder.isUninstalled(packageName);
                final boolean usesSdk = pmInfoHolder.usesSdk(packageName);
                if (!isInstrumented && isInstalled && (!hasCorrectVolume || !usesSdk)) {
                    destroySdkDataPackageDirectory(volumeUuid, userId, packageName, isCeData);
                }
            }
        }

        // Now loop over all installed packages and ensure all packages have sdk data directories
        final Iterator<String> it = pmInfoHolder.getInstalledPackagesUsingSdks().iterator();
        while (it.hasNext()) {
            final String packageName = it.next();
            final String volumeUuid = pmInfoHolder.getVolumeUuid(packageName);
            // Verify if package dir contains a subdir for each sdk and a shared directory
            final String packageDir = getSdkDataPackageDirectory(volumeUuid, userId, packageName,
                    isCeData);
            final SubDirectories subDirs = new SubDirectories(packageDir);
            final Set<String> expectedSdkNames = pmInfoHolder.getSdksUsed(packageName);
            if (subDirs.isValid(expectedSdkNames)) {
                continue;
            }

            Log.i(TAG, "Reconciling missing package directory for: " + packageDir);
            final int uid = pmInfoHolder.getUid(packageName);
            if (uid == -1) {
                Log.w(TAG, "Failed to get uid for reconcilation of " + packageDir);
                // Safe to continue since we will retry during loading sdk
                continue;
            }
            final CallingInfo callingInfo = new CallingInfo(uid, packageName);
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/ false);
        }
    }

    private PackageManager getPackageManager(int userId) {
        return mContext.createContextAsUser(UserHandle.of(userId), 0).getPackageManager();
    }

    @GuardedBy("mLock")
    private void destroySdkDataPackageDirectory(
            @Nullable String volumeUuid, int userId, String packageName, boolean isCeData) {
        final Path packageDir =
                Paths.get(getSdkDataPackageDirectory(volumeUuid, userId, packageName, isCeData));
        if (!Files.exists(packageDir)) {
            return;
        }

        Log.i(TAG, "Destroying sdk data package directory " + packageDir);

        // Even though system owns the package directory, the sub-directories are owned by sandbox.
        // We first need to get rid of sub-directories.
        try {
            final int flag = isCeData
                    ? PackageManagerLocal.FLAG_STORAGE_CE
                    : PackageManagerLocal.FLAG_STORAGE_DE;
            mPackageManagerLocal.reconcileSdkData(volumeUuid, packageName,
                    Collections.emptyList(), userId, /*appId=*/-1, /*previousAppId=*/-1,
                    /*seInfo=*/"default", flag);
        } catch (Exception e) {
            Log.e(TAG, "Failed to destroy sdk data on user unlock for userId: " + userId
                    + " packageName: " + packageName +  " error: " + e.getMessage());
        }

        // Now that the package directory is empty, we can delete it
        try {
            Files.delete(packageDir);
        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Failed to destroy sdk data on user unlock for userId: "
                            + userId
                            + " packageName: "
                            + packageName
                            + " error: "
                            + e.getMessage());
        }
    }

    private String getDataDirectory(@Nullable String volumeUuid) {
        if (TextUtils.isEmpty(volumeUuid)) {
            return mRootDir + "/data";
        } else {
            return mRootDir + "/mnt/expand/" + volumeUuid;
        }
    }

    private String getSdkDataRootDirectory(
            @Nullable String volumeUuid, int userId, boolean isCeData) {
        return getDataDirectory(volumeUuid) + (isCeData ? "/misc_ce/" : "/misc_de/") + userId
            + "/sdksandbox";
    }

    /** Fetches the SDK data package directory based on the arguments */
    public String getSdkDataPackageDirectory(
            @Nullable String volumeUuid, int userId, String packageName, boolean isCeData) {
        return getSdkDataRootDirectory(volumeUuid, userId, isCeData) + "/" + packageName;
    }

    /**
     * Class representing collection of sub-directories used for sdk sandox storage
     *
     * <p>There are two kinds of sub-directories:
     *
     * <ul>
     *   <li>Sdk sub-directory: belongs exclusively to individual sdk and has name <sdk>@random
     *   <li>Internal sub-directory: not specific to a particular sdk. Can belong to other entities.
     *       Typically has structure <name>#random. The only exception being shared storage which is
     *       just named "shared".
     * </ul>
     *
     * <p>This class helps in organizing the sdk-subdirectories in groups so that they are easier to
     * process.
     *
     * @hide
     */
    public static class SubDirectories {

        public static final String SHARED_DIR = "shared";
        public static final String SANDBOX_DIR = "sandbox";
        static final ArraySet<String> INTERNAL_SUBDIRS =
                new ArraySet(Arrays.asList(SHARED_DIR, SANDBOX_DIR));

        private final String mBaseDir;
        private final ArrayMap<String, String> mSdkSubDirs;
        private final ArrayMap<String, String> mInternalSubDirs;
        private boolean mHasUnknownSubDirs = false;

        /**
         * Lists all the children of provided path and organizes them into sdk and internal group.
         */
        SubDirectories(String path) {
            mBaseDir = path;
            mSdkSubDirs = new ArrayMap<>();
            mInternalSubDirs = new ArrayMap<>();

            final File parent = new File(path);
            final String[] children = parent.list();
            if (children == null) {
                return;
            }
            for (int i = 0; i < children.length; i++) {
                final String child = children[i];
                if (child.indexOf("@") != -1) {
                    final String[] tokens = child.split("@");
                    mSdkSubDirs.put(tokens[0], child);
                } else if (child.indexOf("#") != -1) {
                    final String[] tokens = child.split("#");
                    mInternalSubDirs.put(tokens[0], child);
                } else if (child.equals(SHARED_DIR)) {
                    mInternalSubDirs.put(SHARED_DIR, SHARED_DIR);
                } else {
                    mHasUnknownSubDirs = true;
                }
            }
        }

        /** Gets the sub-directory name of provided sdk with random suffix */
        @Nullable
        public String getSdkSubDir(String sdkName) {
            return getSdkSubDir(sdkName, /*fullPath=*/ false);
        }

        /** Gets the full path of per-sdk storage with random suffix */
        @Nullable
        public String getSdkSubDir(String sdkName, boolean fullPath) {
            final String subDir = mSdkSubDirs.getOrDefault(sdkName, null);
            if (subDir == null || !fullPath) return subDir;
            return Paths.get(mBaseDir, subDir).toString();
        }

        /** Gets the full path of internal storage directory with random suffix */
        @Nullable
        public String getInternalSubDir(String subDirName, boolean fullPath) {
            final String subDir = mInternalSubDirs.getOrDefault(subDirName, null);
            if (subDir == null || !fullPath) return subDir;
            return Paths.get(mBaseDir, subDir).toString();
        }

        /**
         * Provided a list of sdk names, verifies if the current collection of directories satisfies
         * per-sdk and internal sub-directory requirements.
         */
        public boolean isValid(Set<String> expectedSdkNames) {
            final boolean hasCorrectSdkSubDirs = mSdkSubDirs.keySet().equals(expectedSdkNames);
            final boolean hasCorrectInternalSubDirs =
                    mInternalSubDirs.keySet().equals(INTERNAL_SUBDIRS);
            return hasCorrectSdkSubDirs && hasCorrectInternalSubDirs && !mHasUnknownSubDirs;
        }

        /**
         * Give the sdk names, generate sub-dir names for these sdks and sub-dirs for internal use.
         *
         * <p>Random suffix for existing directories are re-used.
         */
        public List<String> generateSubDirNames(List<String> sdkNames) {
            final List<String> result = new ArrayList<>();

            // Populate sub-dirs for internal use
            for (int i = 0; i < INTERNAL_SUBDIRS.size(); i++) {
                final String subDirValue = INTERNAL_SUBDIRS.valueAt(i);
                final String subDirName = getOrGenerateInternalSubDir(subDirValue);
                result.add(subDirName);
            }

            // Populate sub-dirs for per-sdk usage
            for (int i = 0; i < sdkNames.size(); i++) {
                final String sdkName = sdkNames.get(i);
                final String subDirName = getOrGenerateSdkSubDir(sdkName);
                result.add(subDirName);
            }

            return result;
        }

        public ArrayList<String> getSdkNames() {
            ArrayList<String> sdkNames = new ArrayList<>();
            for (int i = 0; i < mSdkSubDirs.size(); i++) {
                sdkNames.add(mSdkSubDirs.keyAt(i));
            }
            return sdkNames;
        }

        private String getOrGenerateSdkSubDir(String sdkName) {
            final String subDir = getSdkSubDir(sdkName);
            if (subDir != null) return subDir;
            return sdkName + "@" + getRandomString();
        }

        private String getOrGenerateInternalSubDir(String internalDirName) {
            if (internalDirName.equals(SHARED_DIR)) {
                return SHARED_DIR;
            }
            final String subDir = mInternalSubDirs.getOrDefault(internalDirName, null);
            if (subDir != null) return subDir;
            return internalDirName + "#" + getRandomString();
        }

        // Returns a random string.
        private static String getRandomString() {
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP);
        }
    }

    private static class PackageInfoHolder {
        private final Context mContext;
        final ArrayMap<String, Set<String>> mPackagesWithSdks = new ArrayMap<>();
        final ArrayMap<String, Integer> mPackageNameToUid = new ArrayMap<>();
        final ArrayMap<String, String> mPackageNameToVolumeUuid = new ArrayMap<>();
        final Set<String> mUninstalledPackages = new ArraySet<>();

        PackageInfoHolder(Context context, int userId) {
            mContext = context.createContextAsUser(UserHandle.of(userId), 0);

            PackageManager pm = mContext.getPackageManager();
            final List<PackageInfo> packageInfoList = pm.getInstalledPackages(
                    PackageManager.GET_SHARED_LIBRARY_FILES);
            final ArraySet<String> installedPackages = new ArraySet<>();

            for (int i = 0; i < packageInfoList.size(); i++) {
                final PackageInfo info = packageInfoList.get(i);
                installedPackages.add(info.packageName);
                final String volumeUuid =
                        StorageUuuidConverter.convertToVolumeUuid(info.applicationInfo.storageUuid);
                mPackageNameToVolumeUuid.put(info.packageName, volumeUuid);
                mPackageNameToUid.put(info.packageName, info.applicationInfo.uid);

                final List<String> sdksUsedNames =
                        SdkSandboxStorageManager.getSdksUsed(info.applicationInfo);
                if (sdksUsedNames.isEmpty()) {
                    continue;
                }
                mPackagesWithSdks.put(info.packageName, new ArraySet<>(sdksUsedNames));
            }

            // If an app is uninstalled with DELETE_KEEP_DATA flag, we need to preserve its sdk
            // data. For that, we need names of uninstalled packages.
            final List<PackageInfo> allPackages =
                    pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);
            for (int i = 0; i < allPackages.size(); i++) {
                final String packageName = allPackages.get(i).packageName;
                if (!installedPackages.contains(packageName)) {
                    mUninstalledPackages.add(packageName);
                }
            }
        }

        public boolean isUninstalled(String packageName) {
            return mUninstalledPackages.contains(packageName);
        }

        public int getUid(String packageName) {
            return mPackageNameToUid.getOrDefault(packageName, -1);
        }

        public Set<String> getInstalledPackagesUsingSdks() {
            return mPackagesWithSdks.keySet();
        }

        public Set<String> getSdksUsed(String packageName) {
            return mPackagesWithSdks.get(packageName);
        }

        public boolean usesSdk(String packageName) {
            return mPackagesWithSdks.containsKey(packageName);
        }

        public String getVolumeUuid(String packageName) {
            return mPackageNameToVolumeUuid.get(packageName);
        }
    }

    // TODO(b/234023859): We will remove this class once the required APIs get unhidden
    // The class below has been copied from StorageManager's convert logic
    private static class StorageUuuidConverter {
        private static final String FAT_UUID_PREFIX = "fafafafa-fafa-5afa-8afa-fafa";
        private static final UUID UUID_DEFAULT =
                UUID.fromString("41217664-9172-527a-b3d5-edabb50a7d69");
        private static final String UUID_SYSTEM = "system";
        private static final UUID UUID_SYSTEM_ =
                UUID.fromString("5d258386-e60d-59e3-826d-0089cdd42cc0");
        private static final String UUID_PRIVATE_INTERNAL = null;
        private static final String UUID_PRIMARY_PHYSICAL = "primary_physical";
        private static final UUID UUID_PRIMARY_PHYSICAL_ =
                UUID.fromString("0f95a519-dae7-5abf-9519-fbd6209e05fd");

        private static @Nullable String convertToVolumeUuid(@NonNull UUID storageUuid) {
            if (UUID_DEFAULT.equals(storageUuid)) {
                return UUID_PRIVATE_INTERNAL;
            } else if (UUID_PRIMARY_PHYSICAL_.equals(storageUuid)) {
                return UUID_PRIMARY_PHYSICAL;
            } else if (UUID_SYSTEM_.equals(storageUuid)) {
                return UUID_SYSTEM;
            } else {
                String uuidString = storageUuid.toString();
                // This prefix match will exclude fsUuids from private volumes because
                // (a) linux fsUuids are generally Version 4 (random) UUIDs so the prefix
                // will contain 4xxx instead of 5xxx and (b) we've already matched against
                // known namespace (Version 5) UUIDs above.
                if (uuidString.startsWith(FAT_UUID_PREFIX)) {
                    String fatStr =
                            uuidString.substring(FAT_UUID_PREFIX.length()).toUpperCase(Locale.US);
                    return fatStr.substring(0, 4) + "-" + fatStr.substring(4);
                }

                return storageUuid.toString();
            }
        }
    }

    // We loop over "/mnt/expand" directory's children and find the volumeUuids
    // TODO(b/234023859): We want to use storage manager api in future for this task
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    List<String> getMountedVolumes() {
        // Collect package names from root directory
        final List<String> volumeUuids = new ArrayList<>();
        volumeUuids.add(null);

        final String[] mountedVolumes = new File(mRootDir + "/mnt/expand").list();
        if (mountedVolumes == null) {
            return volumeUuids;
        }

        for (int i = 0; i < mountedVolumes.length; i++) {
            final String volumeUuid = mountedVolumes[i];
            volumeUuids.add(volumeUuid);
        }
        return volumeUuids;
    }

    private @Nullable String getVolumeUuidForPackage(int userId, String packageName)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = getPackageManager(userId);
        ApplicationInfo info = pm.getApplicationInfo(packageName, /*flags=*/ 0);
        return StorageUuuidConverter.convertToVolumeUuid(info.storageUuid);
    }

    /**
     * Sdk data directories for a particular sdk or internal usage.
     *
     * <p>Every sdk sub-directory has two data directories. One is credentially encrypted storage
     * and another is device encrypted.
     *
     * @hide
     */
    public static class StorageDirInfo {
        @Nullable final String mCeData;
        @Nullable final String mDeData;

        public StorageDirInfo(@Nullable String ceDataPath, @Nullable String deDataPath) {
            mCeData = ceDataPath;
            mDeData = deDataPath;
        }

        @Nullable
        public String getCeDataDir() {
            return mCeData;
        }

        @Nullable
        public String getDeDataDir() {
            return mDeData;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StorageDirInfo)) return false;
            StorageDirInfo that = (StorageDirInfo) o;
            return mCeData.equals(that.mCeData) && mDeData.equals(that.mDeData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mCeData, mDeData);
        }
    }
}
