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

package android.car.cts;

import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.service.pm.PackageProto;
import android.service.pm.PackageProto.UserPermissionsProto;
import android.service.pm.PackageServiceDumpProto;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.CommonTestUtils.BooleanSupplierWithThrow;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Base class for all test cases.
 */
// NOTE: must be public because of @Rules
public abstract class CarHostJUnit4TestCase extends BaseHostJUnit4Test {

    private static final int SYSTEM_USER_ID = 0;

    private static final int DEFAULT_TIMEOUT_SEC = 20;
    protected static final int SYSTEM_RESTART_TIMEOUT_SEC = 120;

    private static final Pattern CREATE_USER_OUTPUT_PATTERN = Pattern.compile("id=(\\d+)");

    private static final String USER_PREFIX = "CtsCarHostTestCases";

    /**
     * User pattern in the output of "cmd user list --all -v"
     * TEXT id=<id> TEXT name=<name>, TEX flags=<flags> TEXT
     * group 1: id group 2: name group 3: flags group 4: other state(like "(running)")
     */
    private static final Pattern USER_PATTERN = Pattern.compile(
            ".*id=(\\d+).*name=([^\\s,]+).*flags=(\\S+)(.*)");

    private static final int USER_PATTERN_GROUP_ID = 1;
    private static final int USER_PATTERN_GROUP_NAME = 2;
    private static final int USER_PATTERN_GROUP_FLAGS = 3;
    private static final int USER_PATTERN_GROUP_OTHER_STATE = 4;

    /**
     * User's package permission pattern string format in the output of "dumpsys package PKG_NAME"
     */
    protected static final String APP_APK = "CtsCarApp.apk";
    protected static final String APP_PKG = "android.car.cts.app";

    @Rule
    public final RequiredFeatureRule mHasAutomotiveRule = new RequiredFeatureRule(this,
            "android.hardware.type.automotive");

    private final HashSet<Integer> mUsersToBeRemoved = new HashSet<>();

    private int mInitialUserId;
    private int mTestRunningUserId;
    private Integer mInitialMaximumNumberOfUsers;

    // It is possible that during test initial user is deleted and it is not possible to switch
    // to the initial User. This boolean controls if test should switch to initial user on clean up.
    private boolean mSwitchToInitialUser = true;

    /**
     * Saves multi-user state so it can be restored after the test.
     */
    @Before
    public void saveUserState() throws Exception {
        removeUsers(USER_PREFIX);

        mInitialUserId = getCurrentUserId();

        // The test runs as the current user in most cases. For secondary_user_on_secondary_display
        // case, we set mTestRunningUserId from RUN_TEST_AS_USER.
        mTestRunningUserId = getCurrentUserId();
        if (getDevice().isVisibleBackgroundUsersSupported()) {
            try {
                mTestRunningUserId = Integer.parseInt(
                        getTestInformation().properties().get(RUN_TESTS_AS_USER_KEY));
            } catch (Exception e) {
                CLog.e("Failed to parse the userId for " + RUN_TESTS_AS_USER_KEY + " due to " + e);
            }
        }
    }

    /**
     * Restores multi-user state from before the test.
     */
    @After
    public void restoreUsersState() throws Exception {
        int currentUserId = getCurrentUserId();
        CLog.d("restoreUsersState(): initial user: %d, current user: %d, created users: %s "
                + "max number of users: %d",
                mInitialUserId, currentUserId, mUsersToBeRemoved, mInitialMaximumNumberOfUsers);
        if (currentUserId != mInitialUserId && mSwitchToInitialUser) {
            CLog.i("Switching back from %d to %d", currentUserId, mInitialUserId);
            switchUser(mInitialUserId);
        }

        if (!mUsersToBeRemoved.isEmpty()) {
            CLog.i("Removing users %s", mUsersToBeRemoved);
            for (int userId : mUsersToBeRemoved) {
                removeUser(userId);
            }
        }

        // Should have been removed above, but as the saying goes, better safe than sorry...
        removeUsers(USER_PREFIX);

        if (mInitialMaximumNumberOfUsers != null) {
            CLog.i("Restoring max number of users to %d", mInitialMaximumNumberOfUsers);
            setMaxNumberUsers(mInitialMaximumNumberOfUsers);
        }
    }

    /**
     * It is possible that during test initial user is deleted and it is not possible to switch to
     * the initial User. This method controls if test should switch to initial user on clean up.
     */
    public void doNotSwitchToInitialUserAfterTest() {
        mSwitchToInitialUser = false;
    }

