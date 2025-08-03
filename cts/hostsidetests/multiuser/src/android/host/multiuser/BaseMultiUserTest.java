/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */
package android.host.multiuser;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for multi user tests.
 */
// Must be public because of @Rule
public abstract class BaseMultiUserTest extends BaseHostJUnit4Test {

    /** Guest flag value from android/content/pm/UserInfo.java */
    private static final int FLAG_GUEST = 0x00000004;

    /**
     * Feature flag for automotive devices
     * https://source.android.com/compatibility/android-cdd#2_5_automotive_requirements
     */
    private static final String FEATURE_AUTOMOTIVE = "feature:android.hardware.type.automotive";

    protected static final String TEST_APP_PKG_NAME = "com.android.cts.multiuser";
    protected static final String TEST_APP_PKG_APK = "CtsMultiuserApp.apk";

    protected static final long LOGCAT_POLL_INTERVAL_MS = 1000; // 1 second
    protected static final long USER_REMOVAL_COMPLETE_TIMEOUT_MS = 8 * 60 * 1000; // 8 minutes

    /** Whether multi-user is supported. */
    protected int mInitialUserId;
    protected int mPrimaryUserId;

    /** Users we shouldn't delete in the tests. */
    private ArrayList<Integer> mFixedUsers;

    @Rule
    public final TestName mTestNameRule = new TestName();

    @Before
    public void setUp() throws Exception {
        mInitialUserId = getDevice().getCurrentUser();
        mPrimaryUserId = getDevice().getPrimaryUserId();

        // Test should not modify / remove any of the existing users.
        mFixedUsers = getDevice().listUsers();
    }

    @After
    public void tearDown() throws Exception {
        int currentUserId = getDevice().getCurrentUser();
        if (currentUserId != mInitialUserId) {
            CLog.w("User changed during test (to %d). Switching back to %d", currentUserId,
                    mInitialUserId);
            getDevice().switchUser(mInitialUserId);
        }
        // Remove the users created during this test.
        removeTestUsers();
    }

    protected String getTestName() {
        return mTestNameRule.getMethodName();
    }

    protected void assumeNotRoot() throws DeviceNotAvailableException {
        if (!getDevice().isAdbRoot()) return;

        String message = "Cannot test " + getTestName() + " on rooted devices";
        CLog.logAndDisplay(Log.LogLevel.WARN, message);
        throw new AssumptionViolatedException(message);
    }

    protected int createRestrictedProfile(int userId)
            throws DeviceNotAvailableException, IllegalStateException{
        final String command = "pm create-user --profileOf " + userId + " --restricted "
                + "TestUser_" + System.currentTimeMillis();
        final String output = getDevice().executeShellCommand(command);

        if (output.startsWith("Success")) {
            try {
                return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            } catch (NumberFormatException e) {
                CLog.e("Failed to parse result: %s", output);
            }
        } else {
            CLog.e("Failed to create restricted profile: %s", output);
        }
        throw new IllegalStateException();
    }

