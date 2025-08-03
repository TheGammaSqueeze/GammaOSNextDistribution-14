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

import android.frameworks.cameraservice.device.OutputConfiguration;
import android.frameworks.cameraservice.device.StreamConfigurationMode;

@VintfStability
parcelable SessionConfiguration {
    /**
     * A vector containing all output configurations
     */
    OutputConfiguration[] outputStreams;
    /**
     * Input stream width
     *
     * Note: this must be <= 0 if there is no input stream.
     */
    int inputWidth;
    /**
     * Input stream height
     *
     * Note: this must be <= 0 if there is no input stream.
     */
    int inputHeight;
    /**
     * Input stream format
     *
     * Note: this must be one of the AIMAGE_FORMATS defined in
     * frameworks/av/media/ndk/include/media/NdkImage.h.
     */
    int inputFormat;
    /**
     * Operation mode of camera device
     */
    StreamConfigurationMode operationMode;
}
