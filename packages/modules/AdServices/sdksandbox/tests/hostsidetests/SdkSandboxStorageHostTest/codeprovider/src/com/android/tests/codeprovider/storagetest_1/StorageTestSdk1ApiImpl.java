/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tests.codeprovider.storagetest_1;

import android.content.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StorageTestSdk1ApiImpl extends IStorageTestSdk1Api.Stub {
    private final Context mContext;

    public StorageTestSdk1ApiImpl(Context sdkContext) {
        mContext = sdkContext;
    }

    @Override
    public void verifySharedStorageIsUsable() {
        String sharedPath = getSharedStoragePath();
        try {
            // Read the file
            String input = Files.readAllLines(Paths.get(sharedPath, "readme.txt")).get(0);

            // Create a dir
            Files.createDirectory(Paths.get(sharedPath, "dir"));
            // Write to a file
            Path filepath = Paths.get(sharedPath, "dir", "file");
            Files.createFile(filepath);
            Files.write(filepath, input.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void verifyPerSdkStorageIsUsable() {
        try {
            String sdkDataPath = mContext.getDataDir().toString();
            // Read the file
            String input = Files.readAllLines(Paths.get(sdkDataPath, "readme.txt")).get(0);

            // Create a dir
            Files.createDirectory(Paths.get(sdkDataPath, "dir"));
            // Write to a file
            Path filepath = Paths.get(sdkDataPath, "dir", "file");
            Files.createFile(filepath);
            Files.write(filepath, input.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void createFilesInStorage(int sizeInBytes) {
        if (getSharedStoragePath().equals(mContext.getDataDir().toString())) {
            throw new IllegalStateException("Per-SDK storage not accessible");
        }
        try {
            createFilesinStorage(sizeInBytes, getSharedStoragePath());
            createFilesinStorage(sizeInBytes, getSharedStorageCachePath());
            createFilesinStorage(sizeInBytes, mContext.getDataDir().toString());
            createFilesinStorage(sizeInBytes, mContext.getDataDir().toString() + "/cache");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void createFilesinStorage(int sizeInBytes, String path) throws IOException {
        final byte[] buffer = new byte[sizeInBytes];
        Files.createDirectory(Paths.get(path, "attribution"));
        final Path filepath = Paths.get(path, "attribution", "file");
        Files.createFile(filepath);
        Files.write(filepath, buffer);
    }

    private String getSharedStoragePath() {
        return mContext.getApplicationContext().getDataDir().toString();
    }

    private String getSharedStorageCachePath() {
        return mContext.getApplicationContext().getCacheDir().toString();
    }
}
