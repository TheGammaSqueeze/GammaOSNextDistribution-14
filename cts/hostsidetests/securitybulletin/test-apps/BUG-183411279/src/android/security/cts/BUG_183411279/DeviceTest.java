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

package android.security.cts.BUG_183411279;

import static android.security.cts.BUG_183411279.Constants.CAR_SETTINGS_APP_PACKAGE;
import static android.security.cts.BUG_183411279.Constants.TAG;
import static android.security.cts.BUG_183411279.Constants.TAPJACKED_ACTIVITY_INTENT_ACTION;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Basic sample for unbundled UiAutomator. */
@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    private static final long WAIT_FOR_UI_TIMEOUT = 10_000;

    private Context mContext;
    private UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        Log.d(TAG, "startMainActivityFromHomeScreen()");

        mContext = getApplicationContext();

        // If the permission is not granted, the app will not be able to show an overlay dialog.
        // This is required for the test below.
        // NOTE: The permission is granted by the HostJUnit4Test implementation and should not fail.
        assertEquals("Permission SYSTEM_ALERT_WINDOW not granted!",
                mContext.checkSelfPermission("android.permission.SYSTEM_ALERT_WINDOW"),
                PackageManager.PERMISSION_GRANTED);

        // Initialize UiDevice instance
        mDevice = UiDevice.getInstance(getInstrumentation());
        if (!mDevice.isScreenOn()) {
            mDevice.wakeUp();
        }
        mDevice.pressHome();
    }

    @Test
    public void testTapjacking() throws InterruptedException {
        Log.d(TAG, "Starting tap-jacking test");

        launchTestApp();

        launchTapjackedActivity();

        mContext.sendBroadcast(new Intent(Constants.ACTION_START_TAPJACKING));
        Log.d(TAG, "Sent intent to start tap-jacking!");

        UiObject2 overlay = waitForView(By.text(mContext.getString(R.string.tapjacking_text)));
        assertNull("Tap-jacking successful. Overlay was displayed.!", overlay);
    }

    @After
    public void tearDown() {
        mDevice.pressHome();
    }

    private void launchTestApp() {
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(
                Constants.TEST_APP_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mContext.startActivity(intent);

        // Wait for the app to appear
        UiObject2 view = waitForView(By.pkg(Constants.TEST_APP_PACKAGE).depth(0));
        assertNotNull("test-app did not appear!", view);
        Log.d(TAG, "test-app appeared");
    }

    private void launchTapjackedActivity() {
        Intent intent = new Intent();
        intent.setAction(TAPJACKED_ACTIVITY_INTENT_ACTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        UiObject2 activityInstance = waitForView(By.pkg(CAR_SETTINGS_APP_PACKAGE).depth(0));
        assertNotNull("Activity under-test was not launched or found!", activityInstance);
        Log.d(TAG, "Started Activity under-test.");
    }

    private UiObject2 waitForView(BySelector selector) {
        return mDevice.wait(Until.findObject(selector), WAIT_FOR_UI_TIMEOUT);
    }
}
