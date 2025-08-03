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

package android.scopedstorage.cts.general;

import static android.app.AppOpsManager.permissionToOp;
import static android.os.ParcelFileDescriptor.MODE_CREATE;
import static android.os.ParcelFileDescriptor.MODE_READ_WRITE;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMismatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromFile;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromRawResource;
import static android.scopedstorage.cts.lib.TestUtils.BYTES_DATA2;
import static android.scopedstorage.cts.lib.TestUtils.STR_DATA2;
import static android.scopedstorage.cts.lib.TestUtils.addressStoragePermissions;
import static android.scopedstorage.cts.lib.TestUtils.allowAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.assertCanRenameDirectory;
import static android.scopedstorage.cts.lib.TestUtils.assertCanRenameFile;
import static android.scopedstorage.cts.lib.TestUtils.assertCantInsertToOtherPrivateAppDirectories;
import static android.scopedstorage.cts.lib.TestUtils.assertCantRenameDirectory;
import static android.scopedstorage.cts.lib.TestUtils.assertCantRenameFile;
import static android.scopedstorage.cts.lib.TestUtils.assertCantUpdateToOtherPrivateAppDirectories;
import static android.scopedstorage.cts.lib.TestUtils.assertDirectoryContains;
import static android.scopedstorage.cts.lib.TestUtils.assertFileContent;
import static android.scopedstorage.cts.lib.TestUtils.assertMountMode;
import static android.scopedstorage.cts.lib.TestUtils.assertThrows;
import static android.scopedstorage.cts.lib.TestUtils.canOpen;
import static android.scopedstorage.cts.lib.TestUtils.canOpenFileAs;
import static android.scopedstorage.cts.lib.TestUtils.canQueryOnUri;
import static android.scopedstorage.cts.lib.TestUtils.checkPermission;
import static android.scopedstorage.cts.lib.TestUtils.createFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAsNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.deleteRecursively;
import static android.scopedstorage.cts.lib.TestUtils.deleteRecursivelyAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteWithMediaProvider;
import static android.scopedstorage.cts.lib.TestUtils.deleteWithMediaProviderNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.denyAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.executeShellCommand;
import static android.scopedstorage.cts.lib.TestUtils.fileExistsAs;
import static android.scopedstorage.cts.lib.TestUtils.getAlarmsDir;
import static android.scopedstorage.cts.lib.TestUtils.getAndroidDataDir;
import static android.scopedstorage.cts.lib.TestUtils.getAndroidMediaDir;
import static android.scopedstorage.cts.lib.TestUtils.getAudiobooksDir;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.getDocumentsDir;
import static android.scopedstorage.cts.lib.TestUtils.getDownloadDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalFilesDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalMediaDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalObbDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalStorageDir;
import static android.scopedstorage.cts.lib.TestUtils.getFileMimeTypeFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileOwnerPackageFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileRowIdFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileSizeFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileUri;
import static android.scopedstorage.cts.lib.TestUtils.getImageContentUri;
import static android.scopedstorage.cts.lib.TestUtils.getMoviesDir;
import static android.scopedstorage.cts.lib.TestUtils.getMusicDir;
import static android.scopedstorage.cts.lib.TestUtils.getNotificationsDir;
import static android.scopedstorage.cts.lib.TestUtils.getPicturesDir;
import static android.scopedstorage.cts.lib.TestUtils.getPodcastsDir;
import static android.scopedstorage.cts.lib.TestUtils.getRecordingsDir;
import static android.scopedstorage.cts.lib.TestUtils.getRingtonesDir;
import static android.scopedstorage.cts.lib.TestUtils.grantPermission;
import static android.scopedstorage.cts.lib.TestUtils.installApp;
import static android.scopedstorage.cts.lib.TestUtils.installAppWithStoragePermissions;
import static android.scopedstorage.cts.lib.TestUtils.listAs;
import static android.scopedstorage.cts.lib.TestUtils.openWithMediaProvider;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;
import static android.scopedstorage.cts.lib.TestUtils.queryAudioFile;
import static android.scopedstorage.cts.lib.TestUtils.queryFile;
import static android.scopedstorage.cts.lib.TestUtils.queryFileExcludingPending;
import static android.scopedstorage.cts.lib.TestUtils.queryImageFile;
import static android.scopedstorage.cts.lib.TestUtils.queryVideoFile;
import static android.scopedstorage.cts.lib.TestUtils.readExifMetadataFromTestApp;
import static android.scopedstorage.cts.lib.TestUtils.revokePermission;
import static android.scopedstorage.cts.lib.TestUtils.setAppOpsModeForUid;
import static android.scopedstorage.cts.lib.TestUtils.setAttrAs;
import static android.scopedstorage.cts.lib.TestUtils.trashFileAndAssert;
import static android.scopedstorage.cts.lib.TestUtils.uninstallAppNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.untrashFileAndAssert;
import static android.scopedstorage.cts.lib.TestUtils.updateDisplayNameWithMediaProvider;
import static android.scopedstorage.cts.lib.TestUtils.verifyInsertFromExternalMediaDirViaRelativePath_allowed;
import static android.scopedstorage.cts.lib.TestUtils.verifyInsertFromExternalPrivateDirViaRelativePath_denied;
import static android.scopedstorage.cts.lib.TestUtils.verifyUpdateToExternalMediaDirViaRelativePath_allowed;
import static android.scopedstorage.cts.lib.TestUtils.verifyUpdateToExternalPrivateDirsViaRelativePath_denied;
import static android.system.OsConstants.F_OK;
import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_EXCL;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.R_OK;
import static android.system.OsConstants.S_IRWXU;
import static android.system.OsConstants.W_OK;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getTargetContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.RedactionTestHelper;
import android.scopedstorage.cts.lib.ScopedStorageBaseDeviceTest;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.cts.install.lib.TestApp;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.io.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Device-side test suite to verify scoped storage business logic.
 */
@RunWith(Parameterized.class)
public class ScopedStorageDeviceTest extends ScopedStorageBaseDeviceTest {
    public static final String STR_DATA1 = "Just some random text";

    public static final byte[] BYTES_DATA1 = STR_DATA1.getBytes();

    static final String TAG = "ScopedStorageDeviceTest";
    static final String THIS_PACKAGE_NAME = getContext().getPackageName();

    /**
     * To help avoid flaky tests, give ourselves a unique nonce to be used for
     * all filesystem paths, so that we don't risk conflicting with previous
     * test runs.
     */
    static final String NONCE = String.valueOf(System.nanoTime());

    static final String TEST_DIRECTORY_NAME = "ScopedStorageDeviceTestDirectory" + NONCE;

    static final String AUDIO_FILE_NAME = "ScopedStorageDeviceTest_file_" + NONCE + ".mp3";
    static final String PLAYLIST_FILE_NAME = "ScopedStorageDeviceTest_file_" + NONCE + ".m3u";
    static final String SUBTITLE_FILE_NAME = "ScopedStorageDeviceTest_file_" + NONCE + ".srt";
    static final String VIDEO_FILE_NAME = "ScopedStorageDeviceTest_file_" + NONCE + ".mp4";
    static final String IMAGE_FILE_NAME = "ScopedStorageDeviceTest_file_" + NONCE + ".jpg";
    static final String NONMEDIA_FILE_NAME = "ScopedStorageDeviceTest_file_" + NONCE + ".pdf";

    static final String FILE_CREATION_ERROR_MESSAGE = "No such file or directory";

    // The following apps are installed before the tests are run via a target_preparer.
    // See test config for details.
    // An app with READ_EXTERNAL_STORAGE and READ_MEDIA_* permissions
    private static final TestApp APP_A_HAS_RES =
            new TestApp(
                    "TestAppA",
                    "android.scopedstorage.cts.testapp.A.withres",
                    1,
                    false,
                    "CtsScopedStorageTestAppA.apk");
    // An app with no permissions
    private static final TestApp APP_B_NO_PERMS = new TestApp("TestAppB",
            "android.scopedstorage.cts.testapp.B.noperms", 1, false,
            "CtsScopedStorageTestAppB.apk");
    // An app that has file manager (MANAGE_EXTERNAL_STORAGE) permission.
    private static final TestApp APP_FM = new TestApp("TestAppFileManager",
            "android.scopedstorage.cts.testapp.filemanager", 1, false,
            "CtsScopedStorageTestAppFileManager.apk");
    // A legacy targeting app with RES and WES permissions
    private static final TestApp APP_D_LEGACY_HAS_RW = new TestApp("TestAppDLegacy",
            "android.scopedstorage.cts.testapp.D", 1, false, "CtsScopedStorageTestAppDLegacy.apk");
    private static final TestApp APP_E = new TestApp("TestAppE",
            "android.scopedstorage.cts.testapp.E", 1, false, "CtsScopedStorageTestAppE.apk");
    private static final TestApp APP_E_LEGACY = new TestApp("TestAppELegacy",
            "android.scopedstorage.cts.testapp.E.legacy", 1, false,
            "CtsScopedStorageTestAppELegacy.apk");
    // APP_GENERAL_ONLY is not installed at test startup - please install before using.
    private static final TestApp APP_GENERAL_ONLY = new TestApp("TestAppGeneralOnly",
            "android.scopedstorage.cts.testapp.general.only", 1, false,
            "CtsScopedStorageGeneralTestOnlyApp.apk");

    private static final String[] SYSTEM_GALERY_APPOPS = {
            AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO};
    private static final String OPSTR_MANAGE_EXTERNAL_STORAGE =
            permissionToOp(Manifest.permission.MANAGE_EXTERNAL_STORAGE);

    private static final String TRANSFORMS_DIR = ".transforms";
    private static final String TRANSFORMS_TRANSCODE_DIR = TRANSFORMS_DIR + "/" + "transcode";
    private static final String TRANSFORMS_SYNTHETIC_DIR = TRANSFORMS_DIR + "/" + "synthetic";

    @Parameter(0)
    public String mVolumeName;

    /** Parameters data. */
    @Parameters(name = "volume={0}")
    public static Iterable<? extends Object> data() {
        return ScopedStorageDeviceTest.getTestParameters();
    }

    @BeforeClass
    public static void setupApps() throws Exception {
        // File manager needs to be explicitly granted MES app op.
        final int fmUid =
                getContext().getPackageManager().getPackageUid(APP_FM.getPackageName(),
                        0);
        allowAppOpsToUid(fmUid, OPSTR_MANAGE_EXTERNAL_STORAGE);

        // Others are installed by target preparer with runtime permissions.
        // Verify.
        assertThat(checkPermission(APP_A_HAS_RES,
                Manifest.permission.READ_EXTERNAL_STORAGE)).isTrue();
        assertThat(checkPermission(APP_B_NO_PERMS,
                Manifest.permission.READ_EXTERNAL_STORAGE)).isFalse();
        assertThat(checkPermission(APP_D_LEGACY_HAS_RW,
                Manifest.permission.READ_EXTERNAL_STORAGE)).isTrue();
        assertThat(checkPermission(APP_D_LEGACY_HAS_RW,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)).isTrue();
    }

    @After
    public void tearDown() throws Exception {
        executeShellCommand("rm -r /sdcard/Android/data/com.android.shell");
    }

    @Before
    public void setupExternalStorage() throws Exception {
        super.setupExternalStorage(mVolumeName);
        Log.i(TAG, "Using volume : " + mVolumeName);
    }

