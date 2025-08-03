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

package com.android.cobalt.crypto;

import static com.android.cobalt.crypto.PublicKeys.ANALYZER_KEY_DEV;
import static com.android.cobalt.crypto.PublicKeys.ANALYZER_KEY_INDEX_DEV;
import static com.android.cobalt.crypto.PublicKeys.ANALYZER_KEY_INDEX_PROD;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_KEY_DEV;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_KEY_INDEX_DEV;
import static com.android.cobalt.crypto.PublicKeys.SHUFFLER_KEY_INDEX_PROD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.runner.AndroidJUnit4;

import com.android.adservices.cobalt.HpkeEncryptImpl;
import com.android.cobalt.CobaltPipelineType;
import com.android.cobalt.crypto.testing.HpkeEncryptFactory;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.EncryptedMessage.EncryptionScheme;
import com.google.cobalt.Envelope;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationBatch;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ObservationToEncrypt;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

@RunWith(AndroidJUnit4.class)
public class HpkeEncrypterTest {
    // KeyIndex values for testing.
    private static final int SHUFFLER_KEY_INDEX_VALUE = 1;
    private static final int ANALYZER_KEY_INDEX_VALUE = 1337;
    private HpkeEncrypter mHpkeEncrypter;

    private static final int TEST_METRIC_ID = 25;
    private static final ByteString TEST_CONTRIBUTION_ID = ByteString.copyFromUtf8("testContId");

    @Before
    public void setUp() {
        mHpkeEncrypter =
                new HpkeEncrypter(
                        HpkeEncryptFactory.noOpHpkeEncrypt(),
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);
    }

    /** Round-trip an envelope through the {@link HpkeEncrypter} with a no-op encryption. */
    @Test
    public void encryptEnvelope_noOpRoundTripSuccess() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        // NoOp-encrypt the envelope.
        Optional<EncryptedMessage> encryptionResult = mHpkeEncrypter.encryptEnvelope(envelope);
        assertTrue(encryptionResult.isPresent());

        // Check that returned EncryptedMessage fields are correctly set.
        EncryptedMessage encryptedMessage = encryptionResult.get();
        assertThat(encryptedMessage.getKeyIndex()).isEqualTo(SHUFFLER_KEY_INDEX_VALUE);
        assertThat(encryptedMessage.getPublicKeyFingerprint()).isEmpty();
        assertThat(encryptedMessage.getScheme()).isEqualTo(EncryptionScheme.NONE);
        assertThat(encryptedMessage.getContributionId()).isEmpty();

        // NoOp-decrypt the envelope.
        byte[] envelopeDecryptedBytes = encryptedMessage.getCiphertext().toByteArray();
        Envelope decryptedEnvelope = Envelope.parseFrom(envelopeDecryptedBytes);

