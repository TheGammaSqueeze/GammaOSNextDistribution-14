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

package android.scopedstorage.cts.redacturi;


import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.database.Cursor.FIELD_TYPE_BLOB;
import static android.scopedstorage.cts.lib.TestUtils.addressStoragePermissions;
import static android.scopedstorage.cts.lib.TestUtils.assertThrows;
import static android.scopedstorage.cts.lib.TestUtils.canOpenRedactedUriForWrite;
import static android.scopedstorage.cts.lib.TestUtils.canQueryOnUri;
import static android.scopedstorage.cts.lib.TestUtils.checkPermission;
import static android.scopedstorage.cts.lib.TestUtils.forceStopApp;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.grantPermission;
import static android.scopedstorage.cts.lib.TestUtils.isFileDescriptorRedacted;
import static android.scopedstorage.cts.lib.TestUtils.isFileOpenRedacted;
import static android.scopedstorage.cts.lib.TestUtils.setShouldForceStopTestApp;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;


import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.scopedstorage.cts.lib.ScopedStorageBaseDeviceTest;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.system.Os;

import androidx.test.filters.SdkSuppress;

import com.android.cts.install.lib.TestApp;
import com.android.modules.utils.build.SdkLevel;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Device-side test suite to verify redacted URI operations.
 */
