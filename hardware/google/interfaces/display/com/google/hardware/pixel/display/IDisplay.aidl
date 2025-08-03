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

package com.google.hardware.pixel.display;
import com.google.hardware.pixel.display.HistogramCapability;
import com.google.hardware.pixel.display.HistogramConfig;
import com.google.hardware.pixel.display.HistogramErrorCode;
import com.google.hardware.pixel.display.HbmState;
import com.google.hardware.pixel.display.LbeState;
import com.google.hardware.pixel.display.Weight;
import com.google.hardware.pixel.display.HistogramPos;
import com.google.hardware.pixel.display.PanelCalibrationStatus;
import com.google.hardware.pixel.display.Priority;
import android.hardware.common.NativeHandle;
import android.hardware.graphics.common.Rect;

@VintfStability
interface IDisplay {
    /**
     * Query Display High Brightness Mode Supported
     *
     * @return true if HBM is supported on this platform.
     *         If false, HBM is not supported.
     */
    boolean isHbmSupported();

    /**
     * Set Display High Brightness Mode
     *
     * @param state to be queried.
     */
    void setHbmState(in HbmState state);

    /**
     * Get Display High Brightness Mode State
     *
     * @return state of display high brightness mode
     */
    HbmState getHbmState();

    /**
     * Query Display Local Brightness Enhancement Supported
     *
     * @return true if LBE is supported on this platform.
     *         If false, LBE is not supported.
     */
    boolean isLbeSupported();

    /**
     * Set Display Local Brightness Enhancement
     *
     * @param state to be queried.
     */
    void setLbeState(in LbeState state);

    /**
     * Set Display Local Brightness Enhancement Ambient Light
     *
     * @param ambientLux the proper ambient light in Lux.
     *        The value is equal to zero or above zero.
     */
    void setLbeAmbientLight(in int ambientLux);

    /**
     * Get Display Local Brightness Enhancement State
     *
     * @return state of display local brightness enhancement
     */
    LbeState getLbeState();

    /**
     * Query Display Local High Brightness Mode Supported
     *
     * @return true if LHBM is supported on this platform.
     *         If false, LHBM is not supported.
     */
    boolean isLhbmSupported();

    /**
     * Set Display Local High Brightness Mode
     *
     * @param enabled true if LHBM should be enabled, false otherwise.
     */
    void setLhbmState(in boolean enabled);

    /**
     * Get Display Local High Brightness Mode State
     *
     * @return true if LHBM is ON, false otherwise.
     */
    boolean getLhbmState();

    /**
     * Set NativeHandle then server can fill the compensation
     * image into the native handle
     *
     * @param native_handle share the NativeHandle from client.
     * @param imageName the image name that define in client.
     * @return errno of setCompensationImageHandle
     */
    int setCompensationImageHandle(in NativeHandle native_handle, in String imageName);

    /**
     * Limit minimum idle display refresh rate. This is only relevant for displays
     * that can idle to a lower refresh rate than what is set by the platform. That is,
     * this API only controls kernel driver or panel self-driven refresh rate updates
     * it does not enforce the platform selected refresh rate (via setActiveConfig).
     *
     * @param fps The lowest refresh rate allowed. Setting to -1 means display
     *            should not switch to lower refresh rate while idle.
     * @return errno if there was a problem with the request, zero if successful
     */
    int setMinIdleRefreshRate(in int fps);

    /**
     * Throttle refresh rate switching such that switches don't happen earlier
     * than the provided delay in milliseconds
     *
     * @param delayMs Amount of time in milliseconds to wait before switching to
     *                 a new refresh rate
     * @return errno if there was a problem with the request, zero if successful
     */
    int setRefreshRateThrottle(in int delayMs);

    /**
     * Collects the luma histograms of the content displayed on screen.
     *
     *
     * @param  roi             input from client, is the regions of interest in the frames that
     *                         should be sampled to collect the luma values of the roi
     *                         The sample represents the most-recently posted frames
     * @param  weight          input from client, the weights for red(weight_r), green(weight_g)
     *                         and blue(weight_b) colors, which is used in luma calculation formula.
     *                         The formula:
     *                               luma = weight_r * red + weight_g * green + weight_b * blue
     *                         The weight_r + weight_g + weight_b = 1024
     * @param pos              histogram sample position, could be PRE_DQE or POST_DQE
     * @param priority         input from client, is the priority of the call.
     *                         Priority is a num with two values {normal, priority}
     * @param histogrambuffer  output from histogram server, is a 256 * 16 bits buffer to store the
     *                         luma counts return by the histogram hardware
     * @return error           NONE = 0 upon success. Otherwise
     *                         BAD_ROI = 1  when roi passed in is not in correct format
     *                         BAD_WEIGHT = 2 when weight passed in is not in correct format
     *                         BAD_POSITION = 3 when the histogram sampling position is incorrect
     *                         BAD_PRIORITY = 4 when the priority input is not in correct format
     *                         ENABLE_HIST_ERROR = 5 when enable histogram property error
     *                         DISABLE_HIST_ERROR = 6 when disable histogram property error
     *                         BAD_HIST_DATA = 7 when there is error to collect histogram data
     *                         DRM_PLAYING = 8 when there is DRM content playing,
     *                                       for content protection, no histogram data sampled
     *                         DISPLAY_POWEROFF = 9 when the display is power off,
     *                                       no histogram data is sampled
     *                         API_DEPRECATED = 10 when the API is deprecated, should use the multi
     *                                             channel API instead (registerHistogram and
     *                                             queryHistogram)
     */
    HistogramErrorCode histogramSample(in Rect roi, in Weight weight, in HistogramPos pos,
                                       in Priority pri, out char[] histogrambuffer);

