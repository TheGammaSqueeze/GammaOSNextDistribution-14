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

package com.android.bedstead.nene.users;

import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Representation of a user on an Android device.
 */
public final class AdbUser {

    private static final String LOG_TAG = "AdbUser";

    /* From UserInfo */
    static final int FLAG_MANAGED_PROFILE = 0x00000020;

    public enum UserState {
        NOT_RUNNING,
        RUNNING_LOCKED,
        RUNNING_UNLOCKED,
        RUNNING_UNLOCKING,
        STOPPING,
        SHUTDOWN,
        UNKNOWN;

        static UserState fromDumpSysValue(String value) {
            try {
                return UserState.valueOf(value);
            } catch (IllegalArgumentException e) {
                if (value.equals("-1")) {
                    return NOT_RUNNING;
                }
                Log.w(LOG_TAG, "Unknown user state string: " + value);
                return UNKNOWN;
            }
        }
    }

    static final class MutableUser {
        Integer mId;
        @Nullable Integer mSerialNo;
        @Nullable String mName;
        @Nullable UserType mType;
        @Nullable Boolean mHasProfileOwner;
        @Nullable Boolean mIsPrimary;
        @Nullable UserState mState;
        @Nullable Boolean mIsRemoving;
        @Nullable Integer mFlags;
        @Nullable UserReference mParent;
    }

    final MutableUser mMutableUser;

    AdbUser(MutableUser mutableUser) {
        mMutableUser = mutableUser;
    }

    /** Get the id of the user. */
    public int id() {
        return mMutableUser.mId;
    }

    /** Get the serial number of the user. */
    public Integer serialNo() {
        return mMutableUser.mSerialNo;
    }

    /** Get the name of the user. */
    public String name() {
        return mMutableUser.mName;
    }

    /** Get the {@link UserState} of the user. */
    public UserState state() {
        return mMutableUser.mState;
    }

    /** True if the user is currently being removed. */
    public boolean isRemoving() {
        return mMutableUser.mIsRemoving;
    }

    /**
     * Get the user type.
     */
    public UserType type() {
        return mMutableUser.mType;
    }

    /** {@code true} if the user has a profile owner. */
    public Boolean hasProfileOwner() {
        return mMutableUser.mHasProfileOwner;
    }

    /**
     * Return {@code true} if this is the primary user.
     */
    public Boolean isPrimary() {
        return mMutableUser.mIsPrimary;
    }

    boolean hasFlag(int flag) {
        if (mMutableUser.mFlags == null) {
            return false;
        }
        return (mMutableUser.mFlags & flag) != 0;
    }

    /**
     * Return the parent of this profile.
     *
     * <p>Returns {@code null} if this user is not a profile.
     */
    @Nullable
    public UserReference parent() {
        return mMutableUser.mParent;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("User{");
        stringBuilder.append("id=" + mMutableUser.mId);
        stringBuilder.append(", serialNo=" + mMutableUser.mSerialNo);
        stringBuilder.append(", name=" + mMutableUser.mName);
        stringBuilder.append(", type=" + mMutableUser.mType);
        stringBuilder.append(", hasProfileOwner" + mMutableUser.mHasProfileOwner);
        stringBuilder.append(", isPrimary=" + mMutableUser.mIsPrimary);
        stringBuilder.append(", state=" + mMutableUser.mState);
        stringBuilder.append(", parent=" + mMutableUser.mParent);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }
}
