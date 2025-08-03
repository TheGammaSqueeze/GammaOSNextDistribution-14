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

package android.backup.app;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.BackupRestoreEventLogger;
import android.app.backup.FullBackupDataOutput;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

public class LoggingFullBackupAgent extends BackupAgent {
    private static final String DATA_TYPE = "data_type";
    private static final String ERROR = "error";
    private static final String METADATA = "metadata";
    private static final int SUCCESS_COUNT = 1;
    private static final int FAIL_COUNT = 2;

    private BackupRestoreEventLogger mBackupRestoreEventLogger;

    @Override
    public void onCreate() {
        BackupManager backupManager = new BackupManager(getApplicationContext());
        mBackupRestoreEventLogger = backupManager.getBackupRestoreEventLogger(
                /* backupAgent */ this);
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        super.onFullBackup(data);

        mBackupRestoreEventLogger.logItemsBackedUp(DATA_TYPE, SUCCESS_COUNT);
        mBackupRestoreEventLogger.logBackupMetadata(DATA_TYPE, METADATA);
        mBackupRestoreEventLogger.logItemsBackupFailed(DATA_TYPE, FAIL_COUNT, ERROR);
    }

    @Override
    public void onRestoreFinished() {
        super.onRestoreFinished();

        mBackupRestoreEventLogger.logItemsRestored(DATA_TYPE, SUCCESS_COUNT);
        mBackupRestoreEventLogger.logRestoreMetadata(DATA_TYPE,  METADATA);
        mBackupRestoreEventLogger.logItemsRestoreFailed(DATA_TYPE, FAIL_COUNT, ERROR);
    }
}
