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

package android.scopedstorage.cts;

import static android.scopedstorage.cts.lib.TestUtils.canOpenFileAs;
import static android.scopedstorage.cts.lib.TestUtils.createFileAs;
import static android.scopedstorage.cts.lib.TestUtils.fileExistsAs;
import static android.scopedstorage.cts.lib.TestUtils.listAs;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.TestApp;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class AppCloningDeviceTest {

    private static final String EMPTY_STRING = "";
    private static final String EXTERNAL_STORAGE_DCIM_PATH = "/storage/emulated/%d/DCIM";

    // An app with no permissions
    private static final TestApp APP_B_NO_PERMS = new TestApp("TestAppB",
            "android.scopedstorage.cts.testapp.B.noperms", 1, false,
            "CtsScopedStorageTestAppB.apk");

    @Test
    public void testInsertFilesInDirectoryViaMediaProvider() throws Exception {
        String dirPath = String.format(EXTERNAL_STORAGE_DCIM_PATH,
                Integer.parseInt(getCurrentUserId()));
        final File dir = new File(dirPath);
        //TODO: After b/285880821 is fixed, we can re-add the DCIM directory presence check.
        //assertThat(dir.exists()).isTrue();
        final File file = new File(dir, getFileToBeCreatedName());
        assertThat(createFileAs(APP_B_NO_PERMS, file.getPath())).isTrue();
        assertThat(canOpenFileAs(APP_B_NO_PERMS, file, true)).isTrue();
        assertThat(listAs(APP_B_NO_PERMS, dir.getPath())).contains(file.getName());
    }

    @Test
    public void testInsertFilesInDirectoryViaMediaProviderWithPathSpecified() throws Exception {
        String dirPath = String.format(EXTERNAL_STORAGE_DCIM_PATH,
                Integer.parseInt(getUserIdForPath()));
        final File dir = new File(dirPath);
        //TODO: After b/285880821 is fixed, we can re-add the DCIM directory presence check.
        //assertThat(dir.exists()).isTrue();
        final File file = new File(dir, getFileToBeCreatedName());
        assertThat(createFileAs(APP_B_NO_PERMS, file.getPath())).isTrue();
        assertThat(fileExistsAs(APP_B_NO_PERMS, file)).isTrue();
        assertThat(canOpenFileAs(APP_B_NO_PERMS, file, true)).isTrue();
        assertThat(listAs(APP_B_NO_PERMS, dir.getPath())).contains(file.getName());
    }

    @Test
    public void testGetFilesInDirectoryViaMediaProviderRespectsUserId() throws Exception {
        String dirPath = String.format(EXTERNAL_STORAGE_DCIM_PATH,
                Integer.parseInt(getCurrentUserId()));
        final File dir = new File(dirPath);
        assertThat(dir.exists()).isTrue();
        final File expectedFile = new File(dir, getFileToBeExpectedName());
        assertThat(listAs(APP_B_NO_PERMS, dir.getPath())).contains(expectedFile.getName());
        final File notExpectedFile = new File(dir, getFileNotToBeExpectedName());
        assertThat(listAs(APP_B_NO_PERMS, dir.getPath())).doesNotContain(notExpectedFile.getName());
    }

    private String getTestArgumentValueForGivenKey(String testArgumentKey) {
        final Bundle testArguments = InstrumentationRegistry.getArguments();
        String testArgumentValue = testArguments.getString(testArgumentKey, EMPTY_STRING);
        return testArgumentValue;
    }

    private String getUserIdForPath() {
        return getTestArgumentValueForGivenKey("userIdForPath");
    }

    private String getCurrentUserId() {
        return getTestArgumentValueForGivenKey("currentUserId");
    }

    private String getFileToBeCreatedName() {
        return getTestArgumentValueForGivenKey("fileToBeCreated");
    }

    private String getFileToBeExpectedName() {
        return getTestArgumentValueForGivenKey("fileExpectedToBePresent");
    }

    private String getFileNotToBeExpectedName() {
        return getTestArgumentValueForGivenKey("fileNotExpectedToBePresent");
    }
}
