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

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.car.admin.ui.UserAvatarView;
import com.android.internal.util.UserIcons;

import com.google.android.car.kitchensink.R;

/**
 * Custom {@link android.view.View} that shows an Android user info.
 */
public final class UserInfoView extends LinearLayout {

    private static final String TAG = UserInfoView.class.getSimpleName();

    private UserAvatarView mUserAvatarView;
    private EditText mUserIdEditText;
    private EditText mUserNameEditText;
    private EditText mUserTypeEditText;
    private EditText mUserFlagsEditText;
    private EditText mUserStatusEditText;
    private TextView mManagedTextView;

    public UserInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflate(context, R.layout.user_info_view, this);
        mUserAvatarView = findViewById(R.id.user_avatar);
        mUserIdEditText = findViewById(R.id.user_id);
        mUserNameEditText = findViewById(R.id.user_name);
        mUserTypeEditText = findViewById(R.id.user_type);
        mUserFlagsEditText = findViewById(R.id.user_flags);
        mUserStatusEditText = findViewById(R.id.user_status);
        mManagedTextView = findViewById(R.id.managed);
    }

    /**
     * Initializes the widget with the given user.
     */
    public void update(@NonNull UserInfo user) {
        Log.v(TAG, "initializing with " + user.toFullString());

        setUserIcon(user.id);
        mUserIdEditText.setText(String.valueOf(user.id));
        mUserNameEditText.setText(user.name);
        mUserTypeEditText.setText(user.userType);
        mUserFlagsEditText.setText(UserInfo.flagsToString(user.flags));
        mUserStatusEditText.setText(getStatus(user));
        setManagedStatus(user.id);
    }

    private String getStatus(@NonNull UserInfo user) {
        ActivityManager am = (ActivityManager) getContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        int currentUserId = ActivityManager.getCurrentUser();
        boolean current = user.id == currentUserId;
        boolean running = am.isUserRunning(user.id);
        boolean visible = isUserVisible(user.id);
        String s = new StringBuilder()
                .append(user.partial ? " (partial)" : "")
                .append(running ? " (running)" : "")
                .append(current ? " (current)" : "")
                .append(visible ? " (visible)" : "")
                .toString();
        return s;
    }

    private boolean isUserVisible(@UserIdInt int userId) {
        UserHandle user = UserHandle.of(userId);
        Context context = mContext.createContextAsUser(user, /* flags= */ 0);
        boolean visible = context.getSystemService(UserManager.class).isUserVisible();
        Log.v(TAG, "isUserVisible(" + userId + "): " + visible);
        return visible;
    }

    private void setUserIcon(@UserIdInt int userId) {
        UserManager um = UserManager.get(getContext());
        Bitmap icon = um.getUserIcon(userId);
        if (icon == null) {
            Log.v(TAG, "No icon for user " + userId); // Happens on system user
            icon = UserIcons.convertToBitmap(
                    UserIcons.getDefaultUserIcon(getResources(), userId, /* light= */ false));
        }
        mUserAvatarView.setAvatarWithBadge(icon, userId);
    }

    private void setManagedStatus(@UserIdInt int userId) {
        // Check managed status
        DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);
        if (dpm == null) return;

        UserHandle deviceOwner = dpm.getDeviceOwnerUser();
        Log.v(TAG, "Device owner is " + deviceOwner);

        if (deviceOwner != null && deviceOwner.getIdentifier() == userId) {
            setManagedText("DeviceOwner");
            return;
        }

        ComponentName profileOwner = dpm.getProfileOwnerAsUser(userId);
        if (profileOwner != null) {
            Log.v(TAG, "User " + userId + " has profile owner: " + profileOwner);
            setManagedText("ProfileOwner");
            return;
        }
        mManagedTextView.setVisibility(GONE);
    }

    private void setManagedText(@NonNull CharSequence text) {
        mManagedTextView.setText(text);
        mManagedTextView.setVisibility(VISIBLE);
    }
}
