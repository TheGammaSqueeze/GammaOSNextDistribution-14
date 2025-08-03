/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telecom.Log;
import android.view.KeyEvent;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Static class to handle listening to the headset media buttons.
 */
public class HeadsetMediaButton extends CallsManagerListenerBase {

    // Types of media button presses
    @VisibleForTesting
    public static final int SHORT_PRESS = 1;
    @VisibleForTesting
    public static final int LONG_PRESS = 2;

    private static final AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build();

    private static final int MSG_MEDIA_SESSION_INITIALIZE = 0;
    private static final int MSG_MEDIA_SESSION_SET_ACTIVE = 1;

    /**
     * Wrapper class that abstracts an instance of {@link MediaSession} to the
     * {@link MediaSessionAdapter} interface this class uses.  This is done because
     * {@link MediaSession} is a final class and cannot be mocked for testing purposes.
     */
    public class MediaSessionWrapper implements MediaSessionAdapter {
        private final MediaSession mMediaSession;

        public MediaSessionWrapper(MediaSession mediaSession) {
            mMediaSession = mediaSession;
        }

        /**
         * Sets the underlying {@link MediaSession} active status.
         * @param active
         */
        @Override
        public void setActive(boolean active) {
            mMediaSession.setActive(active);
        }

        @Override
        public void setCallback(MediaSession.Callback callback) {
            mMediaSession.setCallback(callback);
        }

        /**
         * Gets the underlying {@link MediaSession} active status.
         * @return {@code true} if active, {@code false} otherwise.
         */
        @Override
        public boolean isActive() {
            return mMediaSession.isActive();
        }
    }

    /**
     * Interface which defines the basic functionality of a {@link MediaSession} which is important
     * for the {@link HeadsetMediaButton} to operator; this is for testing purposes so we can mock
     * out that functionality.
     */
    public interface MediaSessionAdapter {
        void setActive(boolean active);
        void setCallback(MediaSession.Callback callback);
        boolean isActive();
    }

    private final MediaSession.Callback mSessionCallback = new MediaSession.Callback() {
        @Override
        public boolean onMediaButtonEvent(Intent intent) {
            try {
                Log.startSession("HMB.oMBE");
                KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                Log.v(this, "SessionCallback.onMediaButton()...  event = %s.", event);
                if ((event != null) && ((event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK) ||
                        (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))) {
                    synchronized (mLock) {
                        Log.v(this, "SessionCallback: HEADSETHOOK/MEDIA_PLAY_PAUSE");
                        boolean consumed = handleCallMediaButton(event);
                        Log.v(this, "==> handleCallMediaButton(): consumed = %b.", consumed);
                        return consumed;
                    }
                }
                return true;
            } finally {
                Log.endSession();
            }
        }
    };

    private final Handler mMediaSessionHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_MEDIA_SESSION_INITIALIZE: {
                    MediaSession session = new MediaSession(
                            mContext,
                            HeadsetMediaButton.class.getSimpleName());
                    session.setCallback(mSessionCallback);
                    session.setFlags(MediaSession.FLAG_EXCLUSIVE_GLOBAL_PRIORITY
                            | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
                    session.setPlaybackToLocal(AUDIO_ATTRIBUTES);
                    mSession = new MediaSessionWrapper(session);
                    break;
                }
                case MSG_MEDIA_SESSION_SET_ACTIVE: {
                    if (mSession != null) {
                        boolean activate = msg.arg1 != 0;
                        if (activate != mSession.isActive()) {
                            mSession.setActive(activate);
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
    };

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final TelecomSystem.SyncRoot mLock;
    private MediaSessionAdapter mSession;
    private KeyEvent mLastHookEvent;

    /**
     * Constructor used for testing purposes to initialize a {@link HeadsetMediaButton} with a
     * specified {@link MediaSessionAdapter}.  Will not trigger MSG_MEDIA_SESSION_INITIALIZE and
     * cause an actual {@link MediaSession} instance to be created.
     * @param context the context
     * @param callsManager the mock calls manager
     * @param lock the lock
     * @param adapter the adapter
     */
    @VisibleForTesting
    public HeadsetMediaButton(
            Context context,
            CallsManager callsManager,
            TelecomSystem.SyncRoot lock,
            MediaSessionAdapter adapter) {
        mContext = context;
        mCallsManager = callsManager;
        mLock = lock;
        mSession = adapter;

        adapter.setCallback(mSessionCallback);
    }

    /**
     * Production code constructor; this version triggers MSG_MEDIA_SESSION_INITIALIZE which will
     * create an actual instance of {@link MediaSession}.
     * @param context the context
     * @param callsManager the calls manager
     * @param lock the telecom lock
     */
    public HeadsetMediaButton(
            Context context,
            CallsManager callsManager,
            TelecomSystem.SyncRoot lock) {
        mContext = context;
        mCallsManager = callsManager;
        mLock = lock;

        // Create a MediaSession but don't enable it yet. This is a
        // replacement for MediaButtonReceiver
        mMediaSessionHandler.obtainMessage(MSG_MEDIA_SESSION_INITIALIZE).sendToTarget();
    }

    /**
     * Handles the wired headset button while in-call.
     *
     * @return true if we consumed the event.
     */
    private boolean handleCallMediaButton(KeyEvent event) {
        Log.d(this, "handleCallMediaButton()...%s %s", event.getAction(), event.getRepeatCount());

        // Save ACTION_DOWN Event temporarily.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mLastHookEvent = event;
        }

        if (event.isLongPress()) {
            return mCallsManager.onMediaButton(LONG_PRESS);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            // We should not judge SHORT_PRESS by ACTION_UP event repeatCount, because it always
            // return 0.
            // Actually ACTION_DOWN event repeatCount only increases when LONG_PRESS performed.
            if (mLastHookEvent != null && mLastHookEvent.getRepeatCount() == 0) {
                return mCallsManager.onMediaButton(SHORT_PRESS);
            }
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            mLastHookEvent = null;
        }

        return true;
    }

    /** ${inheritDoc} */
    @Override
    public void onCallAdded(Call call) {
        if (call.isExternalCall()) {
            return;
        }
        handleCallAddition();
    }

    /**
     * Triggers session activation due to call addition.
     */
    private void handleCallAddition() {
        mMediaSessionHandler.obtainMessage(MSG_MEDIA_SESSION_SET_ACTIVE, 1, 0).sendToTarget();
    }

    /** ${inheritDoc} */
    @Override
    public void onCallRemoved(Call call) {
        if (call.isExternalCall()) {
            return;
        }
        handleCallRemoval();
    }

    /**
     * Triggers session deactivation due to call removal.
     */
    private void handleCallRemoval() {
        if (!mCallsManager.hasAnyCalls()) {
            mMediaSessionHandler.obtainMessage(MSG_MEDIA_SESSION_SET_ACTIVE, 0, 0).sendToTarget();
        }
    }

    /** ${inheritDoc} */
    @Override
    public void onExternalCallChanged(Call call, boolean isExternalCall) {
        // Note: We don't use the onCallAdded/onCallRemoved methods here since they do checks to see
        // if the call is external or not and would skip the session activation/deactivation.
        if (isExternalCall) {
            handleCallRemoval();
        } else {
            handleCallAddition();
        }
    }

    @VisibleForTesting
    /**
     * @return the handler this class instance uses for operation; used for unit testing.
     */
    public Handler getHandler() {
        return mMediaSessionHandler;
    }
}
