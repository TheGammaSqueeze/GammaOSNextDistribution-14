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

package android.app.usage.cts;

import android.os.Environment;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.OnFailureRule;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DumpOnFailureRule extends OnFailureRule {
    private static final String TAG = "DumpOnFailureRule";

    private File mDumpDir = new File(Environment.getExternalStorageDirectory(),
            "CtsUsageStatsTestCases");

    @Override
    public void onTestFailure(Statement base, Description description, Throwable throwable) {
        if (throwable instanceof AssumptionViolatedException) {
            final String testName = description.getClassName() + "_" + description.getMethodName();
            Log.d(TAG, "Skipping test " + testName + ": " + throwable);
            return;
        }

        prepareDumpRootDir();
        final File dumpFile = new File(mDumpDir, "dump-" + getShortenedTestName(description));
        Log.i(TAG, "Dumping debug info for " + description + ": " + dumpFile.getPath());
        try (FileOutputStream out = new FileOutputStream(dumpFile)) {
            dumpCommandOutput(out, "dumpsys usagestats");
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error opening file: " + dumpFile, e);
        } catch (IOException e) {
            Log.e(TAG, "Error closing file: " + dumpFile, e);
        }
    }

    private String getShortenedTestName(Description description) {
        final String qualifiedClassName = description.getClassName();
        final String className = qualifiedClassName.substring(
                qualifiedClassName.lastIndexOf(".") + 1);
        final String shortenedClassName = className.chars()
                .filter(Character::isUpperCase)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return shortenedClassName + "_" + description.getMethodName();
    }

    void dumpCommandOutput(FileOutputStream out, String cmd) {
        final ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(cmd);
        try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            out.write(("Output of '" + cmd + "':\n").getBytes(StandardCharsets.UTF_8));
            FileUtils.copy(in, out);
            out.write("\n\n=================================================================\n\n"
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "Error dumping '" + cmd + "'", e);
        }
    }

    void prepareDumpRootDir() {
        if (!mDumpDir.exists() && !mDumpDir.mkdir()) {
            Log.e(TAG, "Error creating " + mDumpDir);
        }
    }
}
