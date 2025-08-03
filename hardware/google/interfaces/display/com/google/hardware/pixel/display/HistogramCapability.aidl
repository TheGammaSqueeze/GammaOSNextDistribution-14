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

@VintfStability
parcelable HistogramCapability {
    /**
     * supportMultiChannel is true means the server supports multi channels
     * histogram request, the client should use queryHistogram API. Otherwise,
     * the client need to use the legacy histogramSample API.
     */
    boolean supportMultiChannel;

    /**
     * channelCount represents the number of available histogram channels for
     * the client which would be less than the number of histogram hardware
     * channels when driver reserves the histogram channel internally.
     */
    int channelCount;

    /**
     * fullResolutionWidth represents the x component of the full resolution.
     * The roi should be calculated based on the full resolution.
     * Constraints: HistogramConfig.roi.right <= fullResolutionWidth
     */
    int fullResolutionWidth;

    /**
     * fullResolutionHeight represents the y component of the full resolution.
     * The roi should be calculated based on the full resolution.
     * Constraints: HistogramConfig.roi.bottom <= fullResolutionHeight
     */
    int fullResolutionHeight;

    /**
     * supportSamplePosList lists the supported histogram sample position.
     */
    HistogramSamplePos[] supportSamplePosList;

    /**
     * supportBlockingRoi is true means the server support blocking ROI. Otherwise,
     * the client should not use the blocking ROI.
     */
    boolean supportBlockingRoi;
}
