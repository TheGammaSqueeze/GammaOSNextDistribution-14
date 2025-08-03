/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.media;

import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.media.CarAudioPatchHandle;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.car.media.IAudioZoneConfigurationsChangeCallback;
import android.car.media.IAudioZonesMirrorStatusCallback;
import android.car.media.ICarVolumeEventCallback;
import android.car.media.IMediaAudioRequestStatusCallback;
import android.car.media.IPrimaryZoneMediaAudioRequestCallback;
import android.car.media.ISwitchAudioZoneConfigCallback;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;

/**
 * Binder interface for {@link android.car.media.CarAudioManager}.
 * Check {@link android.car.media.CarAudioManager} APIs for expected behavior of each call.
 *
 * @hide
 */
interface ICarAudio {
    boolean isAudioFeatureEnabled(int feature);

    void setGroupVolume(int zoneId, int groupId, int index, int flags);
    int getGroupMaxVolume(int zoneId, int groupId);
    int getGroupMinVolume(int zoneId, int groupId);
    int getGroupVolume(int zoneId, int groupId);

    void setFadeTowardFront(float value);
    void setBalanceTowardRight(float value);

    String[] getExternalSources();
    CarAudioPatchHandle createAudioPatch(in String sourceAddress, int usage, int gainInMillibels);
    void releaseAudioPatch(in CarAudioPatchHandle patch);

    int getVolumeGroupCount(int zoneId);
    int getVolumeGroupIdForUsage(int zoneId, int usage);
    int[] getUsagesForVolumeGroupId(int zoneId, int groupId);

    int[] getAudioZoneIds();
    int getZoneIdForUid(int uid);
    boolean setZoneIdForUid(int zoneId, int uid);
    boolean clearZoneIdForUid(int uid);

    boolean isVolumeGroupMuted(int zoneId, int groupId);
    void setVolumeGroupMute(int zoneId, int groupId, boolean mute, int flags);
    CarVolumeGroupInfo getVolumeGroupInfo(int zoneId, int groupId);
    List<CarVolumeGroupInfo> getVolumeGroupInfosForZone(int zoneId);

    List<AudioAttributes> getAudioAttributesForVolumeGroup(in CarVolumeGroupInfo groupInfo);

    String getOutputDeviceAddressForUsage(int zoneId, int usage);

    List<AudioDeviceAttributes> getInputDevicesForZoneId(int zoneId);

    boolean isPlaybackOnVolumeGroupActive(int volumeGroupId, int audioZoneId);

    boolean registerPrimaryZoneMediaAudioRequestCallback(
        in IPrimaryZoneMediaAudioRequestCallback callback);
    void unregisterPrimaryZoneMediaAudioRequestCallback(
        in IPrimaryZoneMediaAudioRequestCallback callback);

    long requestMediaAudioOnPrimaryZone(in IMediaAudioRequestStatusCallback callback,
        in OccupantZoneInfo info);
    boolean cancelMediaAudioOnPrimaryZone(long requestId);

    boolean allowMediaAudioOnPrimaryZone(in IBinder token, long requestId, boolean allow);
    boolean isMediaAudioAllowedInPrimaryZone(in OccupantZoneInfo info);
    boolean resetMediaAudioOnPrimaryZone(in OccupantZoneInfo zone);

    CarAudioZoneConfigInfo getCurrentAudioZoneConfigInfo(int zoneId);
    List<CarAudioZoneConfigInfo> getAudioZoneConfigInfos(int zoneId);
    void switchZoneToConfig(in CarAudioZoneConfigInfo zoneConfig,
            in ISwitchAudioZoneConfigCallback callback);
    boolean registerAudioZoneConfigsChangeCallback(
            in IAudioZoneConfigurationsChangeCallback callback);
    boolean unregisterAudioZoneConfigsChangeCallback(
            in IAudioZoneConfigurationsChangeCallback callback);

    boolean registerAudioZonesMirrorStatusCallback(in IAudioZonesMirrorStatusCallback callback);
    void unregisterAudioZonesMirrorStatusCallback(in IAudioZonesMirrorStatusCallback callback);
    int canEnableAudioMirror();
    long enableMirrorForAudioZones(in int[] audioZones);
    void extendAudioMirrorRequest(long mirrorId, in int[] audioZones);
    void disableAudioMirrorForZone(int zoneId);
    void disableAudioMirror(long mirrorId);
    int[] getMirrorAudioZonesForAudioZone(int zoneId);
    int[] getMirrorAudioZonesForMirrorRequest(long mirrorId);


    /**
     * IBinder is ICarVolumeCallback but passed as IBinder due to aidl hidden.
     */
    void registerVolumeCallback(in IBinder binder);
    void unregisterVolumeCallback(in IBinder binder);

    boolean registerCarVolumeEventCallback(in ICarVolumeEventCallback callback);
    boolean unregisterCarVolumeEventCallback(in ICarVolumeEventCallback callback);
}
