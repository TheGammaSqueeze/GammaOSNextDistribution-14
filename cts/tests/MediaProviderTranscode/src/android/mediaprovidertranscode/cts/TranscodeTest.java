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

package android.mediaprovidertranscode.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static android.mediaprovidertranscode.cts.TranscodeTestUtils.assertFileContent;
import static android.mediaprovidertranscode.cts.TranscodeTestUtils.assertTranscode;
import static android.mediaprovidertranscode.cts.TranscodeTestUtils.executeShellCommand;
import static android.mediaprovidertranscode.cts.TranscodeTestUtils.installAppWithStoragePermissions;
import static android.mediaprovidertranscode.cts.TranscodeTestUtils.isAppIoBlocked;
import static android.mediaprovidertranscode.cts.TranscodeTestUtils.open;
import static android.mediaprovidertranscode.cts.TranscodeTestUtils.openFileAs;
import static android.mediaprovidertranscode.cts.TranscodeTestUtils.uninstallApp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.ApplicationMediaCapabilities;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.UserHandle;
import android.provider.MediaStore;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.TestApp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TranscodeTest {
    private static final String TAG = "TranscodeTest";
    private static final File EXTERNAL_STORAGE_DIRECTORY
            = Environment.getExternalStorageDirectory();
    private static final File DIR_CAMERA
            = new File(EXTERNAL_STORAGE_DIRECTORY, Environment.DIRECTORY_DCIM + "/Camera");
    // TODO(b/169546642): Test other directories like /sdcard and /sdcard/foo
    // These are the only transcode unsupported directories we can stage files in given our
    // test app permissions
    private static final File[] DIRS_NO_TRANSCODE = {
        new File(EXTERNAL_STORAGE_DIRECTORY, Environment.DIRECTORY_PICTURES),
        new File(EXTERNAL_STORAGE_DIRECTORY, Environment.DIRECTORY_MOVIES),
        new File(EXTERNAL_STORAGE_DIRECTORY, Environment.DIRECTORY_DOWNLOADS),
        new File(EXTERNAL_STORAGE_DIRECTORY, Environment.DIRECTORY_DCIM),
        new File(EXTERNAL_STORAGE_DIRECTORY, Environment.DIRECTORY_DOCUMENTS),
    };

    static final String NONCE = String.valueOf(System.nanoTime());
    private static final String HEVC_FILE_NAME = "TranscodeTestHEVC_" + NONCE + ".mp4";
    private static final String SMALL_HEVC_FILE_NAME = "TranscodeTestHevcSmall_" + NONCE + ".mp4";
    private static final String LEGACY_FILE_NAME = "TranscodeTestLegacy_" + NONCE + ".mp4";

    private static final TestApp TEST_APP_HEVC = new TestApp("TestAppHevc",
            "android.mediaprovidertranscode.cts.testapp", 1, false,
            "CtsTranscodeTestAppSupportsHevc.apk");

    private static final TestApp TEST_APP_SLOW_MOTION = new TestApp("TestAppSlowMotion",
            "android.mediaprovidertranscode.cts.testapp", 1, false,
            "CtsTranscodeTestAppSupportsSlowMotion.apk");

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Media transcoding disabled",
                SystemProperties.getBoolean("sys.fuse.transcode_enabled", false));
        // TODO(b/182846329): GSI doesn't support transcoding yet
        Assume.assumeFalse(
                "Using GSI", SystemProperties.get("ro.build.product").contains("generic"));

        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
        Assume.assumeFalse("FEATURE_LEANBACK", pm.hasSystemFeature(pm.FEATURE_LEANBACK));
        Assume.assumeFalse("FEATURE_WATCH", pm.hasSystemFeature(pm.FEATURE_WATCH));
        Assume.assumeFalse("FEATURE_AUTOMOTIVE", pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE));
        Assume.assumeTrue("AVC hw encoder not supported",
                TranscodeTestUtils.isAVCHWEncoderSupported());

        TranscodeTestUtils.pollForExternalStorageState();
        TranscodeTestUtils.grantPermission(getContext().getPackageName(),
                Manifest.permission.READ_EXTERNAL_STORAGE);
        TranscodeTestUtils.pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, true);
    }

    @After
    public void tearDown() throws Exception {
        TranscodeTestUtils.disableTranscodingForAllPackages();
    }

    /**
     * Tests that we return FD of transcoded file for legacy apps
     * @throws Exception
     */
    @Test
    public void testTranscoded_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            ParcelFileDescriptor pfdTranscoded = open(modernFile, false);

            assertFileContent(modernFile, modernFile, pfdOriginal, pfdTranscoded, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that we don't transcode files outside DCIM/Camera
     * @throws Exception
     */
    @Test
    public void testNoTranscodeOutsideCamera_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        List<File> noTranscodeFiles = new ArrayList<>();
        for (File file : DIRS_NO_TRANSCODE) {
            noTranscodeFiles.add(new File(file, HEVC_FILE_NAME));
        }
        noTranscodeFiles.add(new File(getContext().getExternalFilesDir(null), HEVC_FILE_NAME));

        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            for (File file : noTranscodeFiles) {
                TranscodeTestUtils.stageHEVCVideoFile(file);
            }
            ParcelFileDescriptor pfdOriginal1 = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            for (File file : noTranscodeFiles) {
                pfdOriginal1.seekTo(0);
                ParcelFileDescriptor pfdOriginal2 = open(file, false);
                assertFileContent(modernFile, file, pfdOriginal1, pfdOriginal2, true);
            }
        } finally {
            modernFile.delete();
            for (File file : noTranscodeFiles) {
                file.delete();
            }
        }
    }

    /**
     * Tests that same transcoded file is used for multiple open() from same app
     * @throws Exception
     */
    @Test
    public void testSameTranscoded_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            ParcelFileDescriptor pfdTranscoded1 = open(modernFile, false);
            ParcelFileDescriptor pfdTranscoded2 = open(modernFile, false);

            assertFileContent(modernFile, modernFile, pfdTranscoded1, pfdTranscoded2, true);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that we return FD of transcoded file for legacy apps
     * @throws Exception
     */
    @Test
    public void testTranscoded_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            ParcelFileDescriptor pfdTranscoded = open(uri, false, null /* bundle */);

            assertFileContent(modernFile, modernFile, pfdOriginal, pfdTranscoded, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that we don't transcode files outside DCIM/Camera
     * @throws Exception
     */
    @Test
    public void testNoTranscodeOutsideCamera_ConentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        List<File> noTranscodeFiles = new ArrayList<>();
        for (File file : DIRS_NO_TRANSCODE) {
            noTranscodeFiles.add(new File(file, HEVC_FILE_NAME));
        }

        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            ArrayList<Uri> noTranscodeUris = new ArrayList<>();
            for (File file : noTranscodeFiles) {
                noTranscodeUris.add(TranscodeTestUtils.stageHEVCVideoFile(file));
            }

            ParcelFileDescriptor pfdOriginal1 = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            for (int i = 0; i < noTranscodeUris.size(); i++) {
                pfdOriginal1.seekTo(0);
                ParcelFileDescriptor pfdOriginal2 =
                        open(noTranscodeUris.get(i), false, null /* bundle */);
                assertFileContent(modernFile, noTranscodeFiles.get(1), pfdOriginal1, pfdOriginal2,
                        true);
            }
        } finally {
            modernFile.delete();
            for (File file : noTranscodeFiles) {
                file.delete();
            }
        }
    }

    /**
     * Tests that same transcoded file is used for multiple open() from same app
     * @throws Exception
     */
    @Test
    public void testSameTranscodedFile_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            ParcelFileDescriptor pfdTranscoded1 = open(uri, false, null /* bundle */);
            ParcelFileDescriptor pfdTranscoded2 = open(uri, false, null /* bundle */);

            assertFileContent(modernFile, modernFile, pfdTranscoded1, pfdTranscoded2, true);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that deletes are visible across legacy and modern apps
     * @throws Exception
     */
    @Test
    public void testDeleteTranscodedFile_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTrue(modernFile.delete());
            assertFalse(modernFile.exists());

            TranscodeTestUtils.disableTranscodingForAllPackages();

            assertFalse(modernFile.exists());
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that renames are visible across legacy and modern apps
     * @throws Exception
     */
    @Test
    public void testRenameTranscodedFile_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        File destFile = new File(DIR_CAMERA, "renamed_" + HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTrue(modernFile.renameTo(destFile));
            assertTrue(destFile.exists());
            assertFalse(modernFile.exists());

            TranscodeTestUtils.disableTranscodingForAllPackages();

            assertTrue(destFile.exists());
            assertFalse(modernFile.exists());
        } finally {
            modernFile.delete();
            destFile.delete();
        }
    }

    /**
     * Tests that transcode doesn't start until read(2)
     * @throws Exception
     */
    @Test
    public void testLazyTranscodedFile_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            assertTranscode(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTranscode(modernFile, true);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that transcode cache is reused after file path transcode
     * @throws Exception
     */
    @Test
    public void testTranscodedCacheReuse_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTranscode(modernFile, true);
            assertTranscode(modernFile, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that transcode cache is reused after ContentResolver transcode
     * @throws Exception
     */
    @Test
    public void testTranscodedCacheReuse_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTranscode(uri, true);
            assertTranscode(uri, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that transcode cache is reused after ContentResolver transcode
     * and file path opens
     * @throws Exception
     */
    @Test
    public void testTranscodedCacheReuse_ContentResolverFilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTranscode(uri, true);
            assertTranscode(modernFile, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that transcode cache is reused after file path transcode
     * and ContentResolver opens
     * @throws Exception
     */
    @Test
    public void testTranscodedCacheReuse_FilePathContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTranscode(modernFile, true);
            assertTranscode(uri, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that transcode cache is reused after rename
     * @throws Exception
     */
    @Test
    public void testTranscodedCacheReuseAfterRename_FilePath() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        File destFile = new File(DIR_CAMERA, "renamed_" + HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTranscode(modernFile, true);

            assertTrue(modernFile.renameTo(destFile));

            assertTranscode(destFile, false);
        } finally {
            modernFile.delete();
            destFile.delete();
        }
    }

    @Test
    public void testExtraAcceptOriginalFormatTrue_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            Bundle bundle = new Bundle();
            bundle.putBoolean(MediaStore.EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT, true);
            ParcelFileDescriptor pfdOriginal2 = open(uri, false, bundle);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, true);
        } finally {
            modernFile.delete();
        }
    }

    @Test
    public void testExtraAcceptOriginalFormatFalse_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            Bundle bundle = new Bundle();
            bundle.putBoolean(MediaStore.EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT, false);
            ParcelFileDescriptor pfdTranscoded = open(uri, false, bundle);

            assertFileContent(modernFile, modernFile, pfdOriginal, pfdTranscoded, false);
        } finally {
            modernFile.delete();
        }
    }

    @Test
    public void testExtraMediaCapabilitiesHevcSupportedTrue_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            Bundle bundle = new Bundle();
            ApplicationMediaCapabilities capabilities =
                    new ApplicationMediaCapabilities.Builder()
                    .addSupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC).build();
            bundle.putParcelable(MediaStore.EXTRA_MEDIA_CAPABILITIES, capabilities);
            ParcelFileDescriptor pfdOriginal2 = open(uri, false, bundle);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, true);
        } finally {
            modernFile.delete();
        }
    }

    @Test
    public void testExtraMediaCapabilitiesHevcUnsupportedFalse_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            Bundle bundle = new Bundle();
            ApplicationMediaCapabilities capabilities =
                    new ApplicationMediaCapabilities.Builder()
                            .addUnsupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC).build();
            bundle.putParcelable(MediaStore.EXTRA_MEDIA_CAPABILITIES, capabilities);
            ParcelFileDescriptor pfdOriginal2 = open(uri, false, bundle);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, false);
        } finally {
            modernFile.delete();
        }
    }

    @Test
    public void testExtraMediaCapabilitiesHevcUnspecifiedFalse_ContentResolver() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            Bundle bundle = new Bundle();
            ApplicationMediaCapabilities capabilities =
                    new ApplicationMediaCapabilities.Builder().build();
            bundle.putParcelable(MediaStore.EXTRA_MEDIA_CAPABILITIES, capabilities);
            ParcelFileDescriptor pfdTranscoded = open(uri, false, bundle);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdTranscoded, false);
        } finally {
            modernFile.delete();
        }
    }

    @Test
    public void testExtraAcceptOriginalTrueAndMediaCapabilitiesHevcFalse_ContentResolver()
            throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(uri, false, null /* bundle */);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            Bundle bundle = new Bundle();
            ApplicationMediaCapabilities capabilities =
                    new ApplicationMediaCapabilities.Builder().build();
            bundle.putParcelable(MediaStore.EXTRA_MEDIA_CAPABILITIES, capabilities);
            bundle.putBoolean(MediaStore.EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT, true);
            ParcelFileDescriptor pfdOriginal2 = open(uri, false, bundle);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, true);
        } finally {
            modernFile.delete();
        }
    }

    @Test
    public void testMediaCapabilitiesManifestHevc()
            throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        ParcelFileDescriptor pfdOriginal2 = null;
        try {
            installAppWithStoragePermissions(TEST_APP_HEVC);

            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(TEST_APP_HEVC.getPackageName());

            pfdOriginal2 = openFileAs(TEST_APP_HEVC, modernFile);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, true);
        } finally {
            // Explicitly close PFD otherwise instrumention might crash when test_app is uninstalled
            if (pfdOriginal2 != null) {
                pfdOriginal2.close();
            }
            modernFile.delete();
            uninstallApp(TEST_APP_HEVC);
        }
    }

    @Test
    public void testMediaCapabilitiesManifestSlowMotion()
            throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        ParcelFileDescriptor pfdOriginal2 = null;
        try {
            installAppWithStoragePermissions(TEST_APP_SLOW_MOTION);

            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(TEST_APP_SLOW_MOTION.getPackageName());

            pfdOriginal2 = openFileAs(TEST_APP_SLOW_MOTION, modernFile);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, false);
        } finally {
            // Explicitly close PFD otherwise instrumention might crash when test_app is uninstalled
            if (pfdOriginal2 != null) {
                pfdOriginal2.close();
            }
            modernFile.delete();
            uninstallApp(TEST_APP_HEVC);
        }
    }

    @Test
    public void testAppCompatNoTranscodeHevc() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        String packageName = TEST_APP_SLOW_MOTION.getPackageName();
        ParcelFileDescriptor pfdOriginal2 = null;
        try {
            installAppWithStoragePermissions(TEST_APP_SLOW_MOTION);

            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(packageName);
            // App compat takes precedence
            TranscodeTestUtils.forceEnableAppCompatHevc(packageName);

            Thread.sleep(2000);

            pfdOriginal2 = openFileAs(TEST_APP_SLOW_MOTION, modernFile);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, true);
        } finally {
            // Explicitly close PFD otherwise instrumention might crash when test_app is uninstalled
            if (pfdOriginal2 != null) {
                pfdOriginal2.close();
            }
            modernFile.delete();
            TranscodeTestUtils.resetAppCompat(packageName);
            uninstallApp(TEST_APP_HEVC);
        }
    }

    @Test
    public void testAppCompatTranscodeHevc() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        String packageName = TEST_APP_SLOW_MOTION.getPackageName();
        ParcelFileDescriptor pfdOriginal2 = null;
        try {
            installAppWithStoragePermissions(TEST_APP_SLOW_MOTION);

            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal1 = open(modernFile, false);

            // Transcoding is disabled but app compat enables it (disables hevc support)
            TranscodeTestUtils.forceDisableAppCompatHevc(packageName);

            pfdOriginal2 = openFileAs(TEST_APP_SLOW_MOTION, modernFile);

            assertFileContent(modernFile, modernFile, pfdOriginal1, pfdOriginal2, false);
        } finally {
            // Explicitly close PFD otherwise instrumention might crash when test_app is uninstalled
            if (pfdOriginal2 != null) {
                pfdOriginal2.close();
            }
            modernFile.delete();
            TranscodeTestUtils.resetAppCompat(packageName);
            uninstallApp(TEST_APP_HEVC);
        }
    }

    /**
     * Tests that we never initiate tanscoding for legacy formats.
     * This test compares the bytes read before and after enabling transcoding for the test app.
     * @throws Exception
     */
    @Test
    public void testTranscodedNotInitiatedForLegacy_UsingBytesRead() throws Exception {
        File legacyFile = new File(DIR_CAMERA, LEGACY_FILE_NAME);
        try {
            TranscodeTestUtils.stageLegacyVideoFile(legacyFile);

            ParcelFileDescriptor pfdOriginal = open(legacyFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            ParcelFileDescriptor pfdTranscoded = open(legacyFile, false);

            assertFileContent(legacyFile, legacyFile, pfdOriginal, pfdTranscoded, true);
        } finally {
            legacyFile.delete();
        }
    }

    /**
     * Tests that we never initiate tanscoding for legacy formats.
     * This test asserts using the time it took to read after enabling transcoding for the test app.
     * The reason for keeping this check separately (than
     * {@link TranscodeTest#testTranscodedNotInitiatedForLegacy_UsingTiming()}) is that this
     * provides a higher level of suret that the timing wasn't favorable because of any caching
     * after open().
     * @throws Exception
     */
    @Test
    public void testTranscodedNotInitiatedForLegacy_UsingTiming() throws Exception {
        File legacyFile = new File(DIR_CAMERA, LEGACY_FILE_NAME);
        try {
            TranscodeTestUtils.stageLegacyVideoFile(legacyFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            assertTranscode(legacyFile, false);
        } finally {
            legacyFile.delete();
        }
    }

    /**
     * Tests that we don't timeout while transcoding small HEVC videos.
     * For instance, due to some calculation errors we might incorrectly make timeout to be 0.
     * We test this by making sure that a small HEVC video (< 1 sec long and < 1Mb size) gets
     * transcoded.
     * @throws Exception
     */
    @Test
    public void testNoTranscodeTimeoutForSmallHevcVideos() throws Exception {
        File modernFile = new File(DIR_CAMERA, SMALL_HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageSmallHevcVideoFile(modernFile);
            ParcelFileDescriptor pfdOriginal = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            ParcelFileDescriptor pfdTranscoded = open(modernFile, false);

            assertFileContent(modernFile, modernFile, pfdOriginal, pfdTranscoded, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that we transcode an HEVC file when a modern app passes the mediaCapabilitiesUid of a
     * legacy app that cannot handle an HEVC file.
     */
    @Test
    public void testOriginalCallingUid_modernAppPassLegacyAppUid()
            throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        ParcelFileDescriptor pfdModernApp = null;
        ParcelFileDescriptor pfdModernAppPassingLegacyUid = null;
        try {
            installAppWithStoragePermissions(TEST_APP_SLOW_MOTION);
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            // pfdModernApp is for original content (without transcoding) since this is a modern
            // app.
            pfdModernApp = open(modernFile, false);

            // pfdModernAppPassingLegacyUid is for transcoded content since this modern app is
            // passing the UID of a legacy app capable of handling HEVC files.
            Bundle bundle = new Bundle();
            bundle.putInt(MediaStore.EXTRA_MEDIA_CAPABILITIES_UID,
                    getContext().getPackageManager().getPackageUid(
                            TEST_APP_SLOW_MOTION.getPackageName(), 0));
            pfdModernAppPassingLegacyUid = open(uri, false, bundle);

            assertTranscode(pfdModernApp, false);
            assertTranscode(pfdModernAppPassingLegacyUid, true);

            // pfdModernApp and pfdModernAppPassingLegacyUid should be different.
            assertFileContent(modernFile, modernFile, pfdModernApp, pfdModernAppPassingLegacyUid,
                    false);
        } finally {
            if (pfdModernApp != null) {
                pfdModernApp.close();
            }

            if (pfdModernAppPassingLegacyUid != null) {
                pfdModernAppPassingLegacyUid.close();
            }
            modernFile.delete();
            uninstallApp(TEST_APP_SLOW_MOTION);
        }
    }

    /**
     * Tests that we don't transcode an HEVC file when a legacy app passes the mediaCapabilitiesUid
     * of a modern app that can handle an HEVC file.
     */
    @Test
    public void testOriginalCallingUid_legacyAppPassModernAppUid()
            throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        ParcelFileDescriptor pfdLegacyApp = null;
        ParcelFileDescriptor pfdLegacyAppPassingModernUid = null;
        try {
            installAppWithStoragePermissions(TEST_APP_HEVC);
            Uri uri = TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            // pfdLegacyApp is for transcoded content since this is a legacy app.
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            pfdLegacyApp = open(modernFile, false);

            // pfdLegacyAppPassingModernUid is for original content (without transcoding) since this
            // legacy app is passing the UID of a modern app capable of handling HEVC files.
            Bundle bundle = new Bundle();
            bundle.putInt(MediaStore.EXTRA_MEDIA_CAPABILITIES_UID,
                    getContext().getPackageManager().getPackageUid(TEST_APP_HEVC.getPackageName(),
                            0));
            pfdLegacyAppPassingModernUid = open(uri, false, bundle);

            assertTranscode(pfdLegacyApp, true);
            assertTranscode(pfdLegacyAppPassingModernUid, false);

            // pfdLegacyApp and pfdLegacyAppPassingModernUid should be different.
            assertFileContent(modernFile, modernFile, pfdLegacyApp, pfdLegacyAppPassingModernUid,
                    false);
        } finally {
            if (pfdLegacyApp != null) {
                pfdLegacyApp.close();
            }

            if (pfdLegacyAppPassingModernUid != null) {
                pfdLegacyAppPassingModernUid.close();
            }
            modernFile.delete();
            uninstallApp(TEST_APP_HEVC);
        }
    }

    /**
     * Tests that we return FD of original file from
     * {@link MediaStore#getOriginalMediaFormatFileDescriptor}.
     * @throws Exception
     */
    @Test
    public void testGetOriginalMediaFormatFileDescriptor_onSuccess_returnsOriginalFileDescriptor()
            throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);

            ParcelFileDescriptor pfdOriginal = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            ParcelFileDescriptor pfdTranscoded = open(modernFile, false);

            ParcelFileDescriptor pfdOriginalMediaFormat =
                    MediaStore.getOriginalMediaFormatFileDescriptor(getContext(), pfdTranscoded);

            assertFileContent(modernFile, modernFile, pfdOriginal, pfdOriginalMediaFormat, true);
            assertFileContent(modernFile, modernFile, pfdTranscoded, pfdOriginalMediaFormat, false);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that IOException is thrown from {@link MediaStore#getOriginalMediaFormatFileDescriptor}
     * to indicate failure.
     * @throws Exception
     */
    @Test
    public void testGetOriginalMediaFormatFileDescriptor_onFailure_throwsIOException()
            throws Exception {
        // Create file in internal storage instead of external storage so that
        // getOriginalMediaFormatFileDescriptor fails for the fd of this file.
        File file = new File(getContext().getFilesDir(), HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(file);
            ParcelFileDescriptor pfd = open(file, false);
            assertThrows(IOException.class,
                    () -> MediaStore.getOriginalMediaFormatFileDescriptor(getContext(), pfd));
        } finally {
            file.delete();
        }
    }

    /**
     * Tests that transcoding cache gets cleared when PackagerManager frees storage.
     */
    @Test
    public void testTranscodingCacheClear() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());

            // Trigger transcoding so that the transcoded file gets added to cache.
            assertTranscode(modernFile, true);

            // Invoke PackageManager to free a huge amount of storage, so that it tries to clear all
            // available caches.
            executeShellCommand("pm trim-caches 4096G");

            // Assert that transcoding happens again, i.e., transcoding cache was cleared.
            assertTranscode(modernFile, true);
        } finally {
            modernFile.delete();
        }
    }

    /**
     * Tests that we can successfully write to a transcoded file.
     * We check this by writing something to tanscoded content and then read it back.
     */
    @Test
    public void testWriteSuccessfulToTranscodedContent() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        ParcelFileDescriptor pfdTranscodedContent = null;
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            pfdTranscodedContent = open(modernFile, false);

            // read some bytes from some random offset
            Random random = new Random(System.currentTimeMillis());
            int byteCount = 512;
            int fileOffset = random.nextInt((int) pfdTranscodedContent.getStatSize() - byteCount);
            byte[] readBytes = TranscodeTestUtils.read(pfdTranscodedContent, byteCount, fileOffset);

            // write the bytes at the same offset after some modification
            pfdTranscodedContent = open(modernFile, true);
            byte[] writeBytes = new byte[byteCount];
            for (int i = 0; i < byteCount; ++i) {
                writeBytes[i] = (byte) ~readBytes[i];
            }
            TranscodeTestUtils.write(pfdTranscodedContent, writeBytes, byteCount, fileOffset);

            // read back the same number of bytes from the same offset
            readBytes = TranscodeTestUtils.read(pfdTranscodedContent, byteCount, fileOffset);

            // assert that read is same as written
            assertTrue(Arrays.equals(readBytes, writeBytes));
        } finally {
            if (pfdTranscodedContent != null) {
                pfdTranscodedContent.close();
            }
            modernFile.delete();
        }
    }

    @Test
    public void testTranscodeDirectoryNotAccessible() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        ParcelFileDescriptor pfdTranscodedContent = null;
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            pfdTranscodedContent = open(modernFile, false);
            TranscodeTestUtils.read(pfdTranscodedContent, 512, 0);

            // Transcode directory must be created now.
            String transcodeDirPath =
                    "/storage/emulated/" + UserHandle.myUserId() + "/.transforms/transcode";
            File transcodeDir = new File(transcodeDirPath);
            assertThat(transcodeDir.exists()).isFalse();
        } finally {
            if (pfdTranscodedContent != null) {
                pfdTranscodedContent.close();
            }
            modernFile.delete();
        }
    }

    @Ignore
    @Test
    public void testTranscodeMultipleFilesConcurrently_mediumDurationMediumVolume() throws Exception {
        ModernFileOpenerThread[] modernFileOpenerThreads = new ModernFileOpenerThread[20];
        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i] = new ModernFileOpenerThread(
                    ModernFileOpenerThread.FileDurationSeconds.TWENTIES);
        }

        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i].start();
        }

        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i].join();
            if (modernFileOpenerThreads[i].mException != null) {
                throw new Exception("Failed ModernFileOpenerThread - " + i + ": "
                        + modernFileOpenerThreads[i].mException.getMessage(),
                        modernFileOpenerThreads[i].mException);
            }
        }
    }

    @Ignore
    @Test
    public void testTranscodeMultipleFilesConcurrently_lowDurationHighVolume() throws Exception {
        ModernFileOpenerThread[] modernFileOpenerThreads = new ModernFileOpenerThread[100];
        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i] = new ModernFileOpenerThread(
                    ModernFileOpenerThread.FileDurationSeconds.FEW);
        }

        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i].start();
        }

        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i].join();
            if (modernFileOpenerThreads[i].mException != null) {
                throw new Exception("Failed ModernFileOpenerThread - " + i + ": "
                        + modernFileOpenerThreads[i].mException.getMessage(),
                        modernFileOpenerThreads[i].mException);
            }
        }
    }

    @Ignore
    @Test
    public void testTranscodeMultipleFilesConcurrently_longDurationLowVolume() throws Exception {
        ModernFileOpenerThread[] modernFileOpenerThreads = new ModernFileOpenerThread[5];
        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i] = new ModernFileOpenerThread(
                    ModernFileOpenerThread.FileDurationSeconds.HUNDRED);
        }

        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i].start();
        }

        for (int i = 0; i < modernFileOpenerThreads.length; ++i) {
            modernFileOpenerThreads[i].join();
            if (modernFileOpenerThreads[i].mException != null) {
                throw new Exception("Failed ModernFileOpenerThread - " + i + ": "
                        + modernFileOpenerThreads[i].mException.getMessage(),
                        modernFileOpenerThreads[i].mException);
            }
        }
    }

    private static final class ModernFileOpenerThread extends Thread {
        private final FileDurationSeconds mFileDurationSeconds;
        Throwable mException;

        ModernFileOpenerThread(FileDurationSeconds fileDurationSeconds) {
            mFileDurationSeconds = fileDurationSeconds;
        }

        @Override
        public void run() {
            try {
                openFile();
            } catch (Exception exception) {
                mException = exception;
            }
        }

        private void openFile() throws Exception {
            String fileName = "TranscodeTestHEVC_" + System.nanoTime() + ".mp4";
            File modernFile = new File(DIR_CAMERA, fileName);
            ParcelFileDescriptor pfdTranscoded = null;
            try {
                switch (mFileDurationSeconds) {
                    case FEW:
                        TranscodeTestUtils.stageHEVCVideoFile(modernFile);
                        break;
                    case TWENTIES:
                        TranscodeTestUtils.stageMediumHevcVideoFile(modernFile);
                        break;
                    case HUNDRED:
                        TranscodeTestUtils.stageLongHevcVideoFile(modernFile);
                        break;
                    default:
                        throw new IllegalStateException(
                                "Unknown mFileDurationSeconds: " + mFileDurationSeconds);
                }
                TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
                pfdTranscoded = open(modernFile, false);
                assertTranscode(pfdTranscoded, true);
            } finally {
                if (pfdTranscoded != null) {
                    pfdTranscoded.close();
                }
                modernFile.delete();
            }
        }

        enum FileDurationSeconds {
            FEW,
            TWENTIES,
            HUNDRED
        }
    }

    /**
     * Tests {@link StorageManager#isAppIoBlocked}
     * @throws Exception
     */
    @Test
    public void test_IsAppIoBlocked() throws Exception {
        File modernFile = new File(DIR_CAMERA, HEVC_FILE_NAME);
        StorageManager sm = getContext().getSystemService(StorageManager.class);
        StorageVolume vol = sm.getStorageVolume(modernFile);
        UUID uuid = vol.getStorageUuid();
        try {
            TranscodeTestUtils.stageHEVCVideoFile(modernFile);
            ParcelFileDescriptor pfdOriginal = open(modernFile, false);

            TranscodeTestUtils.enableTranscodingForPackage(getContext().getPackageName());
            ParcelFileDescriptor pfdTranscoded = open(modernFile, false);

            assertFalse(isAppIoBlocked(sm, uuid));

            boolean[] success = new boolean[]{true};
            Thread transcodeThread = new Thread(() -> {
                try {
                    assertFileContent(modernFile, modernFile, pfdOriginal, pfdTranscoded, false);
                } catch (Exception e) {
                    success[0] = false;
                }
            });
            transcodeThread.start();

            // Check in a loop if app IO is blocked cos there might be a delay between read(2)
            // and when the transcoding is scheduled
            int timeLeftMs = 5000;
            int sleepMs = 100;
            boolean appIoBlocked = false;
            while (timeLeftMs > 0) {
                if (isAppIoBlocked(sm, uuid)) {
                    appIoBlocked = true;
                    break;
                }
                timeLeftMs -= sleepMs;
                Thread.sleep(sleepMs);
            }
            assertTrue(appIoBlocked);

            // Wait for transcoding to finish successfully
            transcodeThread.join();
            assertTrue(success[0]);

            assertFalse(isAppIoBlocked(sm, uuid));
        } finally {
            modernFile.delete();
        }
    }
}
