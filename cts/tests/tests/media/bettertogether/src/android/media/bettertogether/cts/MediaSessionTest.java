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

import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.bettertogether.cts.MediaSessionTestService.KEY_EXPECTED_QUEUE_SIZE;
import static android.media.bettertogether.cts.MediaSessionTestService.KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS;
import static android.media.bettertogether.cts.MediaSessionTestService.KEY_SESSION_TOKEN;
import static android.media.bettertogether.cts.MediaSessionTestService.STEP_CHECK;
import static android.media.bettertogether.cts.MediaSessionTestService.STEP_CLEAN_UP;
import static android.media.bettertogether.cts.MediaSessionTestService.STEP_SET_UP;
import static android.media.bettertogether.cts.MediaSessionTestService.TEST_SERIES_OF_SET_QUEUE;
import static android.media.bettertogether.cts.MediaSessionTestService.TEST_SET_QUEUE;
import static android.media.cts.Utils.compareRemoteUserInfo;
import static android.os.UserManager.USER_TYPE_PROFILE_CLONE;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaSession2;
import android.media.Rating;
import android.media.VolumeProvider;
import android.media.cts.Utils;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.RemoteUserInfo;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@NonMainlineTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaSessionTest {
    // The maximum time to wait for an operation that is expected to succeed.
    private static final long TIME_OUT_MS = 3000L;
    // The maximum time to wait for an operation that is expected to fail.
    private static final long WAIT_MS = 100L;
    private static final int MAX_AUDIO_INFO_CHANGED_CALLBACK_COUNT = 10;
    private static final String TEST_SESSION_TAG = "test-session-tag";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-val";
    private static final String TEST_SESSION_EVENT = "test-session-event";
    private static final String TEST_VOLUME_CONTROL_ID = "test-volume-control-id";
    private static final int TEST_CURRENT_VOLUME = 10;
    private static final int TEST_MAX_VOLUME = 11;
    private static final long TEST_QUEUE_ID = 12L;
    private static final long TEST_ACTION = 55L;
    private static final int TEST_TOO_MANY_SESSION_COUNT = 1000;
    private static final boolean SUPPORTS_MULTIPLE_USERS = UserManager.supportsMultipleUsers();

    private AudioManager mAudioManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Object mWaitLock = new Object();
    private MediaControllerCallback mCallback = new MediaControllerCallback();
    private MediaSession mSession;
    private RemoteUserInfo mKeyDispatcherInfo;
    private Context mContext;
    private Optional<Integer> mCloneProfileId = Optional.empty();

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Before
    public void setUp() {
        mContext = getContext();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mSession = new MediaSession(getContext(), TEST_SESSION_TAG);
        mKeyDispatcherInfo = new MediaSessionManager.RemoteUserInfo(
                getContext().getPackageName(), Process.myPid(), Process.myUid());
    }

    @After
    public void tearDown() throws Exception {
        // It is OK to call release() twice.
        if (mSession != null) {
            mSession.release();
            mSession = null;
        }
        removeCloneProfile();
    }

    private void createCloneProfile() {
        Assume.assumeTrue(SUPPORTS_MULTIPLE_USERS);

        InstrumentationRegistry
            .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        UserManager userManager = mContext.getSystemService(UserManager.class);
        boolean isCloneProfileEnabled = userManager.isUserTypeEnabled(USER_TYPE_PROFILE_CLONE);
        InstrumentationRegistry
            .getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        Assume.assumeTrue(isCloneProfileEnabled);

        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final String output = runShellCommand(
                "pm create-user --user-type android.os.usertype.profile.CLONE --profileOf "
                + context.getUserId() + " user2");

        // On a successful run the output will be like
        // Success: created user id 11
        // Hence we use the last index of " " to fetch the cloned profile id.
        int userIdIndex = output.lastIndexOf(" ");
        if (userIdIndex != -1) {
            mCloneProfileId = Optional.of(
                Integer.parseInt(output.substring(userIdIndex).trim()));
        }
    }

    private void removeCloneProfile() {
        mCloneProfileId.ifPresent(cloneProfileId -> {
            runShellCommand("am stop-user -w -f " + cloneProfileId);
            runShellCommand("pm remove-user " + cloneProfileId);
            mCloneProfileId = Optional.empty();
        });
    }

    /**
     * Tests that a session can be created and that all the fields are
     * initialized correctly.
     */
    @Test
    public void testCreateSession() throws Exception {
        assertThat(mSession.getSessionToken()).isNotNull();
        assertWithMessage("New session should not be active").that(mSession.isActive()).isFalse();

        // Verify by getting the controller and checking all its fields
        MediaController controller = mSession.getController();
        assertThat(controller).isNotNull();
        verifyNewSession(controller);
    }

    @Test
    // Needed for assertThat(sessionToken.equals(mSession)).isFalse().
    @SuppressWarnings("EqualsIncompatibleType")
    public void testSessionTokenEquals() {
        MediaSession anotherSession = null;
        try {
            anotherSession = new MediaSession(getContext(), TEST_SESSION_TAG);
            MediaSession.Token sessionToken = mSession.getSessionToken();
            MediaSession.Token anotherSessionToken = anotherSession.getSessionToken();

            // Explicitly checks equals as Guava's EqualsTester is not yet supported (b/236153976).
            assertThat(sessionToken.equals(sessionToken)).isTrue();
            assertThat(sessionToken.equals(null)).isFalse();
            assertThat(sessionToken.equals(mSession)).isFalse();
            assertThat(sessionToken.equals(anotherSessionToken)).isFalse();
        } finally {
            if (anotherSession != null) {
                anotherSession.release();
            }
        }
    }

    /**
     * Tests MediaSession.Token created in the constructor of MediaSession.
     */
    @Test
    public void testSessionToken() throws Exception {
        MediaSession.Token sessionToken = mSession.getSessionToken();

        assertThat(sessionToken).isNotNull();
        assertThat(sessionToken.describeContents()).isEqualTo(0);

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        sessionToken.writeToParcel(p, 0);
        p.setDataPosition(0);
        MediaSession.Token tokenFromParcel = MediaSession.Token.CREATOR.createFromParcel(p);
        assertThat(tokenFromParcel).isEqualTo(sessionToken);
        p.recycle();

        final int arraySize = 5;
        MediaSession.Token[] tokenArray = MediaSession.Token.CREATOR.newArray(arraySize);
        assertThat(tokenArray).isNotNull();
        assertThat(tokenArray.length).isEqualTo(arraySize);
        for (MediaSession.Token tokenElement : tokenArray) {
            assertThat(tokenElement).isNull();
        }
    }

    /**
     * Tests that the various configuration bits on a session get passed to the
     * controller.
     */
    @Test
    public void testConfigureSession() throws Exception {
        MediaController controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        final MediaController.Callback callback = (MediaController.Callback) mCallback;

        synchronized (mWaitLock) {
            // test setExtras
            mCallback.resetLocked();
            final Bundle extras = new Bundle();
            extras.putString(TEST_KEY, TEST_VALUE);
            mSession.setExtras(extras);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnExtraChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onExtrasChanged(mCallback.mExtras);

            Bundle extrasOut = mCallback.mExtras;
            assertThat(extrasOut).isNotNull();
            assertThat(extrasOut.get(TEST_KEY)).isEqualTo(TEST_VALUE);

            extrasOut = controller.getExtras();
            assertThat(extrasOut).isNotNull();
            assertThat(extrasOut.get(TEST_KEY)).isEqualTo(TEST_VALUE);

            // test setFlags
            mSession.setFlags(5);
            assertThat(controller.getFlags()).isEqualTo(5);

            // test setMetadata
            mCallback.resetLocked();
            MediaMetadata metadata =
                    new MediaMetadata.Builder().putString(TEST_KEY, TEST_VALUE).build();
            mSession.setMetadata(metadata);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnMetadataChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onMetadataChanged(mCallback.mMediaMetadata);

            MediaMetadata metadataOut = mCallback.mMediaMetadata;
            assertThat(metadataOut).isNotNull();
            assertThat(metadataOut.getString(TEST_KEY)).isEqualTo(TEST_VALUE);

            metadataOut = controller.getMetadata();
            assertThat(metadataOut).isNotNull();
            assertThat(metadataOut.getString(TEST_KEY)).isEqualTo(TEST_VALUE);

            // test setPlaybackState
            mCallback.resetLocked();
            PlaybackState state = new PlaybackState.Builder().setActions(TEST_ACTION).build();
            mSession.setPlaybackState(state);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnPlaybackStateChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onPlaybackStateChanged(mCallback.mPlaybackState);

            PlaybackState stateOut = mCallback.mPlaybackState;
            assertThat(stateOut).isNotNull();
            assertThat(stateOut.getActions()).isEqualTo(TEST_ACTION);

            stateOut = controller.getPlaybackState();
            assertThat(stateOut).isNotNull();
            assertThat(stateOut.getActions()).isEqualTo(TEST_ACTION);

            // test setQueue and setQueueTitle
            mCallback.resetLocked();
            List<QueueItem> queue = new ArrayList<>();
            QueueItem item = new QueueItem(new MediaDescription.Builder()
                    .setMediaId(TEST_VALUE).setTitle("title").build(), TEST_QUEUE_ID);
            queue.add(item);
            mSession.setQueue(queue);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnQueueChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onQueueChanged(mCallback.mQueue);

            mSession.setQueueTitle(TEST_VALUE);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnQueueTitleChangedCalled).isTrue();

            assertThat(mCallback.mTitle).isEqualTo(TEST_VALUE);
            assertThat(mCallback.mQueue.size()).isEqualTo(queue.size());
            assertThat(mCallback.mQueue.get(0).getQueueId()).isEqualTo(TEST_QUEUE_ID);
            assertThat(mCallback.mQueue.get(0).getDescription().getMediaId()).isEqualTo(TEST_VALUE);

            assertThat(controller.getQueueTitle()).isEqualTo(TEST_VALUE);
            assertThat(controller.getQueue().size()).isEqualTo(queue.size());
            assertThat(controller.getQueue().get(0).getQueueId()).isEqualTo(TEST_QUEUE_ID);
            assertThat(controller.getQueue().get(0).getDescription().getMediaId())
                    .isEqualTo(TEST_VALUE);

            mCallback.resetLocked();
            mSession.setQueue(null);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnQueueChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onQueueChanged(mCallback.mQueue);

            mSession.setQueueTitle(null);
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnQueueTitleChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onQueueTitleChanged(mCallback.mTitle);

            assertThat(mCallback.mTitle).isNull();
            assertThat(mCallback.mQueue).isNull();
            assertThat(controller.getQueueTitle()).isNull();
            assertThat(controller.getQueue()).isNull();

            // test setSessionActivity
            Intent intent = new Intent("cts.MEDIA_SESSION_ACTION");
            PendingIntent pi = PendingIntent.getActivity(getContext(), 555, intent,
                    PendingIntent.FLAG_MUTABLE_UNAUDITED);
            mSession.setSessionActivity(pi);
            assertThat(controller.getSessionActivity()).isEqualTo(pi);

            // test setActivity
            mSession.setActive(true);
            assertThat(mSession.isActive()).isTrue();

            // test sendSessionEvent
            mCallback.resetLocked();
            mSession.sendSessionEvent(TEST_SESSION_EVENT, extras);
            mWaitLock.wait(TIME_OUT_MS);

            assertThat(mCallback.mOnSessionEventCalled).isTrue();
            assertThat(mCallback.mEvent).isEqualTo(TEST_SESSION_EVENT);
            assertThat(mCallback.mExtras.getString(TEST_KEY)).isEqualTo(TEST_VALUE);
            // just call the callback once directly so it's marked as tested
            callback.onSessionEvent(mCallback.mEvent, mCallback.mExtras);

            // test release
            mCallback.resetLocked();
            mSession.release();
            mWaitLock.wait(TIME_OUT_MS);
            assertThat(mCallback.mOnSessionDestroyedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onSessionDestroyed();
        }
    }

    @Test
    public void setMediaSession_withInaccessibleUri_uriCleared() throws Exception {
        createCloneProfile();
        Assume.assumeTrue(mCloneProfileId.isPresent());
        String testMediaUri =
                "content://" + mCloneProfileId.get() + "@media/external/images/media/";
        // Save a screenshot in second user files.
        runShellCommand("screencap -p " + testMediaUri);

        MediaController controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        final MediaController.Callback callback = mCallback;

        synchronized (mWaitLock) {
            // test setMetadata
            mCallback.resetLocked();
            MediaMetadata metadata =
                    new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_ART_URI,
                            testMediaUri).build();
            mSession.setMetadata(metadata);
            mWaitLock.wait(TIME_OUT_MS);

            assertThat(mCallback.mOnMetadataChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onMetadataChanged(mCallback.mMediaMetadata);

            MediaMetadata metadataOut = mCallback.mMediaMetadata;
            assertThat(metadataOut).isNotNull();
            assertThat(TextUtils.isEmpty(metadataOut.getString(MediaMetadata.METADATA_KEY_ART_URI)))
                    .isTrue();
        }
    }

    @Test
    public void setMediaSession_withUri_uriExists() throws Exception {
        String testMediaUri = "content://media/external/images/media/";
        MediaController controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        final MediaController.Callback callback = mCallback;

        synchronized (mWaitLock) {
            // test setMetadata
            mCallback.resetLocked();
            MediaMetadata metadata =
                    new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_ART_URI,
                            testMediaUri).build();
            mSession.setMetadata(metadata);
            mWaitLock.wait(TIME_OUT_MS);

            assertThat(mCallback.mOnMetadataChangedCalled).isTrue();
            // just call the callback once directly so it's marked as tested
            callback.onMetadataChanged(mCallback.mMediaMetadata);

            MediaMetadata metadataOut = mCallback.mMediaMetadata;
            assertThat(metadataOut).isNotNull();
            assertThat(metadataOut.getString(MediaMetadata.METADATA_KEY_ART_URI))
                    .isEqualTo(testMediaUri);
        }
    }

    /**
     * Test whether media button receiver can be a explicit broadcast receiver via
     * MediaSession.setMediaButtonReceiver(PendingIntent).
     */
    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testSetMediaButtonReceiver_broadcastReceiver() throws Exception {
        Intent intent = new Intent(mContext.getApplicationContext(),
                MediaButtonBroadcastReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE_UNAUDITED);

        // Play a sound so this session can get the priority.
        Utils.assertMediaPlaybackStarted(getContext());

        // There is a different MediaSession that's created from StubMediaBrowserService class.
        // This session takes over all the callbacks. we need to change the state of our session
        // to STATE_PLAYING so it has higher priority.
        setPlaybackState(PlaybackState.STATE_PLAYING);

        // Sets the media button receiver. Framework will keep the broadcast receiver component name
        // from the pending intent in persistent storage.
        mSession.setMediaButtonReceiver(pi);

        // Call explicit release, so change in the media key event session can be notified with the
        // pending intent.
        mSession.release();

        int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;
        try {
            CountDownLatch latch = new CountDownLatch(2);
            MediaButtonBroadcastReceiver.setCallback((keyEvent) -> {
                assertThat(keyEvent.getKeyCode()).isEqualTo(keyCode);
                switch ((int) latch.getCount()) {
                    case 2:
                        assertThat(keyEvent.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
                        break;
                    case 1:
                        assertThat(keyEvent.getAction()).isEqualTo(KeyEvent.ACTION_UP);
                        break;
                }
                latch.countDown();
            });
            // Also try to dispatch media key event.
            // System would try to dispatch event.
            simulateMediaKeyInput(keyCode);

            assertThat(latch.await(TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            MediaButtonBroadcastReceiver.setCallback(null);
        }
    }

    /**
     * Test whether media button receiver can be a explicit service.
     */
    @Test
    public void testSetMediaButtonReceiver_service() throws Exception {
        Intent intent = new Intent(mContext.getApplicationContext(),
                MediaButtonReceiverService.class);
        PendingIntent pi = PendingIntent.getService(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE_UNAUDITED);

        // Play a sound so this session can get the priority.
        Utils.assertMediaPlaybackStarted(getContext());

        // There is a different MediaSession that's created from StubMediaBrowserService class.
        // This session takes over all the callbacks. we need to change the state of our session
        // to STATE_PLAYING so it has higher priority.
        setPlaybackState(PlaybackState.STATE_PLAYING);

        // Sets the media button receiver. Framework would try to keep the pending intent in the
        // persistent store.
        mSession.setMediaButtonReceiver(pi);

        // Call explicit release, so change in the media key event session can be notified with the
        // pending intent.
        mSession.release();

        int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;
        try {
            CountDownLatch latch = new CountDownLatch(2);
            MediaButtonReceiverService.setCallback((keyEvent) -> {
                assertThat(keyEvent.getKeyCode()).isEqualTo(keyCode);
                switch ((int) latch.getCount()) {
                    case 2:
                        assertThat(keyEvent.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
                        break;
                    case 1:
                        assertThat(keyEvent.getAction()).isEqualTo(KeyEvent.ACTION_UP);
                        break;
                }
                latch.countDown();
            });
            // Also try to dispatch media key event.
            // System would try to dispatch event.
            simulateMediaKeyInput(keyCode);

            assertThat(latch.await(TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            MediaButtonReceiverService.setCallback(null);
        }
    }

    /**
     * Test whether system doesn't crash by
     * {@link MediaSession#setMediaButtonReceiver(PendingIntent)} with implicit intent.
     */
    @Test
    public void testSetMediaButtonReceiver_implicitIntent() throws Exception {
        // Note: No such broadcast receiver exists.
        Intent intent = new Intent("android.media.bettertogether.cts.ACTION_MEDIA_TEST");
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE_UNAUDITED);

        // Play a sound so this session can get the priority.
        Utils.assertMediaPlaybackStarted(getContext());

        // Sets the media button receiver. Framework would try to keep the pending intent in the
        // persistent store.
        mSession.setMediaButtonReceiver(pi);

        // Call explicit release, so change in the media key event session can be notified with the
        // pending intent.
        mSession.release();

        // Also try to dispatch media key event. System would try to send key event via pending
        // intent, but it would no-op because there's no receiver.
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    @Test
    public void testSetMediaButtonReceiver_withNull_doesNotThrow() {
        try {
            mSession.setMediaButtonReceiver(null);
        } finally {
            mSession.release();
        }
    }

    /**
     * Test whether media button receiver can be a explicit broadcast receiver via
     * MediaSession.setMediaButtonBroadcastReceiver(ComponentName)
     */
    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testSetMediaButtonBroadcastReceiver_broadcastReceiver() throws Exception {
        // Play a sound so this session can get the priority.
        Utils.assertMediaPlaybackStarted(getContext());

        // Sets the broadcast receiver's component name. Framework will keep the component name in
        // persistent storage.
        mSession.setMediaButtonBroadcastReceiver(new ComponentName(mContext,
                MediaButtonBroadcastReceiver.class));

        // There is a different MediaSession that's created from StubMediaBrowserService class.
        // This session takes over all the callbacks. we need to change the state of our session
        // to STATE_PLAYING so it has higher priority.
        setPlaybackState(PlaybackState.STATE_PLAYING);

        // Call explicit release, so change in the media key event session can be notified using the
        // component name.
        mSession.release();

        int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;
        try {
            CountDownLatch latch = new CountDownLatch(2);
            MediaButtonBroadcastReceiver.setCallback((keyEvent) -> {
                assertThat(keyEvent.getKeyCode()).isEqualTo(keyCode);
                switch ((int) latch.getCount()) {
                    case 2:
                        assertThat(keyEvent.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
                        break;
                    case 1:
                        assertThat(keyEvent.getAction()).isEqualTo(KeyEvent.ACTION_UP);
                        break;
                }
                latch.countDown();
            });
            // Also try to dispatch media key event.
            // System would try to dispatch event.
            simulateMediaKeyInput(keyCode);

            assertThat(latch.await(TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            MediaButtonBroadcastReceiver.setCallback(null);
        }
    }

    /**
     * Test public APIs of {@link VolumeProvider}.
     */
    @Test
    public void testVolumeProvider() {
        VolumeProvider vp = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE,
                TEST_MAX_VOLUME, TEST_CURRENT_VOLUME, TEST_VOLUME_CONTROL_ID) {};
        assertThat(vp.getVolumeControl()).isEqualTo(VolumeProvider.VOLUME_CONTROL_RELATIVE);
        assertThat(vp.getMaxVolume()).isEqualTo(TEST_MAX_VOLUME);
        assertThat(vp.getCurrentVolume()).isEqualTo(TEST_CURRENT_VOLUME);
        assertThat(vp.getVolumeControlId()).isEqualTo(TEST_VOLUME_CONTROL_ID);
    }

    /**
     * Test {@link MediaSession#setPlaybackToLocal} and {@link MediaSession#setPlaybackToRemote}.
     */
    @Test
    public void testPlaybackToLocalAndRemote() throws Exception {
        MediaController controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);

        synchronized (mWaitLock) {
            // test setPlaybackToRemote, do this before testing setPlaybackToLocal
            // to ensure it switches correctly.
            mCallback.resetLocked();
            try {
                mSession.setPlaybackToRemote(null);
                assertWithMessage("Expected IAE for setPlaybackToRemote(null)").fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
            VolumeProvider vp = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_FIXED,
                    TEST_MAX_VOLUME, TEST_CURRENT_VOLUME, TEST_VOLUME_CONTROL_ID) {};
            mSession.setPlaybackToRemote(vp);

            MediaController.PlaybackInfo info = null;
            for (int i = 0; i < MAX_AUDIO_INFO_CHANGED_CALLBACK_COUNT; ++i) {
                mCallback.mOnAudioInfoChangedCalled = false;
                mWaitLock.wait(TIME_OUT_MS);
                assertThat(mCallback.mOnAudioInfoChangedCalled).isTrue();
                info = mCallback.mPlaybackInfo;
                if (info != null && info.getCurrentVolume() == TEST_CURRENT_VOLUME
                        && info.getMaxVolume() == TEST_MAX_VOLUME
                        && info.getVolumeControl() == VolumeProvider.VOLUME_CONTROL_FIXED
                        && info.getPlaybackType()
                                == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
                        && TextUtils.equals(info.getVolumeControlId(), TEST_VOLUME_CONTROL_ID)) {
                    break;
                }
            }
            assertThat(info).isNotNull();
            assertThat(info.getPlaybackType())
                    .isEqualTo(MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE);
            assertThat(info.getMaxVolume()).isEqualTo(TEST_MAX_VOLUME);
            assertThat(info.getCurrentVolume()).isEqualTo(TEST_CURRENT_VOLUME);
            assertThat(info.getVolumeControl()).isEqualTo(VolumeProvider.VOLUME_CONTROL_FIXED);
            assertThat(info.getVolumeControlId()).isEqualTo(TEST_VOLUME_CONTROL_ID);

            info = controller.getPlaybackInfo();
            assertThat(info).isNotNull();
            assertThat(info.getPlaybackType())
                    .isEqualTo(MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE);
            assertThat(info.getMaxVolume()).isEqualTo(TEST_MAX_VOLUME);
            assertThat(info.getCurrentVolume()).isEqualTo(TEST_CURRENT_VOLUME);
            assertThat(info.getVolumeControl()).isEqualTo(VolumeProvider.VOLUME_CONTROL_FIXED);
            assertThat(info.getVolumeControlId()).isEqualTo(TEST_VOLUME_CONTROL_ID);

            // test setPlaybackToLocal
            AudioAttributes attrs = new AudioAttributes.Builder().setUsage(USAGE_GAME).build();
            mSession.setPlaybackToLocal(attrs);

            info = controller.getPlaybackInfo();
            assertThat(info).isNotNull();
            assertThat(info.getPlaybackType())
                    .isEqualTo(MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL);
            assertThat(info.getAudioAttributes()).isEqualTo(attrs);
            assertThat(info.getVolumeControlId()).isNull();
        }
    }

    /**
     * Test {@link MediaSession.Callback#onMediaButtonEvent}.
     */
    @Test
    public void testCallbackOnMediaButtonEvent() throws Exception {
        MediaSessionCallback sessionCallback = new MediaSessionCallback();
        mSession.setCallback(sessionCallback, new Handler(Looper.getMainLooper()));
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        mSession.setActive(true);

        // Set state to STATE_PLAYING to get higher priority.
        setPlaybackState(PlaybackState.STATE_PLAYING);

        // A media playback is also needed to receive media key events.
        Utils.assertMediaPlaybackStarted(getContext());

        sessionCallback.reset(1);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnPlayCalledCount).isEqualTo(1);
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        sessionCallback.reset(1);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PAUSE);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnPauseCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        sessionCallback.reset(1);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_NEXT);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnSkipToNextCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        sessionCallback.reset(1);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnSkipToPreviousCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        sessionCallback.reset(1);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_STOP);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnStopCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        sessionCallback.reset(1);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnFastForwardCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        sessionCallback.reset(1);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_REWIND);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnRewindCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        // Test PLAY_PAUSE button twice.
        // First, simulate PLAY_PAUSE button while in STATE_PAUSED.
        sessionCallback.reset(1);
        setPlaybackState(PlaybackState.STATE_PAUSED);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnPlayCalledCount).isEqualTo(1);
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        // Next, simulate PLAY_PAUSE button while in STATE_PLAYING.
        sessionCallback.reset(1);
        setPlaybackState(PlaybackState.STATE_PLAYING);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnPauseCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        // Double tap of PLAY_PAUSE is the next track instead of changing PLAY/PAUSE.
        sessionCallback.reset(2);
        setPlaybackState(PlaybackState.STATE_PLAYING);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertThat(sessionCallback.await(WAIT_MS)).isFalse();
        assertThat(sessionCallback.mOnSkipToNextCalled).isTrue();
        assertThat(sessionCallback.mOnPlayCalledCount).isEqualTo(0);
        assertThat(sessionCallback.mOnPauseCalled).isFalse();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        // Test if PLAY_PAUSE double tap is considered as two single taps when another media
        // key is pressed.
        sessionCallback.reset(3);
        setPlaybackState(PlaybackState.STATE_PAUSED);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_STOP);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnPlayCalledCount).isEqualTo(2);
        assertThat(sessionCallback.mOnStopCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();

        // Test if media keys are handled in order.
        sessionCallback.reset(2);
        setPlaybackState(PlaybackState.STATE_PAUSED);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        simulateMediaKeyInput(KeyEvent.KEYCODE_MEDIA_STOP);
        assertThat(sessionCallback.await(TIME_OUT_MS)).isTrue();
        assertThat(sessionCallback.mOnPlayCalledCount).isEqualTo(1);
        assertThat(sessionCallback.mOnStopCalled).isTrue();
        assertThat(compareRemoteUserInfo(mKeyDispatcherInfo, sessionCallback.mCallerInfo)).isTrue();
        synchronized (mWaitLock) {
            assertThat(mSession.getController().getPlaybackState().getState())
                    .isEqualTo(PlaybackState.STATE_STOPPED);
        }
    }

    /**
     * Tests {@link MediaSession#setCallback} with {@code null}. No callbacks will be called
     * once {@code setCallback(null)} is done.
     */
    @Test
    public void testSetCallbackWithNull() throws Exception {
        MediaSessionCallback sessionCallback = new MediaSessionCallback();
        mSession.setCallback(sessionCallback, mHandler);
        mSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setActive(true);

        MediaController controller = mSession.getController();
        setPlaybackState(PlaybackState.STATE_PLAYING);

        sessionCallback.reset(1);
        mSession.setCallback(null, mHandler);

        controller.getTransportControls().pause();
        assertThat(sessionCallback.await(WAIT_MS)).isFalse();
        assertWithMessage("Callback shouldn't be called.")
                .that(sessionCallback.mOnPauseCalled).isFalse();
    }

    private void setPlaybackState(int state) {
        final long allActions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND;
        PlaybackState playbackState = new PlaybackState.Builder().setActions(allActions)
                .setState(state, 0L, 0.0f).build();
        synchronized (mWaitLock) {
            mSession.setPlaybackState(playbackState);
        }
    }

    /**
     * Test {@link MediaSession#release} doesn't crash when multiple media sessions are in the app
     * which receives the media key events.
     * See: b/36669550
     */
    @Test
    public void testReleaseNoCrashWithMultipleSessions() throws Exception {
        // Start a media playback for this app to receive media key events.
        Utils.assertMediaPlaybackStarted(getContext());

        MediaSession anotherSession = null;
        try {
            anotherSession = new MediaSession(getContext(), TEST_SESSION_TAG);
            mSession.release();
            anotherSession.release();

            // Try release with the different order.
            mSession = new MediaSession(getContext(), TEST_SESSION_TAG);
            anotherSession = new MediaSession(getContext(), TEST_SESSION_TAG);
            anotherSession.release();
            mSession.release();
        } finally {
            if (anotherSession != null) {
                anotherSession.release();
                anotherSession = null;
            }
        }
    }

    // This uses public APIs to dispatch key events, so sessions would consider this as
    // 'media key event from this application'.
    private void simulateMediaKeyInput(int keyCode) {
        long downTime = System.currentTimeMillis();
        mAudioManager.dispatchMediaKeyEvent(
                new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0));
        mAudioManager.dispatchMediaKeyEvent(
                new KeyEvent(downTime, System.currentTimeMillis(), KeyEvent.ACTION_UP, keyCode, 0));
    }

    /**
     * Tests {@link MediaSession.QueueItem}.
     */
    @Test
    public void testQueueItem() {
        MediaDescription.Builder descriptionBuilder = new MediaDescription.Builder()
                .setMediaId("media-id")
                .setTitle("title");

        try {
            new QueueItem(/*description=*/null, TEST_QUEUE_ID);
            assertWithMessage("Unreachable statement.").fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            new QueueItem(descriptionBuilder.build(), QueueItem.UNKNOWN_ID);
            assertWithMessage("Unreachable statement.").fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }

        QueueItem item = new QueueItem(descriptionBuilder.build(), TEST_QUEUE_ID);

        Parcel p = Parcel.obtain();
        item.writeToParcel(p, 0);
        p.setDataPosition(0);
        QueueItem other = QueueItem.CREATOR.createFromParcel(p);
        assertThat(other.toString()).isEqualTo(item.toString());
        p.recycle();

        final int arraySize = 5;
        QueueItem[] queueItemArray = QueueItem.CREATOR.newArray(arraySize);
        assertThat(queueItemArray).isNotNull();
        assertThat(queueItemArray.length).isEqualTo(arraySize);
        for (QueueItem elem : queueItemArray) {
            assertThat(elem).isNull();
        }
    }

    @Test
    public void testQueueItemEquals() {
        MediaDescription.Builder descriptionBuilder = new MediaDescription.Builder()
                .setMediaId("media-id")
                .setTitle("title");

        QueueItem item = new QueueItem(descriptionBuilder.build(), TEST_QUEUE_ID);
        assertThat(item.getQueueId()).isEqualTo(TEST_QUEUE_ID);
        assertThat(item.getDescription().getMediaId()).isEqualTo("media-id");
        assertThat(item.getDescription().getTitle()).isEqualTo("title");
        assertThat(item.describeContents()).isEqualTo(0);

        assertThat(item.equals(null)).isFalse();
        assertThat(item).isNotEqualTo(descriptionBuilder.build());

        QueueItem sameItem = new QueueItem(descriptionBuilder.build(), TEST_QUEUE_ID);
        assertThat(item.equals(sameItem)).isTrue();

        QueueItem differentQueueId = new QueueItem(
                descriptionBuilder.build(), TEST_QUEUE_ID + 1);
        assertThat(item.equals(differentQueueId)).isFalse();

        QueueItem differentDescription = new QueueItem(
                descriptionBuilder.setTitle("title2").build(), TEST_QUEUE_ID);
        assertThat(item.equals(differentDescription)).isFalse();
    }

    @Test
    public void testSessionInfoWithFrameworkParcelable() {
        final String testKey = "test_key";
        final AudioAttributes frameworkParcelable = new AudioAttributes.Builder().build();

        Bundle sessionInfo = new Bundle();
        sessionInfo.putParcelable(testKey, frameworkParcelable);

        MediaSession session = null;
        try {
            session = new MediaSession(
                    mContext, "testSessionInfoWithFrameworkParcelable", sessionInfo);
            Bundle sessionInfoOut = session.getController().getSessionInfo();
            assertThat(sessionInfoOut.containsKey(testKey)).isTrue();
            assertThat((AudioAttributes) sessionInfoOut.getParcelable(testKey))
                    .isEqualTo(frameworkParcelable);
        } finally {
            if (session != null) {
                session.release();
            }
        }

    }

    @Test
    public void testSessionInfoWithCustomParcelable() {
        final String testKey = "test_key";
        final MediaSession2Test.CustomParcelable customParcelable =
                new MediaSession2Test.CustomParcelable(1);

        Bundle sessionInfo = new Bundle();
        sessionInfo.putParcelable(testKey, customParcelable);

        MediaSession session = null;
        try {
            session = new MediaSession(
                    mContext, "testSessionInfoWithCustomParcelable", sessionInfo);
            assertWithMessage("Custom Parcelable shouldn't be accepted!").fail();
        } catch (IllegalArgumentException e) {
            // Expected
        } finally {
            if (session != null) {
                session.release();
            }
        }
    }

    /**
     * An app should not be able to create too many sessions.
     * See MediaSessionService#SESSION_CREATION_LIMIT_PER_UID
     */
    @Test
    public void testSessionCreationLimit() {
        List<MediaSession> sessions = new ArrayList<>();
        try {
            for (int i = 0; i < TEST_TOO_MANY_SESSION_COUNT; i++) {
                sessions.add(new MediaSession(mContext, "testSessionCreationLimit"));
            }
            assertWithMessage("The number of session should be limited!").fail();
        } catch (RuntimeException e) {
            // Expected
        } finally {
            for (MediaSession session : sessions) {
                session.release();
            }
        }
    }

    /**
     * Check that calling {@link MediaSession#release()} multiple times for the same session
     * does not decrement current session count multiple times.
     */
    @Test
    public void testSessionCreationLimitWithMediaSessionRelease() {
        List<MediaSession> sessions = new ArrayList<>();
        MediaSession sessionToReleaseMultipleTimes = null;
        try {
            sessionToReleaseMultipleTimes = new MediaSession(
                    mContext, "testSessionCreationLimitWithMediaSessionRelease");
            for (int i = 0; i < TEST_TOO_MANY_SESSION_COUNT; i++) {
                sessions.add(new MediaSession(
                        mContext, "testSessionCreationLimitWithMediaSessionRelease"));
                // Call release() many times with the same session.
                sessionToReleaseMultipleTimes.release();
            }
            assertWithMessage("The number of session should be limited!").fail();
        } catch (RuntimeException e) {
            // Expected
        } finally {
            for (MediaSession session : sessions) {
                session.release();
            }
            if (sessionToReleaseMultipleTimes != null) {
                sessionToReleaseMultipleTimes.release();
            }
        }
    }

    /**
     * Check that calling {@link MediaSession2#close()} does not decrement current session count.
     */
    @Test
    public void testSessionCreationLimitWithMediaSession2Release() {
        List<MediaSession> sessions = new ArrayList<>();
        try {
            for (int i = 0; i < 1000; i++) {
                sessions.add(new MediaSession(
                        mContext, "testSessionCreationLimitWithMediaSession2Release"));

                try (MediaSession2 session2 = new MediaSession2.Builder(mContext).build()) {
                    // Do nothing
                }
            }
            assertWithMessage("The number of session should be limited!").fail();
        } catch (RuntimeException e) {
            // Expected
        } finally {
            for (MediaSession session : sessions) {
                session.release();
            }
        }
    }

    /**
     * Check that a series of {@link MediaSession#setQueue} does not break {@link MediaController}
     * on the remote process due to binder buffer overflow.
     */
    @Test
    public void testSeriesOfSetQueue() throws Exception {
        int numberOfCalls = 100;
        int queueSize = 1_000;
        List<QueueItem> queue = new ArrayList<>();
        for (int id = 0; id < queueSize; id++) {
            MediaDescription description = new MediaDescription.Builder()
                    .setMediaId(Integer.toString(id)).build();
            queue.add(new QueueItem(description, id));
        }

        try (RemoteService.Invoker invoker = new RemoteService.Invoker(mContext,
                MediaSessionTestService.class, TEST_SERIES_OF_SET_QUEUE)) {
            Bundle args = new Bundle();
            args.putParcelable(KEY_SESSION_TOKEN, mSession.getSessionToken());
            args.putInt(KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS, numberOfCalls * queueSize);
            invoker.run(STEP_SET_UP, args);
            for (int i = 0; i < numberOfCalls; i++) {
                mSession.setQueue(queue);
            }
            invoker.run(STEP_CHECK);
            invoker.run(STEP_CLEAN_UP);
        }
    }

    @Test
    public void testSetQueueWithLargeNumberOfItems() throws Exception {
        int queueSize = 500_000;
        List<QueueItem> queue = new ArrayList<>();
        for (int id = 0; id < queueSize; id++) {
            MediaDescription description = new MediaDescription.Builder()
                    .setMediaId(Integer.toString(id)).build();
            queue.add(new QueueItem(description, id));
        }

        try (RemoteService.Invoker invoker = new RemoteService.Invoker(mContext,
                MediaSessionTestService.class, TEST_SET_QUEUE)) {
            Bundle args = new Bundle();
            args.putParcelable(KEY_SESSION_TOKEN, mSession.getSessionToken());
            args.putInt(KEY_EXPECTED_QUEUE_SIZE, queueSize);
            invoker.run(STEP_SET_UP, args);
            mSession.setQueue(queue);
            invoker.run(STEP_CHECK);
            invoker.run(STEP_CLEAN_UP);
        }
    }

    @Test
    public void testSetQueueWithEmptyQueue() throws Exception {
        try (RemoteService.Invoker invoker = new RemoteService.Invoker(mContext,
                MediaSessionTestService.class, TEST_SET_QUEUE)) {
            Bundle args = new Bundle();
            args.putParcelable(KEY_SESSION_TOKEN, mSession.getSessionToken());
            args.putInt(KEY_EXPECTED_QUEUE_SIZE, 0);
            invoker.run(STEP_SET_UP, args);
            mSession.setQueue(Collections.emptyList());
            invoker.run(STEP_CHECK);
            invoker.run(STEP_CLEAN_UP);
        }
    }

    /**
     * Verifies that a new session hasn't had any configuration bits set yet.
     *
     * @param controller The controller for the session
     */
    @SuppressWarnings("ReturnValueIgnored")
    private void verifyNewSession(MediaController controller) {
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getFlags()).isEqualTo(0);
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getExtras()).isNull();
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getMetadata()).isNull();
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getPackageName()).isEqualTo(getContext().getPackageName());
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getPlaybackState()).isNull();
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getQueue()).isNull();
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getQueueTitle()).isNull();
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getRatingType()).isEqualTo(Rating.RATING_NONE);
        assertWithMessage("New session has unexpected configuration")
                .that(controller.getSessionActivity()).isNull();

        assertThat(controller.getSessionToken()).isNotNull();
        assertThat(controller.getTransportControls()).isNotNull();

        MediaController.PlaybackInfo info = controller.getPlaybackInfo();
        assertThat(info).isNotNull();
        info.toString(); // Test that calling PlaybackInfo.toString() does not crash.
        assertThat(info.getPlaybackType())
                .isEqualTo(MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL);
        AudioAttributes attrs = info.getAudioAttributes();
        assertThat(attrs).isNotNull();
        assertThat(attrs.getUsage()).isEqualTo(USAGE_MEDIA);
        assertThat(info.getCurrentVolume())
                .isEqualTo(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    private class MediaControllerCallback extends MediaController.Callback {
        private volatile boolean mOnPlaybackStateChangedCalled;
        private volatile boolean mOnMetadataChangedCalled;
        private volatile boolean mOnQueueChangedCalled;
        private volatile boolean mOnQueueTitleChangedCalled;
        private volatile boolean mOnExtraChangedCalled;
        private volatile boolean mOnAudioInfoChangedCalled;
        private volatile boolean mOnSessionDestroyedCalled;
        private volatile boolean mOnSessionEventCalled;

        private volatile PlaybackState mPlaybackState;
        private volatile MediaMetadata mMediaMetadata;
        private volatile List<QueueItem> mQueue;
        private volatile CharSequence mTitle;
        private volatile String mEvent;
        private volatile Bundle mExtras;
        private volatile MediaController.PlaybackInfo mPlaybackInfo;

        public void resetLocked() {
            mOnPlaybackStateChangedCalled = false;
            mOnMetadataChangedCalled = false;
            mOnQueueChangedCalled = false;
            mOnQueueTitleChangedCalled = false;
            mOnExtraChangedCalled = false;
            mOnAudioInfoChangedCalled = false;
            mOnSessionDestroyedCalled = false;
            mOnSessionEventCalled = false;

            mPlaybackState = null;
            mMediaMetadata = null;
            mQueue = null;
            mTitle = null;
            mExtras = null;
            mPlaybackInfo = null;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            synchronized (mWaitLock) {
                mOnPlaybackStateChangedCalled = true;
                mPlaybackState = state;
                mWaitLock.notify();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            synchronized (mWaitLock) {
                mOnMetadataChangedCalled = true;
                mMediaMetadata = metadata;
                mWaitLock.notify();
            }
        }

        @Override
        public void onQueueChanged(List<QueueItem> queue) {
            synchronized (mWaitLock) {
                mOnQueueChangedCalled = true;
                mQueue = queue;
                mWaitLock.notify();
            }
        }

        @Override
        public void onQueueTitleChanged(CharSequence title) {
            synchronized (mWaitLock) {
                mOnQueueTitleChangedCalled = true;
                mTitle = title;
                mWaitLock.notify();
            }
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            synchronized (mWaitLock) {
                mOnExtraChangedCalled = true;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
            synchronized (mWaitLock) {
                mOnAudioInfoChangedCalled = true;
                mPlaybackInfo = info;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSessionDestroyed() {
            synchronized (mWaitLock) {
                mOnSessionDestroyedCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSessionEvent(String event, Bundle extras) {
            synchronized (mWaitLock) {
                mOnSessionEventCalled = true;
                mEvent = event;
                mExtras = (Bundle) extras.clone();
                mWaitLock.notify();
            }
        }
    }

    private class MediaSessionCallback extends MediaSession.Callback {
        private CountDownLatch mLatch;
        private int mOnPlayCalledCount;
        private boolean mOnPauseCalled;
        private boolean mOnStopCalled;
        private boolean mOnFastForwardCalled;
        private boolean mOnRewindCalled;
        private boolean mOnSkipToPreviousCalled;
        private boolean mOnSkipToNextCalled;
        private RemoteUserInfo mCallerInfo;

        public void reset(int count) {
            mLatch = new CountDownLatch(count);
            mOnPlayCalledCount = 0;
            mOnPauseCalled = false;
            mOnStopCalled = false;
            mOnFastForwardCalled = false;
            mOnRewindCalled = false;
            mOnSkipToPreviousCalled = false;
            mOnSkipToNextCalled = false;
        }

        public boolean await(long waitMs) {
            try {
                return mLatch.await(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        @Override
        public void onPlay() {
            mOnPlayCalledCount++;
            mCallerInfo = mSession.getCurrentControllerInfo();
            setPlaybackState(PlaybackState.STATE_PLAYING);
            mLatch.countDown();
        }

        @Override
        public void onPause() {
            mOnPauseCalled = true;
            mCallerInfo = mSession.getCurrentControllerInfo();
            setPlaybackState(PlaybackState.STATE_PAUSED);
            mLatch.countDown();
        }

        @Override
        public void onStop() {
            mOnStopCalled = true;
            mCallerInfo = mSession.getCurrentControllerInfo();
            setPlaybackState(PlaybackState.STATE_STOPPED);
            mLatch.countDown();
        }

        @Override
        public void onFastForward() {
            mOnFastForwardCalled = true;
            mCallerInfo = mSession.getCurrentControllerInfo();
            mLatch.countDown();
        }

        @Override
        public void onRewind() {
            mOnRewindCalled = true;
            mCallerInfo = mSession.getCurrentControllerInfo();
            mLatch.countDown();
        }

        @Override
        public void onSkipToPrevious() {
            mOnSkipToPreviousCalled = true;
            mCallerInfo = mSession.getCurrentControllerInfo();
            mLatch.countDown();
        }

        @Override
        public void onSkipToNext() {
            mOnSkipToNextCalled = true;
            mCallerInfo = mSession.getCurrentControllerInfo();
            mLatch.countDown();
        }
    }
}
