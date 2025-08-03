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

package com.android.car.audio;

import static android.car.oem.CarAudioFeaturesInfo.AUDIO_FEATURE_NO_FEATURE;
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioAttributes.USAGE_VIRTUAL_SOURCE;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.car.oem.AudioFocusEntry;
import android.car.oem.CarAudioFeaturesInfo;
import android.car.oem.OemCarAudioFocusResult;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.util.SparseArray;

import com.android.car.CarLocalServices;
import com.android.car.oem.CarOemAudioFocusProxyService;
import com.android.car.oem.CarOemProxyService;

import org.mockito.Mock;

import java.util.List;

abstract class CarZonesAudioFocusTestBase {
    protected static final String INVALID_CLIENT_ID = "invalid-client-id";
    protected static final String INVALID_CLIENT_ID_2 = "invalid-client2-id";
    protected static final String MEDIA_CLIENT_ID = "media-client-id";
    protected static final String MEDIA_CLIENT_ID_2 = "media-client2-id";
    protected static final String NAVIGATION_CLIENT_ID = "nav-client-id";
    protected static final String NAVIGATION_CLIENT_ID_2 = "nav-client2-id";
    protected static final String VOICE_COMMAND_CLIENT_ID = "voice-cmd-client-id";
    protected static final String VOICE_COMMAND_CLIENT_ID_2 = "voice-cmd-client2-id";
    protected static final String CALL_RING_CLIENT_ID = "call-ring-client-id";
    protected static final String CALL_RING_CLIENT_ID_2 = "call-ring-client2-id";
    protected static final String CALL_CLIENT_ID = "call-client-id";
    protected static final String CALL_CLIENT_ID_2 = "call-client2-id";
    protected static final String ALARM_CLIENT_ID = "alarm-client-id";
    protected static final String ALARM_CLIENT_ID_2 = "alarm-client2-id";
    protected static final String NOTIFICATION_CLIENT_ID = "notification-client-id";
    protected static final String NOTIFICATION_CLIENT_ID_2 = "notification-client2-id";
    protected static final String SYSTEM_SOUND_CLIENT_ID = "sys-sound-client-id";
    protected static final String SYSTEM_SOUND_CLIENT_ID_2 = "sys-sound-client2-id";
    protected static final String EMERGENCY_CLIENT_ID = "emergency-client-id";
    protected static final String EMERGENCY_CLIENT_ID_2 = "emergency-client2-id";
    protected static final String SAFETY_CLIENT_ID = "safety-client-id";
    protected static final String SAFETY_CLIENT_ID_2 = "safety-client2-id";
    protected static final String VEHICLE_STATUS_CLIENT_ID = "vehicle-status-client-id";
    protected static final String VEHICLE_STATUS_CLIENT_ID_2 = "vehicle-status-client2-id";
    protected static final String ANNOUNCEMENT_CLIENT_ID = "announcement-client-id";
    protected static final String ANNOUNCEMENT_CLIENT_ID_2 = "announcement-client2-id";

    protected static final int PRIMARY_ZONE_ID = CarAudioManager.PRIMARY_AUDIO_ZONE;
    protected static final int SECONDARY_ZONE_ID = CarAudioManager.PRIMARY_AUDIO_ZONE + 1;
    protected static final int TEST_USER_ID = 10;
    protected static final int INVALID_CLIENT_UID_1 = 1000005;
    protected static final int INVALID_CLIENT_UID_2 = 1000007;
    protected static final int MEDIA_CLIENT_UID_1 = 1086753;
    protected static final int MEDIA_CLIENT_UID_2 = 1000009;
    protected static final int NAVIGATION_CLIENT_UID_1 = 1010101;
    protected static final int NAVIGATION_CLIENT_UID_2 = 1000207;
    protected static final int VOICE_COMMAND_CLIENT_UID_1 = 1000305;
    protected static final int VOICE_COMMAND_CLIENT_UID_2 = 1000307;
    protected static final int CALL_RING_CLIENT_UID_1 = 1000405;
    protected static final int CALL_RING_CLIENT_UID_2 = 1000407;
    protected static final int CALL_CLIENT_UID_1 = 1086753;
    protected static final int CALL_CLIENT_UID_2 = 1000507;
    protected static final int ALARM_CLIENT_UID_1 = 1000605;
    protected static final int ALARM_CLIENT_UID_2 = 1000607;
    protected static final int NOTIFICATION_CLIENT_UID_1 = 1000705;
    protected static final int NOTIFICATION_CLIENT_UID_2 = 1000707;
    protected static final int SYSTEM_SOUND_CLIENT_UID_1 = 1000805;
    protected static final int SYSTEM_SOUND_CLIENT_UID_2 = 1000807;
    protected static final int EMERGENCY_CLIENT_UID_1 = 1000905;
    protected static final int EMERGENCY_CLIENT_UID_2 = 1000907;
    protected static final int SAFETY_CLIENT_UID_1 = 1001005;
    protected static final int SAFETY_CLIENT_UID_2 = 1001007;
    protected static final int VEHICLE_STATUS_CLIENT_UID_1 = 1001105;
    protected static final int VEHICLE_STATUS_CLIENT_UID_2 = 1001107;
    protected static final int ANNOUNCEMENT_CLIENT_UID_1 = 1001205;
    protected static final int ANNOUNCEMENT_CLIENT_UID_2 = 1001207;

