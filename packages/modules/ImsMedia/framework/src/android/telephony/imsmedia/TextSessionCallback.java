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

import java.util.concurrent.Executor;

/**
 * Text session callback APIs
 *
 * @hide
 */
public class TextSessionCallback extends ImsMediaManager.SessionCallback {

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

    private static class CallbackBinder extends IImsTextSessionCallback.Stub {
        private final TextSessionCallback mLocalCallback;
        private Executor mExecutor;

        CallbackBinder(final TextSessionCallback localCallback) {
            mLocalCallback = localCallback;
        }

        @Override
        public void onOpenSessionSuccess(final IImsTextSession session) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(()
                        -> mLocalCallback.onOpenSessionSuccess(new ImsTextSession(session)));
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
        public void onModifySessionResponse(final TextConfig config,
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
        public void onRttReceived(final String text) {
            if (mLocalCallback == null) return;

            final long callingIdentity = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> mLocalCallback.onRttReceived(text));
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
     * @param config The TextConfig passed in ImsMediaSession#modifySession()
     * @param result The result of modify session
     */
    public void onModifySessionResponse(final TextConfig config,
            final @ImsMediaSession.SessionOperationResult int result) {
        // Base Implementation
    }

    /**
     * Called when there is text stream received from the network
     *
     * @param text The text string received from the network
     */
    public void onRttReceived(final String text) {
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
}
