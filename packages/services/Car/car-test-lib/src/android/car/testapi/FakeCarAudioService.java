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

package android.car.testapi;

import static android.car.media.CarAudioManager.AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES;
import static android.service.autofill.FillRequest.INVALID_REQUEST_ID;

import android.car.CarOccupantZoneManager;
import android.car.media.AudioZonesMirrorStatusCallback;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioPatchHandle;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.car.media.IAudioZoneConfigurationsChangeCallback;
import android.car.media.IAudioZonesMirrorStatusCallback;
import android.car.media.ICarAudio;
import android.car.media.ICarVolumeEventCallback;
import android.car.media.IMediaAudioRequestStatusCallback;
import android.car.media.IPrimaryZoneMediaAudioRequestCallback;
import android.car.media.ISwitchAudioZoneConfigCallback;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.os.IBinder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Fake service that is used by {@link FakeCar} to provide an implementation of {@link ICarAudio}.
 * The reason we couldn't use a mock version of this service is that {@link AudioDeviceAttributes}
 * is annotated with @hide, and Mockito fails to create a mock instance.
 */
final class FakeCarAudioService extends ICarAudio.Stub {
    @Override
    public boolean isAudioFeatureEnabled(int feature) {
        return false;
    }

    @Override
    public void setGroupVolume(int zoneId, int groupId, int index, int flags) {
    }

    @Override
    public int getGroupMaxVolume(int zoneId, int groupId) {
        return 0;
    }

    @Override
    public int getGroupMinVolume(int zoneId, int groupId) {
        return 0;
    }

    @Override
    public int getGroupVolume(int zoneId, int groupId) {
        return 0;
    }

    @Override
    public void setFadeTowardFront(float value) {
    }

    @Override
    public void setBalanceTowardRight(float value) {
    }

    @Override
    public String[] getExternalSources() {
        return new String[] {};
    }

    @Override
    public CarAudioPatchHandle createAudioPatch(String sourceAddress, int usage,
            int gainInMillibels) {
        return null;
    }

    @Override
    public void releaseAudioPatch(CarAudioPatchHandle patch) {
    }

    @Override
    public int getVolumeGroupCount(int zoneId) {
        return 0;
    }

    @Override
    public int getVolumeGroupIdForUsage(int zoneId, int usage) {
        return 0;
    }

    @Override
    public int[] getUsagesForVolumeGroupId(int zoneId, int groupId) {
        return new int[] {};
    }

    @Override
    public CarVolumeGroupInfo getVolumeGroupInfo(int zoneId, int groupId) {
        return null;
    }

    @Override
    public List<CarVolumeGroupInfo> getVolumeGroupInfosForZone(int zoneId) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<AudioAttributes> getAudioAttributesForVolumeGroup(CarVolumeGroupInfo groupInfo) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public int[] getAudioZoneIds() {
        return new int[] {};
    }

    @Override
    public int getZoneIdForUid(int uid) {
        return 0;
    }

    @Override
    public boolean setZoneIdForUid(int zoneId, int uid) {
        return false;
    }

    @Override
    public boolean clearZoneIdForUid(int uid) {
        return false;
    }


    @Override
    public boolean cancelMediaAudioOnPrimaryZone(long requestId) {
        return false;
    }

    @Override
    public boolean resetMediaAudioOnPrimaryZone(CarOccupantZoneManager.OccupantZoneInfo info) {
        return false;
    }

    @Override
    public boolean isMediaAudioAllowedInPrimaryZone(CarOccupantZoneManager.OccupantZoneInfo info) {
        return false;
    }

    @Override
    public long requestMediaAudioOnPrimaryZone(IMediaAudioRequestStatusCallback callback,
            CarOccupantZoneManager.OccupantZoneInfo info) {
        return INVALID_REQUEST_ID;
    }

    @Override
    public boolean allowMediaAudioOnPrimaryZone(IBinder token, long requestId, boolean allow) {
        return false;
    }

