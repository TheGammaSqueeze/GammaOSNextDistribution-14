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

package com.android.adservices.service.topics;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_RESPONSE_LENGTH;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_DECODE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_REQUEST;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_RESPONSE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Class to handle encryption for {@link Topic} objects.
 *
 * <p>Identify the algorithm supported for Encryption.
 *
 * <p>Fetch public key corresponding to sdk(adtech) caller.
 *
 * <p>Generate {@link EncryptedTopic} object from the encrypted cipher text.
 */
public class EncryptionManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    private static final int ENCAPSULATED_KEY_LENGTH = 32;
    private static final String TEST_PUBLIC_KEY_BASE64 =
            "rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=";

    private static EncryptionManager sSingleton;

    private Encrypter mEncrypter;
    private EnrollmentDao mEnrollmentDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    private Flags mFlags;

    EncryptionManager(
            Encrypter encrypter,
            EnrollmentDao enrollmentDao,
            EncryptionKeyDao encryptionKeyDao,
            Flags flags) {
        mEncrypter = encrypter;
        mEnrollmentDao = enrollmentDao;
        mEncryptionKeyDao = encryptionKeyDao;
        mFlags = flags;
    }

    /** Returns the singleton instance of the {@link EncryptionManager} given a context. */
    @NonNull
    public static EncryptionManager getInstance(@NonNull Context context) {
        synchronized (EncryptionManager.class) {
            if (sSingleton == null) {
                sSingleton =
                        new EncryptionManager(
                                new HpkeEncrypter(),
                                EnrollmentDao.getInstance(context),
                                EncryptionKeyDao.getInstance(context),
                                FlagsFactory.getFlags());
            }
        }
        return sSingleton;
    }

    /**
     * Converts plain text {@link Topic} object to {@link EncryptedTopic}.
     *
     * <p>Returns {@link Optional#empty()} if encryption fails.
     *
     * @param topic object to be encrypted
     * @return corresponding encrypted object
     */
    public Optional<EncryptedTopic> encryptTopic(Topic topic, String sdkName) {
        Optional<EncryptedTopic> encryptedTopicOptional =
                encryptTopicWithKey(topic, fetchPublicKeyFor(sdkName));
        sLogger.v("Encrypted topic for %s is %s", topic, encryptedTopicOptional);
        return encryptedTopicOptional;
    }

    /**
     * Returns public key from the enrolled {@code sdkName}. Returns {@link Optional#empty()} if the
     * public key is missing.
     */
    private Optional<String> fetchPublicKeyFor(String sdkName) {
        if (!mFlags.getTopicsTestEncryptionPublicKey().isEmpty()) {
            // Use testing key for encryption if the test key is non-empty.
            return Optional.of(mFlags.getTopicsTestEncryptionPublicKey());
        }

        sLogger.v("Fetching EnrollmentData for %s", sdkName);
        EnrollmentData enrollmentData = mEnrollmentDao.getEnrollmentDataFromSdkName(sdkName);
        if (enrollmentData != null && enrollmentData.getEnrollmentId() != null) {
            sLogger.v("Fetching EncryptionKeys for %s", enrollmentData.getEnrollmentId());
            List<EncryptionKey> encryptionKeys =
                    mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                            enrollmentData.getEnrollmentId(), EncryptionKey.KeyType.ENCRYPTION);
            Optional<EncryptionKey> latestKey =
                    encryptionKeys.stream()
                            .max(Comparator.comparingLong(EncryptionKey::getExpiration));

            if (latestKey.isPresent() && latestKey.get().getBody() != null) {
                return Optional.of(latestKey.get().getBody());
            }
        }
        sLogger.e("Failed to fetch encryption key for %s", sdkName);
        ErrorLogUtil.e(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        return Optional.empty();
    }

    /**
     * Serialise {@link Topic} to JSON string with UTF-8 encoding. Encrypt serialised Topic with the
     * given public key.
     *
     * <p>Returns {@link Optional#empty()} if the public key is missing or if the topic
     * serialisation fails.
     */
    private Optional<EncryptedTopic> encryptTopicWithKey(Topic topic, Optional<String> publicKey) {
        Objects.requireNonNull(topic);

        Optional<JSONObject> optionalTopicJSON = TopicsJsonMapper.toJson(topic);
        if (publicKey.isPresent() && optionalTopicJSON.isPresent()) {
            try {
                // UTF-8 is the default encoding for JSON data.
                byte[] unencryptedSerializedTopic =
                        optionalTopicJSON.get().toString().getBytes(StandardCharsets.UTF_8);
                byte[] base64DecodedPublicKey = Base64.getDecoder().decode(publicKey.get());
                byte[] response =
                        mEncrypter.encrypt(
                                /* publicKey */ base64DecodedPublicKey,
                                /* plainText */ unencryptedSerializedTopic,
                                /* contextInfo */ EMPTY_BYTE_ARRAY);

                return buildEncryptedTopic(response, publicKey.get());
            } catch (IllegalArgumentException illegalArgumentException) {
                ErrorLogUtil.e(
                        illegalArgumentException,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_DECODE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                sLogger.e(
                        illegalArgumentException,
                        "Failed to decode with Base64 decoder for public key = %s",
                        publicKey);
            } catch (NullPointerException nullPointerException) {
                ErrorLogUtil.e(
                        nullPointerException,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_REQUEST,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                sLogger.e(
                        nullPointerException, "Null params while trying to encrypt Topics object.");
            }
        }
        return Optional.empty();
    }

    private static Optional<EncryptedTopic> buildEncryptedTopic(byte[] response, String publicKey) {
        if (response == null) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_RESPONSE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            sLogger.e("Null encryption response received for public key = %s", publicKey);
            return Optional.empty();
        }
        if (response.length < ENCAPSULATED_KEY_LENGTH) {
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_RESPONSE_LENGTH,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            sLogger.e(
                    "Encrypted response size is smaller than minimum expected size of "
                            + ENCAPSULATED_KEY_LENGTH);
            return Optional.empty();
        }

        // First 32 bytes are the encapsulated key and the remaining array in the cipher text.
        int cipherTextLength = response.length - ENCAPSULATED_KEY_LENGTH;
        byte[] encapsulatedKey = new byte[ENCAPSULATED_KEY_LENGTH];
        byte[] cipherText = new byte[cipherTextLength];
        System.arraycopy(
                response,
                /* srcPos */ 0,
                encapsulatedKey,
                /* destPos */ 0,
                /* length */ ENCAPSULATED_KEY_LENGTH);
        System.arraycopy(
                response,
                /* srcPos */ ENCAPSULATED_KEY_LENGTH,
                cipherText,
                /* destPos */ 0,
                /* length */ cipherTextLength);

        return Optional.of(EncryptedTopic.create(cipherText, publicKey, encapsulatedKey));
    }
}
