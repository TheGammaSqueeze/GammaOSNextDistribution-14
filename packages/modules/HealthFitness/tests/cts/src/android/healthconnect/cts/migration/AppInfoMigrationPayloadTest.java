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

import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppInfoMigrationPayloadTest {

    private static final String PACKAGE_NAME = "package.name";
    private static final String APP_NAME = "app";
    private static final byte[] ICON_BYTES = new byte[] {1, 2, 3};

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void createdWithoutSetters_validData() {
        final AppInfoMigrationPayload payload =
                new AppInfoMigrationPayload.Builder(PACKAGE_NAME, APP_NAME).build();

        mExpect.that(payload.getPackageName()).isEqualTo(PACKAGE_NAME);
        mExpect.that(payload.getAppName()).isEqualTo(APP_NAME);
        mExpect.that(payload.getAppIcon()).isNull();
    }

    @Test
    public void createdWithSetters_validData() {
        final AppInfoMigrationPayload payload =
                new AppInfoMigrationPayload.Builder("", "")
                        .setPackageName(PACKAGE_NAME)
                        .setAppName(APP_NAME)
                        .setAppIcon(ICON_BYTES)
                        .build();

        mExpect.that(payload.getPackageName()).isEqualTo(PACKAGE_NAME);
        mExpect.that(payload.getAppName()).isEqualTo(APP_NAME);
        mExpect.that(payload.getAppIcon()).isEqualTo(ICON_BYTES);
    }
}
