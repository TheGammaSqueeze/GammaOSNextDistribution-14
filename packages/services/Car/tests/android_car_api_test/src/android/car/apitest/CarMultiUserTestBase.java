/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.car.test.util.UserTestingHelper.setMaxSupportedUsers;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.SyncResultCallback;
import android.car.test.util.AndroidHelper;
import android.car.test.util.UserTestingHelper;
import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalRequest;
import android.car.user.UserStartRequest;
import android.car.user.UserStopRequest;
import android.car.user.UserSwitchRequest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Base class for tests that deal with multi user operations (creation, switch, etc)
 *
 */
abstract class CarMultiUserTestBase extends CarApiTestBase {

    private static final String TAG = CarMultiUserTestBase.class.getSimpleName();

    private static final long REMOVE_USER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30_000);
    private static final long SWITCH_USER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30_000);

    private static final String NEW_USER_NAME_PREFIX = "CarApiTest.";

    private static final int sMaxNumberUsersBefore = UserManager.getMaxSupportedUsers();
    private static boolean sChangedMaxNumberUsers;

    protected CarOccupantZoneManager mCarOccupantZoneManager;
    protected CarUserManager mCarUserManager;
    protected UserManager mUserManager;

    /**
     * Current user before the test runs.
     */
    private UserInfo mInitialUser;

    private final CountDownLatch mUserRemoveLatch = new CountDownLatch(1);
    private final List<Integer> mUsersToRemove = new ArrayList<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received a broadcast: " + AndroidHelper.toString(intent));
            mUserRemoveLatch.countDown();
        }
    };

    // Guard to avoid test failure on @After when @Before failed (as it would hide the real issue)
    private boolean mSetupFinished;

    @Before
    public final void setMultiUserFixtures() throws Exception {
        Log.d(TAG, "setMultiUserFixtures() for " + getTestName());

        mCarOccupantZoneManager = getCarService(Car.CAR_OCCUPANT_ZONE_SERVICE);
        mCarUserManager = getCarService(Car.CAR_USER_SERVICE);
        mUserManager = getContext().getSystemService(UserManager.class);

        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_REMOVED);
        getContext().registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Registered a broadcast receiver: " + mReceiver
                + " with filter: " + filter);

        List<UserInfo> users = mUserManager.getAliveUsers();

        // Set current user
        int currentUserId = getCurrentUserId();
        Log.d(TAG, "Multi-user state on " + getTestName() + ": currentUser=" + currentUserId
                + ", aliveUsers=" + users);
        for (UserInfo user : users) {
            if (user.id == currentUserId) {
                mInitialUser = user;
                break;
            }
        }
        assertWithMessage("user for currentId %s").that(mInitialUser).isNotNull();

        // Make sure current user is not a left-over from previous test
        if (isUserCreatedByTheseTests(mInitialUser)) {
            UserInfo properUser = null;
            for (UserInfo user : users) {
                if (user.id != UserHandle.USER_SYSTEM && !isUserCreatedByTheseTests(user)) {
                    properUser = user;
                    break;
                }
            }
            assertWithMessage("found a proper user to switch from %s", mInitialUser.toFullString())
                    .that(properUser).isNotNull();

            Log.i(TAG, "Current user on start of " + getTestName() + " is a dangling user: "
                    + mInitialUser.toFullString() + "; switching to " + properUser.toFullString());
            switchUser(properUser.id);
            mInitialUser = properUser;
        }

        // Remove dangling users from previous tests
        for (UserInfo user : users) {
            if (!isUserCreatedByTheseTests(user)) continue;
            Log.e(TAG, "Removing dangling user " + user.toFullString() + " on @Before method of "
                    + getTestName());
            boolean removed = mUserManager.removeUser(user.id);
            if (!removed) {
                Log.e(TAG, "user " + user.toFullString() + " was not removed");
            }
        }

        Log.d(TAG, "setMultiUserFixtures(): Saul Goodman, setting mSetupFinished to true");
        mSetupFinished = true;
    }

    @After
    public final void cleanupUserState() throws Exception {
        try {
            if (!mSetupFinished) {
                Log.w(TAG, "skipping cleanupUserState() because mSetupFinished is false");
                return;
            }

            getContext().unregisterReceiver(mReceiver);
            Log.d(TAG, "Unregistered a broadcast receiver: " + mReceiver);

            int currentUserId = getCurrentUserId();
            int initialUserId = mInitialUser.id;
            if (currentUserId != initialUserId) {
                Log.i(TAG, "Wrong current userId at the end of " + getTestName() + ": "
                        + currentUserId + "; switching back to " + initialUserId);
                switchUser(initialUserId);

            }
            if (!mUsersToRemove.isEmpty()) {
                Log.i(TAG, "removing users at end of " + getTestName() + ": " + mUsersToRemove);
                for (Integer userId : mUsersToRemove) {
                    if (hasUser(userId)) {
                        mUserManager.removeUser(userId);
                    }
                }
            } else {
                Log.i(TAG, "no user to remove at end of " + getTestName());
            }
        } catch (Exception e) {
            // Must catch otherwise it would be the test failure, which could hide the real issue
            Log.e(TAG, "Caught exception on " + getTestName()
                    + " disconnectCarAndCleanupUserState()", e);
        }
    }

    protected static void setupMaxNumberOfUsers(int requiredUsers) {
        if (sMaxNumberUsersBefore < requiredUsers) {
            sChangedMaxNumberUsers = true;
            Log.i(TAG, "Increasing maximizing number of users from " + sMaxNumberUsersBefore
                    + " to " + requiredUsers);
            setMaxSupportedUsers(requiredUsers);
        }
    }

    protected static void restoreMaxNumberOfUsers() {
        if (sChangedMaxNumberUsers) {
            Log.i(TAG, "Restoring maximum number of users to " + sMaxNumberUsersBefore);
            setMaxSupportedUsers(sMaxNumberUsersBefore);
        }
    }

    @UserIdInt
    protected int getCurrentUserId() {
        return ActivityManager.getCurrentUser();
    }

    @NonNull
    protected UserInfo createUser() throws Exception {
        return createUser("NonGuest");
    }

    @NonNull
    protected UserInfo createUser(String name) throws Exception {
        return createUser(name, /* isGuest= */ false);
    }

    @NonNull
    protected UserInfo createGuest() throws Exception {
        return createGuest("Guest");
    }

    @NonNull
    protected UserInfo createGuest(String name) throws Exception {
        return createUser(name, /* isGuest= */ true);
    }

    @NonNull
    private UserInfo createUser(@Nullable String name, boolean isGuest) throws Exception {
        name = getNewUserName(name);
        Log.d(TAG, "Creating new " + (isGuest ? "guest" : "user") + " with name '" + name
                + "' using CarUserManager");

        assertCanAddUser();

        UserCreationRequest.Builder userCreationRequestBuilder = new UserCreationRequest.Builder();

        if (isGuest) {
            userCreationRequestBuilder.setGuest();
        }

        SyncResultCallback<UserCreationResult> userCreationResultCallback =
                new SyncResultCallback<>();

        mCarUserManager.createUser(userCreationRequestBuilder.setName(name).build(), Runnable::run,
                userCreationResultCallback);

        UserCreationResult result = userCreationResultCallback.get(
                DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        Log.d(TAG, "result: " + result);
        assertWithMessage("user creation result (waited for %sms)", DEFAULT_WAIT_TIMEOUT_MS)
                .that(result).isNotNull();
        assertWithMessage("user creation result (%s) success for user named %s", result, name)
                .that(result.isSuccess()).isTrue();
        UserHandle user = result.getUser();
        assertWithMessage("user on result %s", result).that(user).isNotNull();
        mUsersToRemove.add(user.getIdentifier());
        assertWithMessage("new user %s is guest", user.toString())
                .that(mUserManager.isGuestUser(user.getIdentifier()))
                .isEqualTo(isGuest);
        return mUserManager.getUserInfo(result.getUser().getIdentifier());
    }

    private String getNewUserName(String name) {
        StringBuilder newName = new StringBuilder(NEW_USER_NAME_PREFIX).append(getTestName());
        if (name != null) {
            newName.append('.').append(name);
        }
        return newName.toString();
    }

    protected void assertCanAddUser() {
        Bundle restrictions = mUserManager.getUserRestrictions();
        Log.d(TAG, "Restrictions for user " + getContext().getUser() + ": "
                + AndroidHelper.toString(restrictions));
        assertWithMessage("%s restriction", UserManager.DISALLOW_ADD_USER)
                .that(restrictions.getBoolean(UserManager.DISALLOW_ADD_USER, false)).isFalse();
    }

    protected void assertInitialUserIsAdmin() {
        assertWithMessage("initial user (%s) is admin", mInitialUser.toFullString())
                .that(mInitialUser.isAdmin()).isTrue();
    }

    protected void waitForUserRemoval(@UserIdInt int userId) throws Exception {
        boolean result = mUserRemoveLatch.await(REMOVE_USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("User %s removed in %sms", userId, REMOVE_USER_TIMEOUT_MS)
                .that(result)
                .isTrue();
    }

    protected void switchUser(@UserIdInt int userId) throws Exception {
        boolean waitForUserSwitchToComplete = true;
        // If current user is the target user, no life cycle event is expected.
        if (getCurrentUserId() == userId) waitForUserSwitchToComplete = false;

        Log.d(TAG, "registering listener for user switching");
        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(userId)
                .setTimeout(SWITCH_USER_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                .build();
        mCarUserManager.addListener(Runnable::run, listener);

        try {
            Log.i(TAG, "Switching to user " + userId + " using CarUserManager");
            mCarUserManager.switchUser(new UserSwitchRequest.Builder(
                    UserHandle.of(userId)).build(), Runnable::run, response -> {
                    Log.d(TAG, "result: " + response);
                    assertWithMessage("User %s switched in %sms. Result: %s", userId,
                        SWITCH_USER_TIMEOUT_MS, response).that(response.isSuccess()).isTrue();
                }
            );

            if (waitForUserSwitchToComplete) {
                listener.waitForEvents();
            }
        } finally {
            mCarUserManager.removeListener(listener);
        }

        Log.d(TAG, "User switch complete. User id: " + userId);
    }

    protected void removeUser(@UserIdInt int userId) throws Exception {
        Log.d(TAG, "Removing user " + userId);

        mCarUserManager.removeUser(new UserRemovalRequest.Builder(
                UserHandle.of(userId)).build(), Runnable::run, response -> {
                    Log.d(TAG, "result: " + response);
                    assertWithMessage("User %s removed. Result: %s", userId, response)
                            .that(response.isSuccess()).isTrue();
                }
        );
    }

    protected void startUserInBackgroundOnSecondaryDisplay(@UserIdInt int userId, int displayId)
            throws Exception {
        Log.i(TAG, "Starting background user " + userId + " on display " + displayId);

        UserStartRequest request = new UserStartRequest.Builder(UserHandle.of(userId))
                .setDisplayId(displayId).build();
        mCarUserManager.startUser(request, Runnable::run,
                response ->
                    assertWithMessage("startUser success for user %s on display %s",
                            userId, displayId).that(response.isSuccess()).isTrue());
    }

    protected void forceStopUser(@UserIdInt int userId) throws Exception {
        Log.i(TAG, "Force-stopping user " + userId);

        UserStopRequest request =
                new UserStopRequest.Builder(UserHandle.of(userId)).setForce().build();
        mCarUserManager.stopUser(request, Runnable::run,
                response ->
                    assertWithMessage("stopUser success for user %s", userId)
                            .that(response.isSuccess()).isTrue());
    }

    @Nullable
    protected UserInfo getUser(@UserIdInt int id) {
        List<UserInfo> list = mUserManager.getUsers();

        for (UserInfo user : list) {
            if (user.id == id) {
                return user;
            }
        }
        return null;
    }

    protected boolean hasUser(@UserIdInt int id) {
        return getUser(id) != null;
    }

    protected void setSystemProperty(String property, String value) {
        String oldValue = SystemProperties.get(property);
        Log.d(TAG, "Setting system prop " + property + " from '" + oldValue + "' to '"
                + value + "'");

        // NOTE: must use Shell command as SystemProperties.set() requires SELinux permission check
        // (so invokeWithShellPermissions() would not be enough)
        runShellCommand("setprop %s %s", property, value);
        Log.v(TAG, "Set: " + SystemProperties.get(property));
    }

    protected void assertUserInfo(UserInfo actualUser, UserInfo expectedUser) {
        assertWithMessage("Wrong id for user %s", actualUser.toFullString())
                .that(actualUser.id).isEqualTo(expectedUser.id);
        assertWithMessage("Wrong name for user %s", actualUser.toFullString())
                .that(actualUser.name).isEqualTo(expectedUser.name);
        assertWithMessage("Wrong type for user %s", actualUser.toFullString())
                .that(actualUser.userType).isEqualTo(expectedUser.userType);
        assertWithMessage("Wrong flags for user %s", actualUser.toFullString())
                .that(actualUser.flags).isEqualTo(expectedUser.flags);
    }

    private static boolean isUserCreatedByTheseTests(UserInfo user) {
        return user.name != null && user.name.startsWith(NEW_USER_NAME_PREFIX);
    }

    /**
     * Checks if the target device supports MUMD (multi-user multi-display).
     * @throws AssumptionViolatedException if the device does not support MUMD.
     */
    protected static void requireMumd() {
        UserTestingHelper.requireMumd(getTargetContext());
    }

    /**
     * Returns a secondary display that is available to start a background user on.
     *
     * @return the id of a secondary display that is not assigned to any user, if any.
     * @throws IllegalStateException when there is no secondary display available.
     */
    protected int getDisplayForStartingBackgroundUser() {
        return UserTestingHelper.getDisplayForStartingBackgroundUser(
                getTargetContext(), mCarOccupantZoneManager);
    }

    private static Context getTargetContext() {
        return getInstrumentation().getTargetContext();
    }
}
