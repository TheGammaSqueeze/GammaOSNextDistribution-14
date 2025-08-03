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
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.car.kitchensink.R;

import java.util.List;

/**
 * Custom {@link android.view.View} that shows the existing Android users.
 */
public final class ExistingUsersView extends LinearLayout {

    private UserManager mUserManager;

    private TextView mNumberUsers;
    private UsersSpinner mExistingUsers;
    private UserInfoView mSelectedUser;
    private Button mUpdateButton;

    public ExistingUsersView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mUserManager = UserManager.get(context);

        inflate(context, R.layout.existing_users_view, this);
        mNumberUsers = findViewById(R.id.number_users);
        mExistingUsers = findViewById(R.id.existing_users);
        mSelectedUser = findViewById(R.id.selected_user);
        mUpdateButton = findViewById(R.id.update);

        mExistingUsers.setOnUserSelectedListener((u) -> updateUser(u));
        mUpdateButton.setOnClickListener((v) -> updateState());

        updateState();
    }

    private void updateUser(@NonNull UserInfo user) {
        mSelectedUser.update(user);
    }

    public void updateState() {
        List<UserInfo> users = mUserManager.getUsers(/*excludePartial= */ false,
                /* excludeDying= */ false, /* excludePreCreated= */ false);
        mNumberUsers.setText(users.size() + " existing users");
        mExistingUsers.init(users);
        updateUser(mExistingUsers.getSelectedUser());
    }

    public @UserIdInt int getSelectedUserId() {
        return mExistingUsers.getSelectedUserId();
    }
}
