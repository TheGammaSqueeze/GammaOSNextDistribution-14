/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_USERS;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;
import static android.car.user.CarUserManager.UserLifecycleEvent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import static java.lang.Math.max;

import android.car.CarOccupantZoneManager;
import android.car.SyncResultCallback;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.test.util.AndroidHelper;
import android.car.test.util.UserTestingHelper;
import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalRequest;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartRequest;
import android.car.user.UserStopRequest;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CarUserManagerTest extends AbstractCarTestCase {
    private static final String TAG = CarUserManagerTest.class.getSimpleName();
    private static final String NEW_USER_NAME_PREFIX = "CarCTSTest.";
    private static final int START_TIMEOUT_MS = 20_000;
    private static final int NO_EVENTS_TIMEOUT_MS = 5_000;
    private static final int CREATE_USER_TIMEOUT_MS = 20_000;

    private final List<UserHandle> mUsersToRemove = new ArrayList<>();
    private CarUserManager mCarUserManager;
    private CarOccupantZoneManager mOccupantZoneManager;
    private UserManager mUserManager;

    @Before
    public void setUp() {
        mUserManager = mContext.getSystemService(UserManager.class);
        mCarUserManager = getCar().getCarManager(CarUserManager.class);
        mOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);
    }

    @After
    public void cleanupUserState() {
        try {
            if (!mUsersToRemove.isEmpty()) {
                Log.i(TAG, "removing users at end of " + getTestName() + ": " + mUsersToRemove);
                for (UserHandle userHandle : mUsersToRemove) {
                    removeUser(userHandle);
                }
            } else {
                Log.i(TAG, "no user to remove at end of " + getTestName());
            }
        } catch (Exception e) {
            // Must catch otherwise it would be the test failure, which could hide the real issue
            Log.e(TAG, "Caught exception on " + getTestName()
                    + " cleanupUserState()", e);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#startUser(UserStartRequest, Executor, ResultCallback)",
            "android.car.user.CarUserManager#stopUser(UserStopRequest, Executor, ResultCallback)"})
    @EnsureHasPermission({CREATE_USERS, MANAGE_USERS, INTERACT_ACROSS_USERS})
    public void testStartUserOnDisplayAndStopUser() throws Exception {

        // Check if the device supports MUMD. If not, skip the test.
        UserTestingHelper.requireMumd(mContext);

        int displayId = UserTestingHelper
                .getDisplayForStartingBackgroundUser(mContext, mOccupantZoneManager);

        BlockingUserLifecycleListener listenerForVisible = null;
        BlockingUserLifecycleListener listenerForStarting = null;
        BlockingUserLifecycleListener listenerForInvisible = null;
        UserHandle newUser = null;
        boolean isAdded = false;
        try {
            newUser = createUser("newUser", /* isGuest= */ false);
            int userId = newUser.getIdentifier();
            listenerForVisible = BlockingUserLifecycleListener
                    .forSpecificEvents()
                    .forUser(userId)
                    .setTimeout(START_TIMEOUT_MS)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_VISIBLE)
                    .build();
            listenerForStarting = BlockingUserLifecycleListener
                    .forSpecificEvents()
                    .forUser(userId)
                    .setTimeout(START_TIMEOUT_MS)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .build();
            listenerForInvisible = BlockingUserLifecycleListener
                    .forSpecificEvents()
                    .forUser(userId)
                    .setTimeout(START_TIMEOUT_MS)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)
                    .build();

            Log.d(TAG, "Registering listeners.");
            mCarUserManager.addListener(Runnable::run, listenerForVisible);
            mCarUserManager.addListener(Runnable::run, listenerForStarting);
            mCarUserManager.addListener(Runnable::run, listenerForInvisible);
            Log.d(TAG, "Registered listeners.");
            isAdded = true;

            // Start the new user on a display.
            UserStartRequest startRequest = new UserStartRequest.Builder(newUser)
                    .setDisplayId(displayId).build();
            mCarUserManager.startUser(startRequest, Runnable::run,
                    response ->
                            assertWithMessage("startUser success for user %s on display %s",
                                    userId, displayId).that(response.isSuccess()).isTrue());

            List<UserLifecycleEvent> visibleEvents = listenerForVisible.waitForEvents();
            assertWithMessage("events").that(visibleEvents).hasSize(1);
            UserLifecycleEvent visibleEvent = visibleEvents.get(0);
            assertWithMessage("Type of the event").that(visibleEvent.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_VISIBLE);
            assertWithMessage("UserId of the event").that(visibleEvent.getUserHandle())
                    .isEqualTo(newUser);
            assertWithMessage("Visible users").that(mUserManager.getVisibleUsers())
                    .contains(newUser);

            List<UserLifecycleEvent> startingEvents = listenerForStarting.waitForEvents();
            assertWithMessage("events").that(startingEvents).hasSize(1);
            UserLifecycleEvent startingEvent = startingEvents.get(0);
            assertWithMessage("Type of the event").that(startingEvent.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_STARTING);
            assertWithMessage("UserId of the event").that(startingEvent.getUserHandle())
                    .isEqualTo(newUser);

            // By the time USER_VISIBLE event and USER_STARTING event are both received
            // the user should have been assigned to the display in occupant zone.
            assertWithMessage("User assigned to display %s", displayId)
                    .that(mOccupantZoneManager.getUserForDisplayId(displayId))
                    .isEqualTo(userId);
            CarOccupantZoneManager.OccupantZoneInfo zone =
                    mOccupantZoneManager.getOccupantZoneForUser(newUser);
            assertWithMessage("The display assigned to the started user %s", newUser)
                    .that(mOccupantZoneManager.getDisplayForOccupant(
                            zone, CarOccupantZoneManager.DISPLAY_TYPE_MAIN).getDisplayId())
                    .isEqualTo(displayId);

            // Stop the user.
            UserStopRequest stopRequest = new UserStopRequest.Builder(newUser).setForce().build();
            mCarUserManager.stopUser(stopRequest, Runnable::run,
                    response ->
                            assertWithMessage("stopUser success for user %s on display %s",
                                    userId, displayId).that(response.isSuccess()).isTrue());

            List<UserLifecycleEvent> invisibleEvents = listenerForInvisible.waitForEvents();
            assertWithMessage("events").that(invisibleEvents).hasSize(1);
            UserLifecycleEvent invisibleEvent = invisibleEvents.get(0);
            assertWithMessage("Type of the event").that(invisibleEvent.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE);
            assertWithMessage("UserId of the event").that(invisibleEvent.getUserHandle())
                    .isEqualTo(newUser);
            assertWithMessage("Visible users").that(mUserManager.getVisibleUsers())
                    .doesNotContain(newUser);
            // By the time USER_INVISIBLE event is received, the user should have been unassigned
            // from the display.
            assertWithMessage("User assigned to display %s", displayId)
                    .that(mOccupantZoneManager.getUserForDisplayId(displayId))
                            .isEqualTo(CarOccupantZoneManager.INVALID_USER_ID);
            assertWithMessage("The occupant zone assigned to the stopped user %s", newUser)
                    .that(mOccupantZoneManager.getOccupantZoneForUser(newUser))
                    .isNull();
        } finally {
            Log.d(TAG, "Unregistering the listeners.");
            if (isAdded) {
                mCarUserManager.removeListener(listenerForVisible);
                mCarUserManager.removeListener(listenerForInvisible);
            }
            Log.d(TAG, "Unregistered listeners.");

            if (newUser != null) {
                removeUser(newUser);
            }
            Log.d(TAG, "Removed the user." + newUser);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_CREATED"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testLifecycleUserCreatedListener_supportedVersion() throws Exception {

        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_CREATED)
                .build();

        UserHandle newUser = null;
        boolean isAdded = false;
        try {
            Log.d(TAG, "registering listener: " + listener);
            mCarUserManager.addListener(Runnable::run, listener);
            // If adding the listener fails, an exception will be thrown.
            isAdded = true;
            Log.v(TAG, "ok");

            newUser = createUser("TestUserToCreate", false);

            Log.d(TAG, "Waiting for events");
            List<UserLifecycleEvent> events = listener.waitForEvents();
            Log.d(TAG, "events: " + events);
            assertWithMessage("events").that(events).hasSize(1);
            UserLifecycleEvent event = events.get(0);
            assertWithMessage("type of event %s", event).that(event.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_CREATED);
            assertWithMessage("user id on %s", event).that(event.getUserHandle().getIdentifier())
                    .isEqualTo(newUser.getIdentifier());
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            if (isAdded) {
                mCarUserManager.removeListener(listener);
            }
            Log.v(TAG, "ok");

            if (newUser != null) {
                removeUser(newUser);
            }
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_REMOVED"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testLifecycleUserRemovedListener_supportedVersion() throws Exception {

        UserHandle newUser = createUser("TestUserToRemove", false);

        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUser.getIdentifier())
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_REMOVED)
                .build();

        boolean isAdded = false;
        try {
            Log.d(TAG, "registering listener: " + listener);
            // If adding the listener fails, an exception will be thrown.
            mCarUserManager.addListener(Runnable::run, listener);
            isAdded = true;
            Log.v(TAG, "ok");

            removeUser(newUser);

            Log.d(TAG, "Waiting for events");
            List<UserLifecycleEvent> events = listener.waitForEvents();
            Log.d(TAG, "events: " + events);
            assertWithMessage("events").that(events).hasSize(1);
            UserLifecycleEvent event = events.get(0);
            assertWithMessage("type of event %s", event).that(event.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_REMOVED);
            assertWithMessage("user id on %s", event).that(event.getUserHandle().getIdentifier())
                    .isEqualTo(newUser.getIdentifier());
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            if (isAdded) {
                mCarUserManager.removeListener(listener);
            }
            Log.v(TAG, "ok");
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#isValidUser(UserHandle)"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testIsValidUserExists() {
        assertThat(mCarUserManager.isValidUser(Process.myUserHandle())).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#isValidUser(UserHandle)"})
    public void testIsValidUserDoesNotExist() {
        assertThrows(SecurityException.class,
                () -> mCarUserManager.isValidUser(getNonExistentUser()));
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#lifecycleEventTypeToString"})
    public void testLifecycleEventTypeToString() {
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_STARTING)).isEqualTo("STARTING");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING)).isEqualTo("SWITCHING");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)).isEqualTo("UNLOCKING");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)).isEqualTo("UNLOCKED");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_STOPPING)).isEqualTo("STOPPING");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_STOPPED)).isEqualTo("STOPPED");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_CREATED)).isEqualTo("CREATED");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_REMOVED)).isEqualTo("REMOVED");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_VISIBLE)).isEqualTo("VISIBLE");
        expectThat(mCarUserManager.lifecycleEventTypeToString(
                USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)).isEqualTo("INVISIBLE");
        expectThat(mCarUserManager.lifecycleEventTypeToString(0)).isEqualTo("UNKNOWN-0");
    }

    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#removeUser(UserRemovalRequest, Executor, "
                    + "ResultCallback)"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testRemoveUserExists() {
        UserHandle newUser = createUser("TestUserToRemove", false);

        mCarUserManager.removeUser(new UserRemovalRequest.Builder(newUser).build(), Runnable::run,
                response -> assertThat(response.getStatus()).isEqualTo(
                        UserRemovalResult.STATUS_SUCCESSFUL)
        );

        // If user is removed by CarUserManager, then user does not need to be removed in cleanup.
        mUsersToRemove.remove(newUser);
    }

    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#removeUser(UserRemovalRequest, Executor, "
                    + "ResultCallback)"})
    @EnsureHasPermission({CREATE_USERS})
    public void testRemoveUserDoesNotExist() {
        mCarUserManager.removeUser(new UserRemovalRequest.Builder(getNonExistentUser()).build(),
                Runnable::run, response -> assertThat(response.getStatus()).isEqualTo(
                        UserRemovalResult.STATUS_USER_DOES_NOT_EXIST)
        );
    }

    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#createUser(UserCreationRequest, Executor, "
                    + "ResultCallback)"})
    @EnsureHasPermission({CREATE_USERS})
    public void testCreateUser() throws Exception {
        SyncResultCallback<UserCreationResult> userCreationResultCallback =
                new SyncResultCallback<>();
        mCarUserManager.createUser(new UserCreationRequest.Builder().build(), Runnable::run,
                userCreationResultCallback);

        UserCreationResult result = userCreationResultCallback.get(CREATE_USER_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);

        assertWithMessage("createUser: ").that(result.getStatus()).isEqualTo(
                UserCreationResult.STATUS_SUCCESSFUL);

        // Clean up new user at end of test.
        mUsersToRemove.add(result.getUser());
    }

    @NonNull
    private UserHandle createUser(@Nullable String name, boolean isGuest) {
        name = getNewUserName(name);
        Log.d(TAG, "Creating new " + (isGuest ? "guest" : "user") + " with name '" + name
                + "' using CarUserManager");

        assertCanAddUser();

        SyncResultCallback<UserCreationResult> userCreationResultCallback =
                new SyncResultCallback<>();
        mCarUserManager.createUser(new UserCreationRequest.Builder().build(), Runnable::run,
                userCreationResultCallback);
        UserCreationResult result = new UserCreationResult(
                UserCreationResult.STATUS_ANDROID_FAILURE);

        try {
            result = userCreationResultCallback.get(CREATE_USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Log.d(TAG, "result: " + result);
        } catch (TimeoutException e) {
            Log.e(TAG, "createUser: timed out waiting for result", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "createUser: interrupted waiting for result", e);
            Thread.currentThread().interrupt();
        }

        assertWithMessage("user creation result (waited for %sms)", DEFAULT_WAIT_TIMEOUT_MS)
                .that(result).isNotNull();
        assertWithMessage("user creation result (%s) for user named %s", result, name)
                .that(result.isSuccess()).isTrue();
        UserHandle user = result.getUser();
        assertWithMessage("user on result %s", result).that(user).isNotNull();
        mUsersToRemove.add(user);
        return user;
    }

    protected void removeUser(UserHandle userHandle) throws IOException {
        Log.d(TAG, "Removing user " + userHandle.getIdentifier());

        assertWithMessage("User removed").that(
                executeShellCommand("pm remove-user --wait %d",
                        userHandle.getIdentifier())).contains("Success: removed user");

        mUsersToRemove.remove(userHandle);
    }

    protected void assertCanAddUser() {
        Bundle restrictions = mUserManager.getUserRestrictions();
        Log.d(TAG, "Restrictions for user " + mContext.getUser() + ": "
                + AndroidHelper.toString(restrictions));
        assertWithMessage("%s restriction", UserManager.DISALLOW_ADD_USER)
                .that(restrictions.getBoolean(UserManager.DISALLOW_ADD_USER, false)).isFalse();
    }

    private String getNewUserName(String name) {
        StringBuilder newName = new StringBuilder(NEW_USER_NAME_PREFIX).append(getTestName());
        if (name != null) {
            newName.append('.').append(name);
        }
        return newName.toString();
    }

    protected String getTestName() {
        // TODO(b/300964422): Find out a way to get test name
        return "";
    }

    protected static void fail(String format, Object... args) {
        String message = String.format(format, args);
        Log.e(TAG, "test failed: " + message);
        org.junit.Assert.fail(message);
    }

    private UserHandle getNonExistentUser() {
        List<UserHandle> existingUsers = mUserManager.getUserHandles(false);

        int newUserId = UserHandle.USER_NULL;
        for (UserHandle userHandle : existingUsers) {
            newUserId = max(newUserId, userHandle.getIdentifier());
        }

        return UserHandle.of(++newUserId);
    }
}
