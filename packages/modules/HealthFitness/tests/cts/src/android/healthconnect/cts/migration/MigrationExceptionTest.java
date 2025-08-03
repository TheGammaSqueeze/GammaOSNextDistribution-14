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

import android.health.connect.migration.MigrationException;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MigrationExceptionTest {

    private static final String DEFAULT_FAILED_ENTITY_ID = "entity-id";

    private static final int DEFAULT_ERROR_CODE = MigrationException.ERROR_INTERNAL;

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void createdViaConstructor_validData() {
        final MigrationException exception =
                new MigrationException(
                        "failure message", DEFAULT_ERROR_CODE, DEFAULT_FAILED_ENTITY_ID);

        mExpect.that(exception.getErrorCode()).isEqualTo(DEFAULT_ERROR_CODE);
        mExpect.that(exception.getFailedEntityId()).isEqualTo(DEFAULT_FAILED_ENTITY_ID);
    }
}
