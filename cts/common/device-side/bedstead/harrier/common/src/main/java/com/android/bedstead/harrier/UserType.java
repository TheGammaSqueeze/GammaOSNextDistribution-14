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

package com.android.bedstead.harrier;

/**
 * A type of user for use with Harrier.
 *
 * <p>Generally, you should prefer to use the abstract user types (such as {@link #INITIAL_USER})
 * over specific types such as {@link #PRIMARY_USER} to enable running the test on as many
 * devices as possible.
 */
public enum UserType {
    /** No restriction on user. */
    ANY,

    // Basic user types

    /**
     * The system user. This contains all system services.
     *
     * <p>Note that the system user is not the same as the {@link UserType#INITIAL_USER} on all
     * devices. If the intent is to run this test on a user with user apps and data, use
     * {@link UserType#INITIAL_USER}.
     */
    SYSTEM_USER,

    /**
     * A user with type {@code android.os.UserManager#USER_TYPE_FULL_SECONDARY}.
     *
     * <p>Note that on some devices, this may be the same as {@link UserType#INITIAL_USER}. If you
     * need a user that is different from {@link UserType#INITIAL_USER} you should use
     * {@link UserType#ADDITIONAL_USER}.
     */
    SECONDARY_USER,

    /**
     * A user with type {@code USER_TYPE_PROFILE_MANAGED} and which has a Profile Owner.
     *
     * <p>The parent of this profile will be {@link UserType#INITIAL_USER}.
     */
    // TODO(b/210869636): split work profile from managed_profile/profile
    WORK_PROFILE,

    /**
     * A user with type {@code com.android.tv.profile}.
     *
     * <p>The parent of this profile will be {@link UserType#INITIAL_USER}.
     */
    TV_PROFILE,

    /**
     * A user with the "primary" flag set to true.
     *
     * @deprecated This type of user will not exist on some Android devices.
     *  {@link UserType#INITIAL_USER} serves largely the same purpose but works on all devices. You
     *  can continue to use {@link #PRIMARY_USER} but should make sure you absolutely want to only
     *  support primary users.
     */
    @Deprecated
    PRIMARY_USER,

    // Abstract user types

    /** The user running the instrumented test process. */
    INSTRUMENTED_USER,

    /** The user in the foreground. */
    CURRENT_USER,

    /** The user with the primary DPC installed. */
    DPC_USER,

    /** The user of the first person using the device. This will be the parent of any profiles. */
    INITIAL_USER,

    /** A {@link UserType#SECONDARY_USER} who is not the {@link UserType#INITIAL_USER}. */
    ADDITIONAL_USER,

    /** A user for whom {@code UserReference#isAdmin} returns true. */
    ADMIN_USER,

    /**
     * A user with type {@code android.os.usertype.profile.CLONE}.
     *
     * <p>The parent of this profile will be {@link UserType#INITIAL_USER}.
     */
    CLONE_PROFILE,

    /**
     * A user with type {@code android.os.usertype.profile.PRIVATE}.
     *
     * <p>The parent of this profile will be {@link UserType#INITIAL_USER}.
     */
    PRIVATE_PROFILE
}
