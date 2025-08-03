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

package android.server.wm.insets;

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class ForceRelayoutTest extends ForceRelayoutTestBase {

    @Test
    public void testNoRelayoutWhenInsetsChange_adjustPan() throws Throwable {
        testRelayoutWhenInsetsChange(
                false /* expectRelayoutWhenInsetsChange */, SOFT_INPUT_ADJUST_PAN);
    }

    @Test
    public void testNoRelayoutWhenInsetsChange_adjustNothing() throws Throwable {
        testRelayoutWhenInsetsChange(
                false /* expectRelayoutWhenInsetsChange */, SOFT_INPUT_ADJUST_NOTHING);
    }

    @Test
    public void testNoRelayoutWhenInsetsChange_adjustResize() throws Throwable {
        testRelayoutWhenInsetsChange(
                false /* expectRelayoutWhenInsetsChange */, SOFT_INPUT_ADJUST_RESIZE);
    }
}
