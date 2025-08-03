/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.bluetooth.avrcp;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.audio_util.Image;
import com.android.bluetooth.avrcpcontroller.BipEncoding;
import com.android.bluetooth.avrcpcontroller.BipImageDescriptor;
import com.android.bluetooth.avrcpcontroller.BipImageFormat;
import com.android.bluetooth.avrcpcontroller.BipImageProperties;
import com.android.bluetooth.avrcpcontroller.BipPixel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class CoverArtTest {
    private Resources mTestResources;

    private static final BipPixel PIXEL_THUMBNAIL = BipPixel.createFixed(200, 200);
    private static final String IMAGE_HANDLE_1 = "0000001";

    private Bitmap m200by200Image = null;
    private Bitmap m200by200ImageBlue = null;

    private Image mImage = null;
    private Image mImage2 = null;

    @Before
    public void setUp() throws Exception {
        mTestResources = TestUtils.getTestApplicationResources(
                InstrumentationRegistry.getTargetContext());

        m200by200Image = loadImage(com.android.bluetooth.tests.R.raw.image_200_200);
        m200by200ImageBlue = loadImage(com.android.bluetooth.tests.R.raw.image_200_200_blue);
        mImage = new Image(null, m200by200Image);
        mImage2 = new Image(null, m200by200ImageBlue);
    }

    @After
    public void tearDown() throws Exception {
        mImage2 = null;
        mImage = null;
        m200by200ImageBlue = null;
        m200by200Image = null;
        mTestResources = null;
    }

    private Bitmap loadImage(int resId) {
        InputStream imageInputStream = mTestResources.openRawResource(resId);
        return BitmapFactory.decodeStream(imageInputStream);
    }

    private InputStream toInputSteam(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        return new ByteArrayInputStream(imageBytes);
    }

    private Bitmap toBitmap(byte[] imageBytes) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        return BitmapFactory.decodeStream(inputStream);
    }

    private BipImageDescriptor getDescriptor(int encoding, int width, int height) {
        return new BipImageDescriptor.Builder()
                .setEncoding(encoding)
                .setFixedDimensions(width, height)
                .build();
    }

    private boolean containsThumbnailFormat(BipImageProperties properties) {
        if (properties == null) return false;

        for (BipImageFormat format : properties.getNativeFormats()) {
            BipEncoding encoding = format.getEncoding();
            BipPixel pixel = format.getPixel();
            if (encoding == null || pixel == null) continue;
            if (encoding.getType() == BipEncoding.JPEG && PIXEL_THUMBNAIL.equals(pixel)) {
                return true;
            }
        }

        for (BipImageFormat format : properties.getVariantFormats()) {
            BipEncoding encoding = format.getEncoding();
            BipPixel pixel = format.getPixel();
            if (encoding == null || pixel == null) continue;
            if (encoding.getType() == BipEncoding.JPEG && PIXEL_THUMBNAIL.equals(pixel)) {
                return true;
            }
        }

        return false;
    }

    private boolean isThumbnailFormat(Bitmap image) {
        if (image == null) return false;
        return (200 == image.getHeight() && 200 == image.getWidth());
    }

    /**
     * Make sure you can create an image from an Image object
     */
    @Test
    public void testCreateCoverArtFromImage() {
        CoverArt artwork = new CoverArt(mImage);
        assertThat(artwork.getImage()).isNotNull();
    }

    /**
     * Make sure you get an image hash from a valid image
     */
    @Test
    public void testGetImageHash() {
        CoverArt artwork = new CoverArt(mImage);
        String hash = artwork.getImageHash();
        assertThat(hash).isNotNull();
    }

    /**
     * Make sure you get the same image hash from several calls to the same object
     */
    @Test
    public void testGetImageHashSameForMultipleCalls() {
        CoverArt artwork = new CoverArt(mImage);
        String hash = artwork.getImageHash();
        assertThat(hash).isNotNull();
        assertThat(artwork.getImageHash()).isEqualTo(hash); // extra call 1
        assertThat(artwork.getImageHash()).isEqualTo(hash); // extra call 2
    }

    /**
     * Make sure you get the same image hash from separate objects created from the same image
     */
    @Test
    public void testGetImageHashSameForSameImages() {
        CoverArt artwork = new CoverArt(mImage);
        CoverArt artwork2 = new CoverArt(mImage);
        String hash = artwork.getImageHash();
        String hash2 = artwork2.getImageHash();

        assertThat(hash).isNotNull();
        assertThat(hash2).isNotNull();
        assertThat(hash).isEqualTo(hash2);
    }

    /**
     * Make sure you get different image hashes from separate objects created from different images
     */
    @Test
    public void testGetImageHashDifferentForDifferentImages() {
        CoverArt artwork = new CoverArt(mImage);
        CoverArt artwork2 = new CoverArt(mImage2);
        String hash = artwork.getImageHash();
        String hash2 = artwork2.getImageHash();

        assertThat(hash).isNotNull();
        assertThat(hash2).isNotNull();
        assertThat(hash).isNotEqualTo(hash2);
    }

    /**
     * Make sure you get an image when asking for the native image
     */
    @Test
    public void testGetNativeImage() {
        CoverArt artwork = new CoverArt(mImage);
        byte[] image = artwork.getImage();
        assertThat(image).isNotNull();
    }

    /**
     * Make sure you getThumbnailImage returns an image as a 200 by 200 JPEG
     */
    @Test
    public void testGetThumbnailImage() {
        CoverArt artwork = new CoverArt(mImage);
        byte[] imageBytes = artwork.getThumbnail();
        assertThat(imageBytes).isNotNull();
        Bitmap image = toBitmap(imageBytes);
        assertThat(isThumbnailFormat(image)).isTrue();
    }

    /**
     * Make sure you can set the image handle associated with this object
     */
    @Test
    public void testGetAndSetImageHandle() {
        CoverArt artwork = new CoverArt(mImage);
        assertThat(artwork.getImageHandle()).isNull();
        artwork.setImageHandle(IMAGE_HANDLE_1);
        assertThat(artwork.getImageHandle()).isEqualTo(IMAGE_HANDLE_1);
    }

    /**
     * Make sure a getImageProperties() yields a set of image properties. The thumbnail format
     * MUST be contained in that set
     */
    @Test
    public void testGetImageProperties() {
        CoverArt artwork = new CoverArt(mImage);
        artwork.setImageHandle(IMAGE_HANDLE_1);
        BipImageProperties properties = artwork.getImageProperties();
        assertThat(properties).isNotNull();
        assertThat(containsThumbnailFormat(properties)).isTrue();
    }

    /**
     * Make sure a getImage(<valid descriptor>) yield an image in the format you asked for
     */
    @Test
    public void testGetImageWithValidDescriptor() {
        CoverArt artwork = new CoverArt(mImage);
        BipImageDescriptor descriptor = getDescriptor(BipEncoding.JPEG, 200, 200);
        byte[] image = artwork.getImage(descriptor);
        assertThat(image).isNotNull();
    }

    /**
     * Make sure a getImage(<thumbnail descriptor>) yields the image in the thumbnail format
     */
    @Test
    public void testGetImageWithThumbnailDescriptor() {
        CoverArt artwork = new CoverArt(mImage);
        BipImageDescriptor descriptor = getDescriptor(BipEncoding.JPEG, 200, 200);
        byte[] imageBytes = artwork.getImage(descriptor);
        assertThat(imageBytes).isNotNull();
        Bitmap image = toBitmap(imageBytes);
        assertThat(isThumbnailFormat(image)).isTrue();
    }

    /**
     * Make sure a getImage(<invalid descriptor>) yields a null
     */
    @Test
    public void testGetImageWithInvalidDescriptor() {
        CoverArt artwork = new CoverArt(mImage);
        BipImageDescriptor descriptor = getDescriptor(BipEncoding.BMP, 1200, 1200);
        byte[] image = artwork.getImage(descriptor);
        assertThat(image).isNull();
    }

    /**
     * Make sure a getImage(<null descriptor>) yields the native image
     */
    @Test
    public void testGetImageWithoutDescriptor() {
        CoverArt artwork = new CoverArt(mImage);
        byte[] image = artwork.getImage(null);
        byte[] nativeImage = artwork.getImage();
        assertThat(Arrays.equals(nativeImage, image)).isTrue();
    }

    /**
     * Make sure we can get a valid string representation of the CoverArt
     */
    @Test
    public void testGetSize() {
        CoverArt artwork = new CoverArt(mImage);
        assertThat(artwork.size() > 0).isTrue();
    }

    /**
     * Make sure we can get a valid string representation of the CoverArt
     */
    @Test
    public void testToString() {
        CoverArt artwork = new CoverArt(mImage);
        assertThat(artwork.toString()).isNotNull();
    }
}