    protected static final AudioClientInfo INVALID_SOUND_INFO_1 = new AudioClientInfo(
            USAGE_VIRTUAL_SOURCE, INVALID_CLIENT_UID_1, INVALID_CLIENT_ID);
    protected static final AudioClientInfo INVALID_SOUND_INFO_2 = new AudioClientInfo(
            USAGE_VIRTUAL_SOURCE, INVALID_CLIENT_UID_2, INVALID_CLIENT_ID_2);
    protected static final AudioClientInfo MEDIA_INFO_1 = new AudioClientInfo(
            USAGE_MEDIA, MEDIA_CLIENT_UID_1, MEDIA_CLIENT_ID);
    protected static final AudioClientInfo MEDIA_INFO_2 = new AudioClientInfo(
            USAGE_MEDIA, MEDIA_CLIENT_UID_2, MEDIA_CLIENT_ID_2);
    protected static final AudioClientInfo NAVIGATION_INFO_1 = new AudioClientInfo(
            USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, NAVIGATION_CLIENT_UID_1, NAVIGATION_CLIENT_ID);
    protected static final AudioClientInfo NAVIGATION_INFO_2 = new AudioClientInfo(
            USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, NAVIGATION_CLIENT_UID_2, NAVIGATION_CLIENT_ID_2);
    protected static final AudioClientInfo VOICE_COMMAND_INFO_1 = new AudioClientInfo(
            USAGE_ASSISTANCE_ACCESSIBILITY, VOICE_COMMAND_CLIENT_UID_1, VOICE_COMMAND_CLIENT_ID);
    protected static final AudioClientInfo VOICE_COMMAND_INFO_2 = new AudioClientInfo(
            USAGE_ASSISTANCE_ACCESSIBILITY, VOICE_COMMAND_CLIENT_UID_2, VOICE_COMMAND_CLIENT_ID_2);
    protected static final AudioClientInfo CALL_RING_INFO_1 = new AudioClientInfo(
            USAGE_NOTIFICATION_RINGTONE, CALL_RING_CLIENT_UID_1, CALL_RING_CLIENT_ID);
    protected static final AudioClientInfo CALL_RING_INFO_2 = new AudioClientInfo(
            USAGE_NOTIFICATION_RINGTONE, CALL_RING_CLIENT_UID_2, CALL_RING_CLIENT_ID_2);
    protected static final AudioClientInfo CALL_INFO_1 = new AudioClientInfo(
            USAGE_VOICE_COMMUNICATION, CALL_CLIENT_UID_1, CALL_CLIENT_ID);
    protected static final AudioClientInfo CALL_INFO_2 = new AudioClientInfo(
            USAGE_VOICE_COMMUNICATION, CALL_CLIENT_UID_2, CALL_CLIENT_ID_2);
    protected static final AudioClientInfo ALARM_INFO_1 = new AudioClientInfo(
            USAGE_ALARM, ALARM_CLIENT_UID_1, ALARM_CLIENT_ID);
    protected static final AudioClientInfo ALARM_INFO_2 = new AudioClientInfo(
            USAGE_ALARM, ALARM_CLIENT_UID_2, ALARM_CLIENT_ID_2);
    protected static final AudioClientInfo NOTIFICATION_INFO_1 = new AudioClientInfo(
            USAGE_NOTIFICATION, NOTIFICATION_CLIENT_UID_1, NOTIFICATION_CLIENT_ID);
    protected static final AudioClientInfo NOTIFICATION_INFO_2 = new AudioClientInfo(
            USAGE_NOTIFICATION, NOTIFICATION_CLIENT_UID_2, NOTIFICATION_CLIENT_ID_2);
    protected static final AudioClientInfo SYSTEM_SOUND_INFO_1 = new AudioClientInfo(
            USAGE_ASSISTANCE_SONIFICATION, SYSTEM_SOUND_CLIENT_UID_1, SYSTEM_SOUND_CLIENT_ID);
    protected static final AudioClientInfo SYSTEM_SOUND_INFO_2 = new AudioClientInfo(
            USAGE_ASSISTANCE_SONIFICATION, SYSTEM_SOUND_CLIENT_UID_2, SYSTEM_SOUND_CLIENT_ID_2);
    protected static final AudioClientInfo EMERGENCY_INFO_1 = new AudioClientInfo(
            USAGE_EMERGENCY, EMERGENCY_CLIENT_UID_1, EMERGENCY_CLIENT_ID);
    protected static final AudioClientInfo EMERGENCY_INFO_2 = new AudioClientInfo(
            USAGE_EMERGENCY, EMERGENCY_CLIENT_UID_2, EMERGENCY_CLIENT_ID_2);
    protected static final AudioClientInfo SAFETY_INFO_1 = new AudioClientInfo(
            USAGE_SAFETY, SAFETY_CLIENT_UID_1, SAFETY_CLIENT_ID);
    protected static final AudioClientInfo SAFETY_INFO_2 = new AudioClientInfo(
            USAGE_SAFETY, SAFETY_CLIENT_UID_2, SAFETY_CLIENT_ID_2);
    protected static final AudioClientInfo VEHICLE_STATUS_INFO_1 = new AudioClientInfo(
            USAGE_VEHICLE_STATUS, VEHICLE_STATUS_CLIENT_UID_1, VEHICLE_STATUS_CLIENT_ID);
    protected static final AudioClientInfo VEHICLE_STATUS_INFO_2 = new AudioClientInfo(
            USAGE_VEHICLE_STATUS, VEHICLE_STATUS_CLIENT_UID_2, VEHICLE_STATUS_CLIENT_ID_2);
    protected static final AudioClientInfo ANNOUNCEMENT_INFO_1 = new AudioClientInfo(
            USAGE_ANNOUNCEMENT, ANNOUNCEMENT_CLIENT_UID_1, ANNOUNCEMENT_CLIENT_ID);
    protected static final AudioClientInfo ANNOUNCEMENT_INFO_2 = new AudioClientInfo(
            USAGE_ANNOUNCEMENT, ANNOUNCEMENT_CLIENT_UID_2, ANNOUNCEMENT_CLIENT_ID_2);

