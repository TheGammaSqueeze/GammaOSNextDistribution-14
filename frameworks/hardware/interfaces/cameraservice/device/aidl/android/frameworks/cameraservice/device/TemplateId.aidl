/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.frameworks.cameraservice.device;

/**
 * TemplateId to be used with createDefaultRequest
 */
@VintfStability
@Backing(type="int")
enum TemplateId {
    /**
     * Create a request suitable for a camera preview window. Specifically, this
     * means that high frame rate is given priority over the highest-quality
     * post-processing.
     */
    PREVIEW = 1,
    /**
     * Create a request suitable for still image capture. Specifically, this
     * means prioritizing image quality over frame rate
     */
    STILL_CAPTURE = 2,
    /**
     * Create a request suitable for video recording. Specifically, this means
     * that a stable frame rate is used, and post-processing is set for
     * recording quality.
     */
    RECORD = 3,
    /**
     * Create a request suitable for still image capture while recording
     * video. Specifically, this means maximizing image quality without
     * disrupting the ongoing recording.
     */
    VIDEO_SNAPSHOT = 4,
    /**
     * Create a request suitable for zero shutter lag still capture. This means
     * means maximizing image quality without compromising preview frame rate.
     */
    ZERO_SHUTTER_LAG = 5,
    /**
     * A basic template for direct application control of capture
     * parameters. All automatic control is disabled (auto-exposure, auto-white
     * balance, auto-focus), and post-processing parameters are set to preview
     * quality. The manual capture parameters (exposure, sensitivity, and so on)
     * are set to reasonable defaults, but should be overridden by the
     * application depending on the intended use case.
     */
    MANUAL = 6,
}
