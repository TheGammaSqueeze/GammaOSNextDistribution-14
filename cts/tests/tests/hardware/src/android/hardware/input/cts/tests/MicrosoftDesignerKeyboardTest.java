/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.hardware.cts.R;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MicrosoftDesignerKeyboardTest extends InputHidTestCase {

    public MicrosoftDesignerKeyboardTest() {
        super(R.raw.microsoft_designer_keyboard_register);
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.microsoft_designer_keyboard_keyeventtests);
    }

    /**
     * Microsoft Designer Keyboard has meta control keys of NUM_LOCK, CAPS_LOCK and SCROLL_LOCK.
     * Do not verify the meta key states that have global state and initially to be on.
     */
    @Override
    void assertMetaState(String testCase, int expectedMetaState, int actualMetaState) {
        final int metaStates = KeyEvent.META_NUM_LOCK_ON | KeyEvent.META_CAPS_LOCK_ON
                | KeyEvent.META_SCROLL_LOCK_ON;
        actualMetaState &= ~metaStates;
        assertEquals(testCase + " (meta state)", expectedMetaState , actualMetaState);
    }
}
