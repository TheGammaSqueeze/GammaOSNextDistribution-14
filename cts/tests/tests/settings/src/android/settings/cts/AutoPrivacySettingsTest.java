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

package android.settings.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutoPrivacySettingsTest {
    private static final int TIMEOUT_MS = 2000;
    private static final String ACTION_SETTINGS = "android.settings.SETTINGS";
    private static final String PRIVACY = "Privacy";
    private static final String MICROPHONE = "Microphone";
    private static final String USE_MICROPHONE = "Use microphone";
    private static final String[] EXPECTED_MICROPHONE_ENABLED_SETTINGS = {
            USE_MICROPHONE, "Recently accessed", "Manage microphone permissions"};

    // For the camera privacy setting test
    private static final String CAMERA = "Camera";

    private static final String USE_CAMERA = "Use camera";
    private static final String[] EXPECTED_CAMERA_ENABLED_SETTINGS = {
            USE_CAMERA, "Recently accessed", "Manage camera permissions"};

    // To support dual panes in AAOS S
    private static final int MAX_NUM_SCROLLABLES = 2;

    private final Context mContext = InstrumentationRegistry.getContext();
    private final UiDevice mDevice = UiDevice.getInstance(getInstrumentation());

    @Before
    public void setUp() {
        assumeFalse("Skipping test: Requirements only apply to Auto",
                !mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
    }

    /**
     * MUST provide a user affordance to do microphone toggle in the following location:Settings
     * > Privacy.
     */
    @CddTest(requirement = "9.8.2/A-1-3")
    @Test
    public void testPrivacyMicrophoneSettings() throws Exception {
        goHome();

        launchActionSettings();
        mDevice.waitForIdle();

        UiObject2 privacyObj = assertScrollToAndFind(PRIVACY);
        privacyObj.click();
        mDevice.waitForIdle();

        UiObject2 micObj = assertScrollToAndFind(MICROPHONE);
        micObj.click();
        mDevice.waitForIdle();

        // verify state when mic is enabled
        disableCameraMicPrivacy();
        for (String setting : EXPECTED_MICROPHONE_ENABLED_SETTINGS) {
            assertScrollToAndFind(setting);
        }

        goHome();
    }

    /**
     * MUST provide a user affordance to do camera toggle in the following location:Settings >
     * Privacy.
     */
    @CddTest(requirement = "9.8.2/A-2-3")
    @Test
    public void testPrivacyCameraSettings() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            // Skip this test if the system does not have a camera.
            return;
        }

        goHome();

        launchActionSettings();
        mDevice.waitForIdle();

        UiObject2 privacyObj = assertScrollToAndFind(PRIVACY);
        privacyObj.click();
        mDevice.waitForIdle();

        UiObject2 camObj = assertScrollToAndFind(CAMERA);
        camObj.click();
        mDevice.waitForIdle();

        // verify state when camera is enabled
        disableCameraMicPrivacy();
        for (String setting : EXPECTED_CAMERA_ENABLED_SETTINGS) {
            assertScrollToAndFind(setting);
        }

        goHome();
    }

    /**
     * Find the specified text.
     */
    private UiObject2 assertFind(String text) {
        UiObject2 obj = mDevice.findObject(By.text(text));
        assertNotNull("Failed to find '" + text + "'.", obj);
        return obj;
    }

    /**
     * Scroll to find the specified text.
     */
    private UiObject2 assertScrollToAndFind(String text) {
        scrollToText(text);
        return assertFind(text);
    }

    /**
     * Scroll to text, which might be at the bottom of a scrollable list.
     */
    @Nullable
    private UiObject2 scrollToText(String text) {
        UiObject2 foundObject = null;
        // Iterate through multiple scrollables.
        for (int i = 0; i < MAX_NUM_SCROLLABLES; i++) {
            UiScrollable scrollable = new UiScrollable(
                    new UiSelector().scrollable(true).instance(i));
            scrollable.setMaxSearchSwipes(10);
            try {
                scrollable.scrollTextIntoView(text);
            } catch (UiObjectNotFoundException e) {
                // Ignore the exception if there's no scroll bar.
            }
            foundObject = mDevice.findObject(By.text(text));
            if (foundObject != null) {
                // No need to look at other scrollables.
                break;
            }
        }

        mDevice.waitForIdle();
        return foundObject;
    }


    /**
     * Launch the action settings screen.
     */
    private void launchActionSettings() {
        final Intent intent = new Intent(ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mContext.startActivity(intent);
    }

    private void goHome() {
        final Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(home);
        mDevice.waitForIdle();
    }

    private void disableCameraMicPrivacy() {
        BySelector switchSelector = By.clazz(Switch.class);
        UiObject2 switchButton = mDevice.findObject(switchSelector);
        if (switchButton != null && !switchButton.isChecked()) {
            switchButton.click();
            mDevice.waitForIdle();
            switchButton.wait(Until.checked(true), TIMEOUT_MS);
        }
    }
}
