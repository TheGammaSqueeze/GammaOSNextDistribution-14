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

package android.scopedstorage.cts.bypassdatabase;

import static android.app.AppOpsManager.permissionToOp;
import static android.scopedstorage.cts.lib.TestUtils.allowAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.createFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAsNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.denyAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.getPicturesDir;
import static android.scopedstorage.cts.lib.TestUtils.renameFileAs;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.Manifest;
import android.app.AppOpsManager;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.ScopedStorageBaseDeviceTest;
import android.scopedstorage.cts.lib.TestUtils;
import android.util.Log;

import androidx.test.filters.SdkSuppress;

import com.android.cts.install.lib.TestApp;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;

/**
 * Device-side test suite to verify file path operations optionally bypassing database operations.
 */
@RunWith(Parameterized.class)
public class BypassDatabaseOperationsTest extends ScopedStorageBaseDeviceTest {
    static final String TAG = "BypassDatabaseOperationsTest";
    // An app with READ_EXTERNAL_STORAGE permission. Targets current SDK and is preinstalled
    private static final TestApp APP_SYSTEM_GALLERY_DEFAULT = new TestApp("TestAppA",
            "android.scopedstorage.cts.testapp.A.withres", 1, false,
            "CtsScopedStorageTestAppA.apk");
    // An app with READ_EXTERNAL_STORAGE_PERMISSION. Targets current SDK and has
    // requestRawExternalStorageAccess=true
    private static final TestApp APP_SYSTEM_GALLERY_BYPASS_DB = new TestApp(
            "TestAppSystemGalleryBypassDB",
            "android.scopedstorage.cts.testapp.SystemGalleryBypassDB", 1, false,
            "CtsScopedStorageTestAppSystemGalleryBypassDB.apk");
    // An app with READ_EXTERNAL_STORAGE_PERMISSION. Targets targetSDK=30.
    private static final TestApp APP_SYSTEM_GALLERY_30 = new TestApp("TestAppE30",
            "android.scopedstorage.cts.testapp.E30", 1, false,
            "CtsScopedStorageTestAppE30.apk");
    // An app with READ_EXTERNAL_STORAGE_PERMISSION. Targets targetSDK=30 and has
    // requestRawExternalStorageAccess=true
    private static final TestApp APP_SYSTEM_GALLERY_30_BYPASS_DB = new TestApp(
            "TestAppSystemGallery30BypassDB",
            "android.scopedstorage.cts.testapp.SystemGallery30BypassDB", 1, false,
            "CtsScopedStorageTestAppSystemGallery30BypassDB.apk");
    // An app that has file manager (MANAGE_EXTERNAL_STORAGE) permission.
    // Targets current SDK and preinstalled
    private static final TestApp APP_FM_DEFAULT = new TestApp(
            "TestAppFileManager", "android.scopedstorage.cts.testapp.filemanager", 1, false,
            "CtsScopedStorageTestAppFileManager.apk");
    // An app that has file manager (MANAGE_EXTERNAL_STORAGE) permission.
    // Targets current SDK and has requestRawExternalStorageAccess=true
    private static final TestApp APP_FM_BYPASS_DATABASE_OPS = new TestApp(
            "TestAppFileManagerBypassDB", "android.scopedstorage.cts.testapp.filemanagerbypassdb",
            1, false, "CtsScopedStorageTestAppFileManagerBypassDB.apk");
    // An app that has file manager (MANAGE_EXTERNAL_STORAGE) permission and targets targetSDK=30
    private static final TestApp APP_FM_TARGETS_30 = new TestApp("TestAppE30FileManager",
            "android.scopedstorage.cts.testapp.E30.filemanager", 1, false,
            "CtsScopedStorageTestAppE30FileManager.apk");

