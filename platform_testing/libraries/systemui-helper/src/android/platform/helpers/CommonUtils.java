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

package android.platform.helpers;

import static android.platform.helpers.ui.UiAutomatorUtils.getInstrumentation;
import static android.platform.helpers.ui.UiAutomatorUtils.getUiDevice;
import static android.platform.helpers.ui.UiSearch.search;
import static android.platform.uiautomator_helpers.DeviceHelpers.getContext;

import static com.google.common.truth.Truth.assertWithMessage;

import static java.lang.String.format;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.platform.helpers.features.common.HomeLockscreenPage;
import android.platform.test.util.HealthTestingUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for writing System UI ui tests. It consists of common utils required while writing
 * UI tests.
 */
public class CommonUtils {

    private static final int LARGE_SCREEN_DP_THRESHOLD = 600;
    private static final String TAG = "CommonUtils";
    private static final int SWIPE_STEPS = 100;
    private static final int DEFAULT_MARGIN = 5;
    private static final String LIST_ALL_USERS_COMMAND = "cmd user list -v --all";
    private static final String GET_MAIN_USER_COMMAND = "cmd user get-main-user";

    private CommonUtils() {
    }

    /**
     * Prints a message to standard output during an instrumentation test.
     *
     * Message will be printed to terminal if test is run using {@code am instrument}. This is
     * useful for debugging.
     */
    public static void println(String msg) {
        final Bundle streamResult = new Bundle();
        streamResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, msg + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, streamResult);
    }

    /**
     * This method help you execute you shell command.
     * Example: adb shell pm list packages -f
     * Here you just need to provide executeShellCommand("pm list packages -f")
     *
     * @param command command need to executed.
     * @return output in String format.
     */
    public static String executeShellCommand(String command) {
        Log.d(TAG, format("Executing Shell Command: %s", command));
        try {
            String out = getUiDevice().executeShellCommand(command);
            return out;
        } catch (IOException e) {
            Log.d(TAG, format("IOException Occurred: %s", e));
            throw new RuntimeException(e);
        }
    }

    /** Returns PIDs of all System UI processes */
    private static String[] getSystemUiPids() {
        String output = executeShellCommand("pidof com.android.systemui");
        if (output.isEmpty()) {
            // explicit check empty string, and return 0-length array.
            // "".split("\\s") returns 1-length array [""], which invalidates
            // allSysUiProcessesRestarted check.
            return new String[0];
        }
        return output.split("\\s");
    }

    private static boolean allSysUiProcessesRestarted(List<String> initialPidsList) {
        final String[] currentPids = getSystemUiPids();
        Log.d(TAG, "restartSystemUI: Current PIDs=" + Arrays.toString(currentPids));
        if (currentPids.length < initialPidsList.size()) {
            return false; // No all processes restarted.
        }
        for (String pid : currentPids) {
            if (initialPidsList.contains(pid)) {
                return false; // Old process still running.
            }
        }
        return true;
    }

    /**
     * Restart System UI by running {@code am crash com.android.systemui}.
     *
     * <p>This is sometimes necessary after changing flags, configs, or settings ensure that
     * systemui is properly initialized with the new changes. This method will wait until the home
     * screen is visible, then it will optionally dismiss the home screen via swipe.
     *
     * @param swipeUp whether to call {@link HomeLockscreenPage#swipeUp()} after restarting System
     *     UI
     * @deprecated Use {@link SysuiRestarter} instead. It has been moved out from here to use
     *     androidx uiautomator version (this class depends on the old version, and there are many
     *     deps that don't allow to easily switch to the new androidx one)
     */
    @Deprecated
    public static void restartSystemUI(boolean swipeUp) {
        SysuiRestarter.restartSystemUI(swipeUp);
    }

    /** Asserts that the screen is on. */
    public static void assertScreenOn(String errorMessage) {
        try {
            assertWithMessage(errorMessage)
                    .that(getUiDevice().isScreenOn())
                    .isTrue();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method to swipe the given object.
     *
     * @param gestureType direction which to swipe.
     * @param obj         object which needs to be swiped.
     */
    public static void swipe(GestureType gestureType, UiObject obj) {
        Log.d(TAG, format("Swiping Object[%s] %s", obj.getSelector(), gestureType));
        try {
            Rect boundary = obj.getBounds();
            final int displayHeight = getUiDevice().getDisplayHeight() - DEFAULT_MARGIN;
            final int displayWidth = getUiDevice().getDisplayWidth() - DEFAULT_MARGIN;
            final int objHeight = boundary.height();
            final int objWidth = boundary.width();
            final int marginHeight = (Math.abs(displayHeight - objHeight)) / 2;
            final int marginWidth = (Math.abs(displayWidth - objWidth)) / 2;
            switch (gestureType) {
                case DOWN:
                    getUiDevice().swipe(
                            marginWidth + (objWidth / 2),
                            marginHeight,
                            marginWidth + (objWidth / 2),
                            displayHeight,
                            SWIPE_STEPS
                    );
                    break;
                case UP:
                    getUiDevice().swipe(
                            marginWidth + (objWidth / 2),
                            displayHeight,
                            marginWidth + (objWidth / 2),
                            marginHeight,
                            SWIPE_STEPS
                    );
                    break;
                case RIGHT:
                    getUiDevice().swipe(
                            marginWidth,
                            marginHeight + (objHeight / 2),
                            displayWidth,
                            marginHeight + (objHeight / 2),
                            SWIPE_STEPS
                    );
                    break;
                case LEFT:
                    getUiDevice().swipe(
                            displayWidth,
                            marginHeight + (objHeight / 2),
                            marginWidth,
                            marginHeight + (objHeight / 2),
                            SWIPE_STEPS
                    );
                    break;
            }
        } catch (UiObjectNotFoundException e) {
            Log.e(TAG,
                    format("Given object was not found. Hence failed to swipe. Exception %s", e));
            throw new RuntimeException(e);
        }
    }

    /**
     * Launching an app with different ways.
     *
     * @param launchAppWith                  options used to launching an app.
     * @param packageActivityOrComponentName required package or activity or component name to
     *                                       launch the given app
     * @param appName                        name of the app
     */
    public static void launchApp(LaunchAppWith launchAppWith, String packageActivityOrComponentName,
            String appName) {
        Log.d(TAG, String.format("Opening app %s using their %s [%s]",
                appName, launchAppWith, packageActivityOrComponentName));
        Intent appIntent = null;
        switch (launchAppWith) {
            case PACKAGE_NAME:
                PackageManager packageManager = getContext().getPackageManager();
                appIntent = packageManager.getLaunchIntentForPackage(
                        packageActivityOrComponentName);
                appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                break;
            case ACTIVITY:
                appIntent = new Intent(packageActivityOrComponentName);
                break;
            case COMPONENT_NAME:
                ComponentName componentName = ComponentName.unflattenFromString(
                        packageActivityOrComponentName);
                appIntent = new Intent();
                appIntent.setComponent(componentName);
                break;
            default:
                throw new AssertionError("Non-supported Launch App with: " + launchAppWith);
        }
        // Ensure the app is completely restarted so that none of the test app's state
        // leaks between tests.
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getContext().startActivity(appIntent);
    }

    /**
     * Asserts that a given page is visible.
     *
     * @param pageSelector      selector helped to verify the page
     * @param pageName          name of the page to be verified
     * @param maxTimeoutSeconds max time in seconds to verify the page
     */
    public static void assertPageVisible(BySelector pageSelector, String pageName,
            int maxTimeoutSeconds) {
        assertWithMessage(format("Page[%s] not visible; selector: %s", pageName, pageSelector))
                .that(search(null, pageSelector, format("Page[%s]", pageName), maxTimeoutSeconds))
                .isTrue();
    }

    /**
     * Asserts that a given page is not visible.
     *
     * @param pageSelector selector helped to verify the page
     * @param pageName     name of the page to be verified
     */
    public static void assertPageNotVisible(BySelector pageSelector, String pageName) {
        HealthTestingUtils.waitForCondition(
                () -> "Page is still visible",
                () -> !search(null, pageSelector, format("Page[%s]", pageName), 0));
    }

    /**
     * Execute the given shell command and get the detailed output
     *
     * @param shellCommand shell command to be executed
     * @return the detailed output as an arraylist.
     */
    public static ArrayList<String> executeShellCommandWithDetailedOutput(String shellCommand) {
        try {
            ParcelFileDescriptor fileDescriptor =
                    getInstrumentation().getUiAutomation().executeShellCommand(shellCommand);
            byte[] buf = new byte[512];
            int bytesRead;
            FileInputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(
                    fileDescriptor);
            ArrayList<String> output = new ArrayList<>();
            while ((bytesRead = inputStream.read(buf)) != -1) {
                output.add(new String(buf, 0, bytesRead));
            }
            inputStream.close();
            return output;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the current user user ID. NOTE: UserID = 0 is for System
     *
     * @return a current user ID
     */
    public static int getCurrentUserId() {
        Log.d(TAG, "Getting the Current User ID");

        // Example terminal output of the list all users command:
        //
        //  $ adb shell cmd user list -v --all
        // 2 users:
        //
        // 0: id=0, name=Owner, type=full.SYSTEM, flags=FULL|INITIALIZED|PRIMARY|SYSTEM (running)
        // 1: id=10, name=Guest, type=full.GUEST, flags=FULL|GUEST|INITIALIZED (running) (current)
        ArrayList<String> output = executeShellCommandWithDetailedOutput(LIST_ALL_USERS_COMMAND);
        String getCurrentUser = null;
        for (String line : output) {
            if (line.contains("(current)")) {
                getCurrentUser = line;
                break;
            }
        }
        Pattern userRegex = Pattern.compile("[\\d]+:.*id=([\\d]+).*\\(current\\)");
        Matcher matcher = userRegex.matcher(getCurrentUser);
        while (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        Log.d(TAG, "Failed to find current user ID. dumpsys activity follows:");
        for (String line : output) {
            Log.d(TAG, line);
        }
        throw new RuntimeException("Failed to find current user ID.");
    }

    /**
     * Returns the main user ID. Main user is the main human user on the device.
     * Returns 0 by default, if there is no main user. Android Auto is example of HSUM without
     * main user.
     * 
     * NOTE: For headless system main user it is NOT 0. Therefore Main user should be used in 
     * test cases rather than owner or deprecated primary user.
     */
    public static int getMainUserId() {
        ArrayList<String> output = executeShellCommandWithDetailedOutput(GET_MAIN_USER_COMMAND);
        try {
            return Integer.parseInt(output.get(0).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isSplitShade() {
        int orientation = getContext().getResources().getConfiguration().orientation;
        return isLargeScreen() && orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isLargeScreen() {
        Point sizeDp = getUiDevice().getDisplaySizeDp();
        return sizeDp.x >= LARGE_SCREEN_DP_THRESHOLD && sizeDp.y >= LARGE_SCREEN_DP_THRESHOLD;
    }

    /**
     * Gesture for swipe
     */
    public enum GestureType {
        RIGHT,
        LEFT,
        UP,
        DOWN
    }

    /**
     * Different options used for launching an app.
     */
    public enum LaunchAppWith {
        PACKAGE_NAME,
        ACTIVITY,
        COMPONENT_NAME
    }
}
