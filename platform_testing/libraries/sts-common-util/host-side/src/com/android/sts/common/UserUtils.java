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

package com.android.sts.common;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.HashMap;
import java.util.Map;

/** Util to manage secondary user */
public class UserUtils {

    public static class SecondaryUser {
        private ITestDevice mDevice;
        private String mName; // Name of the new user
        private boolean mIsDemo; // User type : --demo
        private boolean mIsEphemeral; // User type : --ephemeral
        private boolean mIsForTesting; // User type : --for-testing
        private boolean mIsGuest; // User type : --guest
        private boolean mIsManaged; // User type : --managed
        private boolean mIsPreCreateOnly; // User type : --pre-created-only
        private boolean mIsRestricted; // User type : --restricted
        private boolean mSwitch; // Switch to newly created user
        private int mProfileOf; // Userid associated with managed user
        private int mTestUserId;
        private Map<String, String> mUserRestrictions; // Map of user-restrictions for new user

        /**
         * Create an instance of secondary user.
         *
         * @param device the device {@link ITestDevice} to use.
         * @throws Exception
         */
        public SecondaryUser(ITestDevice device) throws Exception {
            // Device should not be null
            if (device == null) {
                throw new IllegalArgumentException("Device should not be null");
            }

            // Check if device supports multiple users
            if (!device.isMultiUserSupported()) {
                throw new IllegalStateException("Device does not support multiple users");
            }

            mDevice = device;
            mName = "testUser"; /* Default username */
            mUserRestrictions = new HashMap<String, String>();

            // Set default value for all flags as false
            mIsDemo = false;
            mIsEphemeral = false;
            mIsForTesting = false;
            mIsGuest = false;
            mIsManaged = false;
            mIsPreCreateOnly = false;
            mIsRestricted = false;
            mSwitch = false;
        }

        /**
         * Set the user type as demo.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser demo() {
            mIsDemo = true;
            return this;
        }

        /**
         * Set the user type as ephemeral.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser ephemeral() {
            mIsEphemeral = true;
            return this;
        }

        /**
         * Set the user type as for-testing.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser forTesting() {
            mIsForTesting = true;
            return this;
        }

        /**
         * Set the user type as guest.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser guest() {
            mIsGuest = true;
            return this;
        }

        /**
         * Set the user type as managed.
         *
         * @param profileOf value is set as the userid associated with managed user.
         * @return this object for method chaining.
         */
        public SecondaryUser managed(int profileOf) {
            mIsManaged = true;
            mProfileOf = profileOf;
            return this;
        }

        /**
         * Set the user type as pre-created-only.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser preCreateOnly() {
            mIsPreCreateOnly = true;
            return this;
        }

        /**
         * Set the user type as restricted.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser restricted() {
            mIsRestricted = true;
            return this;
        }

        /**
         * Set the name of the new user.
         *
         * @param name value is set to name of the user.
         * @return this object for method chaining.
         * @throws IllegalArgumentException when {@code name} is null.
         */
        public SecondaryUser name(String name) throws IllegalArgumentException {
            // The argument 'name' should not be null
            if (mName == null) {
                throw new IllegalArgumentException("The name of the user should not be null");
            }

            mName = name;
            return this;
        }

        /**
         * Set if switching to newly created user is required.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser doSwitch() {
            mSwitch = true;
            return this;
        }

        /**
         * Set user-restrictions on newly created secondary user.
         * Note: Setting user-restrictions requires enabling root.
         *
         * @return this object for method chaining.
         */
        public SecondaryUser withUserRestrictions(Map<String, String> restrictions) {
            mUserRestrictions.putAll(restrictions);
            return this;
        }

        /**
         * Create a secondary user and if required, switch to it. Returns an Autocloseable that
         * removes the secondary user.
         *
         * @return AutoCloseable that switches back to the caller user if required, and removes the
         *     secondary user.
         * @throws Exception
         */
        public AutoCloseable withUser() throws Exception {
            // Fetch the caller's user id
            final int callerUserId = mDevice.getCurrentUser();

            // Command to create user
            String command =
                    "pm create-user "
                            + (mIsDemo ? "--demo " : "")
                            + (mIsEphemeral ? "--ephemeral " : "")
                            + (mIsGuest ? "--guest " : "")
                            + (mIsManaged ? ("--profileOf " + mProfileOf + " --managed ") : "")
                            + (mIsPreCreateOnly ? "--pre-create-only " : "")
                            + (mIsRestricted ? "--restricted " : "")
                            + (mIsForTesting && mDevice.getApiLevel() >= 34 ? "--for-testing " : "")
                            + mName;

            // Create a new user
            final CommandResult output = mDevice.executeShellV2Command(command);
            if (output.getStatus() != CommandStatus.SUCCESS) {
                throw new IllegalStateException(
                        String.format("Failed to create user, due to : %s", output.toString()));
            }
            final String outputStdout = output.getStdout();
            mTestUserId =
                    Integer.parseInt(outputStdout.substring(outputStdout.lastIndexOf(" ")).trim());

            AutoCloseable asSecondaryUser =
                    () -> {
                        // Switch back to the caller user if required and the user type is
                        // neither managed nor pre-created-only
                        if (mSwitch && !mIsManaged && !mIsPreCreateOnly) {
                            mDevice.switchUser(callerUserId);
                        }

                        // Stop and remove the user if user type is not ephemeral
                        if (!mIsEphemeral) {
                            mDevice.stopUser(mTestUserId);
                            mDevice.removeUser(mTestUserId);
                        }
                    };

            // Start the user
            if (!mDevice.startUser(mTestUserId, true /* waitFlag */)) {
                // Remove the user
                asSecondaryUser.close();
                throw new IllegalStateException(
                        String.format("Failed to start the user: %s", mTestUserId));
            }

            // Add user-restrictions to newly created secondary user
            if (!mUserRestrictions.isEmpty()) {
                if (!mDevice.isAdbRoot()) {
                    throw new IllegalStateException("Setting user-restriction requires root");
                }

                for (Map.Entry<String, String> entry : mUserRestrictions.entrySet()) {
                    final CommandResult cmdOutput =
                            mDevice.executeShellV2Command(
                                    String.format(
                                            "pm set-user-restriction --user %d %s %s",
                                            mTestUserId, entry.getKey(), entry.getValue()));
                    if (cmdOutput.getStatus() != CommandStatus.SUCCESS) {
                        asSecondaryUser.close();
                        throw new IllegalStateException(
                                String.format(
                                        "Failed to set user restriction %s value %s with"
                                                + " message %s",
                                        entry.getKey(), entry.getValue(), cmdOutput.toString()));
                    }
                }
            }

            // Switch to the user if required and the user type is neither managed nor
            // pre-created-only
            if (mSwitch && !mIsManaged && !mIsPreCreateOnly && !mDevice.switchUser(mTestUserId)) {
                // Stop and remove the user
                asSecondaryUser.close();
                throw new IllegalStateException(
                        String.format("Failed to switch the user: %s", mTestUserId));
            }
            return asSecondaryUser;
        }
    }
}