    /**
     * Returns whether device is in headless system user mode.
     */
    boolean isHeadlessSystemUserMode() throws Exception {
        return getDevice().isHeadlessSystemUserMode();
    }

    /**
     * Makes sure the device supports multiple users, throwing {@link AssumptionViolatedException}
     * if it doesn't.
     */
    protected final void assumeSupportsMultipleUsers() throws Exception {
        assumeTrue("device does not support multi-user",
                getDevice().getMaxNumberOfUsersSupported() > 1);
    }

    /**
     * Makes sure the device can add {@code numberOfUsers} new users, increasing limit if needed or
     * failing if not possible.
     */
    protected final void requiresExtraUsers(int numberOfUsers) throws Exception {
        assumeSupportsMultipleUsers();

        int maxNumber = getDevice().getMaxNumberOfUsersSupported();
        int currentNumber = getDevice().listUsers().size();

        if (currentNumber + numberOfUsers <= maxNumber) return;

        if (!getDevice().isAdbRoot()) {
            failCannotCreateUsers(numberOfUsers, currentNumber, maxNumber, /* isAdbRoot= */ false);
        }

        // Increase limit...
        mInitialMaximumNumberOfUsers = maxNumber;
        setMaxNumberUsers(maxNumber + numberOfUsers);

        // ...and try again
        maxNumber = getDevice().getMaxNumberOfUsersSupported();
        if (currentNumber + numberOfUsers > maxNumber) {
            failCannotCreateUsers(numberOfUsers, currentNumber, maxNumber, /* isAdbRoot= */ true);
        }
    }

    private void failCannotCreateUsers(int numberOfUsers, int currentNumber, int maxNumber,
            boolean isAdbRoot) {
        String reason = isAdbRoot ? "failed to increase it"
                : "cannot be increased without adb root";
        String existingUsers = "";
        try {
            existingUsers = "Existing users: " + executeCommand("cmd user list --all -v");
        } catch (Exception e) {
            // ignore
        }
        fail("Cannot create " + numberOfUsers + " users: current number is " + currentNumber
                + ", limit is " + maxNumber + " and could not be increased (" + reason + "). "
                + existingUsers);
    }

    /**
     * Executes the shell command and returns the output.
     */
    protected String executeCommand(String command, Object... args) throws Exception {
        String fullCommand = String.format(command, args);
        return getDevice().executeShellCommand(fullCommand);
    }

    /**
     * Executes the shell command and parses output with {@code resultParser}.
     */
    protected <T> T executeAndParseCommand(Function<String, T> resultParser,
            String command, Object... args) throws Exception {
        String output = executeCommand(command, args);
        return resultParser.apply(output);
    }

    /**
     * Executes the shell command and parses the Matcher output with {@code resultParser}, failing
     * with {@code matchNotFoundErrorMessage} if it didn't match the {@code regex}.
     */
    protected <T> T executeAndParseCommand(Pattern regex, String matchNotFoundErrorMessage,
            Function<Matcher, T> resultParser,
            String command, Object... args) throws Exception {
        String output = executeCommand(command, args);
        Matcher matcher = regex.matcher(output);
        if (!matcher.find()) {
            fail(matchNotFoundErrorMessage + ". Shell command: '" + String.format(command, args)
                    + "'. Output: " + output.trim() + ". Regex: " + regex);
        }
        return resultParser.apply(matcher);
    }

    /**
     * Executes the shell command and parses the Matcher output with {@code resultParser}.
     */
    protected <T> T executeAndParseCommand(Pattern regex, Function<Matcher, T> resultParser,
            String command, Object... args) throws Exception {
        String output = executeCommand(command, args);
        return resultParser.apply(regex.matcher(output));
    }

    /**
     * Executes the shell command that returns all users (including pre-created and partial)
     * and returns {@code function} applied to them.
     */
    public <T> T onAllUsers(Function<List<UserInfo>, T> function) throws Exception {
        ArrayList<UserInfo> allUsers = executeAndParseCommand(USER_PATTERN, (matcher) -> {
            ArrayList<UserInfo> users = new ArrayList<>();
            while (matcher.find()) {
                users.add(new UserInfo(matcher));
            }
            return users;
        }, "cmd user list --all -v");
        return function.apply(allUsers);
    }

    /**
     * Gets the info for the given user.
     */
    public UserInfo getUserInfo(int userId) throws Exception {
        return onAllUsers((allUsers) -> allUsers.stream()
                .filter((u) -> u.id == userId))
                        .findFirst().get();
    }

