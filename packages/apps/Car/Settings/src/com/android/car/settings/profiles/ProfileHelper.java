/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.car.settings.profiles;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.user.CarUserManager;
import android.car.user.OperationResult;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.UserHandle;
import android.os.UserManager;
import android.sysprop.CarProperties;
import android.util.Log;
import android.widget.Toast;

import com.android.car.internal.user.UserHelper;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class for providing basic profile logic that applies across the Settings app for Cars.
 */
public class ProfileHelper {
    private static final String TAG = "ProfileHelper";
    private static final int TIMEOUT_MS = CarProperties.user_hal_timeout().orElse(5_000) + 500;
    private static ProfileHelper sInstance;

    private final UserManager mUserManager;
    private final CarUserManager mCarUserManager;
    private final Resources mResources;
    private final String mDefaultAdminName;
    private final String mDefaultGuestName;

    /**
     * Result code for when a profile was successfully marked for removal and the
     * device switched to a different profile.
     */
    public static final int REMOVE_PROFILE_RESULT_SUCCESS = 0;

    /**
     * Result code for when there was a failure removing a profile.
     */
    public static final int REMOVE_PROFILE_RESULT_FAILED = 1;

    /**
     * Result code when the profile was successfully marked for removal, but the switch to a new
     * profile failed. In this case the profile marked for removal is set as ephemeral and will be
     * removed on the next profile switch or reboot.
     */
    public static final int REMOVE_PROFILE_RESULT_SWITCH_FAILED = 2;

