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
package android.platform.test.rule;


import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** A rule that performs post-processing of test failures. */
public class FailureWatcher extends TestWatcher {
    private static final String TAG = "FailureWatcher";
    private final UiDevice mDevice;

    public FailureWatcher() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    FailureWatcher.super.apply(base, description).evaluate();
                } catch (Throwable t) {
                    final String systemAnomalyMessage = getSystemAnomalyMessage(mDevice);
                    if (systemAnomalyMessage != null) {
                        throw new AssertionError(systemAnomalyMessage, t);
                    } else {
                        throw t;
                    }
                }
            }
        };
    }

    private static BySelector getAnyObjectSelector() {
        return By.textStartsWith("");
    }

    static String getSystemAnomalyMessage(UiDevice device) {
        if (!device.wait(Until.hasObject(getAnyObjectSelector()), 10000)) {
            return "Screen is empty";
        }

        final StringBuilder sb = new StringBuilder();

        UiObject2 object = device.findObject(By.res("android", "alertTitle").pkg("android"));
        if (object != null) {
            sb.append("TITLE: ").append(object.getText());
        }

        object = device.findObject(By.res("android", "message").pkg("android"));
        if (object != null) {
            sb.append(" PACKAGE: ")
                    .append(object.getApplicationPackage())
                    .append(" MESSAGE: ")
                    .append(object.getText());
        }

        if (sb.length() != 0) {
            return "System alert popup is visible: " + sb;
        }

        return null;
    }
}
