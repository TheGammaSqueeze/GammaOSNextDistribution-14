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

package com.android.bedstead.testapp;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;

/**
 * Utilities relied on by generated code
 */
public final class Utils {

    /**
     * Show the most appropriate error when a "no existing activity" exception is thrown.
     */
    public static NeneException dealWithNoExistingActivityException(
            String activityClassName, Throwable t) {
        String reasons = "";
        if (!TestApis.users().instrumented().canShowActivities()) {
            reasons += ". Running on user which cannot show activities ("
                    + TestApis.users().instrumented() + ") ";
        }

        return new NeneException("Error finding activity " + activityClassName + reasons
                + ". Relevant logcat: "
        + TestApis.logcat().dump(
                l -> l.contains(activityClassName) || l.contains("ActivityTaskManager")), t);
    }

    /**
     * Show the most appropriate error when an exception is thrown by the Connected Apps SDK.
     */
    public static NeneException dealWithConnectedAppsSdkException(
            UserReference user, Package pkg, Throwable t) {
        if (t.getMessage().contains("Profile not available")) {
            // This is the general connection error - it's confusing to put it in the cause and
            // doesn't generally contain anything useful for debugging TestApp

            String reasons = "";

            if (!user.isRunning()) {
                reasons += "User " + user + " is not running.";
            }
            if (!user.isUnlocked()) {
                reasons += "User " + user + " is not unlocked.";
            }
            if (!pkg.installedOnUser(user)) {
                reasons += "Package " + pkg + " is not installed on User " + user + ".";
            }

            return new NeneException("Error connecting to test app: " + reasons
                    + ". Relevant logcat: "
                    + TestApis.logcat().dump(
                            l -> l.contains("TestAppBinder")
                                    || l.contains("CrossProfileSender")
                                    || l.contains("TestAppConnector")
                                    || l.contains(pkg.packageName())));
        }

        return new NeneException("Error connecting to test app", t);
    }

}
