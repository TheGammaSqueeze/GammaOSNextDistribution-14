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

package com.android.tv.settings.device.displaysound;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.preference.Preference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.androidx.fragment.FragmentController;

@RunWith(RobolectricTestRunner.class)
public class DisplaySoundFragmentTest {
    private static final String KEY_RESOLUTION_TITLE = "resolution_selection";

    @Mock
    private DisplayManager mDisplayManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testNoSystemPreferredDisplayMode_hidesDisplayModeSetting() {
        Display.Mode[] modes = new Display.Mode[] {
                new Display.Mode(0, 800, 600, 120),
                new Display.Mode(1, 800, 600, 60)
        };
        DisplaySoundFragment fragment =
                createDisplaySoundFragmentWith(modes, modes[1], null);

        Preference resolutionPreference =
                fragment.getPreferenceScreen().findPreference(KEY_RESOLUTION_TITLE);
        assertThat(resolutionPreference).isEqualTo(null);
    }

    @Test
    public void testWithSystemPreferredDisplayMode_showsDisplayModeSetting() {
        Display.Mode[] modes = new Display.Mode[] {
                new Display.Mode(0, 800, 600, 120),
                new Display.Mode(1, 800, 600, 60)
        };
        DisplaySoundFragment fragment =
                createDisplaySoundFragmentWith(modes, modes[1], modes[1]);

        Preference resolutionPreference =
                fragment.getPreferenceScreen().findPreference(KEY_RESOLUTION_TITLE);
        assertThat(resolutionPreference).isNotEqualTo(null);
    }

    private DisplaySoundFragment createDisplaySoundFragmentWith(
            Display.Mode[] supportedModes, Display.Mode userPreferredMode,
            Display.Mode systemPreferredMode) {
        Display display = spy(Display.class);
        doReturn(supportedModes).when(display).getSupportedModes();
        doReturn(systemPreferredMode).when(display).getSystemPreferredDisplayMode();
        doReturn(new int[]{}).when(mDisplayManager).getSupportedHdrOutputTypes();
        doReturn(display).when(mDisplayManager).getDisplay(Display.DEFAULT_DISPLAY);

        DisplaySoundFragment fragment = spy(DisplaySoundFragment.class);
        doReturn(mDisplayManager).when(fragment).getDisplayManager();

        return FragmentController.of(fragment)
                .create()
                .start()
                .get();
    }
}
