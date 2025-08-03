/*
 * Copyright 2020 The Android Open Source Project
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
package android.media.misc.cts;

import static android.Manifest.permission.MEDIA_CONTENT_CONTROL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaCommunicationManager;
import android.media.MediaSession2;
import android.media.Session2CommandGroup;
import android.media.Session2Token;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link android.media.MediaCommunicationManager}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class MediaCommunicationManagerTest {
    private static final int TIMEOUT_MS = 5000;
    private static final int WAIT_MS = 500;

    private Context mContext;
    private MediaCommunicationManager mManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mManager = mContext.getSystemService(MediaCommunicationManager.class);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testGetVersion() {
        assertNotNull("Missing MediaCommunicationManager", mManager);
        assertTrue(mManager.getVersion() > 0);
    }

    @Test
    public void testGetSession2Tokens() throws Exception {
        // registerSessionCallback requires permission MEDIA_CONTENT_CONTROL
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(MEDIA_CONTENT_CONTROL);

        Executor executor = Executors.newSingleThreadExecutor();

        assertNotNull("Missing MediaCommunicationManager", mManager);
        ManagerSessionCallback managerCallback = new ManagerSessionCallback();
        Session2Callback sessionCallback = new Session2Callback();

        mManager.registerSessionCallback(executor, managerCallback);

        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setSessionCallback(executor, sessionCallback)
                .build()) {
            assertTrue(managerCallback.mCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Session2Token currentToken = session.getToken();
            assertTrue(managerCallback.mCreatedTokens.contains(currentToken));
            assertTrue(mManager.getSession2Tokens().contains(currentToken));
        }

        mManager.unregisterSessionCallback(managerCallback);
    }

    @Test
    public void registerSessionCallback_noMediaContentControlPermission_throwsSecurityException()
            throws Exception {
        Executor executor = Executors.newSingleThreadExecutor();

        assertNotNull("Missing MediaCommunicationManager", mManager);
        ManagerSessionCallback managerCallback = new ManagerSessionCallback();

        // Test permission enforced
        assertThrows(SecurityException.class,
                () -> mManager.registerSessionCallback(executor, managerCallback));
    }

    @Test
    public void testManagerSessionCallback() throws Exception {
        // registerSessionCallback requires permission MEDIA_CONTENT_CONTROL
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(MEDIA_CONTENT_CONTROL);

        Executor executor = Executors.newSingleThreadExecutor();

        assertNotNull("Missing MediaCommunicationManager", mManager);
        ManagerSessionCallback managerCallback = new ManagerSessionCallback();
        Session2Callback sessionCallback = new Session2Callback();

        mManager.registerSessionCallback(executor, managerCallback);

        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setId("session1")
                .setSessionCallback(executor, sessionCallback)
                .build()) {
            assertTrue(managerCallback.mCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue(managerCallback.mChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Session2Token currentToken = session.getToken();
            assertTrue(managerCallback.mCreatedTokens.contains(currentToken));
            assertTrue(managerCallback.mLastTokens.contains(currentToken));

            // Create another session
            managerCallback.resetLatches();
            MediaSession2 session2 = new MediaSession2.Builder(mContext)
                    .setId("session2")
                    .setSessionCallback(executor, sessionCallback).build();
            assertTrue(managerCallback.mCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue(managerCallback.mChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Session2Token token2 = session2.getToken();
            assertTrue(managerCallback.mCreatedTokens.contains(token2));
            assertTrue(managerCallback.mLastTokens.contains(token2));

            // Test if onSession2TokensChanged are called if a session is closed
            managerCallback.resetLatches();
            session2.close();
            assertTrue(managerCallback.mChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            assertFalse(managerCallback.mLastTokens.contains(token2));
        }

        mManager.unregisterSessionCallback(managerCallback);
    }

    private static class Session2Callback extends MediaSession2.SessionCallback {
        @Override
        public Session2CommandGroup onConnect(MediaSession2 session,
                MediaSession2.ControllerInfo controller) {
            return new Session2CommandGroup.Builder().build();
        }
    }

    private static class ManagerSessionCallback
            implements MediaCommunicationManager.SessionCallback {
        CountDownLatch mCreatedLatch;
        CountDownLatch mChangedLatch;
        final List<Session2Token> mCreatedTokens = new CopyOnWriteArrayList<>();
        List<Session2Token> mLastTokens = Collections.emptyList();

        private ManagerSessionCallback() {
            mCreatedLatch = new CountDownLatch(1);
            mChangedLatch = new CountDownLatch(1);
        }

        @Override
        public void onSession2TokenCreated(Session2Token token) {
            mCreatedTokens.add(token);
            mCreatedLatch.countDown();
        }

        @Override
        public void onSession2TokensChanged(List<Session2Token> tokens) {
            mLastTokens = new ArrayList<>(tokens);
            mChangedLatch.countDown();
        }

        public void resetLatches() {
            mCreatedLatch = new CountDownLatch(1);
            mChangedLatch = new CountDownLatch(1);
        }
    }
}
