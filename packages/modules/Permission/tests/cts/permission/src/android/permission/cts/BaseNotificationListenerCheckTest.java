/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permission.cts;

import static android.os.Process.myUserHandle;
import static android.permission.cts.TestUtils.eventually;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.DeviceConfig;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;

import java.util.List;

/**
 * Base test class used for {@code NotificationListenerCheckTest} and
 * {@code NotificationListenerCheckWithSafetyCenterUnsupportedTest}
 */
public class BaseNotificationListenerCheckTest {
    private static final String LOG_TAG = BaseNotificationListenerCheckTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    protected static final String TEST_APP_PKG =
            "android.permission.cts.appthathasnotificationlistener";
    private static final String TEST_APP_NOTIFICATION_SERVICE =
            TEST_APP_PKG + ".CtsNotificationListenerService";
    protected static final String TEST_APP_NOTIFICATION_LISTENER_APK =
            "/data/local/tmp/cts-permission/CtsAppThatHasNotificationListener.apk";

    private static final int NOTIFICATION_LISTENER_CHECK_JOB_ID = 4;

    /**
     * Device config property for whether notification listener check is enabled on the device
     */
    private static final String PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED =
            "notification_listener_check_enabled";

    /**
     * Device config property for time period in milliseconds after which current enabled
     * notification
     * listeners are queried
     */
    protected static final String PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS =
            "notification_listener_check_interval_millis";

    protected static final Long OVERRIDE_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS =
            SECONDS.toMillis(0);

    private static final String ACTION_SET_UP_NOTIFICATION_LISTENER_CHECK =
            "com.android.permissioncontroller.action.SET_UP_NOTIFICATION_LISTENER_CHECK";
    private static final String NotificationListenerOnBootReceiver =
            "com.android.permissioncontroller.privacysources.SetupPeriodicNotificationListenerCheck";

    /**
     * ID for notification shown by
     * {@link com.android.permissioncontroller.privacysources.NotificationListenerCheck}.
     */
    public static final int NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID = 3;

    protected static final long UNEXPECTED_TIMEOUT_MILLIS = 10000;
    protected static final long ENSURE_NOTIFICATION_NOT_SHOWN_EXPECTED_TIMEOUT_MILLIS = 5000;

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final UiAutomation sUiAutomation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation();

    private static final String PERMISSION_CONTROLLER_PKG = sContext.getPackageManager()
            .getPermissionControllerPackageName();

    private static List<ComponentName> sPreviouslyEnabledNotificationListeners;

    // Override SafetyCenter enabled flag
    @Rule
    public DeviceConfigStateChangerRule sPrivacyDeviceConfigSafetyCenterEnabled =
            new DeviceConfigStateChangerRule(sContext,
                    DeviceConfig.NAMESPACE_PRIVACY,
                    SafetyCenterUtils.PROPERTY_SAFETY_CENTER_ENABLED,
                    Boolean.toString(true));

    // Override NlsCheck enabled flag
    @Rule
    public DeviceConfigStateChangerRule sPrivacyDeviceConfigNlsCheckEnabled =
            new DeviceConfigStateChangerRule(sContext,
                    DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED,
                    Boolean.toString(true));

    // Override general notification interval from once every day to once ever 1 second
    @Rule
    public DeviceConfigStateChangerRule sPrivacyDeviceConfigNlsCheckIntervalMillis =
            new DeviceConfigStateChangerRule(sContext,
                    DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS,
                    Long.toString(OVERRIDE_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS));

    @Rule
    public CtsNotificationListenerHelperRule ctsNotificationListenerHelper =
            new CtsNotificationListenerHelperRule(sContext);

    @BeforeClass
    public static void beforeClassSetup() throws Exception {
        // Bypass battery saving restrictions
        runShellCommand("cmd tare set-vip "
                + myUserHandle().getIdentifier() + " " + PERMISSION_CONTROLLER_PKG + " true");
        // Disallow any OEM enabled NLS
        disallowPreexistingNotificationListeners();
    }

    @AfterClass
    public static void afterClassTearDown() throws Throwable {
        // Reset battery saving restrictions
        runShellCommand("cmd tare set-vip "
                + myUserHandle().getIdentifier() + " " + PERMISSION_CONTROLLER_PKG + " default");
        // Reallow any previously OEM allowed NLS
        reallowPreexistingNotificationListeners();
    }

