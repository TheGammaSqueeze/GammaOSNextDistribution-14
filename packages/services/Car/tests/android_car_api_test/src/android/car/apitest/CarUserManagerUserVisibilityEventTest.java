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
package android.car.apitest;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.util.Log;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.Executor;

// DO NOT ADD ANY TEST TO THIS CLASS
// This class will have only one test testUserVisibilityEvents.
public final class CarUserManagerUserVisibilityEventTest extends CarMultiUserTestBase {

    private static final String TAG = CarUserManagerUserVisibilityEventTest.class.getSimpleName();

    private static final int START_TIMEOUT_MS = 100_000;
    // A large stop timeout is required as sometimes stop user broadcast takes a significantly
    // long time to complete. This happens when there are multiple users starting/stopping in
    // background which is the case in this test class.
    private static final int STOP_TIMEOUT_MS = 600_000;

    // TODO(b/253264316) Stopping the user takes a while, even when calling force stop - change it
    // to {@code false} if {@code testLifecycleListener} becomes flaky.
    private static final boolean TEST_STOP_EVENTS = true;

    @BeforeClass
    public static void setUp() {
        setupMaxNumberOfUsers(3); // system user, current user, 1 extra user
    }

    @AfterClass
    public static void cleanUp() {
        restoreMaxNumberOfUsers();
    }

    @Test(timeout = 600_000)
    public void testUserVisibilityEvents() throws Exception {

        // Check if the device supports MUMD. If not, skip the test.
        requireMumd();

        int displayId = getDisplayForStartingBackgroundUser();
        int newUserId = createUser().id;

        BlockingUserLifecycleListener startListener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUserId)
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_VISIBLE)
                .build();

        Log.d(TAG, "registering start listener: " + startListener);

        Executor directExecutor = r -> r.run();
        mCarUserManager.addListener(directExecutor, startListener);

        // Start new user on the secondary display.
        startUserInBackgroundOnSecondaryDisplay(newUserId, displayId);

        List<UserLifecycleEvent> startEvents = startListener.waitForEvents();
        Log.d(TAG, "Received expected events: " + startEvents);
        assertWithMessage("Background user start events").that(startEvents)
                .containsExactly(
                        new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING, newUserId),
                        new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING, newUserId),
                        new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, newUserId),
                        new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_VISIBLE, newUserId));

        Log.d(TAG, "unregistering start listener: " + startListener);
        mCarUserManager.removeListener(startListener);

        BlockingUserLifecycleListener stopListener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUserId)
                .setTimeout(STOP_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)
                .build();

        Log.d(TAG, "registering stop listener: " + stopListener);
        mCarUserManager.addListener(directExecutor, stopListener);

        // Stop the background user on the virtual display.
        forceStopUser(newUserId);

        if (TEST_STOP_EVENTS) {
            // Must force stop the user, otherwise it can take minutes for its process to finish
            forceStopUser(newUserId);

            List<UserLifecycleEvent> stopEvents = stopListener.waitForEvents();
            Log.d(TAG, "stopEvents: " + stopEvents + "; all events on stop listener: "
                    + stopListener.getAllReceivedEvents());
            assertWithMessage("Background user stop events").that(stopEvents)
                    .containsExactly(
                            new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING, newUserId),
                            new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED, newUserId),
                            new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE, newUserId));
        } else {
            Log.w(TAG, "NOT testing user stop events");
        }

        // Make sure unregistered listener didn't receive any more events
        List<UserLifecycleEvent> allStartEvents = startListener.getAllReceivedEvents();
        Log.d(TAG, "All start events: " + startEvents);
        assertThat(allStartEvents).containsAtLeastElementsIn(startEvents).inOrder();

        Log.d(TAG, "unregistering stop listener: " + stopListener);
        mCarUserManager.removeListener(stopListener);
    }
}
