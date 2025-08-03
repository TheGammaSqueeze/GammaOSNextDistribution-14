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
package com.google.android.car.kitchensink.users;

import static android.car.user.CarUserManager.USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_ASSOCIATE_CURRENT_USER;
import static android.car.user.CarUserManager.USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_CURRENT_USER;
import static android.car.user.CarUserManager.USER_IDENTIFICATION_ASSOCIATION_TYPE_KEY_FOB;
import static android.car.user.CarUserManager.USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATE_CURRENT_USER;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.car.Car;
import android.car.SyncResultCallback;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserRemovalResult;
import android.car.user.UserSwitchRequest;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.util.DebugUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shows information (and actions) about the current user.
 *
 * <p>Could / should be improved to:
 *
 * <ul>
 *   <li>Add more actions like renaming or deleting the user.
 *   <li>Add actions for other users (switch, create, remove etc).
 *   <li>Add option on how to execute tasks above (UserManager or CarUserManager).
 *   <li>Merge with UserRestrictions and ProfileUser fragments.
 * </ul>
 */
public final class UserFragment extends Fragment {

    private static final String TAG = UserFragment.class.getSimpleName();

    private static final long TIMEOUT_MS = 5_000;
    private static final long SWITCH_USER_TIMEOUT_MS = 20_000;

    private final int mUserId = UserHandle.myUserId();
    private UserManager mUserManager;
    private CarUserManager mCarUserManager;

    // Current user
    private UserInfoView mCurrentUser;

    private CheckBox mIsAdminCheckBox;
    private CheckBox mIsAssociatedKeyFobCheckBox;