        // Check that the observation was correctly round-tripped.
        assertThat(decryptedEnvelope.getBatchCount()).isEqualTo(1);
        assertThat(decryptedEnvelope.getBatch(0).getMetaData().getMetricId())
                .isEqualTo(TEST_METRIC_ID);
    }

    /** Round-trip an observation through the {@link HpkeEncrypter} with a no-op encryption. */
    @Test
    public void encryptObservation_noOpRoundTripSuccess() throws Exception {
        String testObservationIdString = "testObsId";
        Observation observation =
                Observation.newBuilder()
                        .setRandomId(ByteString.copyFromUtf8(testObservationIdString))
                        .build();
        ObservationToEncrypt observationToEncrypt =
                ObservationToEncrypt.newBuilder()
                        .setContributionId(TEST_CONTRIBUTION_ID)
                        .setObservation(observation)
                        .build();

        // NoOp-Encrypt the observation.
        Optional<EncryptedMessage> encryptionResult =
                mHpkeEncrypter.encryptObservation(observationToEncrypt);
        assertTrue(encryptionResult.isPresent());

        // Check that returned EncryptedMessage fields are correctly set.
        EncryptedMessage encryptedMessage = encryptionResult.get();
        assertThat(encryptedMessage.getPublicKeyFingerprint()).isEmpty();
        assertThat(encryptedMessage.getScheme()).isEqualTo(EncryptionScheme.NONE);
        assertThat(encryptedMessage.getKeyIndex()).isEqualTo(ANALYZER_KEY_INDEX_VALUE);
        assertThat(encryptedMessage.getContributionId()).isEqualTo(TEST_CONTRIBUTION_ID);

        // NoOp-decrypt the envelope.
        byte[] observationDecryptedBytes = encryptedMessage.getCiphertext().toByteArray();
        Observation decryptedObservation = Observation.parseFrom(observationDecryptedBytes);

        // Check that the observation was correctly round-tripped.
        assertThat(decryptedObservation.getRandomId().toString(UTF_8))
                .isEqualTo(testObservationIdString);
    }

    @Test
    public void encryptEnvelope_hpkeSingleTripSuccess() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        mHpkeEncrypter =
                new HpkeEncrypter(
                        new HpkeEncryptImpl(),
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);

        assertTrue(mHpkeEncrypter.encryptEnvelope(envelope).isPresent());
    }

    @Test
    public void encryptObservation_hpkeSingleTripSuccess() throws Exception {
        String testObservationIdString = "testObsId";
        Observation observation =
                Observation.newBuilder()
                        .setRandomId(ByteString.copyFromUtf8(testObservationIdString))
                        .build();
        ObservationToEncrypt observationToEncrypt =
                ObservationToEncrypt.newBuilder()
                        .setContributionId(TEST_CONTRIBUTION_ID)
                        .setObservation(observation)
                        .build();

        mHpkeEncrypter =
                new HpkeEncrypter(
                        new HpkeEncryptImpl(),
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);

        assertTrue(mHpkeEncrypter.encryptObservation(observationToEncrypt).isPresent());
    }

    @Test
    public void encryptEmptyMessage_returnsEmptyOptional() throws Exception {
        assertThat(mHpkeEncrypter.encryptEnvelope(Envelope.newBuilder().build()))
                .isEqualTo(Optional.empty());
        assertThat(
                        mHpkeEncrypter.encryptObservation(
                                ObservationToEncrypt.newBuilder()
                                        .setContributionId(TEST_CONTRIBUTION_ID)
                                        .build()))
                .isEqualTo(Optional.empty());
    }

    @Test
    public void wrongSizedKey_throwsAssertionError() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        byte[] non32BytePublicKey = new byte[] {};
        mHpkeEncrypter =
                new HpkeEncrypter(
                        HpkeEncryptFactory.noOpHpkeEncrypt(),
                        non32BytePublicKey,
                        SHUFFLER_KEY_INDEX_VALUE,
                        non32BytePublicKey,
                        ANALYZER_KEY_INDEX_VALUE);
        assertThrows(AssertionError.class, () -> mHpkeEncrypter.encryptEnvelope(envelope));
    }

    @Test
    public void encryptionFailure_throwsEncryptionFailedException() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        mHpkeEncrypter =
                new HpkeEncrypter(
                        HpkeEncryptFactory.emptyHpkeEncrypt(),
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);
        assertThrows(
                EncryptionFailedException.class, () -> mHpkeEncrypter.encryptEnvelope(envelope));
    }

    @Test
    public void checkKeyIndices_prodEnvironment() {
        HpkeEncrypter hpkeEncrypter =
                HpkeEncrypter.createForEnvironment(
                        HpkeEncryptFactory.noOpHpkeEncrypt(), CobaltPipelineType.PROD);
        assertThat(hpkeEncrypter.mShufflerKeyIndex).isEqualTo(SHUFFLER_KEY_INDEX_PROD);
        assertThat(hpkeEncrypter.mAnalyzerKeyIndex).isEqualTo(ANALYZER_KEY_INDEX_PROD);
    }

    @Test
    public void checkKeyIndices_devEnvironment() {
        HpkeEncrypter hpkeEncrypter =
                HpkeEncrypter.createForEnvironment(
                        HpkeEncryptFactory.noOpHpkeEncrypt(), CobaltPipelineType.DEV);
        assertThat(hpkeEncrypter.mShufflerKeyIndex).isEqualTo(SHUFFLER_KEY_INDEX_DEV);
        assertThat(hpkeEncrypter.mAnalyzerKeyIndex).isEqualTo(ANALYZER_KEY_INDEX_DEV);
    }
}
