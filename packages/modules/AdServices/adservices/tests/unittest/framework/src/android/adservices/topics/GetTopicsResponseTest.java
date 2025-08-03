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
package android.adservices.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Unit tests for {@link android.adservices.topics.GetTopicsResponse}
 */
@SmallTest
public final class GetTopicsResponseTest {

    private static final List<Topic> TOPICS_LIST =
            List.of(new Topic(/* mTaxonomyVersion */ 1L, /* mModelVersion */ 1L, /* mTopicId */ 0));
    private static final List<EncryptedTopic> ENCRYPTED_TOPICS_LIST =
            List.of(
                    new EncryptedTopic(
                            /* mEncryptedTopic */ "cipherText".getBytes(StandardCharsets.UTF_8),
                            /* mKeyIdentifier */ "publicKey",
                            /* mEncapsulatedKey */ "encapsulatedKey"
                                    .getBytes(StandardCharsets.UTF_8)));

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetTopicsResponseBuilder_nullableThrows() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    GetTopicsResponse unusedResponse =
                            new GetTopicsResponse.Builder(null, ENCRYPTED_TOPICS_LIST).build();
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    GetTopicsResponse unusedResponse =
                            new GetTopicsResponse.Builder(TOPICS_LIST, null).build();
                });
    }

    @Test
    public void testGetTopicsResponseBuilder_emptyListsAllowed() {
        // Verify empty list for plain text Topics is allowed.
        assertThat(
                        new GetTopicsResponse.Builder(List.of(), ENCRYPTED_TOPICS_LIST)
                                .build()
                                .getEncryptedTopics())
                .isEqualTo(ENCRYPTED_TOPICS_LIST);
    }

    @Test
    public void testGetTopicsResponseBuilder() {
        // Build GetTopicsResponse using topicList
        GetTopicsResponse response =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();

        // Validate the topicList is same to what we created
        assertEquals(TOPICS_LIST, response.getTopics());
        // Validate the encryptedTopicList is same to what we created
        assertEquals(ENCRYPTED_TOPICS_LIST, response.getEncryptedTopics());
    }

    @Test
    public void testEquals() {
        GetTopicsResponse getTopicsResponse1 =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();
        GetTopicsResponse getTopicsResponse2 =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();

        assertThat(getTopicsResponse1.equals(getTopicsResponse2)).isTrue();
    }

    @Test
    public void testHashCode() {
        GetTopicsResponse getTopicsResponse1 =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();
        GetTopicsResponse getTopicsResponse2 =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();

        assertThat(getTopicsResponse1.hashCode()).isEqualTo(getTopicsResponse2.hashCode());
    }
}