    protected static void setDeviceConfigPrivacyProperty(String propertyName, String value) {
        runWithShellPermissionIdentity(() -> {
            boolean valueWasSet =  DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_PRIVACY,
                    /* name = */ propertyName,
                    /* value = */ value,
                    /* makeDefault = */ false);
            if (!valueWasSet) {
                throw new  IllegalStateException("Could not set " + propertyName + " to " + value);
            }
        });
    }

    /**
     * Enable or disable notification listener check
     */
    protected static void setNotificationListenerCheckEnabled(boolean enabled) {
        setDeviceConfigPrivacyProperty(
                PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED,
                /* value = */ String.valueOf(enabled));
    }

    /**
     * Allow or disallow a {@link NotificationListenerService} component for the current user
     *
     * @param listenerComponent {@link NotificationListenerService} component to allow or disallow
     */
    private static void setNotificationListenerServiceAllowed(ComponentName listenerComponent,
            boolean allowed) {
        String command = " cmd notification " + (allowed ? "allow_listener " : "disallow_listener ")
                + listenerComponent.flattenToString();
        runShellCommand(command);
    }

    private static void disallowPreexistingNotificationListeners() {
        runWithShellPermissionIdentity(() -> {
            NotificationManager notificationManager =
                    sContext.getSystemService(NotificationManager.class);
            sPreviouslyEnabledNotificationListeners =
                    notificationManager.getEnabledNotificationListeners();
        });
        if (DEBUG) {
            Log.d(LOG_TAG, "Found " + sPreviouslyEnabledNotificationListeners.size()
                    + " previously allowed notification listeners. Disabling before test run.");
        }
        for (ComponentName listener : sPreviouslyEnabledNotificationListeners) {
            setNotificationListenerServiceAllowed(listener, false);
        }
    }

    private static void reallowPreexistingNotificationListeners() {
        if (DEBUG) {
            Log.d(LOG_TAG, "Re-allowing " + sPreviouslyEnabledNotificationListeners.size()
                    + " previously allowed notification listeners found before test run.");
        }
        for (ComponentName listener : sPreviouslyEnabledNotificationListeners) {
            setNotificationListenerServiceAllowed(listener, true);
        }
    }

    protected void allowTestAppNotificationListenerService() {
        setNotificationListenerServiceAllowed(
                new ComponentName(TEST_APP_PKG, TEST_APP_NOTIFICATION_SERVICE), true);
    }

    protected void disallowTestAppNotificationListenerService() {
        setNotificationListenerServiceAllowed(
                new ComponentName(TEST_APP_PKG, TEST_APP_NOTIFICATION_SERVICE), false);
    }

    /**
     * Force a run of the notification listener check.
     */
    protected static void runNotificationListenerCheck() throws Throwable {
        TestUtils.awaitJobUntilRequestedState(
                PERMISSION_CONTROLLER_PKG,
                NOTIFICATION_LISTENER_CHECK_JOB_ID,
                UNEXPECTED_TIMEOUT_MILLIS,
                sUiAutomation,
                "waiting"
        );

        TestUtils.runJobAndWaitUntilCompleted(
                PERMISSION_CONTROLLER_PKG,
                NOTIFICATION_LISTENER_CHECK_JOB_ID,
                UNEXPECTED_TIMEOUT_MILLIS,
                sUiAutomation
        );
    }

    /**
     * Skip tests for if Safety Center not supported
     */
    protected void assumeDeviceSupportsSafetyCenter() {
        assumeTrue(SafetyCenterUtils.deviceSupportsSafetyCenter(sContext));
    }

    /**
     * Skip tests for if Safety Center IS supported
     */
    protected void assumeDeviceDoesNotSupportSafetyCenter() {
        assumeFalse(SafetyCenterUtils.deviceSupportsSafetyCenter(sContext));
    }

    protected void wakeUpAndDismissKeyguard() {
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        runShellCommand("wm dismiss-keyguard");
    }

    /**
     * Reset the permission controllers state before each test
     */
    protected void resetPermissionControllerBeforeEachTest() throws Throwable {
        resetPermissionController();

        // ensure no posted notification listener notifications exits
        eventually(() -> assertNull(getNotification(false)), UNEXPECTED_TIMEOUT_MILLIS);

        // Reset job scheduler stats (to allow more jobs to be run)
        runShellCommand(
                "cmd jobscheduler reset-execution-quota -u " + myUserHandle().getIdentifier() + " "
                        + PERMISSION_CONTROLLER_PKG);
        runShellCommand("cmd jobscheduler reset-schedule-quota");
    }

    /**
     * Reset the permission controllers state.
     */
    private static void resetPermissionController() throws Throwable {
        PermissionUtils.resetPermissionControllerJob(sUiAutomation, PERMISSION_CONTROLLER_PKG,
                NOTIFICATION_LISTENER_CHECK_JOB_ID, 45000,
                ACTION_SET_UP_NOTIFICATION_LISTENER_CHECK, NotificationListenerOnBootReceiver);
    }

    /**
     * Preshow/dismiss cts NotificationListener notification as it negatively affects test results
     * (can result in unexpected test pass/failures)
     */
    protected void triggerAndDismissCtsNotificationListenerNotification() throws Throwable {
        // CtsNotificationListenerService isn't enabled at this point, but NotificationListener
        // should be. Mark as notified by showing and dismissing
        runNotificationListenerCheck();

        // Ensure notification shows and dismiss
        eventually(() -> assertNotNull(getNotification(true)),
                UNEXPECTED_TIMEOUT_MILLIS);
    }

    /**
     * Get a notification listener notification that is currently visible.
     *
     * @param cancelNotification if `true` the notification is canceled inside this method
     * @return The notification or `null` if there is none
     */
    protected StatusBarNotification getNotification(boolean cancelNotification) throws Throwable {
        return CtsNotificationListenerServiceUtils.getNotificationForPackageAndId(
                PERMISSION_CONTROLLER_PKG,
                NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID,
                cancelNotification);
    }

    /**
     * Clear any notifications related to NotificationListenerCheck to ensure clean test setup
     */
    protected void clearNotifications() throws Throwable {
        // Clear notification if present
        CtsNotificationListenerServiceUtils.cancelNotifications(PERMISSION_CONTROLLER_PKG);
    }
}
