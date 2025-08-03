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
package com.google.android.car.kitchensink.admin;

import static android.app.admin.DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.admin.CarDevicePolicyManager;
import android.car.admin.CreateUserResult;
import android.car.admin.RemoveUserResult;
import android.car.admin.StartUserInBackgroundResult;
import android.car.admin.StopUserResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.DebugUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkHelper;
import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.users.ExistingUsersView;
import com.google.android.car.kitchensink.users.UserInfoView;

import java.util.ArrayList;
import java.util.List;

/**
 * Test UI for {@link CarDevicePolicyManager}.
 */
public final class DevicePolicyFragment extends Fragment {

    private static final String TAG = DevicePolicyFragment.class.getSimpleName();

    private UserManager mUserManager;
    private DevicePolicyManager mDevicePolicyManager;
    private CarDevicePolicyManager mCarDevicePolicyManager;

    // Current user
    private UserInfoView mCurrentUser;

    // Existing users
    private ExistingUsersView mCurrentUsers;

    // New user
    private EditText mNewUserNameText;
    private CheckBox mNewUserIsAdminCheckBox;
    private CheckBox mNewUserIsGuestCheckBox;
    private Button mCreateUserButton;

    // Reset password
    private EditText mPasswordText;
    private Button mResetPasswordButton;

    // Other actions
    private Button mRemoveUserButton;
    private Button mStartUserInBackgroundButton;
    private Button mStopUserButton;
    private Button mLockNowButton;
    private EditText mWipeDataFlagsText;
    private Button mWipeDataButton;

    // Lock tasks
    private Button mCheckLockTasksButton;
    private Button mStartLockTasksButton;
    private Button mStopLockTasksButton;

