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
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.Arrays;
import java.util.List;

/**
 * Custom {@link Spinner} to show a list of Android users.
 */
public final class UsersSpinner extends Spinner {

    private static final String TAG = UsersSpinner.class.getSimpleName();

    private UserInfo[] mUsers;
    private String[] mEntries;
    private OnUserSelectedListener mListener;

    public UsersSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes the spinner with the given users.
     */
    public void init(List<UserInfo> users) {
        Log.v(TAG, "init(): " + users.size() + " users (" + users + ")");
        int size = users.size();
        mUsers = new UserInfo[size];
        users.toArray(mUsers);
        mEntries = new String[size];
        for (int i = 0; i < size; i++) {
            UserInfo user = mUsers[i];
            StringBuilder entry = new StringBuilder().append(user.id).append('-');
            if (!TextUtils.isEmpty(user.name)) {
                entry.append(user.name);
            } else {
                if (user.id == UserHandle.USER_SYSTEM) {
                    entry.append("SYSTEM");
                } else {
                    entry.append(UserInfo.flagsToString(user.flags));
                }
            }
            mEntries[i] = entry.toString();
        }
        Log.v(TAG, "initializing spinner with " + size + " entries: " + Arrays.toString(mEntries));
        setAdapter(new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item,
                mEntries));
        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                UserInfo user = getSelectedUser();
                Log.v(TAG, "onItemSelected(): index=" + position + ", user=" + user);
                if (mListener != null) {
                    mListener.onUserSelected(user);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.v(TAG, "nothing selected");
            }
        });
    }

    /**
     * Gets the selected user id, or {@link UserHandle#USER_NULL}.
     */
    public @UserIdInt int getSelectedUserId() {
        UserInfo user = getSelectedUser();
        return user == null ? UserHandle.USER_NULL : user.id;
    }

    /**
     * Gets the selected user info.
     */
    public @Nullable UserInfo getSelectedUser() {
        int selectedIndex = getSelectedItemPosition();
        Log.v(TAG, "getSelectedUser(): index=" + selectedIndex);
        try {
            return mUsers[selectedIndex];
        } catch (RuntimeException e) {
            Log.e(TAG, "getSelectedUserId(): failed to get user at index " + selectedIndex
                    + ". Users: " + Arrays.toString(mUsers));
            return null;
        }
    }

    /**
     * Sets the listener called when the spinner value change.
     */
    public void setOnUserSelectedListener(@Nullable OnUserSelectedListener listener) {
        mListener = listener;
    }

    /**
     * Listener called when the spinner value change.
     */
    public interface OnUserSelectedListener {
        /**
         * Callback for the selected user.
         */
        void onUserSelected(@NonNull UserInfo user);
    }
}
