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

import android.health.connect.migration.AppInfoMigrationPayload;
import android.health.connect.migration.MigrationEntity;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MigrationEntityTest {

    private static final String DEFAULT_PACKAGE_NAME = "package.name";
    private static final String DEFAULT_APP_NAME = "app";
    private static final byte[] DEFAULT_ICON_BYTES = new byte[] {1, 2, 3};
    private static final AppInfoMigrationPayload APP_INFO_PAYLOAD =
            new AppInfoMigrationPayload.Builder(DEFAULT_PACKAGE_NAME, DEFAULT_APP_NAME)
                    .setAppIcon(DEFAULT_ICON_BYTES)
                    .build();
    private static final String DEFAULT_ENTITY_ID = "entity-1";

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void createdViaConstructor_validData() {
        final MigrationEntity entity = new MigrationEntity(DEFAULT_ENTITY_ID, APP_INFO_PAYLOAD);

        mExpect.that(entity.getEntityId()).isEqualTo(DEFAULT_ENTITY_ID);
        mExpect.that(entity.getPayload()).isInstanceOf(AppInfoMigrationPayload.class);

        AppInfoMigrationPayload payload = (AppInfoMigrationPayload) entity.getPayload();
        mExpect.that(payload.getPackageName()).isEqualTo(DEFAULT_PACKAGE_NAME);
        mExpect.that(payload.getAppName()).isEqualTo(DEFAULT_APP_NAME);
        mExpect.that(payload.getAppIcon()).isEqualTo(DEFAULT_ICON_BYTES);
    }

    @Test(expected = NullPointerException.class)
    public void nullEntityId_throws() {
        new MigrationEntity(/*entityId=*/ null, APP_INFO_PAYLOAD);
    }

    @Test(expected = NullPointerException.class)
    public void nullPayload_throws() {
        new MigrationEntity(DEFAULT_ENTITY_ID, /*payload=*/ null);
    }
}