    /**
     * Get the panel calibration status.
     *
     * @return status of panel calibration.
     */
    PanelCalibrationStatus getPanelCalibrationStatus();


   /**
     * Query Dim Brightness Mode Supported. The dim brightness is a lower minimum brightness,
     * which is not listed in the brightness table. This API is to query whether the dim
     * brightness mode is supported or not.
     *
     * @return true if Dim Brightness Mode is supported on this platform, false otherwise.
     */
    boolean isDbmSupported();

    /**
     * Set Dim Brightness Mode. If enabled, it will set the dim brightness when reaching the
     * minimum brightness which can be different from the actual minimum brightness from the
     * brightness table we can achieve.
     *
     * @param enabled true to enable, false to disable the mode.
     */
    void setDbmState(in boolean enabled);

    /**
     * Set Display Peak Refresh Rate
     *
     * @param rate (frame per second).
     */
    void setPeakRefreshRate(in int rate);

    /**
     * Set Low Power Mode
     *
     * @param enabled.
     */
    void setLowPowerMode(in boolean enabled);

    /**
     * Query Operation Rate supporting status. Only if the display panel supports
     * operation rate switching, peak refresh rate and low power mode status from Android
     * framework are needed to forward into lower display software which manages operation
     * rate.
     *
     * @return true if operation rate switch is supported.
     *         false if not supported.
     */
    boolean isOperationRateSupported();

    /**
     * Return the histogram capability for the platform.
     *
     * @return HistogramCapability which describes the histogram capability for the platform.
     */
    HistogramCapability getHistogramCapability();

    /**
     * Register the histogram sampling configuration, and allocate a histogram hardware channel
     * from the histogram server. The DPU hardware will continuously sample the histogram
     * data. The histogram channel can be disabled (see unregisterHistogram) to save
     * power consumption.
     *
     * @param token            input from client, is the handle for the following histogram
     *                         operations which would be used to identify the client and register
     *                         a binderdied callback to release the histogram channel automatically
     *                         when client crashes.
     *
     * @param histogramConfig  input from client, is the histogram configuration.
     *
     * @return error           NONE, upon success. Otherwise
     *                         BAD_TOKEN, when the token passed in has error or already registered
     *                         BAD_ROI, when roi passed in is not in correct format
     *                         BAD_WEIGHT, when weight passed in is not in correct format
     *                         BAD_POSITION, when the histogram sampling position is incorrect
     *                         CONFIG_HIST_ERROR, when enable histogram property error
     *                         NO_CHANNEL_AVAILABLE, when all of the histogram channels are in used
     */
    HistogramErrorCode registerHistogram(in IBinder token, in HistogramConfig histogramConfig);

    /**
     * Query the latest histogram data.
     *
     * @param token            input from client, is the handle registered via registerHistogram
     *                         which would be used to identify the client.
     *
     * @param histogramBuffer  output from histogram server, is a 256 * 16 bits buffer to store the
     *                         luma counts return by the histogram hardware.
     *
     * @return error           NONE, upon success. Otherwise
     *                         BAD_TOKEN, when the token passed in has error or not registered
     *                                    before
     *                         DISPLAY_POWEROFF, when the display is power off
     *                         BAD_HIST_DATA, when there is error to collect histogram data
     *                         DRM_PLAYING, when there is secure content playing,
     *                                      for content protection, no histogram data sampled
     */
    HistogramErrorCode queryHistogram(in IBinder token, out char[] histogramBuffer);

    /**
     * change the histogram channel configuration. The DPU hardware will continuously sample the
     * histogram data. The histogram channel can be disabled (see unregisterHistogram) to save
     * power consumption.
     *
     * @param token            input from client, is the handle registered via registerHistogram
     *                         which would be used to identify the client.
     *
     * @param histogramConfig  input from client, is the histogram configuration.
     *
     * @return error           NONE, upon success. Otherwise
     *                         BAD_TOKEN, when the token passed in has error or not registered
     *                                    before
     *                         BAD_ROI, when roi passed in is not in correct format
     *                         BAD_WEIGHT, when weight passed in is not in correct format
     *                         BAD_POSITION, when the histogram sampling position is incorrect
     *                         CONFIG_HIST_ERROR, when change histogram property error
     */
    HistogramErrorCode reconfigHistogram(in IBinder token, in HistogramConfig histogramConfig);

    /**
     * Disable the histogram channel to release the hardware resource and save power consumption.
     *
     * @param token            input from client, is the handle registered via registerHistogram
     *                         which would be used to identify the client.
     *
     * @return error           NONE, upon success. Otherwise
     *                         BAD_TOKEN, when the token passed in has error or not registered
     *                                    before
     */
    HistogramErrorCode unregisterHistogram(in IBinder token);
}
