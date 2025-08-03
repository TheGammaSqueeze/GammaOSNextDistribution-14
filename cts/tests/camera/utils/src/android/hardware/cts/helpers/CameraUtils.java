/*
 * Copyright 2014 The Android Open Source Project
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

package android.hardware.cts.helpers;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.devicestate.DeviceStateManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.TextureView;

import androidx.test.InstrumentationRegistry;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class containing helper functions for the Camera CTS tests.
 */
public class CameraUtils {
    private static final float FOCAL_LENGTH_TOLERANCE = .01f;


    private static final String TAG = "CameraUtils";
    private static final long SHORT_SLEEP_WAIT_TIME_MS = 100;

    /**
     * Returns {@code true} if this device only supports {@code LEGACY} mode operation in the
     * Camera2 API for the given camera ID.
     *
     * @param context {@link Context} to access the {@link CameraManager} in.
     * @param cameraId the ID of the camera device to check.
     * @return {@code true} if this device only supports {@code LEGACY} mode.
     */
    public static boolean isLegacyHAL(Context context, int cameraId) throws Exception {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String cameraIdStr = manager.getCameraIdListNoLazy()[cameraId];
        return isLegacyHAL(manager, cameraIdStr);
    }

    /**
     * Returns {@code true} if this device only supports {@code LEGACY} mode operation in the
     * Camera2 API for the given camera ID.
     *
     * @param manager The {@link CameraManager} used to retrieve camera characteristics.
     * @param cameraId the ID of the camera device to check.
     * @return {@code true} if this device only supports {@code LEGACY} mode.
     */
    public static boolean isLegacyHAL(CameraManager manager, String cameraIdStr) throws Exception {
        CameraCharacteristics characteristics =
                manager.getCameraCharacteristics(cameraIdStr);

        return characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    /**
     * Returns {@code true} if the Camera.Parameter and Camera.Info arguments describe a similar
     * camera as the CameraCharacteristics.
     *
     * @param params Camera.Parameters to use for matching.
     * @param info Camera.CameraInfo to use for matching.
     * @param ch CameraCharacteristics to use for matching.
     * @return {@code true} if the arguments describe similar camera devices.
     */
    public static boolean matchParametersToCharacteristics(Camera.Parameters params,
            Camera.CameraInfo info, CameraCharacteristics ch) {
        Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
        switch (facing.intValue()) {
            case CameraMetadata.LENS_FACING_EXTERNAL:
                if (info.facing != Camera.CameraInfo.CAMERA_FACING_FRONT &&
                    info.facing != Camera.CameraInfo.CAMERA_FACING_BACK) {
                    return false;
                }
                break;
            case CameraMetadata.LENS_FACING_FRONT:
                if (info.facing != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    return false;
                }
                break;
            case CameraMetadata.LENS_FACING_BACK:
                if (info.facing != Camera.CameraInfo.CAMERA_FACING_BACK) {
                    return false;
                }
                break;
            default:
                return false;
        }

        Integer orientation = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (orientation.intValue() != info.orientation) {
            return false;
        }

        StaticMetadata staticMeta = new StaticMetadata(ch);
        boolean legacyHasFlash = params.getSupportedFlashModes() != null;
        if (staticMeta.hasFlash() != legacyHasFlash) {
            return false;
        }

        boolean isExternal = (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL);
        boolean hasValidMinFocusDistance = staticMeta.areKeysAvailable(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        boolean fixedFocusExternal = isExternal && !hasValidMinFocusDistance;
        boolean hasFocuser = staticMeta.hasFocuser() && !fixedFocusExternal;
        List<String> legacyFocusModes = params.getSupportedFocusModes();
        boolean legacyHasFocuser = !((legacyFocusModes.size() == 1) &&
                (legacyFocusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)));
        if (hasFocuser != legacyHasFocuser) {
            return false;
        }

        if (staticMeta.isVideoStabilizationSupported() !=
                params.isVideoStabilizationSupported()) {
            return false;
        }

        float legacyFocalLength = params.getFocalLength();
        if (ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) != null) {
            float [] focalLengths = staticMeta.getAvailableFocalLengthsChecked();
            boolean found = false;
            for (float focalLength : focalLengths) {
                if (Math.abs(focalLength - legacyFocalLength) <= FOCAL_LENGTH_TOLERANCE) {
                    found = true;
                    break;
                }
            }
            return found;
        } else if (legacyFocalLength != -1.0f) {
            return false;
        }

        return true;
    }

