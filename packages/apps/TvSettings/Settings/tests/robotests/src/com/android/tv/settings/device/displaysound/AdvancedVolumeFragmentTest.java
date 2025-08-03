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

package com.android.tv.settings.device.displaysound;

import static com.android.tv.settings.device.displaysound.AdvancedVolumeFragment.KEY_SHOW_HIDE_FORMAT_INFO;
import static com.android.tv.settings.device.displaysound.AdvancedVolumeFragment.KEY_SURROUND_SOUND_AUTO;
import static com.android.tv.settings.device.displaysound.AdvancedVolumeFragment.KEY_SURROUND_SOUND_FORMAT_PREFIX;
import static com.android.tv.settings.device.displaysound.AdvancedVolumeFragment.KEY_SURROUND_SOUND_MANUAL;
import static com.android.tv.settings.device.displaysound.AdvancedVolumeFragment.KEY_SURROUND_SOUND_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.R;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.androidx.fragment.FragmentController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(RobolectricTestRunner.class)
public class AdvancedVolumeFragmentTest {

    @Spy
    private AudioManager mAudioManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOnPreferenceChange_withAuto_storesAutoInSettings() {
        AdvancedVolumeFragment fragment = createDefaultAdvancedVolumeFragment();
        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_AUTO);

        fragment.onPreferenceTreeClick(preference);

