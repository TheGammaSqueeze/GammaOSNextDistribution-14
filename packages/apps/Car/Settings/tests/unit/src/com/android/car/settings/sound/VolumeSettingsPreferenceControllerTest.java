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

package com.android.car.settings.sound;

import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_EVENTS;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.os.UserManager.DISALLOW_ADJUST_VOLUME;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupInfo;
import android.content.Context;
import android.os.UserManager;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.settings.CarSettingsApplication;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.common.SeekBarPreference;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class VolumeSettingsPreferenceControllerTest {

    private static final int ZONE_ID = PRIMARY_AUDIO_ZONE;
    private static final int INVALID_ZONE_ID = -1;
    private static final int GROUP_ID = 0;

    private static final String TEST_RESTRICTION = DISALLOW_ADJUST_VOLUME;

    private static final int TEST_PRIMARY_ZONE_GROUP_0 = 0;
    private static final int MIN_GAIN = 0;
    private static final int MAX_GAIN = 100;
    private static final int DEFAULT_GAIN = 50;
    private static final int NEW_GAIN = 80;
    private static final int STEP_SIZE = 2;
    private static final int TEST_DEFAULT_VOLUME = DEFAULT_GAIN / STEP_SIZE;
    private static final int TEST_NEW_VOLUME = NEW_GAIN / STEP_SIZE;
    private static final int TEST_MIN_VOLUME = MIN_GAIN / STEP_SIZE;
    private static final int TEST_MAX_VOLUME = MAX_GAIN / STEP_SIZE;
    private static final int TEST_MIN_ACTIVATION_VOLUME = TEST_MIN_VOLUME + 1;
    private static final int TEST_MAX_ACTIVATION_VOLUME = TEST_MAX_VOLUME - 1;
    private static final CarVolumeGroupInfo TEST_PRIMARY_ZONE_DEFAULT_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_PRIMARY_ZONE_GROUP_0,
                    PRIMARY_AUDIO_ZONE, TEST_PRIMARY_ZONE_GROUP_0).setMuted(false)
                    .setMinVolumeGainIndex(TEST_MIN_VOLUME).setMaxVolumeGainIndex(TEST_MAX_VOLUME)
                    .setVolumeGainIndex(TEST_DEFAULT_VOLUME)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_VOLUME)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_VOLUME).build();
    private static final CarVolumeGroupInfo TEST_PRIMARY_ZONE_NEW_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_PRIMARY_ZONE_GROUP_0,
                    PRIMARY_AUDIO_ZONE, TEST_PRIMARY_ZONE_GROUP_0).setMuted(false)
                    .setMinVolumeGainIndex(TEST_MIN_VOLUME).setMaxVolumeGainIndex(TEST_MAX_VOLUME)
                    .setVolumeGainIndex(TEST_NEW_VOLUME)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_VOLUME)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_VOLUME).build();
    private static final CarVolumeGroupInfo TEST_PRIMARY_ZONE_MUTED_GROUP_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_PRIMARY_ZONE_GROUP_0,
                    PRIMARY_AUDIO_ZONE, TEST_PRIMARY_ZONE_GROUP_0).setMuted(true)
                    .setMinVolumeGainIndex(TEST_MIN_VOLUME).setMaxVolumeGainIndex(TEST_MAX_VOLUME)
                    .setVolumeGainIndex(TEST_DEFAULT_VOLUME)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_VOLUME)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_VOLUME).build();
    private static final CarVolumeGroupEvent TEST_CAR_VOLUME_GROUP_EVENT_DEFAULT =
            new CarVolumeGroupEvent.Builder(List.of(TEST_PRIMARY_ZONE_DEFAULT_VOLUME_INFO),
                    CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                    List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();
    private static final CarVolumeGroupEvent TEST_CAR_VOLUME_GROUP_EVENT_NEW_VOLUME =
            new CarVolumeGroupEvent.Builder(List.of(TEST_PRIMARY_ZONE_NEW_VOLUME_INFO),
                    CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                    List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();
    private static final CarVolumeGroupEvent TEST_CAR_VOLUME_GROUP_EVENT_MUTED_GROUP =
            new CarVolumeGroupEvent.Builder(List.of(TEST_PRIMARY_ZONE_MUTED_GROUP_INFO),
                    CarVolumeGroupEvent.EVENT_TYPE_MUTE_CHANGED,
                    List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private VolumeSettingsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CarAudioManager mCarAudioManager;
    @Mock
    private VolumeSettingsRingtoneManager mRingtoneManager;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private Toast mMockToast;
    @Mock
    private CarSettingsApplication mCarSettingsApplication;

    @Before
    @UiThreadTest
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(Toast.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        when(mCarAudioManager.getVolumeGroupCount(ZONE_ID)).thenReturn(1);
        when(mCarAudioManager.getUsagesForVolumeGroupId(ZONE_ID, GROUP_ID))
                .thenReturn(new int[]{1, 2});
        when(mCarAudioManager.getGroupMinVolume(ZONE_ID, GROUP_ID)).thenReturn(TEST_MIN_VOLUME);
        when(mCarAudioManager.getGroupVolume(ZONE_ID, GROUP_ID)).thenReturn(TEST_DEFAULT_VOLUME);
        when(mCarAudioManager.getGroupMaxVolume(ZONE_ID, GROUP_ID)).thenReturn(TEST_MAX_VOLUME);
        when(mCarAudioManager.isVolumeGroupMuted(ZONE_ID, GROUP_ID)).thenReturn(false);
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING))
                .thenReturn(true);
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .thenReturn(false);
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_EVENTS))
                .thenReturn(false);
        when(mCarAudioManager.isPlaybackOnVolumeGroupActive(ZONE_ID, GROUP_ID)).thenReturn(false);
        when(mCarAudioManager.getVolumeGroupInfo(ZONE_ID, GROUP_ID))
                .thenReturn(TEST_PRIMARY_ZONE_DEFAULT_VOLUME_INFO);

        when(mContext.getApplicationContext()).thenReturn(mCarSettingsApplication);
        when(mCarSettingsApplication.getCarAudioManager()).thenReturn(mCarAudioManager);
        when(mCarSettingsApplication.getMyAudioZoneId()).thenReturn(ZONE_ID);

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mPreferenceController = new TestVolumeSettingsPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions, mRingtoneManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testRefreshUi_serviceNotStarted() {
        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void testRefreshUi_serviceStarted() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onServiceConnected_registersVolumeCallback() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        verify(mCarAudioManager).registerCarVolumeCallback(
                mPreferenceController.mVolumeChangeCallback);
    }

    @Test
    public void testRefreshUi_serviceStarted_multipleCalls() {
        mPreferenceController.onCreate(mLifecycleOwner);

        // Calling this multiple times shouldn't increase the number of elements.
        mPreferenceController.refreshUi();
        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void testRefreshUi_createdPreferenceHasMinMax() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        SeekBarPreference preference = (SeekBarPreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.getMin()).isEqualTo(TEST_MIN_VOLUME);
        assertThat(preference.getValue()).isEqualTo(TEST_DEFAULT_VOLUME);
        assertThat(preference.getMax()).isEqualTo(TEST_MAX_VOLUME);
    }

    @Test
    public void testOnPreferenceChange_noDynamicAudio_ringtonePlays() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        SeekBarPreference preference = (SeekBarPreference) mPreferenceGroup.getPreference(0);
        preference.getOnPreferenceChangeListener().onPreferenceChange(preference, TEST_NEW_VOLUME);

        verify(mRingtoneManager).playAudioFeedback(anyInt(), anyInt());
    }

    @Test
    public void testOnPreferenceChange_dynamicAudio_playbackActive_ringtoneDoesNotPlay() {
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .thenReturn(true);
        when(mCarAudioManager.isPlaybackOnVolumeGroupActive(ZONE_ID, GROUP_ID)).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        SeekBarPreference preference = (SeekBarPreference) mPreferenceGroup.getPreference(0);
        preference.getOnPreferenceChangeListener().onPreferenceChange(preference, TEST_NEW_VOLUME);

        verify(mRingtoneManager, never()).playAudioFeedback(anyInt(), anyInt());
    }

    @Test
    public void testOnPreferenceChange_dynamicAudio_playbackNotActive_ringtonePlays() {
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .thenReturn(true);
        when(mCarAudioManager.isPlaybackOnVolumeGroupActive(ZONE_ID, GROUP_ID)).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        SeekBarPreference preference = (SeekBarPreference) mPreferenceGroup.getPreference(0);
        preference.getOnPreferenceChangeListener().onPreferenceChange(preference, TEST_NEW_VOLUME);

        verify(mRingtoneManager).playAudioFeedback(anyInt(), anyInt());
    }

    @Test
    public void testOnPreferenceChange_audioManagerSet() throws CarNotConnectedException {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        SeekBarPreference preference = (SeekBarPreference) mPreferenceGroup.getPreference(0);
        preference.getOnPreferenceChangeListener().onPreferenceChange(preference, TEST_NEW_VOLUME);

        verify(mCarAudioManager).setGroupVolume(ZONE_ID, GROUP_ID, TEST_NEW_VOLUME, 0);
    }

    @Test
    public void onGroupVolumeChanged_sameValue_doesNotUpdateVolumeSeekbar() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        SeekBarPreference preference = spy((SeekBarPreference) mPreferenceGroup.getPreference(0));
        mPreferenceController.mVolumeChangeCallback.onGroupVolumeChanged(ZONE_ID,
                GROUP_ID, /* flags= */ 0);

        verify(preference, never()).setValue(any(Integer.class));
    }

    @Test
    public void onGroupVolumeChanged_differentValue_updatesVolumeSeekbar() {
        when(mCarAudioManager.getVolumeGroupInfo(ZONE_ID, GROUP_ID))
                .thenReturn(TEST_PRIMARY_ZONE_NEW_VOLUME_INFO);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        mPreferenceController.mVolumeChangeCallback.onGroupVolumeChanged(ZONE_ID,
                GROUP_ID, /* flags= */ 0);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        SeekBarPreference preference = (SeekBarPreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.getValue()).isEqualTo(TEST_NEW_VOLUME);
    }

    @Test
    public void onGroupVolumeChanged_invalidZoneId_doesNotUpdateVolumeSeekbar() {
        when(mCarAudioManager.getVolumeGroupInfo(ZONE_ID, GROUP_ID))
                .thenReturn(TEST_PRIMARY_ZONE_NEW_VOLUME_INFO);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        mPreferenceController.mVolumeChangeCallback.onGroupVolumeChanged(INVALID_ZONE_ID,
                GROUP_ID, /* flags= */ 0);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        SeekBarPreference preference =
                (SeekBarPreference) spy(mPreferenceGroup.getPreference(0));
        verify(preference, never()).setValue(any(Integer.class));
    }

    @Test
    public void onGroupMuteChanged_sameValue_doesNotUpdateIsMuted() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        mPreferenceController.mVolumeChangeCallback.onGroupMuteChanged(ZONE_ID,
                GROUP_ID, /* flags= */ 0);

        verify(preference, never()).setIsMuted(any(Boolean.class));
    }

    @Test
    public void onGroupMuteChanged_differentValue_updatesMutedState() {
        when(mCarAudioManager.getVolumeGroupInfo(ZONE_ID, GROUP_ID))
                .thenReturn(TEST_PRIMARY_ZONE_MUTED_GROUP_INFO);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        mPreferenceController.mVolumeChangeCallback.onGroupMuteChanged(ZONE_ID,
                GROUP_ID, /* flags= */ 0);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        VolumeSeekBarPreference preference =
                (VolumeSeekBarPreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isMuted()).isEqualTo(true);
    }

    @Test
    public void onGroupMuteChanged_differentValue_muteFeatureDisabled_doesNotUpdatesMutedState() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        when(mCarAudioManager.isVolumeGroupMuted(ZONE_ID, GROUP_ID)).thenReturn(true);
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING))
                .thenReturn(false);
        mPreferenceController.mVolumeChangeCallback.onGroupMuteChanged(ZONE_ID,
                GROUP_ID, /* flags= */ 0);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        VolumeSeekBarPreference preference =
                (VolumeSeekBarPreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isMuted()).isEqualTo(false);
    }

    @Test
    public void onGroupMuteChanged_invalidZoneId_doesNotUpdateMutedState() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        when(mCarAudioManager.isVolumeGroupMuted(ZONE_ID, GROUP_ID)).thenReturn(true);
        mPreferenceController.mVolumeChangeCallback.onGroupMuteChanged(ZONE_ID,
                GROUP_ID, /* flags= */ 0);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        VolumeSeekBarPreference preference =
                (VolumeSeekBarPreference) spy(mPreferenceGroup.getPreference(0));
        verify(preference, never()).setIsMuted(any(Boolean.class));
    }

    @Test
    public void onVolumeGroupEvent_sameGroupInfo_doesNotUpdateVolumeSeekbar() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();
        SeekBarPreference preference = spy((SeekBarPreference) mPreferenceGroup.getPreference(0));

        mPreferenceController.mCarVolumeGroupEventCallback.onVolumeGroupEvent(
                List.of(TEST_CAR_VOLUME_GROUP_EVENT_DEFAULT));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(preference, never()).setValue(any(Integer.class));
    }

    @Test
    public void onVolumeGroupEvent_differentGroupInfo_updatesVolumeSeekbar() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        mPreferenceController.mCarVolumeGroupEventCallback.onVolumeGroupEvent(
                List.of(TEST_CAR_VOLUME_GROUP_EVENT_NEW_VOLUME));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        SeekBarPreference preference = (SeekBarPreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.getValue()).isEqualTo(TEST_NEW_VOLUME);
    }

    @Test
    public void onVolumeGroupEvent_invalidGroupInfo_doesNotUpdateVolumeSeekbar() {
        CarVolumeGroupInfo invalidGroupInfo =
                new CarVolumeGroupInfo.Builder("group id " + 0, 1 /* ZoneId */, 0).setMuted(false)
                        .setMinVolumeGainIndex(TEST_MIN_VOLUME)
                        .setMaxVolumeGainIndex(TEST_MAX_VOLUME)
                        .setVolumeGainIndex(TEST_DEFAULT_VOLUME)
                        .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_VOLUME)
                        .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_VOLUME).build();
        CarVolumeGroupEvent eventWithInvalidGroupInfo =
                new CarVolumeGroupEvent.Builder(List.of(invalidGroupInfo),
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                        List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        mPreferenceController.mCarVolumeGroupEventCallback.onVolumeGroupEvent(
                List.of(eventWithInvalidGroupInfo));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        SeekBarPreference preference = spy((SeekBarPreference) mPreferenceGroup.getPreference(0));
        verify(preference, never()).setValue(any(Integer.class));
    }

    @Test
    public void onVolumeGroupEvent_groupMuteChanged_updatesVolumeSeekbar() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        mPreferenceController.mCarVolumeGroupEventCallback.onVolumeGroupEvent(
                List.of(TEST_CAR_VOLUME_GROUP_EVENT_MUTED_GROUP));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        VolumeSeekBarPreference preference =
                (VolumeSeekBarPreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isMuted()).isTrue();
    }

    @Test
    public void onVolumeGroupEvent_groupMuteUnchanged_doesNotUpdateVolumeSeekbar() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        mPreferenceController.mCarVolumeGroupEventCallback.onVolumeGroupEvent(
                List.of(TEST_CAR_VOLUME_GROUP_EVENT_DEFAULT));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        VolumeSeekBarPreference preference =
                (VolumeSeekBarPreference) spy(mPreferenceGroup.getPreference(0));
        verify(preference, never()).setIsMuted(any(Boolean.class));
        assertThat(preference.isMuted()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_unavailable() {
        mockUserRestrictionSetByUm(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_unavailable_zoneWrite() {
        mockUserRestrictionSetByUm(true);
        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_unavailable_zoneRead() {
        mockUserRestrictionSetByUm(true);
        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_unavailable_zoneHidden() {
        mockUserRestrictionSetByUm(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_unavailable() {
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_unavailable_zoneWrite() {
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_unavailable_zoneRead() {
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_unavailable_zoneHidden() {
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_unrestricted_available() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_unrestricted_available_zoneWrite() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_unrestricted_available_zoneRead() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_unrestricted_available_zoneHidden() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByDpm_showDialog() {
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        preference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByUm_showToast() {
        mockUserRestrictionSetByUm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        VolumeSeekBarPreference preference =
                spy((VolumeSeekBarPreference) mPreferenceGroup.getPreference(0));
        preference.performClick();

        assertShowingBlockedToast();
    }

    private static class TestVolumeSettingsPreferenceController extends
            VolumeSettingsPreferenceController {

        TestVolumeSettingsPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions,
                VolumeSettingsRingtoneManager ringtoneManager) {
            super(context, preferenceKey, fragmentController, uxRestrictions, ringtoneManager);
        }

        @Override
        public int carVolumeItemsXml() {
            return R.xml.test_car_volume_items;
        }
    }

    private void mockUserRestrictionSetByUm(boolean restricted) {
        when(mMockUserManager.hasBaseUserRestriction(eq(TEST_RESTRICTION), any()))
                .thenReturn(restricted);
    }

    private void mockUserRestrictionSetByDpm(boolean restricted) {
        mockUserRestrictionSetByUm(false);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(restricted);
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    private void assertShowingBlockedToast() {
        String toastText = mContext.getString(R.string.action_unavailable);
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), eq(toastText), anyInt()));
        verify(mMockToast).show();
    }
}
