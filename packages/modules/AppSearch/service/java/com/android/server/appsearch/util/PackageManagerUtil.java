/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.appsearch.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Utility to verify if the package is signed with correct certificates.
 *
 * @hide
 */
public final class PackageManagerUtil {

    /**
     * Verifies if the callingPackage has correct matching certificate.
     *
     * <p>For Pre-P devices, this matches with a single byte-array corresponding to the oldest
     * available signature. For P+ devices, it used existing PackageManager's hasSigningCertificate
     * implementation that takes rotation history in account.
     *
     * @param context Context of the calling app.
     * @param packageName package whose signing certificates to check
     * @param sha256cert sha256 of the signing certificate for which to search
     * @return true if this package was or is signed by exactly the certificate with SHA-256 as
     *         {@code sha256cert}
     */
    public static boolean hasSigningCertificate(
            Context context, String packageName, byte[] sha256cert) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return hasSigningCertificateBelowP(context, packageName, sha256cert);
        }

        return context
                .getPackageManager()
                .hasSigningCertificate(packageName, sha256cert, PackageManager.CERT_INPUT_SHA256);
    }

    private static boolean hasSigningCertificateBelowP(
            Context context, String packageName, byte[] sha256cert) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException("Given package does not exist on device!");
        }
        if (packageInfo == null) {
            return false;
        }

        // Verification of an android application requires set-equals matching, to avoid known
        // security vulnerabilities a certificate hash will only be matched when exactly one
        // certificate is present. See http://issuetracker.google.com/36992561 for more information.
        try {
            Signature[] signatures = packageInfo.signatures;
            if (signatures != null && signatures.length == 1) {
                byte[] certificate = MessageDigest.getInstance(/* algorithm= */ "SHA-256")
                                .digest(signatures[0].toByteArray());
                return Arrays.equals(certificate, sha256cert);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Provided SHA-256 implementation is invalid!");
        }
        return false;
    }

    private PackageManagerUtil() {}
}
