/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.content.Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import java.util.Map;

/**
 * Provides constants and utility methods to interact with
 * {@link android.view.inputmethod.ctstestapp.MainActivity}.
 */
public final class MockTestActivityUtil {
    public static final ComponentName TEST_ACTIVITY = new ComponentName(
            "android.view.inputmethod.ctstestapp",
            "android.view.inputmethod.ctstestapp.MainActivity");
    private static final Uri TEST_ACTIVITY_URI =
            Uri.parse("https://example.com/android/view/inputmethod/ctstestapp");

    public static final String ACTION_TRIGGER = "broadcast_action_trigger";

    /**
     * A key to be used as the {@code key} of {@link Map} passed as {@code extras} parameter of
     * {@link #launchSync(boolean, long, Map)}.
     *
     * <p>A valid {@code value} is either {@code "true"} or {@code "false"}.</p>
     */
    public static final String EXTRA_KEY_SHOW_DIALOG =
            "android.view.inputmethod.ctstestapp.EXTRA_KEY_SHOW_DIALOG";

    /**
     * A key to be used as the {@code key} of {@link Map} passed as {@code extras} parameter of
     * {@link #launchSync(boolean, long, Map)}.
     *
     * <p>The specified {@code value} will be set to
     * {@link android.view.inputmethod.EditorInfo#privateImeOptions}.</p>
     */
    public static final String EXTRA_KEY_PRIVATE_IME_OPTIONS =
            "android.view.inputmethod.ctstestapp.EXTRA_KEY_PRIVATE_IME_OPTIONS";

    /**
     * Can be passed to {@link #sendBroadcastAction(String)} to dismiss the dialog box if exists.
     */
    public static final String EXTRA_DISMISS_DIALOG = "extra_dismiss_dialog";

    /**
     * Can be passed to {@link #sendBroadcastAction(String)} call
     * {@link android.view.inputmethod.InputMethodManager#showSoftInput(android.view.View, int)}.
     */
    public static final String EXTRA_SHOW_SOFT_INPUT = "extra_show_soft_input";

    /**
     * Can be passed to {@link #sendBroadcastAction(String)} to declare editor as a
     * {@link android.view.View#setIsHandwritingDelegate(boolean) handwriting delegate}.
     */
    public static final String EXTRA_HANDWRITING_DELEGATE = "extra_handwriting_delegate";

    /**
     * Can be passed to {@link #sendBroadcastAction(String)} to declare editor as {@link
     * android.view.View#setHomeScreenHandwritingDelegatorAllowed(boolean)}.
     */
    public static final String EXTRA_HOME_HANDWRITING_DELEGATOR_ALLOWED =
            "extra_home_handwriting_delegator_allowed";

    /**
     * Is used by the {@link RemoteCallback} in launchSyncAsUser()
     */
    public static final String ACTION_KEY_REPLY_USER_HANDLE =
            "android.inputmethodservice.cts.ime.ReplyUserHandle";
    public static final String EXTRA_ON_CREATE_INPUT_CONNECTION_CALLBACK =
            "extra_on_create_input_connection_callback";
    public static final String EXTRA_ON_CREATE_USER_HANDLE_SESSION_ID =
            "extra_on_create_user_handle_session_id";

    @NonNull
    private static Uri formatStringIntentParam(@NonNull Uri uri, Map<String, String> extras) {
        if (extras == null) {
            return uri;
        }
        final Uri.Builder builder = uri.buildUpon();
        extras.forEach(builder::appendQueryParameter);
        return builder.build();
    }

    /**
     * Launches {@link "android.view.inputmethod.ctstestapp.MainActivity"}.
     *
     * @param instant {@code true} when the Activity is installed as an instant app.
     * @param timeout the timeout to wait until the Activity becomes ready.
     * @return {@link AutoCloseable} object to automatically stop the test Activity package.
     */
    public static AutoCloseable launchSync(boolean instant, long timeout) {
        return launchSync(instant, timeout, null);
    }

