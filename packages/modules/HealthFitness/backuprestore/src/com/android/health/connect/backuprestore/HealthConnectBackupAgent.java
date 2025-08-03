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

package com.android.health.connect.backuprestore;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import android.annotation.NonNull;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.health.connect.HealthConnectManager;
import android.health.connect.restore.StageRemoteDataException;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * An intermediary to help with the transfer of HealthConnect data during device-to-device transfer.
 */
public class HealthConnectBackupAgent extends BackupAgent {
    private static final String TAG = "HealthConnectBackupAgent";
    private static final String BACKUP_DATA_DIR_NAME = "backup_data";
    private static final boolean DEBUG = false;

    private HealthConnectManager mHealthConnectManager;

    @Override
    public void onCreate() {
        if (DEBUG) {
            Slog.v(TAG, "onCreate()");
        }

        mHealthConnectManager = getHealthConnectService();
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        Set<String> backupFileNames =
                mHealthConnectManager.getAllBackupFileNames(
                        (data.getTransportFlags() & FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0);
        File backupDataDir = getBackupDataDir();
        backupFileNames.forEach(
                (fileName) -> {
                    File file = new File(backupDataDir, fileName);
                    try {
                        file.createNewFile();
                        pfdsByFileName.put(
                                fileName,
                                ParcelFileDescriptor.open(
                                        file, ParcelFileDescriptor.MODE_WRITE_ONLY));
                    } catch (IOException e) {
                        Slog.e(TAG, "Unable to backup " + fileName, e);
                    }
                });

        mHealthConnectManager.getAllDataForBackup(pfdsByFileName);

        File[] backupFiles = backupDataDir.listFiles(file -> !file.isDirectory());
        for (var file : backupFiles) {
            backupFile(file, data);
        }

        deleteBackupFiles();
    }

    @Override
    public void onRestoreFinished() {
        Slog.v(TAG, "Staging all of HC data");
        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        File[] filesToTransfer = getBackupDataDir().listFiles();

        // We work with a flat dir structure where all files to be transferred are sitting in this
        // dir itself.
        for (var file : filesToTransfer) {
            try {
                pfdsByFileName.put(file.getName(), ParcelFileDescriptor.open(file, MODE_READ_ONLY));
            } catch (Exception e) {
                // this should never happen as we are reading files from our own dir on the disk.
                Slog.e(TAG, "Unable to open restored file from disk.", e);
            }
        }

        mHealthConnectManager.stageAllHealthConnectRemoteData(
                pfdsByFileName,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void result) {
                        Slog.i(TAG, "Backup data successfully staged. Deleting all files.");
                        deleteBackupFiles();
                    }

                    @Override
                    public void onError(@NonNull StageRemoteDataException err) {
                        for (var fileNameToException : err.getExceptionsByFileNames().entrySet()) {
                            Slog.w(
                                    TAG,
                                    "Failed staging Backup file: "
                                            + fileNameToException.getKey()
                                            + " with error: "
                                            + fileNameToException.getValue());
                        }
                        deleteBackupFiles();
                    }
                });

        // close the FDs
        for (var pfdToFileName : pfdsByFileName.entrySet()) {
            try {
                pfdToFileName.getValue().close();
            } catch (IOException e) {
                Slog.e(TAG, "Unable to close restored file from disk.", e);
            }
        }
    }

    @Override
    public void onBackup(
            ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
        // we don't do incremental backup / restore.
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) {
        // we don't do incremental backup / restore.
    }

    @VisibleForTesting
    File getBackupDataDir() {
        File backupDataDir = new File(this.getFilesDir(), BACKUP_DATA_DIR_NAME);
        backupDataDir.mkdirs();
        return backupDataDir;
    }

    @VisibleForTesting
    HealthConnectManager getHealthConnectService() {
        return this.getSystemService(HealthConnectManager.class);
    }

    @VisibleForTesting
    void deleteBackupFiles() {
        Slog.i(TAG, "Deleting all files.");
        File[] filesToTransfer = getBackupDataDir().listFiles();
        for (var file : filesToTransfer) {
            file.delete();
        }
    }

    @VisibleForTesting
    void backupFile(File file, FullBackupDataOutput data) {
        fullBackupFile(file, data);
    }
}