    /**
     * Returns {@code true} if this device only supports {@code EXTERNAL} mode operation in the
     * Camera2 API for the given camera ID.
     *
     * @param context {@link Context} to access the {@link CameraManager} in.
     * @param cameraId the ID of the camera device to check.
     * @return {@code true} if this device only supports {@code LEGACY} mode.
     */
    public static boolean isExternal(Context context, int cameraId) throws Exception {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        Camera camera = null;
        Camera.Parameters params = null;
        Camera.CameraInfo info = new Camera.CameraInfo();
        try {
            Camera.getCameraInfo(cameraId, info);
            camera = Camera.open(cameraId);
            params = camera.getParameters();
        } finally {
            if (camera != null) {
                camera.release();
            }
        }

        String [] cameraIdList = manager.getCameraIdList();
        CameraCharacteristics characteristics =
                manager.getCameraCharacteristics(cameraIdList[cameraId]);

        if (!matchParametersToCharacteristics(params, info, characteristics)) {
            boolean found = false;
            for (String id : cameraIdList) {
                characteristics = manager.getCameraCharacteristics(id);
                if (matchParametersToCharacteristics(params, info, characteristics)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL;
    }

    /**
     * Shared size comparison method used by size comparators.
     *
     * <p>Compares the number of pixels it covers.If two the areas of two sizes are same, compare
     * the widths.</p>
     */
     public static int compareSizes(int widthA, int heightA, int widthB, int heightB) {
        long left = widthA * (long) heightA;
        long right = widthB * (long) heightB;
        if (left == right) {
            left = widthA;
            right = widthB;
        }
        return (left < right) ? -1 : (left > right ? 1 : 0);
    }

    /**
     * Size comparator that compares the number of pixels it covers.
     *
     * <p>If two the areas of two sizes are same, compare the widths.</p>
     */
    public static class LegacySizeComparator implements Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return compareSizes(lhs.width, lhs.height, rhs.width, rhs.height);
        }
    }

    public static String getOverrideCameraId() {
        Bundle bundle = InstrumentationRegistry.getArguments();
        return bundle.getString("camera-id");
    }

    public static int[] deriveCameraIdsUnderTest() throws Exception {
        String overrideId = getOverrideCameraId();
        int numberOfCameras = Camera.getNumberOfCameras();
        int[] cameraIds;
        if (overrideId == null) {
            cameraIds = IntStream.range(0, numberOfCameras).toArray();
        } else {
            int overrideCameraId = Integer.parseInt(overrideId);
            if (overrideCameraId >= 0 && overrideCameraId < numberOfCameras) {
                cameraIds = new int[]{overrideCameraId};
            } else {
                cameraIds = new int[]{};
            }
        }
        return cameraIds;
    }

    /**
     * Wait until the SurfaceTexture available from the TextureView, then return it.
     * Return null if the wait times out.
     *
     * @param timeOutMs The timeout value for the wait
     * @return The available SurfaceTexture, return null if the wait times out.
    */
    public static SurfaceTexture getAvailableSurfaceTexture(long timeOutMs, TextureView view) {
        long waitTime = timeOutMs;

        while (!view.isAvailable() && waitTime > 0) {
            long startTimeMs = SystemClock.elapsedRealtime();
            SystemClock.sleep(SHORT_SLEEP_WAIT_TIME_MS);
            waitTime -= (SystemClock.elapsedRealtime() - startTimeMs);
        }

        if (view.isAvailable()) {
            return view.getSurfaceTexture();
        } else {
            Log.w(TAG, "Wait for SurfaceTexture available timed out after " + timeOutMs + "ms");
            return null;
        }
    }

    /**
     * Uses {@link DeviceStateManager} to determine if the device is foldable or not. It relies on
     * the OEM exposing supported states, and setting
     * com.android.internal.R.array.config_foldedDeviceStates correctly with the folded states.
     *
     * @return true is the device is a foldable; false otherwise
     */
    public static boolean isDeviceFoldable(Context mContext) {
        DeviceStateManager deviceStateManager =
                mContext.getSystemService(DeviceStateManager.class);
        if (deviceStateManager == null) {
            Log.w(TAG, "Couldn't locate DeviceStateManager to detect if the device is foldable"
                    + " or not. Defaulting to not-foldable.");
            return false;
        }
        Set<Integer> supportedStates = Arrays.stream(
                deviceStateManager.getSupportedStates()).boxed().collect(Collectors.toSet());

        Resources systemRes = Resources.getSystem();
        int foldedStatesArrayIdentifier = systemRes.getIdentifier("config_foldedDeviceStates",
                "array", "android");
        int[] foldedDeviceStates = systemRes.getIntArray(foldedStatesArrayIdentifier);

        // Device is a foldable if supportedStates contains any state in foldedDeviceStates
        return Arrays.stream(foldedDeviceStates).anyMatch(supportedStates::contains);
    }

    /**
     * Returns Gets the supported video frame sizes that can be used by MediaRecorder.
     *
     * @param camera Camera for which to get the supported video sizes.
     * @return a list of Size object if camera has separate preview and video output;
     * in case there isn't a separate video/preview size option,
     * it returns the preview sizes.
     */
    public static List<Size> getSupportedVideoSizes(Camera camera) {
        Parameters parameters = camera.getParameters();
        assertNotNull("Camera did not provide parameters", parameters);
        List<Size> videoSizes = parameters.getSupportedVideoSizes();

        if (videoSizes == null) {
            videoSizes = parameters.getSupportedPreviewSizes();
            assertNotNull(videoSizes);
        }

        return videoSizes;
    }
}
