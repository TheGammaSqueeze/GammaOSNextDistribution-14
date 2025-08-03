/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.Manifest.permission.READ_DEVICE_CONFIG;

import static com.android.adservices.ui.util.AdServicesUiTestCase.LAUNCH_TIMEOUT;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;

import java.util.concurrent.TimeUnit;

/** Util class for Notification Activity tests. */
public final class NotificationActivityTestUtil {
    private static final String NOTIFICATION_PACKAGE = "android.adservices.ui.NOTIFICATIONS";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    public static final int WINDOW_LAUNCH_TIMEOUT = 2_000;
    public static final int SCROLL_WAIT_TIME = 1_000;

    // Private constructor to ensure noninstantiability
    private NotificationActivityTestUtil() {}

    public static void setupBeforeTests() throws InterruptedException {
        // sleep for 1 min for bootCompleteReceiver to get invoked on S-
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            TimeUnit.SECONDS.sleep(60);
        }
    }

    public static void startActivity(boolean isEUActivity, UiDevice device)
            throws InterruptedException {
        if (sContext.checkCallingOrSelfPermission(READ_DEVICE_CONFIG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("adservices", "this does not have read_device_config permission");
        } else {
            Log.d("adservices", "this has read_device_config permission");
        }

        String notificationPackage = NOTIFICATION_PACKAGE;
        Intent intent = new Intent(notificationPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("isEUDevice", isEUActivity);

        sContext.startActivity(intent);
        device.wait(Until.hasObject(By.pkg(notificationPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    /***
     * Click on the More button on the notification page.
     * @param device device
     */
    public static void clickMoreToBottom(UiDevice device) {
        UiObject2 moreButton =
                ApkTestUtil.getElement(device, R.string.notificationUI_more_button_text);

        if (moreButton == null) {
            LogUtil.e("More Button not Found");
            return;
        }

        int clickCount = 10;
        while (moreButton != null && clickCount-- > 0) {
            moreButton.clickAndWait(Until.scrollFinished(Direction.DOWN), SCROLL_WAIT_TIME);
            // Retrieve a new instance to avoid android.support.test.uiautomator
            // .StaleObjectException.
            moreButton = ApkTestUtil.getElement(device, R.string.notificationUI_more_button_text);
        }
        assertWithMessage("More button").that(moreButton).isNull();
    }
}
