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

package com.android.adservices.service.adselection.signature;

import static android.adservices.adselection.SignedContextualAdsFixture.aSignedContextualAds;

import static com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager.PUBLIC_TEST_KEY_STRING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.adservices.adselection.SignedContextualAds;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class ProtectedAudienceSignatureManagerTest {
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EncryptionKeyDao mEncryptionKeyDaoMock;
    private ProtectedAudienceSignatureManager mNoOpSignatureManager;
    private ProtectedAudienceSignatureManager mSignatureManager;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        SignatureVerifier noOpSignatureVerifier =
                new SignatureVerifier() {
                    @Override
                    public boolean verify(byte[] publicKey, byte[] data, byte[] signature) {
                        return true;
                    }
                };
        boolean enrollmentEnabled = false;
        mSignatureManager =
                new ProtectedAudienceSignatureManager(
                        mEnrollmentDaoMock, mEncryptionKeyDaoMock, enrollmentEnabled);
        mNoOpSignatureManager =
                new ProtectedAudienceSignatureManager(
                        mEnrollmentDaoMock, mEncryptionKeyDaoMock, noOpSignatureVerifier);
    }

    @Test
    public void testVerifySignature_validSignature_returnTrue() {
        SignedContextualAds signedContextualAds = aSignedContextualAds();
        String enrollmentId = "enrollment1";
        AdTechIdentifier buyer = signedContextualAds.getBuyer();

        doReturn(new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build())
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(buyer);
        doReturn(
                        Collections.singletonList(
                                new EncryptionKey.Builder()
                                        .setBody(PUBLIC_TEST_KEY_STRING)
                                        .build()))
                .when(mEncryptionKeyDaoMock)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentId, EncryptionKey.KeyType.SIGNING);

        boolean isVerified = mSignatureManager.isVerified(buyer, signedContextualAds);

        assertThat(isVerified).isTrue();
    }

    @Test
    public void testVerifySignature_invalidSignature_returnFalse() {
        byte[] invalidSignature = new byte[] {1, 2, 3};
        SignedContextualAds signedContextualAds =
                new SignedContextualAds.Builder(aSignedContextualAds())
                        .setSignature(invalidSignature)
                        .build();
        String enrollmentId = "enrollment1";
        AdTechIdentifier buyer = signedContextualAds.getBuyer();

        doReturn(new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build())
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(buyer);
        doReturn(
                        Collections.singletonList(
                                new EncryptionKey.Builder()
                                        .setBody(PUBLIC_TEST_KEY_STRING)
                                        .build()))
                .when(mEncryptionKeyDaoMock)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentId, EncryptionKey.KeyType.SIGNING);

        boolean isVerified = mSignatureManager.isVerified(buyer, signedContextualAds);

        assertThat(isVerified).isFalse();
    }

    @Test
    public void testFetchKeys_validAdTech_success() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        byte[] publicKeyBytes = new byte[] {1, 2, 3, 4, 5};
        String publicKey = Base64.getEncoder().encodeToString(publicKeyBytes);
        String enrollmentId = "enrollment1";

        doReturn(new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build())
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);
        doReturn(Collections.singletonList(new EncryptionKey.Builder().setBody(publicKey).build()))
                .when(mEncryptionKeyDaoMock)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentId, EncryptionKey.KeyType.SIGNING);

        List<byte[]> signingKeys = mNoOpSignatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys.size()).isEqualTo(1);
        assertThat(signingKeys.get(0)).isEqualTo(publicKeyBytes);
    }

    @Test
    public void testFetchKeys_validAdTechMultipleKeysSortedProperly_success() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        String enrollmentId = "enrollment1";
        EnrollmentData enrollment =
                new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();

        byte[] publicKeyBytes1 = new byte[] {1, 2, 3, 4, 5};
        String publicKey1 = Base64.getEncoder().encodeToString(publicKeyBytes1);
        byte[] publicKeyBytes2 = new byte[] {6, 7, 8, 9, 10};
        String publicKey2 = Base64.getEncoder().encodeToString(publicKeyBytes2);
        long expiration1 = 0L;
        long expiration2 = 1L;
        EncryptionKey encKey1 =
                new EncryptionKey.Builder().setBody(publicKey1).setExpiration(expiration1).build();
        EncryptionKey encKey2 =
                new EncryptionKey.Builder().setBody(publicKey2).setExpiration(expiration2).build();
        List<EncryptionKey> encKeysToPersistInReverseOrder = List.of(encKey2, encKey1);

        doReturn(enrollment)
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);
        doReturn(encKeysToPersistInReverseOrder)
                .when(mEncryptionKeyDaoMock)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentId, EncryptionKey.KeyType.SIGNING);

        List<byte[]> signingKeys = mNoOpSignatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys.size()).isEqualTo(2);
        assertThat(signingKeys.get(0)).isEqualTo(publicKeyBytes1);
        assertThat(signingKeys.get(1)).isEqualTo(publicKeyBytes2);
    }

    @Test
    public void testFetchKeys_notEnrolledAdTech_returnsEmptyList() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        doReturn(null)
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);

        List<byte[]> signingKeys = mNoOpSignatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testFetchKeys_enrolledAdTechWithNullId_returnsEmptyList() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        doReturn(new EnrollmentData.Builder().build())
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);

        List<byte[]> signingKeys = mNoOpSignatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys).isEqualTo(Collections.emptyList());
    }
}
