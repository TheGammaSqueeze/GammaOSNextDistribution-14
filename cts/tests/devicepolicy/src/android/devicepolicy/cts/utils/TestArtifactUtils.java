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

package android.devicepolicy.cts.utils;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_EXTERNAL_STORAGE;
import static com.android.bedstead.nene.permissions.CommonPermissions.WRITE_EXTERNAL_STORAGE;

import android.annotation.NonNull;
import android.os.Environment;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

import java.io.File;
import java.io.IOException;

/**
 * Utility for dumping additional test artifacts for debugging.
 *
 * Requires android:requestLegacyExternalStorage="true" to create top level folder.
 * Logs are collected by FilePullerLogCollector specified in AndroidTest.xml.
 */
public class TestArtifactUtils {
    private static final String TAG = "TestArtifactUtils";
    private static final File BASE_DIR =
            new File(Environment.getExternalStorageDirectory(), "CtsDevicePolicyTestCases");

    /**
     * Dumps UI hierarchy for debugging UiAutomator issues.
     * @param suffix filename suffix (e.g. test name)
     */
    public static void dumpWindowHierarchy(String suffix) {
        withWritableBaseDir(() -> {
            File dumpFile = new File(BASE_DIR, "ui-hierarchy-dump-" + suffix);
            try {
                TestApis.ui().device().dumpWindowHierarchy(dumpFile);
            } catch (IOException e) {
                Log.e(TAG, "Failed to dump window hierarchy", e);
            }
        });
    }

    private static void withWritableBaseDir(@NonNull Runnable action) {
        try (PermissionContext p = TestApis.permissions()
                .withPermission(MANAGE_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)) {
            if (!BASE_DIR.exists() && !BASE_DIR.mkdir()) {
                Log.e(TAG, "Error creating " + BASE_DIR);
                return;
            }
            action.run();
        }
    }
}
