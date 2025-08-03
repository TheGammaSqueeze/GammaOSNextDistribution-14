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
 * limitations under the License
 */

package android.cts.backup.adbbackupapp;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Provides device side routines for running {@code adb backup}. To be invoked by the host side
 * {@link BackupEligibilityHostSideTest}. These are not designed to be called in any other
 * way, as they rely on state set up by the host side test.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class AdbBackupApp {
    private static final int CONFIRM_DIALOG_TIMEOUT_MS = 30000;

    @Test
    public void clickAdbBackupConfirmButton() throws Exception {
        UiDevice device = UiDevice.getInstance(getInstrumentation());
        BySelector confirmButtonSelector = By.res("com.android.backupconfirm:id/button_allow");
        UiObject2 confirmButton =
                device.wait(Until.findObject(confirmButtonSelector), CONFIRM_DIALOG_TIMEOUT_MS);

        assertNotNull("confirm button not found", confirmButton);
        assumeTrue(confirmButton.isEnabled());

        confirmButton.click();
    }
}
