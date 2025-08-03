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

package com.android.adservices.ui.util;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
import com.android.adservices.shared.testing.common.FileHelper;
import com.android.compatibility.common.util.ShellUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Util class for APK tests. */
public class ApkTestUtil {

    private static final String PRIVACY_SANDBOX_UI = "android.adservices.ui.SETTINGS";
    private static final String ANDROID_WIDGET_SCROLLVIEW = "android.widget.ScrollView";

    private static final String TAG = "ApkTestUtil";
    private static final int WINDOW_LAUNCH_TIMEOUT = 2_000;
    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS = 1_000;

    /**
     * Check whether the device is supported. Adservices doesn't support non-phone device.
     *
     * @return if the device is supported.
     */
    public static boolean isDeviceSupported() {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        PackageManager pm = inst.getContext().getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static UiObject2 getConsentSwitch(UiDevice device) {
        UiObject2 consentSwitch =
                device.wait(
                        Until.findObject(By.clazz("android.widget.Switch")),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        // Swipe the screen by the width of the toggle so it's not blocked by the nav bar on AOSP
        // devices.
        if (device.getDisplayHeight() - consentSwitch.getVisibleBounds().centerY() < 100) {
            device.swipe(
                    consentSwitch.getVisibleBounds().centerX(),
                    500,
                    consentSwitch.getVisibleBounds().centerX(),
                    0,
                    100);
        }

        return consentSwitch;
    }

    /** Returns the string corresponding to a resource ID. */
    public static String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    public static void scrollToAndClick(UiDevice device, int resId) {
        UiObject2 obj = scrollTo(device, resId);
        clickTopLeft(obj);
    }

    public static void click(UiDevice device, int resId) {
        UiObject2 obj = device.findObject(By.text(getString(resId)));
        // objects may be partially hidden by the status bar and nav bars.
        clickTopLeft(obj);
    }

    public static void clickTopLeft(UiObject2 obj) {
        assertThat(obj).isNotNull();
        obj.clickAndWait(
                new Point(obj.getVisibleBounds().top, obj.getVisibleBounds().left),
                Until.newWindow(),
                WINDOW_LAUNCH_TIMEOUT);
    }

    public static void gentleSwipe(UiDevice device) {
        device.waitForWindowUpdate(null, WINDOW_LAUNCH_TIMEOUT);
        UiObject2 scrollView = device.wait(
                Until.findObject(By.scrollable(true).clazz(ANDROID_WIDGET_SCROLLVIEW)),
                PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        scrollView.scroll(Direction.DOWN, /* percent */ 0.25F);
    }

    public static UiObject2 scrollTo(UiDevice device, int resId) {
        device.waitForWindowUpdate(null, WINDOW_LAUNCH_TIMEOUT);
        UiObject2 scrollView = device.wait(
                Until.findObject(By.scrollable(true).clazz(ANDROID_WIDGET_SCROLLVIEW)),
                PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        String targetStr = getString(resId);
        if (scrollView != null) {
            scrollView.scrollUntil(
                    Direction.DOWN,
                    Until.findObject(
                            By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE))));
            scrollView.scrollUntil(
                    Direction.UP,
                    Until.findObject(
                            By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE))));
        }
        return getElement(device, resId);
    }

    public static UiObject2 scrollTo(UiDevice device, String regexStr) {
        device.waitForWindowUpdate(null, WINDOW_LAUNCH_TIMEOUT);
        UiObject2 scrollView =
                device.wait(
                        Until.findObject(By.scrollable(true).clazz(ANDROID_WIDGET_SCROLLVIEW)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (scrollView != null) {
            scrollView.scrollUntil(
                    Direction.DOWN,
                    Until.findObject(By.res(Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE))));
            scrollView.scrollUntil(
                    Direction.UP,
                    Until.findObject(By.res(Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE))));
        }
        return getElement(device, regexStr);
    }

    /** Returns the UiObject corresponding to a resource ID. */
    public static UiObject2 getElement(UiDevice device, int resId) {
        String targetStr = getString(resId);
        Log.d(
                TAG,
                "Waiting for object using target string "
                        + targetStr
                        + " until a timeout of "
                        + PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS
                        + " ms");
        UiObject2 obj =
                device.wait(
                        Until.findObject(By.text(targetStr)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (obj == null) {
            obj = device.findObject(By.text(targetStr.toUpperCase(Locale.getDefault())));
        }
        return obj;
    }

    /** Returns the string corresponding to a resource ID and index. */
    public static UiObject2 getElement(UiDevice device, int resId, int index) {
        String targetStr = getString(resId);
        List<UiObject2> objs =
                device.wait(
                        Until.findObjects(By.text(targetStr)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (objs == null) {
            return device.wait(
                    Until.findObjects(By.text(targetStr.toUpperCase(Locale.getDefault()))),
                    PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS).get(index);
        }
        return objs.get(index);
    }

    /** Returns the string corresponding to a resource regex string and index. */
    public static UiObject2 getElement(UiDevice device, String regexStr) {
        Log.d(
                TAG,
                "Waiting for object using res id regex "
                        + regexStr
                        + " until a timeout of "
                        + PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS
                        + " ms");
        return device.wait(
                Until.findObject(By.res(Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE))),
                PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
    }

    /** Returns the UiObject corresponding to a resource ID. */
    public static UiObject getPageElement(UiDevice device, int resId) {
        return device.findObject(new UiSelector().text(getString(resId)));
    }

    /** Launch Privacy Sandbox Setting View. */
    public static void launchSettingView(UiDevice device, int launchTimeout) {
        // Launch the setting view.
        Intent intent = new Intent(PRIVACY_SANDBOX_UI);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ApplicationProvider.getApplicationContext().startActivity(intent);

        // Wait for the view to appear
        device.wait(Until.hasObject(By.pkg(PRIVACY_SANDBOX_UI).depth(0)), launchTimeout);
    }

    /** Launch Privacy Sandbox Setting View with UX extra. */
    public static void launchSettingViewGivenUx(UiDevice device, int launchTimeout, String ux) {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_activity_debug_mode true");

        // Launch the setting view.
        Intent intent = new Intent(PRIVACY_SANDBOX_UI);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("ux", ux);

        ApplicationProvider.getApplicationContext().startActivity(intent);

        // Wait for the view to appear
        device.wait(Until.hasObject(By.pkg(PRIVACY_SANDBOX_UI).depth(0)), launchTimeout);
    }

    /** Takes the screenshot at the end of each test for debugging. */
    public static void takeScreenshot(UiDevice device, String methodName) {
        try {
            String timeStamp =
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(Date.from(Instant.now()));

            File screenshotFile =
                    new File(
                            FileHelper.getAdServicesTestsOutputDir(),
                            methodName + timeStamp + ".png");
            device.takeScreenshot(screenshotFile);
        } catch (RuntimeException e) {
            LogUtil.e("Failed to take screenshot: " + e.getMessage());
        }
    }
}
