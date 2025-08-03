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

package android.healthconnect.tests.backuprestore;

import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.health.connect.ReadRecordsRequest;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.datatypes.BodyFatRecord;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.restore.StageRemoteDataException;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.healthconnect.integrationtests.backuprestore.R;
import android.os.FileUtils;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Integration test for the backup-restore functionality of HealthConnect service. */
@RunWith(AndroidJUnit4.class)
public class BackupRestoreApiTest {
    public static final String MANAGE_HEALTH_DATA = HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION;
    private static final String TAG = "BackupRestoreIntegrationTest";
    private Context mContext;
    private HealthConnectManager mService;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mService = mContext.getSystemService(HealthConnectManager.class);
    }

    // This test uses a db created using DATABASE_VERSION 7 (Last bumped on 2023-03-17T17:23:29Z).
    // The health db is sitting directly in the app and is staged directly with the HC service.
    // And then the records from this staged db are merged.
    // Ideally this db should stay good forever, as the newer versions of HC code are guaranteed to
    // work with the older versions of the health db.
    // However, if for some reason there's any issue, please try by creating another db (probably
    // using a newer version of HF module) and replacing the db in the app's resources.
    @Test
    public void testMergeStagedData_withEmptyHealthDb_mergesAllData() throws Exception {
        // Step 0: reset everything as some tests leave stuff behind.
        verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        deleteAllStagedRemoteData();

        List<BodyFatRecord> bodyFatRecordsRead =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyFatRecord.class).build());
        List<HeightRecord> heightRecordsRead =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class).build());

        assertThat(bodyFatRecordsRead).isEmpty();
        assertThat(heightRecordsRead).isEmpty();

        // Step 1: prepare the backup data for restore.
        prepareDataForRestore();

        // Step 2: Restore the db and the grant times.
        restoreBackupData();
        Thread.sleep(TimeUnit.SECONDS.toMillis(10)); // give some time for merge to finish.

        // Step 3: Assert that the restored db (with the service) has the records from the db with
        // the app.
        heightRecordsRead =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeightRecord.class).build());
        assertThat(heightRecordsRead.size()).isEqualTo(2);
        bodyFatRecordsRead =
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(BodyFatRecord.class).build());
        assertThat(bodyFatRecordsRead.size()).isEqualTo(1);

        File backupDataDir = getBackupDataDir();
        SQLiteDatabase db =
                SQLiteDatabase.openDatabase(
                        new File(backupDataDir, "healthconnect_staged.db"),
                        new SQLiteDatabase.OpenParams.Builder().build());
        Cursor cursor = db.rawQuery("select * from height_record_table", null);
        ArraySet<Double> heights = new ArraySet<>();
        while (cursor.moveToNext()) {
            heights.add(cursor.getDouble(cursor.getColumnIndex("height")));
        }

        cursor = db.rawQuery("select * from body_fat_record_table", null);
        ArraySet<Double> bodyFats = new ArraySet<>();
        while (cursor.moveToNext()) {
            bodyFats.add(cursor.getDouble(cursor.getColumnIndex("percentage")));
        }

        for (var heightRecordRead : heightRecordsRead) {
            assertThat(heights).contains(heightRecordRead.getHeight().getInMeters());
        }

        for (var bodyFatRecordRead : bodyFatRecordsRead) {
            assertThat(bodyFats).contains(bodyFatRecordRead.getPercentage().getValue());
        }

        verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        deleteAllStagedRemoteData();
    }

    private File getBackupDataDir() {
        File backupDataDir = new File(mContext.getFilesDir(), "backup_data");
        backupDataDir.mkdirs();
        return backupDataDir;
    }

    private void prepareDataForRestore() throws Exception {
        File backupDataDir = getBackupDataDir();
        try (InputStream in = mContext.getResources().openRawResource(R.raw.healthconnect_staged);
                FileOutputStream out =
                        new FileOutputStream(new File(backupDataDir, "healthconnect_staged.db"))) {
            FileUtils.copy(in, out);
            out.getFD().sync();
        }
        try (InputStream in =
                        mContext.getResources()
                                .openRawResource(R.raw.health_permissions_first_grant_times);
                FileOutputStream out =
                        new FileOutputStream(
                                new File(
                                        backupDataDir,
                                        "health-permissions-first-grant-times.xml"))) {
            FileUtils.copy(in, out);
            out.getFD().sync();
        }
    }

    private void restoreBackupData() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        try {
            File[] filesToRestore = getBackupDataDir().listFiles();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            for (var file : filesToRestore) {
                pfdsByFileName.put(file.getName(), ParcelFileDescriptor.open(file, MODE_READ_ONLY));
            }

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA");
            mService.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {}
                    });
            assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private <T extends Record> List<T> readRecords(ReadRecordsRequest<T> request)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(mService).isNotNull();
        assertThat(request.getRecordType()).isNotNull();
        AtomicReference<List<T>> response = new AtomicReference<>();
        AtomicReference<HealthConnectException> healthConnectExceptionAtomicReference =
                new AtomicReference<>();
        mService.readRecords(
                request,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(ReadRecordsResponse<T> result) {
                        response.set(result.getRecords());
                        latch.countDown();
                    }

                    @Override
                    public void onError(HealthConnectException exception) {
                        Log.e(TAG, exception.getMessage());
                        healthConnectExceptionAtomicReference.set(exception);
                        latch.countDown();
                    }
                });
        assertThat(latch.await(3, TimeUnit.SECONDS)).isEqualTo(true);
        if (healthConnectExceptionAtomicReference.get() != null) {
            throw healthConnectExceptionAtomicReference.get();
        }
        return response.get();
    }

    public void verifyDeleteRecords(DeleteUsingFiltersRequest request) throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<HealthConnectException> exceptionAtomicReference =
                    new AtomicReference<>();
            assertThat(mService).isNotNull();
            mService.deleteRecords(
                    request,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(HealthConnectException healthConnectException) {
                            exceptionAtomicReference.set(healthConnectException);
                            latch.countDown();
                        }
                    });
            assertThat(latch.await(3, TimeUnit.SECONDS)).isEqualTo(true);
            if (exceptionAtomicReference.get() != null) {
                throw exceptionAtomicReference.get();
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
