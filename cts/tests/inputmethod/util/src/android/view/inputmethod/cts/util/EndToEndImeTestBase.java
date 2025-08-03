/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.inputmethod.cts.util;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.lang.reflect.Method;
import java.util.List;

public class EndToEndImeTestBase {

    @Rule
    public TestName mTestName = new TestName();

    protected final CtsTouchUtils mCtsTouchUtils = new CtsTouchUtils(
            InstrumentationRegistry.getInstrumentation().getTargetContext());

    /** Returns a unique marker based on the concrete class name and elapsed time. */
    protected String createUniqueMarker() {
        return getClass().getName() + "/" + SystemClock.elapsedRealtimeNanos();
    }

    /** Command to get verbose ImeTracker logging state. */
    private static final String GET_VERBOSE_IME_TRACKER_LOGGING_CMD =
            "getprop persist.debug.imetracker";

    /** Command to set verbose ImeTracker logging state. */
    private static final String SET_VERBOSE_IME_TRACKER_LOGGING_CMD =
            "setprop persist.debug.imetracker";

    /**
     * Whether verbose ImeTracker logging was enabled prior to running the tests,
     * used to handle reverting the state when the test run ends.
     */
    private static boolean sWasVerboseImeTrackerLoggingEnabled;

    /**
     * Skip test executions for know broken platforms.
     */
    @Before
    public final void checkSupportedPlatforms() {
        // STOPSHIP(b/288952673): Re-enable tests for wear once it becomes stable enough.
        assumeFalse(FeatureUtil.isWatch());
    }

    /**
     * Enters touch mode when instrumenting.
     *
     * Making the view focus state in instrumentation process more reliable in case when
     * {@link android.view.View#clearFocus()} invoked but system may reFocus again when the view
     * was not in touch mode. (i.e {@link android.view.View#isInTouchMode()} is {@code false}).
     */
    @Before
    public final void enterTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true);
    }

    /**
     * Restore to the default touch mode state after the test.
     */
    @After
    public final void restoreTouchMode() {
        InstrumentationRegistry.getInstrumentation().resetInTouchMode();
    }

    /**
     * Our own safeguard in case "atest" command is regressed and start running tests with
     * {@link AppModeInstant} even when {@code --instant} option is not specified.
     *
     * <p>Unfortunately this scenario had regressed at least 3 times.  That's why we also check
     * this in our side.  See Bug 158617529, Bug 187211725 and Bug 187222205 for examples.</p>
     */
    @Before
    public void verifyAppModeConsistency() {
        final Class<?> thisClass = this.getClass();
        final String testMethodName = mTestName.getMethodName();
        final String fullTestMethodName = thisClass.getSimpleName() + "#" + testMethodName;

        final Method testMethod;
        try {
            testMethod = thisClass.getMethod(testMethodName);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Failed to find " + fullTestMethodName, e);
        }

        final boolean hasAppModeFull = testMethod.getAnnotation(AppModeFull.class) != null;
        final boolean hasAppModeInstant = testMethod.getAnnotation(AppModeInstant.class) != null;

        if (hasAppModeFull && hasAppModeInstant) {
            fail("Both @AppModeFull and @AppModeInstant are found in " + fullTestMethodName
                    + ", which does not make sense. "
                    + "Remove both to make it clear that this test is app-mode agnostic, "
                    + "or specify one of them otherwise.");
        }

        // We want to explicitly check this condition in case tests are executed with atest
        // command.  See Bug 158617529 for details.
        if (hasAppModeFull) {
            assumeFalse("This test should run under and only under the full app mode.",
                    InstrumentationRegistry.getInstrumentation().getTargetContext()
                            .getPackageManager().isInstantApp());
        }
        if (hasAppModeInstant) {
            assumeTrue("This test should run under and only under the instant app mode.",
                    InstrumentationRegistry.getInstrumentation().getTargetContext()
                            .getPackageManager().isInstantApp());
        }
    }

    @Before
    public void showStateInitializeActivity() {
        // TODO(b/37502066): Move this back to @BeforeClass once b/37502066 is fixed.
        assumeTrue("MockIme cannot be used for devices that do not support installable IMEs",
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS));

        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClass(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        StateInitializeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    @Before
    public void clearLaunchParams() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final ActivityTaskManager atm = context.getSystemService(ActivityTaskManager.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            // Clear launch params for all test packages to make sure each test is run in a clean
            // state.
            atm.clearLaunchParamsForPackages(List.of(context.getPackageName()));
        }, Manifest.permission.MANAGE_ACTIVITY_TASKS);
    }

    protected static boolean isPreventImeStartup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try {
            return context.getResources().getBoolean(
                    android.R.bool.config_preventImeStartupUnlessTextEditor);
        } catch (Resources.NotFoundException e) {
            // Assume this is not enabled.
            return false;
        }
    }

    /**
     * Enables verbose logging in {@link android.view.inputmethod.ImeTracker}.
     */
    @BeforeClass
    public static void enableVerboseImeTrackerLogging() {
        sWasVerboseImeTrackerLoggingEnabled = getVerboseImeTrackerLogging();
        if (!sWasVerboseImeTrackerLoggingEnabled) {
            setVerboseImeTrackerLogging(true);
        }
    }

    /**
     * Reverts verbose logging in {@link android.view.inputmethod.ImeTracker} to the previous value.
     */
    @AfterClass
    public static void revertVerboseImeTrackerLogging() {
        if (!sWasVerboseImeTrackerLoggingEnabled) {
            setVerboseImeTrackerLogging(false);
        }
    }

    /**
     * Gets the verbose logging state in {@link android.view.inputmethod.ImeTracker}.
     *
     * @return {@code true} iff verbose logging is enabled.
     */
    private static boolean getVerboseImeTrackerLogging() {
        return SystemUtil.runShellCommand(GET_VERBOSE_IME_TRACKER_LOGGING_CMD).trim().equals("1");
    }

    /**
     * Sets verbose logging in {@link android.view.inputmethod.ImeTracker}.
     *
     * @param enabled whether to enable or disable verbose logging.
     *
     * @implNote This must use {@link ActivityManager#notifySystemPropertiesChanged()} to listen
     *           for changes to the system property for the verbose ImeTracker logging.
     */
    private static void setVerboseImeTrackerLogging(boolean enabled) {
        final var context = InstrumentationRegistry.getInstrumentation().getContext();
        final var am = context.getSystemService(ActivityManager.class);

        SystemUtil.runShellCommand(
                SET_VERBOSE_IME_TRACKER_LOGGING_CMD + " " + (enabled ? "1" : "0"));
        am.notifySystemPropertiesChanged();
    }
}
