/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.userpicker;

import static android.view.Display.INVALID_DISPLAY;

import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Object wrapper class for {@link UserInfo}.  Use it to distinguish if a profile is a
 * guest profile, add user profile, or the foreground user, and user state (logged-in, display)
 */
final class UserRecord {
    private static final String TAG = UserRecord.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public final UserInfo mInfo;
    public final String mName;
    public final boolean mIsStartGuestSession;
    public final boolean mIsAddUser;
    public final boolean mIsForeground;
    public final Drawable mIcon;
    public final boolean mIsLoggedIn;
    public final int mLoggedInDisplay;
    public final String mSeatLocationName;
    public final boolean mIsStopping;

    // This is set from {@link UserPickerController}
    public View.OnClickListener mOnClickListener;

    private UserRecord(UserInfo info, String name, boolean isStartGuestSession, boolean isAddUser,
            boolean isForeground, Drawable icon, boolean isLoggedIn, int loggedInDisplay,
            String seatLocationName, boolean isStopping) {
        mInfo = info;
        mName = name;
        mIsStartGuestSession = isStartGuestSession;
        mIsAddUser = isAddUser;
        mIsForeground = isForeground;
        mIcon = icon;
        mIsLoggedIn = isLoggedIn;
        mLoggedInDisplay = loggedInDisplay;
        mSeatLocationName = seatLocationName;
        mIsStopping = isStopping;
    }

    static UserRecord create(UserInfo info, String name, boolean isStartGuestSession,
            boolean isAddUser, boolean isForeground, Drawable icon,
            OnClickListenerCreatorBase listenerMaker) {
        return create(info, name, isStartGuestSession, isAddUser, isForeground, icon, listenerMaker,
                false, INVALID_DISPLAY, null, false);
    }

    static UserRecord create(UserInfo info, String name, boolean isStartGuestSession,
            boolean isAddUser, boolean isForeground, Drawable icon,
            OnClickListenerCreatorBase listenerMaker, boolean isLoggedIn, int loggedInDisplay,
            String seatLocationName, boolean isStopping) {
        UserRecord userRecord = new UserRecord(info, name, isStartGuestSession, isAddUser,
                isForeground, icon, isLoggedIn, loggedInDisplay, seatLocationName, isStopping);
        listenerMaker.setUserRecord(userRecord);
        userRecord.mOnClickListener = listenerMaker.createOnClickListenerWithUserRecord();
        return userRecord;
    }

    abstract static class OnClickListenerCreatorBase {
        protected UserRecord mUserRecord;

        void setUserRecord(UserRecord userRecord) {
            mUserRecord = userRecord;
        }

        abstract View.OnClickListener createOnClickListenerWithUserRecord();
    }

    @NonNull
    @Override
    public String toString() {
        if (DEBUG) {
            StringBuilder sb = new StringBuilder();
            sb.append("UserRecord@").append(Integer.toHexString(hashCode())).append(" [");
            if (mInfo != null) {
                sb.append("userId=").append(mInfo.id).append(" ");
            }
            sb.append("name='").append(mName).append("' ");
            sb.append("isStartGuestSession=").append(mIsStartGuestSession).append(" ");
            sb.append("isAddUser=").append(mIsAddUser).append(" ");
            sb.append("isForeground=").append(mIsForeground).append(" ");
            sb.append("isLoggedIn=").append(mIsLoggedIn).append(" ");
            sb.append("loggedInDisplay=").append(mLoggedInDisplay).append(" ");
            sb.append("seatLocationName='").append(mSeatLocationName).append("' ");
            sb.append("isStopping=").append(mIsStopping).append("]");
            return sb.toString();
        } else {
            return super.toString();
        }
    }
}
