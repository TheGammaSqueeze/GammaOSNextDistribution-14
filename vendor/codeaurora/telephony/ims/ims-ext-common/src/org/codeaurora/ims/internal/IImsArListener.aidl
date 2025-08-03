/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims.internal;

import android.view.Surface;

/**
 * Used by client application to get the result from lower layer by
 * communicating with vendor.
 * {@hide}
 */
oneway interface IImsArListener {

   /*
    * Implemented by the AR call SDK. Used by vendor to propagate
    * updated surface to the client
    * @param phoneId - Denotes the phoneId
    * @param surface - Updated recording surface from VTLib
    * @param width - Updated width for AR call SDK
    * @param height - Updated height for AR call SDK
    * @param cameraId - camera Id for AR call.
    */
    void onRecordingSurfaceChanged(int phoneId, in Surface surface,
            int width, int height, String cameraId);

    /*
     * Implemented by the AR call SDK. Used by vendor to propagate
     * update fps to client.
     * @param phoneId - Denotes the phoneId
     * @param rate - fps.
     * @param cameraId - camera Id for AR call.
     */
    void onRecorderFrameRateChanged(int phoneId, int rate, String cameraId);

    /*
     * Implemented by the AR call SDK. Used by vendor to notify AR SDK
     * to start recording.
     * @param phoneId - Denotes the phoneId
     * @param cameraId - camera Id for AR call.
     */
    void onRecordingEnabled(int phoneId, String cameraId);

    /*
     * Implemented by the AR call SDK. Used by vendor to notify AR SDK
     * to stop recording.
     * @param phoneId - Denotes the phoneId
     * @param cameraId - camera Id for AR call.
     */
    void onRecordingDisabled(int phoneId, String cameraId);
}
