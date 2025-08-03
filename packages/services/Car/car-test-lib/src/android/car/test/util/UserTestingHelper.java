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
package android.car.test.util;

import static android.car.PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.internal.util.Preconditions;

import org.junit.AssumptionViolatedException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides utilities for Android User related tasks.
 */
public final class UserTestingHelper {

    private static final String TAG = UserTestingHelper.class.getSimpleName();

    /**
     * Checks if the target device supports MUMD (multi-user multi-display).
     * @throws AssumptionViolatedException if the device does not support MUMD.
     */
    // TODO(b/250108245): Currently doing this because using DeviceState rule is very heavy. We
    //  may want to use PermissionsCheckerRule as a light-weight feature check
    //  (and probably rename it to something like DeviceStateLite).
    public static void requireMumd(Context context) {
        assumeTrue(
                "The device does not support multiple users on multiple displays",
                Car.getPlatformVersion().isAtLeast(UPSIDE_DOWN_CAKE_0)
                && context.getSystemService(UserManager.class).isVisibleBackgroundUsersSupported());
    }

    /**
     * Returns a display that is available to start a background user on.
     *
     * @return the id of a secondary display that is not assigned to any user, if any.
     * @throws IllegalStateException when there is no secondary display available.
     */
    public static int getDisplayForStartingBackgroundUser(
            Context context, CarOccupantZoneManager occupantZoneManager) {
        int[] displayIds = context.getSystemService(ActivityManager.class)
                .getDisplayIdsForStartingVisibleBackgroundUsers();
        Log.d(TAG, "getSecondaryDisplayIdsForStartingBackgroundUsers() display IDs"
                + " returned by AM: " + Arrays.toString(displayIds));
        if (displayIds == null || displayIds.length == 0) {
            throw new IllegalStateException("No secondary display is available to start a user.");
        }

        for (int displayId : displayIds) {
            int userId = occupantZoneManager.getUserForDisplayId(displayId);
            if (userId == CarOccupantZoneManager.INVALID_USER_ID) {
                Log.d(TAG, "Returning first available display: " + displayId);
                return displayId;
            }
            Log.d(TAG, "Display " + displayId + "is curretnly assigned to user " + userId);
        }

        throw new IllegalStateException(
                "All secondary displays are assigned. No secondary display is available.");
    }

    /**
     * Creates a simple {@link UserInfo}, containing just the given {@code userId}.
     */
    @NonNull
    public static UserInfo newUser(@UserIdInt int userId) {
        return new UserInfoBuilder(userId).build();
    }

    /**
     * Creates a simple {@link UserInfo}, containing just the given {@code userId}
     * and {@code userName}.
     */
    @NonNull
    public static UserInfo newUser(@UserIdInt int userId, @NonNull String userName) {
        return new UserInfoBuilder(userId).setName(userName).build();
    }

    /**
     * Creates a list of {@link UserInfo UserInfos}, each containing just the given user ids.
     */
    @NonNull
    public static List<UserInfo> newUsers(@UserIdInt int... userIds) {
        return Arrays.stream(userIds)
                .mapToObj(id -> newUser(id))
                .collect(Collectors.toList());
    }

    /**
     * Creates a list of {@link UserHandle UserHandles}, each containing just the given user ids.
     */
    @NonNull
    public static List<UserHandle> newUserHandles(@UserIdInt int... userIds) {
        return Arrays.stream(userIds)
                .mapToObj(id -> UserHandle.of(id))
                .collect(Collectors.toList());
    }

    /**
     * Creates a list of {@link UserInfo UserInfos}.
     */
    @NonNull
    public static List<UserInfo> toList(@NonNull UserInfo... users) {
        return Arrays.stream(users).collect(Collectors.toList());
    }

    /**
     * Creates a list of {@link UserHandle UserHandles}.
     */
    @NonNull
    public static List<UserHandle> toList(@NonNull UserHandle... users) {
        return Arrays.stream(users).collect(Collectors.toList());
    }

    /**
     * Creates a {@link UserInfo} with the type explicitly set and with the given {@code userId}.
     */
    @NonNull
    public static UserInfo newSecondaryUser(@UserIdInt int userId) {
        return new UserInfoBuilder(userId).setType(UserManager.USER_TYPE_FULL_SECONDARY).build();
    }

