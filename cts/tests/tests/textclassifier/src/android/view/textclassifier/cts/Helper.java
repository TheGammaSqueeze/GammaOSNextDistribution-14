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

package android.view.textclassifier.cts;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.BitmapUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Helper for common funcionalities.
 */
public final class Helper {

    private static final String TAG = "Helper";

    public static final String LOCAL_TEST_FILES_DIR = "/sdcard/CtsTextClassifierTestCases";


    /**
     * Takes a screenshot and save it in the file system for analysis.
     */
    public static void takeScreenshotAndSave(Context context, String testName,
            String targetFolder) {
        File file = null;
        try {
            file = createTestFile(testName, "sreenshot.png", targetFolder);
            if (file != null) {
                Log.i(TAG, "Taking screenshot on " + file);
                final Bitmap screenshot = takeScreenshot();
                saveBitmapToFile(screenshot, file);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error taking screenshot and saving on " + file, e);
        }
    }

    /**
     * Save dumpsys result in the file system for analysis.
     */
    public static void dumpsysAndSave(String dumpsysString, String testName,
            String targetFolder) {
        File file = null;
        try {
            file = createTestFile(testName, "dumpsys.txt", targetFolder);
            if (file != null) {
                Log.i(TAG, "dumpSys on" + file);
                FileWriter wr = new FileWriter(file);
                wr.write(dumpsysString);
                wr.flush();
                wr.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error taking screenshot and saving on " + file, e);
        }
    }

    private static File saveBitmapToFile(Bitmap bitmap, File file) {
        Log.i(TAG, "Saving bitmap at " + file);
        BitmapUtils.saveBitmap(bitmap, file.getParent(), file.getName());
        return file;
    }

    private static Bitmap takeScreenshot() {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UiAutomation automan = instrumentation.getUiAutomation();
        return automan.takeScreenshot();
    }

    private static File createTestFile(String testName, String name, String targetFolder)
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
