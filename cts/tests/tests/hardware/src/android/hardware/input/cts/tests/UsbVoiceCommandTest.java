/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assume.assumeFalse;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.cts.R;
import android.hardware.input.cts.InputAssistantActivity;
import android.server.wm.WindowManagerStateHelper;
import android.speech.RecognizerIntent;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UsbVoiceCommandTest extends InputHidTestCase {
    private static final String TAG = "UsbVoiceCommandTest";

    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final PackageManager mPackageManager =
            InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Intent mVoiceIntent;
    private final Intent mWebIntent;
    private final List<String> mExcludedPackages = new ArrayList<String>();

    // Simulates the behavior of Google Gamepad with Voice Command buttons.
    public UsbVoiceCommandTest() {
        super(R.raw.google_gamepad_usb_register);
        mVoiceIntent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        mVoiceIntent.putExtra(RecognizerIntent.EXTRA_SECURE, true);
        mWebIntent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
    }

    private void setPackageState(boolean enabled) throws Exception {
        runWithShellPermissionIdentity(mUiAutomation, () -> {
            for (int i = 0; i < mExcludedPackages.size(); i++) {
                if (enabled) {
                    mPackageManager.setApplicationEnabledSetting(
                            mExcludedPackages.get(i),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                } else {
                    mPackageManager.setApplicationEnabledSetting(
                            mExcludedPackages.get(i),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                }
            }
        });
    }

    private void addExcludedPackages(Intent intent) {
        final List<ResolveInfo> list = mPackageManager.queryIntentActivities(intent,
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);

        for (int i = 0; i < list.size(); i++) {
            ResolveInfo info = list.get(i);
            if (!info.activityInfo.packageName.equals(mTestActivity.getPackageName())) {
                mExcludedPackages.add(info.activityInfo.packageName);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Exclude packages for voice intent
        addExcludedPackages(mVoiceIntent);
        // Exclude packages for web intent
        addExcludedPackages(mWebIntent);
        // Set packages state to be disabled.
        setPackageState(false);
    }

    @After
    public void tearDown() throws Exception {
        // Enable the packages.
        setPackageState(true);
        mExcludedPackages.clear();
        super.tearDown();
    }

    @Test
    public void testMediaKeys() {
        testInputEvents(R.raw.google_gamepad_keyevent_media_tests);
    }

    @Test
    public void testVolumeKeys() {
        // {@link PhoneWindowManager} in interceptKeyBeforeDispatching, on TVs platform,
        // volume keys never go to the foreground app.
        // Skip the key test for TV platform.
        assumeFalse("TV platform and devices handling key events outside window manager "
                + "don't send volume keys to app, test should be skipped", shouldSkipVolumeTest());
        testInputEvents(R.raw.google_gamepad_keyevent_volume_tests);
    }

    /**
     * Assistant keyevent is not sent to apps, verify InputAssistantActivity launched and visible.
     */
    @Test
    public void testVoiceAssistantKey() throws Exception {
        /* Inject assistant key from hid device */
        testInputEvents(R.raw.google_gamepad_assistkey);

        WindowManagerStateHelper wmStateHelper = new WindowManagerStateHelper();

        /* InputAssistantActivity should be visible */
        final ComponentName inputAssistant =
                new ComponentName(mTestActivity.getPackageName(),
                        InputAssistantActivity.class.getName());
        wmStateHelper.waitForValidState(inputAssistant);
        wmStateHelper.assertActivityDisplayed(inputAssistant);
    }

    private boolean shouldSkipVolumeTest() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || mContext.getResources().getBoolean(Resources.getSystem().getIdentifier(
                                "config_handleVolumeKeysInWindowManager",
                                "bool", "android"));
    }
}
