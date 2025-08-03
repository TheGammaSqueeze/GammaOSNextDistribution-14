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

package com.android.car.carlauncher.datastore.launcheritem;

import androidx.annotation.Nullable;

import com.android.car.carlauncher.LauncherItemProto.LauncherItemListMessage;
import com.android.car.carlauncher.datastore.ProtoDataSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A ProtoDataSource that holds a single LauncherItemListMessage, which is an ordered list of
 * LauncherItemMessage, each representing a specific app item in the app grid.
 */
public class LauncherItemListSource extends ProtoDataSource<LauncherItemListMessage> {

    public LauncherItemListSource(File dataFileDirectory, String dataFileName) {
        super(dataFileDirectory, dataFileName);
    }

    @Override
    @Nullable
    protected LauncherItemListMessage parseDelimitedFrom(InputStream inputStream)
            throws IOException {
        return LauncherItemListMessage.parseDelimitedFrom(inputStream);
    }

    @Override
    protected void writeDelimitedTo(LauncherItemListMessage outputData, OutputStream outputStream)
            throws IOException {
        outputData.writeDelimitedTo(outputStream);
    }
}
