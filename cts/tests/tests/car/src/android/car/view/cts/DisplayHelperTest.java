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

package android.car.view.cts;

import static com.google.common.truth.Truth.assertThat;

import android.car.view.DisplayHelper;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DisplayHelperTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Test
    public void testGetUniqueId() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        String defaultDisplayUniqueId = DisplayHelper.getUniqueId(defaultDisplay);
        assertThat(defaultDisplayUniqueId).isNotNull();
        // {@link Display#getUniqueId()} is a test api.
        assertThat(defaultDisplayUniqueId).isEqualTo(defaultDisplay.getUniqueId());
    }
}