    private static final String OPSTR_MANAGE_EXTERNAL_STORAGE =
            permissionToOp(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
    private static final String[] SYSTEM_GALLERY_APPOPS = {
            AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO};

    /**
     * To help avoid flaky tests, give ourselves a unique nonce to be used for
     * all filesystem paths, so that we don't risk conflicting with previous
     * test runs.
     */
    static final String NONCE = String.valueOf(System.nanoTime());

    static final String IMAGE_FILE_NAME = "BypassDatabaseOperations_file_" + NONCE + ".jpg";

    @BeforeClass
    public static void setupApps() throws Exception {
        // File manager needs to be explicitly granted MES app op.
        final int fmUid =
                getContext().getPackageManager().getPackageUid(
                        APP_FM_DEFAULT.getPackageName(), 0);
        allowAppOpsToUid(fmUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
    }

    @Parameter(0)
    public String mVolumeName;

    @Parameters(name = "volume={0}")
    public static Iterable<? extends Object> data() {
        return ScopedStorageBaseDeviceTest.getTestParameters();
    }

    @Before
    public void setupExternalStorage() throws Exception {
        super.setupExternalStorage(mVolumeName);
        Log.i(TAG, "Using volume : " + mVolumeName);
    }


    /**
     * Test that app with MANAGE_EXTERNAL_STORAGE permission and targeting
     * targetSDK=31 or higher will not bypass database operations by default.
     */
    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testManageExternalStorage_DoesntBypassDatabase_afterS() throws Exception {
        testAppDoesntBypassDatabaseOps(APP_FM_DEFAULT);
    }

    /**
     * Test that app with MANAGE_EXTERNAL_STORAGE permission, targeting
     * targetSDK=31 or higher and with requestRawExternalStorageAccess=true
     * will bypass database operations.
     */
    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testManageExternalStorage_WithBypassFlag_BypassesDatabase() throws Exception {
        final int fmUid = getContext().getPackageManager().getPackageUid(
                        APP_FM_BYPASS_DATABASE_OPS.getPackageName(), 0);
        allowAppOpsToUid(fmUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
        testAppBypassesDatabaseOps(APP_FM_BYPASS_DATABASE_OPS);
    }

    /**
     * Test that app with MANAGE_EXTERNAL_STORAGE permission and targeting
     * targetSDK=30 or lower will bypass database operations by default.
     */
    @Test
    public void testManageExternalStorage_targets30_BypassesDatabase() throws Exception {
        final int fmUid = getContext().getPackageManager().getPackageUid(
                        APP_FM_TARGETS_30.getPackageName(), 0);
        allowAppOpsToUid(fmUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
        testAppBypassesDatabaseOps(APP_FM_TARGETS_30);
    }

    /**
     * Test that app with SYSTEM_GALLERY role and targeting
     * targetSDK=current or higher will not bypass database operations by default.
     */
    @Test
    public void testSystemGallery_DoesntBypassDatabase() throws Exception {
        final int sgUid =
                getContext().getPackageManager().getPackageUid(
                        APP_SYSTEM_GALLERY_DEFAULT.getPackageName(), 0);
        try {
            allowAppOpsToUid(sgUid, SYSTEM_GALLERY_APPOPS);
            testAppDoesntBypassDatabaseOps(APP_SYSTEM_GALLERY_DEFAULT);
        } finally {
            denyAppOpsToUid(sgUid, SYSTEM_GALLERY_APPOPS);
        }
    }


    /**
     * Test that app with SYSTEM_GALLERY role, targeting
     * targetSDK=current or higher and with requestOptimizedSystemGalleryAccess=true
     * will bypass database operations.
     */
    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSystemGallery_WithBypassFlag_BypassesDatabase() throws Exception {
        final int sgUid = getContext().getPackageManager().getPackageUid(
                        APP_SYSTEM_GALLERY_BYPASS_DB.getPackageName(), 0);
        allowAppOpsToUid(sgUid, SYSTEM_GALLERY_APPOPS);
        testAppBypassesDatabaseOps(APP_SYSTEM_GALLERY_BYPASS_DB);
    }

    /**
     * Test that app with SYSTEM_GALLERY role and targeting
     * targetSDK=30 or higher will not bypass database operations by default.
     */
    @Test
    public void testSystemGallery_targets30_DoesntBypassDatabase() throws Exception {
        final int sgUid = getContext().getPackageManager().getPackageUid(
                        APP_SYSTEM_GALLERY_30.getPackageName(), 0);
        allowAppOpsToUid(sgUid, SYSTEM_GALLERY_APPOPS);
        testAppDoesntBypassDatabaseOps(APP_SYSTEM_GALLERY_30);
    }

    /**
     * Test that app with SYSTEM_GALLERY role, targeting
     * targetSDK=30 or higher and with requestOptimizedSystemGalleryAccess=true
     * will bypass database operations.
     */
    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testSystemGallery_targets30_WithBypassFlag_BypassesDatabase() throws Exception {
        final int sgUid = getContext().getPackageManager().getPackageUid(
                        APP_SYSTEM_GALLERY_30_BYPASS_DB.getPackageName(), 0);
        allowAppOpsToUid(sgUid, SYSTEM_GALLERY_APPOPS);
        testAppBypassesDatabaseOps(APP_SYSTEM_GALLERY_30_BYPASS_DB);
    }

    private void testAppDoesntBypassDatabaseOps(TestApp app) throws Exception {
        final File file = new File(getDcimDir(), IMAGE_FILE_NAME);
        final File renamedFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        try {
            assertThat(createFileAs(app, file.getAbsolutePath())).isTrue();
            // File path create() added file to database.
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, file)).isTrue();

            assertThat(renameFileAs(app, file, renamedFile)).isTrue();
            // File path rename() also updates the database row
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, file)).isFalse();
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, renamedFile)).isTrue();

            assertThat(deleteFileAs(app, renamedFile.getAbsolutePath())).isTrue();
            // File path delete() removes database row.
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, renamedFile)).isFalse();
        } finally {
            if (file.exists()) {
                deleteFileAsNoThrow(app, file.getAbsolutePath());
            }
            if (renamedFile.exists()) {
                deleteFileAsNoThrow(app, renamedFile.getAbsolutePath());
            }
        }
    }

    private void testAppBypassesDatabaseOps(TestApp app) throws Exception {
        final File file = new File(getDcimDir(), IMAGE_FILE_NAME);
        final File renamedFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        try {
            assertThat(createFileAs(app, file.getAbsolutePath())).isTrue();
            // File path create() didn't add the file to database.
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, file)).isFalse();

            // Ensure file is added to database.
            assertNotNull(MediaStore.scanFile(getContentResolver(), file));

            assertThat(renameFileAs(app, file, renamedFile)).isTrue();
            // Rename() didn't update the database row.
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, file)).isTrue();
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, renamedFile)).isFalse();

            // Ensure database is updated with renamed path
            assertNull(MediaStore.scanFile(getContentResolver(), file));
            assertNotNull(MediaStore.scanFile(getContentResolver(), renamedFile));
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, renamedFile)).isTrue();

            assertThat(deleteFileAs(app, renamedFile.getAbsolutePath())).isTrue();
            // Unlink() didn't remove the database row.
            assertThat(TestUtils.checkDatabaseRowExistsAs(app, renamedFile)).isTrue();
        } finally {
            if (file.exists()) {
                deleteFileAsNoThrow(app, file.getAbsolutePath());
            }
            if (renamedFile.exists()) {
                deleteFileAsNoThrow(app, renamedFile.getAbsolutePath());
            }
            MediaStore.scanFile(getContentResolver(), file);
            MediaStore.scanFile(getContentResolver(), renamedFile);
        }
    }
}