    // Set device admin
    private final List<DeviceAdminApp> mDeviceAdminApps = new ArrayList<>();
    private Spinner mDeviceAdminAppsSpinner;
    private Button mSetDeviceAdminAppButton;
    private KitchenSinkHelper mKitchenSinkHelper;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof KitchenSinkHelper)) {
            throw new IllegalStateException(
                    "Attached activity does not implement "
                            + KitchenSinkHelper.class.getSimpleName());
        }
        mKitchenSinkHelper = (KitchenSinkHelper) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.device_policy, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mUserManager = UserManager.get(getContext());
        mDevicePolicyManager = getContext().getSystemService(DevicePolicyManager.class);
        Car car = mKitchenSinkHelper.getCar();
        mCarDevicePolicyManager = (CarDevicePolicyManager) car
                .getCarManager(Car.CAR_DEVICE_POLICY_SERVICE);

        mCurrentUser = view.findViewById(R.id.current_user);
        mCurrentUsers = view.findViewById(R.id.current_users);
        mRemoveUserButton = view.findViewById(R.id.remove_user);
        mStartUserInBackgroundButton = view.findViewById(R.id.start_user_in_background);
        mStopUserButton = view.findViewById(R.id.stop_user);

        mNewUserNameText = view.findViewById(R.id.new_user_name);
        mNewUserIsAdminCheckBox = view.findViewById(R.id.new_user_is_admin);
        mNewUserIsGuestCheckBox = view.findViewById(R.id.new_user_is_guest);
        mCreateUserButton = view.findViewById(R.id.create_user);

        mRemoveUserButton.setOnClickListener((v) -> removeUser());
        mCreateUserButton.setOnClickListener((v) -> createUser());
        mStartUserInBackgroundButton.setOnClickListener((v) -> startUserInBackground());
        mStopUserButton.setOnClickListener((v) -> stopUser());

        mPasswordText = view.findViewById(R.id.password);
        mResetPasswordButton = view.findViewById(R.id.reset_password);
        mResetPasswordButton.setOnClickListener((v) -> resetPassword());

        mLockNowButton = view.findViewById(R.id.lock_now);
        mLockNowButton.setOnClickListener((v) -> lockNow());

        mWipeDataFlagsText = view.findViewById(R.id.wipe_data_flags);
        mWipeDataButton = view.findViewById(R.id.wipe_data);
        mWipeDataButton.setOnClickListener((v) -> wipeData());

        mCheckLockTasksButton = view.findViewById(R.id.check_lock_tasks);
        mCheckLockTasksButton.setOnClickListener((v) -> checkLockTasks());

        mStartLockTasksButton = view.findViewById(R.id.start_lock_tasks);
        mStartLockTasksButton.setOnClickListener((v) -> startLockTask());

        mStopLockTasksButton = view.findViewById(R.id.stop_lock_tasks);
        mStopLockTasksButton.setOnClickListener((v) -> stopLockTasks());

        mDeviceAdminAppsSpinner = view.findViewById(R.id.device_admin_apps);
        mSetDeviceAdminAppButton = view.findViewById(R.id.set_device_admin_app);
        mSetDeviceAdminAppButton.setOnClickListener((v) -> launchSetDeviceAdminIntent());

        updateState();
    }

    private void updateState() {
        // Current user
        int userId = UserHandle.myUserId();
        UserInfo user = mUserManager.getUserInfo(userId);
        Log.v(TAG, "updateState(): currentUser= " + user);
        mCurrentUser.update(user);

        // Existing users
        mCurrentUsers.updateState();

        setAdminApps();
    }

    private void setAdminApps() {
        mDeviceAdminApps.clear();

        PackageManager pm = getContext().getPackageManager();

        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(
                new Intent(ACTION_DEVICE_ADMIN_ENABLED), /* flags= */ 0);
        if (receivers.isEmpty()) {
            Log.i(TAG, "setDeviceAdminApps(): no receivers for " + ACTION_DEVICE_ADMIN_ENABLED);
            return;
        }
        Log.i(TAG, receivers.size() + " receivers for " + ACTION_DEVICE_ADMIN_ENABLED);

        String[] entries = new String[receivers.size()];
        int i = 0;
        for (ResolveInfo receiver : receivers) {
            DeviceAdminApp adminApp = new DeviceAdminApp(receiver, pm);
            Log.d(TAG, "Adding " + adminApp);
            mDeviceAdminApps.add(adminApp);
            entries[i++] = adminApp.name;
        }
        mDeviceAdminAppsSpinner.setAdapter(
                new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item,
                        entries));
    }

    private void removeUser() {
        int userId = mCurrentUsers.getSelectedUserId();
        Log.i(TAG, "Remove user: " + userId);
        RemoveUserResult result = mCarDevicePolicyManager.removeUser(UserHandle.of(userId));
        if (result.isSuccess()) {
            updateState();
            showMessage("User %d removed", userId);
        } else {
            showMessage("Failed to remove user %d: %s", userId, result);
        }
    }

    private void createUser() {
        String name = mNewUserNameText.getText().toString();
        if (TextUtils.isEmpty(name)) {
            name = null;
        }
        // Type is treated as a flag here so we can emulate an invalid value by selecting both.
        int type = CarDevicePolicyManager.USER_TYPE_REGULAR;
        boolean isAdmin = mNewUserIsAdminCheckBox.isChecked();
        if (isAdmin) {
            type |= CarDevicePolicyManager.USER_TYPE_ADMIN;
        }
        boolean isGuest = mNewUserIsGuestCheckBox.isChecked();
        if (isGuest) {
            type |= CarDevicePolicyManager.USER_TYPE_GUEST;
        }
        CreateUserResult result = mCarDevicePolicyManager.createUser(name, type);
        if (result.isSuccess()) {
            showMessage("User created: %s", result.getUserHandle().getIdentifier());
            updateState();
        } else {
            showMessage("Failed to create user with type %d: %s", type, result);
        }
    }

    private void startUserInBackground() {
        int userId = mCurrentUsers.getSelectedUserId();
        Log.i(TAG, "Start user in background: " + userId);
        StartUserInBackgroundResult result =
                mCarDevicePolicyManager.startUserInBackground(UserHandle.of(userId));
        if (result.isSuccess()) {
            updateState();
            showMessage("User %d started", userId);
        } else {
            showMessage("Failed to start user %d in background: %s", userId, result);
        }
    }

    private void stopUser() {
        int userId = mCurrentUsers.getSelectedUserId();
        Log.i(TAG, "Stop user: " + userId);
        StopUserResult result = mCarDevicePolicyManager.stopUser(UserHandle.of(userId));
        if (result.isSuccess()) {
            updateState();
            showMessage("User %d stopped", userId);
        } else {
            showMessage("Failed to stop user %d: %s", userId, result);
        }
    }

    private void resetPassword() {
        String password = mPasswordText.getText().toString();
        // NOTE: on "real" code the password should NEVER be logged in plain text, but it's fine
        // here (as it's used for testing / development purposes)
        Log.i(TAG, "Calling resetPassword('" + password + "')...");
        run(() -> mDevicePolicyManager.resetPassword(password, /* flags= */ 0), "Password reset!");
    }

    private void lockNow() {
        Log.i(TAG, "Calling lockNow()...");
        run(() -> mDevicePolicyManager.lockNow(), "Locked!");
    }

    private void wipeData() {
        new AlertDialog.Builder(getContext())
            .setMessage("Wiping data is irreversible, are you sure you want to self-destruct?")
            .setPositiveButton("Yes", (d, w) -> selfDestruct())
            .show();
    }

    private boolean isAllowedToCheckLockTasks() {
        return mDevicePolicyManager.isLockTaskPermitted(getContext().getPackageName());
    }

    private void checkLockTasks() {
        boolean isAllowed = isAllowedToCheckLockTasks();
        showMessage("KitchenSink %s allowed to lock tasks", isAllowed ? "IS" : "is NOT");
    }

    private void startLockTask() {
        Log.v(TAG, "startLockTask()");
        if (!isAllowedToCheckLockTasks()) {
            showMessage("KitchenSink is not allowed to lock tasks, "
                    + "you must use the DPC app to allow it");
            return;
        }

        try {
            getActivity().startLockTask();
        } catch (IllegalStateException e) {
            showError(e, "No lock task present");
        }
    }

    private void stopLockTasks() {
        Log.v(TAG, "stopLockTasks()");
        try {
            getActivity().stopLockTask();
        } catch (IllegalStateException e) {
            showError(e, "No lock task present");
        }
    }

    private void launchSetDeviceAdminIntent() {
        if (mDeviceAdminApps.isEmpty()) {
            // Should be disabled
            Log.e(TAG, "setAdminApp(): no admin");
            return;
        }
        int index = mDeviceAdminAppsSpinner.getSelectedItemPosition();
        DeviceAdminApp app;
        try {
            app = mDeviceAdminApps.get(index);
        } catch (Exception e) {
            Log.e(TAG, "Could not get app at index " + index, e);
            return;
        }
        Log.v(TAG, "setAdminApp(): index=" + index + ",size=" + mDeviceAdminApps.size() + ",app="
                + app);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, app.admin);
        Log.i(TAG, "launching intent " + intent + " for " + app);
        getActivity().startActivity(intent);
    }

    private void selfDestruct() {
        int flags = 0;
        String flagsText = mWipeDataFlagsText.getText().toString();
        if (!TextUtils.isEmpty(flagsText)) {
            try {
                flags = Integer.parseInt(flagsText);
            } catch (Exception e) {
                Log.e(TAG, "Invalid wipeData flags: " + flagsText);
            }
        }

        String flagsDesc = flags == 0 ? "0" : flags + "("
                + DebugUtils.flagsToString(DevicePolicyManager.class, "WIPE_", flags) + ")";

        Log.i(TAG, "Calling wipeData(" + flagsDesc + ")...");
        try {
            mDevicePolicyManager.wipeData(flags, "SelfDestruct");
        } catch (Exception e) {
            Log.e(TAG, "wipeData(" + flagsDesc + ") failed", e);
            showMessage("wipeData(%s) failed: %s", flagsDesc, e);
        }
    }

    private void run(@NonNull Runnable runnable, @NonNull String successMessage) {
        try {
            runnable.run();
            showMessage(successMessage);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed", e);
            showMessage("failed: " + e);
        }
    }

    private void showMessage(@NonNull String pattern, @Nullable Object... args) {
        String message = String.format(pattern, args);
        Log.v(TAG, "showMessage(): " + message);
        new AlertDialog.Builder(getContext()).setMessage(message).show();
    }

    private void showError(@NonNull Exception e, @NonNull String pattern,
            @Nullable Object... args) {
        String message = String.format(pattern, args);
        Log.e(TAG, "showError(): " + message, e);
        new AlertDialog.Builder(getContext()).setMessage(message).show();
    }

    private static final class DeviceAdminApp {
        public final ComponentName admin;
        public final String name;

        DeviceAdminApp(ResolveInfo resolveInfo, PackageManager pm) {
            admin = resolveInfo.getComponentInfo().getComponentName();
            CharSequence label = resolveInfo.loadLabel(pm);
            if (TextUtils.isEmpty(label)) {
                name = resolveInfo.getComponentInfo().name;
                Log.v(TAG, "no label for " + admin.flattenToShortString() + "; using " + name);
            } else {
                name = label.toString();
            }
        }

        @Override
        public String toString() {
            return "AdminApp[name=" + name + ", admin=" + admin.flattenToShortString() + ']';
        }
    }
}
