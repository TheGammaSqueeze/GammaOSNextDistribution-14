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

package android.scopedstorage.cts.device;

import static android.scopedstorage.cts.device.OtherAppFilesRule.GrantModifications.GRANT;
import static android.scopedstorage.cts.device.OwnedAndOtherFilesRule.getResultForFilesQuery;
import static android.scopedstorage.cts.device.OwnedFilesRule.RESOURCE_ID_WITH_METADATA;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertFileAccess_listFiles;
import static android.scopedstorage.cts.lib.FilePathAccessTestUtils.assertFileAccess_readWrite;
import static android.scopedstorage.cts.lib.RedactionTestHelper.assertExifMetadataMatch;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromFile;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadataFromRawResource;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_canReadThumbnail;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_listFiles;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_readWrite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriDoesNotExist;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriIsFavorite;
import static android.scopedstorage.cts.lib.ResolverAccessTestUtils.assertResolver_uriIsNotFavorite;
import static android.scopedstorage.cts.lib.TestUtils.assertFileContent;
import static android.scopedstorage.cts.lib.TestUtils.doEscalation;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.ResolverAccessTestUtils;
import android.system.Os;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public class StorageOwnedFilesTest {

    private static final String TAG = "StorageOwnedFilesTest";
    private static final ContentResolver sContentResolver = getContentResolver();
    @ClassRule
    public static final OwnedFilesRule sFilesRule = new OwnedFilesRule(sContentResolver);

    private static final String STR_DATA1 = "Random dest data";

    private static final byte[] BYTES_DATA1 = STR_DATA1.getBytes();
    private static final File IMAGE_FILE_1 = sFilesRule.getImageFile1();
    private static final File IMAGE_FILE_2_METADATA = sFilesRule.getImageFile2Metadata();
    private static final File VIDEO_FILE_1 = sFilesRule.getVideoFile1();

    // Cannot be static as the underlying resource isn't
    private final Uri mImageUri1 = sFilesRule.getImageUri1();
    private final Uri mImageUri2 = sFilesRule.getImageUri2_Metadata();
    private final Uri mVideoUri1 = sFilesRule.getVideoUri1();

    @BeforeClass
    public static void init() throws Exception {
        pollForPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, true);
    }

    @Test
    public void owned_listOwnership() throws Exception {
        Set<File> expectedValues = Set.of(IMAGE_FILE_1, IMAGE_FILE_2_METADATA, VIDEO_FILE_1);
        Set<File> notExpected = Collections.emptySet();
        // File access
        assertFileAccess_listFiles(IMAGE_FILE_1.getParentFile(), expectedValues, notExpected);
        // Query DCIM
        assertResolver_listFiles(Environment.DIRECTORY_DCIM, expectedValues, notExpected,
                sContentResolver);
    }

    @Test
    public void owned_canRead() throws Exception {
        assertResolver_readWrite(mImageUri1, sContentResolver);
        assertResolver_readWrite(mImageUri2, sContentResolver);
        assertResolver_readWrite(mVideoUri1, sContentResolver);
        assertFileAccess_readWrite(IMAGE_FILE_1);
        assertFileAccess_readWrite(IMAGE_FILE_2_METADATA);
        assertFileAccess_readWrite(VIDEO_FILE_1);
    }

    @Test
    public void owned_canReadThumbnail() throws Exception {
        assertResolver_canReadThumbnail(mImageUri1, sContentResolver);
        assertResolver_canReadThumbnail(mImageUri2, sContentResolver);
        // TODO Need a valid video to create a thumbnail
        // assertResolver_canReadThumbnail(mVideoUri1, sContentResolver);
    }

    @Test
    public void owned_createFavoriteRequest() throws Exception {
        doEscalation(MediaStore.createFavoriteRequest(sContentResolver, Arrays.asList(mImageUri1,
                        mImageUri2, mVideoUri1),
                true));
        assertResolver_uriIsFavorite(mImageUri1, sContentResolver);
        assertResolver_uriIsFavorite(mImageUri2, sContentResolver);
        assertResolver_uriIsFavorite(mVideoUri1, sContentResolver);
        doEscalation(MediaStore.createFavoriteRequest(sContentResolver, Arrays.asList(mImageUri1,
                        mImageUri2, mVideoUri1),
                false));
        assertResolver_uriIsNotFavorite(mImageUri1, sContentResolver);
        assertResolver_uriIsNotFavorite(mImageUri2, sContentResolver);
        assertResolver_uriIsNotFavorite(mVideoUri1, sContentResolver);

    }

    @Test
    public void owned_deleteRequest() throws Exception {
        File fileToBeDeleted1 = new File(getDcimDir(), TAG + "_delete_1.jpg");
        File fileToBeDeleted2 = new File(getDcimDir(), TAG + "_delete_2.mp4");
        try {
            Uri uriToBeDeleted1 = sFilesRule.createFile(fileToBeDeleted1);
            Uri uriToBeDeleted2 = sFilesRule.createFile(fileToBeDeleted2);
            Log.e(TAG, "alea " + uriToBeDeleted1 + " " + uriToBeDeleted2);
            doEscalation(
                    MediaStore.createDeleteRequest(sContentResolver,
                            Arrays.asList(uriToBeDeleted1, uriToBeDeleted2)));
            assertResolver_uriDoesNotExist(uriToBeDeleted1, sContentResolver);
            assertResolver_uriDoesNotExist(uriToBeDeleted2, sContentResolver);
        } finally {
            fileToBeDeleted1.delete();
            fileToBeDeleted2.delete();
        }
    }

    @Test
    public void owned_insertRequest() throws Exception {
        assertInsertFile(TAG + "_insert_1.jpg",
                ResolverAccessTestUtils::assertResolver_insertImage);
        assertInsertFile(TAG + "_insert_2.mp4",
                ResolverAccessTestUtils::assertResolver_insertVideo);
    }

    @Test
    public void owned_grantOwnedItemBySelection() throws IOException {
        // A granted item should be returned when recent_selection is requested even though the item
        // is an owned item.
        OtherAppFilesRule.modifyReadAccess(IMAGE_FILE_1, ApplicationProvider.getApplicationContext()
                .getPackageName(), GRANT);
        Bundle queryArgs = new Bundle();
        queryArgs.putBoolean(MediaStore.QUERY_ARG_LATEST_SELECTION_ONLY, true);
        try (Cursor c = getResultForFilesQuery(sContentResolver, queryArgs)) {
            assertThat(c).isNotNull();
            // Now only recently selected item should be returned.
            assertWithMessage("Expected number of items(only recently selected) is 1.")
                    .that(c.getCount()).isEqualTo(1);
            final Uri expectedMediaUri = MediaStore.scanFile(sContentResolver,
                    IMAGE_FILE_1);
            c.moveToFirst();
            assertWithMessage("Expected item Uri was: " + expectedMediaUri).that(
                    c.getInt(c.getColumnIndex(
                            MediaStore.Files.FileColumns._ID))).isEqualTo(
                    ContentUris.parseId(expectedMediaUri));
        }
    }

    private void assertInsertFile(String filename,
            TriFunction<String, String, ContentResolver, Uri> inserter) throws Exception {
        File fileToBeInserted = new File(getDcimDir(), filename);
        final Uri targetUri = inserter.apply(fileToBeInserted.getName(),
                Environment.DIRECTORY_DCIM, sContentResolver);
        try (ParcelFileDescriptor parcelFileDescriptor = sContentResolver.openFileDescriptor(
                targetUri, "rw")) {
            assertThat(parcelFileDescriptor).isNotNull();
            Os.write(parcelFileDescriptor.getFileDescriptor(), ByteBuffer.wrap(BYTES_DATA1));
            assertFileContent(parcelFileDescriptor.getFileDescriptor(), BYTES_DATA1);
        } finally {
            fileToBeInserted.delete();
        }
    }

    @FunctionalInterface
    interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @Test
    public void owned_accessLocationMetadata() throws Exception {
        HashMap<String, String> originalExif = getExifMetadataFromRawResource(
                RESOURCE_ID_WITH_METADATA);

        HashMap<String, String> exif = getExifMetadataFromFile(IMAGE_FILE_2_METADATA);
        assertExifMetadataMatch(exif, originalExif);
        //TODO do we have videos with metadata?
    }
}
