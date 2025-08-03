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

import static android.car.media.CarAudioManager.AUDIO_MIRROR_CAN_ENABLE;
import static android.car.media.CarAudioManager.AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.car.media.IAudioZonesMirrorStatusCallback;
import android.media.AudioDeviceAttributes;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.LongSparseArray;
import android.util.SparseLongArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.audio.CarAudioDumpProto.CarAudioMirrorRequestHandlerProto;
import com.android.car.audio.CarAudioDumpProto.CarAudioMirrorRequestHandlerProto.RequestIdToZones;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Managed the car audio mirror request
 */
/* package */ final class CarAudioMirrorRequestHandler {
    private static final String TAG = CarLog.TAG_AUDIO;

    private static final String REQUEST_HANDLER_THREAD_NAME = "CarAudioMirrorRequest";

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            REQUEST_HANDLER_THREAD_NAME);
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());

    private final Object mLock = new Object();

    // Lock not needed as the callback is only broadcast inside the handler's thread
    // If this changes then the lock may be needed to prevent concurrent calls to
    // mAudioZonesMirrorStatusCallbacks.beginBroadcast
    private final RemoteCallbackList<IAudioZonesMirrorStatusCallback>
            mAudioZonesMirrorStatusCallbacks = new RemoteCallbackList<>();
    @GuardedBy("mLock")
    private final List<CarAudioDeviceInfo> mMirrorDevices = new ArrayList<>();
    @GuardedBy("mLock")
    private final SparseLongArray mZonesToMirrorRequestId = new SparseLongArray();
    @GuardedBy("mLock")
    private final LongSparseArray<CarAudioDeviceInfo> mRequestIdToMirrorDevice =
            new LongSparseArray<>();

    @GuardedBy("mLock")
    private final LongSparseArray<int[]> mRequestIdToZones = new LongSparseArray<>();

    private final RequestIdGenerator mRequestIdGenerator = new RequestIdGenerator();

    boolean registerAudioZonesMirrorStatusCallback(
            IAudioZonesMirrorStatusCallback callback) {
        Objects.requireNonNull(callback, "Audio zones mirror status callback can not be null");

        if (!isMirrorAudioEnabled()) {
            Slogf.w(TAG, "Could not register audio mirror status callback, mirroring not enabled");
            return false;
        }

        return mAudioZonesMirrorStatusCallbacks.register(callback);
    }

    boolean unregisterAudioZonesMirrorStatusCallback(IAudioZonesMirrorStatusCallback callback) {
        Objects.requireNonNull(callback, "Audio zones mirror status callback can not be null");

        return mAudioZonesMirrorStatusCallbacks.unregister(callback);
    }

    boolean isMirrorAudioEnabled() {
        synchronized (mLock) {
            return !mMirrorDevices.isEmpty();
        }
    }

    void setMirrorDeviceInfos(List<CarAudioDeviceInfo> mirroringDevices) {
        Objects.requireNonNull(mirroringDevices, "Mirror devices can not be null");

        synchronized (mLock) {
            mMirrorDevices.clear();
            mMirrorDevices.addAll(mirroringDevices);
        }
    }

    List<CarAudioDeviceInfo> getMirroringDeviceInfos() {
        synchronized (mLock) {
            return List.copyOf(mMirrorDevices);
        }
    }

    @Nullable
    AudioDeviceAttributes getAudioDevice(long requestId) {
        Preconditions.checkArgument(requestId != INVALID_REQUEST_ID,
                "Request id for device can not be INVALID_REQUEST_ID");
        synchronized (mLock) {
            int index = mRequestIdToMirrorDevice.indexOfKey(requestId);
            if (index < 0) {
                return null;
            }
            return mRequestIdToMirrorDevice.valueAt(index).getAudioDevice();
        }
    }

    void enableMirrorForZones(long requestId, int[] audioZones) {
        Objects.requireNonNull(audioZones, "Mirror audio zones can not be null");
        Preconditions.checkArgument(requestId != INVALID_REQUEST_ID,
                "Request id can not be INVALID_REQUEST_ID");
        synchronized (mLock) {
            mRequestIdToZones.put(requestId, audioZones);
            for (int index = 0; index < audioZones.length; index++) {
                mZonesToMirrorRequestId.put(audioZones[index], requestId);
            }
        }
        mHandler.post(() ->
                handleInformCallbacks(audioZones, CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED));
    }

    private void handleInformCallbacks(int[] audioZones, int status) {
        int n = mAudioZonesMirrorStatusCallbacks.beginBroadcast();
        for (int c = 0; c < n; c++) {
            IAudioZonesMirrorStatusCallback callback =
                    mAudioZonesMirrorStatusCallbacks.getBroadcastItem(c);
            try {
                // Calling binder inside lock here since the call is one way and doest not block.
                // The lock is needed to prevent concurrent beginBroadcast
                callback.onAudioZonesMirrorStatusChanged(audioZones, status);
            } catch (RemoteException e) {
                Slogf.e(TAG, e, "Could not inform mirror status callback index %d of total %d",
                        c, n);
            }

        }
        mAudioZonesMirrorStatusCallbacks.finishBroadcast();
    }

    @Nullable
    int[] getMirrorAudioZonesForRequest(long requestId) {
        synchronized (mLock) {
            return mRequestIdToZones.get(requestId, /* valueIfKeyNotFound= */ null);
        }
    }

    boolean isMirrorEnabledForZone(int zoneId) {
        synchronized (mLock) {
            return mZonesToMirrorRequestId.get(zoneId, INVALID_REQUEST_ID) != INVALID_REQUEST_ID;
        }
    }

    void rejectMirrorForZones(long requestId, int[] audioZones) {
        Objects.requireNonNull(audioZones, "Rejected audio zones can not be null");
        Preconditions.checkArgument(audioZones.length > 1,
                "Rejected audio zones must be greater than one");

        synchronized (mLock) {
            releaseRequestIdLocked(requestId);
        }
        mHandler.post(() ->
                handleInformCallbacks(audioZones, CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED));
    }

    void updateRemoveMirrorConfigurationForZones(long requestId, int[] newConfig) {
        ArraySet<Integer> newConfigSet = CarServiceUtils.toIntArraySet(newConfig);
        ArrayList<Integer> delta = new ArrayList<>();
        synchronized (mLock) {
            int[] prevConfig = mRequestIdToZones.get(requestId, new int[0]);
            for (int index = 0; index < prevConfig.length; index++) {
                int zoneId = prevConfig[index];
                mZonesToMirrorRequestId.delete(zoneId);
                if (newConfigSet.contains(zoneId)) {
                    continue;
                }
                delta.add(zoneId);
            }
            if (newConfig.length == 0) {
                mRequestIdToZones.remove(requestId);
                releaseRequestIdLocked(requestId);
            } else {
                mRequestIdToZones.put(requestId, newConfig);
            }
            for (int index = 0; index < newConfig.length; index++) {
                int zoneId = newConfig[index];
                mZonesToMirrorRequestId.put(zoneId, requestId);
            }
        }
        mHandler.post(() ->
                handleInformCallbacks(CarServiceUtils.toIntArray(delta),
                        CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED));
    }

    /**
     * Return the difference between the audio zones ids to remove and the current for the request
     * id. This can be used to determine how a configuration should change when some zones
     * are removed.
     */
    @Nullable
    int[] calculateAudioConfigurationAfterRemovingZonesFromRequestId(long requestId,
            int[] audioZoneIdsToRemove) {
        Objects.requireNonNull(audioZoneIdsToRemove, "Audio zone ids to remove must not be null");
        Preconditions.checkArgument(audioZoneIdsToRemove.length > 0,
                "audio zones ids to remove must not empty");
        ArraySet<Integer> zonesToRemove = CarServiceUtils.toIntArraySet(audioZoneIdsToRemove);
        int[] oldConfig;

        synchronized (mLock) {
            oldConfig = mRequestIdToZones.get(requestId, /* valueIfKeyNotFound= */ null);
        }

        if (oldConfig == null) {
            Slogf.w(TAG, "calculateAudioConfigurationAfterRemovingZonesFromRequestId Request "
                    + "id %d is no longer valid");
            return null;
        }

        ArrayList<Integer> newConfig = new ArrayList<>();
        for (int index = 0; index < oldConfig.length; index++) {
            int zoneId = oldConfig[index];
            if (zonesToRemove.contains(zoneId)) {
                continue;
            }
            newConfig.add(zoneId);
        }

        return CarServiceUtils.toIntArray(newConfig);
    }

    long getUniqueRequestIdAndAssignMirrorDevice() {
        long requestId = mRequestIdGenerator.generateUniqueRequestId();
        synchronized (mLock) {
            if (assignAvailableDeviceToRequestIdLocked(requestId)) {
                return requestId;
            }
            releaseRequestIdLocked(requestId);
        }
        return INVALID_REQUEST_ID;
    }

    long getRequestIdForAudioZone(int audioZoneId) {
        synchronized (mLock) {
            return mZonesToMirrorRequestId.get(audioZoneId, INVALID_REQUEST_ID);
        }
    }

    void verifyValidRequestId(long requestId) {
        synchronized (mLock) {
            Preconditions.checkArgument(mRequestIdToZones.indexOfKey(requestId) >= 0,
                    "Mirror request id " + requestId + " is not valid");
        }
    }

    int canEnableAudioMirror() {
        synchronized (mLock) {
            return mRequestIdToMirrorDevice.size() < mMirrorDevices.size()
                    ? AUDIO_MIRROR_CAN_ENABLE : AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES;
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.printf("Is audio mirroring enabled? %s\n", isMirrorAudioEnabled() ? "Yes" : "No");
        if (!isMirrorAudioEnabled()) {
            return;
        }
        writer.increaseIndent();
        int registeredCount = mAudioZonesMirrorStatusCallbacks.getRegisteredCallbackCount();
        synchronized (mLock) {
            writer.println("Mirroring device info:");
            dumpMirrorDeviceInfosLocked(writer);
            writer.printf("Registered callback count: %d\n", registeredCount);
            dumpMirroringConfigurationsLocked(writer);
            dumpZonesToIdMappingLocked(writer);
            dumpMirrorDeviceMappingLocked(writer);
        }
        writer.decreaseIndent();
    }

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpMirroringConfigurationsLocked(IndentingPrintWriter writer) {
        writer.println("Mirroring configurations:");
        writer.increaseIndent();
        for (int index = 0; index < mRequestIdToZones.size(); index++) {
            writer.printf("Audio zone request id %d: %s\n", mRequestIdToZones.keyAt(index),
                    Arrays.toString(mRequestIdToZones.valueAt(index)));
        }
        writer.decreaseIndent();
    }

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpZonesToIdMappingLocked(IndentingPrintWriter writer) {
        writer.println("Mirroring zone to id mapping:");
        writer.increaseIndent();
        for (int index = 0; index < mZonesToMirrorRequestId.size(); index++) {
            writer.printf("Audio zone %d: request id %d\n",
                    mZonesToMirrorRequestId.keyAt(index),
                    mZonesToMirrorRequestId.valueAt(index));
        }
        writer.decreaseIndent();
    }

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpMirrorDeviceMappingLocked(IndentingPrintWriter writer) {
        writer.println("Mirroring device to id mapping:");
        writer.increaseIndent();
        for (int index = 0; index < mRequestIdToMirrorDevice.size(); index++) {
            writer.printf("Mirror device %s: request id %d\n",
                    mRequestIdToMirrorDevice.valueAt(index),
                    mRequestIdToMirrorDevice.keyAt(index));
        }
        writer.decreaseIndent();
    }

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpMirrorDeviceInfosLocked(IndentingPrintWriter writer) {
        for (int index = 0; index < mMirrorDevices.size(); index++) {
            writer.printf("Mirror device[%d]\n", index);
            writer.increaseIndent();
            mMirrorDevices.get(index).dump(writer);
            writer.decreaseIndent();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long mirrorRequestHandlerToken = proto.start(CarAudioDumpProto
                .CAR_AUDIO_MIRROR_REQUEST_HANDLER);
        proto.write(CarAudioMirrorRequestHandlerProto.IS_MIRROR_AUDIO_ENABLED,
                isMirrorAudioEnabled());
        if (!isMirrorAudioEnabled()) {
            proto.end(mirrorRequestHandlerToken);
            return;
        }

        int registeredCount = mAudioZonesMirrorStatusCallbacks.getRegisteredCallbackCount();
        synchronized (mLock) {
            dumpProtoMirrorDeviceInfosLocked(proto);
            proto.write(CarAudioMirrorRequestHandlerProto.REGISTER_COUNT, registeredCount);
            dumpProtoMirroringConfigurationsLocked(proto);
            dumpProtoMirrorDeviceMappingLocked(proto);
        }
        proto.end(mirrorRequestHandlerToken);
    }

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpProtoMirrorDeviceInfosLocked(ProtoOutputStream proto) {
        for (int index = 0; index < mMirrorDevices.size(); index++) {
            mMirrorDevices.get(index).dumpProto(CarAudioMirrorRequestHandlerProto
                    .MIRROR_DEVICE_INFOS, proto);
        }
    }

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpProtoMirroringConfigurationsLocked(ProtoOutputStream proto) {
        for (int index = 0; index < mRequestIdToZones.size(); index++) {
            long configurationToken = proto.start(
                    CarAudioMirrorRequestHandlerProto.MIRRORING_CONFIGURATIONS);
            proto.write(RequestIdToZones.REQUEST_ID, mRequestIdToZones.keyAt(index));
            for (int zoneIndex = 0; zoneIndex < mRequestIdToZones.valueAt(index).length;
                    zoneIndex++) {
                proto.write(RequestIdToZones.ZONE_IDS, mRequestIdToZones.valueAt(index)[zoneIndex]);
            }
            proto.end(configurationToken);
        }
    }

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpProtoMirrorDeviceMappingLocked(ProtoOutputStream proto) {
        for (int index = 0; index < mRequestIdToMirrorDevice.size(); index++) {
            long mirrorDeviceMappingToken = proto.start(
                    CarAudioMirrorRequestHandlerProto.MIRROR_DEVICE_MAPPINGS);
            mRequestIdToMirrorDevice.valueAt(index).dumpProto(CarAudioMirrorRequestHandlerProto
                    .RequestIdToMirrorDevice.MIRROR_DEVICE, proto);
            proto.write(CarAudioMirrorRequestHandlerProto.RequestIdToMirrorDevice.REQUEST_ID,
                    mRequestIdToMirrorDevice.keyAt(index));
            proto.end(mirrorDeviceMappingToken);
        }
    }

    @GuardedBy("mLock")
    private boolean assignAvailableDeviceToRequestIdLocked(long requestId) {
        for (int index = 0; index < mMirrorDevices.size(); index++) {
            CarAudioDeviceInfo info = mMirrorDevices.get(index);
            if (mRequestIdToMirrorDevice.indexOfValue(info) >= 0) {
                continue;
            }
            mRequestIdToMirrorDevice.put(requestId, info);
            return true;
        }
        return false;
    }

    @GuardedBy("mLock")
    private void releaseRequestIdLocked(long requestId) {
        mRequestIdGenerator.releaseRequestId(requestId);
        synchronized (mLock) {
            mRequestIdToMirrorDevice.remove(requestId);
        }
    }
}
