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

import android.hardware.cts.R;
import android.server.wm.WindowManagerStateHelper;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RazerRaijuMobileBluetoothTest extends InputHidTestCase {

    // Simulates the behavior of Razer Raiju Mobile gamepad.
    public RazerRaijuMobileBluetoothTest() {
        super(R.raw.razer_raiju_mobile_bluetooth_register);
        addDelayAfterSetup();
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.razer_raiju_mobile_bluetooth_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        testInputEvents(R.raw.razer_raiju_mobile_bluetooth_motioneventtests);
    }

    /**
     * Add BUTTON_MODE to the activity's unhandled keys list to allow the fallback
     * of HOME. Do home button behavior check with wm utils.
     */
    @Test
    public void testHomeKey() throws Exception {
        mTestActivity.addUnhandleKeyCode(KeyEvent.KEYCODE_BUTTON_MODE);
        testInputEvents(R.raw.razer_raiju_mobile_bluetooth_homekey);

        WindowManagerStateHelper wmStateHelper = new WindowManagerStateHelper();

        wmStateHelper.waitForHomeActivityVisible();
        wmStateHelper.assertHomeActivityVisible(true);
    }
}