    @Mock
    protected AudioManager mMockAudioManager;
    @Mock
    protected AudioPolicy mAudioPolicy;
    @Mock
    protected CarAudioService mCarAudioService;
    @Mock
    protected CarZonesAudioFocus.CarFocusCallback mMockCarFocusCallback;
    @Mock
    protected CarOemProxyService mMockCarOemProxyService;
    @Mock
    protected CarOemAudioFocusProxyService mMockCarOemAudioFocusProxyService;
    @Mock
    private ContentResolver mContentResolver;
    @Mock
    private CarAudioSettings mCarAudioSettings;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private CarVolumeInfoWrapper mMockCarVolumeInfoWrapper;

    protected SparseArray<CarAudioZone> mCarAudioZones;

    public void setUp() {
        mCarAudioZones = generateAudioZones();
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.addService(CarOemProxyService.class, mMockCarOemProxyService);
        when(mMockCarOemProxyService.isOemServiceReady()).thenReturn(false);
        when(mMockCarOemProxyService.isOemServiceEnabled()).thenReturn(false);
    }

    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
    }

    protected AudioFocusInfo generateCallRequestForPrimaryZone() {
        return new AudioFocusInfoBuilder().setUsage(USAGE_VOICE_COMMUNICATION)
                .setGainRequest(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setClientId(CALL_CLIENT_ID)
                .setClientUid(CALL_CLIENT_UID_1).createAudioFocusInfo();
    }

    protected AudioFocusInfo generateMediaRequestForPrimaryZone(boolean isDelayedFocusEnabled) {
        return new AudioFocusInfoBuilder().setUsage(USAGE_MEDIA)
                .setGainRequest(AUDIOFOCUS_GAIN)
                .setClientId(MEDIA_CLIENT_ID)
                .setDelayedFocusRequestEnable(isDelayedFocusEnabled)
                .setClientUid(MEDIA_CLIENT_UID_1).createAudioFocusInfo();
    }

    protected void requestFocusAndAssertIfRequestNotGranted(CarZonesAudioFocus carZonesAudioFocus,
            AudioFocusInfo audioFocusClient) {
        requestFocusAndAssertIfRequestDiffers(carZonesAudioFocus, audioFocusClient,
                AUDIOFOCUS_REQUEST_GRANTED);
    }

    private void requestFocusAndAssertIfRequestDiffers(CarZonesAudioFocus carZonesAudioFocus,
            AudioFocusInfo audioFocusClient, int expectedAudioFocusResults) {
        carZonesAudioFocus.onAudioFocusRequest(audioFocusClient, expectedAudioFocusResults);
        verify(mMockAudioManager)
                .setFocusRequestResult(audioFocusClient, expectedAudioFocusResults, mAudioPolicy);
    }

    protected void abandonFocusAndAssertIfAbandonNotGranted(CarZonesAudioFocus carZonesAudioFocus,
            AudioFocusInfo audioFocusClient) {
        carZonesAudioFocus.onAudioFocusAbandon(audioFocusClient);
        verify(mMockAudioManager, never()).dispatchAudioFocusChange(eq(audioFocusClient),
                anyInt(), eq(mAudioPolicy));
    }

    protected SparseArray<CarAudioZone> generateAudioZones() {
        int testZoneConfigId = 0;
        SparseArray<CarAudioZone> zones = new SparseArray<>(2);
        CarAudioContext testCarAudioContext =
                new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                        /* useCoreAudioRouting= */ false);
        CarAudioZone primaryZone = new CarAudioZone(testCarAudioContext, "Primary zone",
                PRIMARY_ZONE_ID);
        CarAudioZone secondaryZone = new CarAudioZone(testCarAudioContext, "Secondary zone",
                SECONDARY_ZONE_ID);
        CarAudioZoneConfig primaryZoneConfig =
                new CarAudioZoneConfig.Builder("Primary zone config", PRIMARY_ZONE_ID,
                        testZoneConfigId, /* isDefault= */ true).build();
        CarAudioZoneConfig secondaryZoneConfig =
                new CarAudioZoneConfig.Builder("Secondary zone config", SECONDARY_ZONE_ID,
                        testZoneConfigId, /* isDefault= */ true).build();
        primaryZone.addZoneConfig(primaryZoneConfig);
        secondaryZone.addZoneConfig(secondaryZoneConfig);
        zones.put(PRIMARY_ZONE_ID, primaryZone);
        zones.put(SECONDARY_ZONE_ID, secondaryZone);
        return zones;
    }

    protected CarZonesAudioFocus getCarZonesAudioFocus(CarZonesAudioFocus.CarFocusCallback
            carFocusCallback) {
        CarZonesAudioFocus carZonesAudioFocus =
                CarZonesAudioFocus.createCarZonesAudioFocus(mMockAudioManager,
                        mMockPackageManager, mCarAudioZones, mCarAudioSettings, carFocusCallback,
                        mMockCarVolumeInfoWrapper, new CarAudioFeaturesInfo.Builder(
                                AUDIO_FEATURE_NO_FEATURE).build());
        carZonesAudioFocus.setOwningPolicy(mCarAudioService, mAudioPolicy);

        return carZonesAudioFocus;
    }

    protected CarZonesAudioFocus getCarZonesAudioFocus() {
        return getCarZonesAudioFocus(mMockCarFocusCallback);
    }

    protected void setUpRejectNavigationOnCallValue(boolean rejectNavigationOnCall) {
        when(mCarAudioSettings.getContentResolverForUser(TEST_USER_ID))
                .thenReturn(mContentResolver);
        when(mCarAudioSettings.isRejectNavigationOnCallEnabledInSettings(TEST_USER_ID))
                .thenReturn(rejectNavigationOnCall);
    }

    protected OemCarAudioFocusResult getAudioFocusResults(AudioFocusEntry entry,
            List<AudioFocusEntry> lostEntries, List<AudioFocusEntry> blockedEntries, int results) {
        return new OemCarAudioFocusResult.Builder(lostEntries, blockedEntries, results)
                .setAudioFocusEntry(entry).build();
    }

    public static final class AudioClientInfo {
        private final int mUsage;
        private final int mClientUid;
        private final String mClientId;

        AudioClientInfo(int usage, int clientUid, String clientId) {
            mUsage = usage;
            mClientUid = clientUid;
            mClientId = clientId;
        }

        public int getUsage() {
            return mUsage;
        }

        public int getClientUid() {
            return mClientUid;
        }

        public String getClientId() {
            return mClientId;
        }
    }
}