@RunWith(Parameterized.class)
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class RedactUriDeviceTest extends ScopedStorageBaseDeviceTest {

    /**
     * To help avoid flaky tests, give ourselves a unique nonce to be used for
     * all filesystem paths, so that we don't risk conflicting with previous
     * test runs.
     */
    static final String NONCE = String.valueOf(System.nanoTime());

    static final String IMAGE_FILE_NAME = "ScopedStorageDeviceTest_file_" + NONCE + ".jpg";

    static final String FUZZER_HEIC_FILE_NAME =
            "ScopedStorageDeviceTest_file_fuzzer_" + NONCE + ".heic";

    // An app with no permissions
    private static final TestApp APP_B_NO_PERMS = new TestApp("TestAppB",
            "android.scopedstorage.cts.testapp.B.noperms", 1, false,
            "CtsScopedStorageTestAppB.apk");

    private static final TestApp APP_E = new TestApp("TestAppE",
            "android.scopedstorage.cts.testapp.E", 1, false, "CtsScopedStorageTestAppE.apk");

    @Parameterized.Parameter(0)
    public String mVolumeName;

    /** Parameters data. */
    @Parameterized.Parameters(name = "volume={0}")
    public static Iterable<? extends Object> data() {
        return getTestParameters();
    }

    @BeforeClass
    public static void setupApps() {
        // Installed by target preparer
        assertThat(checkPermission(APP_B_NO_PERMS,
                Manifest.permission.READ_EXTERNAL_STORAGE)).isFalse();
        setShouldForceStopTestApp(false);
    }

    @AfterClass
    public static void destroy() {
        setShouldForceStopTestApp(true);
    }

    @Test
    public void testRedactedUri_single() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);

        try {
            final Uri uri = MediaStore.scanFile(getContentResolver(), img);
            final Uri redactedUri = MediaStore.getRedactedUri(getContentResolver(), uri);
            testRedactedUriCommon(uri, redactedUri);
        } finally {
            img.delete();
        }
    }

    @Test
    public void testRedactedUri_list() throws Exception {
        List<Uri> uris = new ArrayList<>();
        List<File> files = new ArrayList<>();

        try {
            for (int i = 0; i < 10; i++) {
                File file = stageImageFileWithMetadata("img_metadata" + String.valueOf(
                        System.nanoTime()) + i + ".jpg");
                files.add(file);
                uris.add(MediaStore.scanFile(getContentResolver(), file));
            }

            final Collection<Uri> redactedUris = MediaStore.getRedactedUri(getContentResolver(),
                    uris);
            int i = 0;
            for (Uri redactedUri : redactedUris) {
                Uri uri = uris.get(i++);
                testRedactedUriCommon(uri, redactedUri);
            }
        } finally {
            files.forEach(file -> file.delete());
        }
    }

    @Test
    public void testQueryOnRedactionUri() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        final Uri uri = MediaStore.scanFile(getContentResolver(), img);
        final Uri redactedUri = MediaStore.getRedactedUri(getContentResolver(), uri);
        final Cursor uriCursor = getContentResolver().query(uri, null, null, null);
        final String redactedUriDir = ".transforms/synthetic/redacted";
        final String redactedUriDirAbsolutePath =
                Environment.getExternalStorageDirectory() + "/" + redactedUriDir;
        try {
            assertNotNull(uriCursor);
            assertThat(uriCursor.moveToFirst()).isTrue();

            final Cursor redactedUriCursor = getContentResolver().query(redactedUri, null, null,
                    null);
            assertNotNull(redactedUriCursor);
            assertThat(redactedUriCursor.moveToFirst()).isTrue();

            assertEquals(redactedUriCursor.getColumnCount(), uriCursor.getColumnCount());

            final String data = getStringFromCursor(redactedUriCursor,
                    MediaStore.MediaColumns.DATA);
            final String redactedUriDisplayName = redactedUri.getLastPathSegment() + ".jpg";
            assertEquals(redactedUriDirAbsolutePath + "/" + redactedUriDisplayName, data);

            final String name = getStringFromCursor(redactedUriCursor,
                    MediaStore.MediaColumns.DISPLAY_NAME);
            assertEquals(redactedUriDisplayName, name);

            final String relativePath = getStringFromCursor(redactedUriCursor,
                    MediaStore.MediaColumns.RELATIVE_PATH);
            assertEquals(redactedUriDir, relativePath);

            final String bucketDisplayName = getStringFromCursor(redactedUriCursor,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
            assertEquals(redactedUriDir, bucketDisplayName);

            final String docId = getStringFromCursor(redactedUriCursor,
                    MediaStore.MediaColumns.DOCUMENT_ID);
            assertNull(docId);

            final String insId = getStringFromCursor(redactedUriCursor,
                    MediaStore.MediaColumns.INSTANCE_ID);
            assertNull(insId);

            final String bucId = getStringFromCursor(redactedUriCursor,
                    MediaStore.MediaColumns.BUCKET_ID);
            assertNull(bucId);

            final Collection<String> updatedCols = Arrays.asList(MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DOCUMENT_ID,
                    MediaStore.MediaColumns.INSTANCE_ID,
                    MediaStore.MediaColumns.BUCKET_ID);
            for (String colName : uriCursor.getColumnNames()) {
                if (!updatedCols.contains(colName)) {
                    if (uriCursor.getType(uriCursor.getColumnIndex(colName)) == FIELD_TYPE_BLOB) {
                        assertThat(
                                Arrays.equals(uriCursor.getBlob(uriCursor.getColumnIndex(colName)),
                                        redactedUriCursor.getBlob(redactedUriCursor.getColumnIndex(
                                                colName)))).isTrue();
                    } else {
                        assertEquals(getStringFromCursor(uriCursor, colName),
                                getStringFromCursor(redactedUriCursor, colName));
                    }
                }
            }
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that app can't open the shared redacted URI for write.
     **/
    @Test
    public void testSharedRedactedUri_openFdForWrite() throws Exception {
        forceStopApp(APP_B_NO_PERMS.getPackageName());
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        try {
            Uri redactedUri = shareAndGetRedactedUri(img, APP_B_NO_PERMS);
            assertThrows(UnsupportedOperationException.class,
                    () -> canOpenRedactedUriForWrite(APP_B_NO_PERMS, redactedUri));
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that app with correct permission can open the shared redacted URI for read in
     * redacted mode.
     **/
    @Test
    public void testSharedRedactedUri_openFdForRead() throws Exception {
        forceStopApp(APP_B_NO_PERMS.getPackageName());
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        try {
            final Uri redactedUri = shareAndGetRedactedUri(img, APP_B_NO_PERMS);
            assertThat(isFileDescriptorRedacted(APP_B_NO_PERMS, redactedUri)).isTrue();
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that app with correct permission can open the shared redacted URI for read in
     * redacted mode.
     **/
    @Test
    public void testSharedRedactedUri_openFileForRead() throws Exception {
        forceStopApp(APP_B_NO_PERMS.getPackageName());
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        try {
            Uri redactedUri = shareAndGetRedactedUri(img, APP_B_NO_PERMS);
            assertThat(isFileOpenRedacted(APP_B_NO_PERMS, redactedUri)).isTrue();
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that the app with redacted URI granted can query it.
     **/
    @Test
    public void testSharedRedactedUri_query() throws Exception {
        forceStopApp(APP_B_NO_PERMS.getPackageName());
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        try {
            Uri redactedUri = shareAndGetRedactedUri(img, APP_B_NO_PERMS);
            assertThat(canQueryOnUri(APP_B_NO_PERMS, redactedUri)).isTrue();
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that for app with AML permission shared redacted URI opens for read in redacted mode.
     **/
    @Test
    public void testSharedRedactedUri_openFileForRead_withLocationPerm() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        forceStopApp(APP_E.getPackageName());
        try {
            addressStoragePermissions(APP_E.getPackageName(), true);
            grantPermission(APP_E.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);

            Uri redactedUri = shareAndGetRedactedUri(img, APP_E);
            assertThat(isFileOpenRedacted(APP_E, redactedUri)).isTrue();
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that for app with AML permission shared redacted URI opens for read in redacted mode.
     **/
    @Test
    public void testSharedRedactedUri_openFdForRead_withLocationPerm() throws Exception {
        forceStopApp(APP_E.getPackageName());
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        try {
            addressStoragePermissions(APP_E.getPackageName(), true);
            grantPermission(APP_E.getPackageName(), Manifest.permission.ACCESS_MEDIA_LOCATION);

            Uri redactedUri = shareAndGetRedactedUri(img, APP_E);
            assertThat(isFileDescriptorRedacted(APP_E, redactedUri)).isTrue();
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that the test app can't access unshared redacted uri via file descriptor
     **/
    @Test
    public void testUnsharedRedactedUri_openFdForRead() throws Exception {
        forceStopApp(APP_B_NO_PERMS.getPackageName());
        forceStopApp(APP_E.getPackageName());
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        try {
            addressStoragePermissions(APP_E.getPackageName(), true);

            final Uri redactedUri = getRedactedUri(img);
            // APP_E has R_E_S, so should have access to redactedUri
            assertThat(isFileDescriptorRedacted(APP_E, redactedUri)).isTrue();
            assertThrows(SecurityException.class,
                    () -> isFileDescriptorRedacted(APP_B_NO_PERMS, redactedUri));
        } finally {
            img.delete();
        }
    }

    /*
     * Verify that the test app can't access unshared redacted uri via file path
     **/
    @Test
    public void testUnsharedRedactedUri_openFileForRead() throws Exception {
        forceStopApp(APP_B_NO_PERMS.getPackageName());
        forceStopApp(APP_E.getPackageName());
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        try {
            addressStoragePermissions(APP_E.getPackageName(), true);

            final Uri redactedUri = getRedactedUri(img);
            // APP_E has R_E_S
            assertThat(isFileOpenRedacted(APP_E, redactedUri)).isTrue();
            assertThrows(IOException.class, () -> isFileOpenRedacted(APP_B_NO_PERMS, redactedUri));
        } finally {
            img.delete();
        }
    }

    @Test
    public void testGrantUriPermissionsForRedactedUri() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        final Uri redactedUri = getRedactedUri(img);
        try {
            getContext().grantUriPermission(APP_B_NO_PERMS.getPackageName(), redactedUri,
                    FLAG_GRANT_READ_URI_PERMISSION);
            assertThrows(SecurityException.class, () ->
                    getContext().grantUriPermission(APP_B_NO_PERMS.getPackageName(), redactedUri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } finally {
            img.delete();
        }
    }

    @Test
    public void testDisallowedOperationsOnRedactedUri() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        final Uri redactedUri = getRedactedUri(img);
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DATE_ADDED, 1);
            assertEquals(0, getContentResolver().update(redactedUri, new ContentValues(),
                    new Bundle()));
            assertEquals(0, getContentResolver().delete(redactedUri, new Bundle()));
        } finally {
            img.delete();
        }
    }

    @Test
    public void testOpenOnRedactedUri_file() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        final Uri redactedUri = getRedactedUri(img);
        try {
            assertUriIsUnredacted(img);

            final Cursor redactedUriCursor = getRedactedCursor(redactedUri);
            File file = new File(
                    getStringFromCursor(redactedUriCursor, MediaStore.MediaColumns.DATA));
            ExifInterface redactedExifInf = new ExifInterface(file);
            assertUriIsRedacted(redactedExifInf);

            assertThrows(FileNotFoundException.class, () -> new FileOutputStream(file));
        } finally {
            img.delete();
        }
    }

    @Test
    public void testOpenOnRedactedUri_write() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        final Uri redactedUri = getRedactedUri(img);
        try {
            assertThrows(UnsupportedOperationException.class,
                    () -> getContentResolver().openFileDescriptor(redactedUri,
                            "w"));
        } finally {
            img.delete();
        }
    }

    @Test
    public void testOpenOnRedactedUri_inputstream() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        final Uri redactedUri = getRedactedUri(img);
        try {
            assertUriIsUnredacted(img);

            try (InputStream is = getContentResolver().openInputStream(redactedUri)) {
                ExifInterface redactedExifInf = new ExifInterface(is);
                assertUriIsRedacted(redactedExifInf);
            }
        } finally {
            img.delete();
        }
    }

    @Test
    public void testOpenOnRedactedUri_read() throws Exception {
        final File img = stageImageFileWithMetadata(IMAGE_FILE_NAME);
        final Uri redactedUri = getRedactedUri(img);
        try {
            assertUriIsUnredacted(img);

            try (ParcelFileDescriptor pfd =
                         getContentResolver().openFileDescriptor(redactedUri, "r")) {
                FileDescriptor fd = pfd.getFileDescriptor();
                ExifInterface redactedExifInf = new ExifInterface(fd);
                assertUriIsRedacted(redactedExifInf);
            }
        } finally {
            img.delete();
        }
    }

    @Test
    public void testOpenOnRedactedUri_readFuzzer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        final File img = stageFuzzerImageFileWithMetadata(FUZZER_HEIC_FILE_NAME);
        final Uri redactedUri = getRedactedUri(img);
        try {
            assertUriIsUnredacted(img);

            try (ParcelFileDescriptor pfd =
                         getContentResolver().openFileDescriptor(redactedUri, "r")) {
                FileDescriptor fd = pfd.getFileDescriptor();
                int bufSize = 0x1000000;
                byte[] data = new byte[bufSize];
                int fileSize = Os.read(fd, data, 0, bufSize);
                assertUriIsRedacted(data, fileSize);
            }
        } finally {
            img.delete();
        }
    }

    private void testRedactedUriCommon(Uri uri, Uri redactedUri) {
        assertEquals(redactedUri.getAuthority(), uri.getAuthority());
        assertEquals(redactedUri.getScheme(), uri.getScheme());
        assertNotEquals(redactedUri.getPath(), uri.getPath());
        assertNotEquals(redactedUri.getPathSegments(), uri.getPathSegments());

        final String uriId = redactedUri.getLastPathSegment();
        assertThat(uriId.startsWith("RUID")).isTrue();
        assertEquals(uriId.length(), 36);
    }

    private Uri shareAndGetRedactedUri(File file, TestApp testApp) {
        final Uri redactedUri = getRedactedUri(file);
        getContext().grantUriPermission(testApp.getPackageName(), redactedUri,
                FLAG_GRANT_READ_URI_PERMISSION);

        return redactedUri;
    }

    private Uri getRedactedUri(File file) {
        final Uri uri = MediaStore.scanFile(getContentResolver(), file);
        return MediaStore.getRedactedUri(getContentResolver(), uri);
    }

    private void assertUriIsUnredacted(File img) throws Exception {
        final ExifInterface exifInterface = new ExifInterface(img);
        assertNotEquals(exifInterface.getGpsDateTime(), -1);

        float[] latLong = new float[]{0, 0};
        exifInterface.getLatLong(latLong);
        assertNotEquals(latLong[0], 0);
        assertNotEquals(latLong[1], 0);
    }

    private void assertUriIsRedacted(ExifInterface redactedExifInf) {
        assertEquals(redactedExifInf.getGpsDateTime(), -1);
        float[] latLong = new float[]{0, 0};
        redactedExifInf.getLatLong(latLong);
        assertEquals(latLong[0], 0.0, 0.0);
        assertEquals(latLong[1], 0.0, 0.0);
    }

    private void assertUriIsRedacted(byte[] data, int fileSize) {
        // Data in redaction ranges should be zero.
        int[] start = new int[]{50538, 712941, 712965, 712989, 713033, 713101};
        int[] end = new int[]{711958, 712943, 712967, 712990, 713100, 713125};

        assertTrue(fileSize == 4407744);
        for (int index = 0; index < start.length && index < end.length; index++) {
            for (int c = start[index]; c < end[index]; c++) {
                assertTrue("It should be zero!", data[c] == (byte) 0);
            }
        }
    }

    private Cursor getRedactedCursor(Uri redactedUri) {
        Cursor redactedUriCursor = getContentResolver().query(redactedUri, null, null, null);
        assertNotNull(redactedUriCursor);
        assertThat(redactedUriCursor.moveToFirst()).isTrue();

        return redactedUriCursor;
    }

    private String getStringFromCursor(Cursor c, String colName) {
        return c.getString(c.getColumnIndex(colName));
    }

    private File stageImageFileWithMetadata(String name) throws Exception {
        return stageImageFileWithMetadata(name, R.raw.img_with_metadata);
    }

    private File stageFuzzerImageFileWithMetadata(String name) throws Exception {
        return stageImageFileWithMetadata(name, R.raw.fuzzer);
    }

    private File stageImageFileWithMetadata(String name, int sourceId) throws Exception {
        final File img = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), name);

        try (InputStream in =
                     getContext().getResources().openRawResource(sourceId);
             OutputStream out = new FileOutputStream(img)) {
            // Dump the image we have to external storage
            FileUtils.copy(in, out);
        }

        return img;
    }
}
