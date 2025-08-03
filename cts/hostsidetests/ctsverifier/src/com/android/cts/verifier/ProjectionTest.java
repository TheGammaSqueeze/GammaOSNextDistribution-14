/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.verifier;

import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.SupportMultiDisplayMode;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class ProjectionTest extends CtsVerifierTest {

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ProjectionCubeTest() throws Exception {
        requireFeatures("android.hardware.faketouch");

        runTest(".projection.cube.ProjectionCubeActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ProjectionWidgetTest() throws Exception {
        requireFeatures("android.hardware.faketouch");

        runTest(".projection.widgets.ProjectionWidgetActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ProjectionListTest() throws Exception {
        excludeFeatures("android.hardware.type.television", "android.software.leanback");

        runTest(".projection.list.ProjectionListActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ProjectionVideoTest() throws Exception {
        excludeFeatures("android.hardware.type.watch");

        runTest(".projection.video.ProjectionVideoActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ProjectionTouchTest() throws Exception {
        requireFeatures("android.hardware.faketouch", "android.hardware.touchscreen.multitouch");

        runTest(".projection.touch.ProjectionTouchActivity");
    }

    @Interactive
    @Test
    @SupportMultiDisplayMode
    // MultiDisplayMode
    public void ProjectionOffscreenTest() throws Exception {
        excludeFeatures(
                "android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.automotive");

        runTest(".projection.offscreen.ProjectionOffscreenActivity");
    }
}
