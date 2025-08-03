/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.server.wm;

import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.second.Components.IMPLICIT_TARGET_SECOND_TEST_ACTION;
import static android.window.DisplayAreaOrganizer.FEATURE_UNDEFINED;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.server.wm.CommandSession.LaunchInjector;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.text.TextUtils;
import android.util.Log;

/** Utility class which contains common code for launching activities. */
public class ActivityLauncher {
    public static final String TAG = ActivityLauncher.class.getSimpleName();

    /** Key for string extra, indicates the action to apply. */
    public static final String KEY_ACTION = "intent_action";
    /** Key for boolean extra, indicates whether it should launch an activity. */
    public static final String KEY_LAUNCH_ACTIVITY = "launch_activity";
    /** Key for boolean extra, indicates whether it should launch implicitly. */
    public static final String KEY_LAUNCH_IMPLICIT = "launch_implicit";
    /** Key for boolean extra, indicates whether it should launch fromm pending intent. */
    public static final String KEY_LAUNCH_PENDING = "launch_pending";
    /**
     * Key for boolean extra, indicates whether it the activity should be launched to side in
     * split-screen.
     */
    public static final String KEY_LAUNCH_TO_SIDE = "launch_to_the_side";
    /**
     * Key for boolean extra, indicates if launch intent should include random data to be different
     * from other launch intents.
     */
    public static final String KEY_RANDOM_DATA = "random_data";
    /**
     * Key for boolean extra, indicates if launch intent should have
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK}.
     */
    public static final String KEY_NEW_TASK = "new_task";
    /**
     * Key for boolean extra, indicates if launch intent should have
     * {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}.
     */
    public static final String KEY_MULTIPLE_TASK = "multiple_task";
    /**
     * Key for boolean extra, indicates if launch intent should have
     * {@link Intent#FLAG_ACTIVITY_REORDER_TO_FRONT}.
     */
    public static final String KEY_REORDER_TO_FRONT = "reorder_to_front";
    /**
     * Key for boolean extra, indicates if launch task without presented to user.
     * {@link ActivityOptions#makeTaskLaunchBehind()}.
     */
    public static final String KEY_LAUNCH_TASK_BEHIND = "launch_task_behind";
    /**
     * Key for string extra with string representation of target component.
     */
    public static final String KEY_TARGET_COMPONENT = "target_component";
    /**
     * Key for int extra with target display id where the activity should be launched. Adding this
     * automatically applies {@link Intent#FLAG_ACTIVITY_NEW_TASK} and
     * {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK} to the intent.
     */
    public static final String KEY_DISPLAY_ID = "display_id";
    /**
     * Key for boolean extra, indicates if launch should be done from application context of the one
     * passed in {@link #launchActivityFromExtras(Context, Bundle)}.
     */
    public static final String KEY_USE_APPLICATION_CONTEXT = "use_application_context";
    /**
     * Key for boolean extra, indicates if any exceptions thrown during launch other then
     * {@link SecurityException} should be suppressed. A {@link SecurityException} is never thrown,
     * it's always written to logs.
     */
    public static final String KEY_SUPPRESS_EXCEPTIONS = "suppress_exceptions";
    /**
     * Key for boolean extra, indicates the result of
     * {@link ActivityManager#isActivityStartAllowedOnDisplay(Context, int, Intent)}
     */
    public static final String KEY_IS_ACTIVITY_START_ALLOWED_ON_DISPLAY =
            "is_activity_start_allowed_on_display";
    /**
     * Key for boolean extra, indicates a security exception is caught when launching activity by
     * {@link #launchActivityFromExtras}.
     */
    private static final String KEY_CAUGHT_SECURITY_EXCEPTION = "caught_security_exception";
    /**
     * Key for boolean extra, indicates a pending intent canceled exception is caught when
     * launching activity by {@link #launchActivityFromExtras}.
     */
    private static final String KEY_CAUGHT_PENDING_INTENT_CANCELED_EXCEPTION =
            "caught_pending_intent_exception";
    /**
     * Key for int extra with target activity type where activity should be launched as.
     */
    public static final String KEY_ACTIVITY_TYPE = "activity_type";
    /**
     * Key for int extra with intent flags which are used for launching an activity.
     */
    public static final String KEY_INTENT_FLAGS = "intent_flags";
    /**
     * Key for boolean extra, indicates if need to automatically applies
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK} to
     * the intent when target display id set.
     */
    public static final String KEY_MULTIPLE_INSTANCES = "multiple_instances";