    protected int createGuestUser() throws Exception {
        return getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                true /* guest */,
                false /* ephemeral */);
    }

    protected int getGuestUser() throws Exception {
        for (int userId : getDevice().listUsers()) {
            if ((getDevice().getUserFlags(userId) & FLAG_GUEST) != 0) {
                return userId;
            }
        }
        return -1;
    }

    protected void assumeGuestDoesNotExist() throws Exception {
        assumeTrue("Device already has a guest user", getGuestUser() == -1);
    }

    protected void assumeIsAutomotive() throws Exception {
        assumeTrue("Device does not have " + FEATURE_AUTOMOTIVE,
                getDevice().hasFeature(FEATURE_AUTOMOTIVE));
    }

    protected void assertSwitchToUser(int toUserId) throws Exception {
        final boolean switchResult = getDevice().switchUser(toUserId);
        assertWithMessage("Couldn't switch to user %s", toUserId)
                .that(switchResult).isTrue();

        final int currentUserId = getDevice().getCurrentUser();
        assertWithMessage("Current user is %s, after switching to user %s", currentUserId, toUserId)
                .that(currentUserId).isEqualTo(toUserId);
    }

    protected void assertUserNotPresent(int userId) throws Exception {
        assertWithMessage("User ID %s should not be present", userId)
                .that(getDevice().listUsers()).doesNotContain(userId);
    }

    protected void assertUserPresent(int userId) throws Exception {
        assertWithMessage("User ID %s should be present", userId)
                .that(getDevice().listUsers()).contains(userId);
    }

    /*
     * Waits for userId to removed or at removing state.
     * Returns true if user is removed or at removing state.
     * False if user is not removed by USER_SWITCH_COMPLETE_TIMEOUT_MS.
     */
    protected boolean waitForUserRemove(int userId)
            throws DeviceNotAvailableException, InterruptedException {
        // Example output from dumpsys when user is flagged for removal:
        // UserInfo{11:Driver:154} serialNo=50 <removing>  <partial>
        final String userSerialPatter = "(.*\\{)(\\d+)(.*\\})(.*=)(\\d+)(.*)";
        final Pattern pattern = Pattern.compile(userSerialPatter);
        long ti = System.currentTimeMillis();
        while (System.currentTimeMillis() - ti < USER_REMOVAL_COMPLETE_TIMEOUT_MS) {
            if (!getDevice().listUsers().contains(userId)) {
                return true;
            }
            String commandOutput = getDevice().executeShellCommand("dumpsys user");
            Matcher matcher = pattern.matcher(commandOutput);
            while(matcher.find()) {
                if (Integer.parseInt(matcher.group(2)) == userId
                        && matcher.group(6).contains("removing")) {
                    return true;
                }
            }
            RunUtil.getDefault().sleep(LOGCAT_POLL_INTERVAL_MS);
        }
        return false;
    }

    private void removeTestUsers() throws Exception {
        for (int userId : getDevice().listUsers()) {
            if (!mFixedUsers.contains(userId)) {
                getDevice().removeUser(userId);
            }
        }
    }

    static class AppCrashOnBootError extends AssertionError {
        private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("package ([^\\s]+)");
        private Set<String> errorPackages;

        AppCrashOnBootError(Set<String> errorLogs) {
            super("App error dialog(s) are present: " + errorLogs);
            this.errorPackages = errorLogsToPackageNames(errorLogs);
        }

        private static Set<String> errorLogsToPackageNames(Set<String> errorLogs) {
            Set<String> result = new HashSet<>();
            for (String line : errorLogs) {
                Matcher matcher = PACKAGE_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    result.add(matcher.group(1));
                } else {
                    throw new IllegalStateException("Unrecognized line " + line);
                }
            }
            return result;
        }
    }

    /**
     * Rule that retries the test if it failed due to {@link AppCrashOnBootError}
     */
    public static class AppCrashRetryRule implements TestRule {

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Set<String> errors = evaluateAndReturnAppCrashes(base);
                    if (errors.isEmpty()) {
                        CLog.v("Good News, Everyone! No App crashes on %s",
                                description.getMethodName());
                        return;
                    }
                    CLog.e("Retrying due to app crashes: %s", errors);
                    // Fail only if same apps are crashing in both runs
                    errors.retainAll(evaluateAndReturnAppCrashes(base));
                    assertWithMessage("App error dialog(s) are present after 2 attempts")
                            .that(errors).isEmpty();
                }
            };
        }

        private static Set<String> evaluateAndReturnAppCrashes(Statement base) throws Throwable {
            try {
                base.evaluate();
            } catch (AppCrashOnBootError e) {
                return e.errorPackages;
            }
            return new HashSet<>();
        }
    }

    /**
     * Rule that skips a test if device does not support more than 1 user
     */
    protected static class SupportsMultiUserRule implements TestRule {

        private final BaseHostJUnit4Test mDeviceTest;

        SupportsMultiUserRule(BaseHostJUnit4Test deviceTest) {
            mDeviceTest = deviceTest;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    boolean supports = mDeviceTest.getDevice().getMaxNumberOfUsersSupported() > 1;
                    assumeTrue("device does not support multi users", supports);

                    base.evaluate();
                }
            };
        }
    }
}