    /**
     * Gets all persistent (i.e., non-ephemeral) users.
     */
    protected List<Integer> getAllPersistentUsers() throws Exception {
        return onAllUsers((allUsers) -> allUsers.stream()
                .filter((u) -> !u.flags.contains("DISABLED") && !u.flags.contains("EPHEMERAL")
                        && !u.otherState.contains("pre-created")
                        && !u.otherState.contains("partial"))
                .map((u) -> u.id).collect(Collectors.toList()));
    }

    /**
     * Sets the maximum number of users that can be created for this car.
     *
     * @throws IllegalStateException if adb is not running as root
     */
    protected void setMaxNumberUsers(int numUsers) throws Exception {
        if (!getDevice().isAdbRoot()) {
            throw new IllegalStateException("must be running adb root");
        }
        executeCommand("setprop fw.max_users %d", numUsers);
    }

    /**
     * Gets the user's id that is running the test.
     *
     * <p>The tests run as the current user so this is same as {@link #getCurrentUserId()} in most
     * cases. For secondary_user_on_secondary_display case, this is returned from RUN_TEST_AS_USER.
     */
    protected int getTestRunningUserId()  {
        return mTestRunningUserId;
    }

    /**
     * Gets the current user's id.
     */
    protected int getCurrentUserId() throws DeviceNotAvailableException {
        return getDevice().getCurrentUser();
    }

    /**
     * Creates a full user with car service shell command.
     */
    protected int createFullUser(String name) throws Exception {
        return createUser(name, /* flags= */ 0, /* isGuest= */ false);
    }

    /**
     * Creates a full guest with car service shell command.
     */
    protected int createGuestUser(String name) throws Exception {
        return createUser(name, /* flags= */ 0, /* isGuest= */ true);
    }

    /**
     * Creates a flexible user with car service shell command.
     *
     * <p><b>NOTE: </b>it uses User HAL flags, not core Android's.
     */
    protected int createUser(String name, int flags, boolean isGuest) throws Exception {
        name = USER_PREFIX + "." + name;
        waitForCarServiceReady();
        int userId = executeAndParseCommand(CREATE_USER_OUTPUT_PATTERN,
                "Could not create user with name " + name
                        + ", flags " + flags + ", guest " + isGuest,
                matcher -> Integer.parseInt(matcher.group(1)),
                "cmd car_service create-user --flags %d %s%s",
                flags, (isGuest ? "--guest " : ""), name);
        markUserForRemovalAfterTest(userId);
        return userId;
    }

    /**
     * Marks a user to be removed at the end of the tests.
     */
    protected void markUserForRemovalAfterTest(int userId) {
        mUsersToBeRemoved.add(userId);
    }

    /**
     * Waits until the given user is initialized.
     */
    protected void waitForUserInitialized(int userId) throws Exception {
        CommonTestUtils.waitUntil("timed out waiting for user " + userId + " initialization",
                DEFAULT_TIMEOUT_SEC, () -> isUserInitialized(userId));
    }

    /**
     * Waits until the system server is ready.
     */
    protected void waitForCarServiceReady() throws Exception {
        CommonTestUtils.waitUntil("timed out waiting for car service",
                DEFAULT_TIMEOUT_SEC, () -> isCarServiceReady());
    }

    protected boolean isCarServiceReady() {
        String cmd = "service check car_service";
        try {
            String output = getDevice().executeShellCommand(cmd);
            return !output.endsWith("not found");
        } catch (Exception e) {
            CLog.i("%s failed: %s", cmd, e.getMessage());
        }
        return false;
    }

    /**
     * Asserts that the given user is initialized.
     */
    protected void assertUserInitialized(int userId) throws Exception {
        assertWithMessage("User %s not initialized", userId).that(isUserInitialized(userId))
                .isTrue();
        CLog.v("User %d is initialized", userId);
    }

    /**
     * Checks if the given user is initialized.
     */
    protected boolean isUserInitialized(int userId) throws Exception {
        UserInfo userInfo = getUserInfo(userId);
        CLog.v("isUserInitialized(%d): %s", userId, userInfo);
        return userInfo.flags.contains("INITIALIZED");
    }

    /**
     * Checks if the given user is ephemeral.
     */
    protected boolean isUserEphemeral(int userId) throws Exception {
        UserInfo userInfo = getUserInfo(userId);
        CLog.v("isUserEphemeral(%d): %s", userId, userInfo);
        return userInfo.flags.contains("EPHEMERAL");
    }

    /**
     * Switches the current user.
     */
    protected void switchUser(int userId) throws Exception {
        waitForCarServiceReady();
        String output = executeCommand("cmd car_service switch-user %d", userId);
        if (!output.contains("STATUS_SUCCESSFUL")) {
            throw new IllegalStateException("Failed to switch to user " + userId + ": " + output);
        }
        waitUntilCurrentUser(userId);
    }

