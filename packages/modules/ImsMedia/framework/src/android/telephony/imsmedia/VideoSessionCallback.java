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
import android.telephony.ims.RtpHeaderExtension;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Video session callback APIs
 *
 * @hide
 */
public class VideoSessionCallback extends ImsMediaManager.SessionCallback {

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

    private static class CallbackBinder extends IImsVideoSessionCallback.Stub {
        private final VideoSessionCallback mLocalCallback;
        private Executor mExecutor;

        CallbackBinder(final VideoSessionCallback localCallback) {
            mLocalCallback = localCallback;
        }

        @Override
        public void onOpenSessionSuccess(final IImsVideoSession session) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(()
                        -> mLocalCallback.onOpenSessionSuccess(new ImsVideoSession(session)));
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
        public void onModifySessionResponse(final VideoConfig config,
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
        public void onFirstMediaPacketReceived(final VideoConfig config) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onFirstMediaPacketReceived(config));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onPeerDimensionChanged(final int width, final int height) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onPeerDimensionChanged(width, height));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void onHeaderExtensionReceived(final List<RtpHeaderExtension> extensions) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onHeaderExtensionReceived(extensions));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void notifyMediaInactivity(final int packetType) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.notifyMediaInactivity(packetType));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void notifyBitrate(final int bitrate) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.notifyBitrate(bitrate));
            } finally {
                restoreCallingIdentity(callingIdentity);
            }
        }

        @Override
        public void notifyVideoDataUsage(final long bytes) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.notifyVideoDataUsage(bytes));
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
     * @param config The VideoConfig passed in ImsMediaSession#modifySession()
     * @param result The result of modify session
     */
    public void onModifySessionResponse(final VideoConfig config,
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
    public void onFirstMediaPacketReceived(final VideoConfig config) {
        // Base Implementation
    }

    /**
     * Notify when the received video frame resolution is different with the current resolution.
     * @param width width of resolution changed.
     * @param height height of resolution changed.
     */
    public void onPeerDimensionChanged(final int width, final int height) {
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
     * Notifies media inactivity observed as per thresholds set by
     * setMediaQualityThreshold() API
     *
     * @param packetType either RTP or RTCP
     */
    public void notifyMediaInactivity(final int packetType) {
         // Base Implementation
    }

    /**
     * Notifies when the video bitrate decreased below the threshold set by
     * setMediaQualityThreshold() API
     *
     * @param bitrate The bitrate of sending video packets in bps unit
     */
    public void notifyBitrate(final int bitrate) {
        // Base Implementation
    }

    /**
     * Notify accumulated video data usage in the current session.
     * @param bytes bytes of send and received rtp data.
     */
    public void notifyVideoDataUsage(final long bytes) {
        // Base Implementation
    }
}
