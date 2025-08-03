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

package com.android.tv.settings.device.displaysound;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.view.Display;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;

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
public class PreferredDynamicRangeFragmentTest {
    private static final Display.Mode TEST_MODE = new Display.Mode(0, 0, 0, 0, new float[0],
            new int[]{2, 3});

    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private Display mDisplay;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOnPreferenceTreeClick_selectSystem() {
        PreferredDynamicRangeFragment fragment = createPreferredDynamicRangeFragment();
        RadioPreference preference = fragment.findPreference(
                PreferredDynamicRangeFragment.KEY_DYNAMIC_RANGE_SELECTION_SYSTEM);

        fragment.onPreferenceTreeClick(preference);

        ArgumentCaptor<HdrConversionMode> mode = ArgumentCaptor.forClass(HdrConversionMode.class);
        verify(mDisplayManager).setHdrConversionMode(mode.capture());
        assertThat(mode.getValue()).isEqualTo(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_SYSTEM));
        assertThat(preference.isChecked()).isTrue();
    }

    @Test
    public void testOnPreferenceTreeClick_selectMatchContent() {
        PreferredDynamicRangeFragment fragment = createPreferredDynamicRangeFragment();
        RadioPreference preference = fragment.findPreference(
                PreferredDynamicRangeFragment.KEY_DYNAMIC_RANGE_SELECTION_PASSTHROUGH);

        fragment.onPreferenceTreeClick(preference);

        ArgumentCaptor<HdrConversionMode> mode = ArgumentCaptor.forClass(HdrConversionMode.class);
        verify(mDisplayManager).setHdrConversionMode(mode.capture());
        assertThat(mode.getValue()).isEqualTo(
                new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH));
        assertThat(preference.isChecked()).isTrue();
    }

    @Test
    public void testGetPreferenceScreen_returnsCorrectDescriptions() {
        PreferredDynamicRangeFragment fragment = createPreferredDynamicRangeFragment();

        assertThat(fragment.getPreferenceScreen().getPreferenceCount()).isEqualTo(1);
        Preference dynamicRangePreference = fragment.getPreferenceScreen().getPreference(0);
        assertThat(getChildrenTitles(dynamicRangePreference)).containsExactly(
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_system_title),
                fragment.getContext().getString(
                        R.string.match_content_dynamic_range_title),
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_force_title));

        assertThat(getChildrenSummaries(dynamicRangePreference)).containsExactly(
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_system_desc),
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_passthrough_desc),
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_force_desc));
    }

    @Test
    public void testDynamicRangeForcePreferenceScreen_returnsCorrectDescriptions() {
        PreferredDynamicRangeForceFragment fragment =
                createPreferredDynamicRangeForceFragmentWith(new int[]{1, 2, 3});
        assertThat(fragment.getPreferenceScreen().getPreferenceCount()).isEqualTo(1);
        Preference dynamicRangePreference = fragment.getPreferenceScreen().getPreference(0);
        assertThat(getChildrenTitles(dynamicRangePreference)).containsExactly(
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_force_hdr_title,
                        fragment.getContext().getString(R.string.hdr_format_hdr10)),
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_force_hdr_title,
                        fragment.getContext().getString(R.string.hdr_format_hlg)),
                fragment.getContext().getString(
                        R.string.preferred_dynamic_range_selection_force_sdr_title));
    }

    private PreferredDynamicRangeFragment createPreferredDynamicRangeFragment() {
        return createPreferredDynamicRangeFragmentWith(new int[]{1, 2, 3});
    }

    private PreferredDynamicRangeFragment createPreferredDynamicRangeFragmentWith(int[] hdrTypes) {
        doReturn(mDisplay).when(mDisplayManager).getDisplay(Display.DEFAULT_DISPLAY);
        doReturn(new Display.Mode[]{TEST_MODE}).when(mDisplay).getSupportedModes();
        doReturn(hdrTypes).when(mDisplayManager).getSupportedHdrOutputTypes();
        doReturn(new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH))
                .when(mDisplayManager).getHdrConversionModeSetting();

        PreferredDynamicRangeFragment fragment = spy(PreferredDynamicRangeFragment.class);
        doReturn(mDisplayManager).when(fragment).getDisplayManager();

        return FragmentController.of(fragment)
                .create()
                .start()
                .get();
    }

    private PreferredDynamicRangeForceFragment createPreferredDynamicRangeForceFragmentWith(
            int[] hdrTypes) {
        doReturn(mDisplay).when(mDisplayManager).getDisplay(Display.DEFAULT_DISPLAY);
        doReturn(new Display.Mode[]{TEST_MODE}).when(mDisplay).getSupportedModes();
        doReturn(hdrTypes).when(mDisplayManager).getSupportedHdrOutputTypes();
        doReturn(new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH))
                .when(mDisplayManager).getHdrConversionModeSetting();

        PreferredDynamicRangeForceFragment fragment = spy(PreferredDynamicRangeForceFragment.class);
        doReturn(mDisplayManager).when(fragment).getDisplayManager();

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

        return IntStream.range(0, category.getPreferenceCount())
                .mapToObj(i -> category.getPreference(i).getSummary().toString())
                .collect(Collectors.toList());
    }
}
