/*
 * Copyright 2019 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.Capability;
import android.hardware.camera2.params.ColorSpaceProfiles;
import android.hardware.camera2.params.DeviceStateSensorOrientationMap;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.Face;
import android.util.Range;
import android.util.Size;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * Test CaptureRequest/Result/CameraCharacteristics.Key objects.
 */
@RunWith(AndroidJUnit4.class)
@SuppressWarnings("EqualsIncompatibleType")
public class SimpleObjectsTest {

    @Test
    public void cameraKeysTest() throws Exception {
        String keyName = "android.testing.Key";
        String keyName2 = "android.testing.Key2";

        CaptureRequest.Key<Integer> testRequestKey =
                new CaptureRequest.Key<>(keyName, Integer.class);
        Assert.assertEquals("Request key name not correct",
                testRequestKey.getName(), keyName);

        CaptureResult.Key<Integer> testResultKey =
                new CaptureResult.Key<>(keyName, Integer.class);
        Assert.assertEquals("Result key name not correct",
                testResultKey.getName(), keyName);

        CameraCharacteristics.Key<Integer> testCharacteristicsKey =
                new CameraCharacteristics.Key<>(keyName, Integer.class);
        Assert.assertEquals("Characteristics key name not correct",
                testCharacteristicsKey.getName(), keyName);

        CaptureRequest.Key<Integer> testRequestKey2 =
                new CaptureRequest.Key<>(keyName, Integer.class);
        Assert.assertEquals("Two request keys with same name/type should be equal",
                testRequestKey, testRequestKey2);
        CaptureRequest.Key<Byte> testRequestKey3 =
                new CaptureRequest.Key<>(keyName, Byte.class);
        Assert.assertTrue("Two request keys with different types should not be equal",
                !testRequestKey.equals(testRequestKey3));
        CaptureRequest.Key<Integer> testRequestKey4 =
                new CaptureRequest.Key<>(keyName2, Integer.class);
        Assert.assertTrue("Two request keys with different names should not be equal",
                !testRequestKey.equals(testRequestKey4));
        CaptureRequest.Key<Byte> testRequestKey5 =
                new CaptureRequest.Key<>(keyName2, Byte.class);
        Assert.assertTrue("Two request keys with different types and names should not be equal",
                !testRequestKey.equals(testRequestKey5));

        CaptureResult.Key<Integer> testResultKey2 =
                new CaptureResult.Key<>(keyName, Integer.class);
        Assert.assertEquals("Two result keys with same name/type should be equal",
                testResultKey, testResultKey2);
        CaptureResult.Key<Byte> testResultKey3 =
                new CaptureResult.Key<>(keyName, Byte.class);
        Assert.assertTrue("Two result keys with different types should not be equal",
                !testResultKey.equals(testResultKey3));
        CaptureResult.Key<Integer> testResultKey4 =
                new CaptureResult.Key<>(keyName2, Integer.class);
        Assert.assertTrue("Two result keys with different names should not be equal",
                !testResultKey.equals(testResultKey4));
        CaptureResult.Key<Byte> testResultKey5 =
                new CaptureResult.Key<>(keyName2, Byte.class);
        Assert.assertTrue("Two result keys with different types and names should not be equal",
                !testResultKey.equals(testResultKey5));

        CameraCharacteristics.Key<Integer> testCharacteristicsKey2 =
                new CameraCharacteristics.Key<>(keyName, Integer.class);
        Assert.assertEquals("Two characteristics keys with same name/type should be equal",
                testCharacteristicsKey, testCharacteristicsKey2);
        CameraCharacteristics.Key<Byte> testCharacteristicsKey3 =
                new CameraCharacteristics.Key<>(keyName, Byte.class);
        Assert.assertTrue("Two characteristics keys with different types should not be equal",
                !testCharacteristicsKey.equals(testCharacteristicsKey3));
        CameraCharacteristics.Key<Integer> testCharacteristicsKey4 =
                new CameraCharacteristics.Key<>(keyName2, Integer.class);
        Assert.assertTrue("Two characteristics keys with different names should not be equal",
                !testCharacteristicsKey.equals(testCharacteristicsKey4));
        CameraCharacteristics.Key<Byte> testCharacteristicsKey5 =
                new CameraCharacteristics.Key<>(keyName2, Byte.class);
        Assert.assertTrue(
                "Two characteristics keys with different types and names should not be equal",
                !testCharacteristicsKey.equals(testCharacteristicsKey5));

    }

