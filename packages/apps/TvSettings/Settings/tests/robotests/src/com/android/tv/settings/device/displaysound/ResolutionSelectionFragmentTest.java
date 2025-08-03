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

package com.android.tv.settings.device.displaysound;

import static com.android.tv.settings.device.displaysound.ResolutionSelectionFragment.KEY_RESOLUTION_PREFIX;
import static com.android.tv.settings.device.displaysound.ResolutionSelectionFragment.KEY_RESOLUTION_SELECTION_AUTO;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.util.ResolutionSelectionUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.androidx.fragment.FragmentController;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(RobolectricTestRunner.class)
public class ResolutionSelectionFragmentTest {
    @Mock
    private DisplayManager mDisplayManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOnPreferenceTreeClick_selectAuto() {
        Display.Mode[] modes = new Display.Mode[] {
                new Display.Mode(0, 800, 600, 120),
                new Display.Mode(1, 800, 600, 60),
                new Display.Mode(2, 2160, 2160, 60),
                new Display.Mode(3, 2160, 2160, 120),
        };
        ResolutionSelectionFragment fragment =
                createResolutionSelectionFragmentWith(modes);
        RadioPreference preference = fragment.findPreference(KEY_RESOLUTION_SELECTION_AUTO);

        fragment.onPreferenceTreeClick(preference);

        verify(mDisplayManager).clearGlobalUserPreferredDisplayMode();
        assertThat(preference.isChecked()).isTrue();
    }

    @Test
    public void testOnPreferenceTreeClick_selectMode() {
        Display.Mode[] modes = new Display.Mode[] {
                new Display.Mode(0, 800, 600, 120),
                new Display.Mode(1, 800, 600, 60),
                new Display.Mode(2, 2160, 2160, 60),
                new Display.Mode(3, 2160, 2160, 120),
        };
        ResolutionSelectionFragment fragment =
                createResolutionSelectionFragmentWith(modes);
        RadioPreference preference = fragment.findPreference(KEY_RESOLUTION_PREFIX + 2);

        fragment.onPreferenceTreeClick(preference);

        ArgumentCaptor<Display.Mode> mode = ArgumentCaptor.forClass(Display.Mode.class);
        verify(mDisplayManager).setGlobalUserPreferredDisplayMode(mode.capture());
        assertThat(mode.getValue().getPhysicalHeight()).isEqualTo(600);
        assertThat(mode.getValue().getPhysicalWidth()).isEqualTo(800);
        assertThat(mode.getValue().getRefreshRate()).isEqualTo(120);
        assertThat(preference.isChecked()).isTrue();
    }

    @Test
    public void testGetPreferenceScreen_returnsCorrectModeDescriptions() {
        Display.Mode[] modes = new Display.Mode[] {
                new Display.Mode(0, 600, 800, 59.944f),
                new Display.Mode(1, 800, 1200, 60),
                new Display.Mode(2, 576, 576, 60),
                new Display.Mode(3, 800, 1200, 120),
                new Display.Mode(4, 600, 800, 120),
                new Display.Mode(5, 2160, 2160, 60),
                new Display.Mode(5, 2160, 2160, 59.944f),
        };
        ResolutionSelectionFragment fragment =
                createResolutionSelectionFragmentWith(modes);

        assertThat(fragment.getPreferenceScreen().getPreferenceCount()).isEqualTo(1);
        Preference modePreference = fragment.getPreferenceScreen().getPreference(0);
        assertThat(getChildrenTitles(modePreference)).containsExactly(
                fragment.getContext().getString(R.string.resolution_selection_auto_title),
                fragment.getContext().getString(R.string.resolution_display_mode,
                        ResolutionSelectionUtils.getResolutionString(2160, 2160),
                        ResolutionSelectionUtils.getRefreshRateString(60)),
                fragment.getContext().getString(R.string.resolution_display_mode,
                        ResolutionSelectionUtils.getResolutionString(2160, 2160),
                        ResolutionSelectionUtils.getRefreshRateString(59.944f)),
                fragment.getContext().getString(R.string.resolution_display_mode,
                        ResolutionSelectionUtils.getResolutionString(576, 576),
                        ResolutionSelectionUtils.getRefreshRateString(60)),
                fragment.getContext().getString(R.string.resolution_display_mode,
                        ResolutionSelectionUtils.getResolutionString(800, 1200),
                        ResolutionSelectionUtils.getRefreshRateString(120)),
                fragment.getContext().getString(R.string.resolution_display_mode,
                        ResolutionSelectionUtils.getResolutionString(800, 1200),
                        ResolutionSelectionUtils.getRefreshRateString(60)),
                fragment.getContext().getString(R.string.resolution_display_mode,
                        ResolutionSelectionUtils.getResolutionString(600, 800),
                        ResolutionSelectionUtils.getRefreshRateString(120)),
                fragment.getContext().getString(R.string.resolution_display_mode,
                        ResolutionSelectionUtils.getResolutionString(600, 800),
                        ResolutionSelectionUtils.getRefreshRateString(59.944f)));

        assertThat(getChildrenSummaries(modePreference)).containsExactly(
                "2,160 x 2,160",
                "2,160 x 2,160",
                "576 x 576",
                "800 x 1,200",
                "800 x 1,200",
                "600 x 800",
                "600 x 800");
    }

