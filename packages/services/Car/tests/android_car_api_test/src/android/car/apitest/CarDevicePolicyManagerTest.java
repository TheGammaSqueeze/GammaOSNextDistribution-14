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
package android.car.apitest;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.admin.CarDevicePolicyManager;
import android.car.admin.CreateUserResult;
import android.car.admin.RemoveUserResult;
import android.car.admin.StartUserInBackgroundResult;
import android.car.admin.StopUserResult;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Test;

public final class CarDevicePolicyManagerTest extends CarMultiUserTestBase {

    private static final String TAG = CarDevicePolicyManagerTest.class.getSimpleName();

    private static final int TIMEOUT_MS = 5_000; // 5 seconds

    private CarDevicePolicyManager mCarDpm;
    private DevicePolicyManager mDpm;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;

    @Before
    public void setManager() throws Exception {
        mCarDpm = getCarService(Car.CAR_DEVICE_POLICY_SERVICE);
        Context context = getContext();
        mDpm = context.getSystemService(DevicePolicyManager.class);
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
    }

    @Test
    public void testRemoveUser() throws Exception {
        assertInitialUserIsAdmin();

        UserInfo user = createUser();
        Log.d(TAG, "removing user " + user.toFullString());
        RemoveUserResult result = mCarDpm.removeUser(user.getUserHandle());
        Log.d(TAG, "result: " + result);

        assertWithMessage("Result of removeUser %s: %s", user.toFullString(), result)
                .that(result.isSuccess()).isTrue();
    }

    @Test
    public void testRemoveUser_whenDisallowed() throws Exception {
        try {
            testRemoveUser();
        } finally {
            mUserManager.setUserRestriction(UserManager.DISALLOW_REMOVE_USER, false,
                    UserHandle.SYSTEM);
        }
    }

    @Test
    public void testRemoveUser_currentUserSetEphemeral() throws Exception {
        assertInitialUserIsAdmin();
        int initialUserId = getCurrentUserId();

        UserInfo user = createUser();
        Log.d(TAG, "switching to user " + user.toFullString());
        switchUser(user.id);

        Log.d(TAG, "removing user " + user.toFullString());
        RemoveUserResult result = mCarDpm.removeUser(user.getUserHandle());

        assertWithMessage("status of remove user %s (%s)", user.toFullString(), result)
                .that(result.getStatus())
                .isEqualTo(RemoveUserResult.STATUS_SUCCESS_SET_EPHEMERAL);

        assertWithMessage("User %s still exist", user).that(hasUser(user.id)).isTrue();
        assertWithMessage("User %s set as ephemeral", user)
                .that(getUser(user.id).isEphemeral())
                .isTrue();

        // Switch back to the starting user.
        Log.d(TAG, "switching to user " + initialUserId);
        switchUser(initialUserId);

        // User is removed once switch is complete
        Log.d(TAG, "waiting for user to be removed: " + user);
        waitForUserRemoval(user.id);
        waitUntil("User " + user + " exists after switch (should be removed)",
                TIMEOUT_MS, () -> !hasUser(user.id));
    }

    @Test
    public void testCreateUser() throws Exception {
        assertCanAddUser();

        String name = "CarDevicePolicyManagerTest.testCreateUser";
        int type = CarDevicePolicyManager.USER_TYPE_REGULAR;
        Log.d(TAG, "creating new user with name " + name + " and type " + type);
        CreateUserResult result = mCarDpm.createUser(name, type);
        Log.d(TAG, "result: " + result);
        UserHandle user = result.getUserHandle();

        try {
            assertWithMessage("Created user named %s and type %s: %s", name, type,
                    result).that(result.isSuccess()).isTrue();
            assertWithMessage("%s is not an admin user", user).that(
                    mUserManager.isUserAdmin(user.getIdentifier())).isFalse();
            assertWithMessage("Create user with name %s", name).that(
                    mUserManager.getUserInfo(user.getIdentifier()).name).isEqualTo(name);
        } finally {
            if (user != null) {
                removeUser(user.getIdentifier());
            }
        }
    }

    @Test
    public void testCreateAdminUser() throws Exception {
        assertCanAddUser();

        String name = "CarDevicePolicyManagerTest.testCreateUser";
        int type = CarDevicePolicyManager.USER_TYPE_ADMIN;
        Log.d(TAG, "creating new user with name " + name + " and type " + type);
        CreateUserResult result = mCarDpm.createUser(name, type);
        Log.d(TAG, "result: " + result);
        UserHandle user = result.getUserHandle();

        try {
            assertWithMessage("Created user named %s and type %s: %s", name, type,
                    result).that(result.isSuccess()).isTrue();
            assertWithMessage("%s is an admin user", user).that(
                    mUserManager.isUserAdmin(user.getIdentifier())).isTrue();
            assertWithMessage("Create user with name %s", name).that(
                    mUserManager.getUserInfo(user.getIdentifier()).name).isEqualTo(name);

        } finally {
            if (user != null) {
                removeUser(user.getIdentifier());
            }
        }
    }

