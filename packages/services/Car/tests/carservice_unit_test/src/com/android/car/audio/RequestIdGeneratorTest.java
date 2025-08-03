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

package com.android.car.audio;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RequestIdGeneratorTest {

    public static final int MAX_REQUESTS = 5;
    private RequestIdGenerator mRequestIdGenerator;

    @Before
    public void setup() {
        mRequestIdGenerator = new RequestIdGenerator();
    }

    @Test
    public void generateUniqueRequestId() {
        assertWithMessage("Generated request id")
                .that(mRequestIdGenerator.generateUniqueRequestId()).isEqualTo(0);
    }

    @Test
    public void generateUniqueRequestId_multipleTimes() {
        mRequestIdGenerator.generateUniqueRequestId();

        long requestId = mRequestIdGenerator.generateUniqueRequestId();

        assertWithMessage("Second generated request id")
                .that(requestId).isEqualTo(1);
    }

    @Test
    public void generateUniqueRequestId_afterReleaseReturnPrevRequestId() {
        long requestId = mRequestIdGenerator.generateUniqueRequestId();
        mRequestIdGenerator.releaseRequestId(requestId);

        long regeneratedRequestId = mRequestIdGenerator.generateUniqueRequestId();

        assertWithMessage("Regenerated request id")
                .that(regeneratedRequestId).isEqualTo(requestId);
    }

    @Test
    public void generateUniqueRequestId_failsAfterLimit() {
        RequestIdGenerator generator = new RequestIdGenerator(MAX_REQUESTS);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            for (int c = 0; c < MAX_REQUESTS + 1; c++) {
                generator.generateUniqueRequestId();
            }
        });

        assertWithMessage("Over limit request generation exception")
                .that(thrown).hasMessageThat().contains("Could not generate request id");
    }
}
