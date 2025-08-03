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

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.Nullable;
import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.UserLifecycleEventFilter;
import android.os.UserHandle;
import android.util.Log;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

// DO NOT ADD ANY TEST TO THIS CLASS
// This class will have only one test testUserLifecycleEventFilter.
public final class CarUserManagerLifecycleEventFilterTest extends CarMultiUserTestBase {

    private static final String TAG = CarUserManagerLifecycleEventFilterTest.class.getSimpleName();

    private static final int EVENTS_TIMEOUT_MS = 70_000;

    // Any events for any user. Expect to receive 4 events.
    private Listener mListenerForAllEventsOnAnyUser = new Listener(
            BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_CREATED)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                    .build(), /* filter= */ null);

    // Any events except for user CREATED for the current user.
    // The user CREATED event is deliberately filtered out when current user is specified
    // because the latency of its event delivery might vary.
    // When the latency is long, its event delivery might happen after the 1st user switch.
    // When the latency is short, its event delivery might happen before the 1st user
    // switch. These two scenarios will lead to different results when the current user
    // filter is applied, causing the test to become flaky.
    // Expect to receive 2 events.
    // TODO(b/246959046): Investigate the root cause of user CREATED event received when the
    // current user filter is in place.
    private Listener mListenerForAllEventsOnCurrentUsers = new Listener(
            BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                    .build(),
                    new UserLifecycleEventFilter.Builder()
                            .addUser(UserHandle.CURRENT)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                            .build());

    // Starting events for any user. Expect to receive 1 event.
    private Listener mListenerForStartingEventsOnAnyUser = new Listener(
            BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .build(),
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                            .build());

    // Stopping/stopped events for any user. Expect to receive 0 event.
    private Listener mListenerForStoppingEventsOnAnyUser = new Listener(
            BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                    .build(),
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                            .build());

    // Switching events for any user. Expect to receive 2 events.
    private Listener mListenerForSwitchingEventsOnAnyUser = new Listener(
            BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                    .build(),
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                            .build());

    private Listener[] mListeners = {
            mListenerForAllEventsOnAnyUser,
            mListenerForAllEventsOnCurrentUsers,
            mListenerForStartingEventsOnAnyUser,
            mListenerForStoppingEventsOnAnyUser,
            mListenerForSwitchingEventsOnAnyUser};

    @BeforeClass
    public static void setUp() {
        setupMaxNumberOfUsers(3); // system user, current user, 1 extra user
    }

    @AfterClass
    public static void cleanUp() {
        restoreMaxNumberOfUsers();
    }

    // TODO(246989094): Add the platform unsupported version of the test.
    @Test(timeout = 100_000)
    public void testUserLifecycleEventFilter() throws Exception {
        int initialUserId = getCurrentUserId();

        for (Listener listener : mListeners) {
            Log.d(TAG, "registering listener:" + listener.listener + "%s, with filter:"
                    + listener.filter);
            if (listener.filter == null) {
                mCarUserManager.addListener(listener.executor, listener.listener);
            } else {
                mCarUserManager.addListener(listener.executor, listener.filter, listener.listener);
            }
        }

        // Create a new user while the listeners are registered.
        int newUserId = createUser().id;

        // Switch while the listeners are registered.
        switchUser(newUserId);

        // Switch back to the initial user
        switchUser(initialUserId);

        // Wait for all listeners to receive all expected events.
        // Note: Temporarily calling waitUntilNoFail to narrow down which listener did not receive
        // expected events or received unexpected events. In those situations, the test will fail in
        // specific assertions below.
        boolean expectedEventsReceived = waitUntilNoFail(EVENTS_TIMEOUT_MS,
                () -> (mListenerForAllEventsOnAnyUser.listener.getAllReceivedEvents().size() >= 4
                        && mListenerForAllEventsOnCurrentUsers.listener
                                .getAllReceivedEvents().size() >= 3
                        && mListenerForSwitchingEventsOnAnyUser.listener
                                .getAllReceivedEvents().size() == 2));
        Log.d(TAG, "expected events received: " + expectedEventsReceived);

        // unregister listeners.
        for (Listener listener : mListeners) {
            Log.d(TAG, "unregistering listener:" + listener.listener + ", which received"
                    + " events: " + listener.listener.getAllReceivedEvents());
            mCarUserManager.removeListener(listener.listener);
        }

        // We do not need to clean up the user that is created by
        // CarMultiUserTestBase#createUser().
        // TODO(b/246959046): Listen to the user removed event after investigating why some
        // other events, e,g. stopping, stopped and removed are out of order.

        // The expected events are (in order): CREATED, STARTING, SWITCHING, SWITCHING.
        UserLifecycleEvent[] events = buildExpectedEvents(initialUserId, newUserId);

        assertThat(mListenerForAllEventsOnAnyUser.listener.getAllReceivedEvents())
                .containsAtLeastElementsIn(events)
                .inOrder();
        assertThat(mListenerForAllEventsOnCurrentUsers.listener.getAllReceivedEvents())
                .containsAtLeast(events[1], events[2], events[3])
                .inOrder();
        assertThat(mListenerForStartingEventsOnAnyUser.listener.getAllReceivedEvents())
                .containsExactly(events[1]);
        assertThat(mListenerForStoppingEventsOnAnyUser.listener.getAllReceivedEvents()).isEmpty();
        assertThat(mListenerForSwitchingEventsOnAnyUser.listener.getAllReceivedEvents())
                .containsExactly(events[2], events[3])
                .inOrder();
    }

    private UserLifecycleEvent[] buildExpectedEvents(int initialUserId, int newUserId) {
        return new UserLifecycleEvent[] {
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_CREATED, newUserId),
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING, newUserId),
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
                        /* from= */initialUserId, /* to= */newUserId),
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
                        /* from= */newUserId, /* to= */initialUserId)};
    }

    private static class Listener {
        public AtomicInteger executeCount;
        public BlockingUserLifecycleListener listener;
        public UserLifecycleEventFilter filter;
        public Executor executor = r -> {
            executeCount.getAndIncrement();
            r.run();
        };

        Listener(BlockingUserLifecycleListener listener,
                @Nullable UserLifecycleEventFilter filter) {
            executeCount = new AtomicInteger(0);
            this.listener = listener;
            this.filter = filter;
        }
    }
}
