/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims;

import org.codeaurora.ims.internal.IImsArListener;
import android.view.Surface;

public abstract class ImsArListenerBase {

    private final class ArListener extends IImsArListener.Stub {

        public void onRecordingSurfaceChanged(int phoneId, Surface surface,
                int width, int height, String cameraId) {
            ImsArListenerBase.this.
                    onRecordingSurfaceChanged(phoneId, surface, width, height, cameraId);
        }

        public void onRecorderFrameRateChanged(int phoneId, int rate, String cameraId) {
            ImsArListenerBase.this.onRecorderFrameRateChanged(phoneId, rate, cameraId);
        }

        public void onRecordingEnabled(int phoneId, String cameraId) {
            ImsArListenerBase.this.onRecordingEnabled(phoneId, cameraId);
        }

        public void onRecordingDisabled(int phoneId, String cameraId) {
            ImsArListenerBase.this.onRecordingDisabled(phoneId, cameraId);
        }
    }

    private ArListener mListener;

    public IImsArListener getBinder() {
        if (mListener == null) {
            mListener = new ArListener();
        }
        return mListener;
    }

    protected void onRecordingSurfaceChanged(int phoneId, Surface surface,
            int width, int height, String cameraId){
        // no-op
    }

    protected void onRecorderFrameRateChanged(int phoneId, int rate, String cameraId) {
        // no-op
    }

    protected void onRecordingEnabled(int phoneId, String cameraId) {
        // no-op
    }

    protected void onRecordingDisabled(int phoneId, String cameraId) {
        // no-op
    }
}