    /**
     * Possible return values for {@link #removeProfile(int)}, which attempts to remove a profile
     * and switch to a new one. Note that this IntDef is distinct from {@link UserRemovalResult},
     * which is only a result code for the profile removal operation.
     */
    @IntDef(prefix = {"REMOVE_PROFILE_RESULT"}, value = {
            REMOVE_PROFILE_RESULT_SUCCESS,
            REMOVE_PROFILE_RESULT_FAILED,
            REMOVE_PROFILE_RESULT_SWITCH_FAILED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RemoveProfileResult {
    }

    /**
     * Returns an instance of ProfileHelper.
     */
    public static ProfileHelper getInstance(Context context) {
        if (sInstance == null) {
            Context appContext = context.getApplicationContext();
            Resources resources = appContext.getResources();
            sInstance = new ProfileHelper(
                    appContext.getSystemService(UserManager.class), resources,
                    resources.getString(com.android.internal.R.string.owner_name),
                    resources.getString(com.android.internal.R.string.guest_name),
                    getCarUserManager(appContext));
        }
        return sInstance;
    }

    @VisibleForTesting
    ProfileHelper(UserManager userManager, Resources resources, String defaultAdminName,
            String defaultGuestName, CarUserManager carUserManager) {
        mUserManager = userManager;
        mResources = resources;
        mDefaultAdminName = defaultAdminName;
        mDefaultGuestName = defaultGuestName;
        mCarUserManager = carUserManager;
    }

    private static CarUserManager getCarUserManager(@NonNull Context context) {
        Car car = Car.createCar(context);
        CarUserManager carUserManager = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);
        return carUserManager;
    }

    /**
     * Tries to remove the profile that's passed in. System profile cannot be removed.
     * If the profile to be removed is profile currently running the process, it switches to the
     * guest profile first, and then removes the profile.
     * If the profile being removed is the last admin profile, this will create a new admin profile.
     *
     * @param context  An application context
     * @param userInfo Profile to be removed
     * @return {@link RemoveProfileResult} indicating the result status for profile removal and
     * switching
     */
    @RemoveProfileResult
    public int removeProfile(Context context, UserInfo userInfo) {
        if (userInfo.id == UserHandle.USER_SYSTEM) {
            Log.w(TAG, "User " + userInfo.id + " is system user, could not be removed.");
            return REMOVE_PROFILE_RESULT_FAILED;
        }

        // Try to create a new admin before deleting the current one.
        if (userInfo.isAdmin() && getAllAdminProfiles().size() <= 1) {
            return replaceLastAdmin(context, userInfo);
        }

        if (!mUserManager.isAdminUser() && !isCurrentProcessUser(userInfo)) {
            // If the caller is non-admin, they can only delete themselves.
            Log.e(TAG, "Non-admins cannot remove other profiles.");
            return REMOVE_PROFILE_RESULT_FAILED;
        }

        if (userInfo.id == ActivityManager.getCurrentUser()) {
            return removeThisProfileAndSwitchToGuest(context, userInfo);
        }

        return removeProfile(userInfo.id);
    }

    /**
     * If the ID being removed is the current foreground profile, we need to handle switching to
     * a new or existing guest.
     */
    @RemoveProfileResult
    private int removeThisProfileAndSwitchToGuest(Context context, UserInfo userInfo) {
        if (mUserManager.getUserSwitchability() != UserManager.SWITCHABILITY_STATUS_OK) {
            // If we can't switch to a different profile, we can't exit this one and therefore
            // can't delete it.
            Log.w(TAG, "Profile switching is not allowed. Current profile cannot be deleted");
            return REMOVE_PROFILE_RESULT_FAILED;
        }
        UserInfo guestUser = createNewOrFindExistingGuest(context);
        if (guestUser == null) {
            Log.e(TAG, "Could not create a Guest profile.");
            return REMOVE_PROFILE_RESULT_FAILED;
        }

        // since the profile is still current, this will set it as ephemeral
        int result = removeProfile(userInfo.id);
        if (result != REMOVE_PROFILE_RESULT_SUCCESS) {
            return result;
        }

        if (!switchProfile(guestUser.id)) {
            return REMOVE_PROFILE_RESULT_SWITCH_FAILED;
        }

        return REMOVE_PROFILE_RESULT_SUCCESS;
    }

    @RemoveProfileResult
    private int removeProfile(@UserIdInt int userId) {
        UserRemovalResult result = mCarUserManager.removeUser(userId);
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "Remove profile result: " + result);
        }
        if (result.isSuccess()) {
            return REMOVE_PROFILE_RESULT_SUCCESS;
        } else {
            Log.w(TAG, "Failed to remove profile " + userId + ": " + result);
            return REMOVE_PROFILE_RESULT_FAILED;
        }
    }

    /**
     * Switches to the given profile.
     */
    // TODO(b/186905050, b/205185521): add unit / robo test
    public boolean switchProfile(@UserIdInt int userId) {
        Log.i(TAG, "Switching to profile / user " + userId);

        UserSwitchResult result = getResult("switch", mCarUserManager.switchUser(userId));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Result: " + result);
        }
        return result != null && result.isSuccess();
    }

    /**
     * Logs out the given profile (which must have been switched to by a device admin).
     */
    // TODO(b/186905050, b/214336184): add unit / robo test
    public boolean logoutProfile() {
        Log.i(TAG, "Logging out current profile");

        UserSwitchResult result = getResult("logout", mCarUserManager.logoutUser());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Result: " + result);
        }
        return result != null && result.isSuccess();
    }

    /**
     * Returns the {@link StringRes} that corresponds to a {@link RemoveProfileResult} result code.
     */
    @StringRes
    public int getErrorMessageForProfileResult(@RemoveProfileResult int result) {
        if (result == REMOVE_PROFILE_RESULT_SWITCH_FAILED) {
            return R.string.delete_user_error_set_ephemeral_title;
        }

        return R.string.delete_user_error_title;
    }

    /**
     * Gets the result of an async operation.
     *
     * @param operation name of the operation, to be logged in case of error
     * @param future    future holding the operation result.
     * @return result of the operation or {@code null} if it failed or timed out.
     */
    @Nullable
    private static <T extends OperationResult> T getResult(String operation,
            AsyncFuture<T> future) {
        T result = null;
        try {
            result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted waiting to " + operation + " profile", e);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            Log.w(TAG, "Exception waiting to " + operation + " profile", e);
            return null;
        }
        if (result == null) {
            Log.w(TAG, "Time out (" + TIMEOUT_MS + " ms) trying to " + operation + " profile");
            return null;
        }
        if (!result.isSuccess()) {
            Log.w(TAG, "Failed to " + operation + " profile: " + result);
            return null;
        }
        return result;
    }

    @RemoveProfileResult
    private int replaceLastAdmin(Context context, UserInfo userInfo) {
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "Profile " + userInfo.id
                    + " is the last admin profile on device. Creating a new admin.");
        }

        UserInfo newAdmin = createNewAdminProfile(context, mDefaultAdminName);
        if (newAdmin == null) {
            Log.w(TAG, "Couldn't create another admin, cannot delete current profile.");
            return REMOVE_PROFILE_RESULT_FAILED;
        }

        int removeUserResult = removeProfile(userInfo.id);
        if (removeUserResult != REMOVE_PROFILE_RESULT_SUCCESS) {
            return removeUserResult;
        }

        if (switchProfile(newAdmin.id)) {
            return REMOVE_PROFILE_RESULT_SUCCESS;
        } else {
            return REMOVE_PROFILE_RESULT_SWITCH_FAILED;
        }
    }

    /**
     * Creates a new profile on the system, the created profile would be granted admin role.
     * Only admins can create other admins.
     *
     * @param userName Name to give to the newly created profile.
     * @return Newly created admin profile, null if failed to create a profile.
     */
    @Nullable
    private UserInfo createNewAdminProfile(Context context, String userName) {
        if (!(mUserManager.isAdminUser() || mUserManager.isSystemUser())) {
            // Only Admins or System profile can create other privileged profiles.
            Log.e(TAG, "Only admin profiles and system profile can create other admins.");
            return null;
        }
        UserCreationResult result = getResult("create admin",
                mCarUserManager.createUser(userName, UserInfo.FLAG_ADMIN));
        if (result == null) return null;
        UserInfo user = mUserManager.getUserInfo(result.getUser().getIdentifier());

        UserHelper.assignDefaultIcon(context, user.getUserHandle());
        return user;
    }

    /**
     * Creates and returns a new guest profile or returns the existing one.
     * Returns null if it fails to create a new guest.
     *
     * @param context an application context
     * @return The UserInfo representing the Guest, or null if it failed
     */
    @Nullable
    public UserInfo createNewOrFindExistingGuest(Context context) {
        // createGuest() will return null if a guest already exists.
        UserCreationResult result = getResult("create guest",
                mCarUserManager.createGuest(mDefaultGuestName));
        UserInfo newGuest = result == null ? null
                : mUserManager.getUserInfo(result.getUser().getIdentifier());

        if (newGuest != null) {
            UserHelper.assignDefaultIcon(context, newGuest.getUserHandle());
            return newGuest;
        }

        return mUserManager.findCurrentGuestUser();
    }

    /**
     * Checks if the current process profile can modify accounts. Demo and Guest profiles cannot
     * modify accounts even if the DISALLOW_MODIFY_ACCOUNTS restriction is not applied.
     */
    public boolean canCurrentProcessModifyAccounts() {
        return !mUserManager.hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS)
                && !isDemoOrGuest();
    }

    /**
     * Checks if the current process is demo or guest user.
     */
    public boolean isDemoOrGuest() {
        return mUserManager.isDemoUser() || mUserManager.isGuestUser();
    }

    /**
     * Returns a list of {@code UserInfo} representing all profiles that can be brought to the
     * foreground.
     */
    public List<UserInfo> getAllProfiles() {
        return getAllLivingProfiles(/* filter= */ null);
    }

    /**
     * Returns a list of {@code UserInfo} representing all profiles that can be swapped with the
     * current profile into the foreground.
     */
    public List<UserInfo> getAllSwitchableProfiles() {
        final int foregroundUserId = ActivityManager.getCurrentUser();
        return getAllLivingProfiles(userInfo -> userInfo.id != foregroundUserId);
    }

    /**
     * Returns a list of {@code UserInfo} representing all profiles that are non-ephemeral and are
     * valid to have in the foreground.
     */
    public List<UserInfo> getAllPersistentProfiles() {
        return getAllLivingProfiles(userInfo -> !userInfo.isEphemeral());
    }

    /**
     * Returns a list of {@code UserInfo} representing all admin profiles and are
     * valid to have in the foreground.  Note that ephemeral users are excluded from the results.
     */
    public List<UserInfo> getAllAdminProfiles() {
        return getAllLivingProfiles(userInfo -> (userInfo.isAdmin() && !userInfo.isEphemeral()));
    }

    /**
     * Gets all profiles that are not dying.  This method will handle
     * {@link UserManager#isHeadlessSystemUserMode} and ensure the system profile is not
     * part of the return list when the flag is on.
     * @param filter Optional filter to apply to the list of profiles.  Pass null to skip.
     * @return An optionally filtered list containing all living profiles
     */
    public List<UserInfo> getAllLivingProfiles(@Nullable Predicate<? super UserInfo> filter) {
        Stream<UserInfo> filteredListStream = mUserManager.getAliveUsers().stream();

        if (filter != null) {
            filteredListStream = filteredListStream.filter(filter);
        }

        if (UserManager.isHeadlessSystemUserMode()) {
            filteredListStream =
                    filteredListStream.filter(userInfo -> userInfo.id != UserHandle.USER_SYSTEM);
        }
        filteredListStream = filteredListStream.sorted(
                (u1, u2) -> Long.signum(u1.creationTime - u2.creationTime));
        return filteredListStream.collect(Collectors.toList());
    }

    /**
     * Checks whether passed in user is the user that's running the current process.
     *
     * @param userInfo User to check.
     * @return {@code true} if user running the process, {@code false} otherwise.
     */
    public boolean isCurrentProcessUser(UserInfo userInfo) {
        return UserHandle.myUserId() == userInfo.id;
    }

    /**
     * Gets UserInfo for the user running the caller process.
     *
     * <p>Differentiation between foreground user and current process user is relevant for
     * multi-user deployments.
     *
     * <p>Some multi-user aware components (like SystemUI) needs to run a singleton component
     * in system user. Current process user is always the same for that component, even when
     * the foreground user changes.
     *
     * @return {@link UserInfo} for the user running the current process.
     */
    public UserInfo getCurrentProcessUserInfo() {
        return mUserManager.getUserInfo(UserHandle.myUserId());
    }

    /**
     * Maximum number of profiles allowed on the device. This includes real profiles, managed
     * profiles and restricted profiles, but excludes guests.
     *
     * <p> It excludes system profile in headless system profile model.
     *
     * @return Maximum number of profiles that can be present on the device.
     */
    private int getMaxSupportedProfiles() {
        int maxSupportedUsers = UserManager.getMaxSupportedUsers();
        if (UserManager.isHeadlessSystemUserMode()) {
            maxSupportedUsers -= 1;
        }
        return maxSupportedUsers;
    }

    private int getManagedProfilesCount() {
        List<UserInfo> users = getAllProfiles();

        // Count all users that are managed profiles of another user.
        int managedProfilesCount = 0;
        for (UserInfo user : users) {
            if (user.isManagedProfile()) {
                managedProfilesCount++;
            }
        }
        return managedProfilesCount;
    }

    /**
     * Gets the maximum number of real (non-guest, non-managed profile) profiles that can be created
     * on the device. This is a dynamic value and it decreases with the increase of the number of
     * managed profiles on the device.
     *
     * <p> It excludes system profile in headless system profile model.
     *
     * @return Maximum number of real profiles that can be created.
     */
    public int getMaxSupportedRealProfiles() {
        return getMaxSupportedProfiles() - getManagedProfilesCount();
    }

    /**
     * When the Preference is disabled while still visible, {@code ActionDisabledByAdminDialog}
     * should be shown when the action is disallowed by a device owner or a profile owner.
     * Otherwise, a {@code Toast} will be shown to inform the user that the action is disabled.
     */
    public static void runClickableWhileDisabled(Context context,
            FragmentController fragmentController) {
        if (hasUserRestrictionByDpm(context, UserManager.DISALLOW_MODIFY_ACCOUNTS)) {
            showActionDisabledByAdminDialog(context, fragmentController);
        } else {
            Toast.makeText(context, context.getString(R.string.action_unavailable),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void showActionDisabledByAdminDialog(Context context,
            FragmentController fragmentController) {
        fragmentController.showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(context,
                        UserManager.DISALLOW_MODIFY_ACCOUNTS),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }

    /**
     * Checks whether the current user has acknowledged the new user disclaimer.
     */
    public static boolean isNewUserDisclaimerAcknolwedged(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        return dpm.isNewUserDisclaimerAcknowledged();
    }
}
