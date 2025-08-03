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

package android.security.cts;

import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

public class ZipPathValidatorHostTest extends CompatChangeGatingTestCase {
    protected static final String TEST_APK = "CtsZipValidateApp.apk";
    protected static final String TEST_PKG = "android.security.cts";

    private static final long VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL = 242716250L;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
    }

    public void testNewZipFile_whenZipFileHasDangerousEntriesAndChangeEnabled_throws() throws
            Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "newZipFile_whenZipFileHasDangerousEntriesAndChangeEnabled_throws",
                /* enabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL),
                /* disabledChanges */ ImmutableSet.of());
    }

    public void
            testZipInputStreamGetNextEntry_whenZipFileHasDangerousEntriesAndChangeEnabled_throws()
                    throws Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "zipInputStreamGetNextEntry_whenZipFileHasDangerousEntriesAndChangeEnabled_throws",
                /* enabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL),
                /* disabledChanges */ ImmutableSet.of());
    }

    public void testNewZipFile_whenZipFileHasNormalEntriesAndChangeEnabled_doesNotThrow() throws
            Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "newZipFile_whenZipFileHasNormalEntriesAndChangeEnabled_doesNotThrow",
                /* enabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL),
                /* disabledChanges */ ImmutableSet.of());
    }

    public void
            testZipInputStreamGetNextEntry_whenZipFileHasNormalEntriesAndChangeEnabled_doesNotThrow()
                    throws Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "zipInputStreamGetNextEntry_whenZipFileHasNormalEntriesAndChangeEnabled_"
                        + "doesNotThrow",
                /* enabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL),
                /* disabledChanges */ ImmutableSet.of());
    }

    public void
            testNewZipFile_whenZipFileHasNormalAndDangerousEntriesAndChangeDisabled_doesNotThrow()
                    throws Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "newZipFile_whenZipFileHasNormalAndDangerousEntriesAndChangeDisabled_doesNotThrow",
                /* enabledChanges */ ImmutableSet.of(),
                /* disabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL));
    }

    public void
            testZipInputStreamGetNextEntry_whenZipFileHasNormalAndDangerousEntriesAndChangeDisabled_doesNotThrow()
                    throws Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "zipInputStreamGetNextEntry_whenZipFileHasNormalAndDangerousEntriesAnd"
                        + "ChangeDisabled_doesNotThrow",
                /* enabledChanges */ ImmutableSet.of(),
                /* disabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL));
    }

    public void testLoadingApksWillNotCallZipPathValidator_changeEnabled() throws Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "loadingApksWillNotCallZipPathValidator_changeEnabledOrDisabled",
                /* enabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL),
                /* disabledChanges */ ImmutableSet.of());
    }

    public void testLoadingApksWillNotCallZipPathValidator_changeDisabled() throws Exception {
        runDeviceCompatTest(TEST_PKG,
                ".ZipPathValidatorDeviceTest",
                "loadingApksWillNotCallZipPathValidator_changeEnabledOrDisabled",
                /* enabledChanges */ ImmutableSet.of(),
                /* disabledChanges */ ImmutableSet.of(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL));
    }
}
