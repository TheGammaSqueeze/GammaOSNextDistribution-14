/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.media.misc.cts;

import static android.media.ExifInterface.TAG_SUBJECT_AREA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.FileUtils;
import android.os.StrictMode;
import android.platform.test.annotations.AppModeFull;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;

import libcore.io.IoUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@NonMainlineTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(AndroidJUnit4.class)
public class ExifInterfaceTest {
    private static final String TAG = ExifInterface.class.getSimpleName();
    private static final boolean VERBOSE = false;  // lots of logging

    private static final double DIFFERENCE_TOLERANCE = .001;

    static final String mInpPrefix = WorkDir.getMediaDirString() + "images/";

    // This base directory is needed for the files listed below.
    // These files will be available for download in Android O release.
    // Link: https://source.android.com/compatibility/cts/downloads.html#cts-media-files
    private static final String JPEG_WITH_EXIF_BYTE_ORDER_II = "image_exif_byte_order_ii.jpg";
    private static final String JPEG_WITH_EXIF_BYTE_ORDER_MM = "image_exif_byte_order_mm.jpg";
    private static final String DNG_WITH_EXIF_WITH_XMP = "lg_g4_iso_800.dng";
    private static final String JPEG_WITH_EXIF_WITH_XMP = "lg_g4_iso_800.jpg";
    private static final String ARW_SONY_RX_100 = "sony_rx_100.arw";
    private static final String CR2_CANON_G7X = "canon_g7x.cr2";
    private static final String RAF_FUJI_X20 = "fuji_x20.raf";
    private static final String NEF_NIKON_1AW1 = "nikon_1aw1.nef";
    private static final String NRW_NIKON_P330 = "nikon_p330.nrw";
    private static final String ORF_OLYMPUS_E_PL3 = "olympus_e_pl3.orf";
    private static final String RW2_PANASONIC_GM5 = "panasonic_gm5.rw2";
    private static final String PEF_PENTAX_K5 = "pentax_k5.pef";
    private static final String SRW_SAMSUNG_NX3000 = "samsung_nx3000.srw";
    private static final String JPEG_VOLANTIS = "volantis.jpg";
    private static final String WEBP_WITH_EXIF = "webp_with_exif.webp";
    private static final String WEBP_WITHOUT_EXIF_WITH_ANIM_DATA =
            "webp_with_anim_without_exif.webp";
    private static final String WEBP_WITHOUT_EXIF = "webp_without_exif.webp";
    private static final String WEBP_WITHOUT_EXIF_WITH_LOSSLESS_ENCODING =
            "webp_lossless_without_exif.webp";
    private static final String PNG_WITH_EXIF_BYTE_ORDER_II = "png_with_exif_byte_order_ii.png";
    private static final String PNG_WITHOUT_EXIF = "png_without_exif.png";
    private static final String JPEG_WITH_DATETIME_TAG = "jpeg_with_datetime_tag.jpg";

    private static final String[] EXIF_TAGS = {
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_WHITE_BALANCE
    };

    private static class ExpectedValue {
        // Thumbnail information.
        public final boolean hasThumbnail;
        public final int thumbnailWidth;
        public final int thumbnailHeight;
        public final boolean isThumbnailCompressed;
        public final int thumbnailOffset;
        public final int thumbnailLength;

        // GPS information.
        public final boolean hasLatLong;
        public final float latitude;
        public final int latitudeOffset;
        public final int latitudeLength;
        public final float longitude;
        public final float altitude;

        // Make information
        public final boolean hasMake;
        public final int makeOffset;
        public final int makeLength;
        public final String make;

        // Values.
        public final String model;
        public final float aperture;
        public final String dateTimeOriginal;
        public final float exposureTime;
        public final float flash;
        public final String focalLength;
        public final String gpsAltitude;
        public final String gpsAltitudeRef;
        public final String gpsDatestamp;
        public final String gpsLatitude;
        public final String gpsLatitudeRef;
        public final String gpsLongitude;
        public final String gpsLongitudeRef;
        public final String gpsProcessingMethod;
        public final String gpsTimestamp;
        public final int imageLength;
        public final int imageWidth;
        public final String iso;
        public final int orientation;
        public final int whiteBalance;

        // XMP information.
        public final boolean hasXmp;
        public final int xmpOffset;
        public final int xmpLength;

        private static String getString(TypedArray typedArray, int index) {
            String stringValue = typedArray.getString(index);
            if (stringValue == null || stringValue.equals("")) {
                return null;
            }
            return stringValue.trim();
        }

