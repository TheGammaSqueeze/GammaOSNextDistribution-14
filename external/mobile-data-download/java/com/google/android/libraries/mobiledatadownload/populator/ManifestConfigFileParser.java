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

package com.google.android.libraries.mobiledatadownload.populator;

import android.net.Uri;

import com.google.android.libraries.mobiledatadownload.internal.logging.LogUtil;
import com.google.android.libraries.mobiledatadownload.populator.ManifestFileGroupPopulator.ManifestConfigParser;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadProtoOpener;
import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.DownloadConfigProto.ManifestConfig;

import java.util.concurrent.Executor;

/**
 * The default manifest parser that parses a given manifest file to {@link ManifestConfig}.
 *
 * <p>The format of the manifest file supported by this {@link ManifestConfigFileParser} is proto.
 * That is, the content of manifest file should be accepted by {@link
 * ManifestConfig#parseFrom(byte[])}.
 */
public final class ManifestConfigFileParser implements ManifestConfigParser {

    private static final String TAG = "ManifestConfigFileParser";

    private final SynchronousFileStorage fileStorage;
    private final Executor backgroundExecutor;

    public ManifestConfigFileParser(SynchronousFileStorage fileStorage, Executor backgroundExecutor) {
        this.fileStorage = fileStorage;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    public ListenableFuture<ManifestConfig> parse(Uri fileUri) {
        return PropagatedFutures.submit(
                () -> {
                    LogUtil.d("%s: Start parsing manifest file at %s", TAG, fileUri);
                    ManifestConfig manifestConfig =
                            fileStorage.open(fileUri, ReadProtoOpener.create(ManifestConfig.parser()));
                    return manifestConfig;
                },
                backgroundExecutor);
    }
}