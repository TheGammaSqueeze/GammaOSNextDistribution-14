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
 * limitations under the License.
 */

package android.platform.tests;

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoFacetBarHelper;
import android.platform.helpers.IAutoHomeHelper;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SystemUiTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private HelperAccessor<IAutoHomeHelper> mHomeHelper;
    private HelperAccessor<IAutoFacetBarHelper> mFacetBarHelper;

    public SystemUiTest() {
        mHomeHelper = new HelperAccessor<>(IAutoHomeHelper.class);
        mFacetBarHelper = new HelperAccessor<>(IAutoFacetBarHelper.class);
    }

    @Before
    public void setup() {
        mHomeHelper.get().open();
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testSystemUi() {
        mHomeHelper.get().openSystemUi();
        assertTrue("Maps widget is not displayed", mHomeHelper.get().hasMapsWidget());
        mHomeHelper.get().openCarUi();
        mFacetBarHelper.get().clickOnFacetIcon(IAutoFacetBarHelper.FACET_BAR.APP_GRID);
        assertTrue(
                "App grid did not open",
                mFacetBarHelper
                        .get()
                        .isAppInForeground(IAutoFacetBarHelper.VERIFY_OPEN_APP.APP_GRID));
    }
}
