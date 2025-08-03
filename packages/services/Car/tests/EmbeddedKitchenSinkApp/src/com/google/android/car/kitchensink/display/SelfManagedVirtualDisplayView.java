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

package com.google.android.car.kitchensink.display;

import static android.widget.Toast.LENGTH_SHORT;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.users.UsersSpinner;

import java.io.PrintWriter;
import java.util.List;

/**
 * Custom view that hosts a virtual display and a button to create / remove it
 */

public final class SelfManagedVirtualDisplayView extends LinearLayout {

    private static final String TAG = SelfManagedVirtualDisplayView.class.getSimpleName();

    private static final String NO_ID_TEXT = "N/A";

    private final VirtualDisplayView mVirtualDisplayView;

    private final LinearLayout mHeader;
    private final EditText mDisplayIdEditText;
    private final Button mCreateDisplayButton;
    private final Button mDeleteDisplayButton;
    private final UsersSpinner mUsersSpinner;
    private final Button mSwitchUserButton;

    private int mDisplayId = Display.INVALID_DISPLAY;

    @Nullable
    private List<UserInfo> mUsers;

    public SelfManagedVirtualDisplayView(Context context, AttributeSet attrs) {
        this(context, VirtualDisplayView.getName(context, attrs));
    }

    public SelfManagedVirtualDisplayView(Context context, String name) {
        super(context);

        inflate(context, R.layout.self_managed_virtual_display_view, this);

        mHeader = findViewById(R.id.header);
        mVirtualDisplayView = findViewById(R.id.virtual_display);
        mDisplayIdEditText = findViewById(R.id.display_id);
        mCreateDisplayButton = findViewById(R.id.create);
        mDeleteDisplayButton = findViewById(R.id.delete);

        mUsersSpinner = findViewById(R.id.users);
        mSwitchUserButton = findViewById(R.id.switch_user);

        if (name != null) {
            mVirtualDisplayView.setName(name);
        }
        toggleCreateDeleteButtons(/* create= */ true);
        mDisplayIdEditText.setText(NO_ID_TEXT);
        mCreateDisplayButton.setOnClickListener((v) -> createDisplayAndToastMessage());
        mDeleteDisplayButton.setOnClickListener((v) -> deleteDisplayAndToastMessage());

        mSwitchUserButton.setOnClickListener((v) -> switchUser());
        setUserSwitchingVisible(false);
    }

    // TODO: ideally it should be part of the constructor / AttributeSet, and it should use a 
    // boolean to indicated it's enabled (rather than relying on mUsers being null)
    void enableUserSwitching() {
        mUsers = getContext().getSystemService(UserManager.class).getAliveUsers();
        Log.d(TAG, "Enabling user switching. Users = " + mUsers);
        mUsersSpinner.init(mUsers);
    }

    private void setUserSwitchingVisible(boolean visible) {
        int visibility = visible && mUsers != null ? View.VISIBLE : View.GONE;
        mUsersSpinner.setVisibility(visibility);
        mSwitchUserButton.setVisibility(visibility);
    }

    private void switchUser() {
        UserInfo user = mUsersSpinner.getSelectedUser();
        Log.d(TAG, "Starting user " + user.toFullString() + " on display " + mDisplayId);
        try {
            boolean started = mContext.getSystemService(ActivityManager.class)
                    .startUserInBackgroundVisibleOnDisplay(user.id, mDisplayId);
            logAndToastMessage("%s user %d on display %d",
                    (started ? "Started" : "Failed to start"), user.id, mDisplayId);
        } catch (Exception e) {
            logAndToastError(e, "Error starting user %d on display %d", user.id, mDisplayId);
        }
    }

    void setHeaderVisible(boolean visible) {
        toggleView(mHeader, visible);
    }

    String getName() {
        return mVirtualDisplayView.getName();
    }

    void release() {
        mVirtualDisplayView.release();
    }

    void dump(String prefix, PrintWriter writer, String[] args) {
        dumpView(prefix, writer, mHeader, "Buttons panel");
        dumpView(prefix, writer, mCreateDisplayButton, "Create display button");
        dumpView(prefix, writer, mDeleteDisplayButton, "Delete display button");

        writer.printf("%sDisplay id: %s\n", prefix, mDisplayIdEditText.getText());
        writer.printf("%sVirtual Display View:\n", prefix);
        mVirtualDisplayView.dump(prefix + "  ", writer);
    }

    private void createDisplayAndToastMessage() {
        Log.i(TAG, "Creating virtual display");
        try {
            createDisplay();
            logAndToastMessage("Created virtual display with id %d", mDisplayId);
        } catch (Exception e) {
            logAndToastError(e, "Failed to create virtual display");
        }
    }

    /**
     * Creates the display and return its id.
     */
    int createDisplay() {
        mDisplayId = mVirtualDisplayView.createVirtualDisplay();
        mDisplayIdEditText.setText(String.valueOf(mDisplayId));
        toggleCreateDeleteButtons(/* create= */ false);
        setUserSwitchingVisible(/* visible= */ true);
        return mDisplayId;
    }

    private void deleteDisplayAndToastMessage() {
        Log.i(TAG, "Deleting display");
        try {
            deleteDisplay();
            logAndToastMessage("Virtual display deleted");
        } catch (Exception e) {
            logAndToastError(e, "Failed to delete virtual display");
        }
    }

    /**
     * Deletes the display.
     */
    void deleteDisplay() {
        mVirtualDisplayView.deleteVirtualDisplay();
        mDisplayId = Display.INVALID_DISPLAY;
        mDisplayIdEditText.setText(NO_ID_TEXT);
        toggleCreateDeleteButtons(/* create= */ true);
        setUserSwitchingVisible(/* visible= */ false);
    }

    private void toggleCreateDeleteButtons(boolean create) {
        toggleView(mCreateDisplayButton, create);
        toggleView(mDeleteDisplayButton, !create);
    }

    // TODO(b/231499090): move plumbing below to common code

    private void dumpView(String prefix, PrintWriter writer, View view, String name) {
        writer.printf("%s%s: %s %s\n", prefix, name,
                (view.isEnabled() ? "enabled" : "disabled"),
                visibilityToString(view.getVisibility()));
    }

    private void toggleView(View view, boolean on) {
        view.setEnabled(on);
        view.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    protected void logAndToastMessage(String format, Object...args) {
        String message = String.format(format, args);
        Log.i(TAG, message);
        Toast.makeText(getContext(), message, LENGTH_SHORT).show();
    }

    protected void printMessage(PrintWriter writer, String format, Object...args) {
        String message = String.format(format, args);
        writer.printf("%s\n", message);
    }

    protected void logAndToastError(Exception e, String format, Object...args) {
        String message = String.format(format, args);
        if (e != null) {
            Log.e(TAG, message, e);
        } else {
            Log.e(TAG, message);
        }
        Toast.makeText(getContext(), message, LENGTH_SHORT).show();
    }

    protected void printError(PrintWriter writer, Exception e, String format, Object...args) {
        String message = String.format(format, args);
        if (e != null) {
            writer.printf("%s: %s\n", message, e);
        } else {
            writer.printf("%s\n", message);
        }
    }

    public static String visibilityToString(int visibility) {
        switch (visibility) {
            case View.VISIBLE:
                return "VISIBLE";
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "UNKNOWN-" + visibility;
        }
    }
}
