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

import android.os.Binder;
import android.os.IBinder;
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.IImsAudioSession;
import android.telephony.imsmedia.IImsAudioSessionCallback;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Audio session callback APIs
 *
 * @hide
 */
public class AudioSessionCallback extends ImsMediaManager.SessionCallback {

    private final CallbackBinder mCallbackBinder = new CallbackBinder(this);

    /** @hide */
    @Override
    public IBinder getBinder() {
        return mCallbackBinder;
    }

    /** @hide */
    @Override
    public void setExecutor(final Executor executor) {
        mCallbackBinder.setExecutor(executor);
    }

    private static class CallbackBinder extends IImsAudioSessionCallback.Stub {
        private final AudioSessionCallback mLocalCallback;
        private Executor mExecutor;

        CallbackBinder(final AudioSessionCallback localCallback) {
            mLocalCallback = localCallback;
        }

        @Override
        public void onOpenSessionSuccess(final IImsAudioSession session) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(()
                        -> mLocalCallback.onOpenSessionSuccess(new ImsAudioSession(session)));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onOpenSessionFailure(final int error) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onOpenSessionFailure(error));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onSessionClosed() {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onSessionClosed());
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onModifySessionResponse(final AudioConfig config,
                final @ImsMediaSession.SessionOperationResult int result) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onModifySessionResponse(config, result));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onAddConfigResponse(final AudioConfig config,
                final @ImsMediaSession.SessionOperationResult int result) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onAddConfigResponse(config, result));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onConfirmConfigResponse(final AudioConfig config,
                final @ImsMediaSession.SessionOperationResult int result) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onConfirmConfigResponse(config, result));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onFirstMediaPacketReceived(final AudioConfig config) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onFirstMediaPacketReceived(config));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onHeaderExtensionReceived(final List<RtpHeaderExtension> extensions){
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onHeaderExtensionReceived(extensions));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void notifyMediaQualityStatus(final MediaQualityStatus status) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.notifyMediaQualityStatus(status));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onCallQualityChanged(final CallQuality callQuality) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onCallQualityChanged(callQuality));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void triggerAnbrQuery(final AudioConfig config) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.triggerAnbrQuery(config));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onDtmfReceived(final char dtmfDigit, final int durationMs) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onDtmfReceived(dtmfDigit, durationMs));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void notifyRtpReceptionStats(final RtpReceptionStats stats) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.notifyRtpReceptionStats(stats));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        private void setExecutor(final Executor executor) {
            mExecutor = executor;
        }
    }

    /**
     * Called when ImsMediaSession#modifySession() API is handled
     *
     * @param config The AudioConfig passed in ImsMediaSession#modifySession()
     * @param result The result of modify session
     */
    public void onModifySessionResponse(final AudioConfig config,
            final @ImsMediaSession.SessionOperationResult int result) {
        // Base Implementation
    }

    /**
     * Called when ImsMediaSession#addConfig() API is handled
     *
     * @param config The RTP config passed in
     *        ImsMediaSession#addConfig()
     * @param result The result of adding a configuration
     */
    public void onAddConfigResponse(final AudioConfig config,
            final @ImsMediaSession.SessionOperationResult int result) {
        // Base Implementation
    }

    /**
     * Called when ImsMediaSession#confirmConfig() API is handled
     *
     * @param config The RTP config passed in
     *        ImsMediaSession#confirmConfig()
     * @param result The result of confirm configuration
     */
    public void onConfirmConfigResponse(final AudioConfig config,
            final @ImsMediaSession.SessionOperationResult int result) {
        // Base Implementation
    }

    /**
     * Indicates when the first Rtp media packet is received by the UE
     * during ring back, call hold or early media scenarios. This is
     * sent only if the packet is received on the active remote
     * configuration.
     *
     * In case of early media scenarios, the implementation shall play
     * the RTP packets from the most recently added config.
     *
     * @param config the remote config where media packet is received
     */
    public void onFirstMediaPacketReceived(final AudioConfig config) {
        // Base Implementation
    }

    /**
     * RTP header extension received from the other party
     *
     * @param extensions List of received RTP header extensions
     */
    public void onHeaderExtensionReceived(final List<RtpHeaderExtension> extensions){
        // Base Implementation
    }

    /**
     * Notifies media quality status observed as per thresholds set by
     * setMediaQualityThreshold() API
     *
     * @param status The object of MediaQualityStatus with the rtp and
     *        the rtcp statistics.
     */
    public void notifyMediaQualityStatus(final MediaQualityStatus status) {
         // Base Implementation
    }

    /**
     * Notifies when a change to media quality is occurred
     *
     * @param callQuality The media quality statistics since last report
     */
    public void onCallQualityChanged(final CallQuality callQuality) {
        // Base Implementation
    }

    /**
     * Notifies when ImsMedia want to query the desired bitrate to NW
     *
     * @param config The config containing desired bitrate and direction
     */
    public void triggerAnbrQuery(final AudioConfig config) {
        // Base Implementation
    }

    /**
     * Notifies received DTMF digit to play the tone
     *
     * @param dtmfDigit single char having one of 12 values: 0-9, *, #
     * @param durationMs The duration to play the tone in milliseconds unit
     */
    public void onDtmfReceived(final char dtmfDigit, final int durationMs) {
        // Base Implementation
    }

    /**
     * Notifies the rtp reception parameters periodically when the requestRtpReceptionStats() is
     * triggered with the period
     *
     * @param stats The rtp reception parameters
     */
    public void notifyRtpReceptionStats(RtpReceptionStats stats) {
        // Base Implementation
    }

}