    @Override
    public boolean registerPrimaryZoneMediaAudioRequestCallback(
            IPrimaryZoneMediaAudioRequestCallback backk) {
        return false;
    }

    @Override
    public void unregisterPrimaryZoneMediaAudioRequestCallback(
            IPrimaryZoneMediaAudioRequestCallback callback) {
    }

    /**
     * {@link CarAudioManager#setAudioZoneMirrorStatusCallback(Executor,
     *      AudioZonesMirrorStatusCallback)}
     */
    @Override
    public boolean registerAudioZonesMirrorStatusCallback(
            IAudioZonesMirrorStatusCallback callback) {
        return false;
    }

    /**
     * {@link CarAudioManager#clearAudioZonesMirrorStatusCallback()}
     */
    @Override
    public void unregisterAudioZonesMirrorStatusCallback(IAudioZonesMirrorStatusCallback callback) {
    }

    /**
     * {@link CarAudioManager#canEnableAudioMirror()}
     */
    @Override
    public int canEnableAudioMirror()  {
        return AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES;
    }

    /**
     * {@link CarAudioManager#enableMirrorForAudioZones(List)}
     */
    @Override
    public long enableMirrorForAudioZones(int[] audioZones) {
        return INVALID_REQUEST_ID;
    }

    /**
     * {@link CarAudioManager#extendAudioMirrorRequest(long, List)}
     */
    @Override
    public void extendAudioMirrorRequest(long mirrorId, int[] audioZones) {
    }

    /**
     * {@link CarAudioManager#disableAudioMirrorForZone(int)}
     */
    @Override
    public void disableAudioMirrorForZone(int zoneId) {
    }

    /**
     * {@link CarAudioManager#disableAudioMirrorRequest(int)}
     */
    @Override
    public void disableAudioMirror(long requestId) {
    }

    /**
     * {@link CarAudioManager#getMirrorAudioZonesForAudioZone(int)}
     */
    @Override
    public int[] getMirrorAudioZonesForAudioZone(int zoneId) {
        return new int[0];
    }

    /**
     * {@link CarAudioManager#getMirrorAudioZonesForMirrorRequest(long)}
     */
    @Override
    public int[] getMirrorAudioZonesForMirrorRequest(long mirrorId
    ) {
        return new int[0];
    }

    @Override
    public boolean isVolumeGroupMuted(int zoneId, int groupId) {
        return false;
    }

    @Override
    public void setVolumeGroupMute(int zoneId, int groupId, boolean mute, int flags) {
    }

    @Override
    public String getOutputDeviceAddressForUsage(int zoneId, int usage) {
        return "";
    }

    @Override
    public List<AudioDeviceAttributes> getInputDevicesForZoneId(int zoneId) {
        return Collections.emptyList();
    }

    @Override
    public boolean isPlaybackOnVolumeGroupActive(int volumeGroupId, int audioZoneId) {
        return false;
    }

    @Override
    public CarAudioZoneConfigInfo getCurrentAudioZoneConfigInfo(int audioZoneId) {
        return null;
    }

    @Override
    public List<CarAudioZoneConfigInfo> getAudioZoneConfigInfos(int audioZoneId) {
        return Collections.emptyList();
    }

    @Override
    public void switchZoneToConfig(CarAudioZoneConfigInfo zoneConfig,
            ISwitchAudioZoneConfigCallback callback) {
    }

    @Override
    public boolean registerAudioZoneConfigsChangeCallback(
            IAudioZoneConfigurationsChangeCallback callback) {
        return false;
    }

    @Override
    public boolean unregisterAudioZoneConfigsChangeCallback(
            IAudioZoneConfigurationsChangeCallback callback) {
        return false;
    }

    @Override
    public void registerVolumeCallback(IBinder binder) {
    }

    @Override
    public void unregisterVolumeCallback(IBinder binder) {
    }

    @Override
    public boolean registerCarVolumeEventCallback(ICarVolumeEventCallback callback) {
        return false;
    }

    @Override
    public boolean unregisterCarVolumeEventCallback(ICarVolumeEventCallback callback) {
        return false;
    }
}
