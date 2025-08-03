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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.storage.StorageManager;

import java.util.List;

public class StorageManagerCompat {
    public StorageManagerCompat(StorageManager manager) {
        mManager = manager;
    }

    public StorageManagerCompat(Context context) {
        mManager = context.getSystemService(StorageManager.class);
    }

    private final StorageManager mManager;

    public void registerListener(StorageEventListenerCompat listener) {
        mManager.registerListener(listener.listener);
    }

    public void unregisterListener(StorageEventListenerCompat listener) {
        mManager.unregisterListener(listener.listener);
    }

    public @Nullable
    VolumeRecordCompat findRecordByUuid(String fsUuid) {
        return new VolumeRecordCompat(mManager.findRecordByUuid(fsUuid));
    }

    public @NonNull
    List<VolumeInfoCompat> getVolumes() {
        return VolumeInfoCompat.convert(mManager.getVolumes());
    }

    public @NonNull
    List<DiskInfoCompat> getDisks() {
        return DiskInfoCompat.convert(mManager.getDisks());
    }

    public @NonNull
    List<VolumeRecordCompat> getVolumeRecords() {
        return VolumeRecordCompat.convert(mManager.getVolumeRecords());
    }

    public @Nullable
    DiskInfoCompat findDiskById(String id) {
        return new DiskInfoCompat(mManager.findDiskById(id));
    }

    public @Nullable
    VolumeInfoCompat findVolumeByUuid(String fsUuid) {
        return new VolumeInfoCompat(mManager.findVolumeByUuid(fsUuid));
    }

    public void forgetVolume(String fsUuid) {
        mManager.forgetVolume(fsUuid);
    }

    public @Nullable
    String getBestVolumeDescription(VolumeInfoCompat vol) {
        return mManager.getBestVolumeDescription(vol.mVolumeInfo);
    }

    public @Nullable
    VolumeInfoCompat findEmulatedForPrivate(VolumeInfoCompat privateVol) {
        return new VolumeInfoCompat(mManager.findEmulatedForPrivate(privateVol.mVolumeInfo));
    }

    public @Nullable
    VolumeInfoCompat findVolumeById(String id) {
        return new VolumeInfoCompat(mManager.findVolumeById(id));
    }

    public void partitionPublic(String diskId) {
        mManager.partitionPublic(diskId);
    }

    public void partitionPrivate(String diskId) {
        mManager.partitionPrivate(diskId);
    }

    public long benchmark(String volId) {
        return mManager.benchmark(volId);
    }

    public void unmount(String volId) {
        mManager.unmount(volId);
    }
}
