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

import android.health.connect.HealthPermissions;
import android.health.connect.migration.PermissionMigrationPayload;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class PermissionMigrationPayloadTest {

    private static final String DEFAULT_HOLDING_PACKAGE_NAME = "package.name";
    private static final Instant DEFAULT_FIRST_GRANT_TIME = Instant.now();
    private static final String DEFAULT_PERMISSION = HealthPermissions.WRITE_STEPS;

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void createdWithoutSetters_validData() {
        final PermissionMigrationPayload payload =
                new PermissionMigrationPayload.Builder(
                                DEFAULT_HOLDING_PACKAGE_NAME, DEFAULT_FIRST_GRANT_TIME)
                        .build();

        mExpect.that(payload.getHoldingPackageName()).isEqualTo(DEFAULT_HOLDING_PACKAGE_NAME);
        mExpect.that(payload.getFirstGrantTime()).isEqualTo(DEFAULT_FIRST_GRANT_TIME);
        mExpect.that(payload.getPermissions()).isEmpty();
    }

    @Test
    public void createdWithSetters_validData() {
        final PermissionMigrationPayload payload =
                new PermissionMigrationPayload.Builder("", Instant.now())
                        .setHoldingPackageName(DEFAULT_HOLDING_PACKAGE_NAME)
                        .setFirstGrantTime(DEFAULT_FIRST_GRANT_TIME)
                        .addPermission(DEFAULT_PERMISSION)
                        .build();

        mExpect.that(payload.getHoldingPackageName()).isEqualTo(DEFAULT_HOLDING_PACKAGE_NAME);
        mExpect.that(payload.getFirstGrantTime()).isEqualTo(DEFAULT_FIRST_GRANT_TIME);
        mExpect.that(payload.getPermissions()).containsExactly(DEFAULT_PERMISSION);
    }
}
