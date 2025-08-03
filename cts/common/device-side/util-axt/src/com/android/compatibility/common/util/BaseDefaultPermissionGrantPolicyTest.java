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

package com.android.compatibility.common.util;

import static android.content.pm.PermissionInfo.PROTECTION_DANGEROUS;

import static com.android.compatibility.common.util.BaseDefaultPermissionGrantPolicyTest.UidState.FixedState.FIXED;
import static com.android.compatibility.common.util.BaseDefaultPermissionGrantPolicyTest.UidState.FixedState.NOT_FIXED;
import static com.android.compatibility.common.util.BaseDefaultPermissionGrantPolicyTest.UidState.FixedState.SUPER_FIXED;
import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class BaseDefaultPermissionGrantPolicyTest extends BusinessLogicTestCase {
    public static final String LOG_TAG = "DefaultPermissionGrantPolicy";
    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final String BRAND_PROPERTY = "ro.product.brand";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private Set<DefaultPermissionGrantException> mRemoteExceptions = new HashSet<>();

    /**
     * Returns whether this build is a CN build.
     */
    public abstract boolean isCnBuild();

    /**
     * Returns whether this build is a CN build with GMS.
     */
    public abstract boolean isCnGmsBuild();

    /**
     * Add default permissions for all applicable apps.
     */
    public abstract void addDefaultSystemHandlerPermissions(
            ArrayMap<String, PackageInfo> packagesToVerify,
            SparseArray<UidState> pregrantUidStates) throws Exception;

    /**
     * Return the names of all the runtime permissions that should be checked for violations.
     */
    public abstract Set<String> getEnforcedRuntimePermissionNames(List<PackageInfo> packageInfos);

    /**
     * Return the names of all the packages whose permissions can always be granted as fixed.
     */
    public Set<String> getGrantAsFixedPackageNames(ArrayMap<String, PackageInfo> packagesToVerify) {
        return Collections.emptySet();
    }

    /**
     * Returns whether the permission name, as defined in
     * {@link PermissionManager.SplitPermissionInfo#getNewPermissions()}
     * should be considered a violation.
     */
    public abstract boolean isSplitPermissionNameViolation(String permissionName);

    public void testDefaultGrantsWithRemoteExceptions(boolean preGrantsOnly) throws Exception {
        List<PackageInfo> allPackages = getAllPackages();
        Set<String> runtimePermNames = getEnforcedRuntimePermissionNames(allPackages);
        ArrayMap<String, PackageInfo> packagesToVerify =
                getMsdkTargetingPackagesUsingRuntimePerms(allPackages, runtimePermNames);

        // Ignore CTS infrastructure
        packagesToVerify.remove("android.tradefed.contentprovider");
        packagesToVerify.remove("androidx.test.services");

        SparseArray<UidState> pregrantUidStates = new SparseArray<>();

        addDefaultSystemHandlerPermissions(packagesToVerify, pregrantUidStates);

        // Add split permissions that were split from non-runtime permissions
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q)) {
            addSplitFromNonDangerousPermissions(packagesToVerify, pregrantUidStates);
        }

        if ((ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)
                || ApiLevelUtil.codenameStartsWith("T"))
                && runtimePermNames.contains(Manifest.permission.POST_NOTIFICATIONS)) {
            addImplicitlyGrantedPermission(Manifest.permission.POST_NOTIFICATIONS,
                    Build.VERSION_CODES.TIRAMISU, packagesToVerify, pregrantUidStates);
        }

        // Add exceptions
        addExceptionsDefaultPermissions(packagesToVerify, runtimePermNames, pregrantUidStates);

        // packageName -> message -> [permission]
        ArrayMap<String, ArrayMap<String, ArraySet<String>>> violations = new ArrayMap();

        // Enforce default grants in the right state
        checkDefaultGrantsInCorrectState(packagesToVerify, pregrantUidStates, violations);

        // Nothing else should have default grants
        checkPackagesForUnexpectedGrants(packagesToVerify, runtimePermNames, violations,
                preGrantsOnly);

        logPregrantUidStates(pregrantUidStates);

        // Bail if we found any violations
        if (!violations.isEmpty()) {
            fail(createViolationsErrorString(violations));
        }
    }


    /**
     * Primarily invoked by business logic, set default permission grant exceptions for this
     * instance of the test class. This is an alternative to downloading the encrypted xml
     * file, a process which is now deprecated.
     *
     * @param pkg         the package name
     * @param sha256      the sha256 cert digest of the package
     * @param permissions the set of permissions, formatted "permission_name fixed_boolean"
     */
    public void setException(String pkg, String sha256, String... permissions) {
        HashMap<String, UidState.FixedState> permissionsMap = new HashMap<>();
        for (String permissionString : permissions) {
            String[] parts = permissionString.trim().split("\\s+");
            if (parts.length != 2) {
                Log.e(LOG_TAG, "Unable to parse remote exception permission: " + permissionString);
                return;
            }
            permissionsMap.put(parts[0], Boolean.valueOf(parts[1]) ? FIXED : NOT_FIXED);
        }
        mRemoteExceptions.add(new DefaultPermissionGrantException(pkg, sha256, permissionsMap));
    }

    /**
     * Primarily invoked by business logic, set default permission grant exceptions for this
     * instance of the test class. Also enables the supply of exception metadata.
     *
     * @param company     the company name
     * @param metadata    the exception metadata
     * @param pkg         the package name
     * @param sha256      the sha256 cert digest of the package
     * @param permissions the set of permissions, formatted "permission_name fixed_boolean"
     */
    public void setExceptionWithMetadata(String company, String metadata, String pkg,
            String sha256, String... permissions) {
        HashMap<String, UidState.FixedState> permissionsMap = new HashMap<>();
        for (String permissionString : permissions) {
            String[] parts = permissionString.trim().split("\\s+");
            if (parts.length != 2) {
                Log.e(LOG_TAG, "Unable to parse remote exception permission: " + permissionString);
                return;
            }
            permissionsMap.put(parts[0], Boolean.valueOf(parts[1]) ? FIXED : NOT_FIXED);
        }
        mRemoteExceptions.add(new DefaultPermissionGrantException(
                company, metadata, pkg, sha256, permissionsMap));
    }

    /**
     * Primarily invoked by business logic, set default permission grant exceptions on CN Gms
     * for this instance of the test class. This is an alternative to downloading the encrypted
     * xml file, a process which is now deprecated.
     *
     * @param pkg         the package name
     * @param sha256      the sha256 cert digest of the package
     * @param permissions the set of permissions, formatted "permission_name fixed_boolean"
     */
    public void setCNGmsException(String pkg, String sha256, String... permissions) {
        if (!isCnGmsBuild()) {
            Log.e(LOG_TAG, "Regular GMS build, skip allowlisting: " + pkg);
            return;
        }
        setException(pkg, sha256, permissions);
    }

    /**
     * Primarily invoked by business logic, set default permission grant exceptions on CN Gms
     * for this instance of the test class. This is an alternative to downloading the encrypted
     * xml file, a process which is now deprecated.
     *
     * @param company     the company name
     * @param metadata    the exception metadata
     * @param pkg         the package name
     * @param sha256      the sha256 cert digest of the package
     * @param permissions the set of permissions, formatted "permission_name fixed_boolean"
     */
    public void setCNGmsExceptionWithMetadata(String company, String metadata, String pkg,
            String sha256, String... permissions) {
        if (!isCnGmsBuild()) {
            Log.e(LOG_TAG, "Regular GMS build, skip allowlisting: " + pkg);
            return;
        }
        setExceptionWithMetadata(company, metadata, pkg, sha256, permissions);
    }


    public List<PackageInfo> getAllPackages() {
        return getInstrumentation().getContext().getPackageManager()
                .getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES
                        | PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
    }

    public static ArrayMap<String, PackageInfo> getMsdkTargetingPackagesUsingRuntimePerms(
            List<PackageInfo> packageInfos, Set<String> runtimePermNames) {
        ArrayMap<String, PackageInfo> packageInfoMap = new ArrayMap<>();

        final int packageInfoCount = packageInfos.size();
        for (int i = 0; i < packageInfoCount; i++) {
            PackageInfo packageInfo = packageInfos.get(i);
            if (packageInfo.requestedPermissions == null) {
                continue;
            }
            if (packageInfo.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                continue;
            }
            for (String requestedPermission : packageInfo.requestedPermissions) {
                if (runtimePermNames.contains(requestedPermission)) {
                    packageInfoMap.put(packageInfo.packageName, packageInfo);
                    break;
                }
            }
        }

        return packageInfoMap;
    }

    public static void addViolation(
            Map<String, ArrayMap<String, ArraySet<String>>> violations, String packageName,
            String permission, String message) {
        if (!violations.containsKey(packageName)) {
            violations.put(packageName, new ArrayMap<>());
        }

        if (!violations.get(packageName).containsKey(message)) {
            violations.get(packageName).put(message, new ArraySet<>());
        }

        violations.get(packageName).get(message).add(permission);
    }

    public static boolean isPackageOnSystemImage(PackageInfo packageInfo) {
        return (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }


    public static String computePackageCertDigest(Signature signature) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            /* can't happen */
            return null;
        }

        messageDigest.update(signature.toByteArray());

        final byte[] digest = messageDigest.digest();
        final int digestLength = digest.length;
        final int charCount = 2 * digestLength;

        final char[] chars = new char[charCount];
        for (int i = 0; i < digestLength; i++) {
            final int byteHex = digest[i] & 0xFF;
            chars[i * 2] = HEX_ARRAY[byteHex >>> 4];
            chars[i * 2 + 1] = HEX_ARRAY[byteHex & 0x0F];
        }
        return new String(chars);
    }

    public static ArrayMap<String, Object> getPackageProperties(String packageName) {
        ArrayMap<String, Object> properties = new ArrayMap();

        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        PackageInfo info = null;
        try {
            info = pm.getPackageInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        properties.put("targetSDK", info.applicationInfo.targetSdkVersion);
        properties.put("signature", computePackageCertDigest(info.signatures[0]).toUpperCase());
        properties.put("uid", UserHandle.getAppId(info.applicationInfo.uid));
        properties.put("priv app", info.applicationInfo.isPrivilegedApp());
        properties.put("persistent", ((info.applicationInfo.flags
                & ApplicationInfo.FLAG_PERSISTENT) != 0) + "\n");
        properties.put("has platform signature", (pm.checkSignatures(info.packageName,
                PLATFORM_PACKAGE_NAME) == PackageManager.SIGNATURE_MATCH));
        properties.put("on system image", isPackageOnSystemImage(info));

        return properties;
    }


    public void addException(DefaultPermissionGrantException exception,
            Set<String> runtimePermNames, Map<String, PackageInfo> packageInfos,
            Set<String> platformSignedPackages, SparseArray<UidState> outUidStates) {
        Log.v(LOG_TAG, "Adding exception for company " + exception.company
                + ". Metadata: " + exception.metadata);
        String packageName = exception.pkg;
        PackageInfo packageInfo = packageInfos.get(packageName);
        if (packageInfo == null) {
            Log.v(LOG_TAG, "Trying to add exception to missing package:" + packageName);

            try {
                // Do not overwhelm logging
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            return;
        }
        if (!packageInfo.applicationInfo.isSystemApp()) {
            if (exception.hasNonBrandSha256()) {
                // Due to CN app removability requirement (b/121209050) and EU DMA requirement
                // (b/248964397), allow non-system app pregrant exceptions as long as they specify a
                // hash.
            } else {
                Log.w(LOG_TAG, "Cannot pregrant permissions to non-system package without cert"
                        + " digest: " + packageName);
                return;
            }
        }
        // If cert SHA-256 digest is specified it is used for verification, for user builds only
        if (exception.hasNonBrandSha256()) {
            String expectedDigest = exception.sha256.replace(":", "").toLowerCase();
            String packageDigest = computePackageCertDigest(packageInfo.signatures[0]);
            if (PropertyUtil.isUserBuild() && !expectedDigest.equalsIgnoreCase(packageDigest)) {
                Log.w(LOG_TAG, "SHA256 cert digest does not match for package: " + packageName
                        + ". Expected: " + expectedDigest.toUpperCase() + ", observed: "
                        + packageDigest.toUpperCase());
                return;
            }
        } else if (exception.hasBrand) {
            // Rare case -- exception.sha256 is actually brand name, verify brand instead
            String expectedBrand = exception.sha256;
            String actualBrand = PropertyUtil.getProperty(BRAND_PROPERTY);
            if (!expectedBrand.equalsIgnoreCase(actualBrand)) {
                Log.w(LOG_TAG, String.format("Brand %s does not match for package: %s",
                        expectedBrand, packageName));
                return;
            }
        } else {
            if (!platformSignedPackages.contains(packageName)) {
                String packageDigest = computePackageCertDigest(packageInfo.signatures[0]);
                Log.w(LOG_TAG, "Package is not signed with the platform certificate: " + packageName
                        + ". Package signature: " + packageDigest.toUpperCase());
                return;
            }
        }

        List<String> requestedPermissions = Arrays.asList(packageInfo.requestedPermissions);
        for (Map.Entry<String, UidState.FixedState> entry : exception.permissions.entrySet()) {
            String permission = entry.getKey();
            UidState.FixedState fixed = entry.getValue();
            if (!requestedPermissions.contains(permission)) {
                Log.w(LOG_TAG, "Permission " + permission + " not requested by: " + packageName);
                continue;
            }
            if (!runtimePermNames.contains(permission)) {
                Log.w(LOG_TAG, "Permission:" + permission + " in not runtime");
                continue;
            }
            final int uid = packageInfo.applicationInfo.uid;
            UidState uidState = outUidStates.get(uid);
            if (uidState == null) {
                uidState = new UidState();
                outUidStates.put(uid, uidState);
            }

            for (String extendedPerm : extendBySplitPermissions(permission,
                    packageInfo.applicationInfo.targetSdkVersion)) {
                uidState.overrideGrantedPermission(packageInfo.packageName,
                        permission.equals(extendedPerm) ? "exception"
                                : "exception (split from " + permission + ")", extendedPerm, fixed);
            }
        }
    }


    public static ArrayList<String> extendBySplitPermissions(String permission, int appTargetSdk) {
        ArrayList<String> extendedPermissions = new ArrayList<>();
        extendedPermissions.add(permission);

        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q)) {
            Context context = getInstrumentation().getTargetContext();
            PermissionManager permissionManager = context.getSystemService(PermissionManager.class);

            for (PermissionManager.SplitPermissionInfo splitPerm :
                    permissionManager.getSplitPermissions()) {
                if (splitPerm.getSplitPermission().equals(permission)
                        && splitPerm.getTargetSdk() > appTargetSdk) {
                    extendedPermissions.addAll(splitPerm.getNewPermissions());
                }
            }
        }

        return extendedPermissions;
    }


    public void setPermissionGrantState(String packageName, String permission,
            boolean granted) {
        try {
            if (granted) {
                getInstrumentation().getUiAutomation().grantRuntimePermission(packageName,
                        permission, android.os.Process.myUserHandle());
            } else {
                getInstrumentation().getUiAutomation().revokeRuntimePermission(packageName,
                        permission, android.os.Process.myUserHandle());
            }
        } catch (Exception e) {
            /* do nothing - best effort */
        }
    }

    public void addExceptionsDefaultPermissions(Map<String, PackageInfo> packageInfos,
            Set<String> runtimePermNames,
            SparseArray<UidState> outUidStates) throws Exception {
        // Only use exceptions from business logic if they've been added
        if (!mRemoteExceptions.isEmpty()) {
            Log.d(LOG_TAG, String.format("Found %d remote exceptions", mRemoteExceptions.size()));
            Set<String> platformSignedPackages = getPlatformSignedPackages(packageInfos);
            for (DefaultPermissionGrantException dpge : mRemoteExceptions) {
                addException(dpge, runtimePermNames, packageInfos, platformSignedPackages,
                        outUidStates);
            }
        } else {
            Log.w(LOG_TAG, "Failed to retrieve remote default permission grant exceptions.");
        }
    }

    private Set<String> getPlatformSignedPackages(Map<String, PackageInfo> packageInfos) {
        Set<String> platformSignedPackages = new ArraySet<>();
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        for (PackageInfo pkg : packageInfos.values()) {
            boolean isPlatformSigned = pm.checkSignatures(pkg.packageName, PLATFORM_PACKAGE_NAME)
                    == PackageManager.SIGNATURE_MATCH;
            if (isPlatformSigned) {
                platformSignedPackages.add(pkg.packageName);
            }
        }
        return platformSignedPackages;
    }


    // Permissions split from non dangerous permissions
    private void addSplitFromNonDangerousPermissions(Map<String, PackageInfo> packageInfos,
            SparseArray<UidState> outUidStates) {
        Context context = getInstrumentation().getTargetContext();

        for (PackageInfo pkg : packageInfos.values()) {
            int targetSdk = pkg.applicationInfo.targetSdkVersion;
            int uid = pkg.applicationInfo.uid;

            for (String permission : pkg.requestedPermissions) {
                PermissionInfo permInfo;
                try {
                    permInfo = context.getPackageManager().getPermissionInfo(permission, 0);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // ignore permissions that are requested but not defined
                    continue;
                }


                // Permissions split from dangerous permission are granted when the original
                // permission permission is granted;
                if ((permInfo.getProtection() & PROTECTION_DANGEROUS) != 0) {
                    continue;
                }

                for (String extendedPerm : extendBySplitPermissions(permission, targetSdk)) {
                    if (!isSplitPermissionNameViolation(extendedPerm)) {
                        continue;
                    }

                    if (!extendedPerm.equals(permission)) {
                        UidState uidState = outUidStates.get(uid);

                        if (uidState != null
                                && uidState.grantedPermissions.containsKey(extendedPerm)) {
                            // permission is already granted. Don't override the grant-state.
                            continue;
                        }

                        appendPackagePregrantedPerms(pkg, "split from non-dangerous permission "
                                        + permission, NOT_FIXED,
                                Collections.singleton(extendedPerm), outUidStates);
                    }
                }
            }
        }
    }

    /**
     * Add a permission which is granted, if it is implicitly added to a package
     * @param permissionToAdd The permission to be added
     * @param targetSdk The targetSDK below which the permission is added in
     * @param packageInfos The packageInfos to be checked
     * @param outUidStates The state to be modified
     */
    public static void addImplicitlyGrantedPermission(String permissionToAdd, int targetSdk,
            Map<String, PackageInfo> packageInfos, SparseArray<UidState> outUidStates) {
        for (PackageInfo pkg : packageInfos.values()) {
            if (pkg.applicationInfo.targetSdkVersion >= targetSdk) {
                continue;
            }
            for (String perm: pkg.requestedPermissions) {
                if (perm.equals(permissionToAdd)) {
                    int uid = pkg.applicationInfo.uid;
                    UidState uidState = outUidStates.get(uid);
                    if (uidState != null
                            && uidState.grantedPermissions.containsKey(permissionToAdd)) {
                        // permission is already granted. Don't override the grant-state.
                        continue;
                    }

                    appendPackagePregrantedPerms(pkg, "permission " + permissionToAdd
                                    + " is granted to pre-" + targetSdk + " apps", NOT_FIXED,
                            Collections.singleton(permissionToAdd), outUidStates);
                }
            }
        }
    }

    public static void appendPackagePregrantedPerms(PackageInfo packageInfo, String reason,
            boolean fixed, Set<String> pregrantedPerms, SparseArray<UidState> outUidStates) {
        appendPackagePregrantedPerms(packageInfo, reason, fixed ? FIXED : NOT_FIXED,
                pregrantedPerms, outUidStates);
    }

    public static void appendPackagePregrantedPerms(PackageInfo packageInfo, String reason,
            UidState.FixedState fixed, Set<String> pregrantedPerms,
            SparseArray<UidState> outUidStates) {
        final int uid = packageInfo.applicationInfo.uid;
        UidState uidState = outUidStates.get(uid);
        if (uidState == null) {
            uidState = new UidState();
            outUidStates.put(uid, uidState);
        }
        for (String requestedPermission : packageInfo.requestedPermissions) {
            if (pregrantedPerms.contains(requestedPermission)) {
                uidState.addGrantedPermission(packageInfo, reason, requestedPermission,
                        fixed);
            }
        }
    }

    public void logPregrantUidStates(SparseArray<UidState> pregrantUidStates) {
        Log.i(LOG_TAG, "PREGRANT UID STATES");
        for (int i = 0; i < pregrantUidStates.size(); i++) {
            Log.i(LOG_TAG, "uid: " + pregrantUidStates.keyAt(i) + " {");
            pregrantUidStates.valueAt(i).log();
            Log.i(LOG_TAG, "}");
        }
    }

    public void checkDefaultGrantsInCorrectState(ArrayMap<String, PackageInfo> packagesToVerify,
            SparseArray<UidState> pregrantUidStates,
            Map<String, ArrayMap<String, ArraySet<String>>> violations) {
        Set<String> grantAsFixedPackageNames = getGrantAsFixedPackageNames(packagesToVerify);
        PackageManager packageManager = getInstrumentation().getContext().getPackageManager();
        for (PackageInfo packageInfo : packagesToVerify.values()) {
            final int uid = packageInfo.applicationInfo.uid;
            UidState uidState = pregrantUidStates.get(uid);
            if (uidState == null) {
                continue;
            }

            final int grantCount = uidState.grantedPermissions.size();
            // make a modifiable list
            List<String> requestedPermissions = new ArrayList<>(
                    Arrays.asList(packageInfo.requestedPermissions));
            for (int i = 0; i < grantCount; i++) {
                String permission = uidState.grantedPermissions.keyAt(i);

                // If the package did not request this permissions, skip as
                // it is requested by another package in the same UID.
                if (!requestedPermissions.contains(permission)) {
                    continue;
                }

                // Not granting the permission is OK, as we want to catch excessive grants
                if (packageManager.checkPermission(permission, packageInfo.packageName)
                        != PackageManager.PERMISSION_GRANTED) {
                    continue;
                }

                boolean grantBackFineLocation = false;

                // Special case: fine location implies coarse location, so we revoke
                // fine location when verifying coarse to avoid interference.
                if (permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)
                        && packageManager.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION,
                        packageInfo.packageName) == PackageManager.PERMISSION_GRANTED) {
                    setPermissionGrantState(packageInfo.packageName,
                            Manifest.permission.ACCESS_FINE_LOCATION, false);
                    grantBackFineLocation = true;
                }

                setPermissionGrantState(packageInfo.packageName, permission, false);

                UidState.FixedState fixedState = uidState.grantedPermissions.valueAt(i);
                Boolean fixed = grantAsFixedPackageNames.contains(packageInfo.packageName)
                        || fixedState == SUPER_FIXED || fixedState == FIXED;

                // Weaker grant is fine, e.g. not-fixed instead of fixed.
                if (!fixed && packageManager.checkPermission(permission, packageInfo.packageName)
                        == PackageManager.PERMISSION_GRANTED) {
                    addViolation(violations, packageInfo.packageName, permission,
                            "granted by default should be revocable");
                }

                setPermissionGrantState(packageInfo.packageName, permission, true);

                if (grantBackFineLocation) {
                    setPermissionGrantState(packageInfo.packageName,
                            Manifest.permission.ACCESS_FINE_LOCATION, true);
                }

                // Now a small trick - pretend the package does not request this permission
                // as we will later treat each granted runtime permissions as a violation.
                requestedPermissions.remove(permission);
                packageInfo.requestedPermissions = requestedPermissions.toArray(
                        new String[requestedPermissions.size()]);
            }
        }
    }

    public void checkPackagesForUnexpectedGrants(Map<String, PackageInfo> packagesToVerify,
            Set<String> runtimePermNames,
            Map<String, ArrayMap<String, ArraySet<String>>> violations,
            boolean preGrantsOnly) throws Exception {
        PackageManager packageManager = getInstrumentation().getContext().getPackageManager();
        for (PackageInfo packageInfo : packagesToVerify.values()) {
            for (String requestedPermission : packageInfo.requestedPermissions) {
                // If another package in the UID can get the permission
                // then it is fine for this package to have it - skip.
                if (runtimePermNames.contains(requestedPermission)
                        && packageManager.checkPermission(requestedPermission,
                        packageInfo.packageName) == PackageManager.PERMISSION_GRANTED
                        && (!preGrantsOnly || (callWithShellPermissionIdentity(() ->
                        packageManager.getPermissionFlags(
                                requestedPermission,
                                packageInfo.packageName,
                                getInstrumentation().getTargetContext().getUser())
                                & PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT)
                        != 0))) {
                    addViolation(violations,
                            packageInfo.packageName, requestedPermission,
                            "cannot be granted by default to package");
                }
            }
        }
    }

    public String createViolationsErrorString(
            ArrayMap<String, ArrayMap<String, ArraySet<String>>> violations) {
        StringBuilder sb = new StringBuilder();

        for (String packageName : violations.keySet()) {
            sb.append("packageName: " + packageName + " {\n");
            for (Map.Entry<String, Object> property
                    : getPackageProperties(packageName).entrySet()) {
                sb.append("  " + property.getKey() + ": "
                        + property.getValue().toString().trim() + "\n");
            }
            for (String message : violations.get(packageName).keySet()) {
                sb.append("  message: " + message + " {\n");
                for (String permission : violations.get(packageName).get(message)) {
                    sb.append("    permission: " + permission + "\n");
                }
                sb.append("  }\n");
            }
            sb.append("}\n");
        }

        return sb.toString();
    }

    public static class UidState {
        public class GrantReason {
            public final String reason;
            public final boolean override;
            public final FixedState fixed;

            GrantReason(String reason, boolean override, FixedState fixed) {
                this.reason = reason;
                this.override = override;
                this.fixed = fixed;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                GrantReason that = (GrantReason) o;
                return override == that.override
                        && Objects.equals(reason, that.reason)
                        && Objects.equals(fixed, that.fixed);
            }

            @Override
            public int hashCode() {
                return Objects.hash(reason, override, fixed);
            }
        }

        /**
         * Enum representing if a permission's pregrant condition should be fixed and how it should
         * interact with other pregrant conditions' fixed status.
         */
        public enum FixedState {

            /**
             * Permission is fixed and when merging with other pregrant conditions it won't lose
             * fixed status and will override non-fixed status.
             */
            SUPER_FIXED,

            /**
             * Permission is fixed and when merging with other pregrant conditions it may lose
             * fixed status.
             */
            FIXED,

            /**
             * Permission is not fixed.
             */
            NOT_FIXED
        }

        // packageName -> permission -> [reason]
        public ArrayMap<String, ArrayMap<String, ArraySet<GrantReason>>> mGrantReasons =
                new ArrayMap<>();
        public ArrayMap<String, FixedState> grantedPermissions = new ArrayMap<>();

        public void log() {
            for (String packageName : mGrantReasons.keySet()) {
                Log.i(LOG_TAG, "  packageName: " + packageName + " {");

                for (Map.Entry<String, Object> property :
                        getPackageProperties(packageName).entrySet()) {
                    Log.i(LOG_TAG, "    " + property.getKey() + ": " + property.getValue());
                }

                // Resort permission -> reason into reason -> permission
                ArrayMap<String, ArraySet<GrantReason>> permissionsToReasons =
                        mGrantReasons.get(packageName);
                ArrayMap<GrantReason, List<String>> reasonsToPermissions = new ArrayMap<>();
                for (String permission : permissionsToReasons.keySet()) {
                    for (GrantReason reason : permissionsToReasons.get(permission)) {
                        if (!reasonsToPermissions.containsKey(reason)) {
                            reasonsToPermissions.put(reason, new ArrayList<>());
                        }

                        reasonsToPermissions.get(reason).add(permission);
                    }
                }

                for (Map.Entry<GrantReason, List<String>> reasonEntry
                        : reasonsToPermissions.entrySet()) {
                    GrantReason reason = reasonEntry.getKey();
                    Log.i(LOG_TAG, "    reason: " + reason.reason + " {");
                    Log.i(LOG_TAG, "      override: " + reason.override);
                    Log.i(LOG_TAG, "      fixed: " + reason.fixed);

                    Log.i(LOG_TAG, "      permissions: [");
                    for (String permission : reasonEntry.getValue()) {
                        Log.i(LOG_TAG, "        " + permission + ",");
                    }
                    Log.i(LOG_TAG, "      ]");
                    Log.i(LOG_TAG, "    }");

                    // Do not overwhelm log
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // ignored
                    }
                }

                Log.i(LOG_TAG, "  }");
            }
        }

        public void addGrantedPermission(PackageInfo packageInfo, String reason, String permission,
                FixedState fixed) {
            String packageName = packageInfo.packageName;
            int targetSdk = packageInfo.applicationInfo.targetSdkVersion;

            // Add permissions split off from the permission to granted
            for (String extendedPerm : extendBySplitPermissions(permission, targetSdk)) {
                mergeGrantedPermission(packageName, extendedPerm.equals(permission) ? reason
                                : reason + " (split from " + permission + ")", extendedPerm,
                        fixed, false);
            }
        }

        public void overrideGrantedPermission(String packageName, String reason, String permission,
                FixedState fixed) {
            mergeGrantedPermission(packageName, reason, permission, fixed, true);
        }

        public void mergeGrantedPermission(String packageName, String reason, String permission,
                FixedState fixed, boolean override) {
            if (!mGrantReasons.containsKey(packageName)) {
                mGrantReasons.put(packageName, new ArrayMap<>());
            }

            if (!mGrantReasons.get(packageName).containsKey(permission)) {
                mGrantReasons.get(packageName).put(permission, new ArraySet<>());
            }

            mGrantReasons.get(packageName).get(permission).add(new GrantReason(reason, override,
                    fixed));

            FixedState oldFixed = grantedPermissions.get(permission);
            if (oldFixed == null) {
                grantedPermissions.put(permission, fixed);
            } else {
                if (oldFixed != SUPER_FIXED && fixed == SUPER_FIXED) {
                    Log.w(LOG_TAG, "override already granted permission " + permission + "("
                            + fixed + ") for " + packageName);
                    grantedPermissions.put(permission, fixed);
                } else if (override) {
                    if (oldFixed == NOT_FIXED && fixed == FIXED) {
                        Log.w(LOG_TAG, "override already granted permission " + permission + "("
                                + fixed + ") for " + packageName);
                        grantedPermissions.put(permission, fixed);
                    }
                } else {
                    if (oldFixed == FIXED && fixed == NOT_FIXED) {
                        Log.w(LOG_TAG, "add already granted permission " + permission + "("
                                + fixed + ") to " + packageName);
                        grantedPermissions.put(permission, fixed);
                    }
                }
            }
        }
    }

    public static class DefaultPermissionGrantException {

        public static final String UNSET_PLACEHOLDER = "(UNSET)";
        public String company;
        public String metadata;
        public String pkg;
        public String sha256;
        public boolean hasBrand; // in rare cases, brand will be specified instead of SHA256 hash
        public Map<String, UidState.FixedState> permissions = new HashMap<>();

        public boolean hasNonBrandSha256() {
            return !sha256.isEmpty() && !hasBrand;
        }

        public DefaultPermissionGrantException(String pkg, String sha256,
                Map<String, UidState.FixedState> permissions) {
            this(UNSET_PLACEHOLDER, UNSET_PLACEHOLDER, pkg, sha256, permissions);
        }

        public DefaultPermissionGrantException(String company, String metadata, String pkg,
                String sha256,
                Map<String, UidState.FixedState> permissions) {
            this.company = company;
            this.metadata = metadata;
            this.pkg = pkg;
            this.sha256 = sha256;
            if (!sha256.isEmpty() && !sha256.contains(":")) {
                hasBrand = true; // rough approximation of brand vs. SHA256 hash
            }
            this.permissions = permissions;
        }
    }

}
