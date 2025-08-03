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

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.contentprovider.ContentProviderHandler;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs the legacy file path access tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class LegacyStorageHostTest extends BaseHostTestCase {

    private boolean mIsExternalStorageSetup;

    private ContentProviderHandler mContentProviderHandler;

    /**
     * Runs the given phase of LegacyFileAccessTest by calling into the device.
     * Throws an exception if the test phase fails.
     */
    void runDeviceTest(String phase) throws Exception {
        assertThat(runDeviceTests("android.scopedstorage.cts.legacy",
                "android.scopedstorage.cts.legacy.LegacyStorageTest", phase)).isTrue();
    }

    /**
     * <p> Keep in mind that granting WRITE_EXTERNAL_STORAGE also grants READ_EXTERNAL_STORAGE,
     * so in order to test a case where the reader has only WRITE, we must explicitly revoke READ.
     */
    private void grantPermissions(String... perms) throws Exception {
        int currentUserId = getCurrentUserId();
        for (String perm : perms) {
            executeShellCommand("pm grant --user %d android.scopedstorage.cts.legacy %s",
                    currentUserId, perm);
        }
    }

    private void revokePermissions(String... perms) throws Exception {
        int currentUserId = getCurrentUserId();
        for (String perm : perms) {
            executeShellCommand("pm revoke --user %d android.scopedstorage.cts.legacy %s",
                    currentUserId, perm);
        }
    }

    /**
     * Creates a file {@code filePath} in shell and may bypass Media Provider restrictions for
     * creating file.
     */
    private void createFileAsShell(String filePath) throws Exception {
        executeShellCommand("touch %s", filePath);
        assertThat(getDevice().doesFileExist(filePath)).isTrue();
    }

    private void setupExternalStorage() throws Exception {
        if (!mIsExternalStorageSetup) {
            runDeviceTest("setupExternalStorage");
            mIsExternalStorageSetup = true;
        }
    }

    @Before
    public void setup() throws Exception {
        mContentProviderHandler = new ContentProviderHandler(getDevice());
        mContentProviderHandler.setUp();
        setupExternalStorage();
        // Granting WRITE automatically grants READ as well, so we grant them both explicitly by
        // default in order to avoid confusion. Test cases that don't want any of those permissions
        // have to revoke the unwanted permissions.
        grantPermissions("android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE");
    }

    @After
    public void tearDown() throws Exception {
        mContentProviderHandler.tearDown();
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE");
    }

    @Test
    public void testCreateFilesInRandomPlaces_hasW() throws Exception {
        revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        executeShellCommand("mkdir -p /sdcard/Android/data/com.android.shell -m 2770");
        runDeviceTest("testCreateFilesInRandomPlaces_hasW");
    }

    @Test
    public void testCantInsertFilesInOtherAppPrivateDir_hasRW() throws Exception {
        runDeviceTest("testCantInsertFilesInOtherAppPrivateDir_hasRW");
    }

    @Test
    public void testCantUpdateFilesInOtherAppPrivateDir_hasRW() throws Exception {
        runDeviceTest("testCantUpdateFilesInOtherAppPrivateDir_hasRW");
    }

    @Test
    public void testCantInsertFilesInOtherAppPrivateDir_hasMES() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testCantInsertFilesInOtherAppPrivateDir_hasMES");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testCantUpdateFilesInOtherAppPrivateDir_hasMES() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testCantUpdateFilesInOtherAppPrivateDir_hasMES");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testCantInsertFilesInOtherAppPrivateDir_hasSystemGallery() throws Exception {
        runDeviceTest("testCantInsertFilesInOtherAppPrivateDir_hasSystemGallery");
    }

    @Test
    public void testCantUpdateFilesInOtherAppPrivateDir_hasSystemGallery() throws Exception {
        runDeviceTest("testCantUpdateFilesInOtherAppPrivateDir_hasSystemGallery");
    }

    @Test
    public void testMkdirInRandomPlaces_hasW() throws Exception {
        revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        executeShellCommand("mkdir -p /sdcard/Android/data/com.android.shell -m 2770");
        runDeviceTest("testMkdirInRandomPlaces_hasW");
    }

    @Test
    public void testReadOnlyExternalStorage_hasR() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE");
        runDeviceTest("testReadOnlyExternalStorage_hasR");
    }

    @Test
    public void testCantAccessExternalStorage() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE");
        runDeviceTest("testCantAccessExternalStorage");
    }

    @Test
    public void testListFiles_hasR() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE");
        runDeviceTest("testListFiles_hasR");
    }

    @Test
    public void testInsertHiddenFile() throws Exception {
        runDeviceTest("testInsertHiddenFile");
    }

    @Test
    public void testCanRename_hasRW() throws Exception {
        runDeviceTest("testCanRename_hasRW");
    }

    @Test
    public void testCanTrashOtherAndroidMediaFiles_hasRW() throws Exception {
        runDeviceTest("testCanTrashOtherAndroidMediaFiles_hasRW");
    }

    @Test
    public void testCantRename_hasR() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE");
        runDeviceTest("testCantRename_hasR");
    }

    @Test
    public void testCantRename_noStoragePermission() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE");
        runDeviceTest("testCantRename_noStoragePermission");
    }

    @Test
    public void testRenameDirectoryAndUpdateDB_hasW() throws Exception {
        runDeviceTest("testRenameDirectoryAndUpdateDB_hasW");
    }

    @Test
    public void testCanDeleteAllFiles_hasRW() throws Exception {
        runDeviceTest("testCanDeleteAllFiles_hasRW");
    }

    @Test
    public void testLegacyAppCanOwnAFile_hasW() throws Exception {
        runDeviceTest("testLegacyAppCanOwnAFile_hasW");
    }

    @Test
    public void testCreateAndRenameDoesntLeaveStaleDBRow_hasRW() throws Exception {
        runDeviceTest("testCreateAndRenameDoesntLeaveStaleDBRow_hasRW");
    }

    @Test
    public void testRenameDoesntInvalidateUri_hasRW() throws Exception {
        runDeviceTest("testRenameDoesntInvalidateUri_hasRW");
    }

    @Test
    public void testCanRenameAFileWithNoDBRow_hasRW() throws Exception {
        runDeviceTest("testCanRenameAFileWithNoDBRow_hasRW");
    }

    @Test
    public void testCreateDoesntUpsert() throws Exception {
        runDeviceTest("testCreateDoesntUpsert");
    }

    @Test
    public void testCaseInsensitivity() throws Exception {
        runDeviceTest("testAndroidDataObbCannotBeDeleted");
    }

    @Test
    public void testLegacyAppUpdatingOwnershipOfExistingEntry() throws Exception {
        runDeviceTest("testLegacyAppUpdatingOwnershipOfExistingEntry");
    }

    @Test
    public void testInsertWithUnsupportedMimeType() throws Exception {
        runDeviceTest("testInsertWithUnsupportedMimeType");
    }

    @Test
    public void testLegacySystemGalleryCanRenameImagesAndVideosWithoutDbUpdates() throws Exception {
        runDeviceTest("testLegacySystemGalleryCanRenameImagesAndVideosWithoutDbUpdates");
    }

    /**
     * (b/205673506): Test that legacy System Gallery can update() media file's releative_path to a
     * non default top level directory.
     */
    @Test
    public void testLegacySystemGalleryCanUpdateToExistingDirectory() throws Exception {
        runDeviceTest("testLegacySystemGalleryCanUpdateToExistingDirectory");
    }

    @Test
    public void testLegacySystemGalleryWithoutWESCannotRename() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE");
        runDeviceTest("testLegacySystemGalleryWithoutWESCannotRename");
    }

    @Test
    public void testLegacyWESCanRenameImagesAndVideosWithDbUpdates_hasW() throws Exception {
        runDeviceTest("testLegacyWESCanRenameImagesAndVideosWithDbUpdates_hasW");
    }

    @Test
    public void testScanUpdatesMetadataForNewlyAddedFile_hasRW() throws Exception {
        runDeviceTest("testScanUpdatesMetadataForNewlyAddedFile_hasRW");
    }

    @Test
    public void testInsertFromExternalDirsViaData() throws Exception {
        runDeviceTest("testInsertFromExternalDirsViaData");
    }

    @Test
    public void testUpdateToExternalDirsViaData() throws Exception {
        runDeviceTest("testUpdateToExternalDirsViaData");
    }

    @Test
    public void testInsertFromExternalDirsViaRelativePath() throws Exception {
        runDeviceTest("testInsertFromExternalDirsViaRelativePath");
    }

    @Test
    public void testUpdateToExternalDirsViaRelativePath() throws Exception {
        runDeviceTest("testUpdateToExternalDirsViaRelativePath");
    }

    @Test
    public void testInsertToOtherAppPrivateDirFails() throws Exception {
        runDeviceTest("testInsertToOtherAppPrivateDirFails");
    }

    private void allowAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts.legacy "
                    + op + " allow");
        }
    }

    private void denyAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts.legacy "
                    + op + " deny");
        }
    }
}
