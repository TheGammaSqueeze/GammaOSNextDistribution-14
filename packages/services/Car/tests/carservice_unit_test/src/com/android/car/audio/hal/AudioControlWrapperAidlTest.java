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

package com.android.car.audio.hal;

import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.os.IBinder.DeathRecipient;

import static com.android.car.audio.CarHalAudioUtils.usageToMetadata;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_DUCKING;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_FOCUS;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_FOCUS_WITH_METADATA;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.audio.policy.configuration.V7_0.AudioUsage;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.DuckingInfo;
import android.hardware.automotive.audiocontrol.IAudioControl;
import android.hardware.automotive.audiocontrol.IAudioGainCallback;
import android.hardware.automotive.audiocontrol.IFocusListener;
import android.hardware.automotive.audiocontrol.MutingInfo;
import android.hardware.automotive.audiocontrol.Reasons;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.audio.CarAudioContext;
import com.android.car.audio.CarAudioGainConfigInfo;
import com.android.car.audio.CarAudioZone;
import com.android.car.audio.CarDuckingInfo;
import com.android.car.audio.CarHalAudioUtils;
import com.android.car.audio.hal.AudioControlWrapper.AudioControlDeathRecipient;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public final class AudioControlWrapperAidlTest extends AbstractExtendedMockitoTestCase {
    private static final float FADE_VALUE = 5;
    private static final float BALANCE_VALUE = 6;
    private static final int USAGE = USAGE_MEDIA;
    private static final String USAGE_NAME = AudioUsage.AUDIO_USAGE_MEDIA.toString();
    private static final int ZONE_ID = 2;
    private static final int PRIMARY_ZONE_ID = 0;
    private static final int SECONDARY_ZONE_ID = 1;
    private static final int FOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN;
    private static final String PRIMARY_MUSIC_ADDRESS = "primary music";
    private static final String PRIMARY_NAVIGATION_ADDRESS = "primary navigation";
    private static final String PRIMARY_CALL_ADDRESS = "primary call";
    private static final String PRIMARY_NOTIFICATION_ADDRESS = "primary notification";
    private static final String SECONDARY_MUSIC_ADDRESS = "secondary music";
    private static final String SECONDARY_NAVIGATION_ADDRESS = "secondary navigation";
    private static final String SECONDARY_CALL_ADDRESS = "secondary call";
    private static final String SECONDARY_NOTIFICATION_ADDRESS = "secondary notification";
    private static final PlaybackTrackMetadata METADATA = usageToMetadata(USAGE);

    private static final int AIDL_AUDIO_CONTROL_VERSION_1 = 1;
    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    public static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    public static final AudioAttributes TEST_NOTIFICATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION);

    public static final int TEST_MEDIA_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE);
    public static final int TEST_NOTIFICATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_NOTIFICATION_ATTRIBUTE);

    @Mock
    private IBinder mBinder;

    @Mock
    private IAudioControl mAudioControl;

    @Mock
    private HalAudioGainCallback mHalAudioGainCallback;

    @Mock
    private AudioControlDeathRecipient mDeathRecipient;

    private AudioControlWrapperAidl mAudioControlWrapperAidl;
    private MutingInfo mPrimaryZoneMutingInfo;
    private MutingInfo mSecondaryZoneMutingInfo;

    public AudioControlWrapperAidlTest() {
        super(AudioControlWrapperAidl.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AudioControlWrapperAidl.class);
    }

    @Before
    public void setUp() {
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mAudioControl);
        doReturn(mBinder).when(AudioControlWrapperAidl::getService);
        mAudioControlWrapperAidl = new AudioControlWrapperAidl(mBinder);
        mPrimaryZoneMutingInfo = new MutingInfoBuilder(PRIMARY_ZONE_ID)
                .setMutedAddresses(PRIMARY_MUSIC_ADDRESS, PRIMARY_NAVIGATION_ADDRESS)
                .setUnMutedAddresses(PRIMARY_CALL_ADDRESS, PRIMARY_NOTIFICATION_ADDRESS)
                .build();

        mSecondaryZoneMutingInfo = new MutingInfoBuilder(SECONDARY_ZONE_ID)
                .setMutedAddresses(SECONDARY_MUSIC_ADDRESS, SECONDARY_NAVIGATION_ADDRESS)
                .setUnMutedAddresses(SECONDARY_CALL_ADDRESS, SECONDARY_NOTIFICATION_ADDRESS)
                .build();
    }

    @Test
    public void setFadeTowardFront_succeeds() throws Exception {
        mAudioControlWrapperAidl.setFadeTowardFront(FADE_VALUE);

        verify(mAudioControl).setFadeTowardFront(FADE_VALUE);
    }

    @Test
    public void setBalanceTowardRight_succeeds() throws Exception {
        mAudioControlWrapperAidl.setBalanceTowardRight(BALANCE_VALUE);

        verify(mAudioControl).setBalanceTowardRight(BALANCE_VALUE);
    }

    @Test
    public void supportsFeature_forAudioFocus_returnsTrue() {
        assertThat(mAudioControlWrapperAidl.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_FOCUS))
                .isTrue();
    }

    @Test
    public void supportsFeature_forAudioDucking_returnsTrue() {
        assertThat(mAudioControlWrapperAidl.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_DUCKING))
                .isTrue();
    }

    @Test
    public void supportsFeature_forAudioMuting_returnsTrue() {
        assertThat(mAudioControlWrapperAidl
                .supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING)).isTrue();
    }

    @Test
    public void supportsFeature_forUnknownFeature_returnsFalse() {
        assertThat(mAudioControlWrapperAidl.supportsFeature(-1)).isFalse();
    }

    @Test
    public void registerFocusListener_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        mAudioControlWrapperAidl.registerFocusListener(mockListener);

        verify(mAudioControl).registerFocusListener(any(IFocusListener.class));
    }

    @Test
    public void registerFocusListener_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControl)
                .registerFocusListener(any(IFocusListener.class));
        HalFocusListener mockListener = mock(HalFocusListener.class);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperAidl.registerFocusListener(mockListener));

        assertWithMessage("Exception thrown when registerFocusListener failed")
                .that(thrown).hasMessageThat()
                .contains("IAudioControl#registerFocusListener failed");
    }

    @Test
    public void requestAudioFocus_forFocusListenerWrapper_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        ArgumentCaptor<IFocusListener.Stub> captor =
                ArgumentCaptor.forClass(IFocusListener.Stub.class);
        mAudioControlWrapperAidl.registerFocusListener(mockListener);
        verify(mAudioControl).registerFocusListener(captor.capture());

        captor.getValue().requestAudioFocus(USAGE_NAME, ZONE_ID, FOCUS_GAIN);

        verify(mockListener).requestAudioFocus(METADATA, ZONE_ID, FOCUS_GAIN);
    }

    @Test
    public void requestAudioFocusWithMetaData_forFocusListenerWrapper_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        ArgumentCaptor<IFocusListener.Stub> captor =
                ArgumentCaptor.forClass(IFocusListener.Stub.class);
        mAudioControlWrapperAidl.registerFocusListener(mockListener);
        verify(mAudioControl).registerFocusListener(captor.capture());

        captor.getValue().requestAudioFocusWithMetaData(METADATA, ZONE_ID, FOCUS_GAIN);

        verify(mockListener).requestAudioFocus(METADATA, ZONE_ID, FOCUS_GAIN);
    }

    @Test
    public void abandonAudioFocus_forFocusListenerWrapper_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        ArgumentCaptor<IFocusListener.Stub> captor =
                ArgumentCaptor.forClass(IFocusListener.Stub.class);
        mAudioControlWrapperAidl.registerFocusListener(mockListener);
        verify(mAudioControl).registerFocusListener(captor.capture());

        captor.getValue().abandonAudioFocus(USAGE_NAME, ZONE_ID);

        verify(mockListener).abandonAudioFocus(METADATA, ZONE_ID);
    }

    @Test
    public void abandonAudioFocusWithMetaData_forFocusListenerWrapper_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        ArgumentCaptor<IFocusListener.Stub> captor =
                ArgumentCaptor.forClass(IFocusListener.Stub.class);
        mAudioControlWrapperAidl.registerFocusListener(mockListener);
        verify(mAudioControl).registerFocusListener(captor.capture());

        captor.getValue().abandonAudioFocusWithMetaData(METADATA, ZONE_ID);

        verify(mockListener).abandonAudioFocus(METADATA, ZONE_ID);
    }

    @Test
    public void onAudioFocusChange_succeeds() throws Exception {
        mAudioControlWrapperAidl.onAudioFocusChange(METADATA, ZONE_ID, FOCUS_GAIN);

        verify(mAudioControl).onAudioFocusChangeWithMetaData(METADATA, ZONE_ID, FOCUS_GAIN);
    }

    @Test
    public void onAudioFocusChange_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControl)
                .onAudioFocusChange(anyString(), anyInt(), anyInt());
        doThrow(new RemoteException()).when(mAudioControl)
                .onAudioFocusChangeWithMetaData(any(), anyInt(), anyInt());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperAidl.onAudioFocusChange(METADATA, ZONE_ID, FOCUS_GAIN));

        assertWithMessage("Exception thrown when onAudioFocusChange failed")
                .that(thrown).hasMessageThat()
                .contains("Failed to query IAudioControl#onAudioFocusChange");
    }

    @Test
    public void onAudioFocusChange_whenMetaDataApiUnimplemented_fallbacksOnUsageApi()
            throws Exception {
        doThrow(new RemoteException()).when(mAudioControl).onAudioFocusChangeWithMetaData(any(),
                anyInt(), anyInt());

        mAudioControlWrapperAidl.onAudioFocusChange(METADATA, ZONE_ID, FOCUS_GAIN);

        verify(mAudioControl).onAudioFocusChange(USAGE_NAME, ZONE_ID, FOCUS_GAIN);
    }

    @Test
    public void onDevicesToDuckChange_withNullDuckingInfo_throws() {
        assertThrows(NullPointerException.class,
                () -> mAudioControlWrapperAidl.onDevicesToDuckChange(null));
    }

    @Test
    public void onDevicesToDuckChange_callsHalWithDuckingInfo() throws Exception {
        CarDuckingInfo carDuckingInfo =
                new CarDuckingInfo(
                        ZONE_ID, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        mAudioControlWrapperAidl.onDevicesToDuckChange(List.of(carDuckingInfo));

        ArgumentCaptor<DuckingInfo[]> captor = ArgumentCaptor.forClass(DuckingInfo[].class);
        verify(mAudioControl).onDevicesToDuckChange(captor.capture());
        DuckingInfo[] duckingInfos = captor.getValue();
        assertThat(duckingInfos).hasLength(1);
    }

    @Test
    public void onDevicesToDuckChange_convertsUsagesToXsdStrings() throws Exception {
        List<AudioAttributes> audioAttributes = List.of(
                TEST_MEDIA_ATTRIBUTE, TEST_NOTIFICATION_ATTRIBUTE);
        CarDuckingInfo carDuckingInfo =
                new CarDuckingInfo(
                        ZONE_ID,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        CarHalAudioUtils.audioAttributesToMetadatas(audioAttributes,
                                generateAudioZoneMock()));

        mAudioControlWrapperAidl.onDevicesToDuckChange(List.of(carDuckingInfo));

        ArgumentCaptor<DuckingInfo[]> captor = ArgumentCaptor.forClass(DuckingInfo[].class);
        verify(mAudioControl).onDevicesToDuckChange(captor.capture());
        DuckingInfo duckingInfo = captor.getValue()[0];
        assertThat(duckingInfo.usagesHoldingFocus).asList()
                .containsExactly(AudioUsage.AUDIO_USAGE_MEDIA.toString(),
                        AudioUsage.AUDIO_USAGE_NOTIFICATION.toString());
    }

    @Test
    public void onDevicesToDuckChange_passesAlongAddressesToDuck() throws Exception {
        String mediaAddress = "media_bus";
        String navigationAddress = "navigation_bus";
        CarDuckingInfo carDuckingInfo =
                new CarDuckingInfo(
                        ZONE_ID,
                        Arrays.asList(mediaAddress, navigationAddress),
                        new ArrayList<>(),
                        new ArrayList<>());

        mAudioControlWrapperAidl.onDevicesToDuckChange(List.of(carDuckingInfo));

        ArgumentCaptor<DuckingInfo[]> captor = ArgumentCaptor.forClass(DuckingInfo[].class);
        verify(mAudioControl).onDevicesToDuckChange(captor.capture());
        DuckingInfo duckingInfo = captor.getValue()[0];
        assertThat(duckingInfo.deviceAddressesToDuck).asList()
                .containsExactly(mediaAddress, navigationAddress);
    }

    @Test
    public void onDevicesToDuckChange_passesAlongAddressesToUnduck() throws Exception {
        String notificationAddress = "notification_bus";
        String callAddress = "call_address";
        CarDuckingInfo carDuckingInfo =
                new CarDuckingInfo(
                        ZONE_ID,
                        new ArrayList<>(),
                        Arrays.asList(notificationAddress, callAddress),
                        new ArrayList<>());

        mAudioControlWrapperAidl.onDevicesToDuckChange(List.of(carDuckingInfo));

        ArgumentCaptor<DuckingInfo[]> captor = ArgumentCaptor.forClass(DuckingInfo[].class);
        verify(mAudioControl).onDevicesToDuckChange(captor.capture());
        DuckingInfo duckingInfo = captor.getValue()[0];
        assertThat(duckingInfo.deviceAddressesToUnduck).asList()
                .containsExactly(notificationAddress, callAddress);
    }

    @Test
    public void onDevicesToDuckChange_passesAlongZoneId() throws Exception {
        CarDuckingInfo carDuckingInfo =
                new CarDuckingInfo(
                        ZONE_ID, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        mAudioControlWrapperAidl.onDevicesToDuckChange(List.of(carDuckingInfo));

        ArgumentCaptor<DuckingInfo[]> captor = ArgumentCaptor.forClass(DuckingInfo[].class);
        verify(mAudioControl).onDevicesToDuckChange(captor.capture());
        DuckingInfo duckingInfo = captor.getValue()[0];
        assertThat(duckingInfo.zoneId).isEqualTo(ZONE_ID);
    }

    @Test
    public void onDevicesToDuckChange_multipleZones_passesADuckingInfoPerZone() throws Exception {
        CarDuckingInfo carDuckingInfo =
                new CarDuckingInfo(
                        ZONE_ID, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        CarDuckingInfo secondaryCarDuckingInfo =
                new CarDuckingInfo(
                        SECONDARY_ZONE_ID, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        mAudioControlWrapperAidl.onDevicesToDuckChange(List.of(carDuckingInfo,
                secondaryCarDuckingInfo));

        ArgumentCaptor<DuckingInfo[]> captor = ArgumentCaptor.forClass(DuckingInfo[].class);
        verify(mAudioControl).onDevicesToDuckChange(captor.capture());
        assertWithMessage("Number of ducking infos passed along")
                .that(captor.getValue().length).isEqualTo(2);
    }

    @Test
    public void linkToDeath_callsBinder() throws Exception {
        mAudioControlWrapperAidl.linkToDeath(null);

        verify(mBinder).linkToDeath(any(DeathRecipient.class), eq(0));
    }

    @Test
    public void linkToDeath_throws() throws Exception {
        doThrow(new RemoteException()).when(mBinder)
                .linkToDeath(any(DeathRecipient.class), anyInt());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperAidl.linkToDeath(null));

        assertWithMessage("Exception thrown when linkToDeath failed")
                .that(thrown).hasMessageThat()
                .contains("Call to IAudioControl#linkToDeath failed");
    }

    @Test
    public void unlinkToDeath_callsBinder() {
        mAudioControlWrapperAidl.linkToDeath(null);

        mAudioControlWrapperAidl.unlinkToDeath();

        verify(mBinder).unlinkToDeath(any(DeathRecipient.class), eq(0));
    }

    @Test
    public void binderDied_fetchesNewBinder() throws Exception {
        mAudioControlWrapperAidl.linkToDeath(null);

        ArgumentCaptor<DeathRecipient> captor = ArgumentCaptor.forClass(DeathRecipient.class);
        verify(mBinder).linkToDeath(captor.capture(), eq(0));
        IBinder.DeathRecipient deathRecipient = captor.getValue();

        deathRecipient.binderDied();

        ExtendedMockito.verify(() -> AudioControlWrapperAidl.getService());
    }

    @Test
    public void binderDied_relinksToDeath() throws Exception {
        mAudioControlWrapperAidl.linkToDeath(null);

        ArgumentCaptor<DeathRecipient> captor = ArgumentCaptor.forClass(DeathRecipient.class);
        verify(mBinder).linkToDeath(captor.capture(), eq(0));
        IBinder.DeathRecipient deathRecipient = captor.getValue();

        deathRecipient.binderDied();

        verify(mBinder, times(2)).linkToDeath(any(DeathRecipient.class), eq(0));
    }

    @Test
    public void binderDied_callsDeathRecipient() throws Exception {
        mAudioControlWrapperAidl.linkToDeath(mDeathRecipient);

        ArgumentCaptor<DeathRecipient> captor = ArgumentCaptor.forClass(
                IBinder.DeathRecipient.class);
        verify(mBinder).linkToDeath(captor.capture(), eq(0));
        IBinder.DeathRecipient deathRecipient = captor.getValue();

        deathRecipient.binderDied();

        verify(mDeathRecipient).serviceDied();
    }

    @Test
    public void onDevicesToMuteChange_withNullMutingInformation_Throws() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> mAudioControlWrapperAidl.onDevicesToMuteChange(null));

        assertWithMessage("NullPointerException thrown by onDevicesToMuteChange")
                .that(thrown).hasMessageThat().contains("not be null");
    }

    @Test
    public void onDevicesToMuteChange_withEmptyMutingInformation_Throws() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mAudioControlWrapperAidl.onDevicesToMuteChange(new ArrayList<>()));

        assertWithMessage("IllegalArgumentException thrown by onDevicesToMuteChange")
                .that(thrown).hasMessageThat().contains("not be empty");
    }

    @Test
    public void onDevicesToMuteChange_passesAlongZoneId() throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo));

        ArgumentCaptor<MutingInfo[]> captor = ArgumentCaptor.forClass(MutingInfo[].class);
        verify(mAudioControl).onDevicesToMuteChange(captor.capture());
        MutingInfo mutingInfo = captor.getValue()[0];
        assertWithMessage("Audio Zone Id")
                .that(mutingInfo.zoneId).isEqualTo(PRIMARY_ZONE_ID);
    }

    @Test
    public void onDevicesToMuteChange_passesAlongAddressesToMute() throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo));

        ArgumentCaptor<MutingInfo[]> captor = ArgumentCaptor.forClass(MutingInfo[].class);
        verify(mAudioControl).onDevicesToMuteChange(captor.capture());
        MutingInfo mutingInfo = captor.getValue()[0];
        assertWithMessage("Device Addresses to mute")
                .that(mutingInfo.deviceAddressesToMute).asList()
                .containsExactly(PRIMARY_MUSIC_ADDRESS, PRIMARY_NAVIGATION_ADDRESS);
    }

    @Test
    public void onDevicesToMuteChange_passesAlongAddressesToUnMute() throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo));

        ArgumentCaptor<MutingInfo[]> captor = ArgumentCaptor.forClass(MutingInfo[].class);
        verify(mAudioControl).onDevicesToMuteChange(captor.capture());
        MutingInfo mutingInfo = captor.getValue()[0];
        assertWithMessage("Device Address to Unmute")
                .that(mutingInfo.deviceAddressesToUnmute).asList()
                .containsExactly(PRIMARY_CALL_ADDRESS, PRIMARY_NOTIFICATION_ADDRESS);
    }

    @Test
    public void onDevicesToMuteChange_withMultipleZones_passesAlongCorrectSizeInfo()
            throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo,
                mSecondaryZoneMutingInfo));

        ArgumentCaptor<MutingInfo[]> captor = ArgumentCaptor.forClass(MutingInfo[].class);
        verify(mAudioControl).onDevicesToMuteChange(captor.capture());
        assertWithMessage("Muting Info size")
                .that(captor.getValue()).asList().hasSize(2);
    }

    @Test
    public void onDevicesToMuteChange_withMultipleZones_passesAlongCorrectZoneInfo()
            throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo,
                mSecondaryZoneMutingInfo));

        ArgumentCaptor<MutingInfo[]> captor = ArgumentCaptor.forClass(MutingInfo[].class);
        verify(mAudioControl).onDevicesToMuteChange(captor.capture());
        List<Integer> zoneIds = Arrays.stream(captor.getValue())
                .map(info -> info.zoneId).collect(Collectors.toList());
        assertWithMessage("Muting Zone Ids").that(zoneIds)
                .containsExactly(PRIMARY_ZONE_ID, SECONDARY_ZONE_ID);
    }

    @Test
    public void
            onDevicesToMuteChange_withMultipleZones_passesAlongCorrectAddressToMuteForPrimaryZone()
            throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo,
                mSecondaryZoneMutingInfo));

        MutingInfo info = verifyOnDevicesToMuteChangeCalled(PRIMARY_ZONE_ID);
        assertWithMessage("Primary Zone Device Addresses to mute")
                .that(info.deviceAddressesToMute).asList()
                .containsExactly(PRIMARY_MUSIC_ADDRESS, PRIMARY_NAVIGATION_ADDRESS);
    }

    @Test
    public void
            onDevicesToMuteChange_withMultiZones_passesAlongCorrectAddressToMuteForSecondaryZone()
            throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo,
                mSecondaryZoneMutingInfo));

        MutingInfo info = verifyOnDevicesToMuteChangeCalled(SECONDARY_ZONE_ID);
        assertWithMessage("Secondary Zone Device Addresses to mute")
                .that(info.deviceAddressesToMute).asList()
                .containsExactly(SECONDARY_MUSIC_ADDRESS, SECONDARY_NAVIGATION_ADDRESS);
    }

    @Test
    public void
            onDevicesToMuteChange_witMultipleZones_passesAlongCorrectAddressToUnMuteForPrimaryZone()
            throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo,
                mSecondaryZoneMutingInfo));

        MutingInfo info = verifyOnDevicesToMuteChangeCalled(PRIMARY_ZONE_ID);
        assertWithMessage("Primary Zone Device Addresses to un-mute")
                .that(info.deviceAddressesToUnmute).asList()
                .containsExactly(PRIMARY_CALL_ADDRESS, PRIMARY_NOTIFICATION_ADDRESS);
    }

    @Test
    public void
            onDevicesToMuteChange_witMultiZones_passesAlongCorrectAddressToUnMuteForSecondaryZone()
            throws Exception {
        mAudioControlWrapperAidl.onDevicesToMuteChange(ImmutableList.of(mPrimaryZoneMutingInfo,
                mSecondaryZoneMutingInfo));

        MutingInfo info = verifyOnDevicesToMuteChangeCalled(SECONDARY_ZONE_ID);

        assertWithMessage("Secondary Zone Device Addresses to un-mute")
                .that(info.deviceAddressesToUnmute).asList()
                .containsExactly(SECONDARY_CALL_ADDRESS, SECONDARY_NOTIFICATION_ADDRESS);
    }

    @Test
    public void supportsFeature_forAudioGainCallback_returnsTrue() throws Exception {
        when(mAudioControl.getInterfaceVersion()).thenReturn(AIDL_AUDIO_CONTROL_VERSION_1 + 1);
        assertThat(
                        mAudioControlWrapperAidl.supportsFeature(
                                AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK))
                .isTrue();

        when(mAudioControl.getInterfaceVersion()).thenReturn(AIDL_AUDIO_CONTROL_VERSION_1 + 4);
        assertThat(
                        mAudioControlWrapperAidl.supportsFeature(
                                AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK))
                .isTrue();
    }

    @Test
    public void supportsFeature_forAudioGainCallback_returnsFalse() throws Exception {
        when(mAudioControl.getInterfaceVersion()).thenReturn(AIDL_AUDIO_CONTROL_VERSION_1);
        assertThat(
                        mAudioControlWrapperAidl.supportsFeature(
                                AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK))
                .isFalse();

        when(mAudioControl.getInterfaceVersion()).thenReturn(0);
        assertThat(
                        mAudioControlWrapperAidl.supportsFeature(
                                AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK))
                .isFalse();
    }

    @Test
    public void supportsFeature_forAudioFocusWithMetadataThrowsException_returnFalse()
            throws Exception {
        doThrow(new RemoteException()).when(mAudioControl).getInterfaceVersion();

        assertThat(mAudioControlWrapperAidl
                .supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_FOCUS_WITH_METADATA)).isFalse();
    }

    @Test
    public void registerAudioGainCallback_succeeds() throws Exception {
        mAudioControlWrapperAidl.registerAudioGainCallback(mHalAudioGainCallback);
        verify(mAudioControl).registerGainCallback(any());
    }

    @Test
    public void registerAudioGainCallback_throws() throws Exception {
        doThrow(new RemoteException("D'OH!")).when(mAudioControl).registerGainCallback(any());

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                mAudioControlWrapperAidl.registerAudioGainCallback(
                                        mHalAudioGainCallback));

        assertWithMessage("IllegalStateException thrown by registerAudioGainCallback")
                .that(thrown)
                .hasMessageThat()
                .contains("IAudioControl#registerAudioGainCallback failed");
    }

    @Test
    public void registerAudioGainCallback_nullcallback_Throws() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                mAudioControlWrapperAidl.registerAudioGainCallback(
                                        /* gainCallback= */ null));

        assertWithMessage("NullPointerException thrown by registerAudioGainCallback")
                .that(thrown)
                .hasMessageThat()
                .contains("Audio Gain Callback can not be null");
    }

    @Test
    public void onAudioDeviceGainsChanged_succeeds() throws Exception {
        ArgumentCaptor<IAudioGainCallback.Stub> captor =
                ArgumentCaptor.forClass(IAudioGainCallback.Stub.class);

        mAudioControlWrapperAidl.registerAudioGainCallback(mHalAudioGainCallback);
        verify(mAudioControl).registerGainCallback(captor.capture());

        int[] halReasons = {Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING};
        List<Integer> reasons = Arrays.stream(halReasons).boxed().collect(Collectors.toList());

        AudioGainConfigInfo gainInfo1 = new AudioGainConfigInfo();
        gainInfo1.zoneId = PRIMARY_ZONE_ID;
        gainInfo1.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo1.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo1);
        AudioGainConfigInfo gainInfo2 = new AudioGainConfigInfo();
        gainInfo2.zoneId = SECONDARY_ZONE_ID;
        gainInfo2.devicePortAddress = SECONDARY_NAVIGATION_ADDRESS;
        gainInfo2.volumeIndex = 999;
        CarAudioGainConfigInfo carGainInfo2 = new CarAudioGainConfigInfo(gainInfo2);

        AudioGainConfigInfo[] gains = new AudioGainConfigInfo[] {gainInfo1, gainInfo2};

        List<CarAudioGainConfigInfo> carGains = List.of(carGainInfo1, carGainInfo2);

        captor.getValue().onAudioDeviceGainsChanged(halReasons, gains);

        ArgumentCaptor<List<CarAudioGainConfigInfo>> captorGains =
                ArgumentCaptor.forClass(List.class);
        verify(mHalAudioGainCallback).onAudioDeviceGainsChanged(eq(reasons), captorGains.capture());

        assertThat(captorGains.getValue()).containsExactlyElementsIn(carGains);
    }

    @Test
    public void onAudioDeviceGainsChanged_invalidReasons() throws Exception {
        ArgumentCaptor<IAudioGainCallback.Stub> captor =
                ArgumentCaptor.forClass(IAudioGainCallback.Stub.class);

        mAudioControlWrapperAidl.registerAudioGainCallback(mHalAudioGainCallback);
        verify(mAudioControl).registerGainCallback(captor.capture());

        int[] halReasons = {-1, 1999, 666};
        List<Integer> emptyReasons = new ArrayList<>();

        AudioGainConfigInfo gainInfo1 = new AudioGainConfigInfo();
        gainInfo1.zoneId = PRIMARY_ZONE_ID;
        gainInfo1.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo1.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo1);
        AudioGainConfigInfo gainInfo2 = new AudioGainConfigInfo();
        gainInfo2.zoneId = SECONDARY_ZONE_ID;
        gainInfo2.devicePortAddress = SECONDARY_NAVIGATION_ADDRESS;
        gainInfo2.volumeIndex = 999;
        CarAudioGainConfigInfo carGainInfo2 = new CarAudioGainConfigInfo(gainInfo2);

        AudioGainConfigInfo[] gains = new AudioGainConfigInfo[] {gainInfo1, gainInfo2};

        List<CarAudioGainConfigInfo> carGains = List.of(carGainInfo1, carGainInfo2);

        captor.getValue().onAudioDeviceGainsChanged(halReasons, gains);

        ArgumentCaptor<List<CarAudioGainConfigInfo>> captorGains =
                ArgumentCaptor.forClass(List.class);
        verify(mHalAudioGainCallback)
                .onAudioDeviceGainsChanged(eq(emptyReasons), captorGains.capture());

        assertThat(captorGains.getValue()).containsExactlyElementsIn(carGains);
    }

    @Test
    public void onAudioDeviceGainsChanged_invalidReasonsAmongValidReasons() throws Exception {
        ArgumentCaptor<IAudioGainCallback.Stub> captor =
                ArgumentCaptor.forClass(IAudioGainCallback.Stub.class);

        mAudioControlWrapperAidl.registerAudioGainCallback(mHalAudioGainCallback);
        verify(mAudioControl).registerGainCallback(captor.capture());

        int[] halReasons = {-1, Reasons.REMOTE_MUTE, 1999, 666, Reasons.NAV_DUCKING};
        List<Integer> validReasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);

        AudioGainConfigInfo gainInfo1 = new AudioGainConfigInfo();
        gainInfo1.zoneId = PRIMARY_ZONE_ID;
        gainInfo1.devicePortAddress = PRIMARY_MUSIC_ADDRESS;
        gainInfo1.volumeIndex = 666;
        CarAudioGainConfigInfo carGainInfo1 = new CarAudioGainConfigInfo(gainInfo1);
        AudioGainConfigInfo gainInfo2 = new AudioGainConfigInfo();
        gainInfo2.zoneId = SECONDARY_ZONE_ID;
        gainInfo2.devicePortAddress = SECONDARY_NAVIGATION_ADDRESS;
        gainInfo2.volumeIndex = 999;
        CarAudioGainConfigInfo carGainInfo2 = new CarAudioGainConfigInfo(gainInfo2);

        AudioGainConfigInfo[] gains = new AudioGainConfigInfo[] {gainInfo1, gainInfo2};

        List<CarAudioGainConfigInfo> carGains = List.of(carGainInfo1, carGainInfo2);

        captor.getValue().onAudioDeviceGainsChanged(halReasons, gains);

        ArgumentCaptor<List<CarAudioGainConfigInfo>> captorGains =
                ArgumentCaptor.forClass(List.class);
        verify(mHalAudioGainCallback)
                .onAudioDeviceGainsChanged(eq(validReasons), captorGains.capture());

        assertThat(captorGains.getValue()).containsExactlyElementsIn(carGains);
    }

    private static CarAudioZone generateAudioZoneMock() {
        CarAudioZone mockZone = mock(CarAudioZone.class);
        when(mockZone.getAddressForContext(TEST_MEDIA_CONTEXT_ID))
                .thenReturn(PRIMARY_MUSIC_ADDRESS);
        when(mockZone.getAddressForContext(TEST_NOTIFICATION_CONTEXT_ID))
                .thenReturn(PRIMARY_NOTIFICATION_ADDRESS);

        when(mockZone.getCarAudioContext()).thenReturn(TEST_CAR_AUDIO_CONTEXT);

        return mockZone;
    }

    private MutingInfo verifyOnDevicesToMuteChangeCalled(int audioZoneId) throws Exception {
        ArgumentCaptor<MutingInfo[]> captor = ArgumentCaptor.forClass(MutingInfo[].class);
        verify(mAudioControl).onDevicesToMuteChange(captor.capture());
        for (MutingInfo info : captor.getValue()) {
            if (info.zoneId != audioZoneId) {
                continue;
            }
            return info;
        }
        return null;
    }

    private static final class MutingInfoBuilder {
        private final int mZoneId;
        private String[] mAddressesToMute;
        private String[] mAddressesToUnMute;

        MutingInfoBuilder(int zoneId) {
            mZoneId = zoneId;
        }

        MutingInfoBuilder setMutedAddresses(String... addressesToMute) {
            mAddressesToMute = addressesToMute;
            return this;
        }

        MutingInfoBuilder setUnMutedAddresses(String... addressesToUnMute) {
            mAddressesToUnMute = addressesToUnMute;
            return this;
        }

        MutingInfo build() {
            MutingInfo info = new MutingInfo();
            info.zoneId = mZoneId;
            info.deviceAddressesToMute = mAddressesToMute;
            info.deviceAddressesToUnmute = mAddressesToUnMute;
            return info;
        }
    }
}
