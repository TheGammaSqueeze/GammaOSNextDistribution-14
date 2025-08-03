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

import java.util.Set;

/**
 * Represents information about an Android User type.
 */
public final class UserType {

    public static final String SECONDARY_USER_TYPE_NAME = "android.os.usertype.full.SECONDARY";
    public static final String SYSTEM_USER_TYPE_NAME = "android.os.usertype.full.SYSTEM";
    public static final String MANAGED_PROFILE_TYPE_NAME = "android.os.usertype.profile.MANAGED";

    public static final int UNLIMITED = -1;

    /** Default base types. */
    public static final class BaseType {
        public static final String SYSTEM = "SYSTEM";
        public static final String PROFILE = "PROFILE";
        public static final String FULL = "FULL";
    }

    static final class MutableUserType {
        String mName;
        Set<String> mBaseType;
        Boolean mEnabled;
        Integer mMaxAllowed;
        Integer mMaxAllowedPerParent;
    }

    private final MutableUserType mMutableUserType;

    UserType(MutableUserType mutableUserType) {
        mMutableUserType = mutableUserType;
    }

    public String name() {
        return mMutableUserType.mName;
    }

    /** Get the base type(s) of this type. */
    public Set<String> baseType() {
        return mMutableUserType.mBaseType;
    }

    public Boolean enabled() {
        return mMutableUserType.mEnabled;
    }

    /**
     * The maximum number of this user type allowed on the device.
     *
     * <p>This value will be {@link #UNLIMITED} if there is no limit.
     */
    public Integer maxAllowed() {
        return mMutableUserType.mMaxAllowed;
    }

    /**
     * The maximum number of this user type allowed for a single parent profile
     *
     * <p>This value will be {@link #UNLIMITED} if there is no limit.
     */
    public Integer maxAllowedPerParent() {
        return mMutableUserType.mMaxAllowedPerParent;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("UserType{");
        stringBuilder.append("name=" + mMutableUserType.mName);
        stringBuilder.append(", baseType=" + mMutableUserType.mBaseType);
        stringBuilder.append(", enabled=" + mMutableUserType.mEnabled);
        stringBuilder.append(", maxAllowed=" + mMutableUserType.mMaxAllowed);
        stringBuilder.append(", maxAllowedPerParent=" + mMutableUserType.mMaxAllowedPerParent);
        return stringBuilder.toString();
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof UserType)) {
            return false;
        }

        UserType other = (UserType) obj;
        return other.name().equals(name());
    }
}