    // Existing users
    private ExistingUsersView mCurrentUsers;
    private Button mSwitchUserButton;
    private Button mRemoveUserButton;
    private Button mLockUserDataButton;
    private EditText mNewUserNameText;
    private CheckBox mNewUserIsAdminCheckBox;
    private CheckBox mNewUserIsGuestCheckBox;
    private EditText mNewUserExtraFlagsText;
    private Button mCreateUserButton;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.user, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mUserManager = UserManager.get(getContext());
        Car car = ((KitchenSinkActivity) getHost()).getCar();
        mCarUserManager = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);

        mCurrentUser = view.findViewById(R.id.current_user);
        mIsAdminCheckBox = view.findViewById(R.id.is_admin);
        mIsAssociatedKeyFobCheckBox = view.findViewById(R.id.is_associated_key_fob);

        mCurrentUsers = view.findViewById(R.id.current_users);
        mSwitchUserButton = view.findViewById(R.id.switch_user);
        mRemoveUserButton = view.findViewById(R.id.remove_user);
        mLockUserDataButton = view.findViewById(R.id.lock_user_data);
        mNewUserNameText = view.findViewById(R.id.new_user_name);
        mNewUserIsAdminCheckBox = view.findViewById(R.id.new_user_is_admin);
        mNewUserIsGuestCheckBox = view.findViewById(R.id.new_user_is_guest);

        mNewUserExtraFlagsText = view.findViewById(R.id.new_user_flags);
        mCreateUserButton = view.findViewById(R.id.create_user);

        mIsAdminCheckBox.setOnClickListener((v) -> toggleAdmin());
        mSwitchUserButton.setOnClickListener((v) -> switchUser());
        mRemoveUserButton.setOnClickListener((v) -> removeUser());
        mCreateUserButton.setOnClickListener((v) -> createUser());
        mLockUserDataButton.setOnClickListener((v) -> lockUserData());
        mIsAssociatedKeyFobCheckBox.setOnClickListener((v) -> toggleKeyFob());

        updateState();
    }

    private void toggleAdmin() {
        if (mIsAdminCheckBox.isChecked()) {
            new AlertDialog.Builder(getContext())
                    .setMessage("Promoting a user as admin is irreversible.\n\n Confirm?")
                    .setNegativeButton("No", (d, w) -> promoteCurrentUserAsAdmin(false))
                    .setPositiveButton("Yes", (d, w) -> promoteCurrentUserAsAdmin(true))
                    .show();
        } else {
            // Shouldn't be called
            Log.w(TAG, "Cannot un-set an admin user");
        }
    }

    private void toggleKeyFob() {
        associateKeyFob(mIsAssociatedKeyFobCheckBox.isChecked());
    }

    private void createUser() {
        String name = mNewUserNameText.getText().toString();
        if (TextUtils.isEmpty(name)) {
            name = null;
        }
        int flags = 0;
        boolean isGuest = mNewUserIsGuestCheckBox.isChecked();
        UserCreationResult result;
        UserInfo userInfo;
        Log.v(TAG, "Create user: name=" + name + ", flags="
                + UserInfo.flagsToString(flags) + ", is guest=" + isGuest);
        if (isGuest) {
            result = getResult(mCarUserManager.createGuest(name));
        } else {
            if (mNewUserIsAdminCheckBox.isChecked()) {
                flags |= UserInfo.FLAG_ADMIN;
            }
            String extraFlags = mNewUserExtraFlagsText.getText().toString();
            if (!TextUtils.isEmpty(extraFlags)) {
                try {
                    flags |= Integer.parseInt(extraFlags);
                } catch (RuntimeException e) {
                    Log.e(TAG, "createUser(): non-numeric flags " + extraFlags);
                }
            }
            Log.v(TAG, "Create user: name=" + name + ", flags=" + UserInfo.flagsToString(flags));
            result = getResult(mCarUserManager.createUser(name, flags));
        }
        updateState();
        StringBuilder message = new StringBuilder();
        if (result == null) {
            message.append("Timed out creating user");
        } else {
            if (result.isSuccess()) {
                message.append("User created: ").append(result.getUser().toString());
            } else {
                int status = result.getStatus();
                message.append("Failed with code ").append(status).append('(')
                        .append(UserCreationResult.statusToString(status)).append(')');
                message.append("\nFull result: ").append(result);
            }
            String error = result.getErrorMessage();
            if (error != null) {
                message.append("\nError message: ").append(error);
            }
        }
        showMessage(message.toString());
    }

    private void removeUser() {
        int userId = mCurrentUsers.getSelectedUserId();
        Log.i(TAG, "Remove user: " + userId);
        UserRemovalResult result = mCarUserManager.removeUser(userId);
        updateState();

        if (result.isSuccess()) {
            showMessage("User %d removed", userId);
        } else {
            showMessage("Failed to remove user %d: %s", userId,
                    UserRemovalResult.statusToString(result.getStatus()));
        }
    }

    private void switchUser() {
        int userId = mCurrentUsers.getSelectedUserId();
        Log.i(TAG, "Switch user: " + userId);
        SyncResultCallback<UserSwitchResult> userSwitchResultCallback =
                new SyncResultCallback<>();
        mCarUserManager.switchUser(new UserSwitchRequest.Builder(UserHandle.of(userId)).build(),
                Runnable::run, userSwitchResultCallback);
        UserSwitchResult result = new UserSwitchResult(UserSwitchResult.STATUS_ANDROID_FAILURE,
                null);
        try {
            result = userSwitchResultCallback.get(SWITCH_USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.e(TAG, "switchUser(" + userId + ") : timed out while waiting for result");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "switchUser(" + userId + ") : interrupted while waiting for result");
        }
        updateState();

        StringBuilder message = new StringBuilder();
        if (result == null) {
            message.append("Timed out switching user");
        } else {
            int status = result.getStatus();
            if (result.isSuccess()) {
                message.append("Switched to user ").append(userId).append(" (status=")
                        .append(UserSwitchResult.statusToString(status)).append(')');
            } else {
                message.append("Failed with code ").append(status).append('(')
                        .append(UserSwitchResult.statusToString(status)).append(')');
            }
            String error = result.getErrorMessage();
            if (error != null) {
                message.append("\nError message: ").append(error);
            }
        }
        showMessage(message.toString());
    }

    private void lockUserData() {
        int userToLock = mCurrentUsers.getSelectedUserId();
        if (userToLock == UserHandle.USER_NULL) {
            return;
        }

        StorageManager storageManager = getContext().getSystemService(StorageManager.class);

        try {
            storageManager.lockCeStorage(userToLock);
        } catch (Exception e) {
            showMessage("Error: lock user data: " + e);
        }
    }

    private void promoteCurrentUserAsAdmin(boolean promote) {
        if (!promote) {
            Log.d(TAG, "NOT promoting user " + mUserId + " as admin");
        } else {
            Log.d(TAG, "Promoting user " + mUserId + " as admin");
            mUserManager.setUserAdmin(mUserId);
        }
        updateState();
    }

    private void updateState() {
        // Current user
        boolean isAdmin = mUserManager.isAdminUser();
        boolean isAssociatedKeyFob = isAssociatedKeyFob();
        UserInfo user = mUserManager.getUserInfo(mUserId);
        Log.v(TAG, "updateState(): user= " + user + ", isAdmin=" + isAdmin
                + ", isAssociatedKeyFob=" + isAssociatedKeyFob);
        mCurrentUser.update(user);
        mIsAdminCheckBox.setChecked(isAdmin);
        mIsAdminCheckBox.setEnabled(!isAdmin); // there's no API to "un-admin a user"
        mIsAssociatedKeyFobCheckBox.setChecked(isAssociatedKeyFob);

        // Existing users
        mCurrentUsers.updateState();
    }

    private boolean isAssociatedKeyFob() {
        UserIdentificationAssociationResponse result = mCarUserManager
                .getUserIdentificationAssociation(USER_IDENTIFICATION_ASSOCIATION_TYPE_KEY_FOB);
        if (!result.isSuccess()) {
            Log.e(TAG, "isAssociatedKeyFob() failed: " + result);
            return false;
        }
        return result.getValues()[0]
                == USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATE_CURRENT_USER;
    }

    private void associateKeyFob(boolean associate) {
        int value = associate ? USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_ASSOCIATE_CURRENT_USER :
                USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_CURRENT_USER;
        Log.d(TAG, "associateKey(" + associate + "): setting to " + DebugUtils.constantToString(
                CarUserManager.class, /* prefix= */ "", value));

        AsyncFuture<UserIdentificationAssociationResponse> future = mCarUserManager
                .setUserIdentificationAssociation(
                        new int[] { USER_IDENTIFICATION_ASSOCIATION_TYPE_KEY_FOB },
                        new int[] { value });
        UserIdentificationAssociationResponse result = getResult(future);
        Log.d(TAG, "Result: " + result);

        String error = null;
        boolean associated = associate;

        if (result == null) {
            error = "Timed out associating key fob";
        } else {
            if (!result.isSuccess()) {
                error = "HAL call failed: " + result;
            } else {
                int newValue = result.getValues()[0];
                String newValueName = DebugUtils.constantToString(CarUserManager.class,
                        /* prefix= */ "", newValue);
                Log.d(TAG, "New status: " + newValueName);
                associated = (
                        newValue == USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATE_CURRENT_USER);
                if (associated != associate) {
                    error = "Result doesn't match request: " + newValueName;
                }
            }
        }
        if (error != null) {
            showMessage("associateKeyFob(" + associate + ") failed: " + error);
        }
        updateState();
    }

    private void showMessage(String pattern, Object... args) {
        String message = String.format(pattern, args);
        Log.v(TAG, "showMessage(): " + message);
        new AlertDialog.Builder(getContext()).setMessage(message).show();
    }

    @Nullable
    private static <T> T getResult(AsyncFuture<T> future) {
        future.whenCompleteAsync((r, e) -> {
            if (e != null) {
                Log.e(TAG, "You have no future!", e);
                return;
            }
            Log.v(TAG, "The future is here: " + r);
        }, Runnable::run);

        T result = null;
        try {
            result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result == null) {
                Log.e(TAG, "Timeout (" + TIMEOUT_MS + "ms) waiting for future " + future);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for future " + future, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Exception getting future " + future, e);
        }
        return result;
    }
}
