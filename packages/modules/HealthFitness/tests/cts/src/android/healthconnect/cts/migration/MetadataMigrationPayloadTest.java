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

package android.healthconnect.cts.migration;

import android.health.connect.migration.MetadataMigrationPayload;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MetadataMigrationPayloadTest {

    private static final int MIN_RRP = 0;
    private static final int MAX_RRP = 7300;
    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void createdWithSettersLowerLimit_validData() {
        final MetadataMigrationPayload payload =
                new MetadataMigrationPayload.Builder()
                        .setRecordRetentionPeriodDays(MIN_RRP)
                        .build();

        mExpect.that(payload.getRecordRetentionPeriodDays()).isEqualTo(MIN_RRP);
    }

    @Test
    public void createdWithSettersUpperLimit_validData() {
        final MetadataMigrationPayload payload =
                new MetadataMigrationPayload.Builder()
                        .setRecordRetentionPeriodDays(MAX_RRP)
                        .build();

        mExpect.that(payload.getRecordRetentionPeriodDays()).isEqualTo(MAX_RRP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidDataBelowRange_throwsIllegalArgumentException() {
        new MetadataMigrationPayload.Builder().setRecordRetentionPeriodDays(MIN_RRP - 1).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidDataAboveRange_throwsIllegalArgumentException() {
        new MetadataMigrationPayload.Builder().setRecordRetentionPeriodDays(MAX_RRP + 1).build();
    }
}