        public ExpectedValue(TypedArray typedArray) {
            int index = 0;

            // Reads thumbnail information.
            hasThumbnail = typedArray.getBoolean(index++, false);
            thumbnailOffset = typedArray.getInt(index++, -1);
            thumbnailLength = typedArray.getInt(index++, -1);
            thumbnailWidth = typedArray.getInt(index++, 0);
            thumbnailHeight = typedArray.getInt(index++, 0);
            isThumbnailCompressed = typedArray.getBoolean(index++, false);

            // Reads GPS information.
            hasLatLong = typedArray.getBoolean(index++, false);
            latitudeOffset = typedArray.getInt(index++, -1);
            latitudeLength = typedArray.getInt(index++, -1);
            latitude = typedArray.getFloat(index++, 0f);
            longitude = typedArray.getFloat(index++, 0f);
            altitude = typedArray.getFloat(index++, 0f);

            // Reads Make information.
            hasMake = typedArray.getBoolean(index++, false);
            makeOffset = typedArray.getInt(index++, -1);
            makeLength = typedArray.getInt(index++, -1);
            make = getString(typedArray, index++);

            // Reads values.
            model = getString(typedArray, index++);
            aperture = typedArray.getFloat(index++, 0f);
            dateTimeOriginal = getString(typedArray, index++);
            exposureTime = typedArray.getFloat(index++, 0f);
            flash = typedArray.getFloat(index++, 0f);
            focalLength = getString(typedArray, index++);
            gpsAltitude = getString(typedArray, index++);
            gpsAltitudeRef = getString(typedArray, index++);
            gpsDatestamp = getString(typedArray, index++);
            gpsLatitude = getString(typedArray, index++);
            gpsLatitudeRef = getString(typedArray, index++);
            gpsLongitude = getString(typedArray, index++);
            gpsLongitudeRef = getString(typedArray, index++);
            gpsProcessingMethod = getString(typedArray, index++);
            gpsTimestamp = getString(typedArray, index++);
            imageLength = typedArray.getInt(index++, 0);
            imageWidth = typedArray.getInt(index++, 0);
            iso = getString(typedArray, index++);
            orientation = typedArray.getInt(index++, 0);
            whiteBalance = typedArray.getInt(index++, 0);

            // Reads XMP information.
            hasXmp = typedArray.getBoolean(index++, false);
            xmpOffset = typedArray.getInt(index++, 0);
            xmpLength = typedArray.getInt(index++, 0);

            typedArray.recycle();
        }
    }

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void printExifTagsAndValues(String fileName, ExifInterface exifInterface) {
        // Prints thumbnail information.
        if (exifInterface.hasThumbnail()) {
            byte[] thumbnailBytes = exifInterface.getThumbnailBytes();
            if (thumbnailBytes != null) {
                Log.v(TAG, fileName + " Thumbnail size = " + thumbnailBytes.length);
                Bitmap bitmap = exifInterface.getThumbnailBitmap();
                if (bitmap == null) {
                    Log.e(TAG, fileName + " Corrupted thumbnail!");
                } else {
                    Log.v(TAG, fileName + " Thumbnail size: " + bitmap.getWidth() + ", "
                            + bitmap.getHeight());
                }
            } else {
                Log.e(TAG, fileName + " Unexpected result: No thumbnails were found. "
                        + "A thumbnail is expected.");
            }
        } else {
            if (exifInterface.getThumbnailBytes() != null) {
                Log.e(TAG, fileName + " Unexpected result: A thumbnail was found. "
                        + "No thumbnail is expected.");
            } else {
                Log.v(TAG, fileName + " No thumbnail");
            }
        }

        // Prints GPS information.
        Log.v(TAG, fileName + " Altitude = " + exifInterface.getAltitude(.0));

        float[] latLong = new float[2];
        if (exifInterface.getLatLong(latLong)) {
            Log.v(TAG, fileName + " Latitude = " + latLong[0]);
            Log.v(TAG, fileName + " Longitude = " + latLong[1]);
        } else {
            Log.v(TAG, fileName + " No latlong data");
        }

        // Prints values.
        for (String tagKey : EXIF_TAGS) {
            String tagValue = exifInterface.getAttribute(tagKey);
            Log.v(TAG, fileName + " Key{" + tagKey + "} = '" + tagValue + "'");
        }
    }

    private void assertIntTag(ExifInterface exifInterface, String tag, int expectedValue) {
        int intValue = exifInterface.getAttributeInt(tag, 0);
        assertEquals(expectedValue, intValue);
    }

    private void assertFloatTag(ExifInterface exifInterface, String tag, float expectedValue) {
        double doubleValue = exifInterface.getAttributeDouble(tag, 0.0);
        assertEquals(expectedValue, doubleValue, DIFFERENCE_TOLERANCE);
    }

    private void assertStringTag(ExifInterface exifInterface, String tag, String expectedValue) {
        String stringValue = exifInterface.getAttribute(tag);
        if (stringValue != null) {
            stringValue = stringValue.trim();
        }
        stringValue = (stringValue == "") ? null : stringValue;

        assertEquals(expectedValue, stringValue);
    }

