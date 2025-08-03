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
package com.android.tradefed.result.skipped;

import com.android.tradefed.result.skipped.SkipReason.DemotionTrigger;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SkipReason}. */
@RunWith(JUnit4.class)
public class SkipReasonTest {

    @Test
    public void parseFromString() {
        SkipReason skipReason = new SkipReason("some message", DemotionTrigger.ERROR_RATE, "8888");
        SkipReason deserializedReason = SkipReason.fromString(skipReason.toString());

        Truth.assertThat(deserializedReason.getReason()).isEqualTo(skipReason.getReason());
        Truth.assertThat(deserializedReason.getTrigger()).isEqualTo(skipReason.getTrigger());
        Truth.assertThat(deserializedReason.getBugId()).isEqualTo(skipReason.getBugId());
    }
}
