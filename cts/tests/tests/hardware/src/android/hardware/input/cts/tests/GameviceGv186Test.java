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

import static org.junit.Assert.assertEquals;

import android.hardware.cts.R;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class GameviceGv186Test extends InputHidTestCase {
    private static final float TOLERANCE = 0.005f;

    public GameviceGv186Test() {
        super(R.raw.gamevice_gv186_register);
    }

    @Override
    void assertAxis(String testCase, MotionEvent expectedEvent, MotionEvent actualEvent) {
        for (int axis = MotionEvent.AXIS_X; axis <= MotionEvent.AXIS_GENERIC_16; axis++) {
            // Skip checking AXIS_GENERIC_1 and AXIS_GENERIC_2, this device has HID usage of DPAD
            // which maps to HAT1X and HAT1Y, which have non zero axes values always.
            if (axis == MotionEvent.AXIS_GENERIC_1 || axis == MotionEvent.AXIS_GENERIC_2) {
                continue;
            }
            assertEquals(testCase + " (" + MotionEvent.axisToString(axis) + ")",
                    expectedEvent.getAxisValue(axis), actualEvent.getAxisValue(axis), TOLERANCE);
        }
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.gamevice_gv186_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        testInputEvents(R.raw.gamevice_gv186_motioneventtests);
    }
}
