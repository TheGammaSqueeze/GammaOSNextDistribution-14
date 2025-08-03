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

import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.migration.RecordMigrationPayload;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class RecordMigrationPayloadTest {

    private static final String DEFAULT_ORIGIN_PACKAGE_NAME = "package.name";
    private static final String DEFAULT_ORIGIN_APP_NAME = "app";
    private static final Record DEFAULT_RECORD =
            new StepsRecord.Builder(
                            new Metadata.Builder()
                                    .setDevice(
                                            new Device.Builder()
                                                    .setManufacturer("Device")
                                                    .setModel("Model")
                                                    .build())
                                    .build(),
                            /* startTime= */ Instant.now(),
                            /* endTime= */ Instant.now().plusMillis(1000),
                            /* count= */ 10)
                    .build();

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void createdWithoutSetters_validData() {
        final RecordMigrationPayload payload =
                new RecordMigrationPayload.Builder(
                                DEFAULT_ORIGIN_PACKAGE_NAME,
                                DEFAULT_ORIGIN_APP_NAME,
                                DEFAULT_RECORD)
                        .build();

        mExpect.that(payload.getOriginPackageName()).isEqualTo(DEFAULT_ORIGIN_PACKAGE_NAME);
        mExpect.that(payload.getOriginAppName()).isEqualTo(DEFAULT_ORIGIN_APP_NAME);
        mExpect.that(payload.getRecord()).isEqualTo(DEFAULT_RECORD);
    }

    @Test
    public void createdWithSetters_validData() {
        final RecordMigrationPayload payload =
                new RecordMigrationPayload.Builder("", "", DEFAULT_RECORD)
                        .setOriginPackageName(DEFAULT_ORIGIN_PACKAGE_NAME)
                        .setOriginAppName(DEFAULT_ORIGIN_APP_NAME)
                        .setRecord(DEFAULT_RECORD)
                        .build();

        mExpect.that(payload.getOriginPackageName()).isEqualTo(DEFAULT_ORIGIN_PACKAGE_NAME);
        mExpect.that(payload.getOriginAppName()).isEqualTo(DEFAULT_ORIGIN_APP_NAME);
        mExpect.that(payload.getRecord()).isEqualTo(DEFAULT_RECORD);
    }

    @Test(expected = NullPointerException.class)
    public void nullOriginPackageName_throws() {
        new RecordMigrationPayload.Builder(
                /*originPackageName=*/ null, DEFAULT_ORIGIN_APP_NAME, DEFAULT_RECORD);
    }

    @Test(expected = NullPointerException.class)
    public void nullOriginAppName_throws() {
        new RecordMigrationPayload.Builder(
                DEFAULT_ORIGIN_PACKAGE_NAME, /*originAppName=*/ null, DEFAULT_RECORD);
    }

    @Test(expected = NullPointerException.class)
    public void nullRecord_throws() {
        new RecordMigrationPayload.Builder(
                DEFAULT_ORIGIN_PACKAGE_NAME, DEFAULT_ORIGIN_APP_NAME, /*record=*/ null);
    }
}
