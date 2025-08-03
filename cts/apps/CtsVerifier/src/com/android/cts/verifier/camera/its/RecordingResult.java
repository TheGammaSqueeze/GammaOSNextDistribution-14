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

package com.android.cts.verifier.camera.its;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Convenience class to record certain fields of a CaptureResult. */
public class RecordingResult {
    public static final List<CaptureResult.Key<?>> PREVIEW_RESULT_TRACKED_KEYS = List.of(
            CaptureResult.CONTROL_ZOOM_RATIO,
            CaptureResult.LENS_FOCAL_LENGTH,
            CaptureResult.LENS_FOCUS_DISTANCE,
            CaptureResult.SCALER_CROP_REGION,
            CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID,
            CaptureResult.LENS_INTRINSIC_CALIBRATION
    );

    HashMap<CaptureResult.Key<?>, Object> mMap;

    public RecordingResult() {
        mMap = new HashMap<>();
    }
    public void addKey(TotalCaptureResult result, CaptureResult.Key<?> key) {
        mMap.put(key, result.get(key));
    }
    public void addKeys(TotalCaptureResult result,
            Iterable<CaptureResult.Key<?>> keys) {
        for (CaptureResult.Key<?> k : keys) {
            this.addKey(result, k);
        }
    }
    public Set<CaptureResult.Key<?>> getKeys() {
        return mMap.keySet();
    }
    public <T> T getResult(CaptureResult.Key<T> key) {
        return (T) mMap.get(key);
    }
}