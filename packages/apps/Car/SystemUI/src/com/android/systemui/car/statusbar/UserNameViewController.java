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

package com.android.systemui.car.statusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Controls TextView and ImageView for the current logged in user.
 * User icon image consists of the first letter of user name.
 * Therefore, the user name and icon have to be changed at the same time.
 */
@SysUISingleton
public class UserNameViewController {
    private static final String TAG = "UserNameViewController";

    private Context mContext;
    private UserTracker mUserTracker;
    private UserManager mUserManager;
    private BroadcastDispatcher mBroadcastDispatcher;
    private String mLastUserName;

    @VisibleForTesting
    UserIconProvider mUserIconProvider;

    @VisibleForTesting
    ArrayList<TextView> mUserNameViews = new ArrayList<TextView>();
    @VisibleForTesting
    ArrayList<ImageView> mUserIconViews = new ArrayList<ImageView>();

    private final BroadcastReceiver mUserUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUser(mUserTracker.getUserId());
        }
    };

    private boolean mUserLifecycleListenerRegistered = false;

    private final UserTracker.Callback mUserChangedCallback =
            new UserTracker.Callback() {
                @Override
                public void onUserChanged(int newUser, Context userContext) {
                    updateUser(newUser);
                }
            };

    @Inject
    public UserNameViewController(Context context, UserTracker userTracker,
            UserManager userManager, BroadcastDispatcher broadcastDispatcher) {
        mContext = context;
        mUserTracker = userTracker;
        mUserManager = userManager;
        mBroadcastDispatcher = broadcastDispatcher;
        mUserIconProvider = new UserIconProvider();
    }

    /**
     * Find the {@link ImageView} or {@link TextView} for the user from a view
     * and if found set them with the current user name and icon.
     */
     @MainThread
    public void addUserNameView(View v) {
        TextView userNameView = v.findViewById(R.id.user_name_text);
        if (userNameView != null) {
            ImageView userIconView = v.findViewById(R.id.user_icon);

            if (mUserNameViews.size() == 0
                    || (userIconView != null && mUserIconViews.size() == 0)) {
                registerForUserChangeEvents();
            }

            if (!mUserNameViews.contains(userNameView)) {
                mUserNameViews.add(userNameView);
            }

            if (userIconView != null && !mUserIconViews.contains(userIconView)) {
                mUserIconViews.add(userIconView);
            }

            updateUser(mUserTracker.getUserId());
        }
    }

    /**
     * Find the {@link ImageView} or {@link TextView} for the user from a view and if found remove
     * them from the user views list.
     */
    public void removeUserNameView(View v) {
        TextView userNameView = v.findViewById(R.id.user_name_text);
        if (userNameView != null && mUserNameViews.contains(userNameView)) {
            mUserNameViews.remove(userNameView);
        }

        ImageView userIconView = v.findViewById(R.id.user_icon);
        if (userIconView != null && mUserIconViews.contains(userIconView)) {
            mUserIconViews.remove(userIconView);
        }
    }

    /**
     * Clean up the controller and unregister receiver.
     */
    public void removeAll() {
        mUserNameViews.clear();
        mUserIconViews.clear();
        if (mUserLifecycleListenerRegistered) {
            mBroadcastDispatcher.unregisterReceiver(mUserUpdateReceiver);
            mUserTracker.removeCallback(mUserChangedCallback);
            mUserLifecycleListenerRegistered = false;
        }
    }

    private void registerForUserChangeEvents() {
        // Register for user switching
        if (!mUserLifecycleListenerRegistered) {
            mUserTracker.addCallback(mUserChangedCallback, mContext.getMainExecutor());
            mUserLifecycleListenerRegistered = true;
        }
        // Also register for user info changing
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mBroadcastDispatcher.registerReceiver(mUserUpdateReceiver, filter, /* executor= */ null,
                UserHandle.ALL);
    }

    private void updateUser(int userId) {
        UserInfo currentUserInfo = mUserManager.getUserInfo(userId);

        // Update user name
        for (int i = 0; i < mUserNameViews.size(); i++) {
            mUserNameViews.get(i).setText(currentUserInfo.name);
        }

        // Update user icon with the first letter of the user name
        if (mLastUserName == null || !mLastUserName.equals(currentUserInfo.name)) {
            mLastUserName = currentUserInfo.name;
            mUserIconProvider.setRoundedUserIcon(currentUserInfo, mContext);
        }

        for (int i = 0; i < mUserIconViews.size(); i++) {
            updateUserIcon(mUserIconViews.get(i), currentUserInfo);
        }
    }

    private void updateUserIcon(ImageView userIconView, UserInfo currentUserInfo) {
        Drawable circleIcon = mUserIconProvider.getRoundedUserIcon(currentUserInfo, mContext);
        userIconView.setImageDrawable(circleIcon);
    }
}
