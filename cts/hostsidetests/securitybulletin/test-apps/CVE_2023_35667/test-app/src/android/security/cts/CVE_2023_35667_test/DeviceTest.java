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

package android.security.cts.CVE_2023_35667;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    @Test
    public void testPocCVE_2023_35667() {
        try {
            Instrumentation instrumentation = getInstrumentation();
            Context context = instrumentation.getContext();

            // Check if the 'HelperListenerService1' was enabled successfully
            checkIfListenerServiceIsEnabled(context, "HelperListenerService1");

            // Check if the 'HelperListenerService2' was enabled successfully
            checkIfListenerServiceIsEnabled(context, "HelperListenerService2");

            // Check if the vulnerable listener service was enabled successfully
            final String vulnerableListenerService =
                    String.format("%0" + (215 /* serviceClassNameLength */) + "d", 0)
                            .replace("0", "A");
            checkIfListenerServiceIsEnabled(context, vulnerableListenerService);

            // Fetching settingsPackageName & activityName dynamically
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            ComponentName notificationSettingsComponent =
                    intent.resolveActivity(context.getPackageManager());
            assume().withMessage("NotificationSettingComponent not found")
                    .that(notificationSettingsComponent)
                    .isNotNull();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // Launching the 'Device and Notification' activity
            context.startActivity(intent);

            // Checking if the listener services are visible in 'Device and Notification'.
            UiDevice uiDevice = UiDevice.getInstance(instrumentation);
            String packageName = context.getPackageName();
            assume().withMessage("Notification_Listener_Setting is not visible")
                    .that(waitForVisibleObject(uiDevice, packageName + ".aaaaaa", 5000L))
                    .isNotNull();
            assume().withMessage("Notification_Listener_Setting is not visible")
                    .that(waitForVisibleObject(uiDevice, packageName + ".aaaaab", 5000L))
                    .isNotNull();

            // Checking for application under 'Device and Notification'.
            // Failing test, when the application is not visible in 'Device and
            // Notification'.
            assertWithMessage("Vulnerable to b/282932362!!")
                    .that(waitForVisibleObject(uiDevice, packageName, 5000L))
                    .isNotNull();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    // To check if the ListenerServices are visible.
    private UiObject2 waitForVisibleObject(UiDevice uiDevice, String text, long timeout) {
        UiObject2 uiObject = uiDevice.wait(Until.findObject(By.text(text)), timeout);
        return uiObject;
    }

    private void checkIfListenerServiceIsEnabled(Context context, String listenerServiceName) {
        // Fetch the component names for which listener service was enabled successfully
        String enabledListeners =
                Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);

        assume().withMessage(
                        String.format(
                                "Notification Listener Service not enabled for %s",
                                listenerServiceName))
                .that(enabledListeners.contains(listenerServiceName))
                .isTrue();
    }
}
