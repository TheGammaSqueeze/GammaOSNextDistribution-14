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

package android.platform.test.scenario.settings;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.test.scenario.annotation.Scenario;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Scroll down and up in Settings. */
@Scenario
@RunWith(JUnit4.class)
public class ScrollInApp {
    static HelperAccessor<IAutoSettingHelper> sHelper =
            new HelperAccessor<>(IAutoSettingHelper.class);

    @Test
    public void testScrollDownAndUp() {
        sHelper.get().scrollDownOnePage();
        sHelper.get().scrollUpOnePage();
    }
}
