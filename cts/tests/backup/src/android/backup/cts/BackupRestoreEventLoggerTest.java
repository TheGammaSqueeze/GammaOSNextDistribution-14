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

package android.backup.cts;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.UiAutomation;
import android.app.backup.BackupManager;
import android.app.backup.BackupManagerMonitor;
import android.app.backup.BackupObserver;
import android.app.backup.BackupRestoreEventLogger.DataTypeResult;
import android.app.backup.RestoreObserver;
import android.app.backup.RestoreSession;
import android.content.Context;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull
public class BackupRestoreEventLoggerTest extends BaseBackupCtsTest {
    private static final String BACKUP_APP_PACKAGE
            = "android.cts.backup.backuprestoreeventloggerapp";
    private static final int OPERATION_TIMEOUT_SECONDS = 30;
    private static final int BACKUP_APP_RESTART_SLEEP_MS = 3_000;


    // Copied from LoggingFullBackupAgent.java
    private static final String DATA_TYPE = "data_type";
    private static final String ERROR = "error";
    private static final String METADATA = "metadata";
    private static final int SUCCESS_COUNT = 1;
    private static final int FAIL_COUNT = 2;

    private final TestBackupManagerMonitor mBackupMonitor = new TestBackupManagerMonitor();
    private final TestBackupManagerMonitor mRestoreMonitor = new TestBackupManagerMonitor();

    private UiAutomation mUiAutomation;
    private BackupManager mBackupManager;
    private BackupObserver mBackupObserver;
    private RestoreObserver mRestoreObserver;
    private CountDownLatch mOperationLatch;
    private RestoreSession mRestoreSession;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Context context = getInstrumentation().getTargetContext();

        mUiAutomation = getInstrumentation().getUiAutomation();
        mBackupManager = new BackupManager(context);
        mBackupObserver = new TestBackupObserver();
        mRestoreObserver = new TestRestoreObserver();

        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.BACKUP);
    }

    @Override
    public void tearDown() throws Exception {

        if (mRestoreSession != null){
            mRestoreSession.endRestoreSession();
        }
        mUiAutomation.dropShellPermissionIdentity();

        super.tearDown();
    }

    public void testBackupRestoreRoundTrip_logsSentToMonitor() throws Exception {
        if (!isBackupSupported()) {
            return;
        }

        // Ensure the app is not in stopped state.
        createTestFileOfSize(BACKUP_APP_PACKAGE, /* size */ 1);

        // Run a backup.
        mOperationLatch = new CountDownLatch(/* count */ 1);
        mBackupManager.requestBackup(new String[] { BACKUP_APP_PACKAGE },
                /* observer */ mBackupObserver, mBackupMonitor, /* flags */ 0);
        boolean backupFinished = mOperationLatch.await(OPERATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);

        // Sleep to allow the app to be killed, so that this doesn't disrupt the restore operation
        Thread.sleep(BACKUP_APP_RESTART_SLEEP_MS);

        // Run a restore.
        mOperationLatch = new CountDownLatch(/* count */ 1);
        mRestoreSession = mBackupManager.beginRestoreSession();
        mRestoreSession.restorePackage(BACKUP_APP_PACKAGE, mRestoreObserver, mRestoreMonitor);
        boolean restoreFinished = mOperationLatch.await(OPERATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);


        assertThat(backupFinished).isTrue();
        assertThat(restoreFinished).isTrue();
        assertLoggingResultsAreCorrect(mBackupMonitor);
        assertLoggingResultsAreCorrect(mRestoreMonitor);
    }

    /**
     * Assert the logging results are consistent with what is logged in LoggingFullBackupAgent.java.
     */
    private void assertLoggingResultsAreCorrect(TestBackupManagerMonitor monitor) {
        assertThat(monitor.mAgentBundle).isNotNull();
        List<DataTypeResult> dataTypeList = monitor.mAgentBundle.getParcelableArrayList(
                BackupManagerMonitor.EXTRA_LOG_AGENT_LOGGING_RESULTS,
                DataTypeResult.class);
        assertThat(dataTypeList.size()).isEqualTo(/* expected */ 1);
        DataTypeResult dataTypeResult = dataTypeList.get(/* index */ 0);
        assertThat(dataTypeResult.getDataType()).isEqualTo(DATA_TYPE);
        assertThat(dataTypeResult.getSuccessCount()).isEqualTo(SUCCESS_COUNT);
        assertThat(dataTypeResult.getErrors().get(ERROR)).isEqualTo(FAIL_COUNT);
    }

    private static class TestBackupManagerMonitor extends BackupManagerMonitor {
        private Bundle mAgentBundle = null;

        @Override
        public void onEvent(Bundle event) {
            if (event.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID)
                    == BackupManagerMonitor.LOG_EVENT_ID_AGENT_LOGGING_RESULTS) {
                mAgentBundle = event;
            }
        }
    }

    private class TestBackupObserver extends BackupObserver {
        @Override
        public void backupFinished(int status) {
            assertThat(status).isEqualTo(/* expected */ 0);
            mOperationLatch.countDown();
        }
    }

    private class TestRestoreObserver extends RestoreObserver {

        @Override
        public void restoreFinished(int error) {
            assertThat(error).isEqualTo(/* expected */ 0);
            mOperationLatch.countDown();
        }
    }
}
