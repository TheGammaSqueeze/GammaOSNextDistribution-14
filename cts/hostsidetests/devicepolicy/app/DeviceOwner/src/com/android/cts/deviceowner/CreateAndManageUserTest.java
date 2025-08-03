/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.deviceowner;

import static android.os.UserManager.USER_OPERATION_SUCCESS;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.expectThrows;

import android.app.ActivityManager;
import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.DebugUtils;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Test {@link DevicePolicyManager#createAndManageUser}.
 */
public class CreateAndManageUserTest extends BaseDeviceOwnerTest {
    private static final String TAG = "CreateAndManageUserTest";

    private static final String AFFILIATION_ID = "affiliation.id";
    private static final String EXTRA_AFFILIATION_ID = "affiliationIdExtra";
    private static final String EXTRA_CURRENT_USER_PACKAGES = "currentUserPackages";
    private static final String EXTRA_METHOD_NAME = "methodName";
    private static final long ON_ENABLED_TIMEOUT_SECONDS = 120;

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_ADD_USER);
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);
        super.tearDown();
    }

    public void testCreateAndManageUser() throws Exception {
        UserHandle userHandle = createAndManageUser();

        assertWithMessage("New user").that(userHandle).isNotNull();
    }

    public void testCreateAndManageUser_MaxUsers() throws Exception {
        UserManager.UserOperationException e = expectThrows(
                UserManager.UserOperationException.class, () -> createAndManageUser());

        assertUserOperationResult(e.getUserOperationResult(),
                UserManager.USER_OPERATION_ERROR_MAX_USERS,
                "user creation when max users is reached");
    }

    @SuppressWarnings("unused")
    private static void assertSkipSetupWizard(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName) throws Exception {
        assertWithMessage("user setup settings (%s)", Settings.Secure.USER_SETUP_COMPLETE)
                .that(Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE))
                .isEqualTo(1);
    }

    public void testCreateAndManageUser_SkipSetupWizard() throws Exception {
        runCrossUserVerification(DevicePolicyManager.SKIP_SETUP_WIZARD, "assertSkipSetupWizard");

        PrimaryUserService.assertCrossUserCallArrived();
    }

    public void testCreateAndManageUser_GetSecondaryUsers() throws Exception {
        UserHandle newUserHandle = createAndManageUser();

        List<UserHandle> secondaryUsers = mDevicePolicyManager.getSecondaryUsers(getWho());
        if (isHeadlessSystemUserMode()) {
            assertWithMessage("secondary users").that(secondaryUsers)
                .containsExactly(getCurrentUser(), newUserHandle);
        } else {
            assertWithMessage("secondary users").that(secondaryUsers)
                    .containsExactly(newUserHandle);
        }
    }

    public void testCreateAndManageUser_SwitchUser() throws Exception {
        UserHandle userHandle = createAndManageUser();

        List<UserHandle> usersOnBroadcasts = switchUserAndWaitForBroadcasts(userHandle);

        assertWithMessage("user on broadcasts").that(usersOnBroadcasts).containsExactly(userHandle,
                userHandle);
    }

    public void testCreateAndManageUser_CannotStopCurrentUser() throws Exception {
        UserHandle userHandle = createAndManageUser();

        switchUserAndWaitForBroadcasts(userHandle);

        stopUserAndCheckResult(userHandle, UserManager.USER_OPERATION_ERROR_CURRENT_USER);
    }

    public void testCreateAndManageUser_StartInBackground() throws Exception {
        UserHandle userHandle = createAndManageUser();

        List<UserHandle> usersOnBroadcasts = startUserInBackgroundAndWaitForBroadcasts(userHandle);

        assertWithMessage("user on broadcasts").that(usersOnBroadcasts).containsExactly(userHandle);
    }

    public void testCreateAndManageUser_StartInBackground_MaxRunningUsers() throws Exception {
        UserHandle userHandle = createAndManageUser();

        // Start user in background and should receive max running users error
        startUserInBackgroundAndCheckResult(userHandle,
                UserManager.USER_OPERATION_ERROR_MAX_RUNNING_USERS);
    }

    public void testCreateAndManageUser_StopUser() throws Exception {
        UserHandle userHandle = createAndManageUser();
        startUserInBackgroundAndWaitForBroadcasts(userHandle);

        List<UserHandle> usersOnBroadcasts = stopUserAndWaitForBroadcasts(userHandle);

        assertWithMessage("user on broadcasts").that(usersOnBroadcasts).containsExactly(userHandle);
    }

    public void testCreateAndManageUser_StopEphemeralUser_DisallowRemoveUser() throws Exception {
        // Set DISALLOW_REMOVE_USER restriction
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);

        UserHandle userHandle = createAndManageUser(DevicePolicyManager.MAKE_USER_EPHEMERAL);
        startUserInBackgroundAndWaitForBroadcasts(userHandle);
        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(),
                BasicAdminReceiver.ACTION_USER_STOPPED, BasicAdminReceiver.ACTION_USER_REMOVED);

        callback.runAndUnregisterSelf(
                () -> stopUserAndCheckResult(userHandle, USER_OPERATION_SUCCESS));

        // It's running just one operation (which issues a ACTION_USER_STOPPED), but as the
        // user is ephemeral, it will be automatically removed (which issues a
        // ACTION_USER_REMOVED).
        assertWithMessage("user on broadcasts").that(callback.getUsersOnReceivedBroadcasts())
                .containsExactly(userHandle, userHandle);
    }

    @SuppressWarnings("unused")
    private static void logoutUser(Context context, DevicePolicyManager devicePolicyManager,
            ComponentName componentName) {
        Log.d(TAG, "calling logoutUser() on user " + context.getUserId());
        int result = devicePolicyManager.logoutUser(componentName);
        Log.d(TAG, "result: " + userOperationResultToString(result));
        assertUserOperationResult(result, USER_OPERATION_SUCCESS, "cannot logout user");
    }

    private void clearLogoutUserIfNecessary() throws Exception {
        UserHandle userHandle = mDevicePolicyManager.getLogoutUser();
        Log.d(TAG, "clearLogoutUserIfNecessary(): logoutUser=" + userHandle);
        if (userHandle == null) {
            Log.d(TAG, "clearLogoutUserIfNecessary(): Saul Goodman!");
            return;
        }
        Log.w(TAG, "test started with a logout user (" + userHandle + "); logging out");
        int result = SystemUtil
                .callWithShellPermissionIdentity(() -> mDevicePolicyManager.logoutUser());
        Log.d(TAG, "Result: " + userOperationResultToString(result));
    }

    public void testCreateAndManageUser_LogoutUser() throws Exception {
        clearLogoutUserIfNecessary();

        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(),
                BasicAdminReceiver.ACTION_USER_STARTED, BasicAdminReceiver.ACTION_USER_STOPPED);

        UserHandle userHandle = runCrossUserVerification(callback,
                /* createAndManageUserFlags= */ 0, "logoutUser", /* currentUserPackages= */ null);

        List<UserHandle> users = callback.getUsersOnReceivedBroadcasts();
        Log.d(TAG, "users on brodcast: " + users);
        assertWithMessage("users on broadcast").that(users).containsExactly(userHandle, userHandle);
        assertWithMessage("final logout user").that(mDevicePolicyManager.getLogoutUser())
                .isNull();
    }

    public void testCreateAndManageUser_LogoutUser_systemApi() throws Exception {
        clearLogoutUserIfNecessary();

        UserHandle currentUser = getCurrentUser();
        UserHandle newUser = createAndManageUser();
        List<UserHandle> usersOnBroadcasts = switchUserAndWaitForBroadcasts(newUser);
        Log.d(TAG, "users on switch broadcast: " + usersOnBroadcasts);
        assertWithMessage("user on broadcasts").that(usersOnBroadcasts).containsExactly(newUser,
                newUser);
        assertWithMessage("logout user after switch").that(mDevicePolicyManager.getLogoutUser())
                .isEqualTo(currentUser);

        List<UserHandle> users = logoutUserUsingSystemApiAndWaitForBroadcasts();
        Log.d(TAG, "users on logout broadcast: " + users);
        assertWithMessage("users on broadcast").that(users).containsExactly(currentUser);
        assertWithMessage("final logout user").that(mDevicePolicyManager.getLogoutUser())
                .isNull();
    }

    @SuppressWarnings("unused")
    private static void newUserDisclaimer(Context context, DevicePolicyManager dpm,
            ComponentName componentName) {

        // Need to wait until host-side granted INTERACT_ACROSS_USERS - use getCurrentUser() to
        // check
        int currentUserId = UserHandle.USER_NULL;
        long maxAttempts = ON_ENABLED_TIMEOUT_SECONDS;
        int waitingTimeMs = 1_000;
        int attempt = 0;
        int myUserId = context.getUserId();
        do {
            attempt++;
            try {
                Log.d(TAG, "checking if user " + myUserId + " is current user");
                currentUserId = ActivityManager.getCurrentUser();
                Log.d(TAG, "currentUserId: " + currentUserId);
            } catch (SecurityException e) {
                Log.d(TAG, "Got exception (" + e.getMessage() + ") on attempt #" + attempt
                        + ", waiting " + waitingTimeMs + "ms until app is authorized");
                SystemClock.sleep(waitingTimeMs);

            }
        } while (currentUserId != myUserId && attempt < maxAttempts);
        Log.v(TAG, "Out of the loop, let's hope for the best...");

        if (currentUserId == UserHandle.USER_NULL) {
            throw new IllegalStateException("App could was not authorized to check current user");
        }
        assertWithMessage("current user").that(currentUserId).isEqualTo(myUserId);

        // Now that the plumbing is done, go back to work...
        Log.d(TAG, "Calling isNewUserDisclaimerAcknowledged()");
        boolean isAcked = dpm.isNewUserDisclaimerAcknowledged();

        Log.d(TAG, "is it: " + isAcked);
        assertWithMessage("isNewUserDisclaimerAcknowledged()").that(isAcked).isFalse();
        Log.d(TAG, "Calling acknowledgeNewUserDisclaimer()");
        dpm.acknowledgeNewUserDisclaimer();

        Log.d(TAG, "Calling isNewUserDisclaimerAcknowledged() again");
        isAcked = dpm.isNewUserDisclaimerAcknowledged();
        Log.d(TAG, "is it now: " + isAcked);
        assertWithMessage("isNewUserDisclaimerAcknowledged()").that(isAcked).isTrue();
    }

    @SuppressWarnings("unused")
    private static void assertAffiliatedUser(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName) {
        assertWithMessage("affiliated user").that(devicePolicyManager.isAffiliatedUser()).isTrue();
    }

    public void testCreateAndManageUser_Affiliated() throws Exception {
        runCrossUserVerification(/* createAndManageUserFlags= */ 0, "assertAffiliatedUser");
        PrimaryUserService.assertCrossUserCallArrived();
    }

    @SuppressWarnings("unused")
    private static void assertEphemeralUser(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName) {
        assertWithMessage("ephemeral user").that(devicePolicyManager.isEphemeralUser(componentName))
                .isTrue();
    }

    public void testCreateAndManageUser_Ephemeral() throws Exception {
        runCrossUserVerification(DevicePolicyManager.MAKE_USER_EPHEMERAL, "assertEphemeralUser");
        PrimaryUserService.assertCrossUserCallArrived();
    }

    @SuppressWarnings("unused")
    private static void assertAllSystemAppsInstalled(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName,
            Set<String> preInstalledSystemPackages) {
        Log.d(TAG, "assertAllSystemAppsInstalled(): checking apps for user " + context.getUserId());

        PackageManager packageManager = context.getPackageManager();
        // First get a set of installed package names
        Set<String> installedPackageNames = packageManager
                .getInstalledApplications(/* flags= */ 0)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toSet());
        // Then filter all package names by those that are not installed
        Set<String> uninstalledPackageNames = packageManager
                .getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .filter(((Predicate<String>) installedPackageNames::contains).negate())
                .collect(Collectors.toSet());

        // Finally, filter out packages that are not pre-installed for the user
        Iterator<String> iterator = uninstalledPackageNames.iterator();
        while (iterator.hasNext()) {
            String pkg = iterator.next();
            if (!preInstalledSystemPackages.contains(pkg)) {
                Log.i(TAG, "assertAllSystemAppsInstalled(): ignoring package " + pkg
                        + " as it's not pre-installed on current user");
                iterator.remove();
            }
        }

        // Assert that all expected apps are installed
        assertWithMessage("uninstalled system apps").that(uninstalledPackageNames).isEmpty();
    }

    public void testCreateAndManageUser_LeaveAllSystemApps() throws Exception {
        int currentUserId = ActivityManager.getCurrentUser();
        // TODO: instead of hard-coding the user type, calling getPreInstallableSystemPackages(),
        // and passing the packages to runCrossUserVerification() / assertAllSystemAppsInstalled(),
        // ideally the later should call um.getPreInstallableSystemPackages(um.getUsertype())
        // (where um is the UserManager with the context of the newly created user),
        // but currently the list of pre-installed apps is passed to the new user in the bundle.
        // Given that these tests will be refactored anyways, it's not worth to try to change it.
        String newUserType = UserManager.USER_TYPE_FULL_SECONDARY;
        Set<String> preInstalledSystemPackages = SystemUtil.callWithShellPermissionIdentity(
                () -> UserManager.get(mContext).getPreInstallableSystemPackages(newUserType));
        if (preInstalledSystemPackages != null) {
            Log.d(TAG, preInstalledSystemPackages.size() + " pre-installed system apps for "
                    + "new user of type " + newUserType + ": " + preInstalledSystemPackages);
        } else {
            Log.d(TAG, "no pre-installed system apps allowlist for new user of type" + newUserType);
        }

        runCrossUserVerification(/* callback= */ null,
                DevicePolicyManager.LEAVE_ALL_SYSTEM_APPS_ENABLED, "assertAllSystemAppsInstalled",
                preInstalledSystemPackages);
        PrimaryUserService.assertCrossUserCallArrived();
    }

    private UserHandle runCrossUserVerification(int createAndManageUserFlags, String methodName)
            throws Exception {
        return runCrossUserVerification(/* callback= */ null, createAndManageUserFlags, methodName,
                /* currentUserPackages= */ null);
    }

    private UserHandle runCrossUserVerification(UserActionCallback callback,
            int createAndManageUserFlags, String methodName,
            Set<String> currentUserPackages) throws Exception {
        return runCrossUserVerification(callback, createAndManageUserFlags, methodName,
                /* switchUser= */ false, currentUserPackages);
    }

    private UserHandle runCrossUserVerification(UserActionCallback callback,
            int createAndManageUserFlags, String methodName, boolean switchUser,
            Set<String> currentUserPackages) throws Exception {
        Log.d(TAG, "runCrossUserVerification(): flags=" + createAndManageUserFlags
                + ", method=" + methodName);
        String testUserName = "TestUser_" + System.currentTimeMillis();

        // Set affiliation id to allow communication.
        mDevicePolicyManager.setAffiliationIds(getWho(), Collections.singleton(AFFILIATION_ID));

        ComponentName profileOwner = SecondaryUserAdminReceiver.getComponentName(getContext());

        // Pack the affiliation id in a bundle so the secondary user can get it.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(EXTRA_AFFILIATION_ID, AFFILIATION_ID);
        bundle.putString(EXTRA_METHOD_NAME, methodName);
        if (currentUserPackages != null) {
            String[] array = new String[currentUserPackages.size()];
            currentUserPackages.toArray(array);
            bundle.putStringArray(EXTRA_CURRENT_USER_PACKAGES, array);
        }

        Log.d(TAG, "creating user with PO " + profileOwner);

        UserHandle userHandle = createAndManageUser(profileOwner, bundle, createAndManageUserFlags);
        if (switchUser) {
            switchUserAndWaitForBroadcasts(userHandle);
        } else if (callback != null) {
            startUserInBackgroundAndWaitForBroadcasts(callback, userHandle);
        } else {
            startUserInBackgroundAndWaitForBroadcasts(userHandle);
        }
        return userHandle;
    }

    // createAndManageUser should circumvent the DISALLOW_ADD_USER restriction
    public void testCreateAndManageUser_AddRestrictionSet() throws Exception {
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_ADD_USER);

        createAndManageUser();
    }

    public void testCreateAndManageUser_RemoveRestrictionSet() throws Exception {
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);

        UserHandle userHandle = createAndManageUser();

        // When the device owner itself has set the user restriction, it should still be allowed
        // to remove a user.
        List<UserHandle> usersOnBroadcasts = removeUserAndWaitForBroadcasts(userHandle);

        assertWithMessage("user on broadcasts").that(usersOnBroadcasts).containsExactly(userHandle);
    }

    public void testUserAddedOrRemovedBroadcasts() throws Exception {
        UserHandle userHandle = createAndManageUser();

        List<UserHandle> userHandles = removeUserAndWaitForBroadcasts(userHandle);

        assertWithMessage("user on broadcasts").that(userHandles).containsExactly(userHandle);
    }

    private UserHandle createAndManageUser() throws Exception {
        return createAndManageUser(/* flags= */ 0);
    }

    private UserHandle createAndManageUser(int flags) throws Exception {
        return createAndManageUser(/* profileOwner= */ getWho(), /* adminExtras= */ null, flags);
    }

    private UserHandle createAndManageUser(ComponentName profileOwner,
            PersistableBundle adminExtras, int flags) throws Exception {
        String testUserName = "TestUser_" + System.currentTimeMillis();

        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(), BasicAdminReceiver.ACTION_USER_ADDED);

        UserHandle userHandle = callback.callAndUnregisterSelf(() ->
                mDevicePolicyManager.createAndManageUser(
                        /* admin= */ getWho(),
                        testUserName,
                        profileOwner,
                        adminExtras,
                        flags));
        Log.d(TAG, "User '" + testUserName + "' created: " + userHandle);
        return userHandle;
    }

    /**
     * Switches to the given user, or fails if the user could not be switched or if the expected
     * broadcasts were not received in time.
     *
     * @return users received in the broadcasts
     */
    private List<UserHandle> switchUserAndWaitForBroadcasts(UserHandle userHandle)
            throws Exception {
        Log.d(TAG, "Switching to user " + userHandle);

        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(),
                BasicAdminReceiver.ACTION_USER_STARTED, BasicAdminReceiver.ACTION_USER_SWITCHED);

        callback.runAndUnregisterSelf(() -> {
            Log.d(TAG, "Calling switchUser() on callback");
            boolean switched = mDevicePolicyManager.switchUser(getWho(), userHandle);
            Log.d(TAG, "Switched: " + switched);
            assertWithMessage("switched to user %s", userHandle).that(switched).isTrue();
        });
        return callback.getUsersOnReceivedBroadcasts();
    }

    /**
     * Logouts the current user using {@link DevicePolicyManager#logoutUser()}, or fails if the
     * user could not be logged out or if the expected broadcasts were not received in time.
     *
     * @return users received in the broadcasts
     */
    private List<UserHandle> logoutUserUsingSystemApiAndWaitForBroadcasts()
            throws Exception {
        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(), BasicAdminReceiver.ACTION_USER_SWITCHED);
        Log.d(TAG, "Logging out current user (" + getCurrentUser() + ") using system API");

        callback.runAndUnregisterSelf(() -> {
            int result = SystemUtil
                    .callWithShellPermissionIdentity(() -> mDevicePolicyManager.logoutUser());
            Log.d(TAG, "Result: " + userOperationResultToString(result));
            assertUserOperationResult(result, USER_OPERATION_SUCCESS, "logout user");
        });
        return callback.getUsersOnReceivedBroadcasts();
    }

    /**
     * Removes the given user, or fails if the user could not be removed or if the expected
     * broadcasts were not received in time.
     *
     * @return users received in the broadcasts
     */
    private List<UserHandle> removeUserAndWaitForBroadcasts(UserHandle userHandle)
            throws Exception {
        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(), BasicAdminReceiver.ACTION_USER_REMOVED);

        callback.runAndUnregisterSelf(() -> {
            boolean removed = mDevicePolicyManager.removeUser(getWho(), userHandle);
            assertWithMessage("removed user %s", userHandle).that(removed).isTrue();
        });

        return callback.getUsersOnReceivedBroadcasts();
    }

    private static String userOperationResultToString(int result) {
        return DebugUtils.constantToString(UserManager.class, "USER_OPERATION_", result);
    }

    private static void assertUserOperationResult(int actualResult, int expectedResult,
            String operationFormat, Object... operationArgs) {
        String operation = String.format(operationFormat, operationArgs);
        assertWithMessage("result for %s (%s instead of %s)", operation,
                userOperationResultToString(actualResult),
                userOperationResultToString(expectedResult))
                        .that(actualResult).isEqualTo(expectedResult);
    }

    private void startUserInBackgroundAndCheckResult(UserHandle userHandle, int expectedResult) {
        int actualResult = mDevicePolicyManager.startUserInBackground(getWho(), userHandle);
        assertUserOperationResult(actualResult, expectedResult, "starting user %s in background",
                userHandle);
    }

    /**
     * Starts the given user in background, or fails if the user could not be started or if the
     * expected broadcasts were not received in time.
     *
     * @return users received in the broadcasts
     */
    private List<UserHandle> startUserInBackgroundAndWaitForBroadcasts(UserHandle userHandle)
            throws Exception {
        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(), BasicAdminReceiver.ACTION_USER_STARTED);
        return startUserInBackgroundAndWaitForBroadcasts(callback, userHandle);
    }

    private List<UserHandle> startUserInBackgroundAndWaitForBroadcasts(UserActionCallback callback,
            UserHandle userHandle) throws Exception {
        callback.runAndUnregisterSelf(
                () -> startUserInBackgroundAndCheckResult(userHandle, USER_OPERATION_SUCCESS));
        return callback.getUsersOnReceivedBroadcasts();
    }

    private void stopUserAndCheckResult(UserHandle userHandle, int expectedResult) {
        int actualResult = mDevicePolicyManager.stopUser(getWho(), userHandle);
        assertUserOperationResult(actualResult, expectedResult, "stopping user %s", userHandle);
    }

    /**
     * Stops the given user, or fails if the user could not be stop or if the expected broadcasts
     * were not received in time.
     *
     * @return users received in the broadcasts
     */
    private List<UserHandle> stopUserAndWaitForBroadcasts(UserHandle userHandle) throws Exception {
        UserActionCallback callback = UserActionCallback.getCallbackForBroadcastActions(
                getContext(), BasicAdminReceiver.ACTION_USER_STOPPED);
        callback.runAndUnregisterSelf(
                () -> stopUserAndCheckResult(userHandle, USER_OPERATION_SUCCESS));
        return callback.getUsersOnReceivedBroadcasts();
    }

    public static final class PrimaryUserService extends Service {
        private static final Semaphore sSemaphore = new Semaphore(0);
        private static String sError;

        private final ICrossUserService.Stub mBinder = new ICrossUserService.Stub() {
            public void onEnabledCalled(String error) {
                Log.d(TAG, "PrimaryUserService.onEnabledCalled() on user "
                        + getApplicationContext().getUserId() + " with error " + error);
                sError = error;
                sSemaphore.release();
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            Log.d(TAG, "PrimaryUserService.onBind() on user "
                    + getApplicationContext().getUserId() + ": " + intent);
            return mBinder;
        }

        static void assertCrossUserCallArrived() throws Exception {
            Log.v(TAG, "assertCrossUserCallArrived(): waiting " + ON_ENABLED_TIMEOUT_SECONDS
                    + " seconds for callback");
            assertWithMessage("cross-user call arrived in %ss", ON_ENABLED_TIMEOUT_SECONDS)
                    .that(sSemaphore.tryAcquire(ON_ENABLED_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
            if (sError != null) {
                Log.e(TAG, "assertCrossUserCallArrived() had error: " + sError);
                throw new Exception(sError);
            }
        }
    }

    public static final class SecondaryUserAdminReceiver extends DeviceAdminReceiver {
        @Override
        public void onEnabled(Context context, Intent intent) {
            Log.d(TAG, "SecondaryUserAdminReceiver.onEnabled() called on user "
                    + context.getUserId() + " and thread " + Thread.currentThread());

            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            ComponentName who = getComponentName(context);

            // Set affiliation ids
            Set<String> ids = Collections.singleton(intent.getStringExtra(EXTRA_AFFILIATION_ID));
            Log.d(TAG, "setting affiliation ids as " + ids);
            dpm.setAffiliationIds(who, ids);

            String error = null;
            try {
                Method method;
                if (intent.hasExtra(EXTRA_CURRENT_USER_PACKAGES)) {
                    method = CreateAndManageUserTest.class.getDeclaredMethod(
                            intent.getStringExtra(EXTRA_METHOD_NAME), Context.class,
                            DevicePolicyManager.class, ComponentName.class, Set.class);
                } else {
                    method = CreateAndManageUserTest.class.getDeclaredMethod(
                            intent.getStringExtra(EXTRA_METHOD_NAME), Context.class,
                            DevicePolicyManager.class, ComponentName.class);
                }
                method.setAccessible(true);
                Log.d(TAG, "Calling method " + method);
                if (intent.hasExtra(EXTRA_CURRENT_USER_PACKAGES)) {
                    String[] pkgsArray = intent.getStringArrayExtra(EXTRA_CURRENT_USER_PACKAGES);
                    Set<String> pkgs = new HashSet<>(pkgsArray.length);
                    for (String pkg : pkgsArray) {
                        pkgs.add(pkg);
                    }
                    method.invoke(null, context, dpm, who, pkgs);
                } else {
                    method.invoke(null, context, dpm, who);
                }
            } catch (NoSuchMethodException | IllegalAccessException e) {
                error = e.toString();
            } catch (InvocationTargetException e) {
                error = e.getCause().toString();
            }
            if (error != null) {
                Log.e(TAG, "Error calling method: " + error);
            }

            // Call all affiliated users
            final List<UserHandle> targetUsers = dpm.getBindDeviceAdminTargetUsers(who);
            Log.d(TAG, "target users: " + targetUsers);
            assertWithMessage("target users").that(targetUsers).hasSize(1);

            pingTargetUser(context, dpm, targetUsers.get(0), error);
        }

        private void pingTargetUser(Context context, DevicePolicyManager dpm,
                UserHandle target, String error) {
            Log.d(TAG, "Pinging target " + target + " with error " + error);
            final ServiceConnection serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "onServiceConnected() is called in " + Thread.currentThread());
                    ICrossUserService crossUserService = ICrossUserService
                            .Stub.asInterface(service);
                    try {
                        crossUserService.onEnabledCalled(error);
                    } catch (RemoteException re) {
                        Log.e(TAG, "Error when calling primary user", re);
                        // Do nothing, primary user will time out
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "onServiceDisconnected() is called");
                }
            };
            Intent serviceIntent = new Intent(context, PrimaryUserService.class);
            boolean bound = dpm.bindDeviceAdminServiceAsUser(
                    getComponentName(context),
                    serviceIntent,
                    serviceConnection,
                    Context.BindServiceFlags.of(Context.BIND_AUTO_CREATE),
                    target);
            assertWithMessage("bound to user %s using intent %s", target, serviceIntent).that(bound)
                    .isTrue();
        }

        public static ComponentName getComponentName(Context context) {
            return new ComponentName(context, SecondaryUserAdminReceiver.class);
        }
    }
}
