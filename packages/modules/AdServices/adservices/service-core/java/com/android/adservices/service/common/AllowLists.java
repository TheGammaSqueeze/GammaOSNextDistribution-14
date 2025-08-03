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

package com.android.adservices.service.common;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Utility class to handle AllowList for Apps and SDKs. */
public class AllowLists {
    @VisibleForTesting public static final String ALLOW_ALL = "*";
    public static final String ALLOW_NONE = "";

    private static final String SPLITTER = ",";
    private static final String HASH_ALGORITHM = "SHA-256";

    /** Returns whether all entities are allowlisted or not based on the given {@code allowList}. */
    public static boolean doesAllowListAllowAll(@NonNull String allowList) {
        Objects.requireNonNull(allowList);
        return ALLOW_ALL.equals(allowList);
    }

    /** Splits the given {@code allowList} into the list of entities allowed. */
    public static List<String> splitAllowList(@NonNull String allowList) {
        Objects.requireNonNull(allowList);

        if (allowList.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(allowList.split(SPLITTER))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    /**
     * A utility to check if an app package exists in the provided allow-list. The allow-list to
     * search is split by {@link #SPLITTER} without any white spaces. E.g. of a valid allow list -
     * "abc.package1.app,com.package2.app,com.package3.xyz" If the provided parameter {@code
     * appPackageName} exists in the allow-list (e.g. com.package2.app), then the method returns
     * true, false otherwise.
     */
    public static boolean isPackageAllowListed(
            @NonNull String allowList, @NonNull String appPackageName) {
        if (ALLOW_ALL.equals(allowList)) {
            return true;
        }

        // TODO(b/237686242): Cache the AllowList so that we don't need to read from Flags and split
        // on every API call.
        return Arrays.stream(allowList.split(SPLITTER))
                .map(String::trim)
                .anyMatch(packageName -> packageName.equals(appPackageName));
    }

    /**
     * A utility to check if all app signatures exist in the provided allow-list. The allow-list to
     * search is split by {@link #SPLITTER} without any white spaces.
     *
     * @param context the Context
     * @param signatureAllowList the list of signatures that is allowed.
     * @param appPackageName the package name of an app
     * @return true if this app is allowed. Otherwise, it returns false.
     */
    public static boolean isSignatureAllowListed(
            @NonNull Context context,
            @NonNull String signatureAllowList,
            @NonNull String appPackageName) {
        if (ALLOW_ALL.equals(signatureAllowList)) {
            return true;
        }

        byte[] appSignatureHash = getAppSignatureHash(context, appPackageName);

        // App must have signatures queried from Package Manager in order to use PPAPI. Otherwise,
        // it is not allowed.
        if (appSignatureHash == null) {
            return false;
        }

        String hexSignature = toHexString(appSignatureHash);

        LogUtil.v("App %s has signature(s) as %s", appPackageName, hexSignature);

        // TODO(b/237686242): Cache the AllowList so that we don't need to read from Flags and split
        // on every API call.
        return Arrays.stream(signatureAllowList.split(SPLITTER))
                .map(String::trim)
                .anyMatch(signature -> signature.equals(hexSignature));
    }

    /**
     * Get Hash for the signature of an app. Most of the methods invoked are from {@link
     * PackageManager}.
     *
     * @param context the Context
     * @param packageName package name of the app
     * @return the hash in byte array format to represent the signature of an app. Returns {@code
     *     null} if app cannot be fetched from package manager or there is no {@link
     *     PackageInfo}/{@link SigningInfo} associated with this app.
     */
    @VisibleForTesting
    @Nullable
    public static byte[] getAppSignatureHash(
            @NonNull Context context, @NonNull String packageName) {
        PackageInfo packageInfo;
        try {
            packageInfo =
                    context.getPackageManager()
                            .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e("Package %s is not found in Package Manager!", packageName);
            return null;
        }
        if (packageInfo == null) {
            LogUtil.w("There is not package info for package %s", packageName);
            return null;
        }

        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo == null) {
            LogUtil.w(
                    "There is no signing info for package %s. Please check the signature!",
                    packageName);
            return null;
        }

        Signature[] signatures = signingInfo.getSigningCertificateHistory();
        if (signatures == null || signatures.length == 0) {
            LogUtil.w(
                    "There is no signature fetched from signing info for package %s.", packageName);
            return null;
        }

        byte[] signatureHash = null;
        // Current signature is at the last index of the history.
        // This AllowList mechanism is actually not design for authentication. We only use it for
        // system health and decide who can participate in our beta release. In this case, it's fine
        // to just use whatever the current signing key is for all the apps that can participate
        // in the beta 1 without needing to worry about subsequent rotations.
        Signature currentSignature = signatures[signatures.length - 1];
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            signatureHash = digest.digest(currentSignature.toByteArray());
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e("SHA not available: " + e.getMessage());
        }

        return signatureHash;
    }

    /**
     * Convert byte array to a hex string.
     *
     * <p>Mostly copied from frameworks/base/core/java/android/content/pm/Signature.java
     *
     * @param bytes the byte array
     * @return the hex string format of the byte array
     */
    @VisibleForTesting
    protected static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte v : bytes) {
            int d = (v >> 4) & 0xf;
            sb.append((char) (d >= 10 ? ('a' + d - 10) : ('0' + d)));
            d = v & 0xf;
            sb.append((char) (d >= 10 ? ('a' + d - 10) : ('0' + d)));
        }

        return sb.toString();
    }
}
