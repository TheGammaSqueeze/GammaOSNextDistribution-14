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

package android.adservices.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/** Unit tests for {@link android.adservices.topics.EncryptedTopic} */
public class EncryptedTopicTest {

    private static final byte[] CIPHER_TEXT =
            "{\"taxonomy_version\":2,\"model_version\":5,\"topic_id\":1}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final String PUBLIC_KEY = "PublicKey1";
    private static final byte[] ENCAPSULATED_KEY = "HKDF-SHA256".getBytes(StandardCharsets.UTF_8);

    private EncryptedTopic mEncryptedTopic1;
    private EncryptedTopic mEncryptedTopic2;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        generateEncryptedTopics();
    }

    @Test
    public void testNullFieldsOnConstructor_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    EncryptedTopic unusedTopic = new EncryptedTopic(null, null, null);
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    EncryptedTopic unusedTopic =
                            new EncryptedTopic(null, PUBLIC_KEY, ENCAPSULATED_KEY);
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    EncryptedTopic unusedTopic =
                            new EncryptedTopic(CIPHER_TEXT, null, ENCAPSULATED_KEY);
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    EncryptedTopic unusedTopic = new EncryptedTopic(CIPHER_TEXT, PUBLIC_KEY, null);
                });
    }

    @Test
    public void testGetters() {
        assertThat(mEncryptedTopic1.getEncryptedTopic()).isEqualTo(CIPHER_TEXT);
        assertThat(mEncryptedTopic1.getKeyIdentifier()).isEqualTo(PUBLIC_KEY);
    }

    @Test
    public void testToString() {
        String expectedTopicString =
                "EncryptedTopic{mEncryptedTopic=[123, 34, 116, 97, 120, 111, 110, 111, 109, 121,"
                    + " 95, 118, 101, 114, 115, 105, 111, 110, 34, 58, 50, 44, 34, 109, 111, 100,"
                    + " 101, 108, 95, 118, 101, 114, 115, 105, 111, 110, 34, 58, 53, 44, 34, 116,"
                    + " 111, 112, 105, 99, 95, 105, 100, 34, 58, 49, 125],"
                    + " mKeyIdentifier=PublicKey1, mEncapsulatedKey=[72, 75, 68, 70, 45, 83, 72,"
                    + " 65, 50, 53, 54]}";
        assertThat(mEncryptedTopic1.toString()).isEqualTo(expectedTopicString);
        assertThat(mEncryptedTopic2.toString()).isEqualTo(expectedTopicString);
    }

    @Test
    public void testEquals() {
        assertThat(mEncryptedTopic1).isEqualTo(mEncryptedTopic2);
    }

    @Test
    public void testEquals_nullObject() {
        // To test code won't throw if comparing to a null object.
        assertThat(mEncryptedTopic1).isNotEqualTo(null);
    }

    @Test
    public void testHashCode() {
        assertThat(mEncryptedTopic1.hashCode()).isEqualTo(mEncryptedTopic2.hashCode());
    }

    private void generateEncryptedTopics() {
        mEncryptedTopic1 =
                new EncryptedTopic(
                        /* mEncryptedTopic */ CIPHER_TEXT, /* mKeyIdentifier */
                        PUBLIC_KEY, /* mEncapsulaetKey */
                        ENCAPSULATED_KEY);
        mEncryptedTopic2 =
                new EncryptedTopic(
                        /* mEncryptedTopic */ CIPHER_TEXT, /* mKeyIdentifier */
                        PUBLIC_KEY, /* mEncapsulaetKey */
                        ENCAPSULATED_KEY);
    }
}
