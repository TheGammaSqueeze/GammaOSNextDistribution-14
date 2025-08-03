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

package com.google.hardware.pixel.display;
import com.google.hardware.pixel.display.HistogramSamplePos;
import com.google.hardware.pixel.display.Weight;
import android.hardware.graphics.common.Rect;

@VintfStability
parcelable HistogramConfig {
    /**
     * roi is the region of interest in the frames that should be sampled
     * to collect the luma values. Rect is represented by the (int) coordinates
     * of its 4 edges (left, top, right, bottom). The coordinates should be
     * calculated based on the full resolution which is described by
     * getHistogramCapability. Note that the right and bottom coordinates are
     * exclusive.
     * Note: (0, 0, 0, 0) means the ROI is disabled, histogram hardware will
     * capture the region inside the entire screen but outside the blocking ROI.
     */
    Rect roi;

    /**
     * The weights for red (weight_r), green (weight_g) and blue (weight_b)
     * colors. The weights are used in luma calculation formula:
     *      luma = weight_r * red + weight_g * green + weight_b * blue
     * weight_r + weight_g + weight_b should be equal to 1024
     */
    Weight weights;

    /**
     * samplePos is the histogram sample position, could be PRE_POSTPROC
     * (before post processing) or POST_POSTPROC (after post processing).
     */
    HistogramSamplePos samplePos;

    /**
     * blockingRoi is the ROI blocking region. The histogram inside blockingRoi
     * is not captured even that region lies within the roi. Rect is represented
     * by the (int) coordinates of its 4 edges (left, top, right, bottom).
     * The coordinates should be calculated based on the full resolution which
     * is described by getHistogramCapability. Note that the right and bottom
     * coordinates are exclusive.
     * Note: (0, 0, 0, 0) means the blocking ROI is disabled.
     */
    @nullable Rect blockingRoi;
}