    /**
     * Waits until the given user is the current foreground user.
     */
    protected void waitUntilCurrentUser(int userId) throws Exception {
        CommonTestUtils.waitUntil("timed out (" + DEFAULT_TIMEOUT_SEC
                + "s) waiting for current user to be " + userId
                + " (it is " + getCurrentUserId() + ")", DEFAULT_TIMEOUT_SEC,
                () -> (getCurrentUserId() == userId));
    }

    /**
     * Waits until the current user is not the system user.
     */
    protected  void waitUntilCurrentUserIsNotSystem(int timeoutSec) throws Exception {
        CommonTestUtils.waitUntil("timed out (" + timeoutSec + "s) waiting for current user to NOT "
                + "be the system user", timeoutSec,  () -> getCurrentUserId() != SYSTEM_USER_ID);
    }

    /**
     * Waits until {@code n} persistent (i.e., non-ephemeral) users are available.
     */
    protected void waitUntilAtLeastNPersistentUsersAreAvailable(int timeoutSec, int n)
            throws Exception {
        waitUntil(timeoutSec, () -> getAllPersistentUsers().size() >= n, "%d persistent users", n);
    }

    /**
     * Waits until the given condition is reached.
     */
    protected void waitUntil(long timeoutSeconds, BooleanSupplierWithThrow<Exception> predicate,
            String msgPattern, Object... msgArgs) throws Exception {
        CommonTestUtils.waitUntil("timed out (" + timeoutSeconds + "s) waiting for "
                + String.format(msgPattern, msgArgs), timeoutSeconds, predicate);
    }

    // TODO(b/230500604): refactor other CommonTestUtils.waitUntil() calls to use this one insteads
    /**
     * Waits until the given condition is reached, using the default timeout.
     */
    protected void waitUntil(BooleanSupplierWithThrow<Exception> predicate,
            String msgPattern, Object... msgArgs) throws Exception {
        waitUntil(DEFAULT_TIMEOUT_SEC, predicate, msgPattern, msgArgs);
    }

    /**
     * Removes a user by user ID and update the list of users to be removed.
     */
    protected boolean removeUser(int userId) throws Exception {
        String result = executeCommand("cmd car_service remove-user %d", userId);
        if (result.contains("STATUS_SUCCESSFUL")) {
            return true;
        }
        return false;
    }

