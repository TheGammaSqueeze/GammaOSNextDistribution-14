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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaSession2;
import android.media.Session2CommandGroup;
import android.media.Session2Token;
import android.media.cts.Utils;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaSessionManagerTest {
    private static final String TAG = "MediaSessionManagerTest";
    private static final int TIMEOUT_MS = 3000;
    private static final int WAIT_MS = 500;
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";

    @Rule public final ResourceReleaser mResourceReleaser = new ResourceReleaser();

    private Context mContext;
    private AudioManager mAudioManager;
    private MediaSessionManager mSessionManager;

    private static boolean sIsAtLeastU = ApiLevelUtil.isAtLeast(
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    @Before
    public void setUp() {
        mContext = getInstrumentation().getContext();
        mAudioManager = (AudioManager) getInstrumentation().getTargetContext()
                .getSystemService(Context.AUDIO_SERVICE);
        mSessionManager = (MediaSessionManager) getInstrumentation().getTargetContext()
                .getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @After
    public void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    @NonMainlineTest
    public void testGetActiveSessions() throws Exception {
        assertThrows("Expected security exception for unauthorized call to getActiveSessions",
                SecurityException.class,
                () -> mSessionManager.getActiveSessions(null));
        // TODO enable a notification listener, test again, disable, test again
    }

    @Test
    @NonMainlineTest
    public void testGetMediaKeyEventSession_throwsSecurityException() {
        assertThrows("Expected security exception for call to getMediaKeyEventSession",
                SecurityException.class,
                () -> mSessionManager.getMediaKeyEventSession());
    }

    @Test
    @NonMainlineTest
    public void testGetMediaKeyEventSessionPackageName_throwsSecurityException() {
        assertThrows("Expected security exception for call to getMediaKeyEventSessionPackageName",
                SecurityException.class,
                () -> mSessionManager.getMediaKeyEventSessionPackageName());
    }

    @Test
    @NonMainlineTest
    public void testOnMediaKeyEventSessionChangedListener() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MEDIA_CONTENT_CONTROL,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE);

        MediaKeyEventSessionListener keyEventSessionListener = new MediaKeyEventSessionListener();
        mSessionManager.addOnMediaKeyEventSessionChangedListener(
                createExecutorWithScheduledShutdown(), keyEventSessionListener);

        MediaSession session = createMediaKeySession();
        mResourceReleaser.add(session::release);
        assertThat(keyEventSessionListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        assertThat(keyEventSessionListener.mSessionToken).isEqualTo(session.getSessionToken());
        assertThat(mSessionManager.getMediaKeyEventSession()).isEqualTo(session.getSessionToken());
        assertThat(mSessionManager.getMediaKeyEventSessionPackageName())
                .isEqualTo(getInstrumentation().getTargetContext().getPackageName());

        mSessionManager.removeOnMediaKeyEventSessionChangedListener(keyEventSessionListener);
        keyEventSessionListener.resetCountDownLatch();

        // This shouldn't be called because the callback is removed
        assertThat(keyEventSessionListener.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS))
                .isFalse();
    }

    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    @NonMainlineTest
    public void testOnMediaKeyEventSessionChangedListener_whenSessionIsReleased() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MEDIA_CONTENT_CONTROL,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE);

        MediaKeyEventSessionListener keyEventSessionListener = new MediaKeyEventSessionListener();
        mSessionManager.addOnMediaKeyEventSessionChangedListener(
                createExecutorWithScheduledShutdown(), keyEventSessionListener);

        MediaSession session = createMediaKeySession();
        assertThat(keyEventSessionListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        // Check that this is called when the session is released.
        keyEventSessionListener.resetCountDownLatch();
        session.release();
        assertThat(keyEventSessionListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(keyEventSessionListener.mSessionToken).isNull();
        assertThat(keyEventSessionListener.mPackageName).isEmpty();

        assertThat(mSessionManager.getMediaKeyEventSession()).isNull();
        assertThat(mSessionManager.getMediaKeyEventSessionPackageName()).isEqualTo("");
    }

    @Test
    @NonMainlineTest
    public void testOnMediaKeyEventSessionChangedListener_noSession_passesEmptyPackageAndNullToken()
            throws InterruptedException {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MEDIA_CONTENT_CONTROL);

        MediaKeyEventSessionListener keyEventSessionListener = new MediaKeyEventSessionListener();
        mSessionManager.addOnMediaKeyEventSessionChangedListener(
                createExecutorWithScheduledShutdown(), keyEventSessionListener);

        assertThat(keyEventSessionListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(keyEventSessionListener.mSessionToken).isNull();
        assertThat(keyEventSessionListener.mPackageName).isEmpty();

        // Clean up listener.
        mSessionManager.removeOnMediaKeyEventSessionChangedListener(keyEventSessionListener);
    }

    private MediaSession createMediaKeySession() {
        MediaSession session = new MediaSession(getInstrumentation().getTargetContext(), TAG);
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1.0f).build();
        // Fake the media session service so this session can take the media key events.
        session.setPlaybackState(state);
        session.setActive(true);
        Utils.assertMediaPlaybackStarted(getInstrumentation().getTargetContext());

        return session;
    }

    @Test
    @NonMainlineTest
    public void testOnMediaKeyEventSessionChangedListener_noPermission_throwsSecurityException() {
        MediaKeyEventSessionListener keyEventSessionListener = new MediaKeyEventSessionListener();
        assertThrows(
                "Expected security exception for call to"
                        + " addOnMediaKeyEventSessionChangedListener",
                SecurityException.class,
                () ->
                        mSessionManager.addOnMediaKeyEventSessionChangedListener(
                                createExecutorWithScheduledShutdown(), keyEventSessionListener));
    }

    @Test
    @NonMainlineTest
    public void testOnMediaKeyEventDispatchedListener() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MEDIA_CONTENT_CONTROL,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE);

        MediaKeyEventDispatchedListener keyEventDispatchedListener =
                new MediaKeyEventDispatchedListener();
        mSessionManager.addOnMediaKeyEventDispatchedListener(
                createExecutorWithScheduledShutdown(), keyEventDispatchedListener);

        MediaSession session = new MediaSession(getInstrumentation().getTargetContext(), TAG);
        mResourceReleaser.add(session::release);

        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1.0f).build();
        // Fake the media session service so this session can take the media key events.
        session.setPlaybackState(state);
        session.setActive(true);
        Utils.assertMediaPlaybackStarted(getInstrumentation().getTargetContext());

        final int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;
        simulateMediaKeyInput(keyCode);
        assertThat(keyEventDispatchedListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        assertThat(keyEventDispatchedListener.mKeyEvent.getKeyCode()).isEqualTo(keyCode);
        assertThat(keyEventDispatchedListener.mPackageName)
                .isEqualTo(getInstrumentation().getTargetContext().getPackageName());
        assertThat(keyEventDispatchedListener.mSessionToken).isEqualTo(session.getSessionToken());

        mSessionManager.removeOnMediaKeyEventDispatchedListener(keyEventDispatchedListener);
        keyEventDispatchedListener.resetCountDownLatch();

        simulateMediaKeyInput(keyCode);
        // This shouldn't be called because the callback is removed
        assertThat(keyEventDispatchedListener.mCountDownLatch
                .await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    @UiThreadTest
    @NonMainlineTest
    public void testAddOnActiveSessionsListener() throws Exception {
        assertThrows("Expected NPE for call to addOnActiveSessionsChangedListener",
                NullPointerException.class,
                () -> mSessionManager.addOnActiveSessionsChangedListener(null, null));

        MediaSessionManager.OnActiveSessionsChangedListener listener = controllers -> {};

        assertThrows("Expected security exception for call to addOnActiveSessionsChangedListener",
                SecurityException.class,
                () -> mSessionManager.addOnActiveSessionsChangedListener(listener, null));
    }

    private void assertKeyEventEquals(KeyEvent lhs, int keyCode, int action, int repeatCount) {
        assertThat(lhs.getKeyCode() == keyCode
                && lhs.getAction() == action
                && lhs.getRepeatCount() == repeatCount).isTrue();
    }

    private void injectInputEvent(int keyCode, boolean longPress) throws IOException {
        // Injecting key with instrumentation requires a window/view, but we don't have it.
        // Inject key event through the adb commend to workaround.
        final String command = "input keyevent " + (longPress ? "--longpress " : "") + keyCode;
        SystemUtil.runShellCommand(getInstrumentation(), command);
    }

    @Test
    @NonMainlineTest
    public void testSetOnVolumeKeyLongPressListener() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                || context.getResources().getBoolean(Resources.getSystem().getIdentifier(
                        "config_handleVolumeKeysInWindowManager", "bool", "android"))) {
            // Skip this test, because the PhoneWindowManager dispatches volume key
            // events directly to the audio service to change the system volume.
            return;
        }
        Handler handler = createHandlerWithScheduledLooperQuit();

        // Ensure that the listener is called for long-press.
        VolumeKeyLongPressListener listener = new VolumeKeyLongPressListener(3, handler);
        mSessionManager.setOnVolumeKeyLongPressListener(listener, handler);
        injectInputEvent(KeyEvent.KEYCODE_VOLUME_DOWN, true);
        assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(listener.mKeyEvents).hasSize(3);
        assertKeyEventEquals(listener.mKeyEvents.get(0),
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0);
        assertKeyEventEquals(listener.mKeyEvents.get(1),
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 1);
        assertKeyEventEquals(listener.mKeyEvents.get(2),
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, 0);

        // Ensure the the listener isn't called for short-press.
        listener = new VolumeKeyLongPressListener(1, handler);
        mSessionManager.setOnVolumeKeyLongPressListener(listener, handler);
        injectInputEvent(KeyEvent.KEYCODE_VOLUME_DOWN, false);
        assertThat(listener.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(listener.mKeyEvents).isEmpty();

        // Ensure that the listener isn't called anymore.
        mSessionManager.setOnVolumeKeyLongPressListener(null, handler);
        injectInputEvent(KeyEvent.KEYCODE_VOLUME_DOWN, true);
        assertThat(listener.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(listener.mKeyEvents).isEmpty();
    }

    @Test
    @NonMainlineTest
    public void testSetOnMediaKeyListener() throws Exception {
        Handler handler = createHandlerWithScheduledLooperQuit();
        MediaSession session = new MediaSession(getInstrumentation().getTargetContext(), TAG);
        mResourceReleaser.add(session::release);

        MediaSessionCallback callback = new MediaSessionCallback(2, session);
        session.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(callback, handler);
        PlaybackState state =
                new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1.0f).build();
        // Fake the media session service so this session can take the media key events.
        session.setPlaybackState(state);
        session.setActive(true);

        // A media playback is also needed to receive media key events.
        Utils.assertMediaPlaybackStarted(getInstrumentation().getTargetContext());

        // Ensure that the listener is called for media key event,
        // and any other media sessions don't get the key.
        MediaKeyListener listener = new MediaKeyListener(2, true, handler);
        mSessionManager.setOnMediaKeyListener(listener, handler);
        injectInputEvent(KeyEvent.KEYCODE_HEADSETHOOK, false);
        assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(listener.mKeyEvents).hasSize(2);
        assertKeyEventEquals(
                listener.mKeyEvents.get(0), KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_DOWN, 0);
        assertKeyEventEquals(
                listener.mKeyEvents.get(1), KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_UP, 0);
        assertThat(callback.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(callback.mKeyEvents).isEmpty();

        // Ensure that the listener is called for media key event,
        // and another media session gets the key.
        listener = new MediaKeyListener(2, false, handler);
        mSessionManager.setOnMediaKeyListener(listener, handler);
        injectInputEvent(KeyEvent.KEYCODE_HEADSETHOOK, false);
        assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(listener.mKeyEvents).hasSize(2);
        assertKeyEventEquals(
                listener.mKeyEvents.get(0), KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_DOWN, 0);
        assertKeyEventEquals(
                listener.mKeyEvents.get(1), KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_UP, 0);
        assertThat(callback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mKeyEvents).hasSize(2);
        assertKeyEventEquals(
                callback.mKeyEvents.get(0), KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_DOWN, 0);
        assertKeyEventEquals(
                callback.mKeyEvents.get(1), KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.ACTION_UP, 0);

        // Ensure that the listener isn't called anymore.
        listener = new MediaKeyListener(1, true, handler);
        mSessionManager.setOnMediaKeyListener(listener, handler);
        mSessionManager.setOnMediaKeyListener(null, handler);
        injectInputEvent(KeyEvent.KEYCODE_HEADSETHOOK, false);
        assertThat(listener.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(listener.mKeyEvents).isEmpty();
    }

    @Test
    @NonMainlineTest
    public void testRemoteUserInfo() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        Handler handler = createHandlerWithScheduledLooperQuit();

        MediaSession session = new MediaSession(context, TAG);
        mResourceReleaser.add(session::release);
        MediaSessionCallback callback = new MediaSessionCallback(5, session);
        session.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(callback, handler);
        PlaybackState state =
                new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1.0f).build();
        // Fake the media session service so this session can take the media key events.
        session.setPlaybackState(state);
        session.setActive(true);

        // A media playback is also needed to receive media key events.
        Utils.assertMediaPlaybackStarted(context);

        // Dispatch key events 5 times.
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
        // (1), (2): dispatch through key -- this will trigger event twice for up & down.
        injectInputEvent(KeyEvent.KEYCODE_HEADSETHOOK, false);
        // (3): dispatch through controller
        session.getController().dispatchMediaButtonEvent(event);

        // Creating another controller.
        MediaController controller = new MediaController(context, session.getSessionToken());
        // (4): dispatch through different controller.
        controller.dispatchMediaButtonEvent(event);
        // (5): dispatch through the same controller
        controller.dispatchMediaButtonEvent(event);

        // Wait.
        assertThat(callback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        // Caller of (1) ~ (4) shouldn't be the same as any others.
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < i; j++) {
                assertThat(callback.mCallers.get(i)).isNotSameInstanceAs(callback.mCallers.get(j));
            }
        }
        // Caller of (5) should be the same as (4), since they're called from the same
        assertThat(callback.mCallers.get(4)).isEqualTo(callback.mCallers.get(3));
    }

    @Test
    public void testGetSession2Tokens() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        Handler handler = createHandlerWithScheduledLooperQuit();
        Executor handlerExecutor = new HandlerExecutor(handler);

        Session2TokenListener listener = new Session2TokenListener();
        mSessionManager.addOnSession2TokensChangedListener(listener, handler);

        Session2Callback sessionCallback = new Session2Callback();
        try (MediaSession2 session = new MediaSession2.Builder(context)
                .setSessionCallback(handlerExecutor, sessionCallback)
                .build()) {
            assertThat(sessionCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isTrue();
            assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            Session2Token currentToken = session.getToken();
            assertThat(listContainsToken(listener.mTokens, currentToken)).isTrue();
            assertThat(listContainsToken(mSessionManager.getSession2Tokens(), currentToken))
                    .isTrue();
        }
    }

    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testGetSession2TokensWithTwoSessions() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        Handler handler = createHandlerWithScheduledLooperQuit();
        Executor handlerExecutor = new HandlerExecutor(handler);

        Session2TokenListener listener = new Session2TokenListener();
        mSessionManager.addOnSession2TokensChangedListener(listener, handler);

        try (MediaSession2 session1 = new MediaSession2.Builder(context)
                .setSessionCallback(handlerExecutor, new Session2Callback())
                .setId("testGetSession2TokensWithTwoSessions_session1")
                .build()) {

            assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            Session2Token session1Token = session1.getToken();
            assertThat(listContainsToken(mSessionManager.getSession2Tokens(), session1Token))
                    .isTrue();

            // Create another session and check the result of getSession2Token().
            listener.resetCountDownLatch();
            Session2Token session2Token = null;
            try (MediaSession2 session2 = new MediaSession2.Builder(context)
                    .setSessionCallback(handlerExecutor, new Session2Callback())
                    .setId("testGetSession2TokensWithTwoSessions_session2")
                    .build()) {

                assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .isTrue();
                session2Token = session2.getToken();
                assertThat(session2Token).isNotNull();
                assertThat(listContainsToken(mSessionManager.getSession2Tokens(), session1Token))
                        .isTrue();
                assertThat(listContainsToken(mSessionManager.getSession2Tokens(), session2Token))
                        .isTrue();

                listener.resetCountDownLatch();
            }

            // Since the session2 is closed, getSession2Tokens() shouldn't include session2's token.
            assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isTrue();
            assertThat(listContainsToken(mSessionManager.getSession2Tokens(), session1Token))
                    .isTrue();
            assertThat(listContainsToken(mSessionManager.getSession2Tokens(), session2Token))
                    .isFalse();
        }
    }

    @Test
    public void testAddAndRemoveSession2TokensListener() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        Handler handler = createHandlerWithScheduledLooperQuit();
        Executor handlerExecutor = new HandlerExecutor(handler);

        Session2TokenListener listener1 = new Session2TokenListener();
        mSessionManager.addOnSession2TokensChangedListener(listener1, handler);

        Session2Callback sessionCallback = new Session2Callback();
        try (MediaSession2 session = new MediaSession2.Builder(context)
                .setSessionCallback(handlerExecutor, sessionCallback)
                .build()) {
            assertThat(listener1.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            Session2Token currentToken = session.getToken();
            assertThat(listContainsToken(listener1.mTokens, currentToken)).isTrue();

            // Test removing listener
            listener1.resetCountDownLatch();
            Session2TokenListener listener2 = new Session2TokenListener();
            mSessionManager.addOnSession2TokensChangedListener(listener2, handler);
            mSessionManager.removeOnSession2TokensChangedListener(listener1);

            session.close();
            assertThat(listener1.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isFalse();
            assertThat(listener2.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    @Test
    public void testSession2TokensNotChangedBySession1() throws Exception {
        final Context context = getInstrumentation().getTargetContext();
        Handler handler = createHandlerWithScheduledLooperQuit();

        Session2TokenListener listener = new Session2TokenListener();
        List<Session2Token> initialSession2Tokens = mSessionManager.getSession2Tokens();
        mSessionManager.addOnSession2TokensChangedListener(listener, handler);
        MediaSession session = new MediaSession(context, TAG);
        mResourceReleaser.add(session::release);
        session.setActive(true);
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        assertThat(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isFalse();
        List<Session2Token> laterSession2Tokens = mSessionManager.getSession2Tokens();

        assertThat(laterSession2Tokens).hasSize(initialSession2Tokens.size());
    }

    @Test
    @NonMainlineTest
    public void testCustomClassConfigValuesAreValid() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        String customMediaKeyDispatcher = context.getString(
                android.R.string.config_customMediaKeyDispatcher);
        String customMediaSessionPolicyProvider = context.getString(
                android.R.string.config_customMediaSessionPolicyProvider);
        // MediaSessionService will call Class.forName(String) with the existing config value.
        // If the config value is not valid (i.e. given class doesn't exist), the following
        // methods will return false.
        if (!customMediaKeyDispatcher.isEmpty()) {
            assertThat(mSessionManager.hasCustomMediaKeyDispatcher(customMediaKeyDispatcher))
                    .isTrue();
        }
        if (!customMediaSessionPolicyProvider.isEmpty()) {
            assertThat(mSessionManager.hasCustomMediaSessionPolicyProvider(
                    customMediaSessionPolicyProvider)).isTrue();
        }
    }

    @Test
    @NonMainlineTest
    public void testIsTrustedForMediaControl_withEnabledNotificationListener() throws Exception {
        List<String> packageNames = getEnabledNotificationListenerPackages();
        for (String packageName : packageNames) {
            int packageUid =
                    mContext.getPackageManager().getPackageUid(packageName, /* flags= */ 0);
            MediaSessionManager.RemoteUserInfo info =
                    new MediaSessionManager.RemoteUserInfo(packageName, /* pid= */ 0, packageUid);
            assertThat(mSessionManager.isTrustedForMediaControl(info)).isTrue();
        }
    }

    @Test
    @NonMainlineTest
    public void testIsTrustedForMediaControl_withInvalidUid() throws Exception {
        List<String> packageNames = getEnabledNotificationListenerPackages();
        for (String packageName : packageNames) {
            MediaSessionManager.RemoteUserInfo info =
                    new MediaSessionManager.RemoteUserInfo(
                            packageName, /* pid= */ 0, Process.myUid());
            assertThat(mSessionManager.isTrustedForMediaControl(info)).isFalse();
        }
    }

    private boolean listContainsToken(List<Session2Token> tokens, Session2Token token) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(token)) {
                return true;
            }
        }
        return false;
    }

    private Executor createExecutorWithScheduledShutdown() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        mResourceReleaser.add(executor::shutdown);
        return executor;
    }

    private Handler createHandlerWithScheduledLooperQuit() {
        HandlerThread handlerThread = new HandlerThread("MediaSessionManagerTest");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        mResourceReleaser.add(looper::quitSafely);
        return new Handler(looper);
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

    private List<String> getEnabledNotificationListenerPackages() {
        List<String> listeners = new ArrayList<>();
        String enabledNotificationListeners =
                Settings.Secure.getString(
                        mContext.getContentResolver(),
                        ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(enabledNotificationListeners)) {
            String[] components = enabledNotificationListeners.split(":");
            for (String componentString : components) {
                ComponentName component = ComponentName.unflattenFromString(componentString);
                if (component != null) {
                    listeners.add(component.getPackageName());
                }
            }
        }
        return listeners;
    }

    private class VolumeKeyLongPressListener
            implements MediaSessionManager.OnVolumeKeyLongPressListener {
        private final List<KeyEvent> mKeyEvents = new ArrayList<>();
        private final CountDownLatch mCountDownLatch;
        private final Handler mHandler;

        VolumeKeyLongPressListener(int count, Handler handler) {
            mCountDownLatch = new CountDownLatch(count);
            mHandler = handler;
        }

        @Override
        public void onVolumeKeyLongPress(KeyEvent event) {
            mKeyEvents.add(event);
            // Ensure the listener is called on the thread.
            assertThat(mHandler.getLooper()).isEqualTo(Looper.myLooper());
            mCountDownLatch.countDown();
        }
    }

    private class MediaKeyListener implements MediaSessionManager.OnMediaKeyListener {
        private final CountDownLatch mCountDownLatch;
        private final boolean mConsume;
        private final Handler mHandler;
        private final List<KeyEvent> mKeyEvents = new ArrayList<>();

        MediaKeyListener(int count, boolean consume, Handler handler) {
            mCountDownLatch = new CountDownLatch(count);
            mConsume = consume;
            mHandler = handler;
        }

        @Override
        public boolean onMediaKey(KeyEvent event) {
            mKeyEvents.add(event);
            // Ensure the listener is called on the thread.
            assertThat(mHandler.getLooper()).isEqualTo(Looper.myLooper());
            mCountDownLatch.countDown();
            return mConsume;
        }
    }

    private class MediaSessionCallback extends MediaSession.Callback {
        private final CountDownLatch mCountDownLatch;
        private final MediaSession mSession;
        private final List<KeyEvent> mKeyEvents = new ArrayList<>();
        private final List<MediaSessionManager.RemoteUserInfo> mCallers = new ArrayList<>();

        private MediaSessionCallback(int count, MediaSession session) {
            mCountDownLatch = new CountDownLatch(count);
            mSession = session;
        }

        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent event = (KeyEvent) mediaButtonIntent.getParcelableExtra(
                    Intent.EXTRA_KEY_EVENT);
            assertThat(event).isNotNull();
            mKeyEvents.add(event);
            mCallers.add(mSession.getCurrentControllerInfo());
            mCountDownLatch.countDown();
            return true;
        }
    }

    private class Session2Callback extends MediaSession2.SessionCallback {
        private CountDownLatch mCountDownLatch;

        private Session2Callback() {
            mCountDownLatch = new CountDownLatch(1);
        }

        @Override
        public Session2CommandGroup onConnect(MediaSession2 session,
                MediaSession2.ControllerInfo controller) {
            if (controller.getUid() == Process.SYSTEM_UID) {
                // System server will try to connect here for monitor session.
                mCountDownLatch.countDown();
            }
            return new Session2CommandGroup.Builder().build();
        }
    }

    private class Session2TokenListener implements
            MediaSessionManager.OnSession2TokensChangedListener {
        private CountDownLatch mCountDownLatch;
        private List<Session2Token> mTokens;

        private Session2TokenListener() {
            mCountDownLatch = new CountDownLatch(1);
        }

        @Override
        public void onSession2TokensChanged(List<Session2Token> tokens) {
            mTokens = tokens;
            mCountDownLatch.countDown();
        }

        public void resetCountDownLatch() {
            mCountDownLatch = new CountDownLatch(1);
        }
    }

    private class MediaKeyEventSessionListener
            implements MediaSessionManager.OnMediaKeyEventSessionChangedListener {
        CountDownLatch mCountDownLatch;
        MediaSession.Token mSessionToken;
        String mPackageName;

        MediaKeyEventSessionListener() {
            mCountDownLatch = new CountDownLatch(1);
        }

        void resetCountDownLatch() {
            mCountDownLatch = new CountDownLatch(1);
            mSessionToken = null;
            mPackageName = null;
        }

        @Override
        public void onMediaKeyEventSessionChanged(String packageName,
                MediaSession.Token sessionToken) {
            if (sIsAtLeastU) {
                assertWithMessage("The package name cannot be null.")
                        .that(packageName)
                        .isNotNull();
            }
            mSessionToken = sessionToken;
            mPackageName = packageName;
            mCountDownLatch.countDown();
        }
    }

    private class MediaKeyEventDispatchedListener
            implements MediaSessionManager.OnMediaKeyEventDispatchedListener {
        CountDownLatch mCountDownLatch;
        KeyEvent mKeyEvent;
        String mPackageName;
        MediaSession.Token mSessionToken;

        MediaKeyEventDispatchedListener() {
            resetCountDownLatch();
        }

        void resetCountDownLatch() {
            // Initial count reflects that we expect 2 events when we dispatch a key event. One for
            // key down and one for key up.
            mCountDownLatch = new CountDownLatch(2);
        }

        @Override
        public void onMediaKeyEventDispatched(KeyEvent event, String packageName,
                MediaSession.Token sessionToken) {
            mKeyEvent = event;
            mPackageName = packageName;
            mSessionToken = sessionToken;

            mCountDownLatch.countDown();
        }
    }

    private static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        HandlerExecutor(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(Runnable command) {
            mHandler.post(command);
        }
    }
}
