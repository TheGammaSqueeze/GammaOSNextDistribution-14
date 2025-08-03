/*
 * Copyright (C) 2020 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;


import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GoogleAtvReferenceRemoteControlTest extends InputHidTestCase {

    // Exercises the Bluetooth behavior of the ATV reference remote control
    public GoogleAtvReferenceRemoteControlTest() {
        super(R.raw.google_atvreferenceremote_register);
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.google_atvreferenceremote_keyeventtests);
    }

    /**
     * We cannot test the home key using "testAllKeys" because the home key does not get forwarded
     * to apps, and therefore cannot be received in InputCtsActivity.
     * Instead, we rely on the home button behavior check using the wm utils.
     */
    @Test
    public void testHomeKey() {
        testInputEvents(R.raw.google_atvreferenceremote_homekey);
        WindowManagerStateHelper wmStateHelper = new WindowManagerStateHelper();
        wmStateHelper.waitForHomeActivityVisible();
        wmStateHelper.assertHomeActivityVisible(true);
    }
}