    /**
     * Removes users whose name start with the given prefix.
     */
    protected void removeUsers(String prefix) throws Exception {
        Pattern pattern = Pattern.compile("^.*id=(\\d+), name=(" + prefix + ".*),.*$");
        String output = executeCommand("cmd user list --all -v");
        for (String line : output.split("\\n")) {
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) continue;

            int userId = Integer.parseInt(matcher.group(1));
            String name = matcher.group(2);
            CLog.e("Removing user with %s prefix (id=%d, name='%s')", prefix, userId, name);
            removeUser(userId);
        }
    }

    /**
     * Checks if an app is installed for a given user.
     */
    protected boolean isAppInstalledForUser(String packageName, int userId)
            throws DeviceNotAvailableException {
        return getDevice().isPackageInstalled(packageName, Integer.toString(userId));
    }

    /**
     * Fails the test if the app is installed for the given user.
     */
    protected void assertAppInstalledForUser(String packageName, int userId)
            throws DeviceNotAvailableException {
        assertWithMessage("%s should BE installed for user %s", packageName, userId).that(
                isAppInstalledForUser(packageName, userId)).isTrue();
    }

    /**
     * Fails the test if the app is NOT installed for the given user.
     */
    protected void assertAppNotInstalledForUser(String packageName, int userId)
            throws DeviceNotAvailableException {
        assertWithMessage("%s should NOT be installed for user %s", packageName, userId).that(
                isAppInstalledForUser(packageName, userId)).isFalse();
    }

    /**
     * Restarts the system server process.
     *
     * <p>Useful for cases where the test case changes system properties, as
     * {@link ITestDevice#reboot()} would reset them.
     */
    protected void restartSystemServer() throws Exception {
        long uptimeBefore = getSystemServerUptime();
        CLog.d("Uptime before restart: %d", uptimeBefore);

        restartOrReboot();

        getDevice().waitForDeviceAvailable();

        // Also checks for uptime - it might be an overkill, but at least it will add more logs,
        // which could help in case of issues
        CommonTestUtils.waitUntil("timed out waiting until for new system server uptime",
                SYSTEM_RESTART_TIMEOUT_SEC, () -> {
                    long uptimeAfter = getSystemServerUptime();
                    CLog.d("Uptime after restart: %d", uptimeAfter);
                    return uptimeAfter != -1 && uptimeAfter != uptimeBefore;
                });

        waitForCarServiceReady();
    }

    private void restartOrReboot() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();

        if (device.isAdbRoot()) {
            CLog.d("Restarting system server");
            device.executeShellCommand("stop");
            device.executeShellCommand("start");
            return;
        }

        CLog.d("Only root user can restart system server; rebooting instead");
        getDevice().reboot();
    }

    /**
     * Gets the system server uptime (or {@code -1} if not available).
     */
    protected long getSystemServerUptime() throws DeviceNotAvailableException {
        // Do not use getDevice().getIntProperty because it internally caches the value and will
        // not return the latest value.
        try {
            return Long.parseLong(getDevice().executeShellCommand(
                    "getprop sys.system_server.start_uptime").strip());
        } catch (DeviceNotAvailableException e) {
            throw e;
        } catch (Exception e) {
            CLog.w("Failed to getprop sys.system_server.start_uptime", e);
            return -1;
        }
    }

    /**
     * Reboots the device.
     */
    protected void reboot() throws Exception {
        CLog.d("Rebooting device");
        getDevice().reboot();
    }

    /**
     * Gets mapping of package and permissions granted for requested user id.
     *
     * @return Map<String, List<String>> where key is the package name and
     * the value is list of permissions granted for this user.
     */
    protected Map<String, List<String>> getPackagesAndPermissionsForUser(int userId)
            throws Exception {
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        getDevice().executeShellCommand("dumpsys package --proto", receiver);

        PackageServiceDumpProto dump = PackageServiceDumpProto.parser()
                .parseFrom(receiver.getOutput());

        CLog.v("Device has %d packages while getPackagesAndPermissions", dump.getPackagesCount());
        Map<String, List<String>> pkgMap = new HashMap<>();
        for (PackageProto pkg : dump.getPackagesList()) {
            String pkgName = pkg.getName();
            for (UserPermissionsProto userPermissions : pkg.getUserPermissionsList()) {
                if (userPermissions.getId() == userId) {
                    pkgMap.put(pkg.getName(), userPermissions.getGrantedPermissionsList());
                    break;
                }
            }
        }
        return pkgMap;
    }

    /**
     * Checks if the given package has a process running on the device.
     */
    protected boolean isPackageRunning(String packageName) throws Exception {
        return !executeCommand("pidof %s", packageName).isEmpty();
    }

    /**
     * Sleeps for the given amount of milliseconds.
     */
    protected void sleep(long ms) throws InterruptedException {
        CLog.v("Sleeping for %dms", ms);
        Thread.sleep(ms);
        CLog.v("Woke up; Little Susie woke up!");
    }

    // TODO(b/169341308): move to common infra code
    private static final class RequiredFeatureRule implements TestRule {

        private final ITestInformationReceiver mReceiver;
        private final String mFeature;

        RequiredFeatureRule(ITestInformationReceiver receiver, String feature) {
            mReceiver = receiver;
            mFeature = feature;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    boolean hasFeature = false;
                    try {
                        hasFeature = mReceiver.getTestInformation().getDevice()
                                .hasFeature(mFeature);
                    } catch (DeviceNotAvailableException e) {
                        CLog.e("Could not check if device has feature %s: %e", mFeature, e);
                        return;
                    }

                    if (!hasFeature) {
                        CLog.d("skipping %s#%s"
                                + " because device does not have feature '%s'",
                                description.getClassName(), description.getMethodName(), mFeature);
                        throw new AssumptionViolatedException("Device does not have feature '"
                                + mFeature + "'");
                    }
                    base.evaluate();
                }
            };
        }

        @Override
        public String toString() {
            return "RequiredFeatureRule[" + mFeature + "]";
        }
    }

    /**
     * Represents a user as returned by {@code cmd user list -v}.
     */
    public static final class UserInfo {
        public final int id;
        public final String flags;
        public final String name;
        public final String otherState;

        private UserInfo(Matcher matcher) {
            id = Integer.parseInt(matcher.group(USER_PATTERN_GROUP_ID));
            flags = matcher.group(USER_PATTERN_GROUP_FLAGS);
            name = matcher.group(USER_PATTERN_GROUP_NAME);
            otherState = matcher.group(USER_PATTERN_GROUP_OTHER_STATE);
        }

        @Override
        public String toString() {
            return "[UserInfo: id=" + id + ", flags=" + flags + ", name=" + name
                    + ", otherState=" + otherState + "]";
        }
    }
}