    private void compareWithExpectedValue(ExifInterface exifInterface,
            ExpectedValue expectedValue, String verboseTag, boolean assertRanges) {
        if (VERBOSE) {
            printExifTagsAndValues(verboseTag, exifInterface);
        }
        // Checks a thumbnail image.
        assertEquals(expectedValue.hasThumbnail, exifInterface.hasThumbnail());
        if (expectedValue.hasThumbnail) {
            assertNotNull(exifInterface.getThumbnailRange());
            if (assertRanges) {
                final long[] thumbnailRange = exifInterface.getThumbnailRange();
                assertEquals(expectedValue.thumbnailOffset, thumbnailRange[0]);
                assertEquals(expectedValue.thumbnailLength, thumbnailRange[1]);
            }
            testThumbnail(expectedValue, exifInterface);
        } else {
            assertNull(exifInterface.getThumbnailRange());
            assertNull(exifInterface.getThumbnail());
            assertNull(exifInterface.getThumbnailBitmap());
            assertFalse(exifInterface.isThumbnailCompressed());
        }

        // Checks GPS information.
        float[] latLong = new float[2];
        assertEquals(expectedValue.hasLatLong, exifInterface.getLatLong(latLong));
        if (expectedValue.hasLatLong) {
            assertNotNull(exifInterface.getAttributeRange(ExifInterface.TAG_GPS_LATITUDE));
            if (assertRanges) {
                final long[] latitudeRange = exifInterface
                        .getAttributeRange(ExifInterface.TAG_GPS_LATITUDE);
                assertEquals(expectedValue.latitudeOffset, latitudeRange[0]);
                assertEquals(expectedValue.latitudeLength, latitudeRange[1]);
            }
            assertEquals(expectedValue.latitude, latLong[0], DIFFERENCE_TOLERANCE);
            assertEquals(expectedValue.longitude, latLong[1], DIFFERENCE_TOLERANCE);
            assertTrue(exifInterface.hasAttribute(ExifInterface.TAG_GPS_LATITUDE));
            assertTrue(exifInterface.hasAttribute(ExifInterface.TAG_GPS_LONGITUDE));
        } else {
            assertNull(exifInterface.getAttributeRange(ExifInterface.TAG_GPS_LATITUDE));
            assertFalse(exifInterface.hasAttribute(ExifInterface.TAG_GPS_LATITUDE));
            assertFalse(exifInterface.hasAttribute(ExifInterface.TAG_GPS_LONGITUDE));
        }
        assertEquals(expectedValue.altitude, exifInterface.getAltitude(.0), DIFFERENCE_TOLERANCE);

        // Checks Make information.
        String make = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
        assertEquals(expectedValue.hasMake, make != null);
        if (expectedValue.hasMake) {
            assertNotNull(exifInterface.getAttributeRange(ExifInterface.TAG_MAKE));
            if (assertRanges) {
                final long[] makeRange = exifInterface
                        .getAttributeRange(ExifInterface.TAG_MAKE);
                assertEquals(expectedValue.makeOffset, makeRange[0]);
                assertEquals(expectedValue.makeLength, makeRange[1]);
            }
            assertEquals(expectedValue.make, make.trim());
        } else {
            assertNull(exifInterface.getAttributeRange(ExifInterface.TAG_MAKE));
            assertFalse(exifInterface.hasAttribute(ExifInterface.TAG_MAKE));
        }

        // Checks values.
        assertStringTag(exifInterface, ExifInterface.TAG_MAKE, expectedValue.make);
        assertStringTag(exifInterface, ExifInterface.TAG_MODEL, expectedValue.model);
        assertFloatTag(exifInterface, ExifInterface.TAG_F_NUMBER, expectedValue.aperture);
        assertStringTag(exifInterface, ExifInterface.TAG_DATETIME_ORIGINAL,
                expectedValue.dateTimeOriginal);
        assertFloatTag(exifInterface, ExifInterface.TAG_EXPOSURE_TIME, expectedValue.exposureTime);
        assertFloatTag(exifInterface, ExifInterface.TAG_FLASH, expectedValue.flash);
        assertStringTag(exifInterface, ExifInterface.TAG_FOCAL_LENGTH, expectedValue.focalLength);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_ALTITUDE, expectedValue.gpsAltitude);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_ALTITUDE_REF,
                expectedValue.gpsAltitudeRef);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_DATESTAMP, expectedValue.gpsDatestamp);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LATITUDE, expectedValue.gpsLatitude);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LATITUDE_REF,
                expectedValue.gpsLatitudeRef);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LONGITUDE, expectedValue.gpsLongitude);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LONGITUDE_REF,
                expectedValue.gpsLongitudeRef);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_PROCESSING_METHOD,
                expectedValue.gpsProcessingMethod);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_TIMESTAMP, expectedValue.gpsTimestamp);
        assertIntTag(exifInterface, ExifInterface.TAG_IMAGE_LENGTH, expectedValue.imageLength);
        assertIntTag(exifInterface, ExifInterface.TAG_IMAGE_WIDTH, expectedValue.imageWidth);
        assertStringTag(exifInterface, ExifInterface.TAG_ISO_SPEED_RATINGS, expectedValue.iso);
        assertIntTag(exifInterface, ExifInterface.TAG_ORIENTATION, expectedValue.orientation);
        assertIntTag(exifInterface, ExifInterface.TAG_WHITE_BALANCE, expectedValue.whiteBalance);

        if (expectedValue.hasXmp) {
            assertNotNull(exifInterface.getAttributeRange(ExifInterface.TAG_XMP));
            if (assertRanges) {
                final long[] xmpRange = exifInterface.getAttributeRange(ExifInterface.TAG_XMP);
                assertEquals(expectedValue.xmpOffset, xmpRange[0]);
                assertEquals(expectedValue.xmpLength, xmpRange[1]);
            }
            final String xmp = new String(exifInterface.getAttributeBytes(ExifInterface.TAG_XMP),
                    StandardCharsets.UTF_8);
            // We're only interested in confirming that we were able to extract
            // valid XMP data, which must always include this XML tag; a full
            // XMP parser is beyond the scope of ExifInterface. See XMP
            // Specification Part 1, Section C.2.2 for additional details.
            if (!xmp.contains("<rdf:RDF")) {
                fail("Invalid XMP: " + xmp);
            }
        } else {
            assertNull(exifInterface.getAttributeRange(ExifInterface.TAG_XMP));
        }
    }

    private void readFromStandaloneDataWithExif(String fileName, int typedArrayResourceId)
            throws IOException {
        ExpectedValue expectedValue = new ExpectedValue(
                getContext().getResources().obtainTypedArray(typedArrayResourceId));

        Preconditions.assertTestFileExists(mInpPrefix + fileName);
        File imageFile = new File(mInpPrefix, fileName);
        String verboseTag = imageFile.getName();

        FileInputStream fis = new FileInputStream(imageFile);
        // Skip the following marker bytes (0xff, 0xd8, 0xff, 0xe1)
        fis.skip(4);
        // Read the value of the length of the exif data
        short length = readShort(fis);
        byte[] exifBytes = new byte[length];
        fis.read(exifBytes);

        ByteArrayInputStream bin = new ByteArrayInputStream(exifBytes);
        ExifInterface exifInterface =
                new ExifInterface(bin, ExifInterface.STREAM_TYPE_EXIF_DATA_ONLY);
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag, true);
    }

    private void testExifInterfaceCommon(String fileName, ExpectedValue expectedValue)
            throws IOException {
        File imageFile = new File(mInpPrefix, fileName);
        Preconditions.assertTestFileExists(mInpPrefix + fileName);
        String verboseTag = imageFile.getName();

        // Creates via path.
        ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        assertNotNull(exifInterface);
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag, true);

        // Creates via file.
        exifInterface = new ExifInterface(imageFile);
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag, true);

        InputStream in = null;
        // Creates via InputStream.
        try {
            in = new BufferedInputStream(new FileInputStream(imageFile.getAbsolutePath()));
            exifInterface = new ExifInterface(in);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag, true);
        } finally {
            IoUtils.closeQuietly(in);
        }

        // Creates via FileDescriptor.
        FileDescriptor fd = null;
        try {
            fd = Os.open(imageFile.getAbsolutePath(), OsConstants.O_RDONLY, 0600);
            exifInterface = new ExifInterface(fd);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag, true);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        } finally {
            IoUtils.closeQuietly(fd);
        }
    }

    private void testExifInterfaceRange(String fileName, ExpectedValue expectedValue)
            throws IOException {
        Preconditions.assertTestFileExists(mInpPrefix + fileName);
        File imageFile = new File(mInpPrefix, fileName);
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(imageFile.getAbsolutePath()));
            if (expectedValue.hasThumbnail) {
                in.skip(expectedValue.thumbnailOffset);
                byte[] thumbnailBytes = new byte[expectedValue.thumbnailLength];
                if (in.read(thumbnailBytes) != expectedValue.thumbnailLength) {
                    throw new IOException("Failed to read the expected thumbnail length");
                }
                // TODO: Need a way to check uncompressed thumbnail file
                if (expectedValue.isThumbnailCompressed) {
                    Bitmap thumbnailBitmap = BitmapFactory.decodeByteArray(thumbnailBytes, 0,
                            thumbnailBytes.length);
                    assertNotNull(thumbnailBitmap);
                    assertEquals(expectedValue.thumbnailWidth, thumbnailBitmap.getWidth());
                    assertEquals(expectedValue.thumbnailHeight, thumbnailBitmap.getHeight());
                }
            }
            // TODO: Creating a new input stream is a temporary
            //  workaround for BufferedInputStream#mark/reset not working properly for
            //  LG_G4_ISO_800_DNG. Need to investigate cause.
            in = new BufferedInputStream(new FileInputStream(imageFile.getAbsolutePath()));
            if (expectedValue.hasMake) {
                in.skip(expectedValue.makeOffset);
                byte[] makeBytes = new byte[expectedValue.makeLength];
                if (in.read(makeBytes) != expectedValue.makeLength) {
                    throw new IOException("Failed to read the expected make length");
                }
                String makeString = new String(makeBytes);
                // Remove null bytes
                makeString = makeString.replaceAll("\u0000.*", "");
                assertEquals(expectedValue.make, makeString.trim());
            }
            in = new BufferedInputStream(new FileInputStream(imageFile.getAbsolutePath()));
            if (expectedValue.hasXmp) {
                in.skip(expectedValue.xmpOffset);
                byte[] identifierBytes = new byte[expectedValue.xmpLength];
                if (in.read(identifierBytes) != expectedValue.xmpLength) {
                    throw new IOException("Failed to read the expected xmp length");
                }
                final String xmpIdentifier = "<?xpacket begin=";
                assertTrue(new String(identifierBytes, StandardCharsets.UTF_8)
                        .startsWith(xmpIdentifier));
            }
            // TODO: Add code for retrieving raw latitude data using offset and length
        } finally {
            IoUtils.closeQuietly(in);
        }
    }

    private void writeToFilesWithExif(String fileName, int typedArrayResourceId)
            throws IOException {
        ExpectedValue expectedValue = new ExpectedValue(
                getContext().getResources().obtainTypedArray(typedArrayResourceId));

        Preconditions.assertTestFileExists(mInpPrefix + fileName);
        File srcFile = new File(mInpPrefix, fileName);
        File imageFile = clone(srcFile);
        String verboseTag = imageFile.getName();

        ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        exifInterface.saveAttributes();
        exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag, false);
        assertBitmapsEquivalent(srcFile, imageFile);

        // Test for modifying one attribute.
        exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        String backupValue = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
        exifInterface.setAttribute(ExifInterface.TAG_MAKE, "abc");
        exifInterface.saveAttributes();
        // Check if thumbnail offset and length are properly updated without parsing the data again.
        if (expectedValue.hasThumbnail) {
            testThumbnail(expectedValue, exifInterface);
        }
        assertEquals("abc", exifInterface.getAttribute(ExifInterface.TAG_MAKE));
        // Check if thumbnail bytes can be retrieved from the new thumbnail range.
        if (expectedValue.hasThumbnail) {
            testThumbnail(expectedValue, exifInterface);
        }

        // Restore the backup value.
        exifInterface.setAttribute(ExifInterface.TAG_MAKE, backupValue);
        exifInterface.saveAttributes();
        exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag, false);

        FileDescriptor fd = null;
        try {
            fd = Os.open(imageFile.getAbsolutePath(), OsConstants.O_RDWR, 0600);
            exifInterface = new ExifInterface(fd);
            exifInterface.saveAttributes();
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            exifInterface = new ExifInterface(fd);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag, false);

            // Test for modifying one attribute.
            backupValue = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
            exifInterface.setAttribute(ExifInterface.TAG_MAKE, "abc");
            exifInterface.saveAttributes();
            // Check if thumbnail offset and length are properly updated without parsing the data
            // again.
            if (expectedValue.hasThumbnail) {
                testThumbnail(expectedValue, exifInterface);
            }
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            exifInterface = new ExifInterface(fd);
            assertEquals("abc", exifInterface.getAttribute(ExifInterface.TAG_MAKE));
            // Check if thumbnail bytes can be retrieved from the new thumbnail range.
            if (expectedValue.hasThumbnail) {
                testThumbnail(expectedValue, exifInterface);
            }

            // Restore the backup value.
            exifInterface.setAttribute(ExifInterface.TAG_MAKE, backupValue);
            exifInterface.saveAttributes();
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            exifInterface = new ExifInterface(fd);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag, false);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        } finally {
            IoUtils.closeQuietly(fd);
        }
        imageFile.delete();
    }

    private void readFromFilesWithExif(String fileName, int typedArrayResourceId)
            throws IOException {
        ExpectedValue expectedValue = new ExpectedValue(
                getContext().getResources().obtainTypedArray(typedArrayResourceId));

        testExifInterfaceCommon(fileName, expectedValue);

        // Test for checking expected range by retrieving raw data with given offset and length.
        testExifInterfaceRange(fileName, expectedValue);
    }

    private void writeToFilesWithoutExif(String fileName) throws IOException {
        // Test for reading from external data storage.
        Preconditions.assertTestFileExists(mInpPrefix + fileName);
        File srcFile = new File(mInpPrefix, fileName);
        File imageFile = clone(srcFile);

        ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        exifInterface.setAttribute(ExifInterface.TAG_MAKE, "abc");
        exifInterface.saveAttributes();

        assertBitmapsEquivalent(srcFile, imageFile);
        exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        String make = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
        assertEquals("abc", make);
        imageFile.delete();
    }

    private void testThumbnail(ExpectedValue expectedValue, ExifInterface exifInterface) {
        byte[] thumbnailBytes = exifInterface.getThumbnailBytes();
        assertNotNull(thumbnailBytes);

        // Note: NEF file (nikon_1aw1.nef) contains uncompressed thumbnail.
        Bitmap thumbnailBitmap = exifInterface.getThumbnailBitmap();
        assertNotNull(thumbnailBitmap);
        assertEquals(expectedValue.thumbnailWidth, thumbnailBitmap.getWidth());
        assertEquals(expectedValue.thumbnailHeight, thumbnailBitmap.getHeight());
        assertEquals(expectedValue.isThumbnailCompressed, exifInterface.isThumbnailCompressed());
    }

    @Before
    public void setUp() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectUnbufferedIo()
                .penaltyDeath()
                .build());
    }

    @Test
    public void testReadExifDataFromExifByteOrderIIJpeg() throws Throwable {
        readFromFilesWithExif(JPEG_WITH_EXIF_BYTE_ORDER_II, R.array.jpeg_with_exif_byte_order_ii);
        writeToFilesWithExif(JPEG_WITH_EXIF_BYTE_ORDER_II, R.array.jpeg_with_exif_byte_order_ii);
    }

    @Test
    public void testReadExifDataFromExifByteOrderMMJpeg() throws Throwable {
        readFromFilesWithExif(JPEG_WITH_EXIF_BYTE_ORDER_MM, R.array.jpeg_with_exif_byte_order_mm);
        writeToFilesWithExif(JPEG_WITH_EXIF_BYTE_ORDER_MM, R.array.jpeg_with_exif_byte_order_mm);
    }

    @Test
    public void testReadExifDataFromLgG4Iso800Dng() throws Throwable {
        readFromFilesWithExif(DNG_WITH_EXIF_WITH_XMP, R.array.dng_with_exif_with_xmp);
    }

    @Test
    public void testReadExifDataFromLgG4Iso800Jpg() throws Throwable {
        readFromFilesWithExif(JPEG_WITH_EXIF_WITH_XMP, R.array.jpeg_with_exif_with_xmp);
        writeToFilesWithExif(JPEG_WITH_EXIF_WITH_XMP, R.array.jpeg_with_exif_with_xmp);
    }

    @Test
    public void testDoNotFailOnCorruptedImage() throws Throwable {
        // To keep the compatibility with old versions of ExifInterface, even on a corrupted image,
        // it shouldn't raise any exceptions except an IOException when unable to open a file.
        byte[] bytes = new byte[1024];
        try {
            new ExifInterface(new ByteArrayInputStream(bytes));
            // Always success
        } catch (IOException e) {
            fail("Should not reach here!");
        }
    }

    @Test
    public void testReadExifDataFromVolantisJpg() throws Throwable {
        // Test if it is possible to parse the volantis generated JPEG smoothly.
        readFromFilesWithExif(JPEG_VOLANTIS, R.array.volantis_jpg);
        writeToFilesWithExif(JPEG_VOLANTIS, R.array.volantis_jpg);
    }

    @Test
    public void testReadExifDataFromSonyRX100Arw() throws Throwable {
        readFromFilesWithExif(ARW_SONY_RX_100, R.array.sony_rx_100_arw);
    }

    @Test
    public void testReadExifDataFromCanonG7XCr2() throws Throwable {
        readFromFilesWithExif(CR2_CANON_G7X, R.array.canon_g7x_cr2);
    }

    @Test
    public void testReadExifDataFromFujiX20Raf() throws Throwable {
        readFromFilesWithExif(RAF_FUJI_X20, R.array.fuji_x20_raf);
    }

    @Test
    public void testReadExifDataFromNikon1AW1Nef() throws Throwable {
        readFromFilesWithExif(NEF_NIKON_1AW1, R.array.nikon_1aw1_nef);
    }

    @Test
    public void testReadExifDataFromNikonP330Nrw() throws Throwable {
        readFromFilesWithExif(NRW_NIKON_P330, R.array.nikon_p330_nrw);
    }

    @Test
    public void testReadExifDataFromOlympusEPL3Orf() throws Throwable {
        readFromFilesWithExif(ORF_OLYMPUS_E_PL3, R.array.olympus_e_pl3_orf);
    }

    @Test
    public void testReadExifDataFromPanasonicGM5Rw2() throws Throwable {
        readFromFilesWithExif(RW2_PANASONIC_GM5, R.array.panasonic_gm5_rw2);
    }

    @Test
    public void testReadExifDataFromPentaxK5Pef() throws Throwable {
        readFromFilesWithExif(PEF_PENTAX_K5, R.array.pentax_k5_pef);
    }

    @Test
    public void testReadExifDataFromSamsungNX3000Srw() throws Throwable {
        readFromFilesWithExif(SRW_SAMSUNG_NX3000, R.array.samsung_nx3000_srw);
    }

    @Test
    public void testPngFiles() throws Throwable {
        readFromFilesWithExif(PNG_WITH_EXIF_BYTE_ORDER_II, R.array.png_with_exif_byte_order_ii);
        writeToFilesWithoutExif(PNG_WITHOUT_EXIF);
    }

    @Test
    public void testStandaloneData() throws Throwable {
        readFromStandaloneDataWithExif(JPEG_WITH_EXIF_BYTE_ORDER_II,
                R.array.standalone_data_with_exif_byte_order_ii);
        readFromStandaloneDataWithExif(JPEG_WITH_EXIF_BYTE_ORDER_MM,
                R.array.standalone_data_with_exif_byte_order_mm);
    }

    @Test
    public void testWebpFiles() throws Throwable {
        readFromFilesWithExif(WEBP_WITH_EXIF, R.array.webp_with_exif);
        writeToFilesWithExif(WEBP_WITH_EXIF, R.array.webp_with_exif);
        writeToFilesWithoutExif(WEBP_WITHOUT_EXIF_WITH_ANIM_DATA);
        writeToFilesWithoutExif(WEBP_WITHOUT_EXIF);
        writeToFilesWithoutExif(WEBP_WITHOUT_EXIF_WITH_LOSSLESS_ENCODING);
    }

    @Test
    public void testGetSetDateTime() throws Throwable {
        final long expectedDatetimeValue = 1454059947000L;
        final String dateTimeValue = "2017:02:02 22:22:22";
        final String dateTimeOriginalValue = "2017:01:01 11:11:11";

        Preconditions.assertTestFileExists(mInpPrefix + JPEG_WITH_DATETIME_TAG);
        File srcFile = new File(mInpPrefix, JPEG_WITH_DATETIME_TAG);
        File imageFile = clone(srcFile);

        ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
        assertEquals(expectedDatetimeValue, exif.getDateTime());
        assertEquals(expectedDatetimeValue, exif.getDateTimeOriginal());
        assertEquals(expectedDatetimeValue, exif.getDateTimeDigitized());
        assertEquals(expectedDatetimeValue, exif.getGpsDateTime());

        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTimeValue);
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTimeOriginalValue);
        exif.saveAttributes();

        // Check that the DATETIME value is not overwritten by DATETIME_ORIGINAL's value.
        exif = new ExifInterface(imageFile.getAbsolutePath());
        assertEquals(dateTimeValue, exif.getAttribute(ExifInterface.TAG_DATETIME));
        assertEquals(dateTimeOriginalValue, exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));

        // Now remove the DATETIME value.
        exif.setAttribute(ExifInterface.TAG_DATETIME, null);
        exif.saveAttributes();

        // When the DATETIME has no value, then it should be set to DATETIME_ORIGINAL's value.
        exif = new ExifInterface(imageFile.getAbsolutePath());
        assertEquals(dateTimeOriginalValue, exif.getAttribute(ExifInterface.TAG_DATETIME));
        imageFile.delete();
    }

    @Test
    public void testIsSupportedMimeType() {
        try {
            ExifInterface.isSupportedMimeType(null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        assertTrue(ExifInterface.isSupportedMimeType("image/jpeg"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-adobe-dng"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-canon-cr2"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-nikon-nef"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-nikon-nrw"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-sony-arw"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-panasonic-rw2"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-olympus-orf"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-pentax-pef"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-samsung-srw"));
        assertTrue(ExifInterface.isSupportedMimeType("image/x-fuji-raf"));
        assertTrue(ExifInterface.isSupportedMimeType("image/heic"));
        assertTrue(ExifInterface.isSupportedMimeType("image/heif"));
        assertTrue(ExifInterface.isSupportedMimeType("image/png"));
        assertTrue(ExifInterface.isSupportedMimeType("image/webp"));
        assertFalse(ExifInterface.isSupportedMimeType("image/gif"));
    }

    @Test
    public void testSetAttribute() throws Throwable {
        Preconditions.assertTestFileExists(mInpPrefix + JPEG_WITH_EXIF_BYTE_ORDER_MM);
        File srcFile = new File(mInpPrefix, JPEG_WITH_EXIF_BYTE_ORDER_MM);
        File imageFile = clone(srcFile);

        ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
        try {
            exif.setAttribute(null, null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }

        // Test setting tag to null
        assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null);
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));

        // Test tags that are converted to rational values for compatibility:
        // 1. GpsTimeStamp tag will be converted to rational in setAttribute and converted back to
        // timestamp format in getAttribute.
        String validGpsTimeStamp = "11:11:11";
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, validGpsTimeStamp);
        assertEquals(validGpsTimeStamp, exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));
        // Check that invalid format is not set
        String invalidGpsTimeStamp = "11:11:11:11";
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, invalidGpsTimeStamp);
        assertEquals(validGpsTimeStamp, exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));

        // 2. FNumber tag will be converted to rational in setAttribute and converted back to
        // double value in getAttribute
        String validFNumber = "2.4";
        exif.setAttribute(ExifInterface.TAG_F_NUMBER, validFNumber);
        assertEquals(validFNumber, exif.getAttribute(ExifInterface.TAG_F_NUMBER));
        // Check that invalid format is not set
        String invalidFNumber = "invalid format";
        exif.setAttribute(ExifInterface.TAG_F_NUMBER, invalidFNumber);
        assertEquals(validFNumber, exif.getAttribute(ExifInterface.TAG_F_NUMBER));

        // Test writing different types of formats:
        // 1. Byte format tag
        String gpsVersionId = "2.3.0.0";
        exif.setAttribute(ExifInterface.TAG_GPS_VERSION_ID, gpsVersionId);
        byte[] setGpsVersionIdBytes =
                exif.getAttribute(ExifInterface.TAG_GPS_VERSION_ID).getBytes();
        for (int i = 0; i < setGpsVersionIdBytes.length; i++) {
            assertEquals(gpsVersionId.getBytes()[i], setGpsVersionIdBytes[i]);
        }
        // Test TAG_GPS_ALTITUDE_REF, which is an exceptional case since the only valid values are
        // "0" and "1".
        String gpsAltitudeRef = "1";
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, gpsAltitudeRef);
        assertEquals(gpsAltitudeRef.getBytes()[0],
                exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF).getBytes()[0]);

        // 2. String format tag
        String makeValue = "MakeTest";
        exif.setAttribute(ExifInterface.TAG_MAKE, makeValue);
        assertEquals(makeValue, exif.getAttribute(ExifInterface.TAG_MAKE));
        // Check that the following values are not parsed as rational values
        String makeValueWithOneSlash = "Make/Test";
        exif.setAttribute(ExifInterface.TAG_MAKE, makeValueWithOneSlash);
        assertEquals(makeValueWithOneSlash, exif.getAttribute(ExifInterface.TAG_MAKE));
        String makeValueWithTwoSlashes = "Make/Test/Test";
        exif.setAttribute(ExifInterface.TAG_MAKE, makeValueWithTwoSlashes);
        assertEquals(makeValueWithTwoSlashes, exif.getAttribute(ExifInterface.TAG_MAKE));
        // When a value has a comma, it should be parsed as a string if any of the values before or
        // after the comma is a string.
        int defaultValue = -1;
        String makeValueWithCommaType1 = "Make,2";
        exif.setAttribute(ExifInterface.TAG_MAKE, makeValueWithCommaType1);
        assertEquals(makeValueWithCommaType1, exif.getAttribute(ExifInterface.TAG_MAKE));
        // Make sure that it's not stored as an integer value.
        assertEquals(defaultValue, exif.getAttributeInt(ExifInterface.TAG_MAKE, defaultValue));
        String makeValueWithCommaType2 = "2,Make";
        exif.setAttribute(ExifInterface.TAG_MAKE, makeValueWithCommaType2);
        assertEquals(makeValueWithCommaType2, exif.getAttribute(ExifInterface.TAG_MAKE));
        // Make sure that it's not stored as an integer value.
        assertEquals(defaultValue, exif.getAttributeInt(ExifInterface.TAG_MAKE, defaultValue));

        // 3. Unsigned short format tag
        String isoSpeedRatings = "800";
        exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, isoSpeedRatings);
        assertEquals(isoSpeedRatings, exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS));
        // When a value has multiple components, all of them should be of the format that the tag
        // supports. Thus, the following values (SHORT,LONG) should not be set since TAG_COMPRESSION
        // only allows short values.
        assertNull(exif.getAttribute(ExifInterface.TAG_COMPRESSION));
        String invalidMultipleComponentsValueType1 = "1,65536";
        exif.setAttribute(ExifInterface.TAG_COMPRESSION, invalidMultipleComponentsValueType1);
        assertNull(exif.getAttribute(ExifInterface.TAG_COMPRESSION));
        String invalidMultipleComponentsValueType2 = "65536,1";
        exif.setAttribute(ExifInterface.TAG_COMPRESSION, invalidMultipleComponentsValueType2);
        assertNull(exif.getAttribute(ExifInterface.TAG_COMPRESSION));

        // 4. Unsigned long format tag
        String validImageWidthValue = "65536"; // max unsigned short value + 1
        exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, validImageWidthValue);
        assertEquals(validImageWidthValue, exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH));
        String invalidImageWidthValue = "-65536";
        exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, invalidImageWidthValue);
        assertEquals(validImageWidthValue, exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH));

        // 5. Unsigned rational format tag
        String exposureTime = "1/8";
        exif.setAttribute(ExifInterface.TAG_APERTURE_VALUE, exposureTime);
        assertEquals(exposureTime, exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE));

        // 6. Signed rational format tag
        String brightnessValue = "-220/100";
        exif.setAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE, brightnessValue);
        assertEquals(brightnessValue, exif.getAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE));

        // 7. Undefined format tag
        String userComment = "UserCommentTest";
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment);
        assertEquals(userComment, exif.getAttribute(ExifInterface.TAG_USER_COMMENT));

        imageFile.delete();
    }

    @Test
    public void testGetAttributeForNullAndNonExistentTag() throws Throwable {
        // JPEG_WITH_EXIF_BYTE_ORDER_MM does not have a value for TAG_SUBJECT_AREA tag.
        Preconditions.assertTestFileExists(mInpPrefix + JPEG_WITH_EXIF_BYTE_ORDER_MM);
        File srcFile = new File(mInpPrefix, JPEG_WITH_EXIF_BYTE_ORDER_MM);
        File imageFile = clone(srcFile);

        ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
        try {
            exif.getAttribute(null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        assertNull(exif.getAttribute(TAG_SUBJECT_AREA));

        int defaultValue = -1;
        try {
            exif.getAttributeInt(null, defaultValue);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        assertEquals(defaultValue, exif.getAttributeInt(TAG_SUBJECT_AREA, defaultValue));

        try {
            exif.getAttributeDouble(null, defaultValue);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        assertEquals(defaultValue, exif.getAttributeInt(TAG_SUBJECT_AREA, defaultValue));

        try {
            exif.getAttributeBytes(null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        assertNull(exif.getAttributeBytes(TAG_SUBJECT_AREA));
    }

    private static File clone(File original) throws IOException {
        final File cloned =
                File.createTempFile("cts_", +System.nanoTime() + "_" + original.getName());
        FileUtils.copyFileOrThrow(original, cloned);
        return cloned;
    }

    private short readShort(InputStream is) throws IOException {
        int ch1 = is.read();
        int ch2 = is.read();
        if ((ch1 | ch2) < 0) {
            throw new EOFException();
        }
        return (short) ((ch1 << 8) + (ch2));
    }

    /**
     * Asserts that {@code expectedImageFile} and {@code actualImageFile} can be decoded by
     * {@link BitmapFactory} and the results have the same width, height and MIME type.
     *
     * <p>This does not check the image itself for similarity/equality.
     */
    private void assertBitmapsEquivalent(File expectedImageFile, File actualImageFile) {
        BitmapFactory.Options expectedOptions = new BitmapFactory.Options();
        Bitmap expectedBitmap = Objects.requireNonNull(
                decodeBitmap(expectedImageFile, expectedOptions));
        BitmapFactory.Options actualOptions = new BitmapFactory.Options();
        Bitmap actualBitmap = Objects.requireNonNull(decodeBitmap(actualImageFile, actualOptions));

        assertEquals(expectedOptions.outWidth, actualOptions.outWidth);
        assertEquals(expectedOptions.outHeight, actualOptions.outHeight);
        assertEquals(expectedOptions.outMimeType, actualOptions.outMimeType);
        assertEquals(expectedBitmap.getWidth(), actualBitmap.getWidth());
        assertEquals(expectedBitmap.getHeight(), actualBitmap.getHeight());
    }

    /**
     * Equivalent to {@link BitmapFactory#decodeFile(String, BitmapFactory.Options)} but uses a
     * {@link BufferedInputStream} to avoid violating
     * {@link StrictMode.ThreadPolicy.Builder#detectUnbufferedIo()}.
     */
    private static Bitmap decodeBitmap(File file, BitmapFactory.Options options) {
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            return BitmapFactory.decodeStream(inputStream, /* outPadding= */ null, options);
        } catch (IOException e) {
            return null;
        }
    }
}
