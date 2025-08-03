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

import android.health.connect.HealthDataCategory;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.migration.PriorityMigrationPayload;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PriorityMigrationPayloadTest {

    private static final int DEFAULT_CATEGORY = HealthDataCategory.ACTIVITY;
    private static final DataOrigin DEFAULT_ORIGIN_PACKAGE_NAME_1 =
            new DataOrigin.Builder().setPackageName("package.name.1").build();

    private static final DataOrigin DEFAULT_ORIGIN_PACKAGE_NAME_2 =
            new DataOrigin.Builder().setPackageName("package.name.2").build();

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void createdWithSetters_validData() {
        final PriorityMigrationPayload payload =
                new PriorityMigrationPayload.Builder()
                        .setDataCategory(DEFAULT_CATEGORY)
                        .addDataOrigin(DEFAULT_ORIGIN_PACKAGE_NAME_1)
                        .addDataOrigin(DEFAULT_ORIGIN_PACKAGE_NAME_2)
                        .build();

        mExpect.that(payload.getDataCategory()).isEqualTo(DEFAULT_CATEGORY);
        List<DataOrigin> priorityList = payload.getDataOrigins();
        mExpect.that(priorityList.size()).isEqualTo(2);
        mExpect.that(priorityList.get(0).getPackageName())
                .isEqualTo(DEFAULT_ORIGIN_PACKAGE_NAME_1.getPackageName());
        mExpect.that(priorityList.get(1).getPackageName())
                .isEqualTo(DEFAULT_ORIGIN_PACKAGE_NAME_2.getPackageName());
    }

    @Test
    public void createdWithoutSetters_validData() {
        final PriorityMigrationPayload payload = new PriorityMigrationPayload.Builder().build();

        mExpect.that(payload.getDataCategory()).isEqualTo(HealthDataCategory.UNKNOWN);
        mExpect.that(payload.getDataOrigins()).isEmpty();
    }
}