    @Test
    public void testGetUserPreferredDisplayMode_selectsCorrectPreference() {
        Display.Mode[] modes = new Display.Mode[] {
                new Display.Mode(1, 800, 1200, 120),
                new Display.Mode(2, 800, 1200, 60),
                new Display.Mode(0, 600, 800, 120)
        };
        Display.Mode userPreferredMode = modes[1];
        ResolutionSelectionFragment fragment =
                createResolutionSelectionFragmentWith(modes, userPreferredMode);

        RadioPreference autoPreference = fragment.findPreference(KEY_RESOLUTION_SELECTION_AUTO);
        assertThat(autoPreference.isChecked()).isFalse();
        RadioPreference preference = fragment.findPreference(KEY_RESOLUTION_PREFIX + 0);
        assertThat(preference.isChecked()).isFalse();
        preference = fragment.findPreference(KEY_RESOLUTION_PREFIX + 1);
        assertThat(preference.isChecked()).isTrue();
        preference = fragment.findPreference(KEY_RESOLUTION_PREFIX + 2);
        assertThat(preference.isChecked()).isFalse();

        fragment =
                createResolutionSelectionFragmentWith(modes, null);

        autoPreference = fragment.findPreference(KEY_RESOLUTION_SELECTION_AUTO);
        assertThat(autoPreference.isChecked()).isTrue();
        preference = fragment.findPreference(KEY_RESOLUTION_PREFIX + 0);
        assertThat(preference.isChecked()).isFalse();
        preference = fragment.findPreference(KEY_RESOLUTION_PREFIX + 1);
        assertThat(preference.isChecked()).isFalse();
        preference = fragment.findPreference(KEY_RESOLUTION_PREFIX + 2);
        assertThat(preference.isChecked()).isFalse();
    }

    private ResolutionSelectionFragment createResolutionSelectionFragmentWith(
            Display.Mode[] supportedModes) {
        return createResolutionSelectionFragmentWith(supportedModes, null);
    }

    private ResolutionSelectionFragment createResolutionSelectionFragmentWith(
            Display.Mode[] supportedModes, Display.Mode userPreferredMode) {
        Display display = spy(Display.class);
        doReturn(supportedModes).when(display).getSupportedModes();
        Display.Mode systemPreferredMode = supportedModes[0];
        doReturn(systemPreferredMode).when(display).getSystemPreferredDisplayMode();
        doReturn(display).when(mDisplayManager).getDisplay(Display.DEFAULT_DISPLAY);

        ResolutionSelectionFragment fragment = spy(ResolutionSelectionFragment.class);
        doReturn(mDisplayManager).when(fragment).getDisplayManager();
        doReturn(userPreferredMode).when(mDisplayManager).getGlobalUserPreferredDisplayMode();

        return FragmentController.of(fragment)
                .create()
                .start()
                .get();
    }

    private List<String> getChildrenTitles(Preference preference) {
        PreferenceCategory category = (PreferenceCategory) preference;

        return IntStream.range(0, category.getPreferenceCount())
                .mapToObj(i -> category.getPreference(i).getTitle().toString())
                .collect(Collectors.toList());
    }

    private List<String> getChildrenSummaries(Preference preference) {
        PreferenceCategory category = (PreferenceCategory) preference;

        return IntStream.range(1, category.getPreferenceCount())
                .mapToObj(i -> category.getPreference(i).getSummary().toString())
                .collect(Collectors.toList());
    }
}
