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

import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.bedstead.nene.users.Users.SYSTEM_USER_ID;

import android.os.Build;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;

import java.util.UUID;

/**
 * Builder for creating a new Android User.
 */
public final class UserBuilder {

    private String mName;
    private @Nullable UserType mType;
    private @Nullable UserReference mParent;
    private boolean mForTesting = true;

    private static final String LOG_TAG = "UserBuilder";

    UserBuilder() {
    }

    /**
     * Set the user's name.
     */
    @CheckResult
    public UserBuilder name(String name) {
        if (name == null) {
            throw new NullPointerException();
        }
        mName = name;
        return this;
    }

    /**
     * Set the {@link UserType}.
     *
     * <p>Defaults to android.os.usertype.full.SECONDARY
     */
    @CheckResult
    public UserBuilder type(UserType type) {
        if (type == null) {
            // We don't want to allow null to be passed in explicitly as that would cause subtle
            // bugs when chaining with .supportedType() which can return null
            throw new NullPointerException("Can not set type to null");
        }
        mType = type;
        return this;
    }

    /**
     * Set the {@link UserType}.
     *
     * <p>Defaults to android.os.usertype.full.SECONDARY
     */
    @CheckResult
    public UserBuilder type(String typeName) {
        if (typeName == null) {
            // We don't want to allow null to be passed in explicitly as that would cause subtle
            // bugs when chaining with .supportedType() which can return null
            throw new NullPointerException("Can not set type to null");
        }
        return type(TestApis.users().supportedType(typeName));
    }

    /**
     * Set if this user should be marked as for-testing.
     *
     * <p>This means it should not contain human user data - and will ensure it does not block
     * usage of some test functionality
     *
     * <p>This defaults to true
     */
    @CheckResult
    public UserBuilder forTesting(boolean forTesting) {
        mForTesting = forTesting;
        return this;
    }

    /**
     * Set the parent of the new user.
     *
     * <p>This should only be set if the {@link #type(UserType)} is a profile.
     */
    @CheckResult
    public UserBuilder parent(UserReference parent) {
        mParent = parent;
        return this;
    }

    /** Create the user. */
    public UserReference create() {
        if (mName == null) {
            mName = UUID.randomUUID().toString();
        }

        ShellCommand.Builder commandBuilder = ShellCommand.builder("pm create-user");

        if (mType != null) {
            if (mType.baseType().contains(UserType.BaseType.SYSTEM)) {
                throw new NeneException(
                        "Can not create additional system users " + this);
            }

            if (mType.baseType().contains(UserType.BaseType.PROFILE)) {
                if (mParent == null) {
                    throw new NeneException("When creating a profile, the parent user must be"
                            + " specified");
                }

                commandBuilder.addOption("--profileOf", mParent.id());
            } else if (mParent != null) {
                throw new NeneException("A parent should only be specified when create profiles");
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                if (mType.name().equals(MANAGED_PROFILE_TYPE_NAME)) {
                    if (mParent.id() != SYSTEM_USER_ID) {
                        // On R, this error will be thrown when we execute the command
                        throw new NeneException(
                                "Can not create managed profiles of users other than the "
                                        + "system user"
                        );
                    }

                    commandBuilder.addOperand("--managed");
                } else if (!mType.name().equals(SECONDARY_USER_TYPE_NAME)) {
                    // This shouldn't be reachable as before R we can't fetch a list of user types
                    //  so the only supported ones are system/managed profile/secondary
                    throw new NeneException(
                            "Can not create users of type " + mType + " on this device");
                }
            } else {
                commandBuilder.addOption("--user-type", mType.name());
            }
        }

        if (Versions.meetsMinimumSdkVersionRequirement(Versions.U) && mForTesting) {
            // Marking all created users as test users means we don't block changing device
            // management states
            commandBuilder.addOperand("--for-testing");
        }

        commandBuilder.addOperand(mName);

        // Expected success string is e.g. "Success: created user id 14"
        try {

            Log.d(LOG_TAG, "Creating user with command " + commandBuilder);
            int userId =
                    commandBuilder.validate(ShellCommandUtils::startsWithSuccess)
                            .executeAndParseOutput(
                                    (output) -> Integer.parseInt(output.split("id ")[1].trim()));
            return TestApis.users().find(userId);
        } catch (AdbException e) {
            throw new NeneException("Could not create user " + this, e);
        }
    }

    /**
     * Create the user and start it.
     *
     * <p>Equivalent of calling {@link #create()} and then {@link User#start()}.
     */
    public UserReference createAndStart() {
        return create().start();
    }

    @Override
    public String toString() {
        return new StringBuilder("UserBuilder{")
            .append("name=").append(mName)
            .append(", type=").append(mType)
            .append("}")
            .toString();
    }
}
