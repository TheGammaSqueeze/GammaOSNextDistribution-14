/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.media.session.cts;

import static android.media.cts.MediaSessionTestHelperConstants.MEDIA_SESSION_TEST_HELPER_PKG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.RemoteUserInfo;
import android.media.session.PlaybackState;
import android.os.Process;
import android.service.notification.NotificationListenerService;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link MediaSessionManager} with the multi-user environment.
 * <p>Don't run tests here directly. They aren't stand-alone tests and each test will be run
 * indirectly by the host-side test CtsMediaHostTestCases after the proper device setup.
 */
@SmallTest
public class MediaSessionManagerTest extends NotificationListenerService {
    private static final String TAG = "MediaSessionManagerTest";
    private static final int TIMEOUT_MS = 3000;
    private static final int WAIT_MS = 500;

    private Context mContext;
    private MediaSessionManager mMediaSessionManager;
    private ComponentName mComponentName;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mMediaSessionManager =
                mContext.getSystemService(MediaSessionManager.class);
        mComponentName = new ComponentName(mContext, MediaSessionManagerTest.class);
    }

    /**
     * Tests if the MediaSessionTestHelper doesn't have an active media session.
     */
    @Test
    public void testGetActiveSessions_noMediaSessionFromMediaSessionTestHelper() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        for (MediaController controller : controllers) {
            if (controller.getPackageName().equals(MEDIA_SESSION_TEST_HELPER_PKG)) {
                fail("Media session for the media session app shouldn't be available");
                return;
            }
        }
    }

    /**
     * Tests if the MediaSessionTestHelper has an active media session.
     */
    @Test
    public void testGetActiveSessions_hasMediaSessionFromMediaSessionTestHelper() throws Exception {
        boolean found = false;
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        for (MediaController controller : controllers) {
            if (controller.getPackageName().equals(MEDIA_SESSION_TEST_HELPER_PKG)) {
                if (found) {
                    fail("Multiple media session for the media session app is unexpected");
                }
                found = true;
            }
        }
        if (!found) {
            fail("Media session for the media session app is expected");
        }
    }

    /**
     * Tests if there's no media session.
     */
    @Test
    public void testGetActiveSessions_noMediaSession() throws Exception {
        List<MediaController> controllers = mMediaSessionManager.getActiveSessions(mComponentName);
        assertTrue(controllers.isEmpty());
    }

    /**
     * Tests if this application is trusted.
     */
    @Test
    public void testIsTrusted_returnsTrue() throws Exception {
        RemoteUserInfo userInfo = new RemoteUserInfo(
                mContext.getPackageName(), Process.myPid(), Process.myUid());
        assertTrue(mMediaSessionManager.isTrustedForMediaControl(userInfo));
    }

    /**
     * Tests if this application isn't trusted.
     */
    @Test
    public void testIsTrusted_returnsFalse() throws Exception {
        RemoteUserInfo userInfo = new RemoteUserInfo(
                mContext.getPackageName(), Process.myPid(), Process.myUid());
        assertFalse(mMediaSessionManager.isTrustedForMediaControl(userInfo));
    }

    /**
     * Tests adding/removing {@link MediaSessionManager.OnMediaKeyEventSessionChangedListener}.
     */
    @Test
    public void testOnMediaKeyEventSessionChangedListener() throws Exception {
        MediaKeyEventSessionListener keyEventSessionListener = new MediaKeyEventSessionListener();
        mMediaSessionManager.addOnMediaKeyEventSessionChangedListener(
                Executors.newSingleThreadExecutor(), keyEventSessionListener);

        MediaSession session = createMediaKeySession();
        assertTrue(keyEventSessionListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        assertEquals(session.getSessionToken(), keyEventSessionListener.mSessionToken);
        assertEquals(session.getSessionToken(),
                mMediaSessionManager.getMediaKeyEventSession());
        assertEquals(mContext.getPackageName(),
                mMediaSessionManager.getMediaKeyEventSessionPackageName());

        mMediaSessionManager.removeOnMediaKeyEventSessionChangedListener(keyEventSessionListener);
        keyEventSessionListener.resetCountDownLatch();

        session.release();
        // This shouldn't be called because the callback is removed
        assertFalse(keyEventSessionListener.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
    }

    /**
     * Tests {@link MediaSessionManager.OnMediaKeyEventSessionChangedListener} is called when
     * the current media key session is released.
     */
    @Test
    public void testOnMediaKeyEventSessionChangedListener_whenSessionIsReleased() throws Exception {
        MediaKeyEventSessionListener keyEventSessionListener = new MediaKeyEventSessionListener();
        mMediaSessionManager.addOnMediaKeyEventSessionChangedListener(
                Executors.newSingleThreadExecutor(), keyEventSessionListener);

        MediaSession session = createMediaKeySession();
        assertTrue(keyEventSessionListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Check that this is called when the session is released.
        keyEventSessionListener.resetCountDownLatch();
        session.release();
        assertTrue(keyEventSessionListener.mCountDownLatch
                .await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNull(keyEventSessionListener.mSessionToken);
        assertNull(mMediaSessionManager.getMediaKeyEventSession());
        assertEquals("", mMediaSessionManager.getMediaKeyEventSessionPackageName());
    }

    private MediaSession createMediaKeySession() {
        MediaSession session = new MediaSession(mContext, TAG);
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1.0f).build();
        // Fake the media session service so this session can take the media key events.
        session.setPlaybackState(state);
        session.setActive(true);
        Utils.assertMediaPlaybackStarted(mContext);

        return session;
    }

    private class MediaKeyEventSessionListener
            implements MediaSessionManager.OnMediaKeyEventSessionChangedListener {
        CountDownLatch mCountDownLatch;
        MediaSession.Token mSessionToken;

        MediaKeyEventSessionListener() {
            mCountDownLatch = new CountDownLatch(1);
        }

        void resetCountDownLatch() {
            mCountDownLatch = new CountDownLatch(1);
        }

        @Override
        public void onMediaKeyEventSessionChanged(String packageName,
                MediaSession.Token sessionToken) {
            mSessionToken = sessionToken;
            mCountDownLatch.countDown();
        }
    }
}
