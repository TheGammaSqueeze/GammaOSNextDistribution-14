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
package android.media.bettertogether.cts;

import static android.media.cts.Utils.compareRemoteUserInfo;
import static android.media.session.PlaybackState.STATE_PLAYING;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Rating;
import android.media.VolumeProvider;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager.RemoteUserInfo;
import android.media.session.PlaybackState;
import android.media.session.PlaybackState.CustomAction;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.ResultReceiver;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link android.media.session.MediaController}.
 */
@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class MediaControllerTest {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;
    private static final String SESSION_TAG = "test-session";
    private static final String EXTRAS_KEY = "test-key";
    private static final String EXTRAS_VALUE = "test-val";

    private final Object mWaitLock = new Object();
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaSession mSession;
    private Bundle mSessionInfo;
    private MediaSessionCallback mCallback = new MediaSessionCallback();
    private MediaController mController;
    private RemoteUserInfo mControllerInfo;

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Before
    public void setUp() throws Exception {
        mSessionInfo = new Bundle();
        mSessionInfo.putString(EXTRAS_KEY, EXTRAS_VALUE);
        mSession = new MediaSession(getContext(), SESSION_TAG, mSessionInfo);
        mSession.setCallback(mCallback, mHandler);
        mController = mSession.getController();
        mControllerInfo = new RemoteUserInfo(
                getContext().getPackageName(), Process.myPid(), Process.myUid());
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.release();
            mSession = null;
        }
    }

    @Test
    public void testGetPackageName() {
        assertThat(mController.getPackageName())
                .isEqualTo(getContext().getPackageName());
    }

    @Test
    public void testGetPlaybackState() {
        final int testState = STATE_PLAYING;
        final long testPosition = 100000L;
        final float testSpeed = 1.0f;
        final long testActions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SEEK_TO;
        final long testActiveQueueItemId = 3377;
        final long testBufferedPosition = 100246L;
        final String testErrorMsg = "ErrorMsg";

        final Bundle extras = new Bundle();
        extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

        final double positionDelta = 500;

        PlaybackState state = new PlaybackState.Builder()
                .setState(testState, testPosition, testSpeed)
                .setActions(testActions)
                .setActiveQueueItemId(testActiveQueueItemId)
                .setBufferedPosition(testBufferedPosition)
                .setErrorMessage(testErrorMsg)
                .setExtras(extras)
                .build();

        mSession.setPlaybackState(state);

        // Note: No need to wait since the AIDL call is not oneway.
        PlaybackState stateOut = mController.getPlaybackState();
        assertThat(stateOut).isNotNull();
        assertThat(stateOut.getState()).isEqualTo(testState);
        assertThat((double) stateOut.getPosition()).isWithin(positionDelta).of(testPosition);
        assertThat(stateOut.getPlaybackSpeed()).isWithin(0.0f).of(testSpeed);
        assertThat(stateOut.getActions()).isEqualTo(testActions);
        assertThat(stateOut.getActiveQueueItemId()).isEqualTo(testActiveQueueItemId);
        assertThat(stateOut.getBufferedPosition()).isEqualTo(testBufferedPosition);
        assertThat(stateOut.getErrorMessage().toString()).isEqualTo(testErrorMsg);
        assertThat(stateOut.getExtras()).isNotNull();
        assertThat(stateOut.getExtras().get(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
    }

    @Test
    public void testGetRatingType() {
        assertWithMessage("Default rating type of a session must be Rating.RATING_NONE")
                .that(mController.getRatingType())
                .isEqualTo(Rating.RATING_NONE);

        mSession.setRatingType(Rating.RATING_5_STARS);
        // Note: No need to wait since the AIDL call is not oneway.
        assertThat(mController.getRatingType()).isEqualTo(Rating.RATING_5_STARS);
    }

    @Test
    public void testGetSessionToken() {
        assertThat(mController.getSessionToken()).isEqualTo(mSession.getSessionToken());
    }

    @Test
    public void testGetSessionInfo() {
        Bundle sessionInfo = mController.getSessionInfo();
        assertThat(sessionInfo).isNotNull();
        assertThat(sessionInfo.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);

        Bundle cachedSessionInfo = mController.getSessionInfo();
        assertThat(cachedSessionInfo.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
    }

    @Test
    public void testGetSessionInfoReturnsAnEmptyBundleWhenNotSet() {
        MediaSession session = new MediaSession(getContext(), "test_tag", /*sessionInfo=*/ null);
        try {
            assertThat(session.getController().getSessionInfo().isEmpty()).isTrue();
        } finally {
            session.release();
        }
    }

    @Test
    public void testGetTag() {
        assertThat(mController.getTag()).isEqualTo(SESSION_TAG);
    }

    @Test
    public void testSendCommand() throws Exception {
        synchronized (mWaitLock) {
            mCallback.reset();
            final String command = "test-command";
            final Bundle extras = new Bundle();
            extras.putString(EXTRAS_KEY, EXTRAS_VALUE);
            mController.sendCommand(command, extras, new ResultReceiver(null));
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnCommandCalled).isTrue();
            assertThat(mCallback.mCommandCallback).isNotNull();
            assertThat(mCallback.mCommand).isEqualTo(command);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();
        }
    }

    @Test
    public void testSendCommandWithIllegalArgumentsThrowsIAE() {
        Bundle args = new Bundle();
        ResultReceiver resultReceiver = new ResultReceiver(mHandler);

        assertThrows(IllegalArgumentException.class,
                () -> mController.sendCommand(/*command=*/ null, args, resultReceiver));

        assertThrows(IllegalArgumentException.class,
                () -> mController.sendCommand(/*command=*/ "", args, resultReceiver));
    }

    @Test
    public void testSetPlaybackSpeed() throws Exception {
        synchronized (mWaitLock) {
            mCallback.reset();

            final float testSpeed = 2.0f;
            mController.getTransportControls().setPlaybackSpeed(testSpeed);
            mWaitLock.wait(TIME_OUT_MS);

            assertThat(mCallback.mOnSetPlaybackSpeedCalled).isTrue();
            assertThat(mCallback.mSpeed).isWithin(0.0f).of(testSpeed);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();
        }
    }

    @Test
    public void testAdjustVolumeWithIllegalDirection() {
        // Call the method with illegal direction. System should not reboot.
        mController.adjustVolume(37, 0);
    }

    @Test
    public void testVolumeControl() throws Exception {
        VolumeProvider vp = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, 11, 5) {
            @Override
            public void onSetVolumeTo(int volume) {
                synchronized (mWaitLock) {
                    setCurrentVolume(volume);
                    mWaitLock.notify();
                }
            }

            @Override
            public void onAdjustVolume(int direction) {
                synchronized (mWaitLock) {
                    switch (direction) {
                        case AudioManager.ADJUST_LOWER:
                            setCurrentVolume(getCurrentVolume() - 1);
                            break;
                        case AudioManager.ADJUST_RAISE:
                            setCurrentVolume(getCurrentVolume() + 1);
                            break;
                    }
                    mWaitLock.notify();
                }
            }
        };
        mSession.setPlaybackToRemote(vp);

        synchronized (mWaitLock) {
            // test setVolumeTo
            mController.setVolumeTo(7, 0);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(vp.getCurrentVolume()).isEqualTo(7);

            // test adjustVolume
            mController.adjustVolume(AudioManager.ADJUST_LOWER, 0);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(vp.getCurrentVolume()).isEqualTo(6);

            mController.adjustVolume(AudioManager.ADJUST_RAISE, 0);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(vp.getCurrentVolume()).isEqualTo(7);
        }
    }

    @Test
    public void testTransportControlsAndMediaSessionCallback() throws Exception {
        MediaController.TransportControls controls = mController.getTransportControls();
        final MediaSession.Callback callback = (MediaSession.Callback) mCallback;

        synchronized (mWaitLock) {
            mCallback.reset();
            controls.play();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPlayCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.pause();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPauseCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.stop();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnStopCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.fastForward();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnFastForwardCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.rewind();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnRewindCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.skipToPrevious();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnSkipToPreviousCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.skipToNext();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnSkipToNextCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            final long seekPosition = 1000;
            controls.seekTo(seekPosition);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnSeekToCalled).isTrue();
            assertThat(mCallback.mSeekPosition).isEqualTo(seekPosition);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            final Rating rating = Rating.newStarRating(Rating.RATING_5_STARS, 3f);
            controls.setRating(rating);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnSetRatingCalled).isTrue();
            assertThat(mCallback.mRating.getRatingStyle()).isEqualTo(rating.getRatingStyle());
            assertThat(mCallback.mRating.getStarRating())
                    .isWithin(0.0f)
                    .of(rating.getStarRating());
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            final String mediaId = "test-media-id";
            final Bundle extras = new Bundle();
            extras.putString(EXTRAS_KEY, EXTRAS_VALUE);
            controls.playFromMediaId(mediaId, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPlayFromMediaIdCalled).isTrue();
            assertThat(mCallback.mMediaId).isEqualTo(mediaId);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            final String query = "test-query";
            controls.playFromSearch(query, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPlayFromSearchCalled).isTrue();
            assertThat(mCallback.mQuery).isEqualTo(query);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            final Uri uri = Uri.parse("content://test/popcorn.mod");
            controls.playFromUri(uri, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPlayFromUriCalled).isTrue();
            assertThat(mCallback.mUri).isEqualTo(uri);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            final String action = "test-action";
            controls.sendCustomAction(action, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnCustomActionCalled).isTrue();
            assertThat(mCallback.mAction).isEqualTo(action);
            assertThat(mCallback.mExtras.get(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            mCallback.mOnCustomActionCalled = false;
            final CustomAction customAction =
                    new CustomAction.Builder(action, action, -1).setExtras(extras).build();
            controls.sendCustomAction(customAction, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnCustomActionCalled).isTrue();
            assertThat(mCallback.mAction).isEqualTo(action);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            final long queueItemId = 1000;
            controls.skipToQueueItem(queueItemId);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnSkipToQueueItemCalled).isTrue();
            assertThat(mCallback.mQueueItemId).isEqualTo(queueItemId);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.prepare();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPrepareCalled).isTrue();
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.prepareFromMediaId(mediaId, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPrepareFromMediaIdCalled).isTrue();
            assertThat(mCallback.mMediaId).isEqualTo(mediaId);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.prepareFromSearch(query, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPrepareFromSearchCalled).isTrue();
            assertThat(mCallback.mQuery).isEqualTo(query);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            controls.prepareFromUri(uri, extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPrepareFromUriCalled).isTrue();
            assertThat(mCallback.mUri).isEqualTo(uri);
            assertThat(mCallback.mExtras.getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            mCallback.reset();
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP);
            mController.dispatchMediaButtonEvent(event);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnMediaButtonEventCalled).isTrue();
            // KeyEvent doesn't override equals.
            assertThat(mCallback.mKeyEvent.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
            assertThat(mCallback.mKeyEvent.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_MEDIA_STOP);
            assertThat(compareRemoteUserInfo(mControllerInfo, mCallback.mCallerInfo)).isTrue();

            // just call the callback once directly so it's marked as tested
            try {
                callback.onPlay();
                callback.onPause();
                callback.onStop();
                callback.onFastForward();
                callback.onRewind();
                callback.onSkipToPrevious();
                callback.onSkipToNext();
                callback.onSeekTo(mCallback.mSeekPosition);
                callback.onSetRating(mCallback.mRating);
                callback.onPlayFromMediaId(mCallback.mMediaId, mCallback.mExtras);
                callback.onPlayFromSearch(mCallback.mQuery, mCallback.mExtras);
                callback.onPlayFromUri(mCallback.mUri, mCallback.mExtras);
                callback.onCustomAction(mCallback.mAction, mCallback.mExtras);
                callback.onCustomAction(mCallback.mAction, mCallback.mExtras);
                callback.onSkipToQueueItem(mCallback.mQueueItemId);
                callback.onPrepare();
                callback.onPrepareFromMediaId(mCallback.mMediaId, mCallback.mExtras);
                callback.onPrepareFromSearch(mCallback.mQuery, mCallback.mExtras);
                callback.onPrepareFromUri(Uri.parse("http://d.android.com"), mCallback.mExtras);
                callback.onCommand(mCallback.mCommand, mCallback.mExtras, null);
                callback.onSetPlaybackSpeed(mCallback.mSpeed);
                Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaButtonIntent.putExtra(Intent.EXTRA_KEY_EVENT, event);
                callback.onMediaButtonEvent(mediaButtonIntent);
            } catch (IllegalStateException ex) {
                // Expected, since the MediaSession.getCurrentControllerInfo() is called in every
                // callback method, but no controller is sending any command.
            }
        }
    }

    @Test
    public void testRegisterCallbackWithNullThrowsIAE() {
        assertThrows(IllegalArgumentException.class,
                () -> mController.registerCallback(/*handler=*/ null));

        assertThrows(IllegalArgumentException.class,
                () -> mController.registerCallback(/*handler=*/ null, mHandler));
    }

    @Test
    public void testRegisteringSameCallbackWithDifferentHandlerHasNoEffect() {
        MediaController.Callback callback = new MediaController.Callback() {};
        mController.registerCallback(callback, mHandler);

        Handler initialHandler = mController.getHandlerForCallback(callback);
        assertThat(initialHandler.getLooper()).isEqualTo(mHandler.getLooper());

        // Create a separate handler with a new looper.
        HandlerThread handlerThread = new HandlerThread("Test thread");
        handlerThread.start();

        // This call should not change the handler which is previously set.
        mController.registerCallback(callback, new Handler(handlerThread.getLooper()));
        Handler currentHandlerInController = mController.getHandlerForCallback(callback);

        // The handler should not have been replaced.
        assertThat(currentHandlerInController).isEqualTo(initialHandler);
        assertThat(currentHandlerInController.getLooper()).isNotEqualTo(handlerThread.getLooper());

        handlerThread.quitSafely();
    }

    @Test
    public void testUnregisterCallbackWithNull() {
        assertThrows(IllegalArgumentException.class,
                () -> mController.unregisterCallback(/*handler=*/ null));
    }

    @Test
    public void testUnregisterCallbackShouldRemoveCallback() {
        MediaController.Callback callback = new MediaController.Callback() {};
        mController.registerCallback(callback, mHandler);
        assertThat(mController.getHandlerForCallback(callback).getLooper())
                .isEqualTo(mHandler.getLooper());

        mController.unregisterCallback(callback);
        assertThat(mController.getHandlerForCallback(callback)).isNull();
    }

    @Test
    public void testDispatchMediaButtonEventWithNullKeyEvent() {
        assertThrows(IllegalArgumentException.class,
                () -> mController.dispatchMediaButtonEvent(/*keyEvent=*/ null));
    }

    @Test
    public void testDispatchMediaButtonEventWithNonMediaKeyEventReturnsFalse() {
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CAPS_LOCK);
        assertThat(mController.dispatchMediaButtonEvent(keyEvent)).isFalse();
    }

    @Test
    public void testPlaybackInfoCreatorNewArray() {
        final int arrayLength = 5;
        MediaController.PlaybackInfo[] playbackInfoArrayInitializedWithNulls =
                MediaController.PlaybackInfo.CREATOR.newArray(arrayLength);
        assertThat(playbackInfoArrayInitializedWithNulls).isNotNull();
        assertThat(playbackInfoArrayInitializedWithNulls.length).isEqualTo(arrayLength);
        for (MediaController.PlaybackInfo playbackInfo : playbackInfoArrayInitializedWithNulls) {
            assertThat(playbackInfo).isNull();
        }
    }

    @Test
    public void testTransportControlsPlayAndPrepareFromMediaIdWithIllegalArgumentsThrowsIAE() {
        MediaController.TransportControls transportControls = mController.getTransportControls();

        assertThrows(IllegalArgumentException.class,
                () -> transportControls
                        .playFromMediaId(/*mediaId=*/ null, /*extras=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls.playFromMediaId(/*mediaId=*/ "", /*extras=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls
                        .prepareFromMediaId(/*mediaId=*/ null, /*extras=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls
                        .prepareFromMediaId(/*mediaId=*/ "", /*extras=*/ new Bundle()));
    }

    @Test
    public void testTransportControlsPlayAndPrepareFromUriWithIllegalArgumentsThrowsIAE() {
        MediaController.TransportControls transportControls = mController.getTransportControls();

        assertThrows(IllegalArgumentException.class,
                () -> transportControls.playFromUri(/*uri=*/ null, /*extras=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls.playFromUri(Uri.EMPTY, /*extras=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls.prepareFromUri(/*uri=*/ null, /*extras=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls.prepareFromUri(Uri.EMPTY, /*extras=*/ new Bundle()));
    }

    @Test
    public void testTransportControlsPlayAndPrepareFromSearchWithNullDoesNotCrash()
            throws Exception {
        MediaController.TransportControls transportControls = mController.getTransportControls();

        synchronized (mWaitLock) {
            // These calls should not crash. Null query is accepted on purpose.
            transportControls.playFromSearch(/*query=*/ null, /*extras=*/ new Bundle());
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPlayFromSearchCalled).isTrue();

            transportControls.prepareFromSearch(/*query=*/ null, /*extras=*/ new Bundle());
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPrepareFromSearchCalled).isTrue();
        }
    }

    @Test
    public void testSendCustomActionWithIllegalArgumentsThrowsIAE() {
        MediaController.TransportControls transportControls = mController.getTransportControls();

        assertThrows(IllegalArgumentException.class,
                () -> transportControls.sendCustomAction((PlaybackState.CustomAction) null,
                        /*args=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls
                        .sendCustomAction(/*action=*/ (String) null, /*args=*/ new Bundle()));

        assertThrows(IllegalArgumentException.class,
                () -> transportControls.sendCustomAction(/*action=*/ "", /*args=*/ new Bundle()));
    }

    private class MediaSessionCallback extends MediaSession.Callback {
        private long mSeekPosition;
        private long mQueueItemId;
        private Rating mRating;
        private String mMediaId;
        private String mQuery;
        private Uri mUri;
        private String mAction;
        private String mCommand;
        private Bundle mExtras;
        private ResultReceiver mCommandCallback;
        private KeyEvent mKeyEvent;
        private RemoteUserInfo mCallerInfo;
        private float mSpeed;

        private boolean mOnPlayCalled;
        private boolean mOnPauseCalled;
        private boolean mOnStopCalled;
        private boolean mOnFastForwardCalled;
        private boolean mOnRewindCalled;
        private boolean mOnSkipToPreviousCalled;
        private boolean mOnSkipToNextCalled;
        private boolean mOnSeekToCalled;
        private boolean mOnSkipToQueueItemCalled;
        private boolean mOnSetRatingCalled;
        private boolean mOnPlayFromMediaIdCalled;
        private boolean mOnPlayFromSearchCalled;
        private boolean mOnPlayFromUriCalled;
        private boolean mOnCustomActionCalled;
        private boolean mOnCommandCalled;
        private boolean mOnPrepareCalled;
        private boolean mOnPrepareFromMediaIdCalled;
        private boolean mOnPrepareFromSearchCalled;
        private boolean mOnPrepareFromUriCalled;
        private boolean mOnMediaButtonEventCalled;
        private boolean mOnSetPlaybackSpeedCalled;

        public void reset() {
            mSeekPosition = -1;
            mQueueItemId = -1;
            mRating = null;
            mMediaId = null;
            mQuery = null;
            mUri = null;
            mAction = null;
            mExtras = null;
            mCommand = null;
            mCommandCallback = null;
            mKeyEvent = null;
            mCallerInfo = null;
            mSpeed = -1.0f;

            mOnPlayCalled = false;
            mOnPauseCalled = false;
            mOnStopCalled = false;
            mOnFastForwardCalled = false;
            mOnRewindCalled = false;
            mOnSkipToPreviousCalled = false;
            mOnSkipToNextCalled = false;
            mOnSkipToQueueItemCalled = false;
            mOnSeekToCalled = false;
            mOnSetRatingCalled = false;
            mOnPlayFromMediaIdCalled = false;
            mOnPlayFromSearchCalled = false;
            mOnPlayFromUriCalled = false;
            mOnCustomActionCalled = false;
            mOnCommandCalled = false;
            mOnPrepareCalled = false;
            mOnPrepareFromMediaIdCalled = false;
            mOnPrepareFromSearchCalled = false;
            mOnPrepareFromUriCalled = false;
            mOnMediaButtonEventCalled = false;
            mOnSetPlaybackSpeedCalled = false;
        }

        @Override
        public void onPlay() {
            synchronized (mWaitLock) {
                mOnPlayCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onPause() {
            synchronized (mWaitLock) {
                mOnPauseCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onStop() {
            synchronized (mWaitLock) {
                mOnStopCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onFastForward() {
            synchronized (mWaitLock) {
                mOnFastForwardCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onRewind() {
            synchronized (mWaitLock) {
                mOnRewindCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToPrevious() {
            synchronized (mWaitLock) {
                mOnSkipToPreviousCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToNext() {
            synchronized (mWaitLock) {
                mOnSkipToNextCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onSeekTo(long pos) {
            synchronized (mWaitLock) {
                mOnSeekToCalled = true;
                mSeekPosition = pos;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onSetRating(Rating rating) {
            synchronized (mWaitLock) {
                mOnSetRatingCalled = true;
                mRating = rating;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPlayFromMediaIdCalled = true;
                mMediaId = mediaId;
                mExtras = extras;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPlayFromSearchCalled = true;
                mQuery = query;
                mExtras = extras;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPlayFromUriCalled = true;
                mUri = uri;
                mExtras = extras;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            synchronized (mWaitLock) {
                mOnCustomActionCalled = true;
                mAction = action;
                mExtras = extras;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToQueueItem(long id) {
            synchronized (mWaitLock) {
                mOnSkipToQueueItemCalled = true;
                mQueueItemId = id;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            synchronized (mWaitLock) {
                final RemoteUserInfo currentControllerInfo = mSession.getCurrentControllerInfo();
                if (!compareRemoteUserInfo(mControllerInfo, currentControllerInfo)) {
                    // Ignore this command we did not send
                    return;
                }
                mOnCommandCalled = true;
                mCommand = command;
                mExtras = extras;
                mCommandCallback = cb;
                mCallerInfo = currentControllerInfo;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepare() {
            synchronized (mWaitLock) {
                mOnPrepareCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPrepareFromMediaIdCalled = true;
                mMediaId = mediaId;
                mExtras = extras;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepareFromSearch(String query, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPrepareFromSearchCalled = true;
                mQuery = query;
                mExtras = extras;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public void onPrepareFromUri(Uri uri, Bundle extras) {
            synchronized (mWaitLock) {
                mOnPrepareFromUriCalled = true;
                mUri = uri;
                mExtras = extras;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mWaitLock.notify();
            }
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            synchronized (mWaitLock) {
                mOnMediaButtonEventCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mKeyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                mWaitLock.notify();
            }
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

        @Override
        public void onSetPlaybackSpeed(float speed) {
            synchronized (mWaitLock) {
                mOnSetPlaybackSpeedCalled = true;
                mCallerInfo = mSession.getCurrentControllerInfo();
                mSpeed = speed;
                mWaitLock.notify();
            }
        }
    }
}
