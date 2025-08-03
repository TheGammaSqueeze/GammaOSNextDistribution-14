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
package com.android.systemui.car.qc;

import static android.os.UserManager.SWITCHABILITY_STATUS_OK;
import static android.provider.Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.car.ui.utils.CarUiUtils.drawableToBitmap;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.SyncResultCallback;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserStartRequest;
import android.car.user.UserStopRequest;
import android.car.user.UserStopResponse;
import android.car.user.UserSwitchRequest;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.sysprop.CarProperties;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.car.internal.user.UserHelper;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.internal.util.UserIcons;
import com.android.settingslib.utils.StringUtil;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.settings.UserTracker;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Local provider for the profile switcher panel.
 */
public class ProfileSwitcher extends BaseLocalQCProvider {
    private static final String TAG = ProfileSwitcher.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int TIMEOUT_MS = CarProperties.user_hal_timeout().orElse(5_000) + 500;

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    if (DEBUG) {
                        Log.d(TAG, "car connected");
                    }
                    mCarUserManager = car.getCarManager(CarUserManager.class);
                    notifyChange();
                }
            };

    protected final UserTracker mUserTracker;
    protected final UserIconProvider mUserIconProvider;
    private final UserManager mUserManager;
    private final DevicePolicyManager mDevicePolicyManager;
    public final Handler mHandler;
    private final CarServiceProvider mCarServiceProvider;
    @Nullable
    private CarUserManager mCarUserManager;
    protected boolean mPendingUserAdd;

    @Inject
    public ProfileSwitcher(Context context, UserTracker userTracker,
            CarServiceProvider carServiceProvider, @Background Handler handler) {
        super(context);
        mUserTracker = userTracker;
        mUserManager = context.getSystemService(UserManager.class);
        mDevicePolicyManager = context.getSystemService(DevicePolicyManager.class);
        mUserIconProvider = new UserIconProvider();
        mHandler = handler;
        mCarServiceProvider = carServiceProvider;
        mCarServiceProvider.addListener(mCarServiceOnConnectedListener);
    }

    @VisibleForTesting
    ProfileSwitcher(Context context, UserTracker userTracker, UserManager userManager,
            DevicePolicyManager devicePolicyManager, CarUserManager carUserManager,
            UserIconProvider userIconProvider, Handler handler) {
        super(context);
        mUserTracker = userTracker;
        mUserManager = userManager;
        mDevicePolicyManager = devicePolicyManager;
        mUserIconProvider = userIconProvider;
        mCarUserManager = carUserManager;
        mCarServiceProvider = null;
        mHandler = handler;
    }

    @Override
    public QCItem getQCItem() {
        if (mCarUserManager == null) {
            return null;
        }
        QCList.Builder listBuilder = new QCList.Builder();

        if (mDevicePolicyManager.isDeviceManaged()
                || mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()) {
            listBuilder.addRow(createOrganizationOwnedDeviceRow());
        }

        boolean isLogoutEnabled = mDevicePolicyManager.isLogoutEnabled()
                && mDevicePolicyManager.getLogoutUser() != null;

        int fgUserId = mUserTracker.getUserId();
        UserHandle fgUserHandle = UserHandle.of(fgUserId);
        // If the foreground user CANNOT switch to other users, only display the foreground user.
        if (mUserManager.getUserSwitchability(fgUserHandle) != SWITCHABILITY_STATUS_OK) {
            UserInfo currentUser = mUserManager.getUserInfo(mUserTracker.getUserId());
            listBuilder.addRow(createUserProfileRow(currentUser));
            if (isLogoutEnabled) {
                listBuilder.addRow(createLogOutRow());
            }
            return listBuilder.build();
        }

        List<UserInfo> profiles = getProfileList();
        for (UserInfo profile : profiles) {
            listBuilder.addRow(createUserProfileRow(profile));
        }
        listBuilder.addRow(createGuestProfileRow());
        if (!hasAddUserRestriction(fgUserHandle)) {
            listBuilder.addRow(createAddProfileRow());
        }

        if (isLogoutEnabled) {
            listBuilder.addRow(createLogOutRow());
        }
        return listBuilder.build();
    }

    @Override
    public void onDestroy() {
        if (mCarServiceProvider != null) {
            mCarServiceProvider.removeListener(mCarServiceOnConnectedListener);
        }
    }

    private List<UserInfo> getProfileList() {
        return mUserManager.getAliveUsers()
                .stream()
                .filter(userInfo -> userInfo.supportsSwitchTo() && userInfo.isFull()
                        && !userInfo.isGuest())
                .sorted((u1, u2) -> Long.signum(u1.creationTime - u2.creationTime))
                .collect(Collectors.toList());
    }

    private QCRow createOrganizationOwnedDeviceRow() {
        Icon icon = Icon.createWithBitmap(
                drawableToBitmap(mContext.getDrawable(R.drawable.car_ic_managed_device)));
        QCRow row = new QCRow.Builder()
                .setIcon(icon)
                .setSubtitle(mContext.getString(R.string.do_disclosure_generic))
                .build();
        row.setActionHandler(new QCItem.ActionHandler() {
            @Override
            public void onAction(@NonNull QCItem item, @NonNull Context context,
                    @NonNull Intent intent) {
                mContext.startActivityAsUser(new Intent(ACTION_ENTERPRISE_PRIVACY_SETTINGS),
                        mUserTracker.getUserHandle());
            }

            @Override
            public boolean isActivity() {
                return true;
            }
        });
        return row;
    }

    protected QCRow createUserProfileRow(UserInfo userInfo) {
        QCItem.ActionHandler actionHandler = (item, context, intent) -> {
            if (mPendingUserAdd) {
                return;
            }
            switchUser(userInfo.id);
        };

        return createProfileRow(userInfo.name,
                mUserIconProvider.getDrawableWithBadge(mContext, userInfo), actionHandler);
    }

    protected QCRow createGuestProfileRow() {
        QCItem.ActionHandler actionHandler = (item, context, intent) -> {
            if (mPendingUserAdd) {
                return;
            }
            UserInfo guest = createNewOrFindExistingGuest(mContext);
            if (guest != null) {
                switchUser(guest.id);
            }
        };

        return createProfileRow(mContext.getString(com.android.internal.R.string.guest_name),
                mUserIconProvider.getRoundedGuestDefaultIcon(mContext),
                actionHandler);
    }

    private QCRow createAddProfileRow() {
        QCItem.ActionHandler actionHandler = (item, context, intent) -> {
            if (mPendingUserAdd) {
                return;
            }
            if (!mUserManager.canAddMoreUsers()) {
                showMaxUserLimitReachedDialog();
            } else {
                showConfirmAddUserDialog();
            }
        };

        return createProfileRow(mContext.getString(R.string.car_add_user),
                mUserIconProvider.getDrawableWithBadge(mContext, getCircularAddUserIcon()),
                actionHandler);
    }

    private QCRow createLogOutRow() {
        QCRow row = new QCRow.Builder()
                .setIcon(Icon.createWithResource(mContext, R.drawable.car_ic_logout))
                .setTitle(mContext.getString(R.string.end_session))
                .build();
        row.setActionHandler((item, context, intent) -> logoutUser());
        return row;
    }

    private QCRow createProfileRow(String title, Drawable iconDrawable,
            QCItem.ActionHandler actionHandler) {
        Icon icon = Icon.createWithBitmap(drawableToBitmap(iconDrawable));
        QCRow row = new QCRow.Builder()
                .setIcon(icon)
                .setIconTintable(false)
                .setTitle(title)
                .build();
        row.setActionHandler(actionHandler);
        return row;
    }

    protected void switchUser(@UserIdInt int userId) {
        mContext.sendBroadcastAsUser(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                mUserTracker.getUserHandle());
        if (mUserTracker.getUserId() == userId) {
            return;
        }
        if (mUserManager.isVisibleBackgroundUsersSupported()) {
            if (mUserManager.getVisibleUsers().stream().anyMatch(
                    userHandle -> userHandle.getIdentifier() == userId)) {
                // TODO_MD - finalize behavior for non-switchable users
                Toast.makeText(mContext,
                        "Cannot switch to user already running on another display.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
                switchSecondaryUser(userId);
                return;
            }
        }
        switchForegroundUser(userId);
    }

    private void switchForegroundUser(@UserIdInt int userId) {
        // Switch user in the background thread to avoid ANR in UI thread.
        mHandler.post(() -> {
            UserSwitchResult userSwitchResult = null;
            try {
                SyncResultCallback<UserSwitchResult> userSwitchCallback =
                        new SyncResultCallback<>();
                mCarUserManager.switchUser(
                        new UserSwitchRequest.Builder(UserHandle.of(userId)).build(),
                        Runnable::run, userSwitchCallback);
                userSwitchResult = userSwitchCallback.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.w(TAG, "Exception while switching to the user " + userId, e);
            }
            if (userSwitchResult == null || !userSwitchResult.isSuccess()) {
                Log.w(TAG, "Could not switch user: " + userSwitchResult);
            }
        });
    }

    private void switchSecondaryUser(@UserIdInt int userId) {
        // Switch user in the background thread to avoid ANR in UI thread.
        mHandler.post(() -> {
            try {
                SyncResultCallback<UserStopResponse> userStopCallback = new SyncResultCallback<>();
                mCarUserManager.stopUser(new UserStopRequest.Builder(
                                mUserTracker.getUserHandle()).setForce().build(),
                        Runnable::run, userStopCallback);
                UserStopResponse userStopResponse =
                        userStopCallback.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!userStopResponse.isSuccess()) {
                    Log.w(TAG, "Could not stop user " + mUserTracker.getUserId() + ". Response: "
                            + userStopResponse);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Exception while stopping user " + mUserTracker.getUserId(), e);
                return;
            }

            int displayId = mContext.getDisplayId();
            try {
                mCarUserManager.startUser(
                        new UserStartRequest.Builder(UserHandle.of(userId)).setDisplayId(
                                displayId).build(),
                        Runnable::run,
                        response -> {
                            if (!response.isSuccess()) {
                                Log.e(TAG, "Could not start user " + userId + " on display "
                                        + displayId + ". Response: " + response);
                            }
                        });
            } catch (Exception e) {
                Log.w(TAG, "Exception while starting user " + userId + " on display " + displayId,
                        e);
            }
        });
    }

    private void logoutUser() {
        mContext.sendBroadcastAsUser(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                mUserTracker.getUserHandle());
        AsyncFuture<UserSwitchResult> userSwitchResultFuture = mCarUserManager.logoutUser();
        UserSwitchResult userSwitchResult;
        try {
            userSwitchResult = userSwitchResultFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.w(TAG, "Could not log out user.", e);
            return;
        }
        if (userSwitchResult == null) {
            Log.w(TAG, "Timed out while logging out user: " + TIMEOUT_MS + "ms");
        } else if (!userSwitchResult.isSuccess()) {
            Log.w(TAG, "Could not log out user: " + userSwitchResult);
        }
    }

    /**
     * Finds the existing Guest user, or creates one if it doesn't exist.
     *
     * @param context App context
     * @return UserInfo representing the Guest user
     */
    @Nullable
    protected UserInfo createNewOrFindExistingGuest(Context context) {
        AsyncFuture<UserCreationResult> future = mCarUserManager.createGuest(
                context.getString(com.android.internal.R.string.guest_name));
        // CreateGuest will return null if a guest already exists.
        UserInfo newGuest = getUserInfo(future);
        if (newGuest != null) {
            UserHelper.assignDefaultIcon(context, newGuest.getUserHandle());
            return newGuest;
        }
        return mUserManager.findCurrentGuestUser();
    }

    @Nullable
    private UserInfo getUserInfo(AsyncFuture<UserCreationResult> future) {
        UserCreationResult userCreationResult;
        try {
            userCreationResult = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.w(TAG, "Could not create user.", e);
            return null;
        }
        if (userCreationResult == null) {
            Log.w(TAG, "Timed out while creating user: " + TIMEOUT_MS + "ms");
            return null;
        }
        if (!userCreationResult.isSuccess() || userCreationResult.getUser() == null) {
            Log.w(TAG, "Could not create user: " + userCreationResult);
            return null;
        }
        return mUserManager.getUserInfo(userCreationResult.getUser().getIdentifier());
    }

    private RoundedBitmapDrawable getCircularAddUserIcon() {
        RoundedBitmapDrawable circleIcon = RoundedBitmapDrawableFactory.create(
                mContext.getResources(),
                UserIcons.convertToBitmap(mContext.getDrawable(R.drawable.car_add_circle_round)));
        circleIcon.setCircular(true);
        return circleIcon;
    }

    private boolean hasAddUserRestriction(UserHandle userHandle) {
        return mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_ADD_USER, userHandle);
    }

    private int getMaxSupportedRealUsers() {
        int maxSupportedUsers = UserManager.getMaxSupportedUsers();
        if (UserManager.isHeadlessSystemUserMode()) {
            maxSupportedUsers -= 1;
        }
        List<UserInfo> users = mUserManager.getAliveUsers();
        // Count all users that are managed profiles of another user.
        int managedProfilesCount = 0;
        for (UserInfo user : users) {
            if (user.isManagedProfile()) {
                managedProfilesCount++;
            }
        }
        return maxSupportedUsers - managedProfilesCount;
    }

    private void showMaxUserLimitReachedDialog() {
        AlertDialog maxUsersDialog = new AlertDialog.Builder(mContext,
                com.android.internal.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.profile_limit_reached_title)
                .setMessage(StringUtil.getIcuPluralsString(mContext, getMaxSupportedRealUsers(),
                        R.string.profile_limit_reached_message))
                .setPositiveButton(android.R.string.ok, null)
                .create();
        // Sets window flags for the SysUI dialog
        applyCarSysUIDialogFlags(maxUsersDialog);
        maxUsersDialog.show();
    }

    private void showConfirmAddUserDialog() {
        String message = mContext.getString(R.string.user_add_user_message_setup)
                .concat(System.getProperty("line.separator"))
                .concat(System.getProperty("line.separator"))
                .concat(mContext.getString(R.string.user_add_user_message_update));
        AlertDialog addUserDialog = new AlertDialog.Builder(mContext,
                com.android.internal.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.user_add_profile_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> new AddNewUserTask().execute(
                                mContext.getString(R.string.car_new_user)))
                .create();
        // Sets window flags for the SysUI dialog
        applyCarSysUIDialogFlags(addUserDialog);
        addUserDialog.show();
    }

    private void applyCarSysUIDialogFlags(AlertDialog dialog) {
        Window window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.getAttributes().setFitInsetsTypes(
                window.getAttributes().getFitInsetsTypes() & ~statusBars());
    }

    private class AddNewUserTask extends AsyncTask<String, Void, UserInfo> {
        @Override
        protected UserInfo doInBackground(String... userNames) {
            AsyncFuture<UserCreationResult> future = mCarUserManager.createUser(userNames[0],
                    /* flags= */ 0);
            try {
                UserInfo user = getUserInfo(future);
                if (user != null) {
                    UserHelper.setDefaultNonAdminRestrictions(mContext, user.getUserHandle(),
                            /* enable= */ true);
                    UserHelper.assignDefaultIcon(mContext, user.getUserHandle());
                    return user;
                } else {
                    Log.e(TAG, "Failed to create user in the background");
                    return user;
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Log.e(TAG, "Error creating new user: ", e);
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            mPendingUserAdd = true;
        }

        @Override
        protected void onPostExecute(UserInfo user) {
            mPendingUserAdd = false;
            if (user != null) {
                switchUser(user.id);
            }
        }
    }
}
