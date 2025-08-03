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
 * limitations under the License.
 */
package android.packageinstaller.tapjacking.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
@AppModeFull
public class TapjackingTest {

    private static final String LOG_TAG = TapjackingTest.class.getSimpleName();
    private static final String SYSTEM_PACKAGE_NAME = "android";
    private static final String PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller";
    private static final String INSTALL_BUTTON_ID = "button1";
    private static final String OVERLAY_ACTIVITY_TEXT_VIEW_ID = "overlay_description";
    private static final String WM_DISMISS_KEYGUARD_COMMAND = "wm dismiss-keyguard";
    private static final String TEST_APP_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts";

    private static final long WAIT_FOR_UI_TIMEOUT = 5000;

    private Context mContext = InstrumentationRegistry.getTargetContext();
    private String mPackageName;
    private UiDevice mUiDevice;

    ActivityScenario<TestActivity> mScenario;

    @Before
    public void setUp() throws Exception {
        mPackageName = mContext.getPackageName();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        mUiDevice.executeShellCommand(WM_DISMISS_KEYGUARD_COMMAND);
    }

    private void launchPackageInstaller() {
        Intent appInstallIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        appInstallIntent.setData(Uri.parse("package:" + TEST_APP_PACKAGE_NAME));

        Intent intent = new Intent(mContext, TestActivity.class);
        intent.putExtra(Intent.EXTRA_INTENT, appInstallIntent);
        intent.putExtra("requestCode", 1);
        mScenario = ActivityScenario.launch(intent);
    }

    private void launchOverlayingActivity() {
        Intent intent = new Intent(mContext, OverlayingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private UiObject2 waitForView(String packageName, String id) {
        final BySelector selector = By.res(packageName, id);
        return mUiDevice.wait(Until.findObject(selector), WAIT_FOR_UI_TIMEOUT);
    }

    private UiObject2 waitForButton(String id) {
        return mUiDevice.wait(Until.findObject(getBySelector(id)), WAIT_FOR_UI_TIMEOUT);
    }

    private BySelector getBySelector(String id) {
        return By.res(Pattern.compile(
            String.format("(?:^%s|^%s):id/%s", PACKAGE_INSTALLER_PACKAGE_NAME, SYSTEM_PACKAGE_NAME,
                id)));
    }

    @Test
    public void testTapsDroppedWhenObscured() throws Exception {
        Log.i(LOG_TAG, "launchPackageInstaller");
        launchPackageInstaller();

        UiObject2 installButton = waitForButton(INSTALL_BUTTON_ID);
        assertNotNull("Install button not shown", installButton);

        Log.i(LOG_TAG, "launchOverlayingActivity");
        launchOverlayingActivity();
        assertNotNull("Overlaying activity not started",
                waitForView(mPackageName, OVERLAY_ACTIVITY_TEXT_VIEW_ID));

        installButton = waitForButton(INSTALL_BUTTON_ID);
        if (installButton != null) {
            Log.i(LOG_TAG, "installButton.click");
            installButton.click();
            assertFalse("Tap on install button succeeded", mUiDevice.wait(
                Until.gone(getBySelector(INSTALL_BUTTON_ID)), WAIT_FOR_UI_TIMEOUT));
        }

        mUiDevice.pressBack();
    }

    @Test
    @Ignore("b/322018861")
    public void testTapsWhenNotObscured() throws Exception {
        Log.i(LOG_TAG, "launchPackageInstaller");
        launchPackageInstaller();

        UiObject2 installButton = waitForButton(INSTALL_BUTTON_ID);
        assertNotNull("Install button not shown", installButton);

        Log.i(LOG_TAG, "launchOverlayingActivity");
        launchOverlayingActivity();
        assertNotNull("Overlaying activity not started",
            waitForView(mPackageName, OVERLAY_ACTIVITY_TEXT_VIEW_ID));

        installButton = waitForButton(INSTALL_BUTTON_ID);
        if (installButton != null) {
            Log.i(LOG_TAG, "installButton.click");
            installButton.click();
            assertFalse("Tap on install button succeeded", mUiDevice.wait(
                    Until.gone(getBySelector(INSTALL_BUTTON_ID)), WAIT_FOR_UI_TIMEOUT));
        }

        mUiDevice.pressBack();

        // Overlay should be gone and we require that the button can be found.
        installButton = waitForButton(INSTALL_BUTTON_ID);
        assertNotNull("Cannot find install button", installButton);

        Log.i(LOG_TAG, "installButton.click after overlay removed");
        installButton.click();
        assertTrue("Tap on install button failed", mUiDevice.wait(
            Until.gone(getBySelector(INSTALL_BUTTON_ID)), WAIT_FOR_UI_TIMEOUT));
    }

    @After
    public void tearDown() throws Exception {
        mUiDevice.pressHome();
    }

    public static final class TestActivity extends Activity {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Intent appInstallIntent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
            int requestCode = getIntent().getIntExtra("requestCode", Integer.MIN_VALUE);
            startActivityForResult(appInstallIntent, requestCode);
        }
    }
}