    /**
     * Creates a new guest with the given {@code userId} and proper flags and types set.
     */
    @NonNull
    public static UserInfo newGuestUser(@UserIdInt int userId, boolean ephemeral) {
        return new UserInfoBuilder(userId).setGuest(true).setEphemeral(ephemeral).build();
    }

    /**
     * Creates a new guest with the given {@code userId} and without any flag..
     */
    @NonNull
    public static UserInfo newGuestUser(@UserIdInt int userId) {
        return new UserInfoBuilder(userId).setGuest(true).build();
    }

    /**
     * Gets the default {@link UserInfo#userType} for a guest / regular user.
     */
    @NonNull
    public static String getDefaultUserType(boolean isGuest) {
        return isGuest ? UserManager.USER_TYPE_FULL_GUEST : UserManager.USER_TYPE_FULL_SECONDARY;
    }

    /**
     * Sets the property that defines the maximum number of uses allowed in the device.
     */
    public static void setMaxSupportedUsers(int max) {
        runShellCommand("setprop fw.max_users %d", max);
    }

    /**
     * Configures the user to use PIN credentials.
     */
    public static void setUserLockCredentials(@UserIdInt int userId, int pin) {
        runShellCommand("locksettings set-pin %s --user %d ", pin, userId);
    }

    /**
     * Clears the user credentials using current PIN.
     */
    public static void clearUserLockCredentials(@UserIdInt int userId, int pin) {
        runShellCommand("locksettings clear --old %d --user %d ", pin, userId);
    }

    /**
     * Builder for {@link UserInfo} objects.
     */
    public static final class UserInfoBuilder {

        @UserIdInt
        private final int mUserId;

        @UserInfoFlag
        private int mFlags;

        @Nullable
        private String mName;

        @Nullable
        private String mType;

        private boolean mGuest;
        private boolean mEphemeral;
        private boolean mAdmin;
        private boolean mInitialized;

        /**
         * Default constructor.
         */
        public UserInfoBuilder(@UserIdInt int userId) {
            mUserId = userId;
        }

        /**
         * Sets the user name.
         */
        @NonNull
        public UserInfoBuilder setName(@Nullable String name) {
            mName = name;
            return this;
        }

        /**
         * Sets the user type.
         */
        @NonNull
        public UserInfoBuilder setType(@Nullable String type) {
            Preconditions.checkState(!mGuest, "cannot set type (" + mType + ") after setting it as "
                    + "guest");
            mType = type;
            return this;
        }

        /**
         * Sets whether the user is a guest.
         */
        @NonNull
        public UserInfoBuilder setGuest(boolean guest) {
            Preconditions.checkState(mType == null, "cannot set guest after setting type (" + mType
                    + ")");
            mGuest = guest;
            return this;
        }

        /**
         * Sets the user flags
         */
        @NonNull
        public UserInfoBuilder setFlags(@UserInfoFlag int flags) {
            mFlags = flags;
            return this;
        }

        /**
         * Sets whether the user is ephemeral.
         */
        @NonNull
        public UserInfoBuilder setEphemeral(boolean ephemeral) {
            mEphemeral = ephemeral;
            return this;
        }

        /**
         * Sets whether the user is an admin.
         */
        @NonNull
        public UserInfoBuilder setAdmin(boolean admin) {
            mAdmin = admin;
            return this;
        }

        /**
         * Sets whether the user is initialized.
         */
        @NonNull
        public UserInfoBuilder setInitialized(boolean initialized) {
            mInitialized = initialized;
            return this;
        }

        /**
         * Creates a new {@link UserInfo}.
         */
        @NonNull
        public UserInfo build() {
            int flags = mFlags;
            if (mEphemeral) {
                flags |= UserInfo.FLAG_EPHEMERAL;
            }
            if (mAdmin) {
                flags |= UserInfo.FLAG_ADMIN;
            }
            if (mInitialized) {
                flags |= UserInfo.FLAG_INITIALIZED;
            }
            if (mGuest) {
                mType = UserManager.USER_TYPE_FULL_GUEST;
            }
            UserInfo info = new UserInfo(mUserId, mName, /* iconPath= */ null, flags, mType);
            return info;
        }
    }

    private UserTestingHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
