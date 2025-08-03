/**
 * Copyright (C) 2020 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.platform.test.scenario.codelab.answers;

import android.os.SystemClock;
import android.platform.test.rule.NaturalOrientationRule;
import android.platform.test.scenario.annotation.Scenario;
import android.support.test.uiautomator.UiDevice;
import androidx.test.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Opens the Calculator app, waits for 5 seconds and goes back to home screen after. */
@Scenario // Marks the test as a CUJ that CrystalBall recognizes.
@RunWith(JUnit4.class)
public class OpenCalculator {
    // An example for using rules. This one locks the screen orientation during the test.
    @ClassRule public static NaturalOrientationRule orientationRule = new NaturalOrientationRule();

    // Lazily initialized UiDevice instance for running shell commands.
    private static UiDevice mDevice;

    @Test
    public void testOpenCalculator() throws IOException {
        // Open Calculator with a shell command. We usually do something more sophisticated/robust
        // than this, but this example shows that any kind of test code can go here.
        getUiDevice().executeShellCommand("am start com.google.android.calculator");
        SystemClock.sleep(TimeUnit.SECONDS.toMillis(5));
    }

    @AfterClass
    public static void closeApp() throws IOException {
        // Press home to go back to the home screen. We usually do something more robust like
        // backing to the homescreen with the "Back" button, but this is a simplistic example.
        getUiDevice().pressHome();
    }

    private static UiDevice getUiDevice() {
        if (mDevice == null) {
            mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return mDevice;
    }
}
