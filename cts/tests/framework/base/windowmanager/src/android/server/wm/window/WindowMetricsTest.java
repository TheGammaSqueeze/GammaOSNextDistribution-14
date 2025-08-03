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

package android.server.wm.window;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.WindowInsets;
import android.view.WindowMetrics;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

/**
 * Tests for {@link android.view.WindowMetrics} constructor
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:WindowMetricsTest
 */
@SmallTest
@Presubmit
@ApiTest(apis = "android.view.WindowMetrics")
public class WindowMetricsTest {

    @Test
    public void testConstructor() {
        final Rect bounds = new Rect(0, 0, 1000, 1000);
        final WindowInsets windowInsets = WindowInsets.CONSUMED;
        final float density = 1.0f;
        final WindowMetrics windowMetrics = new WindowMetrics(bounds, windowInsets, density);

        assertThat(windowMetrics.getBounds()).isEqualTo(bounds);
        assertThat(windowMetrics.getWindowInsets()).isEqualTo(windowInsets);
        assertThat(windowMetrics.getDensity()).isEqualTo(density);
    }
}
