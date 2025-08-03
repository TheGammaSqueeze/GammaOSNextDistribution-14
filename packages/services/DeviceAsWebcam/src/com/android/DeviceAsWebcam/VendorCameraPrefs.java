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

package com.android.DeviceAsWebcam;

import android.content.Context;
import android.util.ArrayMap;
import android.util.JsonReader;
import android.util.Log;
import android.util.Range;

import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class for providing camera related information overridden by vendors through resource overlays.
 */
public class VendorCameraPrefs {
    private static final String TAG = "VendorCameraPrefs";

    public static class PhysicalCameraInfo {
        public final String physicalCameraId;
        // Camera category which might help UI labelling while cycling through camera ids.
        public final CameraCategory cameraCategory;
        @Nullable
        public final Range<Float> zoomRatioRange;

        PhysicalCameraInfo(String physicalCameraIdI, CameraCategory cameraCategoryI,
                @Nullable Range<Float> zoomRatioRangeI) {
            physicalCameraId = physicalCameraIdI;
            cameraCategory = cameraCategoryI;
            zoomRatioRange = zoomRatioRangeI;
        }
    }

    public VendorCameraPrefs(ArrayMap<String, List<PhysicalCameraInfo>> logicalToPhysicalMap,
            List<String> ignoredCameraList) {
        mLogicalToPhysicalMap = logicalToPhysicalMap;
        mIgnoredCameraList = ignoredCameraList;
    }

    @Nullable
    public List<PhysicalCameraInfo> getPhysicalCameraInfos(String cameraId) {
        return mLogicalToPhysicalMap.get(cameraId);
    }

    /**
     * Returns the custom physical camera zoom ratio range. Returns {@code null} if no custom value
     * can be found.
     *
     * <p>This is used to specify the available zoom ratio range when the working camera is a
     * physical camera under a logical camera.
     */
    @Nullable
    public Range<Float> getPhysicalCameraZoomRatioRange(CameraId cameraId) {
        PhysicalCameraInfo physicalCameraInfo = getPhysicalCameraInfo(cameraId);
        return physicalCameraInfo != null ? physicalCameraInfo.zoomRatioRange : null;
    }

    /**
     * Retrieves the {@link CameraCategory} if it is specified by the vendor camera prefs data.
     */
    public CameraCategory getCameraCategory(CameraId cameraId) {
        PhysicalCameraInfo physicalCameraInfo = getPhysicalCameraInfo(cameraId);
        return physicalCameraInfo != null ? physicalCameraInfo.cameraCategory
                : CameraCategory.UNKNOWN;
    }

    /**
     * Returns the {@link PhysicalCameraInfo} corresponding to the specified camera id. Returns
     * null if no item can be found.
     */
    private PhysicalCameraInfo getPhysicalCameraInfo(CameraId cameraId) {
        List<PhysicalCameraInfo> physicalCameraInfos = getPhysicalCameraInfos(
                cameraId.mainCameraId);

        if (physicalCameraInfos != null) {
            for (PhysicalCameraInfo physicalCameraInfo : physicalCameraInfos) {
                if (Objects.equals(physicalCameraInfo.physicalCameraId,
                        cameraId.physicalCameraId)) {
                    return physicalCameraInfo;
                }
            }
        }

        return null;
    }

    /**
     * Returns the ignored camera list.
     */
    public List<String> getIgnoredCameraList() {
        return mIgnoredCameraList;
    }

    // logical camera -> PhysicalCameraInfo. The list of PhysicalCameraInfos
    // is in order of preference for the physical streams that must be used by
    // DeviceAsWebcam service.
    private final ArrayMap<String, List<PhysicalCameraInfo>> mLogicalToPhysicalMap;
    // The ignored camera list.
    private final List<String> mIgnoredCameraList;

