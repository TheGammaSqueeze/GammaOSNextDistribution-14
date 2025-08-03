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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_KEY_LENGTH;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.topics.EncryptionManagerTest.DECODED_PRIVATE_KEY;
import static com.android.adservices.service.topics.EncryptionManagerTest.DECODED_PUBLIC_KEY;
import static com.android.adservices.service.topics.EncryptionManagerTest.EMPTY_CONTEXT_INFO;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_MODEL_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TAXONOMY_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TOPIC_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.HpkeJni;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/** Unit tests for {@link HpkeEncrypter}. */
public final class HpkeEncrypterTest {
    private HpkeEncrypter mHpkeEncrypter = new HpkeEncrypter();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesExtendedMockitoRule mAdServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this).spyStatic(ErrorLogUtil.class).build();

    @Test
    public void testEncryption_success() throws JSONException {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        // Encrypt plain text to cipher text
        byte[] cipherText =
                mHpkeEncrypter.encrypt(DECODED_PUBLIC_KEY, plainText, EMPTY_CONTEXT_INFO);

        // Verify cipher text
        assertThat(cipherText).isNotEmpty();

        // Decrypt and deserialize to verify correct information.
        byte[] decryptedText = HpkeJni.decrypt(DECODED_PRIVATE_KEY, cipherText, EMPTY_CONTEXT_INFO);
        JSONObject returnedJSON = new JSONObject(new String(decryptedText));
        Topic returnedTopic =
                Topic.create(
                        returnedJSON.getInt(KEY_TOPIC_ID),
                        returnedJSON.getLong(KEY_TAXONOMY_VERSION),
                        returnedJSON.getLong(KEY_MODEL_VERSION));

        // Verify decrypted and deserialized object creates the expected Topic.
        assertThat(returnedTopic).isEqualTo(topic);
    }

    @Test
    public void testEncryption_invalidKeyLength_returnsEmpty() {
        doNothingOnErrorLogUtilError();
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        // Encrypt plain text to cipher text
        byte[] cipherText =
                mHpkeEncrypter.encrypt(
                        "invalidKey".getBytes(StandardCharsets.UTF_8),
                        plainText,
                        EMPTY_CONTEXT_INFO);

        // Verify cipher text is null.
        assertThat(cipherText).isNull();
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_KEY_LENGTH,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    public void testEncryption_nullPlainText_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mHpkeEncrypter.encrypt(
                                DECODED_PUBLIC_KEY, /* plainText */ null, EMPTY_CONTEXT_INFO));
    }

    @Test
    public void testEncryption_nullPublicKey_throwsException() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        assertThrows(
                NullPointerException.class,
                () -> mHpkeEncrypter.encrypt(/* publicKey */ null, plainText, EMPTY_CONTEXT_INFO));
    }

    @Test
    public void testEncryption_nullContextInfo_throwsException() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        assertThrows(
                NullPointerException.class,
                () ->
                        mHpkeEncrypter.encrypt(
                                DECODED_PUBLIC_KEY, plainText, /* contextInfo */ null));
    }

    private byte[] generateTopicsPlainText(Topic topic) {
        return TopicsJsonMapper.toJson(topic).get().toString().getBytes(StandardCharsets.UTF_8);
    }
}