    /**
     * Launches {@link "android.view.inputmethod.ctstestapp.MainActivity"}.
     *
     * @param instant {@code true} when the Activity is installed as an instant app.
     * @param timeout the timeout to wait until the Activity becomes ready.
     * @param extras extra parameters to be passed to the Activity.
     * @return {@link AutoCloseable} object to automatically stop the test Activity package.
     */
    public static AutoCloseable launchSync(boolean instant, long timeout,
            @Nullable Map<String, String> extras) {
        final StringBuilder commandBuilder = new StringBuilder();
        if (instant) {
            // Override app-links domain verification.
            runShellCommandOrThrow(
                    String.format("pm set-app-links-user-selection --user cur --package %s true %s",
                            TEST_ACTIVITY.getPackageName(), TEST_ACTIVITY_URI.getHost()));
            final Uri uri = formatStringIntentParam(TEST_ACTIVITY_URI, extras);
            commandBuilder.append(String.format("am start -a %s -c %s --activity-clear-task %s",
                    Intent.ACTION_VIEW, Intent.CATEGORY_BROWSABLE, uri.toString()));
        } else {
            commandBuilder.append(String.format("am start -a %s -n %s --activity-clear-task",
                    Intent.ACTION_MAIN, TEST_ACTIVITY.flattenToShortString()));
            if (extras != null) {
                extras.forEach((key, value) -> commandBuilder.append(" --es ")
                        .append(key).append(" ").append(value));
            }
        }

        runWithShellPermissionIdentity(() -> {
            runShellCommandOrThrow(commandBuilder.toString());
        });
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        BySelector activitySelector = By.pkg(TEST_ACTIVITY.getPackageName()).depth(0);
        uiDevice.wait(Until.hasObject(activitySelector), timeout);

        // Make sure to stop package after test finished for resource reclaim.
        return () -> TestUtils.forceStopPackage(TEST_ACTIVITY.getPackageName());
    }

    /**
     * Launches {@link android.view.inputmethod.ctstestapp.MainActivity}.
     *
     * @param userId the user id for which the Activity should be started
     * @param instant {@code true} when the Activity is installed as an instant app.
     * @param extras extra parameters to be passed to the Activity.
     * @param onCreateInputConnectionCallback the callback that will be invoked, once the input
     *                                        connection was created
     * @return {@link AutoCloseable} object to automatically stop the test Activity package.
     */
    public static AutoCloseable launchSyncAsUser(int userId, boolean instant,
            @Nullable Map<String, String> extras, RemoteCallback onCreateInputConnectionCallback) {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final Intent intent = new Intent().setClassName(TEST_ACTIVITY.getPackageName(),
                TEST_ACTIVITY.getClassName()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (extras != null) {
            extras.forEach(intent::putExtra);
        }
        if (onCreateInputConnectionCallback != null) {
            intent.putExtra(EXTRA_ON_CREATE_INPUT_CONNECTION_CALLBACK,
                    onCreateInputConnectionCallback);
            intent.putExtra(EXTRA_ON_CREATE_USER_HANDLE_SESSION_ID,
                    Long.toString(SystemClock.elapsedRealtimeNanos()));
        }

        if (instant) {
            // Override app-links domain verification.
            runShellCommand(
                    String.format("pm set-app-links-user-selection --user %s --package %s true %s",
                            userId, TEST_ACTIVITY.getPackageName(), TEST_ACTIVITY_URI.getHost()));
            intent.setAction(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setData(TEST_ACTIVITY_URI);
        } else {
            intent.setAction(Intent.ACTION_MAIN);
        }
        runWithShellPermissionIdentity(() -> {
            context.startActivityAsUser(intent, UserHandle.of(userId));
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        // Make sure to stop package after test finished for resource reclaim.
        return () -> TestUtils.forceStopPackage(TEST_ACTIVITY.getPackageName());
    }

    /**
     * Sends a broadcast to {@link "android.view.inputmethod.ctstestapp.MainActivity"}.
     *
     * @param extra {@link #EXTRA_DISMISS_DIALOG} or {@link #EXTRA_SHOW_SOFT_INPUT}.
     */
    public static void sendBroadcastAction(String extra) {
        final StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("am broadcast -a ").append(ACTION_TRIGGER).append(" -p ").append(
                TEST_ACTIVITY.getPackageName());
        commandBuilder.append(" -f 0x").append(
                Integer.toHexString(FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS));
        commandBuilder.append(" --receiver-registered-only");
        commandBuilder.append(" --ez " + extra + " true");
        runWithShellPermissionIdentity(() -> {
            runShellCommand(commandBuilder.toString());
        });
    }

    /**
     * Force-stops {@link "android.view.inputmethod.ctstestapp"} package.
     */
    public static void forceStopPackage() {
        TestUtils.forceStopPackage(TEST_ACTIVITY.getPackageName());
    }

    /**
     * @return {@code "android.view.inputmethod.ctstestapp"}.
     */
    public static String getPackageName() {
        return TEST_ACTIVITY.getPackageName();
    }
}