    /**
     * Converts an InputStream into a String
     *
     * @param in InputStream
     * @return InputStream converted to a String
     */
    private static String inputStreamToString(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.lines().forEach(builder::append);
        }
        return builder.toString();
    }

    /**
     * Returns an instance of {@link VendorCameraPrefs} that does not provide Physical
     * Camera Mapping. Used for when we want to force CameraController to use the logical
     * cameras. The returned VendorCameraPrefs still honors ignored cameras retrieved from
     * {@link #getIgnoredCameralist}.
     */
    public static VendorCameraPrefs createEmptyVendorCameraPrefs(Context context) {
        List<String> ignoredCameraList = getIgnoredCameralist(context);
        return new VendorCameraPrefs(new ArrayMap<>(), ignoredCameraList);
    }

    /**
     * Reads the vendor camera preferences from the custom JSON files.
     *
     * @param context Application context which can be used to retrieve resources.
     */
    public static VendorCameraPrefs getVendorCameraPrefsFromJson(Context context) {
        ArrayMap<String, Range<Float>> zoomRatioRangeInfo = getZoomRatioRangeInfo(context);
        ArrayMap<String, List<PhysicalCameraInfo>> logicalToPhysicalMap =
                createLogicalToPhysicalMap(context, zoomRatioRangeInfo);
        List<String> ignoredCameraList = getIgnoredCameralist(context);
        return new VendorCameraPrefs(logicalToPhysicalMap, ignoredCameraList);
    }

    /**
     * Creates a logical to physical camera map by parsing the physical camera mapping info from
     * the input which is expected to be a valid JSON stream.
     *
     * @param context            Application context which can be used to retrieve resources.
     * @param zoomRatioRangeInfo A map contains the physical camera zoom ratio range info. This is
     *                           used to created the PhysicalCameraInfo.
     */
    private static ArrayMap<String, List<PhysicalCameraInfo>> createLogicalToPhysicalMap(
            Context context, ArrayMap<String, Range<Float>> zoomRatioRangeInfo) {
        InputStream in = context.getResources().openRawResource(R.raw.physical_camera_mapping);
        ArrayMap<String, List<PhysicalCameraInfo>> logicalToPhysicalMap = new ArrayMap<>();
        try {
            JSONObject physicalCameraMapping = new JSONObject(inputStreamToString(in));
            for (String logCam : physicalCameraMapping.keySet()) {
                JSONObject physicalCameraObj = physicalCameraMapping.getJSONObject(logCam);
                List<PhysicalCameraInfo> physicalCameraIds = new ArrayList<>();
                for (String physCam : physicalCameraObj.keySet()) {
                    String identifier = CameraId.createIdentifier(logCam, physCam);
                    physicalCameraIds.add(new PhysicalCameraInfo(physCam,
                            convertLabelToCameraCategory(physicalCameraObj.getString(physCam)),
                            zoomRatioRangeInfo.get(identifier)));
                }
                logicalToPhysicalMap.put(logCam, physicalCameraIds);
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to parse JSON", e);
        }
        return logicalToPhysicalMap;
    }

    /**
     * Converts the label string to corresponding {@link CameraCategory}.
     */
    private static CameraCategory convertLabelToCameraCategory(String label) {
        return switch (label) {
            case "W" -> CameraCategory.WIDE_ANGLE;
            case "UW" -> CameraCategory.ULTRA_WIDE;
            case "T" -> CameraCategory.TELEPHOTO;
            case "S" -> CameraCategory.STANDARD;
            case "O" -> CameraCategory.OTHER;
            default -> CameraCategory.UNKNOWN;
        };
    }

    /**
     * Obtains the zoom ratio range info from the input which is expected to be a valid
     * JSON stream.
     *
     * @param context Application context which can be used to retrieve resources.
     */
    private static ArrayMap<String, Range<Float>> getZoomRatioRangeInfo(Context context) {
        InputStream in = context.getResources().openRawResource(
                R.raw.physical_camera_zoom_ratio_ranges);
        ArrayMap<String, Range<Float>> zoomRatioRangeInfo = new ArrayMap<>();
        try {
            JSONObject physicalCameraMapping = new JSONObject(inputStreamToString(in));
            for (String logCam : physicalCameraMapping.keySet()) {
                JSONObject physicalCameraObj = physicalCameraMapping.getJSONObject(logCam);
                for (String physCam : physicalCameraObj.keySet()) {
                    String identifier = CameraId.createIdentifier(logCam, physCam);
                    JSONArray zoomRatioRangeArray = physicalCameraObj.getJSONArray(physCam);
                    Preconditions.checkArgument(zoomRatioRangeArray.length() == 2,
                            "Incorrect number of values in zoom ratio range. Expected: %d, Found:"
                                    + " %d", 2, zoomRatioRangeArray.length());
                    boolean isAvailable = zoomRatioRangeArray.getDouble(0) > 0.0
                            && zoomRatioRangeArray.getDouble(1) > 0.0
                            && zoomRatioRangeArray.getDouble(0) < zoomRatioRangeArray.getDouble(1);
                    Preconditions.checkArgument(isAvailable,
                            "Incorrect zoom ratio range values. All values should be > 0.0 and "
                                    + "the first value should be lower than the second value.");
                    zoomRatioRangeInfo.put(identifier,
                            Range.create((float) zoomRatioRangeArray.getDouble(0),
                                    (float)zoomRatioRangeArray.getDouble(1)));
                }
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to parse JSON", e);
        }
        return zoomRatioRangeInfo;
    }

    /**
     * Retrieves the ignored camera list from the input which is expected to be a valid JSON stream.
     */
    private static List<String> getIgnoredCameralist(Context context) {
        List<String> ignoredCameras = new ArrayList<>();
        try(InputStream in = context.getResources().openRawResource(R.raw.ignored_cameras);
            JsonReader jsonReader = new JsonReader(new InputStreamReader(in))) {
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                String node = jsonReader.nextString();
                ignoredCameras.add(node);
            }
            jsonReader.endArray();
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse JSON. Running with a partial ignored camera list", e);
        }

        return ignoredCameras;
    }
}
