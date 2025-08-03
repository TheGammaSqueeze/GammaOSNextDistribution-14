/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.packagemanager.stats.device;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class IncrementalAppErrorStatsTestsHelper {
    private static final String HELPER_ARG_APK_PATH = "remoteApkPath";
    private static final String HELPER_ARG_PKG_NAME = "packageName";
    private static final String PAGE_INDEX_TO_BLOCK = "pageIndexToBlock";
    // The apk contains a video resource file which has 9 pages. We only need to block 1 page
    // such that the loading progress is never completed.
    private static final String FILE_PAGE_TO_BLOCK = "res/raw/colors_video.mp4";
    private static final int BLOCK_SIZE = 4096;
    // Instrumentation status code used to write resolution to metrics
    private static final int INST_STATUS_IN_PROGRESS = 2;

    @Test
    public void getPageIndexToBlock() throws IOException {
        final Bundle testArgs = InstrumentationRegistry.getArguments();
        final String apkPath = testArgs.getString(HELPER_ARG_APK_PATH);
        assertThat(apkPath).isNotNull();
        assertThat(new File(apkPath).exists()).isTrue();
        ZipFile zip = new ZipFile(apkPath);
        final ZipArchiveEntry info = zip.getEntry(FILE_PAGE_TO_BLOCK);
        assertThat(info.getSize()).isGreaterThan(BLOCK_SIZE);
        assertThat(info.getDataOffset()).isGreaterThan(BLOCK_SIZE * 2);
        final int pageToBlock = (int) info.getDataOffset() / 4096 + 1;
        // Pass data to the host-side test
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Bundle bundle = new Bundle();
        bundle.putString(PAGE_INDEX_TO_BLOCK, String.valueOf(pageToBlock));
        inst.sendStatus(INST_STATUS_IN_PROGRESS, bundle);
    }

    @Test
    public void loadingApks() throws Exception {
        final Bundle testArgs = InstrumentationRegistry.getArguments();
        final String packageName = testArgs.getString(HELPER_ARG_PKG_NAME);
        assertThat(packageName).isNotNull();
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final ApplicationInfo appInfo = context.getPackageManager()
                .getApplicationInfo(packageName, 0);
        final String codePath = appInfo.sourceDir;
        final String apkDir = codePath.substring(0, codePath.lastIndexOf('/'));
        for (String apkName : new File(apkDir).list()) {
            final String apkPath = apkDir + "/" + apkName;
            if (new File(apkPath).isFile()) {
                try {
                    Files.readAllBytes(Paths.get(apkPath));
                } catch (IOException ignored) {
                    // Probably hitting pages that we are intentionally blocking
                }
            }
        }
    }
}
