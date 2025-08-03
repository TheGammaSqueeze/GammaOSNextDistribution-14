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
package android.hardware.camera2.cts;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.ExtensionCaptureRequest;
import android.hardware.camera2.ExtensionCaptureResult;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestRule;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.ArraySet;
import android.util.FeatureFlagUtils;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.internal.camera.flags.Flags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CameraExtensionCharacteristicsTest {
    private static final String TAG = "CameraExtensionManagerTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private ArrayList<Integer> mExtensionList = new ArrayList<>();

    private final Context mContext = InstrumentationRegistry.getTargetContext();

    private static final CaptureRequest.Key[] EYES_FREE_AUTO_ZOOM_REQUEST_SET = {
            ExtensionCaptureRequest.EFV_AUTO_ZOOM,
            ExtensionCaptureRequest.EFV_MAX_PADDING_ZOOM_FACTOR};
    private static final CaptureResult.Key[] EYES_FREE_AUTO_ZOOM_RESULT_SET = {
            ExtensionCaptureResult.EFV_AUTO_ZOOM,
            ExtensionCaptureResult.EFV_AUTO_ZOOM_PADDING_REGION,
            ExtensionCaptureResult.EFV_MAX_PADDING_ZOOM_FACTOR};
    private static final CaptureRequest.Key[] EYES_FREE_REQUEST_SET = {
            ExtensionCaptureRequest.EFV_PADDING_ZOOM_FACTOR,
            ExtensionCaptureRequest.EFV_STABILIZATION_MODE,
            ExtensionCaptureRequest.EFV_TRANSLATE_VIEWPORT,
            ExtensionCaptureRequest.EFV_ROTATE_VIEWPORT};
    private static final CaptureResult.Key[] EYES_FREE_RESULT_SET = {
            ExtensionCaptureResult.EFV_PADDING_REGION,
            ExtensionCaptureResult.EFV_TARGET_COORDINATES,
            ExtensionCaptureResult.EFV_PADDING_ZOOM_FACTOR,
            ExtensionCaptureResult.EFV_STABILIZATION_MODE,
            ExtensionCaptureResult.EFV_ROTATE_VIEWPORT,
            ExtensionCaptureResult.EFV_TRANSLATE_VIEWPORT};

    @Rule
    public final Camera2AndroidTestRule mTestRule = new Camera2AndroidTestRule(mContext);

    @Before
    public void setUp() throws Exception {
        mExtensionList.addAll(Arrays.asList(
                CameraExtensionCharacteristics.EXTENSION_AUTOMATIC,
                CameraExtensionCharacteristics.EXTENSION_BEAUTY,
                CameraExtensionCharacteristics.EXTENSION_BOKEH,
                CameraExtensionCharacteristics.EXTENSION_HDR,
                CameraExtensionCharacteristics.EXTENSION_NIGHT));
        if (FeatureFlagUtils.isEnabled(mContext,
                "com.android.internal.camera.flags.concert_mode")) {
            mExtensionList.add(CameraExtensionCharacteristics.EXTENSION_EYES_FREE_VIDEOGRAPHY);
        }
    }

    @After
    public void tearDown() throws Exception {
        mExtensionList.clear();
    }

    private void openDevice(String cameraId) throws Exception {
        mTestRule.setCamera(CameraTestUtils.openCamera(
                mTestRule.getCameraManager(), cameraId,
                mTestRule.getCameraListener(), mTestRule.getHandler()));
        mTestRule.getCollector().setCameraId(cameraId);
        mTestRule.setStaticInfo(new StaticMetadata(
                mTestRule.getCameraManager().getCameraCharacteristics(cameraId),
                StaticMetadata.CheckLevel.ASSERT, /*collector*/null));
    }

    private <T> void verifySupportedExtension(CameraExtensionCharacteristics chars, String cameraId,
            Integer extension, Class<T> klass) {
        List<Size> availableSizes = chars.getExtensionSupportedSizes(extension, klass);
        assertTrue(String.format("Supported extension %d on camera id: %s doesn't " +
                        "include any valid resolutions!", extension, cameraId),
                (availableSizes != null) && (!availableSizes.isEmpty()));
    }

    private <T> void verifySupportedSizes(CameraExtensionCharacteristics chars, String cameraId,
            Integer extension, Class<T> klass) throws Exception {
        verifySupportedExtension(chars, cameraId, extension, klass);
        try {
            openDevice(cameraId);
            List<Size> extensionSizes = chars.getExtensionSupportedSizes(extension, klass);
            List<Size> cameraSizes = Arrays.asList(
                    mTestRule.getStaticInfo().getAvailableSizesForFormatChecked(ImageFormat.PRIVATE,
                            StaticMetadata.StreamDirection.Output));
            for (Size extensionSize : extensionSizes) {
                assertTrue(String.format("Supported extension %d on camera id: %s advertises " +
                                " resolution %s unsupported by camera", extension, cameraId,
                        extensionSize), cameraSizes.contains(extensionSize));
            }
        } finally {
            mTestRule.closeDevice(cameraId);
        }
    }

    private void verifySupportedSizes(CameraExtensionCharacteristics chars, String cameraId,
            Integer extension, int format) throws Exception {
        List<Size> extensionSizes = chars.getExtensionSupportedSizes(extension, format);
        assertFalse(String.format("No available sizes for extension %d on camera id: %s " +
                "using format: %x", extension, cameraId, format), extensionSizes.isEmpty());
        try {
            openDevice(cameraId);
            List<Size> cameraSizes = Arrays.asList(
                    mTestRule.getStaticInfo().getAvailableSizesForFormatChecked(format,
                            StaticMetadata.StreamDirection.Output));
            for (Size extensionSize : extensionSizes) {
                assertTrue(String.format("Supported extension %d on camera id: %s advertises " +
                                " resolution %s unsupported by camera", extension, cameraId,
                        extensionSize), cameraSizes.contains(extensionSize));
            }
        } finally {
            mTestRule.closeDevice(cameraId);
        }
    }

    private <T> void verifyUnsupportedExtension(CameraExtensionCharacteristics chars,
            Integer extension, Class<T> klass) {
        try {
            chars.getExtensionSupportedSizes(extension, klass);
            fail("should get IllegalArgumentException due to unsupported extension");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't access Test API")
    public void testExtensionAvailability() throws Exception {
        boolean extensionsAdvertised = false;
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            ArrayList<Integer> unsupportedExtensions = new ArrayList<>(mExtensionList);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            if (!extensionsAdvertised && !supportedExtensions.isEmpty()) {
                extensionsAdvertised = true;
            }
            for (Integer extension : supportedExtensions) {
                verifySupportedExtension(extensionChars, id, extension, SurfaceTexture.class);
                unsupportedExtensions.remove(extension);
            }

            // Unsupported extension size queries must throw corresponding exception.
            for (Integer extension : unsupportedExtensions) {
                verifyUnsupportedExtension(extensionChars, extension, SurfaceTexture.class);
            }
        }
        boolean extensionsEnabledProp = PropertyUtil.areCameraXExtensionsEnabled();
        assertEquals("Extensions system property : " + extensionsEnabledProp + " does not match " +
                "with the advertised extensions: " + extensionsAdvertised, extensionsEnabledProp,
                extensionsAdvertised);
    }

    @Test
    public void testExtensionSizes() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                verifySupportedSizes(extensionChars, id, extension, SurfaceTexture.class);
                verifySupportedSizes(extensionChars, id, extension, ImageFormat.JPEG);
            }
        }
    }

    @Test
    public void testIllegalArguments() throws Exception {
        try {
            mTestRule.getCameraManager().getCameraExtensionCharacteristics("InvalidCameraId!");
            fail("should get IllegalArgumentException due to invalid camera id");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        for (String id : mTestRule.getCameraIdsUnderTest()) {
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                try {
                    extensionChars.getExtensionSupportedSizes(extension, ImageFormat.UNKNOWN);
                    fail("should get IllegalArgumentException due to invalid pixel format");
                } catch (IllegalArgumentException e) {
                    // Expected
                }

                try {
                    final class NotSupported {};
                    List<Size> ret = extensionChars.getExtensionSupportedSizes(extension,
                            NotSupported.class);
                    assertTrue("should get empty resolution list for unsupported " +
                            "surface type", ret.isEmpty());
                } catch (IllegalArgumentException e) {
                    fail("should not get IllegalArgumentException due to unsupported surface " +
                            "type");
                }
            }
        }
    }

    @Test
    public void testExtensionLatencyRanges() throws Exception {
        final int testFormat = ImageFormat.JPEG;
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            CameraExtensionCharacteristics chars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = chars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = chars.getExtensionSupportedSizes(extension, testFormat);
                for (Size sz : extensionSizes) {
                    Range<Long> latencyRange = chars.getEstimatedCaptureLatencyRangeMillis(
                            extension, sz, testFormat);
                    if (latencyRange != null) {
                        assertTrue("Negative range surface type", (latencyRange.getLower() > 0) &&
                                (latencyRange.getUpper() > 0));
                        assertTrue("Lower range value must be smaller compared to the upper",
                                (latencyRange.getLower() < latencyRange.getUpper()));
                    }
                }
            }
        }
    }

    @Test
    public void testExtensionRequestKeys() throws Exception {
        ArraySet<CaptureRequest.Key> extensionRequestKeys = new ArraySet<>();
        extensionRequestKeys.add(CaptureRequest.EXTENSION_STRENGTH);
        extensionRequestKeys.addAll(Arrays.asList(EYES_FREE_REQUEST_SET));
        extensionRequestKeys.addAll(Arrays.asList(EYES_FREE_AUTO_ZOOM_REQUEST_SET));

        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            CameraExtensionCharacteristics chars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = chars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                Set<CaptureRequest.Key> captureKeySet =
                        chars.getAvailableCaptureRequestKeys(extension);
                ArraySet<CaptureRequest.Key> captureKeys = new ArraySet<>(captureKeySet);
                // No repeating keys allowed
                assertEquals(captureKeys.size(), captureKeySet.size());
                // Jpeg quality and jpeg orientation must always be available
                assertTrue(captureKeys.contains(CaptureRequest.JPEG_QUALITY));
                assertTrue(captureKeys.contains(CaptureRequest.JPEG_ORIENTATION));
                // The extension request keys must always match or be a subset of the regular keys
                for (CaptureRequest.Key captureKey : captureKeys) {
                    String msg = String.format("Supported extension request key %s doesn't appear "
                            + " int the regular camera characteristics list of supported keys!",
                            captureKey.getName());
                    assertTrue(msg, staticMeta.areKeysAvailable(captureKey) ||
                            extensionRequestKeys.contains(captureKey));
                }

                // Ensure eyes-free specific keys are only supported for eyes-free extension
                if (extension
                        != CameraExtensionCharacteristics.EXTENSION_EYES_FREE_VIDEOGRAPHY) {
                    CameraTestUtils.checkKeysAreSupported(Arrays.asList(EYES_FREE_REQUEST_SET,
                            EYES_FREE_AUTO_ZOOM_REQUEST_SET), captureKeySet, false);
                }
            }
        }
    }

    @Test
    public void testExtensionResultKeys() throws Exception {
        ArraySet<CaptureResult.Key> extensionResultKeys = new ArraySet<>();
        extensionResultKeys.add(CaptureResult.EXTENSION_STRENGTH);
        extensionResultKeys.add(CaptureResult.EXTENSION_CURRENT_TYPE);
        extensionResultKeys.addAll(Arrays.asList(EYES_FREE_RESULT_SET));
        extensionResultKeys.addAll(Arrays.asList(EYES_FREE_AUTO_ZOOM_RESULT_SET));

        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            CameraExtensionCharacteristics chars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = chars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                Set<CaptureResult.Key> resultKeySet =
                        chars.getAvailableCaptureResultKeys(extension);
                if (resultKeySet.isEmpty()) {
                    // Extension capture result support is optional
                    continue;
                }

                ArraySet<CaptureResult.Key> resultKeys = new ArraySet<>(resultKeySet);
                ArraySet<String> resultKeyNames = new ArraySet<>(resultKeys.size());
                // No repeating keys allowed
                assertEquals(resultKeys.size(), resultKeySet.size());
                // Sensor timestamp, jpeg quality and jpeg orientation must always be available
                assertTrue(resultKeys.contains(CaptureResult.SENSOR_TIMESTAMP));
                assertTrue(resultKeys.contains(CaptureResult.JPEG_QUALITY));
                assertTrue(resultKeys.contains(CaptureResult.JPEG_ORIENTATION));
                // The extension result keys must always match or be a subset of the regular result
                // keys
                for (CaptureResult.Key resultKey : resultKeys) {
                    String msg = String.format("Supported extension result key %s doesn't appear "
                            + " in the regular camera characteristics list of supported keys!",
                            resultKey.getName());
                    assertTrue(msg, staticMeta.areKeysAvailable(resultKey) ||
                            extensionResultKeys.contains(resultKey));
                    resultKeyNames.add(resultKey.getName());
                }

                ArraySet<CaptureRequest.Key> captureKeys = new ArraySet<>(
                        chars.getAvailableCaptureRequestKeys(extension));
                for (CaptureRequest.Key requestKey : captureKeys) {
                    String msg = String.format("Supported extension request key %s doesn't appear "
                            + " in the corresponding supported extension result key list!",
                            requestKey.getName());
                    assertTrue(msg, resultKeyNames.contains(requestKey.getName()));
                }

                // Ensure eyes-free specific keys are only supported for eyes-free extension
                if (extension
                        != CameraExtensionCharacteristics.EXTENSION_EYES_FREE_VIDEOGRAPHY) {
                    CameraTestUtils.checkKeysAreSupported(Arrays.asList(EYES_FREE_RESULT_SET,
                            EYES_FREE_AUTO_ZOOM_RESULT_SET), resultKeySet, false);
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_EXTENSIONS_CHARACTERISTICS_GET)
    public void testExtensionGetCharacteristics() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            CameraExtensionCharacteristics chars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);

            List<Integer> supportedExtensions = chars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                Set<CameraCharacteristics.Key> keys = chars.getKeys(extension);
                for (CameraCharacteristics.Key key : keys) {
                    assertNotNull("Associated value for key cannot be null.",
                            chars.get(extension, key));
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_EXTENSIONS_CHARACTERISTICS_GET)
    public void testGetRequiredKeys() throws Exception {
        ArraySet<CameraCharacteristics.Key> requiredKeys = new ArraySet<>();
        requiredKeys.add(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        requiredKeys.add(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            CameraExtensionCharacteristics chars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);

            List<Integer> supportedExtensions = chars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                Set<CameraCharacteristics.Key> keys = chars.getKeys(extension);
                if (keys.isEmpty()) {
                    continue;
                }
                for (CameraCharacteristics.Key key : requiredKeys) {
                    assertTrue(keys.contains(key));
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_EXTENSIONS_CHARACTERISTICS_GET)
    public void testGetUnsupportedCharacteristics() throws Exception {
        for (String id : mTestRule.getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            Set<Integer> unsupportedCapabilities = new HashSet<>(Arrays.asList(
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
            ));

            CameraExtensionCharacteristics chars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);

            List<Integer> supportedExtensions = chars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                int[] availableCapabilities =
                        chars.get(extension, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (availableCapabilities != null) {
                    for (int c : availableCapabilities) {
                        assertFalse("Capabilitiy is not supported by extensions",
                                unsupportedCapabilities.contains(c));
                    }
                }
                StreamConfigurationMap map = chars.get(extension,
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assertNull("StreamConfigurationMap must not be present in get", map);
            }
        }
    }
}
