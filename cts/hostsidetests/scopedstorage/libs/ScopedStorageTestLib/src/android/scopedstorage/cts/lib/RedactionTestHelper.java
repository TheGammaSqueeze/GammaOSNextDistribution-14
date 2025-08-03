/**
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

package android.scopedstorage.cts.lib;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.junit.Assert.fail;

import android.media.ExifInterface;
import android.net.Uri;
import android.os.FileUtils;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Objects;

/**
 * Helper functions and utils for redactions tests
 */
public class RedactionTestHelper {
    private static final String TAG = "RedactionTestHelper";

    private static final String[] EXIF_GPS_TAGS = {
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_VERSION_ID,
    };

    public static final String EXIF_METADATA_QUERY = "android.scopedstorage.cts.exif";

    /**
     * Retrieve the EXIF metadata from the given file.
     */
    @NonNull
    public static HashMap<String, String> getExifMetadataFromFile(@NonNull File file)
            throws IOException {
        final ExifInterface exif = new ExifInterface(file);
        return dumpExifGpsTagsToMap(exif);
    }

    /**
     * Retrieve the EXIF metadata from the given uri.
     */
    @NonNull
    private static HashMap<String, String> getExifMetadataFromUri(@NonNull Uri uri)
            throws IOException {
        try (InputStream is = getContext().getContentResolver().openInputStream(uri)) {
            final ExifInterface exif = new ExifInterface(is);
            return dumpExifGpsTagsToMap(exif);
        }
    }


    /**
     * Retrieve the EXIF metadata from the given resource.
     */
    @NonNull
    public static HashMap<String, String> getExifMetadataFromRawResource(@RawRes int resId)
            throws IOException {
        final ExifInterface exif;
        try (InputStream in = getContext().getResources().openRawResource(resId)) {
            exif = new ExifInterface(in);
        }
        return dumpExifGpsTagsToMap(exif);
    }

    /**
     * Asserts the 2 given EXIF maps have the same content.
     */
    public static void assertExifMetadataMatch(
            @NonNull HashMap<String, String> actual, @NonNull HashMap<String, String> expected) {
        for (String tag : EXIF_GPS_TAGS) {
            assertMetadataEntryMatch(tag, actual.get(tag), expected.get(tag));
        }
    }

    /**
     * Asserts the 2 given EXIF maps don't have the same content.
     */
    public static void assertExifMetadataMismatch(
            @NonNull HashMap<String, String> actual, @NonNull HashMap<String, String> expected) {
        for (String tag : EXIF_GPS_TAGS) {
            assertMetadataEntryMismatch(tag, actual.get(tag), expected.get(tag));
        }
    }

    private static void assertMetadataEntryMatch(String tag, String actual, String expected) {
        if (!Objects.equals(actual, expected)) {
            fail("Unexpected metadata mismatch for tag: " + tag + "\n"
                    + "expected:" + expected + "\n"
                    + "but was: " + actual);
        }
    }

    private static void assertMetadataEntryMismatch(String tag, String actual, String expected) {
        if (Objects.equals(actual, expected)) {
            fail("Unexpected metadata match for tag: " + tag + "\n"
                    + "expected not to be:" + expected);
        }
    }

    private static HashMap<String, String> dumpExifGpsTagsToMap(ExifInterface exif) {
        final HashMap<String, String> res = new HashMap<>();
        for (String tag : EXIF_GPS_TAGS) {
            res.put(tag, exif.getAttribute(tag));
        }
        return res;
    }

    public static void assertConsistentNonRedactedAccess(File file, int metadataResId)
            throws Exception {
        // Write some meta-data to the file to assert on redacted information access
        try (InputStream in =
                     getContext().getResources().openRawResource(metadataResId);
             FileOutputStream out = new FileOutputStream(file)) {
            FileUtils.copy(in, out);
            out.getFD().sync();
        }

        HashMap<String, String> originalExif = getExifMetadataFromRawResource(metadataResId);

        // Using File API
        HashMap<String, String> exifFromFilePath = getExifMetadataFromFile(file);
        assertExifMetadataMatch(exifFromFilePath, originalExif);

        Uri uri = MediaStore.scanFile(getContext().getContentResolver(), file);
        // Using ContentResolver API
        HashMap<String, String> exifFromContentResolver = getExifMetadataFromUri(uri);
        assertExifMetadataMatch(exifFromContentResolver, originalExif);
    }
}
