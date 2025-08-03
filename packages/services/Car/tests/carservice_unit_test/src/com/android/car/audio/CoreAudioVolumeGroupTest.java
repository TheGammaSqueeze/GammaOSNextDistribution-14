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

package com.android.car.audio;

import static android.car.PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_MUTE_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
import static android.car.test.mocks.AndroidMockitoHelper.mockCarGetPlatformVersion;

import static com.android.car.audio.CoreAudioRoutingUtils.MEDIA_CONTEXT_INFO;
import static com.android.car.audio.CoreAudioRoutingUtils.MOVIE_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_AM_INIT_INDEX;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_CAR_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_DEVICE_ADDRESS;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_GROUP_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_MAX_INDEX;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_MIN_INDEX;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_STRATEGY;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_STRATEGY_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_CAR_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_CONTEXT_INFO;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_DEVICE_ADDRESS;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_GROUP_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_MAX_INDEX;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_MIN_INDEX;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_STRATEGY_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_CAR_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_CONTEXT_INFO;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_DEVICE_ADDRESS;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_GROUP_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_MAX_INDEX;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_MIN_INDEX;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_STRATEGY_ID;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.builtin.media.AudioManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.SparseArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CoreAudioVolumeGroupTest  extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CoreAudioVolumeGroupTest.class.getSimpleName();

    private static final int ZONE_CONFIG_ID = 0;
    private static final int MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE = 10;
    private static final int MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE = 90;
    private CarAudioContext mMusicContext;
    private CarAudioContext mNavContext;
    private CarAudioContext mOemContext;

    private CarAudioDeviceInfo mNavInfoMock = mock(CarAudioDeviceInfo.class);
    private CarAudioDeviceInfo mOemInfoMock = mock(CarAudioDeviceInfo.class);

    @Mock
    AudioManager mMockAudioManager;
    @Mock
    CarAudioSettings mSettingsMock;

    private CoreAudioVolumeGroup mMusicCoreAudioVolumeGroup;
    private CoreAudioVolumeGroup mNavCoreAudioVolumeGroup;
    private CoreAudioVolumeGroup mOemCoreAudioVolumeGroup;
    private AudioDeviceAttributes mMusicDeviceAttributes;

    public CoreAudioVolumeGroupTest() {
        super(CoreAudioVolumeGroup.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(CoreAudioHelper.class)
                .spyStatic(AudioManagerHelper.class)
                .spyStatic(Car.class);
    }
    void setupMock() {
        doReturn(MUSIC_GROUP_ID)
                .when(() -> CoreAudioHelper.getVolumeGroupIdForAudioAttributes(MUSIC_ATTRIBUTES));
        doReturn(MUSIC_STRATEGY)
                .when(() -> CoreAudioHelper.getProductStrategyForAudioAttributes(MUSIC_ATTRIBUTES));
        doReturn(MUSIC_STRATEGY)
                .when(() -> CoreAudioHelper.getProductStrategyForAudioAttributes(MOVIE_ATTRIBUTES));
        doReturn(MUSIC_ATTRIBUTES).when(() ->
                        CoreAudioHelper.selectAttributesForVolumeGroupName(eq(MUSIC_GROUP_NAME)));
        when(mMockAudioManager.getMinVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.getMaxVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MAX_INDEX);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_AM_INIT_INDEX);

        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(false);

        doReturn(NAV_GROUP_ID)
                .when(() -> CoreAudioHelper.getVolumeGroupIdForAudioAttributes(NAV_ATTRIBUTES));
        doReturn(NAV_ATTRIBUTES)
                .when(() -> CoreAudioHelper
                        .selectAttributesForVolumeGroupName(eq(NAV_GROUP_NAME)));
        when(mMockAudioManager.getMinVolumeIndexForAttributes(NAV_ATTRIBUTES))
                .thenReturn(NAV_MIN_INDEX);
        when(mMockAudioManager.getMaxVolumeIndexForAttributes(NAV_ATTRIBUTES))
                .thenReturn(NAV_MAX_INDEX);

        doReturn(OEM_ATTRIBUTES)
                .when(() -> CoreAudioHelper
                        .selectAttributesForVolumeGroupName(eq(OEM_GROUP_NAME)));
        when(mMockAudioManager.getMinVolumeIndexForAttributes(OEM_ATTRIBUTES))
                .thenReturn(OEM_MIN_INDEX);
        doReturn(OEM_GROUP_ID)
                .when(() -> CoreAudioHelper.getVolumeGroupIdForAudioAttributes(
                        OEM_ATTRIBUTES));
        when(mMockAudioManager.getMaxVolumeIndexForAttributes(OEM_ATTRIBUTES))
                .thenReturn(OEM_MAX_INDEX);

        mockCarGetPlatformVersion(UPSIDE_DOWN_CAKE_0);
    }

    @Before
    public void setUp() {
        setupMock();

        mMusicContext = new CarAudioContext(
                List.of(MEDIA_CONTEXT_INFO), /* useCoreAudioRouting= */ true);
        mNavContext = new CarAudioContext(
                List.of(NAV_CONTEXT_INFO), /* useCoreAudioRouting= */ true);
        mOemContext = new CarAudioContext(
                List.of(OEM_CONTEXT_INFO), /* useCoreAudioRouting= */ true);

        mMusicDeviceAttributes = new AudioDeviceAttributes(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                MUSIC_DEVICE_ADDRESS);
        SparseArray<CarAudioDeviceInfo> musicContextToDeviceInfo = new SparseArray<>();
        musicContextToDeviceInfo.put(MUSIC_STRATEGY_ID, mOemInfoMock);
        when(mOemInfoMock.getAddress()).thenReturn(MUSIC_DEVICE_ADDRESS);
        when(mOemInfoMock.getAudioDevice()).thenReturn(mMusicDeviceAttributes);
        mMusicCoreAudioVolumeGroup = new CoreAudioVolumeGroup(mMockAudioManager, mMusicContext,
                mSettingsMock, musicContextToDeviceInfo, PRIMARY_AUDIO_ZONE, ZONE_CONFIG_ID,
                MUSIC_CAR_GROUP_ID, MUSIC_GROUP_NAME, /* useCarVolumeGroupMute= */ false,
                MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE, MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);

        SparseArray<CarAudioDeviceInfo> navContextToDeviceInfo = new SparseArray<>();
        navContextToDeviceInfo.put(NAV_STRATEGY_ID, mNavInfoMock);
        when(mNavInfoMock.getAddress()).thenReturn(NAV_DEVICE_ADDRESS);
        mNavCoreAudioVolumeGroup = new CoreAudioVolumeGroup(mMockAudioManager, mNavContext,
                mSettingsMock, navContextToDeviceInfo, PRIMARY_AUDIO_ZONE, ZONE_CONFIG_ID,
                NAV_CAR_GROUP_ID, NAV_GROUP_NAME, /* useCarVolumeGroupMute= */ false,
                MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE, MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);

        SparseArray<CarAudioDeviceInfo> oemContextToDeviceInfo = new SparseArray<>();
        oemContextToDeviceInfo.put(OEM_STRATEGY_ID, mOemInfoMock);
        when(mOemInfoMock.getAddress()).thenReturn(OEM_DEVICE_ADDRESS);
        mOemCoreAudioVolumeGroup = new CoreAudioVolumeGroup(mMockAudioManager, mOemContext,
                mSettingsMock, oemContextToDeviceInfo,
                PRIMARY_AUDIO_ZONE, ZONE_CONFIG_ID, OEM_CAR_GROUP_ID, OEM_GROUP_NAME,
                /* useCarVolumeGroupMute= */ false, MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE,
                MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);
    }

    @Test
    public void getMaxIndex() {
        expectWithMessage("Initial gain index").that(mMusicCoreAudioVolumeGroup.getMaxGainIndex())
                .isEqualTo(MUSIC_MAX_INDEX);
        expectWithMessage("Initial gain index").that(mNavCoreAudioVolumeGroup.getMaxGainIndex())
                .isEqualTo(NAV_MAX_INDEX);
        expectWithMessage("Initial gain index").that(mOemCoreAudioVolumeGroup.getMaxGainIndex())
                .isEqualTo(OEM_MAX_INDEX);
    }

    @Test
    public void getMinIndex() {
        expectWithMessage("Initial gain index").that(mMusicCoreAudioVolumeGroup.getMinGainIndex())
                .isEqualTo(MUSIC_MIN_INDEX);
        expectWithMessage("Initial gain index").that(mNavCoreAudioVolumeGroup.getMinGainIndex())
                .isEqualTo(NAV_MIN_INDEX);
        expectWithMessage("Initial gain index").that(mOemCoreAudioVolumeGroup.getMinGainIndex())
                .isEqualTo(OEM_MIN_INDEX);
    }

    @Test
    public void checkIndexRange() {
        expectWithMessage("check inside range").that(mMusicCoreAudioVolumeGroup.isValidGainIndex(
                        (MUSIC_MAX_INDEX + MUSIC_MIN_INDEX) / 2))
                .isTrue();
        expectWithMessage("check outside range")
                .that(mMusicCoreAudioVolumeGroup.isValidGainIndex(MUSIC_MIN_INDEX - 1))
                .isFalse();
        expectWithMessage("check outside range")
                .that(mMusicCoreAudioVolumeGroup.isValidGainIndex(MUSIC_MAX_INDEX + 1))
                .isFalse();
    }

    @Test
    public void setAndGetIndex() {
        expectWithMessage("Initial music am last audible gain index")
                .that(mMusicCoreAudioVolumeGroup.getAmLastAudibleIndex())
                .isEqualTo(MUSIC_AM_INIT_INDEX);
        expectWithMessage("Initial music am gain index")
                .that(mMusicCoreAudioVolumeGroup.getAmCurrentGainIndex())
                .isEqualTo(MUSIC_AM_INIT_INDEX);

        int index = (MUSIC_MAX_INDEX + MUSIC_MIN_INDEX) / 2;
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(index);

        verify(mMockAudioManager).setVolumeGroupVolumeIndex(
                eq(MUSIC_GROUP_ID), eq(index), anyInt());
    }

    @Test
    public void isMuted_defaultIsFalse() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_AM_INIT_INDEX);
        int initialIndex = mMusicCoreAudioVolumeGroup.getCurrentGainIndex();

        expectWithMessage("Initial gain index").that(initialIndex).isEqualTo(MUSIC_AM_INIT_INDEX);
        expectWithMessage("Initial mute state")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void muteUnmute() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_AM_INIT_INDEX);
        mMusicCoreAudioVolumeGroup.setMute(true);

        verify(() -> AudioManagerHelper.adjustVolumeGroupVolume(any(),
                eq(MUSIC_GROUP_ID), eq(AudioManager.ADJUST_MUTE), anyInt()));
        expectWithMessage("Car volume group mute state after group muted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isTrue();
        expectWithMessage("Index after group muted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex()).isEqualTo(0);

        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);

        mMusicCoreAudioVolumeGroup.setMute(false);

        verify(() -> AudioManagerHelper.adjustVolumeGroupVolume(any(),
                eq(MUSIC_GROUP_ID), eq(AudioManager.ADJUST_UNMUTE), anyInt()));
        expectWithMessage("Car volume group mute state after group unmuted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Index after group unmuted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MUSIC_AM_INIT_INDEX);
    }

    @Test
    public void muteByVolumeZeroKeepMuted() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_MIN_INDEX);

        verify(mMockAudioManager).setVolumeGroupVolumeIndex(
                eq(MUSIC_GROUP_ID), eq(MUSIC_MIN_INDEX), anyInt());
        expectWithMessage("Car volume group initial mute state")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();

        mMusicCoreAudioVolumeGroup.setMute(true);

        verify(() -> AudioManagerHelper.adjustVolumeGroupVolume(any(),
                eq(MUSIC_GROUP_ID), eq(AudioManager.ADJUST_MUTE), anyInt()));
        expectWithMessage("Car volume group mute state after group muted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isTrue();
        expectWithMessage("Index after group muted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex()).isEqualTo(MUSIC_MIN_INDEX);

        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);

        mMusicCoreAudioVolumeGroup.setMute(false);

        verify(() -> AudioManagerHelper.adjustVolumeGroupVolume(any(),
                eq(MUSIC_GROUP_ID), eq(AudioManager.ADJUST_UNMUTE), anyInt()));
        expectWithMessage("Car volume group mute state after group unmuted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Index after group unmuted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex()).isEqualTo(MUSIC_MIN_INDEX);
    }

    @Test
    public void audioManagerIndexSynchronization() {
        int index = (MUSIC_MAX_INDEX + MUSIC_MIN_INDEX) / 2;
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(index);

        verify(mMockAudioManager).setVolumeGroupVolumeIndex(eq(MUSIC_GROUP_ID), eq(index),
                anyInt());

        int amIndex = MUSIC_AM_INIT_INDEX + 2;
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES)).thenReturn(amIndex);
        int flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Reported event flags after am callback")
                .that(flags).isEqualTo(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Index after am callback")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex())
                .isEqualTo(amIndex);

        // Double sync is a no-op
        flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Reported event flags after double am callback").that(flags).isEqualTo(0);
        expectWithMessage("Index after double am callback")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex()).isEqualTo(amIndex);
    }

    @Test
    public void audioManagerMuteStateSynchronization() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);
        // Mute event reported by AudioManager
        int flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am group muted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isTrue();
        expectWithMessage("Reported event flags after am group muted")
                .that(flags).isEqualTo(EVENT_TYPE_MUTE_CHANGED);

        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(false);
        // Unmute event from AM reported
        flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am group unmuted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Reported event flags after am group unmuted")
                .that(flags).isEqualTo(EVENT_TYPE_MUTE_CHANGED);
    }

    @Test
    public void audioManagerMuteStateSynchronization_withIndexChange() {
        int amIndex = MUSIC_AM_INIT_INDEX;
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(amIndex);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);
        // Mute event reported by AudioManager
        int flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am group muted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isTrue();
        expectWithMessage("Reported event flags after am group muted")
                .that(flags).isEqualTo(EVENT_TYPE_MUTE_CHANGED);

        // Unmute event with index change reported by AudioManager
        amIndex += 1;
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES)).thenReturn(amIndex);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(amIndex);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(false);

        flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am group unmuted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Reported event flags after am group unmuted")
                .that(flags).isEqualTo(EVENT_TYPE_MUTE_CHANGED
                        | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Index after am group unmuted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex()).isEqualTo(amIndex);
    }

    @Test
    public void audioManagerMuteStateWithVolumeZeroSynchronization() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);
        // Mute event at volume zero reported by AudioManager
        int flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am group muted by volume zero")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Reported event flags after am group muted by volume zero")
                .that(flags).isEqualTo(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);

        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(false);
        // Unmute event reported by AudioManager
        flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am group unmuted by volume zero")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Reported event flags after am group unmuted").that(flags).isEqualTo(0);
        expectWithMessage("Index after am group unmuted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MUSIC_MIN_INDEX);
    }

    @Test
    public void audioManagerMutedAndUnmutedAtVolumeZeroSynchronization() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);
        // Mute event reported by AudioManager
        mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(false);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_MIN_INDEX);

        // Unmute (at volume zero) event reported by AudioManager
        int flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group muted state after am unmuted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Event flags reported after am unmuted")
                .that(flags).isEqualTo(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED
                        | EVENT_TYPE_MUTE_CHANGED);
        expectWithMessage("Index after am unmuted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MUSIC_MIN_INDEX);
    }

    @Test
    public void audioManagerMutedAtVolumeZeroAndUnmutedAtNonZeroSynchronization() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);
        // Mute event at volume zero reported by AudioManager
        int flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        // Mute by volume 0 is not seen as muted from CarAudioManager api
        expectWithMessage("Car volume group muted state after am muted by zero")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Event flags reported after am muted by zero")
                .that(flags).isEqualTo(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);

        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_AM_INIT_INDEX + 1);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(false);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
               .thenReturn(MUSIC_AM_INIT_INDEX + 1);
        // Unmute event (with non zero index) reported by AudioManager
        flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group muted state after am unmuted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Event flags reported after am unmuted")
                .that(flags).isEqualTo(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Index after am unmuted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MUSIC_AM_INIT_INDEX + 1);
    }

    @Test
    public void audioManagerMuted_thenMutedAtVolumeZeroAndUnmutedAtNonZeroSynchronization() {
        mMusicCoreAudioVolumeGroup.setCurrentGainIndex(MUSIC_AM_INIT_INDEX);
        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_MIN_INDEX);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_MIN_INDEX + 1);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(true);
        // Mute is reported by AudioManager
        mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_MIN_INDEX);

        // Muted by volume 0 is now reported by AudioManager
        int flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am muted by 0 while already muted")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isTrue();
        expectWithMessage("Event flags reported after am muted by 0 while already muted")
                .that(flags).isEqualTo(0);

        when(mMockAudioManager.getVolumeIndexForAttributes(MUSIC_ATTRIBUTES))
                .thenReturn(MUSIC_AM_INIT_INDEX + 1);
        when(mMockAudioManager.isVolumeGroupMuted(MUSIC_GROUP_ID)).thenReturn(false);
        when(mMockAudioManager.getLastAudibleVolumeForVolumeGroup(MUSIC_GROUP_ID))
                .thenReturn(MUSIC_AM_INIT_INDEX + 1);
        // Unmute event (with non zero index) reported by AudioManager
        flags = mMusicCoreAudioVolumeGroup.onAudioVolumeGroupChanged(/* flags= */ 0);

        expectWithMessage("Car volume group mute state after am unmuted ")
                .that(mMusicCoreAudioVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Event flags reported after am muted")
                .that(flags).isEqualTo(EVENT_TYPE_MUTE_CHANGED
                        | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Index after am muted")
                .that(mMusicCoreAudioVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MUSIC_AM_INIT_INDEX + 1);
    }

    @Test
    public void updateDevices_withCoreAudioRoutingDisabled() {
        boolean useCoreAudioRouting = false;

        mMusicCoreAudioVolumeGroup.updateDevices(useCoreAudioRouting);

        verify(mMockAudioManager).setPreferredDeviceForStrategy(MUSIC_STRATEGY,
                mMusicDeviceAttributes);
    }

    @Test
    public void updateDevices_withCoreAudioRoutingEnabled() {
        boolean useCoreAudioRouting = true;

        mMusicCoreAudioVolumeGroup.updateDevices(useCoreAudioRouting);

        verify(mMockAudioManager, never()).setPreferredDeviceForStrategy(MUSIC_STRATEGY,
                mMusicDeviceAttributes);
    }
}
