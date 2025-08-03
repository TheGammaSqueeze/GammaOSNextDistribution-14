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

package android.translation.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentCaptureOptions;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.BitmapUtils;

import java.io.File;
import java.io.IOException;

/**
 * Helper for common funcionalities.
 */
public final class Helper {

    private static final String TAG = "Helper";

    public static final String ACTIVITY_PACKAGE = "android.translation.cts";

    public static final String ACTION_REGISTER_UI_TRANSLATION_CALLBACK =
            "android.translation.cts.action.REGISTER_UI_TRANSLATION_CALLBACK";
    public static final String ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK =
            "android.translation.cts.action.UNREGISTER_UI_TRANSLATION_CALLBACK";
    public static final String ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START =
            "android.translation.cts.action.ASSERT_UI_TRANSLATION_CALLBACK_ON_START";
    public static final String ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH =
            "android.translation.cts.action.ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH";
    public static final String ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME =
            "android.translation.cts.action.ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME";
    public static final String ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE =
            "android.translation.cts.action.ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE";

    public static final String EXTRA_FINISH_COMMAND = "finish_command";
    public static final String EXTRA_SOURCE_LOCALE = "source_locale";
    public static final String EXTRA_TARGET_LOCALE = "target_locale";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_CALL_COUNT = "call_count";

    public static final int COMMAND_CREATE_TRANSLATOR = 1;

    public static final String CUSTOM_TRANSLATION_ID_MY_TAG = "myTag";
    public static final String LOCAL_TEST_FILES_DIR = "/sdcard/CtsTranslationTestCases";
    public static final int TEMP_SERVICE_DURATION_MS = 30_000;

    private static final String LOG_TAG = "log.tag.UiTranslation";

    /**
     * Sets the translation service temporarily.
     *
     * @param service name of temporary translation service.
     */
    public static void setTemporaryTranslationService(String service) {
        Log.d(TAG, "Setting translation service to " + service);
        final int userId = UserHandle.myUserId();
        runShellCommand("cmd translation set temporary-service %d %s %d", userId, service,
                TEMP_SERVICE_DURATION_MS);
    }

    /**
     * Resets the translation service.
     */
    public static void resetTemporaryTranslationService() {
        final int userId = UserHandle.myUserId();
        Log.d(TAG, "Resetting back user " + userId + " to default translation service");
        runShellCommand("cmd translation set temporary-service %d", userId);
    }

    /**
     * Sets the content capture service temporarily.
     *
     * @param service name of temporary translation service.
     */
    public static void setTemporaryContentCaptureService(String service) {
        Log.d(TAG, "Setting content capture service to " + service);
        final int userId = UserHandle.myUserId();
        runShellCommand("cmd content_capture set temporary-service %d %s %d", userId, service,
                TEMP_SERVICE_DURATION_MS);
    }

    /**
     * Resets the content capture service.
     */
    public static void resetTemporaryContentCaptureService() {
        final int userId = UserHandle.myUserId();
        Log.d(TAG, "Resetting back user " + userId + " to default service");
        runShellCommand("cmd content_capture set temporary-service %d", userId);
    }

    /**
     * Enable or disable the default content capture service.
     *
     * @param enabled {@code true} to enable default content capture service.
     */
    public static void setDefaultContentCaptureServiceEnabled(boolean enabled) {
        final int userId = UserHandle.myUserId();
        Log.d(TAG, "setDefaultServiceEnabled(user=" + userId + ", enabled= " + enabled + ")");
        runShellCommand("cmd content_capture set default-service-enabled %d %s", userId,
                Boolean.toString(enabled));
    }

    /**
     * Add the cts itself into content capture allow list.
     *
     * @param context Context of the app.
     */
    public static void allowSelfForContentCapture(Context context) {
        final ContentCaptureOptions options = ContentCaptureOptions.forWhitelistingItself();
        Log.v(TAG, "allowSelfForContentCapture(): options=" + options);
        context.getApplicationContext().setContentCaptureOptions(options);
    }

    /**
     * Reset the cts itself from content capture allow list.
     *
     * @param context Context of the app.
     */
    public static void unAllowSelfForContentCapture(Context context) {
        Log.v(TAG, "unAllowSelfForContentCapture()");
        context.getApplicationContext().setContentCaptureOptions(null);
    }

    /**
     * Return a ui object for resource id.
     *
     * @param resourcePackage  package of the object
     * @param resourceId the resource id of the object
     */
    public static UiObject2 findObjectByResId(String resourcePackage, String resourceId) {
        final UiDevice uiDevice =
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        final UiObject2 foundObj = uiDevice.wait(
                        Until.findObject(By.res(resourcePackage, resourceId)), 5_000L);
        return foundObj;
    }

    /**
     * Enable DEBUG log and returns the original log level value.
     */
    public static String enableDebugLog() {
        String originalValue = System.getProperty(LOG_TAG, "");
        System.setProperty(LOG_TAG, "DEBUG");
        Log.d(TAG, "enableDebugLog(), original value = " + originalValue);
        return originalValue;
    }

    /**
     * Disable debug log.
     *
     * @param level the log level. The value can be DEBUG, INFO, VERBOSE or empty if not set.
     */
    public static void disableDebugLog(String level) {
        Log.d(TAG, "disableDebugLog(), set level  " + level);
        System.setProperty(LOG_TAG, level);
    }

    // TODO: Move to a library that can be shared for smart os components.
    /**
     * Takes a screenshot and save it in the file system for analysis.
     */
    public static void takeScreenshotAndSave(Context context, String testName,
            String targetFolder) {
        File file = null;
        try {
            file = createTestFile(testName,"sreenshot.png", targetFolder);
            if (file != null) {
                Log.i(TAG, "Taking screenshot on " + file);
                final Bitmap screenshot = takeScreenshot();
                saveBitmapToFile(screenshot, file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error taking screenshot and saving on " + file, e);
        }
    }

    public static File saveBitmapToFile(Bitmap bitmap, File file) {
        Log.i(TAG, "Saving bitmap at " + file);
        BitmapUtils.saveBitmap(bitmap, file.getParent(), file.getName());
        return file;
    }

    private static Bitmap takeScreenshot() {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UiAutomation automan = instrumentation.getUiAutomation();
        final Bitmap bitmap = automan.takeScreenshot();
        return bitmap;
    }

    public static File createTestFile(String testName, String name, String targetFolder)
            throws IOException {
        final File dir = getLocalDirectory(targetFolder);
        if (dir == null) return null;
        final String prefix = testName.replaceAll("\\.|\\(|\\/", "_").replaceAll("\\)", "");
        final String filename = prefix + "-" + name;

        return createFile(dir, filename);
    }

    private static File getLocalDirectory(String targetFolder) {
        final File dir = new File(targetFolder);
        dir.mkdirs();
        if (!dir.exists()) {
            Log.e(TAG, "Could not create directory " + dir);
            return null;
        }
        return dir;
    }

    private static File createFile(File dir, String filename) throws IOException {
        final File file = new File(dir, filename);
        if (file.exists()) {
            Log.v(TAG, "Deleting file " + file);
            file.delete();
        }
        if (!file.createNewFile()) {
            Log.e(TAG, "Could not create file " + file);
            return null;
        }
        return file;
    }
}
