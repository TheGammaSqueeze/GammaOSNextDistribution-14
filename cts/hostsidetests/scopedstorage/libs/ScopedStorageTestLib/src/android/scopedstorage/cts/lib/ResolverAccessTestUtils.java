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

package android.scopedstorage.cts.lib;

import static android.provider.MediaStore.VOLUME_EXTERNAL;
import static android.scopedstorage.cts.lib.TestUtils.getImageContentUri;
import static android.scopedstorage.cts.lib.TestUtils.getVideoContentUri;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Size;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to test media access using the Content Resolver API.
 *
 * Its twin class {@link FilePathAccessTestUtils } covers testing using the file system.
 */
public class ResolverAccessTestUtils {
    // Similar logic also found in ResultsAssertionUtils
    public static void assertResolver_readWrite(Uri uri, ContentResolver resolver)
            throws Exception {
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "rw")) {
            assertThat(pfd).isNotNull();
        }
    }

    public static void assertResolver_noReadNoWrite(Uri uri, ContentResolver resolver)
            throws Exception {
        assertResolver_noRead(uri, resolver);
        assertResolver_noWrite(uri, resolver);
    }

    public static void assertResolver_noRead(Uri uri, ContentResolver resolver) throws Exception {
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            fail("Should not grant read access to uri " + uri.toString());
        } catch (SecurityException expected) {
            // Success
        }
    }

    public static void assertResolver_noWrite(Uri uri, ContentResolver resolver) throws Exception {
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Should not grant write access to uri " + uri.toString());
        } catch (SecurityException expected) {
            // Success
        }
    }

    public static void assertResolver_readOnly(Uri uri, ContentResolver resolver) throws Exception {
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            assertThat(pfd).isNotNull();
        }
        assertResolver_noWrite(uri, resolver);
    }

    public static void assertResolver_canReadThumbnail(Uri uri, ContentResolver resolver)
            throws Exception {
        assertThat(resolver.loadThumbnail(uri, new Size(32, 32), null)).isNotNull();
    }

    public static void assertResolver_cannotReadThumbnail(Uri uri, ContentResolver resolver)
            throws Exception {
        assertThat(resolver.loadThumbnail(uri, new Size(32, 32), null)).isNull();
    }

    /**
     * Checks that specific files are visible and others are not in a given folder
     */
    public static void assertResolver_listFiles(String dir, Set<File> expected,
            Set<File> notExpected, ContentResolver resolver) {
        final String[] projection = new String[]{
                MediaStore.Files.FileColumns.DISPLAY_NAME};
        try (Cursor cursor = resolver.query(MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                projection,
                MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ?", new String[]{
                        dir + "%"}, null)) {
            assertThat(collectFromCursor(cursor)).containsAtLeastElementsIn(
                    expected.stream().map(File::getName).collect(
                            Collectors.toSet()));
            assertThat(collectFromCursor(cursor)).doesNotContain(
                    notExpected.stream().map(File::getName).collect(
                            Collectors.toSet()));
        }
    }

    private static Set<String> collectFromCursor(Cursor cursor) {
        Set<String> result = new HashSet<>();
        boolean hasMore = cursor.moveToFirst();
        while (hasMore) {
            result.add(cursor.getString(0));
            hasMore = cursor.moveToNext();
        }
        return result;
    }

    public static void assertResolver_uriIsFavorite(Uri uri, ContentResolver resolver) {
        assertThat(getFavorite(uri, resolver)).isEqualTo("1");
    }

    public static void assertResolver_uriIsNotFavorite(Uri uri, ContentResolver resolver) {
        assertThat(getFavorite(uri, resolver)).isEqualTo("0");
    }

    private static String getFavorite(Uri uri, ContentResolver resolver) {
        try (Cursor c = resolver.query(uri, new String[]{MediaStore.MediaColumns.IS_FAVORITE},
                null, null)) {
            assertThat(c.getCount()).isEqualTo(1);
            assertThat(c.moveToFirst()).isTrue();
            return c.getString(0);
        }
    }

    public static void assertResolver_uriDoesNotExist(Uri uri, ContentResolver resolver) {
        try (Cursor c = resolver.query(uri, null,
                null, null)) {
            assertThat(c.getCount()).isEqualTo(0);
        }
    }

    public static Uri assertResolver_insert(String file, String folder, Uri tableUri,
            ContentResolver contentResolver) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file);
        Uri uri = contentResolver.insert(tableUri, values, null);
        assertThat(uri).isNotNull();
        return uri;
    }

    public static Uri assertResolver_insertImage(String file, String folder,
            ContentResolver contentResolver) {
        return assertResolver_insert(file, folder, getImageContentUri(), contentResolver);
    }

    public static Uri assertResolver_insertVideo(String file, String folder,
            ContentResolver contentResolver) {
        return assertResolver_insert(file, folder, getVideoContentUri(), contentResolver);
    }
}