    @Test
    public void testCreateGuestUser() throws Exception {
        assertCanAddUser();

        String name = "CarDevicePolicyManagerTest.testCreateUser";
        int type = CarDevicePolicyManager.USER_TYPE_GUEST;
        Log.d(TAG, "creating new user with name " + name + " and type " + type);
        CreateUserResult result = mCarDpm.createUser(name, type);
        Log.d(TAG, "result: " + result);
        UserHandle user = result.getUserHandle();

        try {
            assertWithMessage("Created user named %s and type %s: %s", name, type,
                    result).that(result.isSuccess()).isTrue();
            assertWithMessage("%s is a guest user", user).that(
                    mUserManager.isGuestUser(user.getIdentifier())).isTrue();
            assertWithMessage("%s is not an admin user", user).that(
                    mUserManager.isUserAdmin(user.getIdentifier())).isFalse();
            assertWithMessage("Create user with name %s", name).that(
                    mUserManager.getUserInfo(user.getIdentifier()).name).isEqualTo(name);

        } finally {
            if (user != null) {
                removeUser(user.getIdentifier());
            }
        }
    }

    @Test
    public void testStartUserInBackground() throws Exception {
        assertInitialUserIsAdmin();

        UserInfo user = createUser();
        Log.d(TAG, "starting user in background " + user.toFullString());
        StartUserInBackgroundResult result = mCarDpm.startUserInBackground(user.getUserHandle());
        Log.d(TAG, "result: " + result);

        assertWithMessage("Result of startUserInBackground %s: %s", user.toFullString(), result)
                .that(result.isSuccess()).isTrue();
    }

    @Test
    public void testStopUser() throws Exception {
        assertInitialUserIsAdmin();

        UserInfo user = createUser();
        Log.d(TAG, "stopping user in background " + user.toFullString());
        StopUserResult result = mCarDpm.stopUser(user.getUserHandle());
        Log.d(TAG, "result: " + result);

        assertWithMessage("Result of stopUser %s: %s", user.toFullString(), result)
                .that(result.isSuccess()).isTrue();
    }

    @Test
    @FlakyTest(bugId = 190417819)
    public void testLockNow_safe() throws Exception {
        lockNowTest(/* safe= */ true);
    }

    @Test
    @FlakyTest(bugId = 190417819)
    public void testLockNow_unsafe() throws Exception {
        lockNowTest(/* safe= */ false);
    }

    // lockNow() is safe regardless of the UXR state
    private void lockNowTest(boolean safe) throws Exception {

        assertScreenOn();

        runSecureDeviceTest(()-> {
            setDpmSafety(safe);

            try {
                mDpm.lockNow();

                assertLockedEventually();
                assertScreenOn();
            } finally {
                setDpmSafety(/* safe= */ true);
            }
        });
    }

    private void runSecureDeviceTest(@NonNull Runnable test) {
        unlockDevice();
        setUserPin(1234);

        try {
            test.run();
        } finally {
            resetUserPin(1234);
            unlockDevice();
        }
    }

    private void unlockDevice() {
        runShellCommand("input keyevent KEYCODE_POWER");
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        runShellCommand("wm dismiss-keyguard");
        assertUnLockedEventually();
    }

    private void setUserPin(int pin) {
        runShellCommand("locksettings set-pin %d", pin);
    }

    private void resetUserPin(int oldPin) {
        runShellCommand("locksettings clear --old %d", oldPin);
    }

    private void assertUnlocked() {
        assertWithMessage("device is locked").that(mKeyguardManager.isDeviceLocked()).isFalse();
        assertWithMessage("keyguard is locked").that(mKeyguardManager.isKeyguardLocked()).isFalse();
    }

    private void assertUnLockedEventually() {
        eventually(() -> assertUnlocked());
    }

    private void assertLocked() {
        assertDeviceSecure();
        assertWithMessage("device is unlocked").that(mKeyguardManager.isDeviceLocked())
                .isTrue();
        assertWithMessage("keyguard is unlocked").that(mKeyguardManager.isKeyguardLocked())
                .isTrue();
    }

    private void assertLockedEventually() {
        eventually(() -> assertLocked());
    }

    private void assertDeviceSecure() {
        assertWithMessage("device is secure").that(mKeyguardManager.isDeviceSecure()).isTrue();
    }

    private void assertScreenOn() {
        assertWithMessage("screen is off").that(mPowerManager.isInteractive()).isTrue();
    }

    private void setDpmSafety(boolean safe) {
        requireNonUserBuild();
        String state = safe ? "park" : "drive";
        runShellCommand("cmd car_service emulate-driving-state %s", state);
    }
}
