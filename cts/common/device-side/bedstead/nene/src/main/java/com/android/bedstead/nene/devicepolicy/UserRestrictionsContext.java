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

package com.android.bedstead.nene.devicepolicy;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;

import java.util.HashSet;
import java.util.Set;

/**
 * A context where some user restrictions must be set or unset.
 *
 * <p>The state will be undone when closing the context.
 */
public final class UserRestrictionsContext implements AutoCloseable {

    private static final int SET = 1;
    private static final int UNSET = 0;

    private final UserReference mUser;

    private Set<String> mToSetUserRestrictions = new HashSet<>();
    private Set<String> mToUnsetUserRestrictions = new HashSet<>();

    private Set<String> mSetUserRestrictions = new HashSet<>();
    private Set<String> mUnsetUserRestrictions = new HashSet<>();

    UserRestrictionsContext(UserReference user) {
        mUser = user;
    }

    /**
     * Set the restriction.
     */
    public UserRestrictionsContext set(String restriction) {
        mToSetUserRestrictions.add(restriction);
        apply();
        return this;
    }

    /**
     * Unset the restriction.
     */
    public UserRestrictionsContext unset(String restriction) {
        mToUnsetUserRestrictions.add(restriction);
        apply();
        return this;
    }

    private void apply() {
        for (String restriction : mToSetUserRestrictions) {
            if (mSetUserRestrictions.contains(restriction)
                    || TestApis.devicePolicy().userRestrictions(mUser).isSet(restriction)) {
                // Already set - don't do anything
            }

            setNoCheck(restriction);
        }
        for (String restriction : mToUnsetUserRestrictions) {
            if (mUnsetUserRestrictions.contains(restriction)
                    || !TestApis.devicePolicy().userRestrictions(mUser).isSet(restriction)) {
                // Already unset - don't do anything
            }

            unsetNoCheck(restriction);
        }
    }

    private void setNoCheck(String restriction) {
        ShellCommand.builderForUser(mUser, "pm set-user-restriction")
                .addOperand(restriction)
                .addOperand(SET)
                .validate(String::isEmpty)
                .allowEmptyOutput(true)
                .executeOrThrowNeneException("Error setting user restriction " + restriction);
        mSetUserRestrictions.add(restriction);
    }

    private void unsetNoCheck(String restriction) {
        ShellCommand.builderForUser(mUser, "pm set-user-restriction")
                .addOperand(restriction)
                .addOperand(UNSET)
                .validate(String::isEmpty)
                .allowEmptyOutput(true)
                .executeOrThrowNeneException("Error setting user restriction " + restriction);
        mUnsetUserRestrictions.add(restriction);
    }

    @Override
    public void close()  {
        for (String restriction : mSetUserRestrictions) {
            unsetNoCheck(restriction);
        }
        for (String restriction : mUnsetUserRestrictions) {
            setNoCheck(restriction);
        }
    }
}
