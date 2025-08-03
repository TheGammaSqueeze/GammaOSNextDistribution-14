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

package android.content.cts;

import static com.android.server.clipboard.ClipboardService.PROPERTY_AUTO_CLEAR_ENABLED;
import static com.android.server.clipboard.ClipboardService.PROPERTY_AUTO_CLEAR_TIMEOUT;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.server.clipboard.ClipboardService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeNonSdkSandbox(reason = "SDK sandboxes cannot access ClipboardManager.")
@RunWith(AndroidJUnit4.class)
public class ClipboardAutoClearTest {
    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private ClipboardManager mClipboardManager;
    private UiDevice mUiDevice;
    private final int mLatency = 100;
    private final String mTestLatency = Integer.toString(mLatency);
    private static final String LOG_TAG = "ClipboardAutoClearTest";
    private final long mDefaultTimeout = 3600000;


    @Before
    public void setUp() throws Exception {
        assumeTrue("Skipping Test: Wear-Os does not support ClipboardService",
                hasAutoFillFeature());
        mClipboardManager = mContext.getSystemService(ClipboardManager.class);
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().adoptShellPermissionIdentity();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();
        launchActivity(MockActivity.class);
    }

    private void launchActivity(Class<? extends Activity> clazz) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mContext.getPackageName(), clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.pkg(clazz.getPackageName())), 15000);
    }

    @After
    public void cleanUp() {
        if (mClipboardManager != null) {
            mClipboardManager.clearPrimaryClip();
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }


    @Test
    public void testAutoClearEnabledDefault() {
        String enabled = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_CLIPBOARD,
                PROPERTY_AUTO_CLEAR_ENABLED);

        if (enabled != null) {
            DeviceConfig.deleteProperty(DeviceConfig.NAMESPACE_CLIPBOARD,
                    PROPERTY_AUTO_CLEAR_ENABLED);
        }

        ClipboardManager clipboardManager = mClipboardManager;
        clipboardManager.setPrimaryClip(
                ClipData.newPlainText("Test label", "Test string"));
        assertTrue(clipboardManager.hasPrimaryClip());

        try {
            Thread.sleep(mLatency * 10);
        } catch (InterruptedException e) {
            Log.w(LOG_TAG, e);
        }

        assertTrue(clipboardManager.hasPrimaryClip());
        clipboardManager.clearPrimaryClip();

        if (enabled != null) {
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_CLIPBOARD,
                    PROPERTY_AUTO_CLEAR_ENABLED, enabled, false);
        }
    }

    @Test
    public void testAutoClearJob() throws Exception {
        String enabled = getAndSetProperty(
                PROPERTY_AUTO_CLEAR_ENABLED, "true");
        String latency = getAndSetProperty(
                PROPERTY_AUTO_CLEAR_TIMEOUT, mTestLatency);

        ClipboardManager clipboardManager = mClipboardManager;
        clipboardManager.setPrimaryClip(
                ClipData.newPlainText("Test label", "Test string"));

        assertTrue(clipboardManager.hasPrimaryClip());

        retryUntil(() -> !clipboardManager.hasPrimaryClip(), "Auto clear did not run",
                mLatency / 10);

        getAndSetProperty(PROPERTY_AUTO_CLEAR_ENABLED, enabled);
        getAndSetProperty(PROPERTY_AUTO_CLEAR_TIMEOUT, latency);

        clipboardManager.clearPrimaryClip();
    }

    @Test
    public void testDefaultAutoClearDuration() {
        assertEquals(mDefaultTimeout, ClipboardService.DEFAULT_CLIPBOARD_TIMEOUT_MILLIS);
    }

    /**
     * Sets new value for clipboard auto clear property
     *
     * @return old value for the property
     */
    private String getAndSetProperty(String property, String newPropertyValue) {
        String originalPropertyValue = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_CLIPBOARD,
                property);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_CLIPBOARD, property,
                newPropertyValue, false);
        return originalPropertyValue;
    }

    private boolean hasAutoFillFeature() {
        return mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOFILL);
    }
}
