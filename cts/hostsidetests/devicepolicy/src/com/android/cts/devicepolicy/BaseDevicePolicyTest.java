/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Base class for device policy tests. It offers utility methods to run tests, set device or profile
 * owner, etc.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public abstract class BaseDevicePolicyTest extends BaseHostJUnit4Test {

    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String FEATURE_BLUETOOTH = "android.hardware.bluetooth";
    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_CONNECTION_SERVICE = "android.software.connectionservice";
    private static final String FEATURE_FBE = "android.software.file_based_encryption";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String FEATURE_NFC = "android.hardware.nfc";
    private static final String FEATURE_NFC_BEAM = "android.software.nfc.beam";
    private static final String FEATURE_PRINT = "android.software.print";
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    private static final String FEATURE_SECURE_LOCK_SCREEN = "android.software.secure_lock_screen";
    private static final String FEATURE_WIFI = "android.hardware.wifi";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";

    //The maximum time to wait for user to be unlocked.
    private static final long USER_UNLOCK_TIMEOUT_SEC = 60;
    private static final String USER_STATE_UNLOCKED = "RUNNING_UNLOCKED";

    protected static final String PERMISSION_INTERACT_ACROSS_USERS =
            "android.permission.INTERACT_ACROSS_USERS";

    @Option(
            name = "skip-device-admin-feature-check",
            description = "Flag that allows to skip the check for android.software.device_admin "
                + "and run the tests no matter what. This is useful for system that do not what "
                + "to expose that feature publicly."
    )
    private boolean mSkipDeviceAdminFeatureCheck = false;

    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    protected static final int USER_SYSTEM = 0; // From the UserHandle class.

    protected static final int USER_OWNER = USER_SYSTEM;

    private static final long TIMEOUT_USER_REMOVED_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final long WAIT_SAMPLE_INTERVAL_MILLIS = 200;

    /**
     * The defined timeout (in milliseconds) is used as a maximum waiting time when expecting the
     * command output from the device. At any time, if the shell command does not output anything
     * for a period longer than defined timeout the Tradefed run terminates.
     */
    private static final long DEFAULT_SHELL_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(20);

    /**
     * Sets timeout (in milliseconds) that will be applied to each test. In the
     * event of a test timeout it will log the results and proceed with executing the next test.
     */
    private static final long DEFAULT_TEST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

    /**
     * The amount of milliseconds to wait for the remove user calls in {@link #tearDown}.
     * This is a temporary measure until b/114057686 is fixed.
     */
    private static final long USER_REMOVE_WAIT = TimeUnit.SECONDS.toMillis(5);

    /**
     * The amount of milliseconds to wait for the switch user calls in {@link #tearDown}.
     */
    private static final long USER_SWITCH_WAIT = TimeUnit.SECONDS.toMillis(5);

    // From the UserInfo class
    protected static final int FLAG_GUEST = 0x00000004;
    protected static final int FLAG_EPHEMERAL = 0x00000100;
    protected static final int FLAG_MANAGED_PROFILE = 0x00000020;
    protected static final int FLAG_INITIALIZED = 0x00000010;

    /** Default password to use in tests. */
    protected static final String TEST_PASSWORD = "1234";

    /**
     * The {@link android.os.BatteryManager} flags value representing all charging types; {@link
     * android.os.BatteryManager#BATTERY_PLUGGED_AC}, {@link
     * android.os.BatteryManager#BATTERY_PLUGGED_USB}, and {@link
     * android.os.BatteryManager#BATTERY_PLUGGED_WIRELESS}.
     */
    private static final int STAY_ON_WHILE_PLUGGED_IN_FLAGS = 7;

    /**
     * User ID for all users.
     * The value is from the UserHandle class.
     */
    protected static final int USER_ALL = -1;

    private static final String TEST_UPDATE_LOCATION = "/data/local/tmp/cts/deviceowner";

    /**
     * Copied from {@link android.app.admin.DevicePolicyManager
     * .InstallSystemUpdateCallback#UPDATE_ERROR_UPDATE_FILE_INVALID}
     */
    protected static final int UPDATE_ERROR_UPDATE_FILE_INVALID = 3;

    protected CompatibilityBuildHelper mBuildHelper;
    private String mPackageVerifier;

    /** Packages installed as part of the tests */
    private Set<String> mFixedPackages;

    protected int mDeviceOwnerUserId;
    protected int mPrimaryUserId;

    /** Is test running on a watch */
    protected boolean mIsWatch;

    /** Record the initial user ID. */
    protected int mInitialUserId;

    /** Whether multi-user is supported. */
    private boolean mSupportsMultiUser;

    /** Users we shouldn't delete in the tests */
    private final ArrayList<Integer> mFixedUsers = new ArrayList<>();

    protected boolean mHasAttestation;

    private static final String VERIFY_CREDENTIAL_CONFIRMATION = "Lock credential verified";

    public boolean skipDeviceAdminFeatureCheck() {
        return mSkipDeviceAdminFeatureCheck;
    }

    @Rule
    public final DeviceAdminFeaturesCheckerRule mFeaturesCheckerRule =
            new DeviceAdminFeaturesCheckerRule(this);

    @Before
    public void setUp() throws Exception {
        assertNotNull(getBuild());  // ensure build has been set before test is run.

        mSupportsMultiUser = getMaxNumberOfUsersSupported() > 1;
        mFixedPackages = getDevice().getInstalledPackageNames();
        mBuildHelper = new CompatibilityBuildHelper(getBuild());
        mIsWatch = hasDeviceFeature(FEATURE_WATCH);

        String propertyValue = getDevice().getProperty("ro.product.first_api_level");
        if (propertyValue != null && !propertyValue.isEmpty()) {
            mHasAttestation = Integer.parseInt(propertyValue) >= 26;
        }
        if (hasDeviceFeature(FEATURE_SECURE_LOCK_SCREEN)) {
            ensurePrimaryUserHasNoPassword();
        }

        // disable the package verifier to avoid the dialog when installing an app
        mPackageVerifier = getDevice().executeShellCommand(
                "settings get global verifier_verify_adb_installs");
        getDevice().executeShellCommand("settings put global verifier_verify_adb_installs 0");

        // Set the value of initial user ID calls in {@link #setUp}.
        if(mSupportsMultiUser) {
            mInitialUserId = getDevice().getCurrentUser();
        }

        if (!isHeadlessSystemUserMode()) {
            mDeviceOwnerUserId = mPrimaryUserId = getMainUser();
        } else {
            // For headless system user, all tests will be executed on current user
            // and therefore, initial user is set as primary user for test purpose.
            mPrimaryUserId = mInitialUserId;
            mDeviceOwnerUserId = USER_SYSTEM;
        }

        mFixedUsers.add(mPrimaryUserId);
        if (mPrimaryUserId != USER_SYSTEM) {
            mFixedUsers.add(USER_SYSTEM);
        }

        if (mFeaturesCheckerRule.hasRequiredFeatures()) {
            // Switching to primary is only needed when we're testing device admin features.
            switchUser(mPrimaryUserId);
        } else {
            // Otherwise, all the tests can be executed in any of the Android users, so remain in
            // current user, and don't delete it. This enables testing in secondary users.
            if (getDevice().getCurrentUser() != mPrimaryUserId) {
                mFixedUsers.add(getDevice().getCurrentUser());
            }
        }
        getDevice().executeShellCommand(" mkdir " + TEST_UPDATE_LOCATION);

        removeOwners();

        switchUser(mPrimaryUserId);

        removeTestUsers();
        // Unlock keyguard before test
        wakeupAndDismissKeyguard();
        stayAwake();
        // Go to home.
        executeShellCommand("input keyevent KEYCODE_HOME");
    }

    private void ensurePrimaryUserHasNoPassword() throws DeviceNotAvailableException {
        if (!verifyUserCredentialIsCorrect(null, mPrimaryUserId)) {
            changeUserCredential(null, TEST_PASSWORD, mPrimaryUserId);
        }
    }

    /** If package manager is not available, e.g. after system crash, wait for it a little bit. */
    private void ensurePackageManagerReady() throws Exception {
        waitForOutput("Package manager didn't become available", "service check package",
                s -> s.trim().equals("Service package: found"), 120 /* seconds */);
    }

    protected void waitForUserUnlock(int userId) throws Exception {
        waitForOutput("User is not unlocked.",
                String.format("am get-started-user-state %d", userId),
                s -> s.startsWith(USER_STATE_UNLOCKED), USER_UNLOCK_TIMEOUT_SEC);
    }

    protected void waitForOutput(String message, String command, Predicate<String> predicate,
            long timeoutSec) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
        while (!predicate.test(getDevice().executeShellCommand(command))) {
            if (System.nanoTime() > deadline) {
                fail(message);
            }
            RunUtil.getDefault().sleep(1000);
        }
    }

    @After
    public void tearDown() throws Exception {
        // reset the package verifier setting to its original value
        getDevice().executeShellCommand("settings put global verifier_verify_adb_installs "
                + mPackageVerifier);
        removeOwners();

        // Switch back to initial user.
        if (mSupportsMultiUser && getDevice().getCurrentUser() != mInitialUserId) {
            switchUser(mInitialUserId);
        }
        removeTestUsers();
        removeTestPackages();
        getDevice().executeShellCommand(" rm -r " + TEST_UPDATE_LOCATION);
    }

    protected void installAppAsUser(String appFileName, int userId) throws FileNotFoundException,
            DeviceNotAvailableException {
        installAppAsUser(appFileName, true, userId);
    }

    protected void installAppAsUser(String appFileName, boolean grantPermissions, int userId)
            throws FileNotFoundException, DeviceNotAvailableException {
        installAppAsUser(appFileName, grantPermissions, /* dontKillApp */ false, userId);
    }

    protected void installAppAsUser(String appFileName, boolean grantPermissions,
            boolean dontKillApp, int userId)
                    throws FileNotFoundException, DeviceNotAvailableException {
        CLog.e("Installing app %s for user %d", appFileName, userId);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        List<String> extraArgs = new LinkedList<>();
        extraArgs.add("-t");
        // Make the test app queryable by other apps via PackageManager APIs.
        extraArgs.add("--force-queryable");
        extraArgs.add("--bypass-low-target-sdk-block");
        if (dontKillApp) extraArgs.add("--dont-kill");
        String result = getDevice().installPackageForUser(
                buildHelper.getTestFile(appFileName), true, grantPermissions, userId,
                extraArgs.toArray(new String[extraArgs.size()]));
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    protected void installAppIncremental(String appFileName)
            throws FileNotFoundException, DeviceNotAvailableException {
        final String signatureSuffix = ".idsig";
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(appFileName);
        assertNotNull(apk);
        final File idsig = buildHelper.getTestFile(appFileName + signatureSuffix);
        assertNotNull(idsig);
        final String remoteApkPath = TEST_UPDATE_LOCATION + "/" + apk.getName();
        final String remoteIdsigPath = remoteApkPath + signatureSuffix;
        assertTrue(getDevice().pushFile(apk, remoteApkPath));
        assertTrue(getDevice().pushFile(idsig, remoteIdsigPath));
        String installResult = getDevice().executeShellCommand(
                "pm install-incremental -t -g " + remoteApkPath);
        assertEquals("Success\n", installResult);
    }

    protected void installDeviceOwnerApp(String apk) throws Exception {
        installAppAsUser(apk, mDeviceOwnerUserId);

        if (isHeadlessSystemUserMode()) {
            // Need to explicitly install the device owner app for the current user (rather than
            // relying on DPMS) so it has the same privileges (like INTERACT_ACROSS_USERS) as the
            // app running on system user, otherwise some tests might fail
            installAppAsUser(apk, mPrimaryUserId);
        }
    }

    protected void removeDeviceOwnerAdmin(String componentName) throws DeviceNotAvailableException {
        // Don't fail as it could hide the real failure from the test method
        if (!removeAdmin(componentName, mDeviceOwnerUserId)) {
            CLog.e("Failed to remove device owner %s on user %d", componentName,
                    mDeviceOwnerUserId);
        }
        if (isHeadlessSystemUserMode() && !removeAdmin(componentName, mPrimaryUserId)) {
            CLog.e("Failed to remove profile owner %s on user %d", componentName, mPrimaryUserId);
        }
    }

    protected void forceStopPackageForUser(String packageName, int userId) throws Exception {
        // TODO Move this logic to ITestDevice
        executeShellCommand("am force-stop --user " + userId + " " + packageName);
    }

    protected void fgsStopPackageForUser(String packageName, int userId) throws Exception {
        // TODO Move this logic to ITestDevice
        executeShellCommand("am stop-app --user " + userId + " " + packageName);
    }

    protected String executeShellCommand(String commandTemplate, Object...args) throws Exception {
        return executeShellCommand(String.format(commandTemplate, args));
    }

    protected String executeShellCommand(String command) throws Exception {
        CLog.d("Starting command %s", command);
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command %s: %s", command, commandOutput);
        return commandOutput;
    }

    /** Initializes the user with the given id. This is required so that apps can run on it. */
    protected void startUser(int userId) throws Exception {
        CLog.d("Starting user %d", userId);
        getDevice().startUser(userId);
    }

    /** Initializes the user with waitFlag. This is required so that apps can run on it. */
    protected void startUserAndWait(int userId) throws Exception {
        CLog.d("Starting user %d and waiting", userId);
        getDevice().startUser(userId, /* waitFlag= */ true);
    }

    /**
     * Initializes the user with the given id, and waits until the user has started and unlocked
     * before continuing.
     *
     * <p>This is required so that apps can run on it.
     */
    protected void startUser(int userId, boolean waitFlag) throws Exception {
        getDevice().startUser(userId, waitFlag);
    }

    /**
     * Starts switching to the user with the given ID.
     *
     * <p>This is not blocking. Some operations will be flaky if called immediately afterwards, such
     * as {@link #wakeupAndDismissKeyguard()}. Call {@link #waitForBroadcastIdle()} between this
     * method and those operations to ensure that switching the user has finished.
     */
    protected void switchUser(int userId) throws Exception {
        // TODO Move this logic to ITestDevice
        int retries = 10;
        CLog.i("switching to user %d", userId);
        executeShellCommand("am switch-user " + userId);
        RunUtil.getDefault().sleep(USER_SWITCH_WAIT);
        while (getDevice().getCurrentUser() != userId && (--retries) >= 0) {
            // am switch-user can be ignored if a previous user-switching operation
            // is still in progress. In this case, sleep a bit and then retry
            RunUtil.getDefault().sleep(USER_SWITCH_WAIT);
            executeShellCommand("am switch-user " + userId);
        }
        assertTrue("Failed to switch user after multiple retries", getDevice().getCurrentUser() == userId);
    }

    protected int getMaxNumberOfUsersSupported() throws DeviceNotAvailableException {
        return getDevice().getMaxNumberOfUsersSupported();
    }

    protected int getMaxNumberOfRunningUsersSupported() throws DeviceNotAvailableException {
        return getDevice().getMaxNumberOfRunningUsersSupported();
    }

    protected int getUserFlags(int userId) throws DeviceNotAvailableException {
        String command = "pm list users";
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.i("Output for command " + command + ": " + commandOutput);

        String[] lines = commandOutput.split("\\r?\\n");
        assertTrue(commandOutput + " should contain at least one line", lines.length >= 1);
        for (int i = 1; i < lines.length; i++) {
            // Individual user is printed out like this:
            // \tUserInfo{$id$:$name$:$Integer.toHexString(flags)$} [running]
            String[] tokens = lines[i].split("\\{|\\}|:");
            assertTrue(lines[i] + " doesn't contain 4 or 5 tokens",
                    tokens.length == 4 || tokens.length == 5);
            // If the user IDs match, return the flags.
            if (Integer.parseInt(tokens[1]) == userId) {
                return Integer.parseInt(tokens[3], 16);
            }
        }
        fail("User not found");
        return 0;
    }

    protected ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
        return getDevice().listUsers();
    }

    protected  ArrayList<Integer> listRunningUsers() throws DeviceNotAvailableException {
        ArrayList<Integer> runningUsers = new ArrayList<>();
        for (int userId : listUsers()) {
            if (getDevice().isUserRunning(userId)) {
                runningUsers.add(userId);
            }
        }
        return runningUsers;
    }

    protected int getFirstManagedProfileUserId() throws DeviceNotAvailableException {
        for (int userId : listUsers()) {
            if ((getUserFlags(userId) & FLAG_MANAGED_PROFILE) != 0) {
                return userId;
            }
        }
        fail("Managed profile not found");
        return 0;
    }

    private void stopUserAsync(int userId) throws Exception {
        String stopUserCommand = "am stop-user -f " + userId;
        CLog.d("starting command \"" + stopUserCommand);
        CLog.d("Output for command " + stopUserCommand + ": "
                + getDevice().executeShellCommand(stopUserCommand));
    }

    protected void stopUser(int userId) throws Exception {
        String stopUserCommand = "am stop-user -w -f " + userId;
        CLog.d("starting command \"" + stopUserCommand + "\" and waiting.");
        CLog.d("Output for command " + stopUserCommand + ": "
                + getDevice().executeShellCommand(stopUserCommand));
    }

    protected void waitForBroadcastIdle() throws DeviceNotAvailableException, IOException {
        final CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        // We allow 8min for the command to complete and 4min for the command to start to
        // output something.
        getDevice().executeShellCommand(
                "am wait-for-broadcast-idle", receiver, 8, 4, TimeUnit.MINUTES, 0);
        final String output = receiver.getOutput();
        if (!output.contains("All broadcast queues are idle!")) {
            CLog.e("Output from 'am wait-for-broadcast-idle': %s", output);
            fail("'am wait-for-broadcase-idle' did not complete.");
        }
    }

    protected void removeUser(int userId) throws Exception  {
        if (listUsers().contains(userId) && userId != USER_SYSTEM) {
            // Don't log output, as tests sometimes set no debug user restriction, which
            // causes this to fail, we should still continue and remove the user.
            String stopUserCommand = "am stop-user -w -f " + userId;
            CLog.d("stopping and removing user " + userId);
            getDevice().executeShellCommand(stopUserCommand);
            // TODO: Remove both sleeps and USER_REMOVE_WAIT constant when b/114057686 is fixed.
            RunUtil.getDefault().sleep(USER_REMOVE_WAIT);
            // Ephemeral users may have already been removed after being stopped.
            if (listUsers().contains(userId)) {
                assertTrue("Couldn't remove user", getDevice().removeUser(userId));
                RunUtil.getDefault().sleep(USER_REMOVE_WAIT);
            }
        }
    }

    protected void removeTestUsers() throws Exception {
        List<Integer> usersCreatedByTests = getUsersCreatedByTests();

        // The time spent on stopUser is depend on how busy the broadcast queue is.
        // To optimize the time to remove multiple test users, we mark all users as
        // stopping first, so no more broadcasts will be sent to these users, which make the queue
        // less busy.
        for (int userId : usersCreatedByTests) {
            stopUserAsync(userId);
        }
        for (int userId : usersCreatedByTests) {
            removeTestAddedUser(userId);
        }
    }

    private void removeTestAddedUser(int userId) throws Exception  {
        // Don't remove system user or initial user.
        if (userId != USER_SYSTEM && userId != mInitialUserId) {
            removeUser(userId);
        }
    }

    /**
     * Returns the users that have been created since running this class' setUp() method.
     */
    protected List<Integer> getUsersCreatedByTests() throws Exception {
        List<Integer> result = listUsers();
        result.removeAll(mFixedUsers);
        return result;
    }

    /** Removes any packages that were installed during the test. */
    protected void removeTestPackages() throws Exception {
        for (String packageName : getDevice().getUninstallablePackageNames()) {
            if (mFixedPackages.contains(packageName)) {
                continue;
            }
            CLog.w("removing leftover package: " + packageName);
            getDevice().uninstallPackage(packageName);
        }
    }

    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, int userId)
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(pkgName, testClassName, /* testMethodName= */ null, userId);
    }

    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, String testMethodName, int userId)
            throws DeviceNotAvailableException {
        Map<String, String> params = Collections.emptyMap();
        runDeviceTestsAsUser(pkgName, testClassName, testMethodName, userId, params);
    }

    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName, int userId,
            Map<String, String> params) throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        CLog.i("runDeviceTestsAsUser(): user=%d, pkg=%s class=%s, test=%s", userId, pkgName,
                testClassName, testMethodName);
        runDeviceTests(
                getDevice(),
                RUNNER,
                pkgName,
                testClassName,
                testMethodName,
                userId,
                DEFAULT_TEST_TIMEOUT_MILLIS,
                DEFAULT_SHELL_TIMEOUT_MILLIS,
                0L /* maxInstrumentationTimeoutMs */,
                true /* checkResults */,
                false /* isHiddenApiCheckDisabled */,
                params);
    }

    /** Reboots the device and block until the boot complete flag is set. */
    protected void rebootAndWaitUntilReady() throws Exception {
        getDevice().rebootUntilOnline();
        assertTrue("Device failed to boot", getDevice().waitForBootComplete(120_000));
    }

    /** Returns a boolean value of the system property with the specified key. */
    protected boolean getBooleanSystemProperty(String key, boolean defaultValue)
            throws DeviceNotAvailableException {
        final String[] positiveValues = {"1", "y", "yes", "true", "on"};
        final String[] negativeValues = {"0", "n", "no", "false", "off"};
        String propertyValue = getDevice().getProperty(key);
        if (propertyValue == null || propertyValue.isEmpty()) {
            return defaultValue;
        }
        if (Arrays.asList(positiveValues).contains(propertyValue)) {
            return true;
        }
        if (Arrays.asList(negativeValues).contains(propertyValue)) {
            return false;
        }
        fail("Unexpected value of boolean system property '" + key + "': " + propertyValue);
        return false;
    }

    /** Checks whether it is possible to create the desired number of users. */
    protected boolean canCreateAdditionalUsers(int numberOfUsers)
            throws DeviceNotAvailableException {
        return listUsers().size() + numberOfUsers <= getMaxNumberOfUsersSupported();
    }

    /**
     * Throws a {@link org.junit.AssumptionViolatedException} if it's not possible to create the
     * desired number of users.
     */
    protected void assumeCanCreateAdditionalUsers(int numberOfUsers)
            throws DeviceNotAvailableException {
        int maxUsers = getDevice().getMaxNumberOfUsersSupported();
        assumeTrue("Tests needs at least " + numberOfUsers + " extra users, but device supports "
                + "at most " + getMaxNumberOfUsersSupported(),
                canCreateAdditionalUsers(numberOfUsers));
    }

    /** Checks whether it is possible to start the desired number of users. */
    protected boolean canStartAdditionalUsers(int numberOfUsers)
            throws DeviceNotAvailableException {
        return listRunningUsers().size() + numberOfUsers <= getMaxNumberOfRunningUsersSupported();
    }

    protected void assumeCanStartNewUser() throws DeviceNotAvailableException {
        assumeCanCreateOneManagedUser();
        assumeTrue("Cannot start a new user", canStartAdditionalUsers(1));
    }

    protected int createUser() throws Exception {
        int userId = createUser(0);
        CLog.i("Created user with id %d", userId);
        // TODO remove this and audit tests so they start users as necessary
        startUser(userId);
        return userId;
    }

    protected int createUserAndWaitStart() throws Exception {
        int userId = createUser(0);
        startUserAndWait(userId);
        return userId;
    }

    protected int createUser(int flags) throws Exception {
        boolean guest = FLAG_GUEST == (flags & FLAG_GUEST);
        boolean ephemeral = FLAG_EPHEMERAL == (flags & FLAG_EPHEMERAL);
        CLog.i("Creating user with flags %d: guest=%b, ephemeral=%b", flags, guest, ephemeral);
        // TODO Use ITestDevice.createUser() when guest and ephemeral is available
        String command ="pm create-user " + (guest ? "--guest " : "")
                + (ephemeral ? "--ephemeral " : "") + "TestUser_" + System.currentTimeMillis();
        CLog.d("Starting command %s", command);
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command %s: %s", command, commandOutput);

        // Extract the id of the new user.
        String[] tokens = commandOutput.split("\\s+");
        assertTrue(tokens.length > 0);
        assertEquals("Command '" + command + "' failed: " + commandOutput, "Success:", tokens[0]);
        return Integer.parseInt(tokens[tokens.length-1]);
    }

    protected int createManagedProfile(int parentUserId) throws DeviceNotAvailableException {
        String commandOutput = getCreateManagedProfileCommandOutput(parentUserId);
        return getUserIdFromCreateUserCommandOutput(commandOutput);
    }

    protected void assertCannotCreateManagedProfile(int parentUserId)
            throws Exception {
        String commandOutput = getCreateManagedProfileCommandOutput(parentUserId);
        if (commandOutput.startsWith("Error")) {
            return;
        }
        int userId = getUserIdFromCreateUserCommandOutput(commandOutput);
        removeUser(userId);
        fail("Expected not to be able to create a managed profile. Output was: " + commandOutput);
    }

    private void assumeHasDeviceFeature(String feature) throws DeviceNotAvailableException {
        assumeTrue("device doesn't have " + feature, hasDeviceFeature(feature));
    }

    private void assumeDoesNotHaveDeviceFeature(String feature) throws DeviceNotAvailableException {
        assumeFalse("device has " + feature, hasDeviceFeature(feature));
    }

    /**
     * Used by test cases to add additional checks priort to {@link #setUp()}, so that when it
     * throws an {@link AssumptionViolatedException} exception nothing is run
     * (even {@link #tearDown()}).
     */
    protected void assumeTestEnabled() throws Exception {
    }

    protected final void assumeCanCreateOneManagedUser() throws DeviceNotAvailableException {
        assumeSupportsMultiUser();
        assumeCanCreateAdditionalUsers(1);
    }

    protected final void assumeSupportsMultiUser() throws DeviceNotAvailableException {
        // setup isn't always called before this method
        mSupportsMultiUser = getMaxNumberOfUsersSupported() > 1;
        assumeTrue("device doesn't support multiple users", mSupportsMultiUser);
    }

    protected final void assumeHasMainUser() throws DeviceNotAvailableException {
        Integer user = getDevice().getMainUserId();
        assumeTrue("device doesn't have a main user", user != null);
    }
    protected final void assumeHasWifiFeature() throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_WIFI);
    }

    protected final void assumeHasTelephonyFeature() throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_TELEPHONY);
    }

    protected final void assumeSupportsSms() throws Exception {
        assumeTrue("device doesn't support SMS", isSmsCapable());
    }

    protected final void assumeHasNfcFeatures() throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_NFC);
        assumeHasDeviceFeature(FEATURE_NFC_BEAM);
    }

    protected final void assumeHasTelephonyAndConnectionServiceFeatures()
            throws DeviceNotAvailableException {
        assumeHasTelephonyFeature();
        assumeHasDeviceFeature(FEATURE_CONNECTION_SERVICE);
    }

    protected final void assumeHasSecureLockScreenFeature() throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_SECURE_LOCK_SCREEN);
    }

    protected final void assumeDoesNotHaveSecureLockScreenFeature()
            throws DeviceNotAvailableException {
        assumeDoesNotHaveDeviceFeature(FEATURE_SECURE_LOCK_SCREEN);
    }

    protected final void assumeHasFileBasedEncryptionAndSecureLockScreenFeatures()
            throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_FBE);
        assumeHasSecureLockScreenFeature();
    }

    protected final void assumeHasPrintFeature() throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_PRINT);
    }

    protected final void assumeHasCameraFeature() throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_CAMERA);
    }

    protected final void assumeHasBluetoothFeature() throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_BLUETOOTH);
    }

    protected final void assumeApiLevel(int min) throws DeviceNotAvailableException {
        assumeTrue("API level must be >=" + min, getDevice().getApiLevel() >= min);
    }

    private int getUserIdFromCreateUserCommandOutput(String commandOutput) {
        // Extract the id of the new user.
        String[] tokens = commandOutput.split("\\s+");
        assertTrue(commandOutput + " expected to have format \"Success: {USER_ID}\"",
                tokens.length > 0);
        assertEquals(commandOutput, "Success:", tokens[0]);
        return Integer.parseInt(tokens[tokens.length-1]);
    }

    private String getCreateManagedProfileCommandOutput(int parentUserId)
            throws DeviceNotAvailableException {
        String command = "pm create-user --profileOf " + parentUserId + " --managed "
                + "TestProfile_" + System.currentTimeMillis();
        CLog.d("Starting command " + command);
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + commandOutput);
        return commandOutput;
    }

    protected int getMainUser() throws DeviceNotAvailableException {
        Integer user = getDevice().getMainUserId();
        if (user == null) {
            user = getDevice().getPrimaryUserId();
            if (user == null) {
                user = 0;
            }
        }
        return user;
    }

    protected int getCurrentUser() throws DeviceNotAvailableException {
        return getDevice().getCurrentUser();
    }

    protected int getUserSerialNumber(int userId) throws DeviceNotAvailableException{
        // TODO: Move this logic to ITestDevice.
        // dumpsys user output contains lines like "UserInfo{0:Owner:13} serialNo=0 isPrimary=true"
        final Pattern pattern =
                Pattern.compile("UserInfo\\{" + userId + ":[^\\n]*\\sserialNo=(\\d+)\\s");
        final String commandOutput = getDevice().executeShellCommand("dumpsys user");
        final Matcher matcher = pattern.matcher(commandOutput);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        fail("Couldn't find serial number for user " + userId);
        return -1;
    }

    protected boolean setProfileOwner(String componentName, int userId, boolean expectFailure)
            throws DeviceNotAvailableException {
        String command = "dpm set-profile-owner --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        boolean success = commandOutput.startsWith("Success:");
        // If we succeeded always log, if we are expecting failure don't log failures
        // as call stacks for passing tests confuse the logs.
        if (success || !expectFailure) {
            CLog.e("Output for command " + command + ": " + commandOutput);
        } else {
            CLog.e("Command Failed " + command);
        }
        return success;
    }

    protected void setProfileOwnerOrFail(String componentName, int userId)
            throws Exception {
        if (!setProfileOwner(componentName, userId, /*expectFailure*/ false)) {
            // Don't remove system user or initial user that tests require to run on.
            removeTestAddedUser(userId);
            fail("Failed to set profile owner");
        }
    }

    protected void setProfileOwnerExpectingFailure(String componentName, int userId)
            throws Exception {
        if (setProfileOwner(componentName, userId, /* expectFailure =*/ true)) {
            removeTestAddedUser(userId);
            fail("Setting profile owner should have failed.");
        }
    }

    private String setDeviceAdminInner(String componentName, int userId)
            throws DeviceNotAvailableException {
        String command = "dpm set-active-admin --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        return commandOutput;
    }

    protected void setDeviceAdmin(String componentName, int userId)
            throws DeviceNotAvailableException {
        String commandOutput = setDeviceAdminInner(componentName, userId);
        CLog.d("Output for command " + commandOutput
                + ": " + commandOutput);
        assertTrue(commandOutput + " expected to start with \"Success:\"",
                commandOutput.startsWith("Success:"));
    }

    protected void setDeviceAdminExpectingFailure(String componentName, int userId,
            String errorMessage) throws DeviceNotAvailableException {
        String commandOutput = setDeviceAdminInner(componentName, userId);
        if (!commandOutput.contains(errorMessage)) {
            fail(commandOutput + " expected to contain \"" + errorMessage + "\"");
        }
    }

    protected boolean setDeviceOwner(String componentName, int userId, boolean expectFailure)
            throws DeviceNotAvailableException {
        String command = "dpm set-device-owner --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        boolean success = commandOutput.startsWith("Success:");
        // If we succeeded always log, if we are expecting failure don't log failures
        // as call stacks for passing tests confuse the logs.
        if (success || !expectFailure) {
            CLog.d("Output for command " + command + ": " + commandOutput);
        } else {
            CLog.d("Command Failed " + command);
        }
        return success;
    }

    protected void setDeviceOwnerOrFail(String componentName, int userId)
            throws Exception {
        assertTrue(setDeviceOwner(componentName, userId, /* expectFailure =*/ false));
    }

    protected void setDeviceOwnerExpectingFailure(String componentName, int userId)
            throws Exception {
        assertFalse(setDeviceOwner(componentName, userId, /* expectFailure =*/ true));
    }


    protected void affiliateUsers(String deviceAdminPkg, int userId1, int userId2)
            throws Exception {
        CLog.d("Affiliating users %d and %d on admin package %s", userId1, userId2, deviceAdminPkg);
        runDeviceTestsAsUser(
                deviceAdminPkg, ".AffiliationTest", "testSetAffiliationId1", userId1);
        runDeviceTestsAsUser(
                deviceAdminPkg, ".AffiliationTest", "testSetAffiliationId1", userId2);
    }

    protected String getSettings(String namespace, String name, int userId)
            throws DeviceNotAvailableException {
        String command = "settings --user " + userId + " get " + namespace + " " + name;
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + commandOutput);
        return commandOutput.replace("\n", "").replace("\r", "");
    }

    protected void putSettings(String namespace, String name, String value, int userId)
            throws DeviceNotAvailableException {
        String command = "settings --user " + userId + " put " + namespace + " " + name
                + " " + value;
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + commandOutput);
    }

    protected boolean removeAdmin(String componentName, int userId)
            throws DeviceNotAvailableException {
        String command = "dpm remove-active-admin --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + commandOutput);
        return commandOutput.startsWith("Success:");
    }

    // Tries to remove and profile or device owners it finds.
    protected void removeOwners() throws DeviceNotAvailableException {
        String command = "dumpsys device_policy";
        String commandOutput = getDevice().executeShellCommand(command);
        String[] lines = commandOutput.split("\\r?\\n");
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (line.contains("Profile Owner")) {
                // Line is "Profile owner (User <id>):
                String[] tokens = line.split("\\(|\\)| ");
                int userId = Integer.parseInt(tokens[4]);
                i++;
                line = lines[i].trim();
                // Line is admin=ComponentInfo{<component>}
                tokens = line.split("\\{|\\}");
                String componentName = tokens[1];
                CLog.w("Cleaning up profile owner " + userId + " " + componentName);
                removeAdmin(componentName, userId);
            } else if (line.contains("Device Owner:")) {
                i++;
                line = lines[i].trim();
                // Line is admin=ComponentInfo{<component>}
                String[] tokens = line.split("\\{|\\}");
                String componentName = tokens[1];
                // Skip to user id line.
                for (int j = i + 1; j < lines.length; ++j) {
                    line = lines[j].trim();
                    // Line is User ID: <N>
                    if (line.contains("User ID:")) {
                        tokens = line.split(":");
                        int userId = Integer.parseInt(tokens[1].trim());
                        CLog.w("Cleaning up device owner " + userId + " " + componentName);
                        removeAdmin(componentName, userId);
                        return;
                    }
                }
                throw new RuntimeException(
                        "Error finding a user id for this device owner in dumpsys.");
            }
        }
    }

    /**
     * Runs pm enable command to enable a package or component. Returns the command result.
     */
    protected String enableComponentOrPackage(int userId, String packageOrComponent)
            throws DeviceNotAvailableException {
        String command = "pm enable --user " + userId + " " + packageOrComponent;
        String result = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + result);
        return result;
    }

    /**
     * Runs pm disable command to disable a package or component. Returns the command result.
     */
    protected String disableComponentOrPackage(int userId, String packageOrComponent)
            throws DeviceNotAvailableException {
        String command = "pm disable --user " + userId + " " + packageOrComponent;
        String result = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + result);
        return result;
    }

    protected interface SuccessCondition {
        boolean check() throws Exception;
    }

    protected void waitUntilUserRemoved(int userId) throws Exception {
        tryWaitForSuccess(() -> !listUsers().contains(userId),
                "The user " + userId + " has not been removed",
                TIMEOUT_USER_REMOVED_MILLIS
                );
    }

    protected void tryWaitForSuccess(SuccessCondition successCondition, String failureMessage,
            long timeoutMillis) throws Exception {
        long epoch = System.currentTimeMillis();
        while (System.currentTimeMillis() - epoch <= timeoutMillis) {
            RunUtil.getDefault().sleep(WAIT_SAMPLE_INTERVAL_MILLIS);
            if (successCondition.check()) {
                return;
            }
        }
        fail(failureMessage);
    }

    /**
     * Sets a user restriction via SetPolicyActivity.
     * <p>IMPORTANT: The package that contains SetPolicyActivity must have been installed prior to
     * calling this method.
     * @param key user restriction key
     * @param value true if we should set the restriction, false if we should clear it
     * @param userId userId to set/clear the user restriction on
     * @param packageName package where SetPolicyActivity is installed
     * @return The output of the command
     * @throws DeviceNotAvailableException
     */
    protected String changeUserRestriction(String key, boolean value, int userId,
            String packageName) throws DeviceNotAvailableException {
        return changePolicy(getUserRestrictionCommand(value),
                " --es extra-restriction-key " + key, userId, packageName);
    }

    /**
     * Same as {@link #changeUserRestriction(String, boolean, int, String)} but asserts that it
     * succeeds.
     */
    protected void changeUserRestrictionOrFail(String key, boolean value, int userId,
            String packageName) throws DeviceNotAvailableException {
        changePolicyOrFail(getUserRestrictionCommand(value), " --es extra-restriction-key " + key,
                userId, packageName);
    }

    /**
     * Sets some policy via SetPolicyActivity.
     * <p>IMPORTANT: The package that contains SetPolicyActivity must have been installed prior to
     * calling this method.
     * @param command command to pass to SetPolicyActivity
     * @param extras extras to pass to SetPolicyActivity
     * @param userId the userId where we invoke SetPolicyActivity
     * @param packageName where SetPolicyActivity is installed
     * @return The output of the command
     * @throws DeviceNotAvailableException
     */
    protected String changePolicy(String command, String extras, int userId, String packageName)
            throws DeviceNotAvailableException {
        String adbCommand = "am start -W --user " + userId
                + " -c android.intent.category.DEFAULT "
                + " --es extra-command " + command
                + " " + extras
                + getAdditionalExtrasForSetPolicyActivity()
                + " " + packageName + "/.SetPolicyActivity";
        String commandOutput = getDevice().executeShellCommand(adbCommand);
        CLog.d("Output for command " + adbCommand + ": " + commandOutput);
        return commandOutput;
    }

    protected String getAdditionalExtrasForSetPolicyActivity() {
        return "";
    }

    /**
     * Same as {@link #changePolicy(String, String, int, String)} but asserts that it succeeds.
     */
    protected void changePolicyOrFail(String command, String extras, int userId,
            String packageName) throws DeviceNotAvailableException {
        String commandOutput = changePolicy(command, extras, userId, packageName);
        assertTrue("Command was expected to succeed " + commandOutput,
                commandOutput.contains("Status: ok"));
    }

    private String getUserRestrictionCommand(boolean setRestriction) {
        if (setRestriction) {
            return "add-restriction";
        }
        return "clear-restriction";
    }

    /**
     * Set lockscreen password / work challenge for the given user, null or "" means clear
     * IMPORTANT: prefer to use {@link #TEST_PASSWORD} for primary user, otherwise if the test
     * terminates before cleaning password up, the device will be unusable for further testing.
     */
    protected void changeUserCredential(String newCredential, String oldCredential, int userId)
            throws DeviceNotAvailableException {
        final String oldCredentialArgument = (oldCredential == null || oldCredential.isEmpty()) ? ""
                : ("--old " + oldCredential);
        if (newCredential != null && !newCredential.isEmpty()) {
            String commandOutput = getDevice().executeShellCommand(String.format(
                    "cmd lock_settings set-password --user %d %s %s", userId, oldCredentialArgument,
                    newCredential));
            if (!commandOutput.startsWith("Password set to")) {
                fail("Failed to set user credential: " + commandOutput);
            }
        } else {
            String commandOutput = getDevice().executeShellCommand(String.format(
                    "cmd lock_settings clear --user %d %s", userId, oldCredentialArgument));
            if (!commandOutput.startsWith("Lock credential cleared")) {
                fail("Failed to clear user credential: " + commandOutput);
            }
        }
    }

    /**
     * Verifies the lock credential for the given user.
     *
     * @param credential The credential to verify.
     * @param userId The id of the user.
     */
    protected void verifyUserCredential(String credential, int userId)
            throws DeviceNotAvailableException {
        String commandOutput = verifyUserCredentialCommandOutput(credential, userId);
        if (!commandOutput.startsWith(VERIFY_CREDENTIAL_CONFIRMATION)) {
            fail("Failed to verify user credential: " + commandOutput);
        }
     }

    /**
     * Verifies the lock credential for the given user, which unlocks the user, and returns
     * whether it was successful or not.
     *
     * @param credential The credential to verify.
     * @param userId The id of the user.
     */
    protected boolean verifyUserCredentialIsCorrect(String credential, int userId)
            throws DeviceNotAvailableException {
        String commandOutput = verifyUserCredentialCommandOutput(credential, userId);
        return commandOutput.startsWith(VERIFY_CREDENTIAL_CONFIRMATION);
    }

    /**
     * Verifies the lock credential for the given user, which unlocks the user. Returns the
     * commandline output, which includes whether the verification was successful.
     *
     * @param credential The credential to verify.
     * @param userId The id of the user.
     * @return The command line output.
     */
    protected String verifyUserCredentialCommandOutput(String credential, int userId)
            throws DeviceNotAvailableException {
        final String credentialArgument = (credential == null || credential.isEmpty())
                ? "" : ("--old " + credential);
        String commandOutput = getDevice().executeShellCommand(String.format(
                "cmd lock_settings verify --user %d %s", userId, credentialArgument));
        return commandOutput;
    }

    protected void wakeupAndDismissKeyguard() throws Exception {
        executeShellCommand("input keyevent KEYCODE_WAKEUP");
        executeShellCommand("wm dismiss-keyguard");
    }

    protected void pressPowerButton() throws Exception {
        executeShellCommand("input keyevent KEYCODE_POWER");
    }

    private void stayAwake() throws Exception {
        executeShellCommand(
                "settings put global stay_on_while_plugged_in " + STAY_ON_WHILE_PLUGGED_IN_FLAGS);
    }

    protected void startActivityAsUser(int userId, String packageName, String activityName)
        throws Exception {
        wakeupAndDismissKeyguard();
        String command = "am start -W --user " + userId + " " + packageName + "/" + activityName;
        getDevice().executeShellCommand(command);
    }

    protected String getDefaultLauncher() throws Exception {
        return getDevice().executeShellCommand("cmd role get-role-holders --user "
                + getDevice().getCurrentUser() + " android.app.role.HOME").trim();
    }

    void assumeIsDeviceAb() throws DeviceNotAvailableException {
        final String result = getDevice().executeShellCommand("getprop ro.build.ab_update").trim();
        assumeTrue("not device AB", "true".equalsIgnoreCase(result));
    }

    boolean isHeadlessSystemUserMode() throws DeviceNotAvailableException {
        return isHeadlessSystemUserMode(getDevice());
    }

    public static boolean isHeadlessSystemUserMode(ITestDevice device)
            throws DeviceNotAvailableException {
        return device.isHeadlessSystemUserMode();
    }

    protected void assumeHeadlessSystemUserMode(String reason)
            throws DeviceNotAvailableException {
        assumeTrue("Skipping test on non-headless system user mode. Reason: " + reason,
                isHeadlessSystemUserMode());
    }

    protected void grantDpmWrapperPermissions(String deviceAdminPkg, int userId) throws Exception {
        // TODO(b/176993670): INTERACT_ACROSS_USERS is needed by DevicePolicyManagerWrapper to
        // send ordered broadcasts to the test user. The permission is already available to the
        // packages installed by the host side test (as they're installed with -g), but need to be
        // granted for users created by the test, as the package is intalled by code
        // (DPMS.manageUserUnchecked(), which doesn't grant it (as this is a privileged permission
        // that's not available to 3rd party apps). If we get rid of DevicePolicyManagerWrapper,
        // we won't need to grant it anymore.
        grantPermission(deviceAdminPkg, PERMISSION_INTERACT_ACROSS_USERS, userId, "its PO needs to "
                + "send ordered broadcasts to user 0");

        // Probably not needed anymore, but it doesn't hurt to keep...
        allowTestApiAccess(deviceAdminPkg);
    }

    /**
     * Grants access to APIs marked as {@code @TestApi}.
     *
     * <p><b>Note:</b> the {@code application} tag of the app's manifest must contain
     * {@code android:debuggable="true"}, otherwise it won't work on {@code user} builds.
     */
    protected void allowTestApiAccess(String pgkName) throws Exception {
        CLog.i("Granting ALLOW_TEST_API_ACCESS to package %s", pgkName);
        executeShellCommand("am compat enable ALLOW_TEST_API_ACCESS %s", pgkName);
    }

    protected void grantPermission(String pkg, String permission, int userId, String reason)
            throws Exception {
        CLog.i("Granting permission %s to package (%s) on user %d%s", pkg, permission, userId,
                (reason == null ? "" : "(reason: " + reason + ")"));
        executeShellCommand("pm grant --user %d %s %s", userId, pkg, permission);
    }

    protected void revokePermission(String pkg, String permission, int userId) throws Exception {
        CLog.i("Revoking permission %s to package (%s) on user %d", pkg, permission, userId);
        executeShellCommand("pm revoke --user %d %s %s", userId, pkg, permission);
    }

    /** Find effective restriction for user */
    protected boolean isRestrictionSetOnUser(int userId, String restriction) throws Exception {
        String commandOutput = getDevice().executeShellCommand("dumpsys user");
        String[] outputLines = commandOutput.split("\\n");
        Pattern userPattern = Pattern.compile("(^.*)UserInfo\\{" + userId + ":.*$");
        Pattern restrictionPattern = Pattern.compile("(^.*)Effective\\srestrictions\\:.*$");

        boolean userFound = false;
        boolean restrictionsFound = false;
        int lastIndent = -1;

        for (String line : outputLines) {
            // Starting a new block of user infos
            if (!line.startsWith(Strings.repeat(" ", lastIndent + 1))) {
                CLog.d("User %d restrictions found, no matched restriction.", userId);
                return false;
            }
            //First, try matching user pattern
            Matcher userMatcher = userPattern.matcher(line);
            if (userMatcher.find()) {
                CLog.d("User %d found in dumpsys, finding restrictions.", userId);
                userFound = true;
                lastIndent = userMatcher.group(1).length();
            }

            // Second, try matching restriction
            Matcher restrictionMatcher = restrictionPattern.matcher(line);
            if (userFound && restrictionMatcher.find()) {
                CLog.d("User %d restrictions found, finding exact restriction.", userId);
                restrictionsFound = true;
                lastIndent = restrictionMatcher.group(1).length();
            }

            if (restrictionsFound && line.contains(restriction)) {
                return true;
            }
        }
        if (!userFound) {
            CLog.e("User %d not found in dumpsys.", userId);
        }
        if (!restrictionsFound) {
            CLog.d("User %d found in dumpsys, but restrictions not found.", userId);
        }
        return false;
    }

    /**
     * Generates instrumentation arguments that indicate the device-side test is exercising device
     * owner APIs.
     *
     * <p>This is needed for hostside tests that use the same class hierarchy for both device and
     * profile owner tests, as on headless system user mode the test side must decide whether to
     * use its "local DPC" or wrap the calls to the system user DPC.
     */
    protected static Map<String, String> getParamsForDeviceOwnerTest() {
        Map<String, String> params = new HashMap<>();
        params.put("admin_type", "DeviceOwner");
        return params;
    }

    boolean isTv() throws DeviceNotAvailableException {
        return hasDeviceFeature(FEATURE_LEANBACK);
    }

    boolean isAutomotive() throws DeviceNotAvailableException {
        return hasDeviceFeature(FEATURE_AUTOMOTIVE);
    }

    void pushUpdateFileToDevice(String fileName)
            throws IOException, DeviceNotAvailableException {
        File file = File.createTempFile(
                fileName.split("\\.")[0], "." + fileName.split("\\.")[1]);
        try (OutputStream outputStream = new FileOutputStream(file)) {
            InputStream inputStream = getClass().getResourceAsStream("/" + fileName);
            ByteStreams.copy(inputStream, outputStream);
        }

        getDevice().pushFile(file, TEST_UPDATE_LOCATION + "/" + fileName);
        file.delete();
    }

    boolean hasService(String service) {
        String command = "service check " + service;
        try {
            String commandOutput = getDevice().executeShellCommand(command);
            return !commandOutput.contains("not found");
        } catch (Exception e) {
            CLog.w("Exception running '" + command + "': " + e);
            return false;
        }
    }

    void sleep(int timeMs) throws InterruptedException {
        CLog.d("Sleeping %d ms", timeMs);
        RunUtil.getDefault().sleep(timeMs);
    }

    private boolean isSmsCapable() throws Exception {
        String output = getDevice().executeShellCommand("dumpsys phone");
        if (output.contains("isSmsCapable=true")) {
            CLog.d("Device is SMS capable");
            return true;
        }
        CLog.d("Device is not SMS capable");
        return false;
    }
}
