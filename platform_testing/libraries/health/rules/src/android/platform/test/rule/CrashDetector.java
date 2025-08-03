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
package android.platform.test.rule;

import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;

/** A rule that fails if the specified package crashed during the test. */
public class CrashDetector implements TestRule {
    private static final String TAG = CrashDetector.class.getSimpleName();
    private String mExpectedPid;
    private final UiDevice mDevice;
    private final String mPackageName;

    public CrashDetector(String packageName) {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mPackageName = packageName;
    }

    private String getPackagePid() throws IOException {
        return mDevice.executeShellCommand("pidof " + mPackageName).replaceAll("\\s", "");
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mExpectedPid = getPackagePid();
                Log.d(TAG, "Enter, PID=" + mExpectedPid);
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    detectCrash(t);
                    throw t;
                }
                detectCrash(null);
            }

            private void detectCrash(Throwable cause) throws IOException {
                final String newPid = getPackagePid();
                if (!mExpectedPid.equals(newPid)) {
                    throw new AssertionError(
                            mPackageName
                                    + " crashed, old pid= "
                                    + mExpectedPid
                                    + " , new pid="
                                    + newPid,
                            cause);
                }
            }
        };
    }

    public void onLegitimatePackageRestart() {
        try {
            mExpectedPid = getPackagePid();
            Log.d(TAG, "onLegitimatePackageRestart, PID=" + mExpectedPid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
