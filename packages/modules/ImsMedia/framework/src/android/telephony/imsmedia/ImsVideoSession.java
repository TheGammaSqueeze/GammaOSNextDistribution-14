/**
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

package android.telephony.imsmedia;

import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.RtpHeaderExtension;
import android.util.Log;
import android.view.Surface;

import java.util.List;

/**
 * API to manage the video session
 *
 * @hide
 */
public class ImsVideoSession implements ImsMediaSession {
    private static final String TAG = "ImsVideoSession";
    private final IImsVideoSession mSession;

    /** @hide */
    public ImsVideoSession(final IImsVideoSession session) {
        mSession = session;
    }

    /** @hide */
    @Override
    public IBinder getBinder() {
        return mSession.asBinder();
    }

    /** {@inheritDoc} */
    public int getSessionId() {
        try {
            return mSession.getSessionId();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get session ID: " + e);
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public void modifySession(final RtpConfig config) {
        try {
            mSession.modifySession((VideoConfig) config);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to modify session: " + e);
        }
    }

    /**
     * Send preview Surface to session to display video tx preview in video streaming.
     * The setPreviewSurafce should be invoked after openSession or modifySession.
     *
     * @param surface the surface to set
     */
    public void setPreviewSurface(final Surface surface) {
        try {
            mSession.setPreviewSurface(surface);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set preview surface: " + e);
        }
    }

    /**
     * Send display Surface to session to display video rx decoded frames in video streaming.
     * The setDisplaySurface should be invoked after openSession or modifySession.
     *
     * @param surface the surface to set
     */
    public void setDisplaySurface(final Surface surface) {
        try {
            mSession.setDisplaySurface(surface);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set display surface: " + e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setMediaQualityThreshold(final MediaQualityThreshold threshold) {
        try {
            mSession.setMediaQualityThreshold(threshold);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set media quality threshold: " + e);
        }
    }

    /**
     * Send RTP header extension to the other party in the next RTP packet.
     *
     * @param extensions List of RTP header extensions to be transmitted
     */
    public void sendHeaderExtension(final List<RtpHeaderExtension> extensions) {
        try {
            mSession.sendHeaderExtension(extensions);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send RTP header extension: " + e);
        }
    }

    /**
     * Request to report the amount of data usage in current video streaming session.
     * The notifyVideoDataUsage() will be invoked as a notification.
     */
    public void requestVideoDataUsage() {
        try {
            mSession.requestVideoDataUsage();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to request video data usage: " + e);
        }
    }
}
