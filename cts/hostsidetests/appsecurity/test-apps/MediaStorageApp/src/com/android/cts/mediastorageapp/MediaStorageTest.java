/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.mediastorageapp;

import static android.provider.MediaStore.VOLUME_EXTERNAL;
import static android.scopedstorage.cts.lib.TestUtils.doEscalation;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.cts.mediastorageapp.MediaStoreUtils.PendingParams;
import com.android.cts.mediastorageapp.MediaStoreUtils.PendingSession;

import com.google.common.io.ByteStreams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class MediaStorageTest {
    private static final File TEST_JPG = Environment.buildPath(
            Environment.getExternalStorageDirectory(),
            Environment.DIRECTORY_DOWNLOADS, "mediastoragetest_file1.jpg");
    private static final File TEST_PDF = Environment.buildPath(
            Environment.getExternalStorageDirectory(),
            Environment.DIRECTORY_DOWNLOADS, "mediastoragetest_file2.pdf");

    private Context mContext;
    private ContentResolver mContentResolver;
    private int mUserId;

    private static int currentAttempt = 0;
    private static final int MAX_NUMBER_OF_ATTEMPT = 10;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();
        mUserId = mContext.getUserId();
    }

    @Test
    public void testLegacy() throws Exception {
        assertTrue(Environment.isExternalStorageLegacy());

        // We can always see mounted state
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());

        // We might have top-level access
        final File probe = new File(Environment.getExternalStorageDirectory(),
                "cts" + System.nanoTime());
        assertTrue(probe.createNewFile());
        assertNotNull(Environment.getExternalStorageDirectory().list());

        // We always have our package directories
        final File probePackage = new File(mContext.getExternalFilesDir(null),
                "cts" + System.nanoTime());
        assertTrue(probePackage.createNewFile());

        assertTrue(TEST_JPG.exists());
        assertTrue(TEST_PDF.exists());

        final Uri jpgUri = MediaStore.scanFile(mContentResolver, TEST_JPG);
        final Uri pdfUri = MediaStore.scanFile(mContentResolver, TEST_PDF);

        final HashSet<Long> seen = new HashSet<>();
        try (Cursor c = mContentResolver.query(
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                new String[]{MediaColumns._ID}, null, null)) {
            while (c.moveToNext()) {
                seen.add(c.getLong(0));
            }
        }

        assertTrue(seen.contains(ContentUris.parseId(jpgUri)));
        assertTrue(seen.contains(ContentUris.parseId(pdfUri)));
    }

    @Test
    public void testStageFiles() throws Exception {
        final File jpg = stageFile(TEST_JPG);
        assertTrue(jpg.exists());
        final File pdf = stageFile(TEST_PDF);
        assertTrue(pdf.exists());
    }

    @Test
    public void testClearFiles() throws Exception {
        TEST_JPG.delete();
        assertNull(MediaStore.scanFile(mContentResolver, TEST_JPG));
        TEST_PDF.delete();
        assertNull(MediaStore.scanFile(mContentResolver, TEST_PDF));
    }

    @Test
    public void testMediaNone() throws Exception {
        doMediaNone(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createAudio);
        doMediaNone(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createVideo);
        doMediaNone(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createImage);

        // But since we don't hold the Music permission, we can't read the
        // indexed metadata
        try (Cursor c = mContentResolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                null, null, null)) {
            assertEquals(0, c.getCount());
        }
        try (Cursor c = mContentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                null, null, null)) {
            assertEquals(0, c.getCount());
        }
        try (Cursor c = mContentResolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                null, null, null)) {
            assertEquals(0, c.getCount());
        }
    }

    private void doMediaNone(Uri collection, Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        final Uri blue = create.call();

        clearMediaOwner(blue, mUserId);

        // Since we have no permissions, we should only be able to see media
        // that we've contributed
        final HashSet<Long> seen = new HashSet<>();
        try (Cursor c = mContentResolver.query(collection,
                new String[]{MediaColumns._ID}, null, null)) {
            while (c.moveToNext()) {
                seen.add(c.getLong(0));
            }
        }

        assertTrue(seen.contains(ContentUris.parseId(red)));
        assertFalse(seen.contains(ContentUris.parseId(blue)));

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
            fail("Expected read access to be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
            fail("Expected write access to be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }

        // Verify that we can't grant ourselves access
        for (int flag : new int[]{
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }) {
            try {
                mContext.grantUriPermission(mContext.getPackageName(), blue, flag);
                fail("Expected granting to be blocked for flag 0x" + Integer.toHexString(flag));
            } catch (SecurityException expected) {
            }
        }
    }

    /**
     * If the app grants read UriPermission to the uri without id (E.g.
     * MediaStore.Audio.Media.EXTERNAL_CONTENT_URI), the query result of the uri should be the
     * same without granting permission.
     */
    @Test
    public void testReadUriPermissionOnUriWithoutId_sameQueryResult() throws Exception {
        // For Audio, Image, Video, If the app doesn't have delete access to the uri,
        // MediaProvider throws SecurityException to give callers interacting with a specific media
        // item a chance to escalate access if they don't already have it. Check SecurityException
        // for them.
        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createAudio, /* checkExceptionForDelete= */ true);
        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createVideo,/* checkExceptionForDelete= */  true);
        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createImage,/* checkExceptionForDelete= */  true);

        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createAudio, /* checkExceptionForDelete= */ true);
        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createAudio, /* checkExceptionForDelete= */ true);
        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createAudio, /* checkExceptionForDelete= */ true);

        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createDownload,/* checkExceptionForDelete= */  false);
        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                MediaStorageTest::createFile,/* checkExceptionForDelete= */  false);
        doReadUriPermissionOnUriWithoutId_sameQueryResult(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createPlaylist,/* checkExceptionForDelete= */  false);
    }

    /**
     * If the app grants read UriPermission to the uri without id (E.g.
     * MediaStore.Audio.Media.EXTERNAL_CONTENT_URI), the query result of the uri should be the
     * same without granting permission.
     */
    private void doReadUriPermissionOnUriWithoutId_sameQueryResult(Uri collectionUri,
            Callable<Uri> create, boolean checkExceptionForDelete) throws Exception {
        final int flagGrantRead = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        final Uri red = create.call();
        final Uri blue = create.call();
        clearMediaOwner(blue, mUserId);
        final int originalCount;

        try {
            try (Cursor c = mContentResolver.query(collectionUri, new String[]{MediaColumns._ID},
                    null, null)) {
                originalCount = c.getCount();
            }

            mContext.grantUriPermission(mContext.getPackageName(), collectionUri, flagGrantRead);
            try (Cursor c = mContentResolver.query(collectionUri, new String[]{MediaColumns._ID},
                    null, null)) {
                assertWithMessage("After grant read UriPermission to " + collectionUri.toString()
                        + ", the item count of the query result").that(c.getCount()).isEqualTo(
                        originalCount);
            }

            try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
            }

            try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
                fail("Expected read access to " + blue.toString() + " be blocked");
            } catch (SecurityException | FileNotFoundException expected) {
            }

            try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
                fail("Expected write access to " + blue.toString() + " be blocked");
            } catch (SecurityException | FileNotFoundException expected) {
            }

            // If checkExceptionForDelete is true, throws SecurityException is as we expected.
            // Otherwise, the app doesn't have delete access to the file, the deleted count is 0.
            if (checkExceptionForDelete) {
                try {
                    mContentResolver.delete(blue, null);
                    fail("Expected delete access to " + blue.toString() + " be blocked");
                } catch (SecurityException expected) {
                }
            } else {
                final int count = mContentResolver.delete(blue, null);
                assertThat(count).isEqualTo(0);
            }
        } finally {
            mContext.revokeUriPermission(mContext.getPackageName(), collectionUri, flagGrantRead);
        }
    }

    /**
     * b/197302116. The apps can't be granted prefix UriPermissions to the uri, when the query
     * result of the uri is 1.
     */
    @Test
    public void testOwningOneFileNotGrantPrefixUriPermission() throws Exception {
        doOwningOneFileNotGrantPrefixUriPermission(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createAudio);
        doOwningOneFileNotGrantPrefixUriPermission(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createVideo);
        doOwningOneFileNotGrantPrefixUriPermission(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createImage);
        doOwningOneFileNotGrantPrefixUriPermission(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createDownload);
        doOwningOneFileNotGrantPrefixUriPermission(MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                MediaStorageTest::createFile);
        doOwningOneFileNotGrantPrefixUriPermission(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                MediaStorageTest::createPlaylist);
    }

    /**
     * The apps can't be granted prefix UriPermissions to the uri without id, when the query result
     * of the uri is 1.
     */
    private void doOwningOneFileNotGrantPrefixUriPermission(Uri collectionUri, Callable<Uri> create)
            throws Exception {

        clearOwnFiles(collectionUri);

        final Uri red = create.call();
        final Uri blue = create.call();
        clearMediaOwner(blue, mUserId);

        try (Cursor c = mContentResolver.query(collectionUri, new String[]{MediaColumns._ID}, null,
                null)) {
            assertThat(c.getCount()).isEqualTo(1);
            c.moveToFirst();
            assertThat(c.getLong(0)).isEqualTo(ContentUris.parseId(red));
        }

        final int flagGrantReadPrefix =
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        try {
            mContext.grantUriPermission(mContext.getPackageName(), collectionUri,
                    flagGrantReadPrefix);
            fail("Expected granting to " + collectionUri.toString() + " be blocked for flag 0x"
                    + Integer.toHexString(flagGrantReadPrefix));
        } catch (SecurityException expected) {
        }

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "r")) {
        }

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
            fail("Expected read access to " + blue.toString() + " be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }

        final int flagGrantWritePrefix = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        try {
            mContext.grantUriPermission(mContext.getPackageName(), collectionUri,
                    flagGrantWritePrefix);
            fail("Expected granting to " + collectionUri.toString() + " be blocked for flag 0x"
                    + Integer.toHexString(flagGrantWritePrefix));
        } catch (SecurityException expected) {
        }

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
        }

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
            fail("Expected write access to " + blue.toString() + " be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    @Test
    public void testGrantUriPermission() throws Exception {
        doGrantUriPermission_nonPrefixAndPrefix();
        doGrantUriPermission_prefix();
    }

    /**
     * Test prefix and non-prefix uri grant for all packages
     */
    private void doGrantUriPermission_nonPrefixAndPrefix() {
        final int flagGrantRead = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        final int flagGrantWrite = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        final int flagGrantReadPrefix =
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        final int flagGrantWritePrefix =
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

        for (Uri uri : new Uri[]{
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL)
        }) {
            // Non-prefix grant
            checkGrantUriPermission(uri, flagGrantRead, /* isGrantAllowed */ true);
            checkGrantUriPermission(uri, flagGrantWrite, /* isGrantAllowed */ true);

            // Prefix grant
            checkGrantUriPermission(uri, flagGrantReadPrefix, /* isGrantAllowed */ false);
            checkGrantUriPermission(uri, flagGrantWritePrefix, /* isGrantAllowed */ false);

            // revoke granted permissions
            mContext.revokeUriPermission(uri, flagGrantRead | flagGrantWrite);
        }
    }

    /**
     * b/194539422. Test prefix uri grant for all packages
     */
    private void doGrantUriPermission_prefix() {
        final int flagGrantReadPrefix =
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        final int flagGrantWritePrefix =
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

        for (Uri uri : new Uri[]{
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL)
        }) {
            checkGrantUriPermission(uri, flagGrantReadPrefix, /* isGrantAllowed */ false);
            checkGrantUriPermission(uri, flagGrantWritePrefix, /* isGrantAllowed */ false);
        }
    }

    private void checkGrantUriPermission(Uri uri, int mode, boolean isGrantAllowed) {
        if (isGrantAllowed) {
            mContext.grantUriPermission(mContext.getPackageName(), uri, mode);
        } else {
            try {
                mContext.grantUriPermission(mContext.getPackageName(), uri, mode);
                fail("Expected granting to " + uri.toString() + " be blocked for flag 0x"
                        + Integer.toHexString(mode));
            } catch (SecurityException expected) {
            }
        }
    }

    @Test
    public void testMediaRead() throws Exception {
        doMediaRead(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createAudio);
        doMediaRead(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createVideo);
        doMediaRead(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createImage);
    }

    private void doMediaRead(Uri collection, Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        final Uri blue = create.call();

        clearMediaOwner(blue, mUserId);

        // Holding read permission we can see items we don't own
        final HashSet<Long> seen = new HashSet<>();
        try (Cursor c = mContentResolver.query(collection,
                new String[]{MediaColumns._ID}, null, null)) {
            while (c.moveToNext()) {
                seen.add(c.getLong(0));
            }
        }

        assertTrue(seen.contains(ContentUris.parseId(red)));
        assertTrue(seen.contains(ContentUris.parseId(blue)));

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
            fail("Expected write access to be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    @Test
    public void testMediaWrite() throws Exception {
        doMediaWrite(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createAudio);
        doMediaWrite(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createVideo);
        doMediaWrite(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStorageTest::createImage);
    }

    private void doMediaWrite(Uri collection, Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        final Uri blue = create.call();

        clearMediaOwner(blue, mUserId);

        // Holding read permission we can see items we don't own
        final HashSet<Long> seen = new HashSet<>();
        try (Cursor c = mContentResolver.query(collection,
                new String[]{MediaColumns._ID}, null, null)) {
            while (c.moveToNext()) {
                seen.add(c.getLong(0));
            }
        }

        assertTrue(seen.contains(ContentUris.parseId(red)));
        assertTrue(seen.contains(ContentUris.parseId(blue)));

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
        }
        if (Environment.isExternalStorageLegacy()) {
            try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
            }
        } else {
            try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
                fail("Expected write access to be blocked");
            } catch (SecurityException | FileNotFoundException expected) {
            }
        }
    }

    @Test
    public void testMediaEscalation_Open() throws Exception {
        doMediaEscalation_Open(MediaStorageTest::createAudio);
        doMediaEscalation_Open(MediaStorageTest::createVideo);
        doMediaEscalation_Open(MediaStorageTest::createImage);
    }

    private void doMediaEscalation_Open(Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        RecoverableSecurityException exception = null;
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
            fail("Expected write access to be blocked");
        } catch (RecoverableSecurityException expected) {
            exception = expected;
        }

        doEscalation(exception);

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
        }
    }

    @Test
    public void testMediaEscalation_Update() throws Exception {
        doMediaEscalation_Update(MediaStorageTest::createAudio);
        doMediaEscalation_Update(MediaStorageTest::createVideo);
        doMediaEscalation_Update(MediaStorageTest::createImage);
    }

    private void doMediaEscalation_Update(Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        final ContentValues values = new ContentValues();
        values.put(MediaColumns.DISPLAY_NAME, "cts" + System.nanoTime());

        RecoverableSecurityException exception = null;
        try {
            mContentResolver.update(red, values, null, null);
            fail("Expected update access to be blocked");
        } catch (RecoverableSecurityException expected) {
            exception = expected;
        }

        doEscalation(exception);

        assertEquals(1, mContentResolver.update(red, values, null, null));
    }

    @Test
    public void testMediaEscalation_Delete() throws Exception {
        doMediaEscalation_Delete(MediaStorageTest::createAudio);
        doMediaEscalation_Delete(MediaStorageTest::createVideo);
        doMediaEscalation_Delete(MediaStorageTest::createImage);
    }

    private void doMediaEscalation_Delete(Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        RecoverableSecurityException exception = null;
        try {
            mContentResolver.delete(red, null, null);
            fail("Expected update access to be blocked");
        } catch (RecoverableSecurityException expected) {
            exception = expected;
        }

        doEscalation(exception);

        assertEquals(1, mContentResolver.delete(red, null, null));
    }

    @Test
    public void testMediaEscalation_RequestWriteFilePathSupport() throws Exception {
        doMediaEscalation_RequestWrite_withFilePathSupport(MediaStorageTest::createAudio);
        doMediaEscalation_RequestWrite_withFilePathSupport(MediaStorageTest::createVideo);
        doMediaEscalation_RequestWrite_withFilePathSupport(MediaStorageTest::createImage);
        doMediaEscalation_RequestWrite_withFilePathSupport(MediaStorageTest::createPlaylist);
        doMediaEscalation_RequestWrite_withFilePathSupport(MediaStorageTest::createSubtitle);
    }

    private void doMediaEscalation_RequestWrite_withFilePathSupport(
            Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        assertNotNull(red);
        String path = queryForSingleColumn(red, MediaColumns.DATA);
        File file = new File(path);
        assertThat(file.exists()).isTrue();
        assertThat(file.canRead()).isTrue();
        assertThat(file.canWrite()).isTrue();

        clearMediaOwner(red, mUserId);
        assertThat(file.canWrite()).isFalse();

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
            fail("Expected write access to be blocked");
        } catch (SecurityException expected) {
        }

        doEscalation(MediaStore.createWriteRequest(mContentResolver, Arrays.asList(red)));

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
        }
        // Wait for MediaStore to be idle to avoid flakiness due to race conditions
        MediaStore.waitForIdle(mContentResolver);

        // Check File API support
        assertAccessFileAPISupport(file);
        assertReadWriteFileAPISupport(file);
        assertRenameFileAPISupport(file);
        assertRenameAndReplaceFileAPISupport(file, create);
        assertDeleteFileAPISupport(file);
    }

    private void assertAccessFileAPISupport(File file) throws Exception {
        assertThat(file.canRead()).isTrue();
        assertThat(file.canWrite()).isTrue();
    }

    private void assertReadWriteFileAPISupport(File file) throws Exception {
        final String str = "Just some random text";
        final byte[] bytes = str.getBytes();
        // Write to file
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        }
        // Read the same data from file
        try (FileInputStream fis = new FileInputStream(file)) {
            assertThat(ByteStreams.toByteArray(fis)).isEqualTo(bytes);
        }
    }

    public void assertRenameFileAPISupport(File oldFile) throws Exception {
        final String oldName = oldFile.getAbsolutePath();
        final String extension = oldName.substring(oldName.lastIndexOf('.')).trim();
        // Rename to same extension so test app does not lose access to file.
        final String newRelativeName = "cts" + System.nanoTime() + extension;
        final File newFile = Environment.buildPath(
                Environment.getExternalStorageDirectory(),
                Environment.DIRECTORY_DOWNLOADS,
                newRelativeName);
        final String newName = newFile.getAbsolutePath();
        assertWithMessage("Rename from oldName [%s] to newName [%s]", oldName, newName)
                .that(oldFile.renameTo(newFile))
                .isTrue();
        // Rename back to oldFile for other ops like delete
        assertWithMessage("Rename back from newName [%s] to oldName [%s]", newName, oldName)
                .that(newFile.renameTo(oldFile))
                .isTrue();
    }

    public void assertRenameAndReplaceFileAPISupport(File oldFile, Callable<Uri> create)
            throws Exception {
        final String oldName = oldFile.getAbsolutePath();

        // Create new file to which we do not have any access.
        final Uri newUri = create.call();
        assertWithMessage("Check newFile created").that(newUri).isNotNull();
        File newFile = new File(queryForSingleColumn(newUri, MediaColumns.DATA));
        final String newName = newFile.getAbsolutePath();
        clearMediaOwner(newUri, mUserId);

        assertWithMessage(
                "Rename should fail without newFile grant from oldName [%s] to newName [%s]",
                oldName, newName)
                .that(oldFile.renameTo(newFile))
                .isFalse();

        // Grant access to newFile and rename should succeed.
        doEscalation(
                MediaStore.createWriteRequest(mContentResolver, Arrays.asList(newUri)));
        assertWithMessage("Rename from oldName [%s] to newName [%s]", oldName, newName)
                .that(oldFile.renameTo(newFile))
                .isTrue();

        // We need to request grant on newUri again, since the rename above caused the URI grant
        // to be revoked.
        doEscalation(
                MediaStore.createWriteRequest(mContentResolver, Arrays.asList(newUri)));
        // Rename back to oldFile for other ops like delete
        assertWithMessage("Rename back from newName [%s] to oldName [%s]", newName, oldName)
                .that(newFile.renameTo(oldFile))
                .isTrue();
    }

    private void assertDeleteFileAPISupport(File file) throws Exception {
        assertThat(file.delete()).isTrue();
    }

    @Test
    public void testMediaEscalation_RequestWrite() throws Exception {
        doMediaEscalation_RequestWrite(true /* allowAccess */,
                false /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalationWithDenied_RequestWrite() throws Exception {
        doMediaEscalation_RequestWrite(false /* allowAccess */,
                false /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalation_RequestWrite_showConfirmDialog() throws Exception {
        doMediaEscalation_RequestWrite(true /* allowAccess */,
                true /* shouldCheckDialogShownValue */, true /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalation_RequestWrite_notShowConfirmDialog() throws Exception {
        doMediaEscalation_RequestWrite(true /* allowAccess */,
                true /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    private void doMediaEscalation_RequestWrite(boolean allowAccess,
            boolean shouldCheckDialogShownValue, boolean isDialogShownExpected) throws Exception {
        doMediaEscalation_RequestWrite(MediaStorageTest::createAudio, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestWrite(MediaStorageTest::createVideo, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestWrite(MediaStorageTest::createImage, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestWrite(MediaStorageTest::createPlaylist, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestWrite(MediaStorageTest::createSubtitle, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
    }

    private void doMediaEscalation_RequestWrite(Callable<Uri> create, boolean allowAccess,
            boolean shouldCheckDialogShownValue, boolean isDialogShownExpected) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
            fail("Expected write access to be blocked");
        } catch (SecurityException expected) {
        }

        if (allowAccess) {
            doEscalation(
                    MediaStore.createWriteRequest(mContentResolver, Arrays.asList(red)),
                    true /* allowAccess */, shouldCheckDialogShownValue, isDialogShownExpected);

            try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
            }
        } else {
            doEscalation(
                    MediaStore.createWriteRequest(mContentResolver, Arrays.asList(red)),
                    false /* allowAccess */, shouldCheckDialogShownValue, isDialogShownExpected);
            try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
                fail("Expected write access to be blocked");
            } catch (SecurityException expected) {
            }
        }
    }

    @Test
    public void testMediaEscalationWithDenied_RequestUnTrash() throws Exception {
        doMediaEscalationWithDenied_RequestUnTrash(MediaStorageTest::createAudio);
        doMediaEscalationWithDenied_RequestUnTrash(MediaStorageTest::createVideo);
        doMediaEscalationWithDenied_RequestUnTrash(MediaStorageTest::createImage);
        doMediaEscalationWithDenied_RequestUnTrash(MediaStorageTest::createPlaylist);
        doMediaEscalationWithDenied_RequestUnTrash(MediaStorageTest::createSubtitle);
    }

    private void doMediaEscalationWithDenied_RequestUnTrash(Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        assertEquals("0", queryForSingleColumn(red, MediaColumns.IS_TRASHED));
        doEscalation(
                MediaStore.createTrashRequest(mContentResolver, Arrays.asList(red), true));
        assertEquals("1", queryForSingleColumn(red, MediaColumns.IS_TRASHED));
        doEscalation(
                MediaStore.createTrashRequest(mContentResolver, Arrays.asList(red), false),
                false /* allowAccess */, false /* shouldCheckDialogShownValue */,
                false /* isDialogShownExpected */);
        assertEquals("1", queryForSingleColumn(red, MediaColumns.IS_TRASHED));
    }

    @Test
    public void testMediaEscalation_RequestTrash() throws Exception {
        doMediaEscalation_RequestTrash(true /* allowAccess */,
                false /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalationWithDenied_RequestTrash() throws Exception {
        doMediaEscalation_RequestTrash(false /* allowAccess */,
                false /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalation_RequestTrash_showConfirmDialog() throws Exception {
        doMediaEscalation_RequestTrash(true /* allowAccess */,
                true /* shouldCheckDialogShownValue */, true /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalation_RequestTrash_notShowConfirmDialog() throws Exception {
        doMediaEscalation_RequestTrash(true /* allowAccess */,
                true /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    private void doMediaEscalation_RequestTrash(boolean allowAccess,
            boolean shouldCheckDialogShownValue, boolean isDialogShownExpected) throws Exception {
        doMediaEscalation_RequestTrash(MediaStorageTest::createAudio, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestTrash(MediaStorageTest::createVideo, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestTrash(MediaStorageTest::createImage, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestTrash(MediaStorageTest::createPlaylist, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestTrash(MediaStorageTest::createSubtitle, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
    }

    private void doMediaEscalation_RequestTrash(Callable<Uri> create, boolean allowAccess,
            boolean shouldCheckDialogShownValue, boolean isDialogShownExpected) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        assertEquals("0", queryForSingleColumn(red, MediaColumns.IS_TRASHED));

        if (allowAccess) {
            doEscalation(
                    MediaStore.createTrashRequest(mContentResolver, Arrays.asList(red), true),
                    true /* allowAccess */, shouldCheckDialogShownValue, isDialogShownExpected);
            assertEquals("1", queryForSingleColumn(red, MediaColumns.IS_TRASHED));
            doEscalation(
                    MediaStore.createTrashRequest(mContentResolver, Arrays.asList(red), false),
                    true /* allowAccess */, shouldCheckDialogShownValue, isDialogShownExpected);
            assertEquals("0", queryForSingleColumn(red, MediaColumns.IS_TRASHED));
        } else {
            doEscalation(
                    MediaStore.createTrashRequest(mContentResolver, Arrays.asList(red), true),
                    false /* allowAccess */, shouldCheckDialogShownValue, isDialogShownExpected);
            assertEquals("0", queryForSingleColumn(red, MediaColumns.IS_TRASHED));
        }
    }

    @Test
    public void testMediaEscalation_RequestFavorite() throws Exception {
        doMediaEscalation_RequestFavorite(MediaStorageTest::createAudio);
        doMediaEscalation_RequestFavorite(MediaStorageTest::createVideo);
        doMediaEscalation_RequestFavorite(MediaStorageTest::createImage);
        doMediaEscalation_RequestFavorite(MediaStorageTest::createPlaylist);
        doMediaEscalation_RequestFavorite(MediaStorageTest::createSubtitle);
    }

    private void doMediaEscalation_RequestFavorite(Callable<Uri> create) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        assertEquals("0", queryForSingleColumn(red, MediaColumns.IS_FAVORITE));
        doEscalation(
                MediaStore.createFavoriteRequest(mContentResolver, Arrays.asList(red), true));
        assertEquals("1", queryForSingleColumn(red, MediaColumns.IS_FAVORITE));
        doEscalation(
                MediaStore.createFavoriteRequest(mContentResolver, Arrays.asList(red), false));
        assertEquals("0", queryForSingleColumn(red, MediaColumns.IS_FAVORITE));
    }

    @Test
    public void testMediaEscalation_RequestDelete() throws Exception {
        doMediaEscalation_RequestDelete(true /* allowAccess */,
                false /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalationWithDenied_RequestDelete() throws Exception {
        doMediaEscalation_RequestDelete(false /* allowAccess */,
                false /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalation_RequestDelete_showConfirmDialog() throws Exception {
        doMediaEscalation_RequestDelete(true /* allowAccess */,
                true /* shouldCheckDialogShownValue */, true /* isDialogShownExpected */);
    }

    @Test
    public void testMediaEscalation_RequestDelete_notShowConfirmDialog() throws Exception {
        doMediaEscalation_RequestDelete(true /* allowAccess */,
                true /* shouldCheckDialogShownValue */, false /* isDialogShownExpected */);
    }

    private void doMediaEscalation_RequestDelete(boolean allowAccess,
            boolean shouldCheckDialogShownValue, boolean isDialogShownExpected) throws Exception {
        doMediaEscalation_RequestDelete(MediaStorageTest::createAudio, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestDelete(MediaStorageTest::createVideo, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestDelete(MediaStorageTest::createImage, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestDelete(MediaStorageTest::createPlaylist, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
        doMediaEscalation_RequestDelete(MediaStorageTest::createSubtitle, allowAccess,
                shouldCheckDialogShownValue, isDialogShownExpected);
    }

    private void doMediaEscalation_RequestDelete(Callable<Uri> create, boolean allowAccess,
            boolean shouldCheckDialogShownValue, boolean isDialogShownExpected) throws Exception {
        final Uri red = create.call();
        clearMediaOwner(red, mUserId);

        try (Cursor c = mContentResolver.query(red, null, null, null)) {
            assertEquals(1, c.getCount());
        }

        if (allowAccess) {
            doEscalation(
                    MediaStore.createDeleteRequest(mContentResolver, Arrays.asList(red)),
                    true /* allowAccess */, shouldCheckDialogShownValue, isDialogShownExpected);
            try (Cursor c = mContentResolver.query(red, null, null, null)) {
                assertEquals(0, c.getCount());
            }
        } else {
            doEscalation(
                    MediaStore.createDeleteRequest(mContentResolver, Arrays.asList(red)),
                    false /* allowAccess */, shouldCheckDialogShownValue, isDialogShownExpected);
            try (Cursor c = mContentResolver.query(red, null, null, null)) {
                assertEquals(1, c.getCount());
            }
        }
    }

    private static Uri createDownload() throws IOException {
        final String content = "<html><body>Content</body></html>";
        final String displayName = "cts" + System.nanoTime();
        final String mimeType = "text/html";
        final Context context = InstrumentationRegistry.getTargetContext();
        final PendingParams params = new PendingParams(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, displayName, mimeType);

        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        assertNotNull(pendingUri);
        try (PendingSession session = MediaStoreUtils.openPending(context, pendingUri)) {
            try (PrintWriter pw = new PrintWriter(session.openOutputStream())) {
                pw.print(content);
            }
            try (OutputStream out = session.openOutputStream()) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return session.publish();
        }
    }

    private static Uri createFile() throws IOException {
        return createSubtitle();
    }

    private static Uri createAudio() throws IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, displayName, "audio/mpeg");
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);

        try (PendingSession session = MediaStoreUtils.openPending(context, pendingUri)) {
            try (InputStream in = context.getResources().getAssets().open("testmp3.mp3");
                 OutputStream out = session.openOutputStream()) {
                FileUtils.copy(in, out);
            }
            return session.publish();
        }
    }

    private static Uri createVideo() throws IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, displayName, "video/mpeg");
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (PendingSession session = MediaStoreUtils.openPending(context, pendingUri)) {
            try (InputStream in = context.getResources().getAssets().open("testmp3.mp3");
                 OutputStream out = session.openOutputStream()) {
                FileUtils.copy(in, out);
            }
            return session.publish();
        }
    }

    private static Uri createImage() throws IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, displayName, "image/png");
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (PendingSession session = MediaStoreUtils.openPending(context, pendingUri)) {
            try (OutputStream out = session.openOutputStream()) {
                final Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            }
            return session.publish();
        }
    }

    private static Uri createPlaylist() throws IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, displayName, "audio/mpegurl");
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (PendingSession session = MediaStoreUtils.openPending(context, pendingUri)) {
            return session.publish();
        }
    }

    private static Uri createSubtitle() throws IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String displayName = "cts" + System.nanoTime();
        final PendingParams params = new PendingParams(
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL), displayName,
                "application/x-subrip");
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (PendingSession session = MediaStoreUtils.openPending(context, pendingUri)) {
            try (InputStream in = context.getResources().getAssets().open("testmp3.mp3");
                 OutputStream out = session.openOutputStream()) {
                FileUtils.copy(in, out);
            }
            return session.publish();
        }
    }

    private static String queryForSingleColumn(Uri uri, String column) throws Exception {
        final ContentResolver resolver = InstrumentationRegistry.getTargetContext()
                .getContentResolver();
        try (Cursor c = resolver.query(uri, new String[]{column}, null, null)) {
            assertEquals(1, c.getCount());
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }

    private static void clearOwnFiles(Uri uri) throws Exception {
        final ContentResolver resolver = InstrumentationRegistry.getTargetContext()
                .getContentResolver();
        try (Cursor c = resolver.query(uri, new String[]{MediaColumns._ID}, null, null)) {
            while (c.moveToNext()) {
                final long id = c.getLong(0);
                final Uri contentUri = ContentUris.withAppendedId(uri, id);
                resolver.delete(contentUri, null);
            }
        }
    }

    private static void clearMediaOwner(Uri uri, int userId) throws IOException {
        final String cmd = String.format(
                "content update --uri %s --user %d --bind owner_package_name:n:",
                uri, userId);
        runShellCommand(InstrumentationRegistry.getInstrumentation(), cmd);
    }

    static File stageFile(File file) throws Exception {
        // Sometimes file creation fails due to slow permission update, try more times
        while (currentAttempt < MAX_NUMBER_OF_ATTEMPT) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                return file;
            } catch (IOException e) {
                currentAttempt++;
                // wait 500ms
                Thread.sleep(500);
            }
        }
        throw new TimeoutException("File creation failed due to slow permission update");
    }

}