    /**
     * Test that we enforce certain media types can only be created in certain directories.
     */
    @Test
    public void testTypePathConformity() throws Exception {
        final File dcimDir = getDcimDir();
        final File documentsDir = getDocumentsDir();
        final File downloadDir = getDownloadDir();
        final File moviesDir = getMoviesDir();
        final File musicDir = getMusicDir();
        final File picturesDir = getPicturesDir();
        // Only audio files can be created in Music
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(musicDir, NONMEDIA_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(musicDir, VIDEO_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(musicDir, IMAGE_FILE_NAME).createNewFile();
                });
        // Only video files can be created in Movies
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(moviesDir, NONMEDIA_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(moviesDir, AUDIO_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(moviesDir, IMAGE_FILE_NAME).createNewFile();
                });
        // Only image and video files can be created in DCIM
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(dcimDir, NONMEDIA_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(dcimDir, AUDIO_FILE_NAME).createNewFile();
                });
        // Only image and video files can be created in Pictures
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(picturesDir, NONMEDIA_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(picturesDir, AUDIO_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(picturesDir, PLAYLIST_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(dcimDir, SUBTITLE_FILE_NAME).createNewFile();
                });

        assertCanCreateFile(new File(getAlarmsDir(), AUDIO_FILE_NAME));
        assertCanCreateFile(new File(getAudiobooksDir(), AUDIO_FILE_NAME));
        assertCanCreateFile(new File(dcimDir, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(dcimDir, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(documentsDir, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(documentsDir, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(documentsDir, NONMEDIA_FILE_NAME));
        assertCanCreateFile(new File(documentsDir, PLAYLIST_FILE_NAME));
        assertCanCreateFile(new File(documentsDir, SUBTITLE_FILE_NAME));
        assertCanCreateFile(new File(documentsDir, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(downloadDir, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(downloadDir, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(downloadDir, NONMEDIA_FILE_NAME));
        assertCanCreateFile(new File(downloadDir, PLAYLIST_FILE_NAME));
        assertCanCreateFile(new File(downloadDir, SUBTITLE_FILE_NAME));
        assertCanCreateFile(new File(downloadDir, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(moviesDir, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(moviesDir, SUBTITLE_FILE_NAME));
        assertCanCreateFile(new File(musicDir, AUDIO_FILE_NAME));
        assertCanCreateFile(new File(musicDir, PLAYLIST_FILE_NAME));
        assertCanCreateFile(new File(getNotificationsDir(), AUDIO_FILE_NAME));
        assertCanCreateFile(new File(picturesDir, IMAGE_FILE_NAME));
        assertCanCreateFile(new File(picturesDir, VIDEO_FILE_NAME));
        assertCanCreateFile(new File(getPodcastsDir(), AUDIO_FILE_NAME));
        assertCanCreateFile(new File(getRingtonesDir(), AUDIO_FILE_NAME));

        // No file whatsoever can be created in the top level directory
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(getExternalStorageDir(), NONMEDIA_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(getExternalStorageDir(), AUDIO_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(getExternalStorageDir(), IMAGE_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(getExternalStorageDir(), VIDEO_FILE_NAME).createNewFile();
                });
    }

    /**
     * Test that we enforce certain media types can only be created in certain directories.
     */
    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testTypePathConformity_recordingsDir() throws Exception {
        final File recordingsDir = getRecordingsDir();

        // Only audio files can be created in Recordings
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(recordingsDir, NONMEDIA_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(recordingsDir, VIDEO_FILE_NAME).createNewFile();
                });
        assertThrows(IOException.class, "Operation not permitted",
                () -> {
                    new File(recordingsDir, IMAGE_FILE_NAME).createNewFile();
                });

        assertCanCreateFile(new File(recordingsDir, AUDIO_FILE_NAME));
    }

    /**
     * Test that we can create a file in app's external files directory,
     * and that we can write and read to/from the file.
     */
    @Test
    public void testCreateFileInAppExternalDir() throws Exception {
        final File file = new File(getExternalFilesDir(), "text.txt");
        try {
            assertThat(file.createNewFile()).isTrue();
            assertThat(file.delete()).isTrue();
            // Ensure the file is properly deleted and can be created again
            assertThat(file.createNewFile()).isTrue();

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(BYTES_DATA1);
            }

            // Read the same data from file
            assertFileContent(file, BYTES_DATA1);
        } finally {
            file.delete();
        }
    }

    /**
     * Test that we can't create a file in another app's external files directory,
     * and that we'll get the same error regardless of whether the app exists or not.
     */
    @Test
    public void testCreateFileInOtherAppExternalDir() throws Exception {
        // Creating a file in a non existent package dir should return ENOENT, as expected
        final File nonexistentPackageFileDir = new File(
                getExternalFilesDir().getPath().replace(THIS_PACKAGE_NAME, "no.such.package"));
        final File file1 = new File(nonexistentPackageFileDir, NONMEDIA_FILE_NAME);
        assertThrows(
                IOException.class, FILE_CREATION_ERROR_MESSAGE, () -> {
                    file1.createNewFile();
                });

        // Creating a file in an existent package dir should give the same error string to avoid
        // leaking installed app names, and we know the following directory exists because shell
        // mkdirs it in test setup
        final File shellPackageFileDir = new File(
                getExternalFilesDir().getPath().replace(THIS_PACKAGE_NAME, "com.android.shell"));
        final File file2 = new File(shellPackageFileDir, NONMEDIA_FILE_NAME);
        assertThrows(
                IOException.class, FILE_CREATION_ERROR_MESSAGE, () -> {
                    file1.createNewFile();
                });
    }

    /**
     * Test that apps can't read/write files in another app's external files directory,
     * and can do so in their own app's external file directory.
     */
    @Test
    public void testReadWriteFilesInOtherAppExternalDir() throws Exception {
        final File videoFile = new File(getExternalFilesDir(), VIDEO_FILE_NAME);

        try {
            // Create a file in app's external files directory
            if (!videoFile.exists()) {
                assertThat(videoFile.createNewFile()).isTrue();
            }

            // App A should not be able to read/write to other app's external files directory.
            assertThat(canOpenFileAs(APP_A_HAS_RES, videoFile, false /* forWrite */)).isFalse();
            assertThat(canOpenFileAs(APP_A_HAS_RES, videoFile, true /* forWrite */)).isFalse();
            // App A should not be able to delete files in other app's external files
            // directory.
            assertThat(deleteFileAs(APP_A_HAS_RES, videoFile.getPath())).isFalse();

            // Apps should have read/write access in their own app's external files directory.
            assertThat(canOpen(videoFile, false /* forWrite */)).isTrue();
            assertThat(canOpen(videoFile, true /* forWrite */)).isTrue();
            // Apps should be able to delete files in their own app's external files directory.
            assertThat(videoFile.delete()).isTrue();
        } finally {
            videoFile.delete();
        }
    }

    /**
     * Test that we can contribute media without any permissions.
     */
    @Test
    public void testContributeMediaFile() throws Exception {
        final File imageFile = new File(getDcimDir(), IMAGE_FILE_NAME);

        try {
            assertThat(imageFile.createNewFile()).isTrue();

            // Ensure that the file was successfully added to the MediaProvider database
            assertThat(getFileOwnerPackageFromDatabase(imageFile)).isEqualTo(THIS_PACKAGE_NAME);

            // Try to write random data to the file
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(BYTES_DATA1);
                fos.write(BYTES_DATA2);
            }

            final byte[] expected = (STR_DATA1 + STR_DATA2).getBytes();
            assertFileContent(imageFile, expected);

            // Closing the file after writing will not trigger a MediaScan. Call scanFile to update
            // file's entry in MediaProvider's database.
            assertThat(MediaStore.scanFile(getContentResolver(), imageFile)).isNotNull();

            // Ensure that the scan was completed and the file's size was updated.
            assertThat(getFileSizeFromDatabase(imageFile)).isEqualTo(
                    BYTES_DATA1.length + BYTES_DATA2.length);
        } finally {
            imageFile.delete();
        }
        // Ensure that delete makes a call to MediaProvider to remove the file from its database.
        assertThat(getFileRowIdFromDatabase(imageFile)).isEqualTo(-1);
    }

    @Test
    public void testCreateAndDeleteEmptyDir() throws Exception {
        final File externalFilesDir = getExternalFilesDir();
        // Remove directory in order to create it again
        deleteRecursively(externalFilesDir);

        // Can create own external files dir
        assertThat(externalFilesDir.mkdir()).isTrue();

        final File dir1 = new File(externalFilesDir, "random_dir");
        // Can create dirs inside it
        assertThat(dir1.mkdir()).isTrue();

        final File dir2 = new File(dir1, "random_dir_inside_random_dir");
        // And create a dir inside the new dir
        assertThat(dir2.mkdir()).isTrue();

        // And can delete them all
        assertThat(deleteRecursively(dir2)).isTrue();
        assertThat(deleteRecursively(dir1)).isTrue();
        assertThat(deleteRecursively(externalFilesDir)).isTrue();

        // Can't create external dir for other apps
        final File nonexistentPackageFileDir = new File(
                externalFilesDir.getPath().replace(THIS_PACKAGE_NAME, "no.such.package"));
        final File shellPackageFileDir = new File(
                externalFilesDir.getPath().replace(THIS_PACKAGE_NAME, "com.android.shell"));

        assertThat(nonexistentPackageFileDir.mkdir()).isFalse();
        assertThat(shellPackageFileDir.mkdir()).isFalse();
    }

    @Test
    public void testCantAccessOtherAppsContents() throws Exception {
        final File mediaFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        final File nonMediaFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        try {
            assertThat(createFileAs(APP_B_NO_PERMS, mediaFile.getPath())).isTrue();
            assertThat(createFileAs(APP_B_NO_PERMS, nonMediaFile.getPath())).isTrue();

            // We can still see that the files exist
            assertThat(mediaFile.exists()).isTrue();
            assertThat(nonMediaFile.exists()).isTrue();

            // But we can't access their content
            assertThat(canOpen(mediaFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(nonMediaFile, /* forWrite */ true)).isFalse();
            assertThat(canOpen(mediaFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(nonMediaFile, /* forWrite */ true)).isFalse();
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, nonMediaFile.getPath());
            deleteFileAsNoThrow(APP_B_NO_PERMS, mediaFile.getPath());
        }
    }

    @Test
    public void testCantDeleteOtherAppsContents() throws Exception {
        final File dirInDownload = new File(getDownloadDir(), TEST_DIRECTORY_NAME);
        final File mediaFile = new File(dirInDownload, IMAGE_FILE_NAME);
        final File nonMediaFile = new File(dirInDownload, NONMEDIA_FILE_NAME);
        try {
            assertThat(dirInDownload.mkdir()).isTrue();
            // Have another app create a media file in the directory
            assertThat(createFileAs(APP_B_NO_PERMS, mediaFile.getPath())).isTrue();

            // Can't delete the directory since it contains another app's content
            assertThat(dirInDownload.delete()).isFalse();
            // Can't delete another app's content
            assertThat(deleteRecursively(dirInDownload)).isFalse();

            // Have another app create a non-media file in the directory
            assertThat(createFileAs(APP_B_NO_PERMS, nonMediaFile.getPath())).isTrue();

            // Can't delete the directory since it contains another app's content
            assertThat(dirInDownload.delete()).isFalse();
            // Can't delete another app's content
            assertThat(deleteRecursively(dirInDownload)).isFalse();

            // Delete only the media file and keep the non-media file
            assertThat(deleteFileAs(APP_B_NO_PERMS, mediaFile.getPath())).isTrue();
            // Directory now has only the non-media file contributed by another app, so we still
            // can't delete it nor its content
            assertThat(dirInDownload.delete()).isFalse();
            assertThat(deleteRecursively(dirInDownload)).isFalse();

            // Delete the last file belonging to another app
            assertThat(deleteFileAs(APP_B_NO_PERMS, nonMediaFile.getPath())).isTrue();
            // Create our own file
            assertThat(nonMediaFile.createNewFile()).isTrue();

            // Now that the directory only has content that was contributed by us, we can delete it
            assertThat(deleteRecursively(dirInDownload)).isTrue();
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, nonMediaFile.getPath());
            deleteFileAsNoThrow(APP_B_NO_PERMS, mediaFile.getPath());
            // At this point, we're not sure who created this file, so we'll have both apps
            // deleting it
            mediaFile.delete();
            deleteRecursively(dirInDownload);
        }
    }

    /**
     * Test that deleting uri corresponding to a file which was already deleted via filePath
     * doesn't result in a security exception.
     */
    @Test
    public void testDeleteAlreadyUnlinkedFile() throws Exception {
        final File nonMediaFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        try {
            assertTrue(nonMediaFile.createNewFile());
            final Uri uri = MediaStore.scanFile(getContentResolver(), nonMediaFile);
            assertNotNull(uri);

            // Delete the file via filePath
            assertTrue(nonMediaFile.delete());

            // If we delete nonMediaFile with ContentResolver#delete, it shouldn't result in a
            // security exception.
            assertThat(getContentResolver().delete(uri, Bundle.EMPTY)).isEqualTo(0);
        } finally {
            nonMediaFile.delete();
        }
    }

    /**
     * This test relies on the fact that {@link File#list} uses opendir internally, and that it
     * returns {@code null} if opendir fails.
     */
    @Test
    public void testOpendirRestrictions() throws Exception {
        // Opening a non existent package directory should fail, as expected
        final File nonexistentPackageFileDir = new File(
                getExternalFilesDir().getPath().replace(THIS_PACKAGE_NAME, "no.such.package"));
        assertThat(nonexistentPackageFileDir.list()).isNull();

        // Opening another package's external directory should fail as well, even if it exists
        final File shellPackageFileDir = new File(
                getExternalFilesDir().getPath().replace(THIS_PACKAGE_NAME, "com.android.shell"));
        assertThat(shellPackageFileDir.list()).isNull();

        // We can open our own external files directory
        final String[] filesList = getExternalFilesDir().list();
        assertThat(filesList).isNotNull();

        // We can open any public directory in external storage
        assertThat(getDcimDir().list()).isNotNull();
        assertThat(getDownloadDir().list()).isNotNull();
        assertThat(getMoviesDir().list()).isNotNull();
        assertThat(getMusicDir().list()).isNotNull();

        // We can open the root directory of external storage
        final String[] topLevelDirs = getExternalStorageDir().list();
        assertThat(topLevelDirs).isNotNull();
        // TODO(b/145287327): This check fails on a device with no visible files.
        // This can be fixed if we display default directories.
        // assertThat(topLevelDirs).isNotEmpty();
    }

    @Test
    public void testLowLevelFileIO() throws Exception {
        String filePath = new File(getDownloadDir(), NONMEDIA_FILE_NAME).toString();
        try {
            int createFlags = O_CREAT | O_RDWR;
            int createExclFlags = createFlags | O_EXCL;

            FileDescriptor fd = Os.open(filePath, createExclFlags, S_IRWXU);
            Os.close(fd);
            assertThrows(
                    ErrnoException.class, () -> {
                        Os.open(filePath, createExclFlags, S_IRWXU);
                    });

            fd = Os.open(filePath, createFlags, S_IRWXU);
            try {
                assertThat(Os.write(fd,
                        ByteBuffer.wrap(BYTES_DATA1))).isEqualTo(BYTES_DATA1.length);
                assertFileContent(fd, BYTES_DATA1);
            } finally {
                Os.close(fd);
            }
            // should just append the data
            fd = Os.open(filePath, createFlags | O_APPEND, S_IRWXU);
            try {
                assertThat(Os.write(fd,
                        ByteBuffer.wrap(BYTES_DATA2))).isEqualTo(BYTES_DATA2.length);
                final byte[] expected = (STR_DATA1 + STR_DATA2).getBytes();
                assertFileContent(fd, expected);
            } finally {
                Os.close(fd);
            }
            // should overwrite everything
            fd = Os.open(filePath, createFlags | O_TRUNC, S_IRWXU);
            try {
                final byte[] otherData = "this is different data".getBytes();
                assertThat(Os.write(fd, ByteBuffer.wrap(otherData))).isEqualTo(otherData.length);
                assertFileContent(fd, otherData);
            } finally {
                Os.close(fd);
            }
        } finally {
            new File(filePath).delete();
        }
    }

    /**
     * Test that media files from other packages are only visible to apps with storage permission.
     */
    @Test
    public void testListDirectoriesWithMediaFiles() throws Exception {
        final File dcimDir = getDcimDir();
        final File dir = new File(dcimDir, TEST_DIRECTORY_NAME);
        final File videoFile = new File(dir, VIDEO_FILE_NAME);
        final String videoFileName = videoFile.getName();
        try {
            if (!dir.exists()) {
                assertThat(dir.mkdir()).isTrue();
            }

            assertThat(createFileAs(APP_B_NO_PERMS, videoFile.getPath())).isTrue();
            // App B should see TEST_DIRECTORY in DCIM and new file in TEST_DIRECTORY.
            assertThat(listAs(APP_B_NO_PERMS, dcimDir.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(APP_B_NO_PERMS, dir.getPath())).containsExactly(videoFileName);

            // App A has storage permission, so should see TEST_DIRECTORY in DCIM and new file
            // in TEST_DIRECTORY.
            assertThat(listAs(APP_A_HAS_RES, dcimDir.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(APP_A_HAS_RES, dir.getPath())).containsExactly(videoFileName);

            // We are an app without storage permission; should see TEST_DIRECTORY in DCIM and
            // should not see new file in new TEST_DIRECTORY.
            assertThat(dcimDir.list()).asList().contains(TEST_DIRECTORY_NAME);
            assertThat(dir.list()).asList().doesNotContain(videoFileName);
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, videoFile.getPath());
            deleteRecursively(dir);
        }
    }

    /**
     * Test that app can't see non-media files created by other packages
     */
    @Test
    public void testListDirectoriesWithNonMediaFiles() throws Exception {
        final File downloadDir = getDownloadDir();
        final File dir = new File(downloadDir, TEST_DIRECTORY_NAME);
        final File pdfFile = new File(dir, NONMEDIA_FILE_NAME);
        final String pdfFileName = pdfFile.getName();
        try {
            if (!dir.exists()) {
                assertThat(dir.mkdir()).isTrue();
            }

            // Have App B create non media file in the new directory.
            assertThat(createFileAs(APP_B_NO_PERMS, pdfFile.getPath())).isTrue();

            // App B should see TEST_DIRECTORY in downloadDir and new non media file in
            // TEST_DIRECTORY.
            assertThat(listAs(APP_B_NO_PERMS, downloadDir.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(APP_B_NO_PERMS, dir.getPath())).containsExactly(pdfFileName);

            // APP A with storage permission should see TEST_DIRECTORY in downloadDir
            // and should not see non media file in TEST_DIRECTORY.
            assertThat(listAs(APP_A_HAS_RES, downloadDir.getPath())).contains(TEST_DIRECTORY_NAME);
            assertThat(listAs(APP_A_HAS_RES, dir.getPath())).doesNotContain(pdfFileName);
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, pdfFile.getPath());
            deleteRecursively(dir);
        }
    }

    /**
     * Test that app can only see its directory in Android/data.
     */
    @Test
    public void testListFilesFromExternalFilesDirectory() throws Exception {
        final String packageName = THIS_PACKAGE_NAME;
        final File nonmediaFile = new File(getExternalFilesDir(), NONMEDIA_FILE_NAME);

        try {
            // Create a file in app's external files directory
            if (!nonmediaFile.exists()) {
                assertThat(nonmediaFile.createNewFile()).isTrue();
            }
            // App should see its directory and directories of shared packages. App should see all
            // files and directories in its external directory.
            assertDirectoryContains(nonmediaFile.getParentFile(), nonmediaFile);

            // App A should not see other app's external files directory despite RES.
            assertThrows(IOException.class,
                    () -> listAs(APP_A_HAS_RES, getAndroidDataDir().getPath()));
            assertThrows(IOException.class,
                    () -> listAs(APP_A_HAS_RES, getExternalFilesDir().getPath()));
        } finally {
            nonmediaFile.delete();
        }
    }

    /**
     * Test that app can see files and directories in Android/media.
     */
    @Test
    public void testListFilesFromExternalMediaDirectory() throws Exception {
        final File videoFile = new File(getExternalMediaDir(), VIDEO_FILE_NAME);

        try {
            // Create a file in app's external media directory
            if (!videoFile.exists()) {
                assertThat(videoFile.createNewFile()).isTrue();
            }

            // App should see its directory and other app's external media directories with media
            // files.
            assertDirectoryContains(videoFile.getParentFile(), videoFile);

            // App A with storage permission should see other app's external media directory.
            // Apps with READ_EXTERNAL_STORAGE can list files in other app's external media
            // directory.
            assertThat(listAs(APP_A_HAS_RES, getAndroidMediaDir().getPath()))
                    .contains(THIS_PACKAGE_NAME);
            assertThat(listAs(APP_A_HAS_RES, getExternalMediaDir().getPath()))
                    .containsExactly(videoFile.getName());
        } finally {
            videoFile.delete();
        }
    }

    @Test
    public void testMetaDataRedaction() throws Exception {
        File jpgFile = new File(getPicturesDir(), "img_metadata.jpg");
        try {
            if (jpgFile.exists()) {
                assertThat(jpgFile.delete()).isTrue();
            }

            HashMap<String, String> originalExif =
                    getExifMetadataFromRawResource(R.raw.img_with_metadata);

            try (InputStream in =
                         getContext().getResources().openRawResource(R.raw.img_with_metadata);
                FileOutputStream out = new FileOutputStream(jpgFile)) {
                // Dump the image we have to external storage
                FileUtils.copy(in, out);
                // Sync file to disk to ensure file is fully written to the lower fs attempting to
                // open for redaction. Otherwise, the FUSE daemon might not accurately parse the
                // EXIF tags and might misleadingly think there are not tags to redact
                out.getFD().sync();

                HashMap<String, String> exif = getExifMetadataFromFile(jpgFile);
                assertExifMetadataMatch(exif, originalExif);

                HashMap<String, String> exifFromTestApp =
                        readExifMetadataFromTestApp(APP_A_HAS_RES, jpgFile.getPath());
                // App does not have AML; shouldn't have access to the same metadata.
                assertExifMetadataMismatch(exifFromTestApp, originalExif);

                // TODO(b/146346138): Test that if we give APP_A write URI permission,
                //  it would be able to access the metadata.
            } // Intentionally keep the original streams open during the test so bytes are more
            // likely to be in the VFS cache from both file opens
        } finally {
            jpgFile.delete();
        }
    }

    @Test
    public void testOpenFilePathFirstWriteContentResolver() throws Exception {
        String displayName = "open_file_path_write_content_resolver.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            try (ParcelFileDescriptor readPfd = ParcelFileDescriptor.open(file, MODE_READ_WRITE);
                 ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw")) {
                assertRWR(readPfd, writePfd);
                assertUpperFsFd(writePfd); // With cache
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverFirstWriteContentResolver() throws Exception {
        String displayName = "open_content_resolver_write_content_resolver.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            try (ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw");
                 ParcelFileDescriptor readPfd = ParcelFileDescriptor.open(file, MODE_READ_WRITE)) {
                assertRWR(readPfd, writePfd);
                assertLowerFsFdWithPassthrough(file.getPath(), writePfd);
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenFilePathFirstWriteFilePath() throws Exception {
        String displayName = "open_file_path_write_file_path.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            try (ParcelFileDescriptor writePfd = ParcelFileDescriptor.open(file, MODE_READ_WRITE);
                 ParcelFileDescriptor readPfd = openWithMediaProvider(file, "rw")) {
                assertRWR(readPfd, writePfd);
                assertUpperFsFd(readPfd); // With cache
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverFirstWriteFilePath() throws Exception {
        String displayName = "open_content_resolver_write_file_path.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            try (ParcelFileDescriptor readPfd = openWithMediaProvider(file, "rw");
                 ParcelFileDescriptor writePfd = ParcelFileDescriptor.open(file, MODE_READ_WRITE)) {
                assertRWR(readPfd, writePfd);
                assertLowerFsFdWithPassthrough(file.getPath(), readPfd);
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverWriteOnly() throws Exception {
        String displayName = "open_content_resolver_write_only.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            // We upgrade 'w' only to 'rw'
            try (ParcelFileDescriptor writePfd = openWithMediaProvider(file, "w");
                 ParcelFileDescriptor readPfd = openWithMediaProvider(file, "rw")) {
                assertRWR(readPfd, writePfd);
                assertRWR(writePfd, readPfd); // Can read on 'w' only pfd
                assertLowerFsFdWithPassthrough(file.getPath(), writePfd);
                assertLowerFsFdWithPassthrough(file.getPath(), readPfd);
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverDup() throws Exception {
        String displayName = "open_content_resolver_dup.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            file.delete();
            assertThat(file.createNewFile()).isTrue();

            // Even if we close the original fd, since we have a dup open
            // the FUSE IO should still bypass the cache
            try (ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw");
                 ParcelFileDescriptor writePfdDup = writePfd.dup();
                 ParcelFileDescriptor readPfd = ParcelFileDescriptor.open(file, MODE_READ_WRITE)) {
                writePfd.close();

                assertRWR(readPfd, writePfdDup);
                assertLowerFsFdWithPassthrough(file.getPath(), writePfdDup);
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOpenContentResolverClose() throws Exception {
        String displayName = "open_content_resolver_close.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            byte[] readBuffer = new byte[10];
            byte[] writeBuffer = new byte[10];
            Arrays.fill(writeBuffer, (byte) 1);

            assertThat(file.createNewFile()).isTrue();

            // Lower fs open and write
            ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw");
            Os.pwrite(writePfd.getFileDescriptor(), writeBuffer, 0, 10, 0);

            // Close so upper fs open will not use direct_io
            writePfd.close();

            // Upper fs open and read without direct_io
            try (ParcelFileDescriptor readPfd = ParcelFileDescriptor.open(file, MODE_READ_WRITE)) {
                Os.pread(readPfd.getFileDescriptor(), readBuffer, 0, 10, 0);

                // Last write on lower fs is visible via upper fs
                assertThat(readBuffer).isEqualTo(writeBuffer);
                assertThat(readPfd.getStatSize()).isEqualTo(writeBuffer.length);
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testContentResolverDelete() throws Exception {
        String displayName = "content_resolver_delete.jpg";
        File file = new File(getDcimDir(), displayName);

        try {
            assertThat(file.createNewFile()).isTrue();

            deleteWithMediaProvider(file);

            assertThat(file.exists()).isFalse();
            assertThat(file.createNewFile()).isTrue();
        } finally {
            file.delete();
        }
    }

    @Test
    public void testContentResolverUpdate() throws Exception {
        String oldDisplayName = "content_resolver_update_old.jpg";
        String newDisplayName = "content_resolver_update_new.jpg";
        File oldFile = new File(getDcimDir(), oldDisplayName);
        File newFile = new File(getDcimDir(), newDisplayName);

        try {
            assertThat(oldFile.createNewFile()).isTrue();
            // Publish the pending oldFile before updating with MediaProvider. Not publishing the
            // file will make MP consider pending from FUSE as explicit IS_PENDING
            final Uri uri = MediaStore.scanFile(getContentResolver(), oldFile);
            assertNotNull(uri);

            updateDisplayNameWithMediaProvider(uri,
                    Environment.DIRECTORY_DCIM, oldDisplayName, newDisplayName);

            assertThat(oldFile.exists()).isFalse();
            assertThat(oldFile.createNewFile()).isTrue();
            assertThat(newFile.exists()).isTrue();
            assertThat(newFile.createNewFile()).isFalse();
        } finally {
            oldFile.delete();
            newFile.delete();
        }
    }

    void writeAndCheckMtime(final boolean append) throws Exception {
        File file = new File(getDcimDir(), "update_modifies_mtime.jpg");

        try {
            assertThat(file.createNewFile()).isTrue();
            assertThat(file.exists()).isTrue();

            final long creationTime = file.lastModified();

            // File should exist
            assertNotEquals(creationTime, 0L);

            // Sleep a bit more than 1 second because although
            // File::lastModified() represents the duration in milliseconds,
            // has 1 second precision.
            // With lower sleep durations the test results flakey...
            Thread.sleep(2000);

            // Modification time should be the same as long the file has not
            // been modified
            assertEquals(creationTime, file.lastModified());

            // Sleep a bit more than 1 second because although
            // File::lastModified() represents the duration in milliseconds,
            // has 1 second precision.
            // With lower sleep durations the test results flakey...
            Thread.sleep(2000);

            // Assert we can write to the file
            try (FileOutputStream fos = new FileOutputStream(file, append)) {
                fos.write(BYTES_DATA1);
                fos.close();
            }

            final long modificationTime = file.lastModified();

            // As the file has been written, modification time should have
            // changed
            assertNotEquals(modificationTime, 0L);
            assertNotEquals(modificationTime, creationTime);
        } finally {
            file.delete();
        }
    }

    @Test
    // There is a minor bug which, alghough fixed in sc-dev (aosp/1834457),
    // cannot be propagated to the already released sc-release branche
    // (b/234145920), where mainline-modules are tested.
    // Skip this test in S to avoid failures in outdated targets.
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testAppendUpdatesMtime() throws Exception {
        writeAndCheckMtime(true);
    }

    @Test
    public void testWriteUpdatesMtime() throws Exception {
        writeAndCheckMtime(false);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testDefaultNoIsolatedStorageFlag() throws Exception {
        assertThat(Environment.isExternalStorageLegacy()).isFalse();
    }

    @Test
    public void testCreateLowerCaseDeleteUpperCase() throws Exception {
        File upperCase = new File(getDownloadDir(), "CREATE_LOWER_DELETE_UPPER");
        File lowerCase = new File(getDownloadDir(), "create_lower_delete_upper");

        createDeleteCreate(lowerCase, upperCase);
    }

    @Test
    public void testCreateUpperCaseDeleteLowerCase() throws Exception {
        File upperCase = new File(getDownloadDir(), "CREATE_UPPER_DELETE_LOWER");
        File lowerCase = new File(getDownloadDir(), "create_upper_delete_lower");

        createDeleteCreate(upperCase, lowerCase);
    }

    @Test
    public void testCreateMixedCaseDeleteDifferentMixedCase() throws Exception {
        File mixedCase1 = new File(getDownloadDir(), "CrEaTe_MiXeD_dElEtE_mIxEd");
        File mixedCase2 = new File(getDownloadDir(), "cReAtE_mIxEd_DeLeTe_MiXeD");

        createDeleteCreate(mixedCase1, mixedCase2);
    }

    @Test
    public void testAndroidDataObbDoesNotForgetMount() throws Exception {
        File dataDir = getContext().getExternalFilesDir(null);
        File upperCaseDataDir = new File(dataDir.getPath().replace("Android/data", "ANDROID/DATA"));

        File obbDir = getContext().getObbDir();
        File upperCaseObbDir = new File(obbDir.getPath().replace("Android/obb", "ANDROID/OBB"));


        StructStat beforeDataStruct = Os.stat(dataDir.getPath());
        StructStat beforeObbStruct = Os.stat(obbDir.getPath());

        assertThat(dataDir.exists()).isTrue();
        assertThat(upperCaseDataDir.exists()).isTrue();
        assertThat(obbDir.exists()).isTrue();
        assertThat(upperCaseObbDir.exists()).isTrue();

        StructStat afterDataStruct = Os.stat(upperCaseDataDir.getPath());
        StructStat afterObbStruct = Os.stat(upperCaseObbDir.getPath());

        assertThat(beforeDataStruct.st_dev).isEqualTo(afterDataStruct.st_dev);
        assertThat(beforeObbStruct.st_dev).isEqualTo(afterObbStruct.st_dev);
    }

    @Test
    public void testCacheConsistencyForCaseInsensitivity() throws Exception {
        File upperCaseFile = new File(getDownloadDir(), "CACHE_CONSISTENCY_FOR_CASE_INSENSITIVITY");
        File lowerCaseFile = new File(getDownloadDir(), "cache_consistency_for_case_insensitivity");

        try (ParcelFileDescriptor upperCasePfd =
                    ParcelFileDescriptor.open(upperCaseFile, MODE_READ_WRITE | MODE_CREATE);
             ParcelFileDescriptor lowerCasePfd =
                    ParcelFileDescriptor.open(lowerCaseFile, MODE_READ_WRITE | MODE_CREATE)) {

            assertRWR(upperCasePfd, lowerCasePfd);
            assertRWR(lowerCasePfd, upperCasePfd);
        } finally {
            upperCaseFile.delete();
            lowerCaseFile.delete();
        }
    }

    @Test
    public void testInsertDefaultPrimaryCaseInsensitiveCheck() throws Exception {
        final File podcastsDir = getPodcastsDir();
        final File podcastsDirLowerCase =
                new File(getExternalStorageDir(), Environment.DIRECTORY_PODCASTS.toLowerCase());
        final File fileInPodcastsDirLowerCase = new File(podcastsDirLowerCase, AUDIO_FILE_NAME);
        try {
            // Delete the directory if it already exists
            if (podcastsDir.exists()) {
                deleteRecursivelyAsLegacyApp(podcastsDir);
            }
            assertThat(podcastsDir.exists()).isFalse();
            assertThat(podcastsDirLowerCase.exists()).isFalse();

            // Create the directory with lower case
            assertThat(podcastsDirLowerCase.mkdir()).isTrue();
            // Because of case-insensitivity, even though directory is created
            // with lower case, we should be able to see both directory names.
            assertThat(podcastsDirLowerCase.exists()).isTrue();
            assertThat(podcastsDir.exists()).isTrue();

            // File creation with lower case path of podcasts directory should not fail
            assertThat(fileInPodcastsDirLowerCase.createNewFile()).isTrue();
        } finally {
            fileInPodcastsDirLowerCase.delete();
            deleteRecursivelyAsLegacyApp(podcastsDirLowerCase);
            podcastsDir.mkdirs();
        }
    }

    private void createDeleteCreate(File create, File delete) throws Exception {
        try {
            assertThat(create.createNewFile()).isTrue();
            // Wait for the kernel to update the dentry cache.
            Thread.sleep(100);

            assertThat(delete.delete()).isTrue();
            // Wait for the kernel to clean up the dentry cache.
            Thread.sleep(100);

            assertThat(create.createNewFile()).isTrue();
            // Wait for the kernel to update the dentry cache.
            Thread.sleep(100);
        } finally {
            create.delete();
            delete.delete();
        }
    }

    @Test
    public void testReadStorageInvalidation() throws Exception {
        if (SdkLevel.isAtLeastT()) {
            testAppOpInvalidation(
                    APP_E,
                new File(getDcimDir(), "read_storage.jpg"),
                Manifest.permission.READ_MEDIA_IMAGES,
                AppOpsManager.OPSTR_READ_MEDIA_IMAGES,
                /* forWrite */ false);
        } else {
            testAppOpInvalidation(APP_E, new File(getDcimDir(), "read_storage.jpg"),
                Manifest.permission.READ_EXTERNAL_STORAGE,
                AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE, /* forWrite */ false);
        }
    }

    @Test
    public void testWriteStorageInvalidation() throws Exception {
        testAppOpInvalidation(APP_E_LEGACY, new File(getDcimDir(), "write_storage.jpg"),
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE, /* forWrite */ true);
    }

    @Test
    public void testManageStorageInvalidation() throws Exception {
        testAppOpInvalidation(APP_E, new File(getDownloadDir(), "manage_storage.pdf"),
                /* permission */ null, OPSTR_MANAGE_EXTERNAL_STORAGE, /* forWrite */ true);
    }

    @Test
    public void testWriteImagesInvalidation() throws Exception {
        testAppOpInvalidation(APP_E, new File(getDcimDir(), "write_images.jpg"),
                /* permission */ null, AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, /* forWrite */ true);
    }

    @Test
    public void testWriteVideoInvalidation() throws Exception {
        testAppOpInvalidation(APP_E, new File(getDcimDir(), "write_video.mp4"),
                /* permission */ null, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO, /* forWrite */ true);
    }

    @FlakyTest(bugId = 324388050)
    @Test
    public void testAccessMediaLocationInvalidation() throws Exception {
        File imgFile = new File(getDcimDir(), "access_media_location.jpg");

        try {
            // Setup image with sensitive data on external storage
            HashMap<String, String> originalExif =
                    getExifMetadataFromRawResource(R.raw.img_with_metadata);
            try (InputStream in =
                         getContext().getResources().openRawResource(R.raw.img_with_metadata);
                 FileOutputStream out = new FileOutputStream(imgFile)) {
                // Dump the image we have to external storage
                FileUtils.copy(in, out);
                // Sync file to disk to ensure file is fully written to the lower fs.
                out.getFD().sync();
            }
            HashMap<String, String> exif = getExifMetadataFromFile(imgFile);
            assertExifMetadataMatch(exif, originalExif);

            // Install test app
            installAppWithStoragePermissions(APP_GENERAL_ONLY);

            // Grant A_M_L and verify access to sensitive data
            grantPermission(APP_GENERAL_ONLY.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);
            pollForPermission(APP_GENERAL_ONLY.getPackageName(),
                    Manifest.permission.ACCESS_MEDIA_LOCATION, /* granted */ true);
            HashMap<String, String> exifFromTestApp =
                    readExifMetadataFromTestApp(APP_GENERAL_ONLY, imgFile.getPath());
            assertExifMetadataMatch(exifFromTestApp, originalExif);

            // Revoke A_M_L and verify sensitive data redaction
            revokePermission(
                    APP_GENERAL_ONLY.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);
            // revokePermission waits for permission status to be updated, but MediaProvider still
            // needs to get permission change callback and clear its permission cache.
            pollForPermission(APP_GENERAL_ONLY.getPackageName(),
                    Manifest.permission.ACCESS_MEDIA_LOCATION, /* granted */ false);
            Thread.sleep(500);
            exifFromTestApp = readExifMetadataFromTestApp(APP_GENERAL_ONLY, imgFile.getPath());
            assertExifMetadataMismatch(exifFromTestApp, originalExif);

            // Re-grant A_M_L and verify access to sensitive data
            grantPermission(APP_GENERAL_ONLY.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);
            // grantPermission waits for permission status to be updated, but MediaProvider still
            // needs to get permission change callback and clear its permission cache.
            pollForPermission(APP_GENERAL_ONLY.getPackageName(),
                    Manifest.permission.ACCESS_MEDIA_LOCATION, /* granted */ true);
            Thread.sleep(500);
            exifFromTestApp = readExifMetadataFromTestApp(APP_GENERAL_ONLY, imgFile.getPath());
            assertExifMetadataMatch(exifFromTestApp, originalExif);
        } finally {
            imgFile.delete();
            uninstallAppNoThrow(APP_GENERAL_ONLY);
        }
    }

    @Test
    public void testAppUpdateInvalidation() throws Exception {
        File file = new File(getDcimDir(), "app_update.jpg");
        try {
            assertThat(file.createNewFile()).isTrue();

            addressStoragePermissions(APP_E_LEGACY.getPackageName(), true);
            grantPermission(APP_E_LEGACY.getPackageName(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE); // Grants write access for legacy

            // Legacy app can read and write media files contributed by others
            assertThat(canOpenFileAs(APP_E_LEGACY, file, /* forWrite */ false)).isTrue();
            assertThat(canOpenFileAs(APP_E_LEGACY, file, /* forWrite */ true)).isTrue();

            // Update to non-legacy
            addressStoragePermissions(APP_E.getPackageName(), true);
            grantPermission(APP_E_LEGACY.getPackageName(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE); // No effect for non-legacy

            // Non-legacy app can read media files contributed by others
            assertThat(canOpenFileAs(APP_E, file, /* forWrite */ false)).isTrue();
            // But cannot write
            assertThat(canOpenFileAs(APP_E, file, /* forWrite */ true)).isFalse();
        } finally {
            file.delete();
            revokePermission(APP_E_LEGACY.getPackageName(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    @FlakyTest(bugId = 324551195)
    @Test
    public void testAppReinstallInvalidation() throws Exception {
        File file = new File(getDcimDir(), "app_reinstall.jpg");

        try {
            assertThat(file.createNewFile()).isTrue();

            // Install
            installAppWithStoragePermissions(APP_GENERAL_ONLY);
            assertThat(canOpenFileAs(APP_GENERAL_ONLY, file, /* forWrite */ false)).isTrue();

            // Re-install
            uninstallAppNoThrow(APP_GENERAL_ONLY);
            installApp(APP_GENERAL_ONLY);
            assertThat(canOpenFileAs(APP_GENERAL_ONLY, file, /* forWrite */ false)).isFalse();
        } finally {
            file.delete();
            uninstallAppNoThrow(APP_GENERAL_ONLY);
        }
    }

    private void testAppOpInvalidation(TestApp app, File file, @Nullable String permission,
            String opstr, boolean forWrite) throws Exception {
        try {
            addressStoragePermissions(app.getPackageName(), false);
            assertThat(file.createNewFile()).isTrue();
            assertAppOpInvalidation(app, file, permission, opstr, forWrite);
        } finally {
            file.delete();
        }
    }

    /** If {@code permission} is null, appops are flipped, otherwise permissions are flipped */
    private void assertAppOpInvalidation(TestApp app, File file, @Nullable String permission,
            String opstr, boolean forWrite) throws Exception {
        String packageName = app.getPackageName();
        int uid = getContext().getPackageManager().getPackageUid(packageName, 0);

        // Deny
        if (permission != null) {
            revokePermission(packageName, permission);
        } else {
            denyAppOpsToUid(uid, opstr);
            // TODO(191724755): Poll for AppOp state change instead
            Thread.sleep(200);
        }
        assertThat(canOpenFileAs(app, file, forWrite)).isFalse();

        // Grant
        if (permission != null) {
            grantPermission(packageName, permission);
        } else {
            allowAppOpsToUid(uid, opstr);
            // TODO(191724755): Poll for AppOp state change instead
            Thread.sleep(200);
        }
        assertThat(canOpenFileAs(app, file, forWrite)).isTrue();
        // Deny
        if (permission != null) {
            revokePermission(packageName, permission);
        } else {
            denyAppOpsToUid(uid, opstr);
            // TODO(191724755): Poll for AppOp state change instead
            Thread.sleep(200);
        }
        assertThat(canOpenFileAs(app, file, forWrite)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testDisableOpResetForSystemGallery() throws Exception {
        final File otherAppImageFile = new File(getDcimDir(), "other_" + IMAGE_FILE_NAME);
        final File otherAppVideoFile = new File(getDcimDir(), "other_" + VIDEO_FILE_NAME);

        try {
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            // Have another app create an image file
            assertThat(createFileAs(APP_B_NO_PERMS, otherAppImageFile.getPath())).isTrue();
            assertThat(otherAppImageFile.exists()).isTrue();

            // Have another app create a video file
            assertThat(createFileAs(APP_B_NO_PERMS, otherAppVideoFile.getPath())).isTrue();
            assertThat(otherAppVideoFile.exists()).isTrue();

            assertCanWriteAndRead(otherAppImageFile, BYTES_DATA1);
            assertCanWriteAndRead(otherAppVideoFile, BYTES_DATA1);

            // Reset app op should not reset System Gallery privileges
            executeShellCommand("appops reset " + THIS_PACKAGE_NAME);

            // Assert we can still write to images/videos
            assertCanWriteAndRead(otherAppImageFile, BYTES_DATA2);
            assertCanWriteAndRead(otherAppVideoFile, BYTES_DATA2);

        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, otherAppImageFile.getAbsolutePath());
            deleteFileAsNoThrow(APP_B_NO_PERMS, otherAppVideoFile.getAbsolutePath());
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testSystemGalleryAppHasFullAccessToImages() throws Exception {
        final File otherAppImageFile = new File(getDcimDir(), "other_" + IMAGE_FILE_NAME);
        final File topLevelImageFile = new File(getExternalStorageDir(), IMAGE_FILE_NAME);
        final File imageInAnObviouslyWrongPlace = new File(getMusicDir(), IMAGE_FILE_NAME);

        try {
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            // Have another app create an image file
            assertThat(createFileAs(APP_B_NO_PERMS, otherAppImageFile.getPath())).isTrue();
            assertThat(otherAppImageFile.exists()).isTrue();

            // Assert we can write to the file
            try (FileOutputStream fos = new FileOutputStream(otherAppImageFile)) {
                fos.write(BYTES_DATA1);
            }

            // Assert we can read from the file
            assertFileContent(otherAppImageFile, BYTES_DATA1);

            // Assert has access to redacted information
            RedactionTestHelper.assertConsistentNonRedactedAccess(otherAppImageFile,
                    R.raw.img_with_metadata);

            // Assert we can delete the file
            assertThat(otherAppImageFile.delete()).isTrue();
            assertThat(otherAppImageFile.exists()).isFalse();

            // Can create an image anywhere
            assertCanCreateFile(topLevelImageFile);
            assertCanCreateFile(imageInAnObviouslyWrongPlace);

            // Put the file back in its place and let APP B delete it
            assertThat(otherAppImageFile.createNewFile()).isTrue();
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, otherAppImageFile.getAbsolutePath());
            otherAppImageFile.delete();
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testSystemGalleryAppHasNoFullAccessToAudio() throws Exception {
        final File otherAppAudioFile = new File(getMusicDir(), "other_" + AUDIO_FILE_NAME);
        final File topLevelAudioFile = new File(getExternalStorageDir(), AUDIO_FILE_NAME);
        final File audioInAnObviouslyWrongPlace = new File(getPicturesDir(), AUDIO_FILE_NAME);

        try {
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            // Have another app create an audio file
            assertThat(createFileAs(APP_B_NO_PERMS, otherAppAudioFile.getPath())).isTrue();
            assertThat(otherAppAudioFile.exists()).isTrue();

            // Assert we can't access the file
            assertThat(canOpen(otherAppAudioFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(otherAppAudioFile, /* forWrite */ true)).isFalse();

            // Assert we can't delete the file
            assertThat(otherAppAudioFile.delete()).isFalse();

            // Can't create an audio file where it doesn't belong
            assertThrows(IOException.class, "Operation not permitted",
                    () -> {
                        topLevelAudioFile.createNewFile();
                    });
            assertThrows(IOException.class, "Operation not permitted",
                    () -> {
                        audioInAnObviouslyWrongPlace.createNewFile();
                    });
        } finally {
            deleteFileAs(APP_B_NO_PERMS, otherAppAudioFile.getPath());
            topLevelAudioFile.delete();
            audioInAnObviouslyWrongPlace.delete();
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testSystemGalleryCanRenameImagesAndVideos() throws Exception {
        final File otherAppVideoFile = new File(getDcimDir(), "other_" + VIDEO_FILE_NAME);
        final File imageFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        final File videoFile = new File(getPicturesDir(), VIDEO_FILE_NAME);
        final File topLevelVideoFile = new File(getExternalStorageDir(), VIDEO_FILE_NAME);
        final File musicFile = new File(getMusicDir(), AUDIO_FILE_NAME);
        try {
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            // Have another app create a video file
            assertThat(createFileAs(APP_B_NO_PERMS, otherAppVideoFile.getPath())).isTrue();
            assertThat(otherAppVideoFile.exists()).isTrue();

            // Write some data to the file
            try (FileOutputStream fos = new FileOutputStream(otherAppVideoFile)) {
                fos.write(BYTES_DATA1);
            }
            assertFileContent(otherAppVideoFile, BYTES_DATA1);

            // Assert we can rename the file and ensure the file has the same content
            assertCanRenameFile(otherAppVideoFile, videoFile);
            assertFileContent(videoFile, BYTES_DATA1);
            // We can even move it to the top level directory
            assertCanRenameFile(videoFile, topLevelVideoFile);
            assertFileContent(topLevelVideoFile, BYTES_DATA1);
            // And we can even convert it into an image file, because why not?
            assertCanRenameFile(topLevelVideoFile, imageFile);
            assertFileContent(imageFile, BYTES_DATA1);

            // We can convert it to a music file, but we won't have access to music file after
            // renaming.
            assertThat(imageFile.renameTo(musicFile)).isTrue();
            assertThat(getFileRowIdFromDatabase(musicFile)).isEqualTo(-1);
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, otherAppVideoFile.getAbsolutePath());
            imageFile.delete();
            videoFile.delete();
            topLevelVideoFile.delete();
            executeShellCommand("rm  " + musicFile.getAbsolutePath());
            MediaStore.scanFile(getContentResolver(), musicFile);
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * Test that basic file path restrictions are enforced on file rename.
     */
    @Test
    public void testRenameFile() throws Exception {
        final File downloadDir = getDownloadDir();
        final File nonMediaDir = new File(downloadDir, TEST_DIRECTORY_NAME);
        final File pdfFile1 = new File(downloadDir, NONMEDIA_FILE_NAME);
        final File pdfFile2 = new File(nonMediaDir, NONMEDIA_FILE_NAME);
        final File videoFile1 = new File(getDcimDir(), VIDEO_FILE_NAME);
        final File videoFile2 = new File(getMoviesDir(), VIDEO_FILE_NAME);
        final File videoFile3 = new File(downloadDir, VIDEO_FILE_NAME);

        try {
            // Renaming non media file to media directory is not allowed.
            assertThat(pdfFile1.createNewFile()).isTrue();
            assertCantRenameFile(pdfFile1, new File(getDcimDir(), NONMEDIA_FILE_NAME));
            assertCantRenameFile(pdfFile1, new File(getMusicDir(), NONMEDIA_FILE_NAME));
            assertCantRenameFile(pdfFile1, new File(getMoviesDir(), NONMEDIA_FILE_NAME));

            // Renaming non media files to non media directories is allowed.
            if (!nonMediaDir.exists()) {
                assertThat(nonMediaDir.mkdirs()).isTrue();
            }
            // App can rename pdfFile to non media directory.
            assertCanRenameFile(pdfFile1, pdfFile2);

            assertThat(videoFile1.createNewFile()).isTrue();
            // App can rename video file to Movies directory
            assertCanRenameFile(videoFile1, videoFile2);
            // App can rename video file to Download directory
            assertCanRenameFile(videoFile2, videoFile3);
        } finally {
            pdfFile1.delete();
            pdfFile2.delete();
            videoFile1.delete();
            videoFile2.delete();
            videoFile3.delete();
            deleteRecursively(nonMediaDir);
        }
    }

    /**
     * Test that renaming file to different mime type is allowed.
     */
    @Test
    public void testRenameFileType() throws Exception {
        final File pdfFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        final File videoFile = new File(getDcimDir(), VIDEO_FILE_NAME);
        try {
            assertThat(pdfFile.createNewFile()).isTrue();
            assertThat(videoFile.exists()).isFalse();
            // Moving pdfFile to DCIM directory is not allowed.
            assertCantRenameFile(pdfFile, new File(getDcimDir(), NONMEDIA_FILE_NAME));
            // However, moving pdfFile to DCIM directory with changing the mime type to video is
            // allowed.
            assertCanRenameFile(pdfFile, videoFile);

            // On rename, MediaProvider database entry for pdfFile should be updated with new
            // videoFile path and mime type should be updated to video/mp4.
            assertThat(getFileMimeTypeFromDatabase(videoFile)).isEqualTo("video/mp4");
        } finally {
            pdfFile.delete();
            videoFile.delete();
        }
    }

    /**
     * Test that renaming files overwrites files in newPath.
     */
    @Test
    public void testRenameAndReplaceFile() throws Exception {
        final File videoFile1 = new File(getDcimDir(), VIDEO_FILE_NAME);
        final File videoFile2 = new File(getMoviesDir(), VIDEO_FILE_NAME);
        final ContentResolver cr = getContentResolver();
        try {
            assertThat(videoFile1.createNewFile()).isTrue();
            assertThat(videoFile2.createNewFile()).isTrue();
            final Uri uriVideoFile1 = MediaStore.scanFile(cr, videoFile1);
            final Uri uriVideoFile2 = MediaStore.scanFile(cr, videoFile2);

            // Renaming a file which replaces file in newPath videoFile2 is allowed.
            assertCanRenameFile(videoFile1, videoFile2);

            // Uri of videoFile2 should be accessible after rename.
            try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uriVideoFile2, "rw")) {
                assertThat(pfd).isNotNull();
            }

            // Uri of videoFile1 should not be accessible after rename.
            assertThrows(FileNotFoundException.class,
                    () -> {
                        cr.openFileDescriptor(uriVideoFile1, "rw");
                    });
        } finally {
            videoFile1.delete();
            videoFile2.delete();
        }
    }

    /**
     * Test that ScanFile() after renaming file extension updates the right
     * MIME type from the file metadata.
     */
    @Test
    public void testScanUpdatesMimeTypeForRenameFileExtension() throws Exception {
        final String audioFileName = "ScopedStorageDeviceTest_" + NONCE;
        final File mpegFile = new File(getMusicDir(), audioFileName + ".mp3");
        final File nonMpegFile = new File(getMusicDir(), audioFileName + ".snd");
        try {
            // Copy audio content to mpegFile
            try (InputStream in =
                         getContext().getResources().openRawResource(R.raw.test_audio);
                 FileOutputStream out = new FileOutputStream(mpegFile)) {
                FileUtils.copy(in, out);
                out.getFD().sync();
            }
            assertThat(MediaStore.scanFile(getContentResolver(), mpegFile)).isNotNull();
            assertThat(getFileMimeTypeFromDatabase(mpegFile)).isEqualTo("audio/mpeg");

            // This rename changes MIME type from audio/mpeg to audio/basic
            assertCanRenameFile(mpegFile, nonMpegFile);
            assertThat(getFileMimeTypeFromDatabase(nonMpegFile)).isNotEqualTo("audio/mpeg");

            assertThat(MediaStore.scanFile(getContentResolver(), nonMpegFile)).isNotNull();
            // Above scan should read file metadata and update the MIME type to audio/mpeg
            assertThat(getFileMimeTypeFromDatabase(nonMpegFile)).isEqualTo("audio/mpeg");
        } finally {
            mpegFile.delete();
            nonMpegFile.delete();
        }
    }

    /**
     * Test that app without write permission for file can't update the file.
     */
    @Test
    public void testRenameFileNotOwned() throws Exception {
        final File videoFile1 = new File(getDcimDir(), VIDEO_FILE_NAME);
        final File videoFile2 = new File(getMoviesDir(), VIDEO_FILE_NAME);
        try {
            assertThat(createFileAs(APP_B_NO_PERMS, videoFile1.getAbsolutePath())).isTrue();
            // App can't rename a file owned by APP B.
            assertCantRenameFile(videoFile1, videoFile2);

            assertThat(videoFile2.createNewFile()).isTrue();
            // App can't rename a file to videoFile1 which is owned by APP B.
            assertCantRenameFile(videoFile2, videoFile1);
            // TODO(b/146346138): Test that app with right URI permission should be able to rename
            // the corresponding file
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, videoFile1.getAbsolutePath());
            videoFile2.delete();
        }
    }

    /**
     * Test that renaming file paths to an external directory such as Android/* and Android/* /*
     * except Android/media/* /* is not allowed.
     */
    @Test
    public void testRenameFileToAppSpecificDir() throws Exception {
        final File testFile = new File(getExternalMediaDir(), IMAGE_FILE_NAME);
        final File testFileNew = new File(getExternalMediaDir(), NONMEDIA_FILE_NAME);

        try {
            // Create a file in app's external media directory
            if (!testFile.exists()) {
                assertThat(testFile.createNewFile()).isTrue();
            }

            final String androidDirPath = getExternalStorageDir().getPath() + "/Android";

            // Verify that we can't rename a file to Android/ or Android/data or
            // Android/media directory
            assertCantRenameFile(testFile, new File(androidDirPath, IMAGE_FILE_NAME));
            assertCantRenameFile(testFile, new File(androidDirPath + "/data", IMAGE_FILE_NAME));
            assertCantRenameFile(testFile, new File(androidDirPath + "/media", IMAGE_FILE_NAME));

            // Verify that we can rename a file to app specific media directory.
            assertCanRenameFile(testFile, testFileNew);
        } finally {
            testFile.delete();
            testFileNew.delete();
        }
    }

    /**
     * Test that renaming directories is allowed and aligns to default directory restrictions.
     */
    @Test
    public void testRenameDirectory() throws Exception {
        final File dcimDir = getDcimDir();
        final File downloadDir = getDownloadDir();
        final String nonMediaDirectoryName = TEST_DIRECTORY_NAME + "NonMedia";
        final File nonMediaDirectory = new File(downloadDir, nonMediaDirectoryName);
        final File pdfFile = new File(nonMediaDirectory, NONMEDIA_FILE_NAME);

        final String mediaDirectoryName = TEST_DIRECTORY_NAME + "Media";
        final File mediaDirectory1 = new File(dcimDir, mediaDirectoryName);
        final File videoFile1 = new File(mediaDirectory1, VIDEO_FILE_NAME);
        final File mediaDirectory2 = new File(downloadDir, mediaDirectoryName);
        final File videoFile2 = new File(mediaDirectory2, VIDEO_FILE_NAME);
        final File mediaDirectory3 = new File(getMoviesDir(), TEST_DIRECTORY_NAME);
        final File videoFile3 = new File(mediaDirectory3, VIDEO_FILE_NAME);
        final File mediaDirectory4 = new File(mediaDirectory3, mediaDirectoryName);

        try {
            if (!nonMediaDirectory.exists()) {
                assertThat(nonMediaDirectory.mkdirs()).isTrue();
            }
            assertThat(pdfFile.createNewFile()).isTrue();
            // Move directory with pdf file to DCIM directory is not allowed.
            assertThat(nonMediaDirectory.renameTo(new File(dcimDir, nonMediaDirectoryName)))
                    .isFalse();

            if (!mediaDirectory1.exists()) {
                assertThat(mediaDirectory1.mkdirs()).isTrue();
            }
            assertThat(videoFile1.createNewFile()).isTrue();
            // Renaming to and from default directories is not allowed.
            assertThat(mediaDirectory1.renameTo(dcimDir)).isFalse();
            // Moving top level default directories is not allowed.
            assertCantRenameDirectory(downloadDir, new File(dcimDir, TEST_DIRECTORY_NAME), null);

            // Moving media directory to Download directory is allowed.
            // Allow falling back to a recursive copy, since the rename will fail on ARCVM
            // due to EXDEV.
            assertCanRenameDirectory(mediaDirectory1, mediaDirectory2, new File[] {videoFile1},
                    new File[] {videoFile2}, true /* allowCopyFallback */);

            // Moving media directory to Movies directory and renaming directory in new path is
            // allowed.
            // Allow falling back to a recursive copy, since the rename will fail on ARCVM
            // due to EXDEV.
            assertCanRenameDirectory(mediaDirectory2, mediaDirectory3, new File[] {videoFile2},
                    new File[] {videoFile3}, true /* allowCopyFallback */);

            // Can't rename a mediaDirectory to non empty non Media directory.
            assertCantRenameDirectory(mediaDirectory3, nonMediaDirectory, new File[] {videoFile3});
            // Can't rename a file to a directory.
            assertCantRenameFile(videoFile3, mediaDirectory3);
            // Can't rename a directory to file.
            assertCantRenameDirectory(mediaDirectory3, pdfFile, null);
            if (!mediaDirectory4.exists()) {
                assertThat(mediaDirectory4.mkdir()).isTrue();
            }
            // Can't rename a directory to subdirectory of itself.
            assertCantRenameDirectory(mediaDirectory3, mediaDirectory4, new File[] {videoFile3});

        } finally {
            pdfFile.delete();
            deleteRecursively(nonMediaDirectory);

            videoFile1.delete();
            videoFile2.delete();
            videoFile3.delete();
            deleteRecursively(mediaDirectory1);
            deleteRecursively(mediaDirectory2);
            deleteRecursively(mediaDirectory3);
            deleteRecursively(mediaDirectory4);
        }
    }

    /**
     * Test that renaming directory checks file ownership permissions.
     */
    @Test
    public void testRenameDirectoryNotOwned() throws Exception {
        final String mediaDirectoryName = TEST_DIRECTORY_NAME + "Media";
        File mediaDirectory1 = new File(getDcimDir(), mediaDirectoryName);
        File mediaDirectory2 = new File(getMoviesDir(), mediaDirectoryName);
        File videoFile = new File(mediaDirectory1, VIDEO_FILE_NAME);

        try {
            if (!mediaDirectory1.exists()) {
                assertThat(mediaDirectory1.mkdirs()).isTrue();
            }
            assertThat(createFileAs(APP_B_NO_PERMS, videoFile.getAbsolutePath())).isTrue();
            // App doesn't have access to videoFile1, can't rename mediaDirectory1.
            assertThat(mediaDirectory1.renameTo(mediaDirectory2)).isFalse();
            assertThat(videoFile.exists()).isTrue();
            // Test app can delete the file since the file is not moved to new directory.
            assertThat(deleteFileAs(APP_B_NO_PERMS, videoFile.getAbsolutePath())).isTrue();
        } finally {
            deleteFileAsNoThrow(APP_B_NO_PERMS, videoFile.getAbsolutePath());
            deleteRecursively(mediaDirectory1);
            deleteRecursively(mediaDirectory2);
        }
    }

    /**
     * Test renaming empty directory is allowed
     */
    @Test
    public void testRenameEmptyDirectory() throws Exception {
        final String emptyDirectoryName = TEST_DIRECTORY_NAME + "Media";
        File emptyDirectoryOldPath = new File(getDcimDir(), emptyDirectoryName);
        File emptyDirectoryNewPath = new File(getMoviesDir(), TEST_DIRECTORY_NAME + "23456");
        try {
            if (emptyDirectoryOldPath.exists()) {
                executeShellCommand("rm -r " + emptyDirectoryOldPath.getPath());
            }
            assertThat(emptyDirectoryOldPath.mkdirs()).isTrue();
            assertCanRenameDirectory(emptyDirectoryOldPath, emptyDirectoryNewPath, null, null);
        } finally {
            deleteRecursively(emptyDirectoryOldPath);
            deleteRecursively(emptyDirectoryNewPath);
        }
    }

    /**
     * Test that apps can create and delete hidden file.
     */
    @Test
    public void testCanCreateHiddenFile() throws Exception {
        final File hiddenImageFile = new File(getDownloadDir(), ".hiddenFile" + IMAGE_FILE_NAME);
        try {
            assertThat(hiddenImageFile.createNewFile()).isTrue();
            // Write to hidden file is allowed.
            try (FileOutputStream fos = new FileOutputStream(hiddenImageFile)) {
                fos.write(BYTES_DATA1);
            }
            assertFileContent(hiddenImageFile, BYTES_DATA1);

            assertNotMediaTypeImage(hiddenImageFile);

            assertDirectoryContains(getDownloadDir(), hiddenImageFile);
            assertThat(getFileRowIdFromDatabase(hiddenImageFile)).isNotEqualTo(-1);

            // We can delete hidden file
            assertThat(hiddenImageFile.delete()).isTrue();
            assertThat(hiddenImageFile.exists()).isFalse();
        } finally {
            hiddenImageFile.delete();
        }
    }

    /**
     * Test that FUSE upper-fs is consistent with lower-fs after the lower-fs fd is closed.
     */
    @Test
    public void testInodeStatConsistency() throws Exception {
        File file = new File(getDcimDir(), IMAGE_FILE_NAME);

        try {
            byte[] writeBuffer = new byte[10];
            Arrays.fill(writeBuffer, (byte) 1);

            assertThat(file.createNewFile()).isTrue();
            // Scanning a file is essential as files created via filepath will be marked
            // as isPending, and we do not set listener for pending files as it can lead to
            // performance overhead. See: I34611f0ee897dc676e7653beb7943aa6de58c55a.
            MediaStore.scanFile(getContentResolver(), file);

            // File operation #1 (to lower-fs)
            ParcelFileDescriptor writePfd = openWithMediaProvider(file, "rw");

            // File operation #2 (to fuse). This caches the inode for the file.
            file.exists();

            // Write bytes directly to lower-fs
            Os.pwrite(writePfd.getFileDescriptor(), writeBuffer, 0, 10, 0);

            // Close should invalidate inode cache for this file.
            writePfd.close();
            Thread.sleep(1000);

            long fuseFileSize = file.length();
            assertThat(writeBuffer.length).isEqualTo(fuseFileSize);
        } finally {
            file.delete();
        }
    }

    /**
     * Test that apps can rename a hidden file.
     */
    @Test
    public void testCanRenameHiddenFile() throws Exception {
        final String hiddenFileName = ".hidden" + IMAGE_FILE_NAME;
        final File hiddenImageFile1 = new File(getDcimDir(), hiddenFileName);
        final File hiddenImageFile2 = new File(getDownloadDir(), hiddenFileName);
        final File imageFile = new File(getDownloadDir(), IMAGE_FILE_NAME);
        try {
            assertThat(hiddenImageFile1.createNewFile()).isTrue();
            assertCanRenameFile(hiddenImageFile1, hiddenImageFile2);
            assertNotMediaTypeImage(hiddenImageFile2);

            // We can also rename hidden file to non-hidden
            assertCanRenameFile(hiddenImageFile2, imageFile);
            assertIsMediaTypeImage(imageFile);

            // We can rename non-hidden file to hidden
            assertCanRenameFile(imageFile, hiddenImageFile1);
            assertNotMediaTypeImage(hiddenImageFile1);
        } finally {
            hiddenImageFile1.delete();
            hiddenImageFile2.delete();
            imageFile.delete();
        }
    }

    /**
     * Test that files in hidden directory have MEDIA_TYPE=MEDIA_TYPE_NONE
     */
    @Test
    public void testHiddenDirectory() throws Exception {
        final File hiddenDir = new File(getDownloadDir(), ".hidden" + TEST_DIRECTORY_NAME);
        final File hiddenImageFile = new File(hiddenDir, IMAGE_FILE_NAME);
        final File nonHiddenDir = new File(getDownloadDir(), TEST_DIRECTORY_NAME);
        final File imageFile = new File(nonHiddenDir, IMAGE_FILE_NAME);
        try {
            if (!hiddenDir.exists()) {
                assertThat(hiddenDir.mkdir()).isTrue();
            }
            assertThat(hiddenImageFile.createNewFile()).isTrue();

            assertNotMediaTypeImage(hiddenImageFile);

            // Renaming hiddenDir to nonHiddenDir makes the imageFile non-hidden and vice versa
            assertCanRenameDirectory(
                    hiddenDir, nonHiddenDir, new File[] {hiddenImageFile}, new File[] {imageFile});
            assertIsMediaTypeImage(imageFile);

            assertCanRenameDirectory(
                    nonHiddenDir, hiddenDir, new File[] {imageFile}, new File[] {hiddenImageFile});
            assertNotMediaTypeImage(hiddenImageFile);
        } finally {
            hiddenImageFile.delete();
            imageFile.delete();
            deleteRecursively(hiddenDir);
            deleteRecursively(nonHiddenDir);
        }
    }

    /**
     * Test that files in directory with nomedia have MEDIA_TYPE=MEDIA_TYPE_NONE
     */
    @Test
    public void testHiddenDirectory_nomedia() throws Exception {
        final File directoryNoMedia = new File(getDownloadDir(), "nomedia" + TEST_DIRECTORY_NAME);
        final File noMediaFile = new File(directoryNoMedia, ".nomedia");
        final File imageFile = new File(directoryNoMedia, IMAGE_FILE_NAME);
        final File videoFile = new File(directoryNoMedia, VIDEO_FILE_NAME);
        try {
            if (!directoryNoMedia.exists()) {
                assertThat(directoryNoMedia.mkdir()).isTrue();
            }
            assertThat(noMediaFile.createNewFile()).isTrue();
            assertThat(imageFile.createNewFile()).isTrue();

            assertNotMediaTypeImage(imageFile);

            // Deleting the .nomedia file makes the parent directory non hidden.
            noMediaFile.delete();
            MediaStore.scanFile(getContentResolver(), directoryNoMedia);
            assertIsMediaTypeImage(imageFile);

            // Creating the .nomedia file makes the parent directory hidden again
            assertThat(noMediaFile.createNewFile()).isTrue();
            MediaStore.scanFile(getContentResolver(), directoryNoMedia);
            assertNotMediaTypeImage(imageFile);

            // Renaming the .nomedia file to non hidden file makes the parent directory non hidden.
            assertCanRenameFile(noMediaFile, videoFile);
            assertIsMediaTypeImage(imageFile);
        } finally {
            noMediaFile.delete();
            imageFile.delete();
            videoFile.delete();
            deleteRecursively(directoryNoMedia);
        }
    }

    /**
     * Test that only file manager and app that created the hidden file can list it.
     */
    @Test
    public void testListHiddenFile() throws Exception {
        final File dcimDir = getDcimDir();
        final String hiddenImageFileName = ".hidden" + IMAGE_FILE_NAME;
        final File hiddenImageFile = new File(dcimDir, hiddenImageFileName);
        try {
            assertThat(hiddenImageFile.createNewFile()).isTrue();
            assertNotMediaTypeImage(hiddenImageFile);

            assertDirectoryContains(dcimDir, hiddenImageFile);

            // TestApp with read permissions can't see the hidden image file created by other app
            assertThat(listAs(APP_A_HAS_RES, dcimDir.getAbsolutePath()))
                    .doesNotContain(hiddenImageFileName);

            // But file manager can
            assertThat(listAs(APP_FM, dcimDir.getAbsolutePath()))
                    .contains(hiddenImageFileName);

            // Gallery cannot see the hidden image file created by other app
            final int resAppUid =
                    getContext().getPackageManager().getPackageUid(APP_A_HAS_RES.getPackageName(),
                            0);
            try {
                allowAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
                assertThat(listAs(APP_A_HAS_RES, dcimDir.getAbsolutePath()))
                        .doesNotContain(hiddenImageFileName);
            } finally {
                denyAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
            }
        } finally {
            hiddenImageFile.delete();
        }
    }

    @Test
    public void testOpenPendingAndTrashed() throws Exception {
        final File pendingImageFile = new File(getDcimDir(), IMAGE_FILE_NAME);
        final File trashedVideoFile = new File(getPicturesDir(), VIDEO_FILE_NAME);
        final File pendingPdfFile = new File(getDocumentsDir(), NONMEDIA_FILE_NAME);
        final File trashedPdfFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        Uri pendingImgaeFileUri = null;
        Uri trashedVideoFileUri = null;
        Uri pendingPdfFileUri = null;
        Uri trashedPdfFileUri = null;
        try {
            pendingImgaeFileUri = createPendingFile(pendingImageFile);
            assertOpenPendingOrTrashed(pendingImgaeFileUri, /*isImageOrVideo*/ true);

            pendingPdfFileUri = createPendingFile(pendingPdfFile);
            assertOpenPendingOrTrashed(pendingPdfFileUri, /*isImageOrVideo*/ false);

            trashedVideoFileUri = createTrashedFile(trashedVideoFile);
            assertOpenPendingOrTrashed(trashedVideoFileUri, /*isImageOrVideo*/ true);

            trashedPdfFileUri = createTrashedFile(trashedPdfFile);
            assertOpenPendingOrTrashed(trashedPdfFileUri, /*isImageOrVideo*/ false);

        } finally {
            deleteFiles(pendingImageFile, pendingImageFile, trashedVideoFile,
                    trashedPdfFile);
            deleteWithMediaProviderNoThrow(pendingImgaeFileUri, trashedVideoFileUri,
                    pendingPdfFileUri, trashedPdfFileUri);
        }
    }

    @Test
    public void testListPendingAndTrashed() throws Exception {
        final File imageFile = new File(getDcimDir(), IMAGE_FILE_NAME);
        final File pdfFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        Uri imageFileUri = null;
        Uri pdfFileUri = null;
        try {
            imageFileUri = createPendingFile(imageFile);
            // Check that only owner package, file manager and system gallery can list pending image
            // file.
            assertListPendingOrTrashed(imageFileUri, imageFile, /*isImageOrVideo*/ true);

            trashFileAndAssert(imageFileUri);
            // Check that only owner package, file manager and system gallery can list trashed image
            // file.
            assertListPendingOrTrashed(imageFileUri, imageFile, /*isImageOrVideo*/ true);

            pdfFileUri = createPendingFile(pdfFile);
            // Check that only owner package, file manager can list pending non media file.
            assertListPendingOrTrashed(pdfFileUri, pdfFile, /*isImageOrVideo*/ false);

            trashFileAndAssert(pdfFileUri);
            // Check that only owner package, file manager can list trashed non media file.
            assertListPendingOrTrashed(pdfFileUri, pdfFile, /*isImageOrVideo*/ false);
        } finally {
            deleteWithMediaProviderNoThrow(imageFileUri, pdfFileUri);
            deleteFiles(imageFile, pdfFile);
        }
    }

    @Test
    public void testDeletePendingAndTrashed_ownerCanDelete() throws Exception {
        final File pendingVideoFile = new File(getDcimDir(), VIDEO_FILE_NAME);
        final File trashedImageFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        final File pendingPdfFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        final File trashedPdfFile = new File(getDocumentsDir(), NONMEDIA_FILE_NAME);
        // Actual path of the file gets rewritten for pending and trashed files.
        String pendingVideoFilePath = null;
        String trashedImageFilePath = null;
        String pendingPdfFilePath = null;
        String trashedPdfFilePath = null;
        try {
            pendingVideoFilePath = getFilePathFromUri(createPendingFile(pendingVideoFile));
            trashedImageFilePath = getFilePathFromUri(createTrashedFile(trashedImageFile));
            pendingPdfFilePath = getFilePathFromUri(createPendingFile(pendingPdfFile));
            trashedPdfFilePath = getFilePathFromUri(createTrashedFile(trashedPdfFile));

            // App can delete its own pending and trashed file.
            assertCanDeletePaths(pendingVideoFilePath, trashedImageFilePath, pendingPdfFilePath,
                    trashedPdfFilePath);
        } finally {
            deletePaths(pendingVideoFilePath, trashedImageFilePath, pendingPdfFilePath,
                    trashedPdfFilePath);
            deleteFiles(pendingVideoFile, trashedImageFile, pendingPdfFile, trashedPdfFile);
        }
    }

    @Test
    public void testDeletePendingAndTrashed_otherAppCantDelete() throws Exception {
        final File pendingVideoFile = new File(getDcimDir(), VIDEO_FILE_NAME);
        final File trashedImageFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        final File pendingPdfFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        final File trashedPdfFile = new File(getDocumentsDir(), NONMEDIA_FILE_NAME);
        // Actual path of the file gets rewritten for pending and trashed files.
        String pendingVideoFilePath = null;
        String trashedImageFilePath = null;
        String pendingPdfFilePath = null;
        String trashedPdfFilePath = null;
        try {
            pendingVideoFilePath = getFilePathFromUri(createPendingFile(pendingVideoFile));
            trashedImageFilePath = getFilePathFromUri(createTrashedFile(trashedImageFile));
            pendingPdfFilePath = getFilePathFromUri(createPendingFile(pendingPdfFile));
            trashedPdfFilePath = getFilePathFromUri(createTrashedFile(trashedPdfFile));

            // App can't delete other app's pending and trashed file.
            assertCantDeletePathsAs(APP_A_HAS_RES, pendingVideoFilePath, trashedImageFilePath,
                    pendingPdfFilePath, trashedPdfFilePath);
        } finally {
            deletePaths(pendingVideoFilePath, trashedImageFilePath, pendingPdfFilePath,
                    trashedPdfFilePath);
            deleteFiles(pendingVideoFile, trashedImageFile, pendingPdfFile, trashedPdfFile);
        }
    }

    @Test
    public void testDeletePendingAndTrashed_fileManagerCanDelete() throws Exception {
        final File pendingVideoFile = new File(getDcimDir(), VIDEO_FILE_NAME);
        final File trashedImageFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        final File pendingPdfFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        final File trashedPdfFile = new File(getDocumentsDir(), NONMEDIA_FILE_NAME);
        // Actual path of the file gets rewritten for pending and trashed files.
        String pendingVideoFilePath = null;
        String trashedImageFilePath = null;
        String pendingPdfFilePath = null;
        String trashedPdfFilePath = null;
        try {
            pendingVideoFilePath = getFilePathFromUri(createPendingFile(pendingVideoFile));
            trashedImageFilePath = getFilePathFromUri(createTrashedFile(trashedImageFile));
            pendingPdfFilePath = getFilePathFromUri(createPendingFile(pendingPdfFile));
            trashedPdfFilePath = getFilePathFromUri(createTrashedFile(trashedPdfFile));

            // File Manager can delete any pending and trashed file
            assertCanDeletePathsAs(APP_FM, pendingVideoFilePath, trashedImageFilePath,
                    pendingPdfFilePath, trashedPdfFilePath);
        } finally {
            deletePaths(pendingVideoFilePath, trashedImageFilePath, pendingPdfFilePath,
                    trashedPdfFilePath);
            deleteFiles(pendingVideoFile, trashedImageFile, pendingPdfFile, trashedPdfFile);
        }
    }

    @Test
    public void testDeletePendingAndTrashed_systemGalleryCanDeleteMedia() throws Exception {
        final File pendingVideoFile = new File(getDcimDir(), VIDEO_FILE_NAME);
        final File trashedImageFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        final File pendingPdfFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        final File trashedPdfFile = new File(getDocumentsDir(), NONMEDIA_FILE_NAME);
        // Actual path of the file gets rewritten for pending and trashed files.
        String pendingVideoFilePath = null;
        String trashedImageFilePath = null;
        String pendingPdfFilePath = null;
        String trashedPdfFilePath = null;
        try {
            pendingVideoFilePath = getFilePathFromUri(createPendingFile(pendingVideoFile));
            trashedImageFilePath = getFilePathFromUri(createTrashedFile(trashedImageFile));
            pendingPdfFilePath = getFilePathFromUri(createPendingFile(pendingPdfFile));
            trashedPdfFilePath = getFilePathFromUri(createTrashedFile(trashedPdfFile));

            // System Gallery can delete any pending and trashed image or video file.
            final int resAppUid =
                    getContext().getPackageManager().getPackageUid(APP_A_HAS_RES.getPackageName(),
                            0);
            try {
                allowAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
                assertTrue(isMediaTypeImageOrVideo(new File(pendingVideoFilePath)));
                assertTrue(isMediaTypeImageOrVideo(new File(trashedImageFilePath)));
                assertCanDeletePathsAs(APP_A_HAS_RES, pendingVideoFilePath, trashedImageFilePath);

                // System Gallery can't delete other app's pending and trashed pdf file.
                assertFalse(isMediaTypeImageOrVideo(new File(pendingPdfFilePath)));
                assertFalse(isMediaTypeImageOrVideo(new File(trashedPdfFilePath)));
                assertCantDeletePathsAs(APP_A_HAS_RES, pendingPdfFilePath, trashedPdfFilePath);
            } finally {
                denyAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
            }
        } finally {
            deletePaths(pendingVideoFilePath, trashedImageFilePath, pendingPdfFilePath,
                    trashedPdfFilePath);
            deleteFiles(pendingVideoFile, trashedImageFile, pendingPdfFile, trashedPdfFile);
        }
    }

    @Test
    public void testSystemGalleryCanTrashOtherAndroidMediaFiles() throws Exception {
        final File otherVideoFile = new File(getAndroidMediaDir(),
                String.format("%s/%s", APP_B_NO_PERMS.getPackageName(), VIDEO_FILE_NAME));
        try {
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            assertThat(createFileAs(APP_B_NO_PERMS, otherVideoFile.getAbsolutePath())).isTrue();

            final Uri otherVideoUri = MediaStore.scanFile(getContentResolver(), otherVideoFile);
            assertNotNull(otherVideoUri);

            trashFileAndAssert(otherVideoUri);
            untrashFileAndAssert(otherVideoUri);
        } finally {
            otherVideoFile.delete();
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testSystemGalleryCanUpdateOtherAndroidMediaFiles() throws Exception {
        final File otherImageFile = new File(getAndroidMediaDir(),
                String.format("%s/%s", APP_B_NO_PERMS.getPackageName(), IMAGE_FILE_NAME));
        final File updatedImageFileInDcim = new File(getDcimDir(), IMAGE_FILE_NAME);
        try {
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            assertThat(createFileAs(APP_B_NO_PERMS, otherImageFile.getAbsolutePath())).isTrue();

            final Uri otherImageUri = MediaStore.scanFile(getContentResolver(), otherImageFile);
            assertNotNull(otherImageUri);

            final ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);
            // Test that we can move the file to "DCIM/"
            assertWithMessage("Result of ContentResolver#update for " + otherImageUri
                    + " with values " + values)
                    .that(getContentResolver().update(otherImageUri, values, Bundle.EMPTY))
                    .isEqualTo(1);
            assertThat(updatedImageFileInDcim.exists()).isTrue();
            assertThat(otherImageFile.exists()).isFalse();

            values.clear();
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    "Android/media/" + APP_B_NO_PERMS.getPackageName());
            // Test that we can move the file back to other app's owned path
            assertWithMessage("Result of ContentResolver#update for " + otherImageUri
                    + " with values " + values)
                    .that(getContentResolver().update(otherImageUri, values, Bundle.EMPTY))
                    .isEqualTo(1);
            assertThat(otherImageFile.exists()).isTrue();
        } finally {
            otherImageFile.delete();
            updatedImageFileInDcim.delete();
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testQueryOtherAppsFiles() throws Exception {
        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(getDcimDir(), "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(getMusicDir(), "other" + AUDIO_FILE_NAME);
        final File otherHiddenFile = new File(getPicturesDir(), ".otherHiddenFile.jpg");
        try {
            // Apps can't query other app's pending file, hence create file and publish it.
            assertCreatePublishedFilesAs(
                    APP_B_NO_PERMS, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);

            // Since the test doesn't have READ_EXTERNAL_STORAGE nor any other special permissions,
            // it can't query for another app's contents.
            assertCantQueryFile(otherAppImg);
            assertCantQueryFile(otherAppMusic);
            assertCantQueryFile(otherAppPdf);
            assertCantQueryFile(otherHiddenFile);
        } finally {
            deleteFilesAs(APP_B_NO_PERMS, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);
        }
    }

    @Test
    public void testSystemGalleryQueryOtherAppsFiles() throws Exception {
        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(getDcimDir(), "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(getMusicDir(), "other" + AUDIO_FILE_NAME);
        final File otherHiddenFile = new File(getPicturesDir(), ".otherHiddenFile.jpg");
        try {
            // Apps can't query other app's pending file, hence create file and publish it.
            assertCreatePublishedFilesAs(
                    APP_B_NO_PERMS, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);

            // System gallery apps have access to video and image files
            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            assertCanQueryAndOpenFile(otherAppImg, "rw");
            // System gallery doesn't have access to hidden image files of other app
            assertCantQueryFile(otherHiddenFile);
            // But no access to PDFs or music files
            assertCantQueryFile(otherAppMusic);
            assertCantQueryFile(otherAppPdf);
        } finally {
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
            deleteFilesAs(APP_B_NO_PERMS, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);
        }
    }

    /**
     * Test that System Gallery app can rename any directory under the default directories
     * designated for images and videos, even if they contain other apps' contents that
     * System Gallery doesn't have read access to.
     */
    @Test
    public void testSystemGalleryCanRenameImageAndVideoDirs() throws Exception {
        final File dirInDcim = new File(getDcimDir(), TEST_DIRECTORY_NAME);
        final File dirInPictures = new File(getPicturesDir(), TEST_DIRECTORY_NAME);
        final File dirInPodcasts = new File(getPodcastsDir(), TEST_DIRECTORY_NAME);
        final File otherAppImageFile1 = new File(dirInDcim, "other_" + IMAGE_FILE_NAME);
        final File otherAppVideoFile1 = new File(dirInDcim, "other_" + VIDEO_FILE_NAME);
        final File otherAppPdfFile1 = new File(dirInDcim, "other_" + NONMEDIA_FILE_NAME);
        final File otherAppImageFile2 = new File(dirInPictures, "other_" + IMAGE_FILE_NAME);
        final File otherAppVideoFile2 = new File(dirInPictures, "other_" + VIDEO_FILE_NAME);
        final File otherAppPdfFile2 = new File(dirInPictures, "other_" + NONMEDIA_FILE_NAME);
        try {
            assertThat(dirInDcim.exists() || dirInDcim.mkdir()).isTrue();

            executeShellCommand("touch " + otherAppPdfFile1);
            MediaStore.scanFile(getContentResolver(), otherAppPdfFile1);

            allowAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);

            assertCreateFilesAs(APP_A_HAS_RES, otherAppImageFile1, otherAppVideoFile1);

            // System gallery privileges don't go beyond DCIM, Movies and Pictures boundaries.
            assertCantRenameDirectory(dirInDcim, dirInPodcasts, /*oldFilesList*/ null);

            // Rename should succeed, but System Gallery still can't access that PDF file!
            assertCanRenameDirectory(dirInDcim, dirInPictures,
                    new File[] {otherAppImageFile1, otherAppVideoFile1},
                    new File[] {otherAppImageFile2, otherAppVideoFile2});
            assertThat(getFileRowIdFromDatabase(otherAppPdfFile1)).isEqualTo(-1);
            assertThat(getFileRowIdFromDatabase(otherAppPdfFile2)).isEqualTo(-1);
        } finally {
            executeShellCommand("rm " + otherAppPdfFile1);
            executeShellCommand("rm " + otherAppPdfFile2);
            MediaStore.scanFile(getContentResolver(), otherAppPdfFile1);
            MediaStore.scanFile(getContentResolver(), otherAppPdfFile2);
            otherAppImageFile1.delete();
            otherAppImageFile2.delete();
            otherAppVideoFile1.delete();
            otherAppVideoFile2.delete();
            otherAppPdfFile1.delete();
            otherAppPdfFile2.delete();
            deleteRecursively(dirInDcim);
            deleteRecursively(dirInPictures);
            denyAppOpsToUid(Process.myUid(), SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * Test that row ID corresponding to deleted path is restored on subsequent create.
     */
    @Test
    public void testCreateCanRestoreDeletedRowId() throws Exception {
        final File imageFile = new File(getDcimDir(), IMAGE_FILE_NAME);
        final ContentResolver cr = getContentResolver();

        try {
            assertThat(imageFile.createNewFile()).isTrue();
            final long oldRowId = getFileRowIdFromDatabase(imageFile);
            assertThat(oldRowId).isNotEqualTo(-1);
            final Uri uriOfOldFile = MediaStore.scanFile(cr, imageFile);
            assertThat(uriOfOldFile).isNotNull();

            assertThat(imageFile.delete()).isTrue();
            // We should restore old row Id corresponding to deleted imageFile.
            assertThat(imageFile.createNewFile()).isTrue();
            assertThat(getFileRowIdFromDatabase(imageFile)).isEqualTo(oldRowId);
            try (ParcelFileDescriptor pfd =  cr.openFileDescriptor(uriOfOldFile, "rw")) {
                assertThat(pfd).isNotNull();
            }


            assertThat(imageFile.delete()).isTrue();
            assertThat(createFileAs(APP_B_NO_PERMS, imageFile.getAbsolutePath())).isTrue();

            final Uri uriOfNewFile = MediaStore.scanFile(getContentResolver(), imageFile);
            assertThat(uriOfNewFile).isNotNull();
            // We shouldn't restore deleted row Id if delete & create are called from different apps
            assertThat(Integer.getInteger(uriOfNewFile.getLastPathSegment()))
                    .isNotEqualTo(oldRowId);
        } finally {
            imageFile.delete();
            deleteFileAsNoThrow(APP_B_NO_PERMS, imageFile.getAbsolutePath());
        }
    }

    /**
     * Test that row ID corresponding to deleted path is restored on subsequent rename.
     */
    @Test
    public void testRenameCanRestoreDeletedRowId() throws Exception {
        final File imageFile = new File(getDcimDir(), IMAGE_FILE_NAME);
        final File temporaryFile = new File(getDownloadDir(), IMAGE_FILE_NAME + "_.tmp");
        final ContentResolver cr = getContentResolver();

        try {
            assertThat(imageFile.createNewFile()).isTrue();
            final Uri oldUri = MediaStore.scanFile(cr, imageFile);
            assertThat(oldUri).isNotNull();

            Files.copy(imageFile, temporaryFile);
            assertThat(imageFile.delete()).isTrue();
            assertCanRenameFile(temporaryFile, imageFile);

            final Uri newUri = MediaStore.scanFile(cr, imageFile);
            assertThat(newUri).isNotNull();
            assertThat(newUri.getLastPathSegment()).isEqualTo(oldUri.getLastPathSegment());
            // oldUri of imageFile is still accessible after delete and rename.
            try (ParcelFileDescriptor pfd = cr.openFileDescriptor(oldUri, "rw")) {
                assertThat(pfd).isNotNull();
            }
        } finally {
            imageFile.delete();
            temporaryFile.delete();
        }
    }

    @Test
    public void testCantCreateOrRenameFileWithInvalidName() throws Exception {
        File invalidFile = new File(getDownloadDir(), "<>");
        File validFile = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        try {
            assertThrows(IOException.class, "Operation not permitted",
                    () -> {
                        invalidFile.createNewFile();
                    });

            assertThat(validFile.createNewFile()).isTrue();
            // We can't rename a file to a file name with invalid FAT characters.
            assertCantRenameFile(validFile, invalidFile);
        } finally {
            invalidFile.delete();
            validFile.delete();
        }
    }

    @Test
    public void testRenameWithSpecialChars() throws Exception {
        final String specialCharsSuffix = "'`~!@#$%^& ()_+-={}[];'.)";

        final File fileSpecialChars =
                new File(getDownloadDir(), NONMEDIA_FILE_NAME + specialCharsSuffix);

        final File dirSpecialChars =
                new File(getDownloadDir(), TEST_DIRECTORY_NAME + specialCharsSuffix);
        final File file1 = new File(dirSpecialChars, NONMEDIA_FILE_NAME);
        final File fileSpecialChars1 =
                new File(dirSpecialChars, NONMEDIA_FILE_NAME + specialCharsSuffix);

        final File renamedDir = new File(getDocumentsDir(), TEST_DIRECTORY_NAME);
        final File file2 = new File(renamedDir, NONMEDIA_FILE_NAME);
        final File fileSpecialChars2 =
                new File(renamedDir, NONMEDIA_FILE_NAME + specialCharsSuffix);
        try {
            assertTrue(fileSpecialChars.createNewFile());
            if (!dirSpecialChars.exists()) {
                assertTrue(dirSpecialChars.mkdir());
            }
            assertTrue(file1.createNewFile());

            // We can rename file name with special characters
            assertCanRenameFile(fileSpecialChars, fileSpecialChars1);

            // We can rename directory name with special characters
            assertCanRenameDirectory(dirSpecialChars, renamedDir,
                    new File[] {file1, fileSpecialChars1}, new File[] {file2, fileSpecialChars2});
        } finally {
            file1.delete();
            file2.delete();
            fileSpecialChars.delete();
            fileSpecialChars1.delete();
            fileSpecialChars2.delete();
            deleteRecursively(dirSpecialChars);
            deleteRecursively(renamedDir);
        }
    }

    /**
     * Test that IS_PENDING is set for files created via filepath
     */
    @Test
    public void testPendingFromFuse() throws Exception {
        final File pendingFile = new File(getDcimDir(), IMAGE_FILE_NAME);
        final File otherPendingFile = new File(getDcimDir(), VIDEO_FILE_NAME);
        try {
            assertTrue(pendingFile.createNewFile());
            // Newly created file should have IS_PENDING set
            try (Cursor c = queryFile(pendingFile, MediaStore.MediaColumns.IS_PENDING)) {
                assertTrue(c.moveToFirst());
                assertThat(c.getInt(0)).isEqualTo(1);
            }

            // If we query with MATCH_EXCLUDE, we should still see this pendingFile
            try (Cursor c = queryFileExcludingPending(pendingFile,
                    MediaStore.MediaColumns.IS_PENDING)) {
                assertThat(c.getCount()).isEqualTo(1);
                assertTrue(c.moveToFirst());
                assertThat(c.getInt(0)).isEqualTo(1);
            }

            assertNotNull(MediaStore.scanFile(getContentResolver(), pendingFile));

            // IS_PENDING should be unset after the scan
            try (Cursor c = queryFile(pendingFile, MediaStore.MediaColumns.IS_PENDING)) {
                assertTrue(c.moveToFirst());
                assertThat(c.getInt(0)).isEqualTo(0);
            }

            assertCreateFilesAs(APP_A_HAS_RES, otherPendingFile);
            // We can't query other apps pending file from FUSE with MATCH_EXCLUDE
            try (Cursor c = queryFileExcludingPending(otherPendingFile,
                    MediaStore.MediaColumns.IS_PENDING)) {
                assertThat(c.getCount()).isEqualTo(0);
            }
        } finally {
            pendingFile.delete();
            deleteFileAsNoThrow(APP_A_HAS_RES, otherPendingFile.getAbsolutePath());
        }
    }

    /**
     * Test that we don't allow renaming to top level directory
     */
    @Test
    public void testCantRenameToTopLevelDirectory() throws Exception {
        final File topLevelDir1 = new File(getExternalStorageDir(), TEST_DIRECTORY_NAME + "_1");
        final File topLevelDir2 = new File(getExternalStorageDir(), TEST_DIRECTORY_NAME + "_2");
        final File nonTopLevelDir = new File(getDcimDir(), TEST_DIRECTORY_NAME);
        try {
            createDirectoryAsLegacyApp(topLevelDir1);
            assertTrue(topLevelDir1.exists());

            // We can't rename a top level directory to a top level directory
            assertCantRenameDirectory(topLevelDir1, topLevelDir2, null);

            // However, we can rename a top level directory to non-top level directory.
            assertCanRenameDirectory(topLevelDir1, nonTopLevelDir, null, null);

            // We can't rename a non-top level directory to a top level directory.
            assertCantRenameDirectory(nonTopLevelDir, topLevelDir2, null);
        } finally {
            deleteRecursivelyAsLegacyApp(topLevelDir1);
            deleteRecursivelyAsLegacyApp(topLevelDir2);
            deleteRecursively(nonTopLevelDir);
        }
    }

    @Test
    public void testCanCreateDefaultDirectory() throws Exception {
        final File podcastsDir = getPodcastsDir();
        try {
            if (podcastsDir.exists()) {
                deleteRecursivelyAsLegacyApp(podcastsDir);
            }
            assertThat(podcastsDir.mkdir()).isTrue();
        } finally {
            createDirectoryAsLegacyApp(podcastsDir);
        }
    }

    /**
     * b/168830497: Test that app can write to file in DCIM/Camera even with .nomedia presence
     */
    @Test
    public void testCanWriteToDCIMCameraWithNomedia() throws Exception {
        final File cameraDir = new File(getDcimDir(), "Camera");
        final File nomediaFile = new File(cameraDir, ".nomedia");
        Uri targetUri = null;

        try {
            // Recreate required file and directory
            if (cameraDir.exists()) {
                // This is a work around to address a known inode cache inconsistency issue
                // that occurs when test runs for the second time.
                deleteRecursivelyAsLegacyApp(cameraDir);
            }

            createDirectoryAsLegacyApp(cameraDir);
            assertTrue(cameraDir.exists());

            createFileAsLegacyApp(nomediaFile);
            assertTrue(nomediaFile.exists());

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera");
            targetUri = getContentResolver().insert(getImageContentUri(), values, Bundle.EMPTY);
            assertNotNull(targetUri);

            try (ParcelFileDescriptor pfd =
                         getContentResolver().openFileDescriptor(targetUri, "w")) {
                assertThat(pfd).isNotNull();
                Os.write(pfd.getFileDescriptor(), ByteBuffer.wrap(BYTES_DATA1));
            }

            assertFileContent(new File(getFilePathFromUri(targetUri)), BYTES_DATA1);
        } finally {
            deleteWithMediaProviderNoThrow(targetUri);
            deleteAsLegacyApp(nomediaFile);
            deleteRecursivelyAsLegacyApp(cameraDir);
        }
    }

    /**
     * b/182479650: Test that Screenshots directory is not hidden because of .nomedia presence
     */
    @Test
    public void testNoMediaDoesntHideSpecialDirectories() throws Exception {
        for (File directory : new File [] {
                getDcimDir(),
                getDownloadDir(),
                new File(getDcimDir(), "Camera"),
                new File(getPicturesDir(), Environment.DIRECTORY_SCREENSHOTS),
                new File(getMoviesDir(), Environment.DIRECTORY_SCREENSHOTS),
                new File(getExternalStorageDir(), Environment.DIRECTORY_SCREENSHOTS)
        }) {
            assertNoMediaDoesntHideSpecialDirectories(directory);
        }
    }

    private void assertNoMediaDoesntHideSpecialDirectories(File directory) throws Exception {
        final File nomediaFile = new File(directory, ".nomedia");
        final File videoFile = new File(directory, VIDEO_FILE_NAME);
        Log.d(TAG, "Directory " + directory);

        try {
            // Recreate required file and directory
            if (!directory.exists()) {
                Log.d(TAG, "mkdir directory " + directory);
                createDirectoryAsLegacyApp(directory);
            }
            assertWithMessage("Exists " + directory).that(directory.exists()).isTrue();

            Log.d(TAG, "CreateFileAs " + nomediaFile);
            createFileAsLegacyApp(nomediaFile);
            assertWithMessage("Exists " + nomediaFile).that(nomediaFile.exists()).isTrue();

            createFileAsLegacyApp(videoFile);
            assertWithMessage("Exists " + videoFile).that(videoFile.exists()).isTrue();
            final Uri targetUri = MediaStore.scanFile(getContentResolver(), videoFile);
            assertWithMessage("Scan result for " + videoFile).that(targetUri)
                    .isNotNull();

            assertWithMessage("Uri path segment for " + targetUri)
                    .that(targetUri.getPathSegments()).contains("video");

            // Verify that the imageFile is not hidden because of .nomedia presence
            assertWithMessage("Query as other app ")
                    .that(canQueryOnUri(APP_A_HAS_RES, targetUri)).isTrue();
        } finally {
            deleteAsLegacyApp(videoFile);
            deleteAsLegacyApp(nomediaFile);
            deleteRecursivelyAsLegacyApp(directory);
        }
    }

    /**
     * Test that readdir lists unsupported file types in default directories.
     */
    @Test
    public void testListUnsupportedFileType() throws Exception {
        final File pdfFile = new File(getDcimDir(), NONMEDIA_FILE_NAME);
        final File videoFile = new File(getMusicDir(), VIDEO_FILE_NAME);
        try {
            // TEST_APP_A with storage permission should not see pdf file in DCIM
            createFileAsLegacyApp(pdfFile);
            assertThat(pdfFile.exists()).isTrue();
            assertThat(MediaStore.scanFile(getContentResolver(), pdfFile)).isNotNull();

            assertThat(listAs(APP_A_HAS_RES, getDcimDir().getPath()))
                    .doesNotContain(NONMEDIA_FILE_NAME);

            createFileAsLegacyApp(videoFile);
            // We don't insert files to db for files created by shell.
            assertThat(MediaStore.scanFile(getContentResolver(), videoFile)).isNotNull();
            // TEST_APP_A with storage permission should see video file in Music directory.
            assertThat(listAs(APP_A_HAS_RES, getMusicDir().getPath())).contains(VIDEO_FILE_NAME);
        } finally {
            deleteAsLegacyApp(pdfFile);
            deleteAsLegacyApp(videoFile);
            MediaStore.scanFile(getContentResolver(), pdfFile);
            MediaStore.scanFile(getContentResolver(), videoFile);
        }
    }

    /**
     * Test that normal apps cannot access Android/data and Android/obb dirs of other apps
     */
    @Test
    public void testCantAccessOtherAppsExternalDirs() throws Exception {
        File[] obbDirs = getContext().getObbDirs();
        File[] dataDirs = getContext().getExternalFilesDirs(null);
        for (File obbDir : obbDirs) {
            final File otherAppExternalObbDir = new File(obbDir.getPath().replace(
                    THIS_PACKAGE_NAME, APP_B_NO_PERMS.getPackageName()));
            final File file = new File(otherAppExternalObbDir, NONMEDIA_FILE_NAME);
            try {
                assertThat(createFileAs(APP_B_NO_PERMS, file.getPath())).isTrue();
                assertCannotReadOrWrite(file);
            } finally {
                deleteFileAsNoThrow(APP_B_NO_PERMS, file.getAbsolutePath());
            }
        }
        for (File dataDir : dataDirs) {
            final File otherAppExternalDataDir = new File(dataDir.getPath().replace(
                    THIS_PACKAGE_NAME, APP_B_NO_PERMS.getPackageName()));
            final File file = new File(otherAppExternalDataDir, NONMEDIA_FILE_NAME);
            try {
                assertThat(createFileAs(APP_B_NO_PERMS, file.getPath())).isTrue();
                assertCannotReadOrWrite(file);
            } finally {
                deleteFileAsNoThrow(APP_B_NO_PERMS, file.getAbsolutePath());
            }
        }
    }

    /**
     * Test that apps can't set attributes on another app's files.
     */
    @Test
    public void testCantSetAttrOtherAppsFile() throws Exception {
        // This path's permission is checked in MediaProvider (directory/external media dir)
        final File externalMediaPath = new File(getExternalMediaDir(), VIDEO_FILE_NAME);

        try {
            // Create the files
            if (!externalMediaPath.exists()) {
                assertThat(externalMediaPath.createNewFile()).isTrue();
            }

            // APP A should not be able to setattr to other app's files.
            assertWithMessage(
                    "setattr on directory/external media path [%s]", externalMediaPath.getPath())
                    .that(setAttrAs(APP_A_HAS_RES, externalMediaPath.getPath()))
                    .isFalse();
        } finally {
            externalMediaPath.delete();
        }
    }

    /**
     * b/171768780: Test that scan doesn't skip scanning renamed hidden file.
     */
    @Test
    public void testScanUpdatesMetadataForRenamedHiddenFile() throws Exception {
        final File hiddenFile = new File(getPicturesDir(), ".hidden_" + IMAGE_FILE_NAME);
        final File jpgFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        try {
            // Copy the image content to hidden file
            try (InputStream in =
                         getContext().getResources().openRawResource(R.raw.img_with_metadata);
                 FileOutputStream out = new FileOutputStream(hiddenFile)) {
                FileUtils.copy(in, out);
                out.getFD().sync();
            }
            Uri scanUri = MediaStore.scanFile(getContentResolver(), hiddenFile);
            assertNotNull(scanUri);

            // Rename hidden file to non-hidden
            assertCanRenameFile(hiddenFile, jpgFile);

            try (Cursor c = queryFile(jpgFile, MediaStore.MediaColumns.DATE_TAKEN)) {
                assertTrue(c.moveToFirst());
                // The file is not scanned yet, hence the metadata is not updated yet.
                assertThat(c.getString(0)).isNull();
            }

            // Scan the file to update the metadata for renamed hidden file.
            scanUri = MediaStore.scanFile(getContentResolver(), jpgFile);
            assertNotNull(scanUri);

            // Scan should be able to update metadata even if File.lastModifiedTime hasn't changed.
            try (Cursor c = queryFile(jpgFile, MediaStore.MediaColumns.DATE_TAKEN)) {
                assertTrue(c.moveToFirst());
                assertThat(c.getString(0)).isNotNull();
            }
        } finally {
            hiddenFile.delete();
            jpgFile.delete();
        }
    }

    /**
     * Tests that System Gallery apps cannot insert files in other app's private directories.
     */
    @Test
    public void testCantInsertFilesInOtherAppPrivateDir_hasSystemGallery() throws Exception {
        int uid = Process.myUid();
        try {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ALLOWED, SYSTEM_GALERY_APPOPS);
            assertCantInsertToOtherPrivateAppDirectories(IMAGE_FILE_NAME,
                    /* throwsExceptionForDataValue */ false, APP_B_NO_PERMS, THIS_PACKAGE_NAME);
        } finally {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ERRORED, SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * Tests that System Gallery apps cannot update files in other app's private directories.
     */
    @Test
    public void testCantUpdateFilesInOtherAppPrivateDir_hasSystemGallery() throws Exception {
        int uid = Process.myUid();
        try {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ALLOWED, SYSTEM_GALERY_APPOPS);
            assertCantUpdateToOtherPrivateAppDirectories(IMAGE_FILE_NAME,
                    /* throwsExceptionForDataValue */ false, APP_B_NO_PERMS, THIS_PACKAGE_NAME);
        } finally {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ERRORED, SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * This test is for operations to the calling app's own private packages.
     */
    @Test
    public void testInsertFromExternalDirsViaRelativePath() throws Exception {
        verifyInsertFromExternalMediaDirViaRelativePath_allowed();
        verifyInsertFromExternalPrivateDirViaRelativePath_denied();
    }

    /**
     * This test is for operations to the calling app's own private packages.
     */
    @Test
    public void testUpdateToExternalDirsViaRelativePath() throws Exception {
        verifyUpdateToExternalMediaDirViaRelativePath_allowed();
        verifyUpdateToExternalPrivateDirsViaRelativePath_denied();
    }

    /**
     * This test is for operations to the calling app's own private packages.
     */
    @Test
    public void testInsertFromExternalDirsViaRelativePathAsSystemGallery() throws Exception {
        int uid = Process.myUid();
        try {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ALLOWED, SYSTEM_GALERY_APPOPS);
            verifyInsertFromExternalMediaDirViaRelativePath_allowed();
            verifyInsertFromExternalPrivateDirViaRelativePath_denied();
        } finally {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ERRORED, SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * This test is for operations to the calling app's own private packages.
     */
    @Test
    public void testUpdateToExternalDirsViaRelativePathAsSystemGallery() throws Exception {
        int uid = Process.myUid();
        try {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ALLOWED, SYSTEM_GALERY_APPOPS);
            verifyUpdateToExternalMediaDirViaRelativePath_allowed();
            verifyUpdateToExternalPrivateDirsViaRelativePath_denied();
        } finally {
            setAppOpsModeForUid(uid, AppOpsManager.MODE_ERRORED, SYSTEM_GALERY_APPOPS);
        }
    }

    @Test
    public void testDeferredScanHidesPartialDatabaseRows() throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        // Insert a pending row
        final Uri targetUri = getContentResolver().insert(getImageContentUri(), values, null);
        try (InputStream in =
                     getContext().getResources().openRawResource(R.raw.img_with_metadata)) {
            try (ParcelFileDescriptor pfd =
                         getContentResolver().openFileDescriptor(targetUri, "w")) {
                // Write image content to the file
                FileUtils.copy(in, new ParcelFileDescriptor.AutoCloseOutputStream(pfd));
            }
        }

        // Verify that metadata is not updated yet.
        try (Cursor c = getContentResolver().query(targetUri, new String[] {
                MediaStore.Images.ImageColumns.DATE_TAKEN}, null, null)) {
            assertThat(c.moveToFirst()).isTrue();
            assertThat(c.getString(0)).isNull();
        }
        // Get file path to use in the next query().
        final String imageFilePath = getFilePathFromUri(targetUri);

        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        Bundle extras = new Bundle();
        extras.putBoolean(MediaStore.QUERY_ARG_DEFER_SCAN, true);
        // Publish the file, but, defer the scan on update().
        assertThat(getContentResolver().update(targetUri, values, extras)).isEqualTo(1);

        // The update() above can return before scanning is complete. Verify that either we don't
        // see the file in published files or if the file appears in the collection, it means that
        // deferred scan is now complete, hence verify metadata is intact.
        try (Cursor c = getContentResolver().query(getImageContentUri(),
                new String[] {MediaStore.Images.ImageColumns.DATE_TAKEN},
                MediaStore.Files.FileColumns.DATA + "=?", new String[] {imageFilePath}, null)) {
            if (c.getCount() == 1) {
                // If the file appears in media collection as published file, verify that metadata
                // is correct.
                assertThat(c.moveToFirst()).isTrue();
                assertThat(c.getString(0)).isNotNull();
                Log.i(TAG, "Verified that deferred scan on " + imageFilePath + " is complete"
                        + " and hence metadata is updated");

            } else {
                assertThat(c.getCount()).isEqualTo(0);
                Log.i(TAG, "Verified that " + imageFilePath + " was excluded in default query");
            }
        }
    }

    /**
     * Test that renaming a file to {@link Environment#DIRECTORY_RINGTONES} sets
     * {@link MediaStore.Audio.AudioColumns#IS_RINGTONE}
     */

    @Test
    public void testRenameToRingtoneDirectory() throws Exception {
        final File fileInDownloads = new File(getDownloadDir(), AUDIO_FILE_NAME);
        final File fileInRingtones = new File(getRingtonesDir(), AUDIO_FILE_NAME);

        try {
            assertThat(fileInDownloads.createNewFile()).isTrue();
            assertThat(MediaStore.scanFile(getContentResolver(), fileInDownloads)).isNotNull();

            assertCanRenameFile(fileInDownloads, fileInRingtones);

            try (Cursor c = queryAudioFile(fileInRingtones,
                    MediaStore.Audio.AudioColumns.IS_RINGTONE)) {
                assertTrue(c.moveToFirst());
                assertWithMessage("Expected " + MediaStore.Audio.AudioColumns.IS_RINGTONE
                        + " to be set after renaming to " + fileInRingtones)
                        .that(c.getInt(0)).isEqualTo(1);
            }

            assertCanRenameFile(fileInRingtones, fileInDownloads);

            try (Cursor c = queryAudioFile(fileInDownloads,
                    MediaStore.Audio.AudioColumns.IS_RINGTONE)) {
                assertTrue(c.moveToFirst());
                assertWithMessage("Expected " + MediaStore.Audio.AudioColumns.IS_RINGTONE
                        + " to be unset after renaming to " + fileInDownloads)
                        .that(c.getInt(0)).isEqualTo(0);
            }
        } finally {
            fileInDownloads.delete();
            fileInRingtones.delete();
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testTransformsDirFileOperations() throws Exception {
        final String path = Environment.getExternalStorageDirectory() + "/" + TRANSFORMS_DIR;
        final File file = new File(path);
        assertThat(file.exists()).isTrue();
        testTransformsDirCommon(file);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testTransformsSyntheticDirFileOperations() throws Exception {
        final String path =
                Environment.getExternalStorageDirectory() + "/" + TRANSFORMS_SYNTHETIC_DIR;
        final File file = new File(path);
        assertThat(file.exists()).isTrue();
        testTransformsDirCommon(file);
    }

    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testTransformsTranscodeDirFileOperations() throws Exception {
        final String path =
                Environment.getExternalStorageDirectory() + "/" + TRANSFORMS_TRANSCODE_DIR;
        final File file = new File(path);
        assertThat(file.exists()).isFalse();
        testTransformsDirCommon(file);
    }


    /**
     * Test mount modes for a platform signed app with ACCESS_MTP permission.
     */
    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testMTPAppWithPlatformSignatureMountMode() throws Exception {
        final String shellPackageName = "com.android.shell";
        final int uid = getContext().getPackageManager().getPackageUid(shellPackageName, 0);
        assertMountMode(shellPackageName, uid, StorageManager.MOUNT_MODE_EXTERNAL_ANDROID_WRITABLE);
    }

    /**
     * Test mount modes for ExternalStorageProvider and DownloadsProvider.
     */
    @Test
    @SdkSuppress(minSdkVersion = 31, codeName = "S")
    public void testExternalStorageProviderAndDownloadsProvider() throws Exception {
        // External Storage Provider and Downloads Provider are not supported on Wear OS
        if (FeatureUtil.isWatch()) {
            return;
        }
        assertWritableMountModeForProvider(DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY);
        assertWritableMountModeForProvider(DocumentsContract.DOWNLOADS_PROVIDER_AUTHORITY);
    }

    /**
     * Test that normal apps cannot access Android/data and Android/obb dirs of other apps
     */
    @Test
    public void testCantProbeOtherAppsExternalDirs() throws Exception {
        // Before fuse-bpf, apps could see other app's external storage
        boolean expectToSee = !isFuseBpfEnabled()
                && mVolumeName.equals(MediaStore.VOLUME_EXTERNAL);
        String message = expectToSee
                ? "Expected to see other app's private dirs"
                : "Expected not to see other app's private dirs";

        assertWithMessage(message)
                .that(fileExistsAs(APP_B_NO_PERMS, new File(getExternalFilesDir().getParent())))
                .isEqualTo(expectToSee);

        assertWithMessage(message)
                .that(fileExistsAs(APP_B_NO_PERMS, getExternalObbDir()))
                .isEqualTo(expectToSee);
    }

    private boolean isFuseBpfEnabled() throws Exception {
        return executeShellCommand("getprop ro.fuse.bpf.is_running").trim().equals("true");
    }

    private void assertWritableMountModeForProvider(String auth) {
        final ProviderInfo provider = getContext().getPackageManager()
                .resolveContentProvider(auth, 0);
        int uid = provider.applicationInfo.uid;
        final String packageName = provider.applicationInfo.packageName;

        assertMountMode(packageName, uid, StorageManager.MOUNT_MODE_EXTERNAL_ANDROID_WRITABLE);
    }

    private boolean canRenameFile(File file) {
        return file.renameTo(new File(file.getAbsolutePath() + "test"));
    }

    private void testTransformsDirCommon(File file) throws Exception {
        assertThat(file.delete()).isFalse();
        assertThat(canRenameFile(file)).isFalse();

        final File newFile = new File(file.getAbsolutePath(), "test");
        assertThat(newFile.mkdir()).isFalse();
        assertThrows(IOException.class, () -> newFile.createNewFile());
    }

    private void assertCanWriteAndRead(File file, byte[] data) throws Exception {
        // Assert we can write to images/videos
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        assertFileContent(file, data);
    }

    /**
     * Checks restrictions for opening pending and trashed files by different apps. Assumes that
     * given {@code testApp} is already installed and has READ_EXTERNAL_STORAGE permission. This
     * method doesn't uninstall given {@code testApp} at the end.
     */
    private void assertOpenPendingOrTrashed(Uri uri, boolean isImageOrVideo)
            throws Exception {
        final File pendingOrTrashedFile = new File(getFilePathFromUri(uri));

        // App can open its pending or trashed file for read or write
        assertTrue(canOpen(pendingOrTrashedFile, /*forWrite*/ false));
        assertTrue(canOpen(pendingOrTrashedFile, /*forWrite*/ true));

        // App with READ_EXTERNAL_STORAGE can't open other app's pending or trashed file for read or
        // write
        assertFalse(canOpenFileAs(APP_A_HAS_RES, pendingOrTrashedFile, /*forWrite*/ false));
        assertFalse(canOpenFileAs(APP_A_HAS_RES, pendingOrTrashedFile, /*forWrite*/ true));

        assertTrue(canOpenFileAs(APP_FM, pendingOrTrashedFile, /*forWrite*/ false));
        assertTrue(canOpenFileAs(APP_FM, pendingOrTrashedFile, /*forWrite*/ true));

        final int resAppUid =
                getContext().getPackageManager().getPackageUid(APP_A_HAS_RES.getPackageName(), 0);
        try {
            allowAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
            if (isImageOrVideo) {
                // System Gallery can open any pending or trashed image/video file for read or write
                assertTrue(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertTrue(canOpenFileAs(APP_A_HAS_RES, pendingOrTrashedFile, /*forWrite*/ false));
                assertTrue(canOpenFileAs(APP_A_HAS_RES, pendingOrTrashedFile, /*forWrite*/ true));
            } else {
                // System Gallery can't open other app's pending or trashed non-media file for read
                // or write
                assertFalse(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertFalse(canOpenFileAs(APP_A_HAS_RES, pendingOrTrashedFile, /*forWrite*/ false));
                assertFalse(canOpenFileAs(APP_A_HAS_RES, pendingOrTrashedFile, /*forWrite*/ true));
            }
        } finally {
            denyAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * Checks restrictions for listing pending and trashed files by different apps.
     */
    private void assertListPendingOrTrashed(Uri uri, File file, boolean isImageOrVideo)
            throws Exception {
        final String parentDirPath = file.getParent();
        assertTrue(new File(parentDirPath).isDirectory());

        final List<String> listedFileNames = Arrays.asList(new File(parentDirPath).list());
        assertThat(listedFileNames).doesNotContain(file);

        final File pendingOrTrashedFile = new File(getFilePathFromUri(uri));

        assertThat(listedFileNames).contains(pendingOrTrashedFile.getName());

        // App with READ_EXTERNAL_STORAGE can't see other app's pending or trashed file.
        assertThat(listAs(APP_A_HAS_RES, parentDirPath)).doesNotContain(
                pendingOrTrashedFile.getName());

        final int resAppUid =
                getContext().getPackageManager().getPackageUid(APP_A_HAS_RES.getPackageName(), 0);
        // File Manager can see any pending or trashed file.
        assertThat(listAs(APP_FM, parentDirPath)).contains(pendingOrTrashedFile.getName());


        try {
            allowAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
            if (isImageOrVideo) {
                // System Gallery can see any pending or trashed image/video file.
                assertTrue(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertThat(listAs(APP_A_HAS_RES, parentDirPath)).contains(
                        pendingOrTrashedFile.getName());
            } else {
                // System Gallery can't see other app's pending or trashed non media file.
                assertFalse(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertThat(listAs(APP_A_HAS_RES, parentDirPath))
                        .doesNotContain(pendingOrTrashedFile.getName());
            }
        } finally {
            denyAppOpsToUid(resAppUid, SYSTEM_GALERY_APPOPS);
        }
    }

    private Uri createPendingFile(File pendingFile) throws Exception {
        assertTrue(pendingFile.createNewFile());

        final ContentResolver cr = getContentResolver();
        final Uri trashedFileUri = MediaStore.scanFile(cr, pendingFile);
        assertNotNull(trashedFileUri);

        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        assertEquals(1, cr.update(trashedFileUri, values, Bundle.EMPTY));

        return trashedFileUri;
    }

    private Uri createTrashedFile(File trashedFile) throws Exception {
        assertTrue(trashedFile.createNewFile());

        final ContentResolver cr = getContentResolver();
        final Uri trashedFileUri = MediaStore.scanFile(cr, trashedFile);
        assertNotNull(trashedFileUri);

        trashFileAndAssert(trashedFileUri);
        return trashedFileUri;
    }

    /**
     * Gets file path corresponding to the db row pointed by {@code uri}. If {@code uri} points to
     * multiple db rows, file path is extracted from the first db row of the database query result.
     */
    private String getFilePathFromUri(Uri uri) {
        final String[] projection = new String[] {MediaStore.MediaColumns.DATA};
        try (Cursor c = getContentResolver().query(uri, projection, null, null)) {
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }

    private boolean isMediaTypeImageOrVideo(File file) {
        return queryImageFile(file).getCount() == 1 || queryVideoFile(file).getCount() == 1;
    }

    private static void assertIsMediaTypeImage(File file) {
        final Cursor c = queryImageFile(file);
        assertEquals(1, c.getCount());
    }

    private static void assertNotMediaTypeImage(File file) {
        final Cursor c = queryImageFile(file);
        assertEquals(0, c.getCount());
    }

    private static void assertCantQueryFile(File file) {
        assertThat(getFileUri(file)).isNull();
        // Confirm that file exists in the database.
        assertNotNull(MediaStore.scanFile(getContentResolver(), file));
    }

    private static void assertCreateFilesAs(TestApp testApp, File... files) throws Exception {
        for (File file : files) {
            assertFalse("File already exists: " + file, file.exists());
            assertTrue("Failed to create file " + file + " on behalf of "
                    + testApp.getPackageName(), createFileAs(testApp, file.getPath()));
        }
    }

    /**
     * Makes {@code testApp} create {@code files}. Publishes {@code files} by scanning the file.
     * Pending files from FUSE are not visible to other apps via MediaStore APIs. We have to publish
     * the file or make the file non-pending to make the file visible to other apps.
     * <p>
     * Note that this method can only be used for scannable files.
     */
    private static void assertCreatePublishedFilesAs(TestApp testApp, File... files)
            throws Exception {
        for (File file : files) {
            assertTrue("Failed to create published file " + file + " on behalf of "
                    + testApp.getPackageName(), createFileAs(testApp, file.getPath()));
            assertNotNull("Failed to scan " + file,
                    MediaStore.scanFile(getContentResolver(), file));
        }
    }


    private static void deleteFilesAs(TestApp testApp, File... files) throws Exception {
        for (File file : files) {
            deleteFileAs(testApp, file.getPath());
        }
    }
    private static void assertCanDeletePathsAs(TestApp testApp, String... filePaths)
            throws Exception {
        for (String path: filePaths) {
            assertTrue("Failed to delete file " + path + " on behalf of "
                    + testApp.getPackageName(), deleteFileAs(testApp, path));
        }
    }

    private static void assertCantDeletePathsAs(TestApp testApp, String... filePaths)
            throws Exception {
        for (String path: filePaths) {
            assertFalse("Deleting " + path + " on behalf of " + testApp.getPackageName()
                    + " was expected to fail", deleteFileAs(testApp, path));
        }
    }

    private void deleteFiles(File... files) {
        for (File file: files) {
            if (file == null) continue;
            file.delete();
        }
    }

    private void deletePaths(String... paths) {
        for (String path: paths) {
            if (path == null) continue;
            new File(path).delete();
        }
    }

    private static void assertCanDeletePaths(String... filePaths) {
        for (String filePath : filePaths) {
            assertTrue("Failed to delete " + filePath,
                    new File(filePath).delete());
        }
    }

    /**
     * For possible values of {@code mode}, look at {@link android.content.ContentProvider#openFile}
     */
    private static void assertCanQueryAndOpenFile(File file, String mode) throws IOException {
        // This call performs the query
        final Uri fileUri = getFileUri(file);
        // The query succeeds iff it didn't return null
        assertThat(fileUri).isNotNull();
        // Now we assert that we can open the file through ContentResolver
        try (ParcelFileDescriptor pfd =
                     getContentResolver().openFileDescriptor(fileUri, mode)) {
            assertThat(pfd).isNotNull();
        }
    }

    /**
     * Assert that the last read in: read - write - read using {@code readFd} and {@code writeFd}
     * see the last write. {@code readFd} and {@code writeFd} are fds pointing to the same
     * underlying file on disk but may be derived from different mount points and in that case
     * have separate VFS caches.
     */
    private void assertRWR(ParcelFileDescriptor readPfd, ParcelFileDescriptor writePfd)
            throws Exception {
        FileDescriptor readFd = readPfd.getFileDescriptor();
        FileDescriptor writeFd = writePfd.getFileDescriptor();

        byte[] readBuffer = new byte[10];
        byte[] writeBuffer = new byte[10];
        Arrays.fill(writeBuffer, (byte) 1);

        // Write so readFd has content to read from next
        Os.pwrite(readFd, readBuffer, 0, 10, 0);
        // Read so readBuffer is in readFd's mount VFS cache
        Os.pread(readFd, readBuffer, 0, 10, 0);

        // Assert that readBuffer is zeroes
        assertThat(readBuffer).isEqualTo(new byte[10]);

        // Write so writeFd and readFd should now see writeBuffer
        Os.pwrite(writeFd, writeBuffer, 0, 10, 0);

        // Read so the last write can be verified on readFd
        Os.pread(readFd, readBuffer, 0, 10, 0);

        // Assert that the last write is indeed visible via readFd
        assertThat(readBuffer).isEqualTo(writeBuffer);
        assertThat(readPfd.getStatSize()).isEqualTo(writePfd.getStatSize());
    }

    private void assertStartsWith(String actual, String prefix) throws Exception {
        String message = "String \"" + actual + "\" should start with \"" + prefix + "\"";

        assertWithMessage(message).that(actual).startsWith(prefix);
    }

    private void assertLowerFsFd(ParcelFileDescriptor pfd) throws Exception {
        String path = Os.readlink("/proc/self/fd/" + pfd.getFd());
        String prefix = "/storage";

        assertStartsWith(path, prefix);
    }

    private void assertUpperFsFd(ParcelFileDescriptor pfd) throws Exception {
        String path = Os.readlink("/proc/self/fd/" + pfd.getFd());
        String prefix = "/mnt/user";

        assertStartsWith(path, prefix);
    }

    private void assertLowerFsFdWithPassthrough(final String path, ParcelFileDescriptor pfd)
            throws Exception {
        final ContentResolver resolver = getTargetContext().getContentResolver();
        final Bundle res = resolver.call(MediaStore.AUTHORITY, "uses_fuse_passthrough", path, null);
        boolean passthroughEnabled = res.getBoolean("uses_fuse_passthrough_result");

        if (passthroughEnabled) {
            assertUpperFsFd(pfd);
        } else {
            assertLowerFsFd(pfd);
        }
    }

    private static void assertCanCreateFile(File file) throws IOException {
        // If the file somehow managed to survive a previous run, then the test app was uninstalled
        // and MediaProvider will remove our its ownership of the file, so it's not guaranteed that
        // we can create nor delete it.
        if (!file.exists()) {
            assertThat(file.createNewFile()).isTrue();
            assertThat(file.delete()).isTrue();
        } else {
            Log.w(TAG,
                    "Couldn't assertCanCreateFile(" + file + ") because file existed prior to "
                            + "running the test!");
        }
    }

    private static void assertCannotReadOrWrite(File file)
            throws Exception {
        // App data directories have different 'x' bits on upgrading vs new devices. Let's not
        // check 'exists', by passing checkExists=false. But assert this app cannot read or write
        // the other app's file.
        assertAccess(file, false /* value is moot */, false /* canRead */,
                false /* canWrite */, false /* checkExists */);
    }

    private static void assertAccess(File file, boolean exists, boolean canRead, boolean canWrite)
            throws Exception {
        assertAccess(file, exists, canRead, canWrite, true /* checkExists */);
    }

    private static void assertAccess(File file, boolean exists, boolean canRead, boolean canWrite,
            boolean checkExists) throws Exception {
        if (checkExists) {
            assertThat(file.exists()).isEqualTo(exists);
        }
        assertThat(file.canRead()).isEqualTo(canRead);
        assertThat(file.canWrite()).isEqualTo(canWrite);
        if (file.isDirectory()) {
            if (checkExists) {
                assertThat(file.canExecute()).isEqualTo(exists);
            }
        } else {
            assertThat(file.canExecute()).isFalse(); // Filesytem is mounted with MS_NOEXEC
        }

        // Test some combinations of mask.
        assertAccess(file, R_OK, canRead);
        assertAccess(file, W_OK, canWrite);
        assertAccess(file, R_OK | W_OK, canRead && canWrite);
        assertAccess(file, W_OK | F_OK, canWrite);

        if (checkExists) {
            assertAccess(file, F_OK, exists);
        }
    }

    private static void assertAccess(File file, int mask, boolean expected) throws Exception {
        if (expected) {
            assertThat(Os.access(file.getAbsolutePath(), mask)).isTrue();
        } else {
            assertThrows(ErrnoException.class, () -> {
                Os.access(file.getAbsolutePath(), mask);
            });
        }
    }

    /**
     * Creates a file at any location on storage (except external app data directory).
     * The owner of the file is not the caller app.
     */
    private void createFileAsLegacyApp(File file) throws Exception {
        // Use a legacy app to create this file, since it could be outside shared storage.
        Log.d(TAG, "Creating file " + file);
        assertThat(createFileAs(APP_D_LEGACY_HAS_RW, file.getAbsolutePath())).isTrue();
    }

    /**
     * Creates a file at any location on storage (except external app data directory).
     * The owner of the file is not the caller app.
     */
    private void createDirectoryAsLegacyApp(File file) throws Exception {
        // Use a legacy app to create this file, since it could be outside shared storage.
        Log.d(TAG, "Creating directory " + file);
        // Create a tmp file in the target directory, this would also create the required
        // directory, then delete the tmp file. It would leave only new directory.
        assertThat(createFileAs(APP_D_LEGACY_HAS_RW, file.getAbsolutePath() + "/tmp.txt")).isTrue();
        assertThat(deleteFileAs(APP_D_LEGACY_HAS_RW, file.getAbsolutePath() + "/tmp.txt")).isTrue();
    }

    /**
     * Deletes a file or directory at any location on storage (except external app data directory).
     */
    private void deleteAsLegacyApp(File file) throws Exception {
        // Use a legacy app to delete this file, since it could be outside shared storage.
        Log.d(TAG, "Deleting file " + file);
        deleteFileAs(APP_D_LEGACY_HAS_RW, file.getAbsolutePath());
    }

    /**
     * Deletes the given file/directory recursively. If the file is a directory, then deletes all
     * of its children (files or directories) recursively.
     */
    private void deleteRecursivelyAsLegacyApp(File dir) throws Exception {
        // Use a legacy app to delete this directory, since it could be outside shared storage.
        Log.d(TAG, "Deleting directory " + dir);
        deleteRecursivelyAs(APP_D_LEGACY_HAS_RW, dir.getAbsolutePath());
    }
}