        ArgumentCaptor<Integer> mode = ArgumentCaptor.forClass(Integer.class);
        verify(mAudioManager).setEncodedSurroundMode(mode.capture());
        assertThat(mode.getValue()).isEqualTo(Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO);
    }

    @Test
    public void testOnPreferenceChange_withManual_storesManualInSettings() {
        AdvancedVolumeFragment fragment = createDefaultAdvancedVolumeFragment();
        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_MANUAL);

        fragment.onPreferenceTreeClick(preference);

        ArgumentCaptor<Integer> mode = ArgumentCaptor.forClass(Integer.class);
        verify(mAudioManager).setEncodedSurroundMode(mode.capture());
        assertThat(mode.getValue()).isEqualTo(Settings.Global.ENCODED_SURROUND_OUTPUT_MANUAL);
    }

    @Test
    public void testOnPreferenceChange_withNever_storesNeverInSettings() {
        AdvancedVolumeFragment fragment = createDefaultAdvancedVolumeFragment();
        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_NONE);

        fragment.onPreferenceTreeClick(preference);

        ArgumentCaptor<Integer> mode = ArgumentCaptor.forClass(Integer.class);
        verify(mAudioManager).setEncodedSurroundMode(mode.capture());
        assertThat(mode.getValue()).isEqualTo(Settings.Global.ENCODED_SURROUND_OUTPUT_NEVER);
    }

    @Test
    public void testOnPreferenceTreeClick_withFormatDisabled_disablesFormatInAudioManager() {
        Map<Integer, Boolean> formats = ImmutableMap.of(
                AudioFormat.ENCODING_DTS, true,
                AudioFormat.ENCODING_DOLBY_MAT, true,
                AudioFormat.ENCODING_DOLBY_TRUEHD, true);
        List<Integer> reportedFormats = Arrays.asList(
                AudioFormat.ENCODING_DTS,
                AudioFormat.ENCODING_DOLBY_TRUEHD);
        AdvancedVolumeFragment fragment =
                createAdvancedVolumeFragmentWithAudioManagerReturning(formats, reportedFormats);

        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_MANUAL);
        fragment.onPreferenceTreeClick(preference);

        SwitchPreference pref = (SwitchPreference) fragment.findPreference(
                KEY_SURROUND_SOUND_FORMAT_PREFIX + AudioFormat.ENCODING_DTS);
        pref.setChecked(true);
        fragment.onPreferenceTreeClick(pref);

        ArgumentCaptor<Integer> audioFormat = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Boolean> formatEnabled = ArgumentCaptor.forClass(Boolean.class);
        verify(mAudioManager, times(4)).setSurroundFormatEnabled(
                audioFormat.capture(), formatEnabled.capture());
        assertThat(audioFormat.getValue()).isEqualTo(AudioFormat.ENCODING_DTS);
        assertThat(formatEnabled.getValue()).isTrue();
    }

    @Test
    public void testOnPreferenceTreeClick_withFormatEnabled_enablesFormatInAudioManager() {
        Map<Integer, Boolean> formats = ImmutableMap.of(
                AudioFormat.ENCODING_DTS, true,
                AudioFormat.ENCODING_DOLBY_MAT, true,
                AudioFormat.ENCODING_DOLBY_TRUEHD, true);
        List<Integer> reportedFormats = Arrays.asList(
                AudioFormat.ENCODING_DTS,
                AudioFormat.ENCODING_DOLBY_TRUEHD);
        AdvancedVolumeFragment fragment =
                createAdvancedVolumeFragmentWithAudioManagerReturning(formats, reportedFormats);

        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_MANUAL);
        fragment.onPreferenceTreeClick(preference);

        SwitchPreference pref = (SwitchPreference) fragment.findPreference(
                KEY_SURROUND_SOUND_FORMAT_PREFIX + AudioFormat.ENCODING_DTS);
        pref.setChecked(false);
        fragment.onPreferenceTreeClick(pref);

        ArgumentCaptor<Integer> audioFormat = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Boolean> formatEnabled = ArgumentCaptor.forClass(Boolean.class);
        verify(mAudioManager, times(4)).setSurroundFormatEnabled(
                audioFormat.capture(), formatEnabled.capture());
        assertThat(audioFormat.getValue()).isEqualTo(AudioFormat.ENCODING_DTS);
        assertThat(formatEnabled.getValue()).isFalse();
    }

    @Test
    public void testOnPreferenceTreeClick_withDTSFormatsDisabled_disablesFormatInAudioManager() {
        Map<Integer, Boolean> formats = ImmutableMap.of(
                AudioFormat.ENCODING_DTS_HD, true,
                AudioFormat.ENCODING_DTS_HD_MA, true,
                AudioFormat.ENCODING_DTS_UHD_P1, true,
                AudioFormat.ENCODING_DTS_UHD_P2, true);
        List<Integer> reportedFormats = Arrays.asList(
                AudioFormat.ENCODING_DTS_HD,
                AudioFormat.ENCODING_DTS_HD_MA,
                AudioFormat.ENCODING_DTS_UHD_P1,
                AudioFormat.ENCODING_DTS_UHD_P2);
        AdvancedVolumeFragment fragment =
                createAdvancedVolumeFragmentWithAudioManagerReturning(formats, reportedFormats);

        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_MANUAL);
        fragment.onPreferenceTreeClick(preference);

        SwitchPreference pref = (SwitchPreference) fragment.findPreference(
                KEY_SURROUND_SOUND_FORMAT_PREFIX + AudioFormat.ENCODING_DTS_HD);
        pref.setChecked(false);
        fragment.onPreferenceTreeClick(pref);

        pref = (SwitchPreference) fragment.findPreference(
                KEY_SURROUND_SOUND_FORMAT_PREFIX + AudioFormat.ENCODING_DTS_UHD_P1);
        pref.setChecked(false);
        fragment.onPreferenceTreeClick(pref);

        ArgumentCaptor<Integer> audioFormat = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Boolean> formatEnabled = ArgumentCaptor.forClass(Boolean.class);
        verify(mAudioManager, times(8)).setSurroundFormatEnabled(
                audioFormat.capture(), formatEnabled.capture());
        List<Integer> expectedFormats = Arrays.asList(
                AudioFormat.ENCODING_DTS_HD,
                AudioFormat.ENCODING_DTS_HD_MA,
                AudioFormat.ENCODING_DTS_UHD_P1,
                AudioFormat.ENCODING_DTS_UHD_P2,
                AudioFormat.ENCODING_DTS_HD,
                AudioFormat.ENCODING_DTS_HD_MA,
                AudioFormat.ENCODING_DTS_UHD_P1,
                AudioFormat.ENCODING_DTS_UHD_P2);
        List<Boolean> expectedValues = Arrays.asList(
                true, true, true, true, false, false, false, false);
        assertThat(audioFormat.getAllValues()).containsExactlyElementsIn(expectedFormats);
        assertThat(formatEnabled.getAllValues()).containsExactlyElementsIn(expectedValues);
    }

    @Test
    public void testOnPreferenceTreeClick_withDTSFormatsEnabled_enablesFormatInAudioManager() {
        Map<Integer, Boolean> formats = ImmutableMap.of(
                AudioFormat.ENCODING_DTS_HD, false,
                AudioFormat.ENCODING_DTS_HD_MA, false,
                AudioFormat.ENCODING_DTS_UHD_P1, false,
                AudioFormat.ENCODING_DTS_UHD_P2, false);
        List<Integer> reportedFormats = Arrays.asList(
                AudioFormat.ENCODING_DTS_HD,
                AudioFormat.ENCODING_DTS_HD_MA,
                AudioFormat.ENCODING_DTS_UHD_P1,
                AudioFormat.ENCODING_DTS_UHD_P2);
        AdvancedVolumeFragment fragment =
                createAdvancedVolumeFragmentWithAudioManagerReturning(formats, reportedFormats);

        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_MANUAL);
        fragment.onPreferenceTreeClick(preference);

        SwitchPreference pref = (SwitchPreference) fragment.findPreference(
                KEY_SURROUND_SOUND_FORMAT_PREFIX + AudioFormat.ENCODING_DTS_HD);
        pref.setChecked(true);
        fragment.onPreferenceTreeClick(pref);

        pref = (SwitchPreference) fragment.findPreference(
                KEY_SURROUND_SOUND_FORMAT_PREFIX + AudioFormat.ENCODING_DTS_UHD_P1);
        pref.setChecked(true);
        fragment.onPreferenceTreeClick(pref);

        ArgumentCaptor<Integer> audioFormat = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Boolean> formatEnabled = ArgumentCaptor.forClass(Boolean.class);
        verify(mAudioManager, times(8)).setSurroundFormatEnabled(
                audioFormat.capture(), formatEnabled.capture());
        List<Integer> expectedFormats = Arrays.asList(
                AudioFormat.ENCODING_DTS_HD,
                AudioFormat.ENCODING_DTS_HD_MA,
                AudioFormat.ENCODING_DTS_UHD_P1,
                AudioFormat.ENCODING_DTS_UHD_P2,
                AudioFormat.ENCODING_DTS_HD,
                AudioFormat.ENCODING_DTS_HD_MA,
                AudioFormat.ENCODING_DTS_UHD_P1,
                AudioFormat.ENCODING_DTS_UHD_P2);
        List<Boolean> expectedValues = Arrays.asList(
                true, true, true, true, true, true, true, true);
        assertThat(audioFormat.getAllValues()).containsExactlyElementsIn(expectedFormats);
        assertThat(formatEnabled.getAllValues()).containsExactlyElementsIn(expectedValues);
    }

    @Test
    public void testGetPreferenceScreen_whenManual_returnsFormatsInCorrectPreferenceGroup() {
        Map<Integer, Boolean> formats = ImmutableMap.of(
                AudioFormat.ENCODING_DTS, true,
                AudioFormat.ENCODING_DOLBY_MAT, true);
        List<Integer> reportedFormats = Arrays.asList(AudioFormat.ENCODING_DTS);
        AdvancedVolumeFragment fragment =
                createAdvancedVolumeFragmentWithAudioManagerReturning(formats, reportedFormats);

        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_MANUAL);
        fragment.onPreferenceTreeClick(preference);

        assertThat(fragment.getPreferenceScreen().getPreferenceCount()).isEqualTo(3);

        Preference supportedFormatPreference =
                fragment.getPreferenceScreen().getPreference(1);
        assertThat(supportedFormatPreference.getTitle()).isEqualTo(
                fragment.getContext().getString(R.string.surround_sound_supported_title));
        assertThat(getChildrenTitles(supportedFormatPreference)).containsExactly(
                fragment.getContext().getString(R.string.surround_sound_format_dts));

        Preference unsupportedFormatPreference =
                fragment.getPreferenceScreen().getPreference(2);
        assertThat(unsupportedFormatPreference.getTitle()).isEqualTo(
                fragment.getContext().getString(R.string.surround_sound_unsupported_title));
        assertThat(getChildrenTitles(unsupportedFormatPreference)).containsExactly(
                fragment.getContext().getString(R.string.surround_sound_format_dolby_mat));
    }

    @Test
    public void testGetPreferenceScreen_whenAuto_showsFormatInfoPreference() {
        Map<Integer, Boolean> formats = ImmutableMap.of(
                AudioFormat.ENCODING_DTS, true,
                AudioFormat.ENCODING_DOLBY_MAT, false);
        List<Integer> reportedFormats = Arrays.asList(AudioFormat.ENCODING_DTS);
        AdvancedVolumeFragment fragment =
                createAdvancedVolumeFragmentWithAudioManagerReturning(formats, reportedFormats);

        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_AUTO);
        fragment.onPreferenceTreeClick(preference);

        assertThat(fragment.getPreferenceScreen().getPreferenceCount()).isEqualTo(2);

        PreferenceCategory formatInfoPreferenceCategory =
                (PreferenceCategory) fragment.getPreferenceScreen().getPreference(1);
        assertThat(formatInfoPreferenceCategory.getTitle()).isEqualTo(
                fragment.getContext().getString(R.string.surround_sound_format_info));
        assertThat(formatInfoPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        Preference showFormatPreference = formatInfoPreferenceCategory.getPreference(0);
        assertThat(showFormatPreference.getTitle()).isEqualTo(
                fragment.getContext().getString(R.string.surround_sound_show_formats));
    }

    @Test
    public void testGetPreferenceScreen_whenAuto_returnsFormatsInCorrectPreferenceGroup() {
        Map<Integer, Boolean> formats = ImmutableMap.of(
                AudioFormat.ENCODING_DTS, true,
                AudioFormat.ENCODING_DOLBY_MAT, true);
        List<Integer> reportedFormats = Arrays.asList(AudioFormat.ENCODING_DTS);
        AdvancedVolumeFragment fragment =
                createAdvancedVolumeFragmentWithAudioManagerReturning(formats, reportedFormats);

        Preference preference = fragment.findPreference(KEY_SURROUND_SOUND_AUTO);
        fragment.onPreferenceTreeClick(preference);

        preference = fragment.findPreference(KEY_SHOW_HIDE_FORMAT_INFO);
        fragment.onPreferenceTreeClick(preference);

        PreferenceCategory formatInfoPreferenceCategory =
                (PreferenceCategory) fragment.getPreferenceScreen().getPreference(1);
        assertThat(formatInfoPreferenceCategory.getPreferenceCount()).isEqualTo(3);

        Preference hideFormatPreference = formatInfoPreferenceCategory.getPreference(0);
        assertThat(hideFormatPreference.getTitle()).isEqualTo(
                fragment.getContext().getString(R.string.surround_sound_hide_formats));

        Preference enabledFormatsCategory = formatInfoPreferenceCategory.getPreference(1);
        assertThat(enabledFormatsCategory.getTitle()).isEqualTo(
                fragment.getContext().getString(R.string.surround_sound_enabled_formats));
        assertThat(getChildrenTitles(enabledFormatsCategory)).containsExactly(
                fragment.getContext().getString(R.string.surround_sound_format_dts));

        Preference disabledFormatsCategory = formatInfoPreferenceCategory.getPreference(2);
        assertThat(disabledFormatsCategory.getTitle()).isEqualTo(
                fragment.getContext().getString(R.string.surround_sound_disabled_formats));
        assertThat(getChildrenTitles(disabledFormatsCategory)).containsExactly(
                fragment.getContext().getString(R.string.surround_sound_format_dolby_mat));
    }

    private List<String> getChildrenTitles(Preference preference) {
        PreferenceCategory category = (PreferenceCategory) preference;

        return IntStream.range(0, category.getPreferenceCount())
                .mapToObj(i -> category.getPreference(i).getTitle().toString())
                .collect(Collectors.toList());
    }

    private AdvancedVolumeFragment createDefaultAdvancedVolumeFragment() {
        return createAdvancedVolumeFragmentWithAudioManagerReturning(
                Collections.EMPTY_MAP, new ArrayList<>());
    }

    private AdvancedVolumeFragment createAdvancedVolumeFragmentWithAudioManagerReturning(
            Map<Integer, Boolean> formats, List<Integer> reportedFormats) {
        doReturn(formats).when(mAudioManager).getSurroundFormats();
        doReturn(new ArrayList(reportedFormats)).when(mAudioManager).getReportedSurroundFormats();

        AdvancedVolumeFragment fragment = spy(AdvancedVolumeFragment.class);
        doReturn(mAudioManager).when(fragment).getAudioManager();

        return FragmentController.of(fragment)
                .create()
                .start()
                .get();
    }
}
