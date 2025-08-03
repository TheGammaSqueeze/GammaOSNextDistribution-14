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
package com.google.android.car.kitchensink.users;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserStartRequest;
import android.car.user.UserStopRequest;
import android.car.util.concurrent.AsyncFuture;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.UserPickerActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class SimpleUserPickerFragment extends Fragment {

    private static final String TAG = SimpleUserPickerFragment.class.getSimpleName();

    private static final int ERROR_MESSAGE = 0;
    private static final int WARN_MESSAGE = 1;
    private static final int INFO_MESSAGE = 2;

    private static final long TIMEOUT_MS = 10_000;

    private SpinnerWrapper mUsersSpinner;
    private SpinnerWrapper mDisplaysSpinner;

    private Button mStartUserButton;
    private Button mStopUserButton;
    private Button mSwitchUserButton;
    private Button mCreateUserButton;

    private TextView mDisplayIdText;
    private TextView mUserOnDisplayText;
    private TextView mUserIdText;
    private TextView mZoneInfoText;
    private TextView mStatusMessageText;
    private EditText mNewUserNameText;

    private UserManager mUserManager;
    private DisplayManager mDisplayManager;
    private CarOccupantZoneManager mZoneManager;
    private CarUserManager mCarUserManager;

    // The logical display to which the view's window has been attached.
    private Display mDisplayAttached;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_user_picker, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        mUserManager = getContext().getSystemService(UserManager.class);
        mDisplayManager = getContext().getSystemService(DisplayManager.class);

        Car car = ((UserPickerActivity) getHost()).getCar();
        if (car == null) {
            // Car service has crashed. Ignore other parts as it will be
            // restarted anyway.
            Log.i(TAG, "null car instance, finish");
            ((Activity) getHost()).finish();
            return;
        }
        mZoneManager = car.getCarManager(CarOccupantZoneManager.class);
        mZoneManager.registerOccupantZoneConfigChangeListener(
                new ZoneChangeListener());

        mCarUserManager = car.getCarManager(CarUserManager.class);

        mDisplayAttached = getContext().getDisplay();
        if (mDisplayAttached == null) {
            Log.e(TAG, "Cannot find display");
            ((Activity) getHost()).finish();
        }

        int displayId = mDisplayAttached.getDisplayId();
        int driverDisplayId = mZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        Log.i(TAG, "driver display id: " + driverDisplayId);
        boolean isPassengerView = displayId != driverDisplayId;
        boolean hasUserOnDisplay = mZoneManager.getUserForDisplayId(displayId)
                != CarOccupantZoneManager.INVALID_USER_ID;

        mDisplayIdText = view.findViewById(R.id.textView_display_id);
        mUserOnDisplayText = view.findViewById(R.id.textView_user_on_display);
        mUserIdText = view.findViewById(R.id.textView_state);
        mZoneInfoText = view.findViewById(R.id.textView_zoneinfo);
        updateTextInfo();

        mNewUserNameText = view.findViewById(R.id.new_user_name);

        mUsersSpinner = SpinnerWrapper.create(getContext(),
                view.findViewById(R.id.spinner_users), getUnassignedUsers());
        if (isPassengerView && hasUserOnDisplay) {
            view.findViewById(R.id.textView_users).setVisibility(View.GONE);
            view.findViewById(R.id.spinner_users).setVisibility(View.GONE);
        }

        // Listen to user created and removed events to refresh the user Spinner.
        UserLifecycleEventFilter filter = new UserLifecycleEventFilter.Builder()
                .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED)
                .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED).build();
        mCarUserManager.addListener(getContext().getMainExecutor(), filter, (event) ->
                mUsersSpinner.updateEntries(getUnassignedUsers())
        );

        mDisplaysSpinner = SpinnerWrapper.create(getContext(),
                view.findViewById(R.id.spinner_displays), getDisplays());
        if (isPassengerView) {
            view.findViewById(R.id.textView_displays).setVisibility(View.GONE);
            view.findViewById(R.id.spinner_displays).setVisibility(View.GONE);
        }

        mStartUserButton = view.findViewById(R.id.button_start_user);
        mStartUserButton.setOnClickListener(v -> startUser());
        if (isPassengerView) {
            mStartUserButton.setVisibility(View.GONE);
        }

        mStopUserButton = view.findViewById(R.id.button_stop_user);
        mStopUserButton.setOnClickListener(v -> stopUser());
        if (!isPassengerView || isPassengerView && !hasUserOnDisplay) {
            mStopUserButton.setVisibility(View.GONE);
        }

        mSwitchUserButton = view.findViewById(R.id.button_switch_user);
        mSwitchUserButton.setOnClickListener(v -> switchUser());
        if (!isPassengerView || isPassengerView && hasUserOnDisplay) {
            mSwitchUserButton.setVisibility(View.GONE);
        }

        mCreateUserButton = view.findViewById(R.id.button_create_user);
        mCreateUserButton.setOnClickListener(v -> createUser());
        if (isPassengerView && hasUserOnDisplay) {
            view.findViewById(R.id.textView_name).setVisibility(View.GONE);
            view.findViewById(R.id.new_user_name).setVisibility(View.GONE);
            mCreateUserButton.setVisibility(View.GONE);
        }

        mStatusMessageText = view.findViewById(R.id.status_message_text_view);
    }

    private final class ZoneChangeListener implements
            CarOccupantZoneManager.OccupantZoneConfigChangeListener {
        @Override
        public void onOccupantZoneConfigChanged(int changeFlags) {
            Log.i(TAG, "onOccupantZoneConfigChanged changeFlags=" + changeFlags);
            if ((changeFlags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY) != 0) {
                Log.i(TAG, "Detected changes in display to zone assignment");
                mDisplaysSpinner.updateEntries(getDisplays());
                // When a display is removed, user on the display should be stopped.
                mUsersSpinner.updateEntries(getUnassignedUsers());
                updateTextInfo();
            }

            if ((changeFlags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER) != 0) {
                Log.i(TAG, "Detected changes in user to zone assignment");
                mDisplaysSpinner.updateEntries(getDisplays());
                mUsersSpinner.updateEntries(getUnassignedUsers());
                updateTextInfo();
            }
        }
    }

    private void updateTextInfo() {
        int displayId = mDisplayAttached.getDisplayId();
        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplayId(displayId);
        int userId = CarOccupantZoneManager.INVALID_USER_ID;
        try {
            if (zoneInfo != null) {
                userId = mZoneManager.getUserForOccupant(zoneInfo);
            }
        } catch (Exception e) {
            Log.w(TAG, "updateTextInfo: encountered exception in getting user for occupant", e);
        }
        int zoneId = zoneInfo == null ? CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID
                : zoneInfo.zoneId;
        mDisplayIdText.setText("DisplayId: " + displayId + " ZoneId: " + zoneId);
        String userString = userId == CarOccupantZoneManager.INVALID_USER_ID
                ? "unassigned" : Integer.toString(userId);
        mUserOnDisplayText.setText("User on display: " + userString);

        int currentUserId = ActivityManager.getCurrentUser();
        int myUserId = UserHandle.myUserId();
        mUserIdText.setText("Current userId: " + currentUserId + " myUserId:" + myUserId);
        StringBuilder zoneStateBuilder = new StringBuilder();
        zoneStateBuilder.append("Zone-User-Displays: ");
        List<CarOccupantZoneManager.OccupantZoneInfo> zonelist = mZoneManager.getAllOccupantZones();
        for (CarOccupantZoneManager.OccupantZoneInfo zone : zonelist) {
            zoneStateBuilder.append(zone.zoneId);
            zoneStateBuilder.append("-");
            int user = mZoneManager.getUserForOccupant(zone);
            if (user == UserHandle.USER_NULL) {
                zoneStateBuilder.append("unassigned");
            } else {
                zoneStateBuilder.append(user);
            }
            zoneStateBuilder.append("-");
            List<Display> displays = mZoneManager.getAllDisplaysForOccupant(zone);
            for (Display display : displays) {
                zoneStateBuilder.append(display.getDisplayId());
                zoneStateBuilder.append(",");
            }
            zoneStateBuilder.append(":");
        }
        mZoneInfoText.setText(zoneStateBuilder.toString());
    }

    // startUser starts a selected user on a selected secondary display.
    private void startUser() {
        int userId = getSelectedUser();
        if (userId == UserHandle.USER_NULL) {
            return;
        }

        int displayId = getSelectedDisplay();
        if (displayId == Display.INVALID_DISPLAY) {
            return;
        }

        // Start the user on display.
        startUserVisibleOnDisplay(userId, displayId);
    }

    // stopUser stops the visible user on this secondary display.
    private void stopUser() {
        int displayId = mDisplayAttached.getDisplayId();

        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplayId(displayId);
        if (zoneInfo == null) {
            setMessage(ERROR_MESSAGE,
                    "Cannot find occupant zone info associated with display " + displayId);
            return;
        }

        int userId = mZoneManager.getUserForOccupant(zoneInfo);
        if (userId == CarOccupantZoneManager.INVALID_USER_ID) {
            setMessage(ERROR_MESSAGE,
                    "Cannot find the user assigned to the occupant zone " + zoneInfo.zoneId);
            return;
        }

        int currentUser = ActivityManager.getCurrentUser();
        if (userId == currentUser) {
            setMessage(WARN_MESSAGE, "Can not change current user");
            return;
        }

        if (!mUserManager.isUserRunning(userId)) {
            setMessage(WARN_MESSAGE, "User " + userId + " is already stopped");
            return;
        }

        Log.i(TAG, "stop user:" + userId);
        UserStopRequest request = new UserStopRequest.Builder(UserHandle.of(userId)).build();
        mCarUserManager.stopUser(request, Runnable::run,
                response -> {
                    if (!response.isSuccess()) {
                        setMessage(ERROR_MESSAGE,
                                "Cannot stop user " + userId + ", Response: " + response);
                        return;
                    }
                    getActivity().recreate();
                });
    }

    private void switchUser() {
        // Pick an unassigned user to switch to on this display.
        int userId = getSelectedUser();
        if (userId == UserHandle.USER_NULL) {
            setMessage(ERROR_MESSAGE, "Invalid user");
            return;
        }

        int displayId = mDisplayAttached.getDisplayId();
        startUserVisibleOnDisplay(userId, displayId);
    }

    private void startUserVisibleOnDisplay(@UserIdInt int userId, int displayId) {
        Log.i(TAG, "start user: " + userId + " in background on display: " + displayId);
        UserStartRequest request = new UserStartRequest.Builder(UserHandle.of(userId))
                .setDisplayId(displayId).build();
        mCarUserManager.startUser(request, Runnable::run,
                response -> {
                    boolean isSuccess = response.isSuccess();
                    if (!isSuccess) {
                        setMessage(ERROR_MESSAGE,
                                "Cannot start user " + userId + " on display " + displayId
                                        + ", response: " + response);
                    } else {
                        setMessage(INFO_MESSAGE,
                                "Started user " + userId + " on display " + displayId);
                        mUsersSpinner.updateEntries(getUnassignedUsers());
                        updateTextInfo();
                    }
                });
    }

    private void createUser() {
        String name = mNewUserNameText.getText().toString();
        if (TextUtils.isEmpty(name)) {
            setMessage(ERROR_MESSAGE, "Cannot create user without a name");
            return;
        }

        AsyncFuture<UserCreationResult> future = mCarUserManager.createUser(name, /* flags= */ 0);
        setMessage(INFO_MESSAGE, "Creating full secondary user with name " + name + " ...");

        UserCreationResult result = null;
        try {
            result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result == null) {
                Log.e(TAG, "Timed out creating user after " + TIMEOUT_MS + "ms...");
                setMessage(ERROR_MESSAGE, "Timed out creating user after " + TIMEOUT_MS + "ms...");
                return;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for future " + future, e);
            Thread.currentThread().interrupt();
            setMessage(ERROR_MESSAGE, "Interrupted while creating user");
            return;
        } catch (Exception e) {
            Log.e(TAG, "Exception getting future " + future, e);
            setMessage(ERROR_MESSAGE, "Encountered Exception while creating user " + name);
            return;
        }

        StringBuilder message = new StringBuilder();
        if (result.isSuccess()) {
            message.append("User created: ").append(result.getUser().toString());
            setMessage(INFO_MESSAGE, message.toString());
            mUsersSpinner.updateEntries(getUnassignedUsers());
        } else {
            int status = result.getStatus();
            message.append("Failed with code ").append(status).append('(')
                    .append(UserCreationResult.statusToString(status)).append(')');
            message.append("\nFull result: ").append(result);
            String error = result.getErrorMessage();
            if (error != null) {
                message.append("\nError message: ").append(error);
            }
            setMessage(ERROR_MESSAGE, message.toString());
        }
    }

    // TODO(b/248608281): Use API from CarOccupantZoneManager for convenience.
    @Nullable
    private OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        List<OccupantZoneInfo> occupantZoneInfos = mZoneManager.getAllOccupantZones();
        for (int index = 0; index < occupantZoneInfos.size(); index++) {
            OccupantZoneInfo occupantZoneInfo = occupantZoneInfos.get(index);
            List<Display> displays = mZoneManager.getAllDisplaysForOccupant(
                    occupantZoneInfo);
            for (int displayIndex = 0; displayIndex < displays.size(); displayIndex++) {
                if (displays.get(displayIndex).getDisplayId() == displayId) {
                    return occupantZoneInfo;
                }
            }
        }
        return null;
    }

    private void setMessage(int messageType, String title, Exception e) {
        StringBuilder messageTextBuilder = new StringBuilder()
                .append(title)
                .append(": ")
                .append(e.getMessage());
        setMessage(messageType, messageTextBuilder.toString());
    }

    private void setMessage(int messageType, String message) {
        int textColor;
        switch (messageType) {
            case ERROR_MESSAGE:
                Log.e(TAG, message);
                textColor = Color.RED;
                break;
            case WARN_MESSAGE:
                Log.w(TAG, message);
                textColor = Color.YELLOW;
                break;
            case INFO_MESSAGE:
            default:
                Log.i(TAG, message);
                textColor = Color.GREEN;
        }
        mStatusMessageText.setTextColor(textColor);
        mStatusMessageText.setText(message);
    }

    private int getSelectedDisplay() {
        String displayStr = mDisplaysSpinner.getSelectedEntry();
        if (displayStr == null) {
            Log.w(TAG, "getSelectedDisplay, no display selected", new RuntimeException());
            return Display.INVALID_DISPLAY;
        }
        return Integer.parseInt(displayStr.split(",")[0]);
    }

    private int getSelectedUser() {
        String userStr = mUsersSpinner.getSelectedEntry();
        if (userStr == null) {
            Log.w(TAG, "getSelectedUser, user not selected", new RuntimeException());
            return UserHandle.USER_NULL;
        }
        return Integer.parseInt(userStr.split(",")[0]);
    }

    // format: id,type
    private ArrayList<String> getUnassignedUsers() {
        ArrayList<String> users = new ArrayList<>();
        List<UserInfo> aliveUsers = mUserManager.getAliveUsers();
        Set<UserHandle> visibleUsers = mUserManager.getVisibleUsers();
        // Exclude visible users and only show unassigned users.
        for (int i = 0; i < aliveUsers.size(); ++i) {
            UserInfo u = aliveUsers.get(i);
            if (!u.isFull()) continue;
            if (!isIncluded(u.id, visibleUsers)) {
                users.add(Integer.toString(u.id) + "," + u.name);
            }
        }

        return users;
    }

    // format: displayId,[P,]?,address]
    private ArrayList<String> getDisplays() {
        ArrayList<String> displays = new ArrayList<>();
        Display[] disps = mDisplayManager.getDisplays();
        int uidSelf = Process.myUid();
        for (Display disp : disps) {
            if (!disp.hasAccess(uidSelf)) {
                continue;
            }

            int displayId = disp.getDisplayId();
            if (mZoneManager.getUserForDisplayId(displayId)
                    != CarOccupantZoneManager.INVALID_USER_ID) {
                Log.d(TAG, "display " + displayId + " already has user on it, skipping");
                continue;
            }
            StringBuilder builder = new StringBuilder()
                    .append(displayId)
                    .append(",");
            DisplayAddress address = disp.getAddress();
            if (address instanceof  DisplayAddress.Physical) {
                builder.append("P,");
            }
            builder.append(address);
            displays.add(builder.toString());
        }
        return displays;
    }

    private static boolean isIncluded(int userId, Collection<UserHandle> users) {
        return users.stream().anyMatch(u -> u.getIdentifier() == userId);
    }

    private static final class SpinnerWrapper {
        private final Spinner mSpinner;
        private final ArrayList<String> mEntries;
        private final ArrayAdapter<String> mAdapter;

        private static SpinnerWrapper create(Context context, Spinner spinner,
                ArrayList<String> entries) {
            SpinnerWrapper wrapper = new SpinnerWrapper(context, spinner, entries);
            wrapper.init();
            return wrapper;
        }

        private SpinnerWrapper(Context context, Spinner spinner, ArrayList<String> entries) {
            mSpinner = spinner;
            mEntries = new ArrayList<>(entries);
            mAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item,
                    mEntries);
        }

        private void init() {
            mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mSpinner.setAdapter(mAdapter);
        }

        private void updateEntries(ArrayList<String> entries) {
            mEntries.clear();
            mEntries.addAll(entries);
            mAdapter.notifyDataSetChanged();
        }

        @Nullable
        private String getSelectedEntry() {
            return (String) mSpinner.getSelectedItem();
        }
    }
}