    @Test
    public void blackLevelPatternConstructionTest() {
        int[] offsets = { 5, 5, 5, 5 };
        BlackLevelPattern blackLevelPattern = new BlackLevelPattern(offsets);

        try {
            blackLevelPattern = new BlackLevelPattern(/*offsets*/ null);
            Assert.fail("BlackLevelPattern did not throw a NullPointerException for null offsets");
        } catch (NullPointerException e) {
            // Do nothing
        }

        try {
            int[] badOffsets = { 5 };
            blackLevelPattern = new BlackLevelPattern(badOffsets);
            Assert.fail("BlackLevelPattern did not throw an IllegalArgumentException for incorrect"
                    + " offsets length");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

    @Test
    public void capabilityConstructionTest() {
        Capability capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, 128),
                /*zoomRatioRange*/ new Range<Float>(0.2f, 2.0f));

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(-1, 128),
                    /*zoomRatioRange*/ new Range<Float>(0.2f, 2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for negative "
                    + "maxStreamingWidth");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, -1),
                     /*zoomRatioRange*/ new Range<Float>(0.2f, 2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for negative "
                    + "maxStreamingHeight");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, 128),
                    /*zoomRatioRange*/ new Range<Float>(-3.0f, -2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for negative "
                    + "zoom ratios");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, 128),
                    /*zoomRatioRange*/ new Range<Float>(3.0f, 2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for minZoomRatio "
                    + "> maxZoomRatio");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

    @Test
    public void deviceStateSensorOrientationMapConstructionTest() {
        DeviceStateSensorOrientationMap.Builder builder =
                new DeviceStateSensorOrientationMap.Builder();
        DeviceStateSensorOrientationMap deviceStateSensorOrientationMap =
                builder.addOrientationForState(/*deviceState*/ 0, /*angle*/ 90)
                       .addOrientationForState(/*deviceState*/ 1, /*angle*/ 180)
                       .addOrientationForState(/*deviceState*/ 2, /*angle*/ 270)
                       .build();

        try {
            builder = new DeviceStateSensorOrientationMap.Builder();
            deviceStateSensorOrientationMap = builder.build();
            Assert.fail("DeviceStateSensorOrientationMap did not throw an IllegalStateException for"
                    + " zero elements");
        } catch (IllegalStateException e) {
            // Do nothing
        }

        try {
            builder = new DeviceStateSensorOrientationMap.Builder();
            deviceStateSensorOrientationMap = builder.addOrientationForState(/*deviceState*/ 0, 55)
                                                     .build();
            Assert.fail("DeviceStateSensorOrientationMap did not throw an IllegalArgumentException "
                    + "for incorrect elements values");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

    @Test
    public void faceConstructionTest() {
        Rect bounds = new Rect();
        int score = 50;
        int id = 1;
        Point leftEyePosition = new Point();
        Point rightEyePosition = new Point();
        Point mouthPosition = new Point();

        Face.Builder builder = new Face.Builder();
        Face face = builder.setBounds(bounds)
                           .setScore(score)
                           .setId(id)
                           .setLeftEyePosition(leftEyePosition)
                           .setRightEyePosition(rightEyePosition)
                           .setMouthPosition(mouthPosition)
                           .build();

        builder = new Face.Builder();
        face = builder.setBounds(bounds)
                      .setScore(score)
                      .build();

        try {
            builder = new Face.Builder();
            face = builder.build();
            Assert.fail("Face.Builder did not throw an IllegalStateException for unset bounds "
                    + "and score.");
        } catch (IllegalStateException e) {
            // Do nothing
        }

        try {
            builder = new Face.Builder();
            face = builder.setBounds(bounds)
                          .build();
            Assert.fail("Face.Builder did not throw an IllegalStateException for unset score.");
        } catch (IllegalStateException e) {
            // Do nothing
        }

        try {
            builder = new Face.Builder();
            face = builder.setBounds(null)
                    .setScore(score)
                    .setId(id)
                    .setLeftEyePosition(leftEyePosition)
                    .setRightEyePosition(rightEyePosition)
                    .setMouthPosition(mouthPosition)
                    .build();
            Assert.fail("Face did not throw an IllegalArgumentException for null bounds");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            builder = new Face.Builder();
            face = builder.setBounds(bounds)
                          .setScore(Face.SCORE_MIN - 1)
                          .setId(id)
                          .setLeftEyePosition(leftEyePosition)
                          .setRightEyePosition(rightEyePosition)
                          .setMouthPosition(mouthPosition)
                          .build();
            Assert.fail("Face did not throw an IllegalArgumentException for score below range");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            builder = new Face.Builder();
            face = builder.setBounds(bounds)
                          .setScore(Face.SCORE_MAX + 1)
                          .setId(id)
                          .setLeftEyePosition(leftEyePosition)
                          .setRightEyePosition(rightEyePosition)
                          .setMouthPosition(mouthPosition)
                          .build();
            Assert.fail("Face did not throw an IllegalArgumentException for score above range");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            builder = new Face.Builder();
            face = builder.setBounds(bounds)
                          .setScore(score)
                          .setId(Face.ID_UNSUPPORTED)
                          .setLeftEyePosition(leftEyePosition)
                          .setRightEyePosition(rightEyePosition)
                          .setMouthPosition(mouthPosition)
                          .build();
            Assert.fail("Face did not throw an IllegalArgumentException for non-null positions when"
                    + " id is ID_UNSUPPORTED.");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            builder = new Face.Builder();
            face = builder.setBounds(bounds)
                          .setScore(score)
                          .setId(Face.ID_UNSUPPORTED)
                          .setLeftEyePosition(leftEyePosition)
                          .setRightEyePosition(rightEyePosition)
                          .setMouthPosition(null)
                          .build();
            Assert.fail("Face did not throw an IllegalArgumentException for partially defined "
                    + "face features");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

    @Test
    @ApiTest(apis = {
            "android.hardware.camera2.params.ColorSpaceProfiles#getSupportedColorSpaces",
            "android.hardware.camera2.params.ColorSpaceProfiles#getSupportedColorSpacesForDynamicRange",
            "android.hardware.camera2.params.ColorSpaceProfiles#getSupportedImageFormatsForColorSpace",
            "android.hardware.camera2.params.ColorSpaceProfiles#getSupportedDynamicRangeProfiles"})
    public void colorSpaceProfilesTest() {
        long[] elements = {
                ColorSpace.Named.DISPLAY_P3.ordinal(), ImageFormat.YUV_420_888,
                DynamicRangeProfiles.STANDARD
        };

        ColorSpaceProfiles colorSpaceProfiles = new ColorSpaceProfiles(elements);
        Set<ColorSpace.Named> supportedColorSpaces =
                colorSpaceProfiles.getSupportedColorSpaces(ImageFormat.UNKNOWN);
        ColorSpace.Named[] arrColorSpaces = supportedColorSpaces.toArray(new ColorSpace.Named[0]);
        Assert.assertEquals(arrColorSpaces.length, 1);
        Assert.assertEquals(arrColorSpaces[0], ColorSpace.Named.DISPLAY_P3);

        supportedColorSpaces =
                colorSpaceProfiles.getSupportedColorSpaces(ImageFormat.YUV_420_888);
        arrColorSpaces = supportedColorSpaces.toArray(new ColorSpace.Named[0]);
        Assert.assertEquals(arrColorSpaces.length, 1);
        Assert.assertEquals(arrColorSpaces[0], ColorSpace.Named.DISPLAY_P3);

        // getSupportedColorSpaces should return an empty set on an unsupported image format
        supportedColorSpaces =
                colorSpaceProfiles.getSupportedColorSpaces(ImageFormat.PRIVATE);
        arrColorSpaces = supportedColorSpaces.toArray(new ColorSpace.Named[0]);
        Assert.assertEquals(arrColorSpaces.length, 0);

        Set<Integer> imageFormats =
                colorSpaceProfiles.getSupportedImageFormatsForColorSpace(
                                ColorSpace.Named.DISPLAY_P3);
        Integer[] arrImageFormats = imageFormats.toArray(new Integer[0]);
        Assert.assertEquals(arrImageFormats.length, 1);
        Assert.assertTrue(arrImageFormats[0] == ImageFormat.YUV_420_888);

        // getSupportedImageFormatsForColorSpace should return an empty set on an unsupported color
        // space
        imageFormats =
                colorSpaceProfiles.getSupportedImageFormatsForColorSpace(ColorSpace.Named.SRGB);
        arrImageFormats = imageFormats.toArray(new Integer[0]);
        Assert.assertEquals(arrImageFormats.length, 0);

        Set<Long> dynamicRangeProfiles =
                colorSpaceProfiles.getSupportedDynamicRangeProfiles(ColorSpace.Named.DISPLAY_P3,
                        ImageFormat.UNKNOWN);
        Long[] arrDynamicRangeProfiles = dynamicRangeProfiles.toArray(new Long[0]);
        Assert.assertEquals(arrDynamicRangeProfiles.length, 1);
        Assert.assertTrue(arrDynamicRangeProfiles[0] == DynamicRangeProfiles.STANDARD);

        dynamicRangeProfiles =
                colorSpaceProfiles.getSupportedDynamicRangeProfiles(ColorSpace.Named.DISPLAY_P3,
                        ImageFormat.YUV_420_888);
        arrDynamicRangeProfiles = dynamicRangeProfiles.toArray(new Long[0]);
        Assert.assertEquals(arrDynamicRangeProfiles.length, 1);
        Assert.assertTrue(arrDynamicRangeProfiles[0] == DynamicRangeProfiles.STANDARD);

        // getSupportedDynamicRangeProfiles should return an empty set on an unsupported image
        // format
        dynamicRangeProfiles =
                colorSpaceProfiles.getSupportedDynamicRangeProfiles(ColorSpace.Named.DISPLAY_P3,
                        ImageFormat.PRIVATE);
        arrDynamicRangeProfiles = dynamicRangeProfiles.toArray(new Long[0]);
        Assert.assertEquals(arrDynamicRangeProfiles.length, 0);

        // getSupportedDynamicRangeProfiles should return an empty set on an unsupported color space
        dynamicRangeProfiles =
                colorSpaceProfiles.getSupportedDynamicRangeProfiles(ColorSpace.Named.SRGB,
                        ImageFormat.UNKNOWN);
        arrDynamicRangeProfiles = dynamicRangeProfiles.toArray(new Long[0]);
        Assert.assertEquals(arrDynamicRangeProfiles.length, 0);

        supportedColorSpaces =
                colorSpaceProfiles.getSupportedColorSpacesForDynamicRange(ImageFormat.UNKNOWN,
                        DynamicRangeProfiles.STANDARD);
        arrColorSpaces = supportedColorSpaces.toArray(new ColorSpace.Named[0]);
        Assert.assertEquals(arrColorSpaces.length, 1);
        Assert.assertEquals(arrColorSpaces[0], ColorSpace.Named.DISPLAY_P3);

        supportedColorSpaces =
                colorSpaceProfiles.getSupportedColorSpacesForDynamicRange(ImageFormat.YUV_420_888,
                        DynamicRangeProfiles.STANDARD);
        arrColorSpaces = supportedColorSpaces.toArray(new ColorSpace.Named[0]);
        Assert.assertEquals(arrColorSpaces.length, 1);
        Assert.assertEquals(arrColorSpaces[0], ColorSpace.Named.DISPLAY_P3);

        // getSupportedColorSpacesForDynamicRange should return an empty set un an unsupported image
        // format
        supportedColorSpaces =
                colorSpaceProfiles.getSupportedColorSpacesForDynamicRange(ImageFormat.PRIVATE,
                        DynamicRangeProfiles.STANDARD);
        arrColorSpaces = supportedColorSpaces.toArray(new ColorSpace.Named[0]);
        Assert.assertEquals(arrColorSpaces.length, 0);

        // getSupportedColorSpacesForDynamicRange should return an empty set un an unsupported
        // dynamic range profile
        supportedColorSpaces =
                colorSpaceProfiles.getSupportedColorSpacesForDynamicRange(ImageFormat.UNKNOWN,
                        DynamicRangeProfiles.HLG10);
        arrColorSpaces = supportedColorSpaces.toArray(new ColorSpace.Named[0]);
        Assert.assertEquals(arrColorSpaces.length, 0);
    }

}
