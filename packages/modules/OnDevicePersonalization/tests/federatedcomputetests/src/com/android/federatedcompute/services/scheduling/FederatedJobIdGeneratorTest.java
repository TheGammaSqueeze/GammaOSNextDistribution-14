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

package com.android.federatedcompute.services.scheduling;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FederatedJobIdGeneratorTest {

    public static final String POPULATION_NAME = "population_name";
    public static final String CALLING_PACKAGE_NAME = "calling_package_name";

    @Test
    public void testGenerateJobId() {
        FederatedJobIdGenerator generator = FederatedJobIdGenerator.getInstance();

        int jobId = generator.generateJobId(null, POPULATION_NAME, CALLING_PACKAGE_NAME);

        assertThat(jobId).isEqualTo((POPULATION_NAME + CALLING_PACKAGE_NAME).hashCode());
    }
}
