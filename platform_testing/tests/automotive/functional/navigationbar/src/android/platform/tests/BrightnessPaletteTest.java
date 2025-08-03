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

package android.platform.tests;

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoHomeHelper;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BrightnessPaletteTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private HelperAccessor<IAutoHomeHelper> mHomeHelper;

    public BrightnessPaletteTest() {
        mHomeHelper = new HelperAccessor<>(IAutoHomeHelper.class);
    }

    @Before
    public void openBrightnessPalette() {
        mHomeHelper.get().openBrightnessPalette();
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testIfBrightnessPaletteExist() {
        assertTrue(
                "Brightness palette did not open", mHomeHelper.get().hasDisplayBrightessPalette());
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testIfAdaptiveBrightnessSettingExist() {
        assertTrue("Adaptive brightness did not open", mHomeHelper.get().hasAdaptiveBrightness());
    }

    @After
    public void goToHomeScreen() {
        mHomeHelper.get().open();
    }
}
