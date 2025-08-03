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

package com.android.adservices.service.encryptionkey;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Class for handling encryption key fetch and update. */
public class EncryptionKeyJobHandler {

    private final EncryptionKeyDao mEncryptionKeyDao;
    private final EnrollmentDao mEnrollmentDao;
    private final EncryptionKeyFetcher mEncryptionKeyFetcher;

    EncryptionKeyJobHandler(
            EncryptionKeyDao encryptionKeyDao,
            EnrollmentDao enrollmentDao,
            EncryptionKeyFetcher encryptionKeyFetcher) {
        mEncryptionKeyDao = encryptionKeyDao;
        mEnrollmentDao = enrollmentDao;
        mEncryptionKeyFetcher = encryptionKeyFetcher;
    }

    /** Fetch encryption keys or update expired encryption keys. */
    public void fetchAndUpdateEncryptionKeys() {
        // Loop all enrollments, for enrollments that we haven't fetch key successfully before
        // (this can happen when an adtech didn't provide correct key previously), re-fetch the keys
        // as first time fetch.
        List<EnrollmentData> enrollmentDataList = mEnrollmentDao.getAllEnrollmentData();
        Set<String> newlyFetchKeyIdSet = new HashSet<>();
        for (EnrollmentData enrollmentData : enrollmentDataList) {
            List<EncryptionKey> existingKeys =
                    mEncryptionKeyDao.getEncryptionKeyFromEnrollmentId(
                            enrollmentData.getEnrollmentId());
            if (existingKeys.size() == 0) {
                if (Thread.currentThread().isInterrupted()) {
                    LoggerFactory.getLogger()
                            .d(
                                    "EncryptionKeyJobHandler fetchAndUpdateEncryptionKeys"
                                            + " thread interrupted, exiting early.");
                    return;
                }
                Optional<List<EncryptionKey>> newEncryptionKeys =
                        mEncryptionKeyFetcher.fetchEncryptionKeys(
                                /* encryptionKey */ null,
                                /* enrollmentData */ enrollmentData,
                                /* isFirstTimeFetch */ true);
                if (newEncryptionKeys.isPresent()) {
                    for (EncryptionKey encryptionKey : newEncryptionKeys.get()) {
                        newlyFetchKeyIdSet.add(encryptionKey.getId());
                        mEncryptionKeyDao.insert(encryptionKey);
                    }
                }
            }
        }

        // Loop all keys, refresh previously saved encryption keys to check whether keys need to be
        // updated, don't need to refresh newly fetched keys above.
        List<EncryptionKey> encryptionKeyList = mEncryptionKeyDao.getAllEncryptionKeys();
        for (EncryptionKey encryptionKey : encryptionKeyList) {
            // Skip newly fetched keys above.
            if (newlyFetchKeyIdSet.contains(encryptionKey.getId())) {
                continue;
            }
            if (Thread.currentThread().isInterrupted()) {
                LoggerFactory.getLogger()
                        .d(
                                "EncryptionKeyJobHandler"
                                        + " fetchAndUpdateEncryptionKeys thread interrupted,"
                                        + " exiting early.");
                return;
            }
            EnrollmentData enrollmentData =
                    mEnrollmentDao.getEnrollmentData(encryptionKey.getEnrollmentId());
            // When the key doesn't have a corresponding enrollment to it, delete the key, don't
            // need to check expiration time and re-fetch.
            if (enrollmentData == null) {
                mEncryptionKeyDao.delete(encryptionKey.getId());
                continue;
            }

            // Re-fetch keys with "if-modified-since" header to check if we need to update keys.
            Optional<List<EncryptionKey>> updateEncryptionKeys =
                    mEncryptionKeyFetcher.fetchEncryptionKeys(
                            /* encryptionKey */ encryptionKey,
                            /* enrollmentData */ enrollmentData,
                            /* isFirstTimeFetch */ false);
            if (updateEncryptionKeys.isEmpty() || updateEncryptionKeys.get().isEmpty()) {
                continue;
            }
            mEncryptionKeyDao.delete(encryptionKey.getId());
            for (EncryptionKey newKey : updateEncryptionKeys.get()) {
                mEncryptionKeyDao.insert(newKey);
            }
        }
    }
}
