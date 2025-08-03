/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.smartspace.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceManager;
import android.app.smartspace.SmartspaceSession;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.SmartspaceTargetEvent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredServiceRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tests for {@link SmartspaceManager}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceManagerTest {

    private static final String TAG = "SmartspaceManagerTest";
    private static final boolean DEBUG = false;

    private static final long VERIFY_TIMEOUT_MS = 5_000;
    private static final long SERVICE_LIFECYCLE_TIMEOUT_MS = 20_000;
    private static final String TEST_UI_SURFACE = "homescreen";
    private static final String SMARTSPACE_ACTION_ID = "dummy_action_id";
    private static final int TEST_NUM_PREDICTIONS = 10;
    private static final String TEST_LAUNCH_LOCATION = "testCollapsedLocation";
    private static final int TEST_ACTION = 2;

    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.SMARTSPACE_SERVICE);

    private SmartspaceManager mManager;
    private SmartspaceSession mClient;
    private CtsSmartspaceService.Watcher mWatcher;

    @Before
    public void setUp() throws Exception {
        mWatcher = CtsSmartspaceService.setWatcher();
        mManager = getContext().getSystemService(SmartspaceManager.class);
        setService(CtsSmartspaceService.SERVICE_NAME);
        SmartspaceConfig config = createSmartspaceConfig(TEST_UI_SURFACE);
        mClient = createSmartspaceSession(config);
        await(mWatcher.created, "Waiting for onCreate()");
        reset(mWatcher.verifier);
    }

    @After
    public void tearDown() throws Exception {
        Log.d(TAG, "Starting tear down, watcher is: " + mWatcher);
        mClient.close();
        setService(null);
        await(mWatcher.destroyed, "Waiting for onDestroy()");

        mWatcher = null;
        CtsSmartspaceService.clearWatcher();
    }

    @Test
    public void testCreateSmartspaceSession() {
        assertNotNull(mClient);
        assertNotNull(mWatcher.verifier);
    }

    @Test
    public void testDestroySession() {
        SmartspaceSession localClient = createSmartspaceSession(createSmartspaceConfig("surface"));
        localClient.close();
        await(mWatcher.destroyed, "Waiting for onDestroy()");
    }

    @Test
    public void testRequestSmartspaceUpdate() {
        // Send a request for a smartspace update
        SmartspaceTarget testTarget = SmartspaceTestUtils.getBasicSmartspaceTarget("id",
                SmartspaceTestUtils.getTestComponentName(), Process.myUserHandle());
        SmartspaceTargetEvent testEvent = new SmartspaceTargetEvent.Builder(
                SmartspaceTargetEvent.EVENT_TARGET_INTERACTION).setSmartspaceTarget(
                testTarget).setSmartspaceActionId("id").build();
        mClient.notifySmartspaceEvent(testEvent);
        mClient.addOnTargetsAvailableListener(Executors.newSingleThreadExecutor(),
                targets -> {
                    if (targets.size() > 0 && targets.get(0).equals(testTarget)) {
                        mWatcher.queried.countDown();
                    }
                });
        mClient.requestSmartspaceUpdate();
        // Verify that the API received it
        verify(mWatcher.verifier, timeout(VERIFY_TIMEOUT_MS).times(2))
                .onRequestSmartspaceUpdate(any());
        await(mWatcher.queried, "Waiting for updateSmartspaceTargets()");
    }

    @Test
    public void testRequestSmartspaceUpdateForMultipleSessions() {
        SmartspaceTarget testTarget = SmartspaceTestUtils.getBasicSmartspaceTarget("id",
                SmartspaceTestUtils.getTestComponentName(), Process.myUserHandle());
        SmartspaceTargetEvent testEvent = new SmartspaceTargetEvent.Builder(
                SmartspaceTargetEvent.EVENT_TARGET_INTERACTION).setSmartspaceTarget(
                testTarget).setSmartspaceActionId("id").build();

        SmartspaceConfig config1 = createSmartspaceConfig("surface 1");
        SmartspaceSession client1 = createSmartspaceSession(config1);
        SmartspaceConfig config2 = createSmartspaceConfig("surface 2");
        SmartspaceSession client2 = createSmartspaceSession(config2);
        client1.addOnTargetsAvailableListener(Executors.newSingleThreadExecutor(),
                targets -> {
                    // Counting down only if the returned list only contains test target.
                    if (targets.size() > 0 && targets.get(0).equals(testTarget)) {
                        mWatcher.queriedTwice.countDown();
                    }
                });
        client2.addOnTargetsAvailableListener(Executors.newSingleThreadExecutor(),
                targets -> {
                    // Counting down only if the returned list is empty.
                    if (targets.isEmpty()) {
                        mWatcher.queriedTwice.countDown();
                    }
                });
        // Notifying the event only for client1.
        client1.notifySmartspaceEvent(testEvent);
        // Requesting update for both the clients
        client1.requestSmartspaceUpdate();
        client2.requestSmartspaceUpdate();
        // Verify that the API received it 4 times, twice for each client, once while registering
        // and once while requesting.
        verify(mWatcher.verifier, timeout(VERIFY_TIMEOUT_MS).times(4))
                .onRequestSmartspaceUpdate(any());
        await(mWatcher.queriedTwice, "Waiting for updateSmartspaceTargets() to be called twice");
    }

    @Test
    public void testNotifySmartspaceEvent() {
        ComponentName componentName = new ComponentName("package_name", "class_name");
        SmartspaceTarget target = new SmartspaceTarget.Builder("id",
                componentName, Process.myUserHandle()).build();

        SmartspaceTargetEvent smartspaceTargetEvent = new SmartspaceTargetEvent.Builder(
                SmartspaceTargetEvent.EVENT_TARGET_BLOCK).setSmartspaceActionId(
                SMARTSPACE_ACTION_ID).setSmartspaceTarget(target).build();

        ArgumentCaptor<SmartspaceTargetEvent> arg = ArgumentCaptor.forClass(
                SmartspaceTargetEvent.class);
        // Send a request to notifySmartspaceUpdate
        mClient.notifySmartspaceEvent(smartspaceTargetEvent);
        // Verify that the API received it.
        verify(mWatcher.verifier, timeout(VERIFY_TIMEOUT_MS))
                .notifySmartspaceEvent(any(), arg.capture());
        assertEquals(arg.getValue().getSmartspaceActionId(), SMARTSPACE_ACTION_ID);
        assertEquals(arg.getValue().getEventType(), SmartspaceTargetEvent.EVENT_TARGET_BLOCK);
        assertEquals(arg.getValue().getSmartspaceTarget().getComponentName(), componentName);
        assertEquals(arg.getValue().getSmartspaceTarget().getUserHandle(), Process.myUserHandle());
        assertEquals(arg.getValue().getSmartspaceTarget().getSmartspaceTargetId(), "id");

    }

    private void setService(String service) {
        Log.d(TAG, "Setting smartspace service to " + service);
        int userId = Process.myUserHandle().getIdentifier();
        if (service != null) {
            runShellCommand("cmd smartspace set temporary-service "
                    + userId + " " + service + " 60000");
        } else {
            runShellCommand("cmd smartspace set temporary-service " + userId);
        }
    }

    private SmartspaceSession createSmartspaceSession(SmartspaceConfig config) {
        return mManager.createSmartspaceSession(config);
    }

    private SmartspaceConfig createSmartspaceConfig(String uiSurface) {
        return new SmartspaceConfig.Builder(
                InstrumentationRegistry.getTargetContext(),
                uiSurface).setSmartspaceTargetCount(TEST_NUM_PREDICTIONS).build();
    }

    private void await(@NonNull CountDownLatch latch, @NonNull String message) {
        try {
            assertWithMessage(message).that(
                    latch.await(SERVICE_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while: " + message);
        }
    }

    private void runShellCommand(String command) {
        Log.d(TAG, "runShellCommand(): " + command);
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    public static class ConsumerVerifier implements
            Consumer<List<SmartspaceTarget>> {

        private static List<SmartspaceTarget> mExpectedTargets;

        public ConsumerVerifier(List<SmartspaceTarget> targets) {
            mExpectedTargets = targets;
        }

        @Override
        public void accept(List<SmartspaceTarget> actualTargets) {
            if (DEBUG) {
                Log.d(TAG, "ConsumerVerifier.accept targets.size= " + actualTargets.size());
                Log.d(TAG, "ConsumerVerifier.accept target(1).packageName=" + actualTargets.get(
                        0).getComponentName().getPackageName());
            }
            Assert.assertArrayEquals(actualTargets.toArray(), mExpectedTargets.toArray());
        }
    }

    private static class RequestVerifier implements SmartspaceSession.OnTargetsAvailableListener {
        @Override
        public void onTargetsAvailable(List<SmartspaceTarget> targets) {

        }
    }
}
