/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.healthconnect.exportimport;

import android.annotation.NonNull;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.utils.FilesUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Class that manages export related tasks. In this context, export means to make an encrypted copy
 * of Health Connect data that the user can store in some online storage solution.
 *
 * @hide
 */
public class ExportManager {
    private static final String EXPORT_DATABASE_DIR_NAME = "export_data";
    private static final String EXPORT_DATABASE_FILE_NAME = "healthconnect_export.db";
    private static final String TAG = "HealthConnectExportImport";

    // TODO(b/325599879): Change visibility once there is a wrapper.
    /** Writes the backup data into a local file. */
    public String exportLocally(@NonNull UserHandle userHandle) {
        Slog.d(TAG, "Incoming request to make a local copy for export");

        File exportDir =
                new File(
                        FilesUtil.getDataSystemCeHCDirectoryForUser(userHandle.getIdentifier()),
                        EXPORT_DATABASE_DIR_NAME);
        exportDir.mkdirs();
        File exportFile = new File(exportDir, EXPORT_DATABASE_FILE_NAME);

        ParcelFileDescriptor pfd;
        try {
            exportFile.createNewFile();
            pfd = ParcelFileDescriptor.open(exportFile, ParcelFileDescriptor.MODE_WRITE_ONLY);
            try (FileOutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor())) {
                Files.copy(
                        TransactionManager.getInitialisedInstance().getDatabasePath().toPath(),
                        outputStream);
            } catch (IOException | SecurityException e) {
                Slog.e(TAG, "Failed to send data for export", e);
            } finally {
                try {
                    pfd.close();
                } catch (IOException e) {
                    Slog.e(TAG, "Failed to close stream for export", e);
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to create file for export", e);
        }

        return exportFile.getPath();
    }
}
