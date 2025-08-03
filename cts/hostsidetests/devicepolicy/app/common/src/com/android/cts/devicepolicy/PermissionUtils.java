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

package com.android.cts.devicepolicy;

import static android.Manifest.permission.BODY_SENSORS;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PermissionUtils {
    private static final String LOG_TAG = PermissionUtils.class.getSimpleName();
    private static final Set<String> LOCATION_PERMISSIONS = new HashSet<String>();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    static {
        LOCATION_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
        LOCATION_PERMISSIONS.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        LOCATION_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private static final String ACTION_CHECK_HAS_PERMISSION
            = "com.android.cts.permission.action.CHECK_HAS_PERMISSION";
    private static final String ACTION_REQUEST_PERMISSION
            = "com.android.cts.permission.action.REQUEST_PERMISSION";
    private static final String EXTRA_PERMISSION = "com.android.cts.permission.extra.PERMISSION";

    public static void launchActivityAndCheckPermission(PermissionBroadcastReceiver receiver,
            String permission, int expected, String packageName, String activityName)
            throws Exception {
        launchActivityWithAction(permission, ACTION_CHECK_HAS_PERMISSION,
                packageName, activityName);
        assertBroadcastReceived(receiver, expected);
    }

    private static void assertBroadcastReceived(PermissionBroadcastReceiver receiver,
            int expected) throws Exception {
        int actual = receiver.waitForBroadcast();
        assertWithMessage("value returned by %s (%s=%s, %s=%s)", receiver,
                expected, permissionToString(expected),
                actual, permissionToString(actual))
                        .that(actual).isEqualTo(expected);
    }

    public static void launchActivityAndRequestPermission(PermissionBroadcastReceiver receiver,
            String permission, int expected, String packageName, String activityName)
            throws Exception {
        launchActivityWithAction(permission, ACTION_REQUEST_PERMISSION,
                packageName, activityName);
        assertBroadcastReceived(receiver, expected);
    }

    public static void launchActivityAndRequestPermission(PermissionBroadcastReceiver
            receiver, UiDevice device, String permission, int expected,
            String packageName, String activityName) throws Exception {
        final List<String> resNames = new ArrayList<>();
        switch(expected) {
            case PERMISSION_DENIED:
                resNames.add("permission_deny_button");
                resNames.add("permission_deny_and_dont_ask_again_button");
                break;
            case PERMISSION_GRANTED:
                resNames.add("permission_allow_button");
                // For some permissions, different buttons may be available.
                if (LOCATION_PERMISSIONS.contains(permission)
                        || RECORD_AUDIO.equals(permission)
                        || CAMERA.equals(permission)
                        || BODY_SENSORS.equals(permission)) {
                    resNames.add("permission_allow_foreground_only_button");
                    resNames.add("permission_allow_one_time_button");
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid expected permission");
        }
        launchActivityWithAction(permission, ACTION_REQUEST_PERMISSION,
                packageName, activityName);
        pressPermissionPromptButton(device, expected, resNames.toArray(new String[0]));
        assertBroadcastReceived(receiver, expected);
    }

    private static void launchActivityWithAction(String permission, String action,
            String packageName, String activityName) {
        Intent launchIntent = new Intent();
        launchIntent.setComponent(new ComponentName(packageName, activityName));
        launchIntent.putExtra(EXTRA_PERMISSION, permission);
        launchIntent.setAction(action);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        Log.d(LOG_TAG, "Launching activity (with intent " + launchIntent + ") for permission "
                + permission + " on uid " + Process.myUid());
        getContext().startActivity(launchIntent);
    }

    public static void checkPermission(String permission, int expected, String packageName) {
        checkPermission(getContext(), permission, expected, packageName);
    }

    public static void checkPermission(Context context, String permission, int expected,
            String packageName) {
        PackageManager pm = context.getPackageManager();
        Log.d(LOG_TAG, "checkPermission(" + permission + ", " + expected + ", " + packageName
                + "): " + "using " + pm + " on user " + context.getUser());
        assertPermission(permission, packageName, pm.checkPermission(permission, packageName),
                expected);
    }

    private static void assertPermission(String permission, String packageName, int actual,
            int expected) {
        assertWithMessage("Wrong status for permission %s on package %s (where %s=%s and %s=%s)",
                permission, packageName,
                expected, permissionToString(expected), actual, permissionToString(actual))
                        .that(actual).isEqualTo(expected);
    }

    /**
     * Correctly checks a runtime permission. This also works for pre-{@code M} apps.
     */
    public static void checkPermissionAndAppOps(String permission, int expected, String packageName)
            throws Exception {
        checkPermissionAndAppOps(getContext(), permission, expected, packageName);
    }

    /**
     * Correctly checks a runtime permission. This also works for pre-{@code M} apps.
     */
    public static void checkPermissionAndAppOps(Context context, String permission, int expected,
            String packageName) throws Exception {
        assertPermission(permission, packageName,
                checkPermissionAndAppOps(context, permission, packageName), expected);
    }

    private static int checkPermissionAndAppOps(Context context, String permission,
            String packageName) throws Exception {
        Log.d(LOG_TAG, "checkPermissionAndAppOps(): user=" + context.getUser()
                + ", permission=" + permission + ", packageName=" + packageName);
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
        if (context.checkPermission(permission, -1, packageInfo.applicationInfo.uid)
                == PERMISSION_DENIED) {
            return PERMISSION_DENIED;
        }

        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        if (appOpsManager != null && appOpsManager.noteProxyOpNoThrow(
                AppOpsManager.permissionToOp(permission), packageName,
                packageInfo.applicationInfo.uid, null, null)
                != AppOpsManager.MODE_ALLOWED) {
            return PERMISSION_DENIED;
        }

        return PERMISSION_GRANTED;
    }

    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static void pressPermissionPromptButton(UiDevice device, int expectedAction,
            String[] resNames) {
        UiObject2 button = findPermissionPromptButton(device, expectedAction, resNames);
        Log.d(LOG_TAG, "Clicking on '" + button.getText() + "'");
        button.click();
    }

    private static UiObject2 findPermissionPromptButton(UiDevice device, int expectedAction,
            String[] resNames) {
        if ((resNames == null) || (resNames.length == 0)) {
            throw new IllegalArgumentException("resNames must not be null or empty");
        }
        String action;
        switch (expectedAction) {
            case PERMISSION_DENIED:
                action = "PERMISSION_DENIED";
                break;
            case PERMISSION_GRANTED:
                action = "PERMISSION_GRANTED";
                break;
            default:
                throw new IllegalArgumentException("Invalid expected action: "
                        + expectedAction);
        }

        // The dialog was moved from the packageinstaller to the permissioncontroller.
        // Search in multiple packages so the test is not affixed to a particular package.
        String[] possiblePackages = new String[]{
                "com.android.permissioncontroller.permission.ui",
                "com.android.packageinstaller",
                "com.android.permissioncontroller"};

        Log.v(LOG_TAG, "findPermissionPromptButton(): pkgs= " + Arrays.toString(possiblePackages)
                + ", action=" + action + ", resIds=" + Arrays.toString(resNames));
        for (String resName : resNames) {
            for (String possiblePkg : possiblePackages) {
                BySelector selector = By
                        .clazz(android.widget.Button.class.getName())
                        .res(possiblePkg, resName);
                Log.v(LOG_TAG, "trying " + selector);
                device.wait(Until.hasObject(selector), 5000);
                UiObject2 button = device.findObject(selector);
                Log.d(LOG_TAG, String.format("Resource %s in Package %s found? %b", resName,
                        possiblePkg, button != null));
                if (button != null) {
                    return button;
                }
            }
        }

        if (!sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            fail("Did not find button for action " + action + " on packages "
                    + Arrays.toString(possiblePackages));
        }
        return findPermissionPromptButtonAutomotive(device, expectedAction);
    }

    private static UiObject2 findPermissionPromptButtonAutomotive(UiDevice device,
            int expectedAction) {
        // TODO: ideally the UI should use a more specific resource, so it doesn't need to search
        // for text
        Pattern resPattern = Pattern.compile(".*car_ui_list_item_title");
        Pattern textPattern;
        String action;
        switch (expectedAction) {
            case PERMISSION_DENIED:
                action = "PERMISSION_DENIED";
                textPattern = Pattern.compile("^Don’t allow$");
                break;
            case PERMISSION_GRANTED:
                action = "PERMISSION_GRANTED";
                textPattern = Pattern.compile("^Allow|While using the app$");
                break;
            default:
                throw new IllegalArgumentException("Invalid expected action: " + expectedAction);
        }
        Log.i(LOG_TAG, "Button not found on automotive build; searching for " + resPattern
                + " res and " + textPattern + " text instead");
        BySelector selector = By
                .clazz(android.widget.TextView.class.getName())
                 .text(textPattern)
                .res(resPattern);
        Log.v(LOG_TAG, "selector: " + selector);
        device.wait(Until.hasObject(selector), 5000);
        UiObject2 button = device.findObject(selector);
        Log.d(LOG_TAG, "button: " + button + (button == null ? "" : " (" + button.getText() + ")"));
        assertWithMessage("Found button with res %s and text '%s'", resPattern, textPattern)
                .that(button).isNotNull();

        return button;
    }

    public static String permissionGrantStateToString(int state) {
        return constantToString(DevicePolicyManager.class, "PERMISSION_GRANT_STATE_", state);
    }

    public static String permissionPolicyToString(int policy) {
        return constantToString(DevicePolicyManager.class, "PERMISSION_POLICY_", policy);
    }

    public static String permissionToString(int permission) {
        return constantToString(PackageManager.class, "PERMISSION_", permission);
    }

    // Copied from DebugUtils
    private static String constantToString(Class<?> clazz, String prefix, int value) {
        for (Field field : clazz.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            try {
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                        && field.getType().equals(int.class) && field.getName().startsWith(prefix)
                        && field.getInt(null) == value) {
                    return constNameWithoutPrefix(prefix, field);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return prefix + Integer.toString(value);
    }

    // Copied from DebugUtils
    private static String constNameWithoutPrefix(String prefix, Field field) {
        return field.getName().substring(prefix.length());
    }
}
