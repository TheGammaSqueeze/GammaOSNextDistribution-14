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

package android.healthconnect;

import static android.health.connect.datatypes.units.Length.fromMeters;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.internal.ParcelUtils;
import android.health.connect.migration.MigrationEntity;
import android.health.connect.migration.MigrationEntityParcel;
import android.health.connect.migration.RecordMigrationPayload;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class MigrationEntityParcelTest {
    private static final String APP_PACKAGE_NAME = "android.healthconnect.cts.app";
    private static final String ENTITY_ID = "height";
    private static final Instant END_TIME = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final HeightRecord HEIGHT_RECORD =
            new HeightRecord.Builder(
                            getMetadata(ENTITY_ID, APP_PACKAGE_NAME), END_TIME, fromMeters(3D))
                    .build();
    private static final MigrationEntity HEIGHT_MIGRATION_ENTITY =
            getRecordEntity(HEIGHT_RECORD, ENTITY_ID);

    @Test
    public void testMigrationEntityListParsing_usingParcel() {
        List<MigrationEntity> migrationEntityList = new ArrayList<>();
        migrationEntityList.add(HEIGHT_MIGRATION_ENTITY);
        int migrationEntityListSize = migrationEntityList.size();
        MigrationEntityParcel heightMigrationEntityParcel =
                new MigrationEntityParcel(migrationEntityList);
        Parcel migrationEntityParcel = writeToParcel(heightMigrationEntityParcel);
        migrationEntityParcel.setDataPosition(0);

        MigrationEntityParcel deserializedMigrationEntityParcel =
                MigrationEntityParcel.CREATOR.createFromParcel(migrationEntityParcel);
        List<MigrationEntity> deserializedMigrationEntityList =
                deserializedMigrationEntityParcel.getMigrationEntities();

        assertDeserializedListSize(migrationEntityListSize, deserializedMigrationEntityList);
        assertDeserializedHeightRecord(
                migrationEntityListSize, migrationEntityList, deserializedMigrationEntityList);
    }

    @Test
    public void testMigrationEntityListParsing_usingSharedMemory() {
        int numRequiredEntities = getRequiredNumberOfEntities();
        List<MigrationEntity> migrationEntityList = new ArrayList<>();
        for (int i = 0; i < 2 * numRequiredEntities; i++) {
            migrationEntityList.add(HEIGHT_MIGRATION_ENTITY);
        }
        int migrationEntityListSize = migrationEntityList.size();
        MigrationEntityParcel heightMigrationEntityParcel =
                new MigrationEntityParcel(migrationEntityList);
        Parcel migrationEntityParcel = writeToParcel(heightMigrationEntityParcel);
        migrationEntityParcel.setDataPosition(0);

        MigrationEntityParcel deserializedMigrationEntityParcel =
                MigrationEntityParcel.CREATOR.createFromParcel(migrationEntityParcel);
        List<MigrationEntity> deserializedMigrationEntityList =
                deserializedMigrationEntityParcel.getMigrationEntities();

        assertDeserializedListSize(migrationEntityListSize, deserializedMigrationEntityList);
        assertDeserializedHeightRecord(
                migrationEntityListSize, migrationEntityList, deserializedMigrationEntityList);
    }

    @Test
    public void testParcelType_usingSharedMemory() {
        int numRequiredEntities = getRequiredNumberOfEntities();
        List<MigrationEntity> migrationEntityList = new ArrayList<>();
        for (int i = 0; i < 2 * numRequiredEntities; i++) {
            migrationEntityList.add(HEIGHT_MIGRATION_ENTITY);
        }
        MigrationEntityParcel heightMigrationEntityParcel =
                new MigrationEntityParcel(migrationEntityList);
        Parcel migrationEntityParcel = writeToParcel(heightMigrationEntityParcel);
        int parcelType = getParcelType(migrationEntityParcel);

        assertThat(parcelType).isEqualTo(ParcelUtils.USING_SHARED_MEMORY);
    }

    @Test
    public void testParcelType_usingParcel() {
        List<MigrationEntity> migrationEntityList = new ArrayList<>();
        migrationEntityList.add(HEIGHT_MIGRATION_ENTITY);
        MigrationEntityParcel heightMigrationEntityParcel =
                new MigrationEntityParcel(migrationEntityList);
        Parcel migrationEntityParcel = writeToParcel(heightMigrationEntityParcel);
        int parcelType = getParcelType(migrationEntityParcel);

        assertThat(parcelType).isEqualTo(ParcelUtils.USING_PARCEL);
    }

    private static Metadata getMetadata(String clientRecordId, String packageName) {
        return new Metadata.Builder()
                .setClientRecordId(clientRecordId)
                .setId(UUID.randomUUID().toString())
                .setDataOrigin(new DataOrigin.Builder().setPackageName(packageName).build())
                .setDevice(new Device.Builder().setManufacturer("Device").setModel("Model").build())
                .build();
    }

    private static MigrationEntity getRecordEntity(Record record, String entityId) {
        return new MigrationEntity(
                entityId,
                new RecordMigrationPayload.Builder(
                                record.getMetadata().getDataOrigin().getPackageName(),
                                "Example App",
                                record)
                        .build());
    }

    /** Calculates the number of entities we need to cross the IPC_PARCEL_LIMIT threshold */
    private static int getRequiredNumberOfEntities() {
        Parcel migrationEntityRecordParcel = Parcel.obtain();
        HEIGHT_MIGRATION_ENTITY.getPayload().writeToParcel(migrationEntityRecordParcel, 0);
        int migrationEntityDataParcelSize = migrationEntityRecordParcel.dataSize();
        return ParcelUtils.IPC_PARCEL_LIMIT / migrationEntityDataParcelSize;
    }

    private static Parcel writeToParcel(MigrationEntityParcel heightMigrationEntityParcel) {
        Parcel migrationEntityParcel = Parcel.obtain();
        heightMigrationEntityParcel.writeToParcel(migrationEntityParcel, 0);
        return migrationEntityParcel;
    }

    private static int getParcelType(Parcel migrationEntityParcel) {
        migrationEntityParcel.setDataPosition(0);
        int parcelType = migrationEntityParcel.readInt();
        migrationEntityParcel.recycle();
        return parcelType;
    }

    private static void assertDeserializedListSize(
            int migrationEntityListSize, List<MigrationEntity> deserializedMigrationEntityList) {
        int deserializedMigrationEntityListSize = deserializedMigrationEntityList.size();
        assertThat(migrationEntityListSize).isEqualTo(deserializedMigrationEntityListSize);
    }

    private static void assertDeserializedHeightRecord(
            int migrationEntityListSize,
            List<MigrationEntity> migrationEntityList,
            List<MigrationEntity> deserializedMigrationEntityList) {
        for (int i = 0; i < migrationEntityListSize; i++) {
            RecordMigrationPayload recordMigrationPayload =
                    (RecordMigrationPayload) migrationEntityList.get(i).getPayload();
            HeightRecord heightRecord = (HeightRecord) recordMigrationPayload.getRecord();

            RecordMigrationPayload deserializedRecordMigrationPayload =
                    (RecordMigrationPayload) deserializedMigrationEntityList.get(i).getPayload();
            HeightRecord deserializedHeightRecord =
                    (HeightRecord) deserializedRecordMigrationPayload.getRecord();

            assertThat(heightRecord).isEqualTo(deserializedHeightRecord);
        }
    }
}
