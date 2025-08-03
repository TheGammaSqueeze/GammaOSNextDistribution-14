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

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.android.car.audio.CarAudioContext.getAudioAttributeFromUsage;
import static com.android.car.audio.CarHalAudioUtils.audioAttributeToMetadata;
import static com.android.car.audio.CarHalAudioUtils.usageToMetadata;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.car.test.AbstractExpectableTestCase;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.audio.CarAudioContext;
import com.android.car.audio.CarAudioPlaybackMonitor;
import com.android.car.audio.CoreAudioRoutingUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class HalAudioFocusTest extends AbstractExpectableTestCase {
    private static final int[] AUDIO_ZONE_IDS = {0, 1, 2, 3};
    private static final int ZONE_ID = 0;
    private static final int SECOND_ZONE_ID = 1;
    private static final int INVALID_ZONE_ID = 5;
    private static final AudioAttributes ATTR_MEDIA = getAudioAttributeFromUsage(USAGE_MEDIA);
    private static final AudioAttributes ATTR_ASSISTANCE_NAVIGATION_GUIDANCE =
            getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    private static final AudioAttributes ATTR_NOTIFICATION =
            getAudioAttributeFromUsage(USAGE_NOTIFICATION);
    private static final PlaybackTrackMetadata METADATA_MEDIA = usageToMetadata(USAGE_MEDIA);
    private static final PlaybackTrackMetadata METADATA_MOVIE = audioAttributeToMetadata(
            CoreAudioRoutingUtils.MOVIE_ATTRIBUTES);
    private static final PlaybackTrackMetadata METADATA_ALARM = usageToMetadata(USAGE_ALARM);
    private static final PlaybackTrackMetadata METADATA_NOTIFICATION =
            usageToMetadata(USAGE_NOTIFICATION);
    private static final PlaybackTrackMetadata METADATA_ASSISTANCE_NAVIGATION_GUIDANCE =
            usageToMetadata(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private AudioManager mMockAudioManager;
    @Mock
    private AudioControlWrapper mAudioControlWrapper;
    @Mock
    private CarAudioPlaybackMonitor mCarAudioPlaybackMonitor;
    @Captor
    private ArgumentCaptor<List<AudioAttributes>> mAudioAttributesCaptor;
    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private HalAudioFocus mHalAudioFocus;

    @Before
    public void setUp() {
        mHalAudioFocus = new HalAudioFocus(mMockAudioManager, mAudioControlWrapper,
                mCarAudioPlaybackMonitor, TEST_CAR_AUDIO_CONTEXT, AUDIO_ZONE_IDS);
    }

    @Test
    public void registerFocusListener_succeeds() {
        mHalAudioFocus.registerFocusListener();

        verify(mAudioControlWrapper).registerFocusListener(mHalAudioFocus);
    }

    @Test
    public void unregisterFocusListener_succeeds() {
        mHalAudioFocus.registerFocusListener();

        mHalAudioFocus.unregisterFocusListener();

        verify(mAudioControlWrapper).unregisterFocusListener();
    }

    @Test
    public void requestAudioFocus_notifiesHalOfFocusChange() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_REQUEST_GRANTED);
        expectWithMessage("Playback audio attributes with audio focus requested")
                .that(getCarAudioPlaybackMonitorAttributes(ZONE_ID)).containsExactly(ATTR_MEDIA);
    }

    @Test
    public void requestAudioFocus_specifiesUsage() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        AudioFocusRequest actualRequest = getLastRequest();
        assertThat(actualRequest.getAudioAttributes().getUsage()).isEqualTo(USAGE_MEDIA);
    }

    @Test
    public void requestAudioFocus_specifiesFocusGain() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        AudioFocusRequest actualRequest = getLastRequest();
        assertThat(actualRequest.getFocusGain()).isEqualTo(AUDIOFOCUS_GAIN);
    }

    @Test
    public void requestAudioFocus_specifiesZoneId() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        AudioFocusRequest actualRequest = getLastRequest();
        Bundle bundle = actualRequest.getAudioAttributes().getBundle();
        assertThat(bundle.getInt(CarAudioManager.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID)).isEqualTo(
                ZONE_ID);
    }

    @Test
    public void requestAudioFocus_providesFocusChangeListener() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        AudioFocusRequest actualRequest = getLastRequest();
        assertThat(actualRequest.getOnAudioFocusChangeListener()).isNotNull();
    }

    @Test
    public void requestAudioFocus_withNullPlaybackMonitor() {
        HalAudioFocus halAudioFocus = new HalAudioFocus(mMockAudioManager, mAudioControlWrapper,
                /* carAudioPlaybackMonitor= */ null, TEST_CAR_AUDIO_CONTEXT, AUDIO_ZONE_IDS);
        whenAnyFocusRequestGranted();

        halAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_REQUEST_GRANTED);
        AudioFocusRequest actualRequest = getLastRequest();
        expectWithMessage("Audio focus request usage with null playback monitor")
                .that(actualRequest.getAudioAttributes().getUsage()).isEqualTo(USAGE_MEDIA);
        expectWithMessage("Audio focus request gain with null playback monitor")
                .that(actualRequest.getFocusGain()).isEqualTo(AUDIOFOCUS_GAIN);
        Bundle bundle = actualRequest.getAudioAttributes().getBundle();
        expectWithMessage("Audio focus request zone Id with null playback monitor")
                .that(bundle.getInt(CarAudioManager.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID))
                .isEqualTo(ZONE_ID);
    }

    @Test
    public void requestAudioFocus_withInvalidZone_throws() {
        whenAnyFocusRequestGranted();

        assertThrows(IllegalArgumentException.class,
                () -> mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, INVALID_ZONE_ID,
                        AUDIOFOCUS_GAIN));
    }

    @Test
    public void requestAudioFocus_withSameZoneAndUsage_keepsExistingRequest() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest firstRequest = getLastRequest();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(firstRequest);
        expectWithMessage("Playback audio attributes with the same zone and usage focuses")
                .that(getCarAudioPlaybackMonitorAttributes(ZONE_ID)).containsExactly(ATTR_MEDIA);
    }

    @Test
    public void requestAudioFocus_withSameZoneAndUsage_notifiesHalOfExistingRequestStatus() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest firstRequest = getLastRequest();
        OnAudioFocusChangeListener listener = firstRequest.getOnAudioFocusChangeListener();
        listener.onAudioFocusChange(AUDIOFOCUS_LOSS_TRANSIENT);

        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_LOSS_TRANSIENT);

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mAudioControlWrapper, times(2)).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_LOSS_TRANSIENT);
    }

    @Test
    public void requestAudioFocus_withSameContext_notifiesHalOfExistingRequestStatus() {
        CarAudioContext carAudioContextUsingCoreRouting = spy(new CarAudioContext(
                CoreAudioRoutingUtils.getCarAudioContextInfos(), /* useCoreAudioRouting= */ true));
        doReturn(CoreAudioRoutingUtils.MUSIC_STRATEGY_ID).when(carAudioContextUsingCoreRouting)
                .getContextForAudioAttribute(ATTR_MEDIA);
        doReturn(CoreAudioRoutingUtils.MUSIC_STRATEGY_ID).when(carAudioContextUsingCoreRouting)
                .getContextForAudioAttribute(CoreAudioRoutingUtils.MOVIE_ATTRIBUTES);
        HalAudioFocus halAudioFocus = new HalAudioFocus(mMockAudioManager, mAudioControlWrapper,
                mCarAudioPlaybackMonitor, carAudioContextUsingCoreRouting, AUDIO_ZONE_IDS);
        whenAnyFocusRequestGranted();
        halAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest firstRequest = getLastRequest();
        OnAudioFocusChangeListener listener = firstRequest.getOnAudioFocusChangeListener();
        listener.onAudioFocusChange(AUDIOFOCUS_LOSS_TRANSIENT);
        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_LOSS_TRANSIENT);

        halAudioFocus.requestAudioFocus(METADATA_MOVIE, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_LOSS_TRANSIENT);
        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MOVIE, ZONE_ID,
                AUDIOFOCUS_LOSS_TRANSIENT);
    }

    @Test
    public void requestAudioFocus_withDifferentZoneAndSameUsage_keepsExistingRequest() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest firstRequest = getLastRequest();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, SECOND_ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(firstRequest);
    }

    @Test
    public void requestAudioFocus_withSameZoneAndDifferentUsage_keepsExistingRequest() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest firstRequest = getLastRequest();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(firstRequest);
    }

    @Test
    public void requestAudioFocus_withPreviouslyFailedRequest_doesNothingForOldRequest() {
        when(mMockAudioManager.requestAudioFocus(any())).thenReturn(AUDIOFOCUS_REQUEST_FAILED,
                AUDIOFOCUS_REQUEST_GRANTED);

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest firstRequest = getLastRequest();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(firstRequest);
    }

    @Test
    public void onAudioFocusChange_notifiesHalOfChange() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        verify(mAudioControlWrapper, never()).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_LOSS_TRANSIENT);

        AudioFocusRequest actualRequest = getLastRequest();
        OnAudioFocusChangeListener listener = actualRequest.getOnAudioFocusChangeListener();
        listener.onAudioFocusChange(AUDIOFOCUS_LOSS_TRANSIENT);

        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID,
                AUDIOFOCUS_LOSS_TRANSIENT);
    }

    @Test
    public void abandonAudioFocus_withNoCurrentRequest_doesNothing() {
        whenAnyFocusRequestGranted();

        mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, ZONE_ID);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(any());
    }

    @Test
    public void abandonAudioFocus_withInvalidZone_throws() {
        whenAnyFocusRequestGranted();

        assertThrows(IllegalArgumentException.class,
                () -> mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, INVALID_ZONE_ID));
    }

    @Test
    public void abandonAudioFocus_withCurrentRequest_abandonsExistingFocus() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest actualRequest = getLastRequest();

        mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, ZONE_ID);

        verify(mMockAudioManager).abandonAudioFocusRequest(actualRequest);
    }

    @Test
    public void abandonAudioFocus_withCurrentRequest_notifiesHalOfFocusChange() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest actualRequest = getLastRequest();
        when(mMockAudioManager.abandonAudioFocusRequest(actualRequest)).thenReturn(
                AUDIOFOCUS_REQUEST_GRANTED);

        mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, ZONE_ID);

        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_LOSS);
    }

    @Test
    public void abandonAudioFocus_withFocusAlreadyLost_doesNothing() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest actualRequest = getLastRequest();
        OnAudioFocusChangeListener listener = actualRequest.getOnAudioFocusChangeListener();
        listener.onAudioFocusChange(AUDIOFOCUS_LOSS);

        mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, ZONE_ID);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(actualRequest);
    }

    @Test
    public void abandonAudioFocus_withFocusTransientlyLost_abandonsExistingFocus()
            throws Exception {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest actualRequest = getLastRequest();
        OnAudioFocusChangeListener listener = actualRequest.getOnAudioFocusChangeListener();
        listener.onAudioFocusChange(AUDIOFOCUS_LOSS_TRANSIENT);

        mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, ZONE_ID);

        verify(mMockAudioManager).abandonAudioFocusRequest(actualRequest);
    }

    @Test
    public void abandonAudioFocus_withExistingRequestOfDifferentUsage_doesNothing() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        mHalAudioFocus.abandonAudioFocus(METADATA_ALARM, ZONE_ID);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(any());
    }

    @Test
    public void abandonAudioFocus_withExistingRequestOfDifferentZoneId_doesNothing() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, SECOND_ZONE_ID);

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(any());
    }

    @Test
    public void abandonAudioFocus_withFailedRequest_doesNotNotifyHal() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest request = getLastRequest();

        when(mMockAudioManager.abandonAudioFocusRequest(request))
                .thenReturn(AUDIOFOCUS_REQUEST_FAILED);

        mHalAudioFocus.abandonAudioFocus(METADATA_MEDIA, ZONE_ID);

        verify(mAudioControlWrapper, never())
                .onAudioFocusChange(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_LOSS);
    }

    @Test
    public void reset_abandonsExistingRequests() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest mediaRequest = getLastRequest();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        AudioFocusRequest alarmRequest = getLastRequest();

        verify(mMockAudioManager, never()).abandonAudioFocusRequest(any());

        mHalAudioFocus.reset();

        verify(mMockAudioManager).abandonAudioFocusRequest(mediaRequest);
        verify(mMockAudioManager).abandonAudioFocusRequest(alarmRequest);
        verifyNoMoreInteractions(mMockAudioManager);
    }

    @Test
    public void reset_notifiesHal() {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        mHalAudioFocus.requestAudioFocus(METADATA_ALARM, ZONE_ID, AUDIOFOCUS_GAIN);

        verify(mAudioControlWrapper, never()).onAudioFocusChange(any(), eq(ZONE_ID),
                eq(AUDIOFOCUS_LOSS));
        when(mMockAudioManager.abandonAudioFocusRequest(any())).thenReturn(
                AUDIOFOCUS_REQUEST_GRANTED);

        mHalAudioFocus.reset();

        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_LOSS);
        verify(mAudioControlWrapper).onAudioFocusChange(METADATA_ALARM, ZONE_ID, AUDIOFOCUS_LOSS);
    }

    @Test
    public void getActiveAudioAttributesForZone_withEmptyStack_getsEmpty() {
        List<AudioAttributes> audioAttributes =
                mHalAudioFocus.getActiveAudioAttributesForZone(ZONE_ID);

        expectWithMessage("Active audio attributes")
                .that(audioAttributes).isEmpty();
    }

    @Test
    public void getActiveAudioAttributesForZone_withSingleUsage_getsUsage()
            throws Exception {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);

        List<AudioAttributes> audioAttributes =
                mHalAudioFocus.getActiveAudioAttributesForZone(ZONE_ID);

        expectWithMessage("Active audio attributes with active media")
                .that(audioAttributes).containsExactly(ATTR_MEDIA);
    }

    @Test
    public void getActiveAudioAttributesForZone_withMultipleUsages_getsUsages()
            throws Exception {
        whenAnyFocusRequestGranted();
        mHalAudioFocus.requestAudioFocus(METADATA_MEDIA, ZONE_ID, AUDIOFOCUS_GAIN);
        mHalAudioFocus.requestAudioFocus(METADATA_ASSISTANCE_NAVIGATION_GUIDANCE, ZONE_ID,
                AUDIOFOCUS_GAIN);
        mHalAudioFocus.requestAudioFocus(METADATA_NOTIFICATION, ZONE_ID, AUDIOFOCUS_GAIN);

        List<AudioAttributes> audioAttributes =
                mHalAudioFocus.getActiveAudioAttributesForZone(ZONE_ID);

        expectWithMessage("Active audio attributes with active media")
                .that(audioAttributes).containsExactly(ATTR_MEDIA, ATTR_NOTIFICATION,
                        ATTR_ASSISTANCE_NAVIGATION_GUIDANCE);
    }

    private void whenAnyFocusRequestGranted() {
        when(mMockAudioManager.requestAudioFocus(any())).thenReturn(AUDIOFOCUS_REQUEST_GRANTED);
    }

    private AudioFocusRequest getLastRequest() {
        ArgumentCaptor<AudioFocusRequest> captor = ArgumentCaptor.forClass(AudioFocusRequest.class);
        verify(mMockAudioManager, atLeastOnce()).requestAudioFocus(captor.capture());
        return captor.getValue();
    }

    private List<AudioAttributes> getCarAudioPlaybackMonitorAttributes(int zoneId) {
        verify(mCarAudioPlaybackMonitor).onActiveAudioPlaybackAttributesAdded(
                mAudioAttributesCaptor.capture(), eq(zoneId));
        return mAudioAttributesCaptor.getValue();
    }
}
