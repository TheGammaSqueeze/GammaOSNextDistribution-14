/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.scopedstorage.cts.host;

import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs the core ScopedStorageTest tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ScopedStorageCoreHostTest extends BaseHostTestCase {
    private boolean mIsExternalStorageSetup = false;

    /**
     * Runs the given phase of ScopedStorageTest by calling into the device.
     * Throws an exception if the test phase fails.
     */
    void runDeviceTest(String phase) throws Exception {
        assertTrue(runDeviceTests("android.scopedstorage.cts",
                "android.scopedstorage.cts.ScopedStorageTest", phase));

    }

    private void setupExternalStorage() throws Exception {
        if (!mIsExternalStorageSetup) {
            runDeviceTest("setupExternalStorage");
            mIsExternalStorageSetup = true;
        }
    }

    @Before
    public void setup() throws Exception {
        setupExternalStorage();
        executeShellCommand("mkdir /sdcard/Android/data/com.android.shell -m 2770");
        executeShellCommand("mkdir /sdcard/Android/data/com.android.shell/files -m 2770");
    }

    @Before
    public void revokeStoragePermissions() throws Exception {
        revokePermissions(
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_IMAGES");
    }

    @After
    public void tearDown() throws Exception {
        executeShellCommand("rm -r /sdcard/Android/data/com.android.shell");
    }

    @Test
    public void testManageExternalStorageCanCreateFilesAnywhere() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageCanCreateFilesAnywhere");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testManageExternalStorageReaddir() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageReaddir");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testAccess_file() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE");
        grantPermissions("android.permission.READ_MEDIA_IMAGES");
        try {
            runDeviceTest("testAccess_file");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
            revokePermissions("android.permission.READ_MEDIA_IMAGES");
        }
    }

    @Test
    public void testAccess_MediaFile() throws Exception {
        grantPermissions("android.permission.READ_MEDIA_IMAGES");
        grantPermissions("android.permission.READ_MEDIA_AUDIO");
        grantPermissions("android.permission.READ_MEDIA_VIDEO");
        try {
            runDeviceTest("testAccess_MediaFile");
        } finally {
            revokePermissions("android.permission.READ_MEDIA_IMAGES");
            revokePermissions("android.permission.READ_MEDIA_AUDIO");
            revokePermissions("android.permission.READ_MEDIA_VIDEO");
        }
    }

    @Test
    public void testAccess_OnlyAudioFile() throws Exception {
        grantPermissions("android.permission.READ_MEDIA_AUDIO");
        try {
            runDeviceTest("testAccess_OnlyAudioFile");
        } finally {
            revokePermissions("android.permission.READ_MEDIA_AUDIO");
        }
    }

    @Test
    public void testAccess_OnlyVideoFile() throws Exception {
        grantPermissions("android.permission.READ_MEDIA_VIDEO");
        try {
            runDeviceTest("testAccess_OnlyVideoFile");
        } finally {
            revokePermissions("android.permission.READ_MEDIA_VIDEO");
        }
    }

    @Test
    public void testAccess_OnlyImageFile() throws Exception {
        grantPermissions("android.permission.READ_MEDIA_IMAGES");
        try {
            runDeviceTest("testAccess_OnlyImageFile");
        } finally {
            revokePermissions("android.permission.READ_MEDIA_IMAGES");
        }
    }

    @Test
    public void testAccess_MediaFileLegacy() throws Exception {
        runDeviceTest("testAccess_MediaFileLegacy");
    }

    @Test
    public void testAccess_MediaFileWithRES() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testAccess_MediaFileWithRES");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        }
    }

    @Test
    public void testAccess_directory() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testAccess_directory");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE");
        }
    }

    private void grantPermissions(String... perms) throws Exception {
        int currentUserId = getCurrentUserId();
        for (String perm : perms) {
            executeShellCommand("pm grant --user %d android.scopedstorage.cts %s",
                    currentUserId, perm);
        }
    }

    private void revokePermissions(String... perms) throws Exception {
        int currentUserId = getCurrentUserId();
        for (String perm : perms) {
            executeShellCommand("pm revoke --user %d android.scopedstorage.cts %s",
                    currentUserId, perm);
        }
    }

    private void allowAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts %s allow", op);
        }
    }

    private void denyAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts %s deny", op);
        }
    }
}