    /**
     * Key for bundle extra to the intent which are used for launching an activity.
     */
    public static final String KEY_INTENT_EXTRAS = "intent_extras";

    /**
     * Key for int extra, indicates the requested windowing mode.
     */
    public static final String KEY_WINDOWING_MODE = "windowing_mode";

    /**
     * Key for int extra, indicates the launch TaskDisplayArea feature id
     */
    public static final String KEY_TASK_DISPLAY_AREA_FEATURE_ID = "task_display_area_feature_id";


    /** Perform an activity launch configured by provided extras. */
    public static void launchActivityFromExtras(final Context context, Bundle extras) {
        launchActivityFromExtras(context, extras, null /* launchInjector */);
    }

    /**
     * A convenience method to default to false if the extras are null.
     *
     * @param extras {@link Bundle} extras used to launch activity
     * @param key key to look up in extras
     * @return the value for the given key in the extra or false if extras is null
     */
    private static boolean getBoolean(Bundle extras, String key) {
        return extras != null && extras.getBoolean(key);
    }

    public static void launchActivityFromExtras(final Context context, Bundle extras,
            LaunchInjector launchInjector) {
        if (!getBoolean(extras, KEY_LAUNCH_ACTIVITY)) {
            return;
        }
        Log.i(TAG, "launchActivityFromExtras: extras=" + extras);

        final Intent newIntent = new Intent();

        if (getBoolean(extras, KEY_LAUNCH_IMPLICIT)) {
            newIntent.setAction(extras.getString(KEY_ACTION, IMPLICIT_TARGET_SECOND_TEST_ACTION));
        } else {
            final String targetComponent = extras.getString(KEY_TARGET_COMPONENT);
            final ComponentName componentName = TextUtils.isEmpty(targetComponent)
                    ? TEST_ACTIVITY : ComponentName.unflattenFromString(targetComponent);
            newIntent.setComponent(componentName);
        }

        if (getBoolean(extras, KEY_LAUNCH_TO_SIDE)) {
            newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT);
            if (getBoolean(extras, KEY_RANDOM_DATA)) {
                final Uri data = new Uri.Builder()
                        .path(String.valueOf(System.currentTimeMillis()))
                        .build();
                newIntent.setData(data);
            }
        }
        if (getBoolean(extras, KEY_MULTIPLE_TASK)) {
            newIntent.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        if (getBoolean(extras, KEY_NEW_TASK)) {
            newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        }

        if (getBoolean(extras, KEY_REORDER_TO_FRONT)) {
            newIntent.addFlags(FLAG_ACTIVITY_REORDER_TO_FRONT);
        }

        final Bundle intentExtras = extras.getBundle(KEY_INTENT_EXTRAS) ;
        if (intentExtras != null) {
            newIntent.putExtras(intentExtras);
        }

        ActivityOptions options = extras.getBoolean(KEY_LAUNCH_TASK_BEHIND)
                ? ActivityOptions.makeTaskLaunchBehind() : null;
        final int displayId = extras.getInt(KEY_DISPLAY_ID, -1);
        if (displayId != -1) {
            if (options == null) {
                options = ActivityOptions.makeBasic();
            }
            options.setLaunchDisplayId(displayId);
            if (extras.getBoolean(KEY_MULTIPLE_INSTANCES)) {
                newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);
            }
        }
        final int windowingMode = extras.getInt(KEY_WINDOWING_MODE, -1);
        if (windowingMode != -1) {
            if (options == null) {
                options = ActivityOptions.makeBasic();
            }
            options.setLaunchWindowingMode(windowingMode);
        }
        if (launchInjector != null) {
            launchInjector.setupIntent(newIntent);
        }
        final int activityType = extras.getInt(KEY_ACTIVITY_TYPE, -1);
        if (activityType != -1) {
            if (options == null) {
                options = ActivityOptions.makeBasic();
            }
            options.setLaunchActivityType(activityType);
        }
        final int intentFlags = extras.getInt(KEY_INTENT_FLAGS); // 0 if key doesn't exist.
        if (intentFlags != 0) {
            newIntent.addFlags(intentFlags);
        }

