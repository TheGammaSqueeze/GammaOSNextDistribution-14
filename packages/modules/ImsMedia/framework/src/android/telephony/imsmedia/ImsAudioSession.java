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

import java.util.List;

/**
 * API to manage the audio session
 *
 * @hide
 */
public class ImsAudioSession implements ImsMediaSession {
    private static final String TAG = "ImsAudioSession";
    private final IImsAudioSession miSession;

    /** @hide */
    public ImsAudioSession(final IImsAudioSession session) {
        miSession = session;
    }

    /** @hide */
    @Override
    public IBinder getBinder() {
        return miSession.asBinder();
    }

    /** {@inheritDoc} */
    public int getSessionId() {
        try {
            return miSession.getSessionId();
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
            miSession.modifySession((AudioConfig)config);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to modify session: " + e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setMediaQualityThreshold(final MediaQualityThreshold threshold) {
        try {
            miSession.setMediaQualityThreshold(threshold);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to modify session: " + e);
        }
    }

    /**
     * Adds a new remote configuration to a RTP session during early media
     * scenarios where the IMS network could add more than one remote endpoint.
     *
     * @param config provides remote end point info and codec details
     */
    public void addConfig(final AudioConfig config) {
        try {
            miSession.addConfig(config);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to add config: " + e);
        }
    }

    /**
     * Deletes a remote configuration from a RTP session during early media
     * scenarios. A session shall have at least one config so this API shall
     * not delete the last config.
     *
     * @param config remote config to be deleted
     */
    public void deleteConfig(final AudioConfig config) {
        try {
            miSession.deleteConfig(config);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to delete config: " + e);
        }
    }

    /**
     * Confirms a remote configuration for a Rtp session for early media scenarios
     * when there are more than one remote configs. All other early remote configs
     * (potentially including the config created as part of openSession) are auto
     * deleted when one config is confirmed.
     * Confirming a remote configuration is necessary only if additional
     * configurations were created.
     * New remote configurations cannot be added after a remote configuration is
     * confirmed.
     *
     * @param config remote config to be confirmed
     */
    public void confirmConfig(final AudioConfig config) {
        try {
            miSession.confirmConfig(config);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to confirm config: " + e);
        }
    }

    /**
     * Send DTMF digit until the duration expires.
     *
     * @param dtmfDigit single char having one of 12 values: 0-9, *, #
     * @param duration of the key press in milliseconds.
     */
    public void sendDtmf(final char dtmfDigit, final int duration) {
        try {
            miSession.sendDtmf(dtmfDigit, duration);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send DTMF: " + e);
        }
    }

    /**
     * Start sending DTMF digit until the stopDtmf() API is received.
     * If the implementation is currently sending a DTMF tone for which
     * stopDtmf() is not received yet, then that digit must be stopped first
     *
     * @param dtmfDigit single char having one of 12 values: 0-9, *, #
     */
    public void startDtmf(final char dtmfDigit) {
        try {
            miSession.startDtmf(dtmfDigit);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to start DTMF: " + e);
        }
    }

    /**
     * Stop sending the last DTMF digit started by startDtmf().
     * stopDtmf() without preceding startDtmf() must be ignored.
     */
    public void stopDtmf() {
        try {
            miSession.stopDtmf();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to stop DTMF: " + e);
        }
    }

    /**
     * Send RTP header extension to the other party in the next RTP packet.
     *
     * @param extensions List of RTP header extensions to be transmitted
     */
    public void sendHeaderExtension(final List<RtpHeaderExtension> extensions) {
        try {
            miSession.sendHeaderExtension(extensions);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send RTP header extension: " + e);
        }
    }

    /**
     * Request the current rtp reception statistics parameters for checking the current status of
     * the rtp stream. It will trigger the notifyRtpReceptionStats() with the RtpReceptionStats.
     *
     * @param intervalMs The interval of the time in milliseconds of the rtp reception notification
     */
    public void requestRtpReceptionStats(int intervalMs) {
        try {
            miSession.requestRtpReceptionStats(intervalMs);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to query rtp reception statistics: " + e);
        }
    }

    /**
     * Adjust the delay in the jitter buffer to synchronize the audio with the time of video frames
     *
     * @param delayMs The delay to adjust the additional delay to the jitter buffer. The value is
     * always positive.
     */
    public void adjustDelay(int delayMs) {
        try {
            miSession.adjustDelay(delayMs);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to apply delay to the session de-jitter buffer: " + e);
        }
    }
}
