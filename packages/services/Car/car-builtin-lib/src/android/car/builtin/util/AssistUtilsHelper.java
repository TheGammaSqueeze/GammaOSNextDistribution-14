/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.builtin.util;

import static android.service.voice.VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.AssistUtils;
import com.android.internal.app.IVoiceInteractionSessionListener;
import com.android.internal.app.IVoiceInteractionSessionShowCallback;

import java.util.Objects;

/**
 * Class to wrap {@link AssistUtils}.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class AssistUtilsHelper {

    private static final String TAG = AssistUtilsHelper.class.getSimpleName();

    /**
     * Used as a boolean extra field on show the session for the currently active voice interaction
     * service, {@code true} indicates that the service was launch from a key event,
     * {@code false} otherwise.
     */
    @VisibleForTesting
    static final String EXTRA_CAR_PUSH_TO_TALK =
            "com.android.car.input.EXTRA_CAR_PUSH_TO_TALK";

    /**
     * Used as a long extra field on show the session for the currently active voice interaction
     * service, the value indicates the button press time measured in milliseconds since the last
     * boot up.
     */
    @VisibleForTesting
    static final String EXTRA_TRIGGER_TIMESTAMP_PUSH_TO_TALK_MS =
            "com.android.car.input.EXTRA_TRIGGER_TIMESTAMP_PUSH_TO_TALK_MS";

    /**
     * Determines if there is a voice interaction session running.
     *
     * @param context used to build the assist utils.
     * @return {@code true} if a session is running, {@code false} otherwise.
     */
    public static boolean isSessionRunning(@NonNull Context context) {
        AssistUtils assistUtils = getAssistUtils(context);

        return assistUtils.isSessionRunning();
    }

    /**
     * Hides the current voice interaction session running
     *
     * @param context used to build the assist utils.
     */
    public static void hideCurrentSession(@NonNull Context context) {
        AssistUtils assistUtils = getAssistUtils(context);

        assistUtils.hideCurrentSession();
    }

    /**
     * Registers a listener to monitor when the voice sessions are shown or hidden.
     *
     * @param context used to build the assist utils.
     * @param sessionListener listener that will receive shown or hidden voice sessions callback.
     */
    // TODO(b/221604866) : Add unregister method
    public static void registerVoiceInteractionSessionListenerHelper(@NonNull Context context,
            @NonNull VoiceInteractionSessionListenerHelper sessionListener) {
        Objects.requireNonNull(sessionListener, "Session listener must not be null.");

        AssistUtils assistUtils = getAssistUtils(context);

        assistUtils.registerVoiceInteractionSessionListener(
                new InternalVoiceInteractionSessionListener(sessionListener));
    }

    /**
     * Shows the {@link android.service.voice.VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK}
     * session for active service, if the assistant component is active for the current user.
     *
     * @return whether the assistant component is active for the current user.
     */
    public static boolean showPushToTalkSessionForActiveService(@NonNull Context context,
            @NonNull VoiceInteractionSessionShowCallbackHelper callback) {
        Objects.requireNonNull(callback, "On shown callback must not be null.");

        AssistUtils assistUtils = getAssistUtils(context);
        int currentUserId = ActivityManager.getCurrentUser();


        if (assistUtils.getAssistComponentForUser(currentUserId) == null) {
            Slogf.d(TAG, "showPushToTalkSessionForActiveService(): no component for user %d",
                    currentUserId);
            return false;
        }

        Bundle args = new Bundle();
        args.putBoolean(EXTRA_CAR_PUSH_TO_TALK, true);
        args.putLong(EXTRA_TRIGGER_TIMESTAMP_PUSH_TO_TALK_MS, SystemClock.elapsedRealtime());

        IVoiceInteractionSessionShowCallback callbackWrapper =
                new InternalVoiceInteractionSessionShowCallback(callback);

        return assistUtils.showSessionForActiveService(args, SHOW_SOURCE_PUSH_TO_TALK,
                callbackWrapper, /* activityToken= */ null);
    }

    private static AssistUtils getAssistUtils(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must not be null.");
        return new AssistUtils(context);
    }

    /**
     * See {@link IVoiceInteractionSessionShowCallback}
     */
    public interface VoiceInteractionSessionShowCallbackHelper {
        /**
         * See {@link IVoiceInteractionSessionShowCallback#onFailed()}
         */
        void onFailed();

        /**
         * See {@link IVoiceInteractionSessionShowCallback#onShow()}
         */
        void onShown();
    }

    /**
     * See {@link IVoiceInteractionSessionListener}
     */
    public interface VoiceInteractionSessionListenerHelper {

        /**
         * See {@link IVoiceInteractionSessionListener#onVoiceSessionShown()}
         */
        void onVoiceSessionShown();

        /**
         * See {@link IVoiceInteractionSessionListener#onVoiceSessionHidden()}
         */
        void onVoiceSessionHidden();
    }

    private static final class InternalVoiceInteractionSessionShowCallback extends
            IVoiceInteractionSessionShowCallback.Stub {
        private final VoiceInteractionSessionShowCallbackHelper mCallbackHelper;

        InternalVoiceInteractionSessionShowCallback(
                VoiceInteractionSessionShowCallbackHelper callbackHelper) {
            mCallbackHelper = callbackHelper;
        }

        @Override
        public void onFailed() {
            mCallbackHelper.onFailed();
        }

        @Override
        public void onShown() {
            mCallbackHelper.onShown();
        }
    }

    private static final class InternalVoiceInteractionSessionListener extends
            IVoiceInteractionSessionListener.Stub {

        private final VoiceInteractionSessionListenerHelper mListenerHelper;

        InternalVoiceInteractionSessionListener(
                VoiceInteractionSessionListenerHelper listenerHelper) {
            mListenerHelper = listenerHelper;
        }

        @Override
        public void onVoiceSessionShown() throws RemoteException {
            mListenerHelper.onVoiceSessionShown();
        }

        @Override
        public void onVoiceSessionHidden() throws RemoteException {
            mListenerHelper.onVoiceSessionHidden();
        }

        @Override
        public void onSetUiHints(Bundle args) throws RemoteException {
            Slogf.d(TAG, "onSetUiHints() not used");
        }

        @Override
        public void onVoiceSessionWindowVisibilityChanged(boolean visible)
                throws RemoteException {
            Slogf.d(TAG, "onVoiceSessionWindowVisibilityChanged() not used");
        }
    }

    private AssistUtilsHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