        final int launchTaskDisplayAreaFeatureId = extras
                .getInt(KEY_TASK_DISPLAY_AREA_FEATURE_ID, FEATURE_UNDEFINED);
        if (launchTaskDisplayAreaFeatureId != FEATURE_UNDEFINED) {
            if (options == null) {
                options = ActivityOptions.makeBasic();
            }
            options.setLaunchTaskDisplayAreaFeatureId(launchTaskDisplayAreaFeatureId);
        }

        final Bundle optionsBundle = options != null ? options.toBundle() : null;

        final Context launchContext = getBoolean(extras, KEY_USE_APPLICATION_CONTEXT) ?
                context.getApplicationContext() : context;

        try {
            if (getBoolean(extras, KEY_LAUNCH_PENDING)) {
                PendingIntent pendingIntent = PendingIntent.getActivity(launchContext,
                        0, newIntent, PendingIntent.FLAG_IMMUTABLE,
                        ActivityOptions.makeBasic()
                                .setPendingIntentCreatorBackgroundActivityStartMode(
                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                .toBundle());
                pendingIntent.send();
            } else {
                launchContext.startActivity(newIntent, optionsBundle);
            }
        } catch (SecurityException e) {
            handleSecurityException(context, e);
        } catch (PendingIntent.CanceledException e) {
            handlePendingIntentCanceled(context, e);
        } catch (Exception e) {
            if (extras.getBoolean(KEY_SUPPRESS_EXCEPTIONS)) {
                Log.e(TAG, "Exception launching activity");
            } else {
                throw e;
            }
        }
    }

    public static void checkActivityStartOnDisplay(Context context, int displayId,
            ComponentName componentName) {
        final Intent launchIntent = new Intent(Intent.ACTION_VIEW).setComponent(componentName);

        final boolean isAllowed = context.getSystemService(ActivityManager.class)
                .isActivityStartAllowedOnDisplay(context, displayId, launchIntent);
        Log.i(TAG, "isActivityStartAllowedOnDisplay=" + isAllowed);
        TestJournalProvider.putExtras(context, TAG, bundle -> {
            bundle.putBoolean(KEY_IS_ACTIVITY_START_ALLOWED_ON_DISPLAY, isAllowed);
        });
    }

    public static void handleSecurityException(Context context, Exception e) {
        Log.e(TAG, "SecurityException launching activity: " + e);
        TestJournalProvider.putExtras(context, TAG, bundle -> {
            bundle.putBoolean(KEY_CAUGHT_SECURITY_EXCEPTION, true);
        });
    }

    public static void handlePendingIntentCanceled(Context context, Exception e) {
        Log.e(TAG, "PendingIntent.CanceledException launching activity: " + e);
        TestJournalProvider.putExtras(context, TAG, bundle -> {
            bundle.putBoolean(KEY_CAUGHT_PENDING_INTENT_CANCELED_EXCEPTION, true);
        });
    }

    static boolean hasCaughtSecurityException() {
        return TestJournalContainer.get(TAG).extras.containsKey(KEY_CAUGHT_SECURITY_EXCEPTION);
    }
}
