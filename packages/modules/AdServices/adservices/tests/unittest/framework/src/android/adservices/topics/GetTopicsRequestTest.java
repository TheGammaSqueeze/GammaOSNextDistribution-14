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

import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link android.adservices.topics.GetTopicsRequest}
 */
@SmallTest
public final class GetTopicsRequestTest {
    private static final String SOME_SDK_NAME = "SomeSDKName";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuilder_notSettingSdkName() {
        GetTopicsRequest request = new GetTopicsRequest.Builder().build();
        assertThat(request.getAdsSdkName()).isEmpty();
        // RecordObservation default value is true
        assertThat(request.shouldRecordObservation()).isTrue();
    }

    @Test
    public void testBuilderSetAdsSdkName_nullSdkName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new GetTopicsRequest.Builder().setAdsSdkName(/* adsSdkName */ null).build();
                });
    }

    @Test
    public void testBuilderSetAdsSdkName_nonNullSdkName() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder()
                        .setAdsSdkName(/* adsSdkName */ SOME_SDK_NAME)
                        .build();
        assertThat(request.getAdsSdkName()).isEqualTo(SOME_SDK_NAME);
        // RecordObservation default value is true
        assertThat(request.shouldRecordObservation()).isTrue();
    }

    @Test
    public void testBuilderSetAdsSdkName_recordObservationFalse() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder()
                        .setAdsSdkName(/* adsSdkName */ SOME_SDK_NAME)
                        .setShouldRecordObservation(false)
                        .build();
        assertThat(request.getAdsSdkName()).isEqualTo(SOME_SDK_NAME);
        assertThat(request.shouldRecordObservation()).isFalse();
    }

    @Test
    public void testBuilder_recordObservationFalse() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder().setShouldRecordObservation(false).build();
        assertThat(request.getAdsSdkName()).isEmpty();
        assertThat(request.shouldRecordObservation()).isFalse();
    }
}
