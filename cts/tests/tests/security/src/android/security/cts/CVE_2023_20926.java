/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts.CVE_2023_20926;

import static com.android.sts.common.SystemUtil.withSetting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.app.Instrumentation;
import android.app.StatusBarManager;
import android.content.Context;
import android.media.MediaRecorder;
import android.platform.test.annotations.AsbSecurityTest;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_20926 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 253043058)
    @Test
    public void testPocCVE_2023_20926() {
        Instrumentation instrumentation = null;
        MediaRecorder mediaRecorder = null;
        try {
            instrumentation = InstrumentationRegistry.getInstrumentation();
            UiDevice uiDevice = UiDevice.getInstance(instrumentation);
            Context context = instrumentation.getTargetContext();

            // Disable global settings device_provisioned. This is required as fix adds a check
            // based on the state of this setting
            try (AutoCloseable withSettingAutoCloseable =
                    withSetting(instrumentation, "global", "device_provisioned", "0")) {
                // Start recording audio to show the vulnerable icon
                mediaRecorder = new MediaRecorder(context);
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                mediaRecorder.setOutputFile(
                        new File(getContext().getFilesDir(), "CVE-2023-20926.output").getPath());
                mediaRecorder.prepare();
                mediaRecorder.start();

                // Expand statusbar and detect the vulnerability by clicking the vulnerable icon
                context.getSystemService(StatusBarManager.class).expandNotificationsPanel();
                uiDevice.findObject(By.res("com.android.systemui", "icons_container")).click();
                assertFalse(
                        uiDevice.wait(
                                Until.hasObject(
                                        By.text(
                                                Pattern.compile(
                                                        String.format(
                                                                ".*%s.*", context.getPackageName()),
                                                        Pattern.CASE_INSENSITIVE))),
                                5000));
            }
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.release();
                }
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_HOME");
            } catch (Exception e) {
                // Ignoring exceptions as the test has completed.
            }
        }
    }
}
