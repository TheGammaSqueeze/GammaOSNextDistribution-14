/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.SoundEffectConstants;
import android.view.View;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class SoundEffectConstantsTest {
    @Test
    public void testGetContantForFocusDirection() {
        assertEquals(SoundEffectConstants.NAVIGATION_RIGHT,
                SoundEffectConstants
                        .getContantForFocusDirection(View.FOCUS_RIGHT));
        assertEquals(SoundEffectConstants.NAVIGATION_DOWN, SoundEffectConstants
                .getContantForFocusDirection(View.FOCUS_DOWN));
        assertEquals(SoundEffectConstants.NAVIGATION_LEFT, SoundEffectConstants
                .getContantForFocusDirection(View.FOCUS_LEFT));
        assertEquals(SoundEffectConstants.NAVIGATION_UP, SoundEffectConstants
                .getContantForFocusDirection(View.FOCUS_UP));

        assertEquals(SoundEffectConstants.NAVIGATION_DOWN, SoundEffectConstants
                .getContantForFocusDirection(View.FOCUS_FORWARD));

        assertEquals(SoundEffectConstants.NAVIGATION_UP, SoundEffectConstants
                .getContantForFocusDirection(View.FOCUS_BACKWARD));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetContantForFocusDirectionInvalid() {
        SoundEffectConstants.getContantForFocusDirection(-1);
    }

    @Test
    public void testGetConstantForFocusDirection() {
        assertEquals(SoundEffectConstants.NAVIGATION_REPEAT_RIGHT,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_RIGHT,
                        true /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_REPEAT_DOWN,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_DOWN,
                        true /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_REPEAT_LEFT,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_LEFT,
                        true /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_REPEAT_UP,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_UP,
                        true /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_REPEAT_DOWN,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_FORWARD,
                        true /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_REPEAT_UP,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_BACKWARD,
                        true /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_RIGHT,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_RIGHT,
                        false /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_DOWN,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_DOWN,
                        false /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_LEFT,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_LEFT,
                        false /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_UP,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_UP,
                        false /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_DOWN,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_FORWARD,
                        false /* repeating */));
        assertEquals(SoundEffectConstants.NAVIGATION_UP,
                SoundEffectConstants.getConstantForFocusDirection(View.FOCUS_BACKWARD,
                        false /* repeating */));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetConstantForFocusDirectionInvalid() {
        SoundEffectConstants.getConstantForFocusDirection(-1, true);
    }
}
