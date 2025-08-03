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

package android.media.audio.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.audiofx.HapticGenerator;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class HapticGeneratorTest extends PostProcTestBase {

    private String TAG = "HapticGeneratorTest";

    //-----------------------------------------------------------------
    // HAPTIC GENERATOR TESTS:
    //----------------------------------

    //-----------------------------------------------------------------
    // 0 - constructor
    //----------------------------------

    //Test case 0.0: test constructor and release
    @Test
    public void test0_0ConstructorAndRelease() throws Exception {
        if (!HapticGenerator.isAvailable()) {
            // HapticGenerator will only be created on devices supporting haptic playback
            return;
        }
        HapticGenerator effect = createHapticGenerator();
        // If the effect is null, it must fail creation.
        effect.release();
    }

    // Test case 0.1: test constructor and close
    @Test
    public void test0_1ConstructorAndClose() throws Exception {
        if (!HapticGenerator.isAvailable()) {
            // HapticGenerator will only be created on devices supporting haptic playback
            return;
        }
        HapticGenerator effect = createHapticGenerator();
        // If the effect is null, it must fail creation.
        effect.close();
    }

    //-----------------------------------------------------------------
    // 1 - Effect enable/disable
    //----------------------------------

    //Test case 1.0: test setEnabled() and getEnabled() in valid state
    @Test
    public void test1_0SetEnabledGetEnabled() throws Exception {
        if (!HapticGenerator.isAvailable()) {
            // HapticGenerator will only be created on devices supporting haptic playback
            return;
        }
        HapticGenerator effect = createHapticGenerator();
        try {
            effect.setEnabled(true);
            assertTrue("invalid state from getEnabled", effect.getEnabled());
            effect.setEnabled(false);
            assertFalse("invalid state from getEnabled", effect.getEnabled());
            // test passed
        } catch (IllegalStateException e) {
            fail("setEnabled() in wrong state");
        } finally {
            effect.release();
        }
    }

    private HapticGenerator createHapticGenerator() {
        try {
            HapticGenerator effect = HapticGenerator.create(getSessionId());
            try {
                assertTrue("invalid effect ID", (effect.getId() != 0));
            } catch (IllegalStateException e) {
                fail("HapticGenerator not initialized");
            }
            return effect;
        } catch (IllegalArgumentException e) {
            fail("HapticGenerator not found");
        } catch (UnsupportedOperationException e) {
            fail("Effect library not loaded");
        } catch (RuntimeException e) {
            fail("Unexpected run time error: " + e);
        }
        return null;
    }
}
