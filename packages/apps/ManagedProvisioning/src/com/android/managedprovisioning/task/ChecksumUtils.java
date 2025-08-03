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

import static java.util.Objects.requireNonNull;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;

import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Utils related to checksum calculations and comparison.
 */
final class ChecksumUtils {

    private final Utils mUtils;

    ChecksumUtils(Utils utils) {
        mUtils = requireNonNull(utils);
    }

    /**
     * Returns whether a signature hash of downloaded apk matches the hash given in constructor.
     */
    boolean doesASignatureHashMatch(PackageInfo packageInfo, byte[] signatureChecksum) {
        ProvisionLogger.logd("Checking " + Utils.SHA256_TYPE
                + "-hashes of all signatures of downloaded package.");
        List<byte[]> sigHashes = computeHashesOfAllSignatures(packageInfo.signatures);
        if (sigHashes == null || sigHashes.isEmpty()) {
            ProvisionLogger.loge("Downloaded package does not have any signatures.");
            return false;
        }

        for (byte[] sigHash : sigHashes) {
            if (Arrays.equals(sigHash, signatureChecksum)) {
                return true;
            }
        }

        ProvisionLogger.loge("Provided hash does not match any signature hash.");
        ProvisionLogger.loge("Hash provided by programmer: "
                + StoreUtils.byteArrayToString(signatureChecksum));
        ProvisionLogger.loge("Hashes computed from package signatures: ");
        for (byte[] sigHash : sigHashes) {
            if (sigHash != null) {
                ProvisionLogger.loge(StoreUtils.byteArrayToString(sigHash));
            }
        }

        return false;
    }

    /**
     * Returns whether the package hash of downloaded file matches the hash given in {@link
     * PackageDownloadInfo}. By default, {@code SHA-256} is used to verify the file hash.
     */
    boolean doesPackageHashMatch(String downloadLocation, byte[] packageChecksum) {
        byte[] packageSha256Hash = null;

        ProvisionLogger.logd("Checking file hash of entire apk file.");
        packageSha256Hash = mUtils.computeHashOfFile(downloadLocation, Utils.SHA256_TYPE);
        if (Arrays.equals(packageChecksum, packageSha256Hash)) {
            return true;
        }

        ProvisionLogger.loge("Provided hash does not match file hash.");
        ProvisionLogger.loge("Hash provided by programmer: "
                + StoreUtils.byteArrayToString(packageChecksum));
        if (packageSha256Hash != null) {
            ProvisionLogger.loge("SHA-256 Hash computed from file: "
                    + StoreUtils.byteArrayToString(packageSha256Hash));
        }
        return false;
    }

    private List<byte[]> computeHashesOfAllSignatures(Signature[] signatures) {
        if (signatures == null) {
            return null;
        }

        List<byte[]> hashes = new LinkedList<>();
        for (Signature signature : signatures) {
            byte[] hash = mUtils.computeHashOfByteArray(signature.toByteArray());
            hashes.add(hash);
        }
        return hashes;
    }
}
